package com.elio.jianyu.execution

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.data.AppendDomainMessageCommand
import com.elio.jianyu.data.ConsumeExecutionBudgetCommand
import com.elio.jianyu.data.CreateExecutionRuntimeCommand
import com.elio.jianyu.data.ExecutionParticipantStateEntity
import com.elio.jianyu.data.ExecutionRunBudgetEntity
import com.elio.jianyu.data.ExecutionRunEntity
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.ExecutionRuntimeSnapshot
import com.elio.jianyu.data.IssueEntity
import com.elio.jianyu.data.IssueLifecycleEntity
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.IssueRecoveryCore
import com.elio.jianyu.data.IssueRecoveryResources
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.Message
import com.elio.jianyu.data.RecoverInterruptedExecutionCommand
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.SetExecutionBudgetReserveCommand
import com.elio.jianyu.data.StageEntity
import com.elio.jianyu.data.TransitionExecutionParticipantCommand
import com.elio.jianyu.data.TransitionRunCommand
import com.elio.jianyu.data.UpdatePendingDomainMessageCommand
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.catalog.createOfficialSkillCatalogRuntime
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExecutableSkillCoordinatorIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun realSingleSkillResolvesStreamsPersistsAndSucceeds() = runBlocking {
        val persistence = FakeExecutionPersistence()
        val network = FakeExecutionNetworkGateway(
            responses = mapOf("study-planner" to listOf("计划", "计划完成")),
        )
        val coordinator = coordinator(persistence, network)

        val result = coordinator.start(
            startCommand(
                runId = "run-real-single",
                selections = listOf(
                    ExecutionSkillSelection(
                        officialSkillId = "study-planner",
                        defaultResponsibility = "拆解目标和检查节点",
                    ),
                ),
            ),
        )

        assertEquals(ExecutionRunStatus.SUCCEEDED, result.runtime.run.status)
        assertEquals(listOf("study-planner"), network.calls)
        assertEquals(1, result.runtime.participants.size)
        assertEquals("study-planner", result.runtime.participants.single().sourceId)
        assertTrue(result.runtime.participants.single().systemPrompt.isNotBlank())
        assertEquals("拆解目标和检查节点", result.runtime.participants.single().defaultResponsibility)
        assertEquals("计划完成", persistence.messages.single().text)
        assertFalse(persistence.messages.single().isPending)
        assertEquals(
            ExecutionParticipantStatus.SUCCEEDED,
            result.runtime.participantStates.single().status,
        )
    }

    @Test
    fun realMultipleSkillsExecuteInFrozenPositionOrderWithIndependentMessages() = runBlocking {
        val persistence = FakeExecutionPersistence()
        val network = FakeExecutionNetworkGateway(
            responses = mapOf(
                "research-fact-checker" to listOf("核查完成"),
                "report-proposal-writer" to listOf("汇报完成"),
            ),
        )
        val coordinator = coordinator(persistence, network)

        val result = coordinator.start(
            startCommand(
                runId = "run-real-multiple",
                selections = listOf(
                    ExecutionSkillSelection(
                        officialSkillId = "research-fact-checker",
                        defaultResponsibility = "核查事实、来源和时效",
                    ),
                    ExecutionSkillSelection(
                        officialSkillId = "report-proposal-writer",
                        defaultResponsibility = "形成可编辑汇报草稿",
                    ),
                ),
            ),
        )

        assertEquals(ExecutionRunStatus.SUCCEEDED, result.runtime.run.status)
        assertEquals(
            listOf("research-fact-checker", "report-proposal-writer"),
            network.calls,
        )
        assertEquals(listOf(0, 1), result.runtime.participants.map { it.position })
        assertEquals(2, result.runtime.participants.map { it.sourceId }.distinct().size)
        assertTrue(result.runtime.participants.all { it.systemPrompt.isNotBlank() })
        assertEquals(2, persistence.messages.size)
        assertEquals(
            setOf("research-fact-checker", "report-proposal-writer"),
            persistence.messages.map { it.senderId }.toSet(),
        )
        assertTrue(result.runtime.participantStates.all {
            it.status == ExecutionParticipantStatus.SUCCEEDED
        })
    }

    private fun coordinator(
        persistence: FakeExecutionPersistence,
        network: FakeExecutionNetworkGateway,
    ): ExecutionRunCoordinator {
        val runtime = requireNotNull(
            (createOfficialSkillCatalogRuntime(context) as? OfficialSkillCatalogRuntimeResult.Success)
                ?.runtime,
        )
        return ExecutionRunCoordinator(
            persistence = persistence,
            skillResolver = OfficialCatalogExecutionSkillResolver(
                context = context,
                catalog = runtime.catalog,
                executionEligibility = runtime.executionEligibility,
            ),
            networkGateway = network,
            contextBuilder = ExecutionContextBuilder(),
            clock = ExecutionClock { persistence.nextTime() },
        )
    }

    private fun startCommand(
        runId: String,
        selections: List<ExecutionSkillSelection>,
    ): ExecutionStartCommand = ExecutionStartCommand(
        runId = runId,
        issueId = ISSUE_ID,
        stageId = STAGE_ID,
        triggerMessageId = null,
        idempotencyKey = "command-$runId",
        selections = selections,
        currentUserInput = "请根据已确认的问题独立回答",
        roundIndex = 1,
        userConfirmedAt = 1_000L,
    )

    private class FakeExecutionNetworkGateway(
        private val responses: Map<String, List<String>>,
    ) : ExecutionNetworkGateway {
        val calls = mutableListOf<String>()

        override suspend fun prepare(request: ExecutionNetworkRequest): PreparedExecutionNetworkCall {
            val chunks = responses.getValue(request.participant.sourceId)
            return PreparedExecutionNetworkCall { onAttemptStarted, onTextUpdate ->
                calls += request.participant.sourceId
                onAttemptStarted()
                chunks.forEach { onTextUpdate(it) }
                ExecutionNetworkResult(
                    interactionId = "interaction-${request.participant.position}",
                    text = chunks.last(),
                )
            }
        }
    }

    private class FakeExecutionPersistence : ExecutionPersistenceGateway {
        private val issue = IssueEntity(ISSUE_ID, "Issue", 1L, 1L)
        private val stage = StageEntity(STAGE_ID, ISSUE_ID, 0, "Stage", "Objective", 1L, 1L)
        private val runtimes = ConcurrentHashMap<String, ExecutionRuntimeSnapshot>()
        private val budgets = ConcurrentHashMap<String, ExecutionRunBudgetEntity>()
        private var time = 10_000L
        val messages = mutableListOf<Message>()

        fun nextTime(): Long = synchronized(this) { ++time }

        override suspend fun recoverIssue(issueId: String): IssueRecoverySnapshot {
            val allRuns = runtimes.values.map { it.run }.sortedBy { it.createdAt }
            val allParticipants = runtimes.values.flatMap { it.participants }
            return IssueRecoverySnapshot(
                core = IssueRecoveryCore(
                    issue = issue,
                    lifecycle = IssueLifecycleEntity(
                        issueId = ISSUE_ID,
                        state = IssueLifecycleState.ACTIVE,
                        stateChangedAt = 1L,
                        updatedAt = 1L,
                    ),
                    stages = listOf(stage),
                    currentStage = stage,
                    runs = allRuns,
                    activeOrRecoverableRuns = allRuns.filter {
                        it.status in setOf(
                            ExecutionRunStatus.NOT_STARTED,
                            ExecutionRunStatus.RUNNING,
                            ExecutionRunStatus.PARTIAL_SUCCESS,
                            ExecutionRunStatus.RETRYABLE,
                        )
                    },
                    participants = allParticipants,
                    messages = messages.toList(),
                    pendingMessages = messages.filter { it.isPending },
                ),
                resources = IssueRecoveryResources(
                    materialUsages = emptyList(),
                    personalContextUsages = emptyList(),
                    drafts = emptyList(),
                    draftRevisions = emptyList(),
                    artifacts = emptyList(),
                    audioAssets = emptyList(),
                ),
            )
        }

        override suspend fun createRuntime(
            command: CreateExecutionRuntimeCommand,
        ): ExecutionRuntimeSnapshot {
            runtimes.values.firstOrNull {
                it.run.idempotencyKey == command.run.idempotencyKey
            }?.let { return it }
            val budget = if (command.budgetRootRunId == command.run.id) {
                ExecutionRunBudgetEntity(
                    rootRunId = command.run.id,
                    maxApiCalls = command.budget.maxApiCalls,
                    reservedRequiredCalls = command.participants.size,
                    maxCharacters = command.budget.maxCharacters,
                    maxSearchQueriesPerCharacter = command.budget.maxSearchQueriesPerCharacter,
                    maxOutputTokensPerAnswer = command.budget.maxOutputTokensPerAnswer,
                    updatedAt = command.run.createdAt,
                ).also { budgets[it.rootRunId] = it }
            } else {
                budgets.getValue(command.budgetRootRunId)
            }
            return ExecutionRuntimeSnapshot(
                run = command.run,
                participants = command.participants,
                participantStates = command.participants.map {
                    ExecutionParticipantStateEntity(
                        participantSnapshotId = it.id,
                        runId = command.run.id,
                        updatedAt = command.run.createdAt,
                    )
                },
                budget = budget,
            ).also { runtimes[command.run.id] = it }
        }

        override suspend fun getRuntime(runId: String): ExecutionRuntimeSnapshot {
            val runtime = runtimes.getValue(runId)
            return runtime.copy(budget = budgets.getValue(runtime.budget.rootRunId))
        }

        override suspend fun transitionParticipant(
            command: TransitionExecutionParticipantCommand,
        ): ExecutionParticipantStateEntity {
            val runtime = runtimes.getValue(command.runId)
            val current = runtime.participantStates.first {
                it.participantSnapshotId == command.participantSnapshotId
            }
            check(current.status in command.expectedStatuses)
            val updated = current.copy(
                status = command.newStatus,
                attemptCount = current.attemptCount + command.attemptIncrement,
                outputMessageId = command.outputMessageId ?: current.outputMessageId,
                startedAt = command.startedAt ?: current.startedAt,
                finishedAt = command.finishedAt ?: current.finishedAt,
                lastErrorCode = command.lastErrorCode,
                lastErrorMessage = command.lastErrorMessage,
                hasIncompleteOutput = command.hasIncompleteOutput,
                updatedAt = command.updatedAt,
            )
            runtimes[command.runId] = runtime.copy(
                participantStates = runtime.participantStates.map {
                    if (it.participantSnapshotId == updated.participantSnapshotId) updated else it
                },
            )
            return updated
        }

        override suspend fun consumeBudget(
            command: ConsumeExecutionBudgetCommand,
        ): ExecutionRunBudgetEntity {
            val current = budgets.getValue(command.rootRunId)
            if (
                current.closed ||
                current.usedApiCalls + command.count + command.reserveForRequired > current.maxApiCalls
            ) {
                throw ExecutionRepositoryException(
                    RepositoryError.InvalidState("consume_execution_budget", "budget_exhausted"),
                )
            }
            return current.copy(
                usedApiCalls = current.usedApiCalls + command.count,
                reservedRequiredCalls = command.reserveForRequired,
                updatedAt = command.updatedAt,
            ).also { budgets[command.rootRunId] = it }
        }

        override suspend fun setBudgetReserve(
            command: SetExecutionBudgetReserveCommand,
        ): ExecutionRunBudgetEntity {
            val current = budgets.getValue(command.rootRunId)
            return current.copy(
                reservedRequiredCalls = command.reservedRequiredCalls,
                updatedAt = command.updatedAt,
            ).also { budgets[command.rootRunId] = it }
        }

        override suspend fun closeBudget(
            rootRunId: String,
            updatedAt: Long,
        ): ExecutionRunBudgetEntity {
            val current = budgets.getValue(rootRunId)
            return current.copy(closed = true, updatedAt = updatedAt)
                .also { budgets[rootRunId] = it }
        }

        override suspend fun appendMessage(command: AppendDomainMessageCommand): Message {
            check(messages.none { it.id == command.messageId })
            return Message(
                id = command.messageId,
                chatId = 1L,
                senderId = command.senderId,
                senderName = command.senderName,
                avatar = command.avatar,
                text = command.text,
                timestamp = command.timestamp,
                isPending = command.isPending,
                roundIndex = command.roundIndex,
                issueId = command.issueId,
                stageId = command.stageId,
                executionRunId = command.executionRunId,
                participantSnapshotId = command.participantSnapshotId,
            ).also(messages::add)
        }

        override suspend fun updatePendingMessage(
            command: UpdatePendingDomainMessageCommand,
        ): Message {
            val index = messages.indexOfFirst { it.id == command.messageId }
            check(index >= 0)
            val current = messages[index]
            if (!current.isPending) return current
            return current.copy(text = command.text, isPending = command.keepPending)
                .also { messages[index] = it }
        }

        override suspend fun transitionRun(command: TransitionRunCommand): ExecutionRunEntity {
            val runtime = runtimes.getValue(command.runId)
            check(runtime.run.status in command.expectedStatuses)
            val updated = runtime.run.copy(
                status = command.newStatus,
                updatedAt = command.updatedAt,
                startedAt = command.startedAt ?: runtime.run.startedAt,
                finishedAt = command.finishedAt ?: runtime.run.finishedAt,
                stoppedAt = command.stoppedAt ?: runtime.run.stoppedAt,
                failureCode = command.failureCode,
                failureMessage = command.failureMessage,
            )
            runtimes[command.runId] = runtime.copy(run = updated)
            return updated
        }

        override suspend fun recoverInterrupted(
            command: RecoverInterruptedExecutionCommand,
        ): ExecutionRuntimeSnapshot {
            val runtime = runtimes.getValue(command.runId)
            val states = runtime.participantStates.map { state ->
                if (
                    state.status in setOf(
                        ExecutionParticipantStatus.RUNNING,
                        ExecutionParticipantStatus.STREAMING,
                    )
                ) {
                    state.copy(
                        status = ExecutionParticipantStatus.RETRYABLE,
                        lastErrorCode = ExecutionErrorCode.PROCESS_INTERRUPTED.storageValue,
                        finishedAt = command.updatedAt,
                        updatedAt = command.updatedAt,
                    )
                } else {
                    state
                }
            }
            return runtime.copy(
                run = runtime.run.copy(
                    status = ExecutionRunStatus.RETRYABLE,
                    finishedAt = command.updatedAt,
                    updatedAt = command.updatedAt,
                ),
                participantStates = states,
            ).also { runtimes[command.runId] = it }
        }
    }

    private companion object {
        const val ISSUE_ID = "issue-1"
        const val STAGE_ID = "stage-1"
    }
}
