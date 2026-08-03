package com.elio.jianyu.data

/**
 * 见域领域数据公共门面。
 *
 * 公共调用方只依赖 [JianyuRepository]；内部按议题执行、Pending 消息、资源、
 * 使用快照和生命周期恢复拆分组件，并共享唯一 [JianyuRepositoryTransactions] 事务协调器。
 */
class RoomJianyuRepository(
    database: RoundtableDatabase,
    officialSkillIdValidator: OfficialSkillIdValidator = RejectingOfficialSkillIdValidator
) : JianyuRepository, JianyuExecutionRuntimeRepository {
    private val transactions = JianyuRepositoryTransactions(database)
    private val issueExecution = IssueExecutionRepositoryComponent(transactions)
    private val executionRuntime = ExecutionRuntimeRepositoryComponent(transactions)
    private val pendingMessages = PendingMessageRepositoryComponent(transactions)
    private val resources = ResourceRepositoryComponent(
        transactions = transactions,
        officialSkillIdValidator = officialSkillIdValidator
    )
    private val usages = UsageRepositoryComponent(transactions)
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

    override suspend fun createStage(command: CreateStageCommand): RepositoryResult<StageEntity> {
        return issueExecution.createStage(command)
    }

    override suspend fun undoLatestUnrunStage(
        issueId: String,
        stageId: String
    ): RepositoryResult<Unit> {
        return issueExecution.undoLatestUnrunStage(issueId, stageId)
    }

    override suspend fun createExecutionRun(
        command: CreateExecutionRunCommand
    ): RepositoryResult<ExecutionRunSnapshot> {
        return issueExecution.createExecutionRun(command)
    }

    override suspend fun createExecutionRuntime(
        command: CreateExecutionRuntimeCommand,
    ): RepositoryResult<ExecutionRuntimeSnapshot> {
        return executionRuntime.createExecutionRuntime(command)
    }

    override suspend fun getExecutionRuntime(
        runId: String,
    ): RepositoryResult<ExecutionRuntimeSnapshot> {
        return executionRuntime.getExecutionRuntime(runId)
    }

    override suspend fun transitionExecutionParticipant(
        command: TransitionExecutionParticipantCommand,
    ): RepositoryResult<ExecutionParticipantStateEntity> {
        return executionRuntime.transitionExecutionParticipant(command)
    }

    override suspend fun consumeExecutionBudget(
        command: ConsumeExecutionBudgetCommand,
    ): RepositoryResult<ExecutionRunBudgetEntity> {
        return executionRuntime.consumeExecutionBudget(command)
    }

    override suspend fun setExecutionBudgetReserve(
        command: SetExecutionBudgetReserveCommand,
    ): RepositoryResult<ExecutionRunBudgetEntity> {
        return executionRuntime.setExecutionBudgetReserve(command)
    }

    override suspend fun closeExecutionBudget(
        rootRunId: String,
        updatedAt: Long,
    ): RepositoryResult<ExecutionRunBudgetEntity> {
        return executionRuntime.closeExecutionBudget(rootRunId, updatedAt)
    }

    override suspend fun recoverInterruptedExecution(
        command: RecoverInterruptedExecutionCommand,
    ): RepositoryResult<ExecutionRuntimeSnapshot> {
        return executionRuntime.recoverInterruptedExecution(command)
    }

    override suspend fun appendDomainMessage(
        command: AppendDomainMessageCommand
    ): RepositoryResult<Message> {
        return issueExecution.appendDomainMessage(command)
    }

    override suspend fun updatePendingDomainMessage(
        command: UpdatePendingDomainMessageCommand
    ): RepositoryResult<Message> {
        return pendingMessages.updatePendingDomainMessage(command)
    }

    override suspend fun transitionRun(
        command: TransitionRunCommand
    ): RepositoryResult<ExecutionRunEntity> {
        return issueExecution.transitionRun(command)
    }

    override suspend fun saveStageDraft(
        command: SaveStageDraftCommand
    ): RepositoryResult<StageSummaryDraftEntity> {
        return resources.saveStageDraft(command)
    }

    override suspend fun abandonStageDraft(
        issueId: String,
        stageId: String
    ): RepositoryResult<Unit> {
        return resources.abandonStageDraft(issueId, stageId)
    }

    override suspend fun confirmArtifact(
        command: ConfirmArtifactCommand
    ): RepositoryResult<ConfirmedArtifactEntity> {
        return resources.confirmArtifact(command)
    }

    override suspend fun recordMaterialUsage(
        entity: MaterialUsageSnapshotEntity
    ): RepositoryResult<MaterialUsageSnapshotEntity> {
        return usages.recordMaterialUsage(entity)
    }

    override suspend fun recordPersonalContextUsage(
        entity: PersonalContextUsageSnapshotEntity
    ): RepositoryResult<PersonalContextUsageSnapshotEntity> {
        return usages.recordPersonalContextUsage(entity)
    }

    override suspend fun saveOfficialSkillCombination(
        command: SaveOfficialSkillCombinationCommand
    ): RepositoryResult<OfficialSkillCombinationSnapshot> {
        return resources.saveOfficialSkillCombination(command)
    }

    override suspend fun deleteOfficialSkillCombination(
        command: DeleteOfficialSkillCombinationCommand
    ): RepositoryResult<OfficialSkillCombinationEntity> {
        return resources.deleteOfficialSkillCombination(command)
    }

    override suspend fun getOfficialSkillCombination(
        combinationId: String
    ): RepositoryResult<OfficialSkillCombinationSnapshot> {
        return resources.getOfficialSkillCombination(combinationId)
    }

    override suspend fun listOfficialSkillCombinations(): RepositoryResult<List<OfficialSkillCombinationSnapshot>> {
        return resources.listOfficialSkillCombinations()
    }

    override suspend fun archiveIssue(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return lifecycleRecovery.archiveIssue(issueId, changedAt)
    }

    override suspend fun restoreIssue(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return lifecycleRecovery.restoreIssue(issueId, changedAt)
    }

    override suspend fun moveIssueToTrash(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return lifecycleRecovery.moveIssueToTrash(issueId, changedAt)
    }

    override suspend fun restoreIssueFromTrash(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return lifecycleRecovery.restoreIssueFromTrash(issueId, changedAt)
    }

    override suspend fun requestIssuePurge(
        issueId: String,
        requestedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return lifecycleRecovery.requestIssuePurge(issueId, requestedAt)
    }

    override suspend fun recoverIssue(issueId: String): RepositoryResult<IssueRecoverySnapshot> {
        return lifecycleRecovery.recoverIssue(issueId)
    }

    override suspend fun listIssueNavigation(
        states: Set<IssueLifecycleState>
    ): RepositoryResult<List<IssueNavigationItem>> {
        return lifecycleRecovery.listIssueNavigation(states)
    }

    private companion object {
        const val LEGACY_ISSUE_ID_PREFIX = "legacy-chat-"
    }
}
