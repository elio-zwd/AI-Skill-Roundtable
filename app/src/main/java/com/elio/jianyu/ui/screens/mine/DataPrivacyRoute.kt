package com.elio.jianyu.ui.screens.mine

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.backup.AndroidKeystoreSnapshotKeyProvider
import com.elio.jianyu.backup.BackupOperationGate
import com.elio.jianyu.backup.SnapshotCatalog
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.network.AiManager
import com.elio.jianyu.network.AiProvider
import com.elio.jianyu.telemetry.CloudInteractionSettings
import com.elio.jianyu.telemetry.TelemetryRepository
import com.elio.jianyu.ui.components.JianyuMetadataRow
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard
import com.elio.jianyu.ui.settings.AppPreferences
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object DataPrivacyTestTags {
    const val SCREEN = "data_privacy_screen"
    const val EXPORT = "data_privacy_export"
    const val DELETE = "data_privacy_delete"
    const val DELETE_CONFIRMATION = "data_privacy_delete_confirmation"
    const val DELETE_CONFIRM = "data_privacy_delete_confirm"
}

private data class DataOverview(
    val issueCount: Int = 0,
    val materialCount: Int = 0,
    val artifactCount: Int = 0,
    val personalContextCount: Int = 0,
)

@Serializable
internal data class ExportPayload(
    val format: String = "jianyu-readable-export-v1",
    val exportedAt: Long,
    val appId: String = "com.elio.jianyu",
    val notice: String = "不包含完整 API Key；仅包含当前见域 App 可导出的数据。",
    val issues: List<ExportIssue>,
    val materials: List<ExportMaterial>,
    val personalContexts: List<ExportPersonalContext>,
)

@Serializable
internal data class ExportIssue(
    val id: String,
    val title: String,
    val state: String,
    val messages: List<ExportMessage>,
    val artifacts: List<ExportArtifact>,
)

@Serializable
internal data class ExportMessage(val id: Long, val sender: String, val text: String, val timestamp: Long)

@Serializable
internal data class ExportArtifact(val id: String, val title: String, val type: String, val content: String)

@Serializable
internal data class ExportMaterial(val id: String, val title: String, val sourceType: String, val content: String)

@Serializable
internal data class ExportPersonalContext(val id: String, val title: String, val sensitive: Boolean, val content: String)

