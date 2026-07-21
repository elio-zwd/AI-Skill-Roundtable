package com.elio.skillroundtable.telemetry

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

    @Volatile
    private var initialized = false

    @Synchronized
    fun init(context: Context) {
        if (initialized) return
        val applicationContext = context.applicationContext
        appContext = applicationContext
        _enabled.value = applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
        if (!_enabled.value) InteractionChainStore.clearAll()
        initialized = true
    }

    @Synchronized
    fun isEnabled(context: Context): Boolean {
        if (!initialized) init(context)
        return _enabled.value
    }

    @Synchronized
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        if (!initialized) init(context)
        val committed = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .commit()

        _enabled.value = if (committed) enabled else false
        if (!enabled || !committed) InteractionChainStore.clearAll()
        return committed
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
        val store = enabled && requestedStore == true
        return CloudInteractionPolicyResult(
            store = store,
            previousInteractionId = requestedPreviousInteractionId.takeIf { store }
        )
    }
}