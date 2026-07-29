package com.elio.skillroundtable.ui.screens.settings

import com.elio.skillroundtable.network.ApiKeySource
import com.elio.skillroundtable.network.ApiKeySummary
import com.elio.skillroundtable.network.ApiKeyValidationState
import com.elio.skillroundtable.network.BatchImportResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiKeyManagerUiStateTest {
    @Test
    fun availableCount_matchesExistingSchedulingPresentationRule() {
        val summaries = listOf(
            summary(id = "available", validationState = ApiKeyValidationState.AVAILABLE),
            summary(id = "unverified", validationState = ApiKeyValidationState.UNVERIFIED),
            summary(id = "invalid", validationState = ApiKeyValidationState.INVALID),
            summary(id = "disabled", enabled = false),
            summary(id = "banned", remainingBanTimeMs = 60_000L),
        )

        assertEquals(2, availableApiKeyCount(summaries))
    }

    @Test
    fun keyStatusPresentation_coversDisabledBanAndValidationStates() {
        assertEquals(
            ApiKeyStatusPresentation("已禁用", SettingsTone.SECONDARY, ApiKeyStatusIcon.INFO),
            keyStatusPresentation(summary(enabled = false)),
        )
        assertEquals(
            "熔断中，剩余 2 分钟",
            keyStatusPresentation(summary(remainingBanTimeMs = 120_000L)).text,
        )
        assertEquals(
            ApiKeyStatusIcon.PROGRESS,
            keyStatusPresentation(summary(validationState = ApiKeyValidationState.CHECKING)).icon,
        )
        assertEquals(
            SettingsTone.SUCCESS,
            keyStatusPresentation(summary(validationState = ApiKeyValidationState.AVAILABLE)).tone,
        )
        assertEquals(
            "Key 已失效",
            keyStatusPresentation(
                summary(
                    validationState = ApiKeyValidationState.INVALID,
                    validationMessage = "Key 已失效",
                ),
            ).text,
        )
        assertEquals(
            "网络异常",
            keyStatusPresentation(summary(validationState = ApiKeyValidationState.NETWORK_ERROR)).text,
        )
        assertEquals(
            "请求频率受限",
            keyStatusPresentation(summary(validationState = ApiKeyValidationState.RATE_LIMITED)).text,
        )
    }

    @Test
    fun importForm_requiresNonBlankInputAndRemainingCapacity() {
        assertFalse(canImportApiKeys("", 0))
        assertFalse(canImportApiKeys("   ", 0))
        assertTrue(canImportApiKeys("key", 49))
        assertFalse(canImportApiKeys("key", MAX_API_KEY_COUNT))
    }

    @Test
    fun importSummaryAndConfirmationState_keepExistingContent() {
        assertEquals(
            "新增 2，重复 1，非法 3，超限 4",
            batchImportSummary(BatchImportResult(2, 1, 3, 4)),
        )
        val target = summary(id = "delete-me")
        assertEquals(target, (ApiKeyConfirmation.Delete(target) as ApiKeyConfirmation.Delete).summary)
        assertEquals(ApiKeyConfirmation.ClearAll, ApiKeyConfirmation.ClearAll)
    }

    @Test
    fun settingsTestTags_areStable() {
        assertEquals("api_key_row_local-1", SettingsTestTags.apiKeyRow("local-1"))
        assertEquals("api_key_delete_confirm", SettingsTestTags.API_KEY_DELETE_CONFIRM)
        assertEquals("api_key_clear_confirm", SettingsTestTags.API_KEY_CLEAR_CONFIRM)
    }

    private fun summary(
        id: String = "id",
        enabled: Boolean = true,
        validationState: ApiKeyValidationState = ApiKeyValidationState.UNVERIFIED,
        validationMessage: String? = null,
        remainingBanTimeMs: Long = 0L,
    ): ApiKeySummary {
        return ApiKeySummary(
            id = id,
            displayName = "Key $id",
            maskedKey = "••••1234",
            source = ApiKeySource.LOCAL,
            enabled = enabled,
            validationState = validationState,
            validationMessage = validationMessage,
            lastValidatedAt = null,
            banExpireTime = remainingBanTimeMs,
            remainingBanTimeMs = remainingBanTimeMs,
        )
    }
}
