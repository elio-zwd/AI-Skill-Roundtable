package com.elio.jianyu.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.jianyu.telemetry.TelemetryEvent
import com.elio.jianyu.telemetry.TelemetryLevel
import com.elio.jianyu.ui.CardBg
import com.elio.jianyu.ui.GoldAccent
import com.elio.jianyu.ui.PrimaryAccent
import com.elio.jianyu.ui.TextPrimary
import com.elio.jianyu.ui.TextSecondary

@Composable
internal fun TelemetryPrivacyCard(
    uiState: TelemetryUiState,
    onSelectLevel: (TelemetryLevel) -> Unit,
    onToggleContentDebug: () -> Unit,
    onClearTelemetry: () -> Unit,
    onDisableContentDebugAndPurge: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("遥测隐私级别", fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                telemetryLevelDescription(
                    level = uiState.level,
                    remainingMinutes = uiState.remainingContentDebugMinutes,
                ),
                fontSize = 12.sp,
                color = TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSelectLevel(TelemetryLevel.OFF) }) {
                    Text("关闭")
                }
                OutlinedButton(onClick = { onSelectLevel(TelemetryLevel.METADATA_ONLY) }) {
                    Text("仅元数据")
                }
                Button(onClick = onToggleContentDebug) {
                    Text(
                        if (uiState.level == TelemetryLevel.CONTENT_DEBUG) {
                            "关闭正文调试"
                        } else {
                            "临时正文调试"
                        },
                    )
                }
            }
            Divider(color = TextSecondary.copy(alpha = 0.15f))
            Text(
                "事件 ${uiState.events.size} 条 · 估算占用 ${uiState.estimatedBytes} bytes · Metadata 最长 7 天",
                fontSize = 11.sp,
                color = TextSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClearTelemetry) {
                    Text("立即清空全部遥测")
                }
                if (uiState.level == TelemetryLevel.CONTENT_DEBUG) {
                    OutlinedButton(onClick = onDisableContentDebugAndPurge) {
                        Text("关闭并删除预览")
                    }
                }
            }
            uiState.storageError?.let { error ->
                Text(error, color = Color.Red, fontSize = 11.sp)
            }
        }
    }
}

@Composable
internal fun CurrentTelemetryKeyCard(uiState: TelemetryUiState) {
    Card(colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.6f))) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("当前会话与 Key", fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(
                "Key ID：${uiState.currentKeyId ?: "未分配"}",
                fontSize = 12.sp,
                color = GoldAccent,
            )
            Text(
                "显示名：${uiState.currentKeyAccount ?: "无"}",
                fontSize = 11.sp,
                color = TextSecondary,
            )
            Text(
                "Key 状态：${uiState.availableKeyCount} 可用 / ${uiState.totalKeyCount} 总数",
                fontSize = 11.sp,
                color = TextSecondary,
            )
        }
    }
}

@Composable
internal fun TelemetryEventsEmptyState() {
    Box(
        modifier = Modifier.fillMaxWidth().height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("暂无遥测事件", color = TextSecondary)
    }
}

@Composable
internal fun TelemetryEventCard(
    event: TelemetryEvent,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val presentation = telemetryEventPresentation(event)
    val borderColor = if (presentation.isSuccess) {
        PrimaryAccent.copy(alpha = 0.2f)
    } else {
        Color.Red.copy(alpha = 0.25f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SettingsTestTags.telemetryEvent(event.id))
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, borderColor),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "[${event.keyId ?: "none"}] ${event.model ?: event.endpoint}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        presentation.statusText,
                        fontSize = 10.sp,
                        color = TextSecondary,
                    )
                    Text(
                        if (expanded) "▲" else "▼",
                        fontSize = 10.sp,
                        color = TextSecondary,
                    )
                }
            }
            Text(event.endpoint, fontSize = 10.sp, color = TextSecondary)
            event.failureType?.let { failureType ->
                Text("错误分类：$failureType", fontSize = 10.sp, color = Color.Red)
            }
            if (event.hasThoughtStep) {
                Text(
                    "响应包含 thought step（摘要未保存）",
                    fontSize = 10.sp,
                    color = GoldAccent,
                )
            }
            Text(
                if (expanded) {
                    "点击收起详情 ▲"
                } else if (event.containsContentPreview) {
                    "含脱敏截断预览，点击展开 ▼"
                } else {
                    "点击展开诊断详情 ▼"
                },
                fontSize = 10.sp,
                color = if (event.containsContentPreview) GoldAccent else TextSecondary,
            )
            if (expanded) {
                Divider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = borderColor,
                )
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("请求时间：${formatTelemetryTimestamp(event.timestamp)}", fontSize = 10.sp, color = TextSecondary)
                    Text("请求耗时：${event.durationMs} ms", fontSize = 10.sp, color = TextSecondary)
                    event.statusCode?.let { code ->
                        val codeColor = if (code in 200..299) Color(0xFF10B981) else Color.Red
                        Text("状态码：HTTP $code", fontSize = 10.sp, color = codeColor)
                    }
                    event.errorMessage?.let { error ->
                        Text("异常详情：$error", fontSize = 10.sp, color = Color.Red, fontWeight = FontWeight.Medium)
                    }
                    if (!event.containsContentPreview) {
                        Text("元数据诊断信息（未开启临时正文调试）", fontSize = 10.sp, color = TextSecondary.copy(alpha = 0.7f))
                    }
                }
                event.requestPreview?.let { preview ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("请求预览", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GoldAccent)
                    Text(preview, fontSize = 10.sp, color = TextSecondary)
                }
                event.responsePreview?.let { preview ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("响应预览", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GoldAccent)
                    Text(preview, fontSize = 10.sp, color = TextSecondary)
                }
            }
        }
    }
}

private fun formatTelemetryTimestamp(timestamp: Long): String {
    val date = java.util.Date(timestamp)
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault())
    return sdf.format(date)
}

@Composable
internal fun TelemetryConfirmationDialogs(
    confirmation: TelemetryConfirmation?,
    onDismiss: () -> Unit,
    onConfirmContentDebug: () -> Unit,
) {
    when (confirmation) {
        TelemetryConfirmation.ContentDebug -> AlertDialog(
            modifier = Modifier.testTag(SettingsTestTags.TELEMETRY_CONTENT_DEBUG_CONFIRM),
            onDismissRequest = onDismiss,
            title = { Text("开启临时正文调试？") },
            text = {
                Text(
                    "开启后，应用会在本机临时保存经过脱敏和截断的请求/回复预览，最长 24 小时。请勿在调试期间输入密码、私钥或其他高度敏感信息。Release 构建不允许开启。",
                )
            },
            confirmButton = {
                Button(onClick = onConfirmContentDebug) {
                    Text("确认开启")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            },
        )
        null -> Unit
    }
}
