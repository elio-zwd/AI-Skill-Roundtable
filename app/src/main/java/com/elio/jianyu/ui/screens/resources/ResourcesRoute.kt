package com.elio.jianyu.ui.screens.resources

// 稳定导航测试标签：resources_tab_materials / resources_tab_artifacts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.ui.navigation.ResourceTab

@Composable
fun ResourcesRoute(
    repository: JianyuRepository,
    initialTab: ResourceTab,
    onOpenSettings: () -> Unit,
    onOpenIssue: (String, String) -> Unit = { _, _ -> },
    viewModel: ResourcesViewModel = viewModel(factory = ResourcesViewModel.factory(repository)),
    artifactViewModel: ArtifactLibraryViewModel = viewModel(
        factory = ArtifactLibraryViewModel.factory(repository),
    ),
) {
    var selectedRouteValue by rememberSaveable(initialTab.routeValue) {
        mutableStateOf(initialTab.routeValue)
    }
    val state by viewModel.state.collectAsState()
    val artifactState by artifactViewModel.state.collectAsState()
    ResourcesScreen(
        selectedTab = ResourceTab.fromRouteValue(selectedRouteValue),
        state = state,
        artifactState = artifactState,
        onSelectTab = { tab -> selectedRouteValue = tab.routeValue },
        onOpenSettings = onOpenSettings,
        onRetry = viewModel::refresh,
        onSelectSection = viewModel::selectSection,
        onQueryChange = viewModel::updateQuery,
        onLifecyclesChange = viewModel::selectLifecycles,
        onAdd = {
            val content = state as? ResourcesUiState.Content
            if (content?.section == ResourceLibrarySection.PERSONAL_CONTEXTS) {
                viewModel.openNewPersonalContext()
            } else {
                viewModel.openNewMaterial()
            }
        },
        onEditMaterial = viewModel::editMaterial,
        onEditPersonalContext = viewModel::editPersonalContext,
        onMaterialLifecycle = viewModel::changeMaterialLifecycle,
        onPersonalContextLifecycle = viewModel::changePersonalContextLifecycle,
        onRequestMaterialPurge = viewModel::requestMaterialPurge,
        onRequestPersonalContextPurge = viewModel::requestPersonalContextPurge,
        onEditorChange = viewModel::updateEditor,
        onDismissEditor = viewModel::dismissEditor,
        onSaveEditor = viewModel::saveEditor,
        onDismissPurge = viewModel::cancelPurge,
        onConfirmPurge = viewModel::confirmPurge,
        onArtifactRetry = artifactViewModel::refresh,
        onArtifactQueryChange = artifactViewModel::updateQuery,
        onArtifactTypesChange = artifactViewModel::selectTypes,
        onArtifactHistoryChange = artifactViewModel::setIncludeHistory,
        onOpenArtifact = artifactViewModel::openArtifact,
        onDismissArtifact = artifactViewModel::dismissArtifact,
        onOpenArtifactIssue = onOpenIssue,
    )
}
