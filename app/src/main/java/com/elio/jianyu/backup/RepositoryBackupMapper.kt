package com.elio.jianyu.backup

import com.elio.jianyu.JianyuAppRuntime
import com.elio.jianyu.audio.assets.AudioFileResolution
import com.elio.jianyu.data.AudioFileState
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.listArtifactSourcesForIssue
import com.elio.jianyu.data.listStageAdvancements
import com.elio.jianyu.data.MaterialFilter
import com.elio.jianyu.data.PersonalContextFilter
import java.io.File

/** Explicit whitelist mapper; Room table names never become format identifiers. */
object RepositoryBackupMapper {
    suspend fun collect(runtime: JianyuAppRuntime): PortableBackupInput {
        val repository = runtime.repository
        val entities = mutableListOf<BackupEntityRecord>()
        val blobs = mutableListOf<BackupBlobRecord>()
        val navigation = requireSuccess(repository.listIssueNavigation(setOf(
            IssueLifecycleState.ACTIVE,
            IssueLifecycleState.ARCHIVED,
            IssueLifecycleState.TRASHED,
        )))
        navigation.sortedBy { it.issue.id }.forEach { item ->
            val snapshot = requireSuccess(repository.recoverIssue(item.issue.id))
            val lifecycle = snapshot.core.lifecycle
            if (lifecycle.purgeRequestedAt != null) throw BackupException(BackupErrorCode.PURGE_IN_PROGRESS)
            if (snapshot.core.activeOrRecoverableRuns.isNotEmpty() || snapshot.core.pendingMessages.isNotEmpty()) {
                throw BackupException(BackupErrorCode.ACTIVE_WORK_IN_PROGRESS)
            }
            entities += record("issue-${snapshot.core.issue.id}", "issue", fields(
                snapshot.core.issue.id, snapshot.core.issue.title, snapshot.core.issue.createdAt,
                snapshot.core.issue.updatedAt, snapshot.core.issue.defaultThinkingPolicy.storageValue,
                snapshot.core.issue.legacyChatSessionId,
            ))
            snapshot.core.stages.sortedBy { it.sequenceIndex }.forEach { stage ->
                entities += record("stage-${stage.id}", "stage", fields(
                    stage.id, stage.issueId, stage.sequenceIndex, stage.title, stage.objective,
                    stage.createdAt, stage.updatedAt,
                ))
            }
            snapshot.core.runs.sortedBy { it.createdAt }.forEach { run ->
                if (run.status.storageValue in setOf("running", "partial_success", "retryable")) {
                    throw BackupException(BackupErrorCode.ACTIVE_WORK_IN_PROGRESS)
                }
                entities += record("execution-run-${run.id}", "execution_run", fields(
                    run.id, run.issueId, run.stageId, run.triggerMessageId, run.idempotencyKey,
                    run.status.storageValue, run.retryOfRunId, run.createdAt, run.updatedAt,
                    run.startedAt, run.finishedAt, run.stoppedAt, run.failureCode,
                    run.runKind.storageValue, run.parentRunId, run.discussionId,
                    run.historyScope.storageValue, run.actualModelId, run.actualThinkingLevel.storageValue,
                    run.thinkingLevelSource.storageValue,
                ))
            }
            snapshot.core.participants.sortedWith(compareBy({ it.runId }, { it.position })).forEach { participant ->
                entities += record("participant-${participant.id}", "participant_snapshot", fields(
                    participant.id, participant.runId, participant.sourceType, participant.sourceId,
                    participant.displayName, participant.avatar, participant.skillAssetPath,
                    participant.systemPrompt, participant.configurationJson,
                    participant.defaultResponsibility, participant.position, participant.createdAt,
                ))
            }
            snapshot.core.messages.filterNot { it.isPending }.sortedWith(compareBy({ it.timestamp }, { it.id })).forEach { message ->
                entities += record("message-${message.id}", "message", fields(
                    message.id, message.chatId, message.issueId, message.stageId,
                    message.executionRunId, message.participantSnapshotId, message.senderId,
                    message.senderName, message.avatar, message.text, message.timestamp,
                    message.roundIndex,
                ))
            }
            snapshot.resources.drafts.forEach { draft ->
                entities += record("draft-${draft.id}", "stage_summary_draft", fields(
                    draft.id, draft.issueId, draft.stageId, draft.content, draft.revisionNumber,
                    draft.createdAt, draft.updatedAt,
                ))
            }
            snapshot.resources.draftRevisions.forEach { revision ->
                entities += record("draft-revision-${revision.id}", "stage_summary_draft_revision", fields(
                    revision.id, revision.issueId, revision.stageId, revision.draftIdSnapshot,
                    revision.revisionNumber, revision.contentSnapshot, revision.createdAt,
                ))
            }
            snapshot.resources.artifacts.forEach { artifact ->
                entities += record("artifact-${artifact.id}", "confirmed_artifact", fields(
                    artifact.id, artifact.issueId, artifact.stageId, artifact.title, artifact.content,
                    artifact.artifactType, artifact.contentFormat, artifact.confirmedAt,
                    artifact.revisionOfArtifactId, artifact.createdAt, artifact.updatedAt,
                ))
            }
            snapshot.resources.materialUsages.forEach { usage ->
                entities += record("material-usage-${usage.id}", "material_usage", fields(
                    usage.id, usage.issueId, usage.stageId, usage.runId, usage.materialReferenceId,
                    usage.titleSnapshot, usage.contentSnapshot, usage.contentHash,
                    usage.contentState.storageValue, usage.userConfirmedAt, usage.createdAt,
                    usage.networkAllowed, usage.sensitive,
                ))
            }
            snapshot.resources.personalContextUsages.forEach { usage ->
                entities += record("personal-usage-${usage.id}", "personal_context_usage", fields(
                    usage.id, usage.issueId, usage.stageId, usage.runId, usage.personalContextEntryId,
                    usage.titleSnapshot, usage.contentSnapshot, usage.contentHash,
                    usage.contentState.storageValue, usage.userConfirmedAt, usage.createdAt,
                    usage.networkAllowed, usage.sensitive,
                ))
            }
            snapshot.resources.audioAssets.forEach { audio ->
                when (audio.fileState) {
                    AudioFileState.PENDING -> throw BackupException(BackupErrorCode.ACTIVE_WORK_IN_PROGRESS)
                    AudioFileState.AVAILABLE -> {
                        if (audio.deletedAt != null || audio.purgeRequestedAt != null) throw BackupException(BackupErrorCode.SOURCE_CHANGED)
                        val resolution = runtime.audioRuntime.fileStore.resolve(audio.storagePath)
                        val file = (resolution as? AudioFileResolution.Available)?.file
                            ?: throw BackupException(BackupErrorCode.SOURCE_CHANGED)
                        if (!file.isFile || file.length() != audio.sizeBytes) throw BackupException(BackupErrorCode.SOURCE_CHANGED)
                        entities += record("audio-${audio.id}", "audio_asset", fields(
                            audio.id, audio.issueId, audio.stageId, audio.sourceMessageId,
                            audio.sourceArtifactId, audio.mimeType, audio.format, audio.sizeBytes,
                            audio.fileState.storageValue, audio.createdAt, audio.updatedAt,
                        ))
                        blobs += BackupBlobRecord("audio-${audio.id}", "audio_asset", audio.mimeType, file.readBytes())
                    }
                    else -> throw BackupException(BackupErrorCode.SOURCE_CHANGED)
                }
            }
            requireSuccess(repository.listArtifactSourcesForIssue(item.issue.id)).forEach { source ->
                source.messages.forEach { entities += record("artifact-msg-${source.artifactId}-${it.messageId}", "artifact_message_source", fields(it.artifactId, it.issueId, it.messageId, it.createdAt)) }
                source.runs.forEach { entities += record("artifact-run-${source.artifactId}-${it.runId}", "artifact_run_source", fields(it.artifactId, it.issueId, it.runId, it.createdAt)) }
                source.draftRevisions.forEach { entities += record("artifact-draft-${source.artifactId}-${it.draftRevisionId}", "artifact_draft_source", fields(it.artifactId, it.issueId, it.draftRevisionId, it.createdAt)) }
                source.materials.forEach { entities += record("artifact-material-${source.artifactId}-${it.materialUsageSnapshotId}", "artifact_material_source", fields(it.artifactId, it.issueId, it.materialUsageSnapshotId, it.createdAt)) }
            }
            val advancementResult = try {
                repository.listStageAdvancements(item.issue.id)
            } catch (_: Throwable) {
                null
            }
            advancementResult?.let { result ->
                when (result) {
                    is RepositoryResult.Success -> result.value.forEach { advancement ->
                        entities += record("stage-advancement-${advancement.stage.id}", "stage_advancement", fields(
                            advancement.stage.id, advancement.advancement.issueId, advancement.advancement.sourceStageId,
                            advancement.advancement.operationId, advancement.advancement.payloadHash,
                            advancement.advancement.realitySupport, advancement.advancement.thinkingExpansion,
                            advancement.advancement.objective, advancement.advancement.expectedOutput,
                            advancement.advancement.confirmedAt, advancement.advancement.createdAt,
                        ))
                    }
                    is RepositoryResult.Failure -> Unit
                }
            }
        }

        requireSuccess(repository.listMaterials(MaterialFilter())).filter { it.lifecycle != ContextSourceLifecycle.PURGED }.forEach { material ->
            if (material.lifecycle == ContextSourceLifecycle.PURGE_REQUESTED) throw BackupException(BackupErrorCode.PURGE_IN_PROGRESS)
            val (locator, unavailable) = safeLocator(material.sourceType, material.sourceLocator)
            entities += record("material-${material.id}", "material_reference", fields(
                material.id, material.issueId, material.stageId, material.title, material.sourceType,
                locator, unavailable, material.content, material.contentHash, material.sourcePublishedAt,
                material.sourceCapturedAt, material.sensitive, material.lifecycle.storageValue,
                material.createdAt, material.updatedAt,
            ))
        }
        requireSuccess(repository.listPersonalContexts(PersonalContextFilter())).filter { it.lifecycle != ContextSourceLifecycle.PURGED }.forEach { context ->
            if (context.lifecycle == ContextSourceLifecycle.PURGE_REQUESTED) throw BackupException(BackupErrorCode.PURGE_IN_PROGRESS)
            entities += record("personal-${context.id}", "personal_context_entry", fields(
                context.id, context.title, context.content, context.contentHash, context.sensitive,
                context.lifecycle.storageValue, context.createdAt, context.updatedAt,
            ))
        }
        requireSuccess(repository.listOfficialSkillCombinations()).filter { it.combination.deletedAt == null }.forEach { combination ->
            entities += record("skill-combination-${combination.combination.id}", "official_skill_combination", fields(
                combination.combination.id, combination.combination.name, combination.combination.isEnabled,
                combination.combination.createdAt, combination.combination.updatedAt,
            ))
            combination.members.forEach { member ->
                entities += record("skill-member-${member.combinationId}-${member.officialSkillId}", "official_skill_combination_member", fields(
                    member.combinationId, member.officialSkillId, member.position,
                    member.defaultResponsibility, member.createdAt,
                ))
            }
        }
        return PortableBackupInput(
            manifest = BackupManifest(
                formatId = BackupProtocol.portableFormatId,
                createdAt = System.currentTimeMillis(),
                appVersionName = com.elio.jianyu.BuildConfig.VERSION_NAME,
                appVersionCode = com.elio.jianyu.BuildConfig.VERSION_CODE.toLong(),
                sourceRoomVersion = 14L,
                logicalEntryCount = entities.size.toLong(),
                blobCount = blobs.size.toLong(),
                backupScope = listOf("issues", "resources", "personal_context", "audio_available", "skill_combinations"),
            ),
            entities = entities,
            blobs = blobs,
        )
    }

