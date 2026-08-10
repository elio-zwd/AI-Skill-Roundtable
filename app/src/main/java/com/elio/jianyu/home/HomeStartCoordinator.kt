package com.elio.jianyu.home

import com.elio.jianyu.data.ConfirmedContextItem
import com.elio.jianyu.data.ContextContentHasher
import com.elio.jianyu.data.ContextSelectionDraft
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.MAX_EXECUTION_CONTEXT_CHARACTERS
import com.elio.jianyu.data.PrepareExecutionContextCommand
import com.elio.jianyu.data.PreparedExecutionContext
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.SaveIssueCommand
import com.elio.jianyu.execution.ExecutionRunCoordinator
import com.elio.jianyu.execution.ExecutionSkillSelection
import com.elio.jianyu.execution.ExecutionStartCommand
import com.elio.jianyu.execution.ExecutionStartException
import com.elio.jianyu.skill.catalog.OfficialSkillExecutionContext
import com.elio.jianyu.skill.catalog.OfficialSkillExecutionSelectedMode

interface HomeRepositoryGateway {
    suspend fun saveIssue(command: SaveIssueCommand): RepositoryResult<Unit>

    suspend fun prepareExecutionContext(
        command: PrepareExecutionContextCommand,
    ): RepositoryResult<PreparedExecutionContext>
}

class JianyuHomeRepositoryGateway(
    private val repository: JianyuRepository,
) : HomeRepositoryGateway {
    override suspend fun saveIssue(command: SaveIssueCommand): RepositoryResult<Unit> =
        when (val result = repository.saveIssue(command)) {
            is RepositoryResult.Success -> RepositoryResult.Success(
                value = Unit,
                idempotent = result.idempotent,
            )
            is RepositoryResult.Failure -> result
        }

    override suspend fun prepareExecutionContext(
        command: PrepareExecutionContextCommand,
    ): RepositoryResult<PreparedExecutionContext> = repository.prepareExecutionContext(command)
}

sealed interface HomeExecutionStartResult {
    data class Started(val runId: String) : HomeExecutionStartResult
    data class Failure(val errorCode: String) : HomeExecutionStartResult
}

fun interface HomeExecutionStarter {
    suspend fun start(command: ExecutionStartCommand): HomeExecutionStartResult
}

class CoordinatorHomeExecutionStarter(
    private val coordinator: ExecutionRunCoordinator?,
) : HomeExecutionStarter {
    override suspend fun start(command: ExecutionStartCommand): HomeExecutionStartResult {
        val runtimeCoordinator = coordinator
            ?: return HomeExecutionStartResult.Failure("execution_unavailable")
        return try {
            runtimeCoordinator.start(command)
            HomeExecutionStartResult.Started(command.runId)
        } catch (error: ExecutionStartException) {
            HomeExecutionStartResult.Failure(error.failure.code.storageValue)
        } catch (_: IllegalArgumentException) {
            HomeExecutionStartResult.Failure("invalid_execution_command")
        } catch (_: IllegalStateException) {
            HomeExecutionStartResult.Failure("execution_failure")
        }
    }
}

