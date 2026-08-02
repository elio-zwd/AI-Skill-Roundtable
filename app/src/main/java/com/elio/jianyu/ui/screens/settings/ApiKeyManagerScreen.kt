package com.elio.jianyu.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.network.ApiKeySummary
import com.elio.jianyu.ui.SlateBg

@Composable
fun ApiKeyManagerScreen(
    uiState: ApiKeyManagerUiState,
    onBack: () -> Unit,
    onOpenTelemetry: () -> Unit,
    onInputChange: (String) -> Unit,
    onImport: () -> Unit,
    onToggle: (ApiKeySummary, disabled: Boolean) -> Unit,
    onValidate: (ApiKeySummary) -> Unit,
    onRequestDelete: (ApiKeySummary) -> Unit,
    onRequestClearAll: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirmDelete: (ApiKeySummary) -> Unit,
    onConfirmClearAll: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag(SettingsTestTags.API_KEY_ROOT),
        color = SlateBg,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            SettingsTopBar(
                title = "Gemini API Key 管理",
                onBack = onBack,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ApiKeyMetrics(
                        savedCount = uiState.summaries.size,
                        availableCount = uiState.availableCount,
                    )
                }

                uiState.storageError?.let { error ->
                    item {
                        ApiKeyStorageErrorCard(
                            message = error,
                            onRequestClearAll = onRequestClearAll,
                        )
                    }
                }

                item {
                    ApiKeyImportCard(
                        input = uiState.input,
                        resultMessage = uiState.resultMessage,
                        canImport = uiState.canImport,
                        onInputChange = onInputChange,
                        onImport = onImport,
                    )
                }

                item {
                    CurrentSessionKeyCard(
                        account = uiState.currentKeyAccount,
                        onOpenTelemetry = onOpenTelemetry,
                    )
                }

                item {
                    ApiKeyListHeader(
                        hasKeys = uiState.summaries.isNotEmpty(),
                        onRequestClearAll = onRequestClearAll,
                    )
                }

                if (uiState.summaries.isEmpty()) {
                    item {
                        ApiKeyEmptyState()
                    }
                } else {
                    items(
                        items = uiState.summaries,
                        key = { summary -> summary.id },
                    ) { summary ->
                        ApiKeyRow(
                            summary = summary,
                            onToggle = { disabled -> onToggle(summary, disabled) },
                            onValidate = { onValidate(summary) },
                            onDelete = { onRequestDelete(summary) },
                        )
                    }
                }
            }
        }
    }

    ApiKeyConfirmationDialogs(
        confirmation = uiState.confirmation,
        onDismiss = onDismissConfirmation,
        onConfirmDelete = onConfirmDelete,
        onConfirmClearAll = onConfirmClearAll,
    )
}
