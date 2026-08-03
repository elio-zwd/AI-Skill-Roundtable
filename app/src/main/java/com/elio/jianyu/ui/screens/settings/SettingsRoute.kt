package com.elio.jianyu.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard

object SettingsShellTestTags {
    const val SCREEN = "settings_screen"
    const val API_KEYS_ENTRY = "settings_api_keys_entry"
    const val TELEMETRY_ENTRY = "settings_telemetry_entry"
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
        subtitle = "全局能力与本地控制",
        onBack = onBack,
        modifier = Modifier.testTag(SettingsShellTestTags.SCREEN),
    ) {
        JianyuStateCard(
            title = "API Key",
            message = "管理用户自行导入的 BYOK Key 池。",
            actionLabel = "进入",
            onAction = onOpenApiKeys,
            modifier = Modifier.testTag(SettingsShellTestTags.API_KEYS_ENTRY),
        )
        JianyuStateCard(
            title = "Telemetry",
            message = "查看和配置遥测与诊断。",
            actionLabel = "进入",
            onAction = onOpenTelemetry,
            modifier = Modifier.testTag(SettingsShellTestTags.TELEMETRY_ENTRY),
        )
        JianyuStateCard(
            title = "后续能力预留",
            message = "本地数据、存储、备份、导入导出、恢复快照、应用锁、隐私说明和关于将在后续 PR 独立实现。",
        )
    }
}
