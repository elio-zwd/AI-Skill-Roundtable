package com.elio.jianyu.ui.screens.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.result.ArtifactType
import com.elio.jianyu.skill.knowledge.SkillKnowledgeSelection
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard
import com.elio.jianyu.ui.navigation.ResourceTab

@Composable
fun ResourcesScreen(
    showOverview: Boolean = false,
    showSkillKnowledge: Boolean = false,
    skillKnowledgeState: SkillKnowledgeUiState = SkillKnowledgeUiState.Loading,
    addMaterialSheetVisible: Boolean = false,
    requestMaterialSearchFocus: Boolean = false,
    selectedTab: ResourceTab,
    onSelectTab: (ResourceTab) -> Unit,
    onBackToOverview: () -> Unit = {},
    onShowMaterials: () -> Unit = {},
    onShowArtifacts: () -> Unit = {},
    onShowSkillKnowledge: () -> Unit = {},
    onSkillKnowledgeRetry: () -> Unit = {},
    onSkillKnowledgeQueryChange: (String) -> Unit = {},
    onOpenSkillKnowledgeDocument: (String) -> Unit = {},
    onDismissSkillKnowledgeDocument: () -> Unit = {},
    onUseSkillKnowledgeInConversation: (SkillKnowledgeSelection) -> Unit = {},
    onSearchMaterials: () -> Unit = {},
    onOpenAddMaterialSheet: () -> Unit = {},
    onDismissAddMaterialSheet: () -> Unit = {},
    onChooseMaterialKind: (String) -> Unit = {},
    onOpenSettings: () -> Unit,
    state: ResourcesUiState = ResourcesUiState.Content(),
    artifactState: ArtifactLibraryUiState =
        (state as? ResourcesUiState.Content)?.artifactLibrary ?: ArtifactLibraryUiState.Loading,
    onRetry: () -> Unit = {},
    onSelectSection: (ResourceLibrarySection) -> Unit = {},
    onQueryChange: (String) -> Unit = {},
    onLifecyclesChange: (Set<ContextSourceLifecycle>) -> Unit = {},
    onAdd: () -> Unit = {},
    onEditMaterial: (MaterialUiItem) -> Unit = {},
    onOpenMaterial: (MaterialUiItem) -> Unit = {},
    onDismissMaterial: () -> Unit = {},
    onMaterialLifecycle: (MaterialUiItem, ContextSourceLifecycle) -> Unit = { _, _ -> },
    onRequestMaterialPurge: (MaterialUiItem) -> Unit = {},
    onEditorChange: (ResourceEditorDraft) -> Unit = {},
    onDismissEditor: () -> Unit = {},
    onSaveEditor: () -> Unit = {},
    onDismissPurge: () -> Unit = {},
    onConfirmPurge: () -> Unit = {},
    onArtifactRetry: () -> Unit = {},
    onArtifactQueryChange: (String) -> Unit = {},
    onArtifactTypesChange: (Set<ArtifactType>) -> Unit = {},
    onArtifactHistoryChange: (Boolean) -> Unit = {},
    onOpenArtifact: (String) -> Unit = {},
    onDismissArtifact: () -> Unit = {},
    onOpenArtifactIssue: (String, String) -> Unit = { _, _ -> },
    onCopyArtifact: (com.elio.jianyu.result.ArtifactLibraryItem) -> Unit = {},
) {
    if (showSkillKnowledge) {
        SkillKnowledgeScreen(
            state = skillKnowledgeState,
            onBack = onBackToOverview,
            onRetry = onSkillKnowledgeRetry,
            onQueryChange = onSkillKnowledgeQueryChange,
            onOpenDocument = onOpenSkillKnowledgeDocument,
            onDismissDocument = onDismissSkillKnowledgeDocument,
            onUseInConversation = onUseSkillKnowledgeInConversation,
            onOpenSettings = onOpenSettings,
        )
    } else if (showOverview) {
        Box(modifier = Modifier.fillMaxSize().testTag(ResourcesTestTags.SCREEN)) {
            val knowledgeContent = skillKnowledgeState as? SkillKnowledgeUiState.Content
            ResourcesOverviewScreen(
                overview = buildResourceOverview(state, artifactState),
                onSearchMaterials = onSearchMaterials,
                onAddMaterial = onOpenAddMaterialSheet,
                onShowMaterials = onShowMaterials,
                onShowArtifacts = onShowArtifacts,
                skillKnowledgeCount = knowledgeContent?.documents?.size ?: 0,
                skillKnowledgeUnavailable = skillKnowledgeState !is SkillKnowledgeUiState.Content,
                onShowSkillKnowledge = onShowSkillKnowledge,
                onOpenMaterial = onOpenMaterial,
                onOpenArtifact = onOpenArtifact,
                onOpenSettings = onOpenSettings,
            )
        }
    } else {
        JianyuPageShell(
        title = if (selectedTab == ResourceTab.ARTIFACTS) "全部成果" else "全部资料",
        subtitle = null,
        onBack = onBackToOverview,
        onOpenSettings = onOpenSettings,
        contentScrollable = true,
        modifier = Modifier.testTag(ResourcesTestTags.SCREEN),
        ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("让判断可回看", style = MaterialTheme.typography.headlineMedium)
            Text(
                "资料和成果保持不同对象语义；个人背景请从【我的】管理。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val selectedLibraryTab = if (selectedTab == ResourceTab.ARTIFACTS) 1 else 0
        TabRow(selectedTabIndex = selectedLibraryTab) {
            Box(modifier = Modifier.testTag(ResourcesTestTags.MATERIAL_LIBRARY)) {
                Tab(
                    selected = selectedLibraryTab == 0,
                    onClick = {
                        onSelectTab(ResourceTab.MATERIALS)
                        onSelectSection(ResourceLibrarySection.MATERIALS)
                    },
                    text = { Text("资料") },
                    modifier = Modifier.testTag(ResourcesTestTags.MATERIALS_TAB),
                )
            }
            Tab(
                selected = selectedLibraryTab == 1,
                onClick = { onSelectTab(ResourceTab.ARTIFACTS) },
                text = { Text("成果") },
                modifier = Modifier.testTag(ResourcesTestTags.ARTIFACTS_TAB),
            )
        }

        when (selectedTab) {
            ResourceTab.ARTIFACTS -> ArtifactLibraryContent(
                state = artifactState,
                onRetry = onArtifactRetry,
                onQueryChange = onArtifactQueryChange,
                onTypesChange = onArtifactTypesChange,
                onIncludeHistoryChange = onArtifactHistoryChange,
                onOpenArtifact = onOpenArtifact,
                onDismissArtifact = onDismissArtifact,
                onOpenIssue = onOpenArtifactIssue,
                onCopyArtifact = onCopyArtifact,
            )
            ResourceTab.MATERIALS -> ResourceLibraryContent(
                state = state,
                onRetry = onRetry,
                onSelectSection = onSelectSection,
                onQueryChange = onQueryChange,
                onLifecyclesChange = onLifecyclesChange,
                onAdd = onAdd,
                requestSearchFocus = requestMaterialSearchFocus,
                onOpenMaterial = onOpenMaterial,
                onEditMaterial = onEditMaterial,
                onMaterialLifecycle = onMaterialLifecycle,
                onRequestMaterialPurge = onRequestMaterialPurge,
            )
        }
    }
    }

    val content = state as? ResourcesUiState.Content
    content?.selectedMaterial?.let { material ->
        MaterialDetailDialog(
            item = material,
            onDismiss = onDismissMaterial,
            onEdit = { onEditMaterial(material) },
            onLifecycle = { onMaterialLifecycle(material, it) },
            onRequestPurge = { onRequestMaterialPurge(material) },
        )
    }

    if (addMaterialSheetVisible) {
        AddMaterialSheet(
            onDismiss = onDismissAddMaterialSheet,
            onChoose = onChooseMaterialKind,
        )
    }
    content?.editor?.let { draft ->
        ResourceEditorDialog(
            draft = draft,
            issues = content.issues,
            message = content.partialFailure,
            saving = content.operationInProgress,
            onChange = onEditorChange,
            onDismiss = onDismissEditor,
            onSave = onSaveEditor,
        )
    }
    content?.purgeConfirmation?.let { confirmation ->
        PurgeConfirmationDialog(
            confirmation = confirmation,
            onDismiss = onDismissPurge,
            onConfirm = onConfirmPurge,
        )
    }
}

@Composable
private fun ResourceLibraryContent(
    state: ResourcesUiState,
    onRetry: () -> Unit,
    onSelectSection: (ResourceLibrarySection) -> Unit,
    onQueryChange: (String) -> Unit,
    onLifecyclesChange: (Set<ContextSourceLifecycle>) -> Unit,
    onAdd: () -> Unit,
    requestSearchFocus: Boolean,
    onOpenMaterial: (MaterialUiItem) -> Unit,
    onEditMaterial: (MaterialUiItem) -> Unit,
    onMaterialLifecycle: (MaterialUiItem, ContextSourceLifecycle) -> Unit,
    onRequestMaterialPurge: (MaterialUiItem) -> Unit,
) {
    when (state) {
        ResourcesUiState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("正在读取资料")
            Text(
                "加载只读取本地资料库，不会把正文发送到网络。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        is ResourcesUiState.Failure -> JianyuStateCard(
            title = "资料库读取失败",
            message = state.message,
            actionLabel = "重试",
            onAction = onRetry,
        )
        is ResourcesUiState.Content -> {
            val searchFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
            LaunchedEffect(requestSearchFocus) {
                if (requestSearchFocus) searchFocusRequester.requestFocus()
            }
            Text(
                "资料必须关联会话，可选关联对话节点；已关联不等于自动发送。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("搜索标题或来源类型") },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(searchFocusRequester)
                    .testTag(ResourcesTestTags.SEARCH),
            )
            ResourceLifecycleFilters(state.lifecycles, onLifecyclesChange)
            Button(
                onClick = onAdd,
                enabled = !state.operationInProgress,
                modifier = Modifier.testTag(ResourcesTestTags.ADD),
            ) {
                Text("新建资料")
            }
            state.partialFailure?.let { message ->
                JianyuStateCard(title = "部分操作未完成", message = message)
            }
            if (state.operationInProgress) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("正在保存本地资料")
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(JianyuAutomationTags.Resources.MATERIALS_CONTENT),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.visibleMaterials.isEmpty()) {
                    val hasStoredMaterials = state.materials.isNotEmpty()
                    JianyuStateCard(
                        title = if (hasStoredMaterials) "暂无匹配资料" else "暂无资料",
                        message = if (hasStoredMaterials) {
                            "可调整搜索词或状态筛选。"
                        } else {
                            "可粘贴文本、保存手动笔记，或记录 URL 与用户提供的摘录。"
                        },
                        modifier = Modifier.testTag(ResourcesTestTags.EMPTY_STATE),
                    )
                } else {
                    state.visibleMaterials.forEach { item ->
                        MaterialCard(
                            item = item,
                            onOpen = { onOpenMaterial(item) },
                            onEdit = { onEditMaterial(item) },
                            onLifecycle = { onMaterialLifecycle(item, it) },
                            onRequestPurge = { onRequestMaterialPurge(item) },
                        )
                    }
                }
            }
        }
    }
}
