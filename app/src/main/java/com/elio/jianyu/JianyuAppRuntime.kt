package com.elio.jianyu

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.elio.jianyu.audio.runtime.JianyuAudioRuntime
import com.elio.jianyu.audio.runtime.createJianyuAudioRuntime
import com.elio.jianyu.collaboration.IssueCollaborationCoordinator
import com.elio.jianyu.collaboration.OfficialCollaborationSkillEligibility
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.RoomJianyuRepository
import com.elio.jianyu.data.RoundtableDatabase
import com.elio.jianyu.execution.ExecutionContextBuilder
import com.elio.jianyu.execution.ExecutionRunCoordinator
import com.elio.jianyu.execution.InteractionExecutionNetworkGateway
import com.elio.jianyu.execution.JianyuExecutionPersistenceGateway
import com.elio.jianyu.execution.OfficialCatalogExecutionSkillResolver
import com.elio.jianyu.lifecycle.JianyuLifecycleRuntime
import com.elio.jianyu.lifecycle.createJianyuLifecycleRuntime
import com.elio.jianyu.result.StageResultService
import com.elio.jianyu.runtime.DatabaseMaintenanceOutcome
import com.elio.jianyu.runtime.DatabaseMaintenanceStage
import com.elio.jianyu.runtime.JianyuRuntimeLease
import com.elio.jianyu.runtime.JianyuRuntimeState
import com.elio.jianyu.runtime.JianyuRuntimeUnavailableException
import com.elio.jianyu.runtime.RuntimeLeaseRegistry
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.catalog.createOfficialSkillCatalogRuntime
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** App 组合层共享的见域运行时，保证页面与 Repository 使用同一官方 Skill 事实源。 */
data class JianyuAppRuntime(
    val repository: JianyuRepository,
    val officialSkillCatalogRuntimeResult: OfficialSkillCatalogRuntimeResult,
    val executionCoordinator: ExecutionRunCoordinator?,
    val collaborationCoordinator: IssueCollaborationCoordinator?,
    val stageResultService: StageResultService,
    val audioRuntime: JianyuAudioRuntime,
    val lifecycleRuntime: JianyuLifecycleRuntime,
    internal val database: RoundtableDatabase,
)

/**
 * 见域 App Runtime 的唯一生命周期所有者。
 *
 * 普通调用方应使用 [withRuntime] 或 UI 租约；闭库维护只能使用 [withDatabaseClosed]。
 */
object JianyuAppRuntimeProvider {
    private const val DATABASE_NAME = "roundtable_database"

    private val databaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateMonitor = Any()
    private val maintenanceMutex = Mutex()
    private val _state = MutableStateFlow<JianyuRuntimeState>(JianyuRuntimeState.Uninitialized)

    @Volatile
    private var runtime: JianyuAppRuntime? = null
    private var generation: Long = 0L
    private var leaseRegistry = RuntimeLeaseRegistry()

    fun observe(context: Context): StateFlow<JianyuRuntimeState> {
        if (_state.value is JianyuRuntimeState.Uninitialized) {
            runCatching { ensureReady(context.applicationContext) }
        }
        return _state.asStateFlow()
    }

    /**
     * 兼容同步读取入口。维护期间受控失败，绝不返回正在关闭或已经关闭的旧 Runtime。
     */
    fun get(context: Context): JianyuAppRuntime = ensureReady(context.applicationContext)

    /**
     * 为 Compose 根宿主获取指定 Ready 世代的租约。状态已变化时返回 null。
     */
    fun tryAcquireReady(expectedGeneration: Long): JianyuRuntimeLease? = synchronized(stateMonitor) {
        val ready = _state.value as? JianyuRuntimeState.Ready ?: return@synchronized null
        if (ready.generation != expectedGeneration) return@synchronized null
        val registryLease = leaseRegistry.tryAcquire(expectedGeneration) ?: return@synchronized null
        JianyuRuntimeLease(
            generation = expectedGeneration,
            runtime = ready.runtime,
            registryLease = registryLease,
        )
    }

    /** Worker 和非 UI 服务必须在完整业务操作期间持有租约。 */
    suspend fun <T> withRuntime(
        context: Context,
        block: suspend (JianyuAppRuntime) -> T,
    ): T {
        val applicationContext = context.applicationContext
        while (true) {
            when (val current = observe(applicationContext).value) {
                is JianyuRuntimeState.Ready -> {
                    val lease = tryAcquireReady(current.generation)
                    if (lease != null) {
                        try {
                            return block(lease.runtime)
                        } finally {
                            lease.close()
                        }
                    }
                }
                is JianyuRuntimeState.Maintenance -> {
                    _state.first { next -> next !is JianyuRuntimeState.Maintenance }
                }
                is JianyuRuntimeState.Unavailable -> throw JianyuRuntimeUnavailableException()
                JianyuRuntimeState.Uninitialized -> Unit
            }
        }
    }

