package com.elio.jianyu.data

sealed interface RepositoryResult<out T> {
    data class Success<out T>(
        val value: T,
        val idempotent: Boolean = false
    ) : RepositoryResult<T>

    data class Failure(
        val error: RepositoryError
    ) : RepositoryResult<Nothing>
}

sealed interface RepositoryError {
    data class NotFound(
        val resource: String,
        val stableId: String
    ) : RepositoryError

    data class AlreadyExists(
        val resource: String,
        val stableId: String
    ) : RepositoryError

    data class IdempotencyConflict(
        val operation: String,
        val stableId: String
    ) : RepositoryError

    data class InvalidState(
        val operation: String,
        val stateCode: String
    ) : RepositoryError

    data class ConstraintViolation(
        val operation: String,
        val constraintCode: String
    ) : RepositoryError

    data class StorageFailure(
        val operation: String,
        val retryable: Boolean
    ) : RepositoryError

    data class CompatibilityFailure(
        val operation: String,
        val compatibilityCode: String
    ) : RepositoryError
}

fun interface OfficialSkillIdValidator {
    suspend fun isValid(officialSkillId: String): Boolean
}

object RejectingOfficialSkillIdValidator : OfficialSkillIdValidator {
    override suspend fun isValid(officialSkillId: String): Boolean = false
}

data class SaveIssueCommand(
    val issueId: String,
    val title: String,
    val initialStageId: String,
    val initialStageTitle: String,
    val initialObjective: String,
    val createdAt: Long
)

data class SavedIssue(
    val issue: IssueEntity,
    val initialStage: StageEntity,
    val lifecycle: IssueLifecycleEntity
)

data class CreateStageCommand(
    val stageId: String,
    val issueId: String,
    val title: String,
    val objective: String,
    val createdAt: Long
)

data class CreateExecutionRunCommand(
    val run: ExecutionRunEntity,
    val participants: List<ExecutionParticipantSnapshotEntity>
)

data class ExecutionRunSnapshot(
    val run: ExecutionRunEntity,
    val participants: List<ExecutionParticipantSnapshotEntity>
)

data class AppendDomainMessageCommand(
    val messageId: Long,
    val issueId: String,
    val stageId: String,
    val executionRunId: String? = null,
    val participantSnapshotId: String? = null,
    val senderId: String,
    val senderName: String,
    val avatar: String,
    val text: String,
    val timestamp: Long,
    val isPending: Boolean,
    val roundIndex: Int,
    val compatibilitySessionTitle: String
)

/**
 * 对已存在 Pending 领域消息进行流式文本更新或原位完成。
 *
 * 关系字段必须与原消息完全一致；消息一旦完成，迟到片段不得覆盖成功正文。
 */
data class UpdatePendingDomainMessageCommand(
    val messageId: Long,
    val issueId: String,
    val stageId: String,
    val executionRunId: String? = null,
    val participantSnapshotId: String? = null,
    val text: String,
    val keepPending: Boolean
)

data class TransitionRunCommand(
    val runId: String,
    val expectedStatuses: Set<ExecutionRunStatus>,
    val newStatus: ExecutionRunStatus,
    val updatedAt: Long,
    val startedAt: Long? = null,
    val finishedAt: Long? = null,
    val stoppedAt: Long? = null,
    val failureCode: String? = null,
    val failureMessage: String? = null
)

data class SaveStageDraftCommand(
    val draft: StageSummaryDraftEntity,
    val revision: StageSummaryDraftRevisionEntity
)

data class ConfirmArtifactCommand(
    val artifact: ConfirmedArtifactEntity,
    val sources: ArtifactSources
)

data class SaveOfficialSkillCombinationCommand(
    val combination: OfficialSkillCombinationEntity,
    val members: List<OfficialSkillCombinationMemberEntity>,
    val expectedUpdatedAt: Long? = null
)

data class DeleteOfficialSkillCombinationCommand(
    val combinationId: String,
    val expectedUpdatedAt: Long,
    val deletedAt: Long
)

data class OfficialSkillCombinationSnapshot(
    val combination: OfficialSkillCombinationEntity,
    val members: List<OfficialSkillCombinationMemberEntity>
)

enum class LifecycleAction {
    ARCHIVE,
    RESTORE,
    MOVE_TO_TRASH,
    RESTORE_FROM_TRASH,
    REQUEST_PURGE
}

data class IssueRecoveryCore(
    val issue: IssueEntity,
    val lifecycle: IssueLifecycleEntity,
    val stages: List<StageEntity>,
    val currentStage: StageEntity?,
    val runs: List<ExecutionRunEntity>,
    val activeOrRecoverableRuns: List<ExecutionRunEntity>,
    val participants: List<ExecutionParticipantSnapshotEntity>,
    val messages: List<Message>,
    val pendingMessages: List<Message>
)

