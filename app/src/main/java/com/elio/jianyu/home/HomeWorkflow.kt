package com.elio.jianyu.home

object HomeWorkflow {
    fun initial(ids: HomeWorkflowIds): HomeWorkflowState = HomeWorkflowState(ids = ids)

    fun restore(state: HomeWorkflowState): HomeWorkflowState = state.copy(
        step = HomeWorkflowStep.RESTORED_DRAFT,
        recommendationConfirmed = false,
        contextSelection = state.contextSelection.copy(confirmed = false),
        executionConsent = HomeExecutionConsentSnapshot(),
        finalConfirmationReady = false,
        activeRecommendationToken = null,
        operationInFlight = false,
        restored = true,
    )

    fun onQuestionChanged(
        state: HomeWorkflowState,
        question: String,
    ): HomeWorkflowState {
        if (state.draft.question == question) return state
        return state.copy(
            draft = state.draft.copy(question = question),
            step = HomeWorkflowStep.EDITING_QUESTION,
            recommendation = null,
            recommendationConfirmed = false,
            contextSelection = HomeContextSelectionSnapshot(),
            executionConsent = HomeExecutionConsentSnapshot(),
            finalConfirmationReady = false,
            activeRecommendationToken = null,
            errorCode = null,
            operationInFlight = false,
        )
    }

    fun toggleDirection(
        state: HomeWorkflowState,
        direction: ValueDirection,
    ): HomeWorkflowState {
        val directions = state.draft.directions.toMutableSet().apply {
            if (!add(direction)) remove(direction)
        }.toSet()
        return state.copy(
            draft = state.draft.copy(directions = directions),
            step = HomeWorkflowStep.EDITING_QUESTION,
            recommendation = null,
            recommendationConfirmed = false,
            contextSelection = HomeContextSelectionSnapshot(),
            executionConsent = HomeExecutionConsentSnapshot(),
            finalConfirmationReady = false,
            activeRecommendationToken = null,
            errorCode = null,
        )
    }

    fun beginRecommendation(state: HomeWorkflowState): HomeRecommendationRequestTransition {
        if (state.draft.question.isBlank()) {
            return HomeRecommendationRequestTransition(
                state = state.copy(errorCode = HomeWorkflowError.QUESTION_REQUIRED.code),
                requestToken = null,
                error = HomeWorkflowError.QUESTION_REQUIRED,
            )
        }
        if (state.operationInFlight && state.step == HomeWorkflowStep.RECOMMENDATION_LOADING) {
            return HomeRecommendationRequestTransition(
                state = state,
                requestToken = state.activeRecommendationToken,
            )
        }
        val token = state.nextRecommendationToken
        return HomeRecommendationRequestTransition(
            state = state.copy(
                step = HomeWorkflowStep.RECOMMENDATION_LOADING,
                recommendation = null,
                recommendationConfirmed = false,
                contextSelection = HomeContextSelectionSnapshot(),
                executionConsent = HomeExecutionConsentSnapshot(),
                finalConfirmationReady = false,
                activeRecommendationToken = token,
                nextRecommendationToken = token + 1L,
                errorCode = null,
                operationInFlight = true,
            ),
            requestToken = token,
        )
    }

    fun applyRecommendation(
        state: HomeWorkflowState,
        requestToken: Long,
        recommendation: HomeRecommendation,
    ): HomeWorkflowState {
        if (state.activeRecommendationToken != requestToken) return state
        val normalized = recommendation.copy(
            skills = recommendation.skills.mapIndexed { index, skill ->
                skill.copy(position = index)
            },
        )
        return state.copy(
            step = if (normalized.selectedSkills.isEmpty()) {
                HomeWorkflowStep.NO_EXECUTABLE_SKILL
            } else {
                HomeWorkflowStep.RECOMMENDATION_READY
            },
            recommendation = normalized,
            recommendationConfirmed = false,
            contextSelection = HomeContextSelectionSnapshot(),
            executionConsent = HomeExecutionConsentSnapshot(),
            finalConfirmationReady = false,
            activeRecommendationToken = null,
            errorCode = null,
            operationInFlight = false,
        )
    }

    fun noSuitableSkill(
        state: HomeWorkflowState,
        requestToken: Long,
    ): HomeWorkflowState {
        if (state.activeRecommendationToken != requestToken) return state
        return state.copy(
            step = HomeWorkflowStep.NO_SUITABLE_SKILL,
            recommendation = null,
            recommendationConfirmed = false,
            executionConsent = HomeExecutionConsentSnapshot(),
            activeRecommendationToken = null,
            errorCode = HomeWorkflowError.NO_SUITABLE_SKILL.code,
            operationInFlight = false,
        )
    }

