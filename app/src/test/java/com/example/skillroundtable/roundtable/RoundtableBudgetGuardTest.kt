package com.example.skillroundtable.roundtable

import android.content.Context
import com.example.skillroundtable.data.Character
import com.example.skillroundtable.data.Message
import com.example.skillroundtable.network.ApiKeySource
import com.example.skillroundtable.network.keys.ApiKeyLease
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class RoundtableBudgetGuardTest {

    private object ZeroDelayProvider : DelayProvider {
        override suspend fun delay(ms: Long) = Unit
    }

    private class FakeDatabaseGateway(
        val messages: MutableList<Message>,
        val activeCharacters: MutableList<Character>
    ) : RoundtableDatabaseGateway {
        override suspend fun getMessages(sessionId: Long): List<Message> {
            return messages.filter { it.chatId == sessionId }
        }

        override suspend fun insertMessage(message: Message): Long {
            val nextId = (messages.maxOfOrNull { it.id } ?: 0L) + 1L
            messages.add(message.copy(id = nextId))
            return nextId
        }

        override suspend fun deleteMessageById(id: Long) {
            messages.removeAll { it.id == id }
        }

        override suspend fun removePendingMessages(sessionId: Long) {
            messages.removeAll { it.chatId == sessionId && it.isPending }
        }

        override suspend fun getActiveCharacters(): List<Character> = activeCharacters.toList()
    }

    private class BudgetConsumingAnswerGateway : CharacterAnswerGateway {
        override suspend fun callGeminiApi(
            character: Character,
            prompt: String,
            attemptPlan: List<ApiKeyLease>,
            tracker: RequestBudgetTracker,
            budget: RoundtableBudget,
            sessionId: Long,
            isRequired: Boolean,
            reserveForRequired: Int
        ): String {
            val consumed = if (isRequired) {
                tracker.tryConsumeRequired(
                    count = 1,
                    reserveForOtherRequired = (reserveForRequired - 1).coerceAtLeast(0)
                )
            } else {
                tracker.tryConsumeOptional(1, reserveForRequired)
            }
            check(consumed) { "预算预留拒绝了请求" }
            return "${character.name}回复"
        }

        override suspend fun getEmbedding(
            context: Context,
            text: String,
            sessionId: Long,
            attemptPlan: List<ApiKeyLease>,
            tracker: RequestBudgetTracker,
            isRequired: Boolean,
            reserveForRequired: Int
        ): List<Float> = emptyList()
    }

    private fun character(id: String, order: Int): Character {
        return Character(
            id = id,
            name = id,
            avatar = id,
            tagline = "测试角色",
            systemPrompt = "测试提示",
            order = order
        )
    }

    private fun orchestrator(
        database: FakeDatabaseGateway,
        budgetManager: RoundtableBudgetManager
    ): RoundtableOrchestrator {
        return RoundtableOrchestrator(
            context = mock(Context::class.java),
            dbGateway = database,
            answerGateway = BudgetConsumingAnswerGateway(),
            budgetManager = budgetManager,
            delayProvider = ZeroDelayProvider,
            minIntervalMs = 0L,
            createAttemptPlan = { _, _ ->
                listOf(ApiKeyLease("key", "测试 Key", "secret", ApiKeySource.LOCAL))
            }
        )
    }

    @Test
    fun optionalRequestCannotConsumeGloballyReservedRoundBudget() {
        val tracker = RequestBudgetTracker(limit = 2)
        tracker.setRequiredReserve(2)

        assertFalse(
            "当剩余预算刚好只够两个主回答时，异步标题等 OPTIONAL 请求必须被拒绝",
            tracker.tryConsumeOptional(count = 1, reserveForRequired = 0)
        )
        assertEquals(0, tracker.getUsed())
        assertEquals(2, tracker.getRequiredReserve())
    }

    @Test
    fun lockedParticipantSnapshotSurvivesDeactivation() = runBlocking {
        val charA = character("char_a", 1)
        val charB = character("char_b", 2)
        val database = FakeDatabaseGateway(
            messages = mutableListOf(
                Message(
                    id = 1001L,
                    chatId = 1L,
                    senderId = "user",
                    senderName = "User",
                    avatar = "U",
                    text = "测试问题"
                )
            ),
            activeCharacters = mutableListOf(charA, charB)
        )
        val budgetManager = RoundtableBudgetManager(
            RoundtableBudget(maxCharactersPerQuestion = 2, maxApiCallsPerQuestion = 10)
        )
        val orchestrator = orchestrator(database, budgetManager)

        val firstRound = orchestrator.runRoundtableSequence(
            sessionId = 1L,
            questionRunId = 1001L,
            isSemanticRoutingEnabled = false
        )
        assertEquals(listOf("char_a", "char_b"), firstRound.completedCharacters)

        // 模拟第一轮结束后 B 被停用或从当前激活列表移除。
        database.activeCharacters.clear()
        database.activeCharacters.add(charA)

        val secondRound = orchestrator.runRoundtableSequence(
            sessionId = 1L,
            questionRunId = 1001L,
            isSemanticRoutingEnabled = false
        )

        assertEquals(
            "当前问题必须继续复用首次锁定的角色快照，不能因 B 被停用而卡死或换入新角色",
            listOf("char_a", "char_b"),
            secondRound.completedCharacters
        )
    }

    @Test
    fun insufficientBudgetClosesQuestionBeforePartialNextRound() = runBlocking {
        val charA = character("char_a", 1)
        val charB = character("char_b", 2)
        val database = FakeDatabaseGateway(
            messages = mutableListOf(
                Message(
                    id = 1001L,
                    chatId = 1L,
                    senderId = "user",
                    senderName = "User",
                    avatar = "U",
                    text = "测试问题"
                )
            ),
            activeCharacters = mutableListOf(charA, charB)
        )
        val budgetManager = RoundtableBudgetManager(
            RoundtableBudget(maxCharactersPerQuestion = 2, maxApiCallsPerQuestion = 3)
        )
        val orchestrator = orchestrator(database, budgetManager)

        val firstRound = orchestrator.runRoundtableSequence(
            sessionId = 1L,
            questionRunId = 1001L,
            isSemanticRoutingEnabled = false
        )
        assertEquals(2, firstRound.completedCharacters.size)

        val tracker = budgetManager.getTracker(1001L)
        assertEquals("真实调用计数不能为了关闭预算而伪造为上限", 2, tracker.getUsed())
        assertTrue("只剩一次额度，无法完成两人下一整轮时应关闭该问题", tracker.isExceeded())

        val secondRound = orchestrator.runRoundtableSequence(
            sessionId = 1L,
            questionRunId = 1001L,
            isSemanticRoutingEnabled = false
        )
        assertTrue(secondRound.isStoppedByBudget)
        assertTrue(secondRound.completedCharacters.isEmpty())
        assertEquals(2, tracker.getUsed())
    }

    @Test
    fun insufficientBudgetDoesNotStartPartialCurrentRound() = runBlocking {
        val charA = character("char_a", 1)
        val charB = character("char_b", 2)
        val database = FakeDatabaseGateway(
            messages = mutableListOf(
                Message(
                    id = 1001L,
                    chatId = 1L,
                    senderId = "user",
                    senderName = "User",
                    avatar = "U",
                    text = "测试问题"
                )
            ),
            activeCharacters = mutableListOf(charA, charB)
        )
        val budgetManager = RoundtableBudgetManager(
            RoundtableBudget(maxCharactersPerQuestion = 2, maxApiCallsPerQuestion = 1)
        )
        val orchestrator = orchestrator(database, budgetManager)

        val result = orchestrator.runRoundtableSequence(
            sessionId = 1L,
            questionRunId = 1001L,
            isSemanticRoutingEnabled = false
        )

        assertTrue(result.isStoppedByBudget)
        assertTrue(result.completedCharacters.isEmpty())
        assertEquals(0, budgetManager.getTracker(1001L).getUsed())
        assertFalse(database.messages.any { it.isPending })
    }
}
