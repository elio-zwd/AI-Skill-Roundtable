package com.elio.jianyu.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.elio.jianyu.JianyuAppRuntimeProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException

data class PortableBackupResult(
    val bytesWritten: Long,
    val destination: String,
)

/** Public export facade. Import remains intentionally unavailable until PR09-14A/14B. */
class PortableBackupService(
    private val context: Context,
    private val gate: BackupOperationGate = BackupOperationGate.forContext(context),
) {
    suspend fun createToFile(password: String, destination: File): PortableBackupResult = gate.withWriteLock {
        try {
            JianyuAppRuntimeProvider.withRuntime(context.applicationContext) { runtime ->
                val input = RepositoryBackupMapper.collect(runtime)
                BackupEnvelopeWriter.writeVerifiedFile(password, input, destination)
                PortableBackupResult(destination.length(), destination.absolutePath)
            }
        } catch (error: CancellationException) {
            throw BackupException(BackupErrorCode.OPERATION_CANCELED, error)
        }
    }

    /**
     * CreateDocument 已经创建了一个空目标文档。正式写入前先把它重命名为同 provider
     * 的临时名，写入后重新打开并完整验证，最后再 rename 成用户选择的名称。
     * provider 不支持 document rename/delete/reopen 时失败关闭，不直接写最终 .jybak。
     */
    suspend fun createToUri(password: String, destination: Uri): PortableBackupResult = gate.withWriteLock {
        // CreateDocument 返回的空文档在最终 rename 成功前都视为临时对象。
        var workingUri: Uri? = destination
        try {
            val resolver = context.contentResolver
            if (!DocumentsContract.isDocumentUri(context, destination)) {
                throw BackupException(BackupErrorCode.PROVIDER_CAPABILITY_MISSING)
            }
            val originalName = queryDisplayName(destination)
                ?: throw BackupException(BackupErrorCode.PROVIDER_CAPABILITY_MISSING)
            val temporaryName = "$originalName.partial-${UUID.randomUUID()}"
            workingUri = DocumentsContract.renameDocument(resolver, destination, temporaryName)
                ?: throw BackupException(BackupErrorCode.PROVIDER_CAPABILITY_MISSING)
            val temporaryUri = requireNotNull(workingUri)

            val bytes = JianyuAppRuntimeProvider.withRuntime(context.applicationContext) { runtime ->
                BackupEnvelopeWriter.createPortable(password, RepositoryBackupMapper.collect(runtime))
            }
            resolver.openFileDescriptor(temporaryUri, "w")?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { output ->
                    output.write(bytes)
                    output.flush()
                    output.fd.sync()
                }
            } ?: throw BackupException(BackupErrorCode.PROVIDER_CAPABILITY_MISSING)

            val persisted = resolver.openInputStream(temporaryUri)?.use { it.readBytes() }
                ?: throw BackupException(BackupErrorCode.PROVIDER_CAPABILITY_MISSING)
            if (!persisted.contentEquals(bytes)) {
                throw BackupException(BackupErrorCode.VERIFICATION_FAILED)
            }
            val plaintext = BackupCrypto.decryptPortable(password, persisted)
            BackupRecordStream.verify(plaintext, BackupProtocol.portableFormatId)

            val published = DocumentsContract.renameDocument(resolver, temporaryUri, originalName)
                ?: throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED)
            workingUri = null
            PortableBackupResult(bytes.size.toLong(), published.toString())
        } catch (error: CancellationException) {
            cleanupTemporaryDocument(workingUri, error)
            throw BackupException(BackupErrorCode.OPERATION_CANCELED, error)
        } catch (error: BackupException) {
            cleanupTemporaryDocument(workingUri, error)
            throw error
        } catch (error: Throwable) {
            cleanupTemporaryDocument(workingUri, error)
            throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED, error)
        }
    }

    private fun queryDisplayName(uri: Uri): String? =
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null
            else cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                ?.takeIf(String::isNotBlank)
        }

    private fun cleanupTemporaryDocument(uri: Uri?, cause: Throwable) {
        if (uri == null) return
        val cleaned = runCatching {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        }.getOrDefault(false)
        if (!cleaned) {
            throw BackupException(BackupErrorCode.TEMPORARY_CLEANUP_FAILED, cause)
        }
    }
}
