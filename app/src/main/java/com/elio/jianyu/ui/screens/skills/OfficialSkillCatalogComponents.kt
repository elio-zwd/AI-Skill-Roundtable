package com.elio.jianyu.ui.screens.skills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elio.jianyu.data.OfficialSkillCombinationSnapshot
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogFilters
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.catalog.OfficialSkillMaterialRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillNetworkRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryValue
import com.elio.jianyu.skill.catalog.OfficialSkillPublicationStatus
import com.elio.jianyu.skill.catalog.OfficialSkillRiskLevel
import com.elio.jianyu.skill.catalog.OfficialSkillUseMode

@Composable
internal fun OfficialSkillCatalogHeader(
    query: String,
    totalSkillCount: Int,
    onQueryChanged: (String) -> Unit,
    onOpenFilters: () -> Unit,
) {
    Text(
        text = "官方 Skill",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        text = "共 $totalSkillCount 项。能找到不等于已经可以执行。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            label = { Text("搜索名称、ID、领域、场景或输出") },
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .testTag(OfficialSkillCatalogTestTags.SEARCH),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(
            onClick = onOpenFilters,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag(OfficialSkillCatalogTestTags.FILTER_BUTTON),
        ) {
            Text("筛选")
        }
    }
}

@Composable
internal fun OfficialSkillCatalogSectionTabs(
    selected: OfficialSkillCatalogSection,
    favoriteCount: Int,
    recentCount: Int,
    combinationCount: Int,
    onSelected: (OfficialSkillCatalogSection) -> Unit,
) {
    val tabs = listOf(
        OfficialSkillCatalogSection.DISCOVER to "发现",
        OfficialSkillCatalogSection.FAVORITES to "收藏 $favoriteCount",
        OfficialSkillCatalogSection.RECENT to "最近 $recentCount",
        OfficialSkillCatalogSection.COMBINATIONS to "组合 $combinationCount",
    )
    ScrollableTabRow(
        selectedTabIndex = tabs.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        edgePadding = 0.dp,
        divider = {},
    ) {
        tabs.forEach { (section, label) ->
            Tab(
                selected = section == selected,
                onClick = { onSelected(section) },
                text = { Text(label) },
                modifier = when (section) {
                    OfficialSkillCatalogSection.FAVORITES -> Modifier.testTag(
                        OfficialSkillCatalogTestTags.FAVORITES_TAB,
                    )
                    OfficialSkillCatalogSection.RECENT -> Modifier.testTag(
                        OfficialSkillCatalogTestTags.RECENT_TAB,
                    )
                    OfficialSkillCatalogSection.COMBINATIONS -> Modifier.testTag(
                        OfficialSkillCatalogTestTags.COMBINATIONS_TAB,
                    )
                    else -> Modifier
                },
            )
        }
    }
}

