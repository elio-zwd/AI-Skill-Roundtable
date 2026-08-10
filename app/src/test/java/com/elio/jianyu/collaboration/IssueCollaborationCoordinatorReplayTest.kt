package com.elio.jianyu.collaboration

import com.elio.jianyu.data.ContextContentHasher
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.data.ContextUsageSnapshot
import com.elio.jianyu.data.ExecutionHistoryScope
import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
import com.elio.jianyu.data.ExecutionParticipantStateEntity
import com.elio.jianyu.data.ExecutionRunBudgetEntity
import com.elio.jianyu.data.ExecutionRunEntity
import com.elio.jianyu.data.ExecutionRunKind
import com.elio.jianyu.data.ExecutionThinkingLevel
import com.elio.jianyu.data.ExecutionThinkingSource
import com.elio.jianyu.data.ExecutionRuntimeBudgetConfig
import com.elio.jianyu.data.ExecutionRuntimeSnapshot
import com.elio.jianyu.data.IssueEntity
import com.elio.jianyu.data.IssueLifecycleEntity
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.IssueRecoveryCore
import com.elio.jianyu.data.IssueRecoveryResources
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.JianyuCollaborationRepository
import com.elio.jianyu.data.JianyuExecutionRuntimeRepository
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.Message
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.SnapshotContentState
import com.elio.jianyu.data.StageCollaborationSnapshot
import com.elio.jianyu.data.StageEntity
import com.elio.jianyu.execution.ExecutionPreparedRunCommand
import com.elio.jianyu.execution.ExecutionRunCoordinator
import com.elio.jianyu.execution.ExecutionRunResult
import com.elio.jianyu.execution.ExecutionSkillResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class IssueCollaborationCoordinatorReplayTest {
    @Test
    fun existingStandardRunReplaysFromPersistedMessageRoundAndContext() = runBlocking {
        val ids = CollaborationOperationIds.standard(OPERATION_ID)
        val budgetConfig = ExecutionRuntimeBudgetConfig()
        val run = ExecutionRunEntity(
            id = ids.runId,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            triggerMessageId = ids.userMessageId,
            idempotencyKey = ids.idempotencyKey,
            createdAt = PERSISTED_TIME,
            actualModelId = "gemini-3.6-flash",
            actualThinkingLevel = ExecutionThinkingLevel.MEDIUM,
            thinkingLevelSource = ExecutionThinkingSource.AUTO_ROUTED,
            updatedAt = PERSISTED_TIME,
            runKind = ExecutionRunKind.STANDARD,
            historyScope = ExecutionHistoryScope.FULL_STAGE,
        )
        val participant = ExecutionParticipantSnapshotEntity(
            id = "${ids.runId}-participant-0",
            runId = ids.runId,
            sourceType = "official_skill",
            sourceId = "study-planner",
            displayName = "学习规划助手",
            avatar = "学",
            skillAssetPath = "skills/study-planner/SKILL.md",
            systemPrompt = "冻结提示词",
            configurationJson = "{}",
            defaultResponsibility = "拆解学习目标",
            position = 0,
            createdAt = PERSISTED_TIME,
        )
        val runtime = ExecutionRuntimeSnapshot(
            run = run,
            participants = listOf(participant),
            participantStates = listOf(
                ExecutionParticipantStateEntity(
                    participantSnapshotId = participant.id,
                    runId = run.id,
                    updatedAt = PERSISTED_TIME,
                ),
            ),
            budget = ExecutionRunBudgetEntity(
                rootRunId = run.id,
                maxApiCalls = budgetConfig.maxApiCalls,
                reservedRequiredCalls = 1,
                maxCharacters = budgetConfig.maxCharacters,
                maxSearchQueriesPerCharacter = budgetConfig.maxSearchQueriesPerCharacter,
                maxOutputTokensPerAnswer = budgetConfig.maxOutputTokensPerAnswer,
                updatedAt = PERSISTED_TIME,
            ),
        )
        val trigger = Message(
            id = ids.userMessageId,
            chatId = 1L,
            senderId = "user",
            senderName = "你",
            avatar = "我",
            text = QUESTION,
            timestamp = PERSISTED_TIME,
            roundIndex = PERSISTED_ROUND,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
        )
        val contextContent = "用户确认的学习时间为每周六小时。"
        val contextUsage = ContextUsageSnapshot(
            sourceType = ContextSourceType.PERSONAL_CONTEXT,
            sourceId = "profile-1",
            title = "学习时间",
            content = contextContent,
            contentHash = ContextContentHasher.hash(contextContent),
            contentState = SnapshotContentState.AVAILABLE,
            userConfirmedAt = PERSISTED_TIME - 10,
            usedAt = PERSISTED_TIME,
            networkAllowed = true,
            sensitive = false,
        )
        val repository = repositoryMock()
        val collaborationRepository = repository as JianyuCollaborationRepository
        val runtimeRepository = repository as JianyuExecutionRuntimeRepository
        whenever(repository.recoverIssue(ISSUE_ID)).thenReturn(
            RepositoryResult.Success(recovery(run, participant, trigger)),
        )
        whenever(collaborationRepository.getStageCollaboration(STAGE_ID)).thenReturn(
            RepositoryResult.Success(StageCollaborationSnapshot(emptyList(), emptyMap())),
        )
        whenever(runtimeRepository.getExecutionRuntime(ids.runId)).thenReturn(
            RepositoryResult.Success(runtime),
        )
        whenever(repository.listRunContextUsage(ids.runId)).thenReturn(
            RepositoryResult.Success(listOf(contextUsage)),
        )
        val executionCoordinator = mock<ExecutionRunCoordinator>()
        whenever(executionCoordinator.startPrepared(any())).thenReturn(ExecutionRunResult(runtime))
        val coordinator = IssueCollaborationCoordinator(
            repository = repository,
            executionCoordinator = executionCoordinator,
            integratorResolver = mock<ExecutionSkillResolver>(),
            eligibility = mock<OfficialCollaborationSkillEligibility>(),
        )

        val result = coordinator.startStandardFollowUp(
            StandardFollowUpRequest(
                operationId = OPERATION_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                question = QUESTION,
                roundIndex = 99,
                userConfirmedAt = 9_999L,
            ),
        )

        val command = argumentCaptor<ExecutionPreparedRunCommand>()
        verify(executionCoordinator).startPrepared(command.capture())
        verify(collaborationRepository, never()).createStandardInteraction(any())
        assertEquals(run.id, result.runtime.run.id)
        assertEquals(QUESTION, command.firstValue.currentUserInput)
        assertEquals(PERSISTED_ROUND, command.firstValue.roundIndex)
        assertEquals(PERSISTED_TIME, command.firstValue.userConfirmedAt)
        assertEquals(contextContent, command.firstValue.contributions.single().content)
        assertTrue(command.firstValue.contributions.single().networkAllowed)
    }

    private fun repositoryMock(): JianyuRepository = Mockito.mock(
        JianyuRepository::class.java,
        Mockito.withSettings().extraInterfaces(
            JianyuCollaborationRepository::class.java,
            JianyuExecutionRuntimeRepository::class.java,
        ),
    )

    private fun recovery(
        run: ExecutionRunEntity,
        participant: ExecutionParticipantSnapshotEntity,
        trigger: Message,
    ): IssueRecoverySnapshot {
        val issue = IssueEntity(ISSUE_ID, "测试议题", 100L, 100L)
        val stage = StageEntity(
            id = STAGE_ID,
            issueId = ISSUE_ID,
            sequenceIndex = 0,
            title = "当前阶段",
            objective = "继续分析",
            createdAt = 100L,
            updatedAt = 100L,
        )
        return IssueRecoverySnapshot(
            core = IssueRecoveryCore(
                issue = issue,
                lifecycle = IssueLifecycleEntity(
                    issueId = ISSUE_ID,
                    state = IssueLifecycleState.ACTIVE,
                    stateChangedAt = 100L,
                    updatedAt = 100L,
                ),
                stages = listOf(stage),
                currentStage = stage,
                runs = listOf(run),
                activeOrRecoverableRuns = listOf(run),
                participants = listOf(participant),
                messages = listOf(trigger),
                pendingMessages = emptyList(),
            ),
            resources = IssueRecoveryResources(
                drafts = emptyList(),
                draftRevisions = emptyList(),
                artifacts = emptyList(),
                materialUsages = emptyList(),
                personalContextUsages = emptyList(),
                audioAssets = emptyList(),
            ),
        )
    }

    private companion object {
        const val OPERATION_ID = "operation-replay-12345678"
        const val ISSUE_ID = "issue-replay"
        const val STAGE_ID = "stage-replay"
        const val QUESTION = "请继续检查当前方案"
        const val PERSISTED_ROUND = 3
        const val PERSISTED_TIME = 1_000L
    }
}
