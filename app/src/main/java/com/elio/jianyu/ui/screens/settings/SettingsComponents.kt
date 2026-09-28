package com.elio.jianyu.ui.screens.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.elio.jianyu.ui.GoldAccent
import com.elio.jianyu.ui.PrimaryAccent
import com.elio.jianyu.ui.TextSecondary
import com.elio.jianyu.ui.components.JianyuTopBar
import com.elio.jianyu.ui.components.JianyuTopBarLevel

@Composable
internal fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER")
    showLeadingSpacer: Boolean = false,
) {
    JianyuTopBar(
        title = title,
        level = JianyuTopBarLevel.SECONDARY,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun settingsToneColor(tone: SettingsTone): Color {
    return when (tone) {
        SettingsTone.PRIMARY -> PrimaryAccent
        SettingsTone.SECONDARY -> TextSecondary
        SettingsTone.SUCCESS -> Color(0xFF4CAF50)
        SettingsTone.WARNING -> GoldAccent
        SettingsTone.ERROR -> Color.Red
    }
}
