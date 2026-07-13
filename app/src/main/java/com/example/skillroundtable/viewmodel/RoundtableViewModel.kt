package com.example.skillroundtable.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.skillroundtable.BuildConfig
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
import com.example.skillroundtable.network.GoogleSearch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 圆桌会议 ViewModel，负责管理会话、消息、智囊角色状态以及触发 API 逻辑。
 */
class RoundtableViewModel(application: Application) : AndroidViewModel(application) {

    private val database = RoundtableDatabase.getDatabase(application, viewModelScope)
    private val charRepo = com.example.skillroundtable.data.CharacterRepository(database.characterDao())
    private val chatRepo = com.example.skillroundtable.data.ChatRepository(database.chatDao())
    private val groupRepo = com.example.skillroundtable.data.CharacterGroupRepository(database.characterGroupDao())

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

    private val _typingCharacterId = MutableStateFlow<String?>(null)
    val typingCharacterId: StateFlow<String?> = _typingCharacterId.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 控制是否自动让下一个角色发言
    private val _isAutoNextEnabled = MutableStateFlow(true)
    val isAutoNextEnabled: StateFlow<Boolean> = _isAutoNextEnabled.asStateFlow()

    fun setAutoNextEnabled(enabled: Boolean) {
        _isAutoNextEnabled.value = enabled
    }

    // 控制是否启用“专家先发”（向量语义路由）
    private val _isSemanticRoutingEnabled = MutableStateFlow(false)
    val isSemanticRoutingEnabled: StateFlow<Boolean> = _isSemanticRoutingEnabled.asStateFlow()

    fun setSemanticRoutingEnabled(enabled: Boolean) {
        _isSemanticRoutingEnabled.value = enabled
    }

    // 联网搜索模式（智能搜索、强制联网、关闭联网）
    private val _searchMode = MutableStateFlow(SearchMode.SMART)
    val searchMode: StateFlow<SearchMode> = _searchMode.asStateFlow()

    fun setSearchMode(mode: SearchMode) {
        _searchMode.value = mode
    }

