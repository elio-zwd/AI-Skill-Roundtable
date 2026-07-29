package com.elio.skillroundtable.ui.screens.settings

import com.elio.skillroundtable.network.ApiKeySource
import com.elio.skillroundtable.network.ApiKeyValidationState
import com.elio.skillroundtable.network.KeyStatus
import com.elio.skillroundtable.telemetry.TelemetryEvent
import com.elio.skillroundtable.telemetry.TelemetryLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryUiStateTest {
    @Test
    fun telemetryLevelDescription_preservesPrivacyCopy() {
        assertEquals(
            "关闭：不创建本地遥测事件",
            telemetryLevelDescription(TelemetryLevel.OFF, null),
        )
        assertEquals(
            "仅元数据（默认）：不读取或保存请求/回复正文",
            telemetryLevelDescription(TelemetryLevel.METADATA_ONLY, null),
        )
        assertEquals(
            "临时正文调试：本机保存脱敏、截断预览，剩余约 15 分钟",
            telemetryLevelDescription(TelemetryLevel.CONTENT_DEBUG, 15L),
        )
    }

    @Test
    fun telemetryAvailableKeyCount_excludesBannedAndManualDisabled() {
        val statuses = listOf(
            status(id = "available"),
            status(id = "banned", isBanned = true),
            status(id = "disabled", isManualDisabled = true),
        )

        assertEquals(1, telemetryAvailableKeyCount(statuses))
    }

    @Test
    fun telemetryEventPresentation_distinguishesSuccessAndFailure() {
        val success = telemetryEventPresentation(event(statusCode = 204, durationMs = 18L))
        val failure = telemetryEventPresentation(event(statusCode = null, durationMs = 90L))

        assertTrue(success.isSuccess)
        assertEquals("204 · 18ms", success.statusText)
        assertEquals(SettingsTone.PRIMARY, success.tone)
        assertFalse(failure.isSuccess)
        assertEquals("ERR · 90ms", failure.statusText)
        assertEquals(SettingsTone.ERROR, failure.tone)
    }

    @Test
    fun riskConfirmationsAndEventTags_areStable() {
        assertEquals(TelemetryConfirmation.ContentDebug, TelemetryConfirmation.ContentDebug)
        assertEquals(TelemetryConfirmation.CloudInteraction, TelemetryConfirmation.CloudInteraction)
        assertEquals("telemetry_event_event-1", SettingsTestTags.telemetryEvent("event-1"))
        assertEquals(
            "telemetry_content_debug_confirm",
            SettingsTestTags.TELEMETRY_CONTENT_DEBUG_CONFIRM,
        )
        assertEquals("telemetry_cloud_confirm", SettingsTestTags.TELEMETRY_CLOUD_CONFIRM)
    }

    private fun status(
        id: String,
        isBanned: Boolean = false,
        isManualDisabled: Boolean = false,
    ): KeyStatus {
        return KeyStatus(
            id = id,
            displayName = id,
            maskedKey = "••••1234",
            source = ApiKeySource.LOCAL,
            validationState = ApiKeyValidationState.AVAILABLE,
            validationMessage = null,
            isBanned = isBanned,
            banExpireTime = 0L,
            remainingBanTimeMs = 0L,
            isManualDisabled = isManualDisabled,
        )
    }

    private fun event(statusCode: Int?, durationMs: Long): TelemetryEvent {
        return TelemetryEvent(
            id = "event",
            timestamp = 0L,
            durationMs = durationMs,
            endpoint = "/test",
            statusCode = statusCode,
        )
    }
}
