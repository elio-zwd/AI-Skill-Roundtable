package com.elio.jianyu.result

import com.elio.jianyu.data.ArtifactDraftSourceEntity
import com.elio.jianyu.data.ArtifactMaterialSourceEntity
import com.elio.jianyu.data.ArtifactMessageSourceEntity
import com.elio.jianyu.data.ArtifactRunSourceEntity
import com.elio.jianyu.data.ArtifactSources
import com.elio.jianyu.data.ConfirmArtifactCommand
import com.elio.jianyu.data.ConfirmedArtifactEntity
import com.elio.jianyu.data.ExecutionRunEntity
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.Message
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.SaveStageDraftCommand
import com.elio.jianyu.data.StageSummaryDraftEntity
import com.elio.jianyu.data.StageSummaryDraftRevisionEntity
import com.elio.jianyu.data.getStageCollaboration
import com.elio.jianyu.data.listArtifactSourcesForIssue

class StageResultService(
    private val repository: JianyuRepository,
) {
    suspend fun load(
        issueId: String,
        stageId: String,
    ): StageResultLoadResult {
        val snapshot = when (val recovered = repository.recoverIssue(issueId)) {
            is RepositoryResult.Success -> recovered.value
            is RepositoryResult.Failure -> return StageResultLoadResult.Failure(
                STAGE_RESULT_LOAD_FAILED,
            )
        }
        if (snapshot.core.stages.none { it.id == stageId && it.issueId == issueId }) {
            return StageResultLoadResult.Failure(STAGE_RESULT_STAGE_NOT_FOUND)
        }
        val artifacts = snapshot.resources.artifacts.filter {
            it.issueId == issueId && it.stageId == stageId
        }
        val collaboration = when (val loaded = repository.getStageCollaboration(stageId)) {
            is RepositoryResult.Success -> loaded.value
            is RepositoryResult.Failure -> null
        }
        val artifactSources = when (val loaded = repository.listArtifactSourcesForIssue(issueId)) {
            is RepositoryResult.Success -> loaded.value.associateBy { it.artifactId }
            is RepositoryResult.Failure -> emptyMap()
        }
        val runsById = snapshot.core.runs.associateBy { it.id }
        val selectableMessages = selectableStageOutputs(
            issueId = issueId,
            stageId = stageId,
            messages = snapshot.core.messages,
            runsById = runsById,
        )
        val usageByRun = collaboration?.messageUsageByRun.orEmpty()
        val metadata = selectableMessages.associate { message ->
            val run = requireNotNull(runsById[message.executionRunId])
            message.id to StageMessageSourceMetadata(
                runKind = run.runKind,
                runStatus = run.status,
                historyScope = run.historyScope,
                participantSnapshotId = message.participantSnapshotId,
                actualMessageUsageCount = usageByRun[run.id].orEmpty().size,
            )
        }
        return StageResultLoadResult.Ready(
            StageResultWorkspace(
                issueId = issueId,
                stageId = stageId,
                draft = snapshot.resources.drafts.singleOrNull {
                    it.issueId == issueId && it.stageId == stageId
                },
                draftRevisions = snapshot.resources.draftRevisions
                    .filter { it.issueId == issueId && it.stageId == stageId }
                    .sortedBy { it.revisionNumber },
                artifacts = artifacts.sortedWith(
                    compareBy<ConfirmedArtifactEntity> { it.confirmedAt }.thenBy { it.id },
                ),
                selectableMessages = selectableMessages,
                materialUsages = snapshot.resources.materialUsages
                    .filter { it.issueId == issueId && it.stageId == stageId }
                    .sortedWith(compareBy({ it.createdAt }, { it.id })),
                artifactRevisionResolution = ArtifactRevisionResolver.resolve(artifacts),
                messageSourceMetadata = metadata,
                messageUsageByRun = usageByRun,
                artifactSourcesById = artifactSources.filterKeys { artifactId ->
                    artifacts.any { it.id == artifactId }
                },
            ),
        )
    }

    suspend fun saveDraft(
        command: SaveStageResultDraftCommand,
    ): StageDraftWriteResult {
        if (command.savedAt <= 0L || command.expectedCurrentRevision < 0) {
            return StageDraftWriteResult.Failure(DRAFT_SAVE_FAILED)
        }
        val snapshot = when (val recovered = repository.recoverIssue(command.issueId)) {
            is RepositoryResult.Success -> recovered.value
            is RepositoryResult.Failure -> return StageDraftWriteResult.Failure(DRAFT_SAVE_FAILED)
        }
        if (snapshot.core.stages.none {
                it.id == command.stageId && it.issueId == command.issueId
            }
        ) {
            return StageDraftWriteResult.Failure(STAGE_RESULT_STAGE_NOT_FOUND)
        }
        val current = snapshot.resources.drafts.singleOrNull {
            it.issueId == command.issueId && it.stageId == command.stageId
        }
        if (
            (current?.revisionNumber ?: 0) != command.expectedCurrentRevision ||
            (current != null && current.id != command.draftId)
        ) {
            return StageDraftWriteResult.Conflict
        }

        return when (val plan = StageDraftEditorPolicy.plan(current, command.content)) {
            StageDraftSavePlan.Unchanged -> StageDraftWriteResult.Unchanged(requireNotNull(current))
            is StageDraftSavePlan.Persist -> {
                val draft = StageSummaryDraftEntity(
                    id = command.draftId,
                    issueId = command.issueId,
                    stageId = command.stageId,
                    content = command.content,
                    revisionNumber = plan.revisionNumber,
                    createdAt = current?.createdAt ?: command.savedAt,
                    updatedAt = command.savedAt,
                )
                val revision = StageSummaryDraftRevisionEntity(
                    id = command.revisionId,
                    issueId = command.issueId,
                    stageId = command.stageId,
                    draftIdSnapshot = command.draftId,
                    revisionNumber = plan.revisionNumber,
                    contentSnapshot = command.content,
                    createdAt = command.savedAt,
                )
                when (
                    val saved = repository.saveStageDraft(
                        SaveStageDraftCommand(draft = draft, revision = revision),
                    )
                ) {
                    is RepositoryResult.Success -> StageDraftWriteResult.Saved(
                        draft = saved.value,
                        revision = revision,
                    )
                    is RepositoryResult.Failure -> when (
                        StageDraftEditorPolicy.mapFailure(saved.error)
                    ) {
                        StageDraftSaveFailure.REVISION_CONFLICT -> StageDraftWriteResult.Conflict
                        StageDraftSaveFailure.STORAGE_FAILURE -> StageDraftWriteResult.Failure(
                            DRAFT_SAVE_FAILED,
                        )
                    }
                }
            }
        }
    }

    suspend fun abandonDraft(
        issueId: String,
        stageId: String,
    ): StageDraftAbandonResult {
        return when (repository.abandonStageDraft(issueId, stageId)) {
            is RepositoryResult.Success -> StageDraftAbandonResult.Abandoned
            is RepositoryResult.Failure -> StageDraftAbandonResult.Failure(
                DRAFT_ABANDON_FAILED,
            )
        }
    }

    suspend fun confirmArtifact(
        command: ConfirmStageArtifactCommand,
    ): StageArtifactConfirmationResult {
        if (
            command.title.isBlank() || command.confirmedAt <= 0L ||
            command.artifactId.isBlank()
        ) {
            return StageArtifactConfirmationResult.Failure(ARTIFACT_CONFIRMATION_FAILED)
        }
        val snapshot = when (val recovered = repository.recoverIssue(command.issueId)) {
            is RepositoryResult.Success -> recovered.value
            is RepositoryResult.Failure -> return StageArtifactConfirmationResult.Failure(
                ARTIFACT_CONFIRMATION_FAILED,
            )
        }
        if (snapshot.core.stages.none {
                it.id == command.stageId && it.issueId == command.issueId
            }
        ) {
            return StageArtifactConfirmationResult.Failure(STAGE_RESULT_STAGE_NOT_FOUND)
        }

        val currentDraft = snapshot.resources.drafts.singleOrNull {
            it.issueId == command.issueId && it.stageId == command.stageId
        }
        val draftRevision = snapshot.resources.draftRevisions.singleOrNull {
            it.id == command.draftRevisionId &&
                it.issueId == command.issueId &&
                it.stageId == command.stageId
        }
        if (
            currentDraft == null || draftRevision == null ||
            draftRevision.draftIdSnapshot != currentDraft.id ||
            draftRevision.revisionNumber != currentDraft.revisionNumber ||
            draftRevision.contentSnapshot != currentDraft.content
        ) {
            return StageArtifactConfirmationResult.Failure(DRAFT_REVISION_NOT_SAVED)
        }

        val revisionFailure = validateRevisionTarget(command, snapshot.resources.artifacts)
        if (revisionFailure != null) {
            return StageArtifactConfirmationResult.Failure(revisionFailure)
        }

        val runsById = snapshot.core.runs.associateBy { it.id }
        val selectableMessageIds = selectableStageOutputs(
            issueId = command.issueId,
            stageId = command.stageId,
            messages = snapshot.core.messages,
            runsById = runsById,
        ).mapTo(mutableSetOf()) { it.id }
        if (command.selectedMessageIds.any { it !in selectableMessageIds }) {
            return StageArtifactConfirmationResult.Failure(ARTIFACT_SOURCE_MISMATCH)
        }

        val selected = StageMessageSelectionPolicy.select(
            issueId = command.issueId,
            stageId = command.stageId,
            messages = snapshot.core.messages,
            selectedMessageIds = command.selectedMessageIds,
        )
        if (selected is StageMessageSelectionResult.Rejected) {
            return StageArtifactConfirmationResult.Failure(ARTIFACT_SOURCE_MISMATCH)
        }
        selected as StageMessageSelectionResult.Selected
        val messageById = snapshot.core.messages.associateBy { it.id }
        val selectedMessages = selected.messages.map { requireNotNull(messageById[it.messageId]) }
        val sourceResult = ArtifactSourceBuilder.build(
            issueId = command.issueId,
            stageId = command.stageId,
            draftRevision = draftRevision,
            selectedMessages = selectedMessages,
            materialUsages = snapshot.resources.materialUsages,
        )
        if (sourceResult is ArtifactSourceBuildResult.Rejected) {
            return StageArtifactConfirmationResult.Failure(ARTIFACT_SOURCE_MISMATCH)
        }
        sourceResult as ArtifactSourceBuildResult.Ready

        val artifact = ConfirmedArtifactEntity(
            id = command.artifactId,
            issueId = command.issueId,
            stageId = command.stageId,
            title = command.title.trim(),
            content = draftRevision.contentSnapshot,
            artifactType = command.artifactType.storageValue,
            contentFormat = ARTIFACT_CONTENT_FORMAT_MARKDOWN,
            confirmedAt = command.confirmedAt,
            revisionOfArtifactId = command.revisionOfArtifactId,
            createdAt = command.confirmedAt,
            updatedAt = command.confirmedAt,
        )
        val plan = sourceResult.plan
        val sources = ArtifactSources(
            messages = plan.messageIds.map {
                ArtifactMessageSourceEntity(
                    artifactId = artifact.id,
                    issueId = artifact.issueId,
                    messageId = it,
                    createdAt = command.confirmedAt,
                )
            },
            runs = plan.runIds.map {
                ArtifactRunSourceEntity(
                    artifactId = artifact.id,
                    issueId = artifact.issueId,
                    runId = it,
                    createdAt = command.confirmedAt,
                )
            },
            draftRevisions = plan.draftRevisionIds.map {
                ArtifactDraftSourceEntity(
                    artifactId = artifact.id,
                    issueId = artifact.issueId,
                    draftRevisionId = it,
                    createdAt = command.confirmedAt,
                )
            },
            materials = plan.materialUsageSnapshotIds.map {
                ArtifactMaterialSourceEntity(
                    artifactId = artifact.id,
                    issueId = artifact.issueId,
                    materialUsageSnapshotId = it,
                    createdAt = command.confirmedAt,
                )
            },
        )

        return when (
            val confirmed = repository.confirmArtifact(
                ConfirmArtifactCommand(artifact = artifact, sources = sources),
            )
        ) {
            is RepositoryResult.Success -> StageArtifactConfirmationResult.Confirmed(
                confirmed.value,
            )
            is RepositoryResult.Failure -> StageArtifactConfirmationResult.Failure(
                confirmationErrorCode(confirmed.error),
            )
        }
    }

    private fun selectableStageOutputs(
        issueId: String,
        stageId: String,
        messages: List<Message>,
        runsById: Map<String, ExecutionRunEntity>,
    ): List<Message> {
        return messages.asSequence()
            .filter { message ->
                message.issueId == issueId &&
                    message.stageId == stageId &&
                    !message.isPending &&
                    message.executionRunId != null &&
                    message.participantSnapshotId != null
            }
            .filter { message ->
                val run = runsById[message.executionRunId]
                run != null && run.issueId == issueId && run.stageId == stageId
            }
            .sortedWith(compareBy<Message> { it.timestamp }.thenBy { it.id })
            .toList()
    }

    private fun validateRevisionTarget(
        command: ConfirmStageArtifactCommand,
        artifacts: List<ConfirmedArtifactEntity>,
    ): String? {
        val parentId = command.revisionOfArtifactId ?: return null
        if (parentId == command.artifactId) return ARTIFACT_REVISION_INVALID
        val parent = artifacts.firstOrNull { it.id == parentId }
            ?: return ARTIFACT_REVISION_INVALID
        if (parent.issueId != command.issueId || parent.stageId != command.stageId) {
            return ARTIFACT_REVISION_INVALID
        }
        if (artifacts.any { it.revisionOfArtifactId == parentId && it.id != command.artifactId }) {
            return ARTIFACT_REVISION_FORK
        }
        return null
    }

    private fun confirmationErrorCode(error: RepositoryError): String = when (error) {
        is RepositoryError.IdempotencyConflict -> ARTIFACT_CONFIRMATION_CONFLICT
        is RepositoryError.ConstraintViolation -> ARTIFACT_SOURCE_MISMATCH
        else -> ARTIFACT_CONFIRMATION_FAILED
    }

    companion object {
        const val STAGE_RESULT_LOAD_FAILED = "stage_result_load_failed"
        const val STAGE_RESULT_STAGE_NOT_FOUND = "stage_result_stage_not_found"
        const val DRAFT_SAVE_FAILED = "draft_save_failed"
        const val DRAFT_ABANDON_FAILED = "draft_abandon_failed"
        const val DRAFT_REVISION_NOT_SAVED = "draft_revision_not_saved"
        const val ARTIFACT_SOURCE_MISMATCH = "artifact_source_mismatch"
        const val ARTIFACT_CONFIRMATION_FAILED = "artifact_confirmation_failed"
        const val ARTIFACT_CONFIRMATION_CONFLICT = "artifact_confirmation_conflict"
        const val ARTIFACT_REVISION_INVALID = "artifact_revision_invalid"
        const val ARTIFACT_REVISION_FORK = "artifact_revision_fork"
    }
}
