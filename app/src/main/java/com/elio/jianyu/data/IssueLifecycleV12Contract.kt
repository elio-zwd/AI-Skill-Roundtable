package com.elio.jianyu.data

import com.elio.jianyu.lifecycle.ResumeChangeNotePolicy
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class ArchiveIssueWithEventCommand(
    val eventId: String,
    val issueId: String,
    val operationId: String,
    val summaryMarkdown: String,
    val currentStageIdSnapshot: String?,
    val stageCountSnapshot: Int,
    val runCountSnapshot: Int,
    val draftCountSnapshot: Int,
    val artifactCountSnapshot: Int,
    val audioAssetCountSnapshot: Int,
    val archivedAt: Long,
)

data class ArchivedIssueResult(
    val lifecycle: IssueLifecycleEntity,
    val archiveEvent: IssueArchiveEventEntity,
)

data class ResumeArchivedIssueCommand(
    val eventId: String,
    val issueId: String,
    val archiveEventId: String,
    val operationId: String,
    val changeNote: String,
    val noChangeConfirmed: Boolean,
    val resumedAt: Long,
)

data class ResumedIssueResult(
    val lifecycle: IssueLifecycleEntity,
    val resumeEvent: IssueResumeEventEntity,
)

data class CreateRelatedIssueCommand(
    val relationId: String,
    val operationId: String,
    val sourceIssueId: String,
    val sourceArchiveEventId: String,
    val targetIssueId: String,
    val targetIssueTitle: String,
    val targetStageId: String,
    val targetStageTitle: String,
    val targetObjective: String,
    val createdAt: Long,
)

data class RelatedIssueResult(
    val issue: IssueEntity,
    val initialStage: StageEntity,
    val lifecycle: IssueLifecycleEntity,
    val relation: IssueRelationEntity,
)

data class RequestIssuePurgeOperationCommand(
    val id: String,
    val issueId: String,
    val operationId: String,
    val impactHash: String,
    val firstConfirmation: Boolean,
    val finalConfirmation: Boolean,
    val requestedAt: Long,
)

data class TransitionIssuePurgeOperationCommand(
    val operationId: String,
    val expectedStates: Set<IssuePurgeState>,
    val targetState: IssuePurgeState,
    val updatedAt: Long,
    val failureCode: String? = null,
    val failurePhase: IssuePurgeFailurePhase? = null,
)

interface IssueLifecycleV12Repository {
    suspend fun archiveIssueWithEvent(
        command: ArchiveIssueWithEventCommand,
    ): RepositoryResult<ArchivedIssueResult>

    suspend fun resumeArchivedIssue(
        command: ResumeArchivedIssueCommand,
    ): RepositoryResult<ResumedIssueResult>

    suspend fun createRelatedIssue(
        command: CreateRelatedIssueCommand,
    ): RepositoryResult<RelatedIssueResult>

    suspend fun listArchiveEvents(
        issueId: String,
    ): RepositoryResult<List<IssueArchiveEventEntity>>

    suspend fun listResumeEvents(
        issueId: String,
    ): RepositoryResult<List<IssueResumeEventEntity>>

    suspend fun listIssueRelations(
        issueId: String,
    ): RepositoryResult<List<IssueRelationEntity>>

    suspend fun requestIssuePurgeOperation(
        command: RequestIssuePurgeOperationCommand,
    ): RepositoryResult<IssuePurgeOperationEntity>

    suspend fun transitionIssuePurgeOperation(
        command: TransitionIssuePurgeOperationCommand,
    ): RepositoryResult<IssuePurgeOperationEntity>

    suspend fun getIssuePurgeOperation(
        operationId: String,
    ): RepositoryResult<IssuePurgeOperationEntity>

    suspend fun listRecoverableIssuePurgeOperations(): RepositoryResult<List<IssuePurgeOperationEntity>>
}

internal object IssueLifecycleV12CommandPolicy {
    fun normalize(command: ArchiveIssueWithEventCommand): ArchiveIssueWithEventCommand {
        require(command.eventId.isNotBlank())
        require(command.issueId.isNotBlank())
        require(command.operationId.isNotBlank())
        require(command.summaryMarkdown.isNotBlank())
        require(command.archivedAt > 0L)
        require(
            listOf(
                command.stageCountSnapshot,
                command.runCountSnapshot,
                command.draftCountSnapshot,
                command.artifactCountSnapshot,
                command.audioAssetCountSnapshot,
            ).all { it >= 0 },
        )
        return command.copy(
            eventId = command.eventId.trim(),
            issueId = command.issueId.trim(),
            operationId = command.operationId.trim(),
            summaryMarkdown = command.summaryMarkdown.trim(),
            currentStageIdSnapshot = command.currentStageIdSnapshot?.trim()?.takeIf(String::isNotBlank),
        )
    }

