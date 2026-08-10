package com.elio.jianyu.ui.screens.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.result.ArtifactType
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard
import com.elio.jianyu.ui.navigation.ResourceTab

@Composable
fun ResourcesScreen(
    selectedTab: ResourceTab,
    onSelectTab: (ResourceTab) -> Unit,
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
    onEditPersonalContext: (PersonalContextUiItem) -> Unit = {},
    onMaterialLifecycle: (MaterialUiItem, ContextSourceLifecycle) -> Unit = { _, _ -> },
    onPersonalContextLifecycle: (PersonalContextUiItem, ContextSourceLifecycle) -> Unit = { _, _ -> },
    onRequestMaterialPurge: (MaterialUiItem) -> Unit = {},
    onRequestPersonalContextPurge: (PersonalContextUiItem) -> Unit = {},
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
) {
    JianyuPageShell(
        title = "资料与成果",
        subtitle = null,
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
                "资料、个人背景和成果保持不同对象语义。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TabRow(selectedTabIndex = selectedTab.ordinal) {
            Tab(
                selected = selectedTab == ResourceTab.MATERIALS,
                onClick = { onSelectTab(ResourceTab.MATERIALS) },
                text = { Text("资料") },
                modifier = Modifier.testTag(ResourcesTestTags.MATERIALS_TAB),
            )
            Tab(
                selected = selectedTab == ResourceTab.ARTIFACTS,
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
            )
            ResourceTab.MATERIALS -> ResourceLibraryContent(
                state = state,
                onRetry = onRetry,
                onSelectSection = onSelectSection,
                onQueryChange = onQueryChange,
                onLifecyclesChange = onLifecyclesChange,
                onAdd = onAdd,
                onEditMaterial = onEditMaterial,
                onEditPersonalContext = onEditPersonalContext,
                onMaterialLifecycle = onMaterialLifecycle,
                onPersonalContextLifecycle = onPersonalContextLifecycle,
                onRequestMaterialPurge = onRequestMaterialPurge,
                onRequestPersonalContextPurge = onRequestPersonalContextPurge,
            )
        }
    }

    val content = state as? ResourcesUiState.Content
    content?.editor?.let { draft ->
        ResourceEditorDialog(
            draft = draft,
            issues = content.issues,
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
    onEditMaterial: (MaterialUiItem) -> Unit,
    onEditPersonalContext: (PersonalContextUiItem) -> Unit,
    onMaterialLifecycle: (MaterialUiItem, ContextSourceLifecycle) -> Unit,
    onPersonalContextLifecycle: (PersonalContextUiItem, ContextSourceLifecycle) -> Unit,
    onRequestMaterialPurge: (MaterialUiItem) -> Unit,
    onRequestPersonalContextPurge: (PersonalContextUiItem) -> Unit,
) {
    when (state) {
        ResourcesUiState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator()
            Text("正在读取资料与个人背景")
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
            TabRow(
                selectedTabIndex = state.section.ordinal,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = state.section == ResourceLibrarySection.MATERIALS,
                    onClick = { onSelectSection(ResourceLibrarySection.MATERIALS) },
                    text = { Text("资料") },
                    modifier = Modifier.testTag(ResourcesTestTags.MATERIAL_LIBRARY),
                )
                Tab(
                    selected = state.section == ResourceLibrarySection.PERSONAL_CONTEXTS,
                    onClick = { onSelectSection(ResourceLibrarySection.PERSONAL_CONTEXTS) },
                    text = { Text("个人背景") },
                    modifier = Modifier.testTag(ResourcesTestTags.PERSONAL_CONTEXT_LIBRARY),
                )
            }
            Text(
                if (state.section == ResourceLibrarySection.MATERIALS) {
                    "资料必须关联议题，可选关联阶段；已关联不等于自动发送。"
                } else {
                    "个人背景可跨议题复用，但每次执行默认不勾选。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                label = { Text("搜索标题或来源类型") },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ResourcesTestTags.SEARCH),
            )
            ResourceLifecycleFilters(state.lifecycles, onLifecyclesChange)
            Button(
                onClick = onAdd,
                enabled = !state.operationInProgress,
                modifier = Modifier.testTag(ResourcesTestTags.ADD),
            ) {
                Text(
                    if (state.section == ResourceLibrarySection.MATERIALS) "新建资料"
                    else "新建个人背景",
                )
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
            when (state.section) {
                ResourceLibrarySection.MATERIALS -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(JianyuAutomationTags.Resources.MATERIALS_CONTENT),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.visibleMaterials.isEmpty()) {
                        JianyuStateCard(
                            title = "暂无资料",
                            message = "可粘贴文本、保存手动笔记，或记录 URL 与用户提供的摘录。",
                            modifier = Modifier.testTag(ResourcesTestTags.EMPTY_STATE),
                        )
                    } else {
                        state.visibleMaterials.forEach { item ->
                            MaterialCard(
                                item = item,
                                onEdit = { onEditMaterial(item) },
                                onLifecycle = { onMaterialLifecycle(item, it) },
                                onRequestPurge = { onRequestMaterialPurge(item) },
                            )
                        }
                    }
                }
                ResourceLibrarySection.PERSONAL_CONTEXTS -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(JianyuAutomationTags.Resources.PERSONAL_CONTEXT_CONTENT),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.visiblePersonalContexts.isEmpty()) {
                        JianyuStateCard(
                            title = "暂无匹配个人背景",
                            message = "背景条目不会在应用启动或创建议题时自动加入模型上下文。",
                            modifier = Modifier.testTag(ResourcesTestTags.EMPTY_STATE),
                        )
                    } else {
                        state.visiblePersonalContexts.forEach { item ->
                            PersonalContextCard(
                                item = item,
                                onEdit = { onEditPersonalContext(item) },
                                onLifecycle = { onPersonalContextLifecycle(item, it) },
                                onRequestPurge = { onRequestPersonalContextPurge(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}
