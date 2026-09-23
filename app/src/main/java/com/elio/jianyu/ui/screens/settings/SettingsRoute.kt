package com.elio.jianyu.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard
import com.elio.jianyu.ui.settings.AppPreferences
import com.elio.jianyu.ui.settings.AppPreferencesState
import com.elio.jianyu.ui.settings.ContentDensityMode
import com.elio.jianyu.ui.settings.FontSizeMode
import com.elio.jianyu.ui.settings.ThemeMode

object SettingsShellTestTags {
    const val SCREEN = "settings_screen"
    const val AI_MANAGEMENT_ENTRY = "settings_ai_management_entry"
    const val AI_MANAGEMENT_ACTION = "settings_ai_management_action"
    const val TELEMETRY_ENTRY = "settings_telemetry_entry"
    const val TELEMETRY_ACTION = "settings_telemetry_action"
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenAiManagement: () -> Unit,
    onOpenTelemetry: () -> Unit,
) {
    val context = LocalContext.current
    val preferences by AppPreferences.state.collectAsState()
    SettingsScreen(
        preferences = preferences,
        onBack = onBack,
        onOpenAiManagement = onOpenAiManagement,
        onOpenTelemetry = onOpenTelemetry,
        onThemeModeChange = { AppPreferences.setThemeMode(context, it) },
        onFontSizeModeChange = { AppPreferences.setFontSizeMode(context, it) },
        onContentDensityModeChange = { AppPreferences.setContentDensityMode(context, it) },
        onReducedMotionChange = { AppPreferences.setReducedMotion(context, it) },
        onHighContrastChange = { AppPreferences.setHighContrastText(context, it) },
        onSensitiveContextChange = { AppPreferences.setConfirmSensitiveContext(context, it) },
        onTimestampChange = { AppPreferences.setShowMessageTimestamps(context, it) },
    )
}

@Composable
fun SettingsScreen(
    preferences: AppPreferencesState = AppPreferencesState(),
    onBack: () -> Unit,
    onOpenAiManagement: () -> Unit,
    onOpenTelemetry: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit = {},
    onFontSizeModeChange: (FontSizeMode) -> Unit = {},
    onContentDensityModeChange: (ContentDensityMode) -> Unit = {},
    onReducedMotionChange: (Boolean) -> Unit = {},
    onHighContrastChange: (Boolean) -> Unit = {},
    onSensitiveContextChange: (Boolean) -> Unit = {},
    onTimestampChange: (Boolean) -> Unit = {},
) {
    JianyuPageShell(
        title = "设置",
        subtitle = null,
        onBack = onBack,
        compactHeader = true,
        contentScrollable = true,
        modifier = Modifier.testTag(SettingsShellTestTags.SCREEN),
    ) {
        androidx.compose.material3.Text(
            text = "安全地管理工作台",
            style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
        )
        androidx.compose.material3.Text(
            text = "按风险从低到高分组；API Key 不会在界面或日志中完整显示。",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
        )
        androidx.compose.material3.Text(
            text = "外观与无障碍",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        SettingsChoiceRow(
            title = "主题",
            value = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> "跟随系统"
                ThemeMode.LIGHT -> "浅色"
                ThemeMode.DARK -> "深色"
            },
            onClick = {
                val next = when (preferences.themeMode) {
                    ThemeMode.SYSTEM -> ThemeMode.LIGHT
                    ThemeMode.LIGHT -> ThemeMode.DARK
                    ThemeMode.DARK -> ThemeMode.SYSTEM
                }
                onThemeModeChange(next)
            },
        )
        SettingsChoiceRow(
            title = "字号",
            value = when (preferences.fontSizeMode) {
                FontSizeMode.SYSTEM -> "跟随系统"
                FontSizeMode.SMALL -> "较小"
                FontSizeMode.LARGE -> "较大"
            },
            onClick = {
                onFontSizeModeChange(
                    when (preferences.fontSizeMode) {
                        FontSizeMode.SYSTEM -> FontSizeMode.SMALL
                        FontSizeMode.SMALL -> FontSizeMode.LARGE
                        FontSizeMode.LARGE -> FontSizeMode.SYSTEM
                    },
                )
            },
        )
        SettingsChoiceRow(
            title = "内容密度",
            value = when (preferences.contentDensityMode) {
                ContentDensityMode.COMPACT -> "紧凑"
                ContentDensityMode.STANDARD -> "标准"
                ContentDensityMode.COMFORTABLE -> "宽松"
            },
            onClick = {
                onContentDensityModeChange(
                    when (preferences.contentDensityMode) {
                        ContentDensityMode.COMPACT -> ContentDensityMode.STANDARD
                        ContentDensityMode.STANDARD -> ContentDensityMode.COMFORTABLE
                        ContentDensityMode.COMFORTABLE -> ContentDensityMode.COMPACT
                    },
                )
            },
        )
        SettingsSwitchRow("减少动效", preferences.reducedMotion, onReducedMotionChange)
        SettingsSwitchRow("增强文字对比度", preferences.highContrastText, onHighContrastChange)
        SettingsSwitchRow("发送前确认敏感资料", preferences.confirmSensitiveContext, onSensitiveContextChange)
        SettingsSwitchRow("默认显示消息时间", preferences.showMessageTimestamps, onTimestampChange)
        androidx.compose.material3.Text(
            text = "模型与 API Key",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        JianyuStateCard(
            title = "AI 管理",
            message = "选择 Gemini 或 DeepSeek 文本模型，并管理各自独立的 BYOK Key 池。",
            actionLabel = "管理",
            actionTestTag = SettingsShellTestTags.AI_MANAGEMENT_ACTION,
            onAction = onOpenAiManagement,
            modifier = Modifier.testTag(SettingsShellTestTags.AI_MANAGEMENT_ENTRY),
        )
        JianyuStateCard(
            title = "遥测与诊断",
            message = "查看和配置遥测、诊断及云端交互授权。",
            actionLabel = "打开诊断",
            actionTestTag = SettingsShellTestTags.TELEMETRY_ACTION,
            onAction = onOpenTelemetry,
            modifier = Modifier.testTag(SettingsShellTestTags.TELEMETRY_ENTRY),
        )
        JianyuStateCard(
            title = "数据与恢复",
            message = "数据导出、备份与恢复请从“我的”页进入；这里只处理当前见域 App 的数据。",
        )
    }
}

@Composable
private fun SettingsChoiceRow(
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    androidx.compose.material3.ListItem(
        headlineContent = { androidx.compose.material3.Text(title) },
        supportingContent = { androidx.compose.material3.Text("点击切换 · $value") },
        trailingContent = { androidx.compose.material3.Text(value) },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    androidx.compose.material3.ListItem(
        headlineContent = { androidx.compose.material3.Text(title) },
        trailingContent = {
            androidx.compose.material3.Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
    )
}
