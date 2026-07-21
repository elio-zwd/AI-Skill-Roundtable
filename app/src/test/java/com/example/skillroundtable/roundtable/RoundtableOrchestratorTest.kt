package com.example.skillroundtable.roundtable

import android.content.Context
import com.example.skillroundtable.data.Character
import com.example.skillroundtable.data.Message
import com.example.skillroundtable.network.ApiKeySource
import com.example.skillroundtable.network.keys.ApiKeyLease
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito.mock

class RoundtableOrchestratorTest {

    // 单元测试专用的秒过 DelayProvider
    object ZeroDelayProvider : DelayProvider {
        override suspend fun delay(ms: Long) {}
    }

    // 单元测试专用的虚拟 KeyLease 产生器
    private val testAttemptPlan = { _: Context, _: Long ->
        listOf(ApiKeyLease("test_key", "Test", "secret", ApiKeySource.LOCAL))
    }

    // 手写数据库 Fake，绕过 Mockito Kotlin 基本类型 Match 限制
    class FakeRoundtableDatabaseGateway(
        val messages: MutableList<Message> = mutableListOf(),
        val activeCharacters: MutableList<Character> = mutableListOf()
    ) : RoundtableDatabaseGateway {
        val deletedMessageIds = mutableListOf<Long>()

        override suspend fun getMessages(sessionId: Long): List<Message> {
            return messages.filter { it.chatId == sessionId }
        }

        override suspend fun insertMessage(message: Message): Long {
            val newId = (messages.maxOfOrNull { it.id } ?: 0L) + 1
            val savedMsg = message.copy(id = newId)
            messages.add(savedMsg)
            return newId
        }

        override suspend fun deleteMessageById(id: Long) {
            messages.removeAll { it.id == id }
            deletedMessageIds.add(id)
        }

        override suspend fun removePendingMessages(sessionId: Long) {
            messages.removeAll { it.chatId == sessionId && it.isPending }
        }

        override suspend fun getActiveCharacters(): List<Character> {
            return activeCharacters
        }
    }

    // 手写 API Gateway Fake，完美支持预算统计与断言
    class FakeCharacterAnswerGateway(
        var replyText: String = "默认回复",
        var embeddingValue: List<Float> = listOf(0.1f, 0.2f),
        var onCallApi: ((Character, String) -> Unit)? = null,
        var shouldThrow: Boolean = false
    ) : CharacterAnswerGateway {

        override suspend fun callGeminiApi(
            character: Character,
            prompt: String,
            attemptPlan: List<ApiKeyLease>,
            tracker: RequestBudgetTracker,
            budget: RoundtableBudget,
            sessionId: Long
        ): String {
            if (shouldThrow) {
                throw RuntimeException("Simulated API Error")
            }
            tracker.tryConsume(1)
            onCallApi?.invoke(character, prompt)
            return replyText
        }

        override suspend fun getEmbedding(
            context: Context,
            text: String,
            sessionId: Long,
            attemptPlan: List<ApiKeyLease>,
            tracker: RequestBudgetTracker
        ): List<Float> {
            tracker.tryConsume(1)
            return embeddingValue
        }
    }

    @Test
    fun testSequentialExecutionReadsLatestMessages() = runBlocking {
        val context = mock(Context::class.java)
        val charA = Character(id = "char_a", name = "智囊A", avatar = "A", tagline = "A", systemPrompt = "SetA", order = 1)
        val charB = Character(id = "char_b", name = "智囊B", avatar = "B", tagline = "B", systemPrompt = "SetB", order = 2)

        val messagesList = mutableListOf(
            Message(id = 1001L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "你好")
        )
        val dbGateway = FakeRoundtableDatabaseGateway(messagesList, mutableListOf(charA, charB))
        
        var verifiedBReceivedA = false
        val answerGateway = FakeCharacterAnswerGateway(
            replyText = "智囊回复",
            onCallApi = { character, prompt ->
                if (character.id == "char_b") {
                    // 验证：B 接收的 prompt (Transcript) 里应当已经包含了 A 刚刚入库的发言！
                    if (prompt.contains("智囊回复")) {
                        verifiedBReceivedA = true
                    }
                }
            }
        )

        val budgetManager = RoundtableBudgetManager(RoundtableBudget())
        val orchestrator = RoundtableOrchestrator(
            context = context,
            dbGateway = dbGateway,
            answerGateway = answerGateway,
            budgetManager = budgetManager,
            delayProvider = ZeroDelayProvider,
            minIntervalMs = 0L,
            createAttemptPlan = testAttemptPlan
        )

        val result = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)