    /**
     * 在全部 Runtime 消费者释放后执行闭库维护，并在任何闭库后失败或取消时尝试重开。
     *
     * 锁顺序固定为：外部 BackupOperationGate 写锁（由 PR09-13B 提供）→ 本维护 Mutex →
     * Runtime 租约归零 → Room checkpoint/close。
     */
    internal suspend fun <T> withDatabaseClosed(
        context: Context,
        beforeClose: suspend (RoundtableDatabase) -> Unit,
        whileClosed: suspend (databaseFile: File) -> T,
        afterReopen: suspend (JianyuAppRuntime) -> Unit,
    ): DatabaseMaintenanceOutcome<T> {
        maintenanceMutex.lock()
        try {
            val applicationContext = context.applicationContext
            val ready = ensureReady(applicationContext).let { currentRuntime ->
                synchronized(stateMonitor) {
                    val state = _state.value as? JianyuRuntimeState.Ready
                        ?: throw JianyuRuntimeUnavailableException()
                    check(state.runtime === currentRuntime) { "Runtime 状态与缓存不一致" }
                    if (!leaseRegistry.stopAccepting(state.generation)) {
                        throw JianyuRuntimeUnavailableException()
                    }
                    _state.value = JianyuRuntimeState.Maintenance(state.generation)
                    state
                }
            }

            try {
                leaseRegistry.awaitReleased(ready.generation)
            } catch (error: CancellationException) {
                restoreExistingReady(ready)
                throw error
            }

            try {
                beforeClose(ready.runtime.database)
            } catch (error: CancellationException) {
                restoreExistingReady(ready)
                throw error
            } catch (error: Throwable) {
                restoreExistingReady(ready)
                return DatabaseMaintenanceOutcome.Failure(
                    stage = DatabaseMaintenanceStage.BEFORE_CLOSE,
                    cause = error,
                    reopened = false,
                    generation = ready.generation,
                )
            }

            val targetGeneration = ready.generation + 1L
            var operationValue: T? = null
            var operationFailure: Throwable? = null
            var cancellation: CancellationException? = null
            var closeFailure: Throwable? = null

            try {
                RoundtableDatabase.closeAndClear(ready.runtime.database)
            } catch (error: Throwable) {
                closeFailure = error
            }

            if (closeFailure == null) {
                try {
                    operationValue = whileClosed(applicationContext.getDatabasePath(DATABASE_NAME))
                } catch (error: CancellationException) {
                    cancellation = error
                } catch (error: Throwable) {
                    operationFailure = error
                }
            } else {
                withContext(NonCancellable) {
                    closeDatabaseBestEffort(ready.runtime.database)
                }
            }

            val reopenedRuntimeResult = withContext(NonCancellable + Dispatchers.IO) {
                runCatching {
                    create(
                        context = applicationContext,
                        recoverPendingOperations = false,
                    )
                }
            }
            val reopenedRuntime = reopenedRuntimeResult.getOrNull()
            if (reopenedRuntime == null) {
                val reopenError = reopenedRuntimeResult.exceptionOrNull()
                    ?: IllegalStateException("runtime_reopen_failed")
                publishUnavailable(
                    generation = targetGeneration,
                    stage = DatabaseMaintenanceStage.REOPEN,
                )
                cancellation?.let { throw it }
                return DatabaseMaintenanceOutcome.Failure(
                    stage = DatabaseMaintenanceStage.REOPEN,
                    cause = reopenError,
                    reopened = false,
                    generation = targetGeneration,
                )
            }

            var afterReopenFailure: Throwable? = null
            withContext(NonCancellable + Dispatchers.IO) {
                try {
                    afterReopen(reopenedRuntime)
                } catch (error: Throwable) {
                    afterReopenFailure = error
                }
            }
            if (afterReopenFailure != null) {
                withContext(NonCancellable + Dispatchers.IO) {
                    closeDatabaseBestEffort(reopenedRuntime.database)
                }
                publishUnavailable(
                    generation = targetGeneration,
                    stage = DatabaseMaintenanceStage.AFTER_REOPEN,
                )
                cancellation?.let { throw it }
                return DatabaseMaintenanceOutcome.Failure(
                    stage = DatabaseMaintenanceStage.AFTER_REOPEN,
                    cause = requireNotNull(afterReopenFailure),
                    reopened = false,
                    generation = targetGeneration,
                )
            }

            publishReady(
                runtime = reopenedRuntime,
                newGeneration = targetGeneration,
            )

            cancellation?.let { throw it }
            closeFailure?.let { error ->
                return DatabaseMaintenanceOutcome.Failure(
                    stage = DatabaseMaintenanceStage.CLOSE,
                    cause = error,
                    reopened = true,
                    generation = targetGeneration,
                )
            }
            operationFailure?.let { error ->
                return DatabaseMaintenanceOutcome.Failure(
                    stage = DatabaseMaintenanceStage.WHILE_CLOSED,
                    cause = error,
                    reopened = true,
                    generation = targetGeneration,
                )
            }

            @Suppress("UNCHECKED_CAST")
            return DatabaseMaintenanceOutcome.Success(
                value = operationValue as T,
                generation = targetGeneration,
            )
        } finally {
            maintenanceMutex.unlock()
        }
    }