@Composable
internal fun OfficialSkillCatalogList(
    skills: List<OfficialSkillDefinition>,
    favoriteIds: Set<String>,
    emptyMessage: String,
    onOpenDetail: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
) {
    if (skills.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(OfficialSkillCatalogTestTags.EMPTY),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emptyMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag(OfficialSkillCatalogTestTags.LIST),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(skills, key = OfficialSkillDefinition::id) { skill ->
            OfficialSkillCard(
                skill = skill,
                isFavorite = skill.id in favoriteIds,
                onOpenDetail = { onOpenDetail(skill.id) },
                onToggleFavorite = { onToggleFavorite(skill.id) },
            )
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun OfficialSkillCard(
    skill: OfficialSkillDefinition,
    isFavorite: Boolean,
    onOpenDetail: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetail)
            .semantics { contentDescription = "${skill.nameZh}，官方 Skill 详情" }
            .testTag(OfficialSkillCatalogTestTags.skill(skill.id)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = skill.nameZh,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = skill.id,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag(OfficialSkillCatalogTestTags.favorite(skill.id)),
                ) {
                    Text(if (isFavorite) "取消收藏" else "收藏")
                }
            }
            Text(
                text = skill.summary,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .testTag(OfficialSkillCatalogTestTags.skillStatus(skill.id)),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                skill.statusLabels().forEach { label ->
                    AssistChip(onClick = {}, label = { Text(label) })
                }
            }
            skill.listBoundaryHint()?.let { hint ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun OfficialSkillCatalogFilterDialog(
    filters: OfficialSkillCatalogFilters,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            onEvent(OfficialSkillCatalogEvent.FilterDialogChanged(false))
        },
        modifier = Modifier.testTag(OfficialSkillCatalogTestTags.FILTER_DIALOG),
        title = { Text("筛选官方 Skill") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterGroup(
                    title = "主类型",
                    values = OfficialSkillPrimaryType.entries,
                    selected = filters.primaryTypes,
                    label = OfficialSkillPrimaryType::displayName,
                    onToggle = { onEvent(OfficialSkillCatalogEvent.TogglePrimaryType(it)) },
                )
                FilterGroup(
                    title = "主价值",
                    values = OfficialSkillPrimaryValue.entries,
                    selected = filters.primaryValues,
                    label = OfficialSkillPrimaryValue::displayName,
                    onToggle = { onEvent(OfficialSkillCatalogEvent.TogglePrimaryValue(it)) },
                )
                FilterGroup(
                    title = "使用模式",
                    values = OfficialSkillUseMode.entries,
                    selected = filters.useModes,
                    label = OfficialSkillUseMode::displayName,
                    onToggle = { onEvent(OfficialSkillCatalogEvent.ToggleUseMode(it)) },
                )
                FilterGroup(
                    title = "联网要求",
                    values = OfficialSkillNetworkRequirement.entries,
                    selected = filters.networkRequirements,
                    label = OfficialSkillNetworkRequirement::displayName,
                    onToggle = { onEvent(OfficialSkillCatalogEvent.ToggleNetwork(it)) },
                )
                FilterGroup(
                    title = "资料要求",
                    values = OfficialSkillMaterialRequirement.entries,
                    selected = filters.materialRequirements,
                    label = OfficialSkillMaterialRequirement::displayName,
                    onToggle = { onEvent(OfficialSkillCatalogEvent.ToggleMaterial(it)) },
                )
                FilterGroup(
                    title = "风险等级",
                    values = OfficialSkillRiskLevel.entries,
                    selected = filters.risks,
                    label = OfficialSkillRiskLevel::displayName,
                    onToggle = { onEvent(OfficialSkillCatalogEvent.ToggleRisk(it)) },
                )
                FilterGroup(
                    title = "发布状态",
                    values = OfficialSkillPublicationStatus.entries,
                    selected = filters.publicationStatuses,
                    label = OfficialSkillPublicationStatus::displayName,
                    onToggle = { onEvent(OfficialSkillCatalogEvent.TogglePublication(it)) },
                )
                FilterChip(
                    selected = filters.executableOnly,
                    onClick = { onEvent(OfficialSkillCatalogEvent.ToggleExecutableOnly) },
                    label = { Text("只看当前可执行") },
                )
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(OfficialSkillCatalogEvent.ClearFilters) }) {
                Text("清除")
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onEvent(OfficialSkillCatalogEvent.FilterDialogChanged(false))
                },
            ) {
                Text("完成")
            }
        },
    )
}

@Composable
private fun <T> FilterGroup(
    title: String,
    values: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onToggle: (T) -> Unit,
) {
    Column {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            values.forEach { value ->
                FilterChip(
                    selected = value in selected,
                    onClick = { onToggle(value) },
                    label = { Text(label(value)) },
                )
            }
        }
    }
}

