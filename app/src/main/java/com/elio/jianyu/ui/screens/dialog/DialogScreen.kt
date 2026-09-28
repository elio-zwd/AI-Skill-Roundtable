package com.elio.jianyu.ui.screens.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import com.elio.jianyu.ui.automation.JianyuAutomationTags
import com.elio.jianyu.ui.screens.dialog.components.DialogComposer
import com.elio.jianyu.ui.screens.dialog.components.AnswerPager
import com.elio.jianyu.ui.screens.dialog.components.AnswerPagerControls
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
            if (index >= 0) {
                listState.scrollToItem(index)
                val layout = listState.layoutInfo
                val itemHeight = layout.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
                val viewportHeight = layout.viewportEndOffset - layout.viewportStartOffset
                val offset = restoredReadingOffset(
                    uiState.conversationScrollOffset,
                    uiState.conversationScrollProgress,
                    itemHeight,
                    viewportHeight,
                )
                if (offset > 0) listState.scrollToItem(index, offset)
            } else listState.scrollToItem(uiState.visibleMessages.lastIndex)
            scrollRestored = true
        }
    }
    var previousItemCount by remember(uiState.session.id) { mutableStateOf(uiState.visibleMessages.size) }
    val bottomTolerancePx = with(LocalDensity.current) { 24.dp.roundToPx() }
    LaunchedEffect(uiState.visibleMessages.size, uiState.session.id) {
        val newCount = uiState.visibleMessages.size
        val layout = listState.layoutInfo
        val previousLast = layout.visibleItemsInfo.firstOrNull { it.index == previousItemCount - 1 }
        // 最后一项可能是长回复；仅当其末尾已进入视口时才跟随新增消息。
        val wasAtBottom = previousLast != null &&
            previousLast.offset + previousLast.size <= layout.viewportEndOffset + bottomTolerancePx
        if (newCount > previousItemCount && previousItemCount > 0 && wasAtBottom) {
            listState.scrollToItem(newCount - 1)
        }
        previousItemCount = newCount
    }
    LaunchedEffect(uiState.session.id, uiState.restoredSessionId, uiState.visibleMessages.map { it.id }) {
        if (uiState.session.id.isNotEmpty() && uiState.restoredSessionId == uiState.session.id) {
            snapshotFlow {
                val index = listState.firstVisibleItemIndex
                val offset = listState.firstVisibleItemScrollOffset
                val size = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }?.size ?: 0
                Triple(index, offset, size)
            }
                .distinctUntilChanged()
                .debounce(350)
                .collect { (index, offset, size) ->
                    uiState.visibleMessages.getOrNull(index)?.let {
                        onEvent(DialogEvent.SaveConversationOffset(it.id, offset, readingProgress(offset, size)))
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
            val currentRoleId = uiState.selectedRoleIds[event.questionId]
            val currentId = group.replies.firstOrNull { it.role.id == currentRoleId }?.id
                ?: group.replies.first().id
            val offset = listState.firstVisibleItemScrollOffset
            val itemHeight = listState.layoutInfo.visibleItemsInfo
                .firstOrNull { it.index == groupIndex }?.size ?: 0
            onEvent(DialogEvent.SaveAnswerOffset(currentId, offset, readingProgress(offset, itemHeight)))
        }
        onEvent(event)
    }
    val visibleAnswerIds = uiState.visibleMessages
        .filterIsInstance<DialogTimelineItem.Answers>()
        .associate { group ->
            val roleId = uiState.selectedRoleIds[group.questionId]
            group.questionId to (group.replies.firstOrNull { it.role.id == roleId }?.id
                ?: group.replies.first().id)
        }
    var previousSelection by remember(uiState.session.id, uiState.restoredSessionId) {
        mutableStateOf(visibleAnswerIds)
    }
    LaunchedEffect(visibleAnswerIds, uiState.session.id) {
        visibleAnswerIds.forEach { (questionId, answerId) ->
            if (previousSelection[questionId] == null || previousSelection[questionId] == answerId) return@forEach
            val groupIndex = uiState.visibleMessages.indexOfFirst {
                it is DialogTimelineItem.Answers && it.questionId == questionId
            }
            if (groupIndex >= 0 && groupIndex in listState.layoutInfo.visibleItemsInfo.map { it.index }) {
                listState.scrollToItem(groupIndex)
                val layout = listState.layoutInfo
                val itemHeight = layout.visibleItemsInfo.firstOrNull { it.index == groupIndex }?.size ?: 0
                val viewportHeight = layout.viewportEndOffset - layout.viewportStartOffset
                // 至少保留半个视口的目标回答，避免从长文切到短文时越过当前问题。
                val restoredOffset = restoredReadingOffset(
                    uiState.answerScrollOffsets[answerId] ?: 0,
                    uiState.answerScrollProgress[answerId],
                    itemHeight,
                    viewportHeight,
                )
                if (restoredOffset > 0) listState.scrollToItem(groupIndex, restoredOffset)
            }
        }
        previousSelection = visibleAnswerIds
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DialogTokens.PageBackground)
            .testTag(JianyuAutomationTags.Screen.HOME),
    ) {
        // 主内容纵向布局
        Scaffold(
            containerColor = DialogTokens.PageBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                // imePadding 在 bottomBar 内：IME 弹起时输入栏随键盘上移，
                // Scaffold 内容区的 paddingValues 随 bottomBar 高度自动更新，避免整体平移产生空白。
                Column(
                    modifier = Modifier
                        .background(DialogTokens.PageBackground)
                        .imePadding(),
                ) {
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
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            ) {
                val previewViewportHeight = maxHeight
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().testTag("dialog_message_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(uiState.visibleMessages, key = { it.id }) { item ->
                        when (item) {
                            is DialogTimelineItem.Answers -> AnswerPager(
                                group = item,
                                selectedRoleId = uiState.selectedRoleIds[item.questionId],
                                expandedAnswerIds = uiState.expandedAnswerIds,
                                readAnswerIds = uiState.readAnswerIds,
                                previewViewportHeight = previewViewportHeight,
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
                                is DialogMessageItem.SkillMessage -> SkillMessageCard(
                                    message = message,
                                    onEvent = onEvent,
                                    expanded = message.id in uiState.expandedAnswerIds,
                                    onToggleExpanded = {
                                        if (message.id in uiState.expandedAnswerIds) {
                                            scope.launch { listState.scrollToItem(
                                                uiState.visibleMessages.indexOf(item).coerceAtLeast(0),
                                            ) }
                                        }
                                        onEvent(DialogEvent.ToggleAnswerExpanded(message.id))
                                    },
                                    previewViewportHeight = previewViewportHeight,
                                )
                            }
                        }
                    }
                }
                val firstGroup = uiState.visibleMessages
                    .getOrNull(listState.firstVisibleItemIndex) as? DialogTimelineItem.Answers
                val stickyThreshold = with(LocalDensity.current) { 52.dp.roundToPx() }
                if (firstGroup != null && firstGroup.replies.size > 1 &&
                    listState.firstVisibleItemScrollOffset > stickyThreshold
                ) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                    ) {
                        AnswerPagerControls(
                            group = firstGroup,
                            selectedRoleId = uiState.selectedRoleIds[firstGroup.questionId],
                            readAnswerIds = uiState.readAnswerIds,
                            onEvent = { event ->
                                if (event is DialogEvent.SelectAnswer) selectAnswer(event) else onEvent(event)
                            },
                            sticky = true,
                        )
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

internal fun readingProgress(offset: Int, itemHeight: Int): Float =
    if (itemHeight <= 0) 0f else (offset.toFloat() / itemHeight).coerceIn(0f, 1f)

internal fun restoredReadingOffset(
    offset: Int,
    progress: Float?,
    itemHeight: Int,
    viewportHeight: Int,
): Int {
    val maxOffset = (itemHeight - minOf(itemHeight, viewportHeight.coerceAtLeast(1) / 2))
        .coerceAtLeast(0)
    val contentOffset = progress
        ?.takeIf { it.isFinite() && it in 0f..1f }
        ?.let { (it * itemHeight).roundToInt() }
        ?: offset
    return contentOffset.coerceIn(0, maxOffset)
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
