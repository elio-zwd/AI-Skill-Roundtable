package com.elio.jianyu.ui.screens.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.execution.ExecutionRunCoordinator
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.screens.context.ContextConfirmationDialog

object HomeTestTags {
    const val SCREEN = JianyuAutomationTags.Screen.HOME
    const val QUESTION_INPUT = JianyuAutomationTags.Home.QUESTION_INPUT
    const val RECOMMENDATION_REQUEST_BUTTON =
        JianyuAutomationTags.Home.RECOMMENDATION_REQUEST_BUTTON
    const val RECOMMENDATION_RESULT = JianyuAutomationTags.Home.RECOMMENDATION_RESULT
    const val RECOMMENDATION_CONFIRM_BUTTON =
        JianyuAutomationTags.Home.RECOMMENDATION_CONFIRM_BUTTON
    const val CONTEXT_CONFIRMATION_BUTTON =
        JianyuAutomationTags.Home.CONTEXT_CONFIRMATION_BUTTON
    const val CONTEXT_CONFIRMED_SUMMARY =
        JianyuAutomationTags.Home.CONTEXT_CONFIRMED_SUMMARY
    const val NETWORK_AUTHORIZATION = "home_execution_network_authorization"
    const val HIGH_STAKES_CONFIRMATION = "home_execution_high_stakes_confirmation"
    const val PERSON_DISCLAIMER_CONFIRMATION = "home_execution_person_disclaimer_confirmation"
    const val RESTRICTED_MATERIAL_BLOCK = "home_execution_restricted_material_block"
}

@Composable
fun HomeRoute(
    repository: JianyuRepository,
    catalogRuntimeResult: OfficialSkillCatalogRuntimeResult,
    executionCoordinator: ExecutionRunCoordinator?,
    onOpenSettings: () -> Unit,
    onNavigateToIssue: (issueId: String, stageId: String) -> Unit,
    onOpenSkillCatalog: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            repository = repository,
            catalogRuntimeResult = catalogRuntimeResult,
            coordinator = executionCoordinator,
        ),
    ),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(viewModel, onNavigateToIssue, onOpenSkillCatalog) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is HomeNavigationEvent.NavigateToIssue -> onNavigateToIssue(
                    event.issueId,
                    event.stageId,
                )
                HomeNavigationEvent.OpenSkillCatalog -> onOpenSkillCatalog()
            }
        }
    }

    HomeScreen(
        uiState = uiState,
        onQuestionChanged = viewModel::onQuestionChanged,
        onClearQuestion = viewModel::clearQuestion,
        onToggleDirection = viewModel::toggleDirection,
        onUseExample = viewModel::useExample,
        onRequestRecommendation = viewModel::requestRecommendation,
        onSaveIssueOnly = viewModel::saveIssueOnly,
        onToggleSkill = viewModel::toggleSkill,
        onResponsibilityChanged = viewModel::updateSkillResponsibility,
        onMoveSkill = viewModel::moveSkill,
        onModeChanged = viewModel::switchMode,
        onConfirmRecommendation = viewModel::confirmRecommendation,
        onOpenContextConfirmation = viewModel::openContextConfirmation,
        onNetworkAuthorized = viewModel::setNetworkAuthorized,
        onHighStakesConfirmed = viewModel::setHighStakesConfirmed,
        onPersonDisclaimerConfirmed = viewModel::setPersonDisclaimerConfirmed,
        onBrowseSkills = viewModel::browseSkills,
        onStartIssue = viewModel::startIssue,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
    )

    uiState.contextConfirmation?.let { confirmation ->
        ContextConfirmationDialog(
            state = confirmation,
            onDismiss = viewModel::dismissContextConfirmation,
            onToggleSelected = viewModel::toggleContextCandidate,
            onNetworkAllowed = viewModel::setContextNetworkAllowed,
            onSensitiveConfirmed = viewModel::setContextSensitiveConfirmed,
            onExcerptChanged = viewModel::updateContextExcerpt,
            onConfirm = viewModel::confirmContext,
        )
    }
}
