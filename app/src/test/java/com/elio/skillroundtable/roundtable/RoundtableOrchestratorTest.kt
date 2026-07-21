package com.elio.skillroundtable.roundtable

import android.content.Context
import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.data.Message
import com.elio.skillroundtable.network.ApiKeySource
import com.elio.skillroundtable.network.keys.ApiKeyLease
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.mockito.Mockito.mock
import okhttp3.MediaType.Companion.toMediaType

class RoundtableOrchestratorTest {

    object ZeroDelayProvider : DelayProvider {
        override suspend fun delay(ms: Long) {}
    }

    private val testAttemptPlan = { _: Context, _: Long ->
        listOf(ApiKeyLease("test_key", "Test", "secret", ApiKeySource.LOCAL))
    }

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

    class FakeCharacterAnswerGateway(
        var replyText: String = "默认回复",
        var embeddingValue: List<Float> = listOf(0.1f, 0.2f),
        var onCallApi: ((Character, String) -> Unit)? = null,
        var onCallApiDetailed: ((Character, String, Boolean, Int) -> Unit)? = null,
        var shouldThrow: Boolean = false
    ) : CharacterAnswerGateway {

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
            if (shouldThrow) {
                throw RuntimeException("Simulated API Error")
            }
            val consumed = if (isRequired) {
                tracker.tryConsumeRequired(1)
            } else {
                tracker.tryConsumeOptional(1, reserveForRequired)
            }
            if (!consumed) {
                throw RuntimeException("Budget reservation failed")
            }
            onCallApi?.invoke(character, prompt)
            onCallApiDetailed?.invoke(character, prompt, isRequired, reserveForRequired)
            return replyText
        }

        override suspend fun getEmbedding(
            context: Context,
            text: String,
            sessionId: Long,
            attemptPlan: List<ApiKeyLease>,
            tracker: RequestBudgetTracker,
            isRequired: Boolean,
            reserveForRequired: Int
        ): List<Float> {
            val consumed = if (isRequired) {
                tracker.tryConsumeRequired(1)
            } else {
                tracker.tryConsumeOptional(1, reserveForRequired)
            }
            if (!consumed) {
                throw RuntimeException("Budget reservation failed for Embedding")
            }
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
                sessionId: Long,
                isRequired: Boolean,
                reserveForRequired: Int
            ): String {
                runBlocking { delay(100) }
                return "A回复"
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

        delay(20)

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

        val dynamicGateway = object : CharacterAnswerGateway {
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
                tracker: RequestBudgetTracker,
                isRequired: Boolean,
                reserveForRequired: Int
            ): List<Float> = emptyList()
        }

        val orchestrator = RoundtableOrchestrator(
            context = context,
            dbGateway = dbGateway,
            answerGateway = dynamicGateway,
            budgetManager = budgetManager,
            delayProvider = ZeroDelayProvider,
            minIntervalMs = 0L,
            createAttemptPlan = testAttemptPlan
        )

