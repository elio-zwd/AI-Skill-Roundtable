package com.elio.jianyu.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.elio.jianyu.data.Character
import com.elio.jianyu.data.ChatSession
import com.elio.jianyu.data.ConversationSessionPreferences
import com.elio.jianyu.data.Message
import com.elio.jianyu.data.RoundtableDatabase
import com.elio.jianyu.network.Content
import com.elio.jianyu.network.GenerateContentRequest
import com.elio.jianyu.network.Part
import com.elio.jianyu.network.GeminiRestTransport
import com.elio.jianyu.network.GeminiInteractionsTransport
import com.elio.jianyu.network.AiManager
import com.elio.jianyu.network.AiProvider
import com.elio.jianyu.network.AiUseCase
import com.elio.jianyu.network.DeepSeekTransport
import com.elio.jianyu.network.defaultModel
import com.elio.jianyu.network.Tool
import com.elio.jianyu.network.CreateInteractionRequest
import com.elio.jianyu.network.InteractionGenerationConfig
import com.elio.jianyu.network.outputText
import com.elio.jianyu.telemetry.CloudInteractionSettings
import com.elio.jianyu.telemetry.PrivacySafeLogger
import com.elio.jianyu.network.keys.ApiKeyLease
import com.elio.jianyu.roundtable.RoundtableBudget
import com.elio.jianyu.roundtable.RequestBudgetTracker
import com.elio.jianyu.roundtable.RoundtableOrchestrator
import com.elio.jianyu.roundtable.TranscriptBuilder
import com.elio.jianyu.execution.SearchMode
import com.elio.jianyu.roundtable.RoundtableDatabaseGateway
import com.elio.jianyu.roundtable.CharacterAnswerGateway
import com.elio.jianyu.roundtable.RoundtableBudgetManager
import com.elio.jianyu.roundtable.DefaultDelayProvider
import com.elio.jianyu.roundtable.OrchestrationResult
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import android.widget.Toast
import com.elio.jianyu.audio.AudioPlaybackManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkManager
import java.io.File

private const val ROUNDTABLE_SEQUENCE_TIMEOUT_MS = 8 * 60 * 1000L
private const val DEFAULT_SESSION_ROLE_COUNT = 2

data class RetryableRoundtableState(
    val sessionId: Long,
    val questionRunId: Long,
    val characterIds: List<String>
)

internal fun buildRetryableCharacterIds(
    failedCharacters: List<String>,
    timedOutCharacters: List<String>
): List<String> {
    val combined = ArrayList<String>(failedCharacters.size + timedOutCharacters.size)
    for (id in failedCharacters) {
        if (id !in combined) combined.add(id)
    }
    for (id in timedOutCharacters) {
        if (id !in combined) combined.add(id)
    }
    return combined
}

internal fun remainingRetryableCharacterIds(
    initialTargetIds: List<String>,
    completedIds: Set<String>
): List<String> {
    return initialTargetIds.filter { id -> id !in completedIds }
}

/**
 * 对话运行 ViewModel，负责管理会话、消息、Skill 角色状态以及触发 API 逻辑。
 */
class RoundtableViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("roundtable_settings", android.content.Context.MODE_PRIVATE)
    private val conversationPreferences = ConversationSessionPreferences(application)

    private var skillsSummaries: org.json.JSONObject? = null
    private var activeRoundtableJob: Job? = null

    private fun loadSkillsSummariesOnce(context: android.content.Context): org.json.JSONObject {
        val current = skillsSummaries
        if (current != null) return current
        val json = try {
            val jsonStr = context.assets.open("skills_summaries.json").use { it.reader().readText() }
            org.json.JSONObject(jsonStr)
        } catch (error: java.lang.Exception) {
            PrivacySafeLogger.e("RoundtableViewModel", "Failed to load skill summaries", error)
            org.json.JSONObject()
        }
        skillsSummaries = json
        return json
    }

    private val database = RoundtableDatabase.getDatabase(application, viewModelScope)
    private val charRepo = com.elio.jianyu.data.CharacterRepository(database.characterDao())
    private val chatRepo = com.elio.jianyu.data.ChatRepository(database.chatDao())
    private val groupRepo = com.elio.jianyu.data.CharacterGroupRepository(database.characterGroupDao())
    private val startupPendingCleanupJob: Job = viewModelScope.launch(Dispatchers.IO) {
        try {
            chatRepo.removeAllPendingMessages()
        } catch (error: Exception) {
            PrivacySafeLogger.e("RoundtableViewModel", "Startup pending-message cleanup failed", error)
        }
    }

    val allCharacters: StateFlow<List<Character>> = charRepo.allCharacters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGroups: StateFlow<List<com.elio.jianyu.data.CharacterGroup>> = groupRepo.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentDetailSkillContent = MutableStateFlow<String?>(null)
    val currentDetailSkillContent: StateFlow<String?> = _currentDetailSkillContent.asStateFlow()

    val allSessions: StateFlow<List<ChatSession>> = chatRepo.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

    private val _currentParticipantIds = MutableStateFlow<List<String>>(emptyList())
    val currentParticipantIds: StateFlow<List<String>> = _currentParticipantIds.asStateFlow()

    private val _archivedSessionIds = MutableStateFlow(conversationPreferences.getArchivedSessionIds())
    val archivedSessionIds: StateFlow<Set<Long>> = _archivedSessionIds.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentMessages: StateFlow<List<Message>> = _currentSessionId
        .flatMapLatest { id ->
            if (id != null) {
                chatRepo.getMessagesFlow(id)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRoundtableRunning = MutableStateFlow(false)
    val isRoundtableRunning: StateFlow<Boolean> = _isRoundtableRunning.asStateFlow()

    private val _retryableRoundtableState = MutableStateFlow<RetryableRoundtableState?>(null)
    val retryableRoundtableState: StateFlow<RetryableRoundtableState?> = _retryableRoundtableState.asStateFlow()

    fun dismissRetryableState() {
        _retryableRoundtableState.value = null
    }

    private val _typingCharacterIds = MutableStateFlow<Set<String>>(emptySet())
    val typingCharacterIds: StateFlow<Set<String>> = _typingCharacterIds.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isAutoNextEnabled = MutableStateFlow(true)
    val isAutoNextEnabled: StateFlow<Boolean> = _isAutoNextEnabled.asStateFlow()

    fun setAutoNextEnabled(enabled: Boolean) {
        _isAutoNextEnabled.value = enabled
        prefs.edit().putBoolean("is_auto_next_enabled", enabled).apply()
    }

    private val _isSemanticRoutingEnabled = MutableStateFlow(false)
    val isSemanticRoutingEnabled: StateFlow<Boolean> = _isSemanticRoutingEnabled.asStateFlow()

    fun setSemanticRoutingEnabled(enabled: Boolean) {
        _isSemanticRoutingEnabled.value = enabled
        prefs.edit().putBoolean("is_semantic_routing_enabled", enabled).apply()
    }

    private val _searchMode = MutableStateFlow(SearchMode.AUTO)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    fun setSearchMode(mode: SearchMode) {
        _searchMode.value = mode
        prefs.edit().putString("search_mode", mode.name).apply()
    }

    private val _thinkingIntensity = MutableStateFlow("标准")
    val thinkingIntensity: StateFlow<String> = _thinkingIntensity.asStateFlow()

    fun setThinkingIntensity(intensity: String) {
        val normalized = intensity.takeIf { it in setOf("极简", "标准", "深度") } ?: "标准"
        _thinkingIntensity.value = normalized
        prefs.edit().putString("thinking_intensity", normalized).apply()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val aiKeySummaries = AiManager.configuration(application).configuration
        .flatMapLatest { configuration ->
            AiManager.keys(application, configuration.modelFor(AiUseCase.ROUNDTABLE_ANSWER).provider).summaries
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val budgetManager = RoundtableBudgetManager()

    private val dbGateway = object : RoundtableDatabaseGateway {
        override suspend fun getMessages(sessionId: Long): List<Message> = chatRepo.getMessages(sessionId)
        override suspend fun insertMessage(message: Message): Long = chatRepo.insertMessage(message)
        override suspend fun deleteMessageById(id: Long) = chatRepo.deleteMessageById(id)
        override suspend fun updatePendingMessageText(id: Long, text: String) {
            chatRepo.updatePendingMessageText(id, text)
        }
        override suspend fun removePendingMessages(sessionId: Long) = chatRepo.removePendingMessages(sessionId)
        override suspend fun getActiveCharacters(): List<Character> = charRepo.getActiveCharacters()
    }

    private val answerGateway = object : CharacterAnswerGateway {
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
            _typingCharacterIds.update { it + character.id }
            return try {
                this@RoundtableViewModel.callGeminiApi(
                    character,
                    prompt,
                    attemptPlan,
                    tracker,
                    budget,
                    sessionId,
                    isRequired,
                    reserveForRequired
                )
            } finally {
                _typingCharacterIds.update { it - character.id }
            }
        }

        override suspend fun callGeminiApiStreaming(
            character: Character,
            prompt: String,
            attemptPlan: List<ApiKeyLease>,
            tracker: RequestBudgetTracker,
            budget: RoundtableBudget,
            sessionId: Long,
            isRequired: Boolean,
            reserveForRequired: Int,
            onAttemptStarted: suspend () -> Unit,
            onTextUpdate: suspend (String) -> Unit
        ): String {
            _typingCharacterIds.update { it + character.id }
            return try {
                this@RoundtableViewModel.callGeminiApi(
                    character = character,
                    prompt = prompt,
                    attemptPlan = attemptPlan,
                    tracker = tracker,
                    budget = budget,
                    sessionId = sessionId,
                    isRequired = isRequired,
                    reserveForRequired = reserveForRequired,
                    onAttemptStarted = {
                        _typingCharacterIds.update { it + character.id }
                        onAttemptStarted()
                    },
                    onTextUpdate = { partialText ->
                        _typingCharacterIds.update { it - character.id }
                        onTextUpdate(partialText)
                    }
                )
            } finally {
                _typingCharacterIds.update { it - character.id }
            }
        }

        override suspend fun getEmbedding(
            context: android.content.Context,
            text: String,
            sessionId: Long,
            attemptPlan: List<ApiKeyLease>,
            tracker: RequestBudgetTracker,
            isRequired: Boolean,
            reserveForRequired: Int
        ): List<Float> {
            if (attemptPlan.firstOrNull()?.provider == AiProvider.DEEPSEEK) return emptyList()
            return GeminiRestTransport.embedContent(
                context = context,
                text = text,
                sessionId = sessionId,
                attemptPlan = attemptPlan,
                tracker = tracker,
                operationName = "EmbedQuestion",
                isRequired = isRequired,
                reserveForRequired = reserveForRequired
            )
        }
    }

    private val orchestrator = RoundtableOrchestrator(
        context = application.applicationContext,
        dbGateway = dbGateway,
        answerGateway = answerGateway,
        budgetManager = budgetManager,
        delayProvider = DefaultDelayProvider,
        minIntervalMs = 1000L
    )

    private val _roundActionState = MutableStateFlow(RoundActionState.CONTINUE_ROUND)
    val roundActionState: StateFlow<RoundActionState> = _roundActionState.asStateFlow()

    init {
        val context = getApplication<Application>().applicationContext
        AiManager.initialize(context)

        _isAutoNextEnabled.value = prefs.getBoolean("is_auto_next_enabled", true)
        _isSemanticRoutingEnabled.value = prefs.getBoolean("is_semantic_routing_enabled", false)
        val savedSearchModeStr = prefs.getString("search_mode", SearchMode.AUTO.name)
        _searchMode.value = try {
            SearchMode.valueOf(savedSearchModeStr ?: SearchMode.AUTO.name)
        } catch (_: Exception) {
            SearchMode.AUTO
        }
        _thinkingIntensity.value = prefs.getString("thinking_intensity", "标准")
            ?.takeIf { it in setOf("极简", "标准", "深度") }
            ?: "标准"

        ensureCoreCharactersExist()
    }

    private fun ensureCoreCharactersExist() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val skillConfigs = com.elio.jianyu.skill.SkillLoader.loadSkillsConfig(context)
            if (skillConfigs.isEmpty()) {
                PrivacySafeLogger.e("RoundtableViewModel", "No valid skill configuration found")
                return@launch
            }

            for (config in skillConfigs) {
                val existing = charRepo.getCharacterById(config.id)
                val prompt = com.elio.jianyu.skill.SkillLoader.loadSkill(context, config.skillAssetPath)
                val vectorStr = config.descriptionVector.joinToString(",")
                val character = Character(
                    id = config.id,
                    name = config.name,
                    avatar = config.avatar,
                    tagline = config.tagline,
                    systemPrompt = prompt,
                    skillAssetPath = config.skillAssetPath,
                    order = config.order,
                    isActive = config.isActive,
                    skillDescriptionVector = vectorStr
                )

                if (existing == null) {
                    charRepo.insert(character)
                } else {
                    charRepo.insert(
                        existing.copy(
                            name = config.name,
                            avatar = config.avatar,
                            tagline = config.tagline,
                            skillAssetPath = config.skillAssetPath,
                            systemPrompt = prompt,
                            order = config.order,
                            skillDescriptionVector = vectorStr
                        )
                    )
                }
            }

            val extraIds = listOf(
                "industry_analyst",
                "ai_visionary",
                "career_coach",
                "silver_spoon",
                "academic_dean",
                "freelance_nomad"
            )
            for (extraId in extraIds) charRepo.deleteById(extraId)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun ensureConversationReady() {
        if (_currentSessionId.value != null) return
        viewModelScope.launch {
            val firstSession = chatRepo.allSessions.first()
                .firstOrNull { it.id !in _archivedSessionIds.value }
            if (firstSession != null) {
                selectSession(firstSession.id)
            } else {
                createNewSession("新建对话")
            }
        }
    }

    fun selectSession(sessionId: Long) {
        _currentSessionId.value = sessionId
        _currentParticipantIds.value = emptyList()
        viewModelScope.launch {
            _currentSession.value = chatRepo.getSessionById(sessionId)
            _currentParticipantIds.value = loadParticipantIds(sessionId)
            updateRoundActionState(sessionId)
        }
    }

    fun createNewSession(title: String) {
        viewModelScope.launch {
            val id = chatRepo.createSession(title)
            _currentSessionId.value = id
            _currentSession.value = chatRepo.getSessionById(id)
            _currentParticipantIds.value = loadParticipantIds(id)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            val messages = chatRepo.getMessages(sessionId)
            val userMsgIds = messages.filter { it.senderId == "user" }.map { it.id }
            userMsgIds.forEach { budgetManager.clearQuestion(it) }

            chatRepo.deleteSession(sessionId)
            _archivedSessionIds.value = conversationPreferences.clearSession(sessionId)
            if (_retryableRoundtableState.value?.sessionId == sessionId) {
                _retryableRoundtableState.value = null
            }
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = null
                _currentSession.value = null
                _currentParticipantIds.value = emptyList()
                val nextSession = chatRepo.allSessions.first()
                    .firstOrNull { it.id !in _archivedSessionIds.value }
                if (nextSession != null) {
                    selectSession(nextSession.id)
                } else {
                    createNewSession("新建对话")
                }
            }
        }
    }

    fun addSkillRoleToCurrentSession(skillId: String) {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            val available = charRepo.getCharacterById(skillId) ?: return@launch
            val updated = (_currentParticipantIds.value + available.id).distinct().take(15)
            conversationPreferences.setParticipantIds(sessionId, updated)
            _currentParticipantIds.value = updated
        }
    }

    fun removeSkillRoleFromCurrentSession(skillId: String) {
        val sessionId = _currentSessionId.value ?: return
        val current = _currentParticipantIds.value
        if (skillId !in current) return
        if (current.size == 1) {
            _errorMessage.value = "会话至少需要保留一个 Skill 角色。"
            return
        }
        val updated = current.filterNot { it == skillId }
        conversationPreferences.setParticipantIds(sessionId, updated)
        _currentParticipantIds.value = updated
    }

    fun archiveSession(sessionId: Long) {
        _archivedSessionIds.value = conversationPreferences.setArchived(sessionId, archived = true)
        if (_currentSessionId.value == sessionId) {
            _currentSessionId.value = null
            _currentSession.value = null
            _currentParticipantIds.value = emptyList()
            ensureConversationReady()
        }
    }

    fun restoreSession(sessionId: Long) {
        _archivedSessionIds.value = conversationPreferences.setArchived(sessionId, archived = false)
        selectSession(sessionId)
    }

    private suspend fun loadParticipantIds(sessionId: Long): List<String> {
        val immediatelyAvailable = charRepo.getActiveCharacters()
        val availableIds = if (immediatelyAvailable.isNotEmpty()) {
            immediatelyAvailable.map(Character::id)
        } else {
            withTimeoutOrNull(5_000L) {
                charRepo.allCharacters
                    .first { characters -> characters.any(Character::isActive) }
                    .filter(Character::isActive)
                    .map(Character::id)
            }.orEmpty()
        }
        val defaults = availableIds.take(DEFAULT_SESSION_ROLE_COUNT)
        val stored = conversationPreferences.getParticipantIds(sessionId, defaults)
            .filter { it in availableIds }
            .take(15)
        val resolved = stored.ifEmpty { defaults }
        conversationPreferences.setParticipantIds(sessionId, resolved)
        return resolved
    }

    fun addOrUpdateCharacter(character: Character) {
        viewModelScope.launch { charRepo.insert(character) }
    }

    fun deleteCharacter(id: String) {
        viewModelScope.launch { charRepo.deleteById(id) }
    }

    fun askQuestion(text: String, targetCharacterId: String? = null): Boolean {
        val sessionId = _currentSessionId.value ?: return false
        if (text.isBlank()) return false
        if (activeRoundtableJob?.isActive == true) {
            _errorMessage.value = "对话回复正在生成，请稍候或先停止。"
            return false
        }
        if (!AiManager.keysForUseCase(getApplication(), AiUseCase.ROUNDTABLE_ANSWER).hasAvailableKeys()) {
            _errorMessage.value = "当前没有可用的 API 密钥，请稍后再试或在“我的配置”中填写密钥。"
            return false
        }
        val targetCharacterIds = targetCharacterId?.let(::listOf)
            ?: _currentParticipantIds.value.toList()
        if (targetCharacterIds.isEmpty()) {
            _errorMessage.value = "当前会话没有可用的 Skill 角色，请先增加一个角色。"
            return false
        }

        launchRoundtableJob {
            _retryableRoundtableState.value = null

            val userMsg = Message(
                chatId = sessionId,
                senderId = "user",
                senderName = "你",
                avatar = "👤",
                text = text
            )
            val questionRunId = chatRepo.insertMessage(userMsg)
            runRoundtableSequence(
                sessionId = sessionId,
                questionRunId = questionRunId,
                targetCharacterIds = targetCharacterIds,
                responseMode = TranscriptBuilder.ResponseMode.INDEPENDENT,
            )

            val allMsgs = chatRepo.getMessages(sessionId)
            val userMsgs = allMsgs.filter { it.senderId == "user" }
            if (userMsgs.size == 1) generateSessionTitle(sessionId, questionRunId, text)
            updateRoundActionState(sessionId)
        }
        return true
    }

    fun generateSessionTitle(sessionId: Long, questionRunId: Long, firstQuestion: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val tracker = budgetManager.getTracker(questionRunId)

            val prompt = """
                你是一个对话标题提炼助手。
                请针对用户提问，提炼出一个简短、吸引人且能概括核心内容的对话标题。
                要求：
                1. 长度不超过 15 个字。
                2. 不要包含任何标点符号、引号或前缀。
                3. 直接输出标题内容，不要有多余解释。

                用户提问：$firstQuestion
            """.trimIndent()

            try {
                val model = AiManager.configuration(context).configuration.value
                    .modelFor(AiUseCase.SESSION_TITLE)
                val plan = AiManager.keys(context, model.provider).createAttemptPlan(sessionId)
                val reply = when (model.provider) {
                    AiProvider.GEMINI -> {
                        val response = GeminiRestTransport.generateContent(
                            context = context,
                            model = model.modelId,
                            request = GenerateContentRequest(
                                contents = listOf(Content(parts = listOf(Part(text = prompt))))
                            ),
                            sessionId = sessionId,
                            attemptPlan = plan,
                            tracker = tracker,
                            operationName = "GenerateTitle",
                            isRequired = false,
                            reserveForRequired = 0,
                        )
                        response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                    }
                    AiProvider.DEEPSEEK -> DeepSeekTransport.createChatCompletion(
                        context = context,
                        sessionId = sessionId,
                        attemptPlan = plan,
                        model = model,
                        systemInstruction = "你是一个对话标题提炼助手。只输出不超过 15 个字的标题，不要解释。",
                        userContent = firstQuestion,
                        maxOutputTokens = 40,
                        operationName = "GenerateTitle",
                        tracker = tracker,
                    ).choices.firstOrNull()?.message?.content?.trim()
                }
                if (!reply.isNullOrBlank()) {
                    val cleanTitle = reply.replace("\"", "").replace("'", "").trim()
                    chatRepo.updateSessionTitle(sessionId, cleanTitle)
                    chatRepo.getSessionById(sessionId)?.let { _currentSession.value = it }
                }
            } catch (error: Exception) {
                PrivacySafeLogger.e("RoundtableViewModel", "Title generation failed", error)
            }
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepo.updateSessionTitle(sessionId, newTitle)
            chatRepo.getSessionById(sessionId)?.let { _currentSession.value = it }
        }
    }

    suspend fun exportConversation(sessionId: Long): String = withContext(Dispatchers.IO) {
        val session = chatRepo.getSessionById(sessionId) ?: return@withContext ""
        val messages = chatRepo.getMessages(sessionId).filter { !it.isPending }
        if (messages.isEmpty()) return@withContext ""

        val sb = java.lang.StringBuilder()
        sb.append("# ${session.title}\n")
        val dateStr = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", session.createdAt).toString()
        sb.append("**时间**：$dateStr\n\n")

        for (msg in messages) {
            if (msg.senderId == "user") {
                sb.append("## 👤 用户\n")
                sb.append("> ${msg.text}\n\n")
            } else {
                sb.append("### ${msg.avatar} ${msg.senderName}\n")
                sb.append("${msg.text}\n\n")
            }
        }
        sb.toString()
    }

    val currentPlayingMessageId = AudioPlaybackManager.currentPlayingMessageId
    val isAudioPlaying = AudioPlaybackManager.isPlaying
    val allAudioMessages: StateFlow<List<Message>> = chatRepo.audioMessages.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    fun playOrSynthesizeTts(message: Message, voiceName: String) {
        val context = getApplication<Application>().applicationContext
        if (!message.audioFilePath.isNullOrBlank()) {
            AudioPlaybackManager.playAudio(context, message.id, message.audioFilePath)
            return
        }

        viewModelScope.launch {
            if (!AiManager.keys(context, AiProvider.GEMINI).hasAvailableKeys()) {
                _errorMessage.value = "无法播放语音：无可用 API Key"
                return@launch
            }

            val tempWavFile = File(context.cacheDir, "tts_${message.id}.wav")
            try {
                PrivacySafeLogger.d("RoundtableViewModel", "Starting TTS synthesis")
                val path = com.elio.jianyu.network.GeminiLiveAudioTransport.generateTtsWav(
                    context = context,
                    sessionId = message.chatId,
                    text = message.text,
                    voiceName = voiceName,
                    outputFile = tempWavFile
                )
                chatRepo.updateMessageAudio(message.id, path, "wav", tempWavFile.length())
                AudioPlaybackManager.playAudio(context, message.id, path)
                enqueueTranscodeWork(message.id, path)
            } catch (error: Exception) {
                PrivacySafeLogger.e("RoundtableViewModel", "TTS synthesis failed", error)
                Toast.makeText(context, "语音合成失败，请稍后重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enqueueTranscodeWork(messageId: Long, wavPath: String) {
        val context = getApplication<Application>().applicationContext
        val inputData = workDataOf("message_id" to messageId, "wav_path" to wavPath)
        val request = OneTimeWorkRequestBuilder<com.elio.jianyu.audio.AudioTranscodeWorker>()
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun deleteAudio(message: Message) {
        viewModelScope.launch(Dispatchers.IO) {
            if (AudioPlaybackManager.currentPlayingMessageId.value == message.id) {
                AudioPlaybackManager.stopAudio()
            }
            if (!message.audioFilePath.isNullOrBlank()) {
                val file = File(message.audioFilePath)
                if (file.exists()) file.delete()
            }
            chatRepo.updateMessageAudio(message.id, null, null, 0L)
        }
    }

    fun triggerTranscode(messageId: Long, wavPath: String) {
        enqueueTranscodeWork(messageId, wavPath)
    }

    fun triggerNextCharacterManual() {
        val sessionId = _currentSessionId.value ?: return
        launchRoundtableJob {
            val messages = chatRepo.getMessages(sessionId)
            val lastUserMsg = messages.lastOrNull { it.senderId == "user" }
            if (lastUserMsg != null) {
                runRoundtableSequence(
                    sessionId = sessionId,
                    questionRunId = lastUserMsg.id,
                    targetCharacterIds = _currentParticipantIds.value,
                    responseMode = TranscriptBuilder.ResponseMode.INDEPENDENT,
                )
            }
            updateRoundActionState(sessionId)
        }
    }

    fun letSkillRoleAnswerCurrent(skillId: String) {
        val sessionId = _currentSessionId.value ?: return
        if (skillId !in _currentParticipantIds.value) return
        launchRoundtableJob {
            val lastUserMessage = chatRepo.getMessages(sessionId)
                .lastOrNull { it.senderId == "user" }
            if (lastUserMessage == null) {
                _errorMessage.value = "请先发送一条消息，再让该 Skill 角色回答。"
                return@launchRoundtableJob
            }
            runRoundtableSequence(
                sessionId = sessionId,
                questionRunId = lastUserMessage.id,
                targetCharacterIds = listOf(skillId),
                responseMode = TranscriptBuilder.ResponseMode.INDEPENDENT,
            )
        }
    }

    fun triggerCrossDiscussion() {
        val sessionId = _currentSessionId.value ?: return
        val participantIds = _currentParticipantIds.value
        if (participantIds.size < 2) {
            _errorMessage.value = "交叉讨论至少需要两个 Skill 角色。"
            return
        }
        launchRoundtableJob {
            val messages = chatRepo.getMessages(sessionId)
            val lastUserMessage = messages.lastOrNull { it.senderId == "user" }
            if (lastUserMessage == null) {
                _errorMessage.value = "请先开始对话，再发起交叉讨论。"
                return@launchRoundtableJob
            }
            val hasRoleViewpoint = messages
                .dropWhile { it.id != lastUserMessage.id }
                .drop(1)
                .any { it.senderId != "user" && !it.isPending }
            if (!hasRoleViewpoint) {
                _errorMessage.value = "当前还没有可供交叉讨论的角色观点。"
                return@launchRoundtableJob
            }
            runRoundtableSequence(
                sessionId = sessionId,
                questionRunId = lastUserMessage.id,
                targetCharacterIds = participantIds,
                responseMode = TranscriptBuilder.ResponseMode.CROSS_DISCUSSION,
            )
        }
    }

    fun cancelRoundtable() {
        val job = activeRoundtableJob?.takeIf { it.isActive } ?: return
        _errorMessage.value = "正在停止本轮生成…"
        job.cancel(CancellationException("User requested roundtable cancellation"))
    }

    private fun launchRoundtableJob(block: suspend () -> Unit) {
        if (activeRoundtableJob?.isActive == true) {
            _errorMessage.value = "对话回复正在生成，请稍候或先停止。"
            return
        }

        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            startupPendingCleanupJob.join()
            block()
        }
        activeRoundtableJob = job
        job.invokeOnCompletion {
            if (activeRoundtableJob === job) activeRoundtableJob = null
        }
        job.start()
    }

    fun retryFailedCharacters() {
        val sessionId = _currentSessionId.value ?: return
        val state = _retryableRoundtableState.value ?: return
        if (state.sessionId != sessionId || state.characterIds.isEmpty()) return

        launchRoundtableJob {
            runRetryRoundtableSequence(sessionId, state.questionRunId, state.characterIds)
        }
    }

    private suspend fun runRetryRoundtableSequence(sessionId: Long, questionRunId: Long, targetCharacterIds: List<String>) {
        val context = getApplication<Application>().applicationContext
        if (!AiManager.keysForUseCase(context, AiUseCase.ROUNDTABLE_ANSWER).hasAvailableKeys()) {
            _errorMessage.value = "当前没有可用的 API 密钥，请稍后再试或在“我的配置”中填写密钥。"
            return
        }

        val messages = chatRepo.getMessages(sessionId)
        val questionMsgIndex = messages.indexOfFirst { it.id == questionRunId }
        if (questionMsgIndex == -1) {
            _retryableRoundtableState.value = null
            return
        }

        val activeCharacters = charRepo.getActiveCharacters()
        val activeIds = activeCharacters.map { it.id }.toSet()
        val executableTargetIds = targetCharacterIds.filter { it in activeIds }

        if (executableTargetIds.isEmpty()) {
            _errorMessage.value = "失败角色当前不可用，请重新启用后重试。"
            return
        }

        _isRoundtableRunning.value = true
        _errorMessage.value = null

        try {
            val result = withTimeout(ROUNDTABLE_SEQUENCE_TIMEOUT_MS) {
                orchestrator.runRoundtableSequence(
                    sessionId = sessionId,
                    questionRunId = questionRunId,
                    isSemanticRoutingEnabled = _isSemanticRoutingEnabled.value,
                    targetCharacterIds = targetCharacterIds
                )
            }

            val completedSet = result.completedCharacters.toSet()
            val remainingIds = remainingRetryableCharacterIds(targetCharacterIds, completedSet)

            if (remainingIds.isEmpty()) {
                _retryableRoundtableState.value = null
                _errorMessage.value = "失败角色已全部完成回复。"
            } else {
                _retryableRoundtableState.value = RetryableRoundtableState(sessionId, questionRunId, remainingIds)
                if (result.completedCharacters.isNotEmpty()) {
                    _errorMessage.value = "部分角色已完成，仍有 ${remainingIds.size} 位 Skill 角色未完成，可再次重试。"
                } else {
                    _errorMessage.value = "仍有 ${remainingIds.size} 位 Skill 角色未完成，可再次重试。"
                }
            }
        } catch (error: TimeoutCancellationException) {
            PrivacySafeLogger.w("RoundtableViewModel", "Retry sequence timed out")
            withContext(NonCancellable) {
                chatRepo.removePendingMessages(sessionId)
            }
            _errorMessage.value = "重试等待时间过长，已自动停止。已完成的回复会继续保留。"
        } catch (error: CancellationException) {
            PrivacySafeLogger.d("RoundtableViewModel", "Retry sequence cancelled by user")
            withContext(NonCancellable) {
                chatRepo.removePendingMessages(sessionId)
            }
            val latestMsgs = runCatching { chatRepo.getMessages(sessionId) }.getOrDefault(emptyList())
            val qIndex = latestMsgs.indexOfFirst { it.id == questionRunId }
            val answeredInRun = if (qIndex != -1) {
                latestMsgs.subList(qIndex + 1, latestMsgs.size)
                    .takeWhile { it.senderId != "user" }
                    .filterNot { it.isPending }
                    .map { it.senderId }
                    .toSet()
            } else emptySet()

            val remainingIds = remainingRetryableCharacterIds(targetCharacterIds, answeredInRun)
            if (remainingIds.isNotEmpty()) {
                _retryableRoundtableState.value = RetryableRoundtableState(sessionId, questionRunId, remainingIds)
            } else {
                _retryableRoundtableState.value = null
            }
            _errorMessage.value = "已停止本轮生成，已完成的回复会继续保留。"
            throw error
        } catch (error: IllegalStateException) {
            PrivacySafeLogger.w("RoundtableViewModel", "Duplicate roundtable execution blocked")
            _errorMessage.value = "对话回复正在生成，请稍候或先停止。"
        } catch (error: Exception) {
            PrivacySafeLogger.e("RoundtableViewModel", "Retry generation failed", error)
            _errorMessage.value = "对话生成出错，请稍后重试。"
            withContext(NonCancellable) {
                chatRepo.removePendingMessages(sessionId)
            }
        } finally {
            _typingCharacterIds.value = emptySet()
            _isRoundtableRunning.value = false
            updateRoundActionState(sessionId)
        }
    }

    private suspend fun runRoundtableSequence(
        sessionId: Long,
        questionRunId: Long,
        targetCharacterIds: List<String>,
        responseMode: TranscriptBuilder.ResponseMode,
    ) {
        val context = getApplication<Application>().applicationContext
        if (!AiManager.keysForUseCase(context, AiUseCase.ROUNDTABLE_ANSWER).hasAvailableKeys()) {
            _errorMessage.value = "当前没有可用的 API 密钥，请稍后再试或在“我的配置”中填写密钥。"
            return
        }
        if (targetCharacterIds.isEmpty()) {
            _errorMessage.value = "当前会话没有可用的 Skill 角色，请先增加一个角色。"
            return
        }

        _isRoundtableRunning.value = true
        _errorMessage.value = null
        try {
            val result = withTimeout(ROUNDTABLE_SEQUENCE_TIMEOUT_MS) {
                orchestrator.runRoundtableSequence(
                    sessionId = sessionId,
                    questionRunId = questionRunId,
                    isSemanticRoutingEnabled = _isSemanticRoutingEnabled.value,
                    targetCharacterIds = targetCharacterIds,
                    responseMode = responseMode,
                )
            }
            val retryableIds = buildRetryableCharacterIds(result.failedCharacters, result.timedOutCharacters)
            if (retryableIds.isNotEmpty()) {
                _retryableRoundtableState.value = RetryableRoundtableState(sessionId, questionRunId, retryableIds)
            } else {
                _retryableRoundtableState.value = null
            }
            _errorMessage.value = buildRoundtableFeedback(result, budgetManager.budget)
        } catch (error: TimeoutCancellationException) {
            PrivacySafeLogger.w("RoundtableViewModel", "Roundtable sequence timed out")
            withContext(NonCancellable) {
                chatRepo.removePendingMessages(sessionId)
            }
            _errorMessage.value = "本轮生成等待时间过长，已自动停止。已完成的回复会继续保留。"
        } catch (error: CancellationException) {
            PrivacySafeLogger.d("RoundtableViewModel", "Roundtable sequence cancelled by user")
            withContext(NonCancellable) {
                chatRepo.removePendingMessages(sessionId)
            }
            _errorMessage.value = "已停止本轮生成，已完成的回复会继续保留。"
            throw error
        } catch (error: IllegalStateException) {
            PrivacySafeLogger.w("RoundtableViewModel", "Duplicate roundtable execution blocked")
            _errorMessage.value = "对话回复正在生成，请稍候或先停止。"
        } catch (error: Exception) {
            PrivacySafeLogger.e("RoundtableViewModel", "Roundtable generation failed", error)
            _errorMessage.value = "对话生成出错，请稍后重试。"
            withContext(NonCancellable) {
                chatRepo.removePendingMessages(sessionId)
            }
        } finally {
            _typingCharacterIds.value = emptySet()
            _isRoundtableRunning.value = false
            updateRoundActionState(sessionId)
        }
    }

    private suspend fun callGeminiApi(
        character: Character,
        prompt: String,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker,
        budget: RoundtableBudget,
        sessionId: Long,
        isRequired: Boolean = true,
        reserveForRequired: Int = 0,
        onAttemptStarted: suspend () -> Unit = {},
        onTextUpdate: suspend (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val context = getApplication<Application>().applicationContext
        val folderName = character.skillAssetPath
            .substringAfter("skills/", "")
            .substringBefore("/SKILL.md", "")

        val mainSkillPrompt = com.elio.jianyu.skill.SkillLoader.loadSkill(
            context,
            character.skillAssetPath
        )
        val configuration = AiManager.configuration(context).configuration.value
        val configuredModel = configuration.modelFor(AiUseCase.ROUNDTABLE_ANSWER)
        val provider = attemptPlan.firstOrNull()?.provider ?: configuredModel.provider
        val model = configuredModel.takeIf { it.provider == provider } ?: defaultModel(provider)
        val exampleFiles = if (folderName.isNotBlank()) {
            com.elio.jianyu.skill.SkillLoader
                .listFilesInAssetDir(context, "skills/$folderName/examples")
                .filter { it.endsWith(".md", ignoreCase = true) }
        } else {
            emptyList()
        }
        val referenceFiles = if (folderName.isNotBlank()) {
            com.elio.jianyu.skill.SkillLoader
                .listFilesInAssetDir(context, "skills/$folderName/references")
                .filter { it.endsWith(".md", ignoreCase = true) }
        } else {
            emptyList()
        }

        val totalFiles = exampleFiles + referenceFiles
        val mode = _searchMode.value
        var allSearchInfoText = ""
        val selectedExamples = mutableListOf<String>()
        val selectedReferences = mutableListOf<String>()

        if (totalFiles.isNotEmpty() || mode != SearchMode.OFF) {
            val summariesMap = loadSkillsSummariesOnce(context)
            val formatFileList = {
                if (totalFiles.isEmpty()) {
                    "（当前无候选本地资料）"
                } else {
                    totalFiles.joinToString("\n") { fileName ->
                        val isExample = fileName in exampleFiles
                        val folderSum = summariesMap.optJSONObject(folderName)
                        val fileSum = if (isExample) {
                            folderSum?.optJSONObject("examples")?.optString(fileName, "")
                        } else {
                            folderSum?.optJSONObject("references")?.optString(fileName, "")
                        }
                        val cleanSum = if (fileSum.isNullOrBlank()) "暂无摘要" else fileSum
                        "- $fileName (摘要描述: $cleanSum)"
                    }
                }
            }

            val brokerPrompt = when (mode) {
                SearchMode.OFF -> """
                    你是一个知识检索经纪人 (Broker)。
                    请分析当前的对话上下文，并从下方的【候选本地资料文件列表】中，选择回答当前问题最紧密相关、最必要的参考文件（如果列表为空，则返回空数组）。

                    【对话上下文】
                    $prompt

                    【候选本地资料文件列表】
                    ${formatFileList()}

                    【输出规范】
                    你必须返回一个符合以下 JSON 格式的纯 JSON 字符串。不要包含 any Markdown 格式包裹（例如不要使用 ```json 或 ``` 标记），直接输出 JSON 内容。

                    JSON 格式：
                    {
                      "selectedFiles": ["01-writings.md", "03-expression-dna.md"]
                    }
                """.trimIndent()

                SearchMode.AUTO -> """
                    你是一个知识检索与联网决策代理 (Broker)。
                    请分析当前的对话上下文，并作出以下两项决策：
                    1. 本地资料加载决策：从下方的【候选本地资料文件列表】中，选择回答当前问题最紧密相关、最必要的参考文件（如果列表为空，则返回空数组）。
                    2. 联网搜索接地决策：判断当前问题或对话上下文是否需要最新的实时信息、新闻、外部事实数据来辅助解答。如果需要，请将 `needSearch` 设为 `true`，并在 `searchQueries` 数组中提供 1 到多个精准的搜索关键词（建议 1-3 个）。如果不需要，请将 `needSearch` 设为 `false` 且 `searchQueries` 设为空数组。

                    【对话上下文】
                    $prompt

                    【候选本地资料文件列表】
                    ${formatFileList()}

                    【输出规范】
                    你必须返回一个符合以下 JSON 格式的纯 JSON 字符串。不要包含 any Markdown 格式包裹（例如不要使用 ```json 或 ``` 标记），直接输出 JSON 内容。

                    JSON 格式示例：
                    {
                      "selectedFiles": ["01-writings.md"],
                      "needSearch": true,
                      "searchQueries": ["2026年最新大语言模型发布情况", "Gemini 2.5 flash 新特性"]
                    }
                """.trimIndent()

                SearchMode.ON -> """
                    你是一个知识检索与联网决策代理 (Broker)。
                    当前系统已【强制开启联网搜索】，你必须进行联网接地。
                    请分析当前的对话上下文，并作出以下两项决策：
                    1. 本地资料加载决策：从下方的【候选本地资料文件列表】中，选择回答当前问题最紧密相关、最必要的参考文件（如果列表为空，则返回空数组）。
                    2. 联网搜索接地决策：你必须在 `searchQueries` 数组中列出 1 到多个（建议 1-3 个）核心的联网搜索关键词/任务，用以获取最新的实时事实信息来解答此问题，并将 `needSearch` 设为 `true`。

                    【对话上下文】
                    $prompt

                    【候选本地资料文件列表】
                    ${formatFileList()}

                    【输出规范】
                    你必须返回一个符合以下 JSON 格式的纯 JSON 字符串。不要包含 any Markdown 格式包裹（例如不要使用 ```json 或 ``` 标记），直接输出 JSON 内容。

                    JSON 格式示例：
                    {
                      "selectedFiles": [],
                      "needSearch": true,
                      "searchQueries": ["张雪峰2026高考志愿填报最新建议"]
                    }
                """.trimIndent()
            }

            val brokerModel = configuration.modelFor(AiUseCase.MATERIAL_BROKER)
            val brokerPlan = AiManager.keys(context, brokerModel.provider).createAttemptPlan(sessionId)
            val brokerText = try {
                when (brokerModel.provider) {
                    AiProvider.GEMINI -> GeminiRestTransport.createInteraction(
                        context = context,
                        request = CreateInteractionRequest(
                            model = brokerModel.modelId,
                            input = JsonPrimitive(brokerPrompt),
                            systemInstruction = summariesMap.optJSONObject(folderName)?.toString(),
                        ),
                        sessionId = sessionId,
                        attemptPlan = brokerPlan,
                        tracker = tracker,
                        operationName = "BrokerDecision",
                        isRequired = false,
                        reserveForRequired = reserveForRequired,
                    ).outputText
                    AiProvider.DEEPSEEK -> DeepSeekTransport.createChatCompletion(
                        context = context,
                        sessionId = sessionId,
                        attemptPlan = brokerPlan,
                        model = brokerModel,
                        systemInstruction = summariesMap.optJSONObject(folderName)?.toString(),
                        userContent = brokerPrompt,
                        operationName = "BrokerDecision",
                        tracker = tracker,
                    ).choices.firstOrNull()?.message?.content.orEmpty()
                }
            } catch (error: Exception) {
                PrivacySafeLogger.e("RoundtableViewModel", "Broker request failed", error)
                ""
            }

            val cleanedReply = brokerText
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val decision = try {
                if (cleanedReply.isNotBlank()) {
                    kotlinx.serialization.json.Json.decodeFromString<BrokerDecision>(cleanedReply)
                } else {
                    BrokerDecision()
                }
            } catch (_: Exception) {
                PrivacySafeLogger.w(
                    "RoundtableViewModel",
                    "Broker response was not valid JSON; using bounded fallback parsing"
                )
                val selectedFiles = runCatching {
                    "\"[^\"]+\"".toRegex()
                        .findAll(cleanedReply)
                        .map { it.value.trim('"') }
                        .filter { it.endsWith(".md") }
                        .toList()
                }.getOrDefault(emptyList())
                val needSearch = cleanedReply.contains("\"needSearch\"\\s*:\\s*true".toRegex())
                val searchQueries = runCatching {
                    val pattern = "\"searchQueries\"\\s*:\\s*\\[([^\\]]+)\\]".toRegex()
                    val arrayContent = pattern.find(cleanedReply)?.groupValues?.get(1).orEmpty()
                    if (arrayContent.isBlank()) emptyList() else {
                        "\"([^\"]+)\"".toRegex()
                            .findAll(arrayContent)
                            .map { it.groupValues[1] }
                            .toList()
                    }
                }.getOrDefault(emptyList())
                BrokerDecision(selectedFiles, needSearch, searchQueries)
            }

            PrivacySafeLogger.d(
                "RoundtableViewModel",
                "Broker decision (files=${decision.selectedFiles.size}, search=${decision.needSearch}, queries=${decision.searchQueries.size})"
            )

            var finalNeedSearch = decision.needSearch
            val finalQueries = decision.searchQueries
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .take(budget.maxSearchQueriesPerCharacter)
                .toMutableList()

            if (mode == SearchMode.ON) {
                finalNeedSearch = true
                if (finalQueries.isEmpty()) {
                    val lastUserMsg = prompt.lineSequence()
                        .filter { it.startsWith("用户提问：") }
                        .lastOrNull()
                        ?.removePrefix("用户提问：")
                        ?.trim()
                    finalQueries.add(lastUserMsg.takeUnless { it.isNullOrBlank() } ?: "2026年最新进展")
                }
            } else if (mode == SearchMode.OFF) {
                finalNeedSearch = false
                finalQueries.clear()
            }

            val searchInfos = mutableListOf<String>()
            if (finalNeedSearch) {
                val searchModel = configuration.modelFor(AiUseCase.WEB_GROUNDING)
                val searchPlan = AiManager.keys(context, searchModel.provider).createAttemptPlan(sessionId)
                for ((index, query) in finalQueries.withIndex()) {
                    PrivacySafeLogger.d(
                        "RoundtableViewModel",
                        "Starting web grounding request ${index + 1}/${finalQueries.size}"
                    )
                    val searchRequest = CreateInteractionRequest(
                        model = searchModel.modelId,
                        input = JsonPrimitive(
                            "请针对以下搜索任务进行联网搜索并给出详细总结：\n任务：$query\n对话背景：$prompt"
                        ),
                        tools = listOf(Tool(type = "google_search"))
                    )

                    val searchResponse = try {
                        GeminiRestTransport.createInteraction(
                            context = context,
                            request = searchRequest,
                            sessionId = sessionId,
                            attemptPlan = searchPlan,
                            tracker = tracker,
                            operationName = "GoogleSearch-${index + 1}",
                            isRequired = false,
                            reserveForRequired = reserveForRequired
                        )
                    } catch (error: Exception) {
                        PrivacySafeLogger.e(
                            "RoundtableViewModel",
                            "Web grounding request ${index + 1} failed",
                            error
                        )
                        null
                    }

                    if (searchResponse != null) {
                        val searchReplyText = searchResponse.outputText
                        val annotations = searchResponse.steps
                            .filter { it.type == "model_output" }
                            .flatMap { step -> step.content }
                            .flatMap { content -> content.annotations.orEmpty() }

                        val searchInfo = StringBuilder()
                        searchInfo.append("\n【联网搜索结果 #${index + 1}】\n")
                        searchInfo.append("搜索任务：$query\n")
                        searchInfo.append("搜索总结：\n$searchReplyText\n")
                        if (annotations.isNotEmpty()) {
                            searchInfo.append("参考来源：\n")
                            annotations.forEach { item ->
                                val title = item.title ?: "未知来源"
                                val uri = item.url
                                if (!uri.isNullOrBlank()) searchInfo.append("- [$title]($uri)\n")
                            }
                        }
                        searchInfos.add(searchInfo.toString())
                    }
                }
            }

            if (searchInfos.isNotEmpty()) {
                allSearchInfoText = "\n\n=== 联网接地搜索资料 ===\n" + searchInfos.joinToString("\n")
            }
            selectedExamples.addAll(decision.selectedFiles.filter { it in exampleFiles })
            selectedReferences.addAll(decision.selectedFiles.filter { it in referenceFiles })
        }

        val referencesText = buildString {
            append(mainSkillPrompt)
            append("\n\n=== 本次回答深度 ===\n")
            append(thinkingIntensityDirective())
            append(allSearchInfoText)
            if (selectedExamples.isNotEmpty() || selectedReferences.isNotEmpty()) {
                append("\n\n=== 参考资料文件及内容 ===\n")
                selectedExamples.forEach { fileName ->
                    val textContent = readAssetFileAsString(
                        context,
                        "skills/$folderName/examples/$fileName"
                    )
                    if (!textContent.isNullOrBlank()) {
                        append("--- 示例文件: $fileName ---\n")
                        append(textContent).append("\n")
                    }
                }
                selectedReferences.forEach { fileName ->
                    val textContent = readAssetFileAsString(
                        context,
                        "skills/$folderName/references/$fileName"
                    )
                    if (!textContent.isNullOrBlank()) {
                        append("--- 参考资料: $fileName ---\n")
                        append(textContent).append("\n")
                    }
                }
            }
        }

        if (provider == AiProvider.DEEPSEEK) {
            val response = DeepSeekTransport.createChatCompletion(
                context = context,
                sessionId = sessionId,
                attemptPlan = attemptPlan,
                model = model,
                systemInstruction = referencesText,
                userContent = prompt,
                maxOutputTokens = budget.maxOutputTokensPerAnswer,
                operationName = "MainAnswer-${character.id}",
                tracker = tracker,
                onAttemptStarted = onAttemptStarted,
                onTextUpdate = onTextUpdate,
            )
            return@withContext response.choices.firstOrNull()?.message?.content
                ?.takeIf(String::isNotBlank)
                ?: throw IllegalStateException("DeepSeek 未返回可展示的模型文本")
        }

        val request = CreateInteractionRequest(
            model = model.modelId,
            input = JsonPrimitive(prompt),
            systemInstruction = referencesText,
            store = true,
            previousInteractionId = null,
            generationConfig = InteractionGenerationConfig(
                maxOutputTokens = budget.maxOutputTokensPerAnswer,
                thinkingLevel = currentThinkingLevel(),
                thinkingSummaries = "auto"
            )
        )

        val currentResponse = GeminiInteractionsTransport.createInteraction(
            context = context,
            request = request,
            sessionId = sessionId,
            attemptPlan = attemptPlan,
            tracker = tracker,
            operationName = "MainAnswer-${character.id}",
            isRequired = isRequired,
            reserveForRequired = reserveForRequired,
            onAttemptStarted = onAttemptStarted,
            onTextUpdate = onTextUpdate
        )

        var responseText = currentResponse.outputText
        if (responseText.isBlank()) throw Exception("API 未返回可展示的模型文本")

        val maxTokensLimitation = responseText.length > 6000 &&
            !responseText.trim().endsWith("。") &&
            !responseText.trim().endsWith("}")
        if (maxTokensLimitation) {
            if (!CloudInteractionSettings.isEnabled(context)) {
                PrivacySafeLogger.w("RoundtableViewModel", "Continuation skipped: cloud chain disabled")
            } else {
                PrivacySafeLogger.d("RoundtableViewModel", "Starting interaction continuation")
                val continueRequest = CreateInteractionRequest(
                    model = model.modelId,
                    input = JsonPrimitive("请继续"),
                    systemInstruction = referencesText,
                    store = true,
                    previousInteractionId = currentResponse.id,
                    generationConfig = InteractionGenerationConfig(
                        maxOutputTokens = budget.maxOutputTokensPerAnswer,
                        thinkingLevel = currentThinkingLevel(),
                        thinkingSummaries = "auto"
                    )
                )
                val continueResponse = try {
                    GeminiInteractionsTransport.createInteraction(
                        context = context,
                        request = continueRequest,
                        sessionId = sessionId,
                        attemptPlan = attemptPlan,
                        tracker = tracker,
                        operationName = "ContinueAnswer-${character.id}",
                        isRequired = false,
                        reserveForRequired = reserveForRequired,
                        onAttemptStarted = { onTextUpdate(responseText) },
                        onTextUpdate = { continuationText ->
                            onTextUpdate(responseText + continuationText)
                        }
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    PrivacySafeLogger.e("RoundtableViewModel", "Continuation request failed", error)
                    null
                }
                continueResponse?.outputText?.takeIf { it.isNotBlank() }?.let { responseText += it }
            }
        }

        responseText
    }

    private fun currentThinkingLevel(): String = when (_thinkingIntensity.value) {
        "极简" -> "low"
        "深度" -> "high"
        else -> "medium"
    }

    private fun thinkingIntensityDirective(): String = when (_thinkingIntensity.value) {
        "极简" -> "优先直接结论与必要依据，保持简洁，不展开次要分支。"
        "深度" -> "充分检查关键假设、证据、适用条件与风险，再给出结构化结论。"
        else -> "给出清晰结论、核心依据、主要条件与可执行下一步。"
    }

    fun applyCharacterGroup(group: com.elio.jianyu.data.CharacterGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            val activeIds = group.characterIds.split(",").map { it.trim() }.toSet()
            val all = database.characterDao().getAllCharacters().first()
            val updated = all.map { char -> char.copy(isActive = activeIds.contains(char.id)) }
            database.characterDao().insertAll(updated)
        }
    }

    fun saveCurrentActiveAsGroup(name: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = database.characterDao().getAllCharacters().first()
            val activeIds = all.filter { it.isActive }.map { it.id }.joinToString(",")
            val newGroup = com.elio.jianyu.data.CharacterGroup(
                id = "custom_" + System.currentTimeMillis(),
                name = name,
                description = description,
                characterIds = activeIds,
                isPreset = false
            )
            groupRepo.insert(newGroup)
        }
    }

    fun deleteGroup(id: String) {
        viewModelScope.launch(Dispatchers.IO) { groupRepo.deleteById(id) }
    }

    fun loadDetailSkill(character: Character, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _currentDetailSkillContent.value = com.elio.jianyu.skill.SkillLoader.loadSkill(
                    context,
                    character.skillAssetPath
                )
            } catch (error: Exception) {
                PrivacySafeLogger.e("RoundtableViewModel", "Skill detail loading failed", error)
                _currentDetailSkillContent.value = "无法加载该角色的思维模型详情"
            }
        }
    }

    fun clearDetailSkill() {
        _currentDetailSkillContent.value = null
    }

    private fun readAssetFileAsString(context: android.content.Context, assetPath: String): String? {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (error: Exception) {
            PrivacySafeLogger.e("RoundtableViewModel", "Asset text loading failed", error)
            null
        }
    }

    private fun readAssetFileAsBase64(context: android.content.Context, assetPath: String): String? {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                android.util.Base64.encodeToString(inputStream.readBytes(), android.util.Base64.NO_WRAP)
            }
        } catch (error: Exception) {
            PrivacySafeLogger.e("RoundtableViewModel", "Asset binary loading failed", error)
            null
        }
    }

    fun updateRoundActionState(sessionId: Long) {
        viewModelScope.launch {
            val messages = chatRepo.getMessages(sessionId)
            val lastUserMsg = messages.lastOrNull { it.senderId == "user" }
            if (lastUserMsg == null) {
                _roundActionState.value = RoundActionState.CONTINUE_ROUND
                return@launch
            }

            val questionRunId = lastUserMsg.id
            val activeChars = charRepo.getActiveCharacters()
            if (activeChars.isEmpty()) {
                _roundActionState.value = RoundActionState.CONTINUE_ROUND
                return@launch
            }

            val selectedParticipantIds = budgetManager.getOrSetSelectedParticipants(
                questionRunId,
                activeChars.map { it.id }
            )
            val runMsgIndex = messages.indexOfFirst { it.id == questionRunId }
            if (runMsgIndex == -1) {
                _roundActionState.value = RoundActionState.CONTINUE_ROUND
                return@launch
            }
            val messagesSinceRun = messages.subList(runMsgIndex + 1, messages.size)
            _roundActionState.value = com.elio.jianyu.roundtable.RoundActionStateResolver.resolve(
                selectedParticipantIds = selectedParticipantIds,
                messagesSinceRun = messagesSinceRun,
            )
        }
    }
}

