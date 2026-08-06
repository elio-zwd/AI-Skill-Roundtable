package com.elio.jianyu.home

import com.elio.jianyu.data.ContextPreparationResult
import com.elio.jianyu.data.ContextUsageWriteSet
import com.elio.jianyu.data.PrepareExecutionContextCommand
import com.elio.jianyu.data.PreparedExecutionContext
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.SaveIssueCommand
import com.elio.jianyu.execution.ExecutionStartCommand
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStartContextGateTest {
    @Test
    fun personHighStakesAndNetworkRefusalCreatesNoIssueNoContextAndNoRun() = runBlocking {
        val repository = RecordingHomeRepository()
        val starter = RecordingExecutionStarter()
        val coordinator = HomeStartCoordinator(repository, starter)

        val result = coordinator.start(
            confirmation().copy(
                executionConsent = HomeExecutionConsentSnapshot(),
            ),
        )

        assertTrue(result is HomeStartResult.Failure)
        assertEquals(
            "network_authorization_required",
            (result as HomeStartResult.Failure).errorCode,
        )
        assertTrue(repository.saved.isEmpty())
        assertTrue(repository.prepared.isEmpty())
        assertTrue(starter.started.isEmpty())
    }

    @Test
    fun restrictedPatentMaterialCreatesNoIssueAndNoRun() = runBlocking {
        val repository = RecordingHomeRepository()
        val starter = RecordingExecutionStarter()
        val coordinator = HomeStartCoordinator(repository, starter)
        val base = confirmation(
            skill = RecommendedSkill(
                skillId = "patent-disclosure-organizer",
                displayName = "专利交底材料整理",
                responsibility = "整理脱敏摘要",
                reason = "测试禁止外传门禁",
                risk = RecommendationRisk.HIGH_STAKES,
                riskDisclosure = "禁止外传",
                freshnessDisclosure = "按法域核验",
                networkRequirement = "资料正文不得联网发送",
                materialRequirement = "需要资料",
                expectedOutput = "材料清单",
                executable = true,
                selected = true,
                position = 0,
                requiresHighStakesConfirmation = true,
                requiresMaterial = true,
                requiresMaterialAuthorization = true,
                requiresSensitiveMaterialConfirmation = true,
                prohibitsExternalMaterial = true,
            ),
        )
        val result = coordinator.start(
            base.copy(
                contextSelection = HomeContextSelectionSnapshot(
                    items = listOf(
                        HomeContextItemSnapshot(
                            sourceType = "material",
                            sourceId = "secret-material",
                            title = "未公开技术材料",
                            originalContent = "敏感正文",
                            selectedContent = "敏感正文",
                            sourceHash = "hash",
                            sourceUpdatedAt = 1L,
                            sensitive = true,
                            selected = true,
                            networkAllowed = true,
                            sensitiveConfirmed = true,
                            userConfirmedAt = 100L,
                        ),
                    ),
                    confirmed = true,
                ),
                executionConsent = HomeExecutionConsentSnapshot(
                    highStakesConfirmed = true,
                    restrictedMaterialPresent = true,
                    materialMayLeaveDevice = true,
                ),
            ),
        )

        assertTrue(result is HomeStartResult.Failure)
        assertEquals(
            "material_external_transfer_prohibited",
            (result as HomeStartResult.Failure).errorCode,
        )
        assertTrue(repository.saved.isEmpty())
        assertTrue(repository.prepared.isEmpty())
        assertTrue(starter.started.isEmpty())
    }

    private fun confirmation(
        skill: RecommendedSkill = RecommendedSkill(
            skillId = "person-high-network",
            displayName = "人物高后果 Skill",
            responsibility = "给出分析框架",
            reason = "测试上下文门禁",
            risk = RecommendationRisk.HIGH_STAKES,
            riskDisclosure = "AI 模拟，不代表本人；需要专业复核",
            freshnessDisclosure = "当前事实需要联网核验",
            networkRequirement = "需要联网核验",
            materialRequirement = "资料可选",
            expectedOutput = "风险分析",
            executable = true,
            selected = true,
            position = 0,
            isPersonPerspective = true,
            requiresHighStakesConfirmation = true,
            requiresNetworkAuthorization = true,
        ),
    ): HomeFinalConfirmation = HomeFinalConfirmation(
        ids = HomeWorkflowIds(
            workflowId = "workflow-gate",
            issueId = "issue-gate",
            stageId = "stage-gate",
            runId = "run-gate",
            saveIssueIdempotencyKey = "save-gate",
            executionIdempotencyKey = "execute-gate",
        ),
        question = "测试上下文门禁",
        directions = emptySet(),
        recommendation = HomeRecommendation(
            questionSummary = "测试上下文门禁",
            directions = emptySet(),
            mode = RecommendationMode.SINGLE,
            modeReason = "测试",
            skills = listOf(skill),
            expectedOutput = skill.expectedOutput,
            source = RecommendationSource.LOCAL_CATALOG,
        ),
        contextSelection = HomeContextSelectionSnapshot(confirmed = true),
        executionConsent = HomeExecutionConsentSnapshot(
            networkAuthorized = true,
            highStakesConfirmed = true,
            personDisclaimerConfirmed = true,
        ),
        confirmedAt = 200L,
    )
}

private class RecordingHomeRepository : HomeRepositoryGateway {
    val saved = mutableListOf<SaveIssueCommand>()
    val prepared = mutableListOf<PrepareExecutionContextCommand>()

    override suspend fun saveIssue(command: SaveIssueCommand): RepositoryResult<Unit> {
        saved += command
        return RepositoryResult.Success(Unit)
    }

    override suspend fun prepareExecutionContext(
        command: PrepareExecutionContextCommand,
    ): RepositoryResult<PreparedExecutionContext> {
        prepared += command
        return RepositoryResult.Success(
            PreparedExecutionContext(
                preparation = ContextPreparationResult.Ready(
                    items = emptyList(),
                    contributions = emptyList(),
                    totalCharacters = 0,
                    remainingCharacters = 24_000,
                ),
                usage = ContextUsageWriteSet(),
            ),
        )
    }
}

private class RecordingExecutionStarter : HomeExecutionStarter {
    val started = mutableListOf<ExecutionStartCommand>()

    override suspend fun start(command: ExecutionStartCommand): HomeExecutionStartResult {
        started += command
        return HomeExecutionStartResult.Started(command.runId)
    }
}
