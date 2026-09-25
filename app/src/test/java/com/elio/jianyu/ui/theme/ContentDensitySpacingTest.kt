package com.elio.jianyu.ui.theme

import androidx.compose.ui.unit.dp
import com.elio.jianyu.ui.settings.ContentDensityMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentDensitySpacingTest {
    @Test
    fun densityModesUseLayoutTokensWithoutChangingNavigationTouchHeight() {
        val compact = spacingForContentDensity(ContentDensityMode.COMPACT)
        val standard = spacingForContentDensity(ContentDensityMode.STANDARD)
        val comfortable = spacingForContentDensity(ContentDensityMode.COMFORTABLE)

        assertEquals(12.dp, compact.screenHorizontal)
        assertEquals(16.dp, standard.screenHorizontal)
        assertEquals(24.dp, comfortable.screenHorizontal)
        assertEquals(8.dp, compact.small)
        assertEquals(12.dp, standard.small)
        assertEquals(16.dp, comfortable.small)
        assertEquals(80.dp, compact.bottomNavigationHeight)
        assertEquals(80.dp, comfortable.bottomNavigationHeight)
    }
}