    // 默认保留 API key 状态（兼容 UI 配置）
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    init {
        // 从 BuildConfig 获取初始 API 密钥（如果配置了）
        val configKey = BuildConfig.GEMINI_API_KEY
        if (!configKey.isNullOrBlank()) {
            _apiKey.value = configKey
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

    fun setApiKey(key: String) {
        _apiKey.value = key
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

            // 触发圆桌脑暴流程
            runRoundtableSequence(sessionId)
        }
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
        // 允许使用内置 Key 池，所以当 UI api key 为空且内置 Key 也为空时才报错
        if (_apiKey.value.isBlank() && availableKeys.isEmpty()) {
            _errorMessage.value = "当前没有可用的 API 密钥，请稍后再试或在“我的配置”中填写密钥。"
            return
        }

        _isRoundtableRunning.value = true
        _errorMessage.value = null

        try {
            // 实现向量语义路由 (Vector Semantic Routing) 如果启用，动态排序发言人
            var sortedActiveChars = activeChars
            if (_isSemanticRoutingEnabled.value) {
                val messages = chatRepo.getMessages(sessionId)
                val lastUserMsg = messages.lastOrNull { it.senderId == "user" }
                if (lastUserMsg != null) {
                    try {
                        Log.d("RoundtableViewModel", "语义路由已启用，正在对用户提问获取 Embedding...")
                        // 提取用户提问向量
                        val questionVector = RetrofitClient.embedContentWithFallback(context, lastUserMsg.text, sessionId)
                        
                        // 计算每个可用角色的相似度并降序排序
                        sortedActiveChars = activeChars.map { character ->
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
                            Log.d("RoundtableViewModel", "角色 [${character.name}] 与提问的余弦相似度: $similarity")
                            Pair(character, similarity)
                        }.sortedByDescending { it.second }
                         .map { it.first }

                        Log.d("RoundtableViewModel", "语义路由动态排序完成，最终发言顺序: ${sortedActiveChars.joinToString { it.name }}")
                    } catch (e: Exception) {
                        Log.e("RoundtableViewModel", "获取提问向量失败，降级为数据库默认排序。错误: ${e.message}")
                        sortedActiveChars = activeChars
                    }
                }
            }

            for (character in sortedActiveChars) {
                val messages = chatRepo.getMessages(sessionId)
                val lastUserIndex = messages.indexOfLast { it.senderId == "user" }
                if (lastUserIndex == -1) break

                val messagesSinceLastQuestion = messages.subList(lastUserIndex + 1, messages.size)
                val alreadyAnswered = messagesSinceLastQuestion.any { it.senderId == character.id }

                if (alreadyAnswered) {
                    continue
                }

                _typingCharacterId.value = character.id

                val pendingMsgId = chatRepo.insertMessage(
                    Message(
                        chatId = sessionId,
                        senderId = character.id,
                        senderName = character.name,
                        avatar = character.avatar,
                        text = "正在思考中...",
                        isPending = true
                    )
                )

                val transcript = buildTranscript(messages, character)

                // 核心 API 调用，使用了动态多 Key 轮询熔断机制，忽略已传入的单 key 参数
                val responseText = callGeminiApi(character, transcript, _apiKey.value, sessionId)

                chatRepo.deleteMessageById(pendingMsgId)

                chatRepo.insertMessage(
                    Message(
                        chatId = sessionId,
                        senderId = character.id,
                        senderName = character.name,
                        avatar = character.avatar,
                        text = responseText
                    )
                )

                // 若关闭了“自动顺延”，在当前角色回答完后直接截断循环，等待用户手动点击触发下一个
                if (!_isAutoNextEnabled.value) {
                    break
                }

                kotlinx.coroutines.delay(1200)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _errorMessage.value = "对话生成出错: ${e.localizedMessage ?: "未知错误"}"
            chatRepo.removePendingMessages(sessionId)
        } finally {
            _typingCharacterId.value = null
            _isRoundtableRunning.value = false
        }
    }

    private fun buildTranscript(allMessages: List<Message>, currentCharacter: Character): String {
        val sb = StringBuilder()
        sb.append("【圆桌会议脑暴记录】\n\n")

        val recentMessages = allMessages.takeLast(15)

        for (msg in recentMessages) {
            if (msg.isPending) continue
            val roleName = if (msg.senderId == "user") "用户提问" else "智囊「${msg.senderName}」"
            sb.append("$roleName：${msg.text}\n\n")
        }

        sb.append("现在，轮到你——「${currentCharacter.name}」发言了。\n")
        sb.append("请记住你的设定、说话语气和人设。")
        sb.append("请你站在你自己的专业背景与刺头/支持立场，对用户的提问进行解答，")
        sb.append("同时你**必须**参考、评判、补充或反驳前几位智囊的发言，展现出真实的脑暴交锋！")
        sb.append("第一句话请直接切入重点，给出明确的判断或观点，千万别废话铺垫！")

        return sb.toString()
    }

    private suspend fun callGeminiApi(
        character: Character,
        prompt: String,
        apiKey: String,
        sessionId: Long
    ): String = withContext(Dispatchers.IO) {
        val context = getApplication<Application>().applicationContext
        
        // 1. 从 "skills/elon-musk-skill-main/SKILL.md" 中提取 "elon-musk-skill-main"
        val folderName = character.skillAssetPath
            .substringAfter("skills/", "")
            .substringBefore("/SKILL.md", "")

        val mainSkillPrompt = com.example.skillroundtable.skill.SkillLoader.loadSkill(context, character.skillAssetPath)

        var finalSystemPrompt = mainSkillPrompt

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

        if (totalFiles.isNotEmpty() || mode != SearchMode.OFF) {
            val brokerPrompt = when (mode) {
                SearchMode.OFF -> """
                    你是一个知识检索经纪人 (Broker)。
                    请分析当前的会议脑暴上下文，并从下方的【候选本地资料文件列表】中，选择回答当前问题最紧密相关、最必要的参考文件（如果列表为空，则返回空数组）。
                    
                    【会议脑暴上下文】
                    $prompt
                    
                    【候选本地资料文件列表】
                    ${if (totalFiles.isEmpty()) "（当前无候选本地资料）" else totalFiles.joinToString(", ")}
                    
                    【输出规范】
                    你必须返回一个符合以下 JSON 格式的纯 JSON 字符串。不要包含任何 Markdown 格式包裹（例如不要使用 ```json 或 ``` 标记），直接输出 JSON 内容。
                    
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
                    ${if (totalFiles.isEmpty()) "（当前无候选本地资料）" else totalFiles.joinToString(", ")}
                    
                    【输出规范】
                    你必须返回一个符合以下 JSON 格式的纯 JSON 字符串。不要包含任何 Markdown 格式包裹（例如不要使用 ```json 或 ``` 标记），直接输出 JSON 内容。
                    
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
                    ${if (totalFiles.isEmpty()) "（当前无候选本地资料）" else totalFiles.joinToString(", ")}
                    
                    【输出规范】
                    你必须返回一个符合以下 JSON 格式的纯 JSON 字符串。不要包含任何 Markdown 格式包裹（例如不要使用 ```json 或 ``` 标记），直接输出 JSON 内容。
                    
                    JSON 格式示例：
                    {
                      "selectedFiles": [],
                      "needSearch": true,
                      "searchQueries": ["张雪峰2026高考志愿填报最新建议"]
                    }
                """.trimIndent()
            }

            val brokerRequest = GenerateContentRequest(
                contents = listOf(
                    Content(parts = listOf(Part(text = brokerPrompt)))
                )
            )

            // 优先使用内置 Key 池调用 Lite 模型
            val brokerResponse = try {
                RetrofitClient.callBrokerRouterWithFallback(
                    context = context,
                    model = "gemini-3.1-flash-lite-preview",
                    request = brokerRequest,
                    sessionId = sessionId
                )
            } catch (fallbackEx: Exception) {
                if (apiKey.isNotBlank()) {
                    Log.d("RoundtableViewModel", "内置 Key 池 Broker 路由调用失败，尝试使用用户 API Key 直连...")
                    try {
                        RetrofitClient.service.generateContent(
                            model = "gemini-3.1-flash-lite-preview",
                            apiKey = apiKey,
                            request = brokerRequest
                        )
                    } catch (e: Exception) {
                        Log.e("RoundtableViewModel", "用户 API Key 调用 Broker 路由失败: ${e.message}")
                        null
                    }
                } else {
                    Log.e("RoundtableViewModel", "所有 Key 池均不可用，跳过 Broker 决策阶段")
                    null
                }
            }

            val brokerReply = brokerResponse?.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            
            // 去除 ```json 或 ``` 包裹
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
                Log.w("RoundtableViewModel", "JSON 反序列化 Broker 决策失败: '$cleanedReply'，尝试正则降级提取。")
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

            Log.d("RoundtableViewModel", "角色 [${character.name}] 的 Broker 选择加载文件为: ${decision.selectedFiles}, 联网搜索 queries: ${decision.searchQueries}, 是否需要联网: ${decision.needSearch}")

            // 处理联网模式的实际逻辑与兜底机制
            var finalNeedSearch = decision.needSearch
            val finalQueries = decision.searchQueries.toMutableList()

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

            // 循环遍历查询进行多模型联网搜索配合
            val searchInfos = mutableListOf<String>()
            if (finalNeedSearch) {
                for ((index, query) in finalQueries.withIndex()) {
                    Log.d("RoundtableViewModel", "正在执行联网搜索 #${index + 1} / ${finalQueries.size}: $query")
                    val searchRequest = GenerateContentRequest(
                        contents = listOf(
                            Content(parts = listOf(Part(text = "请针对以下搜索任务进行联网搜索并给出详细总结：\n任务：$query\n脑暴背景：$prompt")))
                        ),
                        tools = listOf(Tool(google_search = GoogleSearch()))
                    )

                    val searchResponse = try {
                        RetrofitClient.generateContentWithFallback(
                            context = context,
                            model = "gemini-2.5-flash",
                            request = searchRequest,
                            sessionId = sessionId
                        )
                    } catch (fallbackEx: Exception) {
                        if (apiKey.isNotBlank()) {
                            Log.d("RoundtableViewModel", "内置多 Key 联网搜索失败，尝试使用用户配置的单 Key 直连...")
                            try {
                                RetrofitClient.service.generateContent(
                                    model = "gemini-2.5-flash",
                                    apiKey = apiKey,
                                    request = searchRequest
                                )
                            } catch (e: Exception) {
                                Log.e("RoundtableViewModel", "用户 API Key 联网搜索失败: ${e.message}")
                                null
                            }
                        } else {
                            Log.e("RoundtableViewModel", "内置 Key 池与用户 Key 均不可用，跳过当前搜索项: $query")
                            null
                        }
                    }

                    if (searchResponse != null) {
                        val searchReplyText = searchResponse.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
                        val groundingMetadata = searchResponse.candidates.firstOrNull()?.groundingMetadata

                        val searchInfo = StringBuilder()
                        searchInfo.append("\n【联网搜索结果 #${index + 1}】\n")
                        searchInfo.append("搜索任务：$query\n")
                        searchInfo.append("搜索总结：\n$searchReplyText\n")

                        val chunks = groundingMetadata?.groundingChunks?.mapNotNull { it.web }?.filter { !it.uri.isNullOrBlank() }
                        if (!chunks.isNullOrEmpty()) {
                            searchInfo.append("参考来源：\n")
                            chunks.forEach { chunk ->
                                val title = chunk.title ?: "未知来源"
                                searchInfo.append("- [${title}](${chunk.uri})\n")
                            }
                        }
                        searchInfos.add(searchInfo.toString())
                    }
                }
            }

            // 拼装本地与联网搜索结果
            val selectedExamples = decision.selectedFiles.filter { it in exampleFiles }
            val selectedReferences = decision.selectedFiles.filter { it in referenceFiles }

            val selectedExamplesText = com.example.skillroundtable.skill.SkillLoader.loadSelectedFiles(
                context, folderName, selectedExamples, isExample = true
            )
            val selectedReferencesText = com.example.skillroundtable.skill.SkillLoader.loadSelectedFiles(
                context, folderName, selectedReferences, isExample = false
            )

            val allSearchInfoText = if (searchInfos.isNotEmpty()) {
                "\n\n=== 联网接地搜索资料 ===\n" + searchInfos.joinToString("\n")
            } else {
                ""
            }

            finalSystemPrompt = mainSkillPrompt + selectedExamplesText + selectedReferencesText + allSearchInfoText
        }
        
        // 构建包含 thinkingConfig=high 的请求体
        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = finalSystemPrompt))
            ),
            generationConfig = com.example.skillroundtable.network.GenerationConfig(
                thinkingConfig = com.example.skillroundtable.network.ThinkingConfig(
                    thinkingLevel = "high"
                )
            )
        )

        // 优先尝试内置多 Key 轮询熔断，如失败且用户在 UI 配置了 key，则尝试用用户 Key 直连
        val response = try {
            RetrofitClient.generateContentWithFallback(
                context = context,
                model = "gemini-3.5-flash",
                request = request,
                sessionId = sessionId
            )
        } catch (fallbackEx: Exception) {
            if (apiKey.isNotBlank()) {
                Log.d("RoundtableViewModel", "内置多 Key 轮询失败，尝试使用用户配置的单 Key 进行直连...")
                RetrofitClient.service.generateContent(
                    model = "gemini-3.5-flash",
                    apiKey = apiKey,
                    request = request
                )
            } else {
                throw fallbackEx
            }
        }

        val responseText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
        if (responseText.isNullOrBlank()) {
            throw Exception("API返回空响应")
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