        assertEquals("两个角色都应该完成", 2, result.completedCharacters.size)
        assertTrue("B发言前应当读取到A的回复", verifiedBReceivedA)
    }

    @Test
    fun testReentrancyPreventionThrowsException() = runBlocking {
        val context = mock(Context::class.java)
        val charA = Character(id = "char_a", name = "智囊A", avatar = "A", tagline = "A", systemPrompt = "SetA", order = 1)
        val messagesList = mutableListOf(
            Message(id = 1001L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "你好")
        )
        val dbGateway = FakeRoundtableDatabaseGateway(messagesList, mutableListOf(charA))
        
        val answerGateway = object : CharacterAnswerGateway {
            override suspend fun callGeminiApi(
                character: Character,
                prompt: String,
                attemptPlan: List<ApiKeyLease>,
                tracker: RequestBudgetTracker,
                budget: RoundtableBudget,
                sessionId: Long
            ): String {
                // 并发锁定时长模拟
                runBlocking { delay(100) }
                return "A回复"
            }

            override suspend fun getEmbedding(
                context: Context,
                text: String,
                sessionId: Long,
                attemptPlan: List<ApiKeyLease>,
                tracker: RequestBudgetTracker
            ): List<Float> = emptyList()
        }

        val budgetManager = RoundtableBudgetManager(RoundtableBudget())
        val orchestrator = RoundtableOrchestrator(
            context = context,
            dbGateway = dbGateway,
            answerGateway = answerGateway,
            budgetManager = budgetManager,
            delayProvider = ZeroDelayProvider,
            minIntervalMs = 0L,
            createAttemptPlan = testAttemptPlan
        )

        launch {
            orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)
        }

        delay(20) // 等第一个协程先占锁

        try {
            orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)
            fail("重入应当抛出 IllegalStateException 报错")
        } catch (e: IllegalStateException) {
            assertTrue("重入拦截提示正确", e.message?.contains("already running") == true)
        }
    }

    @Test
    fun testCharacterFailureCleansPendingAndContinues() = runBlocking {
        val context = mock(Context::class.java)
        val charA = Character(id = "char_a", name = "智囊A", avatar = "A", tagline = "A", systemPrompt = "SetA", order = 1)
        val charB = Character(id = "char_b", name = "智囊B", avatar = "B", tagline = "B", systemPrompt = "SetB", order = 2)

        val messagesList = mutableListOf(
            Message(id = 1001L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "你好")
        )
        val dbGateway = FakeRoundtableDatabaseGateway(messagesList, mutableListOf(charA, charB))
        
        val budgetManager = RoundtableBudgetManager(RoundtableBudget())

        // 拦截 A 异常，防止测试块直接挂掉
        val result = try {
            // 第一次脑暴：由于 A 挂了，但 Orchestrator 应当能继续调用 B
            val dynamicGateway = object : CharacterAnswerGateway {
                override suspend fun callGeminiApi(
                    character: Character,
                    prompt: String,
                    attemptPlan: List<ApiKeyLease>,
                    tracker: RequestBudgetTracker,
                    budget: RoundtableBudget,
                    sessionId: Long
                ): String {
                    if (character.id == "char_a") {
                        throw RuntimeException("A失败")
                    }
                    return "B回复"
                }

                override suspend fun getEmbedding(
                    context: Context,
                    text: String,
                    sessionId: Long,
                    attemptPlan: List<ApiKeyLease>,
                    tracker: RequestBudgetTracker
                ): List<Float> = emptyList()
            }
            
            val dynOrchestrator = RoundtableOrchestrator(
                context = context,
                dbGateway = dbGateway,
                answerGateway = dynamicGateway,
                budgetManager = budgetManager,
                delayProvider = ZeroDelayProvider,
                minIntervalMs = 0L,
                createAttemptPlan = testAttemptPlan
            )
            dynOrchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)
        } catch (e: Exception) {
            e.printStackTrace()
            fail("Orchestrator 不应该向外抛出异常，应该捕获并继续执行后续角色。根因: ${e.message} / ${e.javaClass.name}")
            throw e
        }

        // 验证：A 失败，B 成功
        assertEquals("B 应该成功完成", 1, result.completedCharacters.size)
        assertTrue(result.completedCharacters.contains("char_b"))
        assertEquals("A 应该失败", 1, result.failedCharacters.size)
        assertTrue(result.failedCharacters.contains("char_a"))

        // 验证：A 的思考中 pending 消息在失败后确实被移除了
        assertTrue("A的Pending消息被清理", dbGateway.deletedMessageIds.isNotEmpty())
    }

    @Test
    fun testBudgetNotResetAcrossCalls() = runBlocking {
        val context = mock(Context::class.java)
        val charA = Character(id = "char_a", name = "智囊A", avatar = "A", tagline = "A", systemPrompt = "", order = 1)
        val charB = Character(id = "char_b", name = "智囊B", avatar = "B", tagline = "B", systemPrompt = "", order = 2)
        val charC = Character(id = "char_c", name = "智囊C", avatar = "C", tagline = "C", systemPrompt = "", order = 3)

        val messagesList = mutableListOf(
            Message(id = 1001L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "你好")
        )
        // 限制角色数上限为 2
        val budget = RoundtableBudget(maxCharactersPerQuestion = 2, maxApiCallsPerQuestion = 30)
        val budgetManager = RoundtableBudgetManager(budget)
        
        val dbGateway = FakeRoundtableDatabaseGateway(messagesList, mutableListOf(charA, charB))
        val answerGateway = FakeCharacterAnswerGateway("回复")

        val orchestrator = RoundtableOrchestrator(
            context = context,
            dbGateway = dbGateway,
            answerGateway = answerGateway,
            budgetManager = budgetManager,
            delayProvider = ZeroDelayProvider,
            minIntervalMs = 0L,
            createAttemptPlan = testAttemptPlan
        )

        // 第一次调用：只提供 A 和 B 激活
        val result1 = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)
        assertEquals("A和B完成", 2, result1.completedCharacters.size)
        assertFalse(result1.isLimitExceeded)

        // 模拟 A 和 B 的发言已经入库
        messagesList.add(Message(id = 1002L, chatId = 1L, senderId = "char_a", senderName = "A", avatar = "A", text = "回复"))
        messagesList.add(Message(id = 1003L, chatId = 1L, senderId = "char_b", senderName = "B", avatar = "B", text = "回复"))
        
        // 激活 C 角色并再次触发（模拟点击催促）
        dbGateway.activeCharacters.add(charC)
        val result2 = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)

        // 验证：由于同一问题的角色作答总数已达上限 2，C 角色应被限额跳过
        assertTrue("第二次执行同一问题应限额熔断", result2.isLimitExceeded)
        assertEquals("C 无法回答", 0, result2.completedCharacters.size)
    }
}
