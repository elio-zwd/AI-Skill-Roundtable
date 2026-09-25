package com.elio.jianyu.ui.screens.resources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elio.jianyu.result.ArtifactLibraryItem
import com.elio.jianyu.ui.components.JianyuPageShell
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ResourcesOverviewTestTags {
    const val SCREEN = "resources_overview"
    const val SEARCH = "resources_overview_search"
    const val ADD = "resources_overview_add"
    const val MATERIAL_SUMMARY = "resources_overview_material_summary"
    const val ARTIFACT_SUMMARY = "resources_overview_artifact_summary"
    const val SKILL_KNOWLEDGE = "resources_overview_skill_knowledge"

    fun material(id: String) = "resources_overview_material_$id"
    fun artifact(id: String) = "resources_overview_artifact_$id"
}

@Composable
fun ResourcesOverviewScreen(
    overview: ResourceOverviewUiState,
    onSearchMaterials: () -> Unit,
    onAddMaterial: () -> Unit,
    onShowMaterials: () -> Unit,
    onShowArtifacts: () -> Unit,
    skillKnowledgeCount: Int = 0,
    skillKnowledgeUnavailable: Boolean = false,
    onShowSkillKnowledge: () -> Unit = {},
    onOpenMaterial: (MaterialUiItem) -> Unit,
    onOpenArtifact: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    JianyuPageShell(
        title = "资料",
        subtitle = "你的依据与沉淀",
        onOpenSettings = onOpenSettings,
        contentScrollable = true,
        modifier = modifier.testTag(ResourcesOverviewTestTags.SCREEN),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            IconButton(
                onClick = onSearchMaterials,
                modifier = Modifier.testTag(ResourcesOverviewTestTags.SEARCH),
            ) {
                Icon(Icons.Default.Search, contentDescription = "搜索资料")
            }
            IconButton(
                onClick = onAddMaterial,
                modifier = Modifier.testTag(ResourcesOverviewTestTags.ADD),
            ) {
                Icon(Icons.Default.Add, contentDescription = "新增资料")
            }
        }

        overview.message?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(
                    message,
                    modifier = Modifier.padding(14.dp),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OverviewSummaryCard(
                title = "资料",
                count = overview.materialCount,
                description = "文件、链接与摘录",
                unavailable = overview.materialsUnavailable || overview.materialsLoading,
                icon = { Icon(Icons.Default.Info, contentDescription = null) },
                onClick = onShowMaterials,
                modifier = Modifier
                    .weight(1f)
                    .testTag(ResourcesOverviewTestTags.MATERIAL_SUMMARY),
            )
            OverviewSummaryCard(
                title = "成果",
                count = overview.artifactCount,
                description = "方案、笔记与文稿",
                unavailable = overview.artifactsUnavailable || overview.artifactsLoading,
                icon = { Icon(Icons.Default.Star, contentDescription = null) },
                onClick = onShowArtifacts,
                modifier = Modifier
                    .weight(1f)
                    .testTag(ResourcesOverviewTestTags.ARTIFACT_SUMMARY),
            )
        }

        OverviewSummaryCard(
            title = "Skill 资料",
            count = skillKnowledgeCount,
            description = "Skill 角色自带的知识与参考来源",
            unavailable = skillKnowledgeUnavailable,
            icon = { Icon(Icons.Default.Info, contentDescription = null) },
            onClick = onShowSkillKnowledge,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(ResourcesOverviewTestTags.SKILL_KNOWLEDGE),
        )

        OverviewSectionHeader("最近使用的资料", "全部资料", onShowMaterials)
        if (overview.recentMaterials.isEmpty()) {
            OverviewEmptyCard("还没有可显示的资料", "添加文件、链接或文本摘录后，会在这里显示。")
        } else {
            overview.recentMaterials.forEach { material ->
                RecentMaterialCard(material, onOpenMaterial)
            }
        }

        OverviewSectionHeader("最近保存的成果", "全部成果", onShowArtifacts)
        if (overview.recentArtifacts.isEmpty()) {
            OverviewEmptyCard("还没有正式成果", "在对话中明确选择“保存为成果”后，才会出现在这里。")
        } else {
            overview.recentArtifacts.forEach { artifact ->
                RecentArtifactCard(artifact, onOpenArtifact)
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.32f),
            shape = MaterialTheme.shapes.large,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "只有你明确保存的内容才会成为成果。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun OverviewSummaryCard(
    title: String,
    count: Int,
    description: String,
    unavailable: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                icon()
            }
            Text(
                if (unavailable) "--" else count.toString(),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("查看全部", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun OverviewSectionHeader(title: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onClick) {
            Text(action)
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun RecentMaterialCard(item: MaterialUiItem, onOpen: (MaterialUiItem) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(item) }
            .testTag(ResourcesOverviewTestTags.material(item.id)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(modifier = Modifier.weight(1f)) {
                Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text(
                    "${materialKindLabel(item.sourceType)} · ${formatResourceTime(item.updatedAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "查看资料")
        }
    }
}

@Composable
private fun RecentArtifactCard(item: ArtifactLibraryItem, onOpen: (String) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen(item.artifactId) }
            .testTag(ResourcesOverviewTestTags.artifact(item.artifactId)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "来源会话：${item.issueTitle} · ${formatResourceTime(item.confirmedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(item.contentSummary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OverviewEmptyCard(title: String, message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal fun materialKindLabel(sourceType: String): String = when (sourceType.lowercase()) {
    "url", "link" -> "链接"
    "file", "pdf", "docx", "text_file" -> "文件"
    "excerpt" -> "摘录"
    else -> "文本资料"
}

private val resourceTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

internal fun formatResourceTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp.coerceAtLeast(0L))
        .atZone(ZoneId.systemDefault())
        .format(resourceTimeFormatter)
