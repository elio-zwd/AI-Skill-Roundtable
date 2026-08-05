package com.elio.jianyu.ui.screens.resources

import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.result.ArtifactLibraryItem
import com.elio.jianyu.result.ArtifactLibrarySnapshot
import com.elio.jianyu.result.ArtifactType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtifactLibraryUiStateTest {
    @Test
    fun artifactFailureDoesNotHideMaterialLibraryContent() {
        val state = ResourcesUiState.Content(
            materials = listOf(material("material-1")),
            artifactLibrary = ArtifactLibraryUiState.Failure("artifact_load_failed"),
        )

        assertEquals(listOf("material-1"), state.visibleMaterials.map { it.id })
        assertTrue(state.artifactLibrary is ArtifactLibraryUiState.Failure)
    }

    @Test
    fun artifactContentFiltersLatestTitleSummaryAndType() {
        val content = ArtifactLibraryUiState.Content(
            snapshot = ArtifactLibrarySnapshot(
                items = listOf(
                    artifact("latest-action", "行动计划", "第一步验证", ArtifactType.ACTION_PLAN),
                    artifact(
                        "old-action",
                        "旧行动计划",
                        "历史",
                        ArtifactType.ACTION_PLAN,
                        latest = false,
                    ),
                    artifact("note", "知识笔记", "传感器", ArtifactType.KNOWLEDGE_NOTE),
                ),
                revisionProblems = emptyList(),
            ),
            query = "验证",
            selectedTypes = setOf(ArtifactType.ACTION_PLAN),
        )

        assertEquals(listOf("latest-action"), content.visibleItems.map { it.artifactId })
    }

    @Test
    fun partialFailureKeepsSuccessfullyRecoveredArtifacts() {
        val content = ArtifactLibraryUiState.Content(
            snapshot = ArtifactLibrarySnapshot(
                items = listOf(
                    artifact("artifact-1", "阶段总结", "摘要", ArtifactType.GENERAL_SUMMARY),
                ),
                revisionProblems = emptyList(),
            ),
        )
        val state = ArtifactLibraryUiState.PartialFailure(
            content = content,
            errorCode = "artifact_partial_load_failed",
        )

        assertEquals(listOf("artifact-1"), state.content.visibleItems.map { it.artifactId })
        assertEquals("artifact_partial_load_failed", state.errorCode)
    }

    @Test
    fun selectingArtifactOnlyChangesArtifactDetailState() {
        val item = artifact("artifact-1", "阶段总结", "摘要", ArtifactType.GENERAL_SUMMARY)
        val content = ArtifactLibraryUiState.Content(
            snapshot = ArtifactLibrarySnapshot(listOf(item), emptyList()),
            selectedArtifactId = item.artifactId,
        )

        assertEquals(item, content.selectedItem)
        assertEquals(listOf(item), content.visibleItems)
    }

    private fun material(id: String) = MaterialUiItem(
        id = id,
        issueId = "issue-1",
        stageId = "stage-1",
        title = "资料",
        sourceType = "note",
        sourceLocator = null,
        contentPreview = "摘要",
        content = "正文",
        sourcePublishedAt = null,
        sourceCapturedAt = 1,
        sensitive = false,
        lifecycle = ContextSourceLifecycle.ACTIVE,
        updatedAt = 1,
    )

    private fun artifact(
        id: String,
        title: String,
        summary: String,
        type: ArtifactType,
        latest: Boolean = true,
    ) = ArtifactLibraryItem(
        artifactId = id,
        issueId = "issue-1",
        issueTitle = "议题",
        stageId = "stage-1",
        stageTitle = "阶段",
        title = title,
        contentSummary = summary,
        artifactType = type,
        rawArtifactType = type.storageValue,
        confirmedAt = 1,
        revisionOfArtifactId = null,
        revisionNumber = 1,
        latest = latest,
    )
}
