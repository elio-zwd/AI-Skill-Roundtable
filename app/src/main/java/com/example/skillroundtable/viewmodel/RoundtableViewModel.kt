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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 圆桌会议 ViewModel，负责管理会话、消息、智囊角色状态以及触发 API 逻辑。
 */
class RoundtableViewModel(application: Application) : AndroidViewModel(application) {

    private val database = RoundtableDatabase.getDatabase(application, viewModelScope)
    private val charRepo = com.example.skillroundtable.data.CharacterRepository(database.characterDao())
    private val chatRepo = com.example.skillroundtable.data.ChatRepository(database.chatDao())

    val allCharacters: StateFlow<List<Character>> = charRepo.allCharacters
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
     * 从 assets 动态加载系统提示，并物理清除已废弃的 6 个本地角色。
     */
    private fun ensureCoreCharactersExist() {
        viewModelScope.launch(Dispatchers.IO) {
            val coreCharacters = listOf(
                Character(
                    id = "zhang_xuefeng",
                    name = "张雪峰",
                    avatar = "👨‍🏫",
                    tagline = "升学志愿与职业规划导师，刺破理想幻泡的实用主义者",
                    systemPrompt = "",
                    skillAssetPath = "skills/zhang_xuefeng.md",
                    order = 1
                ),
                Character(
                    id = "elon_musk",
                    name = "埃隆·马斯克",
                    avatar = "🪐",
                    tagline = "SpaceX与特斯拉CEO，用第一性原理与五步工作法重塑现实的硬核科技狂人",
                    systemPrompt = "",
                    skillAssetPath = "skills/elon_musk.md",
                    order = 2
                ),
                Character(
                    id = "richard_feynman",
                    name = "理查德·费曼",
                    avatar = "🥁",
                    tagline = "诺贝尔物理学奖得主，拒绝虚荣术语，主张用极简大白话解释一切的科学顽童",
                    systemPrompt = "",
                    skillAssetPath = "skills/richard_feynman.md",
                    order = 3
                ),
                Character(
                    id = "charlie_munger",
                    name = "查理·芒格",
                    avatar = "👴",
                    tagline = "用多元思维模型与逆向思考避开愚蠢的终身学习者",
                    systemPrompt = "",
                    skillAssetPath = "skills/charlie_munger.md",
                    order = 4
                ),
                Character(
                    id = "naval_ravikant",
                    name = "纳瓦尔",
                    avatar = "🧘",
                    tagline = "硅谷投资人与现代智者，用“特定知识”与“无需许可的杠杆”追求财富与快乐",
                    systemPrompt = "",
                    skillAssetPath = "skills/naval_ravikant.md",
                    order = 5
                ),
                Character(
                    id = "steve_jobs",
                    name = "史蒂夫·乔布斯",
                    avatar = "🍎",
                    tagline = "苹果公司联合创始人，站在科技与人文的交汇处的完美主义者",
                    systemPrompt = "",
                    skillAssetPath = "skills/steve_jobs.md",
                    order = 6
                ),
                Character(
                    id = "nassim_taleb",
                    name = "纳西姆·塔勒布",
                    avatar = "🏋️",
                    tagline = "《黑天鹅》《反脆弱》作者，关注尾部风险与切肤之痛的风险工程师",
                    systemPrompt = "",
                    skillAssetPath = "skills/nassim_taleb.md",
                    order = 7
                )
            )

            val context = getApplication<Application>().applicationContext
            for (coreChar in coreCharacters) {
                val existing = charRepo.getCharacterById(coreChar.id)
                val prompt = com.example.skillroundtable.skill.SkillLoader.loadSkill(context, coreChar.skillAssetPath)
                if (existing == null) {
                    charRepo.insert(coreChar.copy(systemPrompt = prompt))
                } else {
                    charRepo.insert(
                        existing.copy(
                            name = coreChar.name,
                            avatar = coreChar.avatar,
                            tagline = coreChar.tagline,
                            skillAssetPath = coreChar.skillAssetPath,
                            systemPrompt = prompt,
                            order = coreChar.order
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
            for (character in activeChars) {
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
                val responseText = callGeminiApi(character, transcript, _apiKey.value)

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
        apiKey: String
    ): String = withContext(Dispatchers.IO) {
        val context = getApplication<Application>().applicationContext
        
        // 构建包含 thinkingConfig=high 的请求体
        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = character.systemPrompt))
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
                preferredModel = "gemini-3.5-flash",
                request = request
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
}