internal fun buildRoundtableFeedback(
    result: OrchestrationResult,
    budget: RoundtableBudget
): String? {
    val notices = mutableListOf<String>()
    if (result.isLimitExceeded) {
        notices += "本次请求最多由 ${budget.maxCharactersPerQuestion} 位 Skill 角色回答。"
    }
    val completedCount = result.completedCharacters.size
    val failedCount = result.failedCharacters.size
    val timedOutCount = result.timedOutCharacters.size
    if (failedCount > 0) {
        notices += when {
            completedCount == 0 && timedOutCount == failedCount ->
                "本次请求的 Skill 角色均未能在规定时间内完成回答，请稍后重试。"
            completedCount == 0 ->
                "本次请求的 Skill 角色均未能完成回答，请检查网络或 API Key 后重试。"
            timedOutCount > 0 ->
                "已保留 ${completedCount} 位 Skill 角色的回复；另有 ${failedCount} 位未完成，其中 ${timedOutCount} 位超时。"
            else ->
                "已保留 ${completedCount} 位 Skill 角色的回复；另有 ${failedCount} 位未完成。"
        }
    }
    return notices.takeIf { it.isNotEmpty() }?.joinToString("\n")
}

enum class RoundActionState {
    CONTINUE_ROUND,
    START_NEXT_ROUND,
}

@kotlinx.serialization.Serializable
data class BrokerDecision(
    val selectedFiles: List<String> = emptyList(),
    val needSearch: Boolean = false,
    val searchQueries: List<String> = emptyList()
)
