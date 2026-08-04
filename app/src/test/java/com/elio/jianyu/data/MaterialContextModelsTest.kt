package com.elio.jianyu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaterialContextModelsTest {
    @Test
    fun contentHashUsesUtf8Sha256AndNormalizedLineEndings() {
        val windows = ContextContentHasher.hash("第一行\r\n第二行\r第三行")
        val unix = ContextContentHasher.hash("第一行\n第二行\n第三行")

        assertEquals(unix, windows)
        assertEquals(64, windows.length)
    }

    @Test
    fun personalContextIsNotSelectedByDefault() {
        val draft = ContextSelectionDraft(
            issueId = "issue-1",
            stageId = "stage-1",
            runId = "run-1",
            baseContextCharacters = 120,
        )

        assertTrue(draft.items.isEmpty())
        assertFalse(draft.confirmed)
    }

    @Test
    fun exactCharacterLimitPassesButOverflowFailsWithoutTruncation() {
        val exact = ContextSelectionValidator.validate(
            baseContextCharacters = 23_999,
            items = listOf(selection(content = "一")),
        )
        val overflow = ContextSelectionValidator.validate(
            baseContextCharacters = 24_000,
            items = listOf(selection(content = "一")),
        )

        assertTrue(exact is ContextPreparationResult.Ready)
        assertEquals(24_000, (exact as ContextPreparationResult.Ready).totalCharacters)
        assertTrue(overflow is ContextPreparationResult.Invalid)
        assertEquals(
            ContextValidationError.CONTEXT_TOO_LARGE,
            (overflow as ContextPreparationResult.Invalid).errors.single(),
        )
        assertEquals("一", overflow.items.single().content)
    }

    @Test
    fun duplicateSourceWithDifferentHashIsRejected() {
        val result = ContextSelectionValidator.validate(
            baseContextCharacters = 0,
            items = listOf(
                selection(content = "旧正文", confirmationOrder = 0),
                selection(content = "新正文", confirmationOrder = 1),
            ),
        )

        assertTrue(result is ContextPreparationResult.Invalid)
        assertEquals(
            ContextValidationError.CONTENT_HASH_MISMATCH,
            (result as ContextPreparationResult.Invalid).errors.single(),
        )
    }

    private fun selection(
        content: String,
        confirmationOrder: Int = 0,
    ): ConfirmedContextItem = ConfirmedContextItem(
        sourceType = ContextSourceType.MATERIAL,
        sourceId = "material-1",
        title = "资料",
        content = content,
        contentHash = ContextContentHasher.hash(content),
        expectedSourceHash = ContextContentHasher.hash("资料库当前正文"),
        expectedSourceUpdatedAt = 1_000L,
        confirmationOrder = confirmationOrder,
        userConfirmedAt = 2_000L,
        networkAllowed = true,
        sensitive = false,
        sensitiveConfirmed = false,
    )
}
