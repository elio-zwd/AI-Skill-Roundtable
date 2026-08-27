package com.elio.jianyu.collaboration

import com.elio.jianyu.data.AppendDomainMessageCommand
import com.elio.jianyu.data.ContextUsageSnapshot
import com.elio.jianyu.data.ContextUsageWriteSet
import com.elio.jianyu.data.CreateCollaborationRetryCommand
import com.elio.jianyu.data.CreateCrossDiscussionResponseCommand
import com.elio.jianyu.data.CreateCrossDiscussionSynthesisCommand
import com.elio.jianyu.data.CreateDirectedInteractionCommand
import com.elio.jianyu.data.CreateStandardInteractionCommand
import com.elio.jianyu.data.CrossDiscussionSessionEntity
import com.elio.jianyu.data.CrossDiscussionStatus
import com.elio.jianyu.data.ExecutionHistoryScope
import com.elio.jianyu.data.ExecutionParticipantStatus
import com.elio.jianyu.data.ExecutionRunEntity
import com.elio.jianyu.data.ExecutionRunKind
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.ExecutionRuntimeSnapshot
import com.elio.jianyu.data.IssueRecoverySnapshot
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.StageCollaborationSnapshot
import com.elio.jianyu.data.TransitionCrossDiscussionCommand
import com.elio.jianyu.data.closeExecutionBudget
import com.elio.jianyu.data.createCollaborationRetry
import com.elio.jianyu.data.createCrossDiscussionResponse
import com.elio.jianyu.data.createCrossDiscussionSynthesis
import com.elio.jianyu.data.createDirectedInteraction
import com.elio.jianyu.data.createStandardInteraction
import com.elio.jianyu.data.getExecutionRuntime
import com.elio.jianyu.data.getStageCollaboration
import com.elio.jianyu.data.transitionCrossDiscussion
import com.elio.jianyu.execution.ExecutionContextContribution
import com.elio.jianyu.execution.ExecutionContextGate
import com.elio.jianyu.execution.ExecutionPreparedRunCommand
import com.elio.jianyu.execution.ExecutionRunCoordinator
import com.elio.jianyu.execution.ExecutionSkillResolver
import com.elio.jianyu.execution.ExecutionSkillSelection
import com.elio.jianyu.execution.ExecutionThinkingPolicyResolver
import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillExecutionEligibility
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

fun interface CollaborationClock {
    fun nowMillis(): Long
}

object SystemCollaborationClock : CollaborationClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}

data class IssueCollaborationSnapshot(
    val issue: IssueRecoverySnapshot,
    val roster: CollaborationRoster?,
    val collaboration: StageCollaborationSnapshot,
)

class OfficialCollaborationSkillEligibility(
    private val catalog: OfficialSkillCatalog,
    private val executionEligibility: OfficialSkillExecutionEligibility,
) {
    fun isExecutable(skillId: String): Boolean {
        val definition = catalog.findById(skillId) ?: return false
        return definition.availability.executable && executionEligibility.audit(definition).eligible
    }

    fun validateIntegrator(): CollaborationValidationResult {
        val definition = catalog.findById(SynthesisSkillEligibilityPolicy.DEFAULT_INTEGRATOR_SKILL_ID)
        val audit = definition?.let(executionEligibility::audit)
        return SynthesisSkillEligibilityPolicy.validate(definition, audit)
    }
}

/**
 * PR09-08 的薄协作编排层。
 *
 * 只负责配置校验、原子命令准备、调用唯一 [ExecutionRunCoordinator]、
 * 根据持久化状态收敛 Discussion；不访问 DAO、不调用 Retrofit、不实现第二套状态机。
 */
