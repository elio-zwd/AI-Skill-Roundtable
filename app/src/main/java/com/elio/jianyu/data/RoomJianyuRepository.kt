package com.elio.jianyu.data

import com.elio.jianyu.lifecycle.IssueWriteAction

/**
 * 见域领域数据公共门面。
 *
 * 公共调用方只依赖 [JianyuRepository]；内部按议题执行、阶段推进、Pending 消息、资源、
 * 使用快照、协作和生命周期恢复拆分组件，并共享唯一 [JianyuRepositoryTransactions] 事务协调器。
 */
class RoomJianyuRepository(
    database: RoundtableDatabase,
    officialSkillIdValidator: OfficialSkillIdValidator = RejectingOfficialSkillIdValidator
) : JianyuRepository,
    JianyuExecutionRuntimeRepository,
    JianyuCollaborationRepository,
    JianyuArtifactSourceRecoveryRepository,
    JianyuStageAdvancementRepository {
    private val transactions = JianyuRepositoryTransactions(database)
    private val writeGate = IssueLifecycleWriteGate(database)
    private val issueExecution = IssueExecutionRepositoryComponent(transactions)
    private val stageAdvancement = StageAdvancementRepositoryComponent(
        transactions = transactions,
        officialSkillIdValidator = officialSkillIdValidator,
    )
    private val executionRuntime = ExecutionRuntimeRepositoryComponent(transactions)
    private val collaborationRuntime = CollaborationRuntimeRepositoryComponent(transactions)
    private val collaboration = CollaborationRepositoryComponent(transactions)
    private val crossDiscussionSynthesis = CrossDiscussionSynthesisRepositoryComponent(transactions)
    private val collaborationRetry = CollaborationRetryRepositoryComponent(transactions)
    private val pendingMessages = PendingMessageRepositoryComponent(transactions)
    private val resources = ResourceRepositoryComponent(
        transactions = transactions,
        officialSkillIdValidator = officialSkillIdValidator
    )
    private val artifactSourceRecovery = ArtifactSourceRecoveryRepositoryComponent(transactions)
    private val usages = UsageRepositoryComponent(transactions)
    private val materialContext = MaterialContextRepositoryComponent(transactions)
    private val lifecycleRecovery = LifecycleRecoveryRepositoryComponent(transactions)

    override suspend fun saveIssue(command: SaveIssueCommand): RepositoryResult<SavedIssue> {
        if (command.issueId.startsWith(LEGACY_ISSUE_ID_PREFIX)) {
            return RepositoryResult.Failure(
                RepositoryError.ConstraintViolation(
                    operation = "save_issue",
                    constraintCode = "reserved_legacy_issue_id_prefix"
                )
            )
        }
        return issueExecution.saveIssue(command)
    }

    override suspend fun createStage(command: CreateStageCommand): RepositoryResult<StageEntity> =
        gate(command.issueId, IssueWriteAction.ADVANCE_STAGE, "create_stage") {
            issueExecution.createStage(command)
        }

    override suspend fun undoLatestUnrunStage(
        issueId: String,
        stageId: String
    ): RepositoryResult<Unit> = gate(issueId, IssueWriteAction.ADVANCE_STAGE, "undo_latest_stage") {
        stageAdvancement.undoLatestUnrunStage(issueId, stageId)
    }

    override suspend fun advanceIssue(
        command: AdvanceIssueCommand,
    ): RepositoryResult<AdvanceIssueResult> =
        gate(command.issueId, IssueWriteAction.ADVANCE_STAGE, "advance_issue") {
            stageAdvancement.advanceIssue(command)
        }

    override suspend fun getStageAdvancement(
        stageId: String,
    ): RepositoryResult<StageAdvancementSnapshot> =
        stageAdvancement.getStageAdvancement(stageId)

    override suspend fun listStageAdvancements(
        issueId: String,
    ): RepositoryResult<List<StageAdvancementSnapshot>> =
        stageAdvancement.listStageAdvancements(issueId)

    override suspend fun createExecutionRun(
        command: CreateExecutionRunCommand
    ): RepositoryResult<ExecutionRunSnapshot> =
        gate(command.run.issueId, IssueWriteAction.CREATE_RUN, "create_execution_run") {
            issueExecution.createExecutionRun(command)
        }

    override suspend fun createExecutionRuntime(
        command: CreateExecutionRuntimeCommand,
    ): RepositoryResult<ExecutionRuntimeSnapshot> =
        gate(command.run.issueId, IssueWriteAction.CREATE_RUN, "create_execution_runtime") {
            executionRuntime.createExecutionRuntime(command)
        }

    override suspend fun getExecutionRuntime(
        runId: String,
    ): RepositoryResult<ExecutionRuntimeSnapshot> = collaborationRuntime.getExecutionRuntime(runId)

    override suspend fun transitionExecutionParticipant(
        command: TransitionExecutionParticipantCommand,
    ): RepositoryResult<ExecutionParticipantStateEntity> =
        executionRuntime.transitionExecutionParticipant(command)

    override suspend fun consumeExecutionBudget(
        command: ConsumeExecutionBudgetCommand,
    ): RepositoryResult<ExecutionRunBudgetEntity> = executionRuntime.consumeExecutionBudget(command)

    override suspend fun setExecutionBudgetReserve(
        command: SetExecutionBudgetReserveCommand,
    ): RepositoryResult<ExecutionRunBudgetEntity> = executionRuntime.setExecutionBudgetReserve(command)

    override suspend fun closeExecutionBudget(
        rootRunId: String,
        updatedAt: Long,
    ): RepositoryResult<ExecutionRunBudgetEntity> =
        executionRuntime.closeExecutionBudget(rootRunId, updatedAt)

    override suspend fun recoverInterruptedExecution(
        command: RecoverInterruptedExecutionCommand,
    ): RepositoryResult<ExecutionRuntimeSnapshot> =
        gateRun(command.runId, IssueWriteAction.CREATE_RUN, "recover_interrupted_execution") {
            collaborationRuntime.recoverInterruptedExecution(command)
        }

    override suspend fun createDirectedInteraction(
        command: CreateDirectedInteractionCommand,
    ): RepositoryResult<CollaborationStartResult> =
        gate(command.userMessage.issueId, IssueWriteAction.DIRECTED_RESPONSE, "create_directed_interaction") {
            collaboration.createDirectedInteraction(command)
        }

    override suspend fun createCrossDiscussionResponse(
        command: CreateCrossDiscussionResponseCommand,
    ): RepositoryResult<CollaborationStartResult> =
        gate(command.userMessage.issueId, IssueWriteAction.CROSS_DISCUSSION, "create_cross_discussion_response") {
            collaboration.createCrossDiscussionResponse(command)
        }

    override suspend fun createCrossDiscussionSynthesis(
        command: CreateCrossDiscussionSynthesisCommand,
    ): RepositoryResult<CollaborationStartResult> =
        gate(command.run.issueId, IssueWriteAction.CROSS_DISCUSSION, "create_cross_discussion_synthesis") {
            crossDiscussionSynthesis.createCrossDiscussionSynthesis(command)
        }

    override suspend fun createCollaborationRetry(
        command: CreateCollaborationRetryCommand,
    ): RepositoryResult<CollaborationStartResult> =
        gateRun(command.previousRunId, IssueWriteAction.CROSS_DISCUSSION, "create_collaboration_retry") {
            collaborationRetry.createCollaborationRetry(command)
        }

    override suspend fun transitionCrossDiscussion(
        command: TransitionCrossDiscussionCommand,
    ): RepositoryResult<CrossDiscussionSessionEntity> =
        collaboration.transitionCrossDiscussion(command)

    override suspend fun getStageCollaboration(
        stageId: String,
    ): RepositoryResult<StageCollaborationSnapshot> = collaboration.getStageCollaboration(stageId)

    override suspend fun listExecutionMessageUsage(
        runId: String,
    ): RepositoryResult<List<ExecutionMessageUsageSnapshotEntity>> =
        collaboration.listExecutionMessageUsage(runId)

    override suspend fun listArtifactSourcesForIssue(
        issueId: String,
    ): RepositoryResult<List<ArtifactSourceRecoverySnapshot>> =
        artifactSourceRecovery.listArtifactSourcesForIssue(issueId)

    override suspend fun appendDomainMessage(
        command: AppendDomainMessageCommand
    ): RepositoryResult<Message> =
        gate(command.issueId, IssueWriteAction.CREATE_RUN, "append_domain_message") {
            issueExecution.appendDomainMessage(command)
        }

    override suspend fun updatePendingDomainMessage(
        command: UpdatePendingDomainMessageCommand
    ): RepositoryResult<Message> =
        gate(command.issueId, IssueWriteAction.CREATE_RUN, "update_pending_domain_message") {
            pendingMessages.updatePendingDomainMessage(command)
        }

    override suspend fun transitionRun(
        command: TransitionRunCommand
    ): RepositoryResult<ExecutionRunEntity> {
        val terminalCancellation = command.newStatus == ExecutionRunStatus.STOPPED ||
            command.newStatus == ExecutionRunStatus.FAILED
        return if (terminalCancellation) {
            issueExecution.transitionRun(command)
        } else {
            gateRun(command.runId, IssueWriteAction.CREATE_RUN, "transition_run") {
                issueExecution.transitionRun(command)
            }
        }
    }

    override suspend fun saveStageDraft(
        command: SaveStageDraftCommand
    ): RepositoryResult<StageSummaryDraftEntity> =
        gate(command.draft.issueId, IssueWriteAction.SAVE_DRAFT, "save_stage_draft") {
            resources.saveStageDraft(command)
        }

    override suspend fun abandonStageDraft(
        issueId: String,
        stageId: String
    ): RepositoryResult<Unit> = gate(issueId, IssueWriteAction.SAVE_DRAFT, "abandon_stage_draft") {
        resources.abandonStageDraft(issueId, stageId)
    }

    override suspend fun confirmArtifact(
        command: ConfirmArtifactCommand
    ): RepositoryResult<ConfirmedArtifactEntity> =
        gate(command.artifact.issueId, IssueWriteAction.CONFIRM_ARTIFACT, "confirm_artifact") {
            resources.confirmArtifact(command)
        }

    override suspend fun recordMaterialUsage(
        entity: MaterialUsageSnapshotEntity
    ): RepositoryResult<MaterialUsageSnapshotEntity> =
        gate(entity.issueId, IssueWriteAction.RECORD_CONTEXT_USAGE, "record_material_usage") {
            usages.recordMaterialUsage(entity)
        }

    override suspend fun recordPersonalContextUsage(
        entity: PersonalContextUsageSnapshotEntity
    ): RepositoryResult<PersonalContextUsageSnapshotEntity> =
        gate(entity.issueId, IssueWriteAction.RECORD_CONTEXT_USAGE, "record_personal_context_usage") {
            usages.recordPersonalContextUsage(entity)
        }

    override suspend fun createMaterial(
        command: CreateMaterialCommand,
    ): RepositoryResult<Material> = materialContext.createMaterial(command)

    override suspend fun updateMaterial(
        command: UpdateMaterialCommand,
    ): RepositoryResult<Material> = materialContext.updateMaterial(command)

    override suspend fun getMaterial(materialId: String): RepositoryResult<Material> =
        materialContext.getMaterial(materialId)

    override suspend fun listMaterials(
        filter: MaterialFilter,
    ): RepositoryResult<List<Material>> = materialContext.listMaterials(filter)

    override suspend fun changeMaterialLifecycle(
        command: ChangeMaterialLifecycleCommand,
    ): RepositoryResult<Material> = materialContext.changeMaterialLifecycle(command)

    override suspend fun getMaterialPurgeImpact(
        materialId: String,
    ): RepositoryResult<ContextPurgeImpact> = materialContext.getMaterialPurgeImpact(materialId)

    override suspend fun purgeMaterial(
        command: PurgeMaterialCommand,
    ): RepositoryResult<Material> = materialContext.purgeMaterial(command)

    override suspend fun createPersonalContext(
        command: CreatePersonalContextCommand,
    ): RepositoryResult<PersonalContext> = materialContext.createPersonalContext(command)

    override suspend fun updatePersonalContext(
        command: UpdatePersonalContextCommand,
    ): RepositoryResult<PersonalContext> = materialContext.updatePersonalContext(command)

    override suspend fun getPersonalContext(
        personalContextId: String,
    ): RepositoryResult<PersonalContext> = materialContext.getPersonalContext(personalContextId)

    override suspend fun listPersonalContexts(
        filter: PersonalContextFilter,
    ): RepositoryResult<List<PersonalContext>> = materialContext.listPersonalContexts(filter)

    override suspend fun changePersonalContextLifecycle(
        command: ChangePersonalContextLifecycleCommand,
    ): RepositoryResult<PersonalContext> = materialContext.changePersonalContextLifecycle(command)

    override suspend fun getPersonalContextPurgeImpact(
        personalContextId: String,
    ): RepositoryResult<ContextPurgeImpact> =
        materialContext.getPersonalContextPurgeImpact(personalContextId)

    override suspend fun purgePersonalContext(
        command: PurgePersonalContextCommand,
    ): RepositoryResult<PersonalContext> = materialContext.purgePersonalContext(command)

    override suspend fun prepareExecutionContext(
        command: PrepareExecutionContextCommand,
    ): RepositoryResult<PreparedExecutionContext> =
        gate(command.draft.issueId, IssueWriteAction.RECORD_CONTEXT_USAGE, "prepare_execution_context") {
            materialContext.prepareExecutionContext(command)
        }

    override suspend fun listRunContextUsage(
        runId: String,
    ): RepositoryResult<List<ContextUsageSnapshot>> = materialContext.listRunContextUsage(runId)

    override suspend fun saveOfficialSkillCombination(
        command: SaveOfficialSkillCombinationCommand
    ): RepositoryResult<OfficialSkillCombinationSnapshot> = resources.saveOfficialSkillCombination(command)

    override suspend fun deleteOfficialSkillCombination(
        command: DeleteOfficialSkillCombinationCommand
    ): RepositoryResult<OfficialSkillCombinationEntity> = resources.deleteOfficialSkillCombination(command)

    override suspend fun getOfficialSkillCombination(
        combinationId: String
    ): RepositoryResult<OfficialSkillCombinationSnapshot> = resources.getOfficialSkillCombination(combinationId)

    override suspend fun listOfficialSkillCombinations(): RepositoryResult<List<OfficialSkillCombinationSnapshot>> =
        resources.listOfficialSkillCombinations()

    override suspend fun archiveIssue(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> = RepositoryResult.Failure(
        RepositoryError.InvalidState("archive_issue", "archive_event_required"),
    )

    override suspend fun restoreIssue(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> = RepositoryResult.Failure(
        RepositoryError.InvalidState("restore_issue", "resume_event_required"),
    )

    override suspend fun moveIssueToTrash(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> =
        writeGate.requireAllowed(issueId, IssueWriteAction.MOVE_TO_TRASH, "move_issue_to_trash").then {
            writeGate.requireNoActiveWork(issueId, "move_issue_to_trash").then {
                lifecycleRecovery.moveIssueToTrash(issueId, changedAt)
            }
        }

    override suspend fun restoreIssueFromTrash(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> =
        gate(issueId, IssueWriteAction.RESTORE_FROM_TRASH, "restore_issue_from_trash") {
            lifecycleRecovery.restoreIssueFromTrash(issueId, changedAt)
        }

    override suspend fun requestIssuePurge(
        issueId: String,
        requestedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> = RepositoryResult.Failure(
        RepositoryError.InvalidState("request_issue_purge", "purge_operation_required"),
    )

    override suspend fun recoverIssue(issueId: String): RepositoryResult<IssueRecoverySnapshot> =
        lifecycleRecovery.recoverIssue(issueId)

    override suspend fun listIssueNavigation(
        states: Set<IssueLifecycleState>
    ): RepositoryResult<List<IssueNavigationItem>> = lifecycleRecovery.listIssueNavigation(states)

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

    private companion object {
        const val LEGACY_ISSUE_ID_PREFIX = "legacy-chat-"
    }
}
