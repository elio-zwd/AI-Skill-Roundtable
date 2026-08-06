package com.elio.jianyu

import android.content.Context
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
import com.elio.jianyu.result.StageResultService
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
    val collaborationCoordinator: IssueCollaborationCoordinator?,
    val stageResultService: StageResultService,
    val audioRuntime: JianyuAudioRuntime,
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
        return JianyuAppRuntime(
            repository = repository,
            officialSkillCatalogRuntimeResult = catalogRuntimeResult,
            executionCoordinator = executionCoordinator,
            collaborationCoordinator = collaborationCoordinator,
            stageResultService = StageResultService(repository),
            audioRuntime = createJianyuAudioRuntime(context, database),
        )
    }
}
