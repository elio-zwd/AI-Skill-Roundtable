package com.elio.jianyu.ui.screens.dialog

import com.elio.jianyu.data.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogTimelineTest {
    private fun message(id: Long, sender: String, question: Long? = null, mode: String = "INDEPENDENT") =
        Message(
            id = id,
            chatId = 1,
            senderId = sender,
            senderName = sender,
            avatar = "",
            text = "reply $id",
            questionMessageId = question,
            responseMode = mode,
        )

    @Test
    fun independentRepliesShareOneContainerAndKeepArrivalOrder() {
        val timeline = buildDialogTimeline(
            listOf(message(1, "user"), message(2, "a", 1), message(3, "b", 1)),
            emptyMap(),
            false,
        )
        assertEquals(2, timeline.size)
        val replies = timeline[1] as DialogTimelineItem.Answers
        assertEquals("1", replies.questionId)
        assertEquals(listOf("2", "3"), replies.replies.map { it.id })
    }

    @Test
    fun legacyAndCrossDiscussionStayInChronologicalFlow() {
        val timeline = buildDialogTimeline(
            listOf(
                message(1, "user"),
                message(2, "legacy"),
                message(3, "a", 1),
                message(4, "b", 1, "CROSS_DISCUSSION"),
                message(5, "orphan", 99),
            ),
            emptyMap(),
            false,
        )
        assertEquals(5, timeline.size)
        assertTrue(timeline[1] is DialogTimelineItem.Single)
        assertTrue(timeline[2] is DialogTimelineItem.Answers)
        assertTrue(timeline[3] is DialogTimelineItem.Single)
        assertTrue(timeline[4] is DialogTimelineItem.Single)
    }

    @Test
    fun retryReplacesOnlyThatRolesPageAndKeepsOriginalRoleOrder() {
        val timeline = buildDialogTimeline(
            listOf(
                message(1, "user"),
                message(2, "a", 1),
                message(3, "b", 1),
                message(4, "a", 1),
            ),
            emptyMap(),
            false,
        )

        val replies = (timeline[1] as DialogTimelineItem.Answers).replies
        assertEquals(listOf("a", "b"), replies.map { it.role.id })
        assertEquals(listOf("4", "3"), replies.map { it.id })
    }
}
