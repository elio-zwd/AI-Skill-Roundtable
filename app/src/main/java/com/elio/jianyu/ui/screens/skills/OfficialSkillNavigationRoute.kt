package com.elio.jianyu.ui.screens.skills

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.catalog.OfficialSkillUseRequest

/**
 * 【角色】一级入口。
 *
 * UI-02 起不再承载旧“Skill 目录”顶栏/Tab/详情 Dialog；主页面统一由
 * [SkillRoleCatalogRoute] 管理。保留旧参数是为了不破坏现有调用方，详情导航由
 * [onOpenSkillDetail] 显式交给 App NavHost。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun OfficialSkillNavigationRoute(
    repository: JianyuRepository,
    runtimeResult: OfficialSkillCatalogRuntimeResult,
    onOpenSettings: () -> Unit,
    onUseSkill: (OfficialSkillUseRequest) -> Unit,
    modifier: Modifier = Modifier,
    initialSkillId: String? = null,
    onBack: (() -> Unit)? = null,
    onOpenSkillDetail: (String) -> Unit = {},
) {
    SkillRoleCatalogRoute(
        runtimeResult = runtimeResult,
        onOpenSkillDetail = onOpenSkillDetail,
        modifier = modifier,
    )
}
