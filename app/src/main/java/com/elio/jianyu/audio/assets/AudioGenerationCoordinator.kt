package com.elio.jianyu.audio.assets

import com.elio.jianyu.data.AudioFileState

/** 正式音频生成服务使用的稳定业务错误码。 */
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
    data class Ready(
        val snapshot: AudioSourceSnapshot,
    ) : AudioSourceLoadResult

    data class Rejected(
        val errorCode: AudioGenerationErrorCode,
    ) : AudioSourceLoadResult
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
    data class Created(
        val asset: AudioAssetRecord,
    ) : AudioAssetCreateResult

    data class Existing(
        val asset: AudioAssetRecord,
    ) : AudioAssetCreateResult

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
    data class Reset(
        val asset: AudioAssetRecord,
    ) : AudioAssetRetryResetResult

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

/** 阶段 B 由最终 Room Repository 适配该能力接口；正式调用方不得直接访问 DAO。 */
interface AudioAssetRepositoryPort {
    suspend fun loadSource(reference: AudioSourceReference): AudioSourceLoadResult

    suspend fun findByGenerationKey(generationKey: String): AudioAssetRecord?

    suspend fun createPending(command: CreatePendingAudioCommand): AudioAssetCreateResult

    suspend fun loadAsset(audioAssetId: String): AudioAssetRecord?

    suspend fun markAvailable(command: MarkAudioAvailableCommand): Boolean

    suspend fun markFailed(
        audioAssetId: String,
        expectedState: AudioFileState,
    ): Boolean

    suspend fun markCanceled(
        audioAssetId: String,
        expectedState: AudioFileState,
    ): Boolean

    /**
     * 将失败、缺失或已取消资产以比较并交换方式恢复为 PENDING。
     *
     * 阶段 A 使用拒绝作为安全默认值；阶段 B 的 Room 适配器必须提供原子实现。
     */
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

    data class ReusedAvailable(
        val asset: AudioAssetRecord,
    ) : AudioGenerationRequestResult

    data class ReusedPending(
        val asset: AudioAssetRecord,
    ) : AudioGenerationRequestResult

    data class ExplicitRetryRequired(
        val asset: AudioAssetRecord,
    ) : AudioGenerationRequestResult

    data class Failure(
        val errorCode: AudioGenerationErrorCode,
    ) : AudioGenerationRequestResult
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

    data class ReusedAvailable(
        val asset: AudioAssetRecord,
    ) : AudioGenerationRetryResult

    data class ReusedPending(
        val asset: AudioAssetRecord,
    ) : AudioGenerationRetryResult

    data class Failure(
        val errorCode: AudioGenerationErrorCode,
    ) : AudioGenerationRetryResult
}

sealed interface AudioGenerationExecutionResult {
    data class Available(
        val audioAssetId: String,
        val relativePath: String,
    ) : AudioGenerationExecutionResult

    data class Suppressed(
        val errorCode: AudioGenerationErrorCode,
    ) : AudioGenerationExecutionResult

    data class Failure(
        val errorCode: AudioGenerationErrorCode,
    ) : AudioGenerationExecutionResult
}

sealed interface AudioGenerationCancelResult {
    data class Canceled(
        val audioAssetId: String,
    ) : AudioGenerationCancelResult

    data class Failure(
        val errorCode: AudioGenerationErrorCode,
    ) : AudioGenerationCancelResult
}