@Composable
fun DataPrivacyRoute(
    repository: JianyuRepository,
    onBack: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenTelemetry: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(context) {
        CloudInteractionSettings.init(context)
        TelemetryRepository.init(context)
    }
    val cloudInteractionEnabled by CloudInteractionSettings.enabled.collectAsState()
    val scope = rememberCoroutineScope()
    var overview by remember { mutableStateOf<DataOverview?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    var deleteDialog by remember { mutableStateOf(false) }
    var deleteInput by remember { mutableStateOf("") }

    fun loadOverview() {
        scope.launch {
            val result = withContext(Dispatchers.IO) { readOverview(repository) }
            overview = result.first
            loadError = result.second
        }
    }
    LaunchedEffect(repository) { loadOverview() }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                val result = withContext(Dispatchers.IO) { writeExport(context, repository, uri) }
                busy = false
                actionMessage = result
            }
        }
    }

    JianyuPageShell(
        title = "数据与隐私",
        subtitle = "你的数据由你决定如何使用",
        onBack = onBack,
        contentScrollable = true,
        modifier = Modifier.testTag(DataPrivacyTestTags.SCREEN),
    ) {
        JianyuStateCard(
            title = "本地数据概览",
            message = "这里显示当前见域 App 可读取的数据数量。只统计当前见域 App 可读取的数据，不访问其他应用的数据。",
        )
        if (loadError != null) {
            JianyuStateCard("暂时无法读取数据概览", loadError!!, actionLabel = "重试", onAction = ::loadOverview)
        } else if (overview == null) {
            Text("正在读取数据概览…")
        } else {
            JianyuMetadataRow("会话数据", "${overview!!.issueCount} 个会话")
            JianyuMetadataRow("资料", "${overview!!.materialCount} 项")
            JianyuMetadataRow("正式成果", "${overview!!.artifactCount} 项")
            JianyuMetadataRow("个人背景", "${overview!!.personalContextCount} 项")
        }
        JianyuStateCard(
            title = "云端交互授权",
            message = if (cloudInteractionEnabled) {
                "已允许需要云端模型的功能按本次确认发送选定内容。"
            } else {
                "当前未开启额外的云端交互授权。具体请求仍会在执行前显示确认。"
            },
        )
        Button(
            onClick = { exportLauncher.launch("jianyu-data-${System.currentTimeMillis()}.json") },
            enabled = !busy,
            modifier = Modifier.testTag(DataPrivacyTestTags.EXPORT),
        ) { Text(if (busy) "正在处理…" else "导出我的数据") }
        Button(onClick = onOpenBackup, enabled = !busy) { Text("备份与恢复") }
        Button(onClick = onOpenTelemetry, enabled = !busy) { Text("遥测与诊断") }
        actionMessage?.let { JianyuStateCard("导出结果", it) }
        JianyuStateCard(
            title = "删除所有本地数据",
            message = "将清除当前 App 的会话、资料、成果、个人背景、设备快照、应用偏好、API Key 和遥测记录。你另存到系统文件位置的导出或可移植备份不会自动删除；其他应用的数据不会被访问。",
            actionLabel = "进入删除确认",
            actionTestTag = DataPrivacyTestTags.DELETE,
            onAction = {
                deleteInput = ""
                deleteDialog = true
            },
        )
        Text(
            "删除前请确认已保留需要的外部导出或可移植备份。删除操作会清理当前见域 App 私有的设备快照。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (deleteDialog) {
        AlertDialog(
            modifier = Modifier.testTag(DataPrivacyTestTags.DELETE_CONFIRMATION),
            onDismissRequest = { if (!busy) deleteDialog = false },
            title = { Text("删除所有本地数据？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("此操作会清理当前见域 App 的本地数据和设备快照；外部导出/可移植备份不会自动删除，其他应用的数据不会被访问。")
                    OutlinedTextField(
                        value = deleteInput,
                        onValueChange = { deleteInput = it },
                        label = { Text("请输入“删除”") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteInput.trim() == "删除" && !busy,
                    modifier = Modifier.testTag(DataPrivacyTestTags.DELETE_CONFIRM),
                    onClick = {
                        scope.launch {
                            busy = true
                            val result = withContext(Dispatchers.IO) { clearAllLocalData(context, repository) }
                            busy = false
                            deleteDialog = false
                            actionMessage = result
                            if (result == "本地数据已清除") loadOverview()
                        }
                    },
                ) { Text("确认删除") }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }, enabled = !busy) { Text("取消") } },
        )
    }
}

private suspend fun readOverview(repository: JianyuRepository): Pair<DataOverview?, String?> {
    return try {
        val issues = repository.listIssueNavigation(IssueLifecycleState.entries.toSet()).valueOrNull()
            ?: return null to "会话数据暂时无法读取。"
        val materials = repository.listMaterials(
            com.elio.jianyu.data.MaterialFilter(
                lifecycles = setOf(
                    ContextSourceLifecycle.ACTIVE,
                    ContextSourceLifecycle.DISABLED,
                    ContextSourceLifecycle.ARCHIVED,
                ),
            ),
        ).valueOrNull() ?: return null to "资料暂时无法读取。"
        val personal = repository.listPersonalContexts(
            com.elio.jianyu.data.PersonalContextFilter(
                lifecycles = setOf(
                    ContextSourceLifecycle.ACTIVE,
                    ContextSourceLifecycle.DISABLED,
                    ContextSourceLifecycle.ARCHIVED,
                ),
            ),
        ).valueOrNull() ?: return null to "个人背景暂时无法读取。"
        var artifactCount = 0
        issues.forEach { item ->
            when (val recovered = repository.recoverIssue(item.issue.id)) {
                is RepositoryResult.Success -> artifactCount += recovered.value.resources.artifacts.size
                is RepositoryResult.Failure ->
                    return null to "正式成果统计暂时无法读取。"
            }
        }
        DataOverview(issues.size, materials.size, artifactCount, personal.size) to null
    } catch (_: Throwable) {
        null to "本地数据暂时无法读取。"
    }
}

private suspend fun writeExport(context: Context, repository: JianyuRepository, uri: Uri): String {
    return try {
        val json = buildExportJson(repository)
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.writer(Charsets.UTF_8).use { writer -> writer.write(json) }
        } ?: return "无法打开导出位置，请重新选择文件。"
        "已导出结构化数据；文件不包含完整 API Key。"
    } catch (_: Throwable) {
        "导出失败，当前数据未被删除。"
    }
}

