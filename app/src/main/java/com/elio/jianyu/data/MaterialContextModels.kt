package com.elio.jianyu.data

import com.elio.jianyu.execution.ExecutionContextContribution
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

enum class ContextSourceType(val storageValue: String) {
    MATERIAL("material"),
    PERSONAL_CONTEXT("personal_context"),
}

data class Material(
    val id: String,
    val issueId: String,
    val stageId: String?,
    val title: String,
    val sourceType: String,
    val sourceLocator: String?,
    val content: String,
    val contentHash: String,
    val sourcePublishedAt: Long?,
    val sourceCapturedAt: Long?,
    val sensitive: Boolean,
    val lifecycle: ContextSourceLifecycle,
    val createdAt: Long,
    val updatedAt: Long,
)

data class PersonalContext(
    val id: String,
    val title: String,
    val content: String,
    val contentHash: String,
    val sensitive: Boolean,
    val lifecycle: ContextSourceLifecycle,
    val createdAt: Long,
    val updatedAt: Long,
)

data class CreateMaterialCommand(
    val id: String,
    val issueId: String,
    val stageId: String? = null,
    val title: String,
    val sourceType: String,
    val sourceLocator: String? = null,
    val content: String,
    val sourcePublishedAt: Long? = null,
    val sourceCapturedAt: Long? = null,
    val sensitive: Boolean = false,
    val createdAt: Long,
)

data class UpdateMaterialCommand(
    val id: String,
    val title: String,
    val sourceType: String,
    val sourceLocator: String? = null,
    val content: String,
    val sourcePublishedAt: Long? = null,
    val sourceCapturedAt: Long? = null,
    val sensitive: Boolean,
    val expectedUpdatedAt: Long,
    val updatedAt: Long,
)

data class MaterialFilter(
    val query: String = "",
    val issueId: String? = null,
    val stageId: String? = null,
    val sourceType: String? = null,
    val lifecycles: Set<ContextSourceLifecycle> = setOf(ContextSourceLifecycle.ACTIVE),
)

data class ChangeMaterialLifecycleCommand(
    val materialId: String,
    val expectedUpdatedAt: Long,
    val target: ContextSourceLifecycle,
    val changedAt: Long,
)

data class PurgeMaterialCommand(
    val materialId: String,
    val expectedUpdatedAt: Long,
    val confirmedAt: Long,
)

data class CreatePersonalContextCommand(
    val id: String,
    val title: String,
    val content: String,
    val sensitive: Boolean = false,
    val createdAt: Long,
)

data class UpdatePersonalContextCommand(
    val id: String,
    val title: String,
    val content: String,
    val sensitive: Boolean,
    val expectedUpdatedAt: Long,
    val updatedAt: Long,
)

data class PersonalContextFilter(
    val query: String = "",
    val lifecycles: Set<ContextSourceLifecycle> = setOf(ContextSourceLifecycle.ACTIVE),
)

data class ChangePersonalContextLifecycleCommand(
    val personalContextId: String,
    val expectedUpdatedAt: Long,
    val target: ContextSourceLifecycle,
    val changedAt: Long,
)

data class PurgePersonalContextCommand(
    val personalContextId: String,
    val expectedUpdatedAt: Long,
    val confirmedAt: Long,
)

data class ContextPurgeImpact(
    val sourceType: ContextSourceType,
    val sourceId: String,
    val issueCount: Int,
    val stageCount: Int,
    val usageSnapshotCount: Int,
    val runCount: Int,
)

data class ConfirmedContextItem(
    val sourceType: ContextSourceType,
    val sourceId: String,
    val title: String,
    val sourceKind: String = "",
    val sourceLocator: String? = null,
    val sourcePublishedAt: Long? = null,
    val sourceCapturedAt: Long? = null,
    val content: String,
    val contentHash: String,
    val expectedSourceHash: String,
    val expectedSourceUpdatedAt: Long,
    val confirmationOrder: Int,
    val userConfirmedAt: Long,
    val networkAllowed: Boolean,
    val sensitive: Boolean,
    val sensitiveConfirmed: Boolean,
)

data class ContextSelectionDraft(
    val issueId: String,
    val stageId: String,
    val runId: String,
    val baseContextCharacters: Int,
    val items: List<ConfirmedContextItem> = emptyList(),
    val confirmed: Boolean = false,
)

data class PrepareExecutionContextCommand(
    val draft: ContextSelectionDraft,
    val preparedAt: Long,
    val maxContextCharacters: Int = MAX_EXECUTION_CONTEXT_CHARACTERS,
)

enum class ContextValidationError(val code: String) {
    CONFIRMATION_REQUIRED("confirmation_required"),
    SOURCE_NOT_FOUND("source_not_found"),
    SOURCE_DISABLED("source_disabled"),
    SOURCE_ARCHIVED("source_archived"),
    SOURCE_DELETED("source_deleted"),
    SOURCE_PURGED("source_purged"),
    SOURCE_STALE("source_stale"),
    CONTENT_HASH_MISMATCH("content_hash_mismatch"),
    DUPLICATE_SOURCE("duplicate_source"),
    NETWORK_NOT_ALLOWED("network_not_allowed"),
    SENSITIVE_CONFIRMATION_REQUIRED("sensitive_confirmation_required"),
    CONTEXT_TOO_LARGE("context_too_large"),
    CONTENT_EMPTY("content_empty"),
    USAGE_SNAPSHOT_CONFLICT("usage_snapshot_conflict"),
}

sealed interface ContextPreparationResult {
    val items: List<ConfirmedContextItem>

