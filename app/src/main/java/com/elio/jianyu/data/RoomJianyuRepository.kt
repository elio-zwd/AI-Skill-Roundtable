package com.elio.jianyu.data

/** 见域领域数据公共门面；跨表事务与生命周期门禁均由内部组件负责。 */
class RoomJianyuRepository(
    database: RoundtableDatabase,
    officialSkillIdValidator: OfficialSkillIdValidator = RejectingOfficialSkillIdValidator,
) : JianyuRepository,
    JianyuExecutionRuntimeRepository,
    JianyuCollaborationRepository,
    JianyuArtifactSourceRecoveryRepository,
    JianyuStageAdvancementRepository {
    private val transactions = JianyuRepositoryTransactions(database)
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
    private val resources = ResourceRepositoryComponent(transactions, officialSkillIdValidator)
    private val artifactSourceRecovery = ArtifactSourceRecoveryRepositoryComponent(transactions)
    private val usages = UsageRepositoryComponent(transactions)
    private val materialContext = MaterialContextRepositoryComponent(transactions)
    private val lifecycleRecovery = LifecycleRecoveryRepositoryComponent(transactions)
    private val lifecycleWrites = LifecycleGatedRepositoryComponent(
        writeGate = IssueLifecycleWriteGate(database),
        issueExecution = issueExecution,
        stageAdvancement = stageAdvancement,
        executionRuntime = executionRuntime,
        collaborationRuntime = collaborationRuntime,
        collaboration = collaboration,
        crossDiscussionSynthesis = crossDiscussionSynthesis,
        collaborationRetry = collaborationRetry,
        pendingMessages = pendingMessages,
        resources = resources,
        usages = usages,
        materialContext = materialContext,
        lifecycleRecovery = lifecycleRecovery,
    )

    override suspend fun saveIssue(command: SaveIssueCommand): RepositoryResult<SavedIssue> {
        if (command.issueId.startsWith(LEGACY_ISSUE_ID_PREFIX)) {
            return RepositoryResult.Failure(
                RepositoryError.ConstraintViolation("save_issue", "reserved_legacy_issue_id_prefix"),
            )
        }
        return issueExecution.saveIssue(command)
    }

    override suspend fun updateIssueThinkingPolicy(command: UpdateIssueThinkingPolicyCommand) =
        issueExecution.updateIssueThinkingPolicy(command)

    override suspend fun createStage(command: CreateStageCommand) = lifecycleWrites.createStage(command)

    override suspend fun undoLatestUnrunStage(issueId: String, stageId: String) =
        lifecycleWrites.undoLatestUnrunStage(issueId, stageId)

    override suspend fun advanceIssue(command: AdvanceIssueCommand) = lifecycleWrites.advanceIssue(command)

    override suspend fun getStageAdvancement(stageId: String) =
        stageAdvancement.getStageAdvancement(stageId)

    override suspend fun listStageAdvancements(issueId: String) =
        stageAdvancement.listStageAdvancements(issueId)

    override suspend fun createExecutionRun(command: CreateExecutionRunCommand) =
        lifecycleWrites.createExecutionRun(command)

    override suspend fun createExecutionRuntime(command: CreateExecutionRuntimeCommand) =
        lifecycleWrites.createExecutionRuntime(command)

    override suspend fun getExecutionRuntime(runId: String) =
        collaborationRuntime.getExecutionRuntime(runId)

    override suspend fun transitionExecutionParticipant(command: TransitionExecutionParticipantCommand) =
        executionRuntime.transitionExecutionParticipant(command)

    override suspend fun consumeExecutionBudget(command: ConsumeExecutionBudgetCommand) =
        executionRuntime.consumeExecutionBudget(command)

    override suspend fun setExecutionBudgetReserve(command: SetExecutionBudgetReserveCommand) =
        executionRuntime.setExecutionBudgetReserve(command)

    override suspend fun closeExecutionBudget(rootRunId: String, updatedAt: Long) =
        executionRuntime.closeExecutionBudget(rootRunId, updatedAt)

    override suspend fun recoverInterruptedExecution(command: RecoverInterruptedExecutionCommand) =
        lifecycleWrites.recoverInterruptedExecution(command)

    override suspend fun createStandardInteraction(command: CreateStandardInteractionCommand) =
        lifecycleWrites.createStandardInteraction(command)

    override suspend fun createDirectedInteraction(command: CreateDirectedInteractionCommand) =
        lifecycleWrites.createDirectedInteraction(command)

    override suspend fun createCrossDiscussionResponse(command: CreateCrossDiscussionResponseCommand) =
        lifecycleWrites.createCrossDiscussionResponse(command)

    override suspend fun createCrossDiscussionSynthesis(command: CreateCrossDiscussionSynthesisCommand) =
        lifecycleWrites.createCrossDiscussionSynthesis(command)

    override suspend fun createCollaborationRetry(command: CreateCollaborationRetryCommand) =
        lifecycleWrites.createCollaborationRetry(command)

    override suspend fun transitionCrossDiscussion(command: TransitionCrossDiscussionCommand) =
        collaboration.transitionCrossDiscussion(command)

    override suspend fun getStageCollaboration(stageId: String) =
        collaboration.getStageCollaboration(stageId)

    override suspend fun listExecutionMessageUsage(runId: String) =
        collaboration.listExecutionMessageUsage(runId)

    override suspend fun listArtifactSourcesForIssue(issueId: String) =
        artifactSourceRecovery.listArtifactSourcesForIssue(issueId)

    override suspend fun appendDomainMessage(command: AppendDomainMessageCommand) =
        lifecycleWrites.appendDomainMessage(command)

    override suspend fun updatePendingDomainMessage(command: UpdatePendingDomainMessageCommand) =
        lifecycleWrites.updatePendingDomainMessage(command)

    override suspend fun transitionRun(command: TransitionRunCommand) =
        lifecycleWrites.transitionRun(command)

    override suspend fun saveStageDraft(command: SaveStageDraftCommand) =
        lifecycleWrites.saveStageDraft(command)

    override suspend fun abandonStageDraft(issueId: String, stageId: String) =
        lifecycleWrites.abandonStageDraft(issueId, stageId)

    override suspend fun confirmArtifact(command: ConfirmArtifactCommand) =
        lifecycleWrites.confirmArtifact(command)

    override suspend fun recordMaterialUsage(entity: MaterialUsageSnapshotEntity) =
        lifecycleWrites.recordMaterialUsage(entity)

    override suspend fun recordPersonalContextUsage(entity: PersonalContextUsageSnapshotEntity) =
        lifecycleWrites.recordPersonalContextUsage(entity)

    override suspend fun createMaterial(command: CreateMaterialCommand) =
        materialContext.createMaterial(command)

    override suspend fun updateMaterial(command: UpdateMaterialCommand) =
        materialContext.updateMaterial(command)

    override suspend fun getMaterial(materialId: String) = materialContext.getMaterial(materialId)

    override suspend fun listMaterials(filter: MaterialFilter) = materialContext.listMaterials(filter)

    override suspend fun changeMaterialLifecycle(command: ChangeMaterialLifecycleCommand) =
        materialContext.changeMaterialLifecycle(command)

    override suspend fun getMaterialPurgeImpact(materialId: String) =
        materialContext.getMaterialPurgeImpact(materialId)

    override suspend fun purgeMaterial(command: PurgeMaterialCommand) =
        materialContext.purgeMaterial(command)

    override suspend fun createPersonalContext(command: CreatePersonalContextCommand) =
        materialContext.createPersonalContext(command)

    override suspend fun updatePersonalContext(command: UpdatePersonalContextCommand) =
        materialContext.updatePersonalContext(command)

    override suspend fun getPersonalContext(personalContextId: String) =
        materialContext.getPersonalContext(personalContextId)

    override suspend fun listPersonalContexts(filter: PersonalContextFilter) =
        materialContext.listPersonalContexts(filter)

    override suspend fun changePersonalContextLifecycle(command: ChangePersonalContextLifecycleCommand) =
        materialContext.changePersonalContextLifecycle(command)

    override suspend fun getPersonalContextPurgeImpact(personalContextId: String) =
        materialContext.getPersonalContextPurgeImpact(personalContextId)

    override suspend fun purgePersonalContext(command: PurgePersonalContextCommand) =
        materialContext.purgePersonalContext(command)

    override suspend fun prepareExecutionContext(command: PrepareExecutionContextCommand) =
        lifecycleWrites.prepareExecutionContext(command)

    override suspend fun listRunContextUsage(runId: String) =
        materialContext.listRunContextUsage(runId)

    override suspend fun saveOfficialSkillCombination(command: SaveOfficialSkillCombinationCommand) =
        resources.saveOfficialSkillCombination(command)

    override suspend fun deleteOfficialSkillCombination(command: DeleteOfficialSkillCombinationCommand) =
        resources.deleteOfficialSkillCombination(command)

    override suspend fun getOfficialSkillCombination(combinationId: String) =
        resources.getOfficialSkillCombination(combinationId)

    override suspend fun listOfficialSkillCombinations() = resources.listOfficialSkillCombinations()

    override suspend fun archiveIssue(issueId: String, changedAt: Long) =
        RepositoryResult.Failure(
            RepositoryError.InvalidState("archive_issue", "archive_event_required"),
        )

    override suspend fun restoreIssue(issueId: String, changedAt: Long) =
        RepositoryResult.Failure(
            RepositoryError.InvalidState("restore_issue", "resume_event_required"),
        )

    override suspend fun moveIssueToTrash(issueId: String, changedAt: Long) =
        lifecycleWrites.moveIssueToTrash(issueId, changedAt)

    override suspend fun restoreIssueFromTrash(issueId: String, changedAt: Long) =
        lifecycleWrites.restoreIssueFromTrash(issueId, changedAt)

    override suspend fun requestIssuePurge(issueId: String, requestedAt: Long) =
        RepositoryResult.Failure(
            RepositoryError.InvalidState("request_issue_purge", "purge_operation_required"),
        )

    override suspend fun recoverIssue(issueId: String) = lifecycleRecovery.recoverIssue(issueId)

    override suspend fun listIssueNavigation(states: Set<IssueLifecycleState>) =
        lifecycleRecovery.listIssueNavigation(states)

    private companion object {
        const val LEGACY_ISSUE_ID_PREFIX = "legacy-chat-"
    }
}
