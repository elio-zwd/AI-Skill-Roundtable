package com.elio.jianyu.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elio.jianyu.network.AiModel
import com.elio.jianyu.network.AiProvider
import com.elio.jianyu.network.AiUseCase
import com.elio.jianyu.network.ApiKeySummary
import com.elio.jianyu.ui.SlateBg
import com.elio.jianyu.ui.TextPrimary
import com.elio.jianyu.ui.TextSecondary

@Composable
fun AiManagementScreen(
    uiState: AiManagementUiState,
    onBack: () -> Unit,
    onSelectProvider: (AiUseCase, AiProvider) -> Unit,
    onSelectModel: (AiUseCase, AiModel) -> Unit,
    onSelectKeyProvider: (AiProvider) -> Unit,
    onInputChange: (String) -> Unit,
    onImport: () -> Unit,
    onToggleKey: (ApiKeySummary, Boolean) -> Unit,
    onValidateKey: (ApiKeySummary) -> Unit,
    onRequestDeleteKey: (ApiKeySummary) -> Unit,
    onRequestClearProviderKeys: () -> Unit,
    onDismissConfirmation: () -> Unit,
    onConfirmDeleteKey: (ApiKeySummary) -> Unit,
    onConfirmClearProviderKeys: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize().testTag("api_key_manager"),
        color = SlateBg,
    ) {
        Column(modifier = Modifier.fillMaxSize().testTag(AiManagementTestTags.ROOT)) {
            SettingsTopBar(title = "AI 管理", onBack = onBack)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    AiModelSelectionCard(
                        configuration = uiState.configuration,
                        onSelectProvider = onSelectProvider,
                        onSelectModel = onSelectModel,
                    )
                }
                item {
                    AiKeyImportCard(
                        uiState = uiState,
                        onSelectKeyProvider = onSelectKeyProvider,
                        onInputChange = onInputChange,
                        onImport = onImport,
                    )
                }
                uiState.storageError?.let { message ->
                    item { Text(message, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
                }
                item {
                    AiKeyListHeader(
                        provider = uiState.keyProvider,
                        count = uiState.summaries.size,
                        availableCount = uiState.availableKeyCount,
                        onRequestClear = onRequestClearProviderKeys,
                    )
                }
                if (uiState.summaries.isEmpty()) {
                    item {
                        Text("还没有 ${uiState.keyProvider.displayName} Key。导入后才能使用它的功能。", color = TextSecondary)
                    }
                } else {
                    items(uiState.summaries, key = ApiKeySummary::id) { summary ->
                        AiKeyRow(
                            summary = summary,
                            onToggle = { enabled -> onToggleKey(summary, enabled) },
                            onValidate = { onValidateKey(summary) },
                            onDelete = { onRequestDeleteKey(summary) },
                        )
                    }
                }
                item {
                    Text(
                        text = "说明：上方五种文本调用可分别选择模型。联网检索仅提供支持 Google Search 的 Gemini 模型；嵌入与 Gemini Live 语音因协议限制使用固定模型。",
                        color = TextSecondary,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
    AiManagementConfirmationDialog(
        confirmation = uiState.confirmation,
        provider = uiState.keyProvider,
        onDismiss = onDismissConfirmation,
        onConfirmDelete = onConfirmDeleteKey,
        onConfirmClear = onConfirmClearProviderKeys,
    )
}

@Composable
private fun AiModelSelectionCard(
    configuration: com.elio.jianyu.network.AiRuntimeConfiguration,
    onSelectProvider: (AiUseCase, AiProvider) -> Unit,
    onSelectModel: (AiUseCase, AiModel) -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("各调用用途的文本模型", fontWeight = FontWeight.Bold, color = TextPrimary)
            AiUseCase.entries.forEach { useCase ->
                val selectedModel = configuration.modelFor(useCase)
                Text(useCase.displayName, fontWeight = FontWeight.Medium, color = TextPrimary)
                Text(
                    useCase.description,
                    color = TextSecondary,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    useCase.supportedProviders.forEach { provider ->
                        FilterChip(
                            selected = selectedModel.provider == provider,
                            onClick = { onSelectProvider(useCase, provider) },
                            label = { Text(provider.displayName) },
                            modifier = Modifier.testTag(
                                AiManagementTestTags.provider("${useCase.name}_${provider.name}"),
                            ),
                        )
                    }
                }
                AiModel.entries.filter { it.provider == selectedModel.provider }.forEach { model ->
                    FilterChip(
                        selected = selectedModel == model,
                        onClick = { onSelectModel(useCase, model) },
                        label = { Text(model.displayName) },
                        modifier = Modifier.testTag(
                            AiManagementTestTags.model("${useCase.name}_${model.name}"),
                        ),
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AiKeyImportCard(
    uiState: AiManagementUiState,
    onSelectKeyProvider: (AiProvider) -> Unit,
    onInputChange: (String) -> Unit,
    onImport: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("提供商 API Key", fontWeight = FontWeight.Bold, color = TextPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { provider ->
                    FilterChip(
                        selected = uiState.keyProvider == provider,
                        onClick = { onSelectKeyProvider(provider) },
                        label = { Text(provider.displayName) },
                    )
                }
            }
            Text("每行一个，可一次导入多个；完整 Key 只写入本机加密保险箱。", color = TextSecondary)
            OutlinedTextField(
                value = uiState.input,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth().testTag(AiManagementTestTags.IMPORT_INPUT),
                label = { Text("粘贴 API Key") },
                minLines = 3,
            )
            Button(
                onClick = onImport,
                enabled = uiState.canImport,
                modifier = Modifier.testTag(AiManagementTestTags.IMPORT_BUTTON),
            ) { Text("导入并验证") }
            uiState.resultMessage?.let { Text(it, color = TextSecondary) }
            uiState.currentKeyAccount?.let { Text("当前会话优先使用：$it", color = TextSecondary) }
        }
    }
}

@Composable
private fun AiKeyListHeader(
    provider: AiProvider,
    count: Int,
    availableCount: Int,
    onRequestClear: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("${provider.displayName} Key 池", fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("已保存 $count · 当前可用 $availableCount", color = TextSecondary)
        }
        TextButton(onClick = onRequestClear, enabled = count > 0) { Text("清空此提供商") }
    }
}

@Composable
private fun AiKeyRow(
    summary: ApiKeySummary,
    onToggle: (Boolean) -> Unit,
    onValidate: () -> Unit,
    onDelete: () -> Unit,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().testTag(AiManagementTestTags.keyRow(summary.id))) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.displayName, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(summary.maskedKey, color = TextSecondary)
                }
                Switch(checked = summary.enabled, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(aiKeyStatusText(summary), color = TextSecondary, modifier = Modifier.weight(1f))
                IconButton(onClick = onValidate) {
                    Icon(Icons.Default.Refresh, contentDescription = "重新验证")
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        }
    }
}

@Composable
private fun AiManagementConfirmationDialog(
    confirmation: AiManagementConfirmation?,
    provider: AiProvider,
    onDismiss: () -> Unit,
    onConfirmDelete: (ApiKeySummary) -> Unit,
    onConfirmClear: () -> Unit,
) {
    when (confirmation) {
        is AiManagementConfirmation.Delete -> AlertDialog(
            modifier = Modifier.testTag(AiManagementTestTags.DELETE_CONFIRM),
            onDismissRequest = onDismiss,
            title = { Text("删除 ${confirmation.summary.displayName}？") },
            text = { Text("删除后无法恢复完整 Key，需要重新导入。") },
            confirmButton = { TextButton(onClick = { onConfirmDelete(confirmation.summary) }) { Text("删除") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
        AiManagementConfirmation.ClearProviderKeys -> AlertDialog(
            modifier = Modifier.testTag(AiManagementTestTags.CLEAR_CONFIRM),
            onDismissRequest = onDismiss,
            title = { Text("清空全部 ${provider.displayName} Key？") },
            text = { Text("此操作无法撤销，另一个提供商的 Key 不受影响。") },
            confirmButton = { TextButton(onClick = onConfirmClear) { Text("全部删除") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
        null -> Unit
    }
}