    data class Ready(
        override val items: List<ConfirmedContextItem>,
        val contributions: List<ExecutionContextContribution>,
        val totalCharacters: Int,
        val remainingCharacters: Int,
    ) : ContextPreparationResult

    data class Invalid(
        override val items: List<ConfirmedContextItem>,
        val errors: List<ContextValidationError>,
        val totalCharacters: Int,
    ) : ContextPreparationResult
}

data class ContextSourceExpectation(
    val sourceType: ContextSourceType,
    val sourceId: String,
    val expectedUpdatedAt: Long,
    val expectedContentHash: String,
)

data class ContextUsageWriteSet(
    val materials: List<MaterialUsageSnapshotEntity> = emptyList(),
    val personalContexts: List<PersonalContextUsageSnapshotEntity> = emptyList(),
    val sourceExpectations: List<ContextSourceExpectation> = emptyList(),
) {
    val isEmpty: Boolean
        get() = materials.isEmpty() && personalContexts.isEmpty()

    fun sorted(): ContextUsageWriteSet = copy(
        materials = materials.sortedWith(compareBy({ it.userConfirmedAt }, { it.materialReferenceId }, { it.id })),
        personalContexts = personalContexts.sortedWith(
            compareBy({ it.userConfirmedAt }, { it.personalContextEntryId }, { it.id }),
        ),
        sourceExpectations = sourceExpectations.sortedWith(
            compareBy({ it.sourceType.storageValue }, { it.sourceId }),
        ),
    )
}

data class PreparedExecutionContext(
    val preparation: ContextPreparationResult.Ready,
    val usage: ContextUsageWriteSet,
)

data class ContextUsageSnapshot(
    val sourceType: ContextSourceType,
    val sourceId: String?,
    val title: String?,
    val content: String?,
    val contentHash: String?,
    val contentState: SnapshotContentState,
    val userConfirmedAt: Long,
    val usedAt: Long,
    val networkAllowed: Boolean,
    val sensitive: Boolean,
)

object ContextContentHasher {
    fun normalize(content: String): String = content
        .replace("\r\n", "\n")
        .replace('\r', '\n')

    fun hash(content: String): String {
        val bytes = normalize(content).toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

object ContextSelectionValidator {
    fun validate(
        baseContextCharacters: Int,
        items: List<ConfirmedContextItem>,
        maxContextCharacters: Int = MAX_EXECUTION_CONTEXT_CHARACTERS,
    ): ContextPreparationResult {
        require(baseContextCharacters >= 0)
        require(maxContextCharacters > 0)

        val sorted = items.sortedWith(
            compareBy<ConfirmedContextItem>({ it.confirmationOrder }, { it.userConfirmedAt })
                .thenBy { it.sourceType.storageValue }
                .thenBy { it.sourceId },
        )
        val errors = linkedSetOf<ContextValidationError>()
        sorted.forEach { item ->
            if (item.content.isBlank()) errors += ContextValidationError.CONTENT_EMPTY
            if (item.contentHash != ContextContentHasher.hash(item.content)) {
                errors += ContextValidationError.CONTENT_HASH_MISMATCH
            }
            if (!item.networkAllowed) errors += ContextValidationError.NETWORK_NOT_ALLOWED
            if (item.sensitive && !item.sensitiveConfirmed) {
                errors += ContextValidationError.SENSITIVE_CONFIRMATION_REQUIRED
            }
        }
        sorted.groupBy { it.sourceType to it.sourceId }.values
            .filter { it.size > 1 }
            .forEach { duplicates ->
                if (duplicates.map { it.contentHash }.distinct().size > 1) {
                    errors += ContextValidationError.CONTENT_HASH_MISMATCH
                } else {
                    errors += ContextValidationError.DUPLICATE_SOURCE
                }
            }

        val total = baseContextCharacters + sorted.sumOf { it.content.length }
        if (total > maxContextCharacters) errors += ContextValidationError.CONTEXT_TOO_LARGE

        return if (errors.isEmpty()) {
            ContextPreparationResult.Ready(
                items = sorted,
                contributions = sorted.map { item ->
                    ExecutionContextContribution(
                        sourceId = item.sourceId,
                        sourceType = item.sourceType.storageValue,
                        content = item.content,
                        contentHash = item.contentHash,
                        userConfirmedAt = item.userConfirmedAt,
                        networkAllowed = item.networkAllowed,
                        sensitive = item.sensitive,
                    )
                },
                totalCharacters = total,
                remainingCharacters = maxContextCharacters - total,
            )
        } else {
            ContextPreparationResult.Invalid(
                items = sorted,
                errors = errors.toList(),
                totalCharacters = total,
            )
        }
    }
}

internal fun MaterialReferenceEntity.toDomain(): Material = Material(
    id = id,
    issueId = issueId,
    stageId = stageId,
    title = title,
    sourceType = sourceType,
    sourceLocator = sourceLocator,
    content = content,
    contentHash = contentHash,
    sourcePublishedAt = sourcePublishedAt,
    sourceCapturedAt = sourceCapturedAt,
    sensitive = sensitive,
    lifecycle = lifecycleState,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

internal fun PersonalContextEntryEntity.toDomain(): PersonalContext = PersonalContext(
    id = id,
    title = title,
    content = content,
    contentHash = contentHash,
    sensitive = sensitive,
    lifecycle = lifecycleState,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

const val MAX_EXECUTION_CONTEXT_CHARACTERS = 24_000
const val PURGED_CONTEXT_PLACEHOLDER = "内容已清除"
