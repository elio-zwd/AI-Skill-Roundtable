package com.elio.jianyu.ui.screens.settings

import com.elio.jianyu.network.KeyStatus
import com.elio.jianyu.telemetry.TelemetryEvent
import com.elio.jianyu.telemetry.TelemetryLevel

sealed interface TelemetryConfirmation {
    data object ContentDebug : TelemetryConfirmation
    data object CloudInteraction : TelemetryConfirmation
}

data class TelemetryUiState(
    val events: List<TelemetryEvent>,
    val level: TelemetryLevel,
    val storageError: String?,
    val cloudInteractionEnabled: Boolean,
    val expandedEventId: String?,
    val confirmation: TelemetryConfirmation?,
    val remainingContentDebugMinutes: Long?,
    val estimatedBytes: Int,
    val currentKeyId: String?,
    val currentKeyAccount: String?,
    val availableKeyCount: Int,
    val totalKeyCount: Int,
)

data class TelemetryEventPresentation(
    val isSuccess: Boolean,
    val statusText: String,
    val tone: SettingsTone,
)

internal fun telemetryLevelDescription(
    level: TelemetryLevel,
    remainingMinutes: Long?,
): String {
    return when (level) {
        TelemetryLevel.OFF -> "关闭：不创建本地遥测事件"
        TelemetryLevel.METADATA_ONLY -> "仅元数据（默认）：不读取或保存请求/回复正文"
        TelemetryLevel.CONTENT_DEBUG -> {
            "临时正文调试：本机保存脱敏、截断预览，剩余约 ${remainingMinutes ?: 0} 分钟"
        }
    }
}

internal fun telemetryAvailableKeyCount(statuses: List<KeyStatus>): Int {
    return statuses.count { !it.isBanned && !it.isManualDisabled }
}

internal fun telemetryEventPresentation(event: TelemetryEvent): TelemetryEventPresentation {
    val success = event.statusCode?.let { it in 200..299 } == true
    return TelemetryEventPresentation(
        isSuccess = success,
        statusText = "${event.statusCode ?: "ERR"} · ${event.durationMs}ms",
        tone = if (success) SettingsTone.PRIMARY else SettingsTone.ERROR,
    )
}
