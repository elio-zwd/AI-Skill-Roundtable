package com.elio.jianyu.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.elio.jianyu.audio.assets.AudioAssetCreateResult
import com.elio.jianyu.audio.assets.AudioAssetLifecycleRepositoryPort
import com.elio.jianyu.audio.assets.AudioAssetRecord
import com.elio.jianyu.audio.assets.AudioAssetRepositoryPort
import com.elio.jianyu.audio.assets.AudioAssetRetryResetResult
import com.elio.jianyu.audio.assets.AudioAssetSource
import com.elio.jianyu.audio.assets.AudioDeleteWriteResult
import com.elio.jianyu.audio.assets.AudioGenerationErrorCode
import com.elio.jianyu.audio.assets.AudioGenerationKeyFactory
import com.elio.jianyu.audio.assets.AudioSourceLoadResult
import com.elio.jianyu.audio.assets.AudioSourceReference
import com.elio.jianyu.audio.assets.CreatePendingAudioCommand
import com.elio.jianyu.audio.assets.MarkAudioAvailableCommand
import com.elio.jianyu.audio.assets.PersistAudioDeleteRequestCommand
import com.elio.jianyu.audio.assets.ResetAudioForRetryCommand
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Room v11 上的正式 AudioAsset Repository。
 *
 * 使用现有表与唯一索引，不建立竞争 Migration；所有终态写入均通过条件 UPDATE，
 * 使取消、删除请求和迟到成功回调遵循数据库事实而不是进程内时序。
 */
