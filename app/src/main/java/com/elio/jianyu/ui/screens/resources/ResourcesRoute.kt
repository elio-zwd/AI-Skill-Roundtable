package com.elio.jianyu.ui.screens.resources

// 稳定导航测试标签：resources_tab_materials / resources_tab_artifacts

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.skill.knowledge.SkillKnowledgeRepository
import com.elio.jianyu.skill.knowledge.SkillKnowledgeSelection
import com.elio.jianyu.ui.navigation.ResourceTab
import kotlinx.coroutines.launch

@Composable
fun ResourcesRoute(
    repository: JianyuRepository,
    skillKnowledgeRepository: SkillKnowledgeRepository,
    initialTab: ResourceTab,
    onOpenSettings: () -> Unit,
    onOpenIssue: (String, String) -> Unit = { _, _ -> },
    onUseSkillKnowledgeInConversation: (SkillKnowledgeSelection) -> Boolean = { false },
    viewModel: ResourcesViewModel = viewModel(factory = ResourcesViewModel.factory(repository)),
    artifactViewModel: ArtifactLibraryViewModel = viewModel(
        factory = ArtifactLibraryViewModel.factory(repository),
    ),
    skillKnowledgeViewModel: SkillKnowledgeViewModel = viewModel(
        factory = SkillKnowledgeViewModel.factory(skillKnowledgeRepository),
    ),
) {
    var selectedRouteValue by rememberSaveable(initialTab.routeValue) {
        mutableStateOf(initialTab.routeValue)
    }
    var showOverview by rememberSaveable(initialTab.routeValue) {
        mutableStateOf(initialTab == ResourceTab.MATERIALS)
    }
    var addMaterialSheetVisible by rememberSaveable { mutableStateOf(false) }
    var focusMaterialSearch by rememberSaveable { mutableStateOf(false) }
    var showSkillKnowledge by rememberSaveable { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    val artifactState by artifactViewModel.state.collectAsState()
    val skillKnowledgeState by skillKnowledgeViewModel.state.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                when (val result = MaterialFileImporter.import(context.applicationContext, uri)) {
                    is MaterialFileImportResult.Success -> viewModel.openImportedMaterial(result.file)
                    is MaterialFileImportResult.Failure -> viewModel.reportMaterialImportFailure(result.message)
                }
            }
        }
    }
    val selectedSkillKnowledgeDocument =
        (skillKnowledgeState as? SkillKnowledgeUiState.Content)?.selectedDocument != null
    BackHandler(enabled = showSkillKnowledge || !showOverview) {
        when {
            showSkillKnowledge && selectedSkillKnowledgeDocument ->
                skillKnowledgeViewModel.dismissDocument()
            showSkillKnowledge -> {
                showSkillKnowledge = false
                showOverview = true
            }
            else -> showOverview = true
        }
    }
    ResourcesScreen(
        showOverview = showOverview,
        showSkillKnowledge = showSkillKnowledge,
        skillKnowledgeState = skillKnowledgeState,
        addMaterialSheetVisible = addMaterialSheetVisible,
        requestMaterialSearchFocus = focusMaterialSearch,
        selectedTab = ResourceTab.fromRouteValue(selectedRouteValue),
        state = state,
        artifactState = artifactState,
        onSelectTab = { tab ->
            selectedRouteValue = tab.routeValue
            showOverview = false
        },
        onBackToOverview = {
            showSkillKnowledge = false
            showOverview = true
        },
        onShowMaterials = {
            focusMaterialSearch = false
            selectedRouteValue = ResourceTab.MATERIALS.routeValue
            viewModel.selectSection(ResourceLibrarySection.MATERIALS)
            showOverview = false
        },
        onShowArtifacts = {
            focusMaterialSearch = false
            selectedRouteValue = ResourceTab.ARTIFACTS.routeValue
            showSkillKnowledge = false
            showOverview = false
        },
        onShowSkillKnowledge = {
            focusMaterialSearch = false
            showSkillKnowledge = true
            showOverview = false
        },
        onSkillKnowledgeRetry = skillKnowledgeViewModel::refresh,
        onSkillKnowledgeQueryChange = skillKnowledgeViewModel::updateQuery,
        onOpenSkillKnowledgeDocument = skillKnowledgeViewModel::openDocument,
        onDismissSkillKnowledgeDocument = skillKnowledgeViewModel::dismissDocument,
        onUseSkillKnowledgeInConversation = { selection ->
            val success = onUseSkillKnowledgeInConversation(selection)
            skillKnowledgeViewModel.reportUseResult(success)
        },
        onSearchMaterials = {
            focusMaterialSearch = true
            selectedRouteValue = ResourceTab.MATERIALS.routeValue
            viewModel.selectSection(ResourceLibrarySection.MATERIALS)
            showOverview = false
        },
        onOpenAddMaterialSheet = { addMaterialSheetVisible = true },
        onDismissAddMaterialSheet = { addMaterialSheetVisible = false },
        onChooseMaterialKind = { sourceKind ->
            addMaterialSheetVisible = false
            if (sourceKind == "file") {
                filePicker.launch(
                    arrayOf(
                        "text/*",
                        "application/json",
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    ),
                )
            } else {
                viewModel.openNewMaterial(sourceKind)
            }
        },
        onOpenSettings = onOpenSettings,
        onRetry = viewModel::refresh,
        onSelectSection = viewModel::selectSection,
        onQueryChange = viewModel::updateQuery,
        onLifecyclesChange = viewModel::selectLifecycles,
        onAdd = { addMaterialSheetVisible = true },
        onEditMaterial = viewModel::editMaterial,
        onOpenMaterial = viewModel::openMaterial,
        onDismissMaterial = viewModel::dismissMaterial,
        onMaterialLifecycle = viewModel::changeMaterialLifecycle,
        onRequestMaterialPurge = viewModel::requestMaterialPurge,
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
        onCopyArtifact = { artifact -> clipboard.setText(AnnotatedString(artifact.content)) },
    )
}
