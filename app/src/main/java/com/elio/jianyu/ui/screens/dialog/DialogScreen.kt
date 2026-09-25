package com.elio.jianyu.ui.screens.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.screens.dialog.components.DialogComposer
import com.elio.jianyu.ui.screens.dialog.components.AnswerPager
import com.elio.jianyu.ui.screens.dialog.components.DialogTopBar
import com.elio.jianyu.ui.screens.dialog.components.SkillMessageCard
import com.elio.jianyu.ui.screens.dialog.components.SkillRoleStrip
import com.elio.jianyu.ui.screens.dialog.components.UserMessageBubble
import com.elio.jianyu.ui.screens.dialog.overlays.AddSkillRoleBottomSheet
import com.elio.jianyu.ui.screens.dialog.overlays.ComposerFeaturesBottomSheet
import com.elio.jianyu.ui.screens.dialog.overlays.ConversationHistoryDrawer
import com.elio.jianyu.ui.screens.dialog.overlays.SkillRoleDetailBottomSheet
import com.elio.jianyu.ui.screens.dialog.overlays.SessionMoreMenuPopover
import com.elio.jianyu.ui.screens.dialog.overlays.TargetRoleSelectionBottomSheet

/**
 * 见域「对话」Top 1 核心页面主屏 Composable
 * 对应设计规范 docs/product/重构/UI界面/对话/jianyu-dialog-final-ui-spec.md
 */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun DialogScreen(
    uiState: DialogUiState,
    onEvent: (DialogEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var scrollRestored by remember(uiState.session.id) { mutableStateOf(false) }
    LaunchedEffect(uiState.session.id, uiState.restoredSessionId, uiState.visibleMessages.map { it.id }) {
        if (!scrollRestored && uiState.visibleMessages.isNotEmpty() &&
            uiState.session.id.isNotEmpty() && uiState.restoredSessionId == uiState.session.id
        ) {
            val index = uiState.visibleMessages.indexOfFirst { it.id == uiState.conversationScrollKey }
            if (index >= 0) listState.scrollToItem(index, uiState.conversationScrollOffset)
            else listState.scrollToItem(uiState.visibleMessages.lastIndex)
            scrollRestored = true
        }
    }
    var previousItemCount by remember(uiState.session.id) { mutableStateOf(uiState.visibleMessages.size) }
    LaunchedEffect(uiState.visibleMessages.size, uiState.session.id) {
        val newCount = uiState.visibleMessages.size
        if (newCount > previousItemCount && previousItemCount > 0 &&
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == previousItemCount - 1
        ) {
            listState.scrollToItem(newCount - 1)
        }
        previousItemCount = newCount
    }
    LaunchedEffect(uiState.session.id, uiState.restoredSessionId, uiState.visibleMessages.map { it.id }) {
        if (uiState.session.id.isNotEmpty() && uiState.restoredSessionId == uiState.session.id) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .debounce(350)
                .collect { (index, offset) ->
                    uiState.visibleMessages.getOrNull(index)?.let {
                        onEvent(DialogEvent.SaveConversationOffset(it.id, offset))
                    }
                }
        }
    }

    fun selectAnswer(event: DialogEvent.SelectAnswer) {
        val groupIndex = uiState.visibleMessages.indexOfFirst {
            it is DialogTimelineItem.Answers && it.questionId == event.questionId
        }
        val group = uiState.visibleMessages.getOrNull(groupIndex) as? DialogTimelineItem.Answers
        if (group != null && groupIndex == listState.firstVisibleItemIndex) {
            val currentId = uiState.selectedAnswerIds[event.questionId] ?: group.replies.first().id
            onEvent(DialogEvent.SaveAnswerOffset(currentId, listState.firstVisibleItemScrollOffset))
        }
        onEvent(event)
    }
    var previousSelection by remember(uiState.session.id, uiState.restoredSessionId) {
        mutableStateOf(uiState.selectedAnswerIds)
    }
    LaunchedEffect(uiState.selectedAnswerIds, uiState.session.id) {
        uiState.selectedAnswerIds.forEach { (questionId, answerId) ->
            if (previousSelection[questionId] == answerId) return@forEach
            val groupIndex = uiState.visibleMessages.indexOfFirst {
                it is DialogTimelineItem.Answers && it.questionId == questionId
            }
            if (groupIndex >= 0 && groupIndex in listState.layoutInfo.visibleItemsInfo.map { it.index }) {
                listState.scrollToItem(groupIndex, uiState.answerScrollOffsets[answerId] ?: 0)
            }
        }
        previousSelection = uiState.selectedAnswerIds
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DialogTokens.PageBackground)
            .testTag(JianyuAutomationTags.Screen.HOME)
            .imePadding(),
    ) {
        // 主内容纵向布局
        Scaffold(
            containerColor = DialogTokens.PageBackground,
            topBar = {
                Column(modifier = Modifier.background(DialogTokens.SurfaceWhite)) {
                    // 1. 顶部导航栏
                    Box(modifier = Modifier.fillMaxWidth()) {
                        DialogTopBar(
                            session = uiState.session,
                            onEvent = onEvent,
                            roleStripExpanded = uiState.roleStripExpanded,
                        )
                        SessionMoreMenuPopover(
                            expanded = uiState.isMoreMenuOpen,
                            onDismiss = { onEvent(DialogEvent.DismissMoreMenu) },
                            onEvent = onEvent,
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }

                    // 2. Skill 角色条
                    if (uiState.roleStripExpanded) SkillRoleStrip(
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
                items(uiState.visibleMessages, key = { it.id }) { item ->
                    when (item) {
                        is DialogTimelineItem.Answers -> AnswerPager(
                            group = item,
                            selectedAnswerId = uiState.selectedAnswerIds[item.questionId],
                            expandedAnswerIds = uiState.expandedAnswerIds,
                            readAnswerIds = uiState.readAnswerIds,
                            onEvent = { event ->
                                when (event) {
                                    is DialogEvent.SelectAnswer -> selectAnswer(event)
                                    is DialogEvent.ToggleAnswerExpanded -> {
                                        if (event.answerId in uiState.expandedAnswerIds) {
                                            scope.launch { listState.scrollToItem(
                                                uiState.visibleMessages.indexOf(item).coerceAtLeast(0),
                                            ) }
                                        }
                                        onEvent(event)
                                    }
                                    else -> onEvent(event)
                                }
                            },
                        )
                        is DialogTimelineItem.Single -> when (val message = item.message) {
                            is DialogMessageItem.UserMessage -> UserMessageBubble(message = message)
                            is DialogMessageItem.SkillMessage -> SkillMessageCard(message = message, onEvent = onEvent)
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
