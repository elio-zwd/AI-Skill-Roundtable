@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.elio.jianyu.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elio.jianyu.network.AiModel
import com.elio.jianyu.network.AiProvider
import com.elio.jianyu.network.AiUseCase
import com.elio.jianyu.network.ApiKeySummary

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
    var selectedUseCase by remember { mutableStateOf<AiUseCase?>(null) }
    var keySheetProvider by remember { mutableStateOf<AiProvider?>(null) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .testTag("api_key_manager"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .testTag(AiManagementTestTags.ROOT),
        ) {
            SettingsTopBar(title = "AI 管理", onBack = onBack)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(AiManagementTestTags.CONTENT),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    SectionHeader(
                        title = "模型配置",
                        description = "为不同 AI 任务选择使用的模型。",
                    )
                }
                item {
                    ModelConfigurationCard(
                        uiState = uiState,
                        onOpenUseCase = { selectedUseCase = it },
                    )
                }
                item {
                    Text(
                        text = "API Key",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                item {
                    ProviderKeyCard(
                        uiState = uiState,
                        onOpenProvider = { provider ->
                            onSelectKeyProvider(provider)
                            keySheetProvider = provider
                        },
                    )
                }
                item {
                    ModelInfoCard()
                }
                item { Spacer(modifier = Modifier.height(4.dp)) }
            }
        }
    }

    selectedUseCase?.let { useCase ->
        ModelSelectionSheet(
            useCase = useCase,
            selectedModel = uiState.configuration.modelFor(useCase),
            onDismiss = { selectedUseCase = null },
            onSelectProvider = { provider -> onSelectProvider(useCase, provider) },
            onSelectModel = { model ->
                onSelectModel(useCase, model)
                selectedUseCase = null
            },
        )
    }

    keySheetProvider?.let { provider ->
        ProviderKeyManagementSheet(
            provider = provider,
            uiState = uiState,
            onDismiss = { keySheetProvider = null },
            onInputChange = onInputChange,
            onImport = onImport,
            onToggleKey = onToggleKey,
            onValidateKey = onValidateKey,
            onRequestDeleteKey = onRequestDeleteKey,
            onRequestClearProviderKeys = onRequestClearProviderKeys,
        )
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
private fun SectionHeader(
    title: String,
    description: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelConfigurationCard(
    uiState: AiManagementUiState,
    onOpenUseCase: (AiUseCase) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        AiUseCase.entries.forEachIndexed { index, useCase ->
            ModelUseCaseRow(
                useCase = useCase,
                model = uiState.configuration.modelFor(useCase),
                onClick = { onOpenUseCase(useCase) },
            )
            if (index != AiUseCase.entries.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ModelUseCaseRow(
    useCase: AiUseCase,
    model: AiModel,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(AiManagementTestTags.useCase(useCase.name))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = useCase.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = useCase.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
        ) {
            Text(
                text = model.displayName,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProviderKeyCard(
    uiState: AiManagementUiState,
    onOpenProvider: (AiProvider) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        AiProvider.entries.forEachIndexed { index, provider ->
            ProviderKeyRow(
                provider = provider,
                status = uiState.providerStatus(provider),
                onClick = { onOpenProvider(provider) },
            )
            if (index != AiProvider.entries.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ProviderKeyRow(
    provider: AiProvider,
    status: String,
    onClick: () -> Unit,
) {
    val subtitle = when (provider) {
        AiProvider.GEMINI -> "Google Gemini API"
        AiProvider.DEEPSEEK -> "DeepSeek API"
    }
    val badge = when (provider) {
        AiProvider.GEMINI -> "G"
        AiProvider.DEEPSEEK -> "D"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(AiManagementTestTags.keyProviderCard(provider.name))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "›",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.34f),
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "模型说明",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "联网检索仅显示支持 Google Search 的 Gemini 模型。嵌入与 Live 语音使用固定模型，不需要在这里配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModelSelectionSheet(
    useCase: AiUseCase,
    selectedModel: AiModel,
    onDismiss: () -> Unit,
    onSelectProvider: (AiProvider) -> Unit,
    onSelectModel: (AiModel) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AiManagementTestTags.MODEL_SHEET)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "选择${useCase.displayName}模型",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = useCase.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (useCase.supportedProviders.size > 1) {
                Text(
                    text = "提供商",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    useCase.supportedProviders.forEach { provider ->
                        FilterChip(
                            selected = selectedModel.provider == provider,
                            onClick = { onSelectProvider(provider) },
                            label = { Text(provider.displayName) },
                            modifier = Modifier.testTag(
                                AiManagementTestTags.provider("${useCase.name}_${provider.name}"),
                            ),
                        )
                    }
                }
            }

            Text(
                text = "模型",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            val models = AiModel.entries.filter { model ->
                model.provider == selectedModel.provider &&
                    (useCase != AiUseCase.WEB_GROUNDING || model.supportsWebGrounding)
            }
            models.forEachIndexed { index, model ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectModel(model) }
                        .testTag(AiManagementTestTags.model("${useCase.name}_${model.name}"))
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = model == selectedModel,
                        onClick = { onSelectModel(model) },
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = model.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (model == selectedModel) FontWeight.SemiBold else FontWeight.Normal,
                        )
                        Text(
                            text = model.provider.displayName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (index != models.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun ProviderKeyManagementSheet(
    provider: AiProvider,
    uiState: AiManagementUiState,
    onDismiss: () -> Unit,
    onInputChange: (String) -> Unit,
    onImport: () -> Unit,
    onToggleKey: (ApiKeySummary, Boolean) -> Unit,
    onValidateKey: (ApiKeySummary) -> Unit,
    onRequestDeleteKey: (ApiKeySummary) -> Unit,
    onRequestClearProviderKeys: () -> Unit,
) {
    val summaries = uiState.summariesFor(provider)
    val subtitle = when (provider) {
        AiProvider.GEMINI -> "Google Gemini API"
        AiProvider.DEEPSEEK -> "DeepSeek API"
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(AiManagementTestTags.KEY_SHEET),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${provider.displayName} API Key",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = uiState.providerStatus(provider),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            item {
                Text(
                    text = "添加 API Key",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            item {
                Text(
                    text = "每行粘贴一个，可一次导入多个；完整 Key 只写入本机加密保险箱。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.input,
                    onValueChange = onInputChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AiManagementTestTags.IMPORT_INPUT),
                    label = { Text("粘贴 API Key") },
                    minLines = 3,
                )
            }
            item {
                Button(
                    onClick = onImport,
                    enabled = uiState.canImport,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(AiManagementTestTags.IMPORT_BUTTON),
                ) {
                    Text("导入并验证")
                }
            }
            uiState.resultMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (uiState.keyProvider == provider) {
                uiState.currentKeyAccount?.let { account ->
                    item {
                        Text(
                            text = "当前会话优先使用：$account",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                uiState.storageError?.let { message ->
                    item {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Key 池",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = uiState.providerStatus(provider),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (summaries.isEmpty()) {
                item {
                    Text(
                        text = "还没有 ${provider.displayName} Key。导入后才能使用它的功能。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(summaries, key = ApiKeySummary::id) { summary ->
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
                    text = "完整 Key 仅保存在本机加密保险箱中。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                TextButton(
                    onClick = onRequestClearProviderKeys,
                    enabled = summaries.isNotEmpty(),
                ) {
                    Text(
                        text = "清空 ${provider.displayName} Key",
                        color = if (summaries.isNotEmpty()) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AiKeyRow(
    summary: ApiKeySummary,
    onToggle: (Boolean) -> Unit,
    onValidate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(AiManagementTestTags.keyRow(summary.id)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = summary.maskedKey,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = summary.enabled, onCheckedChange = onToggle)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = aiKeyStatusText(summary),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                IconButton(onClick = onValidate) {
                    Icon(Icons.Default.Refresh, contentDescription = "重新验证")
                }
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
            confirmButton = {
                TextButton(onClick = { onConfirmDelete(confirmation.summary) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
        AiManagementConfirmation.ClearProviderKeys -> AlertDialog(
            modifier = Modifier.testTag(AiManagementTestTags.CLEAR_CONFIRM),
            onDismissRequest = onDismiss,
            title = { Text("清空全部 ${provider.displayName} Key？") },
            text = { Text("此操作无法撤销，另一个提供商的 Key 不受影响。") },
            confirmButton = {
                TextButton(onClick = onConfirmClear) {
                    Text("全部删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        )
        null -> Unit
    }
}
