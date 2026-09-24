package com.elio.jianyu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.elio.jianyu.ui.settings.ContentDensityMode

@Immutable
data class SkillRoundtableSpacing(
    val screenHorizontal: Dp = 16.dp,
    val tiny: Dp = 4.dp,
    val compact: Dp = 8.dp,
    val small: Dp = 12.dp,
    val medium: Dp = 16.dp,
    val large: Dp = 24.dp,
    val xLarge: Dp = 32.dp,
    val bottomNavigationHeight: Dp = 80.dp,
)

internal val LocalSkillRoundtableSpacing = staticCompositionLocalOf {
    SkillRoundtableSpacing()
}

val MaterialTheme.skillRoundtableSpacing: SkillRoundtableSpacing
    @Composable get() = LocalSkillRoundtableSpacing.current


internal fun spacingForContentDensity(mode: ContentDensityMode): SkillRoundtableSpacing = when (mode) {
    ContentDensityMode.COMPACT -> SkillRoundtableSpacing(
        screenHorizontal = 12.dp,
        tiny = 4.dp,
        compact = 4.dp,
        small = 8.dp,
        medium = 12.dp,
        large = 16.dp,
        xLarge = 24.dp,
    )
    ContentDensityMode.STANDARD -> SkillRoundtableSpacing()
    ContentDensityMode.COMFORTABLE -> SkillRoundtableSpacing(
        screenHorizontal = 24.dp,
        tiny = 8.dp,
        compact = 12.dp,
        small = 16.dp,
        medium = 24.dp,
        large = 32.dp,
        xLarge = 40.dp,
    )
}
