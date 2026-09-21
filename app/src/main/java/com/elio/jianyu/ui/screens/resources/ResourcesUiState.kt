package com.elio.jianyu.ui.screens.resources

import com.elio.jianyu.data.ContextPurgeImpact
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.result.ArtifactLibraryAggregator
import com.elio.jianyu.result.ArtifactLibraryItem
import com.elio.jianyu.result.ArtifactLibrarySnapshot
import com.elio.jianyu.result.ArtifactType

data class ResourceOverviewUiState(
    val materialCount: Int,
    val artifactCount: Int,
    val recentMaterials: List<MaterialUiItem>,
    val recentArtifacts: List<ArtifactLibraryItem>,
    val materialsLoading: Boolean,
    val artifactsLoading: Boolean,
    val materialsUnavailable: Boolean,
    val artifactsUnavailable: Boolean,
    val message: String?,
)

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
    val importedFileSummary: String? = null,
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

sealed interface ArtifactLibraryUiState {
    data object Loading : ArtifactLibraryUiState

    data object Empty : ArtifactLibraryUiState

    data class Content(
        val snapshot: ArtifactLibrarySnapshot,
        val query: String = "",
        val selectedTypes: Set<ArtifactType> = emptySet(),
        val includeHistory: Boolean = false,
        val selectedArtifactId: String? = null,
    ) : ArtifactLibraryUiState {
        val visibleItems: List<ArtifactLibraryItem>
            get() = ArtifactLibraryAggregator.visibleItems(
                snapshot = snapshot,
                query = query,
                types = selectedTypes,
                includeHistory = includeHistory,
            )

        val selectedItem: ArtifactLibraryItem?
            get() = snapshot.items.firstOrNull { it.artifactId == selectedArtifactId }
    }

    data class PartialFailure(
        val content: Content,
        val errorCode: String,
    ) : ArtifactLibraryUiState

    data class Failure(
        val errorCode: String,
    ) : ArtifactLibraryUiState
}

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
        val artifactLibrary: ArtifactLibraryUiState = ArtifactLibraryUiState.Loading,
        val editor: ResourceEditorDraft? = null,
        val purgeConfirmation: ResourcePurgeConfirmation? = null,
        val partialFailure: String? = null,
        val operationInProgress: Boolean = false,
        val selectedMaterialId: String? = null,
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

        val selectedMaterial: MaterialUiItem?
            get() = materials.firstOrNull { it.id == selectedMaterialId }
    }
}

fun buildResourceOverview(
    resourcesState: ResourcesUiState,
    artifactState: ArtifactLibraryUiState,
): ResourceOverviewUiState {
    val content = resourcesState as? ResourcesUiState.Content
    val materials = content?.materials.orEmpty()
        .filter { it.lifecycle != ContextSourceLifecycle.PURGED }
    val artifactContent = when (artifactState) {
        is ArtifactLibraryUiState.Content -> artifactState
        is ArtifactLibraryUiState.PartialFailure -> artifactState.content
        ArtifactLibraryUiState.Loading,
        ArtifactLibraryUiState.Empty,
        is ArtifactLibraryUiState.Failure -> null
    }
    val artifacts = artifactContent?.snapshot?.items.orEmpty()
        .filter(ArtifactLibraryItem::latest)

    return ResourceOverviewUiState(
        materialCount = materials.size,
        artifactCount = artifacts.size,
        recentMaterials = materials
            .filter { it.lifecycle == ContextSourceLifecycle.ACTIVE }
            .sortedWith(compareByDescending<MaterialUiItem> { it.updatedAt }.thenBy { it.id })
            .take(2),
        recentArtifacts = artifacts
            .sortedWith(compareByDescending<ArtifactLibraryItem> { it.confirmedAt }.thenBy { it.artifactId })
            .take(2),
        materialsLoading = resourcesState is ResourcesUiState.Loading,
        artifactsLoading = artifactState is ArtifactLibraryUiState.Loading,
        materialsUnavailable = resourcesState is ResourcesUiState.Failure,
        artifactsUnavailable = artifactState is ArtifactLibraryUiState.Failure,
        message = listOfNotNull(
            content?.partialFailure,
            (resourcesState as? ResourcesUiState.Failure)?.message,
            if (artifactState is ArtifactLibraryUiState.Failure) {
                "成果库暂时无法读取，资料仍可正常使用。"
            } else {
                null
            },
            if (artifactState is ArtifactLibraryUiState.PartialFailure) {
                "部分成果未能读取，已显示成功恢复的内容。"
            } else {
                null
            },
        ).distinct().joinToString("\n").ifBlank { null },
    )
}