class IssueCollaborationCoordinator(
    private val repository: JianyuRepository,
    private val executionCoordinator: ExecutionRunCoordinator,
    private val integratorResolver: ExecutionSkillResolver,
    private val eligibility: OfficialCollaborationSkillEligibility,
    private val clock: CollaborationClock = SystemCollaborationClock,
    private val modelIdResolver: (String) -> String = { requestedModel -> requestedModel },
) {
    suspend fun recover(
        issueId: String,
        stageId: String,
    ): IssueCollaborationSnapshot {
        val issue = repository.recoverIssue(issueId).valueOrThrow()
        require(issue.core.stages.any { it.id == stageId && it.issueId == issueId })
        return IssueCollaborationSnapshot(
            issue = issue,
            roster = CurrentStageRosterPolicy.resolve(
                stageId = stageId,
                runs = issue.core.runs,
                participants = issue.core.participants,
            ),
            collaboration = repository.getStageCollaboration(stageId).valueOrThrow(),
        )
    }

    suspend fun startStandardFollowUp(
        request: StandardFollowUpRequest,
    ): CollaborationExecutionResult {
        val snapshot = recover(request.issueId, request.stageId)
        if (snapshot.issue.core.currentStage?.id != request.stageId) {
            throw CollaborationStateException("historical_stage_read_only")
        }
        val ids = CollaborationOperationIds.standard(request.operationId)
        existingStandardRuntime(ids.runId)?.let { existing ->
            return resumeStandardFollowUp(
                request = request,
                snapshot = snapshot,
                runtime = existing,
                ids = ids,
            )
        }
        val roster = snapshot.roster
        val executable = roster?.participants.orEmpty()
            .map { it.sourceId }
            .filterTo(mutableSetOf(), eligibility::isExecutable)
        requireValid(StandardFollowUpPolicy.validate(roster, executable))
        val currentRoster = requireNotNull(roster)
        val usage = rebindUsage(
            request.context.usage,
            request.issueId,
            request.stageId,
            ids.runId,
            request.userConfirmedAt,
        )
        requireContext(request.context.contributions, usage)
        val participants = currentRoster.participants
            .sortedBy { it.position }
            .mapIndexed { index, source ->
                source.copy(
                    id = "${ids.runId}-participant-$index",
                    runId = ids.runId,
                    position = index,
                    createdAt = request.userConfirmedAt,
                )
            }
        val run = ExecutionRunEntity(
            id = ids.runId,
            issueId = request.issueId,
            stageId = request.stageId,
            triggerMessageId = ids.userMessageId,
            idempotencyKey = ids.idempotencyKey,
            createdAt = request.userConfirmedAt,
            updatedAt = request.userConfirmedAt,
            runKind = ExecutionRunKind.STANDARD,
            historyScope = ExecutionHistoryScope.FULL_STAGE,
            actualModelId = modelIdResolver(request.model),
            actualThinkingLevel = ExecutionThinkingPolicyResolver.resolve(
                snapshot.issue.core.issue.defaultThinkingPolicy,
                request.thinkingOverride,
                ExecutionRunKind.STANDARD,
            ).level,
            thinkingLevelSource = ExecutionThinkingPolicyResolver.resolve(
                snapshot.issue.core.issue.defaultThinkingPolicy,
                request.thinkingOverride,
                ExecutionRunKind.STANDARD,
            ).source,
        )
        val created = repository.createStandardInteraction(
            CreateStandardInteractionCommand(
                userMessage = userMessage(
                    messageId = ids.userMessageId,
                    issueId = request.issueId,
                    stageId = request.stageId,
                    text = request.question,
                    timestamp = request.userConfirmedAt,
                    roundIndex = request.roundIndex,
                    sessionTitle = snapshot.issue.core.issue.title,
                ),
                run = run,
                participants = participants,
                budget = request.budget,
                contextUsage = usage,
            ),
        ).valueOrThrow()
        val executed = executionCoordinator.startPrepared(
            ExecutionPreparedRunCommand(
                runId = created.runtime.run.id,
                issueId = request.issueId,
                stageId = request.stageId,
                currentUserInput = request.question,
                roundIndex = request.roundIndex,
                userConfirmedAt = request.userConfirmedAt,
                model = request.model,
                searchMode = request.searchMode,
                contributions = request.context.contributions,
            ),
        )
        return CollaborationExecutionResult(executed.runtime)
    }

    private suspend fun existingStandardRuntime(runId: String): ExecutionRuntimeSnapshot? =
        when (val result = repository.getExecutionRuntime(runId)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> when (result.error) {
                is RepositoryError.NotFound -> null
                else -> throw CollaborationRepositoryException(result.error)
            }
        }

    private suspend fun resumeStandardFollowUp(
        request: StandardFollowUpRequest,
        snapshot: IssueCollaborationSnapshot,
        runtime: ExecutionRuntimeSnapshot,
        ids: StandardOperationIds,
    ): CollaborationExecutionResult {
        val run = runtime.run
        val trigger = snapshot.issue.core.messages.singleOrNull { it.id == run.triggerMessageId }
        val replayMatches = run.id == ids.runId &&
            run.issueId == request.issueId &&
            run.stageId == request.stageId &&
            run.idempotencyKey == ids.idempotencyKey &&
            run.triggerMessageId == ids.userMessageId &&
            run.runKind == ExecutionRunKind.STANDARD &&
            run.historyScope == ExecutionHistoryScope.FULL_STAGE &&
            run.parentRunId == null &&
            run.retryOfRunId == null &&
            run.discussionId == null &&
            trigger != null &&
            trigger.issueId == request.issueId &&
            trigger.stageId == request.stageId &&
            trigger.senderId == "user" &&
            trigger.text == request.question
        if (!replayMatches) {
            throw CollaborationStateException("standard_replay_conflict")
        }
        val contributions = repository.listRunContextUsage(run.id)
            .valueOrThrow()
            .map { snapshot ->
                toContribution(snapshot)
                    ?: throw CollaborationStateException("standard_replay_context_unavailable")
            }
        val executed = executionCoordinator.startPrepared(
            ExecutionPreparedRunCommand(
                runId = run.id,
                issueId = run.issueId,
                stageId = run.stageId,
                currentUserInput = requireNotNull(trigger).text,
                roundIndex = trigger.roundIndex,
                userConfirmedAt = run.createdAt,
                model = request.model,
                searchMode = request.searchMode,
                contributions = contributions,
            ),
        )
        return CollaborationExecutionResult(executed.runtime)
    }

    suspend fun startDirected(
        request: DirectedResponseRequest,
    ): CollaborationExecutionResult {
        val snapshot = recover(request.issueId, request.stageId)
        val validation = DirectedResponsePolicy.validate(
            roster = snapshot.roster,
            selectedSkillIds = listOf(request.selectedSkillId),
            executableSkillIds = setOfNotNull(
                request.selectedSkillId.takeIf(eligibility::isExecutable),
            ),
        )
        requireValid(validation)
        val ids = CollaborationOperationIds.directed(request.operationId)
        val usage = rebindUsage(
            request.context.usage,
            request.issueId,
            request.stageId,
            ids.runId,
            request.userConfirmedAt,
        )
        requireContext(request.context.contributions, usage)
        val source = requireNotNull(snapshot.roster)
            .participants.single { it.sourceId == request.selectedSkillId }
        val participant = source.copy(
            id = "${ids.runId}-participant-0",
            runId = ids.runId,
            position = 0,
            createdAt = request.userConfirmedAt,
        )
        val run = ExecutionRunEntity(
            id = ids.runId,
            issueId = request.issueId,
            stageId = request.stageId,
            triggerMessageId = ids.userMessageId,
            idempotencyKey = ids.idempotencyKey,
            createdAt = request.userConfirmedAt,
            updatedAt = request.userConfirmedAt,
            runKind = ExecutionRunKind.DIRECTED_RESPONSE,
            historyScope = historyScope(request.context.selectedMessageIds),
            actualModelId = modelIdResolver(request.model),
            actualThinkingLevel = ExecutionThinkingPolicyResolver.resolve(
                snapshot.issue.core.issue.defaultThinkingPolicy,
                request.thinkingOverride,
                ExecutionRunKind.DIRECTED_RESPONSE,
            ).level,
            thinkingLevelSource = ExecutionThinkingPolicyResolver.resolve(
                snapshot.issue.core.issue.defaultThinkingPolicy,
                request.thinkingOverride,
                ExecutionRunKind.DIRECTED_RESPONSE,
            ).source,
        )
        val created = repository.createDirectedInteraction(
            CreateDirectedInteractionCommand(
                userMessage = userMessage(
                    messageId = ids.userMessageId,
                    issueId = request.issueId,
                    stageId = request.stageId,
                    text = request.question,
                    timestamp = request.userConfirmedAt,
                    roundIndex = request.roundIndex,
                    sessionTitle = snapshot.issue.core.issue.title,
                ),
                run = run,
                participant = participant,
                budget = request.budget,
                contextUsage = usage,
                selectedMessageIds = request.context.selectedMessageIds,
            ),
        ).valueOrThrow()
        val executed = executionCoordinator.startPrepared(
            ExecutionPreparedRunCommand(
                runId = created.runtime.run.id,
                issueId = request.issueId,
                stageId = request.stageId,
                currentUserInput = request.question,
                roundIndex = request.roundIndex,
                userConfirmedAt = request.userConfirmedAt,
                model = request.model,
                searchMode = request.searchMode,
                contributions = request.context.contributions,
            ),
        )
        return CollaborationExecutionResult(executed.runtime)
    }

    suspend fun startCrossDiscussion(
        request: CrossDiscussionRequest,
    ): CollaborationExecutionResult {
        val snapshot = recover(request.issueId, request.stageId)
        val executable = request.selectedSkillIds.filterTo(mutableSetOf(), eligibility::isExecutable)
        val integratorValidation = eligibility.validateIntegrator()
        val validation = CrossDiscussionPolicy.validate(
            roster = snapshot.roster,
            selectedSkillIds = request.selectedSkillIds,
            executableSkillIds = executable,
            integratorSkillId = SynthesisSkillEligibilityPolicy.DEFAULT_INTEGRATOR_SKILL_ID,
            integratorExecutable = integratorValidation.valid,
        )
        requireValid(validation)
        requireValid(integratorValidation)

        val ids = CollaborationOperationIds.crossResponse(request.operationId)
        val usage = rebindUsage(
            request.context.usage,
            request.issueId,
            request.stageId,
            ids.runId,
            request.userConfirmedAt,
        )
        requireContext(request.context.contributions, usage)
        val selectedIds = request.selectedSkillIds.toSet()
        val participants = requireNotNull(snapshot.roster)
            .participants
            .filter { it.sourceId in selectedIds }
            .sortedBy { it.position }
            .mapIndexed { index, source ->
                source.copy(
                    id = "${ids.runId}-participant-$index",
                    runId = ids.runId,
                    position = index,
                    createdAt = request.userConfirmedAt,
                )
            }
        val run = ExecutionRunEntity(
            id = ids.runId,
            issueId = request.issueId,
            stageId = request.stageId,
            triggerMessageId = ids.userMessageId,
            idempotencyKey = ids.runIdempotencyKey,
            createdAt = request.userConfirmedAt,
            updatedAt = request.userConfirmedAt,
            runKind = ExecutionRunKind.CROSS_DISCUSSION_RESPONSE,
            discussionId = ids.discussionId,
            historyScope = historyScope(request.context.selectedMessageIds),
            actualModelId = modelIdResolver(request.model),
            actualThinkingLevel = ExecutionThinkingPolicyResolver.resolve(
                snapshot.issue.core.issue.defaultThinkingPolicy,
                request.thinkingOverride,
                ExecutionRunKind.CROSS_DISCUSSION_RESPONSE,
            ).level,
            thinkingLevelSource = ExecutionThinkingPolicyResolver.resolve(
                snapshot.issue.core.issue.defaultThinkingPolicy,
                request.thinkingOverride,
                ExecutionRunKind.CROSS_DISCUSSION_RESPONSE,
            ).source,
        )
        val session = CrossDiscussionSessionEntity(
            id = ids.discussionId,
            issueId = request.issueId,
            stageId = request.stageId,
            triggerMessageId = ids.userMessageId,
            responseRunId = ids.runId,
            integratorSkillId = SynthesisSkillEligibilityPolicy.DEFAULT_INTEGRATOR_SKILL_ID,
            status = CrossDiscussionStatus.RESPONDING,
            idempotencyKey = ids.discussionIdempotencyKey,
            createdAt = request.userConfirmedAt,
            updatedAt = request.userConfirmedAt,
        )
        val created = repository.createCrossDiscussionResponse(
            CreateCrossDiscussionResponseCommand(
                userMessage = userMessage(
                    messageId = ids.userMessageId,
                    issueId = request.issueId,
                    stageId = request.stageId,
                    text = request.focus,
                    timestamp = request.userConfirmedAt,
                    roundIndex = request.roundIndex,
                    sessionTitle = snapshot.issue.core.issue.title,
                ),
                session = session,
                run = run,
                participants = participants,
                budget = request.budget,
                contextUsage = usage,
                selectedMessageIds = request.context.selectedMessageIds,
            ),
        ).valueOrThrow()
        val executed = executionCoordinator.startPrepared(
            ExecutionPreparedRunCommand(
                runId = created.runtime.run.id,
                issueId = request.issueId,
                stageId = request.stageId,
                currentUserInput = request.focus,
                roundIndex = request.roundIndex,
                userConfirmedAt = request.userConfirmedAt,
                model = request.model,
                searchMode = request.searchMode,
                contributions = request.context.contributions,
                keepBudgetOpenOnSuccess = true,
            ),
        )
        val updatedSession = updateResponseDiscussion(
            discussion = requireNotNull(created.discussion),
            runtime = executed.runtime,
        )
        if (
            updatedSession.status == CrossDiscussionStatus.AWAITING_SYNTHESIS &&
            request.autoStartSynthesisOnFullSuccess
        ) {
            return startSynthesis(
                CrossDiscussionSynthesisRequest(
                    operationId = derivedOperationId("synthesis", request.operationId),
                    issueId = request.issueId,
                    stageId = request.stageId,
                    sessionId = updatedSession.id,
                    focus = request.focus,
                    roundIndex = request.roundIndex + 1,
                    userConfirmedAt = clock.nowMillis(),
                    userAcceptedPartial = false,
                    context = request.context,
                    model = request.model,
                    searchMode = request.searchMode,
                ),
            )
        }
        return CollaborationExecutionResult(executed.runtime, updatedSession)
    }

    suspend fun startSynthesis(
        request: CrossDiscussionSynthesisRequest,
    ): CollaborationExecutionResult {
        val snapshot = recover(request.issueId, request.stageId)
        val session = snapshot.collaboration.discussions.singleOrNull { it.id == request.sessionId }
            ?: throw CollaborationStateException("cross_discussion_not_found")
        if (session.issueId != request.issueId || session.stageId != request.stageId) {
            throw CollaborationStateException("cross_discussion_scope_mismatch")
        }
        if (!CrossDiscussionProgressPolicy.canCreateSynthesis(
                session.status,
                request.userAcceptedPartial,
            )
        ) {
            throw CollaborationStateException("cross_discussion_not_ready_for_synthesis")
        }
        requireValid(eligibility.validateIntegrator())
        val ids = CollaborationOperationIds.crossSynthesis(request.operationId)
        val integrator = integratorResolver.resolve(
            runId = ids.runId,
            selections = listOf(
                ExecutionSkillSelection(
                    officialSkillId = session.integratorSkillId,
                    defaultResponsibility = "透明整合本次交叉讨论，保留共识、分歧与适用条件。",
                ),
            ),
            createdAt = request.userConfirmedAt,
        ).single()
        val usage = rebindUsage(
            request.context.usage,
            request.issueId,
            request.stageId,
            ids.runId,
            request.userConfirmedAt,
        )
        requireContext(request.context.contributions, usage)
        val responseRun = repository.getExecutionRuntime(session.responseRunId).valueOrThrow().run
        val run = ExecutionRunEntity(
            id = ids.runId,
            issueId = request.issueId,
            stageId = request.stageId,
            triggerMessageId = session.triggerMessageId,
            idempotencyKey = ids.idempotencyKey,
            createdAt = request.userConfirmedAt,
            updatedAt = request.userConfirmedAt,
            runKind = ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS,
            parentRunId = session.responseRunId,
            discussionId = session.id,
            historyScope = ExecutionHistoryScope.EXPLICIT_MESSAGES,
            actualModelId = requireNotNull(responseRun.actualModelId),
            actualThinkingLevel = requireNotNull(responseRun.actualThinkingLevel),
            thinkingLevelSource = requireNotNull(responseRun.thinkingLevelSource),
        )
        val created = repository.createCrossDiscussionSynthesis(
            CreateCrossDiscussionSynthesisCommand(
                sessionId = session.id,
                run = run,
                participant = integrator,
                contextUsage = usage,
                additionalSelectedMessageIds = request.context.selectedMessageIds,
                userAcceptedPartial = request.userAcceptedPartial,
                createdAt = request.userConfirmedAt,
            ),
        ).valueOrThrow()
        val executed = executionCoordinator.startPrepared(
            ExecutionPreparedRunCommand(
                runId = created.runtime.run.id,
                issueId = request.issueId,
                stageId = request.stageId,
                currentUserInput = request.focus,
                roundIndex = request.roundIndex,
                userConfirmedAt = request.userConfirmedAt,
                model = request.model,
                searchMode = request.searchMode,
                contributions = request.context.contributions,
            ),
        )
        val updatedSession = updateSynthesisDiscussion(
            discussion = requireNotNull(created.discussion),
            runtime = executed.runtime,
        )
        return CollaborationExecutionResult(executed.runtime, updatedSession)
    }

    suspend fun retry(
        request: CollaborationRetryRequest,
    ): CollaborationExecutionResult {
        val ids = CollaborationOperationIds.retry(request.operationId)
        val previous = repository.getExecutionRuntime(request.previousRunId).valueOrThrow().run
        val issue = repository.recoverIssue(previous.issueId).valueOrThrow().core.issue
        val thinking = ExecutionThinkingPolicyResolver.resolve(
            issueDefault = issue.defaultThinkingPolicy,
            roundOverride = request.thinkingOverride,
            runKind = previous.runKind,
        )
        val created = repository.createCollaborationRetry(
            CreateCollaborationRetryCommand(
                previousRunId = request.previousRunId,
                newRunId = ids.runId,
                idempotencyKey = ids.idempotencyKey,
                createdAt = request.userConfirmedAt,
                actualModelId = modelIdResolver(request.model),
                actualThinkingLevel = thinking.level,
                thinkingLevelSource = thinking.source,
            ),
        ).valueOrThrow()
        val contributions = repository.listRunContextUsage(created.runtime.run.id)
            .valueOrThrow()
            .mapNotNull(::toContribution)
        val executed = executionCoordinator.startPrepared(
            ExecutionPreparedRunCommand(
                runId = created.runtime.run.id,
                issueId = created.runtime.run.issueId,
                stageId = created.runtime.run.stageId,
                currentUserInput = request.currentUserInput,
                roundIndex = request.roundIndex,
                userConfirmedAt = request.userConfirmedAt,
                model = request.model,
                searchMode = request.searchMode,
                contributions = contributions,
                keepBudgetOpenOnSuccess =
                    created.runtime.run.runKind == ExecutionRunKind.CROSS_DISCUSSION_RESPONSE,
            ),
        )
        val discussion = created.discussion?.let { current ->
            when (executed.runtime.run.runKind) {
                ExecutionRunKind.CROSS_DISCUSSION_RESPONSE ->
                    updateResponseDiscussion(current, executed.runtime)
                ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS ->
                    updateSynthesisDiscussion(current, executed.runtime)
                else -> current
            }
        }
        return CollaborationExecutionResult(executed.runtime, discussion)
    }

    suspend fun stop(
        runId: String,
    ): CollaborationExecutionResult {
        val stopped = executionCoordinator.stop(runId)
        val discussionId = stopped.run.discussionId ?: return CollaborationExecutionResult(stopped)
        val snapshot = repository.getStageCollaboration(stopped.run.stageId).valueOrThrow()
        val session = snapshot.discussions.single { it.id == discussionId }
        val updated = repository.transitionCrossDiscussion(
            TransitionCrossDiscussionCommand(
                sessionId = session.id,
                expectedStatuses = setOf(session.status, CrossDiscussionStatus.STOPPED),
                newStatus = CrossDiscussionStatus.STOPPED,
                synthesisRunId = session.synthesisRunId,
                successfulParticipantIds = parseIds(session.successfulParticipantIdsJson),
                failedParticipantIds = parseIds(session.failedParticipantIdsJson),
                updatedAt = clock.nowMillis(),
                failureCode = "user_stopped",
            ),
        ).valueOrThrow()
        repository.closeExecutionBudget(stopped.budget.rootRunId, clock.nowMillis()).valueOrThrow()
        return CollaborationExecutionResult(stopped, updated)
    }

    private suspend fun updateResponseDiscussion(
        discussion: CrossDiscussionSessionEntity,
        runtime: ExecutionRuntimeSnapshot,
    ): CrossDiscussionSessionEntity {
        val participantsById = runtime.participants.associateBy { it.id }
        val currentSuccess = runtime.participantStates
            .filter { it.status == ExecutionParticipantStatus.SUCCEEDED }
            .mapNotNull { participantsById[it.participantSnapshotId]?.sourceId }
        val currentFailed = runtime.participantStates
            .filter { it.status != ExecutionParticipantStatus.SUCCEEDED }
            .mapNotNull { participantsById[it.participantSnapshotId]?.sourceId }
        val successful = (
            parseIds(discussion.successfulParticipantIdsJson) + currentSuccess
            ).distinct().filterNot { it in currentFailed }
        val failed = currentFailed.distinct().filterNot { it in successful }
        val target = when {
            runtime.run.status == ExecutionRunStatus.STOPPED -> CrossDiscussionStatus.STOPPED
            successful.isEmpty() -> CrossDiscussionStatus.FAILED
            failed.isEmpty() -> CrossDiscussionStatus.AWAITING_SYNTHESIS
            else -> CrossDiscussionStatus.PARTIAL_SUCCESS
        }
        return repository.transitionCrossDiscussion(
            TransitionCrossDiscussionCommand(
                sessionId = discussion.id,
                expectedStatuses = setOf(discussion.status, target),
                newStatus = target,
                successfulParticipantIds = successful,
                failedParticipantIds = failed,
                updatedAt = clock.nowMillis(),
                failureCode = runtime.run.failureCode,
            ),
        ).valueOrThrow()
    }

    private suspend fun updateSynthesisDiscussion(
        discussion: CrossDiscussionSessionEntity,
        runtime: ExecutionRuntimeSnapshot,
    ): CrossDiscussionSessionEntity {
        val target = when (runtime.run.status) {
            ExecutionRunStatus.SUCCEEDED -> CrossDiscussionStatus.SUCCEEDED
            ExecutionRunStatus.STOPPED -> CrossDiscussionStatus.STOPPED
            ExecutionRunStatus.RETRYABLE,
            ExecutionRunStatus.PARTIAL_SUCCESS -> CrossDiscussionStatus.SYNTHESIS_RETRYABLE
            ExecutionRunStatus.FAILED -> CrossDiscussionStatus.FAILED
            ExecutionRunStatus.NOT_STARTED,
            ExecutionRunStatus.RUNNING -> CrossDiscussionStatus.SYNTHESIZING
            ExecutionRunStatus.COMPLETED -> CrossDiscussionStatus.SUCCEEDED
        }
        return repository.transitionCrossDiscussion(
            TransitionCrossDiscussionCommand(
                sessionId = discussion.id,
                expectedStatuses = setOf(discussion.status, target),
                newStatus = target,
                synthesisRunId = runtime.run.id,
                successfulParticipantIds = parseIds(discussion.successfulParticipantIdsJson),
                failedParticipantIds = parseIds(discussion.failedParticipantIdsJson),
                updatedAt = clock.nowMillis(),
                failureCode = runtime.run.failureCode,
            ),
        ).valueOrThrow()
    }

    private fun userMessage(
        messageId: Long,
        issueId: String,
        stageId: String,
        text: String,
        timestamp: Long,
        roundIndex: Int,
        sessionTitle: String,
    ) = AppendDomainMessageCommand(
        messageId = messageId,
        issueId = issueId,
        stageId = stageId,
        executionRunId = null,
        participantSnapshotId = null,
        senderId = "user",
        senderName = "你",
        avatar = "我",
        text = text,
        timestamp = timestamp,
        isPending = false,
        roundIndex = roundIndex,
        compatibilitySessionTitle = sessionTitle,
    )

    private fun historyScope(messageIds: List<Long>): ExecutionHistoryScope =
        if (messageIds.isEmpty()) ExecutionHistoryScope.NO_HISTORY
        else ExecutionHistoryScope.EXPLICIT_MESSAGES

    private fun rebindUsage(
        source: ContextUsageWriteSet,
        issueId: String,
        stageId: String,
        runId: String,
        createdAt: Long,
    ): ContextUsageWriteSet = ContextUsageWriteSet(
        materials = source.materials.mapIndexed { index, usage ->
            usage.copy(
                id = "$runId-material-usage-$index",
                issueId = issueId,
                stageId = stageId,
                runId = runId,
                createdAt = createdAt,
            )
        },
        personalContexts = source.personalContexts.mapIndexed { index, usage ->
            usage.copy(
                id = "$runId-personal-usage-$index",
                issueId = issueId,
                stageId = stageId,
                runId = runId,
                createdAt = createdAt,
            )
        },
        sourceExpectations = source.sourceExpectations,
    )

    private fun requireContext(
        contributions: List<ExecutionContextContribution>,
        usage: ContextUsageWriteSet,
    ) {
        ExecutionContextGate.validate(contributions, usage)?.let { failure ->
            throw CollaborationStateException(failure.code.storageValue)
        }
    }

    private fun requireValid(result: CollaborationValidationResult) {
        if (!result.valid) throw CollaborationStateException(result.code.name.lowercase())
    }

    private fun toContribution(snapshot: ContextUsageSnapshot): ExecutionContextContribution? {
        val content = snapshot.content ?: return null
        val hash = snapshot.contentHash ?: return null
        return ExecutionContextContribution(
            sourceId = snapshot.sourceId ?: "snapshot-${hash.take(20)}",
            sourceType = snapshot.sourceType.storageValue,
            content = content,
            contentHash = hash,
            userConfirmedAt = snapshot.userConfirmedAt,
            networkAllowed = snapshot.networkAllowed,
            sensitive = snapshot.sensitive,
        )
    }

    private fun parseIds(value: String): List<String> = runCatching {
        Json.decodeFromString<List<String>>(value)
    }.getOrDefault(emptyList())

    private fun derivedOperationId(prefix: String, source: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return "$prefix-${digest.take(32)}"
    }
}

class CollaborationStateException(
    val code: String,
) : IllegalStateException("Collaboration operation failed: $code")

class CollaborationRepositoryException(
    val error: RepositoryError,
) : IllegalStateException("Collaboration repository operation failed")

private fun <T> RepositoryResult<T>.valueOrThrow(): T = when (this) {
    is RepositoryResult.Success -> value
    is RepositoryResult.Failure -> throw CollaborationRepositoryException(error)
}
