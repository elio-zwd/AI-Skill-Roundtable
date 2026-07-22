package com.elio.skillroundtable.roundtable

import android.content.Context
import com.elio.skillroundtable.data.Character
import com.elio.skillroundtable.data.Message
import com.elio.skillroundtable.network.keys.ApiKeyLease
import com.elio.skillroundtable.network.keys.ApiKeyScheduler
import com.elio.skillroundtable.telemetry.PrivacySafeLogger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object OrchestratorLogger {
    fun e(tag: String, message: String, error: Throwable? = null) {
        PrivacySafeLogger.e(tag, message, error)
    }
}

interface RoundtableDatabaseGateway {
    suspend fun getMessages(sessionId: Long): List<Message>
    suspend fun insertMessage(message: Message): Long
    suspend fun deleteMessageById(id: Long)
    suspend fun updatePendingMessageText(id: Long, text: String) {}
    suspend fun removePendingMessages(sessionId: Long)
    suspend fun getActiveCharacters(): List<Character>
}

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

    suspend fun callGeminiApiStreaming(
        character: Character,
        prompt: String,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker,
        budget: RoundtableBudget,
        sessionId: Long,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0,
        onAttemptStarted: suspend () -> Unit,
        onTextUpdate: suspend (String) -> Unit
    ): String {
        onAttemptStarted()
        val response = callGeminiApi(
            character = character,
            prompt = prompt,
            attemptPlan = attemptPlan,
            tracker = tracker,
            budget = budget,
            sessionId = sessionId,
            isRequired = isRequired,
            reserveForRequired = reserveForRequired
        )
        onTextUpdate(response)
        return response
    }

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

interface DelayProvider {
    suspend fun delay(ms: Long)
}

object DefaultDelayProvider : DelayProvider {
    override suspend fun delay(ms: Long) {
        kotlinx.coroutines.delay(ms)
    }
}

data class OrchestrationResult(
    val completedCharacters: List<String>,
    val failedCharacters: List<String>,
    val apiCallsUsed: Int,
    val isStoppedByBudget: Boolean,
    val isLimitExceeded: Boolean,
    val timedOutCharacters: List<String> = emptyList()
)

class RoundtableBudgetManager(val budget: RoundtableBudget = RoundtableBudget()) {
    private val trackers = ConcurrentHashMap<Long, RequestBudgetTracker>()
    private val answeredCharacters = ConcurrentHashMap<Long, MutableSet<String>>()
    private val selectedParticipants = ConcurrentHashMap<Long, List<String>>()
    private val selectedParticipantSnapshots = ConcurrentHashMap<Long, List<Character>>()

    fun getTracker(questionRunId: Long): RequestBudgetTracker {
        return trackers.computeIfAbsent(questionRunId) {
            RequestBudgetTracker(budget.maxApiCallsPerQuestion)
        }
    }

