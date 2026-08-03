package com.elio.jianyu.ui.screens.skillplaceholder

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.elio.jianyu.ui.components.JianyuMetadataRow
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard
import com.elio.jianyu.ui.navigation.JianyuNavigationRoutes

object SkillPlaceholderTestTags {
    const val SCREEN = "skills_screen"
    const val DETAIL_SCREEN = "skill_detail_screen"
    const val INVALID_DETAIL = "skill_detail_invalid"
}

@Composable
fun SkillPlaceholderRoute(
    onOpenSettings: () -> Unit,
) {
    JianyuPageShell(
        title = "Skill",
        subtitle = "发现适合当前问题的能力",
        onOpenSettings = onOpenSettings,
        modifier = Modifier.testTag(SkillPlaceholderTestTags.SCREEN),
    ) {
        JianyuStateCard(
            title = "官方 Skill 目录正在接入",
            message = "此页面只冻结稳定 Route 和装配点，不复制 PR09-05 的 Catalog、筛选、ViewModel 或详情业务。",
        )
        JianyuStateCard(
            title = "稳定详情接口",
            message = "Skill 详情使用正式 Skill ID 定位，不在 Route 中携带 Prompt、正文或 API Key。",
        )
    }
}

@Composable
fun SkillDetailPlaceholderRoute(
    skillId: String?,
    onBack: () -> Unit,
) {
    val isValid = JianyuNavigationRoutes.isStableId(skillId)
    JianyuPageShell(
        title = "Skill 详情",
        subtitle = if (isValid) "正式页面接入点" else "无法定位",
        onBack = onBack,
        modifier = Modifier.testTag(SkillPlaceholderTestTags.DETAIL_SCREEN),
    ) {
        if (isValid) {
            JianyuStateCard(
                title = "详情页面正在接入",
                message = "PR09-05 合并后，此处将接入其公开 Composable，不修改页面内部实现。",
            )
            JianyuMetadataRow(label = "Skill ID", value = skillId.orEmpty())
        } else {
            JianyuStateCard(
                title = "无效的 Skill ID",
                message = "该深链无法定位到稳定 Skill 标识，未执行任何业务操作。",
                modifier = Modifier.testTag(SkillPlaceholderTestTags.INVALID_DETAIL),
            )
        }
    }
}
