package com.elio.jianyu.lifecycle

import com.elio.jianyu.data.ArchiveIssueWithEventCommand
import com.elio.jianyu.data.ArchivedIssueResult
import com.elio.jianyu.data.CreateRelatedIssueCommand
import com.elio.jianyu.data.IssueLifecycleV12Repository
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.RelatedIssueResult
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.ResumeArchivedIssueCommand
import com.elio.jianyu.data.ResumedIssueResult
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class IssueArchivePreparation(
    val impact: IssueArchiveImpact,
    val activeTasks: IssueLifecycleActiveTasks,
    val generatedSummaryMarkdown: String,
    val confirmationRevision: String,
)

sealed interface IssueArchiveStopResult {
    data class Ready(val preparation: IssueArchivePreparation) : IssueArchiveStopResult
    data class Failure(val code: String) : IssueArchiveStopResult
}

/** 打开、等待、返回和取消只读取状态；只有最终确认调用 Repository 写入。 */
class IssueArchiveCoordinator(
    private val repository: JianyuRepository,
    private val lifecycleRepository: IssueLifecycleV12Repository,
    private val taskController: IssueLifecycleTaskController,
) {
    suspend fun prepare(
        issueId: String,
        userNote: String = "",
    ): RepositoryResult<IssueArchivePreparation> {
        val recovery = when (val result = repository.recoverIssue(issueId)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return RepositoryResult.Failure(result.error)
        }
        val tasks = try {
            taskController.inspect(issueId)
        } catch (_: Exception) {
            return RepositoryResult.Failure(
                RepositoryError.StorageFailure("prepare_archive", retryable = true),
            )
        }
        val impact = IssueArchiveImpact(
            issueId = issueId,
            currentStageTitle = recovery.core.currentStage?.title,
            stageCount = recovery.core.stages.size,
            runCount = recovery.core.runs.size,
            activeWorkCount = tasks.activeStandardRunIds.size +
                tasks.activeCollaborationRunIds.size + tasks.activeDiscussionIds.size,
            pendingMessageCount = tasks.pendingMessageIds.size,
            draftCount = recovery.resources.drafts.size,
            artifactCount = recovery.resources.artifacts.size,
            audioAssetCount = recovery.resources.audioAssets.size,
            audioPendingCount = tasks.pendingAudioAssetIds.size,
        )
        val summary = IssueArchiveSummaryFactory.create(impact, userNote)
        return RepositoryResult.Success(
            IssueArchivePreparation(
                impact = impact,
                activeTasks = tasks,
                generatedSummaryMarkdown = summary,
                confirmationRevision = archiveRevision(
                    currentStageId = recovery.core.currentStage?.id,
                    impact = impact,
                    tasksRevision = tasks.revision,
                ),
            ),
        )
    }

    suspend fun waitUntilReady(
        issueId: String,
        userNote: String = "",
    ): RepositoryResult<IssueArchivePreparation> = prepare(issueId, userNote)

    suspend fun stopActiveWork(
        preparation: IssueArchivePreparation,
        userNote: String = "",
    ): IssueArchiveStopResult {
        if (!preparation.activeTasks.hasActiveWork) {
            return when (val refreshed = prepare(preparation.impact.issueId, userNote)) {
                is RepositoryResult.Success -> IssueArchiveStopResult.Ready(refreshed.value)
                is RepositoryResult.Failure -> IssueArchiveStopResult.Failure("archive_refresh_failed")
            }
        }
        return when (val stopped = taskController.stopAll(preparation.activeTasks)) {
            is IssueLifecycleTaskStopResult.Failure -> IssueArchiveStopResult.Failure(stopped.code)
            is IssueLifecycleTaskStopResult.Stopped -> {
                when (val refreshed = prepare(preparation.impact.issueId, userNote)) {
                    is RepositoryResult.Success -> IssueArchiveStopResult.Ready(refreshed.value)
                    is RepositoryResult.Failure -> IssueArchiveStopResult.Failure("archive_refresh_failed")
                }
            }
        }
    }

    suspend fun confirmArchive(
        preparation: IssueArchivePreparation,
        eventId: String,
        operationId: String,
        editedSummaryMarkdown: String,
        archivedAt: Long,
    ): RepositoryResult<ArchivedIssueResult> {
        val fresh = when (val result = prepare(preparation.impact.issueId)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return RepositoryResult.Failure(result.error)
        }
        if (fresh.confirmationRevision != preparation.confirmationRevision) {
            return RepositoryResult.Failure(
                RepositoryError.InvalidState("archive_issue", "archive_state_changed"),
            )
        }
        if (fresh.activeTasks.hasActiveWork) {
            return RepositoryResult.Failure(
                RepositoryError.InvalidState("archive_issue", "archive_active_work"),
            )
        }
        val recovery = when (val result = repository.recoverIssue(preparation.impact.issueId)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return RepositoryResult.Failure(result.error)
        }
        return lifecycleRepository.archiveIssueWithEvent(
            ArchiveIssueWithEventCommand(
                eventId = eventId,
                issueId = preparation.impact.issueId,
                operationId = operationId,
                summaryMarkdown = editedSummaryMarkdown,
                currentStageIdSnapshot = recovery.core.currentStage?.id,
                stageCountSnapshot = recovery.core.stages.size,
                runCountSnapshot = recovery.core.runs.size,
                draftCountSnapshot = recovery.resources.drafts.size,
                artifactCountSnapshot = recovery.resources.artifacts.size,
                audioAssetCountSnapshot = recovery.resources.audioAssets.size,
                archivedAt = archivedAt,
            ),
        )
    }

    suspend fun resume(
        command: ResumeArchivedIssueCommand,
    ): RepositoryResult<ResumedIssueResult> = lifecycleRepository.resumeArchivedIssue(command)

    suspend fun createRelated(
        command: CreateRelatedIssueCommand,
    ): RepositoryResult<RelatedIssueResult> = lifecycleRepository.createRelatedIssue(command)

    suspend fun moveToTrash(
        preparation: IssueArchivePreparation,
        trashedAt: Long,
    ): RepositoryResult<com.elio.jianyu.data.IssueLifecycleEntity> {
        val fresh = when (val result = prepare(preparation.impact.issueId)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return RepositoryResult.Failure(result.error)
        }
        if (fresh.confirmationRevision != preparation.confirmationRevision) {
            return RepositoryResult.Failure(
                RepositoryError.InvalidState("move_issue_to_trash", "trash_state_changed"),
            )
        }
        if (fresh.activeTasks.hasActiveWork) {
            return RepositoryResult.Failure(
                RepositoryError.InvalidState("move_issue_to_trash", "trash_active_work"),
            )
        }
        return repository.moveIssueToTrash(preparation.impact.issueId, trashedAt)
    }

    suspend fun restoreFromTrash(
        issueId: String,
        restoredAt: Long,
    ): RepositoryResult<com.elio.jianyu.data.IssueLifecycleEntity> =
        repository.restoreIssueFromTrash(issueId, restoredAt)
}

private fun archiveRevision(
    currentStageId: String?,
    impact: IssueArchiveImpact,
    tasksRevision: String,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    listOf(
        impact.issueId,
        currentStageId.orEmpty(),
        impact.stageCount.toString(),
        impact.runCount.toString(),
        impact.pendingMessageCount.toString(),
        impact.draftCount.toString(),
        impact.artifactCount.toString(),
        impact.audioAssetCount.toString(),
        tasksRevision,
    ).forEach { value ->
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
        digest.update(':'.code.toByte())
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