    /**
     * Unavailable 只允许显式重试。候选 Runtime 通过最小查询和外键检查后才重新发布。
     */
    suspend fun retryOpen(context: Context): Boolean {
        maintenanceMutex.lock()
        try {
            val unavailable = synchronized(stateMonitor) {
                _state.value as? JianyuRuntimeState.Unavailable
            } ?: return _state.value is JianyuRuntimeState.Ready

            val reopenedRuntimeResult = withContext(NonCancellable + Dispatchers.IO) {
                runCatching {
                    create(
                        context = context.applicationContext,
                        recoverPendingOperations = false,
                    ).also(::validateRuntimeHealth)
                }
            }
            val reopenedRuntime = reopenedRuntimeResult.getOrNull()
            if (reopenedRuntime == null) {
                publishUnavailable(
                    generation = unavailable.generation,
                    stage = DatabaseMaintenanceStage.REOPEN,
                )
                return false
            }
            publishReady(
                runtime = reopenedRuntime,
                newGeneration = unavailable.generation,
            )
            return true
        } finally {
            maintenanceMutex.unlock()
        }
    }

    @VisibleForTesting
    @Suppress("UNUSED_PARAMETER")
    internal suspend fun resetForTests(context: Context) {
        maintenanceMutex.lock()
        try {
            val currentReady = synchronized(stateMonitor) {
                val ready = _state.value as? JianyuRuntimeState.Ready
                if (ready != null) {
                    leaseRegistry.stopAccepting(ready.generation)
                    _state.value = JianyuRuntimeState.Maintenance(ready.generation)
                }
                ready
            }
            if (currentReady != null) {
                leaseRegistry.awaitReleased(currentReady.generation)
                closeDatabaseBestEffort(currentReady.runtime.database)
            }
            synchronized(stateMonitor) {
                runtime = null
                generation = 0L
                leaseRegistry = RuntimeLeaseRegistry()
                _state.value = JianyuRuntimeState.Uninitialized
            }
        } finally {
            maintenanceMutex.unlock()
        }
    }

    private fun ensureReady(context: Context): JianyuAppRuntime {
        val cached = runtime
        val currentState = _state.value
        if (cached != null && currentState is JianyuRuntimeState.Ready &&
            currentState.runtime === cached
        ) {
            return cached
        }
        return synchronized(stateMonitor) {
            when (val state = _state.value) {
                is JianyuRuntimeState.Ready -> state.runtime
                is JianyuRuntimeState.Maintenance,
                is JianyuRuntimeState.Unavailable,
                -> throw JianyuRuntimeUnavailableException()
                JianyuRuntimeState.Uninitialized -> {
                    val created = try {
                        create(
                            context = context,
                            recoverPendingOperations = true,
                        )
                    } catch (error: Throwable) {
                        generation = maxOf(1L, generation + 1L)
                        _state.value = JianyuRuntimeState.Unavailable(
                            generation = generation,
                            stage = DatabaseMaintenanceStage.REOPEN,
                        )
                        throw error
                    }
                    publishReadyLocked(created, maxOf(1L, generation + 1L))
                    created
                }
            }
        }
    }

    private fun restoreExistingReady(ready: JianyuRuntimeState.Ready) {
        synchronized(stateMonitor) {
            runtime = ready.runtime
            generation = ready.generation
            leaseRegistry.openGeneration(ready.generation)
            _state.value = ready
        }
    }