    fun noExecutableSkill(
        state: HomeWorkflowState,
        requestToken: Long,
        candidates: List<RecommendedSkill>,
    ): HomeWorkflowState {
        if (state.activeRecommendationToken != requestToken) return state
        val recommendation = HomeRecommendation(
            questionSummary = state.draft.question.trim(),
            directions = state.draft.directions,
            mode = RecommendationMode.SINGLE,
            modeReason = "当前候选尚未通过执行门禁，只能查看理由和能力边界。",
            skills = candidates.mapIndexed { index, skill ->
                skill.copy(selected = false, position = index)
            },
            expectedOutput = candidates.firstOrNull()?.expectedOutput ?: "可执行能力说明",
            source = RecommendationSource.LOCAL_CATALOG,
        )
        return state.copy(
            step = HomeWorkflowStep.NO_EXECUTABLE_SKILL,
            recommendation = recommendation,
            recommendationConfirmed = false,
            executionConsent = HomeExecutionConsentSnapshot(),
            activeRecommendationToken = null,
            errorCode = HomeWorkflowError.NO_EXECUTABLE_SKILL.code,
            operationInFlight = false,
        )
    }

    fun failRecommendation(
        state: HomeWorkflowState,
        requestToken: Long,
        error: HomeWorkflowError,
    ): HomeWorkflowState {
        if (state.activeRecommendationToken != requestToken) return state
        return state.copy(
            step = HomeWorkflowStep.RECOMMENDATION_FAILURE,
            activeRecommendationToken = null,
            errorCode = error.code,
            operationInFlight = false,
        )
    }

    fun confirmRecommendation(state: HomeWorkflowState): HomeWorkflowState {
        val selected = state.recommendation?.selectedSkills.orEmpty()
        if (selected.isEmpty() || selected.any { !it.executable }) {
            return state.copy(
                step = HomeWorkflowStep.EDITING_RECOMMENDATION,
                recommendationConfirmed = false,
                executionConsent = HomeExecutionConsentSnapshot(),
                errorCode = HomeWorkflowError.NO_EXECUTABLE_SKILL.code,
            )
        }
        return state.copy(
            step = HomeWorkflowStep.CONTEXT_CONFIRMING,
            recommendationConfirmed = true,
            contextSelection = state.contextSelection.copy(confirmed = false),
            executionConsent = HomeExecutionConsentSnapshot(),
            finalConfirmationReady = false,
            errorCode = null,
        )
    }

    fun updateContextSelection(
        state: HomeWorkflowState,
        selection: HomeContextSelectionSnapshot,
    ): HomeWorkflowState = state.copy(
        step = HomeWorkflowStep.CONTEXT_CONFIRMING,
        contextSelection = selection.copy(confirmed = false),
        executionConsent = HomeExecutionConsentSnapshot(),
        finalConfirmationReady = false,
        errorCode = null,
    )

    fun confirmContext(
        state: HomeWorkflowState,
        selection: HomeContextSelectionSnapshot,
    ): HomeWorkflowState {
        if (!state.recommendationConfirmed || !selection.confirmed) {
            return state.copy(
                step = HomeWorkflowStep.CONTEXT_NEEDS_CORRECTION,
                contextSelection = selection.copy(confirmed = false),
                executionConsent = HomeExecutionConsentSnapshot(),
                finalConfirmationReady = false,
                errorCode = HomeWorkflowError.CONTEXT_CONFIRMATION_REQUIRED.code,
            )
        }
        val restricted = selection.items.any { it.selected && it.sensitive }
        return state.copy(
            step = HomeWorkflowStep.FINAL_REVIEW,
            contextSelection = selection,
            executionConsent = state.executionConsent.copy(
                restrictedMaterialPresent = restricted,
                materialMayLeaveDevice = restricted,
            ),
            finalConfirmationReady = false,
            errorCode = null,
        )
    }

    fun updateExecutionConsent(
        state: HomeWorkflowState,
        consent: HomeExecutionConsentSnapshot,
    ): HomeWorkflowState = state.copy(
        step = HomeWorkflowStep.FINAL_REVIEW,
        executionConsent = consent,
        finalConfirmationReady = false,
        errorCode = null,
    )

    fun enterFinalReview(state: HomeWorkflowState): HomeWorkflowState {
        if (!state.recommendationConfirmed || !state.contextSelection.confirmed) {
            return state.copy(
                step = HomeWorkflowStep.CONTEXT_NEEDS_CORRECTION,
                finalConfirmationReady = false,
                errorCode = HomeWorkflowError.CONTEXT_CONFIRMATION_REQUIRED.code,
            )
        }
        val ready = executionConsentIssues(state).isEmpty()
        return state.copy(
            step = HomeWorkflowStep.FINAL_REVIEW,
            finalConfirmationReady = ready,
            errorCode = if (ready) null else HomeWorkflowError.CONTEXT_NEEDS_CORRECTION.code,
        )
    }

