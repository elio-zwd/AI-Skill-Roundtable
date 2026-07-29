package com.elio.skillroundtable.ui.screens.settings

import com.elio.skillroundtable.network.ApiKeySummary
import com.elio.skillroundtable.network.ApiKeyValidationState
import com.elio.skillroundtable.network.BatchImportResult

internal const val MAX_API_KEY_COUNT = 50

enum class SettingsTone {
    PRIMARY,
    SECONDARY,
    SUCCESS,
    WARNING,
    ERROR,
}

enum class ApiKeyStatusIcon {
    INFO,
    PROGRESS,
    SUCCESS,
    INVALID,
}

data class ApiKeyStatusPresentation(
    val text: String,
    val tone: SettingsTone,
    val icon: ApiKeyStatusIcon,
)

sealed interface ApiKeyConfirmation {
    data class Delete(val summary: ApiKeySummary) : ApiKeyConfirmation
    data object ClearAll : ApiKeyConfirmation
}

data class ApiKeyManagerUiState(
    val summaries: List<ApiKeySummary>,
    val storageError: String?,
    val currentKeyAccount: String?,
    val input: String,
    val resultMessage: String?,
    val confirmation: ApiKeyConfirmation?,
) {
    val availableCount: Int
        get() = availableApiKeyCount(summaries)

    val canImport: Boolean
        get() = canImportApiKeys(input, summaries.size)
}

internal object SettingsTestTags {
    const val API_KEY_ROOT = "api_key_manager"
    const val API_KEY_IMPORT_INPUT = "api_key_import_input"
    const val API_KEY_IMPORT_BUTTON = "api_key_import_button"
    const val API_KEY_DELETE_CONFIRM = "api_key_delete_confirm"
    const val API_KEY_CLEAR_CONFIRM = "api_key_clear_confirm"
    const val API_KEY_ROW_PREFIX = "api_key_row_"

    const val TELEMETRY_ROOT = "telemetry_screen"
    const val TELEMETRY_CONTENT_DEBUG_CONFIRM = "telemetry_content_debug_confirm"
    const val TELEMETRY_CLOUD_CONFIRM = "telemetry_cloud_confirm"
    const val TELEMETRY_EVENT_PREFIX = "telemetry_event_"

    fun apiKeyRow(id: String): String = "$API_KEY_ROW_PREFIX$id"

    fun telemetryEvent(id: String): String = "$TELEMETRY_EVENT_PREFIX$id"
}

internal fun availableApiKeyCount(summaries: List<ApiKeySummary>): Int {
    return summaries.count {
        it.enabled &&
            it.validationState != ApiKeyValidationState.INVALID &&
            it.remainingBanTimeMs <= 0L
    }
}

internal fun canImportApiKeys(input: String, savedCount: Int): Boolean {
    return input.isNotBlank() && savedCount < MAX_API_KEY_COUNT
}

internal fun batchImportSummary(result: BatchImportResult): String {
    return "新增 ${result.added}，重复 ${result.duplicates}，非法 ${result.invalid}，超限 ${result.overflow}"
}

internal fun keyStatusPresentation(summary: ApiKeySummary): ApiKeyStatusPresentation {
    val icon = apiKeyStatusIcon(summary.validationState)
    if (!summary.enabled) {
        return ApiKeyStatusPresentation(
            text = "已禁用",
            tone = SettingsTone.SECONDARY,
            icon = icon,
        )
    }
    if (summary.remainingBanTimeMs > 0L) {
        val minutes = summary.remainingBanTimeMs / 60_000L
        return ApiKeyStatusPresentation(
            text = "熔断中，剩余 ${minutes} 分钟",
            tone = SettingsTone.ERROR,
            icon = icon,
        )
    }
    return when (summary.validationState) {
        ApiKeyValidationState.UNVERIFIED -> ApiKeyStatusPresentation(
            text = "未验证",
            tone = SettingsTone.WARNING,
            icon = icon,
        )
        ApiKeyValidationState.CHECKING -> ApiKeyStatusPresentation(
            text = "验证中",
            tone = SettingsTone.PRIMARY,
            icon = icon,
        )
        ApiKeyValidationState.AVAILABLE -> ApiKeyStatusPresentation(
            text = "可用",
            tone = SettingsTone.SUCCESS,
            icon = icon,
        )
        ApiKeyValidationState.INVALID -> ApiKeyStatusPresentation(
            text = summary.validationMessage ?: "无效",
            tone = SettingsTone.ERROR,
            icon = icon,
        )
        ApiKeyValidationState.NETWORK_ERROR -> ApiKeyStatusPresentation(
            text = summary.validationMessage ?: "网络异常",
            tone = SettingsTone.WARNING,
            icon = icon,
        )
        ApiKeyValidationState.RATE_LIMITED -> ApiKeyStatusPresentation(
            text = "请求频率受限",
            tone = SettingsTone.ERROR,
            icon = icon,
        )
    }
}

private fun apiKeyStatusIcon(validationState: ApiKeyValidationState): ApiKeyStatusIcon {
    return when (validationState) {
        ApiKeyValidationState.CHECKING -> ApiKeyStatusIcon.PROGRESS
        ApiKeyValidationState.AVAILABLE -> ApiKeyStatusIcon.SUCCESS
        ApiKeyValidationState.INVALID -> ApiKeyStatusIcon.INVALID
        ApiKeyValidationState.UNVERIFIED,
        ApiKeyValidationState.NETWORK_ERROR,
        ApiKeyValidationState.RATE_LIMITED,
        -> ApiKeyStatusIcon.INFO
    }
}
