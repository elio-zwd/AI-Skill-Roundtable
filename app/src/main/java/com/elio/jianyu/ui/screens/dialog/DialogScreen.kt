package com.elio.jianyu.ui.screens.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.elio.jianyu.ui.screens.dialog.components.DialogBottomBar
import com.elio.jianyu.ui.screens.dialog.components.DialogComposer
import com.elio.jianyu.ui.screens.dialog.components.DialogTopBar
import com.elio.jianyu.ui.screens.dialog.components.SkillMessageCard
import com.elio.jianyu.ui.screens.dialog.components.SkillRoleStrip
import com.elio.jianyu.ui.screens.dialog.components.UserMessageBubble
import com.elio.jianyu.ui.screens.dialog.overlays.AddSkillRoleBottomSheet
import com.elio.jianyu.ui.screens.dialog.overlays.ComposerFeaturesBottomSheet
import com.elio.jianyu.ui.screens.dialog.overlays.ConversationHistoryDrawer
import com.elio.jianyu.ui.screens.dialog.overlays.SkillRoleDetailBottomSheet
import com.elio.jianyu.ui.screens.dialog.overlays.TargetRoleSelectionBottomSheet

/**
 * 见域「对话」Top 1 核心页面主屏 Composable
 * 对应设计规范 docs/product/重构/UI界面/对话/jianyu-dialog-final-ui-spec.md
 */
@Composable
fun DialogScreen(
    uiState: DialogUiState,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DialogTokens.PageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // 主内容纵向布局
        Scaffold(
            containerColor = DialogTokens.PageBackground,
            topBar = {
                Column(modifier = Modifier.background(DialogTokens.SurfaceWhite)) {
                    // 1. 顶部导航栏
                    DialogTopBar(
                        session = uiState.session,
                        onEvent = onEvent,
                    )

                    // 2. Skill 角色条
                    SkillRoleStrip(
                        activeRoles = uiState.activeRoles,
                        onEvent = onEvent,
                    )
                }
            },
            bottomBar = {
                Column(modifier = Modifier.background(DialogTokens.PageBackground)) {
                    // 3. 对话编辑器与联网状态 Chip
                    DialogComposer(
                        composerState = uiState.composerState,
                        searchState = uiState.searchState,
                        onEvent = onEvent,
                    )

                    // 4. 底部 4 标签一级导航
                    DialogBottomBar(
                        selectedTabIndex = 0,
                        onEvent = onEvent,
                    )
                }
            },
        ) { paddingValues ->
            // 5. 对话消息列表（带充足的底部边距，防止被固定的 Composer 遮挡）
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(uiState.messages, key = { it.id }) { message ->
                    when (message) {
                        is DialogMessageItem.UserMessage -> {
                            UserMessageBubble(message = message)
                        }
                        is DialogMessageItem.SkillMessage -> {
                            SkillMessageCard(
                                message = message,
                                onEvent = onEvent,
                            )
                        }
                    }
                }
            }
        }

        // 6. 浮层层级：左侧会话记录抽屉
        ConversationHistoryDrawer(
            isOpen = uiState.activeOverlay == DialogOverlayType.DRAWER_SESSIONS,
            drawerData = uiState.drawerData,
            onEvent = onEvent,
        )

        // 7. 浮层层级：增加 Skill 角色 Sheet
        AddSkillRoleBottomSheet(
            isOpen = uiState.activeOverlay == DialogOverlayType.SHEET_ADD_SKILL,
            catalog = uiState.addSkillCatalog,
            onEvent = onEvent,
        )

        // 8. 浮层层级：Skill 角色详情 Sheet
        SkillRoleDetailBottomSheet(
            isOpen = uiState.activeOverlay == DialogOverlayType.SHEET_SKILL_DETAIL,
            detail = uiState.selectedSkillDetail,
            onEvent = onEvent,
        )

        // 9. 浮层层级：输入区「+」二级功能 Sheet
        ComposerFeaturesBottomSheet(
            isOpen = uiState.activeOverlay == DialogOverlayType.SHEET_COMPOSER_PLUS_MENU,
            isSearchEnabled = uiState.searchState.enabled,
            thinkingIntensity = uiState.thinkingIntensity,
            onEvent = onEvent,
        )

        // 10. 浮层层级：选择本次回复角色 / @ Sheet
        TargetRoleSelectionBottomSheet(
            isOpen = uiState.activeOverlay == DialogOverlayType.SHEET_TARGET_ROLE_SELECT,
            activeRoles = uiState.activeRoles,
            composerState = uiState.composerState,
            onEvent = onEvent,
        )
    }
}

/**
 * 完整视觉预览（基于小米 14 Ultra 规范 Mock 数据）
 */
@Preview(showBackground = true, device = "spec:width=412dp,height=915dp,dpi=480")
@Composable
fun DialogScreenPreview() {
    DialogScreen(
        uiState = DialogUiState.PreviewMock,
        onEvent = {},
    )
}
