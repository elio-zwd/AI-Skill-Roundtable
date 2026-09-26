package com.elio.jianyu.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConversationReadingStateRepositoryTest {
    @Test
    fun readingStateRestoresAcrossRepositoryInstancesAndDeletesOnlyItsSession() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val firstSessionId = -9012L
        val secondSessionId = -90123L
        val repository = ConversationReadingStateRepository(context)
        repository.deleteSession(firstSessionId)
        repository.deleteSession(secondSessionId)
        try {
            repository.saveRoleStripExpanded(firstSessionId, false)
            repository.saveSelectedRole(firstSessionId, "question-1", "role-b", setOf("answer-a", "answer-b"))
            repository.saveExpandedAnswers(firstSessionId, setOf("answer-b"), null)
            repository.saveAnswerOffset(firstSessionId, "answer-b", 420, 0.4f)
            repository.saveConversationOffset(firstSessionId, "question-1", 72, 0.2f)
            repository.saveRoleStripExpanded(secondSessionId, false)

            val restored = ConversationReadingStateRepository(context).load(firstSessionId)
            assertFalse(restored.roleStripExpanded)
            assertEquals("role-b", restored.selectedRoleIds["question-1"])
            assertEquals(setOf("answer-a", "answer-b"), restored.readAnswerIds)
            assertEquals(setOf("answer-b"), restored.expandedAnswerIds)
            assertEquals(420, restored.answerScrollOffsets["answer-b"])
            assertEquals(0.4f, restored.answerScrollProgress["answer-b"] ?: -1f, 0.0001f)
            assertEquals("question-1", restored.conversationScrollKey)
            assertEquals(72, restored.conversationScrollOffset)
            assertEquals(0.2f, restored.conversationScrollProgress ?: -1f, 0.0001f)

            repository.saveExpandedAnswers(firstSessionId, emptySet(), "answer-b")
            val collapsed = ConversationReadingStateRepository(context).load(firstSessionId)
            assertTrue(collapsed.expandedAnswerIds.isEmpty())
            assertEquals(0, collapsed.answerScrollOffsets["answer-b"])
            assertEquals(0f, collapsed.answerScrollProgress["answer-b"] ?: -1f, 0.0001f)

            repository.deleteSession(firstSessionId)
            val deleted = ConversationReadingStateRepository(context).load(firstSessionId)
            assertTrue(deleted.roleStripExpanded)
            assertTrue(deleted.selectedRoleIds.isEmpty())
            assertNull(deleted.conversationScrollKey)
            assertFalse(ConversationReadingStateRepository(context).load(secondSessionId).roleStripExpanded)
        } finally {
            repository.deleteSession(firstSessionId)
            repository.deleteSession(secondSessionId)
        }
    }
}