    private fun record(id: String, type: String, payload: BackupCborValue): BackupEntityRecord =
        BackupEntityRecord(id, type, payload)

    private fun fields(vararg values: Any?): BackupCborValue = BackupCborValue.MapValue(
        values.mapIndexed { index, value -> index.toLong() + 1L to scalar(value) }.toMap(),
    )

    private fun scalar(value: Any?): BackupCborValue = when (value) {
        null -> BackupCborValue.Text("")
        is BackupCborValue -> value
        is Boolean -> BackupCborValue.UInt(if (value) 1L else 0L)
        is Byte -> BackupCborValue.UInt(value.toLong().coerceAtLeast(0L))
        is Short -> BackupCborValue.UInt(value.toLong().coerceAtLeast(0L))
        is Int -> BackupCborValue.UInt(value.toLong().coerceAtLeast(0L))
        is Long -> BackupCborValue.UInt(value.coerceAtLeast(0L))
        is String -> BackupCborValue.Text(value)
        else -> BackupCborValue.Text(value.toString())
    }

    private fun safeLocator(sourceType: String, locator: String?): Pair<String, Boolean> {
        if (locator.isNullOrBlank()) return "" to false
        val normalized = sourceType.lowercase()
        return if ((normalized == "http" || normalized == "https") && locator.startsWith("$normalized://")) {
            locator to false
        } else {
            "" to true
        }
    }

    private fun <T> requireSuccess(result: RepositoryResult<T>): T = when (result) {
        is RepositoryResult.Success -> result.value
        is RepositoryResult.Failure -> throw BackupException(BackupErrorCode.VERIFICATION_FAILED, result.error.asThrowable())
    }

    private fun RepositoryError.asThrowable(): Throwable = IllegalStateException(toString())
}
