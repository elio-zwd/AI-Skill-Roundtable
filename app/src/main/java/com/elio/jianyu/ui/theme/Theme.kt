package com.elio.jianyu.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val SkillRoundtableDarkColorScheme = darkColorScheme(
    primary = DarkBrandPrimary,
    onPrimary = SlateBackground,
    primaryContainer = DarkBrandContainer,
    onPrimaryContainer = DarkBrandPrimaryStrong,
    secondary = DarkPracticalDirection,
    onSecondary = SlateBackground,
    secondaryContainer = Color(0xFF123E3A),
    onSecondaryContainer = DarkPracticalDirection,
    tertiary = DarkPerspectiveDirection,
    onTertiary = SlateBackground,
    tertiaryContainer = Color(0xFF39206B),
    onTertiaryContainer = DarkPerspectiveDirection,
    error = DarkStatusDanger,
    onError = SlateBackground,
    errorContainer = Color(0xFF4A1D2A),
    onErrorContainer = DarkStatusDanger,
    background = SlateBackground,
    surface = CardBackground,
    onBackground = AppTextPrimary,
    onSurface = AppTextPrimary,
    surfaceVariant = Color(0xFF252C39),
    onSurfaceVariant = AppTextSecondary,
    outline = AppDivider,
    outlineVariant = Color(0xFF252C39),
    inverseSurface = LightTextPrimary,
    inverseOnSurface = LightCanvas,
    inversePrimary = BrandPrimary,
)

private val SkillRoundtableLightColorScheme = lightColorScheme(
    primary = BrandPrimary,
    onPrimary = Color.White,
    primaryContainer = BrandContainer,
    onPrimaryContainer = BrandPrimaryStrong,
    secondary = PracticalDirection,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = PracticalDirection,
    tertiary = PerspectiveDirection,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = PerspectiveDirection,
    error = StatusDanger,
    onError = Color.White,
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = StatusDanger,
    background = LightCanvas,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceRaised,
    onSurfaceVariant = LightTextSecondary,
    outline = LightDivider,
    outlineVariant = Color(0xFFDCE5EF),
    inverseSurface = CardBackground,
    inverseOnSurface = AppTextPrimary,
    inversePrimary = DarkBrandPrimary,
)

@Composable
fun SkillRoundtableTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) SkillRoundtableDarkColorScheme else SkillRoundtableLightColorScheme
    val extensionColors = if (darkTheme) DarkSkillRoundtableColors else LightSkillRoundtableColors
    CompositionLocalProvider(
        LocalSkillRoundtableColors provides extensionColors,
        LocalSkillRoundtableSpacing provides SkillRoundtableSpacing(),
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SkillRoundtableTypography,
            shapes = SkillRoundtableShapes,
            content = content,
        )
    }
}
