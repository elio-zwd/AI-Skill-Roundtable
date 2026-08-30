package com.elio.jianyu.ui.screens.dialog

import com.elio.jianyu.data.Character
import com.elio.jianyu.data.ChatSession
import com.elio.jianyu.data.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 见域「对话」页面真实数据映射与局部状态测试。 */
class DialogUiStateTest {

    @Test
    fun previewMock_containsCompleteDesignReference() {
        val mock = DialogUiState.PreviewMock

        assertEquals(2, mock.session.roleCount)
        assertEquals(2, mock.activeRoles.size)
        assertEquals(3, mock.messages.size)
        assertNotNull(mock.selectedSkillDetail)
    }

    @Test
    fun reduceDialogLocalState_controlsDrawerAndOverlays() {
        var state = DialogUiState.PreviewMock

        state = reduceDialogLocalState(state, DialogEvent.SetDrawerOpen(true))
        assertEquals(DialogOverlayType.DRAWER_SESSIONS, state.activeOverlay)

        state = reduceDialogLocalState(state, DialogEvent.SetDrawerOpen(false))
        assertEquals(DialogOverlayType.NONE, state.activeOverlay)

        state = reduceDialogLocalState(state, DialogEvent.ClickAddSkillCard)
        assertEquals(DialogOverlayType.SHEET_ADD_SKILL, state.activeOverlay)

        state = reduceDialogLocalState(state, DialogEvent.ClickAtButton)
        assertEquals(DialogOverlayType.SHEET_TARGET_ROLE_SELECT, state.activeOverlay)
    }

    @Test
    fun reduceDialogLocalState_targetRoleIsTemporaryComposerState() {
        var state = DialogUiState.PreviewMock
        val role = state.activeRoles[1]

        state = reduceDialogLocalState(state, DialogEvent.SelectReplyTargetRole(role))
        assertEquals(role.id, state.composerState.targetRole?.id)
        assertFalse(state.composerState.isMultiRoleAnswer)

        state = reduceDialogLocalState(state, DialogEvent.SelectMultiRoleAnswer)
        assertNull(state.composerState.targetRole)
        assertTrue(state.composerState.isMultiRoleAnswer)

        state = reduceDialogLocalState(state, DialogEvent.ClearReplyTargetRole)
        assertNull(state.composerState.targetRole)
        assertFalse(state.composerState.isMultiRoleAnswer)
    }

    @Test
    fun clearComposerAfterSubmission_clearsOneShotTarget() {
        val role = DialogUiState.PreviewMock.activeRoles.first()
        val submitted = clearComposerAfterSubmission(
            DialogComposerState(
                inputText = "只问这一次",
                targetRole = role,
                isMultiRoleAnswer = false,
            ),
        )

        assertEquals("", submitted.inputText)
        assertNull(submitted.targetRole)
        assertFalse(submitted.isMultiRoleAnswer)
    }

    @Test
    fun mapDialogUiState_usesRealSessionMessagesAndParticipants() {
        val character = Character(
            id = "steve_jobs",
            name = "史蒂夫·乔布斯",
            avatar = "乔",
            tagline = "从产品体验视角提出判断",
            systemPrompt = "保持角色视角",
            order = 1,
        )
        val role = character.toSkillRoleUiModel(inCurrentSession = true)
        val local = DialogUiState(
            composerState = DialogComposerState(inputText = "继续"),
            selectedSkillDetail = SkillRoleDetailUiModel(
                role = role,
                isInCurrentSession = true,
                fullDescription = role.shortDescription,
                capabilities = emptyList(),
            ),
        )
        val session = ChatSession(id = 7, title = "真实会话")
        val messages = listOf(
            Message(
                id = 10,
                chatId = 7,
                senderId = "user",
                senderName = "你",
                avatar = "我",
                text = "真实问题",
            ),
            Message(
                id = 11,
                chatId = 7,
                senderId = character.id,
                senderName = character.name,
                avatar = character.avatar,
                text = "真实回答",
            ),
        )

        val mapped = mapDialogUiState(
            localState = local,
            sessions = listOf(session),
            currentSession = session,
            messages = messages,
            characters = listOf(character),
            participantIds = listOf(character.id),
            archivedSessionIds = emptySet(),
            showArchivedSessions = false,
            isGenerating = true,
            searchEnabled = false,
            thinkingIntensity = "深度",
        )

        assertEquals("真实会话", mapped.session.title)
        assertEquals(listOf(character.id), mapped.activeRoles.map(SkillRoleUiModel::id))
        assertEquals(2, mapped.messages.size)
        assertEquals("真实问题", (mapped.messages[0] as DialogMessageItem.UserMessage).text)
        assertEquals("真实回答", (mapped.messages[1] as DialogMessageItem.SkillMessage).text)
        assertTrue(mapped.composerState.isGenerating)
        assertFalse(mapped.searchState.enabled)
        assertEquals("深度", mapped.thinkingIntensity)
        assertTrue(mapped.selectedSkillDetail?.identityDisclosure?.contains("AI 模拟角色") == true)
    }

    @Test
    fun toSkillRoleUiModel_keepsAssetAvatarAndUsesNameAsTextPlaceholder() {
        val role = Character(
            id = "zhang_xuefeng",
            name = "张雪峰",
            avatar = "avatars/zhang_xuefeng.jpg",
            tagline = "升学与职业规划",
            systemPrompt = "保持角色视角",
            order = 1,
        ).toSkillRoleUiModel(inCurrentSession = true)

        assertEquals("avatars/zhang_xuefeng.jpg", role.avatarUrl)
        assertEquals("张雪", role.avatarText)
    }

    @Test
    fun mapDialogUiState_filtersArchivedSessions() {
        val current = ChatSession(id = 1, title = "当前")
        val archived = ChatSession(id = 2, title = "归档")

        val mapped = mapDialogUiState(
            localState = DialogUiState(),
            sessions = listOf(current, archived),
            currentSession = current,
            messages = emptyList(),
            characters = emptyList(),
            participantIds = emptyList(),
            archivedSessionIds = setOf(2),
            showArchivedSessions = true,
            isGenerating = false,
            searchEnabled = true,
            thinkingIntensity = "标准",
        )

        val visibleIds = mapped.drawerData.groups.flatMap { group -> group.sessions }.map { it.id }
        assertEquals(listOf("2"), visibleIds)
        assertEquals(1, mapped.drawerData.archivedCount)
        assertTrue(mapped.drawerData.isShowingArchived)
    }
}
