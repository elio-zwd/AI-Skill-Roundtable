package com.elio.jianyu.audio.assets

import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** 受控音频文件操作的稳定错误码。 */
enum class AudioFileStoreErrorCode {
    PATH_REJECTED,
    EMPTY_AUDIO,
    INVALID_AUDIO_FORMAT,
    INSUFFICIENT_STORAGE,
    TEMPORARY_FILE_MISSING,
    FINAL_FILE_EXISTS,
    ATOMIC_MOVE_UNAVAILABLE,
    ATOMIC_MOVE_FAILED,
    FILE_IO,
}

sealed interface AudioStoragePreflight {
    val usableBytes: Long
    val requiredBytes: Long

    data class Sufficient(
        override val usableBytes: Long,
        override val requiredBytes: Long,
    ) : AudioStoragePreflight

    data class Insufficient(
        override val usableBytes: Long,
        override val requiredBytes: Long,
    ) : AudioStoragePreflight
}

/** 同一受控目录内的临时文件与最终文件。 */
class PendingAudioTarget internal constructor(
    val temporaryRelativePath: String,
    val finalRelativePath: String,
    val format: AudioTargetFormat,
    internal val temporaryFile: File,
    internal val finalFile: File,
)

/**
 * 写入完成时执行 flush 与 FileDescriptor.sync，确保原子提交前数据已经交给文件系统。
 */