@Composable
internal fun OfficialSkillDetailDialog(
    skill: OfficialSkillDefinition,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToCombination: () -> Unit,
    onUse: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .testTag(OfficialSkillCatalogTestTags.DETAIL),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = skill.nameZh,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = skill.id,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("关闭") }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(skill.summary, style = MaterialTheme.typography.bodyLarge)
                    DetailSection("分类", listOf(skill.primaryType.displayName(), skill.primaryValue.displayName()))
                    DetailSection("状态", skill.statusLabels())
                    DetailSection("典型场景", skill.typicalScenarios)
                    DetailSection("输入要求", skill.inputRequirements)
                    DetailSection("输出形式", skill.outputForms)
                    DetailSection("使用模式", listOf(skill.useMode.displayName()))
                    DetailSection("联网要求", listOf(skill.networkRequirement.displayName()))
                    DetailSection(
                        "资料要求",
                        skill.materialRequirements.map(OfficialSkillMaterialRequirement::displayName),
                    )
                    DetailSection("风险等级", listOf(skill.riskLevel.displayName()))
                    DetailSection("来源治理", listOf(skill.sourceSummary))
                    DetailSection("能力与安全边界", skill.boundaries)
                    skill.personDisclaimer?.let { DetailSection("非本人声明", listOf(it)) }
                    if (skill.integrityBoundaries.isNotEmpty()) {
                        DetailSection("诚信边界", skill.integrityBoundaries)
                    }
                    if (!skill.availability.executable) {
                        DetailSection(
                            "当前不可执行原因",
                            listOf(skill.nonExecutableReason ?: "尚未通过执行门禁"),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(onClick = onToggleFavorite) {
                        Text(if (isFavorite) "取消收藏" else "收藏")
                    }
                    OutlinedButton(onClick = onAddToCombination) {
                        Text("加入官方组合")
                    }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onUse,
                        enabled = skill.availability.executable,
                    ) {
                        Text(if (skill.availability.executable) "开始使用" else "暂不可用")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(title: String, values: List<String>) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        values.forEach { value ->
            Text(
                text = "• $value",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun OfficialSkillCombinationList(
    combinations: List<OfficialSkillCombinationSnapshot>,
    catalogSkills: List<OfficialSkillDefinition>,
    isLoading: Boolean,
    error: String?,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val catalogById = catalogSkills.associateBy(OfficialSkillDefinition::id)
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("官方组合", style = MaterialTheme.typography.titleLarge)
                Text(
                    "只允许官方 Skill；默认职责不会改写官方 Prompt 或安全边界。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onCreate) { Text("新建组合") }
        }
        Spacer(Modifier.height(8.dp))
        when {
            isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag(OfficialSkillCatalogTestTags.COMBINATION_ERROR),
                contentAlignment = Alignment.Center,
            ) {
                Text(error, color = MaterialTheme.colorScheme.error)
            }
            combinations.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有官方 Skill 组合")
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(combinations, key = { it.combination.id }) { snapshot ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(
                                OfficialSkillCatalogTestTags.combination(snapshot.combination.id),
                            ),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                snapshot.combination.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            snapshot.members.sortedBy { it.position }.forEachIndexed { index, member ->
                                val skill = catalogById[member.officialSkillId]
                                Text(
                                    text = "${index + 1}. ${skill?.nameZh ?: "未知官方 ID"} · ${member.officialSkillId}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                member.defaultResponsibility?.takeIf(String::isNotBlank)?.let {
                                    Text(
                                        text = "职责：$it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                skill?.listBoundaryHint()?.let { hint ->
                                    Text(
                                        text = hint,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                TextButton(onClick = { onEdit(snapshot.combination.id) }) {
                                    Text("编辑")
                                }
                                TextButton(onClick = { onDelete(snapshot.combination.id) }) {
                                    Text("删除")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OfficialSkillCombinationEditorDialog(
    editor: OfficialSkillCombinationEditorState,
    catalogSkills: List<OfficialSkillDefinition>,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
) {
    val catalogById = catalogSkills.associateBy(OfficialSkillDefinition::id)
    val selectedIds = editor.members.mapTo(linkedSetOf()) { it.skillId }
    val orderedSkills = editor.members.mapNotNull { catalogById[it.skillId] } +
        catalogSkills.filterNot { it.id in selectedIds }

    Dialog(
        onDismissRequest = {
            if (!editor.isSaving) onEvent(OfficialSkillCatalogEvent.DismissCombinationEditor)
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.92f)
                .testTag(OfficialSkillCatalogTestTags.COMBINATION_EDITOR),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("编辑官方 Skill 组合", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = editor.name,
                    onValueChange = {
                        onEvent(OfficialSkillCatalogEvent.CombinationNameChanged(it))
                    },
                    label = { Text("组合名称") },
                    singleLine = true,
                    enabled = !editor.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "默认职责只描述成员在本组合中的关注点，不是 System Prompt。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                editor.validationMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(orderedSkills, key = OfficialSkillDefinition::id) { skill ->
                        val memberIndex = editor.members.indexOfFirst { it.skillId == skill.id }
                        val selected = memberIndex >= 0
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerLow
                                },
                            ),
                        ) {
                            Column(Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = {
                                            onEvent(
                                                OfficialSkillCatalogEvent.ToggleCombinationMember(
                                                    skill.id,
                                                ),
                                            )
                                        },
                                        enabled = !editor.isSaving,
                                    )
                                    Column(Modifier.weight(1f)) {
                                        Text(skill.nameZh, fontWeight = FontWeight.SemiBold)
                                        Text(
                                            skill.id,
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                    if (selected) Text("顺序 ${memberIndex + 1}")
                                }
                                if (selected) {
                                    val member = editor.members[memberIndex]
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(
                                            onClick = {
                                                onEvent(
                                                    OfficialSkillCatalogEvent.MoveCombinationMember(
                                                        skill.id,
                                                        -1,
                                                    ),
                                                )
                                            },
                                            enabled = memberIndex > 0 && !editor.isSaving,
                                        ) { Text("上移") }
                                        TextButton(
                                            onClick = {
                                                onEvent(
                                                    OfficialSkillCatalogEvent.MoveCombinationMember(
                                                        skill.id,
                                                        1,
                                                    ),
                                                )
                                            },
                                            enabled = memberIndex < editor.members.lastIndex &&
                                                !editor.isSaving,
                                        ) { Text("下移") }
                                    }
                                    OutlinedTextField(
                                        value = member.defaultResponsibility,
                                        onValueChange = {
                                            onEvent(
                                                OfficialSkillCatalogEvent.CombinationResponsibilityChanged(
                                                    skill.id,
                                                    it,
                                                ),
                                            )
                                        },
                                        label = { Text("可选默认职责") },
                                        enabled = !editor.isSaving,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    skill.listBoundaryHint()?.let { hint ->
                                        Text(
                                            hint,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = {
                            onEvent(OfficialSkillCatalogEvent.DismissCombinationEditor)
                        },
                        enabled = !editor.isSaving,
                    ) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { onEvent(OfficialSkillCatalogEvent.SaveCombination) },
                        enabled = !editor.isSaving,
                    ) {
                        if (editor.isSaving) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text("保存")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun OfficialSkillCatalogMessageDialog(
    message: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("提示") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onDismiss) { Text("知道了") }
        },
    )
}

private fun OfficialSkillPrimaryType.displayName(): String = when (this) {
    OfficialSkillPrimaryType.PERSON_PERSPECTIVE -> "人物视角"
    OfficialSkillPrimaryType.PROFESSIONAL_ADVISOR -> "专业顾问"
    OfficialSkillPrimaryType.TASK_ASSISTANT -> "任务助手"
    OfficialSkillPrimaryType.WORKFLOW_CAPABILITY -> "工作流能力"
}

private fun OfficialSkillPrimaryValue.displayName(): String = when (this) {
    OfficialSkillPrimaryValue.REALITY_SUPPORT -> "现实支持"
    OfficialSkillPrimaryValue.THINKING_EXPANSION -> "思维拓展"
    OfficialSkillPrimaryValue.BOTH -> "两者皆可"
}

private fun OfficialSkillUseMode.displayName(): String = when (this) {
    OfficialSkillUseMode.SINGLE_ONLY -> "仅单 Skill"
    OfficialSkillUseMode.SINGLE_PREFERRED -> "优先单 Skill"
    OfficialSkillUseMode.MULTI_PREFERRED -> "优先多 Skill"
    OfficialSkillUseMode.BOTH -> "单/多均可"
}

private fun OfficialSkillNetworkRequirement.displayName(): String = when (this) {
    OfficialSkillNetworkRequirement.NOT_NEEDED -> "无需联网"
    OfficialSkillNetworkRequirement.OPTIONAL -> "联网可选"
    OfficialSkillNetworkRequirement.REQUIRED -> "需要联网核验"
    OfficialSkillNetworkRequirement.PROHIBITED_FOR_MATERIAL -> "资料禁止上传联网"
}

private fun OfficialSkillMaterialRequirement.displayName(): String = when (this) {
    OfficialSkillMaterialRequirement.NONE -> "无需资料"
    OfficialSkillMaterialRequirement.OPTIONAL -> "资料可选"
    OfficialSkillMaterialRequirement.REQUIRED -> "需要资料"
    OfficialSkillMaterialRequirement.USER_AUTHORIZED -> "用户明确授权"
    OfficialSkillMaterialRequirement.SENSITIVE -> "含敏感资料"
    OfficialSkillMaterialRequirement.TIME_BOUND -> "需要时效信息"
}

private fun OfficialSkillRiskLevel.displayName(): String = when (this) {
    OfficialSkillRiskLevel.GENERAL -> "一般"
    OfficialSkillRiskLevel.SENSITIVE -> "敏感"
    OfficialSkillRiskLevel.HIGH_STAKES -> "高后果"
    OfficialSkillRiskLevel.URGENT -> "紧急"
}

private fun OfficialSkillPublicationStatus.displayName(): String = when (this) {
    OfficialSkillPublicationStatus.BLOCKED_REWORK -> "阻断重构"
    OfficialSkillPublicationStatus.ORIGINALITY_OR_LICENSE_REVIEW -> "许可/原创性待核验"
    OfficialSkillPublicationStatus.NOTICE_AND_DISCLOSURE_REQUIRED -> "声明与生产核验"
    OfficialSkillPublicationStatus.PUBLISHABLE -> "可发布"
}
