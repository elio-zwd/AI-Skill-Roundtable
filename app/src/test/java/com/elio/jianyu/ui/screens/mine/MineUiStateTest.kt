package com.elio.jianyu.ui.screens.mine

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
}
