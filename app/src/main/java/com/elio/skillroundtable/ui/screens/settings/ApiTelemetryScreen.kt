package com.elio.skillroundtable.ui.screens.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elio.skillroundtable.network.ApiKeyPool
import com.elio.skillroundtable.telemetry.CloudInteractionSettings
import com.elio.skillroundtable.telemetry.TelemetryLevel
import com.elio.skillroundtable.telemetry.TelemetryRepository
import com.elio.skillroundtable.ui.CardBg
import com.elio.skillroundtable.ui.GoldAccent
import com.elio.skillroundtable.ui.PrimaryAccent
import com.elio.skillroundtable.ui.SlateBg
import com.elio.skillroundtable.ui.TextPrimary
import com.elio.skillroundtable.ui.TextSecondary
import com.elio.skillroundtable.ui.components.bounceClick

@Composable
fun ApiTelemetryScreen(
    currentSessionId: Long?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        ApiKeyPool.init(context)
        TelemetryRepository.init(context)
        CloudInteractionSettings.init(context)
    }
    val events by TelemetryRepository.events.collectAsState()
    val level by TelemetryRepository.level.collectAsState()
    val storageError by TelemetryRepository.storageError.collectAsState()
    val cloudInteractionEnabled by CloudInteractionSettings.enabled.collectAsState()
    var expandedEventId by remember { mutableStateOf<String?>(null) }
    var showContentDebugWarning by remember { mutableStateOf(false) }
    var showCloudWarning by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }

    val keyStatuses = remember(currentSessionId, refreshTrigger) {
        ApiKeyPool.getKeyStatuses(context)
    }
    val currentKeyInfo = remember(currentSessionId, refreshTrigger) {
        currentSessionId?.let { ApiKeyPool.getOrBindSessionKey(context, it) }
    }
    val expiresAt = TelemetryRepository.contentDebugExpiresAt(context)
    val remainingMinutes = expiresAt?.let { ((it - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L) }
    val estimatedBytes = TelemetryRepository.estimatedBytes()

    Surface(modifier = Modifier.fillMaxSize(), color = SlateBg) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.bounceClick()) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Text("隐私、遥测与 API 诊断", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("遥测隐私级别", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text(
                                when (level) {
                                    TelemetryLevel.OFF -> "关闭：不创建本地遥测事件"
                                    TelemetryLevel.METADATA_ONLY -> "仅元数据（默认）：不读取或保存请求/回复正文"
                                    TelemetryLevel.CONTENT_DEBUG -> "临时正文调试：本机保存脱敏、截断预览，剩余约 ${remainingMinutes ?: 0} 分钟"
                                },
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    TelemetryRepository.setLevel(
                                        context,
                                        TelemetryLevel.OFF
                                    )
                                }) { Text("关闭") }
                                OutlinedButton(onClick = {
                                    TelemetryRepository.setLevel(
                                        context,
                                        TelemetryLevel.METADATA_ONLY
                                    )
                                }) { Text("仅元数据") }
                                Button(onClick = {
                                    if (level == TelemetryLevel.CONTENT_DEBUG) {
                                        TelemetryRepository.disableContentDebugAndPurgePreviews(context)
                                    } else {
                                        showContentDebugWarning = true
                                    }
                                }) { Text(if (level == TelemetryLevel.CONTENT_DEBUG) "关闭正文调试" else "临时正文调试") }
                            }
                            Divider(color = TextSecondary.copy(alpha = 0.15f))
                            Text("事件 ${events.size} 条 · 估算占用 ${estimatedBytes} bytes · Metadata 最长 7 天", fontSize = 11.sp, color = TextSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    val ok = TelemetryRepository.clearAllTelemetry(context)
                                    Toast.makeText(context, if (ok) "遥测已清空" else "遥测清空失败", Toast.LENGTH_SHORT).show()
                                }) { Text("立即清空全部遥测") }
                                if (level == TelemetryLevel.CONTENT_DEBUG) {
                                    OutlinedButton(onClick = {
                                        TelemetryRepository.disableContentDebugAndPurgePreviews(context)
                                    }) { Text("关闭并删除预览") }
                                }
                            }
                            storageError?.let { Text(it, color = Color.Red, fontSize = 11.sp) }
                        }
                    }
                }

                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardBg)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("云端会话链优化", fontWeight = FontWeight.Bold, color = TextPrimary)
                                Text(
                                    "默认关闭。关闭不会阻止模型请求发送给 Gemini，只是不额外启用持久化 Interaction 链。",
                                    fontSize = 11.sp,
                                    color = TextSecondary
                                )
                            }
                            Switch(
                                checked = cloudInteractionEnabled,
                                onCheckedChange = { enabled ->
                                    if (enabled) showCloudWarning = true
                                    else CloudInteractionSettings.setEnabled(context, false)
                                }
                            )
                        }
                    }
                }

                item {
                    Card(colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.6f))) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("当前会话与 Key", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Key ID：${currentKeyInfo?.id ?: "未分配"}", fontSize = 12.sp, color = GoldAccent)
                            Text("显示名：${currentKeyInfo?.account ?: "无"}", fontSize = 11.sp, color = TextSecondary)
                            Text("Key 状态：${keyStatuses.count { !it.isBanned && !it.isManualDisabled }} 可用 / ${keyStatuses.size} 总数", fontSize = 11.sp, color = TextSecondary)
                        }
                    }
                }

                item {
                    Text("最近本地遥测事件", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }

                if (events.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                            Text("暂无遥测事件", color = TextSecondary)
                        }
                    }
                } else {
                    items(events, key = { it.id }) { event ->
                        val success = event.statusCode?.let { it in 200..299 } == true
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable {
                                if (event.containsContentPreview) {
                                    expandedEventId = if (expandedEventId == event.id) null else event.id
                                }
                            },
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            border = BorderStroke(1.dp, if (success) PrimaryAccent.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.25f))
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("[${event.keyId ?: "none"}] ${event.model ?: event.endpoint}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text("${event.statusCode ?: "ERR"} · ${event.durationMs}ms", fontSize = 10.sp, color = TextSecondary)
                                }
                                Text(event.endpoint, fontSize = 10.sp, color = TextSecondary)
                                event.failureType?.let { Text("错误分类：$it", fontSize = 10.sp, color = Color.Red) }
                                if (event.hasThoughtStep) Text("响应包含 thought step（摘要未保存）", fontSize = 10.sp, color = GoldAccent)
                                if (event.containsContentPreview) {
                                    Text("含脱敏截断预览，点击展开", fontSize = 10.sp, color = GoldAccent)
                                }
                                if (expandedEventId == event.id) {
                                    event.requestPreview?.let {
                                        Text("请求预览", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GoldAccent)
                                        Text(it, fontSize = 10.sp, color = TextSecondary)
                                    }
                                    event.responsePreview?.let {
                                        Text("响应预览", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = GoldAccent)
                                        Text(it, fontSize = 10.sp, color = TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showContentDebugWarning) {
        AlertDialog(
            onDismissRequest = { showContentDebugWarning = false },
            title = { Text("开启临时正文调试？") },
            text = { Text("开启后，应用会在本机临时保存经过脱敏和截断的请求/回复预览，最长 24 小时。请勿在调试期间输入密码、私钥或其他高度敏感信息。Release 构建不允许开启。") },
            confirmButton = {
                Button(onClick = {
                    val ok = TelemetryRepository.enableContentDebug(context)
                    Toast.makeText(context, if (ok) "正文调试已开启，24 小时后自动过期" else "当前构建不允许开启", Toast.LENGTH_SHORT).show()
                    showContentDebugWarning = false
                }) { Text("确认开启") }
            },
            dismissButton = { TextButton(onClick = { showContentDebugWarning = false }) { Text("取消") } }
        )
    }

    if (showCloudWarning) {
        AlertDialog(
            onDismissRequest = { showCloudWarning = false },
            title = { Text("启用云端会话链优化？") },
            text = { Text("开启后，请求上下文会继续发送给 Google Gemini，并允许服务商使用持久化 Interaction 链维持续写。服务商侧保留受其政策约束，本应用无法控制远端保留或保证远端删除。") },
            confirmButton = {
                Button(onClick = {
                    CloudInteractionSettings.setEnabled(context, true)
                    showCloudWarning = false
                }) { Text("确认启用") }
            },
            dismissButton = { TextButton(onClick = { showCloudWarning = false }) { Text("取消") } }
        )
    }
}