    fun normalize(command: ResumeArchivedIssueCommand): ResumeArchivedIssueCommand {
        require(command.eventId.isNotBlank())
        require(command.issueId.isNotBlank())
        require(command.archiveEventId.isNotBlank())
        require(command.operationId.isNotBlank())
        require(command.resumedAt > 0L)
        return command.copy(
            eventId = command.eventId.trim(),
            issueId = command.issueId.trim(),
            archiveEventId = command.archiveEventId.trim(),
            operationId = command.operationId.trim(),
            changeNote = ResumeChangeNotePolicy.normalized(
                command.changeNote,
                command.noChangeConfirmed,
            ),
        )
    }

    fun normalize(command: CreateRelatedIssueCommand): CreateRelatedIssueCommand {
        require(command.relationId.isNotBlank())
        require(command.operationId.isNotBlank())
        require(command.sourceIssueId.isNotBlank())
        require(command.sourceArchiveEventId.isNotBlank())
        require(command.targetIssueId.isNotBlank())
        require(command.targetIssueId != command.sourceIssueId)
        require(command.targetIssueTitle.isNotBlank())
        require(command.targetStageId.isNotBlank())
        require(command.targetStageTitle.isNotBlank())
        require(command.targetObjective.isNotBlank())
        require(command.createdAt > 0L)
        return command.copy(
            relationId = command.relationId.trim(),
            operationId = command.operationId.trim(),
            sourceIssueId = command.sourceIssueId.trim(),
            sourceArchiveEventId = command.sourceArchiveEventId.trim(),
            targetIssueId = command.targetIssueId.trim(),
            targetIssueTitle = command.targetIssueTitle.trim(),
            targetStageId = command.targetStageId.trim(),
            targetStageTitle = command.targetStageTitle.trim(),
            targetObjective = command.targetObjective.trim(),
        )
    }

    fun normalize(command: RequestIssuePurgeOperationCommand): RequestIssuePurgeOperationCommand {
        require(command.id.isNotBlank())
        require(command.issueId.isNotBlank())
        require(command.operationId.isNotBlank())
        require(command.impactHash.isNotBlank())
        require(command.firstConfirmation && command.finalConfirmation) { "彻底清除必须完成两次确认" }
        require(command.requestedAt > 0L)
        return command.copy(
            id = command.id.trim(),
            issueId = command.issueId.trim(),
            operationId = command.operationId.trim(),
            impactHash = command.impactHash.trim(),
        )
    }
}

internal object IssueLifecycleV12PayloadHasher {
    fun hash(command: ArchiveIssueWithEventCommand): String {
        val value = IssueLifecycleV12CommandPolicy.normalize(command)
        return hashFields(
            listOf(
                value.eventId,
                value.issueId,
                value.operationId,
                value.summaryMarkdown,
                value.currentStageIdSnapshot.orEmpty(),
                value.stageCountSnapshot.toString(),
                value.runCountSnapshot.toString(),
                value.draftCountSnapshot.toString(),
                value.artifactCountSnapshot.toString(),
                value.audioAssetCountSnapshot.toString(),
            ),
        )
    }

    fun hash(command: ResumeArchivedIssueCommand): String {
        val value = IssueLifecycleV12CommandPolicy.normalize(command)
        return hashFields(
            listOf(
                value.eventId,
                value.issueId,
                value.archiveEventId,
                value.operationId,
                value.changeNote,
            ),
        )
    }

    fun hash(command: CreateRelatedIssueCommand): String {
        val value = IssueLifecycleV12CommandPolicy.normalize(command)
        return hashFields(
            listOf(
                value.relationId,
                value.operationId,
                value.sourceIssueId,
                value.sourceArchiveEventId,
                value.targetIssueId,
                value.targetIssueTitle,
                value.targetStageId,
                value.targetStageTitle,
                value.targetObjective,
            ),
        )
    }

    fun hash(command: RequestIssuePurgeOperationCommand): String {
        val value = IssueLifecycleV12CommandPolicy.normalize(command)
        return hashFields(
            listOf(
                value.id,
                value.issueId,
                value.operationId,
                value.impactHash,
            ),
        )
    }

    private fun hashFields(fields: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { field ->
            val bytes = field.toByteArray(StandardCharsets.UTF_8)
            digest.update(bytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
            digest.update(':'.code.toByte())
            digest.update(bytes)
            digest.update('\n'.code.toByte())
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}
