package com.elio.jianyu.data

internal class LifecycleRecoveryRepositoryComponent(
    private val transactions: JianyuRepositoryTransactions
) {
    suspend fun archiveIssue(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return transitionLifecycle(issueId, LifecycleAction.ARCHIVE, changedAt)
    }

    suspend fun restoreIssue(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return transitionLifecycle(issueId, LifecycleAction.RESTORE, changedAt)
    }

    suspend fun moveIssueToTrash(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return transitionLifecycle(issueId, LifecycleAction.MOVE_TO_TRASH, changedAt)
    }

    suspend fun restoreIssueFromTrash(
        issueId: String,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return transitionLifecycle(issueId, LifecycleAction.RESTORE_FROM_TRASH, changedAt)
    }

    suspend fun requestIssuePurge(
        issueId: String,
        requestedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return transitionLifecycle(issueId, LifecycleAction.REQUEST_PURGE, requestedAt)
    }

    suspend fun recoverIssue(issueId: String): RepositoryResult<IssueRecoverySnapshot> {
        return transactions.transaction("recover_issue") {
            val issue = getIssue(issueId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("issue", issueId)
                )
            val lifecycle = getIssueLifecycle(issueId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.CompatibilityFailure(
                        "recover_issue",
                        "missing_issue_lifecycle"
                    )
                )
            val stages = getStagesForIssue(issueId)
            val runs = getExecutionRunsForIssue(issueId)
            val participants = getParticipantSnapshotsForIssue(issueId)
            val messages = getMessagesForIssue(issueId)
            val activeStatuses = setOf(
                ExecutionRunStatus.NOT_STARTED,
                ExecutionRunStatus.RUNNING,
                ExecutionRunStatus.PARTIAL_SUCCESS,
                ExecutionRunStatus.RETRYABLE
            )

            RepositoryResult.Success(
                IssueRecoverySnapshot(
                    core = IssueRecoveryCore(
                        issue = issue,
                        lifecycle = lifecycle,
                        stages = stages,
                        currentStage = stages.lastOrNull(),
                        runs = runs,
                        activeOrRecoverableRuns = runs.filter { it.status in activeStatuses },
                        participants = participants,
                        messages = messages,
                        pendingMessages = messages.filter { it.isPending }
                    ),
                    resources = IssueRecoveryResources(
                        drafts = getDraftsForIssue(issueId),
                        draftRevisions = getDraftRevisionsForIssue(issueId),
                        artifacts = getArtifactsForIssue(issueId),
                        materialUsages = getMaterialUsagesForIssue(issueId),
                        personalContextUsages = getPersonalContextUsagesForIssue(issueId),
                        audioAssets = getAudioAssetsForIssue(issueId)
                    )
                )
            )
        }
    }

    suspend fun listIssueNavigation(
        states: Set<IssueLifecycleState>
    ): RepositoryResult<List<IssueNavigationItem>> {
        return transactions.transaction("list_issue_navigation") {
            val lifecycleByIssue = getAllIssueLifecycles().associateBy { it.issueId }
            val issues = getAllIssues()
            val missingLifecycleIssue = issues.firstOrNull { it.id !in lifecycleByIssue }
            if (missingLifecycleIssue != null) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.CompatibilityFailure(
                        "list_issue_navigation",
                        "missing_issue_lifecycle"
                    )
                )
            }

            val activeStatuses = setOf(
                ExecutionRunStatus.NOT_STARTED,
                ExecutionRunStatus.RUNNING,
                ExecutionRunStatus.PARTIAL_SUCCESS,
                ExecutionRunStatus.RETRYABLE
            )
            val items = issues.mapNotNull { issue ->
                val lifecycle = requireNotNull(lifecycleByIssue[issue.id])
                if (lifecycle.state !in states) return@mapNotNull null
                val stages = getStagesForIssue(issue.id)
                val runs = getExecutionRunsForIssue(issue.id)
                IssueNavigationItem(
                    issue = issue,
                    lifecycle = lifecycle,
                    currentStage = stages.lastOrNull(),
                    activeRunCount = runs.count { it.status in activeStatuses }
                )
            }
            RepositoryResult.Success(items)
        }
    }

    private suspend fun transitionLifecycle(
        issueId: String,
        action: LifecycleAction,
        changedAt: Long
    ): RepositoryResult<IssueLifecycleEntity> {
        return transactions.transaction("transition_issue_lifecycle") {
            if (getIssue(issueId) == null) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("issue", issueId)
                )
            }
            val current = getIssueLifecycle(issueId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.CompatibilityFailure(
                        "transition_issue_lifecycle",
                        "missing_issue_lifecycle"
                    )
                )
            val target = try {
                resolveLifecycleTransition(current, action, changedAt)
            } catch (error: IllegalArgumentException) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.InvalidState(
                        "transition_issue_lifecycle",
                        "illegal_transition"
                    )
                )
            }
            if (target == current) {
                return@transaction RepositoryResult.Success(current, idempotent = true)
            }
            if (updateIssueLifecycle(target) != 1) {
                throw IllegalStateException("Lifecycle update failed")
            }
            RepositoryResult.Success(target)
        }
    }
}

/**
 * 已持久化成功成员的稳定参与者快照 ID。
 *
 * Pending 消息不视为成功；一个成员只要存在至少一条已完成消息即可在恢复后识别为成功。
 */
fun IssueRecoveryCore.successfulParticipantSnapshotIds(): Set<String> {
    return messages.asSequence()
        .filterNot { it.isPending }
        .mapNotNull { it.participantSnapshotId }
        .toSet()
}

/**
 * 当前活跃或可恢复 Run 中尚无成功消息的稳定参与者快照 ID。
 *
 * 该集合只描述持久化恢复数据，不决定模型重试、预算或网络策略。
 */
fun IssueRecoveryCore.retryableParticipantSnapshotIds(): Set<String> {
    val recoverableRunIds = activeOrRecoverableRuns.mapTo(mutableSetOf()) { it.id }
    val succeeded = successfulParticipantSnapshotIds()
    return participants.asSequence()
        .filter { it.runId in recoverableRunIds }
        .map { it.id }
        .filterNot { it in succeeded }
        .toSet()
}
