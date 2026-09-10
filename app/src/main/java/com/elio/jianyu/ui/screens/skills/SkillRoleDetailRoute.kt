package com.elio.jianyu.ui.screens.skills

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.role.SkillRolePresentationCatalogLoader
import com.elio.jianyu.skill.role.SkillRolePresentationLoadResult
import com.elio.jianyu.ui.components.JianyuStateCard
import kotlinx.coroutines.launch

/** UI-02 二级全屏详情 Route；不写 recent-use。 */
@Composable
internal fun SkillRoleDetailRoute(
    runtimeResult: OfficialSkillCatalogRuntimeResult,
    skillId: String?,
    canAddToCurrentConversation: Boolean,
    onBack: () -> Unit,
    onStartNewConversation: (String) -> Unit,
    onAddToCurrentConversation: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val runtime = when (runtimeResult) {
        is OfficialSkillCatalogRuntimeResult.Failure -> {
            JianyuStateCard(
                title = "角色详情暂不可用",
                message = runtimeResult.message,
                modifier = modifier,
            )
            return
        }
        is OfficialSkillCatalogRuntimeResult.Success -> runtimeResult.runtime
    }
    val resolvedSkillId = skillId?.takeIf(String::isNotBlank)
    if (resolvedSkillId == null || !runtime.catalog.containsOfficialId(resolvedSkillId)) {
        JianyuStateCard(
            title = "角色详情暂不可用",
            message = "无法定位该 Skill 角色。",
            modifier = modifier,
        )
        return
    }

    val appContext = LocalContext.current.applicationContext
    val presentationResult = remember(appContext, runtime.catalog) {
        SkillRolePresentationCatalogLoader.load(appContext, runtime.catalog)
    }
    val presentationCatalog = when (presentationResult) {
        is SkillRolePresentationLoadResult.Failure -> {
            JianyuStateCard(
                title = "角色详情暂不可用",
                message = presentationResult.message,
                modifier = modifier,
            )
            return
        }
        is SkillRolePresentationLoadResult.Success -> presentationResult.catalog
    }

    val favoriteIds by runtime.preferences.favoriteIds.collectAsState()
    val role = remember(runtime.catalog, presentationCatalog, resolvedSkillId) {
        projectSkillRoleCatalog(
            catalog = runtime.catalog,
            presentationCatalog = presentationCatalog,
        ).allRoles.firstOrNull { it.skillId == resolvedSkillId }
    }
    if (role == null) {
        JianyuStateCard(
            title = "角色详情暂不可用",
            message = "角色展示数据不完整。",
            modifier = modifier,
        )
        return
    }

    val scope = rememberCoroutineScope()
    SkillRoleDetailScreen(
        role = role.copy(isFavorite = resolvedSkillId in favoriteIds),
        isFavorite = resolvedSkillId in favoriteIds,
        canAddToCurrentConversation = canAddToCurrentConversation,
        onBack = onBack,
        onToggleFavorite = {
            scope.launch {
                runtime.preferences.setFavorite(
                    resolvedSkillId,
                    resolvedSkillId !in favoriteIds,
                )
            }
        },
        onStartNewConversation = { onStartNewConversation(resolvedSkillId) },
        onAddToCurrentConversation = { onAddToCurrentConversation(resolvedSkillId) },
        modifier = modifier,
    )
}
