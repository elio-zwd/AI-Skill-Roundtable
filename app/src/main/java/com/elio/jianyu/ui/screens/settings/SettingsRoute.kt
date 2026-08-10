package com.elio.jianyu.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard

object SettingsShellTestTags {
    const val SCREEN = "settings_screen"
    const val API_KEYS_ENTRY = "settings_api_keys_entry"
    const val API_KEYS_ACTION = "settings_api_keys_action"
    const val TELEMETRY_ENTRY = "settings_telemetry_entry"
    const val TELEMETRY_ACTION = "settings_telemetry_action"
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenApiKeys: () -> Unit,
    onOpenTelemetry: () -> Unit,
) {
    SettingsScreen(
        onBack = onBack,
        onOpenApiKeys = onOpenApiKeys,
        onOpenTelemetry = onOpenTelemetry,
    )
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenApiKeys: () -> Unit,
    onOpenTelemetry: () -> Unit,
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
        JianyuStateCard(
            title = "字体与显示",
            message = "跟随系统浅暗主题与字号；减少动效会在支持的页面使用静态状态反馈。",
        )
        androidx.compose.material3.Text(
            text = "模型与 API Key",
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        JianyuStateCard(
            title = "API Key",
            message = "管理用户自行导入的 BYOK Key 池；本地浏览不依赖 Key。",
            actionLabel = "管理",
            actionTestTag = SettingsShellTestTags.API_KEYS_ACTION,
            onAction = onOpenApiKeys,
            modifier = Modifier.testTag(SettingsShellTestTags.API_KEYS_ENTRY),
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
            message = "本地数据、备份、导入导出与应用锁将在后续独立实现；当前不会承诺自动迁移或恢复。",
        )
    }
}