class RoomAudioAssetRepository(
    private val database: RoundtableDatabase,
    private val nowProvider: () -> Long = System::currentTimeMillis,
) : AudioAssetRepositoryPort, AudioAssetLifecycleRepositoryPort {
    private val transactions = JianyuRepositoryTransactions(database)

    override suspend fun loadSource(reference: AudioSourceReference): AudioSourceLoadResult {
        return runCatching { loadSourceFromDatabase(reference) }
            .getOrElse { AudioSourceLoadResult.Rejected(AudioGenerationErrorCode.UNKNOWN) }
    }

    override suspend fun findByGenerationKey(generationKey: String): AudioAssetRecord? {
        if (generationKey.isBlank()) return null
        return runCatching {
            val id = database.openHelper.readableDatabase.query(
                "SELECT id FROM audio_assets WHERE generationKey = ? LIMIT 1",
                arrayOf(generationKey),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            id?.let { loadRecord(it) }
        }.getOrNull()
    }

    override suspend fun createPending(command: CreatePendingAudioCommand): AudioAssetCreateResult {
        if (!FormalAudioV1Policy.supports(command.config) || command.generationKey.isBlank()) {
            return AudioAssetCreateResult.Conflict
        }
        findByGenerationKey(command.generationKey)?.let {
            return AudioAssetCreateResult.Existing(it)
        }

        val result = transactions.databaseTransaction("create_audio_asset") {
            val source = loadSourceFromDatabase(command.source.toReference())
            if (source !is AudioSourceLoadResult.Ready || source.snapshot.source != command.source) {
                return@databaseTransaction RepositoryResult.Failure(
                    RepositoryError.InvalidState("create_audio_asset", "source_changed"),
                )
            }
            val existingId = resourceLifecycleDao().getAudioAsset(command.audioAssetId)
            if (existingId != null) {
                return@databaseTransaction RepositoryResult.Failure(
                    RepositoryError.IdempotencyConflict("create_audio_asset", command.audioAssetId),
                )
            }
            val now = nextTimestamp(0L)
            val entity = AudioAssetEntity(
                id = command.audioAssetId,
                issueId = command.source.issueId,
                stageId = command.source.stageId,
                sourceMessageId = (command.source as? AudioAssetSource.CompletedMessage)?.messageId,
                sourceArtifactId = (command.source as? AudioAssetSource.ConfirmedArtifact)?.artifactId,
                storagePath = pendingStoragePath(command.audioAssetId, command.config.targetFormat.extension),
                mimeType = command.config.targetFormat.mimeType,
                format = command.config.targetFormat.storageValue,
                sizeBytes = 0L,
                fileState = AudioFileState.PENDING,
                generationKey = command.generationKey,
                createdAt = now,
                updatedAt = now,
            )
            resourceLifecycleDao().createAudioAsset(entity)
            val record = loadRecord(entity.id)
                ?: return@databaseTransaction RepositoryResult.Failure(
                    RepositoryError.StorageFailure("create_audio_asset", retryable = true),
                )
            RepositoryResult.Success(AudioAssetCreateResult.Created(record))
        }
        return when (result) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> {
                findByGenerationKey(command.generationKey)?.let(AudioAssetCreateResult::Existing)
                    ?: AudioAssetCreateResult.Conflict
            }
        }
    }

    override suspend fun loadAsset(audioAssetId: String): AudioAssetRecord? {
        if (audioAssetId.isBlank()) return null
        return runCatching { loadRecord(audioAssetId) }.getOrNull()
    }

    override suspend fun markAvailable(command: MarkAudioAvailableCommand): Boolean {
        if (command.relativePath.isBlank() || command.sizeBytes <= 0L) return false
        val current = loadAsset(command.audioAssetId) ?: return false
        if (current.config.targetFormat != command.format) return false
        return compareAndSet(
            sql = "UPDATE audio_assets SET storagePath = ?, mimeType = ?, sizeBytes = ?, " +
                "fileState = ?, updatedAt = ? WHERE id = ? AND fileState = ? " +
                "AND deletedAt IS NULL AND purgeRequestedAt IS NULL",
            args = listOf(
                command.relativePath,
                command.mimeType,
                command.sizeBytes,
                AudioFileState.AVAILABLE.storageValue,
                nextTimestamp(currentTimestamp(command.audioAssetId)),
                command.audioAssetId,
                command.expectedState.storageValue,
            ),
        )
    }

    override suspend fun markFailed(
        audioAssetId: String,
        expectedState: AudioFileState,
    ): Boolean = compareAndSetState(audioAssetId, expectedState, AudioFileState.FAILED)

    override suspend fun markCanceled(
        audioAssetId: String,
        expectedState: AudioFileState,
    ): Boolean = compareAndSetState(audioAssetId, expectedState, AudioFileState.CANCELED)

    override suspend fun resetForRetry(
        command: ResetAudioForRetryCommand,
    ): AudioAssetRetryResetResult {
        if (!FormalAudioV1Policy.supports(command.config)) return AudioAssetRetryResetResult.Conflict
        val current = loadAsset(command.audioAssetId) ?: return AudioAssetRetryResetResult.Rejected
        if (current.fileState != command.expectedState ||
            current.deletedAt != null || current.purgeRequestedAt != null ||
            current.source != command.source || current.config != command.config ||
            current.generationKey != command.generationKey
        ) {
            return AudioAssetRetryResetResult.Rejected
        }
        val changed = compareAndSet(
            sql = "UPDATE audio_assets SET storagePath = ?, mimeType = ?, format = ?, sizeBytes = 0, " +
                "fileState = ?, updatedAt = ? WHERE id = ? AND fileState = ? " +
                "AND deletedAt IS NULL AND purgeRequestedAt IS NULL AND generationKey = ?",
            args = listOf(
                pendingStoragePath(current.id, current.config.targetFormat.extension),
                current.config.targetFormat.mimeType,
                current.config.targetFormat.storageValue,
                AudioFileState.PENDING.storageValue,
                nextTimestamp(currentTimestamp(current.id)),
                current.id,
                command.expectedState.storageValue,
                command.generationKey,
            ),
        )
        if (!changed) return AudioAssetRetryResetResult.Rejected
        return loadAsset(current.id)?.let(AudioAssetRetryResetResult::Reset)
            ?: AudioAssetRetryResetResult.Rejected
    }

    override suspend fun getAudioAsset(audioAssetId: String): AudioAssetRecord? =
        loadAsset(audioAssetId)

    override suspend fun listAudioAssetsForIssue(issueId: String): List<AudioAssetRecord> {
        if (issueId.isBlank()) return emptyList()
        return listRecords(
            "SELECT id FROM audio_assets WHERE issueId = ? ORDER BY createdAt, id",
            arrayOf(issueId),
        )
    }

    override suspend fun listAudioAssetsForStage(
        issueId: String,
        stageId: String,
    ): List<AudioAssetRecord> {
        if (issueId.isBlank() || stageId.isBlank()) return emptyList()
        return listRecords(
            "SELECT id FROM audio_assets WHERE issueId = ? AND stageId = ? ORDER BY createdAt, id",
            arrayOf(issueId, stageId),
        )
    }

    override suspend fun markMissing(
        audioAssetId: String,
        expectedState: AudioFileState,
    ): Boolean = compareAndSetState(audioAssetId, expectedState, AudioFileState.MISSING)

    override suspend fun requestDelete(
        command: PersistAudioDeleteRequestCommand,
    ): AudioDeleteWriteResult {
        val current = loadAsset(command.audioAssetId) ?: return AudioDeleteWriteResult.Rejected
        if (current.purgeRequestedAt != null) return AudioDeleteWriteResult.AlreadyRequested(current)
        if (current.deletedAt != null || current.fileState != command.expectedState || command.requestedAt <= 0L) {
            return AudioDeleteWriteResult.Rejected
        }
        val changed = compareAndSet(
            sql = "UPDATE audio_assets SET purgeRequestedAt = ?, updatedAt = ? " +
                "WHERE id = ? AND fileState = ? AND deletedAt IS NULL AND purgeRequestedAt IS NULL",
            args = listOf(
                command.requestedAt,
                maxOf(command.requestedAt, nextTimestamp(currentTimestamp(current.id))),
                command.audioAssetId,
                command.expectedState.storageValue,
            ),
        )
        val latest = loadAsset(command.audioAssetId) ?: return AudioDeleteWriteResult.Rejected
        return when {
            changed -> AudioDeleteWriteResult.Requested(latest)
            latest.purgeRequestedAt != null -> AudioDeleteWriteResult.AlreadyRequested(latest)
            else -> AudioDeleteWriteResult.Rejected
        }
    }

    private suspend fun loadSourceFromDatabase(reference: AudioSourceReference): AudioSourceLoadResult {
        return when (reference) {
            is AudioSourceReference.Message -> {
                val message = database.jianyuRepositoryDao().getMessage(reference.messageId)
                    ?: return AudioSourceLoadResult.Rejected(AudioGenerationErrorCode.SOURCE_NOT_FOUND)
                when {
                    message.issueId != reference.issueId ->
                        AudioSourceLoadResult.Rejected(AudioGenerationErrorCode.CROSS_ISSUE)
                    message.stageId != reference.stageId ->
                        AudioSourceLoadResult.Rejected(AudioGenerationErrorCode.CROSS_STAGE)
                    message.isPending ->
                        AudioSourceLoadResult.Rejected(AudioGenerationErrorCode.PENDING_MESSAGE)
                    else -> AudioSourceLoadResult.Ready(
                        com.elio.jianyu.audio.assets.AudioSourceSnapshot(
                            source = AudioAssetSource.CompletedMessage(
                                issueId = reference.issueId,
                                stageId = reference.stageId,
                                contentHash = sha256(message.text),
                                messageId = message.id,
                            ),
                            content = message.text,
                        ),
                    )
                }
            }
            is AudioSourceReference.Artifact -> {
                val artifact = database.jianyuRepositoryDao().getArtifact(reference.artifactId)
                    ?: return AudioSourceLoadResult.Rejected(AudioGenerationErrorCode.SOURCE_NOT_FOUND)
                when {
                    artifact.issueId != reference.issueId ->
                        AudioSourceLoadResult.Rejected(AudioGenerationErrorCode.CROSS_ISSUE)
                    artifact.stageId != reference.stageId ->
                        AudioSourceLoadResult.Rejected(AudioGenerationErrorCode.CROSS_STAGE)
                    else -> AudioSourceLoadResult.Ready(
                        com.elio.jianyu.audio.assets.AudioSourceSnapshot(
                            source = AudioAssetSource.ConfirmedArtifact(
                                issueId = reference.issueId,
                                stageId = reference.stageId,
                                contentHash = sha256(artifact.content),
                                artifactId = artifact.id,
                            ),
                            content = artifact.content,
                        ),
                    )
                }
            }
        }
    }

    private suspend fun loadRecord(audioAssetId: String): AudioAssetRecord? {
        val entity = database.resourceLifecycleDao().getAudioAsset(audioAssetId) ?: return null
        val config = FormalAudioV1Policy.restore(entity.format) ?: return null
        val reference = when {
            entity.sourceMessageId != null -> AudioSourceReference.Message(
                entity.issueId,
                entity.stageId,
                entity.sourceMessageId,
            )
            entity.sourceArtifactId != null -> AudioSourceReference.Artifact(
                entity.issueId,
                entity.stageId,
                entity.sourceArtifactId,
            )
            else -> return null
        }
        val source = (loadSourceFromDatabase(reference) as? AudioSourceLoadResult.Ready)
            ?.snapshot?.source ?: return null
        val generationKey = entity.generationKey
            ?: AudioGenerationKeyFactory.create(source, config)
        return AudioAssetRecord(
            id = entity.id,
            source = source,
            config = config,
            generationKey = generationKey,
            fileState = entity.fileState,
            storagePath = entity.storagePath.takeUnless {
                entity.fileState == AudioFileState.PENDING || it.startsWith("pending-")
            },
            mimeType = entity.mimeType.takeIf { it.isNotBlank() },
            sizeBytes = entity.sizeBytes,
            deletedAt = entity.deletedAt,
            purgeRequestedAt = entity.purgeRequestedAt,
        )
    }

    private suspend fun listRecords(sql: String, args: Array<out Any?>): List<AudioAssetRecord> {
        return runCatching {
            val ids = database.openHelper.readableDatabase.query(sql, args).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            ids.mapNotNull { loadRecord(it) }
        }.getOrDefault(emptyList())
    }

    private suspend fun compareAndSetState(
        audioAssetId: String,
        expectedState: AudioFileState,
        newState: AudioFileState,
    ): Boolean {
        val current = loadAsset(audioAssetId) ?: return false
        return compareAndSet(
            sql = "UPDATE audio_assets SET fileState = ?, updatedAt = ? WHERE id = ? " +
                "AND fileState = ? AND deletedAt IS NULL AND purgeRequestedAt IS NULL",
            args = listOf(
                newState.storageValue,
                nextTimestamp(currentTimestamp(current.id)),
                current.id,
                expectedState.storageValue,
            ),
        )
    }

    private suspend fun compareAndSet(sql: String, args: List<Any?>): Boolean {
        val result = transactions.databaseTransaction("update_audio_asset") {
            val changed = openHelper.writableDatabase.executeUpdateDelete(sql, args)
            RepositoryResult.Success(changed == 1)
        }
        return (result as? RepositoryResult.Success)?.value == true
    }

    private fun currentTimestamp(audioAssetId: String): Long {
        return runCatching {
            database.openHelper.readableDatabase.query(
                "SELECT updatedAt FROM audio_assets WHERE id = ? LIMIT 1",
                arrayOf(audioAssetId),
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
        }.getOrDefault(0L)
    }

    private fun nextTimestamp(previous: Long): Long = maxOf(nowProvider(), previous + 1L)

    private fun pendingStoragePath(audioAssetId: String, extension: String): String =
        "pending-${sha256(audioAssetId)}.$extension"

    private fun AudioAssetSource.toReference(): AudioSourceReference = when (this) {
        is AudioAssetSource.CompletedMessage -> AudioSourceReference.Message(issueId, stageId, messageId)
        is AudioAssetSource.ConfirmedArtifact -> AudioSourceReference.Artifact(issueId, stageId, artifactId)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private fun SupportSQLiteDatabase.executeUpdateDelete(
    sql: String,
    args: List<Any?>,
): Int {
    val statement = compileStatement(sql)
    args.forEachIndexed { index, value ->
        val bindIndex = index + 1
        when (value) {
            null -> statement.bindNull(bindIndex)
            is String -> statement.bindString(bindIndex, value)
            is Long -> statement.bindLong(bindIndex, value)
            is Int -> statement.bindLong(bindIndex, value.toLong())
            is Boolean -> statement.bindLong(bindIndex, if (value) 1L else 0L)
            is Double -> statement.bindDouble(bindIndex, value)
            is Float -> statement.bindDouble(bindIndex, value.toDouble())
            else -> statement.bindString(bindIndex, value.toString())
        }
    }
    return statement.executeUpdateDelete()
}
