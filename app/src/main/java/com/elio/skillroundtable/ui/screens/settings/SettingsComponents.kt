package com.elio.skillroundtable.ui.screens.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.skillroundtable.ui.GoldAccent
import com.elio.skillroundtable.ui.PrimaryAccent
import com.elio.skillroundtable.ui.TextPrimary
import com.elio.skillroundtable.ui.TextSecondary
import com.elio.skillroundtable.ui.components.bounceClick

@Composable
internal fun SettingsTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    showLeadingSpacer: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.bounceClick()) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回",
                tint = TextPrimary,
            )
        }
        if (showLeadingSpacer) {
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
internal fun settingsToneColor(tone: SettingsTone): Color {
    return when (tone) {
        SettingsTone.PRIMARY -> PrimaryAccent
        SettingsTone.SECONDARY -> TextSecondary
        SettingsTone.SUCCESS -> Color(0xFF4CAF50)
        SettingsTone.WARNING -> GoldAccent
        SettingsTone.ERROR -> MaterialTheme.colorScheme.error
    }
}
