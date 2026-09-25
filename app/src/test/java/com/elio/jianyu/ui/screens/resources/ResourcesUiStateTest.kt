package com.elio.jianyu.ui.screens.resources

import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.result.ArtifactLibraryItem
import com.elio.jianyu.result.ArtifactLibrarySnapshot
import com.elio.jianyu.result.ArtifactType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourcesUiStateTest {
    @Test
    fun activeFilterHidesArchivedAndDeletedItems() {
        val state = ResourcesUiState.Content(
            materials = listOf(
                material("active", ContextSourceLifecycle.ACTIVE),
                material("archived", ContextSourceLifecycle.ARCHIVED),
                material("deleted", ContextSourceLifecycle.DELETED),
            ),
        )

        assertEquals(listOf("active"), state.visibleMaterials.map { it.id })
    }

    @Test
    fun querySearchesTitleAndSourceTypeWithoutUsingBody() {
        val state = ResourcesUiState.Content(
            query = "url",
            materials = listOf(
                material("url", ContextSourceLifecycle.ACTIVE, sourceType = "url"),
                material("body-only", ContextSourceLifecycle.ACTIVE, content = "url"),
            ),
        )

        assertEquals(listOf("url"), state.visibleMaterials.map { it.id })
        assertTrue(state.visiblePersonalContexts.isEmpty())
    }

    @Test
    fun overviewUsesRealCountsAndStableRecentOrdering() {
        val materials = listOf(
            material("older", ContextSourceLifecycle.ACTIVE, updatedAt = 10),
            material("newer", ContextSourceLifecycle.ACTIVE, updatedAt = 20),
            material("purged", ContextSourceLifecycle.PURGED, updatedAt = 30),
        )
        val artifacts = listOf(
            artifact("old", confirmedAt = 10),
            artifact("new", confirmedAt = 20),
            artifact("history", confirmedAt = 30, latest = false),
        )

        val overview = buildResourceOverview(
            resourcesState = ResourcesUiState.Content(materials = materials),
            artifactState = ArtifactLibraryUiState.Content(
                ArtifactLibrarySnapshot(artifacts, emptyList()),
            ),
        )

        assertEquals(2, overview.materialCount)
        assertEquals(2, overview.artifactCount)
        assertEquals(listOf("newer", "older"), overview.recentMaterials.map { it.id })
        assertEquals(listOf("new", "old"), overview.recentArtifacts.map { it.artifactId })
    }

    @Test
    fun overviewKeepsSuccessfulSideVisibleWhenOtherLibraryFails() {
        val overview = buildResourceOverview(
            resourcesState = ResourcesUiState.Content(
                materials = listOf(material("kept", ContextSourceLifecycle.ACTIVE)),
            ),
            artifactState = ArtifactLibraryUiState.Failure("storage"),
        )

        assertEquals(1, overview.materialCount)
        assertTrue(overview.artifactsUnavailable)
        assertEquals("成果库暂时无法读取，资料仍可正常使用。", overview.message)
    }

    @Test
    fun overviewMarksCountsUnknownWhileLoading() {
        val overview = buildResourceOverview(
            resourcesState = ResourcesUiState.Loading,
            artifactState = ArtifactLibraryUiState.Loading,
        )

        assertTrue(overview.materialsLoading)
        assertTrue(overview.artifactsLoading)
    }

    private fun material(
        id: String,
        lifecycle: ContextSourceLifecycle,
        sourceType: String = "note",
        content: String = "正文",
        updatedAt: Long = 1L,
    ) = MaterialUiItem(
        id = id,
        issueId = "issue-1",
        stageId = null,
        title = id,
        sourceType = sourceType,
        sourceLocator = null,
        contentPreview = content,
        content = content,
        sourcePublishedAt = null,
        sourceCapturedAt = 1L,
        sensitive = false,
        lifecycle = lifecycle,
        updatedAt = updatedAt,
    )

    private fun artifact(
        id: String,
        confirmedAt: Long,
        latest: Boolean = true,
    ) = ArtifactLibraryItem(
        artifactId = id,
        issueId = "issue-1",
        issueTitle = "会话",
        stageId = "stage-1",
        stageTitle = "节点",
        title = id,
        contentSummary = "摘要",
        artifactType = ArtifactType.GENERAL_SUMMARY,
        rawArtifactType = ArtifactType.GENERAL_SUMMARY.storageValue,
        confirmedAt = confirmedAt,
        revisionOfArtifactId = null,
        revisionNumber = 1,
        latest = latest,
    )
}
