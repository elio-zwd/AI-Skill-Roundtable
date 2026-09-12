package com.elio.jianyu.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.data.ChatRepository
import com.elio.jianyu.data.ConversationSessionPreferences
import com.elio.jianyu.data.RoundtableDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoundtableViewModelSkillRoleActionsTest {
    private val application = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun createWithRoleThenAddRole_waitsForPublishedParticipantState() = runBlocking {
        val viewModel = RoundtableViewModel(application)

        val created = viewModel.createNewSessionWithSkillRole(
            skillId = "meeting-to-action",
            title = "UI-02 role action test",
        )

        assertTrue(created)
        assertNotNull(viewModel.currentSessionId.value)
        assertEquals(listOf("meeting-to-action"), viewModel.currentParticipantIds.value)

        val sessionId = viewModel.currentSessionId.value
        val added = viewModel.addSkillRoleToCurrentSessionAwait("study-planner")

        assertTrue(added)
        assertEquals(sessionId, viewModel.currentSessionId.value)
        assertEquals(
            listOf("meeting-to-action", "study-planner"),
            viewModel.currentParticipantIds.value,
        )

        val beforeUnknown = viewModel.currentParticipantIds.value
        assertFalse(viewModel.addSkillRoleToCurrentSessionAwait("unknown-official-role"))
        assertEquals(sessionId, viewModel.currentSessionId.value)
        assertEquals(beforeUnknown, viewModel.currentParticipantIds.value)
    }

    @Test
    fun startNew_whenSettleFails_removesCreatedSessionAndRestoresSelection() = runBlocking {
        val viewModel = RoundtableViewModel(application)
        val database = RoundtableDatabase.getDatabase(application, this)
        val chatRepository = ChatRepository(database.chatDao())
        val sessionsBefore = chatRepository.allSessions.first().map { it.id }.toSet()
        val previousSessionId = viewModel.currentSessionId.value

        val success = viewModel.createNewSessionWithSkillRole(
            skillId = "meeting-to-action",
            title = "rollback-test",
            settleTimeoutMs = 0L,
        )

        assertFalse(success)
        val sessionsAfter = chatRepository.allSessions.first().map { it.id }.toSet()
        assertEquals(sessionsBefore, sessionsAfter)
        assertEquals(previousSessionId, viewModel.currentSessionId.value)
    }

    @Test
    fun addCurrent_whenSettleFails_restoresOriginalParticipants() = runBlocking {
        val viewModel = RoundtableViewModel(application)
        assertTrue(viewModel.createNewSessionWithSkillRole("meeting-to-action"))
        val sessionId = requireNotNull(viewModel.currentSessionId.value)
        val original = viewModel.currentParticipantIds.value

        val success = viewModel.addSkillRoleToCurrentSessionAwait(
            skillId = "study-planner",
            settleTimeoutMs = 0L,
        )

        assertFalse(success)
        assertEquals(
            original,
            ConversationSessionPreferences(application).getParticipantIds(sessionId, emptyList()),
        )
        withTimeout(5_000L) {
            viewModel.currentParticipantIds.first { it == original }
        }
    }
}
