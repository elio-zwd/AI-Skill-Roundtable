package com.example.skillroundtable.roundtable

import com.example.skillroundtable.data.Character
import com.example.skillroundtable.data.Message
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class RoundtableOrchestratorTest {

    // 模拟的数据仓库，提供最新的消息存取
    class FakeChatRepository {
        private val db = mutableListOf<Message>()
        
        fun getMessages(sessionId: Long): List<Message> {
            return db.toList()
        }

        fun insertMessage(message: Message) {
            db.add(message)
        }
    }

    // 模拟的模型调用器
    class FakeGeminiApi(private val delayMs: Long = 0) {
        val callCount = AtomicInteger(0)
        
        suspend fun call(character: Character, history: List<Message>): String {
            callCount.incrementAndGet()
            if (delayMs > 0) {
                delay(delayMs)
            }
            // 返回带有当前看到的前序消息数的信息
            return "我是 ${character.name}，我看到了 ${history.size} 条消息。"
        }
    }

    // 核心重载模型：完美对齐 RoundtableViewModel 的排他锁与增量串行调度逻辑
    class MockRoundtableOrchestrator(
        private val chatRepo: FakeChatRepository,
        private val api: FakeGeminiApi
    ) {
        val roundtableMutex = Mutex()
        var isRunning = false

        suspend fun runRoundtableSequence(sessionId: Long, activeChars: List<Character>) {
            if (!roundtableMutex.tryLock()) {
                // 防重入：获取锁失败直接返回
                return
            }
            isRunning = true
            try {
                for (character in activeChars) {
                    // 核心关键点：每次开始前重新读取最新消息列表，保证 100% 看到前序已入库的内容
                    val latestMessages = chatRepo.getMessages(sessionId)

                    // 执行脑暴调用
                    val replyText = api.call(character, latestMessages)

                    // 将回复入库，以供后面的角色读取
                    chatRepo.insertMessage(
                        Message(
                            chatId = sessionId,
                            senderId = character.id,
                            senderName = character.name,
                            avatar = character.avatar,
                            text = replyText
                        )
                    )
                }
            } finally {
                isRunning = false
                roundtableMutex.unlock()
            }
        }
    }

    @Test
    fun testSequentialExecutionReadsLatestMessages() = runBlocking {
        val chatRepo = FakeChatRepository()
        val api = FakeGeminiApi()
        val orchestrator = MockRoundtableOrchestrator(chatRepo, api)

        // 插入最初的用户提问
        chatRepo.insertMessage(Message(chatId = 1, senderId = "user", senderName = "User", avatar = "U", text = "什么是大语言模型？"))

        val charA = Character(id = "char_a", name = "智囊A", avatar = "A", tagline = "A", systemPrompt = "", skillAssetPath = "", order = 1)
        val charB = Character(id = "char_b", name = "智囊B", avatar = "B", tagline = "B", systemPrompt = "", skillAssetPath = "", order = 2)

        // 串行执行智囊 A 和 B
        orchestrator.runRoundtableSequence(sessionId = 1, activeChars = listOf(charA, charB))

        val finalMessages = chatRepo.getMessages(sessionId = 1)
        
        // 期望：
        // 1. 初始 1 条（用户提问）
        // 2. 智囊 A 发言入库（总数变为 2）
        // 3. 智囊 B 发言前读取到了 2 条消息，其发言入库（总数变为 3）
        assertEquals("最终数据库中消息数应该为 3", 3, finalMessages.size)

        val msgA = finalMessages[1]
        val msgB = finalMessages[2]

        assertEquals("智囊 A 首发应看到 1 条历史消息（即用户提问）", "我是 智囊A，我看到了 1 条消息。", msgA.text)
        assertEquals("智囊 B 发言前应看到 2 条消息（用户提问 + 智囊 A 回复）", "我是 智囊B，我看到了 2 条消息。", msgB.text)
    }

    @Test
    fun testRoundtableReentrancyPrevention() = runBlocking {
        val chatRepo = FakeChatRepository()
        // 让 API 模拟需要时间思考的延迟，这样可以测试并发触发的情况
        val api = FakeGeminiApi(delayMs = 100)
        val orchestrator = MockRoundtableOrchestrator(chatRepo, api)

        val charA = Character(id = "char_a", name = "智囊A", avatar = "A", tagline = "A", systemPrompt = "", skillAssetPath = "", order = 1)

        // 在协程中启动圆桌序列 1
        launch {
            orchestrator.runRoundtableSequence(sessionId = 1, activeChars = listOf(charA))
        }

        // 稍等 20ms 以让第一个序列获取到锁并进入 API 思考中
        delay(20)
        assertTrue("第一个序列应当成功获取锁并处于运行状态", orchestrator.isRunning)

        // 在第一个序列结束前，试图重复触发第二次脑暴（重入）
        launch {
            orchestrator.runRoundtableSequence(sessionId = 1, activeChars = listOf(charA))
        }

        // 等待所有协程执行完毕
        delay(150)

        // 验证：API 应该仅仅被调用了 1 次（第二次重入因 tryLock 失败立即安全返回，未执行）
        assertEquals("防重入机制成功生效，API 只应该被调用 1 次", 1, api.callCount.get())
        assertFalse("圆桌序列最终状态应重置为未运行", orchestrator.isRunning)
    }
}
