package com.elio.jianyu.ui.screens.dialog

import org.junit.Assert.assertEquals
import org.junit.Test

class DialogReadingOffsetTest {
    @Test
    fun restoresRelativeContentPositionAfterTextReflows() {
        val savedProgress = readingProgress(offset = 1000, itemHeight = 2000)

        assertEquals(
            1500,
            restoredReadingOffset(offset = 1000, progress = savedProgress, itemHeight = 3000, viewportHeight = 800),
        )
    }

    @Test
    fun shortAnswerCannotScrollPastItsOwnContainer() {
        assertEquals(
            0,
            restoredReadingOffset(offset = 1200, progress = 0.8f, itemHeight = 200, viewportHeight = 800),
        )
    }
}
