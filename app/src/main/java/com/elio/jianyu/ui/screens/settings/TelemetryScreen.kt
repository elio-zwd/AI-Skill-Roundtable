package com.elio.jianyu.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.telemetry.TelemetryEvent
import com.elio.jianyu.telemetry.TelemetryLevel
import com.elio.jianyu.ui.SlateBg
import com.elio.jianyu.ui.TextPrimary

@Composable
fun TelemetryScreen(
    uiState: TelemetryUiState,
    onBack: () -> Unit,
    onSelectLevel: (TelemetryLevel) -> Unit,
    onToggleContentDebug: () -> Unit,
    onClearTelemetry: () -> Unit,
    onDisableContentDebugAndPurge: () -> Unit,
    onCloudInteractionChange: (Boolean) -> Unit,
    onToggleEvent: (TelemetryEvent) -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirmContentDebug: () -> Unit,
    onConfirmCloudInteraction: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(SettingsTestTags.TELEMETRY_ROOT),
        color = SlateBg,
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            SettingsTopBar(
                title = "隐私、遥测与 API 诊断",
                onBack = onBack,
                showLeadingSpacer = true,
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                item {
                    TelemetryPrivacyCard(
                        uiState = uiState,
                        onSelectLevel = onSelectLevel,
                        onToggleContentDebug = onToggleContentDebug,
                        onClearTelemetry = onClearTelemetry,
                        onDisableContentDebugAndPurge = onDisableContentDebugAndPurge,
                    )
                }

                item {
                    CloudInteractionCard(
                        enabled = uiState.cloudInteractionEnabled,
                        onEnabledChange = onCloudInteractionChange,
                    )
                }

                item {
                    CurrentTelemetryKeyCard(uiState)
                }

                item {
                    Text(
                        "最近本地遥测事件",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }

                if (uiState.events.isEmpty()) {
                    item {
                        TelemetryEventsEmptyState()
                    }
                } else {
                    items(
                        items = uiState.events,
                        key = { event -> event.id },
                    ) { event ->
                        TelemetryEventCard(
                            event = event,
                            expanded = uiState.expandedEventId == event.id,
                            onToggle = { onToggleEvent(event) },
                        )
                    }
                }
            }
        }
    }

    TelemetryConfirmationDialogs(
        confirmation = uiState.confirmation,
        onDismiss = onDismissConfirmation,
        onConfirmContentDebug = onConfirmContentDebug,
        onConfirmCloudInteraction = onConfirmCloudInteraction,
    )
}