    fun getAnsweredCharacters(questionRunId: Long): MutableSet<String> {
        return answeredCharacters.computeIfAbsent(questionRunId) {
            ConcurrentHashMap.newKeySet()
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
    private val characterTimeoutMs: Long = 120_000L,
    private val createAttemptPlan: (Context, Long) -> List<ApiKeyLease> = { ctx, sessionId ->
        ApiKeyScheduler.createAttemptPlan(ctx, sessionId)
    }
) {
    private val roundtableMutex = Mutex()

    suspend fun runRoundtableSequence(
        sessionId: Long,
        questionRunId: Long,
        isSemanticRoutingEnabled: Boolean
    ): OrchestrationResult {
        if (!roundtableMutex.tryLock()) {
            throw IllegalStateException("Roundtable sequence is already running")
        }

        val completed = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val timedOut = mutableListOf<String>()
        var isStoppedByBudget = false
        var isLimitExceeded = false

        val tracker = budgetManager.getTracker(questionRunId)
        val answered = budgetManager.getAnsweredCharacters(questionRunId)
        val budget = budgetManager.budget

        try {
            if (tracker.isExceeded()) {
                return OrchestrationResult(emptyList(), emptyList(), tracker.getUsed(), true, false)
            }

            val activeCharacters = dbGateway.getActiveCharacters()
            val messages = dbGateway.getMessages(sessionId)
            val runMessageIndex = messages.indexOfFirst { it.id == questionRunId }
            if (runMessageIndex == -1) {
                return OrchestrationResult(emptyList(), emptyList(), tracker.getUsed(), false, false)
            }

            val cachedSnapshots = budgetManager.getSelectedParticipantSnapshots(questionRunId)
            val cachedSelectedIds = budgetManager.getSelectedParticipants(questionRunId)
            val selectedCharacters = when {
                cachedSnapshots != null -> cachedSnapshots
                cachedSelectedIds != null -> {
                    val restored = cachedSelectedIds.mapNotNull { id ->
                        activeCharacters.firstOrNull { it.id == id }
                    }
                    if (restored.isNotEmpty()) {
                        budgetManager.setSelectedParticipantSnapshots(questionRunId, restored)
                    }
                    restored
                }
                else -> selectAndFreezeCharacters(
                    sessionId = sessionId,
                    questionRunId = questionRunId,
                    activeCharacters = activeCharacters,
                    questionMessage = messages[runMessageIndex],
                    semanticRoutingEnabled = isSemanticRoutingEnabled,
                    tracker = tracker,
                    budget = budget
                )
            }

            val selectedParticipantIds = selectedCharacters.map { it.id }
            if (selectedParticipantIds.isEmpty()) {
                tracker.close()
                return OrchestrationResult(emptyList(), emptyList(), tracker.getUsed(), false, false)
            }

            val messagesSinceRun = messages
                .subList(runMessageIndex + 1, messages.size)
                .takeWhile { it.senderId != "user" }

            val currentRound = resolveCurrentRound(messagesSinceRun, selectedParticipantIds)
            val answeredInTargetRound = messagesSinceRun
                .filter { it.roundIndex == currentRound && !it.isPending }
                .map { it.senderId }
                .toSet()
            val pendingCharacters = selectedCharacters.filter { it.id !in answeredInTargetRound }

            if (pendingCharacters.isEmpty()) {
                tracker.close()
                return OrchestrationResult(emptyList(), emptyList(), tracker.getUsed(), false, false)
            }

            if (tracker.getRemaining() < pendingCharacters.size) {
                tracker.close()
                return OrchestrationResult(emptyList(), emptyList(), tracker.getUsed(), true, false)
            }

            tracker.setRequiredReserve(pendingCharacters.size)

            for ((index, character) in pendingCharacters.withIndex()) {
                if (!answered.contains(character.id) && answered.size >= budget.maxCharactersPerQuestion) {
                    isLimitExceeded = true
                    break
                }
                if (tracker.isExceeded()) {
                    isStoppedByBudget = true
                    break
                }

                val pendingMessageId = dbGateway.insertMessage(
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
                        throw IllegalStateException("No available API key plan")
                    }

                    val reply = withTimeoutOrNull(characterTimeoutMs) {
                        answerGateway.callGeminiApiStreaming(
                            character = character,
                            prompt = transcript,
                            attemptPlan = attemptPlan,
                            tracker = tracker,
                            budget = budget,
                            sessionId = sessionId,
                            isRequired = true,
                            reserveForRequired = pendingCharacters.size - index,
                            onAttemptStarted = {
                                dbGateway.updatePendingMessageText(
                                    pendingMessageId,
                                    "正在思考中..."
                                )
                            },
                            onTextUpdate = { partialText ->
                                dbGateway.updatePendingMessageText(pendingMessageId, partialText)
                            }
                        )
                    }

                    dbGateway.deleteMessageById(pendingMessageId)
                    if (reply == null) {
                        PrivacySafeLogger.w(
                            "RoundtableOrchestrator",
                            "Character answer timed out"
                        )
                        failed.add(character.id)
                        timedOut.add(character.id)
                    } else {
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
                    }
                } catch (error: CancellationException) {
                    withContext(NonCancellable) {
                        dbGateway.deleteMessageById(pendingMessageId)
                    }
                    throw error
                } catch (error: Exception) {
                    OrchestratorLogger.e(
                        "RoundtableOrchestrator",
                        "Character answer failed",
                        error
                    )
                    dbGateway.deleteMessageById(pendingMessageId)
                    failed.add(character.id)
                }

                if (minIntervalMs > 0L) delayProvider.delay(minIntervalMs)
            }

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
            isLimitExceeded = isLimitExceeded,
            timedOutCharacters = timedOut
        )
    }

    private suspend fun selectAndFreezeCharacters(
        sessionId: Long,
        questionRunId: Long,
        activeCharacters: List<Character>,
        questionMessage: Message,
        semanticRoutingEnabled: Boolean,
        tracker: RequestBudgetTracker,
        budget: RoundtableBudget
    ): List<Character> {
        if (activeCharacters.isEmpty()) return emptyList()

        val sortedCharacters = if (semanticRoutingEnabled) {
            try {
                val attemptPlan = createAttemptPlan(context, sessionId)
                val requiredCount = minOf(activeCharacters.size, budget.maxCharactersPerQuestion)
                val questionVector = answerGateway.getEmbedding(
                    context = context,
                    text = questionMessage.text,
                    sessionId = sessionId,
                    attemptPlan = attemptPlan,
                    tracker = tracker,
                    isRequired = false,
                    reserveForRequired = requiredCount
                )
                activeCharacters.map { character ->
                    val characterVector = runCatching {
                        if (character.skillDescriptionVector.isBlank()) {
                            emptyList()
                        } else {
                            character.skillDescriptionVector.split(",").map { it.toFloat() }
                        }
                    }.getOrDefault(emptyList())
                    val similarity = if (characterVector.isNotEmpty() && questionVector.isNotEmpty()) {
                        calculateCosineSimilarity(questionVector, characterVector)
                    } else {
                        0f
                    }
                    character to similarity
                }.sortedByDescending { it.second }.map { it.first }
            } catch (_: Exception) {
                activeCharacters
            }
        } else {
            activeCharacters
        }

        val selected = sortedCharacters.take(budget.maxCharactersPerQuestion)
        budgetManager.setSelectedParticipantSnapshots(questionRunId, selected)
        return selected
    }

    private fun resolveCurrentRound(
        messagesSinceRun: List<Message>,
        selectedParticipantIds: List<String>
    ): Int {
        if (messagesSinceRun.isEmpty()) return 1
        val completedMessages = messagesSinceRun.filterNot { it.isPending }
        val maxRound = completedMessages.maxOfOrNull { it.roundIndex } ?: 1
        val answeredInCurrentRound = completedMessages
            .filter { it.roundIndex == maxRound }
            .map { it.senderId }
            .toSet()
        return if (selectedParticipantIds.all { it in answeredInCurrentRound }) {
            maxRound + 1
        } else {
            maxRound
        }
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
        val runMessageIndex = latestMessages.indexOfFirst { it.id == questionRunId }
        if (runMessageIndex == -1) {
            tracker.close()
            return
        }

        val messagesSinceRun = latestMessages
            .subList(runMessageIndex + 1, latestMessages.size)
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
        if (tracker.getRemaining() < requiredForNextStep) tracker.close()
    }

    private fun calculateCosineSimilarity(vectorA: List<Float>, vectorB: List<Float>): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 0f
        var dotProduct = 0f
        var normA = 0f
        var normB = 0f
        for (index in vectorA.indices) {
            dotProduct += vectorA[index] * vectorB[index]
            normA += vectorA[index] * vectorA[index]
            normB += vectorB[index] * vectorB[index]
        }
        val denominator = Math.sqrt(normA.toDouble()) * Math.sqrt(normB.toDouble())
        return if (denominator == 0.0) 0f else (dotProduct / denominator).toFloat()
    }
}