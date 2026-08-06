package com.elio.jianyu.lifecycle

import com.elio.jianyu.data.IssueLifecycleState
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/** 议题正式写入入口。只读能力也列入同一枚举，便于形成可审计白名单。 */
enum class IssueWriteAction {
    CREATE_RUN,
    DIRECTED_RESPONSE,
    CROSS_DISCUSSION,
    ADVANCE_STAGE,
    SAVE_DRAFT,
    CONFIRM_ARTIFACT,
    GENERATE_AUDIO,
    RECORD_CONTEXT_USAGE,
    READ_HISTORY,
    PLAY_AVAILABLE_AUDIO,
    ARCHIVE_ISSUE,
    RESUME_ISSUE,
    CREATE_RELATED_ISSUE,
    MOVE_TO_TRASH,
    RESTORE_FROM_TRASH,
    READ_PURGE_IMPACT,
    REQUEST_PURGE,
    READ_PURGE_STATUS,
    RETRY_PURGE,
    CANCEL_PURGE_BEFORE_FILE_DELETE,
}

data class IssueWriteAccessDecision(
    val allowedActions: Set<IssueWriteAction>,
) {
    fun allows(action: IssueWriteAction): Boolean = action in allowedActions
}

/**
 * 生命周期最终业务门禁。
 *
 * UI 和协调器可以提前隐藏或拒绝，但所有正式写入口仍需在持久化前重新读取生命周期并应用本策略。
 */
object IssueWriteAccessPolicy {
    private val activeActions = IssueWriteAction.entries.toSet() - setOf(
        IssueWriteAction.RESUME_ISSUE,
        IssueWriteAction.RESTORE_FROM_TRASH,
        IssueWriteAction.READ_PURGE_IMPACT,
        IssueWriteAction.REQUEST_PURGE,
        IssueWriteAction.READ_PURGE_STATUS,
        IssueWriteAction.RETRY_PURGE,
        IssueWriteAction.CANCEL_PURGE_BEFORE_FILE_DELETE,
    )

    private val archivedActions = setOf(
        IssueWriteAction.READ_HISTORY,
        IssueWriteAction.PLAY_AVAILABLE_AUDIO,
        IssueWriteAction.RESUME_ISSUE,
        IssueWriteAction.CREATE_RELATED_ISSUE,
        IssueWriteAction.MOVE_TO_TRASH,
    )

    private val trashedActions = setOf(
        IssueWriteAction.READ_PURGE_IMPACT,
        IssueWriteAction.RESTORE_FROM_TRASH,
        IssueWriteAction.REQUEST_PURGE,
    )

    private val purgeActions = setOf(
        IssueWriteAction.READ_PURGE_STATUS,
        IssueWriteAction.RETRY_PURGE,
        IssueWriteAction.CANCEL_PURGE_BEFORE_FILE_DELETE,
    )

    fun evaluate(
        state: IssueLifecycleState,
        purgeRequested: Boolean,
    ): IssueWriteAccessDecision {
        if (purgeRequested) return IssueWriteAccessDecision(purgeActions)
        val allowed = when (state) {
            IssueLifecycleState.ACTIVE -> activeActions
            IssueLifecycleState.ARCHIVED -> archivedActions
            IssueLifecycleState.TRASHED -> trashedActions
        }
        return IssueWriteAccessDecision(allowed)
    }
}

object ResumeChangeNotePolicy {
    const val NO_CHANGE_NOTE = "暂无变化"

    fun isConfirmed(
        changeNote: String,
        noChangeConfirmed: Boolean,
    ): Boolean = changeNote.isNotBlank() || noChangeConfirmed

    fun normalized(
        changeNote: String,
        noChangeConfirmed: Boolean,
    ): String {
        require(isConfirmed(changeNote, noChangeConfirmed)) { "必须填写现在有什么变化或明确选择暂无变化" }
        return if (changeNote.isBlank()) NO_CHANGE_NOTE else changeNote.trim()
    }
}

data class PurgeFileImpact(
    val relativePath: String,
    val sizeBytes: Long,
) {
    init {
        require(relativePath.isNotBlank()) { "受控文件相对路径不能为空" }
        require(sizeBytes >= 0L) { "文件字节数不能为负数" }
    }
}

