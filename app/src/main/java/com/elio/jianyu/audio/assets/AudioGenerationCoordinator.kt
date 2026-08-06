package com.elio.jianyu.audio.assets

import com.elio.jianyu.data.AudioFileState

enum class AudioGenerationErrorCode {
    SOURCE_NOT_FOUND,
    PENDING_MESSAGE,
    DRAFT_SOURCE,
    CROSS_ISSUE,
    CROSS_STAGE,
    UNCONFIRMED_ARTIFACT,
    SOURCE_CHANGED,
    UNSUPPORTED_CONFIG,
    INSUFFICIENT_STORAGE,
    PENDING_WITHOUT_ACTIVE_WORK,
    DUPLICATE_CONFLICT,
    SCHEDULE_FAILED,
    AUTH_UNAVAILABLE,
    RATE_LIMITED,
    OFFLINE,
    TIMEOUT,
    EMPTY_RESPONSE,
    INVALID_RESPONSE,
    INVALID_AUDIO_FILE,
    FILE_IO,
    REPOSITORY_REJECTED,
    ASSET_NOT_FOUND,
    INVALID_STATE,
    CANCELED,
    DELETED,
    PURGE_REQUESTED,
    UNKNOWN,
}

sealed interface AudioSourceReference {
    val issueId: String
    val stageId: String

    data class Message(
        override val issueId: String,
        override val stageId: String,
        val messageId: Long,
    ) : AudioSourceReference

    data class Artifact(
        override val issueId: String,
        override val stageId: String,
        val artifactId: String,
    ) : AudioSourceReference
}

data class AudioSourceSnapshot(
    val source: AudioAssetSource,
    val content: String,
)

sealed interface AudioSourceLoadResult {
    data class Ready(val snapshot: AudioSourceSnapshot) : AudioSourceLoadResult
    data class Rejected(val errorCode: AudioGenerationErrorCode) : AudioSourceLoadResult
}

data class AudioAssetRecord(
    val id: String,
    val source: AudioAssetSource,
    val config: AudioGenerationConfig,
    val generationKey: String,
    val fileState: AudioFileState,
    val storagePath: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val deletedAt: Long?,
    val purgeRequestedAt: Long?,
)

data class CreatePendingAudioCommand(
    val audioAssetId: String,
    val source: AudioAssetSource,
    val config: AudioGenerationConfig,
    val generationKey: String,
)

sealed interface AudioAssetCreateResult {
    data class Created(val asset: AudioAssetRecord) : AudioAssetCreateResult
    data class Existing(val asset: AudioAssetRecord) : AudioAssetCreateResult
    data object Conflict : AudioAssetCreateResult
}

data class ResetAudioForRetryCommand(
    val audioAssetId: String,
    val expectedState: AudioFileState,
    val source: AudioAssetSource,
    val config: AudioGenerationConfig,
    val generationKey: String,
)

sealed interface AudioAssetRetryResetResult {
    data class Reset(val asset: AudioAssetRecord) : AudioAssetRetryResetResult
    data object Rejected : AudioAssetRetryResetResult
    data object Conflict : AudioAssetRetryResetResult
}

data class MarkAudioAvailableCommand(
    val audioAssetId: String,
    val relativePath: String,
    val mimeType: String,
    val format: AudioTargetFormat,
    val sizeBytes: Long,
    val expectedState: AudioFileState = AudioFileState.PENDING,
)

interface AudioAssetRepositoryPort {
    suspend fun loadSource(reference: AudioSourceReference): AudioSourceLoadResult
    suspend fun findByGenerationKey(generationKey: String): AudioAssetRecord?
    suspend fun createPending(command: CreatePendingAudioCommand): AudioAssetCreateResult
    suspend fun loadAsset(audioAssetId: String): AudioAssetRecord?
    suspend fun markAvailable(command: MarkAudioAvailableCommand): Boolean
    suspend fun markFailed(audioAssetId: String, expectedState: AudioFileState): Boolean
    suspend fun markCanceled(audioAssetId: String, expectedState: AudioFileState): Boolean

    suspend fun resetForRetry(
        command: ResetAudioForRetryCommand,
    ): AudioAssetRetryResetResult = AudioAssetRetryResetResult.Rejected
}

