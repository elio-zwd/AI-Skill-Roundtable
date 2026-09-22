package com.elio.jianyu.backup

import android.content.Context
import android.net.Uri
import com.elio.jianyu.JianyuAppRuntimeProvider
import java.io.File
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
     * SAF providers do not expose a portable atomic rename contract through a bare Uri. We encrypt
     * and verify the complete envelope before opening the selected document, then fail closed on
     * provider write errors; no plaintext is ever sent to the provider.
     */
    suspend fun createToUri(password: String, destination: Uri): PortableBackupResult = gate.withWriteLock {
        try {
            val bytes = JianyuAppRuntimeProvider.withRuntime(context.applicationContext) { runtime ->
                BackupEnvelopeWriter.createPortable(password, RepositoryBackupMapper.collect(runtime))
            }
            val plaintext = com.elio.jianyu.backup.BackupCrypto.decryptPortable(password, bytes)
            BackupRecordStream.verify(plaintext, BackupProtocol.portableFormatId)
            context.contentResolver.openOutputStream(destination, "w")?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: throw BackupException(BackupErrorCode.PROVIDER_CAPABILITY_MISSING)
            PortableBackupResult(bytes.size.toLong(), destination.toString())
        } catch (error: CancellationException) {
            throw BackupException(BackupErrorCode.OPERATION_CANCELED, error)
        } catch (error: BackupException) {
            throw error
        } catch (error: Throwable) {
            throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED, error)
        }
    }
}
