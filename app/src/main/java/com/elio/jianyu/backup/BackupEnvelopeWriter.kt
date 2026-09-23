package com.elio.jianyu.backup

import java.io.File
import java.io.OutputStream
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.SecretKey

data class PortableBackupInput(
    val manifest: BackupManifest,
    val entities: List<BackupEntityRecord>,
    val blobs: List<BackupBlobRecord> = emptyList(),
)

data class SnapshotBackupInput(
    val manifest: BackupManifest,
    val entities: List<BackupEntityRecord>,
    val blobs: List<BackupBlobRecord> = emptyList(),
)

/** V1 envelope writer. Random inputs are generated internally and are not injectable in production. */
object BackupEnvelopeWriter {
    fun createPortable(password: String, input: PortableBackupInput): ByteArray {
        val stream = BackupRecordStream.build(input.manifest.copy(formatId = BackupProtocol.portableFormatId), input.entities, input.blobs)
        val random = SecureRandom()
        val salt = ByteArray(BackupProtocol.kdfSaltBytes).also(random::nextBytes)
        val envelopeId = ByteArray(BackupProtocol.envelopeIdBytes).also(random::nextBytes)
        val rootKey = ByteArray(BackupProtocol.rootKeyBytes).also(random::nextBytes)
        val wrapNonce = ByteArray(BackupProtocol.wrapNonceBytes).also(random::nextBytes)
        val header = BackupCrypto.encodeHeader(false, salt, envelopeId)
        val wrapAad = BackupCrypto.buildWrapAad(BackupProtocol.portableMagic, header)
        val kek = BackupCrypto.derivePortableKek(password, salt)
        val wrapped = try {
            BackupCrypto.wrapRootKey(kek, wrapNonce, rootKey, wrapAad)
        } finally { kek.fill(0) }
        val ikm = BackupCrypto.deriveStreamingIkm(rootKey, envelopeId, snapshot = false)
        rootKey.fill(0)
        val encrypted = BackupCrypto.encryptStreaming(ikm, BackupCrypto.streamAssociatedData(wrapAad, wrapNonce, wrapped), stream)
        return BackupCrypto.buildEnvelope(BackupProtocol.portableMagic, header, wrapNonce, wrapped, encrypted)
    }

    fun writePortable(password: String, input: PortableBackupInput, output: OutputStream) {
        output.write(createPortable(password, input))
        output.flush()
    }

    fun createSnapshot(wrappingKey: ByteArray, input: SnapshotBackupInput): ByteArray {
        if (wrappingKey.size != BackupProtocol.rootKeyBytes) throw BackupException(BackupErrorCode.SNAPSHOT_KEY_UNAVAILABLE)
        val stream = BackupRecordStream.build(input.manifest.copy(formatId = BackupProtocol.snapshotFormatId), input.entities, input.blobs)
        val random = SecureRandom()
        val envelopeId = ByteArray(BackupProtocol.envelopeIdBytes).also(random::nextBytes)
        val rootKey = ByteArray(BackupProtocol.rootKeyBytes).also(random::nextBytes)
        val wrapNonce = ByteArray(BackupProtocol.wrapNonceBytes).also(random::nextBytes)
        val header = BackupCrypto.encodeHeader(true, null, envelopeId)
        val wrapAad = BackupCrypto.buildWrapAad(BackupProtocol.snapshotMagic, header)
        val wrapped = BackupCrypto.wrapRootKey(wrappingKey, wrapNonce, rootKey, wrapAad)
        val ikm = BackupCrypto.deriveStreamingIkm(rootKey, envelopeId, snapshot = true)
        rootKey.fill(0)
        val encrypted = BackupCrypto.encryptStreaming(ikm, BackupCrypto.streamAssociatedData(wrapAad, wrapNonce, wrapped), stream)
        return BackupCrypto.buildEnvelope(BackupProtocol.snapshotMagic, header, wrapNonce, wrapped, encrypted)
    }

    fun createSnapshot(wrappingKey: SecretKey, input: SnapshotBackupInput): ByteArray {
        val stream = BackupRecordStream.build(input.manifest.copy(formatId = BackupProtocol.snapshotFormatId), input.entities, input.blobs)
        val random = SecureRandom()
        val envelopeId = ByteArray(BackupProtocol.envelopeIdBytes).also(random::nextBytes)
        val rootKey = ByteArray(BackupProtocol.rootKeyBytes).also(random::nextBytes)
        val header = BackupCrypto.encodeHeader(true, null, envelopeId)
        val wrapAad = BackupCrypto.buildWrapAad(BackupProtocol.snapshotMagic, header)
        val generatedWrap = BackupCrypto.wrapRootKeyWithGeneratedNonce(wrappingKey, rootKey, wrapAad)
        val ikm = try {
            BackupCrypto.deriveStreamingIkm(rootKey, envelopeId, snapshot = true)
        } finally {
            rootKey.fill(0)
        }
        val encrypted = BackupCrypto.encryptStreaming(
            ikm,
            BackupCrypto.streamAssociatedData(wrapAad, generatedWrap.nonce, generatedWrap.ciphertextAndTag),
            stream,
        )
        return BackupCrypto.buildEnvelope(
            BackupProtocol.snapshotMagic,
            header,
            generatedWrap.nonce,
            generatedWrap.ciphertextAndTag,
            encrypted,
        )
    }

    fun writeVerifiedFile(
        password: String,
        input: PortableBackupInput,
        destination: File,
    ) {
        if (destination.exists()) throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED)
        val directory = destination.parentFile ?: throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED)
        if (!directory.isDirectory && !directory.mkdirs()) throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED)
        val temporary = File(directory, "${destination.name}.partial-${UUID.randomUUID()}")
        try {
            temporary.outputStream().use { writePortable(password, input, it) }
            val bytes = temporary.readBytes()
            val plaintext = BackupCrypto.decryptPortable(password, bytes)
            BackupRecordStream.verify(plaintext, BackupProtocol.portableFormatId)
            if (!temporary.renameTo(destination)) throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED)
        } catch (error: BackupException) {
            if (!temporary.delete() && temporary.exists()) throw BackupException(BackupErrorCode.TEMPORARY_CLEANUP_FAILED, error)
            throw error
        } catch (error: Throwable) {
            if (!temporary.delete() && temporary.exists()) throw BackupException(BackupErrorCode.TEMPORARY_CLEANUP_FAILED, error)
            throw BackupException(BackupErrorCode.TARGET_WRITE_FAILED, error)
        }
    }
}
