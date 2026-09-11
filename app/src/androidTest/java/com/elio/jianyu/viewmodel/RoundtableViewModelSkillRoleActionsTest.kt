package com.elio.jianyu.viewmodel

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
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
}
