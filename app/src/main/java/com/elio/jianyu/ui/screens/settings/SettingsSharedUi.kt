package com.elio.jianyu.ui.screens.settings

enum class SettingsTone {
    PRIMARY,
    SECONDARY,
    SUCCESS,
    WARNING,
    ERROR,
}

internal object SettingsTestTags {
    const val TELEMETRY_ROOT = "telemetry_screen"
    const val TELEMETRY_CONTENT_DEBUG_CONFIRM = "telemetry_content_debug_confirm"
    const val TELEMETRY_CLOUD_CONFIRM = "telemetry_cloud_confirm"
    const val TELEMETRY_EVENT_PREFIX = "telemetry_event_"

    fun telemetryEvent(id: String): String = "$TELEMETRY_EVENT_PREFIX$id"
}