data class IssueRecoveryResources(
    val drafts: List<StageSummaryDraftEntity>,
    val draftRevisions: List<StageSummaryDraftRevisionEntity>,
    val artifacts: List<ConfirmedArtifactEntity>,
    val materialUsages: List<MaterialUsageSnapshotEntity>,
    val personalContextUsages: List<PersonalContextUsageSnapshotEntity>,
    val audioAssets: List<AudioAssetEntity>
)

data class IssueRecoverySnapshot(
    val core: IssueRecoveryCore,
    val resources: IssueRecoveryResources
)

data class IssueNavigationItem(
    val issue: IssueEntity,
    val lifecycle: IssueLifecycleEntity,
    val currentStage: StageEntity?,
    val activeRunCount: Int
)

interface JianyuRepository {
    suspend fun saveIssue(command: SaveIssueCommand): RepositoryResult<SavedIssue>

    suspend fun createStage(command: CreateStageCommand): RepositoryResult<StageEntity>

    suspend fun undoLatestUnrunStage(
        issueId: String,
        stageId: String
    ): RepositoryResult<Unit>

    suspend fun createExecutionRun(
        command: CreateExecutionRunCommand
    ): RepositoryResult<ExecutionRunSnapshot>

    suspend fun appendDomainMessage(
        command: AppendDomainMessageCommand
    ): RepositoryResult<Message>

    suspend fun updatePendingDomainMessage(
        command: UpdatePendingDomainMessageCommand
    ): RepositoryResult<Message>

    suspend fun transitionRun(
        command: TransitionRunCommand
    ): RepositoryResult<ExecutionRunEntity>

    suspend fun saveStageDraft(
        command: SaveStageDraftCommand
    ): RepositoryResult<StageSummaryDraftEntity>

    suspend fun abandonStageDraft(
        issueId: String,
        stageId: String
    ): RepositoryResult<Unit>

    suspend fun confirmArtifact(
        command: ConfirmArtifactCommand
    ): RepositoryResult<ConfirmedArtifactEntity>

    suspend fun recordMaterialUsage(
        entity: MaterialUsageSnapshotEntity
    ): RepositoryResult<MaterialUsageSnapshotEntity>

    suspend fun recordPersonalContextUsage(
        entity: PersonalContextUsageSnapshotEntity
    ): RepositoryResult<PersonalContextUsageSnapshotEntity>

    suspend fun createMaterial(command: CreateMaterialCommand): RepositoryResult<Material>

    suspend fun updateMaterial(command: UpdateMaterialCommand): RepositoryResult<Material>

    suspend fun getMaterial(materialId: String): RepositoryResult<Material>

    suspend fun listMaterials(
        filter: MaterialFilter = MaterialFilter()
    ): RepositoryResult<List<Material>>

    suspend fun changeMaterialLifecycle(
        command: ChangeMaterialLifecycleCommand
    ): RepositoryResult<Material>

    suspend fun getMaterialPurgeImpact(
        materialId: String
    ): RepositoryResult<ContextPurgeImpact>

    suspend fun purgeMaterial(command: PurgeMaterialCommand): RepositoryResult<Material>

    suspend fun createPersonalContext(
        command: CreatePersonalContextCommand
    ): RepositoryResult<PersonalContext>

    suspend fun updatePersonalContext(
        command: UpdatePersonalContextCommand
    ): RepositoryResult<PersonalContext>

    suspend fun getPersonalContext(
        personalContextId: String
    ): RepositoryResult<PersonalContext>

    suspend fun listPersonalContexts(
        filter: PersonalContextFilter = PersonalContextFilter()
    ): RepositoryResult<List<PersonalContext>>

    suspend fun changePersonalContextLifecycle(
        command: ChangePersonalContextLifecycleCommand
    ): RepositoryResult<PersonalContext>

    suspend fun getPersonalContextPurgeImpact(
        personalContextId: String
    ): RepositoryResult<ContextPurgeImpact>

    suspend fun purgePersonalContext(
        command: PurgePersonalContextCommand
    ): RepositoryResult<PersonalContext>

    suspend fun prepareExecutionContext(
        command: PrepareExecutionContextCommand
    ): RepositoryResult<PreparedExecutionContext>

    suspend fun listRunContextUsage(
        runId: String
    ): RepositoryResult<List<ContextUsageSnapshot>>

    suspend fun saveOfficialSkillCombination(
        command: SaveOfficialSkillCombinationCommand
    ): RepositoryResult<OfficialSkillCombinationSnapshot>

