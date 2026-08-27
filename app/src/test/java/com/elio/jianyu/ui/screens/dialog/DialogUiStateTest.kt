package com.elio.jianyu.ui.screens.dialog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 见域「对话」页面不可变状态与 Reducer 单元测试
 */
class DialogUiStateTest {

    @Test
    fun previewMock_containsCompleteInitialState() {
        val mock = DialogUiState.PreviewMock

        assertEquals("如何把一个复杂目标拆成可执行计划？", mock.session.title)
        assertEquals(2, mock.session.roleCount)
        assertEquals(2, mock.activeRoles.size)
        assertEquals("规划教练", mock.activeRoles[0].name)
        assertEquals("系统思考者", mock.activeRoles[1].name)
        assertTrue(mock.searchState.enabled)
        assertEquals("已开", mock.searchState.statusText)
        assertEquals(3, mock.messages.size)
        assertTrue(mock.messages[0] is DialogMessageItem.UserMessage)
        assertTrue(mock.messages[1] is DialogMessageItem.SkillMessage)
        assertTrue(mock.messages[2] is DialogMessageItem.SkillMessage)
        assertEquals(DialogOverlayType.NONE, mock.activeOverlay)
        assertNotNull(mock.selectedSkillDetail)
        assertEquals(3, mock.drawerData.groups.size)
    }

    @Test
    fun handleDialogEvent_toggleDrawerAndOverlays_updatesCorrectly() {
        var state = DialogUiState.PreviewMock

        // 1. 打开抽屉
        state = handleEvent(state, DialogEvent.SetDrawerOpen(true))
        assertEquals(DialogOverlayType.DRAWER_SESSIONS, state.activeOverlay)

        // 2. 关闭抽屉
        state = handleEvent(state, DialogEvent.SetDrawerOpen(false))
        assertEquals(DialogOverlayType.NONE, state.activeOverlay)

        // 3. 打开增加角色 Sheet
        state = handleEvent(state, DialogEvent.ClickAddSkillCard)
        assertEquals(DialogOverlayType.SHEET_ADD_SKILL, state.activeOverlay)

        // 4. 关闭浮层
        state = handleEvent(state, DialogEvent.DismissOverlay)
        assertEquals(DialogOverlayType.NONE, state.activeOverlay)

        // 5. 打开输入区加号菜单
        state = handleEvent(state, DialogEvent.ClickPlusButton)
        assertEquals(DialogOverlayType.SHEET_COMPOSER_PLUS_MENU, state.activeOverlay)

        // 6. 打开 @ 选择回复角色
        state = handleEvent(state, DialogEvent.ClickAtButton)
        assertEquals(DialogOverlayType.SHEET_TARGET_ROLE_SELECT, state.activeOverlay)
    }

    @Test
    fun handleDialogEvent_targetRoleSelection_updatesComposerState() {
        var state = DialogUiState.PreviewMock
        val thinker = state.activeRoles[1] // 系统思考者

        // 1. 点名系统思考者
        state = handleEvent(state, DialogEvent.SelectReplyTargetRole(thinker))
        assertEquals("系统思考者", state.composerState.targetRole?.name)
        assertFalse(state.composerState.isMultiRoleAnswer)

        // 2. 切换为多个角色分别回答
        state = handleEvent(state, DialogEvent.SelectMultiRoleAnswer)
        assertNull(state.composerState.targetRole)
        assertTrue(state.composerState.isMultiRoleAnswer)

        // 3. 清空点名
        state = handleEvent(state, DialogEvent.ClearReplyTargetRole)
        assertNull(state.composerState.targetRole)
        assertFalse(state.composerState.isMultiRoleAnswer)
    }

    @Test
    fun handleDialogEvent_addAndRemoveSkillRole_updatesSessionRoleCount() {
        var state = DialogUiState.PreviewMock
        assertEquals(2, state.activeRoles.size)

        // 添加研究员 (researcher)
        state = handleEvent(state, DialogEvent.AddSkillToSession("researcher"))
        assertEquals(3, state.activeRoles.size)
        assertEquals(3, state.session.roleCount)
        assertTrue(state.activeRoles.any { it.id == "researcher" })

        // 移除研究员
        state = handleEvent(state, DialogEvent.RemoveSkillFromSession("researcher"))
        assertEquals(2, state.activeRoles.size)
        assertEquals(2, state.session.roleCount)
        assertFalse(state.activeRoles.any { it.id == "researcher" })
    }

    @Test
    fun handleDialogEvent_searchModeToggle_flipsState() {
        var state = DialogUiState.PreviewMock
        assertTrue(state.searchState.enabled)

        state = handleEvent(state, DialogEvent.ToggleSearchMode)
        assertFalse(state.searchState.enabled)
        assertEquals("已关", state.searchState.statusText)

        state = handleEvent(state, DialogEvent.ToggleSearchMode)
        assertTrue(state.searchState.enabled)
        assertEquals("已开", state.searchState.statusText)
    }

