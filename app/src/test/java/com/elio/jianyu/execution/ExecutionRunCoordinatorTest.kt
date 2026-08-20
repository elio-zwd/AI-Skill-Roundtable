package com.elio.jianyu.execution

import com.elio.jianyu.data.AppendDomainMessageCommand
import com.elio.jianyu.data.CreateExecutionRuntimeCommand
import com.elio.jianyu.data.ExecutionParticipantSnapshotEntity
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
import com.elio.jianyu.data.RecordExecutionApiCallCommand
import com.elio.jianyu.data.StageEntity
import com.elio.jianyu.data.TransitionExecutionParticipantCommand
import com.elio.jianyu.data.TransitionRunCommand
import com.elio.jianyu.data.UpdatePendingDomainMessageCommand
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionRunCoordinatorTest {
    @Test
    fun singleSkillStreamsInPlaceAndSucceeds() = runBlocking {
        val persistence = FakeExecutionPersistence()
        val network = FakeExecutionNetworkGateway(
            mutableMapOf("skill-a" to FakeOutcome.Success(listOf("A", "Answer"))),
        )

        val result = coordinator(persistence, network).start(
            startCommand("run-1", "skill-a"),
        )

        assertEquals(ExecutionRunStatus.SUCCEEDED, result.runtime.run.status)
        assertEquals(listOf("skill-a"), network.calls)
        assertEquals(1, persistence.messages.size)
        assertEquals("Answer", persistence.messages.single().text)
        assertFalse(persistence.messages.single().isPending)
        assertEquals(1, result.runtime.budget.usedApiCalls)
        assertEquals(
            ExecutionParticipantStatus.SUCCEEDED,
            result.runtime.participantStates.single().status,
        )
    }

    @Test
    fun multipleSkillsExecuteInFrozenOrder() = runBlocking {
        val persistence = FakeExecutionPersistence()
        val network = FakeExecutionNetworkGateway(
            mutableMapOf(
                "skill-a" to FakeOutcome.Success(listOf("A")),
                "skill-b" to FakeOutcome.Success(listOf("B")),
                "skill-c" to FakeOutcome.Success(listOf("C")),
            ),
        )

        val result = coordinator(persistence, network).start(
            startCommand("run-1", "skill-a", "skill-b", "skill-c"),
        )

        assertEquals(listOf("skill-a", "skill-b", "skill-c"), network.calls)
        assertEquals(ExecutionRunStatus.SUCCEEDED, result.runtime.run.status)
        assertEquals(3, persistence.messages.size)
    }

    @Test
    fun searchModeIsFrozenIntoEachNetworkRequest() = runBlocking {
        val persistence = FakeExecutionPersistence()
        val network = FakeExecutionNetworkGateway(
            mutableMapOf("skill-a" to FakeOutcome.Success(listOf("A"))),
        )

        coordinator(persistence, network).start(
            startCommand("run-1", "skill-a").copy(searchMode = SearchMode.OFF),
        )

        assertEquals(SearchMode.OFF, network.requests.single().searchMode)
    }

    @Test
    fun partialSuccessKeepsSuccessfulMessageAndMarksRunRetryable() = runBlocking {
        val persistence = FakeExecutionPersistence()
        val network = FakeExecutionNetworkGateway(
            mutableMapOf(
                "skill-a" to FakeOutcome.Success(listOf("A answer")),
                "skill-b" to FakeOutcome.Failure(IOException("offline")),
            ),
        )

        val result = coordinator(persistence, network).start(
            startCommand("run-1", "skill-a", "skill-b"),
        )

        assertEquals(ExecutionRunStatus.RETRYABLE, result.runtime.run.status)
        assertEquals(ExecutionParticipantStatus.SUCCEEDED, result.runtime.participantStates[0].status)
        assertEquals(ExecutionParticipantStatus.RETRYABLE, result.runtime.participantStates[1].status)
        assertEquals("A answer", persistence.messages.first().text)
        assertFalse(persistence.messages.first().isPending)
        assertEquals(ExecutionErrorCode.OFFLINE, result.participantFailures.values.single().code)
    }

    @Test
    fun noApiKeyDoesNotCreatePendingMessage() = runBlocking {
        val persistence = FakeExecutionPersistence()
        val network = FakeExecutionNetworkGateway(
            mutableMapOf("skill-a" to FakeOutcome.NoKey),
        )

        val result = coordinator(persistence, network).start(
            startCommand("run-1", "skill-a"),
        )

        assertTrue(persistence.messages.isEmpty())
        assertEquals(ExecutionRunStatus.RETRYABLE, result.runtime.run.status)
        assertEquals(
            ExecutionErrorCode.NO_API_KEY,
            result.participantFailures.values.single().code,
        )
    }

    @Test
    fun childRunRetriesOnlyFailedMemberAndKeepsOriginalHistory() = runBlocking {
        val persistence = FakeExecutionPersistence()
        val outcomes = mutableMapOf<String, FakeOutcome>(
            "skill-a" to FakeOutcome.Success(listOf("A answer")),
            "skill-b" to FakeOutcome.Failure(IOException("offline")),
        )
        val network = FakeExecutionNetworkGateway(outcomes)
        val coordinator = coordinator(persistence, network)

        coordinator.start(startCommand("run-1", "skill-a", "skill-b"))
        outcomes["skill-b"] = FakeOutcome.Success(listOf("B answer"))
        val retryResult = coordinator.retry(
            ExecutionRetryCommand(
                previousRunId = "run-1",
                newRunId = "run-2",
                idempotencyKey = "retry-command-1",
                currentUserInput = "再次回答",
                roundIndex = 2,
                userConfirmedAt = 2_000L,
            ),
        )

        assertEquals(listOf("skill-a", "skill-b", "skill-b"), network.calls)
        assertEquals(ExecutionRunStatus.SUCCEEDED, retryResult.runtime.run.status)
        assertEquals("run-1", retryResult.runtime.run.retryOfRunId)
        assertEquals(1, persistence.messages.count { it.senderId == "skill-a" })
        assertEquals(2, persistence.messages.count { it.senderId == "skill-b" })
        assertEquals(1, persistence.messages.count { it.text == "B answer" })
        assertEquals("run-1", retryResult.runtime.budget.rootRunId)
    }

    @Test
    fun stopPersistsBeforeCancellationAndRejectsLateText() = runBlocking {
        val persistence = FakeExecutionPersistence()
        val started = CompletableDeferred<Unit>()
        val network = FakeExecutionNetworkGateway(
            mutableMapOf("skill-a" to FakeOutcome.Blocking(started)),
        )
        val coordinator = coordinator(persistence, network)

        val running = async(Dispatchers.Default) {
            coordinator.start(startCommand("run-1", "skill-a"))
        }
        started.await()
        val stopped = coordinator.stop("run-1")
        runCatching { running.await() }

        assertEquals(ExecutionRunStatus.STOPPED, stopped.run.status)
        assertEquals(ExecutionParticipantStatus.STOPPED, stopped.participantStates.single().status)
        assertEquals("partial", persistence.messages.single().text)
        assertFalse(persistence.messages.single().isPending)
        assertFalse(persistence.messages.single().text.contains("late"))
    }

    private fun coordinator(
        persistence: FakeExecutionPersistence,
        network: FakeExecutionNetworkGateway,
    ): ExecutionRunCoordinator {
        val resolver = ExecutionSkillResolver { runId, selections, createdAt ->
            selections.mapIndexed { index, selection ->
                ExecutionParticipantSnapshotEntity(
                    id = "$runId-participant-$index",
                    runId = runId,
                    sourceType = "official_skill",
                    sourceId = selection.officialSkillId,
                    displayName = selection.officialSkillId,
                    avatar = "A",
                    skillAssetPath = "skills/${selection.officialSkillId}/SKILL.md",
                    systemPrompt = "独立分析",
                    configurationJson = "{}",
                    defaultResponsibility = selection.defaultResponsibility,
                    position = index,
                    createdAt = createdAt,
                )
            }
        }
        return ExecutionRunCoordinator(
            persistence = persistence,
            skillResolver = resolver,
            networkGateway = network,
            clock = ExecutionClock { persistence.nextTime() },
        )
    }

    private fun startCommand(runId: String, vararg skillIds: String): ExecutionStartCommand =
        ExecutionStartCommand(
            runId = runId,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            triggerMessageId = null,
            idempotencyKey = "command-$runId",
            selections = skillIds.map(::ExecutionSkillSelection),
            currentUserInput = "请分析",
            roundIndex = 1,
            userConfirmedAt = 1_000L,
        )

    private sealed interface FakeOutcome {
        data class Success(val chunks: List<String>) : FakeOutcome
        data class Failure(val error: Throwable) : FakeOutcome
        data object NoKey : FakeOutcome
        data class Blocking(val started: CompletableDeferred<Unit>) : FakeOutcome
    }

    private class FakeExecutionNetworkGateway(
        val outcomes: MutableMap<String, FakeOutcome>,
    ) : ExecutionNetworkGateway {
        val calls = mutableListOf<String>()
        val requests = mutableListOf<ExecutionNetworkRequest>()

        override suspend fun prepare(request: ExecutionNetworkRequest): PreparedExecutionNetworkCall {
            requests += request
            val outcome = outcomes.getValue(request.participant.sourceId)
            if (outcome == FakeOutcome.NoKey) throw NoExecutionApiKeyException()
            return PreparedExecutionNetworkCall { onAttemptStarted, onTextUpdate ->
                calls += request.participant.sourceId
                onAttemptStarted()
                when (outcome) {
                    is FakeOutcome.Success -> {
                        outcome.chunks.forEach { onTextUpdate(it) }
                        ExecutionNetworkResult("interaction", outcome.chunks.last())
                    }
                    is FakeOutcome.Failure -> throw outcome.error
                    is FakeOutcome.Blocking -> {
                        onTextUpdate("partial")
                        outcome.started.complete(Unit)
                        try {
                            CompletableDeferred<Unit>().await()
                        } finally {
                            withContext(NonCancellable) {
                                runCatching { onTextUpdate("partial late") }
                            }
                        }
                        error("unreachable")
                    }
                    FakeOutcome.NoKey -> error("NoKey must fail during prepare")
                }
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

        override suspend fun recordApiCall(
            command: RecordExecutionApiCallCommand,
        ): ExecutionRunBudgetEntity {
            val current = budgets.getValue(command.rootRunId)
            if (current.closed) {
                throw ExecutionRepositoryException(
                    RepositoryError.InvalidState("record_execution_api_call", "budget_closed"),
                )
            }
            return current.copy(
                usedApiCalls = current.usedApiCalls + command.count,
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
                if (state.status in setOf(
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
