package com.elio.jianyu.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 见域的颜色只在本文件定义；页面与组件通过 MaterialTheme 的语义颜色取值。
// 遵循 /design-taste-frontend 规范：单一主调、绝不对爆饱和、极低饱和度深底与出色的版式对比度。
val BrandPrimary = Color(0xFF6366F1) // Refined Indigo Accent
val BrandPrimaryStrong = Color(0xFF4F46E5)
val BrandContainer = Color(0xFFEEF2FF)
val PracticalDirection = Color(0xFF0EA5E9) // Sky/Teal
val PerspectiveDirection = Color(0xFF8B5CF6) // Desaturated Violet
val StatusSuccess = Color(0xFF10B981)
val StatusWarning = Color(0xFFF59E0B)
val StatusDanger = Color(0xFFEF4444)
val StatusInfo = Color(0xFF3B82F6)

// 深色模式：深炭蓝黑色底 (#0C0F17) + 高质感墨沉沉面板 (#141923)
val SlateBackground = Color(0xFF0C0F17)
val CardBackground = Color(0xFF141923)
val CardBackgroundRaised = Color(0xFF1B2230)
val BrandSecondary = Color(0xFF38BDF8)
val BrandGold = Color(0xFFFBBF24)
val AppTextPrimary = Color(0xFFF1F5F9)
val AppTextSecondary = Color(0xFF94A3B8)
val AppDivider = Color(0xFF1E293B)

// 浅色模式：暖润出版物基底 (#F4F6F9) + 纯白纸面 (#FFFFFF) + 柔和灰蓝容器 (#E9EEF5)
val LightCanvas = Color(0xFFF4F6F9)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceRaised = Color(0xFFEBF0F7)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF64748B)
val LightDivider = Color(0xFFE2E8F0)

val DarkBrandPrimary = Color(0xFFA5B4FC)
val DarkBrandPrimaryStrong = Color(0xFFC7D2FE)
val DarkBrandContainer = Color(0xFF1E1B4B)
val DarkPracticalDirection = Color(0xFF38BDF8)
val DarkPerspectiveDirection = Color(0xFFC4B5FD)
val DarkStatusSuccess = Color(0xFF34D399)
val DarkStatusWarning = Color(0xFFFBBF24)
val DarkStatusDanger = Color(0xFFFCA5A5)
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
