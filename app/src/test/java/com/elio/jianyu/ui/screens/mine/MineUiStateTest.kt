package com.elio.jianyu.ui.screens.mine

import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.data.PersonalContext
import com.elio.jianyu.telemetry.TelemetryLevel
import org.junit.Assert.assertEquals
import org.junit.Test

class MineUiStateTest {
    @Test
    fun statuses_reflectRealAvailabilityAndLoadingState() {
        assertEquals(
            "正在读取个人背景",
            MineUiState().personalBackgroundStatus,
        )
        assertEquals(
            "暂时无法读取数据概览",
            MineUiState(personalContextLoadFailed = true).personalBackgroundStatus,
        )
        assertEquals(
            "已保存 3 项",
            MineUiState(personalContextCount = 3).personalBackgroundStatus,
        )
        assertEquals(
            "Gemini 3.6 Flash · 2 个可用 Key",
            MineUiState(
                modelDisplayName = "Gemini 3.6 Flash",
                availableKeyCount = 2,
            ).aiManagementStatus,
        )
        assertEquals(
            "还没有导入 Gemini Key",
            MineUiState().aiManagementStatus,
        )
        assertEquals("已关闭", MineUiState().telemetryStatus)
        assertEquals(
            "仅记录元数据",
            MineUiState(telemetryLevel = TelemetryLevel.METADATA_ONLY).telemetryStatus,
        )
    }

    @Test
    fun summaryLabels_onlyUseRealNonSensitiveUniqueTitles() {
        val contexts = listOf(
            personalContext(id = "career-a", title = "职业方向", sensitive = false),
            personalContext(id = "career-b", title = " 职业方向 ", sensitive = false),
            personalContext(id = "private", title = "隐私背景", sensitive = true),
            personalContext(id = "study", title = "学习计划", sensitive = false),
        )

        assertEquals(
            listOf("职业方向", "学习计划"),
            contexts.toMineSummaryLabels(),
        )
    }

    private fun personalContext(
        id: String,
        title: String,
        sensitive: Boolean,
    ): PersonalContext = PersonalContext(
        id = id,
        title = title,
        content = "content-$id",
        contentHash = "hash-$id",
        sensitive = sensitive,
        lifecycle = ContextSourceLifecycle.ACTIVE,
        createdAt = 1L,
        updatedAt = 1L,
    )
}
