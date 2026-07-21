package com.example.skillroundtable

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.skillroundtable.network.ApiKeyPool
import com.example.skillroundtable.network.ApiKeySource
import com.example.skillroundtable.network.ApiKeySummary
import com.example.skillroundtable.network.ApiKeyValidationState
import kotlinx.coroutines.launch

private val SlateBg = Color(0xFF121824)
private val CardBg = Color(0xFF1E2638)
private val PrimaryAccent = Color(0xFF6366F1)
private val GoldAccent = Color(0xFFF59E0B)
private val TextPrimary = Color(0xFFF3F4F6)
private val TextSecondary = Color(0xFF9CA3AF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiKeyManagerScreen(
    currentSessionId: Long?,
    onBack: () -> Unit,
    onOpenTelemetry: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val summaries by ApiKeyPool.summaries.collectAsState()
    val storageError by ApiKeyPool.storageError.collectAsState()
    var input by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<ApiKeySummary?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ApiKeyPool.init(context)
    }

    val availableCount = summaries.count {
        it.enabled && it.validationState != ApiKeyValidationState.INVALID && it.remainingBanTimeMs <= 0L
    }
    val currentKey = remember(currentSessionId, summaries) {
        currentSessionId?.let { ApiKeyPool.getOrBindSessionKey(context, it) }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = SlateBg) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.bounceClick()) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回", tint = TextPrimary)
                }
                Text(
                    text = "Gemini API Key 管理",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KeyMetricCard("已保存", summaries.size.toString(), Modifier.weight(1f))
                        KeyMetricCard("可调度", availableCount.toString(), Modifier.weight(1f))
                        KeyMetricCard("上限", "50", Modifier.weight(1f))
                    }
                }

                storageError?.let { error ->
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.08f)),
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = Color.Red)
                                Spacer(Modifier.size(8.dp))
                                Text(error, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { showClearAllConfirm = true }) {
                                    Text("清空", color = Color.Red)
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.16f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("批量导入", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "支持 [key1,key2]、英文/中文逗号和逐行粘贴；自动去重。",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 6,
                                visualTransformation = PasswordVisualTransformation(),
                                placeholder = { Text("[xxxx, bbbbbb, ccccc]") }
                            )
                            resultMessage?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = GoldAccent, fontSize = 11.sp)
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val result = ApiKeyPool.importBatch(context, input)
                                    resultMessage = "新增 ${result.added}，重复 ${result.duplicates}，非法 ${result.invalid}，超限 ${result.overflow}"
                                    if (result.added > 0) {
                                        input = ""
                                        scope.launch { ApiKeyPool.validateKeys(context, result.importedIds) }
                                    }
                                },
                                enabled = input.isNotBlank() && summaries.size < 50,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryAccent)
                            ) {
                                Text("加密保存并验证")
                            }
                        }
                    }
                }

                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.7f)),
                        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.12f))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(
                                "当前会话：${currentKey?.account ?: "未绑定"}",
                                color = GoldAccent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                "服务器 Key Provider 已预留，当前未配置，暂不参与调度。",
                                color = TextSecondary,
                                fontSize = 11.sp
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

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("密钥列表", color = TextPrimary, fontWeight = FontWeight.Bold)
                        if (summaries.isNotEmpty()) {
                            TextButton(onClick = { showClearAllConfirm = true }) {
                                Text("全部删除", color = Color.Red)
                            }
                        }
                    }
                }

                if (summaries.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("尚未导入 Gemini API Key", color = TextSecondary)
                        }
                    }
                } else {
                    items(summaries, key = { it.id }) { summary ->
                        ApiKeyRow(
                            summary = summary,
                            onToggle = { disabled -> ApiKeyPool.setKeyDisabled(context, summary.id, disabled) },
                            onValidate = { scope.launch { ApiKeyPool.validateKey(context, summary.id) } },
                            onDelete = { deleteTarget = summary }
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除 ${target.displayName}？") },
            text = { Text("删除后无法从应用中恢复完整 Key，需要重新导入。") },
            confirmButton = {
                TextButton(onClick = {
                    ApiKeyPool.deleteKey(context, target.id)
                    deleteTarget = null
                }) { Text("删除", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            title = { Text("清空全部 API Key？") },
            text = { Text("此操作会删除加密保险箱和全部会话绑定，无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    val success = ApiKeyPool.clearAllKeys(context)
                    showClearAllConfirm = false
                    Toast.makeText(context, if (success) "已清空 API Key" else "清空失败", Toast.LENGTH_SHORT).show()
                }) { Text("全部删除", color = Color.Red) }
            },
            dismissButton = { TextButton(onClick = { showClearAllConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun KeyMetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.12f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(value, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(label, color = TextSecondary, fontSize = 10.sp)
        }
    }
}

@Composable
private fun ApiKeyRow(
    summary: ApiKeySummary,
    onToggle: (disabled: Boolean) -> Unit,
    onValidate: () -> Unit,
    onDelete: () -> Unit
) {
    val (statusText, statusColor) = keyStatusPresentation(summary)
    Card(
        colors = CardDefaults.cardColors(containerColor = CardBg),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(14.dp)
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
                            modifier = Modifier.background(
                                TextSecondary.copy(alpha = 0.1f),
                                RoundedCornerShape(6.dp)
                            ).padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(summary.maskedKey, color = TextSecondary, fontSize = 12.sp)
                }
                Switch(
                    checked = summary.enabled,
                    onCheckedChange = { enabled -> onToggle(!enabled) }
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                when (summary.validationState) {
                    ApiKeyValidationState.CHECKING -> CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = statusColor
                    )
                    ApiKeyValidationState.AVAILABLE -> Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(15.dp)
                    )
                    else -> Icon(
                        if (summary.validationState == ApiKeyValidationState.INVALID) Icons.Default.Close else Icons.Default.Info,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.size(6.dp))
                Text(statusText, color = statusColor, fontSize = 11.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onValidate, enabled = summary.validationState != ApiKeyValidationState.CHECKING) {
                    Icon(Icons.Default.Refresh, contentDescription = "重新验证", tint = TextSecondary)
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Red.copy(alpha = 0.8f))
                }
            }
        }
    }
}

private fun keyStatusPresentation(summary: ApiKeySummary): Pair<String, Color> {
    if (!summary.enabled) return "已禁用" to TextSecondary
    if (summary.remainingBanTimeMs > 0L) {
        val minutes = summary.remainingBanTimeMs / 60_000L
        return "熔断中，剩余 ${minutes} 分钟" to Color.Red
    }
    return when (summary.validationState) {
        ApiKeyValidationState.UNVERIFIED -> "未验证" to GoldAccent
        ApiKeyValidationState.CHECKING -> "验证中" to PrimaryAccent
        ApiKeyValidationState.AVAILABLE -> "可用" to Color(0xFF4CAF50)
        ApiKeyValidationState.INVALID -> (summary.validationMessage ?: "无效") to Color.Red
        ApiKeyValidationState.NETWORK_ERROR -> (summary.validationMessage ?: "网络异常") to GoldAccent
        ApiKeyValidationState.RATE_LIMITED -> "请求频率受限" to Color.Red
    }
}
