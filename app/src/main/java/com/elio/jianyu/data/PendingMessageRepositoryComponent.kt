package com.elio.jianyu.data

/**
 * Pending 领域消息的唯一原位更新入口。
 *
 * 该组件不负责模型调度，只保证迟到流式片段不能覆盖已完成消息。
 */
internal class PendingMessageRepositoryComponent(
    private val transactions: JianyuRepositoryTransactions
) {
    suspend fun updatePendingDomainMessage(
        command: UpdatePendingDomainMessageCommand
    ): RepositoryResult<Message> {
        return transactions.transaction("update_pending_domain_message") {
            require(command.messageId > 0L)
            require(command.issueId.isNotBlank() && command.stageId.isNotBlank())

            val existing = getMessage(command.messageId)
                ?: return@transaction RepositoryResult.Failure(
                    RepositoryError.NotFound("message", command.messageId.toString())
                )
            if (!matchesDomainIdentity(existing, command)) {
                return@transaction RepositoryResult.Failure(
                    RepositoryError.IdempotencyConflict(
                        "update_pending_domain_message",
                        command.messageId.toString()
                    )
                )
            }
            if (!existing.isPending) {
                return@transaction if (!command.keepPending && existing.text == command.text) {
                    RepositoryResult.Success(existing, idempotent = true)
                } else {
                    RepositoryResult.Failure(
                        RepositoryError.InvalidState(
                            "update_pending_domain_message",
                            "message_already_completed"
                        )
                    )
                }
            }
            if (existing.text == command.text && existing.isPending == command.keepPending) {
                return@transaction RepositoryResult.Success(existing, idempotent = true)
            }

            val changed = compareAndSetPendingDomainMessage(
                messageId = command.messageId,
                issueId = command.issueId,
                stageId = command.stageId,
                executionRunId = command.executionRunId,
                participantSnapshotId = command.participantSnapshotId,
                text = command.text,
                keepPending = command.keepPending
            )
            if (changed != 1) {
                val latest = getMessage(command.messageId)
                    ?: return@transaction RepositoryResult.Failure(
                        RepositoryError.NotFound("message", command.messageId.toString())
                    )
                return@transaction if (
                    matchesDomainIdentity(latest, command) &&
                    latest.text == command.text &&
                    latest.isPending == command.keepPending
                ) {
                    RepositoryResult.Success(latest, idempotent = true)
                } else {
                    RepositoryResult.Failure(
                        RepositoryError.InvalidState(
                            "update_pending_domain_message",
                            "pending_compare_and_set_failed"
                        )
                    )
                }
            }

            RepositoryResult.Success(
                getMessage(command.messageId)
                    ?: throw IllegalStateException("Pending message update disappeared")
            )
        }
    }

    private fun matchesDomainIdentity(
        message: Message,
        command: UpdatePendingDomainMessageCommand
    ): Boolean {
        return message.id == command.messageId &&
            message.issueId == command.issueId &&
            message.stageId == command.stageId &&
            message.executionRunId == command.executionRunId &&
            message.participantSnapshotId == command.participantSnapshotId
    }
}
