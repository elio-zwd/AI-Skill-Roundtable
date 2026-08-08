package com.elio.jianyu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 见域的颜色只在本文件定义；页面与组件通过 MaterialTheme 的语义颜色取值。
val BrandPrimary = Color(0xFF4F46E5)
val BrandPrimaryStrong = Color(0xFF4338CA)
val BrandContainer = Color(0xFFEEF2FF)
val PracticalDirection = Color(0xFF0F766E)
val PerspectiveDirection = Color(0xFF6D28D9)
val StatusSuccess = Color(0xFF047857)
val StatusWarning = Color(0xFFA15C00)
val StatusDanger = Color(0xFFB42318)
val StatusInfo = Color(0xFF1D4ED8)

val SlateBackground = Color(0xFF10131A)
val CardBackground = Color(0xFF171B24)
val BrandSecondary = Color(0xFF5EEAD4)
val BrandGold = Color(0xFFFBBF24)
val AppTextPrimary = Color(0xFFF8FAFC)
val AppTextSecondary = Color(0xFFCBD5E1)
val AppDivider = Color(0xFF334155)

// 浅色界面采用雾灰蓝画布与柔白纸面，避免整屏纯白导致的表单感。
val LightCanvas = Color(0xFFF1F4F8)
val LightSurface = Color(0xFFFAFBFD)
val LightSurfaceRaised = Color(0xFFE9EEF5)
val LightTextPrimary = Color(0xFF111827)
val LightTextSecondary = Color(0xFF475569)
val LightDivider = Color(0xFFC7D2E0)

val DarkBrandPrimary = Color(0xFFA5B4FC)
val DarkBrandPrimaryStrong = Color(0xFFC7D2FE)
val DarkBrandContainer = Color(0xFF29285A)
val DarkPracticalDirection = Color(0xFF5EEAD4)
val DarkPerspectiveDirection = Color(0xFFC4B5FD)
val DarkStatusSuccess = Color(0xFF5EEAD4)
val DarkStatusWarning = Color(0xFFFBBF24)
val DarkStatusDanger = Color(0xFFFDA4AF)
val DarkStatusInfo = Color(0xFF93C5FD)

@Immutable
data class SkillRoundtableColors(
    val goldAccent: Color,
    val textSecondary: Color,
    val divider: Color,
    val practicalDirection: Color,
    val perspectiveDirection: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val info: Color,
)

internal val DarkSkillRoundtableColors = SkillRoundtableColors(
    goldAccent = BrandGold,
    textSecondary = AppTextSecondary,
    divider = AppDivider,
    practicalDirection = DarkPracticalDirection,
    perspectiveDirection = DarkPerspectiveDirection,
    success = DarkStatusSuccess,
    warning = DarkStatusWarning,
    danger = DarkStatusDanger,
    info = DarkStatusInfo,
)

internal val LightSkillRoundtableColors = SkillRoundtableColors(
    goldAccent = StatusWarning,
    textSecondary = LightTextSecondary,
    divider = LightDivider,
    practicalDirection = PracticalDirection,
    perspectiveDirection = PerspectiveDirection,
    success = StatusSuccess,
    warning = StatusWarning,
    danger = StatusDanger,
    info = StatusInfo,
)

internal val LocalSkillRoundtableColors = staticCompositionLocalOf {
    DarkSkillRoundtableColors
}

val MaterialTheme.skillRoundtableColors: SkillRoundtableColors
    @Composable get() = LocalSkillRoundtableColors.current
