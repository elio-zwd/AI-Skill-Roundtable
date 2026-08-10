package com.elio.jianyu.data

import com.elio.jianyu.lifecycle.IssueWriteAction

/**
 * 集中执行生命周期最终写入门禁，使公共 Repository 继续保持纯门面。
 *
 * 停止和失败等终态收敛操作不经过普通业务写入门禁，避免归档或 Purge 阻止任务安全结束。
 */
internal class LifecycleGatedRepositoryComponent(
    private val writeGate: IssueLifecycleWriteGate,
    private val issueExecution: IssueExecutionRepositoryComponent,
    private val stageAdvancement: StageAdvancementRepositoryComponent,
    private val executionRuntime: ExecutionRuntimeRepositoryComponent,
    private val collaborationRuntime: CollaborationRuntimeRepositoryComponent,
    private val collaboration: CollaborationRepositoryComponent,
    private val crossDiscussionSynthesis: CrossDiscussionSynthesisRepositoryComponent,
    private val collaborationRetry: CollaborationRetryRepositoryComponent,
    private val pendingMessages: PendingMessageRepositoryComponent,
    private val resources: ResourceRepositoryComponent,
    private val usages: UsageRepositoryComponent,
    private val materialContext: MaterialContextRepositoryComponent,
    private val lifecycleRecovery: LifecycleRecoveryRepositoryComponent,
) {
    suspend fun createStage(command: CreateStageCommand): RepositoryResult<StageEntity> =
        gate(command.issueId, IssueWriteAction.ADVANCE_STAGE, "create_stage") {
            issueExecution.createStage(command)
        }

    suspend fun undoLatestUnrunStage(issueId: String, stageId: String): RepositoryResult<Unit> =
        gate(issueId, IssueWriteAction.ADVANCE_STAGE, "undo_latest_stage") {
            stageAdvancement.undoLatestUnrunStage(issueId, stageId)
        }

    suspend fun advanceIssue(command: AdvanceIssueCommand): RepositoryResult<AdvanceIssueResult> =
        gate(command.issueId, IssueWriteAction.ADVANCE_STAGE, "advance_issue") {
            stageAdvancement.advanceIssue(command)
        }

    suspend fun createExecutionRun(
        command: CreateExecutionRunCommand,
    ): RepositoryResult<ExecutionRunSnapshot> =
        gate(command.run.issueId, IssueWriteAction.CREATE_RUN, "create_execution_run") {
            issueExecution.createExecutionRun(command)
        }

    suspend fun createExecutionRuntime(
        command: CreateExecutionRuntimeCommand,
    ): RepositoryResult<ExecutionRuntimeSnapshot> =
        gate(command.run.issueId, IssueWriteAction.CREATE_RUN, "create_execution_runtime") {
            executionRuntime.createExecutionRuntime(command)
        }

    suspend fun recoverInterruptedExecution(
        command: RecoverInterruptedExecutionCommand,
    ): RepositoryResult<ExecutionRuntimeSnapshot> =
        gateRun(command.runId, IssueWriteAction.CREATE_RUN, "recover_interrupted_execution") {
            collaborationRuntime.recoverInterruptedExecution(command)
        }

    suspend fun createStandardInteraction(
        command: CreateStandardInteractionCommand,
    ): RepositoryResult<CollaborationStartResult> =
        gate(
            command.userMessage.issueId,
            IssueWriteAction.CREATE_RUN,
            "create_standard_interaction",
        ) {
            collaboration.createStandardInteraction(command)
        }

    suspend fun createDirectedInteraction(
        command: CreateDirectedInteractionCommand,
    ): RepositoryResult<CollaborationStartResult> =
        gate(command.userMessage.issueId, IssueWriteAction.DIRECTED_RESPONSE, "create_directed_interaction") {
            collaboration.createDirectedInteraction(command)
        }

    suspend fun createCrossDiscussionResponse(
        command: CreateCrossDiscussionResponseCommand,
    ): RepositoryResult<CollaborationStartResult> =
        gate(command.userMessage.issueId, IssueWriteAction.CROSS_DISCUSSION, "create_cross_discussion_response") {
            collaboration.createCrossDiscussionResponse(command)
        }

    suspend fun createCrossDiscussionSynthesis(
        command: CreateCrossDiscussionSynthesisCommand,
    ): RepositoryResult<CollaborationStartResult> =
        gate(command.run.issueId, IssueWriteAction.CROSS_DISCUSSION, "create_cross_discussion_synthesis") {
            crossDiscussionSynthesis.createCrossDiscussionSynthesis(command)
        }

    suspend fun createCollaborationRetry(
        command: CreateCollaborationRetryCommand,
    ): RepositoryResult<CollaborationStartResult> =
        gateRun(command.previousRunId, IssueWriteAction.CROSS_DISCUSSION, "create_collaboration_retry") {
            collaborationRetry.createCollaborationRetry(command)
        }

    suspend fun appendDomainMessage(command: AppendDomainMessageCommand): RepositoryResult<Message> =
        gate(command.issueId, IssueWriteAction.CREATE_RUN, "append_domain_message") {
            issueExecution.appendDomainMessage(command)
        }

    suspend fun updatePendingDomainMessage(
        command: UpdatePendingDomainMessageCommand,
    ): RepositoryResult<Message> =
        gate(command.issueId, IssueWriteAction.CREATE_RUN, "update_pending_domain_message") {
            pendingMessages.updatePendingDomainMessage(command)
        }

    suspend fun transitionRun(command: TransitionRunCommand): RepositoryResult<ExecutionRunEntity> {
        val terminal = command.newStatus == ExecutionRunStatus.STOPPED ||
            command.newStatus == ExecutionRunStatus.FAILED
        return if (terminal) {
            issueExecution.transitionRun(command)
        } else {
            gateRun(command.runId, IssueWriteAction.CREATE_RUN, "transition_run") {
                issueExecution.transitionRun(command)
            }
        }
    }

    suspend fun saveStageDraft(
        command: SaveStageDraftCommand,
    ): RepositoryResult<StageSummaryDraftEntity> =
        gate(command.draft.issueId, IssueWriteAction.SAVE_DRAFT, "save_stage_draft") {
            resources.saveStageDraft(command)
        }

    suspend fun abandonStageDraft(issueId: String, stageId: String): RepositoryResult<Unit> =
        gate(issueId, IssueWriteAction.SAVE_DRAFT, "abandon_stage_draft") {
            resources.abandonStageDraft(issueId, stageId)
        }

    suspend fun confirmArtifact(
        command: ConfirmArtifactCommand,
    ): RepositoryResult<ConfirmedArtifactEntity> =
        gate(command.artifact.issueId, IssueWriteAction.CONFIRM_ARTIFACT, "confirm_artifact") {
            resources.confirmArtifact(command)
        }

    suspend fun recordMaterialUsage(
        entity: MaterialUsageSnapshotEntity,
    ): RepositoryResult<MaterialUsageSnapshotEntity> =
        gate(entity.issueId, IssueWriteAction.RECORD_CONTEXT_USAGE, "record_material_usage") {
            usages.recordMaterialUsage(entity)
        }

    suspend fun recordPersonalContextUsage(
        entity: PersonalContextUsageSnapshotEntity,
    ): RepositoryResult<PersonalContextUsageSnapshotEntity> =
        gate(entity.issueId, IssueWriteAction.RECORD_CONTEXT_USAGE, "record_personal_context_usage") {
            usages.recordPersonalContextUsage(entity)
        }

    suspend fun prepareExecutionContext(
        command: PrepareExecutionContextCommand,
    ): RepositoryResult<PreparedExecutionContext> =
        gate(command.draft.issueId, IssueWriteAction.RECORD_CONTEXT_USAGE, "prepare_execution_context") {
            materialContext.prepareExecutionContext(command)
        }

    suspend fun moveIssueToTrash(
        issueId: String,
        changedAt: Long,
    ): RepositoryResult<IssueLifecycleEntity> =
        writeGate.requireAllowed(issueId, IssueWriteAction.MOVE_TO_TRASH, "move_issue_to_trash").then {
            writeGate.requireNoActiveWork(issueId, "move_issue_to_trash").then {
                lifecycleRecovery.moveIssueToTrash(issueId, changedAt)
            }
        }

    suspend fun restoreIssueFromTrash(
        issueId: String,
        changedAt: Long,
    ): RepositoryResult<IssueLifecycleEntity> =
        gate(issueId, IssueWriteAction.RESTORE_FROM_TRASH, "restore_issue_from_trash") {
            lifecycleRecovery.restoreIssueFromTrash(issueId, changedAt)
        }

    private suspend fun <T> gate(
        issueId: String,
        action: IssueWriteAction,
        operation: String,
        block: suspend () -> RepositoryResult<T>,
    ): RepositoryResult<T> = writeGate.requireAllowed(issueId, action, operation).then(block)

    private suspend fun <T> gateRun(
        runId: String,
        action: IssueWriteAction,
        operation: String,
        block: suspend () -> RepositoryResult<T>,
    ): RepositoryResult<T> = writeGate.requireRunAllowed(runId, action, operation).then(block)
}
