package com.elio.jianyu.ui.screens.resources

import com.elio.jianyu.data.ContextPurgeImpact
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.data.ContextSourceType

enum class ResourceLibrarySection {
    MATERIALS,
    PERSONAL_CONTEXTS,
}

data class ResourceIssueOption(
    val issueId: String,
    val title: String,
    val stages: List<ResourceStageOption>,
)

data class ResourceStageOption(
    val stageId: String,
    val title: String,
)

data class MaterialUiItem(
    val id: String,
    val issueId: String,
    val stageId: String?,
    val title: String,
    val sourceType: String,
    val sourceLocator: String?,
    val contentPreview: String,
    val content: String,
    val sourcePublishedAt: Long?,
    val sourceCapturedAt: Long?,
    val sensitive: Boolean,
    val lifecycle: ContextSourceLifecycle,
    val updatedAt: Long,
)

data class PersonalContextUiItem(
    val id: String,
    val title: String,
    val contentPreview: String,
    val content: String,
    val sensitive: Boolean,
    val lifecycle: ContextSourceLifecycle,
    val updatedAt: Long,
)

data class ResourceEditorDraft(
    val sourceType: ContextSourceType,
    val sourceId: String? = null,
    val issueId: String = "",
    val stageId: String? = null,
    val title: String = "",
    val sourceKind: String = "note",
    val sourceLocator: String = "",
    val content: String = "",
    val sensitive: Boolean = false,
    val expectedUpdatedAt: Long? = null,
)

data class ResourcePurgeConfirmation(
    val sourceType: ContextSourceType,
    val sourceId: String,
    val title: String,
    val expectedUpdatedAt: Long,
    val impact: ContextPurgeImpact,
)

sealed interface ResourcesUiState {
    data object Loading : ResourcesUiState

    data class Failure(
        val message: String,
    ) : ResourcesUiState

    data class Content(
        val section: ResourceLibrarySection = ResourceLibrarySection.MATERIALS,
        val query: String = "",
        val lifecycles: Set<ContextSourceLifecycle> = setOf(ContextSourceLifecycle.ACTIVE),
        val issues: List<ResourceIssueOption> = emptyList(),
        val materials: List<MaterialUiItem> = emptyList(),
        val personalContexts: List<PersonalContextUiItem> = emptyList(),
        val editor: ResourceEditorDraft? = null,
        val purgeConfirmation: ResourcePurgeConfirmation? = null,
        val partialFailure: String? = null,
        val operationInProgress: Boolean = false,
    ) : ResourcesUiState {
        val visibleMaterials: List<MaterialUiItem>
            get() = materials.filter { item ->
                item.lifecycle in lifecycles && (
                    query.isBlank() ||
                        item.title.contains(query, ignoreCase = true) ||
                        item.sourceType.contains(query, ignoreCase = true)
                    )
            }

        val visiblePersonalContexts: List<PersonalContextUiItem>
            get() = personalContexts.filter { item ->
                item.lifecycle in lifecycles && (
                    query.isBlank() || item.title.contains(query, ignoreCase = true)
                    )
            }
    }
}
