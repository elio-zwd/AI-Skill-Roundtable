package com.elio.skillroundtable.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val SkillRoundtableDarkColorScheme = darkColorScheme(
    primary = BrandPrimary,
    secondary = BrandSecondary,
    background = SlateBackground,
    surface = CardBackground,
    onPrimary = Color.White,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
)

@Composable
fun SkillRoundtableTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSkillRoundtableColors provides DarkSkillRoundtableColors,
        LocalSkillRoundtableSpacing provides SkillRoundtableSpacing(),
    ) {
        MaterialTheme(
            colorScheme = SkillRoundtableDarkColorScheme,
            typography = SkillRoundtableTypography,
            shapes = SkillRoundtableShapes,
            content = content,
        )
    }
}
