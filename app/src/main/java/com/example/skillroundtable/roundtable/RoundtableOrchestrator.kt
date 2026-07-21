package com.example.skillroundtable.roundtable

import android.content.Context
import com.example.skillroundtable.data.Character
import com.example.skillroundtable.data.Message
import com.example.skillroundtable.network.keys.ApiKeyLease
import com.example.skillroundtable.network.keys.ApiKeyScheduler
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

object OrchestratorLogger {
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        try {
            android.util.Log.e(tag, msg, tr)
        } catch (e: RuntimeException) {
            System.err.println("[$tag] $msg")
            tr?.printStackTrace()
        }
    }
}

// 数据库交互网关接口
interface RoundtableDatabaseGateway {
    suspend fun getMessages(sessionId: Long): List<Message>
    suspend fun insertMessage(message: Message): Long
    suspend fun deleteMessageById(id: Long)
    suspend fun removePendingMessages(sessionId: Long)
    suspend fun getActiveCharacters(): List<Character>
}

// 智囊调用网关接口
interface CharacterAnswerGateway {
    suspend fun callGeminiApi(
        character: Character,
        prompt: String,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker,
        budget: RoundtableBudget,
        sessionId: Long
    ): String

    suspend fun getEmbedding(
        context: android.content.Context,
        text: String,
        sessionId: Long,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker
    ): List<Float>
}

// 延迟提供者接口，防止在单元测试中真实等待
interface DelayProvider {
    suspend fun delay(ms: Long)
}

object DefaultDelayProvider : DelayProvider {
    override suspend fun delay(ms: Long) {
        kotlinx.coroutines.delay(ms)
    }
}

// 编排执行结果
data class OrchestrationResult(
    val completedCharacters: List<String>,
    val failedCharacters: List<String>,
    val apiCallsUsed: Int,
    val isStoppedByBudget: Boolean,
    val isLimitExceeded: Boolean
)

// 线程安全的预算管理器，生命周期绑定到唯一的用户提问消息 ID (questionRunId)
class RoundtableBudgetManager(val budget: RoundtableBudget = RoundtableBudget()) {
    private val trackers = ConcurrentHashMap<Long, RequestBudgetTracker>()
    private val answeredCharacters = ConcurrentHashMap<Long, MutableSet<String>>()

    fun getTracker(questionRunId: Long): RequestBudgetTracker {
        return trackers.computeIfAbsent(questionRunId) {
            RequestBudgetTracker(budget.maxApiCallsPerQuestion)
        }
    }

    fun getAnsweredCharacters(questionRunId: Long): MutableSet<String> {
        return answeredCharacters.computeIfAbsent(questionRunId) {
            java.util.concurrent.ConcurrentHashMap.newKeySet()
        }
    }
}

