package com.elio.jianyu.result

import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.StageSummaryDraftEntity

object StageDraftEditorPolicy {
    fun plan(
        current: StageSummaryDraftEntity?,
        editedContent: String,
    ): StageDraftSavePlan {
        if (current?.content == editedContent) {
            return StageDraftSavePlan.Unchanged
        }
        return StageDraftSavePlan.Persist(
            revisionNumber = (current?.revisionNumber ?: 0) + 1,
        )
    }

    fun mapFailure(error: RepositoryError): StageDraftSaveFailure {
        return if (
            error is RepositoryError.InvalidState &&
            error.operation == "save_stage_draft" &&
            error.stateCode == "revision_not_contiguous"
        ) {
            StageDraftSaveFailure.REVISION_CONFLICT
        } else {
            StageDraftSaveFailure.STORAGE_FAILURE
        }
    }
}
