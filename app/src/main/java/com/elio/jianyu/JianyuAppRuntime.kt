package com.elio.jianyu

import android.content.Context
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.RoomJianyuRepository
import com.elio.jianyu.data.RoundtableDatabase
import com.elio.jianyu.execution.ExecutionContextBuilder
import com.elio.jianyu.execution.ExecutionRunCoordinator
import com.elio.jianyu.execution.InteractionExecutionNetworkGateway
import com.elio.jianyu.execution.JianyuExecutionPersistenceGateway
import com.elio.jianyu.execution.OfficialCatalogExecutionSkillResolver
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.catalog.createOfficialSkillCatalogRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** App 组合层共享的见域运行时，保证页面与 Repository 使用同一官方 Skill 事实源。 */
data class JianyuAppRuntime(
    val repository: JianyuRepository,
    val officialSkillCatalogRuntimeResult: OfficialSkillCatalogRuntimeResult,
    val executionCoordinator: ExecutionRunCoordinator?,
)

object JianyuAppRuntimeProvider {
    private val databaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var runtime: JianyuAppRuntime? = null

    fun get(context: Context): JianyuAppRuntime =
        runtime ?: synchronized(this) {
            runtime ?: create(context.applicationContext).also { created ->
                runtime = created
            }
        }

    private fun create(context: Context): JianyuAppRuntime {
        val catalogRuntimeResult = createOfficialSkillCatalogRuntime(context)
        val database = RoundtableDatabase.getDatabase(
            context = context,
            scope = databaseScope,
        )
        val repository = when (catalogRuntimeResult) {
            is OfficialSkillCatalogRuntimeResult.Success -> RoomJianyuRepository(
                database = database,
                officialSkillIdValidator = catalogRuntimeResult.runtime.validator,
            )
            is OfficialSkillCatalogRuntimeResult.Failure -> RoomJianyuRepository(
                database = database,
            )
        }
        val executionCoordinator = when (catalogRuntimeResult) {
            is OfficialSkillCatalogRuntimeResult.Success -> ExecutionRunCoordinator(
                persistence = JianyuExecutionPersistenceGateway(repository),
                skillResolver = OfficialCatalogExecutionSkillResolver(
                    context = context,
                    catalog = catalogRuntimeResult.runtime.catalog,
                    executionEligibility = catalogRuntimeResult.runtime.executionEligibility,
                ),
                networkGateway = InteractionExecutionNetworkGateway(context),
                contextBuilder = ExecutionContextBuilder(),
            )
            is OfficialSkillCatalogRuntimeResult.Failure -> null
        }
        return JianyuAppRuntime(
            repository = repository,
            officialSkillCatalogRuntimeResult = catalogRuntimeResult,
            executionCoordinator = executionCoordinator,
        )
    }
}
