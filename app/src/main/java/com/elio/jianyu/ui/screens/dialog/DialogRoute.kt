package com.elio.jianyu.ui.screens.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * 见域「对话」页面 Route 桥接层
 * 负责收集 UI 状态、分发事件、处理局部弹窗/抽屉切换与 ViewModel/Repository 副作用对接
 */
@Composable
fun DialogRoute(
    modifier: Modifier = Modifier,
    initialUiState: DialogUiState = DialogUiState.PreviewMock,
) {
    var state by remember { mutableStateOf(initialUiState) }

    DialogScreen(
        uiState = state,
        onEvent = { event ->
            state = handleDialogEvent(state, event)
        },
        modifier = modifier,
    )
}

/**
 * 纯状态 Reducer：处理页面内部交互状态流转，保证可交互与可预览性
 */
private fun handleDialogEvent(currentState: DialogUiState, event: DialogEvent): DialogUiState {
    return when (event) {
        is DialogEvent.SetDrawerOpen -> {
            currentState.copy(
                activeOverlay = if (event.open) DialogOverlayType.DRAWER_SESSIONS else DialogOverlayType.NONE,
            )
        }
        is DialogEvent.SetOverlay -> {
            currentState.copy(activeOverlay = event.overlay)
        }
        DialogEvent.DismissOverlay -> {
            currentState.copy(activeOverlay = DialogOverlayType.NONE)
        }
        DialogEvent.ToggleMoreMenu -> {
            currentState.copy(isMoreMenuOpen = !currentState.isMoreMenuOpen)
        }
        DialogEvent.DismissMoreMenu -> {
            currentState.copy(isMoreMenuOpen = false)
        }
        is DialogEvent.InputTextChanged -> {
            currentState.copy(
                composerState = currentState.composerState.copy(inputText = event.text),
            )
        }
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
        DialogEvent.ClickPlusButton -> {
            currentState.copy(activeOverlay = DialogOverlayType.SHEET_COMPOSER_PLUS_MENU)
        }
        DialogEvent.ClickAtButton -> {
            currentState.copy(activeOverlay = DialogOverlayType.SHEET_TARGET_ROLE_SELECT)
        }
        is DialogEvent.SelectReplyTargetRole -> {
            currentState.copy(
                composerState = currentState.composerState.copy(
                    targetRole = event.role,
                    isMultiRoleAnswer = false,
                ),
            )
        }
        DialogEvent.SelectMultiRoleAnswer -> {
            currentState.copy(
                composerState = currentState.composerState.copy(
                    targetRole = null,
                    isMultiRoleAnswer = true,
                ),
            )
        }
        DialogEvent.ClearReplyTargetRole -> {
            currentState.copy(
                composerState = currentState.composerState.copy(
                    targetRole = null,
                    isMultiRoleAnswer = false,
                ),
            )
        }
        DialogEvent.ToggleSearchMode -> {
            val currentEnabled = currentState.searchState.enabled
            currentState.copy(
                searchState = currentState.searchState.copy(
                    enabled = !currentEnabled,
                    statusText = if (!currentEnabled) "已开" else "已关",
                ),
            )
        }
        is DialogEvent.ClickSkillCard -> {
            val skill = currentState.activeRoles.find { it.id == event.skillId }
            if (skill != null) {
                currentState.copy(
                    activeOverlay = DialogOverlayType.SHEET_SKILL_DETAIL,
                    selectedSkillDetail = currentState.selectedSkillDetail?.copy(role = skill),
                )
            } else {
                currentState
            }
        }
        DialogEvent.ClickAddSkillCard -> {
            currentState.copy(activeOverlay = DialogOverlayType.SHEET_ADD_SKILL)
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
        is DialogEvent.SelectSession -> {
            // 切换会话状态
            currentState.copy(
                session = currentState.session.copy(id = event.sessionId),
                activeOverlay = DialogOverlayType.NONE,
            )
        }
        DialogEvent.CreateNewSession -> {
            currentState.copy(
                session = DialogSessionInfo(
                    id = "session_${System.currentTimeMillis()}",
                    title = "新建对话",
                    roleCount = currentState.activeRoles.size,
                ),
                messages = emptyList(),
                activeOverlay = DialogOverlayType.NONE,
            )
        }
        // 消息与会话业务事件预留
        is DialogEvent.CopyMessage -> currentState
        is DialogEvent.SaveMessageAsArtifact -> currentState
        is DialogEvent.ClickMessageMore -> currentState
        is DialogEvent.SearchSessions -> {
            currentState.copy(
                drawerData = currentState.drawerData.copy(searchQuery = event.query),
            )
        }
        is DialogEvent.SearchSkillsToAdd -> {
            currentState.copy(
                addSkillCatalog = currentState.addSkillCatalog.copy(searchQuery = event.query),
            )
        }
        is DialogEvent.LetSkillAnswerCurrent -> currentState
        is DialogEvent.RenameSession -> currentState
        is DialogEvent.ExportSession -> currentState
        is DialogEvent.ArchiveSession -> currentState
        is DialogEvent.DeleteSession -> currentState
        DialogEvent.OpenArchivedSessions -> currentState
        DialogEvent.ContinueDeeper -> currentState
        DialogEvent.SaveOrOrganizeArtifacts -> currentState
        DialogEvent.AddFileAttachment -> currentState
        DialogEvent.SelectMaterials -> currentState
        DialogEvent.ViewReferenceContent -> currentState
        DialogEvent.TriggerCrossDiscussion -> currentState
        is DialogEvent.SelectThinkingIntensity -> {
            currentState.copy(thinkingIntensity = event.intensity)
        }
        is DialogEvent.NavigateBottomTab -> currentState
    }
}