/**
 * 正式音频资产生成协调器。
 *
 * 阶段 A 只冻结纯业务状态机。Worker、BYOK Gateway、Room Adapter 与 UI 在 PR09-11 合并后接线。
 */
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
        if (!command.userConfirmed) {
            return AudioGenerationRequestResult.ConfirmationRequired
        }
        if (!isSupportedConfig(command.config)) {
            return AudioGenerationRequestResult.Failure(AudioGenerationErrorCode.UNSUPPORTED_CONFIG)
        }

        val snapshot = when (val sourceResult = repository.loadSource(command.sourceReference)) {
            is AudioSourceLoadResult.Ready -> sourceResult.snapshot
            is AudioSourceLoadResult.Rejected -> {
                return AudioGenerationRequestResult.Failure(sourceResult.errorCode)
            }
        }
        sourceRelationshipError(command.sourceReference, snapshot.source)?.let { errorCode ->
            return AudioGenerationRequestResult.Failure(errorCode)
        }

        val generationKey = AudioGenerationKeyFactory.create(snapshot.source, command.config)
        repository.findByGenerationKey(generationKey)?.let { existing ->
            return classifyExisting(existing)
        }

        if (fileStore.preflight(
                estimatedOutputBytes = command.estimatedOutputBytes,
                minimumReservationBytes = command.minimumReservationBytes,
                safetyMarginBytes = command.safetyMarginBytes,
            ) is AudioStoragePreflight.Insufficient
        ) {
            return AudioGenerationRequestResult.Failure(
                AudioGenerationErrorCode.INSUFFICIENT_STORAGE,
            )
        }

        val audioAssetId = audioAssetIdFactory()
        if (audioAssetId.isBlank()) {
            return AudioGenerationRequestResult.Failure(AudioGenerationErrorCode.REPOSITORY_REJECTED)
        }
        val createResult = repository.createPending(
            CreatePendingAudioCommand(
                audioAssetId = audioAssetId,
                source = snapshot.source,
                config = command.config,
                generationKey = generationKey,
            ),
        )
        val asset = when (createResult) {
            is AudioAssetCreateResult.Created -> createResult.asset
            is AudioAssetCreateResult.Existing -> {
                return classifyExistingAfterCreateRace(createResult.asset)
            }
            AudioAssetCreateResult.Conflict -> {
                return AudioGenerationRequestResult.Failure(
                    AudioGenerationErrorCode.DUPLICATE_CONFLICT,
                )
            }
        }

        val plan = AudioGenerationWorkPolicy.plan(
            audioAssetId = asset.id,
            generationKey = asset.generationKey,
            requestKind = AudioWorkRequestKind.INITIAL,
        )
        if (!scheduler.schedule(plan)) {
            repository.markFailed(asset.id, AudioFileState.PENDING)
            return AudioGenerationRequestResult.Failure(AudioGenerationErrorCode.SCHEDULE_FAILED)
        }
        return AudioGenerationRequestResult.Queued(
            asset = asset,
            workPlan = plan,
        )
    }

    suspend fun retryGeneration(
        command: RetryAudioGenerationCommand,
    ): AudioGenerationRetryResult {
        if (!command.userConfirmed) {
            return AudioGenerationRetryResult.ConfirmationRequired
        }

        val original = repository.loadAsset(command.audioAssetId)
            ?: return AudioGenerationRetryResult.Failure(
                AudioGenerationErrorCode.ASSET_NOT_FOUND,
            )
        if (original.deletedAt != null) {
            return AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.DELETED)
        }
        if (original.purgeRequestedAt != null) {
            return AudioGenerationRetryResult.Failure(AudioGenerationErrorCode.PURGE_REQUESTED)
        }
        if (!isSupportedConfig(original.config)) {
            return AudioGenerationRetryResult.Failure(
                AudioGenerationErrorCode.UNSUPPORTED_CONFIG,
            )
        }

        when (original.fileState) {
            AudioFileState.AVAILABLE -> {
                return AudioGenerationRetryResult.ReusedAvailable(original)
            }
            AudioFileState.PENDING -> {
                val plan = AudioGenerationWorkPolicy.plan(
                    audioAssetId = original.id,
                    generationKey = original.generationKey,
                    requestKind = AudioWorkRequestKind.INITIAL,
                )
                return if (scheduler.isActive(plan.uniqueWorkName)) {
                    AudioGenerationRetryResult.ReusedPending(original)
                } else {
                    AudioGenerationRetryResult.Failure(
                        AudioGenerationErrorCode.PENDING_WITHOUT_ACTIVE_WORK,
                    )
                }
            }
            AudioFileState.FAILED,
            AudioFileState.MISSING,
            AudioFileState.CANCELED,
            -> Unit
        }

        val reference = original.source.toReference()
        val snapshot = when (val sourceResult = repository.loadSource(reference)) {
            is AudioSourceLoadResult.Ready -> sourceResult.snapshot
            is AudioSourceLoadResult.Rejected -> {
                return AudioGenerationRetryResult.Failure(sourceResult.errorCode)
            }
        }
        sourceRelationshipError(reference, snapshot.source)?.let { errorCode ->
            return AudioGenerationRetryResult.Failure(errorCode)
        }

        val generationKey = AudioGenerationKeyFactory.create(snapshot.source, original.config)
        val existingForKey = repository.findByGenerationKey(generationKey)
        if (existingForKey != null && existingForKey.id != original.id) {
            return classifyExistingForRetry(existingForKey)
        }

        if (fileStore.preflight(
                estimatedOutputBytes = command.estimatedOutputBytes,
                minimumReservationBytes = command.minimumReservationBytes,
                safetyMarginBytes = command.safetyMarginBytes,
            ) is AudioStoragePreflight.Insufficient
        ) {
            return AudioGenerationRetryResult.Failure(
                AudioGenerationErrorCode.INSUFFICIENT_STORAGE,
            )
        }

        val pendingAsset = if (generationKey == original.generationKey) {
            when (val resetResult = repository.resetForRetry(
                ResetAudioForRetryCommand(
                    audioAssetId = original.id,
                    expectedState = original.fileState,
                    source = snapshot.source,
                    config = original.config,
                    generationKey = generationKey,
                ),
            )) {
                is AudioAssetRetryResetResult.Reset -> resetResult.asset
                AudioAssetRetryResetResult.Conflict -> {
                    return AudioGenerationRetryResult.Failure(
                        AudioGenerationErrorCode.DUPLICATE_CONFLICT,
                    )
                }
                AudioAssetRetryResetResult.Rejected -> {
                    return AudioGenerationRetryResult.Failure(
                        AudioGenerationErrorCode.REPOSITORY_REJECTED,
                    )
                }
            }
        } else {
            val newAudioAssetId = audioAssetIdFactory()
            if (newAudioAssetId.isBlank()) {
                return AudioGenerationRetryResult.Failure(
                    AudioGenerationErrorCode.REPOSITORY_REJECTED,
                )
            }
            when (val createResult = repository.createPending(
                CreatePendingAudioCommand(
                    audioAssetId = newAudioAssetId,
                    source = snapshot.source,
                    config = original.config,
                    generationKey = generationKey,
                ),
            )) {
                is AudioAssetCreateResult.Created -> createResult.asset
                is AudioAssetCreateResult.Existing -> {
                    return classifyExistingForRetry(createResult.asset)
                }
                AudioAssetCreateResult.Conflict -> {
                    return AudioGenerationRetryResult.Failure(
                        AudioGenerationErrorCode.DUPLICATE_CONFLICT,
                    )
                }
            }
        }

        val plan = AudioGenerationWorkPolicy.plan(
            audioAssetId = pendingAsset.id,
            generationKey = pendingAsset.generationKey,
            requestKind = AudioWorkRequestKind.EXPLICIT_RETRY,
        )
        if (!scheduler.schedule(plan)) {
            repository.markFailed(
                audioAssetId = pendingAsset.id,
                expectedState = AudioFileState.PENDING,
            )
            return AudioGenerationRetryResult.Failure(
                AudioGenerationErrorCode.SCHEDULE_FAILED,
            )
        }
        return AudioGenerationRetryResult.Queued(
            asset = pendingAsset,
            workPlan = plan,
        )
    }

    suspend fun execute(audioAssetId: String): AudioGenerationExecutionResult {
        val initial = repository.loadAsset(audioAssetId)
            ?: return AudioGenerationExecutionResult.Failure(
                AudioGenerationErrorCode.ASSET_NOT_FOUND,
            )
        suppressionError(initial)?.let { errorCode ->
            return AudioGenerationExecutionResult.Suppressed(errorCode)
        }
        if (initial.fileState != AudioFileState.PENDING) {
            return AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.INVALID_STATE)
        }
        if (!isSupportedConfig(initial.config)) {
            markFailedIfStillPending(initial.id)
            return AudioGenerationExecutionResult.Failure(
                AudioGenerationErrorCode.UNSUPPORTED_CONFIG,
            )
        }

        val snapshot = when (val sourceResult = repository.loadSource(initial.source.toReference())) {
            is AudioSourceLoadResult.Ready -> sourceResult.snapshot
            is AudioSourceLoadResult.Rejected -> {
                markFailedIfStillPending(initial.id)
                return AudioGenerationExecutionResult.Failure(sourceResult.errorCode)
            }
        }
        if (!sameStableSource(initial.source, snapshot.source)) {
            markFailedIfStillPending(initial.id)
            return AudioGenerationExecutionResult.Failure(
                AudioGenerationErrorCode.SOURCE_CHANGED,
            )
        }

        if (fileStore.preflight(
                estimatedOutputBytes = executionEstimatedOutputBytes,
                minimumReservationBytes = executionMinimumReservationBytes,
                safetyMarginBytes = executionSafetyMarginBytes,
            ) is AudioStoragePreflight.Insufficient
        ) {
            markFailedIfStillPending(initial.id)
            return AudioGenerationExecutionResult.Failure(
                AudioGenerationErrorCode.INSUFFICIENT_STORAGE,
            )
        }

        val target = try {
            fileStore.createPendingTarget(initial.id, initial.config.targetFormat)
        } catch (_: Throwable) {
            markFailedIfStillPending(initial.id)
            return AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.FILE_IO)
        }

        val gatewayResult = try {
            fileStore.openPendingWriter(target).use { writer ->
                gateway.generate(
                    request = AudioGenerationRequest(
                        content = snapshot.content,
                        config = initial.config,
                    ),
                    output = PendingWriterOutput(writer),
                )
            }
        } catch (_: Throwable) {
            AudioGenerationGatewayResult.Failure(AudioGenerationGatewayErrorCode.UNKNOWN)
        }

        if (gatewayResult is AudioGenerationGatewayResult.Failure) {
            fileStore.removeTemporary(target)
            val latest = repository.loadAsset(initial.id)
            latest?.let(::suppressionError)?.let { errorCode ->
                return AudioGenerationExecutionResult.Suppressed(errorCode)
            }
            markFailedIfStillPending(initial.id)
            return AudioGenerationExecutionResult.Failure(
                gatewayResult.errorCode.toBusinessError(),
            )
        }

        repository.loadAsset(initial.id)?.let(::suppressionError)?.let { errorCode ->
            fileStore.removeTemporary(target)
            return AudioGenerationExecutionResult.Suppressed(errorCode)
        }

        when (fileStore.validatePending(target)) {
            is AudioFileValidation.Invalid -> {
                fileStore.removeTemporary(target)
                markFailedIfStillPending(initial.id)
                return AudioGenerationExecutionResult.Failure(
                    AudioGenerationErrorCode.INVALID_AUDIO_FILE,
                )
            }
            is AudioFileValidation.Valid -> Unit
        }

        repository.loadAsset(initial.id)?.let(::suppressionError)?.let { errorCode ->
            fileStore.removeTemporary(target)
            return AudioGenerationExecutionResult.Suppressed(errorCode)
        }

        val committed = when (val commitResult = fileStore.commit(target)) {
            is AudioFileCommitResult.Success -> commitResult.file
            is AudioFileCommitResult.Failure -> {
                fileStore.removeTemporary(target)
                markFailedIfStillPending(initial.id)
                return AudioGenerationExecutionResult.Failure(
                    commitResult.errorCode.toBusinessError(),
                )
            }
        }

        repository.loadAsset(initial.id)?.let(::suppressionError)?.let { errorCode ->
            fileStore.removeCommitted(committed.relativePath)
            return AudioGenerationExecutionResult.Suppressed(errorCode)
        }

        val markedAvailable = repository.markAvailable(
            MarkAudioAvailableCommand(
                audioAssetId = initial.id,
                relativePath = committed.relativePath,
                mimeType = committed.mimeType,
                format = committed.format,
                sizeBytes = committed.sizeBytes,
            ),
        )
        if (!markedAvailable) {
            fileStore.removeCommitted(committed.relativePath)
            return AudioGenerationExecutionResult.Failure(
                AudioGenerationErrorCode.REPOSITORY_REJECTED,
            )
        }
        return AudioGenerationExecutionResult.Available(
            audioAssetId = initial.id,
            relativePath = committed.relativePath,
        )
    }

    suspend fun cancelGeneration(audioAssetId: String): AudioGenerationCancelResult {
        val asset = repository.loadAsset(audioAssetId)
            ?: return AudioGenerationCancelResult.Failure(
                AudioGenerationErrorCode.ASSET_NOT_FOUND,
            )
        if (asset.fileState == AudioFileState.CANCELED) {
            return AudioGenerationCancelResult.Canceled(asset.id)
        }
        if (asset.fileState != AudioFileState.PENDING) {
            return AudioGenerationCancelResult.Failure(AudioGenerationErrorCode.INVALID_STATE)
        }

        val plan = AudioGenerationWorkPolicy.plan(
            audioAssetId = asset.id,
            generationKey = asset.generationKey,
            requestKind = AudioWorkRequestKind.INITIAL,
        )
        scheduler.cancel(plan.uniqueWorkName)
        val markedCanceled = repository.markCanceled(
            audioAssetId = asset.id,
            expectedState = AudioFileState.PENDING,
        )

        val target = runCatching {
            fileStore.createPendingTarget(asset.id, asset.config.targetFormat)
        }.getOrNull()
        if (target != null) {
            fileStore.removeTemporary(target)
            fileStore.removeCommitted(target.finalRelativePath)
        }

        if (!markedCanceled) {
            val latest = repository.loadAsset(asset.id)
            if (latest?.fileState == AudioFileState.CANCELED) {
                return AudioGenerationCancelResult.Canceled(asset.id)
            }
            return AudioGenerationCancelResult.Failure(
                AudioGenerationErrorCode.REPOSITORY_REJECTED,
            )
        }
        return AudioGenerationCancelResult.Canceled(asset.id)
    }

    private suspend fun classifyExisting(
        asset: AudioAssetRecord,
    ): AudioGenerationRequestResult {
        return when (asset.fileState) {
            AudioFileState.AVAILABLE -> AudioGenerationRequestResult.ReusedAvailable(asset)
            AudioFileState.PENDING -> {
                val plan = AudioGenerationWorkPolicy.plan(
                    audioAssetId = asset.id,
                    generationKey = asset.generationKey,
                    requestKind = AudioWorkRequestKind.INITIAL,
                )
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

    private suspend fun classifyExistingForRetry(
        asset: AudioAssetRecord,
    ): AudioGenerationRetryResult {
        return when (asset.fileState) {
            AudioFileState.AVAILABLE -> AudioGenerationRetryResult.ReusedAvailable(asset)
            AudioFileState.PENDING -> {
                val plan = AudioGenerationWorkPolicy.plan(
                    audioAssetId = asset.id,
                    generationKey = asset.generationKey,
                    requestKind = AudioWorkRequestKind.INITIAL,
                )
                if (scheduler.isActive(plan.uniqueWorkName)) {
                    AudioGenerationRetryResult.ReusedPending(asset)
                } else {
                    AudioGenerationRetryResult.Failure(
                        AudioGenerationErrorCode.PENDING_WITHOUT_ACTIVE_WORK,
                    )
                }
            }
            AudioFileState.FAILED,
            AudioFileState.MISSING,
            AudioFileState.CANCELED,
            -> AudioGenerationRetryResult.Failure(
                AudioGenerationErrorCode.DUPLICATE_CONFLICT,
            )
        }
    }

    private suspend fun classifyExistingAfterCreateRace(
        asset: AudioAssetRecord,
    ): AudioGenerationRequestResult {
        if (asset.fileState != AudioFileState.PENDING) {
            return classifyExisting(asset)
        }
        val plan = AudioGenerationWorkPolicy.plan(
            audioAssetId = asset.id,
            generationKey = asset.generationKey,
            requestKind = AudioWorkRequestKind.INITIAL,
        )
        if (!scheduler.isActive(plan.uniqueWorkName) && !scheduler.schedule(plan)) {
            return AudioGenerationRequestResult.Failure(AudioGenerationErrorCode.SCHEDULE_FAILED)
        }
        return AudioGenerationRequestResult.ReusedPending(asset)
    }

    private suspend fun markFailedIfStillPending(audioAssetId: String) {
        repository.markFailed(
            audioAssetId = audioAssetId,
            expectedState = AudioFileState.PENDING,
        )
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

    private fun sameStableSource(
        expected: AudioAssetSource,
        actual: AudioAssetSource,
    ): Boolean {
        return expected == actual
    }

    private fun suppressionError(asset: AudioAssetRecord): AudioGenerationErrorCode? {
        if (asset.deletedAt != null) return AudioGenerationErrorCode.DELETED
        if (asset.purgeRequestedAt != null) return AudioGenerationErrorCode.PURGE_REQUESTED
        if (asset.fileState == AudioFileState.CANCELED) return AudioGenerationErrorCode.CANCELED
        return null
    }

    private fun isSupportedConfig(config: AudioGenerationConfig): Boolean {
        return config.voiceProfileId == V1_VOICE_PROFILE_ID &&
            config.parameterVersion == V1_PARAMETER_VERSION
    }

    private fun AudioAssetSource.toReference(): AudioSourceReference {
        return when (this) {
            is AudioAssetSource.CompletedMessage -> AudioSourceReference.Message(
                issueId = issueId,
                stageId = stageId,
                messageId = messageId,
            )
            is AudioAssetSource.ConfirmedArtifact -> AudioSourceReference.Artifact(
                issueId = issueId,
                stageId = stageId,
                artifactId = artifactId,
            )
        }
    }

    private fun AudioGenerationGatewayErrorCode.toBusinessError(): AudioGenerationErrorCode {
        return when (this) {
            AudioGenerationGatewayErrorCode.AUTH_UNAVAILABLE -> AudioGenerationErrorCode.AUTH_UNAVAILABLE
            AudioGenerationGatewayErrorCode.RATE_LIMITED -> AudioGenerationErrorCode.RATE_LIMITED
            AudioGenerationGatewayErrorCode.OFFLINE -> AudioGenerationErrorCode.OFFLINE
            AudioGenerationGatewayErrorCode.TIMEOUT -> AudioGenerationErrorCode.TIMEOUT
            AudioGenerationGatewayErrorCode.EMPTY_RESPONSE -> AudioGenerationErrorCode.EMPTY_RESPONSE
            AudioGenerationGatewayErrorCode.CANCELED -> AudioGenerationErrorCode.CANCELED
            AudioGenerationGatewayErrorCode.INVALID_RESPONSE -> AudioGenerationErrorCode.INVALID_RESPONSE
            AudioGenerationGatewayErrorCode.UNKNOWN -> AudioGenerationErrorCode.UNKNOWN
        }
    }

    private fun AudioFileStoreErrorCode.toBusinessError(): AudioGenerationErrorCode {
        return when (this) {
            AudioFileStoreErrorCode.INSUFFICIENT_STORAGE -> AudioGenerationErrorCode.INSUFFICIENT_STORAGE
            AudioFileStoreErrorCode.EMPTY_AUDIO,
            AudioFileStoreErrorCode.INVALID_AUDIO_FORMAT,
            -> AudioGenerationErrorCode.INVALID_AUDIO_FILE
            else -> AudioGenerationErrorCode.FILE_IO
        }
    }

    private class PendingWriterOutput(
        private val writer: AudioPendingWriter,
    ) : AudioGenerationOutput {
        override fun write(bytes: ByteArray) {
            writer.write(bytes)
        }

        override fun write(
            bytes: ByteArray,
            offset: Int,
            length: Int,
        ) {
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
