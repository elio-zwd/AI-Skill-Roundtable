package com.example.skillroundtable.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillroundtable.data.Character
import com.example.skillroundtable.data.ChatSession
import com.example.skillroundtable.data.Message
import com.example.skillroundtable.data.RoundtableDatabase
import com.example.skillroundtable.network.Content
import com.example.skillroundtable.network.GenerateContentRequest
import com.example.skillroundtable.network.Part
import com.example.skillroundtable.network.RetrofitClient
import com.example.skillroundtable.network.ApiKeyPool
import com.example.skillroundtable.network.Tool
import com.example.skillroundtable.network.CreateInteractionRequest
import com.example.skillroundtable.network.Interaction
import com.example.skillroundtable.network.InteractionStep
import com.example.skillroundtable.network.InteractionContent
import com.example.skillroundtable.network.InteractionGenerationConfig
import com.example.skillroundtable.network.outputText
import com.example.skillroundtable.telemetry.CloudInteractionSettings
import com.example.skillroundtable.network.keys.ApiKeyLease
import com.example.skillroundtable.network.keys.ApiKeyScheduler
import com.example.skillroundtable.roundtable.RoundtableBudget
import com.example.skillroundtable.roundtable.RequestBudgetTracker
import com.example.skillroundtable.roundtable.TranscriptBuilder
import com.example.skillroundtable.roundtable.RoundtableOrchestrator
import com.example.skillroundtable.roundtable.RoundtableDatabaseGateway
import com.example.skillroundtable.roundtable.CharacterAnswerGateway
import com.example.skillroundtable.roundtable.RoundtableBudgetManager
import com.example.skillroundtable.roundtable.DefaultDelayProvider
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.coroutines.Dispatchers
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
import android.widget.Toast
import com.example.skillroundtable.audio.AudioPlaybackManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.workDataOf
import androidx.work.WorkManager
import java.io.File

/**
 * 圆桌会议 ViewModel，负责管理会话、消息、智囊角色状态以及触发 API 逻辑。
 */
class RoundtableViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("roundtable_settings", android.content.Context.MODE_PRIVATE)

    private var skillsSummaries: org.json.JSONObject? = null

    private fun loadSkillsSummariesOnce(context: android.content.Context): org.json.JSONObject {
        val current = skillsSummaries
        if (current != null) return current
        val json = try {
            val jsonStr = context.assets.open("skills_summaries.json").use { it.reader().readText() }
            org.json.JSONObject(jsonStr)
        } catch (e: java.lang.Exception) {
            android.util.Log.e("RoundtableViewModel", "加载 skills_summaries.json 失败", e)
            org.json.JSONObject()
        }
        skillsSummaries = json
        return json
    }

    private val database = RoundtableDatabase.getDatabase(application, viewModelScope)
    private val charRepo = com.example.skillroundtable.data.CharacterRepository(database.characterDao())
    private val chatRepo = com.example.skillroundtable.data.ChatRepository(database.chatDao())
    private val groupRepo = com.example.skillroundtable.data.CharacterGroupRepository(database.characterGroupDao())

    data class InteractionChainKey(
        val sessionId: Long,
        val characterId: String
    )

    // 记录 (会话ID, 角色ID) 到上一次云端会话 Interaction ID 的内存缓存，消除多角色共享竞态（PR03预留）
    private val lastInteractionIds = java.util.concurrent.ConcurrentHashMap<InteractionChainKey, String>()

    val allCharacters: StateFlow<List<Character>> = charRepo.allCharacters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGroups: StateFlow<List<com.example.skillroundtable.data.CharacterGroup>> = groupRepo.allGroups
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentDetailSkillContent = MutableStateFlow<String?>(null)
    val currentDetailSkillContent: StateFlow<String?> = _currentDetailSkillContent.asStateFlow()

    val allSessions: StateFlow<List<ChatSession>> = chatRepo.allSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

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

    // UI 状态
    private val _isRoundtableRunning = MutableStateFlow(false)
    val isRoundtableRunning: StateFlow<Boolean> = _isRoundtableRunning.asStateFlow()

    private val _typingCharacterIds = MutableStateFlow<Set<String>>(emptySet())
    val typingCharacterIds: StateFlow<Set<String>> = _typingCharacterIds.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 控制是否自动让下一个角色发言
    private val _isAutoNextEnabled = MutableStateFlow(true)
    val isAutoNextEnabled: StateFlow<Boolean> = _isAutoNextEnabled.asStateFlow()

    fun setAutoNextEnabled(enabled: Boolean) {
        _isAutoNextEnabled.value = enabled
        prefs.edit().putBoolean("is_auto_next_enabled", enabled).apply()
    }

    // 控制是否启用“专家先发”（向量语义路由）
    private val _isSemanticRoutingEnabled = MutableStateFlow(false)
    val isSemanticRoutingEnabled: StateFlow<Boolean> = _isSemanticRoutingEnabled.asStateFlow()

    fun setSemanticRoutingEnabled(enabled: Boolean) {
        _isSemanticRoutingEnabled.value = enabled
        prefs.edit().putBoolean("is_semantic_routing_enabled", enabled).apply()
    }

    // 联网搜索模式（智能搜索、强制联网、关闭联网）
    private val _searchMode = MutableStateFlow(SearchMode.SMART)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    fun setSearchMode(mode: SearchMode) {
        _searchMode.value = mode
        prefs.edit().putString("search_mode", mode.name).apply()
    }

    val apiKeySummaries = ApiKeyPool.summaries

    // 统一的预算管理器
    private val budgetManager = RoundtableBudgetManager()

    // 实现真实编排器所需的网关接口
    private val dbGateway = object : RoundtableDatabaseGateway {
        override suspend fun getMessages(sessionId: Long): List<Message> = chatRepo.getMessages(sessionId)
        override suspend fun insertMessage(message: Message): Long = chatRepo.insertMessage(message)
        override suspend fun deleteMessageById(id: Long) = chatRepo.deleteMessageById(id)
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
            // 将真实 API 过程桥接到 ViewModel 底层 callGeminiApi，同时执行加载文件、联网搜索与续写
            _typingCharacterIds.update { it + character.id }
            return try {
                this@RoundtableViewModel.callGeminiApi(character, prompt, attemptPlan, tracker, budget, sessionId, isRequired, reserveForRequired)
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
            return RetrofitClient.embedContent(
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
        minIntervalMs = 1000L // 固定的速率限制保护间隔
    )

    private val _roundActionState = MutableStateFlow(RoundActionState.CONTINUE_ROUND)
    val roundActionState: StateFlow<RoundActionState> = _roundActionState.asStateFlow()

    init {
        val context = getApplication<Application>().applicationContext
        com.example.skillroundtable.network.ApiKeyPool.init(context)

        // 从 SharedPreferences 中加载设置
        _isAutoNextEnabled.value = prefs.getBoolean("is_auto_next_enabled", true)
        _isSemanticRoutingEnabled.value = prefs.getBoolean("is_semantic_routing_enabled", false)
        val savedSearchModeStr = prefs.getString("search_mode", SearchMode.SMART.name)
        _searchMode.value = try {
            SearchMode.valueOf(savedSearchModeStr ?: SearchMode.SMART.name)
        } catch (e: Exception) {
            SearchMode.SMART
        }

        ensureCoreCharactersExist()
    }

    /**
     * 确保 7 个 GitHub 核心角色在数据库中正确初始化。
     * 从 skills_config.json 动态加载配置列表，再从 assets 动态加载系统提示，并物理清除已废弃的本地角色。
     */
    private fun ensureCoreCharactersExist() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext

            val skillConfigs = com.example.skillroundtable.skill.SkillLoader.loadSkillsConfig(context)
            if (skillConfigs.isEmpty()) {
                Log.e("RoundtableViewModel", "未能在 assets 下找到或成功解析 skills_config.json 配置文件！")
                return@launch
            }

            for (config in skillConfigs) {
                val existing = charRepo.getCharacterById(config.id)
                val prompt = com.example.skillroundtable.skill.SkillLoader.loadSkill(context, config.skillAssetPath)
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

            // 清理已废弃的 6 个本地角色
            val extraIds = listOf("industry_analyst", "ai_visionary", "career_coach", "silver_spoon", "academic_dean", "freelance_nomad")
            for (extraId in extraIds) {
                charRepo.deleteById(extraId)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun selectSession(sessionId: Long) {
        _currentSessionId.value = sessionId
        viewModelScope.launch {
            _currentSession.value = chatRepo.getSessionById(sessionId)
            updateRoundActionState(sessionId)
        }
    }

    fun createNewSession(title: String) {
        viewModelScope.launch {
            val id = chatRepo.createSession(title)
            _currentSessionId.value = id
            _currentSession.value = chatRepo.getSessionById(id)
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            val keysToRemove = lastInteractionIds.keys().toList().filter { it.sessionId == sessionId }
            keysToRemove.forEach { lastInteractionIds.remove(it) }

            // 级联清理所删 Session 的内存预算状态
            val messages = chatRepo.getMessages(sessionId)
            val userMsgIds = messages.filter { it.senderId == "user" }.map { it.id }
            userMsgIds.forEach { budgetManager.clearQuestion(it) }

            chatRepo.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                _currentSessionId.value = null
                _currentSession.value = null
            }
        }
    }

    fun addOrUpdateCharacter(character: Character) {
        viewModelScope.launch {
            charRepo.insert(character)
        }
    }

    fun deleteCharacter(id: String) {
        viewModelScope.launch {
            charRepo.deleteById(id)
        }
    }

    fun askQuestion(text: String) {
        val sessionId = _currentSessionId.value ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            val userMsg = Message(
                chatId = sessionId,
                senderId = "user",
                senderName = "你",
                avatar = "👤",
                text = text
            )
            val questionRunId = chatRepo.insertMessage(userMsg)

            // 1. 触发生产 Orchestrator 脑暴流程，绑定 questionRunId 预算！
            runRoundtableSequence(sessionId, questionRunId)

            // 2. 圆桌彻底跑完后，如果预算还有余额才异步生成标题（防并发抢额度）
            val allMsgs = chatRepo.getMessages(sessionId)
            val userMsgs = allMsgs.filter { it.senderId == "user" }
            if (userMsgs.size == 1) {
                generateSessionTitle(sessionId, questionRunId, text)
            }

            updateRoundActionState(sessionId)
        }
    }

    fun generateSessionTitle(sessionId: Long, questionRunId: Long, firstQuestion: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
            val tracker = budgetManager.getTracker(questionRunId)
            val budget = budgetManager.budget

            // 标题降级：当剩余额度不足以承载角色主回答时，跳过标题生成
            if (tracker.isExceeded() || tracker.getUsed() + 2 >= budget.maxApiCallsPerQuestion) {
                Log.w("RoundtableViewModel", "预算不足，跳过自动生成对话标题。")
                return@launch
            }

            val prompt = """
                你是一个对话标题提炼助手。
                请针对用户提问，提炼出一个简短、吸引人且能概括核心内容的对话标题。
                要求：
                1. 长度不超过 15 个字。
                2. 不要包含任何标点符号、引号或前缀。
                3. 直接输出标题内容，不要有多余解释。

                用户提问：$firstQuestion
            """.trimIndent()

            val request = GenerateContentRequest(
                contents = listOf(Content(parts = listOf(Part(text = prompt))))
            )

            try {
                val plan = ApiKeyScheduler.createAttemptPlan(context, sessionId)
                val response = RetrofitClient.generateContent(
                    context = context,
                    model = "gemini-3.1-flash-lite",
                    request = request,
                    sessionId = sessionId,
                    attemptPlan = plan,
                    tracker = tracker,
                    operationName = "GenerateTitle",
                    isRequired = false,
                    reserveForRequired = 0
                )
                val reply = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
                if (!reply.isNullOrBlank()) {
                    val cleanTitle = reply.replace("\"", "").replace("'", "").trim()
                    chatRepo.updateSessionTitle(sessionId, cleanTitle)
                    val updatedSession = chatRepo.getSessionById(sessionId)
                    if (updatedSession != null) {
                        _currentSession.value = updatedSession
                    }
                }
            } catch (e: Exception) {
                Log.e("RoundtableViewModel", "自动生成对话标题失败", e)
            }
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            chatRepo.updateSessionTitle(sessionId, newTitle)
            val updated = chatRepo.getSessionById(sessionId)
            if (updated != null) {
                _currentSession.value = updated
            }
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

        var currentRound = 0
        for (msg in messages) {
            if (msg.senderId == "user") {
                sb.append("## 👤 用户提问\n")
                sb.append("> ${msg.text}\n\n")
                currentRound = 0
            } else {
                if (msg.roundIndex != currentRound) {
                    currentRound = msg.roundIndex
                    sb.append("## ⚡ 第 ${currentRound} 轮脑暴交锋\n\n")
                }
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
            val apiKeyToUse = ApiKeyPool.getAvailableKeys(context).firstOrNull()?.key.orEmpty()
            if (apiKeyToUse.isBlank()) {
                _errorMessage.value = "无法播放语音：无可用 API Key"
                return@launch
            }

            val cacheDir = context.cacheDir
            val tempWavFile = File(cacheDir, "tts_${message.id}.wav")

            try {
                Log.d("RoundtableViewModel", "正在通过 Gemini Live 合成语音: ${message.id}...")

                val path = com.example.skillroundtable.network.LiveApiClient.generateTtsWav(
                    context = context,
                    apiKey = apiKeyToUse,
                    text = message.text,
                    voiceName = voiceName,
                    outputFile = tempWavFile
                )

                chatRepo.updateMessageAudio(message.id, path, "wav", tempWavFile.length())

                AudioPlaybackManager.playAudio(context, message.id, path)

                enqueueTranscodeWork(message.id, path)
            } catch (e: Exception) {
                Log.e("RoundtableViewModel", "TTS 音频合成失败", e)
                Toast.makeText(context, "语音合成失败: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun enqueueTranscodeWork(messageId: Long, wavPath: String) {
        val context = getApplication<Application>().applicationContext
        val inputData = workDataOf(
            "message_id" to messageId,
            "wav_path" to wavPath
        )
        val request = OneTimeWorkRequestBuilder<com.example.skillroundtable.audio.AudioTranscodeWorker>()
            .setInputData(inputData)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun deleteAudio(message: Message) {
        val context = getApplication<Application>().applicationContext
        viewModelScope.launch(Dispatchers.IO) {
            if (AudioPlaybackManager.currentPlayingMessageId.value == message.id) {
                AudioPlaybackManager.stopAudio()
            }
            if (!message.audioFilePath.isNullOrBlank()) {
                val file = File(message.audioFilePath)
                if (file.exists()) {
                    file.delete()
                }
            }
            chatRepo.updateMessageAudio(message.id, null, null, 0L)
        }
    }

    fun triggerTranscode(messageId: Long, wavPath: String) {
        enqueueTranscodeWork(messageId, wavPath)
    }

    fun triggerNextCharacterManual() {
        val sessionId = _currentSessionId.value ?: return
        viewModelScope.launch {
            val messages = chatRepo.getMessages(sessionId)
            val lastUserMsg = messages.lastOrNull { it.senderId == "user" }
            if (lastUserMsg != null) {
                runRoundtableSequence(sessionId, lastUserMsg.id)
            }
            updateRoundActionState(sessionId)
        }
    }

    private suspend fun runRoundtableSequence(sessionId: Long, questionRunId: Long) {
        val context = getApplication<Application>().applicationContext
        val availableKeys = ApiKeyPool.getAvailableKeys(context)
        if (availableKeys.isEmpty()) {
            _errorMessage.value = "当前没有可用的 API 密钥，请稍后再试或在“我的配置”中填写密钥。"
            return
        }

        _isRoundtableRunning.value = true
        _errorMessage.value = null

        try {
            val result = orchestrator.runRoundtableSequence(sessionId, questionRunId, _isSemanticRoutingEnabled.value)

            // 如果遇到预算不足或限额
            if (result.isLimitExceeded) {
                _errorMessage.value = "本问题按安全预算已执行前 ${budgetManager.budget.maxCharactersPerQuestion} 位智囊角色。"
            } else if (result.isStoppedByBudget) {
                _errorMessage.value = "已达到总 API 请求预算上限（${budgetManager.budget.maxApiCallsPerQuestion} 次），停止后续智囊发言。"
            }
        } catch (e: IllegalStateException) {
            // 防重入引起的异常，回显配额/速率限制保护提示，不静默丢弃
            Log.w("RoundtableViewModel", "重入拦截: ${e.message}")
            _errorMessage.value = "圆桌脑暴正在执行中，请勿重复触发（配额限制保护）。"
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "对话生成出错: ${e.localizedMessage ?: "未知错误"}"
            chatRepo.removePendingMessages(sessionId)
        } finally {
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
        reserveForRequired: Int = 0
    ): String = withContext(Dispatchers.IO) {
        val context = getApplication<Application>().applicationContext

        // 1. 获取技能对应的 folderName
        val folderName = character.skillAssetPath
            .substringAfter("skills/", "")
            .substringBefore("/SKILL.md", "")

        val mainSkillPrompt = com.example.skillroundtable.skill.SkillLoader.loadSkill(context, character.skillAssetPath)

        val exampleFiles = if (folderName.isNotBlank()) {
            com.example.skillroundtable.skill.SkillLoader.listFilesInAssetDir(context, "skills/$folderName/examples")
                .filter { it.endsWith(".md", ignoreCase = true) }
        } else {
            emptyList()
        }

        val referenceFiles = if (folderName.isNotBlank()) {
            com.example.skillroundtable.skill.SkillLoader.listFilesInAssetDir(context, "skills/$folderName/references")
                .filter { it.endsWith(".md", ignoreCase = true) }
        } else {
            emptyList()
        }

        val totalFiles = exampleFiles + referenceFiles
        val mode = _searchMode.value
        var allSearchInfoText = ""
        val selectedExamples = mutableListOf<String>()
        val selectedReferences = mutableListOf<String>()

        // 降级预留策略：如果除去当前轮次剩余主回答预留所需后，没有多余预算，则强制跳过可选的 Broker 和联网搜索！
        if ((totalFiles.isNotEmpty() || mode != SearchMode.OFF) && (tracker.getRemaining() - reserveForRequired > 0)) {
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
                    请分析当前的会议脑暴上下文，并从下方的【候选本地资料文件列表】中，选择回答当前问题最紧密相关、最必要的参考文件（如果列表为空，则返回空数组）。

                    【会议脑暴上下文】
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

                SearchMode.SMART -> """
                    你是一个知识检索与联网决策代理 (Broker)。
                    请分析当前的会议脑暴上下文，并作出以下两项决策：
                    1. 本地资料加载决策：从下方的【候选本地资料文件列表】中，选择回答当前问题最紧密相关、最必要的参考文件（如果列表为空，则返回空数组）。
                    2. 联网搜索接地决策：判断当前问题或脑暴上下文是否需要最新的实时信息、新闻、外部事实数据来辅助解答。如果需要，请将 `needSearch` 设为 `true`，并在 `searchQueries` 数组中提供 1 到多个精准的搜索关键词（建议 1-3 个）。如果不需要，请将 `needSearch` 设为 `false` 且 `searchQueries` 设为空数组。

                    【会议脑暴上下文】
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

                SearchMode.FORCE -> """
                    你是一个知识检索与联网决策代理 (Broker)。
                    当前系统已【强制开启联网搜索】，你必须进行联网接地。
                    请分析当前的会议脑暴上下文，并作出以下决策：
                    1. 本地资料加载决策：从下方的【候选本地资料文件列表】中，选择回答当前问题最紧密相关、最必要的参考文件（如果列表为空，则返回空数组）。
                    2. 联网搜索接地决策：你必须在 `searchQueries` 数组中列出 1 到多个（建议 1-3 个）核心的联网搜索关键词/任务，用以获取最新的实时事实信息来解答此问题，并将 `needSearch` 设为 `true`。

                    【会议脑暴上下文】
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

            val charSummariesJson = summariesMap.optJSONObject(folderName)?.toString()
            val brokerRequest = CreateInteractionRequest(
                model = "gemini-3.1-flash-lite",
                input = JsonPrimitive(brokerPrompt),
                systemInstruction = charSummariesJson
            )

            val brokerResponse = try {
                RetrofitClient.createInteraction(
                    context = context,
                    request = brokerRequest,
                    sessionId = sessionId,
                    attemptPlan = attemptPlan,
                    tracker = tracker,
                    operationName = "BrokerDecision",
                    isRequired = false,
                    reserveForRequired = reserveForRequired
                )
            } catch (e: Exception) {
                Log.e("RoundtableViewModel", "调用 Broker 失败，跳过决策阶段: ${e.message}")
                null
            }

            val brokerReply = brokerResponse?.outputText.orEmpty()
            val cleanedReply = brokerReply
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val decision = try {
                if (cleanedReply.isNotBlank()) {
                    kotlinx.serialization.json.Json.decodeFromString<BrokerDecision>(cleanedReply)
                } else {
                    BrokerDecision()
                }
            } catch (e: Exception) {
                Log.w("RoundtableViewModel", "JSON 反序列化 Broker 决策失败: '$cleanedReply'，尝试正则提取。")
                val selectedFiles = try {
                    val pattern = "\"[^\"]+\"".toRegex()
                    pattern.findAll(cleanedReply).map { it.value.trim('"') }.filter { it.endsWith(".md") }.toList()
                } catch (ex: Exception) {
                    emptyList()
                }
                val needSearch = cleanedReply.contains("\"needSearch\"\\s*:\\s*true".toRegex())
                val searchQueries = try {
                    val pattern = "\"searchQueries\"\\s*:\\s*\\[([^\\]]+)\\]".toRegex()
                    val arrayContent = pattern.find(cleanedReply)?.groupValues?.get(1) ?: ""
                    if (arrayContent.isNotBlank()) {
                        "\"([^\"]+)\"".toRegex().findAll(arrayContent).map { it.groupValues[1] }.toList()
                    } else {
                        emptyList()
                    }
                } catch (ex: Exception) {
                    emptyList()
                }
                BrokerDecision(selectedFiles, needSearch, searchQueries)
            }

            Log.d("RoundtableViewModel", "角色 [${character.name}] 的 Broker 选择加载文件: ${decision.selectedFiles}, 联网 queries: ${decision.searchQueries}")

            var finalNeedSearch = decision.needSearch
            val rawQueries = decision.searchQueries
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinct()

            val finalQueries = rawQueries.take(budget.maxSearchQueriesPerCharacter).toMutableList()

            if (mode == SearchMode.FORCE) {
                finalNeedSearch = true
                if (finalQueries.isEmpty()) {
                    val lastUserMsg = prompt.lineSequence()
                        .filter { it.startsWith("用户提问：") }
                        .lastOrNull()
                        ?.removePrefix("用户提问：")
                        ?.trim()
                    val fallbackQuery = if (!lastUserMsg.isNullOrBlank()) lastUserMsg else "2026年最新进展"
                    finalQueries.add(fallbackQuery)
                }
            } else if (mode == SearchMode.OFF) {
                finalNeedSearch = false
                finalQueries.clear()
            }

            val searchInfos = mutableListOf<String>()
            if (finalNeedSearch) {
                for ((index, query) in finalQueries.withIndex()) {
                    Log.d("RoundtableViewModel", "正在执行联网搜索 #${index + 1} / ${finalQueries.size}: $query")
                    val searchRequest = CreateInteractionRequest(
                        model = "gemini-2.5-flash",
                        input = JsonPrimitive("请针对以下搜索任务进行联网搜索并给出详细总结：\n任务：$query\n脑暴背景：$prompt"),
                        tools = listOf(Tool(type = "google_search"))
                    )

                    val searchResponse = try {
                        RetrofitClient.createInteraction(
                            context = context,
                            request = searchRequest,
                            sessionId = sessionId,
                            attemptPlan = attemptPlan,
                            tracker = tracker,
                            operationName = "GoogleSearch-$query",
                            isRequired = false,
                            reserveForRequired = reserveForRequired
                        )
                    } catch (e: Exception) {
                        Log.e("RoundtableViewModel", "联网搜索失败，跳过: $query, 错误: ${e.message}")
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
                                if (!uri.isNullOrBlank()) {
                                    searchInfo.append("- [${title}](${uri})\n")
                                }
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
            append(allSearchInfoText)
            if (selectedExamples.isNotEmpty() || selectedReferences.isNotEmpty()) {
                append("\n\n=== 参考资料文件及内容 ===\n")
                selectedExamples.forEach { fileName ->
                    val textContent = readAssetFileAsString(context, "skills/$folderName/examples/$fileName")
                    if (!textContent.isNullOrBlank()) {
                        append("--- 示例文件: $fileName ---\n")
                        append(textContent).append("\n")
                    }
                }
                selectedReferences.forEach { fileName ->
                    val textContent = readAssetFileAsString(context, "skills/$folderName/references/$fileName")
                    if (!textContent.isNullOrBlank()) {
                        append("--- 参考资料: $fileName ---\n")
                        append(textContent).append("\n")
                    }
                }
            }
        }

        // PR02 暂停云端链复用，previousInteractionId 恒设为 null
        val request = CreateInteractionRequest(
            model = "gemini-3.5-flash",
            input = JsonPrimitive(prompt),
            systemInstruction = referencesText,
            store = true,
            previousInteractionId = null,
            generationConfig = InteractionGenerationConfig(
                maxOutputTokens = budget.maxOutputTokensPerAnswer,
                thinkingLevel = "high",
                thinkingSummaries = "auto"
            )
        )

        val currentResponse = try {
            RetrofitClient.createInteraction(
                context = context,
                request = request,
                sessionId = sessionId,
                attemptPlan = attemptPlan,
                tracker = tracker,
                operationName = "MainAnswer-${character.id}",
                isRequired = isRequired,
                reserveForRequired = reserveForRequired
            )
        } catch (e: Exception) {
            throw e
        }

        var responseText = currentResponse.outputText
        if (responseText.isNullOrBlank()) {
            throw Exception("API 未返回可展示的模型文本")
        }

        // 续写处理（在剩余可用预算不足以保留主回答时，跳过续写）
        val maxTokensLimitation = responseText.length > 6000 && !responseText.trim().endsWith("。") && !responseText.trim().endsWith("}")
        if (maxTokensLimitation) {
            if (!CloudInteractionSettings.isEnabled(context)) {
                Log.w("RoundtableViewModel", "云端会话链未开启，跳过依赖 previousInteractionId 的续写")
            } else if (tracker.isExceeded() || (tracker.getRemaining() - reserveForRequired <= 0)) {
                Log.w("RoundtableViewModel", "预算已超或配额不足，跳过续写")
            } else {
                Log.d("RoundtableViewModel", "检测到回复可能被截断，发起 Interactions 续写请求...")
                val continueRequest = CreateInteractionRequest(
                    model = "gemini-3.5-flash",
                    input = JsonPrimitive("请继续"),
                    systemInstruction = referencesText,
                    store = true,
                    previousInteractionId = currentResponse.id,
                    generationConfig = InteractionGenerationConfig(
                        maxOutputTokens = budget.maxOutputTokensPerAnswer,
                        thinkingLevel = "high",
                        thinkingSummaries = "auto"
                    )
                )
                val continueResponse = try {
                    RetrofitClient.createInteraction(
                        context = context,
                        request = continueRequest,
                        sessionId = sessionId,
                        attemptPlan = attemptPlan,
                        tracker = tracker,
                        operationName = "ContinueAnswer-${character.id}",
                        isRequired = false,
                        reserveForRequired = reserveForRequired
                    )
                } catch (e: Exception) {
                    Log.e("RoundtableViewModel", "续写失败: ${e.message}")
                    null
                }
                val continueText = continueResponse?.outputText
                if (!continueText.isNullOrBlank()) {
                    responseText += continueText
                }
            }
        }

        responseText
    }


    fun applyCharacterGroup(group: com.example.skillroundtable.data.CharacterGroup) {
        viewModelScope.launch(Dispatchers.IO) {
            val activeIds = group.characterIds.split(",").map { it.trim() }.toSet()
            val all = database.characterDao().getAllCharacters().first()
            val updated = all.map { char ->
                char.copy(isActive = activeIds.contains(char.id))
            }
            database.characterDao().insertAll(updated)
        }
    }

    fun saveCurrentActiveAsGroup(name: String, description: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val all = database.characterDao().getAllCharacters().first()
            val activeIds = all.filter { it.isActive }.map { it.id }.joinToString(",")
            val newGroupId = "custom_" + System.currentTimeMillis()
            val newGroup = com.example.skillroundtable.data.CharacterGroup(
                id = newGroupId,
                name = name,
                description = description,
                characterIds = activeIds,
                isPreset = false
            )
            groupRepo.insert(newGroup)
        }
    }

    fun deleteGroup(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            groupRepo.deleteById(id)
        }
    }

    fun loadDetailSkill(character: Character, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val detail = com.example.skillroundtable.skill.SkillLoader.loadSkill(context, character.skillAssetPath)
                _currentDetailSkillContent.value = detail
            } catch (e: Exception) {
                Log.e("RoundtableViewModel", "加载角色详情 SKILL.md 失败", e)
                _currentDetailSkillContent.value = "无法加载该角色的思维模型详情: ${e.localizedMessage}"
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
        } catch (e: Exception) {
            Log.e("RoundtableViewModel", "读取 Asset 转 String 失败: $assetPath", e)
            null
        }
    }

    private fun readAssetFileAsBase64(context: android.content.Context, assetPath: String): String? {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                val bytes = inputStream.readBytes()
                android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
            }
        } catch (e: Exception) {
            Log.e("RoundtableViewModel", "读取 Asset 转 Base64 失败: $assetPath", e)
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
            val tracker = budgetManager.getTracker(questionRunId)
            val budget = budgetManager.budget

            val isBudgetExceeded = tracker.isExceeded() || tracker.getUsed() >= budget.maxApiCallsPerQuestion

            val activeChars = charRepo.getActiveCharacters()
            if (activeChars.isEmpty()) {
                _roundActionState.value = RoundActionState.CONTINUE_ROUND
                return@launch
            }

            val selectedParticipantIds = budgetManager.getOrSetSelectedParticipants(questionRunId, activeChars.map { it.id })

            val runMsgIndex = messages.indexOfFirst { it.id == questionRunId }
            if (runMsgIndex == -1) {
                _roundActionState.value = RoundActionState.CONTINUE_ROUND
                return@launch
            }
            val messagesSinceRun = messages.subList(runMsgIndex + 1, messages.size)

            _roundActionState.value = com.example.skillroundtable.roundtable.RoundActionStateResolver.resolve(
                selectedParticipantIds = selectedParticipantIds,
                messagesSinceRun = messagesSinceRun,
                isBudgetExceeded = isBudgetExceeded
            )
        }
    }
}

enum class RoundActionState {
    CONTINUE_ROUND,      // 继续本轮
    START_NEXT_ROUND,    // 开启下一轮
    BUDGET_EXCEEDED     // 已达到预算或安全上限（不可点击）
}

enum class SearchMode {
    SMART,  // 智能搜索
    FORCE,  // 强制联网
    OFF     // 关闭联网
}

@kotlinx.serialization.Serializable
data class BrokerDecision(
    val selectedFiles: List<String> = emptyList(),
    val needSearch: Boolean = false,
    val searchQueries: List<String> = emptyList()
)
