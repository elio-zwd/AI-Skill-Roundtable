package com.elio.jianyu.ui.screens.resources

import com.elio.jianyu.data.ContextSourceLifecycle
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

    private fun material(
        id: String,
        lifecycle: ContextSourceLifecycle,
        sourceType: String = "note",
        content: String = "正文",
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
        updatedAt = 1L,
    )
}