class RoundtableOrchestrator(
    private val context: Context,
    private val dbGateway: RoundtableDatabaseGateway,
    private val answerGateway: CharacterAnswerGateway,
    private val budgetManager: RoundtableBudgetManager,
    private val delayProvider: DelayProvider = DefaultDelayProvider,
    private val minIntervalMs: Long = 1000L,
    private val createAttemptPlan: (Context, Long) -> List<ApiKeyLease> = { ctx, sessId ->
        ApiKeyScheduler.createAttemptPlan(ctx, sessId)
    }
) {
    private val roundtableMutex = Mutex()

    suspend fun runRoundtableSequence(
        sessionId: Long,
        questionRunId: Long,
        isSemanticRoutingEnabled: Boolean
    ): OrchestrationResult {
        if (!roundtableMutex.tryLock()) {
            throw IllegalStateException("Roundtable sequence is already running for session $sessionId")
        }

        val completed = mutableListOf<String>()
        val failed = mutableListOf<String>()
        var isStoppedByBudget = false
        var isLimitExceeded = false

        val tracker = budgetManager.getTracker(questionRunId)
        val answered = budgetManager.getAnsweredCharacters(questionRunId)
        val budget = budgetManager.budget

        try {
            val activeChars = dbGateway.getActiveCharacters()
            if (activeChars.isEmpty()) {
                return OrchestrationResult(emptyList(), emptyList(), tracker.getUsed(), false, false)
            }

            // 1. 确定当前用户提问后面的消息流
            val messages = dbGateway.getMessages(sessionId)
            val runMsgIndex = messages.indexOfFirst { it.id == questionRunId }
            if (runMsgIndex == -1) {
                return OrchestrationResult(emptyList(), emptyList(), tracker.getUsed(), false, false)
            }

            val messagesSinceRun = messages.subList(runMsgIndex + 1, messages.size)
            
            val currentRound = if (messagesSinceRun.isEmpty()) {
                1
            } else {
                val maxRound = messagesSinceRun.maxOf { it.roundIndex }
                val currentRoundMessages = messagesSinceRun.filter { it.roundIndex == maxRound }
                val answeredInCurrentRound = currentRoundMessages.map { it.senderId }.toSet()
                val activeCharIds = activeChars.map { it.id }.toSet()

                if (activeCharIds.all { it in answeredInCurrentRound }) {
                    maxRound + 1
                } else {
                    maxRound
                }
            }

            val messagesInTargetRound = messagesSinceRun.filter { it.roundIndex == currentRound }
            val answeredInTargetRound = messagesInTargetRound.map { it.senderId }.toSet()
            
            // 排除本轮已完成角色或已经被 answered 计数的角色
            val charactersToAnswer = activeChars.filter { it.id !in answeredInTargetRound && it.id !in answered }

            if (charactersToAnswer.isEmpty()) {
                return OrchestrationResult(emptyList(), emptyList(), tracker.getUsed(), false, false)
            }

            // 2. 排序过滤
            var sortedChars = charactersToAnswer
            if (isSemanticRoutingEnabled) {
                val lastUserMsg = messages[runMsgIndex]
                try {
                    // Embedding 调用必须在有预算时发起
                    if (tracker.getUsed() + 2 < budget.maxApiCallsPerQuestion) {
                        val attemptPlan = createAttemptPlan(context, sessionId)
                        val questionVector = answerGateway.getEmbedding(
                            context = context,
                            text = lastUserMsg.text,
                            sessionId = sessionId,
                            attemptPlan = attemptPlan,
                            tracker = tracker
                        )
                        sortedChars = charactersToAnswer.map { character ->
                            val charVector = try {
                                if (character.skillDescriptionVector.isBlank()) emptyList()
                                else character.skillDescriptionVector.split(",").map { it.toFloat() }
                            } catch (e: Exception) {
                                emptyList()
                            }
                            val similarity = if (charVector.isNotEmpty() && questionVector.isNotEmpty()) {
                                calculateCosineSimilarity(questionVector, charVector)
                            } else {
                                0f
                            }
                            Pair(character, similarity)
                        }.sortedByDescending { it.second }.map { it.first }
                    }
                } catch (e: Exception) {
                    OrchestratorLogger.e("RoundtableOrchestrator", "获取 Embedding 失败，降级为默认顺序。")
                }
            }

            // 3. 严格串行执行
            for (character in sortedChars) {
                // 限额限制（最多回答 6 个角色）
                if (answered.size >= budget.maxCharactersPerQuestion) {
                    isLimitExceeded = true
                    break
                }

                // 预算超额熔断（主回答预留至少 1 次额度）
                if (tracker.isExceeded() || tracker.getUsed() >= budget.maxApiCallsPerQuestion) {
                    isStoppedByBudget = true
                    break
                }

                val pendingMsgId = dbGateway.insertMessage(
                    Message(
                        chatId = sessionId,
                        senderId = character.id,
                        senderName = character.name,
                        avatar = character.avatar,
                        text = "正在思考中...",
                        isPending = true,
                        roundIndex = currentRound
                    )
                )

                try {
                    val latestMessages = dbGateway.getMessages(sessionId)
                    val transcript = TranscriptBuilder.build(latestMessages, character, currentRound)
                    val attemptPlan = createAttemptPlan(context, sessionId)

                    if (attemptPlan.isEmpty()) {
                        throw IllegalStateException("没有可用的 API 密钥计划")
                    }

                    val reply = answerGateway.callGeminiApi(
                        character = character,
                        prompt = transcript,
                        attemptPlan = attemptPlan,
                        tracker = tracker,
                        budget = budget,
                        sessionId = sessionId
                    )

                    dbGateway.deleteMessageById(pendingMsgId)
                    dbGateway.insertMessage(
                        Message(
                            chatId = sessionId,
                            senderId = character.id,
                            senderName = character.name,
                            avatar = character.avatar,
                            text = reply,
                            roundIndex = currentRound
                        )
                    )

                    completed.add(character.id)
                    answered.add(character.id)

                } catch (e: Exception) {
                    OrchestratorLogger.e("RoundtableOrchestrator", "智囊回答失败: ${character.name}", e)
                    dbGateway.deleteMessageById(pendingMsgId)
                    failed.add(character.id)
                }

                if (minIntervalMs > 0L) {
                    delayProvider.delay(minIntervalMs)
                }
            }

        } finally {
            roundtableMutex.unlock()
        }

        return OrchestrationResult(
            completedCharacters = completed,
            failedCharacters = failed,
            apiCallsUsed = tracker.getUsed(),
            isStoppedByBudget = isStoppedByBudget,
            isLimitExceeded = isLimitExceeded
        )
    }

    private fun calculateCosineSimilarity(vectorA: List<Float>, vectorB: List<Float>): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
            normA += vectorA[i] * vectorA[i]
            normB += vectorB[i] * vectorB[i]
        }
        val denom = Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())
        return if (denom == 0.0) 0f else (dotProduct / denom).toFloat()
    }
}
