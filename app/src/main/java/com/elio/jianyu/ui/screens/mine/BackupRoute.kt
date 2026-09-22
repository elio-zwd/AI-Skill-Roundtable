package com.elio.jianyu.ui.screens.mine

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.elio.jianyu.backup.BackupErrorCode
import com.elio.jianyu.backup.BackupException
import com.elio.jianyu.backup.BackupProtocol
import com.elio.jianyu.backup.DeviceSnapshotService
import com.elio.jianyu.backup.PortableBackupService
import com.elio.jianyu.backup.SnapshotCatalog
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object BackupTestTags {
    const val SCREEN = "backup_screen"
    const val CREATE = "backup_create"
    const val RESTORE = "backup_restore"
    const val SNAPSHOT_CREATE = "backup_snapshot_create"
    const val SNAPSHOT_DELETE = "backup_snapshot_delete"
    const val PASSWORD_SHEET = "backup_password_sheet"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRoute(
    @Suppress("UNUSED_PARAMETER") repository: JianyuRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passwordSheet by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingTarget by remember { mutableStateOf<android.net.Uri?>(null) }
    var snapshots by remember { mutableStateOf(SnapshotCatalog.list(context)) }
    var showImportInfo by remember { mutableStateOf(false) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(BackupProtocol.portableMime),
    ) { uri ->
        if (uri != null) {
            pendingTarget = uri
            passwordSheet = true
        }
    }

    JianyuPageShell(
        title = "备份与恢复",
        subtitle = "正式加密导出与设备绑定快照",
        onBack = onBack,
        contentScrollable = true,
        modifier = Modifier.testTag(BackupTestTags.SCREEN),
    ) {
        JianyuStateCard(
            title = "创建可移植备份",
            message = "使用 Argon2id 与 AES-256-GCM 加密，会话、资料、成果和已确认个人背景按白名单导出；API Key、Keystore、令牌和临时数据永不写入。",
            actionLabel = "创建备份",
            actionTestTag = BackupTestTags.CREATE,
            onAction = { createLauncher.launch("jianyu-backup-${System.currentTimeMillis()}${BackupProtocol.portableExtension}") },
        )
        Text("备份密码只在本次操作内存中使用，不会保存，也不能从文件恢复。", color = MaterialTheme.colorScheme.onSurfaceVariant)

        JianyuStateCard(
            title = "设备绑定恢复快照",
            message = "快照保存在 App 私有 noBackup 目录，使用独立 Android Keystore 密钥；创建前会冻结活动工作、执行 WAL checkpoint，并在重开数据库后完成完整校验。",
            actionLabel = "创建设备快照",
            actionTestTag = BackupTestTags.SNAPSHOT_CREATE,
            onAction = {
                scope.launch {
                    busy = true
                    message = withContext(Dispatchers.IO) {
                        runCatching {
                            val result = DeviceSnapshotService(context).createSnapshot()
                            snapshots = SnapshotCatalog.list(context)
                            "设备快照已验证并保存：${result.snapshotId}"
                        }.getOrElse(::formatBackupError)
                    }
                    busy = false
                }
            },
        )
        snapshots.forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(entry.snapshotId, style = MaterialTheme.typography.titleSmall)
                    Text("${entry.sizeBytes} bytes", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry.note.isNotBlank()) Text(entry.note, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            message = withContext(Dispatchers.IO) {
                                runCatching {
                                    SnapshotCatalog.delete(context, entry.snapshotId)
                                    snapshots = SnapshotCatalog.list(context)
                                    "设备快照已删除。"
                                }.getOrElse(::formatBackupError)
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.testTag(BackupTestTags.SNAPSHOT_DELETE),
                ) { Text("删除") }
            }
        }

        Text("导入与数据库替换", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { showImportInfo = true },
            enabled = !busy,
            modifier = Modifier.testTag(BackupTestTags.RESTORE),
        ) { Text("查看导入说明") }
        JianyuStateCard(
            title = "恢复边界",
            message = "当前版本只创建并验证正式 Portable Backup 与 Device Snapshot；Portable 导入、差异预览和数据库替换属于 PR09-14A/14B，尚未开放，不会误删当前数据。",
        )
        message?.let { JianyuStateCard("操作结果", it) }
    }

    if (passwordSheet && pendingTarget != null) {
        BackupPasswordSheet(
            operation = busy,
            onDismiss = { if (!busy) { passwordSheet = false; pendingTarget = null } },
            onConfirm = { password ->
                scope.launch {
                    busy = true
                    message = withContext(Dispatchers.IO) {
                        runCatching {
                            PortableBackupService(context).createToUri(password, pendingTarget!!)
                            "可移植备份已加密、完整验证并保存。"
                        }.getOrElse(::formatBackupError)
                    }
                    busy = false
                    passwordSheet = false
                    pendingTarget = null
                }
            },
        )
    }

    if (showImportInfo) {
        AlertDialog(
            onDismissRequest = { showImportInfo = false },
            title = { Text("导入尚未开放") },
            text = { Text("为了避免未经预览就合并或替换当前数据，Portable 导入、冲突预览和数据库替换会在后续 PR09-14A/14B 通过隔离校验后开放。已有备份文件不会被删除。") },
            confirmButton = { TextButton(onClick = { showImportInfo = false }) { Text("知道了") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupPasswordSheet(
    operation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(BackupTestTags.PASSWORD_SHEET),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("为备份设置密码", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("备份密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("密码经 Unicode NFC 规范化后派生 Argon2id 密钥；不要使用 API Key 作为备份密码。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, enabled = !operation, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = { onConfirm(password) },
                    enabled = password.isNotEmpty() && !operation,
                    modifier = Modifier.weight(1f),
                ) { Text(if (operation) "处理中…" else "加密并保存") }
            }
        }
    }
}

private fun formatBackupError(error: Throwable): String {
    val code = (error as? BackupException)?.code ?: return "操作失败，当前数据未被删除。"
    return when (code) {
        BackupErrorCode.ACTIVE_WORK_IN_PROGRESS -> "当前有运行中的对话、待处理消息或音频任务，请完成后重试。"
        BackupErrorCode.PURGE_IN_PROGRESS -> "有议题正在彻底清除，请完成后重试。"
        BackupErrorCode.OPERATION_ALREADY_RUNNING -> "已有备份或快照操作正在进行。"
        BackupErrorCode.SNAPSHOT_KEY_UNAVAILABLE -> "设备快照密钥不可用，未创建替代密钥。"
        BackupErrorCode.OPERATION_CANCELED -> "操作已取消，未发布有效备份。"
        else -> "操作失败（${code.storageValue}），当前数据未被删除。"
    }
}