internal suspend fun buildExportJson(repository: JianyuRepository): String {
    val issueItems = requireExportSuccess(
        repository.listIssueNavigation(IssueLifecycleState.entries.toSet()),
    )
    val issues = issueItems.map { item ->
        val recovery = requireExportSuccess(repository.recoverIssue(item.issue.id))
        ExportIssue(
            id = item.issue.id,
            title = item.issue.title,
            state = item.lifecycle.state.storageValue,
            messages = recovery.core.messages.map { message ->
                ExportMessage(message.id, message.senderName, message.text, message.timestamp)
            },
            artifacts = recovery.resources.artifacts.map { artifact ->
                ExportArtifact(artifact.id, artifact.title, artifact.artifactType, artifact.content)
            },
        )
    }
    val materials = requireExportSuccess(
        repository.listMaterials(
            com.elio.jianyu.data.MaterialFilter(
                lifecycles = setOf(
                    ContextSourceLifecycle.ACTIVE,
                    ContextSourceLifecycle.DISABLED,
                    ContextSourceLifecycle.ARCHIVED,
                ),
            ),
        ),
    ).map { material ->
        ExportMaterial(material.id, material.title, material.sourceType, material.content)
    }
    val personal = requireExportSuccess(
        repository.listPersonalContexts(
            com.elio.jianyu.data.PersonalContextFilter(
                lifecycles = setOf(
                    ContextSourceLifecycle.ACTIVE,
                    ContextSourceLifecycle.DISABLED,
                    ContextSourceLifecycle.ARCHIVED,
                ),
            ),
        ),
    ).map { item ->
        ExportPersonalContext(item.id, item.title, item.sensitive, item.content)
    }
    return Json { prettyPrint = true; explicitNulls = false }
        .encodeToString(
            ExportPayload.serializer(),
            ExportPayload(
                exportedAt = System.currentTimeMillis(),
                issues = issues,
                materials = materials,
                personalContexts = personal,
            ),
        )
}

private suspend fun clearAllLocalData(context: Context, repository: JianyuRepository): String {
    return try {
        BackupOperationGate.forContext(context).withWriteLock {
            when (repository.clearAllData()) {
                is RepositoryResult.Success -> {
                    val keyResults = AiProvider.entries.map { provider ->
                        AiManager.keys(context, provider).clear()
                    }
                    val modelConfigurationCleared = AiManager.configuration(context).reset()
                    val telemetryCleared = TelemetryRepository.clearAllTelemetry(context)
                    val cloudSettingsCleared = CloudInteractionSettings.setEnabled(context, false)
                    val appPreferencesCleared = AppPreferences.reset(context)
                    val snapshotsCleared = SnapshotCatalog.clearAll(context)
                    val snapshotKeyCleared = AndroidKeystoreSnapshotKeyProvider().deleteExisting()
                    val audioFilesCleared = clearAppOwnedAudioFiles(context)
                    val cleanupSucceeded = keyResults.all { it } &&
                        modelConfigurationCleared &&
                        telemetryCleared &&
                        cloudSettingsCleared &&
                        appPreferencesCleared &&
                        snapshotsCleared &&
                        snapshotKeyCleared &&
                        audioFilesCleared
                    if (cleanupSucceeded) {
                        "本地数据已清除"
                    } else {
                        "数据库已清空，但部分本地设置、文件或设备快照清理失败，请重试。"
                    }
                }
                is RepositoryResult.Failure -> "本地数据清除失败；未确认清除完成。"
            }
        }
    } catch (_: Throwable) {
        "本地数据清除失败；未确认清除完成。"
    }
}
private fun clearAppOwnedAudioFiles(context: Context): Boolean {
    val audioDirectory = File(context.applicationContext.filesDir, "jianyu-audio")
    val audioCleared = !audioDirectory.exists() || audioDirectory.deleteRecursively()
    val cacheCleared = context.applicationContext.cacheDir
        .listFiles()
        .orEmpty()
        .map { file -> runCatching { file.deleteRecursively() }.getOrDefault(false) }
        .all { it }
    return audioCleared && cacheCleared
}

private fun <T> requireExportSuccess(result: RepositoryResult<T>): T = when (result) {
    is RepositoryResult.Success -> result.value
    is RepositoryResult.Failure -> throw IllegalStateException("readable_export_source_unavailable")
}

private fun <T> RepositoryResult<T>.valueOrNull(): T? =
    (this as? RepositoryResult.Success)?.value