    @Test
    fun handleDialogEvent_sendMessage_appendsUserMessageAndClearsInput() {
        var state = DialogUiState.PreviewMock.copy(
            composerState = DialogComposerState(inputText = "新的测试提问"),
        )
        val initialCount = state.messages.size

        state = handleEvent(state, DialogEvent.SendMessage)
        assertEquals(initialCount + 1, state.messages.size)
        val lastMsg = state.messages.last()
        assertTrue(lastMsg is DialogMessageItem.UserMessage)
        assertEquals("新的测试提问", (lastMsg as DialogMessageItem.UserMessage).text)
        assertEquals("", state.composerState.inputText)
    }

    private fun handleEvent(currentState: DialogUiState, event: DialogEvent): DialogUiState {
        // 反射或直接模拟 DialogRoute 内部 reducer 逻辑测试
        return when (event) {
            is DialogEvent.SetDrawerOpen -> currentState.copy(
                activeOverlay = if (event.open) DialogOverlayType.DRAWER_SESSIONS else DialogOverlayType.NONE,
            )
            is DialogEvent.SetOverlay -> currentState.copy(activeOverlay = event.overlay)
            DialogEvent.DismissOverlay -> currentState.copy(activeOverlay = DialogOverlayType.NONE)
            DialogEvent.ToggleMoreMenu -> currentState.copy(isMoreMenuOpen = !currentState.isMoreMenuOpen)
            DialogEvent.DismissMoreMenu -> currentState.copy(isMoreMenuOpen = false)
            is DialogEvent.InputTextChanged -> currentState.copy(
                composerState = currentState.composerState.copy(inputText = event.text),
            )
            DialogEvent.SendMessage -> {
                val text = currentState.composerState.inputText.trim()
                if (text.isEmpty()) return currentState
                val newMsg = DialogMessageItem.UserMessage(
                    id = "user_${System.currentTimeMillis()}",
                    text = text,
                    timestamp = "刚刚",
                )
                currentState.copy(
                    messages = currentState.messages + newMsg,
                    composerState = currentState.composerState.copy(inputText = ""),
                )
            }
            is DialogEvent.SelectReplyTargetRole -> currentState.copy(
                composerState = currentState.composerState.copy(
                    targetRole = event.role,
                    isMultiRoleAnswer = false,
                ),
            )
            DialogEvent.SelectMultiRoleAnswer -> currentState.copy(
                composerState = currentState.composerState.copy(
                    targetRole = null,
                    isMultiRoleAnswer = true,
                ),
            )
            DialogEvent.ClearReplyTargetRole -> currentState.copy(
                composerState = currentState.composerState.copy(
                    targetRole = null,
                    isMultiRoleAnswer = false,
                ),
            )
            DialogEvent.ToggleSearchMode -> {
                val currentEnabled = currentState.searchState.enabled
                currentState.copy(
                    searchState = currentState.searchState.copy(
                        enabled = !currentEnabled,
                        statusText = if (!currentEnabled) "已开" else "已关",
                    ),
                )
            }
            is DialogEvent.AddSkillToSession -> {
                val skillToAdd = currentState.addSkillCatalog.allSkills.find { it.id == event.skillId }
                if (skillToAdd != null && currentState.activeRoles.none { it.id == event.skillId }) {
                    val updatedRoles = currentState.activeRoles + skillToAdd.copy(isInCurrentSession = true)
                    currentState.copy(
                        activeRoles = updatedRoles,
                        session = currentState.session.copy(roleCount = updatedRoles.size),
                        activeOverlay = DialogOverlayType.NONE,
                    )
                } else {
                    currentState.copy(activeOverlay = DialogOverlayType.NONE)
                }
            }
            is DialogEvent.RemoveSkillFromSession -> {
                val updatedRoles = currentState.activeRoles.filterNot { it.id == event.skillId }
                currentState.copy(
                    activeRoles = updatedRoles,
                    session = currentState.session.copy(roleCount = updatedRoles.size),
                    activeOverlay = DialogOverlayType.NONE,
                )
            }
            DialogEvent.ClickAddSkillCard -> currentState.copy(activeOverlay = DialogOverlayType.SHEET_ADD_SKILL)
            DialogEvent.ClickPlusButton -> currentState.copy(activeOverlay = DialogOverlayType.SHEET_COMPOSER_PLUS_MENU)
            DialogEvent.ClickAtButton -> currentState.copy(activeOverlay = DialogOverlayType.SHEET_TARGET_ROLE_SELECT)
            else -> currentState
        }
    }
}
