package com.elio.skillroundtable.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class SkillRoundtableSpacing(
    val screenHorizontal: Dp = 16.dp,
    val compact: Dp = 8.dp,
    val tiny: Dp = 4.dp,
    val bottomNavigationHeight: Dp = 60.dp,
)

internal val LocalSkillRoundtableSpacing = staticCompositionLocalOf {
    SkillRoundtableSpacing()
}

val MaterialTheme.skillRoundtableSpacing: SkillRoundtableSpacing
    @Composable get() = LocalSkillRoundtableSpacing.current
