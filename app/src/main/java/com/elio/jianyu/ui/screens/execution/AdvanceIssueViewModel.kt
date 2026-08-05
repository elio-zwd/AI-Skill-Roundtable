package com.elio.jianyu.ui.screens.execution

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elio.jianyu.collaboration.CurrentStageRosterPolicy
import com.elio.jianyu.collaboration.CurrentStageRosterSource
import com.elio.jianyu.data.AdvanceIssueCommand
import com.elio.jianyu.data.CrossDiscussionStatus
import com.elio.jianyu.data.ExecutionRunStatus
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.MaterialFilter
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.StageAdvancementMeasure
import com.elio.jianyu.data.StageAdvancementSkillPlan
import com.elio.jianyu.data.advanceIssue
import com.elio.jianyu.data.getStageCollaboration
import com.elio.jianyu.data.listStageAdvancements
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdvanceIssueViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: JianyuRepository,
    private val idProvider: () -> String = { UUID.randomUUID().toString() },
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _state = MutableStateFlow<AdvanceIssueUiState>(AdvanceIssueUiState.Idle)
    val state: StateFlow<AdvanceIssueUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<AdvanceIssueEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<AdvanceIssueEvent> = _events.asSharedFlow()

    private var loadedIssueId: String? = null
    private var viewedStageId: String? = null
    private var candidates: AdvanceIssueCandidates? = null

    fun load(issueId: String?, stageId: String?) {
        if (issueId.isNullOrBlank()) {
            clearFlow()
            return
        }
        loadedIssueId = issueId
        viewedStageId = stageId
        viewModelScope.launch {
            val loaded = loadCandidates(issueId, stageId)
            when (loaded) {
                is RepositoryResult.Success -> {
                    candidates = loaded.value
                    if (_state.value is AdvanceIssueUiState.LoadingCandidates) {
                        openWithCandidates(loaded.value)
                    }
                }
                is RepositoryResult.Failure -> {
                    candidates = null
                    _state.value = AdvanceIssueUiState.StorageFailure(
                        repositoryErrorMessage(loaded.error),
                    )
                }
            }
        }
    }

    fun open() {
        val issueId = loadedIssueId ?: return
        val available = candidates
        if (available != null) {
            openWithCandidates(available)
            return
        }
        _state.value = AdvanceIssueUiState.LoadingCandidates
        load(issueId, viewedStageId)
    }

    fun close() {
        _state.value = AdvanceIssueUiState.Idle
    }

    fun toggleDirection(direction: AdvanceIssueDirection) {
        updateDraft { draft ->
            when (direction) {
                AdvanceIssueDirection.REALITY_SUPPORT -> {
                    val enabled = !draft.realitySupport
                    draft.copy(
                        realitySupport = enabled,
                        measures = if (enabled) draft.measures else {
                            draft.measures - REALITY_MEASURES
                        },
                    )
                }
                AdvanceIssueDirection.THINKING_EXPANSION -> {
                    val enabled = !draft.thinkingExpansion
                    draft.copy(
                        thinkingExpansion = enabled,
                        measures = if (enabled) draft.measures else {
                            draft.measures - THINKING_MEASURES
                        },
                    )
                }
            }
        }
    }

    fun continueFromDirection() {
        val current = _state.value as? AdvanceIssueUiState.DirectionStep ?: return
        if (!current.draft.hasDirection) return
        val withDefaultMeasure = if (current.draft.measures.isEmpty()) {
            current.draft.edited { draft ->
                draft.copy(
                    measures = setOf(
                        if (draft.realitySupport) {
                            StageAdvancementMeasure.CLARIFY_NEXT_STEP
                        } else {
                            StageAdvancementMeasure.FIND_MISSING_PERSPECTIVES
                        },
                    ),
                )
            }
        } else {
            current.draft
        }
        persist(withDefaultMeasure)
        _state.value = AdvanceIssueUiState.MeasureStep(current.candidates, withDefaultMeasure)
    }

    fun backToDirection() {
        val pair = stateWithDraft() ?: return
        _state.value = AdvanceIssueUiState.DirectionStep(pair.first, pair.second)
    }

    fun toggleMeasure(measure: StageAdvancementMeasure) {
        updateDraft { draft ->
            val selected = measure in draft.measures
            draft.copy(measures = if (selected) draft.measures - measure else draft.measures + measure)
        }
    }

    fun updateObjective(value: String) {
        updateDraft { it.copy(objective = value) }
    }

    fun updateExpectedOutput(value: String) {
        updateDraft { it.copy(expectedOutput = value) }
    }

    fun toggleMaterial(materialId: String) {
        updateDraft { draft ->
            draft.copy(
                selectedMaterialIds = draft.selectedMaterialIds.toggle(materialId),
            )
        }
    }

    fun toggleArtifact(artifactId: String) {
        updateDraft { draft ->
            draft.copy(
                selectedArtifactIds = draft.selectedArtifactIds.toggle(artifactId),
            )
        }
    }

    fun continueToSummary() {
        val current = _state.value as? AdvanceIssueUiState.MeasureStep ?: return
        if (!current.draft.canEnterSummary) return
        val confirmed = current.draft.copy(
            confirmedRevision = current.draft.summaryRevision,
        )
        persist(confirmed)
        _state.value = AdvanceIssueUiState.SummaryStep(current.candidates, confirmed)
    }

    fun backToMeasures() {
        val pair = stateWithDraft() ?: return
        _state.value = AdvanceIssueUiState.MeasureStep(pair.first, pair.second)
    }

    fun confirm() {
        val current = _state.value as? AdvanceIssueUiState.SummaryStep ?: return
        if (!current.draft.canEnterSummary || !current.draft.summaryIsCurrent) return
        if (current.candidates.viewedStageId != null &&
            current.candidates.viewedStageId != current.candidates.currentStage.id
        ) {
            _state.value = AdvanceIssueUiState.CreateFailure(
                current.candidates,
                current.draft,
                "当前正在查看历史阶段，请先回到最新阶段。",
            )
            return
        }
        if (current.candidates.hasBlockingRun) {
            _state.value = AdvanceIssueUiState.WaitingForRun(current.candidates, current.draft)
            return
        }
        createStage(current.candidates, current.draft)
    }

    fun waitForRun() {
        val current = _state.value as? AdvanceIssueUiState.WaitingForRun ?: return
        _state.value = AdvanceIssueUiState.SummaryStep(
            current.candidates,
            current.draft.copy(confirmedRevision = null),
        )
    }

    fun requestStopCurrentRun() {
        val current = _state.value as? AdvanceIssueUiState.WaitingForRun ?: return
        _state.value = AdvanceIssueUiState.StoppingCurrentRun(current.candidates, current.draft)
        _events.tryEmit(AdvanceIssueEvent.RequestStopCurrentRun)
    }

    fun onStopFinished() {
        val current = _state.value as? AdvanceIssueUiState.StoppingCurrentRun ?: return
        val issueId = loadedIssueId ?: return
        viewModelScope.launch {
            when (val loaded = loadCandidates(issueId, viewedStageId)) {
                is RepositoryResult.Success -> {
                    candidates = loaded.value
                    val invalidated = current.draft.edited { it }
                    persist(invalidated)
                    _state.value = AdvanceIssueUiState.SummaryStep(loaded.value, invalidated)
                }
                is RepositoryResult.Failure -> {
                    _state.value = AdvanceIssueUiState.CreateFailure(
                        current.candidates,
                        current.draft,
                        repositoryErrorMessage(loaded.error),
                    )
                }
            }
        }
    }

    fun requestUndo() {
        val current = candidates ?: return
        if (!current.undoAvailable) return
        _state.value = AdvanceIssueUiState.UndoAvailable(current)
    }

    fun dismissUndo() {
        _state.value = AdvanceIssueUiState.Idle
    }

    fun confirmUndo() {
        val current = (_state.value as? AdvanceIssueUiState.UndoAvailable)?.candidates ?: return
        _state.value = AdvanceIssueUiState.Undoing(current)
        viewModelScope.launch {
            when (val result = repository.undoLatestUnrunStage(
                issueId = current.issueId,
                stageId = current.currentStage.id,
            )) {
                is RepositoryResult.Success -> {
                    clearPersistedDraft()
                    candidates = null
                    _state.value = AdvanceIssueUiState.Idle
                    _events.emit(
                        AdvanceIssueEvent.NavigateToStage(
                            current.issueId,
                            current.stages
                                .filter { it.sequenceIndex < current.currentStage.sequenceIndex }
                                .maxByOrNull { it.sequenceIndex }
                                ?.id
                                ?: return@launch,
                        ),
                    )
                }
                is RepositoryResult.Failure -> {
                    _state.value = AdvanceIssueUiState.UndoFailure(
                        current,
                        repositoryErrorMessage(result.error),
                    )
                }
            }
        }
    }

    private fun createStage(
        candidates: AdvanceIssueCandidates,
        draft: AdvanceIssueDraft,
    ) {
        if (_state.value is AdvanceIssueUiState.CreatingStage) return
        _state.value = AdvanceIssueUiState.CreatingStage(candidates, draft)
        viewModelScope.launch {
            val now = clock()
            val command = AdvanceIssueCommand(
                operationId = draft.operationId,
                issueId = candidates.issueId,
                sourceStageId = candidates.currentStage.id,
                newStageId = draft.newStageId,
                newStageTitle = "阶段 ${candidates.currentStage.sequenceIndex + 2}",
                objective = draft.objective,
                realitySupport = draft.realitySupport,
                thinkingExpansion = draft.thinkingExpansion,
                measures = draft.measures.toList(),
                expectedOutput = draft.expectedOutput,
                roster = draft.roster,
                inheritedMaterialIds = draft.selectedMaterialIds.sorted(),
                inheritedArtifactIds = draft.selectedArtifactIds.sorted(),
                confirmedAt = now,
            )
            try {
                when (val result = repository.advanceIssue(command)) {
                    is RepositoryResult.Success -> {
                        clearPersistedDraft()
                        val stage = result.value.snapshot.stage
                        _state.value = AdvanceIssueUiState.Created(
                            stage.issueId,
                            stage.id,
                            result.idempotent,
                        )
                        _events.emit(AdvanceIssueEvent.NavigateToStage(stage.issueId, stage.id))
                    }
                    is RepositoryResult.Failure -> {
                        _state.value = when (result.error) {
                            is RepositoryError.IdempotencyConflict -> {
                                AdvanceIssueUiState.IdempotencyConflict(candidates, draft)
                            }
                            else -> AdvanceIssueUiState.CreateFailure(
                                candidates,
                                draft,
                                repositoryErrorMessage(result.error),
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = AdvanceIssueUiState.CreateFailure(
                    candidates,
                    draft,
                    "创建新阶段失败，请重试。",
                )
            }
        }
    }

    private suspend fun loadCandidates(
        issueId: String,
        stageId: String?,
    ): RepositoryResult<AdvanceIssueCandidates> {
        val recovery = when (val result = repository.recoverIssue(issueId)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return result
        }
        val currentStage = recovery.core.currentStage
            ?: return RepositoryResult.Failure(
                RepositoryError.InvalidState("load_advance_issue", "missing_current_stage"),
            )
        val advancements = when (val result = repository.listStageAdvancements(issueId)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return result
        }
        val currentPlan = advancements.firstOrNull { it.stage.id == currentStage.id }
            ?.roster
            .orEmpty()
        val rosterSource = CurrentStageRosterPolicy.resolveSource(
            stageId = currentStage.id,
            runs = recovery.core.runs,
            participants = recovery.core.participants,
            plannedMembers = currentPlan,
        )
        val roster = when (rosterSource) {
            is CurrentStageRosterSource.StandardRun -> rosterSource.members.map { member ->
                StageAdvancementSkillPlan(
                    officialSkillId = member.officialSkillId,
                    position = member.position,
                    responsibility = member.responsibility,
                    sourceRunId = member.sourceRunId,
                    sourceParticipantSnapshotId = member.sourceParticipantSnapshotId,
                )
            }
            is CurrentStageRosterSource.AdvancementPlan -> rosterSource.members.map { member ->
                StageAdvancementSkillPlan(
                    officialSkillId = member.officialSkillId,
                    position = member.position,
                    responsibility = member.responsibility,
                    sourceRunId = member.sourceRunId,
                    sourceParticipantSnapshotId = member.sourceParticipantSnapshotId,
                    catalogVersionBasis = currentPlan.firstOrNull {
                        it.officialSkillId == member.officialSkillId
                    }?.catalogVersionBasis,
                )
            }
            CurrentStageRosterSource.NoRoster -> emptyList()
        }
        val materials = when (val result = repository.listMaterials(MaterialFilter(issueId = issueId))) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return result
        }
        val collaboration = when (val result = repository.getStageCollaboration(currentStage.id)) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return result
        }
        val activeStatuses = setOf(ExecutionRunStatus.NOT_STARTED, ExecutionRunStatus.RUNNING)
        val blockingRun = recovery.core.runs.any {
            it.stageId == currentStage.id && it.status in activeStatuses
        }
        val unfinishedDiscussionStatuses = setOf(
            CrossDiscussionStatus.RESPONDING,
            CrossDiscussionStatus.SYNTHESIZING,
            CrossDiscussionStatus.AWAITING_SYNTHESIS,
            CrossDiscussionStatus.PARTIAL_SUCCESS,
        )
        val currentDrafts = recovery.resources.drafts.filter { it.stageId == currentStage.id }
        val defaultMaterialIds = recovery.resources.materialUsages
            .asSequence()
            .filter { it.stageId == currentStage.id }
            .mapNotNull { it.materialReferenceId }
            .filter { materialId -> materials.any { it.id == materialId } }
            .toSet()
            .ifEmpty { currentPlanMaterials(advancements, currentStage.id) }
        val defaultArtifactIds = recovery.resources.artifacts
            .asSequence()
            .filter { it.issueId == issueId }
            .map { it.id }
            .toSet()
            .ifEmpty { currentPlanArtifacts(advancements, currentStage.id) }
        val currentAdvancement = advancements.firstOrNull { it.stage.id == currentStage.id }
        val dependencyFree = currentAdvancement != null &&
            recovery.core.runs.none { it.stageId == currentStage.id } &&
            recovery.core.messages.none { it.stageId == currentStage.id } &&
            recovery.resources.drafts.none { it.stageId == currentStage.id } &&
            recovery.resources.draftRevisions.none { it.stageId == currentStage.id } &&
            recovery.resources.artifacts.none { it.stageId == currentStage.id } &&
            recovery.resources.materialUsages.none { it.stageId == currentStage.id } &&
            recovery.resources.personalContextUsages.none { it.stageId == currentStage.id } &&
            recovery.resources.audioAssets.none { it.stageId == currentStage.id } &&
            collaboration.discussions.isEmpty()

        return RepositoryResult.Success(
            AdvanceIssueCandidates(
                issueId = issueId,
                viewedStageId = stageId,
                currentStage = currentStage,
                stages = recovery.core.stages.sortedBy { it.sequenceIndex },
                roster = roster,
                materials = materials.sortedWith(compareBy({ it.title }, { it.id })),
                artifacts = recovery.resources.artifacts.sortedWith(
                    compareByDescending<com.elio.jianyu.data.ConfirmedArtifactEntity> { it.confirmedAt }
                        .thenBy { it.id },
                ),
                defaultMaterialIds = defaultMaterialIds,
                defaultArtifactIds = defaultArtifactIds,
                hasBlockingRun = blockingRun,
                hasUnfinishedDiscussion = collaboration.discussions.any {
                    it.status in unfinishedDiscussionStatuses
                },
                currentStageHasDraft = currentDrafts.isNotEmpty(),
                undoAvailable = currentStage.sequenceIndex > 0 && dependencyFree,
            ),
        )
    }

    private fun openWithCandidates(candidates: AdvanceIssueCandidates) {
        val restored = restoreDraft(candidates)
        val draft = restored ?: AdvanceIssueDraft(
            operationId = idProvider(),
            newStageId = idProvider(),
            selectedMaterialIds = candidates.defaultMaterialIds,
            selectedArtifactIds = candidates.defaultArtifactIds,
            roster = candidates.roster,
        )
        persist(draft)
        _state.value = AdvanceIssueUiState.DirectionStep(
            candidates = candidates,
            draft = draft,
            restored = restored != null,
        )
    }

    private fun updateDraft(transform: (AdvanceIssueDraft) -> AdvanceIssueDraft) {
        val pair = stateWithDraft() ?: return
        val updated = pair.second.edited(transform)
        persist(updated)
        _state.value = when (_state.value) {
            is AdvanceIssueUiState.DirectionStep -> AdvanceIssueUiState.DirectionStep(pair.first, updated)
            is AdvanceIssueUiState.MeasureStep -> AdvanceIssueUiState.MeasureStep(pair.first, updated)
            is AdvanceIssueUiState.SummaryStep -> AdvanceIssueUiState.MeasureStep(pair.first, updated)
            is AdvanceIssueUiState.CreateFailure -> AdvanceIssueUiState.MeasureStep(pair.first, updated)
            is AdvanceIssueUiState.IdempotencyConflict -> AdvanceIssueUiState.MeasureStep(pair.first, updated)
            else -> _state.value
        }
    }

    private fun stateWithDraft(): Pair<AdvanceIssueCandidates, AdvanceIssueDraft>? =
        when (val current = _state.value) {
            is AdvanceIssueUiState.DirectionStep -> current.candidates to current.draft
            is AdvanceIssueUiState.MeasureStep -> current.candidates to current.draft
            is AdvanceIssueUiState.SummaryStep -> current.candidates to current.draft
            is AdvanceIssueUiState.WaitingForRun -> current.candidates to current.draft
            is AdvanceIssueUiState.StoppingCurrentRun -> current.candidates to current.draft
            is AdvanceIssueUiState.CreatingStage -> current.candidates to current.draft
            is AdvanceIssueUiState.CreateFailure -> current.candidates to current.draft
            is AdvanceIssueUiState.IdempotencyConflict -> current.candidates to current.draft
            is AdvanceIssueUiState.RestoredDraft -> current.candidates to current.draft
            else -> null
        }

    private fun persist(draft: AdvanceIssueDraft) {
        savedStateHandle[KEY_ISSUE_ID] = loadedIssueId
        savedStateHandle[KEY_OPERATION_ID] = draft.operationId
        savedStateHandle[KEY_STAGE_ID] = draft.newStageId
        savedStateHandle[KEY_REALITY] = draft.realitySupport
        savedStateHandle[KEY_THINKING] = draft.thinkingExpansion
        savedStateHandle[KEY_MEASURES] = draft.measures.map { it.storageValue }
        savedStateHandle[KEY_OBJECTIVE] = draft.objective
        savedStateHandle[KEY_EXPECTED_OUTPUT] = draft.expectedOutput
        savedStateHandle[KEY_MATERIALS] = draft.selectedMaterialIds.toList()
        savedStateHandle[KEY_ARTIFACTS] = draft.selectedArtifactIds.toList()
        savedStateHandle[KEY_SUMMARY_REVISION] = draft.summaryRevision
    }

    private fun restoreDraft(candidates: AdvanceIssueCandidates): AdvanceIssueDraft? {
        if (savedStateHandle.get<String>(KEY_ISSUE_ID) != candidates.issueId) return null
        val operationId = savedStateHandle.get<String>(KEY_OPERATION_ID) ?: return null
        val stageId = savedStateHandle.get<String>(KEY_STAGE_ID) ?: return null
        val measures = savedStateHandle.get<List<String>>(KEY_MEASURES)
            .orEmpty()
            .mapNotNull { value ->
                StageAdvancementMeasure.entries.firstOrNull { it.storageValue == value }
            }
            .toSet()
        return AdvanceIssueDraft(
            operationId = operationId,
            newStageId = stageId,
            realitySupport = savedStateHandle[KEY_REALITY] ?: false,
            thinkingExpansion = savedStateHandle[KEY_THINKING] ?: false,
            measures = measures,
            objective = savedStateHandle[KEY_OBJECTIVE] ?: "",
            expectedOutput = savedStateHandle[KEY_EXPECTED_OUTPUT] ?: "行动计划",
            selectedMaterialIds = savedStateHandle.get<List<String>>(KEY_MATERIALS).orEmpty().toSet(),
            selectedArtifactIds = savedStateHandle.get<List<String>>(KEY_ARTIFACTS).orEmpty().toSet(),
            roster = candidates.roster,
            summaryRevision = savedStateHandle[KEY_SUMMARY_REVISION] ?: 0L,
            confirmedRevision = null,
        )
    }

    private fun clearPersistedDraft() {
        listOf(
            KEY_ISSUE_ID,
            KEY_OPERATION_ID,
            KEY_STAGE_ID,
            KEY_REALITY,
            KEY_THINKING,
            KEY_MEASURES,
            KEY_OBJECTIVE,
            KEY_EXPECTED_OUTPUT,
            KEY_MATERIALS,
            KEY_ARTIFACTS,
            KEY_SUMMARY_REVISION,
        ).forEach(savedStateHandle::remove)
    }

    private fun clearFlow() {
        candidates = null
        loadedIssueId = null
        viewedStageId = null
        _state.value = AdvanceIssueUiState.Idle
    }

    private fun repositoryErrorMessage(error: RepositoryError): String = when (error) {
        is RepositoryError.NotFound -> "推进所需的数据不存在。"
        is RepositoryError.AlreadyExists -> "目标阶段已存在，请重新加载。"
        is RepositoryError.IdempotencyConflict -> "本次推进与已提交内容冲突。"
        is RepositoryError.InvalidState -> "当前阶段状态已变化，请重新确认。"
        is RepositoryError.ConstraintViolation -> "推进内容不符合约束，请检查选择。"
        is RepositoryError.StorageFailure -> "存储暂时不可用，请稍后重试。"
        is RepositoryError.CompatibilityFailure -> "当前版本暂不支持推进议题。"
    }

    private fun currentPlanMaterials(
        advancements: List<com.elio.jianyu.data.StageAdvancementSnapshot>,
        stageId: String,
    ): Set<String> = advancements.firstOrNull { it.stage.id == stageId }
        ?.materials
        .orEmpty()
        .map { it.materialReferenceId }
        .toSet()

    private fun currentPlanArtifacts(
        advancements: List<com.elio.jianyu.data.StageAdvancementSnapshot>,
        stageId: String,
    ): Set<String> = advancements.firstOrNull { it.stage.id == stageId }
        ?.artifacts
        .orEmpty()
        .map { it.artifactId }
        .toSet()

    companion object {
        private val REALITY_MEASURES = setOf(
            StageAdvancementMeasure.CLARIFY_NEXT_STEP,
            StageAdvancementMeasure.FORM_EXECUTION_PLAN,
            StageAdvancementMeasure.ANALYZE_EXECUTION_OBSTACLES,
            StageAdvancementMeasure.GENERATE_DELIVERABLE,
            StageAdvancementMeasure.SET_CHECKPOINTS,
        )
        private val THINKING_MEASURES = setOf(
            StageAdvancementMeasure.INTRODUCE_COUNTERARGUMENT,
            StageAdvancementMeasure.FIND_MISSING_PERSPECTIVES,
            StageAdvancementMeasure.CHECK_KEY_ASSUMPTIONS,
            StageAdvancementMeasure.COMPARE_POSITIONS,
            StageAdvancementMeasure.DEEPEN_QUESTION,
        )

        private const val KEY_ISSUE_ID = "advance_issue.issue_id"
        private const val KEY_OPERATION_ID = "advance_issue.operation_id"
        private const val KEY_STAGE_ID = "advance_issue.stage_id"
        private const val KEY_REALITY = "advance_issue.reality"
        private const val KEY_THINKING = "advance_issue.thinking"
        private const val KEY_MEASURES = "advance_issue.measures"
        private const val KEY_OBJECTIVE = "advance_issue.objective"
        private const val KEY_EXPECTED_OUTPUT = "advance_issue.expected_output"
        private const val KEY_MATERIALS = "advance_issue.materials"
        private const val KEY_ARTIFACTS = "advance_issue.artifacts"
        private const val KEY_SUMMARY_REVISION = "advance_issue.summary_revision"

        fun factory(repository: JianyuRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                AdvanceIssueViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    repository = repository,
                )
            }
        }
    }
}

enum class AdvanceIssueDirection {
    REALITY_SUPPORT,
    THINKING_EXPANSION,
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value
