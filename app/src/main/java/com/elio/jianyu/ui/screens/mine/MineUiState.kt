package com.elio.jianyu.ui.screens.mine

import androidx.compose.runtime.Immutable
import com.elio.jianyu.data.PersonalContext
import com.elio.jianyu.telemetry.TelemetryLevel
import com.elio.jianyu.ui.automation.JianyuAutomationTags

@Immutable
data class MineUiState(
    val personalContextCount: Int? = null,
    val personalContextSummaryLabels: List<String> = emptyList(),
    val personalContextLoadFailed: Boolean = false,
    val modelDisplayName: String = "Gemini",
    val availableKeyCount: Int = 0,
    val telemetryLevel: TelemetryLevel = TelemetryLevel.OFF,
) {
    val personalBackgroundStatus: String
        get() = when {
            personalContextLoadFailed -> "暂时无法读取数据概览"
            personalContextCount == null -> "正在读取个人背景"
            personalContextCount == 0 -> "还没有个人背景"
            else -> "已保存 $personalContextCount 项"
        }

    val aiManagementStatus: String
        get() = if (availableKeyCount > 0) {
            "$modelDisplayName · $availableKeyCount 个可用 Key"
        } else {
            "还没有导入 $modelDisplayName Key"
        }

    val telemetryStatus: String
        get() = when (telemetryLevel) {
            TelemetryLevel.OFF -> "已关闭"
            TelemetryLevel.METADATA_ONLY -> "仅记录元数据"
            TelemetryLevel.CONTENT_DEBUG -> "正文调试已开启"
        }
}

internal fun List<PersonalContext>.toMineSummaryLabels(): List<String> =
    asSequence()
        .filter { context -> !context.sensitive }
        .map { context -> context.title.trim() }
        .filter(String::isNotEmpty)
        .distinct()
        .take(3)
        .toList()

internal object MineTestTags {
    const val SCREEN = JianyuAutomationTags.Screen.MINE
    const val SETTINGS_BUTTON = JianyuAutomationTags.Mine.SETTINGS_BUTTON
    const val PERSONAL_BACKGROUND_HERO = JianyuAutomationTags.Mine.PERSONAL_BACKGROUND_HERO
    const val PERSONAL_BACKGROUND_ACTION = JianyuAutomationTags.Mine.PERSONAL_BACKGROUND_ACTION
    const val AVATAR_SWITCH_UNAVAILABLE = JianyuAutomationTags.Mine.AVATAR_SWITCH_UNAVAILABLE
    const val AI_MANAGEMENT_CARD = JianyuAutomationTags.Mine.AI_MANAGEMENT_CARD
    const val DATA_PRIVACY_CARD = JianyuAutomationTags.Mine.DATA_PRIVACY_CARD
    const val BACKUP_RESTORE_CARD = JianyuAutomationTags.Mine.BACKUP_RESTORE_CARD
    const val TELEMETRY_CARD = JianyuAutomationTags.Mine.TELEMETRY_CARD
    const val SETTINGS_ENTRY = JianyuAutomationTags.Mine.SETTINGS_ENTRY
    const val ABOUT_ENTRY = JianyuAutomationTags.Mine.ABOUT_ENTRY
}
