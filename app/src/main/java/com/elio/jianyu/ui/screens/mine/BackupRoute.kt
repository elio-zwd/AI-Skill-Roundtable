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
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import com.elio.jianyu.data.BackupImportArtifact
import com.elio.jianyu.data.BackupImportIssue
import com.elio.jianyu.data.BackupImportMaterial
import com.elio.jianyu.data.BackupImportMessage
import com.elio.jianyu.data.BackupImportPayload
import com.elio.jianyu.data.BackupImportPersonalContext
import com.elio.jianyu.data.importBackup
import kotlinx.serialization.json.Json

object BackupTestTags {
    const val SCREEN = "backup_screen"
    const val CREATE = "backup_create"
    const val RESTORE = "backup_restore"
    const val PASSWORD_SHEET = "backup_password_sheet"
}

private const val BACKUP_MAGIC = "JIANBAK1"
private const val BACKUP_VERSION: Short = 1
private const val PBKDF2_ITERATIONS = 120_000
private const val KEY_BITS = 256

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRoute(
    repository: JianyuRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var passwordSheet by remember { mutableStateOf(false) }
    var restorePassword by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var showRestorePassword by remember { mutableStateOf(false) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri != null) {
            passwordSheet = true
            pendingRestoreUri = uri
        }
    }
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            pendingRestoreUri = uri
            restorePassword = ""
            showRestorePassword = true
        }
    }

    JianyuPageShell(
        title = "备份与恢复",
        subtitle = "本地加密文件，不自动同步",
        onBack = onBack,
        contentScrollable = true,
        modifier = Modifier.testTag(BackupTestTags.SCREEN),
    ) {
        JianyuStateCard(
            title = "创建本地备份",
            message = "备份会包含会话、资料与成果、个人背景和非敏感应用偏好；完整 API Key 不会写入备份文件。",
            actionLabel = "创建备份",
            actionTestTag = BackupTestTags.CREATE,
            onAction = { createLauncher.launch("jianyu-backup-${System.currentTimeMillis()}.jybak") },
        )
        Text("备份文件使用你输入的独立密码加密。密码不会保存到 App，也无法从备份文件中恢复。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("从备份恢复", style = MaterialTheme.typography.titleMedium)
        Button(
            onClick = { openLauncher.launch(arrayOf("application/octet-stream", "application/json")) },
            enabled = !busy,
            modifier = Modifier.testTag(BackupTestTags.RESTORE),
        ) { Text("选择备份文件") }
        JianyuStateCard(
            title = "恢复边界",
            message = "选择文件后会先验证密码和格式，再将资料、个人背景、议题摘要与成果合并到当前 App；不会删除或覆盖当前数据，也不会访问旧包。",
        )
        message?.let { JianyuStateCard("操作结果", it) }
    }

    if (passwordSheet && pendingRestoreUri != null) {
        BackupPasswordSheet(
            title = "为备份设置密码",
            confirmLabel = "加密并保存",
            operation = busy,
            onDismiss = { if (!busy) passwordSheet = false },
            onConfirm = { password ->
                scope.launch {
                    busy = true
                    val result = withContext(Dispatchers.IO) {
                        createEncryptedBackup(context, repository, pendingRestoreUri!!, password)
                    }
                    busy = false
                    passwordSheet = false
                    message = result
                }
            },
        )
    }

    if (showRestorePassword && pendingRestoreUri != null) {
        BackupPasswordSheet(
            title = "验证备份密码",
            confirmLabel = "验证并导入",
            operation = busy,
            onDismiss = { if (!busy) showRestorePassword = false },
            onConfirm = { password ->
                scope.launch {
                    busy = true
                    val result = withContext(Dispatchers.IO) {
                        restoreEncryptedBackup(context, repository, pendingRestoreUri!!, password)
                    }
                    busy = false
                    showRestorePassword = false
                    message = result
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupPasswordSheet(
    title: String,
    confirmLabel: String,
    operation: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(BackupTestTags.PASSWORD_SHEET),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("备份密码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("密码至少 8 个字符；不要使用 API Key 作为备份密码。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onDismiss, enabled = !operation, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = { onConfirm(password) },
                    enabled = password.length >= 8 && !operation,
                    modifier = Modifier.weight(1f),
                ) { Text(if (operation) "处理中…" else confirmLabel) }
            }
        }
    }
}

private suspend fun createEncryptedBackup(
    context: Context,
    repository: JianyuRepository,
    uri: Uri,
    password: String,
): String {
    return try {
        val plaintext = buildExportJson(repository).toByteArray(Charsets.UTF_8)
        val salt = ByteArray(16).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val key = deriveKey(password, salt)
        val ciphertext = aesGcm(Cipher.ENCRYPT_MODE, key, nonce, plaintext)
        val envelope = ByteArrayOutputStream().apply {
            write(BACKUP_MAGIC.toByteArray(Charsets.US_ASCII))
            write(ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN).putShort(BACKUP_VERSION).array())
            write(salt)
            write(nonce)
            write(ciphertext)
        }.toByteArray()
        context.contentResolver.openOutputStream(uri)?.use { it.write(envelope) }
            ?: return "无法打开备份位置，请重新选择文件。"
        "备份已加密保存；完整 API Key 未包含在文件中。"
    } catch (_: Throwable) {
        "备份失败，当前数据未被删除。"
    }
}

private suspend fun restoreEncryptedBackup(
    context: Context,
    repository: JianyuRepository,
    uri: Uri,
    password: String,
): String {
    return try {
        val payload = Json { ignoreUnknownKeys = true }
            .decodeFromString<ExportPayload>(decryptEncryptedBackup(context, uri, password).toString(Charsets.UTF_8))
        val stats = repository.importBackup(
            BackupImportPayload(
                issues = payload.issues.map { issue ->
                    BackupImportIssue(
                        id = issue.id,
                        title = issue.title,
                        messages = issue.messages.map { BackupImportMessage(it.sender, it.text) },
                        artifacts = issue.artifacts.map { BackupImportArtifact(it.id, it.title, it.type, it.content) },
                    )
                },
                materials = payload.materials.map {
                    BackupImportMaterial(it.id, it.title, it.sourceType, it.content)
                },
                personalContexts = payload.personalContexts.map {
                    BackupImportPersonalContext(it.id, it.title, it.sensitive, it.content)
                },
            )
        )
        "备份已验证并合并：议题 ${stats.issues} 个、成果 ${stats.artifacts} 个、资料 ${stats.materials} 项、个人背景 ${stats.personalContexts} 项。当前数据未被删除。"
    } catch (_: Throwable) {
        "密码错误、备份格式不支持或导入失败；当前数据未被删除。"
    }
}

private fun decryptEncryptedBackup(context: Context, uri: Uri, password: String): ByteArray {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalStateException("backup_read_failed")
    require(bytes.size >= 8 + 2 + 16 + 12 + 16) { "backup_incomplete" }
    require(bytes.copyOfRange(0, 8).contentEquals(BACKUP_MAGIC.toByteArray(Charsets.US_ASCII))) {
        "backup_magic_invalid"
    }
    val version = ByteBuffer.wrap(bytes, 8, 2).order(ByteOrder.BIG_ENDIAN).short
    require(version == BACKUP_VERSION) { "backup_version_unsupported" }
    val salt = bytes.copyOfRange(10, 26)
    val nonce = bytes.copyOfRange(26, 38)
    val ciphertext = bytes.copyOfRange(38, bytes.size)
    return aesGcm(Cipher.DECRYPT_MODE, deriveKey(password, salt), nonce, ciphertext)
}

private fun deriveKey(password: String, salt: ByteArray): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_BITS)
    return try {
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
    } finally {
        spec.clearPassword()
    }
}

private fun aesGcm(mode: Int, key: ByteArray, nonce: ByteArray, input: ByteArray): ByteArray =
    Cipher.getInstance("AES/GCM/NoPadding").run {
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        doFinal(input)
    }