interface AudioGenerationSchedulerPort {
    suspend fun isActive(uniqueWorkName: String): Boolean
    suspend fun schedule(plan: AudioGenerationWorkPlan): Boolean
    suspend fun cancel(uniqueWorkName: String): Boolean
}

data class CreateAudioGenerationCommand(
    val sourceReference: AudioSourceReference,
    val config: AudioGenerationConfig,
    val userConfirmed: Boolean,
    val estimatedOutputBytes: Long,
    val minimumReservationBytes: Long,
    val safetyMarginBytes: Long,
)

sealed interface AudioGenerationRequestResult {
    data object ConfirmationRequired : AudioGenerationRequestResult
    data class Queued(
        val asset: AudioAssetRecord,
        val workPlan: AudioGenerationWorkPlan,
    ) : AudioGenerationRequestResult
    data class ReusedAvailable(val asset: AudioAssetRecord) : AudioGenerationRequestResult
    data class ReusedPending(val asset: AudioAssetRecord) : AudioGenerationRequestResult
    data class ExplicitRetryRequired(val asset: AudioAssetRecord) : AudioGenerationRequestResult
    data class Failure(val errorCode: AudioGenerationErrorCode) : AudioGenerationRequestResult
}

data class RetryAudioGenerationCommand(
    val audioAssetId: String,
    val userConfirmed: Boolean,
    val estimatedOutputBytes: Long,
    val minimumReservationBytes: Long,
    val safetyMarginBytes: Long,
)

sealed interface AudioGenerationRetryResult {
    data object ConfirmationRequired : AudioGenerationRetryResult
    data class Queued(
        val asset: AudioAssetRecord,
        val workPlan: AudioGenerationWorkPlan,
    ) : AudioGenerationRetryResult
    data class ReusedAvailable(val asset: AudioAssetRecord) : AudioGenerationRetryResult
    data class ReusedPending(val asset: AudioAssetRecord) : AudioGenerationRetryResult
    data class Failure(val errorCode: AudioGenerationErrorCode) : AudioGenerationRetryResult
}

sealed interface AudioGenerationExecutionResult {
    data class Available(
        val audioAssetId: String,
        val relativePath: String,
    ) : AudioGenerationExecutionResult
    data class Suppressed(val errorCode: AudioGenerationErrorCode) : AudioGenerationExecutionResult
    data class Failure(val errorCode: AudioGenerationErrorCode) : AudioGenerationExecutionResult
}

sealed interface AudioGenerationCancelResult {
    data class Canceled(val audioAssetId: String) : AudioGenerationCancelResult
    data class Failure(val errorCode: AudioGenerationErrorCode) : AudioGenerationCancelResult
}