    fun executionConsentIssues(state: HomeWorkflowState): List<String> {
        val selected = state.recommendation?.selectedSkills.orEmpty()
        val consent = state.executionConsent
        val selectedItems = state.contextSelection.items.filter(HomeContextItemSnapshot::selected)
        return buildList {
            if (selected.any(RecommendedSkill::requiresNetworkAuthorization) && !consent.networkAuthorized) {
                add("network_authorization_required")
            }
            if (
                selected.any(RecommendedSkill::requiresHighStakesConfirmation) &&
                !consent.highStakesConfirmed
            ) {
                add("high_stakes_confirmation_required")
            }
            if (selected.any(RecommendedSkill::isPersonPerspective) && !consent.personDisclaimerConfirmed) {
                add("person_disclaimer_confirmation_required")
            }
            if (selected.any(RecommendedSkill::requiresMaterial) && selectedItems.isEmpty()) {
                add("required_material_missing")
            }
            if (
                selected.any(RecommendedSkill::requiresMaterialAuthorization) &&
                selectedItems.any { it.userConfirmedAt <= 0L }
            ) {
                add("material_authorization_required")
            }
            if (
                selected.any(RecommendedSkill::requiresSensitiveMaterialConfirmation) &&
                selectedItems.filter(HomeContextItemSnapshot::sensitive)
                    .any { !it.sensitiveConfirmed }
            ) {
                add("sensitive_material_confirmation_required")
            }
            if (
                selected.any(RecommendedSkill::prohibitsExternalMaterial) &&
                consent.restrictedMaterialPresent &&
                consent.materialMayLeaveDevice
            ) {
                add("material_external_transfer_prohibited")
            }
        }
    }

    fun toggleSkillSelection(
        state: HomeWorkflowState,
        skillId: String,
    ): HomeWorkflowState = changeRecommendation(state) { recommendation ->
        recommendation.copy(
            skills = recommendation.skills.map { skill ->
                if (skill.skillId == skillId) skill.copy(selected = !skill.selected) else skill
            },
        ).normalizeMode()
    }

    fun updateSkillResponsibility(
        state: HomeWorkflowState,
        skillId: String,
        responsibility: String,
    ): HomeWorkflowState = changeRecommendation(state) { recommendation ->
        recommendation.copy(
            skills = recommendation.skills.map { skill ->
                if (skill.skillId == skillId && responsibility.isNotBlank()) {
                    skill.copy(responsibility = responsibility.trim())
                } else {
                    skill
                }
            },
        )
    }

    fun moveSkill(
        state: HomeWorkflowState,
        skillId: String,
        offset: Int,
    ): HomeWorkflowState = changeRecommendation(state) { recommendation ->
        val items = recommendation.skills.sortedBy(RecommendedSkill::position).toMutableList()
        val from = items.indexOfFirst { it.skillId == skillId }
        if (from < 0) return@changeRecommendation recommendation
        val to = (from + offset).coerceIn(items.indices)
        if (from == to) return@changeRecommendation recommendation
        val item = items.removeAt(from)
        items.add(to, item)
        recommendation.copy(
            skills = items.mapIndexed { index, skill -> skill.copy(position = index) },
        )
    }

    fun switchMode(
        state: HomeWorkflowState,
        mode: RecommendationMode,
    ): HomeWorkflowState = changeRecommendation(state) { recommendation ->
        val ordered = recommendation.skills.sortedBy(RecommendedSkill::position)
        val skills = when (mode) {
            RecommendationMode.SINGLE -> {
                val keepId = ordered.firstOrNull { it.selected && it.executable }?.skillId
                    ?: ordered.firstOrNull { it.executable }?.skillId
                ordered.map { it.copy(selected = it.skillId == keepId) }
            }
            RecommendationMode.MULTI -> ordered
        }
        recommendation.copy(
            mode = mode,
            modeReason = if (mode == RecommendationMode.SINGLE) {
                "用户选择由一个 Skill 聚焦完成当前任务。"
            } else {
                "用户选择多个 Skill 以互补分工完成当前任务。"
            },
            skills = skills,
        )
    }

    fun canSaveIssueOnly(state: HomeWorkflowState): Boolean =
        state.draft.question.isNotBlank() && !state.operationInFlight

    private fun changeRecommendation(
        state: HomeWorkflowState,
        transform: (HomeRecommendation) -> HomeRecommendation,
    ): HomeWorkflowState {
        val current = state.recommendation ?: return state
        val transformed = transform(current)
        val updated = transformed.copy(
            skills = transformed.skills.mapIndexed { index, skill ->
                skill.copy(position = index)
            },
        )
        return state.copy(
            step = HomeWorkflowStep.EDITING_RECOMMENDATION,
            recommendation = updated,
            recommendationConfirmed = false,
            contextSelection = HomeContextSelectionSnapshot(),
            executionConsent = HomeExecutionConsentSnapshot(),
            finalConfirmationReady = false,
            errorCode = null,
        )
    }

    private fun HomeRecommendation.normalizeMode(): HomeRecommendation = copy(
        mode = if (selectedSkills.size <= 1) RecommendationMode.SINGLE else RecommendationMode.MULTI,
        modeReason = if (selectedSkills.size <= 1) {
            "当前选择由一个 Skill 聚焦处理。"
        } else {
            "当前选择由多个 Skill 互补分工。"
        },
    )
}
