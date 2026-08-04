package com.elio.jianyu.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.MaterialFilter
import com.elio.jianyu.data.PersonalContextFilter
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.execution.ExecutionRunCoordinator
import com.elio.jianyu.home.CoordinatorHomeExecutionStarter
import com.elio.jianyu.home.HomeContextItemSnapshot
import com.elio.jianyu.home.HomeContextSelectionSnapshot
import com.elio.jianyu.home.HomeFinalConfirmation
import com.elio.jianyu.home.HomeIdProvider
import com.elio.jianyu.home.HomeRecommendationGateway
import com.elio.jianyu.home.HomeRecommendationOutcome
import com.elio.jianyu.home.HomeRecommendationPolicy
import com.elio.jianyu.home.HomeRecommendationRequest
import com.elio.jianyu.home.HomeRepositoryGateway
import com.elio.jianyu.home.HomeSaveOnlyCommand
import com.elio.jianyu.home.HomeStartCoordinator
import com.elio.jianyu.home.HomeStartResult
import com.elio.jianyu.home.HomeWorkflow
import com.elio.jianyu.home.HomeWorkflowError
import com.elio.jianyu.home.HomeWorkflowState
import com.elio.jianyu.home.HomeWorkflowStep
import com.elio.jianyu.home.JianyuHomeRepositoryGateway
import com.elio.jianyu.home.LocalCatalogHomeRecommendationGateway
import com.elio.jianyu.home.RecommendationMode
import com.elio.jianyu.home.UuidHomeIdProvider
import com.elio.jianyu.home.ValueDirection
import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.ui.screens.context.ContextCandidateUi
import com.elio.jianyu.ui.screens.context.ContextConfirmationUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class HomeViewModel internal constructor(
    private val savedStateHandle: SavedStateHandle,
    private val repository: JianyuRepository,
    private val recommendationGateway: HomeRecommendationGateway?,
    private val catalog: OfficialSkillCatalog?,
    private val startCoordinator: HomeStartCoordinator,
    private val idProvider: HomeIdProvider = UuidHomeIdProvider,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val restoredWorkflow = savedStateHandle.get<String>(WORKFLOW_STATE_KEY)
        ?.let { encoded ->
            runCatching { json.decodeFromString<HomeWorkflowState>(encoded) }.getOrNull()
        }
    private val initialWorkflow = restoredWorkflow
        ?.let(HomeWorkflow::restore)
        ?: HomeWorkflow.initial(idProvider.create())

    private val _uiState = MutableStateFlow(HomeUiState(workflow = initialWorkflow))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<HomeNavigationEvent>(extraBufferCapacity = 1)
    val navigationEvents: SharedFlow<HomeNavigationEvent> = _navigationEvents.asSharedFlow()

    init {
        persist(initialWorkflow)
    }

    fun onQuestionChanged(value: String) {
        setWorkflow(HomeWorkflow.onQuestionChanged(_uiState.value.workflow, value))
    }

    fun useExample(example: HomeExampleQuestion) {
        onQuestionChanged(example.text)
    }

    fun clearQuestion() {
        onQuestionChanged("")
    }

    fun toggleDirection(direction: ValueDirection) {
        setWorkflow(HomeWorkflow.toggleDirection(_uiState.value.workflow, direction))
    }

    fun requestRecommendation() {
        val transition = HomeWorkflow.beginRecommendation(_uiState.value.workflow)
        setWorkflow(transition.state)
        val token = transition.requestToken ?: return
        val gateway = recommendationGateway
        if (gateway == null) {
            setWorkflow(
                HomeWorkflow.failRecommendation(
                    transition.state,
                    token,
                    HomeWorkflowError.CATALOG_UNAVAILABLE,
                ),
                message = "官方 Skill 目录不可用，问题草稿已保留。",
            )
            return
        }
        val request = HomeRecommendationRequest(
            question = transition.state.draft.question.trim(),
            directions = transition.state.draft.directions,
        )
        viewModelScope.launch {
            try {
                when (val outcome = gateway.recommend(request)) {
                    is HomeRecommendationOutcome.Ready -> setWorkflow(
                        HomeWorkflow.applyRecommendation(
                            _uiState.value.workflow,
                            token,
                            outcome.recommendation,
                        ),
                    )
                    is HomeRecommendationOutcome.NoSuitableSkill -> setWorkflow(
                        HomeWorkflow.noSuitableSkill(_uiState.value.workflow, token),
                        message = "当前没有合适的正式 Skill，仍可修改问题或仅保存议题。",
                    )
                    is HomeRecommendationOutcome.NoExecutableSkill -> setWorkflow(
                        HomeWorkflow.noExecutableSkill(
                            _uiState.value.workflow,
                            token,
                            outcome.candidates,
                        ),
                        message = "找到了相关 Skill，但当前没有通过执行门禁的成员。",
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                setWorkflow(
                    HomeWorkflow.failRecommendation(
                        _uiState.value.workflow,
                        token,
                        HomeWorkflowError.RECOMMENDATION_FAILED,
                    ),
                    message = "推荐生成失败，问题和方向选择已保留。",
                )
            }
        }
    }

    fun toggleSkill(skillId: String) {
        setWorkflow(HomeWorkflow.toggleSkillSelection(_uiState.value.workflow, skillId))
    }

    fun updateSkillResponsibility(skillId: String, value: String) {
        setWorkflow(
            HomeWorkflow.updateSkillResponsibility(_uiState.value.workflow, skillId, value),
        )
    }

    fun moveSkill(skillId: String, offset: Int) {
        setWorkflow(HomeWorkflow.moveSkill(_uiState.value.workflow, skillId, offset))
    }

    fun switchMode(mode: RecommendationMode) {
        setWorkflow(HomeWorkflow.switchMode(_uiState.value.workflow, mode))
    }

    fun confirmRecommendation() {
        val workflow = _uiState.value.workflow
        val recommendation = workflow.recommendation ?: return
        val currentCatalog = catalog
        if (currentCatalog == null) {
            setWorkflow(
                workflow.copy(errorCode = HomeWorkflowError.CATALOG_UNAVAILABLE.code),
                message = "官方 Skill 目录不可用，无法确认执行成员。",
            )
            return
        }
        val errors = HomeRecommendationPolicy.validateForStart(currentCatalog, recommendation)
        if (errors.isNotEmpty()) {
            setWorkflow(
                workflow.copy(
                    step = HomeWorkflowStep.EDITING_RECOMMENDATION,
                    recommendationConfirmed = false,
                    errorCode = errors.joinToString(",") { it.name.lowercase() },
                ),
                message = "当前 Skill 阵容仍需修正后才能继续。",
            )
            return
        }
        val confirmed = HomeWorkflow.confirmRecommendation(workflow)
        setWorkflow(confirmed)
        openContextConfirmation()
    }

    fun browseSkills() {
        _navigationEvents.tryEmit(HomeNavigationEvent.OpenSkillCatalog)
    }

    fun openContextConfirmation() {
        val workflow = _uiState.value.workflow
        if (!workflow.recommendationConfirmed) return
        val existing = _uiState.value.contextConfirmation
        if (existing != null) {
            _uiState.value = _uiState.value.copy(
                contextConfirmation = existing.copy(visible = true, errorMessage = null),
            )
            return
        }
        viewModelScope.launch {
            val materialResult = repository.listMaterials(
                MaterialFilter(
                    issueId = workflow.ids.issueId,
                    lifecycles = setOf(ContextSourceLifecycle.ACTIVE),
                ),
            )
            val personalResult = repository.listPersonalContexts(
                PersonalContextFilter(lifecycles = setOf(ContextSourceLifecycle.ACTIVE)),
            )
            val materialCandidates = (materialResult as? RepositoryResult.Success)
                ?.value.orEmpty()
                .filter { it.stageId == null || it.stageId == workflow.ids.stageId }
                .map { material ->
                    ContextCandidateUi(
                        sourceType = ContextSourceType.MATERIAL,
                        sourceId = material.id,
                        title = material.title,
                        sourceKind = material.sourceType,
                        sourceLocator = material.sourceLocator,
                        sourcePublishedAt = material.sourcePublishedAt,
                        sourceCapturedAt = material.sourceCapturedAt,
                        originalContent = material.content,
                        selectedContent = material.content,
                        sourceHash = material.contentHash,
                        sourceUpdatedAt = material.updatedAt,
                        sensitive = material.sensitive,
                    )
                }
            val personalCandidates = (personalResult as? RepositoryResult.Success)
                ?.value.orEmpty()
                .map { personal ->
                    ContextCandidateUi(
                        sourceType = ContextSourceType.PERSONAL_CONTEXT,
                        sourceId = personal.id,
                        title = personal.title,
                        sourceKind = "personal_context",
                        sourceLocator = null,
                        sourcePublishedAt = null,
                        sourceCapturedAt = null,
                        originalContent = personal.content,
                        selectedContent = personal.content,
                        sourceHash = personal.contentHash,
                        sourceUpdatedAt = personal.updatedAt,
                        sensitive = personal.sensitive,
                    )
                }
            val stored = workflow.contextSelection.items.associateBy {
                it.sourceType to it.sourceId
            }
            val candidates = (materialCandidates + personalCandidates).map { candidate ->
                stored[candidate.sourceType.storageValue to candidate.sourceId]
                    ?.toCandidateUi()
                    ?: candidate
            }
            val baseCharacters = calculateBaseContextCharacters(workflow)
            val message = buildList {
                if (materialResult is RepositoryResult.Failure) add("资料候选读取失败")
                if (personalResult is RepositoryResult.Failure) add("个人背景候选读取失败")
            }.joinToString("；").takeIf(String::isNotBlank)
            val selection = HomeContextSelectionSnapshot(
                baseContextCharacters = baseCharacters,
                items = candidates.map { candidate -> candidate.toSnapshot() },
                confirmed = false,
            )
            setWorkflow(HomeWorkflow.updateContextSelection(workflow, selection), message)
            _uiState.value = _uiState.value.copy(
                contextConfirmation = ContextConfirmationUiState(
                    visible = true,
                    runId = workflow.ids.runId,
                    issueId = workflow.ids.issueId,
                    stageId = workflow.ids.stageId,
                    currentUserInput = workflow.draft.question,
                    baseContextCharacters = baseCharacters,
                    candidates = candidates,
                    errorMessage = message,
                ),
            )
        }
    }

    fun dismissContextConfirmation() {
        _uiState.value = _uiState.value.copy(
            contextConfirmation = _uiState.value.contextConfirmation?.copy(visible = false),
        )
    }

    fun toggleContextCandidate(sourceType: ContextSourceType, sourceId: String) {
        updateContextCandidate(sourceType, sourceId) { candidate ->
            candidate.copy(
                selected = !candidate.selected,
                networkAllowed = if (candidate.selected) false else candidate.networkAllowed,
                sensitiveConfirmed = if (candidate.selected) {
                    false
                } else {
                    candidate.sensitiveConfirmed
                },
            )
        }
    }

    fun setContextNetworkAllowed(
        sourceType: ContextSourceType,
        sourceId: String,
        allowed: Boolean,
    ) {
        updateContextCandidate(sourceType, sourceId) { it.copy(networkAllowed = allowed) }
    }

    fun setContextSensitiveConfirmed(
        sourceType: ContextSourceType,
        sourceId: String,
        confirmed: Boolean,
    ) {
        updateContextCandidate(sourceType, sourceId) { it.copy(sensitiveConfirmed = confirmed) }
    }

    fun updateContextExcerpt(
        sourceType: ContextSourceType,
        sourceId: String,
        value: String,
    ) {
        updateContextCandidate(sourceType, sourceId) { it.copy(selectedContent = value) }
    }

    fun confirmContext() {
        val dialog = _uiState.value.contextConfirmation ?: return
        if (
            dialog.tooLarge ||
            dialog.networkPermissionMissing ||
            dialog.sensitiveConfirmationMissing
        ) {
            _uiState.value = _uiState.value.copy(
                contextConfirmation = dialog.copy(errorMessage = "请先修正上下文确认项。"),
            )
            return
        }
        val confirmedAt = clock()
        val confirmedItems = dialog.candidates.mapIndexed { index, candidate ->
            candidate.toSnapshot().copy(
                confirmationOrder = index,
                userConfirmedAt = if (candidate.selected) confirmedAt else 0L,
            )
        }
        val selection = HomeContextSelectionSnapshot(
            baseContextCharacters = dialog.baseContextCharacters,
            items = confirmedItems,
            confirmed = true,
        )
        val confirmed = HomeWorkflow.confirmContext(_uiState.value.workflow, selection)
        val finalReview = HomeWorkflow.enterFinalReview(confirmed)
        setWorkflow(finalReview)
        _uiState.value = _uiState.value.copy(
            contextConfirmation = dialog.copy(
                visible = false,
                confirmedForStart = true,
                candidates = confirmedItems.map { item -> item.toCandidateUi() },
                errorMessage = null,
            ),
        )
    }

    fun saveIssueOnly() {
        val current = _uiState.value.workflow
        if (!HomeWorkflow.canSaveIssueOnly(current)) return
        setWorkflow(
            current.copy(
                step = HomeWorkflowStep.SAVING_ISSUE,
                operationInFlight = true,
                errorCode = null,
            ),
        )
        viewModelScope.launch {
            when (
                val result = startCoordinator.saveOnly(
                    HomeSaveOnlyCommand(
                        ids = current.ids,
                        question = current.draft.question,
                        createdAt = clock(),
                    ),
                )
            ) {
                is HomeStartResult.SavedOnly -> {
                    setWorkflow(
                        _uiState.value.workflow.copy(
                            step = HomeWorkflowStep.NAVIGATING_TO_ISSUE,
                            operationInFlight = false,
                        ),
                    )
                    _navigationEvents.emit(
                        HomeNavigationEvent.NavigateToIssue(result.issueId, result.stageId),
                    )
                }
                is HomeStartResult.Failure -> setWorkflow(
                    _uiState.value.workflow.copy(
                        step = HomeWorkflowStep.EDITING_QUESTION,
                        operationInFlight = false,
                        errorCode = result.errorCode,
                    ),
                    message = "议题保存失败，问题草稿和稳定标识已保留。",
                )
                else -> Unit
            }
        }
    }

    fun startIssue() {
        val current = _uiState.value.workflow
        val recommendation = current.recommendation ?: return
        if (!current.finalConfirmationReady || current.operationInFlight) return
        val confirmedAt = clock()
        val confirmation = HomeFinalConfirmation(
            ids = current.ids,
            question = current.draft.question,
            directions = current.draft.directions,
            recommendation = recommendation,
            contextSelection = current.contextSelection,
            confirmedAt = confirmedAt,
        )
        setWorkflow(
            current.copy(
                step = HomeWorkflowStep.STARTING_EXECUTION,
                operationInFlight = true,
                errorCode = null,
            ),
        )
        viewModelScope.launch {
            when (val result = startCoordinator.start(confirmation)) {
                is HomeStartResult.Started -> {
                    setWorkflow(
                        _uiState.value.workflow.copy(
                            step = HomeWorkflowStep.NAVIGATING_TO_ISSUE,
                            operationInFlight = false,
                        ),
                    )
                    _navigationEvents.emit(
                        HomeNavigationEvent.NavigateToIssue(result.issueId, result.stageId),
                    )
                }
                is HomeStartResult.SavedNotStarted -> setWorkflow(
                    _uiState.value.workflow.copy(
                        step = HomeWorkflowStep.SAVED_NOT_STARTED,
                        operationInFlight = false,
                        errorCode = result.errorCode,
                    ),
                    message = "议题已保存，尚未开始。可修正后使用同一议题重试。",
                )
                is HomeStartResult.Failure -> setWorkflow(
                    _uiState.value.workflow.copy(
                        step = HomeWorkflowStep.START_FAILURE,
                        operationInFlight = false,
                        errorCode = result.errorCode,
                    ),
                    message = "开始失败，确认内容和稳定标识已保留。",
                )
                is HomeStartResult.SavedOnly -> Unit
            }
        }
    }

    private fun updateContextCandidate(
        sourceType: ContextSourceType,
        sourceId: String,
        transform: (ContextCandidateUi) -> ContextCandidateUi,
    ) {
        val dialog = _uiState.value.contextConfirmation ?: return
        val updated = dialog.copy(
            candidates = dialog.candidates.map { candidate ->
                if (candidate.sourceType == sourceType && candidate.sourceId == sourceId) {
                    transform(candidate)
                } else {
                    candidate
                }
            },
            errorMessage = null,
            confirmedForStart = false,
        )
        val selection = HomeContextSelectionSnapshot(
            baseContextCharacters = updated.baseContextCharacters,
            items = updated.candidates.map { candidate -> candidate.toSnapshot() },
            confirmed = false,
        )
        setWorkflow(HomeWorkflow.updateContextSelection(_uiState.value.workflow, selection))
        _uiState.value = _uiState.value.copy(contextConfirmation = updated)
    }

    private fun setWorkflow(
        workflow: HomeWorkflowState,
        message: String? = null,
    ) {
        persist(workflow)
        _uiState.value = _uiState.value.copy(workflow = workflow, message = message)
    }

    private fun persist(workflow: HomeWorkflowState) {
        savedStateHandle[WORKFLOW_STATE_KEY] = json.encodeToString(workflow)
    }

    private fun calculateBaseContextCharacters(workflow: HomeWorkflowState): Int =
        workflow.draft.question.length +
            workflow.recommendation?.selectedSkills.orEmpty().sumOf { it.responsibility.length } +
            HOME_CONTEXT_TEMPLATE_RESERVE_CHARACTERS

    private fun ContextCandidateUi.toSnapshot(): HomeContextItemSnapshot = HomeContextItemSnapshot(
        sourceType = sourceType.storageValue,
        sourceId = sourceId,
        title = title,
        sourceKind = sourceKind,
        sourceLocator = sourceLocator,
        sourcePublishedAt = sourcePublishedAt,
        sourceCapturedAt = sourceCapturedAt,
        originalContent = originalContent,
        selectedContent = selectedContent,
        sourceHash = sourceHash,
        sourceUpdatedAt = sourceUpdatedAt,
        sensitive = sensitive,
        selected = selected,
        networkAllowed = networkAllowed,
        sensitiveConfirmed = sensitiveConfirmed,
    )

    private fun HomeContextItemSnapshot.toCandidateUi(): ContextCandidateUi = ContextCandidateUi(
        sourceType = ContextSourceType.entries.first { it.storageValue == sourceType },
        sourceId = sourceId,
        title = title,
        sourceKind = sourceKind,
        sourceLocator = sourceLocator,
        sourcePublishedAt = sourcePublishedAt,
        sourceCapturedAt = sourceCapturedAt,
        originalContent = originalContent,
        selectedContent = selectedContent,
        sourceHash = sourceHash,
        sourceUpdatedAt = sourceUpdatedAt,
        sensitive = sensitive,
        selected = selected,
        networkAllowed = networkAllowed,
        sensitiveConfirmed = sensitiveConfirmed,
    )

    companion object {
        private const val WORKFLOW_STATE_KEY = "home_workflow_state_v1"
        private const val HOME_CONTEXT_TEMPLATE_RESERVE_CHARACTERS = 512

        fun factory(
            repository: JianyuRepository,
            catalogRuntimeResult: OfficialSkillCatalogRuntimeResult,
            coordinator: ExecutionRunCoordinator?,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val catalog = (catalogRuntimeResult as? OfficialSkillCatalogRuntimeResult.Success)
                    ?.runtime?.catalog
                val repositoryGateway: HomeRepositoryGateway = JianyuHomeRepositoryGateway(repository)
                HomeViewModel(
                    savedStateHandle = createSavedStateHandle(),
                    repository = repository,
                    recommendationGateway = catalog?.let(::LocalCatalogHomeRecommendationGateway),
                    catalog = catalog,
                    startCoordinator = HomeStartCoordinator(
                        repository = repositoryGateway,
                        executionStarter = CoordinatorHomeExecutionStarter(coordinator),
                    ),
                )
            }
        }
    }
}
