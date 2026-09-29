package com.elio.jianyu.ui.screens.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.telemetry.TelemetryEvent
import com.elio.jianyu.telemetry.TelemetryLevel
import com.elio.jianyu.telemetry.TelemetryRepository
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TelemetryRouteRegressionTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun metadataOnlyEventCanExpandDiagnosticDetails() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        TelemetryRepository.init(context)
        TelemetryRepository.setLevel(context, TelemetryLevel.METADATA_ONLY)
        val eventId = "event-meta-expand-${System.nanoTime()}"
        TelemetryRepository.record(
            TelemetryEvent(
                id = eventId,
                timestamp = 1_796_000_000_547L,
                durationMs = 547L,
                endpoint = "POST /v1beta/interactions?alt=sse",
                keyId = "G1",
                statusCode = 200,
                failureType = "SERIALIZATION",
                errorMessage = "InteractionStreamProtocolException: Interaction stream closed before completion",
            )
        )

        composeRule.setContent {
            SkillRoundtableTheme {
                TelemetryRoute(
                    currentSessionId = null,
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag(SettingsTestTags.telemetryEvent(eventId))
            .assertIsDisplayed()
            .performClick()

        composeRule.onNodeWithText("ERR · 547ms").assertIsDisplayed()
        composeRule.onNodeWithText("请求耗时：547 ms").assertIsDisplayed()
        composeRule.onNodeWithText("状态码：HTTP 200").assertIsDisplayed()
        composeRule.onNodeWithText(
            "异常详情：InteractionStreamProtocolException: Interaction stream closed before completion"
        ).assertIsDisplayed()
        composeRule.onNodeWithText("元数据诊断信息（未开启临时正文调试）").assertIsDisplayed()
    }
}
