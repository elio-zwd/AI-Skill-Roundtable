package com.example.skillroundtable.telemetry

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CloudInteractionSettings {
    private const val PREFS_NAME = "roundtable_privacy_settings"
    private const val KEY_ENABLED = "cloud_interaction_enabled"

    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    @Volatile
    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        _enabled.value = applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun isEnabled(context: Context): Boolean {
        if (appContext == null) init(context)
        return _enabled.value
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        if (appContext == null) init(context)
        _enabled.value = enabled
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}

data class CloudInteractionPolicyResult(
    val store: Boolean,
    val previousInteractionId: String?
)

object CloudInteractionRequestPolicy {
    fun apply(
        enabled: Boolean,
        requestedStore: Boolean?,
        requestedPreviousInteractionId: String?
    ): CloudInteractionPolicyResult {
        return if (enabled) {
            CloudInteractionPolicyResult(
                store = requestedStore == true,
                previousInteractionId = requestedPreviousInteractionId
            )
        } else {
            CloudInteractionPolicyResult(store = false, previousInteractionId = null)
        }
    }
}
