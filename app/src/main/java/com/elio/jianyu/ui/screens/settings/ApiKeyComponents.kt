package com.elio.jianyu.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.network.ApiKeySource
import com.elio.jianyu.network.ApiKeySummary
import com.elio.jianyu.ui.CardBg
import com.elio.jianyu.ui.GoldAccent
import com.elio.jianyu.ui.PrimaryAccent
import com.elio.jianyu.ui.TextPrimary
import com.elio.jianyu.ui.TextSecondary

@Composable
internal fun ApiKeyMetrics(
    savedCount: Int,
    availableCount: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        KeyMetricCard("已保存", savedCount.toString(), Modifier.weight(1f))
        KeyMetricCard("可调度", availableCount.toString(), Modifier.weight(1f))
        KeyMetricCard("上限", MAX_API_KEY_COUNT.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun KeyMetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.12f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
internal fun ApiKeyStorageErrorCard(
    message: String,
    onRequestClearAll: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = Color.Red)
            Spacer(Modifier.size(8.dp))
            Text(message, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onRequestClearAll) {
                Text("清空", color = Color.Red)
            }
        }
    }
}

@Composable
internal fun ApiKeyImportCard(
    input: String,
    resultMessage: String?,
    canImport: Boolean,
    onInputChange: (String) -> Unit,
    onImport: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.16f)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("批量导入", color = TextPrimary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "支持 [key1,key2]、英文/中文逗号和逐行粘贴；自动去重。",
                color = TextSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = input,
                onValueChange = onInputChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTestTags.API_KEY_IMPORT_INPUT),
                minLines = 3,
                maxLines = 6,
                visualTransformation = PasswordVisualTransformation(),
                placeholder = { Text("[xxxx, bbbbbb, ccccc]") },
            )
            resultMessage?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(message, color = GoldAccent, fontSize = 11.sp)
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onImport,
                enabled = canImport,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(SettingsTestTags.API_KEY_IMPORT_BUTTON),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent),
            ) {
                Text("加密保存并验证")
            }
        }
    }
}

@Composable
internal fun CurrentSessionKeyCard(
    account: String?,
    onOpenTelemetry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.7f)),
        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.12f)),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Text(
                "当前会话：${account ?: "未绑定"}",
                color = GoldAccent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                "服务器 Key Provider 已预留，当前未配置，暂不参与调度。",
                color = TextSecondary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = onOpenTelemetry) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text("查看熔断与遥测日志")
            }
        }
    }
}

@Composable
internal fun ApiKeyListHeader(
    hasKeys: Boolean,
    onRequestClearAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("密钥列表", color = TextPrimary, fontWeight = FontWeight.Bold)
        if (hasKeys) {
            TextButton(onClick = onRequestClearAll) {
                Text("全部删除", color = Color.Red)
            }
        }
    }
}

@Composable
internal fun ApiKeyEmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("尚未导入 Gemini API Key", color = TextSecondary)
    }
}

@Composable
internal fun ApiKeyRow(
    summary: ApiKeySummary,
    onToggle: (disabled: Boolean) -> Unit,
    onValidate: () -> Unit,
    onDelete: () -> Unit,
) {
    val presentation = keyStatusPresentation(summary)
    val statusColor = settingsToneColor(presentation.tone)

    Card(
        modifier = Modifier.testTag(SettingsTestTags.apiKeyRow(summary.id)),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(summary.displayName, color = TextPrimary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(8.dp))
                        Text(
                            if (summary.source == ApiKeySource.LOCAL) "本地" else "服务器",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier
                                .background(TextSecondary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(summary.maskedKey, color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = summary.enabled,
                    onCheckedChange = { enabled -> onToggle(!enabled) },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (presentation.icon) {
                    ApiKeyStatusIcon.PROGRESS -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = statusColor,
                    )
                    ApiKeyStatusIcon.SUCCESS -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(15.dp),
                    )
                    ApiKeyStatusIcon.INVALID -> Icon(
                        Icons.Default.Close,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(15.dp),
                    )
                    ApiKeyStatusIcon.INFO -> Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(
                    presentation.text,
                    color = statusColor,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onValidate,
                    enabled = summary.validationState != com.elio.jianyu.network.ApiKeyValidationState.CHECKING,
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "重新验证", tint = TextSecondary)
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.Red.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ApiKeyConfirmationDialogs(
    confirmation: ApiKeyConfirmation?,
    onDismiss: () -> Unit,
    onConfirmDelete: (ApiKeySummary) -> Unit,
    onConfirmClearAll: () -> Unit,
) {
    when (confirmation) {
        is ApiKeyConfirmation.Delete -> {
            val target = confirmation.summary
            AlertDialog(
                modifier = Modifier.testTag(SettingsTestTags.API_KEY_DELETE_CONFIRM),
                onDismissRequest = onDismiss,
                title = { Text("删除 ${target.displayName}？") },
                text = { Text("删除后无法从应用中恢复完整 Key，需要重新导入。") },
                confirmButton = {
                    TextButton(onClick = { onConfirmDelete(target) }) {
                        Text("删除", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                },
            )
        }
        ApiKeyConfirmation.ClearAll -> {
            AlertDialog(
                modifier = Modifier.testTag(SettingsTestTags.API_KEY_CLEAR_CONFIRM),
                onDismissRequest = onDismiss,
                title = { Text("清空全部 API Key？") },
                text = { Text("此操作会删除加密保险箱和全部会话绑定，无法撤销。") },
                confirmButton = {
                    TextButton(onClick = onConfirmClearAll) {
                        Text("全部删除", color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                },
            )
        }
        null -> Unit
    }
}
