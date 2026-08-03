package com.elio.jianyu.ui.screens.skills

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun OfficialSkillCatalogScreen(
    uiState: OfficialSkillCatalogUiState,
    onEvent: (OfficialSkillCatalogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(OfficialSkillCatalogTestTags.ROOT),
    ) {
        when {
            uiState.isLoading -> {
                Spacer(Modifier.weight(1f))
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag(OfficialSkillCatalogTestTags.LOADING),
                )
                Spacer(Modifier.weight(1f))
            }
            uiState.catalogError != null -> {
                Spacer(Modifier.weight(1f))
                Text(
                    text = "官方 Skill 目录无法加载",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag(OfficialSkillCatalogTestTags.ERROR),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = uiState.catalogError,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                Spacer(Modifier.weight(1f))
            }
            else -> {
                OfficialSkillCatalogHeader(
                    query = uiState.query,
                    totalSkillCount = uiState.totalSkillCount,
                    onQueryChanged = {
                        onEvent(OfficialSkillCatalogEvent.SearchChanged(it))
                    },
                    onOpenFilters = {
                        onEvent(OfficialSkillCatalogEvent.FilterDialogChanged(true))
                    },
                )
                OfficialSkillCatalogSectionTabs(
                    selected = uiState.section,
                    favoriteCount = uiState.favoriteIds.size,
                    recentCount = uiState.recentUses.size,
                    combinationCount = uiState.combinations.size,
                    onSelected = {
                        onEvent(OfficialSkillCatalogEvent.SectionChanged(it))
                    },
                )
                Spacer(Modifier.height(8.dp))

                if (uiState.section == OfficialSkillCatalogSection.COMBINATIONS) {
                    OfficialSkillCombinationList(
                        combinations = uiState.combinations,
                        catalogSkills = uiState.allSkills.ifEmpty { uiState.visibleSkills },
                        isLoading = uiState.combinationsLoading,
                        error = uiState.combinationError,
                        onCreate = {
                            onEvent(OfficialSkillCatalogEvent.CreateCombination())
                        },
                        onEdit = {
                            onEvent(OfficialSkillCatalogEvent.EditCombination(it))
                        },
                        onDelete = {
                            onEvent(OfficialSkillCatalogEvent.DeleteCombination(it))
                        },
                    )
                } else {
                    OfficialSkillCatalogList(
                        skills = uiState.visibleSkills,
                        favoriteIds = uiState.favoriteIds,
                        emptyMessage = when (uiState.section) {
                            OfficialSkillCatalogSection.FAVORITES -> "还没有收藏的官方 Skill"
                            OfficialSkillCatalogSection.RECENT -> "还没有真正进入过使用流程"
                            else -> "没有符合搜索和筛选条件的 Skill"
                        },
                        onOpenDetail = {
                            onEvent(OfficialSkillCatalogEvent.OpenDetail(it))
                        },
                        onToggleFavorite = {
                            onEvent(OfficialSkillCatalogEvent.ToggleFavorite(it))
                        },
                    )
                }
            }
        }
    }

    if (uiState.filterDialogVisible) {
        OfficialSkillCatalogFilterDialog(
            filters = uiState.filters,
            onEvent = onEvent,
        )
    }

    uiState.selectedSkill?.let { skill ->
        OfficialSkillDetailDialog(
            skill = skill,
            isFavorite = skill.id in uiState.favoriteIds,
            onDismiss = { onEvent(OfficialSkillCatalogEvent.DismissDetail) },
            onToggleFavorite = {
                onEvent(OfficialSkillCatalogEvent.ToggleFavorite(skill.id))
            },
            onAddToCombination = {
                onEvent(OfficialSkillCatalogEvent.CreateCombination(skill.id))
            },
            onUse = { onEvent(OfficialSkillCatalogEvent.UseSkill(skill.id)) },
        )
    }

    uiState.combinationEditor?.let { editor ->
        OfficialSkillCombinationEditorDialog(
            editor = editor,
            catalogSkills = uiState.allSkills.ifEmpty { uiState.visibleSkills },
            onEvent = onEvent,
        )
    }

    uiState.message?.let { message ->
        OfficialSkillCatalogMessageDialog(
            message = message,
            onDismiss = { onEvent(OfficialSkillCatalogEvent.DismissMessage) },
        )
    }
}
