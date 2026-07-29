package com.elio.skillroundtable.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme

val SlateBackground = Color(0xFF121824)
val CardBackground = Color(0xFF1E2638)
val BrandPrimary = Color(0xFF6366F1)
val BrandSecondary = Color(0xFF10B981)
val BrandGold = Color(0xFFF59E0B)
val AppTextPrimary = Color(0xFFF3F4F6)
val AppTextSecondary = Color(0xFF9CA3AF)
val AppDivider = Color(0xFF232D42)

@Immutable
data class SkillRoundtableColors(
    val goldAccent: Color,
    val textSecondary: Color,
    val divider: Color,
)

internal val DarkSkillRoundtableColors = SkillRoundtableColors(
    goldAccent = BrandGold,
    textSecondary = AppTextSecondary,
    divider = AppDivider,
)

internal val LocalSkillRoundtableColors = staticCompositionLocalOf {
    DarkSkillRoundtableColors
}

val MaterialTheme.skillRoundtableColors: SkillRoundtableColors
    @Composable get() = LocalSkillRoundtableColors.current
