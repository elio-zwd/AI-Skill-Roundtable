package com.elio.jianyu.ui.screens.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.result.ArtifactRevisionResolver
import com.elio.jianyu.result.ArtifactType
import com.elio.jianyu.result.ConfirmStageArtifactCommand
import com.elio.jianyu.result.GenericStageDraftBuilder
import com.elio.jianyu.result.SaveStageResultDraftCommand
import com.elio.jianyu.result.StageArtifactConfirmationResult
import com.elio.jianyu.result.StageDraftAbandonResult
import com.elio.jianyu.result.StageDraftWriteResult
import com.elio.jianyu.result.StageMessageSelectionPolicy
import com.elio.jianyu.result.StageMessageSelectionResult
import com.elio.jianyu.result.StageResultLoadResult
import com.elio.jianyu.result.StageResultService
import com.elio.jianyu.result.StageResultWorkspace
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StageResultViewModel internal constructor(
    private val service: StageResultService,
    private val issueId: String,
    private val stageId: String,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: (String) -> String = { prefix -> "$prefix-${UUID.randomUUID()}" },
) : ViewModel() {
    private val _state = MutableStateFlow<StageResultUiState>(StageResultUiState.Loading)
    val state: StateFlow<StageResultUiState> = _state.asStateFlow()
    private var autosaveJob: Job? = null
    private val saveGate = StageDraftSaveGate()
    private val artifactConfirmationGate = StageArtifactConfirmationGate()

    init {
        load()
    }

    fun load() {
        autosaveJob?.cancel()
        viewModelScope.launch {
            _state.value = StageResultUiState.Loading
            _state.value = when (val loaded = service.load(issueId, stageId)) {
                is StageResultLoadResult.Ready -> loaded.workspace.toUiState()
                is StageResultLoadResult.Failure -> StageResultUiState.Failure(loaded.errorCode)
            }
        }
    }

    fun toggleMessage(messageId: Long) {
        updateContent { content ->
            if (content.workspace.selectableMessages.none { it.id == messageId }) {
                content
            } else {
                content.copy(
                    selectedMessageIds = if (messageId in content.selectedMessageIds) {
                        content.selectedMessageIds - messageId
                    } else {
                        content.selectedMessageIds + messageId
                    },
                )
            }
        }
    }

    fun createGenericDraft() {
        createDraftFromSeed(GenericStageDraftBuilder.build())
    }

    fun createDraftFromSelectedMessages() {
        val content = currentContent() ?: return
        val selected = StageMessageSelectionPolicy.select(
            issueId = issueId,
            stageId = stageId,
            messages = content.workspace.selectableMessages,
            selectedMessageIds = content.selectedMessageIds.toList(),
        )
        if (selected is StageMessageSelectionResult.Rejected) {
            updateContent {
                it.copy(saveStatus = StageDraftSaveStatus.Failure("message_selection_invalid"))
            }
            return
        }
        selected as StageMessageSelectionResult.Selected
        createDraftFromSeed(GenericStageDraftBuilder.build(selected.messages))
    }

    fun updateContentText(value: String) {
        updateContent {
            it.copy(
                editorContent = value,
                saveStatus = StageDraftSaveStatus.Dirty,
                artifactStatus = StageArtifactConfirmationStatus.Idle,
            )
        }
        scheduleAutosave()
    }

    fun saveNow() {
        autosaveJob?.cancel()
        viewModelScope.launch { persistCurrentDraft() }
    }

    fun reloadConflict() {
        load()
    }

    fun requestAbandon() {
        updateContent { it.copy(showAbandonConfirmation = true) }
    }

    fun dismissAbandon() {
        updateContent { it.copy(showAbandonConfirmation = false) }
    }

    fun confirmAbandon() {
        autosaveJob?.cancel()
        viewModelScope.launch {
            when (service.abandonDraft(issueId, stageId)) {
                StageDraftAbandonResult.Abandoned -> updateContent { current ->
                    current.copy(
                        workspace = current.workspace.copy(draft = null),
                        draftId = null,
                        editorContent = "",
                        persistedContent = "",
                        currentRevision = 0,
                        lastSavedAt = null,
                        saveStatus = StageDraftSaveStatus.Idle,
                        showAbandonConfirmation = false,
                        showArtifactConfirmation = false,
                        revisionOfArtifactId = null,
                    )
                }
                is StageDraftAbandonResult.Failure -> updateContent {
                    it.copy(
                        showAbandonConfirmation = false,
                        saveStatus = StageDraftSaveStatus.Failure("draft_abandon_failed"),
                    )
                }
            }
        }
    }

    fun requestArtifactConfirmation() {
        autosaveJob?.cancel()
        viewModelScope.launch {
            if (!persistCurrentDraft()) return@launch
            updateContent { current ->
                current.copy(
                    showArtifactConfirmation = true,
                    artifactTitle = current.artifactTitle.ifBlank {
                        current.revisionOfArtifactId?.let { revisionId ->
                            current.workspace.artifacts.firstOrNull { it.id == revisionId }?.title
                        }.orEmpty().ifBlank { "阶段总结" }
                    },
                    artifactStatus = StageArtifactConfirmationStatus.Idle,
                )
            }
        }
    }

    fun dismissArtifactConfirmation() {
        updateContent {
            it.copy(
                showArtifactConfirmation = false,
                artifactStatus = StageArtifactConfirmationStatus.Idle,
            )
        }
    }

    fun updateArtifactTitle(value: String) {
        updateContent { it.copy(artifactTitle = value) }
    }

    fun updateArtifactType(type: ArtifactType) {
        updateContent { it.copy(artifactType = type) }
    }

    fun confirmArtifact() {
        val content = currentContent() ?: return
        if (!content.canConfirmArtifact || content.artifactTitle.isBlank()) return
        val revision = content.workspace.draftRevisions.singleOrNull {
            it.draftIdSnapshot == content.draftId && it.revisionNumber == content.currentRevision
        } ?: run {
            updateContent {
                it.copy(
                    artifactStatus = StageArtifactConfirmationStatus.Failure(
                        "draft_revision_not_saved",
                    ),
                )
            }
            return
        }
        if (!artifactConfirmationGate.tryStart()) return
        val artifactId = idFactory("artifact")
        updateContent {
            it.copy(artifactStatus = StageArtifactConfirmationStatus.Confirming)
        }
        viewModelScope.launch {
            try {
                when (
                    val result = service.confirmArtifact(
                        ConfirmStageArtifactCommand(
                            artifactId = artifactId,
                            issueId = issueId,
                            stageId = stageId,
                            draftRevisionId = revision.id,
                            title = content.artifactTitle,
                            artifactType = content.artifactType,
                            selectedMessageIds = content.selectedMessageIds.toList(),
                            revisionOfArtifactId = content.revisionOfArtifactId,
                            confirmedAt = nextTimestamp(clock()),
                        ),
                    )
                ) {
                    is StageArtifactConfirmationResult.Confirmed -> updateContent { current ->
                        val artifacts = (current.workspace.artifacts + result.artifact)
                            .distinctBy { it.id }
                        current.copy(
                            workspace = current.workspace.copy(
                                artifacts = artifacts,
                                artifactRevisionResolution = ArtifactRevisionResolver.resolve(artifacts),
                            ),
                            showArtifactConfirmation = false,
                            artifactStatus = StageArtifactConfirmationStatus.Confirmed(result.artifact.id),
                            revisionOfArtifactId = null,
                        )
                    }
                    is StageArtifactConfirmationResult.Failure -> updateContent {
                        it.copy(
                            artifactStatus = StageArtifactConfirmationStatus.Failure(result.errorCode),
                        )
                    }
                }
            } finally {
                artifactConfirmationGate.finish()
            }
        }
    }

    fun createRevision(artifactId: String) {
        val content = currentContent() ?: return
        if (content.editorContent != content.persistedContent) {
            updateContent {
                it.copy(saveStatus = StageDraftSaveStatus.Failure("draft_has_unsaved_changes"))
            }
            return
        }
        val artifact = content.workspace.artifacts.firstOrNull { it.id == artifactId } ?: return
        val draftId = content.draftId ?: idFactory("draft")
        updateContent {
            it.copy(
                draftId = draftId,
                editorContent = artifact.content,
                persistedContent = content.persistedContent,
                currentRevision = content.currentRevision,
                saveStatus = StageDraftSaveStatus.Dirty,
                artifactTitle = artifact.title,
                artifactType = ArtifactType.fromStorageValue(artifact.artifactType)
                    ?: ArtifactType.DEFAULT,
                revisionOfArtifactId = artifact.id,
                artifactStatus = StageArtifactConfirmationStatus.Idle,
            )
        }
        scheduleAutosave()
    }

    private fun createDraftFromSeed(seed: com.elio.jianyu.result.StageDraftSeed) {
        val content = currentContent() ?: return
        if (content.hasDraft) return
        updateContent {
            it.copy(
                draftId = idFactory("draft"),
                editorContent = seed.content,
                persistedContent = "",
                currentRevision = 0,
                lastSavedAt = null,
                saveStatus = StageDraftSaveStatus.Dirty,
                artifactType = seed.defaultArtifactType,
                artifactStatus = StageArtifactConfirmationStatus.Idle,
            )
        }
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        val content = currentContent() ?: return
        if (
            !StageDraftAutosavePolicy.shouldSchedule(
                draftId = content.draftId,
                editorContent = content.editorContent,
                persistedContent = content.persistedContent,
                saveStatus = content.saveStatus,
            )
        ) {
            return
        }
        autosaveJob = viewModelScope.launch {
            delay(StageDraftAutosavePolicy.DEBOUNCE_MILLIS)
            persistCurrentDraft()
        }
    }

    private suspend fun persistCurrentDraft(): Boolean = saveGate.run {
        val captured = currentContent() ?: return@run false
        val draftId = captured.draftId ?: return@run false
        if (
            captured.editorContent == captured.persistedContent &&
            captured.currentRevision > 0
        ) {
            updateContent {
                it.copy(
                    saveStatus = StageDraftSaveStatus.Saved(
                        revision = it.currentRevision,
                        savedAt = it.lastSavedAt ?: nextTimestamp(clock()),
                    ),
                )
            }
            return@run true
        }
        val savedAt = nextTimestamp(clock())
        val contentToSave = captured.editorContent
        updateContent { it.copy(saveStatus = StageDraftSaveStatus.Saving) }
        when (
            val result = service.saveDraft(
                SaveStageResultDraftCommand(
                    issueId = issueId,
                    stageId = stageId,
                    draftId = draftId,
                    revisionId = idFactory("draft-revision"),
                    expectedCurrentRevision = captured.currentRevision,
                    content = contentToSave,
                    savedAt = savedAt,
                ),
            )
        ) {
            is StageDraftWriteResult.Saved -> {
                var requiresAnotherSave = false
                updateContent { latest ->
                    val revisions = (latest.workspace.draftRevisions + result.revision)
                        .distinctBy { it.id }
                        .sortedBy { it.revisionNumber }
                    requiresAnotherSave = latest.editorContent != result.draft.content
                    latest.copy(
                        workspace = latest.workspace.copy(
                            draft = result.draft,
                            draftRevisions = revisions,
                        ),
                        persistedContent = result.draft.content,
                        currentRevision = result.draft.revisionNumber,
                        lastSavedAt = result.draft.updatedAt,
                        saveStatus = if (requiresAnotherSave) {
                            StageDraftSaveStatus.Dirty
                        } else {
                            StageDraftSaveStatus.Saved(
                                result.draft.revisionNumber,
                                result.draft.updatedAt,
                            )
                        },
                    )
                }
                if (requiresAnotherSave) scheduleAutosave()
                true
            }
            is StageDraftWriteResult.Unchanged -> {
                updateContent {
                    it.copy(
                        workspace = it.workspace.copy(draft = result.draft),
                        persistedContent = result.draft.content,
                        currentRevision = result.draft.revisionNumber,
                        lastSavedAt = result.draft.updatedAt,
                        saveStatus = StageDraftSaveStatus.Saved(
                            result.draft.revisionNumber,
                            result.draft.updatedAt,
                        ),
                    )
                }
                true
            }
            StageDraftWriteResult.Conflict -> {
                updateContent { it.copy(saveStatus = StageDraftSaveStatus.Conflict) }
                false
            }
            is StageDraftWriteResult.Failure -> {
                updateContent {
                    it.copy(saveStatus = StageDraftSaveStatus.Failure(result.errorCode))
                }
                false
            }
        }
    }

    private fun currentContent(): StageResultUiState.Content? =
        _state.value as? StageResultUiState.Content

    private fun updateContent(
        transform: (StageResultUiState.Content) -> StageResultUiState.Content,
    ) {
        val current = currentContent() ?: return
        _state.value = transform(current)
    }

    private fun StageResultWorkspace.toUiState(): StageResultUiState.Content {
        val currentDraft = draft
        return StageResultUiState.Content(
            workspace = this,
            draftId = currentDraft?.id,
            editorContent = currentDraft?.content.orEmpty(),
            persistedContent = currentDraft?.content.orEmpty(),
            currentRevision = currentDraft?.revisionNumber ?: 0,
            lastSavedAt = currentDraft?.updatedAt,
            saveStatus = if (currentDraft == null) {
                StageDraftSaveStatus.Idle
            } else {
                StageDraftSaveStatus.Saved(currentDraft.revisionNumber, currentDraft.updatedAt)
            },
            selectedMessageIds = emptySet(),
            artifactTitle = "",
            artifactType = ArtifactType.DEFAULT,
            revisionOfArtifactId = null,
            showAbandonConfirmation = false,
            showArtifactConfirmation = false,
            artifactStatus = StageArtifactConfirmationStatus.Idle,
        )
    }

    private fun nextTimestamp(now: Long): Long = now.coerceAtLeast(1L)

    companion object {
        fun factory(
            repository: JianyuRepository,
            issueId: String,
            stageId: String,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(StageResultViewModel::class.java))
                return StageResultViewModel(
                    service = StageResultService(repository),
                    issueId = issueId,
                    stageId = stageId,
                ) as T
            }
        }
    }
}