class HomeStartCoordinator(
    private val repository: HomeRepositoryGateway,
    private val executionStarter: HomeExecutionStarter,
) {
    suspend fun saveOnly(command: HomeSaveOnlyCommand): HomeStartResult {
        val saveCommand = command.toSaveIssueCommand()
        return when (val result = repository.saveIssue(saveCommand)) {
            is RepositoryResult.Success -> HomeStartResult.SavedOnly(
                issueId = command.ids.issueId,
                stageId = command.ids.stageId,
            )
            is RepositoryResult.Failure -> HomeStartResult.Failure(result.error.safeCode())
        }
    }

    suspend fun start(confirmation: HomeFinalConfirmation): HomeStartResult {
        if (!confirmation.contextSelection.confirmed) {
            return HomeStartResult.Failure(HomeWorkflowError.CONTEXT_CONFIRMATION_REQUIRED.code)
        }
        val selected = confirmation.recommendation.selectedSkills
        if (selected.isEmpty() || selected.any { !it.executable }) {
            return HomeStartResult.Failure(HomeWorkflowError.NO_EXECUTABLE_SKILL.code)
        }

        val preflightState = HomeWorkflowState(
            ids = confirmation.ids,
            draft = HomeQuestionDraft(confirmation.question, confirmation.directions),
            step = HomeWorkflowStep.FINAL_REVIEW,
            recommendation = confirmation.recommendation,
            recommendationConfirmed = true,
            contextSelection = confirmation.contextSelection,
            executionConsent = confirmation.executionConsent,
        )
        val preflightIssue = HomeWorkflow.executionConsentIssues(preflightState).firstOrNull()
        if (preflightIssue != null) {
            return HomeStartResult.Failure(preflightIssue)
        }

        val selectionDraft = confirmation.contextSelection.toContextSelectionDraft(confirmation.ids)
            ?: return HomeStartResult.Failure(HomeWorkflowError.CONTEXT_CONFIRMATION_REQUIRED.code)
        val executionContexts = selected.associate { skill ->
            skill.skillId to confirmation.toExecutionContext()
        }

        val saveCommand = HomeSaveOnlyCommand(
            ids = confirmation.ids,
            question = confirmation.question,
            createdAt = confirmation.confirmedAt,
        ).toSaveIssueCommand()
        when (val save = repository.saveIssue(saveCommand)) {
            is RepositoryResult.Failure -> return HomeStartResult.Failure(save.error.safeCode())
            is RepositoryResult.Success -> Unit
        }

        val prepared = when (
            val result = repository.prepareExecutionContext(
                PrepareExecutionContextCommand(
                    draft = selectionDraft,
                    preparedAt = confirmation.confirmedAt,
                ),
            )
        ) {
            is RepositoryResult.Success -> result.value
            is RepositoryResult.Failure -> return HomeStartResult.SavedNotStarted(
                issueId = confirmation.ids.issueId,
                stageId = confirmation.ids.stageId,
                errorCode = result.error.safeCode(),
            )
        }

        val executionCommand = ExecutionStartCommand(
            runId = confirmation.ids.runId,
            issueId = confirmation.ids.issueId,
            stageId = confirmation.ids.stageId,
            triggerMessageId = null,
            idempotencyKey = confirmation.ids.executionIdempotencyKey,
            selections = selected.sortedBy(RecommendedSkill::position).map { skill ->
                ExecutionSkillSelection(
                    officialSkillId = skill.skillId,
                    defaultResponsibility = skill.responsibility,
                    executionContext = executionContexts.getValue(skill.skillId),
                )
            },
            currentUserInput = confirmation.question,
            roundIndex = 0,
            userConfirmedAt = confirmation.confirmedAt,
            thinkingOverride = confirmation.thinkingOverride,
            contributions = prepared.preparation.contributions,
            contextUsage = prepared.usage,
        )
        return when (val result = executionStarter.start(executionCommand)) {
            is HomeExecutionStartResult.Started -> HomeStartResult.Started(
                issueId = confirmation.ids.issueId,
                stageId = confirmation.ids.stageId,
                runId = result.runId,
            )
            is HomeExecutionStartResult.Failure -> HomeStartResult.SavedNotStarted(
                issueId = confirmation.ids.issueId,
                stageId = confirmation.ids.stageId,
                errorCode = result.errorCode,
            )
        }
    }

    private fun HomeFinalConfirmation.toExecutionContext(): OfficialSkillExecutionContext {
        val selectedItems = contextSelection.items.filter(HomeContextItemSnapshot::selected)
        val sensitiveItems = selectedItems.filter(HomeContextItemSnapshot::sensitive)
        return OfficialSkillExecutionContext(
            materialProvided = selectedItems.isNotEmpty(),
            materialAuthorized = selectedItems.isNotEmpty() &&
                selectedItems.all { it.userConfirmedAt > 0L },
            sensitiveMaterialConfirmed = sensitiveItems.isEmpty() ||
                sensitiveItems.all(HomeContextItemSnapshot::sensitiveConfirmed),
            networkAuthorized = executionConsent.networkAuthorized,
            containsRestrictedMaterial = executionConsent.restrictedMaterialPresent,
            materialMayLeaveDevice = executionConsent.materialMayLeaveDevice,
            highStakesConfirmed = executionConsent.highStakesConfirmed,
            personDisclaimerConfirmed = executionConsent.personDisclaimerConfirmed,
            contextCharacters = contextSelection.baseContextCharacters +
                selectedItems.sumOf { it.selectedContent.length },
            maxContextCharacters = MAX_EXECUTION_CONTEXT_CHARACTERS,
            selectedMode = if (recommendation.selectedSkills.size == 1) {
                OfficialSkillExecutionSelectedMode.SINGLE
            } else {
                OfficialSkillExecutionSelectedMode.MULTI
            },
            stageExecutable = true,
        )
    }

    private fun HomeSaveOnlyCommand.toSaveIssueCommand(): SaveIssueCommand {
        val normalized = question.trim()
        return SaveIssueCommand(
            issueId = ids.issueId,
            title = normalized.take(60),
            initialStageId = ids.stageId,
            initialStageTitle = "初始阶段",
            initialObjective = normalized,
            createdAt = createdAt,
        )
    }

    private fun HomeContextSelectionSnapshot.toContextSelectionDraft(
        ids: HomeWorkflowIds,
    ): ContextSelectionDraft? {
        if (!confirmed) return null
        val selectedItems = items.filter(HomeContextItemSnapshot::selected)
        if (selectedItems.any { it.userConfirmedAt <= 0L || !it.networkAllowed }) return null
        return ContextSelectionDraft(
            issueId = ids.issueId,
            stageId = ids.stageId,
            runId = ids.runId,
            baseContextCharacters = baseContextCharacters,
            items = selectedItems.sortedWith(
                compareBy(HomeContextItemSnapshot::confirmationOrder)
                    .thenBy(HomeContextItemSnapshot::sourceType)
                    .thenBy(HomeContextItemSnapshot::sourceId),
            ).map { item ->
                val sourceType = ContextSourceType.entries.firstOrNull {
                    it.storageValue == item.sourceType
                } ?: return null
                ConfirmedContextItem(
                    sourceType = sourceType,
                    sourceId = item.sourceId,
                    title = item.title,
                    sourceKind = item.sourceKind,
                    sourceLocator = item.sourceLocator,
                    sourcePublishedAt = item.sourcePublishedAt,
                    sourceCapturedAt = item.sourceCapturedAt,
                    content = item.selectedContent,
                    contentHash = ContextContentHasher.hash(item.selectedContent),
                    expectedSourceHash = item.sourceHash,
                    expectedSourceUpdatedAt = item.sourceUpdatedAt,
                    confirmationOrder = item.confirmationOrder,
                    userConfirmedAt = item.userConfirmedAt,
                    networkAllowed = item.networkAllowed,
                    sensitive = item.sensitive,
                    sensitiveConfirmed = item.sensitiveConfirmed,
                )
            },
            confirmed = true,
        )
    }
}

internal fun RepositoryError.safeCode(): String = when (this) {
    is RepositoryError.NotFound -> "not_found"
    is RepositoryError.AlreadyExists -> "already_exists"
    is RepositoryError.IdempotencyConflict -> "idempotency_conflict"
    is RepositoryError.InvalidState -> stateCode
    is RepositoryError.ConstraintViolation -> constraintCode
    is RepositoryError.StorageFailure -> "storage_failure"
    is RepositoryError.CompatibilityFailure -> compatibilityCode
}
