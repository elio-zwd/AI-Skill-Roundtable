package com.elio.jianyu.data

/**
 * 首页持续对话的兼容上下文入口。
 *
 * 正式 ExecutionRun 继续使用 prepareExecutionContext + ExecutionRunCoordinator；
 * 旧对话桥没有 formal Run，因此这里只允许 Repository 在同一事务内完成验证并记录 runId=null 的使用快照。
 */
interface JianyuConversationContextUsageRepository {
    suspend fun prepareAndRecordConversationContextUsage(
        command: PrepareExecutionContextCommand,
        usageScopeId: String,
    ): RepositoryResult<PreparedExecutionContext>
}

suspend fun JianyuRepository.prepareAndRecordConversationContextUsage(
    command: PrepareExecutionContextCommand,
    usageScopeId: String,
): RepositoryResult<PreparedExecutionContext> =
    (this as? JianyuConversationContextUsageRepository)
        ?.prepareAndRecordConversationContextUsage(command, usageScopeId)
        ?: RepositoryResult.Failure(
            RepositoryError.CompatibilityFailure(
                "prepare_conversation_context_usage",
                "conversation_context_usage_not_supported",
            ),
        )