class AudioGenerationCoordinator(
    private val repository: AudioAssetRepositoryPort,
    private val scheduler: AudioGenerationSchedulerPort,
    private val gateway: AudioGenerationGateway,
    private val fileStore: AudioFileStore,
    private val audioAssetIdFactory: () -> String,
    private val executionEstimatedOutputBytes: Long = DEFAULT_ESTIMATED_OUTPUT_BYTES,
    private val executionMinimumReservationBytes: Long = DEFAULT_MINIMUM_RESERVATION_BYTES,
    private val executionSafetyMarginBytes: Long = DEFAULT_SAFETY_MARGIN_BYTES,
) {
    suspend fun createGenerationRequest(
        command: CreateAudioGenerationCommand,
    ): AudioGenerationRequestResult {
        if (!command.userConfirmed) return AudioGenerationRequestResult.ConfirmationRequired
        if (!isSupportedConfig(command.config)) {
            return AudioGenerationRequestResult.Failure(AudioGenerationErrorCode.UNSUPPORTED_CONFIG)
        }

        val snapshot = when (val loaded = repository.loadSource(command.sourceReference)) {
            is AudioSourceLoadResult.Ready -> loaded.snapshot
            is AudioSourceLoadResult.Rejected -> {
                return AudioGenerationRequestResult.Failure(loaded.errorCode)
            }
        }
        sourceRelationshipError(command.sourceReference, snapshot.source)?.let {
            return AudioGenerationRequestResult.Failure(it)
        }

        val key = AudioGenerationKeyFactory.create(snapshot.source, command.config)
        repository.findByGenerationKey(key)?.let { return classifyExisting(it) }
        if (!hasSpace(command.estimatedOutputBytes, command.minimumReservationBytes, command.safetyMarginBytes)) {
            return AudioGenerationRequestResult.Failure(AudioGenerationErrorCode.INSUFFICIENT_STORAGE)
        }

        val id = audioAssetIdFactory()
        if (id.isBlank()) {
            return AudioGenerationRequestResult.Failure(AudioGenerationErrorCode.REPOSITORY_REJECTED)
        }
        val asset = when (val created = repository.createPending(
            CreatePendingAudioCommand(id, snapshot.source, command.config, key),
        )) {
            is AudioAssetCreateResult.Created -> created.asset
            is AudioAssetCreateResult.Existing -> return classifyExistingAfterCreateRace(created.asset)
            AudioAssetCreateResult.Conflict -> {
                return AudioGenerationRequestResult.Failure(AudioGenerationErrorCode.DUPLICATE_CONFLICT)
            }
        }

        val plan = workPlan(asset, AudioWorkRequestKind.INITIAL)
        if (!scheduler.schedule(plan)) {
            repository.markFailed(asset.id, AudioFileState.PENDING)
            return AudioGenerationRequestResult.Failure(AudioGenerationErrorCode.SCHEDULE_FAILED)
        }
        return AudioGenerationRequestResult.Queued(asset, plan)
    }

    suspend fun retryGeneration(
        command: RetryAudioGenerationCommand,
    ): AudioGenerationRetryResult {
        if (!command.userConfirmed) return AudioGenerationRetryResult.ConfirmationRequired

        val original = repository.loadAsset(command.audioAssetId)
            ?: return AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.ASSET_NOT_FOUND)
        if (original.deletedAt != null) {
            return AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.DELETED)
        }
        if (original.purgeRequestedAt != null) {
            return AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.PURGE_REQUESTED)
        }
        if (!isSupportedConfig(original.config)) {
            return AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.UNSUPPORTED_CONFIG)
        }

        when (original.fileState) {
            AudioFileState.AVAILABLE -> return AudioGenerationRetryResult.ReusedAvailable(original)
            AudioFileState.PENDING -> return classifyPendingForRetry(original)
            AudioFileState.FAILED,
            AudioFileState.MISSING,
            AudioFileState.CANCELED,
            -> Unit
        }

        val reference = original.source.toReference()
        val snapshot = when (val loaded = repository.loadSource(reference)) {
            is AudioSourceLoadResult.Ready -> loaded.snapshot
            is AudioSourceLoadResult.Rejected -> {
                return AudioGenerationRetryResult.Failure(loaded.errorCode)
            }
        }
        sourceRelationshipError(reference, snapshot.source)?.let {
            return AudioGenerationRetryResult.Failure(it)
        }

        val key = AudioGenerationKeyFactory.create(snapshot.source, original.config)
        repository.findByGenerationKey(key)?.takeIf { it.id != original.id }?.let {
            return classifyExistingForRetry(it)
        }
        if (!hasSpace(command.estimatedOutputBytes, command.minimumReservationBytes, command.safetyMarginBytes)) {
            return AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.INSUFFICIENT_STORAGE)
        }

        val pending = if (key == original.generationKey) {
            when (val reset = repository.resetForRetry(
                ResetAudioForRetryCommand(
                    audioAssetId = original.id,
                    expectedState = original.fileState,
                    source = snapshot.source,
                    config = original.config,
                    generationKey = key,
                ),
            )) {
                is AudioAssetRetryResetResult.Reset -> reset.asset
                AudioAssetRetryResetResult.Conflict -> {
                    return AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.DUPLICATE_CONFLICT)
                }
                AudioAssetRetryResetResult.Rejected -> {
                    return AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.REPOSITORY_REJECTED)
                }
            }
        } else {
            val newId = audioAssetIdFactory()
            if (newId.isBlank()) {
                return AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.REPOSITORY_REJECTED)
            }
            when (val created = repository.createPending(
                CreatePendingAudioCommand(newId, snapshot.source, original.config, key),
            )) {
                is AudioAssetCreateResult.Created -> created.asset
                is AudioAssetCreateResult.Existing -> return classifyExistingForRetry(created.asset)
                AudioAssetCreateResult.Conflict -> {
                    return AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.DUPLICATE_CONFLICT)
                }
            }
        }

        val plan = workPlan(pending, AudioWorkRequestKind.EXPLICIT_RETRY)
        if (!scheduler.schedule(plan)) {
            repository.markFailed(pending.id, AudioFileState.PENDING)
            return AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.SCHEDULE_FAILED)
        }
        return AudioGenerationRetryResult.Queued(pending, plan)
    }

    suspend fun execute(audioAssetId: String): AudioGenerationExecutionResult {
        val initial = repository.loadAsset(audioAssetId)
            ?: return AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.ASSET_NOT_FOUND)
        suppressionError(initial)?.let { return AudioGenerationExecutionResult.Suppressed(it) }
        if (initial.fileState != AudioFileState.PENDING) {
            return AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.INVALID_STATE)
        }
        if (!isSupportedConfig(initial.config)) {
            markFailedIfPending(initial.id)
            return AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.UNSUPPORTED_CONFIG)
        }

        val snapshot = when (val loaded = repository.loadSource(initial.source.toReference())) {
            is AudioSourceLoadResult.Ready -> loaded.snapshot
            is AudioSourceLoadResult.Rejected -> {
                markFailedIfPending(initial.id)
                return AudioGenerationExecutionResult.Failure(loaded.errorCode)
            }
        }
        if (snapshot.source != initial.source) {
            markFailedIfPending(initial.id)
            return AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.SOURCE_CHANGED)
        }
        if (!hasSpace(
                executionEstimatedOutputBytes,
                executionMinimumReservationBytes,
                executionSafetyMarginBytes,
            )
        ) {
            markFailedIfPending(initial.id)
            return AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.INSUFFICIENT_STORAGE)
        }

        val target = runCatching {
            fileStore.createPendingTarget(initial.id, initial.config.targetFormat)
        }.getOrElse {
            markFailedIfPending(initial.id)
            return AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.FILE_IO)
        }

        val gatewayResult = runCatching {
            fileStore.openPendingWriter(target).use { writer ->
                gateway.generate(
                    AudioGenerationRequest(snapshot.content, initial.config),
                    PendingWriterOutput(writer),
                )
            }
        }.getOrElse {
            AudioGenerationGatewayResult.Failure(AudioGenerationGatewayErrorCode.UNKNOWN)
        }

        if (gatewayResult is AudioGenerationGatewayResult.Failure) {
            fileStore.removeTemporary(target)
            latestSuppression(initial.id)?.let { return AudioGenerationExecutionResult.Suppressed(it) }
            markFailedIfPending(initial.id)
            return AudioGenerationExecutionResult.Failure(gatewayResult.errorCode.toBusinessError())
        }

        latestSuppression(initial.id)?.let {
            fileStore.removeTemporary(target)
            return AudioGenerationExecutionResult.Suppressed(it)
        }
        if (fileStore.validatePending(target) is AudioFileValidation.Invalid) {
            fileStore.removeTemporary(target)
            markFailedIfPending(initial.id)
            return AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.INVALID_AUDIO_FILE)
        }
        latestSuppression(initial.id)?.let {
            fileStore.removeTemporary(target)
            return AudioGenerationExecutionResult.Suppressed(it)
        }

        val committed = when (val result = fileStore.commit(target)) {
            is AudioFileCommitResult.Success -> result.file
            is AudioFileCommitResult.Failure -> {
                fileStore.removeTemporary(target)
                markFailedIfPending(initial.id)
                return AudioGenerationExecutionResult.Failure(result.errorCode.toBusinessError())
            }
        }
        latestSuppression(initial.id)?.let {
            fileStore.removeCommitted(committed.relativePath)
            return AudioGenerationExecutionResult.Suppressed(it)
        }

        val available = repository.markAvailable(
            MarkAudioAvailableCommand(
                audioAssetId = initial.id,
                relativePath = committed.relativePath,
                mimeType = committed.mimeType,
                format = committed.format,
                sizeBytes = committed.sizeBytes,
            ),
        )
        if (!available) {
            fileStore.removeCommitted(committed.relativePath)
            return AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.REPOSITORY_REJECTED)
        }
        return AudioGenerationExecutionResult.Available(initial.id, committed.relativePath)
    }

    suspend fun cancelGeneration(audioAssetId: String): AudioGenerationCancelResult {
        val asset = repository.loadAsset(audioAssetId)
            ?: return AudioGenerationCancelResult.Failure(AudioGenerationErrorCode.ASSET_NOT_FOUND)
        if (asset.fileState == AudioFileState.CANCELED) {
            return AudioGenerationCancelResult.Canceled(asset.id)
        }
        if (asset.fileState != AudioFileState.PENDING) {
            return AudioGenerationCancelResult.Failure(AudioGenerationErrorCode.INVALID_STATE)
        }

        val canceled = repository.markCanceled(asset.id, AudioFileState.PENDING)
        if (!canceled) {
            val latest = repository.loadAsset(asset.id)
            if (latest?.fileState != AudioFileState.CANCELED) {
                return AudioGenerationCancelResult.Failure(
                    AudioGenerationErrorCode.REPOSITORY_REJECTED,
                )
            }
        }

        scheduler.cancel(workPlan(asset, AudioWorkRequestKind.INITIAL).uniqueWorkName)
        fileStore.removeTemporaryFilesForAsset(asset.id, asset.config.targetFormat)
        return AudioGenerationCancelResult.Canceled(asset.id)
    }

    private suspend fun classifyExisting(asset: AudioAssetRecord): AudioGenerationRequestResult {
        return when (asset.fileState) {
            AudioFileState.AVAILABLE -> AudioGenerationRequestResult.ReusedAvailable(asset)
            AudioFileState.PENDING -> {
                val plan = workPlan(asset, AudioWorkRequestKind.INITIAL)
                if (scheduler.isActive(plan.uniqueWorkName)) {
                    AudioGenerationRequestResult.ReusedPending(asset)
                } else {
                    AudioGenerationRequestResult.Failure(
                        AudioGenerationErrorCode.PENDING_WITHOUT_ACTIVE_WORK,
                    )
                }
            }
            AudioFileState.FAILED,
            AudioFileState.MISSING,
            AudioFileState.CANCELED,
            -> AudioGenerationRequestResult.ExplicitRetryRequired(asset)
        }
    }

    private suspend fun classifyExistingAfterCreateRace(
        asset: AudioAssetRecord,
    ): AudioGenerationRequestResult {
        if (asset.fileState != AudioFileState.PENDING) return classifyExisting(asset)
        val plan = workPlan(asset, AudioWorkRequestKind.INITIAL)
        if (!scheduler.isActive(plan.uniqueWorkName) && !scheduler.schedule(plan)) {
            return AudioGenerationRequestResult.Failure(AudioGenerationErrorCode.SCHEDULE_FAILED)
        }
        return AudioGenerationRequestResult.ReusedPending(asset)
    }

    private suspend fun classifyPendingForRetry(
        asset: AudioAssetRecord,
    ): AudioGenerationRetryResult {
        val plan = workPlan(asset, AudioWorkRequestKind.INITIAL)
        return if (scheduler.isActive(plan.uniqueWorkName)) {
            AudioGenerationRetryResult.ReusedPending(asset)
        } else {
            AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.PENDING_WITHOUT_ACTIVE_WORK)
        }
    }

    private suspend fun classifyExistingForRetry(
        asset: AudioAssetRecord,
    ): AudioGenerationRetryResult {
        return when (asset.fileState) {
            AudioFileState.AVAILABLE -> AudioGenerationRetryResult.ReusedAvailable(asset)
            AudioFileState.PENDING -> classifyPendingForRetry(asset)
            AudioFileState.FAILED,
            AudioFileState.MISSING,
            AudioFileState.CANCELED,
            -> AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.DUPLICATE_CONFLICT)
        }
    }

    private fun workPlan(
        asset: AudioAssetRecord,
        kind: AudioWorkRequestKind,
    ): AudioGenerationWorkPlan {
        return AudioGenerationWorkPolicy.plan(asset.id, asset.generationKey, kind)
    }

    private fun hasSpace(
        estimated: Long,
        minimum: Long,
        margin: Long,
    ): Boolean {
        return fileStore.preflight(estimated, minimum, margin) is AudioStoragePreflight.Sufficient
    }

    private suspend fun latestSuppression(audioAssetId: String): AudioGenerationErrorCode? {
        return repository.loadAsset(audioAssetId)?.let(::suppressionError)
    }

    private suspend fun markFailedIfPending(audioAssetId: String) {
        repository.markFailed(audioAssetId, AudioFileState.PENDING)
    }

    private fun suppressionError(asset: AudioAssetRecord): AudioGenerationErrorCode? {
        if (asset.deletedAt != null) return AudioGenerationErrorCode.DELETED
        if (asset.purgeRequestedAt != null) return AudioGenerationErrorCode.PURGE_REQUESTED
        if (asset.fileState == AudioFileState.CANCELED) return AudioGenerationErrorCode.CANCELED
        return null
    }

    private fun sourceRelationshipError(
        reference: AudioSourceReference,
        source: AudioAssetSource,
    ): AudioGenerationErrorCode? {
        if (reference.issueId != source.issueId) return AudioGenerationErrorCode.CROSS_ISSUE
        if (reference.stageId != source.stageId) return AudioGenerationErrorCode.CROSS_STAGE
        return when {
            reference is AudioSourceReference.Message &&
                source is AudioAssetSource.CompletedMessage &&
                reference.messageId == source.messageId -> null
            reference is AudioSourceReference.Artifact &&
                source is AudioAssetSource.ConfirmedArtifact &&
                reference.artifactId == source.artifactId -> null
            else -> AudioGenerationErrorCode.SOURCE_NOT_FOUND
        }
    }

    private fun isSupportedConfig(config: AudioGenerationConfig): Boolean {
        return config.voiceProfileId == V1_VOICE_PROFILE_ID &&
            config.parameterVersion == V1_PARAMETER_VERSION
    }

    private fun AudioAssetSource.toReference(): AudioSourceReference = when (this) {
        is AudioAssetSource.CompletedMessage -> AudioSourceReference.Message(
            issueId,
            stageId,
            messageId,
        )
        is AudioAssetSource.ConfirmedArtifact -> AudioSourceReference.Artifact(
            issueId,
            stageId,
            artifactId,
        )
    }

    private fun AudioGenerationGatewayErrorCode.toBusinessError(): AudioGenerationErrorCode =
        when (this) {
            AudioGenerationGatewayErrorCode.AUTH_UNAVAILABLE -> AudioGenerationErrorCode.AUTH_UNAVAILABLE
            AudioGenerationGatewayErrorCode.RATE_LIMITED -> AudioGenerationErrorCode.RATE_LIMITED
            AudioGenerationGatewayErrorCode.OFFLINE -> AudioGenerationErrorCode.OFFLINE
            AudioGenerationGatewayErrorCode.TIMEOUT -> AudioGenerationErrorCode.TIMEOUT
            AudioGenerationGatewayErrorCode.EMPTY_RESPONSE -> AudioGenerationErrorCode.EMPTY_RESPONSE
            AudioGenerationGatewayErrorCode.CANCELED -> AudioGenerationErrorCode.CANCELED
            AudioGenerationGatewayErrorCode.INVALID_RESPONSE -> AudioGenerationErrorCode.INVALID_RESPONSE
            AudioGenerationGatewayErrorCode.UNKNOWN -> AudioGenerationErrorCode.UNKNOWN
        }

    private fun AudioFileStoreErrorCode.toBusinessError(): AudioGenerationErrorCode =
        when (this) {
            AudioFileStoreErrorCode.INSUFFICIENT_STORAGE -> AudioGenerationErrorCode.INSUFFICIENT_STORAGE
            AudioFileStoreErrorCode.EMPTY_AUDIO,
            AudioFileStoreErrorCode.INVALID_AUDIO_FORMAT,
            -> AudioGenerationErrorCode.INVALID_AUDIO_FILE
            else -> AudioGenerationErrorCode.FILE_IO
        }

    private class PendingWriterOutput(
        private val writer: AudioPendingWriter,
    ) : AudioGenerationOutput {
        override fun write(bytes: ByteArray) = writer.write(bytes)
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            writer.write(bytes, offset, length)
        }
    }

    companion object {
        const val V1_VOICE_PROFILE_ID: String = "jianyu-default"
        const val V1_PARAMETER_VERSION: Int = 1

        private const val DEFAULT_ESTIMATED_OUTPUT_BYTES: Long = 4L * 1024L * 1024L
        private const val DEFAULT_MINIMUM_RESERVATION_BYTES: Long = 4L * 1024L * 1024L
        private const val DEFAULT_SAFETY_MARGIN_BYTES: Long = 8L * 1024L * 1024L
    }
}
