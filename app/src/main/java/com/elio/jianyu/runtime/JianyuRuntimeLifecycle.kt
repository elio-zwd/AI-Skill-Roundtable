package com.elio.jianyu.runtime

import com.elio.jianyu.JianyuAppRuntime
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * App Runtime 的可观察生命周期。
 *
 * 状态中只包含运行时世代与稳定维护阶段，不包含用户正文、路径、异常消息或密钥。
 */
sealed interface JianyuRuntimeState {
    data object Uninitialized : JianyuRuntimeState

    data class Ready(
        val generation: Long,
        val runtime: JianyuAppRuntime,
    ) : JianyuRuntimeState

    data class Maintenance(
        val generation: Long,
    ) : JianyuRuntimeState

    data class Unavailable(
        val generation: Long,
        val stage: DatabaseMaintenanceStage,
    ) : JianyuRuntimeState
}

/** 数据库维护阶段使用稳定枚举，禁止把底层异常正文直接展示给 UI。 */
enum class DatabaseMaintenanceStage {
    QUIESCE,
    BEFORE_CLOSE,
    CLOSE,
    WHILE_CLOSED,
    REOPEN,
    AFTER_REOPEN,
}

/**
 * 闭库维护结果。
 *
 * [Failure.cause] 仅供同进程错误映射、测试和安全日志分类，不应直接展示。
 */
sealed interface DatabaseMaintenanceOutcome<out T> {
    data class Success<T>(
        val value: T,
        val generation: Long,
    ) : DatabaseMaintenanceOutcome<T>

    data class Failure(
        val stage: DatabaseMaintenanceStage,
        val cause: Throwable,
        val reopened: Boolean,
        val generation: Long,
    ) : DatabaseMaintenanceOutcome<Nothing>
}

/** 维护期间直接访问 Runtime 的受控失败；不携带异常或用户数据。 */
class JianyuRuntimeUnavailableException internal constructor() :
    IllegalStateException("jianyu_runtime_unavailable")

/**
 * 一次 Runtime 使用租约。租约关闭幂等，调用方不得跨协程长期缓存。
 */
class JianyuRuntimeLease internal constructor(
    val generation: Long,
    val runtime: JianyuAppRuntime,
    private val registryLease: RuntimeLeaseRegistry.Lease,
) : AutoCloseable {
    override fun close() {
        registryLease.close()
    }
}

/**
 * 纯内存世代租约登记表。
 *
 * 维护流程只允许停止接收新租约并等待已有租约自然释放；没有强制清零、跨世代释放或
 * 删除他人租约的接口。
 */
internal class RuntimeLeaseRegistry {
    private val monitor = Any()
    private val changeVersion = MutableStateFlow(0L)
    private val activeByGeneration = mutableMapOf<Long, Int>()
    private var acceptingGeneration: Long? = null

    fun openGeneration(generation: Long) {
        require(generation > 0L) { "运行时世代必须为正数" }
        synchronized(monitor) {
            acceptingGeneration = generation
            activeByGeneration.putIfAbsent(generation, 0)
            signalChangedLocked()
        }
    }

    fun stopAccepting(generation: Long): Boolean = synchronized(monitor) {
        if (acceptingGeneration != generation) {
            false
        } else {
            acceptingGeneration = null
            signalChangedLocked()
            true
        }
    }

    fun tryAcquire(generation: Long): Lease? = synchronized(monitor) {
        if (acceptingGeneration != generation) {
            null
        } else {
            activeByGeneration[generation] = activeByGeneration.getValue(generation) + 1
            signalChangedLocked()
            Lease(
                generation = generation,
                release = ::release,
            )
        }
    }

    suspend fun awaitReleased(generation: Long) {
        while (true) {
            val observedVersion = synchronized(monitor) {
                if (activeByGeneration[generation].orZero() == 0) {
                    return
                }
                changeVersion.value
            }
            changeVersion.first { version -> version != observedVersion }
        }
    }

    fun activeLeaseCount(generation: Long): Int = synchronized(monitor) {
        activeByGeneration[generation].orZero()
    }

    private fun release(generation: Long) {
        synchronized(monitor) {
            val current = activeByGeneration[generation].orZero()
            check(current > 0) { "运行时租约计数不能为负数" }
            activeByGeneration[generation] = current - 1
            if (current == 1 && acceptingGeneration != generation) {
                activeByGeneration.remove(generation)
            }
            signalChangedLocked()
        }
    }

    private fun signalChangedLocked() {
        changeVersion.value = changeVersion.value + 1L
    }

    internal class Lease(
        private val generation: Long,
        private val release: (Long) -> Unit,
    ) : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (closed.compareAndSet(false, true)) {
                release(generation)
            }
        }
    }
}

private fun Int?.orZero(): Int = this ?: 0
