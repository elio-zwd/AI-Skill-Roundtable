package com.elio.jianyu.ui.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppPreferencesState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val reducedMotion: Boolean = false,
    val highContrastText: Boolean = false,
    val showMessageTimestamps: Boolean = true,
    val confirmSensitiveContext: Boolean = true,
)

/** 仅保存用户主动选择的应用偏好；不写入正文、Key 或运行时请求数据。 */
object AppPreferences {
    private const val PREFS = "jianyu_app_preferences"
    private const val THEME = "theme_mode"
    private const val REDUCED_MOTION = "reduced_motion"
    private const val HIGH_CONTRAST = "high_contrast_text"
    private const val SHOW_TIMESTAMPS = "show_message_timestamps"
    private const val CONFIRM_SENSITIVE = "confirm_sensitive_context"

    private val _state = MutableStateFlow(AppPreferencesState())
    val state: StateFlow<AppPreferencesState> = _state.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _state.value = AppPreferencesState(
            themeMode = prefs.getString(THEME, ThemeMode.SYSTEM.name)
                ?.let { value -> ThemeMode.entries.firstOrNull { it.name == value } }
                ?: ThemeMode.SYSTEM,
            reducedMotion = prefs.getBoolean(REDUCED_MOTION, false),
            highContrastText = prefs.getBoolean(HIGH_CONTRAST, false),
            showMessageTimestamps = prefs.getBoolean(SHOW_TIMESTAMPS, true),
            confirmSensitiveContext = prefs.getBoolean(CONFIRM_SENSITIVE, true),
        )
    }

    fun setThemeMode(context: Context, mode: ThemeMode) = update(context) {
        it.copy(themeMode = mode)
    }

    fun setReducedMotion(context: Context, enabled: Boolean) = update(context) {
        it.copy(reducedMotion = enabled)
    }

    fun setHighContrastText(context: Context, enabled: Boolean) = update(context) {
        it.copy(highContrastText = enabled)
    }

    fun setShowMessageTimestamps(context: Context, enabled: Boolean) = update(context) {
        it.copy(showMessageTimestamps = enabled)
    }

    fun setConfirmSensitiveContext(context: Context, enabled: Boolean) = update(context) {
        it.copy(confirmSensitiveContext = enabled)
    }

    fun reset(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        _state.value = AppPreferencesState()
    }

    private fun update(context: Context, transform: (AppPreferencesState) -> AppPreferencesState) {
        val next = transform(_state.value)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(THEME, next.themeMode.name)
            .putBoolean(REDUCED_MOTION, next.reducedMotion)
            .putBoolean(HIGH_CONTRAST, next.highContrastText)
            .putBoolean(SHOW_TIMESTAMPS, next.showMessageTimestamps)
            .putBoolean(CONFIRM_SENSITIVE, next.confirmSensitiveContext)
            .apply()
        _state.value = next
    }
}
