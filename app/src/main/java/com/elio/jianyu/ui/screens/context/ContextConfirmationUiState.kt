package com.elio.jianyu.ui.screens.context

import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.data.ContextUsageSnapshot
import com.elio.jianyu.data.MAX_EXECUTION_CONTEXT_CHARACTERS

data class ContextCandidateUi(
    val sourceType: ContextSourceType,
    val sourceId: String,
    val title: String,
    val sourceKind: String,
    val sourceLocator: String?,
    val sourcePublishedAt: Long?,
    val sourceCapturedAt: Long?,
    val originalContent: String,
    val selectedContent: String,
    val sourceHash: String,
    val sourceUpdatedAt: Long,
    val sensitive: Boolean,
    val selected: Boolean = false,
    val networkAllowed: Boolean = false,
    val sensitiveConfirmed: Boolean = false,
) {
    val characterCount: Int
        get() = selectedContent.length
}

data class ContextConfirmationUiState(
    val visible: Boolean = false,
    val retryMode: Boolean = false,
    val runId: String,
    val issueId: String,
    val stageId: String,
    val currentUserInput: String,
    val baseContextCharacters: Int,
    val candidates: List<ContextCandidateUi> = emptyList(),
    val previousUsage: List<ContextUsageSnapshot> = emptyList(),
    val errorMessage: String? = null,
    val confirmedForStart: Boolean = false,
) {
    val selectedItems: List<ContextCandidateUi>
        get() = candidates.filter(ContextCandidateUi::selected)

    val selectedCharacters: Int
        get() = selectedItems.sumOf(ContextCandidateUi::characterCount)

    val totalCharacters: Int
        get() = baseContextCharacters + selectedCharacters

    val remainingCharacters: Int
        get() = MAX_EXECUTION_CONTEXT_CHARACTERS - totalCharacters

    val tooLarge: Boolean
        get() = totalCharacters > MAX_EXECUTION_CONTEXT_CHARACTERS

    val networkPermissionMissing: Boolean
        get() = selectedItems.any { !it.networkAllowed }

    val sensitiveConfirmationMissing: Boolean
        get() = selectedItems.any { it.sensitive && !it.sensitiveConfirmed }
}