    private fun publishReady(
        runtime: JianyuAppRuntime,
        newGeneration: Long,
    ) {
        synchronized(stateMonitor) {
            publishReadyLocked(runtime, newGeneration)
        }
    }

    private fun publishReadyLocked(
        created: JianyuAppRuntime,
        newGeneration: Long,
    ) {
        runtime = created
        generation = newGeneration
        leaseRegistry.openGeneration(newGeneration)
        _state.value = JianyuRuntimeState.Ready(
            generation = newGeneration,
            runtime = created,
        )
    }

    private fun publishUnavailable(
        generation: Long,
        stage: DatabaseMaintenanceStage,
    ) {
        synchronized(stateMonitor) {
            runtime = null
            this.generation = generation
            _state.value = JianyuRuntimeState.Unavailable(
                generation = generation,
                stage = stage,
            )
        }
    }

    private fun validateRuntimeHealth(candidate: JianyuAppRuntime) {
        candidate.database.openHelper.writableDatabase
            .query("SELECT 1")
            .use { cursor -> check(cursor.moveToFirst()) { "database_minimal_query_failed" } }
        candidate.database.openHelper.writableDatabase
            .query("PRAGMA foreign_key_check")
            .use { cursor -> check(cursor.count == 0) { "database_foreign_key_check_failed" } }
    }

    private fun closeDatabaseBestEffort(database: RoundtableDatabase) {
        runCatching {
            RoundtableDatabase.closeAndClear(database)
        }.onFailure {
            runCatching {
                if (database.isOpen) {
                    database.close()
                }
            }
        }
    }

    private fun create(
        context: Context,
        recoverPendingOperations: Boolean,
    ): JianyuAppRuntime {
        val catalogRuntimeResult = createOfficialSkillCatalogRuntime(context)
        val database = RoundtableDatabase.getDatabase(
            context = context,
            scope = databaseScope,
        )
        try {
            val repository = when (catalogRuntimeResult) {
                is OfficialSkillCatalogRuntimeResult.Success -> RoomJianyuRepository(
                    database = database,
                    officialSkillIdValidator = catalogRuntimeResult.runtime.validator,
                )
                is OfficialSkillCatalogRuntimeResult.Failure -> RoomJianyuRepository(
                    database = database,
                )
            }
            var collaborationCoordinator: IssueCollaborationCoordinator? = null
            val executionCoordinator = when (catalogRuntimeResult) {
                is OfficialSkillCatalogRuntimeResult.Success -> {
                    val skillResolver = OfficialCatalogExecutionSkillResolver(
                        context = context,
                        catalog = catalogRuntimeResult.runtime.catalog,
                        executionEligibility = catalogRuntimeResult.runtime.executionEligibility,
                    )
                    ExecutionRunCoordinator(
                        persistence = JianyuExecutionPersistenceGateway(repository),
                        skillResolver = skillResolver,
                        networkGateway = InteractionExecutionNetworkGateway(context),
                        contextBuilder = ExecutionContextBuilder(),
                    ).also { coordinator ->
                        collaborationCoordinator = IssueCollaborationCoordinator(
                            repository = repository,
                            executionCoordinator = coordinator,
                            integratorResolver = skillResolver,
                            eligibility = OfficialCollaborationSkillEligibility(
                                catalog = catalogRuntimeResult.runtime.catalog,
                                executionEligibility = catalogRuntimeResult.runtime.executionEligibility,
                            ),
                        )
                    }
                }
                is OfficialSkillCatalogRuntimeResult.Failure -> null
            }
            val audioRuntime = createJianyuAudioRuntime(context, database)
            val lifecycleRuntime = createJianyuLifecycleRuntime(
                context = context,
                database = database,
                repository = repository,
                audioRuntime = audioRuntime,
                executionCoordinator = executionCoordinator,
                collaborationCoordinator = collaborationCoordinator,
            )
            if (recoverPendingOperations) {
                databaseScope.launch {
                    lifecycleRuntime.purgeCoordinator.recoverPendingOperations()
                }
            }
            return JianyuAppRuntime(
                repository = repository,
                officialSkillCatalogRuntimeResult = catalogRuntimeResult,
                executionCoordinator = executionCoordinator,
                collaborationCoordinator = collaborationCoordinator,
                stageResultService = StageResultService(repository),
                audioRuntime = audioRuntime,
                lifecycleRuntime = lifecycleRuntime,
                database = database,
            )
        } catch (error: Throwable) {
            closeDatabaseBestEffort(database)
            throw error
        }
    }
}
