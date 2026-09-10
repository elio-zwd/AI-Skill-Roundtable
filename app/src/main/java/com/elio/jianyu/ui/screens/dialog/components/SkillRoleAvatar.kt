package com.elio.jianyu.ui.screens.dialog.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.elio.jianyu.ui.components.JianyuRoleAvatar
import com.elio.jianyu.ui.screens.dialog.SkillRoleUiModel

/** Dialog 保留薄适配层，实际头像加载与 fallback 统一交给共享组件。 */
@Composable
internal fun SkillRoleAvatar(
    role: SkillRoleUiModel,
    modifier: Modifier = Modifier,
) {
    JianyuRoleAvatar(
        name = role.name,
        assetPath = role.avatarUrl,
        avatarResId = role.avatarResId,
        fallbackText = role.avatarText,
        fallbackContainerColor = role.tintBorder,
        fallbackContentColor = role.accentColor,
        modifier = modifier,
    )
}
