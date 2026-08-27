package com.elio.jianyu.ui.screens.settings

import com.elio.jianyu.network.AiRuntimeConfiguration
import com.elio.jianyu.network.AiProvider
import com.elio.jianyu.network.ApiKeySummary
import com.elio.jianyu.network.ApiKeyValidationState
import com.elio.jianyu.network.BatchImportResult

sealed interface AiManagementConfirmation {
    data class Delete(val summary: ApiKeySummary) : AiManagementConfirmation
    data object ClearProviderKeys : AiManagementConfirmation
}

data class AiManagementUiState(
    val configuration: AiRuntimeConfiguration,
    val keyProvider: AiProvider,
    val summaries: List<ApiKeySummary>,
    val storageError: String?,
    val currentKeyAccount: String?,
    val input: String,
    val resultMessage: String?,
    val confirmation: AiManagementConfirmation?,
) {
    val availableKeyCount: Int
        get() = summaries.count {
            it.enabled &&
                it.validationState != ApiKeyValidationState.INVALID &&
                it.remainingBanTimeMs <= 0L
        }

    val canImport: Boolean
        get() = input.isNotBlank() && summaries.size < MAX_PROVIDER_KEY_COUNT
}

internal const val MAX_PROVIDER_KEY_COUNT = 50

internal object AiManagementTestTags {
    const val ROOT = "ai_management"
    const val IMPORT_INPUT = "ai_management_import_input"
    const val IMPORT_BUTTON = "ai_management_import_button"
    const val CLEAR_CONFIRM = "ai_management_clear_confirm"
    const val DELETE_CONFIRM = "ai_management_delete_confirm"

    fun provider(providerName: String): String = "ai_management_provider_$providerName"
    fun model(modelName: String): String = "ai_management_model_$modelName"
    fun keyRow(id: String): String = "ai_management_key_$id"
}

internal fun aiBatchImportSummary(result: BatchImportResult): String =
    "新增 ${result.added}，重复 ${result.duplicates}，非法 ${result.invalid}，超限 ${result.overflow}"

internal fun aiKeyStatusText(summary: ApiKeySummary): String = when {
    !summary.enabled -> "已禁用"
    summary.remainingBanTimeMs > 0L -> "冷却中，剩余 ${summary.remainingBanTimeMs / 60_000L} 分钟"
    else -> when (summary.validationState) {
        ApiKeyValidationState.UNVERIFIED -> "未验证"
        ApiKeyValidationState.CHECKING -> "验证中"
        ApiKeyValidationState.AVAILABLE -> "可用"
        ApiKeyValidationState.INVALID -> "鉴权失败"
        ApiKeyValidationState.NETWORK_ERROR -> "网络验证失败"
        ApiKeyValidationState.RATE_LIMITED -> "请求频率受限"
    }
}
