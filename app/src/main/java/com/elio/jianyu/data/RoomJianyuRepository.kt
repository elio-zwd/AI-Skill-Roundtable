package com.elio.jianyu.data

/**
 * 见域领域数据公共门面。
 *
 * 公共调用方只依赖 [JianyuRepository]；内部按议题执行、Pending 消息、资源、
 * 使用快照、协作和生命周期恢复拆分组件，并共享唯一 [JianyuRepositoryTransactions] 事务协调器。
 */
class RoomJianyuRepository(
    database: RoundtableDatabase,
    officialSkillIdValidator: OfficialSkillIdValidator = RejectingOfficialSkillIdValidator
) : JianyuRepository,
    JianyuExecutionRuntimeRepository,
    JianyuCollaborationRepository,
    JianyuArtifactSourceRecoveryRepository {
    private val transactions = JianyuRepositoryTransactions(database)
    private val issueExecution = IssueExecutionRepositoryComponent(transactions)
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
        issueExecution.createStage(command)

    override suspend fun undoLatestUnrunStage(
        issueId: String,
        stageId: String
    ): RepositoryResult<Unit> = issueExecution.undoLatestUnrunStage(issueId, stageId)

    override suspend fun createExecutionRun(
        command: CreateExecutionRunCommand
    ): RepositoryResult<ExecutionRunSnapshot> = issueExecution.createExecutionRun(command)

    override suspend fun createExecutionRuntime(
        command: CreateExecutionRuntimeCommand,
    ): RepositoryResult<ExecutionRuntimeSnapshot> = executionRuntime.createExecutionRuntime(command)

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
        collaborationRuntime.recoverInterruptedExecution(command)

    override suspend fun createDirectedInteraction(
        command: CreateDirectedInteractionCommand,
    ): RepositoryResult<CollaborationStartResult> =
        collaboration.createDirectedInteraction(command)

    override suspend fun createCrossDiscussionResponse(
        command: CreateCrossDiscussionResponseCommand,
    ): RepositoryResult<CollaborationStartResult> =
        collaboration.createCrossDiscussionResponse(command)

    override suspend fun createCrossDiscussionSynthesis(
        command: CreateCrossDiscussionSynthesisCommand,
    ): RepositoryResult<CollaborationStartResult> =
        crossDiscussionSynthesis.createCrossDiscussionSynthesis(command)

    override suspend fun createCollaborationRetry(
        command: CreateCollaborationRetryCommand,
    ): RepositoryResult<CollaborationStartResult> =
        collaborationRetry.createCollaborationRetry(command)

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
    ): RepositoryResult<Message> = issueExecution.appendDomainMessage(command)

    override suspend fun updatePendingDomainMessage(
        command: UpdatePendingDomainMessageCommand
    ): RepositoryResult<Message> = pendingMessages.updatePendingDomainMessage(command)

    override suspend fun transitionRun(
        command: TransitionRunCommand
    ): RepositoryResult<ExecutionRunEntity> = issueExecution.transitionRun(command)

    override suspend fun saveStageDraft(
        command: SaveStageDraftCommand
    ): RepositoryResult<StageSummaryDraftEntity> = resources.saveStageDraft(command)

    override suspend fun abandonStageDraft(
        issueId: String,
        stageId: String
    ): RepositoryResult<Unit> = resources.abandonStageDraft(issueId, stageId)

    override suspend fun confirmArtifact(
        command: ConfirmArtifactCommand
    ): RepositoryResult<ConfirmedArtifactEntity> = resources.confirmArtifact(command)

    override suspend fun recordMaterialUsage(
        entity: MaterialUsageSnapshotEntity
    ): RepositoryResult<MaterialUsageSnapshotEntity> = usages.recordMaterialUsage(entity)

    override suspend fun recordPersonalContextUsage(
        entity: PersonalContextUsageSnapshotEntity
    ): RepositoryResult<PersonalContextUsageSnapshotEntity> = usages.recordPersonalContextUsage(entity)

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
    ): RepositoryResult<PreparedExecutionContext> = materialContext.prepareExecutionContext(command)

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
    ): RepositoryResult<IssueLifecycleEntity> = lifecycleRecovery.archiveIssue(issueId, changedAt)

    override suspend fun restoreIssue(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> = lifecycleRecovery.restoreIssue(issueId, changedAt)

    override suspend fun moveIssueToTrash(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> = lifecycleRecovery.moveIssueToTrash(issueId, changedAt)

    override suspend fun restoreIssueFromTrash(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> = lifecycleRecovery.restoreIssueFromTrash(issueId, changedAt)

    override suspend fun requestIssuePurge(
        issueId: String,
        requestedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> = lifecycleRecovery.requestIssuePurge(issueId, requestedAt)

    override suspend fun recoverIssue(issueId: String): RepositoryResult<IssueRecoverySnapshot> =
        lifecycleRecovery.recoverIssue(issueId)

    override suspend fun listIssueNavigation(
        states: Set<IssueLifecycleState>
    ): RepositoryResult<List<IssueNavigationItem>> = lifecycleRecovery.listIssueNavigation(states)

    private companion object {
        const val LEGACY_ISSUE_ID_PREFIX = "legacy-chat-"
    }
}
