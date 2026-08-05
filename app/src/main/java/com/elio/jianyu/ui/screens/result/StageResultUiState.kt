package com.elio.jianyu.ui.screens.result

import com.elio.jianyu.result.ArtifactType
import com.elio.jianyu.result.StageResultWorkspace

sealed interface StageDraftSaveStatus {
    data object Idle : StageDraftSaveStatus
    data object Dirty : StageDraftSaveStatus
    data object Saving : StageDraftSaveStatus

    data class Saved(
        val revision: Int,
        val savedAt: Long,
    ) : StageDraftSaveStatus

    data class Failure(
        val errorCode: String,
    ) : StageDraftSaveStatus

    data object Conflict : StageDraftSaveStatus
}

sealed interface StageArtifactConfirmationStatus {
    data object Idle : StageArtifactConfirmationStatus
    data object Confirming : StageArtifactConfirmationStatus

    data class Confirmed(
        val artifactId: String,
    ) : StageArtifactConfirmationStatus

    data class Failure(
        val errorCode: String,
    ) : StageArtifactConfirmationStatus
}

sealed interface StageResultUiState {
    data object Loading : StageResultUiState

    data class Failure(
        val errorCode: String,
    ) : StageResultUiState

    data class Content(
        val workspace: StageResultWorkspace,
        val draftId: String?,
        val editorContent: String,
        val persistedContent: String,
        val currentRevision: Int,
        val lastSavedAt: Long?,
        val saveStatus: StageDraftSaveStatus,
        val selectedMessageIds: Set<Long>,
        val artifactTitle: String,
        val artifactType: ArtifactType,
        val revisionOfArtifactId: String?,
        val showAbandonConfirmation: Boolean,
        val showArtifactConfirmation: Boolean,
        val artifactStatus: StageArtifactConfirmationStatus,
    ) : StageResultUiState {
        val hasDraft: Boolean
            get() = draftId != null

        val canConfirmArtifact: Boolean
            get() = hasDraft &&
                editorContent.isNotBlank() &&
                editorContent == persistedContent &&
                saveStatus is StageDraftSaveStatus.Saved
    }
}

data class StageResultCallbacks(
    val onRetry: () -> Unit = {},
    val onToggleMessage: (Long) -> Unit = {},
    val onCreateGenericDraft: () -> Unit = {},
    val onCreateDraftFromMessages: () -> Unit = {},
    val onContentChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onReloadConflict: () -> Unit = {},
    val onRequestAbandon: () -> Unit = {},
    val onDismissAbandon: () -> Unit = {},
    val onConfirmAbandon: () -> Unit = {},
    val onRequestArtifactConfirmation: () -> Unit = {},
    val onDismissArtifactConfirmation: () -> Unit = {},
    val onArtifactTitleChange: (String) -> Unit = {},
    val onArtifactTypeChange: (ArtifactType) -> Unit = {},
    val onConfirmArtifact: () -> Unit = {},
    val onCreateRevision: (String) -> Unit = {},
    val onOpenArtifact: (String) -> Unit = {},
) {
    companion object {
        val Empty = StageResultCallbacks()
    }
}