    suspend fun deleteOfficialSkillCombination(
        command: DeleteOfficialSkillCombinationCommand
    ): RepositoryResult<OfficialSkillCombinationEntity>

    suspend fun getOfficialSkillCombination(
        combinationId: String
    ): RepositoryResult<OfficialSkillCombinationSnapshot>

    suspend fun listOfficialSkillCombinations(): RepositoryResult<List<OfficialSkillCombinationSnapshot>>

    suspend fun archiveIssue(issueId: String, changedAt: Long): RepositoryResult<IssueLifecycleEntity>

    suspend fun restoreIssue(issueId: String, changedAt: Long): RepositoryResult<IssueLifecycleEntity>

    suspend fun moveIssueToTrash(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity>

    suspend fun restoreIssueFromTrash(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity>

    suspend fun requestIssuePurge(
        issueId: String,
        requestedAt: Long
    ): RepositoryResult<IssueLifecycleEntity>

    suspend fun recoverIssue(issueId: String): RepositoryResult<IssueRecoverySnapshot>

    suspend fun listIssueNavigation(
        states: Set<IssueLifecycleState> = setOf(IssueLifecycleState.ACTIVE)
    ): RepositoryResult<List<IssueNavigationItem>>
}

internal fun validateParticipantPayload(
    participants: List<ExecutionParticipantSnapshotEntity>
): Boolean {
    if (participants.any { it.position < 0 || it.sourceId.isBlank() || it.sourceType.isBlank() }) {
        return false
    }
    if (participants.map { it.position }.distinct().size != participants.size) return false
    if (participants.map { it.sourceType to it.sourceId }.distinct().size != participants.size) {
        return false
    }
    return true
}

internal fun resolveLifecycleTransition(
    current: IssueLifecycleEntity,
    action: LifecycleAction,
    changedAt: Long
): IssueLifecycleEntity {
    require(changedAt > 0L) { "生命周期时间必须为正数" }
    return when (action) {
        LifecycleAction.ARCHIVE -> when (current.state) {
            IssueLifecycleState.ACTIVE -> current.copy(
                state = IssueLifecycleState.ARCHIVED,
                previousState = null,
                stateChangedAt = changedAt,
                updatedAt = changedAt,
                archivedAt = changedAt,
                trashedAt = null,
                purgeRequestedAt = null
            )
            IssueLifecycleState.ARCHIVED -> current
            IssueLifecycleState.TRASHED -> throw IllegalArgumentException("回收站议题不能直接归档")
        }
        LifecycleAction.RESTORE -> when (current.state) {
            IssueLifecycleState.ARCHIVED -> current.copy(
                state = IssueLifecycleState.ACTIVE,
                previousState = null,
                stateChangedAt = changedAt,
                updatedAt = changedAt,
                archivedAt = null,
                trashedAt = null,
                purgeRequestedAt = null
            )
            IssueLifecycleState.ACTIVE -> current
            IssueLifecycleState.TRASHED -> throw IllegalArgumentException("回收站议题必须使用回收站恢复")
        }
        LifecycleAction.MOVE_TO_TRASH -> when (current.state) {
            IssueLifecycleState.TRASHED -> current
            IssueLifecycleState.ACTIVE,
            IssueLifecycleState.ARCHIVED -> current.copy(
                state = IssueLifecycleState.TRASHED,
                previousState = current.state,
                stateChangedAt = changedAt,
                updatedAt = changedAt,
                trashedAt = changedAt,
                purgeRequestedAt = null
            )
        }
        LifecycleAction.RESTORE_FROM_TRASH -> when (current.state) {
            IssueLifecycleState.TRASHED -> {
                val restoredState = current.previousState ?: IssueLifecycleState.ACTIVE
                current.copy(
                    state = restoredState,
                    previousState = null,
                    stateChangedAt = changedAt,
                    updatedAt = changedAt,
                    archivedAt = if (restoredState == IssueLifecycleState.ARCHIVED) {
                        current.archivedAt ?: changedAt
                    } else {
                        null
                    },
                    trashedAt = null,
                    purgeRequestedAt = null
                )
            }
            IssueLifecycleState.ACTIVE,
            IssueLifecycleState.ARCHIVED -> current
        }
        LifecycleAction.REQUEST_PURGE -> {
            require(current.state == IssueLifecycleState.TRASHED) {
                "只有回收站议题可以请求彻底清除"
            }
            if (current.purgeRequestedAt != null) {
                current
            } else {
                current.copy(
                    stateChangedAt = changedAt,
                    updatedAt = changedAt,
                    purgeRequestedAt = changedAt
                )
            }
        }
    }
}
