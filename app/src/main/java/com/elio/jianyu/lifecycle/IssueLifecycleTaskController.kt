package com.elio.jianyu.lifecycle

import com.elio.jianyu.audio.assets.AudioGenerationCancelResult
import com.elio.jianyu.audio.assets.AudioGenerationCoordinator
import com.elio.jianyu.collaboration.IssueCollaborationCoordinator
import com.elio.jianyu.data.AudioFileState
import com.elio.jianyu.data.ExecutionRunKind
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.getStageCollaboration
import com.elio.jianyu.execution.ExecutionRunCoordinator
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

data class IssueLifecycleActiveTasks(
    val issueId: String,
    val activeStandardRunIds: List<String>,
    val activeCollaborationRunIds: List<String>,
    val activeDiscussionIds: List<String>,
    val pendingMessageIds: List<Long>,
    val pendingAudioAssetIds: List<String>,
    val revision: String,
) {
    val hasActiveWork: Boolean
        get() = activeStandardRunIds.isNotEmpty() ||
            activeCollaborationRunIds.isNotEmpty() ||
            activeDiscussionIds.isNotEmpty() ||
            pendingMessageIds.isNotEmpty() ||
            pendingAudioAssetIds.isNotEmpty()
}

sealed interface IssueLifecycleTaskStopResult {
    data class Stopped(val latest: IssueLifecycleActiveTasks) : IssueLifecycleTaskStopResult
    data class Failure(val code: String) : IssueLifecycleTaskStopResult
}

interface IssueLifecycleTaskController {
    suspend fun inspect(issueId: String): IssueLifecycleActiveTasks
    suspend fun stopAll(snapshot: IssueLifecycleActiveTasks): IssueLifecycleTaskStopResult
}

/**
 * 生命周期流程只编排既有正式 Stop/Cancel 接口，不在 Repository 事务中取消网络或 WorkManager。
 *
 * 官方 Skill 目录加载失败时 Execution/Collaboration Coordinator 可能为空；此时仍允许只读检查，
 * 但若确实存在相关活动任务，停止请求必须显式失败，不能把任务伪装成终态。
 */
class DefaultIssueLifecycleTaskController(
    private val repository: JianyuRepository,
    private val executionCoordinator: ExecutionRunCoordinator?,
    private val collaborationCoordinator: IssueCollaborationCoordinator?,
    private val audioCoordinator: AudioGenerationCoordinator,
) : IssueLifecycleTaskController {
    override suspend fun inspect(issueId: String): IssueLifecycleActiveTasks {
        val recovery = when (val result = repository.recoverIssue(issueId)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> throw IllegalStateException("issue_recovery_failed")
        }
        val activeRuns = recovery.core.runs
            .filter { it.status == ExecutionRunStatus.NOT_STARTED || it.status == ExecutionRunStatus.RUNNING }
            .sortedBy { it.id }

        val discussions = mutableListOf<com.elio.jianyu.data.CrossDiscussionSessionEntity>()
        for (stage in recovery.core.stages) {
            when (val result = repository.getStageCollaboration(stage.id)) {
                is RepositoryResult.Success -> discussions += result.value.discussions
                is RepositoryResult.Failure -> Unit
            }
        }
        val activeDiscussionIds = discussions
            .filter { discussion ->
                discussion.status.storageValue in setOf("responding", "synthesizing")
            }
            .map { it.id }
            .distinct()
            .sorted()
        val pendingAudio = recovery.resources.audioAssets
            .filter { it.fileState == AudioFileState.PENDING }
            .filter { it.purgeRequestedAt == null && it.deletedAt == null }
            .map { it.id }
            .sorted()
        val standard = activeRuns
            .filter { it.runKind == ExecutionRunKind.STANDARD }
            .map { it.id }
        val collaboration = activeRuns
            .filter { it.runKind != ExecutionRunKind.STANDARD }
            .map { it.id }
        val pendingMessages = recovery.core.messages
            .filter { it.isPending }
            .map { it.id }
            .sorted()
        return IssueLifecycleActiveTasks(
            issueId = issueId,
            activeStandardRunIds = standard,
            activeCollaborationRunIds = collaboration,
            activeDiscussionIds = activeDiscussionIds,
            pendingMessageIds = pendingMessages,
            pendingAudioAssetIds = pendingAudio,
            revision = taskRevision(
                standard,
                collaboration,
                activeDiscussionIds,
                pendingMessages,
                pendingAudio,
            ),
        )
    }

    override suspend fun stopAll(
        snapshot: IssueLifecycleActiveTasks,
    ): IssueLifecycleTaskStopResult {
        return try {
            if (snapshot.activeStandardRunIds.isNotEmpty() && executionCoordinator == null) {
                return IssueLifecycleTaskStopResult.Failure("lifecycle_execution_runtime_unavailable")
            }
            if (
                (snapshot.activeCollaborationRunIds.isNotEmpty() || snapshot.activeDiscussionIds.isNotEmpty()) &&
                collaborationCoordinator == null
            ) {
                return IssueLifecycleTaskStopResult.Failure("lifecycle_collaboration_runtime_unavailable")
            }
            for (runId in snapshot.activeStandardRunIds) {
                checkNotNull(executionCoordinator).stop(runId)
            }
            for (runId in snapshot.activeCollaborationRunIds) {
                checkNotNull(collaborationCoordinator).stop(runId)
            }
            for (audioAssetId in snapshot.pendingAudioAssetIds) {
                when (audioCoordinator.cancelGeneration(audioAssetId)) {
                    is AudioGenerationCancelResult.Canceled -> Unit
                    is AudioGenerationCancelResult.Failure -> {
                        return IssueLifecycleTaskStopResult.Failure("purge_audio_cancel_failed")
                    }
                }
            }
            val latest = inspect(snapshot.issueId)
            if (latest.hasActiveWork) {
                IssueLifecycleTaskStopResult.Failure("lifecycle_tasks_not_terminal")
            } else {
                IssueLifecycleTaskStopResult.Stopped(latest)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            IssueLifecycleTaskStopResult.Failure("lifecycle_task_stop_failed")
        }
    }
}

private fun taskRevision(
    standard: List<String>,
    collaboration: List<String>,
    discussions: List<String>,
    pendingMessages: List<Long>,
    pendingAudio: List<String>,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    buildList {
        standard.forEach { add("standard:$it") }
        collaboration.forEach { add("collaboration:$it") }
        discussions.forEach { add("discussion:$it") }
        pendingMessages.forEach { add("message:$it") }
        pendingAudio.forEach { add("audio:$it") }
    }.sorted().forEach { value ->
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(StandardCharsets.US_ASCII))
        digest.update(':'.code.toByte())
        digest.update(bytes)
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