        val result = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)

        assertEquals("B 应该成功完成", 1, result.completedCharacters.size)
        assertTrue(result.completedCharacters.contains("char_b"))
        assertEquals("A 应该失败", 1, result.failedCharacters.size)
        assertTrue(result.failedCharacters.contains("char_a"))
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

        val result1 = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)
        assertEquals("A和B完成", 2, result1.completedCharacters.size)
        assertFalse(result1.isLimitExceeded)

        messagesList.add(Message(id = 1002L, chatId = 1L, senderId = "char_a", senderName = "A", avatar = "A", text = "回复"))
        messagesList.add(Message(id = 1003L, chatId = 1L, senderId = "char_b", senderName = "B", avatar = "B", text = "回复"))

        dbGateway.activeCharacters.add(charC)
        val result2 = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)

        assertEquals("C 作为第 3 个角色无法参与回答", 0, result2.completedCharacters.filter { it == "char_c" }.size)
        val totalDistinctParticipants = (result1.completedCharacters + result2.completedCharacters).distinct().size
        assertTrue("该问题的总不同参与者人数被锁定限制为 2 人", totalDistinctParticipants <= 2)
    }

    @Test
    fun optionalRequestsCannotConsumeReservedMainAnswerBudget() = runBlocking {
        val context = mock(Context::class.java)
        val charA = Character(id = "char_a", name = "智囊A", avatar = "A", tagline = "", systemPrompt = "", order = 1)
        val charB = Character(id = "char_b", name = "智囊B", avatar = "B", tagline = "", systemPrompt = "", order = 2)

        val messagesList = mutableListOf(
            Message(id = 1001L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "提问")
        )
        // 限制最大 API 次数为 2 次，这就刚好只够 A 和 B 的主回答 (REQUIRED)
        val budget = RoundtableBudget(maxCharactersPerQuestion = 2, maxApiCallsPerQuestion = 2)
        val budgetManager = RoundtableBudgetManager(budget)
        val tracker = budgetManager.getTracker(1001L)

        val dbGateway = FakeRoundtableDatabaseGateway(messagesList, mutableListOf(charA, charB))

        // 验证可选消费 Embedding 会直接由于预留不足而报错拦截，保全主回答
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

        // 开启语义路由（在 charactersToAnswer.size = 2 时，Embedding (OPTIONAL) 消费 1 次需要预留 2 次 REQUIRED 主回答，
        // 即 1 + 2 = 3 > 2 预算，所以 Embedding 应当被拒绝，退回到默认排序）
        val result = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = true)

        assertEquals("圆桌依然完成了 2 个角色的主回答", 2, result.completedCharacters.size)
        assertEquals("API 次数刚好消耗 2 次", 2, tracker.getUsed())
    }

    @Test
    fun optionalBudgetRespectsRequiredReserve() = runBlocking {
        val budget = RoundtableBudget(maxApiCallsPerQuestion = 5)
        val tracker = RequestBudgetTracker(budget.maxApiCallsPerQuestion)

        // 模拟圆桌进行中需要 5 个主回答（预留 5）
        val reserve = 5

        // 自动标题属于 OPTIONAL，在圆桌进行时它在 tryConsumeOptional 里会由于预留不足被拦截，不与圆桌并发争抢额度
        val successDuringRound = tracker.tryConsumeOptional(1, reserveForRequired = reserve)
        assertFalse("圆桌进行中并发生成标题会被拒绝", successDuringRound)

        // 圆桌完成后（reserve 降为 0），可以成功生成标题
        val successAfterRound = tracker.tryConsumeOptional(1, reserveForRequired = 0)
        assertTrue("圆桌完成后可生成标题", successAfterRound)
    }

    @Test
    fun secondRoundAllowsSameSelectedParticipants() = runBlocking {
        val context = mock(Context::class.java)
        val charA = Character(id = "char_a", name = "智囊A", avatar = "A", tagline = "", systemPrompt = "", order = 1)
        val charB = Character(id = "char_b", name = "智囊B", avatar = "B", tagline = "", systemPrompt = "", order = 2)

        val messagesList = mutableListOf(
            Message(id = 1001L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "你好")
        )
        val dbGateway = FakeRoundtableDatabaseGateway(messagesList, mutableListOf(charA, charB))
        val answerGateway = FakeCharacterAnswerGateway("回复")
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

        // 第一轮脑暴
        val result1 = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)
        assertEquals(2, result1.completedCharacters.size)

        // 写入第一轮的发言结果
        messagesList.add(Message(id = 1002L, chatId = 1L, senderId = "char_a", senderName = "A", avatar = "A", text = "回复A", roundIndex = 1))
        messagesList.add(Message(id = 1003L, chatId = 1L, senderId = "char_b", senderName = "B", avatar = "B", text = "回复B", roundIndex = 1))

        // 第二轮脑暴开始
        val result2 = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)
        assertEquals("第二轮应允许相同的 2 个智囊再次发言", 2, result2.completedCharacters.size)
    }

    @Test
    fun secondRoundTranscriptContainsPreviousRound() = runBlocking {
        val context = mock(Context::class.java)
        val charA = Character(id = "char_a", name = "智囊A", avatar = "A", tagline = "", systemPrompt = "", order = 1)
        val charB = Character(id = "char_b", name = "智囊B", avatar = "B", tagline = "", systemPrompt = "", order = 2)

        val messagesList = mutableListOf(
            Message(id = 1001L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "你好"),
            Message(id = 1002L, chatId = 1L, senderId = "char_a", senderName = "A", avatar = "A", text = "第一轮发言A", roundIndex = 1),
            Message(id = 1003L, chatId = 1L, senderId = "char_b", senderName = "B", avatar = "B", text = "第一轮发言B", roundIndex = 1)
        )
        val dbGateway = FakeRoundtableDatabaseGateway(messagesList, mutableListOf(charA, charB))

        var transcriptForB = ""
        val answerGateway = FakeCharacterAnswerGateway(
            replyText = "第二轮回复",
            onCallApi = { character, prompt ->
                if (character.id == "char_b") {
                    transcriptForB = prompt
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

        // 运行第二轮脑暴
        orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)

        // 验证 Transcript 包含了第一轮的发言内容
        assertTrue("Transcript应包含第一轮A的发言", transcriptForB.contains("第一轮发言A"))
        assertTrue("Transcript应包含第一轮B的发言", transcriptForB.contains("第一轮发言B"))
    }

    @Test
    fun seventhParticipantIsNeverAddedToSameQuestion() = runBlocking {
        val context = mock(Context::class.java)
        val activeChars = (1..8).map { i ->
            Character(id = "char_$i", name = "智囊$i", avatar = "A", tagline = "", systemPrompt = "", order = i)
        }
        val messagesList = mutableListOf(
            Message(id = 1001L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "提问")
        )
        val dbGateway = FakeRoundtableDatabaseGateway(messagesList, activeChars.toMutableList())
        val answerGateway = FakeCharacterAnswerGateway("回复")
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

        // 第一次触发：只取前 6 位进行固化
        val result1 = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)
        assertEquals(6, result1.completedCharacters.size)

        // 模拟这 6 人发言入库
        (1..6).forEach { i ->
            messagesList.add(Message(id = 1001L + i, chatId = 1L, senderId = "char_$i", senderName = "智囊$i", avatar = "A", text = "回复", roundIndex = 1))
        }

        // 第二次触发（仍然绑定相同的 1001L 提问）：即使 activeChars 包含 8 个人，也只能在这 6 个人的范围进行，绝对不会混入 char_7 或 char_8
        val result2 = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)

        assertEquals(6, result2.completedCharacters.size)
        assertFalse("绝对不包含第 7 位角色", result2.completedCharacters.contains("char_7"))
        assertFalse("绝对不包含第 8 位角色", result2.completedCharacters.contains("char_8"))
    }

    @Test
    fun newUserQuestionCutsPreviousTranscript() = runBlocking {
        val messagesList = mutableListOf(
            Message(id = 1001L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "第一个提问"),
            Message(id = 1002L, chatId = 1L, senderId = "char_a", senderName = "A", avatar = "A", text = "老回复"),
            Message(id = 1003L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "新提问")
        )
        val charA = Character(id = "char_a", name = "智囊A", avatar = "A", tagline = "", systemPrompt = "", order = 1)

        val transcript = TranscriptBuilder.build(messagesList, charA, roundIndex = 1)

        assertTrue(transcript.contains("新提问"))
        assertFalse(transcript.contains("第一个提问"))
        assertFalse(transcript.contains("老回复"))
    }

    @Test
    fun requiredRetryPreservesLaterParticipants() = runBlocking {
        val context = mock(Context::class.java)
        // 预算限额是 2，我们有两个 REQUIRED 主回答（A 和 B）
        val budget = RoundtableBudget(maxCharactersPerQuestion = 2, maxApiCallsPerQuestion = 2)
        val budgetManager = RoundtableBudgetManager(budget)

        val charA = Character(id = "char_a", name = "A", avatar = "A", tagline = "", systemPrompt = "", order = 1)
        val charB = Character(id = "char_b", name = "B", avatar = "B", tagline = "", systemPrompt = "", order = 2)

        val messagesList = mutableListOf(
            Message(id = 1001L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "你好")
        )
        val dbGateway = FakeRoundtableDatabaseGateway(messagesList, mutableListOf(charA, charB))

        var callCountA = 0
        val answerGateway = object : CharacterAnswerGateway {
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
                if (character.id == "char_a") {
                    callCountA++
                    // 模拟 500 错误以触发 RETRY_SAME_KEY 决策
                    val response = retrofit2.Response.error<String>(
                        500,
                        okhttp3.ResponseBody.create("application/json".toMediaType(), "Internal Server Error")
                    )
                    throw retrofit2.HttpException(response)
                }
                return "B的回复"
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

        assertEquals("A 应该失败", 1, result.failedCharacters.size)
        assertTrue("A 失败列表包含 char_a", result.failedCharacters.contains("char_a"))
        assertEquals("B 应该成功", 1, result.completedCharacters.size)
        assertTrue("B 成功列表包含 char_b", result.completedCharacters.contains("char_b"))
        assertEquals("A 最终只被真实调用了 1 次（重试被拦截，保全了B的额度）", 1, callCountA)
    }

    @Test
    fun requiredKeyFallbackPreservesLaterParticipants() = runBlocking {
        val context = mock(Context::class.java)
        // 预算限额是 2。有两个智囊。
        val budget = RoundtableBudget(maxCharactersPerQuestion = 2, maxApiCallsPerQuestion = 2)
        val budgetManager = RoundtableBudgetManager(budget)

        val charA = Character(id = "char_a", name = "A", avatar = "A", tagline = "", systemPrompt = "", order = 1)
        val charB = Character(id = "char_b", name = "B", avatar = "B", tagline = "", systemPrompt = "", order = 2)

        val messagesList = mutableListOf(
            Message(id = 1001L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "你好")
        )
        val dbGateway = FakeRoundtableDatabaseGateway(messagesList, mutableListOf(charA, charB))

        var callCountA = 0
        val answerGateway = object : CharacterAnswerGateway {
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
                if (character.id == "char_a") {
                    callCountA++
                    val response = retrofit2.Response.error<String>(
                        401,
                        okhttp3.ResponseBody.create("application/json".toMediaType(), "Unauthorized")
                    )
                    throw retrofit2.HttpException(response)
                }
                return "B的回复"
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

        val attemptPlanTwoKeys = { _: Context, _: Long ->
            listOf(
                ApiKeyLease("key_1", "K1", "secret_1", ApiKeySource.LOCAL),
                ApiKeyLease("key_2", "K2", "secret_2", ApiKeySource.LOCAL)
            )
        }

        val orchestrator = RoundtableOrchestrator(
            context = context,
            dbGateway = dbGateway,
            answerGateway = answerGateway,
            budgetManager = budgetManager,
            delayProvider = ZeroDelayProvider,
            minIntervalMs = 0L,
            createAttemptPlan = attemptPlanTwoKeys
        )

        val result = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = false)

        assertEquals("A 应该失败", 1, result.failedCharacters.size)
        assertEquals("B 应该成功", 1, result.completedCharacters.size)
        assertEquals("A 最终只被真实调用了 1 次（换 Key 被拦截，保全了B的额度）", 1, callCountA)
    }

    @Test
    fun semanticRoutingSelectsTopSixBeforeParticipantLock() = runBlocking {
        val context = mock(Context::class.java)
        val budget = RoundtableBudget(maxCharactersPerQuestion = 3) // 设上限为 3 位角色
        val budgetManager = RoundtableBudgetManager(budget)

        val chars = (1..5).map { i ->
            Character(
                id = "char_$i",
                name = "智囊$i",
                avatar = "A",
                tagline = "",
                systemPrompt = "",
                order = i,
                skillDescriptionVector = when(i) {
                    1 -> "0.0,1.0"
                    2 -> "1.0,0.0" // 相似度最高 (1.0)
                    3 -> "0.8,0.6" // 相似度第二 (0.8)
                    4 -> "0.5,0.866" // 相似度第三 (0.5)
                    else -> "-1.0,0.0"
                }
            )
        }

        val messagesList = mutableListOf(
            Message(id = 1001L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "目标提问")
        )
        val dbGateway = FakeRoundtableDatabaseGateway(messagesList, chars.toMutableList())

        val answerGateway = object : CharacterAnswerGateway {
            override suspend fun callGeminiApi(
                character: Character,
                prompt: String,
                attemptPlan: List<ApiKeyLease>,
                tracker: RequestBudgetTracker,
                budget: RoundtableBudget,
                sessionId: Long,
                isRequired: Boolean,
                reserveForRequired: Int
            ): String = "回复"

            override suspend fun getEmbedding(
                context: Context,
                text: String,
                sessionId: Long,
                attemptPlan: List<ApiKeyLease>,
                tracker: RequestBudgetTracker,
                isRequired: Boolean,
                reserveForRequired: Int
            ): List<Float> = listOf(1.0f, 0.0f) // 目标向量
        }

        val orchestrator = RoundtableOrchestrator(
            context = context,
            dbGateway = dbGateway,
            answerGateway = answerGateway,
            budgetManager = budgetManager,
            delayProvider = ZeroDelayProvider,
            minIntervalMs = 0L,
            createAttemptPlan = testAttemptPlan
        )

        val result = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = true)

        val lockedIds = budgetManager.getSelectedParticipants(1001L)

        assertEquals(3, lockedIds?.size)
        assertEquals("char_2", lockedIds?.get(0))
        assertEquals("char_3", lockedIds?.get(1))
        assertEquals("char_4", lockedIds?.get(2))
    }

    @Test
    fun laterRoundsReuseLockedSemanticParticipants() = runBlocking {
        val context = mock(Context::class.java)
        val budget = RoundtableBudget(maxCharactersPerQuestion = 2) // 上限为 2 位角色
        val budgetManager = RoundtableBudgetManager(budget)

        val charA = Character(id = "char_a", name = "A", avatar = "A", tagline = "", systemPrompt = "", order = 1)
        val charB = Character(id = "char_b", name = "B", avatar = "B", tagline = "", systemPrompt = "", order = 2)
        val charC = Character(id = "char_c", name = "C", avatar = "C", tagline = "", systemPrompt = "", order = 3)

        budgetManager.setSelectedParticipants(1001L, listOf("char_a", "char_c"))

        val messagesList = mutableListOf(
            Message(id = 1001L, chatId = 1L, senderId = "user", senderName = "User", avatar = "U", text = "你好")
        )
        val dbGateway = FakeRoundtableDatabaseGateway(messagesList, mutableListOf(charA, charB, charC))

        val answerGateway = object : CharacterAnswerGateway {
            override suspend fun callGeminiApi(
                character: Character,
                prompt: String,
                attemptPlan: List<ApiKeyLease>,
                tracker: RequestBudgetTracker,
                budget: RoundtableBudget,
                sessionId: Long,
                isRequired: Boolean,
                reserveForRequired: Int
            ): String = "回复"

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

        val orchestrator = RoundtableOrchestrator(
            context = context,
            dbGateway = dbGateway,
            answerGateway = answerGateway,
            budgetManager = budgetManager,
            delayProvider = ZeroDelayProvider,
            minIntervalMs = 0L,
            createAttemptPlan = testAttemptPlan
        )

        val result = orchestrator.runRoundtableSequence(sessionId = 1L, questionRunId = 1001L, isSemanticRoutingEnabled = true)

        assertEquals("只有 A 和 C 完成作答", 2, result.completedCharacters.size)
        assertTrue(result.completedCharacters.contains("char_a"))
        assertTrue(result.completedCharacters.contains("char_c"))
        assertFalse(result.completedCharacters.contains("char_b"))
    }
}