data class IssuePurgeImpactSnapshot(
    val issueId: String,
    val databaseCounts: Map<String, Long>,
    val formalFiles: List<PurgeFileImpact>,
    val pendingWorkNames: List<String>,
    val missingAssetIds: List<String>,
    val orphanRelativePaths: List<String>,
    val relatedIssueCount: Int,
    val externalObjectCount: Int,
) {
    init {
        require(issueId.isNotBlank()) { "议题 ID 不能为空" }
        require(databaseCounts.keys.all(String::isNotBlank)) { "影响对象类型不能为空" }
        require(databaseCounts.values.all { it >= 0L }) { "影响对象数量不能为负数" }
        require(relatedIssueCount >= 0) { "关联议题数量不能为负数" }
        require(externalObjectCount >= 0) { "外部对象数量不能为负数" }
    }

    val databaseObjectCount: Long
        get() = databaseCounts.values.fold(0L, ::safeAdd)

    val formalFileCount: Int
        get() = formalFiles.size

    val formalFileBytes: Long
        get() = formalFiles.fold(0L) { total, file -> safeAdd(total, file.sizeBytes) }
}

object IssuePurgeImpactHasher {
    fun hash(snapshot: IssuePurgeImpactSnapshot): String {
        val fields = buildList {
            add("issue:${snapshot.issueId}")
            snapshot.databaseCounts.toSortedMap().forEach { (type, count) ->
                add("db:$type:$count")
            }
            snapshot.formalFiles
                .sortedWith(compareBy(PurgeFileImpact::relativePath, PurgeFileImpact::sizeBytes))
                .forEach { add("file:${it.relativePath}:${it.sizeBytes}") }
            snapshot.pendingWorkNames.sorted().forEach { add("work:$it") }
            snapshot.missingAssetIds.sorted().forEach { add("missing:$it") }
            snapshot.orphanRelativePaths.sorted().forEach { add("orphan:$it") }
            add("related:${snapshot.relatedIssueCount}")
            add("external:${snapshot.externalObjectCount}")
        }
        return stableSha256(fields)
    }
}

data class IssueArchiveImpact(
    val issueId: String,
    val currentStageTitle: String?,
    val stageCount: Int,
    val runCount: Int,
    val activeWorkCount: Int,
    val pendingMessageCount: Int,
    val draftCount: Int,
    val artifactCount: Int,
    val audioAssetCount: Int,
    val audioPendingCount: Int,
) {
    init {
        require(issueId.isNotBlank()) { "议题 ID 不能为空" }
        require(
            listOf(
                stageCount,
                runCount,
                activeWorkCount,
                pendingMessageCount,
                draftCount,
                artifactCount,
                audioAssetCount,
                audioPendingCount,
            ).all { it >= 0 },
        ) { "归档影响数量不能为负数" }
    }

    val hasActiveWork: Boolean
        get() = activeWorkCount > 0 || pendingMessageCount > 0 || audioPendingCount > 0
}

object IssueArchiveSummaryFactory {
    fun create(
        impact: IssueArchiveImpact,
        userNote: String,
    ): String = buildString {
        appendLine("## 归档简报")
        appendLine()
        appendLine("- 当前阶段：${impact.currentStageTitle?.takeIf(String::isNotBlank) ?: "尚无阶段"}")
        appendLine("- Stage 数量：${impact.stageCount}")
        appendLine("- 运行数量：${impact.runCount}")
        appendLine("- 未完成任务：${impact.activeWorkCount}")
        appendLine("- Pending Message：${impact.pendingMessageCount}")
        appendLine("- 草稿数量：${impact.draftCount}")
        appendLine("- 正式成果数量：${impact.artifactCount}")
        appendLine("- 音频资产数量：${impact.audioAssetCount}")
        appendLine("- 待处理音频：${impact.audioPendingCount}")
        append("- 用户备注：${userNote.trim().ifBlank { "无" }}")
    }
}

internal fun stableSha256(fields: List<String>): String {
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

private fun safeAdd(
    current: Long,
    increment: Long,
): Long = try {
    Math.addExact(current, increment)
} catch (_: ArithmeticException) {
    Long.MAX_VALUE
}