class AudioPendingWriter internal constructor(
    private val stream: FileOutputStream,
) : Closeable {
    fun write(bytes: ByteArray) {
        stream.write(bytes)
    }

    fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) {
        stream.write(bytes, offset, length)
    }

    override fun close() {
        var failure: Throwable? = null
        try {
            stream.flush()
            stream.fd.sync()
        } catch (error: Throwable) {
            failure = error
        }
        try {
            stream.close()
        } catch (error: Throwable) {
            if (failure == null) {
                failure = error
            } else {
                failure.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }
}

sealed interface AudioFileValidation {
    data class Valid(val sizeBytes: Long) : AudioFileValidation

    data class Invalid(
        val errorCode: AudioFileStoreErrorCode,
    ) : AudioFileValidation
}

data class AudioCommittedFile(
    val relativePath: String,
    val mimeType: String,
    val format: AudioTargetFormat,
    val sizeBytes: Long,
)

sealed interface AudioFileCommitResult {
    data class Success(val file: AudioCommittedFile) : AudioFileCommitResult

    data class Failure(
        val errorCode: AudioFileStoreErrorCode,
    ) : AudioFileCommitResult
}

sealed interface AudioFileResolution {
    data class Available(
        val relativePath: String,
        val file: File,
    ) : AudioFileResolution

    data class Missing(
        val relativePath: String,
    ) : AudioFileResolution

    data class Rejected(
        val errorCode: AudioFileStoreErrorCode,
    ) : AudioFileResolution
}

data class AudioOrphanFile(
    val relativePath: String,
    val sizeBytes: Long,
    val lastModifiedAt: Long,
    val reasonCode: String,
)

data class AudioOrphanReport(
    val files: List<AudioOrphanFile>,
)

fun interface AudioAtomicMover {
    @Throws(IOException::class)
    fun move(
        source: File,
        target: File,
    )
}

/**
 * App 私有目录中的音频文件存储边界。
 *
 * 该组件只接受内部资产 ID 和数据库相对路径，不接受来源正文、标题或外部绝对路径。
 */
class AudioFileStore(
    private val rootDirectory: File,
    private val usableSpaceProvider: () -> Long = { rootDirectory.usableSpace },
    private val atomicMover: AudioAtomicMover = AudioAtomicMover { source, target ->
        Files.move(
            source.toPath(),
            target.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
        )
    },
) {
    fun preflight(
        estimatedOutputBytes: Long,
        minimumReservationBytes: Long,
        safetyMarginBytes: Long,
    ): AudioStoragePreflight {
        require(estimatedOutputBytes >= 0L) { "预计输出大小不能为负数" }
        require(minimumReservationBytes >= 0L) { "最小预留空间不能为负数" }
        require(safetyMarginBytes >= 0L) { "安全余量不能为负数" }

        val baseRequirement = maxOf(estimatedOutputBytes, minimumReservationBytes)
        val requiredBytes = try {
            Math.addExact(baseRequirement, safetyMarginBytes)
        } catch (_: ArithmeticException) {
            Long.MAX_VALUE
        }
        val usableBytes = usableSpaceProvider().coerceAtLeast(0L)
        return if (usableBytes >= requiredBytes) {
            AudioStoragePreflight.Sufficient(
                usableBytes = usableBytes,
                requiredBytes = requiredBytes,
            )
        } else {
            AudioStoragePreflight.Insufficient(
                usableBytes = usableBytes,
                requiredBytes = requiredBytes,
            )
        }
    }

    fun createPendingTarget(
        audioAssetId: String,
        format: AudioTargetFormat,
    ): PendingAudioTarget {
        require(audioAssetId.isNotBlank()) { "音频资产 ID 不能为空" }
        ensureRootDirectory()

        val opaqueName = sha256(audioAssetId)
        val finalRelativePath = "$opaqueName.${format.extension}"
        val temporaryRelativePath = "$finalRelativePath.part"
        val finalFile = controlledFile(finalRelativePath)
            ?: throw IllegalArgumentException("最终音频路径不在受控目录内")
        val temporaryFile = controlledFile(temporaryRelativePath)
            ?: throw IllegalArgumentException("临时音频路径不在受控目录内")

        return PendingAudioTarget(
            temporaryRelativePath = temporaryRelativePath,
            finalRelativePath = finalRelativePath,
            format = format,
            temporaryFile = temporaryFile,
            finalFile = finalFile,
        )
    }

    fun openPendingWriter(target: PendingAudioTarget): AudioPendingWriter {
        ensureRootDirectory()
        requireTargetIsControlled(target)
        require(!target.finalFile.exists()) { "正式音频文件已存在" }
        return AudioPendingWriter(FileOutputStream(target.temporaryFile, false))
    }

    fun validatePending(target: PendingAudioTarget): AudioFileValidation {
        if (!isControlled(target.temporaryFile) || !isControlled(target.finalFile)) {
            return AudioFileValidation.Invalid(AudioFileStoreErrorCode.PATH_REJECTED)
        }
        if (!target.temporaryFile.isFile) {
            return AudioFileValidation.Invalid(AudioFileStoreErrorCode.TEMPORARY_FILE_MISSING)
        }
        val sizeBytes = target.temporaryFile.length()
        if (sizeBytes == 0L) {
            return AudioFileValidation.Invalid(AudioFileStoreErrorCode.EMPTY_AUDIO)
        }

        val validFormat = runCatching {
            when (target.format) {
                AudioTargetFormat.WAV -> validateWav(target.temporaryFile)
                AudioTargetFormat.AAC_ADTS -> validateAdtsAac(target.temporaryFile)
            }
        }.getOrDefault(false)

        return if (validFormat) {
            AudioFileValidation.Valid(sizeBytes)
        } else {
            AudioFileValidation.Invalid(AudioFileStoreErrorCode.INVALID_AUDIO_FORMAT)
        }
    }

    fun commit(target: PendingAudioTarget): AudioFileCommitResult {
        val validation = validatePending(target)
        if (validation is AudioFileValidation.Invalid) {
            return AudioFileCommitResult.Failure(validation.errorCode)
        }
        if (target.finalFile.exists()) {
            return AudioFileCommitResult.Failure(AudioFileStoreErrorCode.FINAL_FILE_EXISTS)
        }

        try {
            atomicMover.move(target.temporaryFile, target.finalFile)
        } catch (_: AtomicMoveNotSupportedException) {
            return AudioFileCommitResult.Failure(AudioFileStoreErrorCode.ATOMIC_MOVE_UNAVAILABLE)
        } catch (_: IOException) {
            return AudioFileCommitResult.Failure(AudioFileStoreErrorCode.ATOMIC_MOVE_FAILED)
        } catch (_: SecurityException) {
            return AudioFileCommitResult.Failure(AudioFileStoreErrorCode.FILE_IO)
        }

        if (!target.finalFile.isFile) {
            return AudioFileCommitResult.Failure(AudioFileStoreErrorCode.FILE_IO)
        }
        val committedSize = target.finalFile.length()
        if (committedSize <= 0L) {
            return AudioFileCommitResult.Failure(AudioFileStoreErrorCode.EMPTY_AUDIO)
        }
        return AudioFileCommitResult.Success(
            AudioCommittedFile(
                relativePath = target.finalRelativePath,
                mimeType = target.format.mimeType,
                format = target.format,
                sizeBytes = committedSize,
            ),
        )
    }

    fun resolve(relativePath: String): AudioFileResolution {
        val file = controlledFile(relativePath)
            ?: return AudioFileResolution.Rejected(AudioFileStoreErrorCode.PATH_REJECTED)
        return if (file.isFile) {
            AudioFileResolution.Available(
                relativePath = relativePath,
                file = file,
            )
        } else {
            AudioFileResolution.Missing(relativePath)
        }
    }

    fun removeTemporary(target: PendingAudioTarget): Boolean {
        if (!isControlled(target.temporaryFile)) return false
        return !target.temporaryFile.exists() || target.temporaryFile.delete()
    }

    fun removeCommitted(relativePath: String): Boolean {
        return when (val resolution = resolve(relativePath)) {
            is AudioFileResolution.Available -> resolution.file.delete()
            is AudioFileResolution.Missing -> true
            is AudioFileResolution.Rejected -> false
        }
    }

    /** 扫描但不删除数据库未引用的文件。 */
    fun scanOrphans(referencedRelativePaths: Set<String>): AudioOrphanReport {
        if (!rootDirectory.isDirectory) return AudioOrphanReport(emptyList())

        val referencedCanonicalPaths = referencedRelativePaths.mapNotNull { path ->
            controlledFile(path)?.canonicalPath
        }.toSet()
        val rootCanonical = rootDirectory.canonicalFile
        val files = rootDirectory.listFiles().orEmpty()
            .asSequence()
            .filter { it.isFile }
            .filter { isControlled(it) }
            .filterNot { it.canonicalPath in referencedCanonicalPaths }
            .map { file ->
                val relativePath = rootCanonical.toPath()
                    .relativize(file.canonicalFile.toPath())
                    .toString()
                    .replace(File.separatorChar, '/')
                AudioOrphanFile(
                    relativePath = relativePath,
                    sizeBytes = file.length(),
                    lastModifiedAt = file.lastModified(),
                    reasonCode = if (relativePath.endsWith(".part")) {
                        "unreferenced_temporary_file"
                    } else {
                        "unreferenced_audio_file"
                    },
                )
            }
            .sortedBy { it.relativePath }
            .toList()
        return AudioOrphanReport(files)
    }

    private fun ensureRootDirectory() {
        if (!rootDirectory.exists() && !rootDirectory.mkdirs()) {
            throw IOException("无法创建受控音频目录")
        }
        require(rootDirectory.isDirectory) { "受控音频根路径不是目录" }
    }

    private fun requireTargetIsControlled(target: PendingAudioTarget) {
        require(isControlled(target.temporaryFile)) { "临时音频路径不在受控目录内" }
        require(isControlled(target.finalFile)) { "正式音频路径不在受控目录内" }
        require(target.temporaryFile.parentFile?.canonicalFile == rootDirectory.canonicalFile) {
            "临时音频必须与正式音频位于同一受控目录"
        }
        require(target.finalFile.parentFile?.canonicalFile == rootDirectory.canonicalFile) {
            "正式音频必须位于受控根目录"
        }
    }

    private fun controlledFile(relativePath: String): File? {
        if (relativePath.isBlank() || relativePath.indexOf('\u0000') >= 0) return null
        val candidatePath = runCatching { File(relativePath) }.getOrNull() ?: return null
        if (candidatePath.isAbsolute) return null
        val normalizedSegments = relativePath.replace('\\', '/').split('/')
        if (normalizedSegments.any { it.isBlank() || it == "." || it == ".." }) return null

        return runCatching {
            val rootCanonical = rootDirectory.canonicalFile
            val candidate = File(rootCanonical, relativePath).canonicalFile
            if (candidate.parentFile?.canonicalFile != rootCanonical) null else candidate
        }.getOrNull()
    }

    private fun isControlled(file: File): Boolean {
        return runCatching {
            val rootCanonical = rootDirectory.canonicalFile
            file.canonicalFile.parentFile?.canonicalFile == rootCanonical
        }.getOrDefault(false)
    }

    private fun validateWav(file: File): Boolean {
        if (file.length() <= WAV_HEADER_BYTES) return false
        val header = ByteArray(WAV_HEADER_BYTES)
        val bytesRead = file.inputStream().use { it.read(header) }
        if (bytesRead < WAV_HEADER_BYTES) return false
        return header.sliceArray(0 until 4).toString(StandardCharsets.US_ASCII) == "RIFF" &&
            header.sliceArray(8 until 12).toString(StandardCharsets.US_ASCII) == "WAVE" &&
            header.sliceArray(12 until 16).toString(StandardCharsets.US_ASCII) == "fmt " &&
            header.sliceArray(36 until 40).toString(StandardCharsets.US_ASCII) == "data"
    }

    private fun validateAdtsAac(file: File): Boolean {
        if (file.length() < ADTS_MINIMUM_HEADER_BYTES) return false
        val header = ByteArray(2)
        val bytesRead = file.inputStream().use { it.read(header) }
        if (bytesRead < header.size) return false
        val first = header[0].toInt() and 0xff
        val second = header[1].toInt() and 0xff
        return first == 0xff && second and 0xf0 == 0xf0
    }

    private fun sha256(value: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private companion object {
        const val WAV_HEADER_BYTES = 44
        const val ADTS_MINIMUM_HEADER_BYTES = 7
    }
}
