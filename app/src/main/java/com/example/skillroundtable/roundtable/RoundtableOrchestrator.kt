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
        sessionId: Long,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0
    ): String

    suspend fun getEmbedding(
        context: Context,
        text: String,
        sessionId: Long,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0
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
    private val selectedParticipants = ConcurrentHashMap<Long, List<String>>()

    /**
     * 保存本问题第一次锁定时的角色快照。角色在问题进行中被停用、编辑或删除时，
     * 当前问题仍使用原快照完成后续轮次，避免锁定 ID 与当前激活列表不一致导致死锁。
     */
    private val selectedParticipantSnapshots = ConcurrentHashMap<Long, List<Character>>()

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

    fun getOrSetSelectedParticipants(questionRunId: Long, activeCharIds: List<String>): List<String> {
        return selectedParticipants.computeIfAbsent(questionRunId) {
            activeCharIds.take(budget.maxCharactersPerQuestion)
        }
    }

    fun getSelectedParticipants(questionRunId: Long): List<String>? {
        return selectedParticipants[questionRunId]
    }

    fun setSelectedParticipants(questionRunId: Long, ids: List<String>) {
        selectedParticipants[questionRunId] = ids.toList()
        selectedParticipantSnapshots.remove(questionRunId)
    }

    fun getSelectedParticipantSnapshots(questionRunId: Long): List<Character>? {
        return selectedParticipantSnapshots[questionRunId]
    }

    fun setSelectedParticipantSnapshots(questionRunId: Long, participants: List<Character>) {
        val snapshots = participants.toList()
        selectedParticipantSnapshots[questionRunId] = snapshots
        selectedParticipants[questionRunId] = snapshots.map { it.id }
    }

    fun clearQuestion(questionRunId: Long) {
        trackers.remove(questionRunId)
        answeredCharacters.remove(questionRunId)
        selectedParticipants.remove(questionRunId)
        selectedParticipantSnapshots.remove(questionRunId)
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
            if (tracker.isExceeded()) {
                return OrchestrationResult(emptyList(), emptyList(), tracker.getUsed(), true, false)
            }

            val activeChars = dbGateway.getActiveCharacters()

            // 1. 获取消息，用以分析 question 文本（第一次运行且需要 Embedding 时使用）。
            val messages = dbGateway.getMessages(sessionId)
            val runMsgIndex = messages.indexOfFirst { it.id == questionRunId }
            if (runMsgIndex == -1) {
                return OrchestrationResult(emptyList(), emptyList(), tracker.getUsed(), false, false)
            }

            // 2. 确定并固化本问题参与者。首次锁定角色对象快照，后续轮次不受角色
            // 激活状态、编辑或删除影响，也绝不补入第 7 位角色。
            val cachedSnapshots = budgetManager.getSelectedParticipantSnapshots(questionRunId)
            val cachedSelectedIds = budgetManager.getSelectedParticipants(questionRunId)
            val selectedChars = when {
                cachedSnapshots != null -> cachedSnapshots
                cachedSelectedIds != null -> {
                    // 兼容进程内旧状态或测试直接写入 ID 的情况。只能从当前可用列表恢复，
                    // 不会使用新角色替换缺失角色。
                    val restored = cachedSelectedIds.mapNotNull { id ->
                        activeChars.firstOrNull { it.id == id }
                    }
                    if (restored.isNotEmpty()) {
                        budgetManager.setSelectedParticipantSnapshots(questionRunId, restored)
                    }
                    restored
                }
                else -> {
                    if (activeChars.isEmpty()) {
                        emptyList()
                    } else {
                        val sortedAllChars = if (isSemanticRoutingEnabled) {
                            val lastUserMsg = messages[runMsgIndex]
                            try {
                                val attemptPlan = createAttemptPlan(context, sessionId)
                                val requiredCount = minOf(activeChars.size, budget.maxCharactersPerQuestion)
                                val questionVector = answerGateway.getEmbedding(
                                    context = context,
                                    text = lastUserMsg.text,
                                    sessionId = sessionId,
                                    attemptPlan = attemptPlan,
                                    tracker = tracker,
                                    isRequired = false,
                                    reserveForRequired = requiredCount
                                )
                                activeChars.map { character ->
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
                            } catch (e: Exception) {
                                activeChars
                            }
                        } else {
                            activeChars
                        }

                        val selected = sortedAllChars.take(budget.maxCharactersPerQuestion)
                        budgetManager.setSelectedParticipantSnapshots(questionRunId, selected)
                        selected
                    }
                }
            }

            val selectedParticipantIds = selectedChars.map { it.id }
            if (selectedParticipantIds.isEmpty()) {
                // 没有任何已锁定角色可执行时将问题关闭，避免操作栏不断允许无效重试。
                tracker.close()
                return OrchestrationResult(emptyList(), emptyList(), tracker.getUsed(), false, false)
            }

            // 3. 只处理当前用户问题到下一条用户消息之前的消息。
            val messagesSinceRun = messages
                .subList(runMsgIndex + 1, messages.size)
                .takeWhile { it.senderId != "user" }

            val currentRound = if (messagesSinceRun.isEmpty()) {
                1
            } else {
                val completedMessages = messagesSinceRun.filterNot { it.isPending }
                val maxRound = completedMessages.maxOfOrNull { it.roundIndex } ?: 1
                val answeredInCurrentRound = completedMessages
                    .filter { it.roundIndex == maxRound }
                    .map { it.senderId }
                    .toSet()

                if (selectedParticipantIds.all { it in answeredInCurrentRound }) {
                    maxRound + 1
                } else {
                    maxRound
                }
            }

            val messagesInTargetRound = messagesSinceRun.filter {
                it.roundIndex == currentRound && !it.isPending
            }
            val answeredInTargetRound = messagesInTargetRound.map { it.senderId }.toSet()
            val sortedChars = selectedChars.filter { it.id !in answeredInTargetRound }

            if (sortedChars.isEmpty()) {
                tracker.close()
                return OrchestrationResult(emptyList(), emptyList(), tracker.getUsed(), false, false)
            }

            // 当前剩余额度不足以保证每个待回答角色至少一次主回答时，不启动部分轮次。
            if (tracker.getRemaining() < sortedChars.size) {
                tracker.close()
                return OrchestrationResult(emptyList(), emptyList(), tracker.getUsed(), true, false)
            }

            // 保护当前轮所有待回答角色。标题等异步 OPTIONAL 请求也必须遵守该保护额度。
            tracker.setRequiredReserve(sortedChars.size)

            // 4. 严格串行执行。
            for ((index, character) in sortedChars.withIndex()) {
                // 限额限制只约束不同参与角色数量，不阻止锁定角色进入后续轮次。
                if (!answered.contains(character.id) && answered.size >= budget.maxCharactersPerQuestion) {
                    isLimitExceeded = true
                    break
                }

                if (tracker.isExceeded()) {
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

                    // 包含当前角色本身。底层网络执行器会转换为“为其他角色保留”的数量。
                    val remainingParticipantsCount = sortedChars.size - index

                    val reply = answerGateway.callGeminiApi(
                        character = character,
                        prompt = transcript,
                        attemptPlan = attemptPlan,
                        tracker = tracker,
                        budget = budget,
                        sessionId = sessionId,
                        isRequired = true,
                        reserveForRequired = remainingParticipantsCount
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

            // 根据数据库中的真实完成状态，保护完成当前轮或开启下一整轮所需的最低额度。
            // 如果余额已不足，则关闭该问题，防止之后只产生半轮回答。
            updateRequiredReserveForNextStep(
                sessionId = sessionId,
                questionRunId = questionRunId,
                selectedParticipantIds = selectedParticipantIds,
                tracker = tracker
            )
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

    private suspend fun updateRequiredReserveForNextStep(
        sessionId: Long,
        questionRunId: Long,
        selectedParticipantIds: List<String>,
        tracker: RequestBudgetTracker
    ) {
        if (selectedParticipantIds.isEmpty()) {
            tracker.close()
            return
        }

        val latestMessages = dbGateway.getMessages(sessionId)
        val runMsgIndex = latestMessages.indexOfFirst { it.id == questionRunId }
        if (runMsgIndex == -1) {
            tracker.close()
            return
        }

        val messagesSinceRun = latestMessages
            .subList(runMsgIndex + 1, latestMessages.size)
            .takeWhile { it.senderId != "user" }
            .filterNot { it.isPending }

        val maxRound = messagesSinceRun.maxOfOrNull { it.roundIndex } ?: 1
        val answeredInLatestRound = messagesSinceRun
            .filter { it.roundIndex == maxRound }
            .map { it.senderId }
            .toSet()

        val missingInCurrentRound = selectedParticipantIds.count { it !in answeredInLatestRound }
        val requiredForNextStep = if (missingInCurrentRound > 0) {
            missingInCurrentRound
        } else {
            selectedParticipantIds.size
        }

        tracker.setRequiredReserve(requiredForNextStep)
        if (tracker.getRemaining() < requiredForNextStep) {
            tracker.close()
        }
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
