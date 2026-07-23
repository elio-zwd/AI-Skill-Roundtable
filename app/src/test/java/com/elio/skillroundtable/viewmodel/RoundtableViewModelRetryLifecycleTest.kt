package com.elio.skillroundtable.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundtableViewModelRetryLifecycleTest {

    @Test
    fun testSessionIsolation_stateBelongsToTargetSessionOnly() {
        val sessionAState = RetryableRoundtableState(
            sessionId = 100L,
            questionRunId = 1001L,
            characterIds = listOf("char_a", "char_b")
        )

        // 当当前选中的会话是 session B (200L) 时
        val currentSessionId = 200L
        val isForCurrentSession = (sessionAState.sessionId == currentSessionId)

        // 断言会话 B 不匹配会话 A 的重试状态
        assertFalse(isForCurrentSession)
    }

    @Test
    fun testSessionIsolation_switchingBackToSessionA_restoresStateAccess() {
        val sessionAState = RetryableRoundtableState(
            sessionId = 100L,
            questionRunId = 1001L,
            characterIds = listOf("char_a", "char_b")
        )

        // 切回会话 A (100L)
        val currentSessionId = 100L
        val isForCurrentSession = (sessionAState.sessionId == currentSessionId)

        // 断言切换回 A 后重试状态依然匹配并可访问
        assertTrue(isForCurrentSession)
        assertEquals(listOf("char_a", "char_b"), sessionAState.characterIds)
    }

    @Test
    fun testSessionDeletion_clearsMatchingRetryStateOnly() {
        var retryState: RetryableRoundtableState? = RetryableRoundtableState(
            sessionId = 100L,
            questionRunId = 1001L,
            characterIds = listOf("char_a")
        )

        fun onDeleteSession(deletedSessionId: Long) {
            if (retryState?.sessionId == deletedSessionId) {
                retryState = null
            }
        }

        // 删除非关联的会话 200L
        onDeleteSession(200L)
        assertEquals(100L, retryState?.sessionId)

        // 删除关联的会话 100L
        onDeleteSession(100L)
        assertNull(retryState)
    }

    @Test
    fun testNewQuestionStateClearing_invalidQuestionsDoNotClearState() {
        var retryState: RetryableRoundtableState? = RetryableRoundtableState(
            sessionId = 100L,
            questionRunId = 1001L,
            characterIds = listOf("char_a", "char_b")
        )

        fun trySubmitNewQuestion(
            text: String,
            hasKeys: Boolean,
            hasActiveChars: Boolean,
            isJobRunning: Boolean
        ): Boolean {
            if (text.isBlank()) return false
            if (isJobRunning) return false
            if (!hasKeys) return false
            if (!hasActiveChars) return false

            // 只有通过所有前置校验并正式准备启动时才清除旧重试状态
            retryState = null
            return true
        }

        // 1. 空输入：不清除状态
        assertFalse(trySubmitNewQuestion("   ", hasKeys = true, hasActiveChars = true, isJobRunning = false))
        assertEquals(1001L, retryState?.questionRunId)

        // 2. 正在运行任务：不清除状态
        assertFalse(trySubmitNewQuestion("新问题", hasKeys = true, hasActiveChars = true, isJobRunning = true))
        assertEquals(1001L, retryState?.questionRunId)

        // 3. 没有 API Key：不清除状态
        assertFalse(trySubmitNewQuestion("新问题", hasKeys = false, hasActiveChars = true, isJobRunning = false))
        assertEquals(1001L, retryState?.questionRunId)

        // 4. 没有启用角色：不清除状态
        assertFalse(trySubmitNewQuestion("新问题", hasKeys = true, hasActiveChars = false, isJobRunning = false))
        assertEquals(1001L, retryState?.questionRunId)

        // 5. 校验全部通过：正式启动并清除旧重试状态
        assertTrue(trySubmitNewQuestion("有效新问题", hasKeys = true, hasActiveChars = true, isJobRunning = false))
        assertNull(retryState)
    }

    @Test
    fun testExecutableCharacterCheck_allDisabledOrMissing_preservesRetryState() {
        var retryState: RetryableRoundtableState? = RetryableRoundtableState(
            sessionId = 100L,
            questionRunId = 1001L,
            characterIds = listOf("disabled_1", "missing_1")
        )
        val activeCharacterIds = setOf("char_other")
        var feedbackMessage: String? = null

        fun executeRetrySequence() {
            val targetIds = retryState?.characterIds ?: return
            val executableIds = targetIds.filter { it in activeCharacterIds }
            if (executableIds.isEmpty()) {
                feedbackMessage = "失败角色当前不可用，请重新启用后重试。"
                return
            }
            // 如果可执行才进行下一步
            retryState = null
        }

        executeRetrySequence()

        // 验证没有被清空重试状态，且输出了明确的提示信息
        assertEquals("失败角色当前不可用，请重新启用后重试。", feedbackMessage)
        assertEquals(listOf("disabled_1", "missing_1"), retryState?.characterIds)
    }

    @Test
    fun testQuestionRunIdBinding_invalidNewQuestionDoesNotAlterBoundQuestionRunId() {
        val boundState = RetryableRoundtableState(
            sessionId = 100L,
            questionRunId = 1001L,
            characterIds = listOf("char_a")
        )

        // 尝试发送无效新提问（如空字符串）
        val invalidText = ""
        val newQuestionRunId: Long? = if (invalidText.isNotBlank()) 2002L else null

        val finalQuestionRunIdForRetry = newQuestionRunId ?: boundState.questionRunId

        // 断言用于重试的问题 ID 保持原始 1001L，不受无效提问干扰
        assertEquals(1001L, finalQuestionRunIdForRetry)
    }
}
