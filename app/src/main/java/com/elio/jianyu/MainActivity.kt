package com.elio.jianyu

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.elio.jianyu.ui.MainAppContent
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import com.elio.jianyu.ui.settings.AppPreferences
import com.elio.jianyu.ui.theme.spacingForContentDensity
import com.elio.jianyu.ui.settings.FontSizeMode
import com.elio.jianyu.ui.settings.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            LaunchedEffect(context) { AppPreferences.initialize(context) }
            val preferences by AppPreferences.state.collectAsState()
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val darkTheme = when (preferences.themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val baseDensity = LocalDensity.current
            val fontScaleMultiplier = when (preferences.fontSizeMode) {
                FontSizeMode.SYSTEM -> 1f
                FontSizeMode.SMALL -> 0.9f
                FontSizeMode.LARGE -> 1.15f
            }
            val appDensity = Density(
                density = baseDensity.density,
                fontScale = baseDensity.fontScale * fontScaleMultiplier,
            )
            CompositionLocalProvider(LocalDensity provides appDensity) {
                SkillRoundtableTheme(
                    darkTheme = darkTheme,
                    reducedMotion = preferences.reducedMotion,
                    highContrastText = preferences.highContrastText,
                    spacing = spacingForContentDensity(preferences.contentDensityMode),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        MainAppContent()
                    }
                }
            }
        }
    }
}
