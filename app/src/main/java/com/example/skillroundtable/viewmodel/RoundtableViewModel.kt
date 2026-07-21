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
import com.example.skillroundtable.network.keys.ApiKeyLease
import com.example.skillroundtable.network.keys.ApiKeyScheduler
import com.example.skillroundtable.roundtable.RoundtableBudget
import com.example.skillroundtable.roundtable.RequestBudgetTracker
import com.example.skillroundtable.roundtable.TranscriptBuilder
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

    // 记录 (会话ID, 角色ID) 到上一次云端会话 Interaction ID 的内存缓存，消除多角色共享竞态
    private val lastInteractionIds = java.util.concurrent.ConcurrentHashMap<InteractionChainKey, String>()

    // 会话序列锁，防止重复触发圆桌执行
    private val roundtableMutex = kotlinx.coroutines.sync.Mutex()

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
            
            // 从 skills_config.json 动态加载所有角色配置
            val skillConfigs = com.example.skillroundtable.skill.SkillLoader.loadSkillsConfig(context)
            if (skillConfigs.isEmpty()) {
                Log.e("RoundtableViewModel", "未能在 assets 下找到或成功解析 skills_config.json 配置文件！")
                return@launch
            }

            for (config in skillConfigs) {
                val existing = charRepo.getCharacterById(config.id)
                // 动态加载 systemPrompt 头部被剔除的 Markdown
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
            chatRepo.insertMessage(userMsg)

            val allMsgs = chatRepo.getMessages(sessionId)
            val userMsgs = allMsgs.filter { it.senderId == "user" }
            if (userMsgs.size == 1) {
                generateSessionTitle(sessionId, text)
            }

            // 触发圆桌脑暴流程
            runRoundtableSequence(sessionId)
        }
    }

    fun generateSessionTitle(sessionId: Long, firstQuestion: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>().applicationContext
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
                val response = RetrofitClient.callBrokerRouterWithFallback(
                    context = context,
                    model = "gemini-3.1-flash-lite",
                    request = request,
                    sessionId = sessionId,
                    disableBan = true
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
            runRoundtableSequence(sessionId)
        }
    }

    /**
     * 计算两个浮点数特征向量之间的余弦相似度。
     */
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

    private suspend fun runRoundtableSequence(sessionId: Long) {
        val activeChars = charRepo.getActiveCharacters()
        if (activeChars.isEmpty()) {
            _errorMessage.value = "没有激活的智囊角色，请先激活或添加智囊。"
            return
        }

        val context = getApplication<Application>().applicationContext
        val availableKeys = ApiKeyPool.getAvailableKeys(context)
        if (availableKeys.isEmpty()) {
            _errorMessage.value = "当前没有可用的 API 密钥，请稍后再试或在“我的配置”中填写密钥。"
            return
        }

        if (!roundtableMutex.tryLock()) {
            Log.d("RoundtableViewModel", "已有圆桌序列在运行，跳过重复触发。")
            return
        }

        _isRoundtableRunning.value = true
        _errorMessage.value = null

        try {
            val budget = RoundtableBudget()
            val tracker = RequestBudgetTracker(budget.maxApiCallsPerQuestion)

            // 1. 确定当前轮次
            val messages = chatRepo.getMessages(sessionId)
            val lastUserIndex = messages.indexOfLast { it.senderId == "user" }
            if (lastUserIndex == -1) return

            val messagesSinceLastQuestion = messages.subList(lastUserIndex + 1, messages.size)

            val currentRound = if (messagesSinceLastQuestion.isEmpty()) {
                1
            } else {
                val maxRound = messagesSinceLastQuestion.maxOf { it.roundIndex }
                val currentRoundMessages = messagesSinceLastQuestion.filter { it.roundIndex == maxRound }
                val answeredInCurrentRound = currentRoundMessages.map { it.senderId }.toSet()
                val activeCharIds = activeChars.map { it.id }.toSet()

                if (activeCharIds.all { it in answeredInCurrentRound }) {
                    maxRound + 1
                } else {
                    maxRound
                }
            }

            val messagesInTargetRound = messagesSinceLastQuestion.filter { it.roundIndex == currentRound }
            val answeredInTargetRound = messagesInTargetRound.map { it.senderId }.toSet()
            val charactersToAnswer = activeChars.filter { it.id !in answeredInTargetRound }

            if (charactersToAnswer.isEmpty()) {
                return
            }

            // 2. 排序与截断角色数量
            var sortedChars = charactersToAnswer
            if (_isSemanticRoutingEnabled.value) {
                val lastUserMsg = messages[lastUserIndex]
                try {
                    Log.d("RoundtableViewModel", "语义路由已启用，正在对用户提问获取 Embedding...")
                    val attemptPlan = ApiKeyScheduler.createAttemptPlan(context, sessionId)
                    val questionVector = RetrofitClient.embedContent(
                        context = context,
                        text = lastUserMsg.text,
                        sessionId = sessionId,
                        attemptPlan = attemptPlan,
                        tracker = tracker,
                        operationName = "EmbedQuestion"
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
                } catch (e: Exception) {
                    Log.e("RoundtableViewModel", "获取提问向量失败，降级为默认。错误: ${e.message}")
                }
            }

            val selectedCharacters = sortedChars.take(budget.maxCharactersPerQuestion)
            if (activeChars.size > budget.maxCharactersPerQuestion) {
                Log.d("RoundtableViewModel", "激活角色数超过预算限制，本轮仅执行前 ${budget.maxCharactersPerQuestion} 位角色。")
            }

            // 3. 严格顺序圆桌，进行串行遍历
            for (character in selectedCharacters) {
                // 每次开始前重新读取最新消息列表，保证看到前序已入库的内容
                val latestMessages = chatRepo.getMessages(sessionId)

                if (tracker.isExceeded()) {
                    Log.w("RoundtableViewModel", "达到总 API 请求预算上限，停止后续智囊发言。")
                    _errorMessage.value = "已达到总 API 请求预算上限，停止后续智囊发言。"
                    break
                }

                try {
                    executeCharacterAnswer(character, sessionId, currentRound, latestMessages, tracker, budget)
                } catch (e: Exception) {
                    Log.e("RoundtableViewModel", "角色 [${character.name}] 执行脑暴失败: ${e.message}")
                    // 继续下一个角色，保证流程不永久卡死
                }

                // 串行错开随机延迟
                val delayMs = (2000L..5000L).random()
                kotlinx.coroutines.delay(delayMs)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "对话生成出错: ${e.localizedMessage ?: "未知错误"}"
            chatRepo.removePendingMessages(sessionId)
        } finally {
            _isRoundtableRunning.value = false
            roundtableMutex.unlock()
        }
    }

    private suspend fun executeCharacterAnswer(
        character: Character,
        sessionId: Long,
        currentRound: Int,
        latestMessages: List<Message>,
        tracker: RequestBudgetTracker,
        budget: RoundtableBudget
    ) {
        _typingCharacterIds.update { it + character.id }

        val pendingMsgId = chatRepo.insertMessage(
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
            val transcript = TranscriptBuilder.build(latestMessages, character, currentRound)
            val context = getApplication<Application>().applicationContext
            val attemptPlan = ApiKeyScheduler.createAttemptPlan(context, sessionId)

            if (attemptPlan.isEmpty()) {
                throw Exception("没有可用的 API 密钥。")
            }

            val responseText = callGeminiApi(character, transcript, attemptPlan, tracker, budget, sessionId)
            
            chatRepo.deleteMessageById(pendingMsgId)
            chatRepo.insertMessage(
                Message(
                    chatId = sessionId,
                    senderId = character.id,
                    senderName = character.name,
                    avatar = character.avatar,
                    text = responseText,
                    roundIndex = currentRound
                )
            )
        } catch (e: Exception) {
            Log.e("RoundtableViewModel", "生成回答出错: ${character.name}", e)
            chatRepo.deleteMessageById(pendingMsgId)
            _errorMessage.value = "「${character.name}」的回答未显示：${e.localizedMessage ?: "无法读取模型返回内容"}"
            throw e
        } finally {
            _typingCharacterIds.update { it - character.id }
        }
    }

    private suspend fun callGeminiApi(
        character: Character,
        prompt: String,
        attemptPlan: List<ApiKeyLease>,
        tracker: RequestBudgetTracker,
        budget: RoundtableBudget,
        sessionId: Long
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
 
            // 采用 Interactions 架构调用 Broker Lite 模型
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
                    operationName = "BrokerDecision"
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
            
            // 限制搜索 query 最多 3 条
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
                            operationName = "GoogleSearch-$query"
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
 
        // 拼接参考资料纯文本 追加于 system_instruction
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
 
        // 主力脑暴请求逻辑，通过 (sessionId, characterId) 获取专属云端会话链
        val chainKey = InteractionChainKey(sessionId, character.id)
        val cachedInteractionId = lastInteractionIds[chainKey]
        val roundIndex = try {
            val lastUserMsgIndex = prompt.lineSequence().count { it.contains("在第") && it.contains("轮发言：") } + 1
            lastUserMsgIndex
        } catch(e: Exception) {
            1
        }
 
        val useChain = !cachedInteractionId.isNullOrBlank() && roundIndex > 1
        val finalPrompt = if (useChain) {
            "【系统通知】现在，轮到你——「${character.name}」在第 $roundIndex 轮发言了。请记住你的设定、说话语气和人设。请参考、评判、补充或反驳前几位智囊在前几轮的发言，展现出真实的脑暴交锋！第一句话请直接切入重点，给出明确的判断或观点，千万别废话铺垫！"
        } else {
            prompt
        }
 
        val request = CreateInteractionRequest(
            model = "gemini-3.5-flash",
            input = JsonPrimitive(finalPrompt),
            systemInstruction = referencesText,
            store = true,
            previousInteractionId = if (useChain) cachedInteractionId else null,
            generationConfig = InteractionGenerationConfig(
                maxOutputTokens = budget.maxOutputTokensPerAnswer,
                thinkingLevel = "high",
                thinkingSummaries = "auto"
            )
        )
 
        val currentResponse = try {
            if (useChain) {
                Log.d("RoundtableViewModel", "尝试通过云端会话链调用主力模型，ID: $cachedInteractionId, Role: ${character.name}")
            } else {
                Log.d("RoundtableViewModel", "首轮或无云端会话，执行全量上传，Role: ${character.name}")
            }
            RetrofitClient.createInteraction(
                context = context,
                request = request,
                sessionId = sessionId,
                attemptPlan = attemptPlan,
                tracker = tracker,
                operationName = "MainAnswer-${character.id}"
            )
        } catch (e: Exception) {
            if (useChain) {
                lastInteractionIds.remove(chainKey) // 立即将失效 ID 清理出缓存，防止下轮连环 400 报错
                Log.w("RoundtableViewModel", "云端会话链 ${cachedInteractionId} 请求失败，已清理坏链缓存，触发优雅退回兜底分支：全量历史发送...")
                val fallbackRequest = request.copy(
                    input = JsonPrimitive(prompt), // 全量历史
                    previousInteractionId = null   // 不带 ID 重新建链
                )
                RetrofitClient.createInteraction(
                    context = context,
                    request = fallbackRequest,
                    sessionId = sessionId,
                    attemptPlan = attemptPlan,
                    tracker = tracker,
                    operationName = "MainAnswerFallback-${character.id}"
                )
            } else {
                throw e
            }
        }
 
        // 记录最新 Interaction ID 到内存缓存，用于后续轮次
        val newInteractionId = currentResponse.id
        if (newInteractionId.isNotBlank()) {
            lastInteractionIds[chainKey] = newInteractionId
            Log.d("RoundtableViewModel", "更新会话 $sessionId 的角色 ${character.name} 的上一步 Interaction ID 为: $newInteractionId")
        }
 
        var responseText = currentResponse.outputText
        if (responseText.isNullOrBlank()) {
            throw Exception("API 未返回可展示的模型文本")
        }
 
        // 续写处理：在 Interactions API 下同样使用 previous_interaction_id 的链式方式进行续写
        val maxTokensLimitation = responseText.length > 6000 && !responseText.trim().endsWith("。") && !responseText.trim().endsWith("}")
        if (maxTokensLimitation) {
            if (tracker.isExceeded()) {
                Log.w("RoundtableViewModel", "预算已超，跳过续写")
            } else {
                Log.d("RoundtableViewModel", "检测到回复可能被截断，发起 Interactions 续写请求...")
                val continueRequest = CreateInteractionRequest(
                    model = "gemini-3.5-flash",
                    input = JsonPrimitive("请继续"),
                    systemInstruction = referencesText,
                    store = true,
                    previousInteractionId = newInteractionId,
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
                        operationName = "ContinueAnswer-${character.id}"
                    )
                } catch (e: Exception) {
                    Log.e("RoundtableViewModel", "续写失败: ${e.message}")
                    null
                }
                val continueText = continueResponse?.outputText
                if (!continueText.isNullOrBlank()) {
                    responseText += continueText
                    if (continueResponse.id.isNotBlank()) {
                        lastInteractionIds[chainKey] = continueResponse.id
                    }
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

    private fun alternativeApiKey(context: android.content.Context, currentKey: String): String {
        return ApiKeyPool.getAvailableKeys(context)
            .firstOrNull { it.key != currentKey }
            ?.key
            .orEmpty()
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
