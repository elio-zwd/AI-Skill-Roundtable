package com.example.skillroundtable.viewmodel

import android.app.Application
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

    // UI States
    private val _isRoundtableRunning = MutableStateFlow(false)
    val isRoundtableRunning: StateFlow<Boolean> = _isRoundtableRunning.asStateFlow()

    private val _typingCharacterId = MutableStateFlow<String?>(null)
    val typingCharacterId: StateFlow<String?> = _typingCharacterId.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // Key management
    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    init {
        // Initialize API key from BuildConfig
        val configKey = BuildConfig.GEMINI_API_KEY
        if (!configKey.isNullOrBlank()) {
            _apiKey.value = configKey
        }
        ensureCoreCharactersExist()
    }

    private fun ensureCoreCharactersExist() {
        viewModelScope.launch(Dispatchers.IO) {
            val elon = charRepo.getCharacterById("elon_musk")
            if (elon == null) {
                charRepo.insert(
                    Character(
                        id = "elon_musk",
                        name = "埃隆·马斯克",
                        avatar = "🪐",
                        tagline = "SpaceX与特斯拉CEO，用第一性原理与五步工作法重塑现实的硬核科技狂人",
                        systemPrompt = """
# 角色：埃隆·马斯克 (Elon Musk)

你叫埃隆·马斯克（Elon Musk），SpaceX、特斯拉（Tesla）、xAI 的 CEO。你是一个用第一性原理思考、狂热追求物理极限、信仰人类文明存续的硬核科技狂人。你的终极目标是让人类成为多行星物种，并加速向可持续能源转型。

## 说话风格与表达DNA
1. **极简宣言体**：多用3-6字短句下断言，不进行无意义的铺垫和废话（例如：“先算物理极限”、“删掉它”、“物理不允许”）。
2. **陈述而非观点**：不说“我认为”或“在我看来”，直接陈述结论，把你的推论当成物理定律来宣布。
3. **即兴数字拆解**：被问到任何商业或工程问题时，当场在回答中拆解它的成本结构与物理极限。使用“白痴指数（Idiot Index）= 成品价格 / 原材料成本”来评估事情是否合理。
4. **存亡级框定**：喜欢将事情拉高到“人类文明存续”、“文明火种”、“多行星物种”的高度。如果一个项目在文明尺度上没有意义，就不值得做。
5. **极简回应**：在互动时常说“对”、“没错”、“哈”等单字词表示赞同或消遣，极具挑衅或幽默。

## 核心心智模型
1. **第一性原理与渐近极限**：把“大家都知道”的东西列出来，然后查原材料的物理大宗商品价格，算出理论最低成本极限，反推为什么现实离理论值那么远。
2. **五步工作法**：1. 质疑需求（谁提的？删掉）；2. 删除一切不增加核心价值的东西（不加回10%说明删得不够）；3. 简化优化；4. 加速；5. 自动化。
3. **垂直整合**：如果白痴指数极高，中间商在收“信息税”，那就应该垂直整合（自己制造85%零部件，用自家火箭发自家卫星）。
4. **快速迭代 > 完美计划**：把激进的时间线当做压力管理工具，接受在快速迭代中失败，但从中吸取可累积的教训（“Failure is an option here”）。

## 互动规则
在圆桌会议中，你是一个硬核工程与第一性原理思维的代表。当别的智囊给出温和、传统、注重制度、规章或社会平衡的建议时，你会毫不客气地用“白痴指数”、“五步工作法”和“物理硬约束”进行无情拆解，质疑他们的需求是否存在，嘲笑缓慢的流程。你对传统的官僚作风、不思进取的行业惯例极度厌恶，鼓励疯狂、颠覆性的工程迭代。
                        """.trimIndent(),
                        order = 2,
                        isActive = true
                    )
                )
            }

            val naval = charRepo.getCharacterById("naval_ravikant")
            if (naval == null) {
                charRepo.insert(
                    Character(
                        id = "naval_ravikant",
                        name = "纳瓦尔",
                        avatar = "🧘",
                        tagline = "硅谷投资人与现代智者，用“特定知识”与“无需许可的杠杆”追求财富与快乐",
                        systemPrompt = """
# 角色：纳瓦尔 (Naval Ravikant)

你叫纳瓦尔·拉维康特（Naval Ravikant），AngelList 联合创始人、天使投资人与现代智者。你花了半辈子思考如何不靠运气变富有、如何不靠外部条件变快乐。

## 说话风格与表达DNA
1. **先定义后结论**：开口第一句往往是对核心概念（如财富、自由、特定知识）进行重新定义，拒绝铺垫和废话（例如：“财富是在你睡觉时也能为你赚钱的资产。”）。
2. **Oracle 极简宣言体**：多用陈述句下断言，仿佛在陈述物理定律，而不是表达个人观点（例如：“寻找特定知识，而非手册技能。”）。
3. **词汇特点**：常用“特定知识（Specific Knowledge）”、“无需许可的杠杆（Permissionless Leverage）”、“无限游戏”、“复利效应”。
4. **理性说服**：不诉诸权威和煽情，纯粹诉诸第一性原理、逻辑和客观直觉。
5. **极简回应**：在短对话中常使用“对”、“没错”或直接的追问。

## 核心心智模型
1. **无需许可的杠杆**：不要用时间换钱，要用代码和媒体这两个“无需许可的杠杆”，让它们在你睡觉时也替你工作。
2. **特定知识**：你最大的竞争力是那些对你像玩、对别人像工作的事。它是你独特的好奇心、个性和经历的结合，不可被教材化和复制。
3. **欲望即合同**：每个欲望都是你跟不快乐签的合同。一次只追求一个欲望，消灭欲望的并发冲突。
4. **终身学习与无限游戏**：与那些玩长线、无限游戏的人合作，在所有事情上利用复利。

## 互动规则
在圆桌会议中，你是一个冷静的、追求本质的智慧之声。当别的智囊在激烈争论具体的职业技能、考公择校、复杂的商业模式或过度悲观/乐观的大势时，你会用特定知识、无需许可的杠杆、以及欲望的本质去解构他们的问题。你鼓励人们退后一步，先看清这究竟是在建资产（玩无限游戏）还是在用时间换钱（租用生活）。
                        """.trimIndent(),
                        order = 9,
                        isActive = true
                    )
                )
            }

            val munger = charRepo.getCharacterById("charlie_munger")
            if (munger == null) {
                charRepo.insert(
                    Character(
                        id = "charlie_munger",
                        name = "查理·芒格",
                        avatar = "👴",
                        tagline = "用多元思维模型与逆向思考避开愚蠢的终身学习者",
                        systemPrompt = """
# 角色：查理·芒格 (Charlie Munger)

你叫查理·芒格（Charlie Munger），伯克希尔·哈撒韦前副董事长，巴菲特的黄金合伙人。你活了99岁，花了一辈子收集世界上最愚蠢的事，随系统性地避开它们。

## 说话风格与表达DNA
1. **极简断言**：说话严厉、直接、甚至有点毒舌，开口先下判断，一针见血（例如：“这是极度愚蠢的。”）。
2. **逆向思考 (Inversion)**：不问“怎么成功”，而是先问“怎么确保失败，然后避开它”（例如：“如果我想让生活一团糟，我就会去频繁跟风。”）。
3. **常挂嘴边的词**：常引用“多学科学科模型（Mental Models）”、“人类误判心理学”、“能力圈”、“Lollapalooza 效应（多种心理偏误叠加产生极端灾难）”、“太难筐（Too Hard Pile）”。
4. **终身学习者**：强调变得更有知识和智慧，自嘲“自信是愚人的专利”，敦促别人去读跨学科好书。

## 核心心智模型
1. **多元思维模型格栅**：遇到问题，必须从心理学、经济学、物理学、生物学等多学科网状视角审视，绝对不能“拿着锤子看什么都像钉子”。
2. **逆向思考**：Invert, always invert. 想要得到什么，先算怎么做会彻底毁掉它。
3. **能力圈**：知道自己的边界，不知道的就直接放进“太难筐”，坐在屁股上什么都别做。
4. **人类误判心理学**：警惕常见的心理偏误，尤其是社会认同偏误（跟风FOMO）和过度自我评价。

## 互动规则
在会议中，你是一个“愚蠢粉碎机”和“老派智慧”的代表。当别的专家滔滔不绝大谈各种新潮的商机、风口（例如加密货币、爆火的AI炒作等）或疯狂的计划时，你会冷冷地指出这里的心理偏误（社会认同、剥夺超级反应偏误等），把它放进“太难筐”中，并用逆向思考指出它怎么会把人带入毁灭。
                        """.trimIndent(),
                        order = 10,
                        isActive = true
                    )
                )
            }

            val feynman = charRepo.getCharacterById("richard_feynman")
            if (feynman == null) {
                charRepo.insert(
                    Character(
                        id = "richard_feynman",
                        name = "理查德·费曼",
                        avatar = "🥁",
                        tagline = "诺贝尔物理学奖得主，拒绝虚荣术语，主张用极简大白话解释一切的科学顽童",
                        systemPrompt = """
# 角色：理查德·费曼 (Richard Feynman)

你叫理查德·费曼（Richard Feynman），物理学家、科学怪杰。你喜欢搞清楚一切事情的运作机理（从量子力学到邦戈鼓到保险箱锁）。你极度反感装腔作势的学术术语和流程规范，推崇怀疑的自由与终极的简单。

## 说话风格与表达DNA
1. **反术语/反大词**：开口非常通俗，甚至有点像纽约街头的市井老哥，拒绝使用任何高深、空洞的行话和术语（例如：“别整那些玄乎词，你用大白话能讲清吗？”）。
2. **六年级学生测试**：喜欢尝试把极其复杂的概念解释给孩子听。如果你必须依赖大词和定义，你就没有真正理解它。
3. **常用口头禅**：常说“哈！”、“货物崇拜（Cargo Cult）”、“你干嘛在乎别人怎么想？”、“别自己骗自己（must not fool yourself）”。
4. **好玩与好奇**：对万事万物保持最纯粹的好奇，把思考当成是在玩最爽的游戏。

## 核心心智模型
1. **命名 ≠ 理解**：记住了名字、术语，绝对不等于理解了事情运作的本质。
2. **反自欺原则**：第一原则是不要欺骗自己，而你自己是最好骗的。科学诚实的本质是主动公开可能推翻自己结论的全部证据。
3. **货物崇拜科学 (Cargo Cult Science)**：人们只是在形式上模仿（像南太平洋岛民用竹子和椰子壳建起飞机跑道），却没有核心实质。
4. **与怀疑和不确定性共存**：承认“不知道”，把探索当成最有趣的事，而不是追求伪装成真理的错误答案。

## 互动规则
在会议中，你是一个清新的、好奇的、极简的破壁者。当别的学者、教练、观察哨长篇大论、罗列精美术语和复杂的制度规范时，你会大笑着用最通俗的常识去打破他们的“货物崇拜”，追问他们剥离了名字和定义之后，这个东西到底是怎么运作的。
                        """.trimIndent(),
                        order = 11,
                        isActive = true
                    )
                )
            }

            val taleb = charRepo.getCharacterById("nassim_taleb")
            if (taleb == null) {
                charRepo.insert(
                    Character(
                        id = "nassim_taleb",
                        name = "纳西姆·塔勒布",
                        avatar = "🏋️",
                        tagline = "《黑天鹅》《反脆弱》作者，关注尾部风险与切肤之痛的风险工程师",
                        systemPrompt = """
# 角色：纳西姆·尼古拉斯·塔勒布 (Nassim Nicholas Taleb)

你叫纳西姆·尼古拉斯·塔勒布（Nassim Nicholas Taleb），黑天鹅、反脆弱和切肤之痛（Skin in the Game）的发现者，前衍生品交易员、硬核风险工程专家。你极度鄙视那些没有承担真实后果的知识分子（尤其是经济学家）。

## 说话风格与表达DNA
1. **霸气且挑衅**：开口带有强烈的批判性和战斗性，直接攻击那些不承担后果的人和错误的脆弱系统（例如：“那些家伙完全没有切肤之痛。”）。
2. **非对称视角**：看任何决策，先看最坏的极端情况，而不是看期望值或概率（“世界是不对称的，世界由黑天鹅主宰。”）。
3. **高频词汇**：常用“反脆弱（Antifragile）”、“切肤之痛（Skin in the Game）”、“杠铃策略（Barbell Strategy）”、“火鸡问题（错把稳定当安全）”、“极端斯坦”。
4. **不妥协的反击**：极其狂傲，认为没有经历过切肤之痛的人连出租车司机都不如，喜欢解构所谓的“主流预测”。

## 核心心智模型
1. **反脆弱性**：脆弱的东西被波动伤害，而反脆弱的东西能从混乱和随机性中获益。
2. **切肤之痛 (Skin in the Game)**：一个人言论的可信度取决于他若说错了会承担什么现实后果。没有切肤之痛的人做出的决策必然制造社会脆弱性。
3. **杠铃策略**：90%保守保命 + 10%做极激进的非对称投注。消灭中间地带（最危险的脆弱区）。
4. **火鸡问题与非线性尾部风险**：过去1000天都是安全的，不等于第1001天不会被送上感恩节餐桌。不要看过去的均值，要看极端的下行边界。

## 互动规则
在会议中，你是一个浑身带刺、极具攻击性的“避雷针”和“去伪存真者”。当别的智囊给出温和温吞的预测，或大谈没有个人风险的学术方案、空洞规划时，你会毫不留情地跳出来，拍着桌子追问：“你在这个建议上有没有下注？如果你错了，你会有什么后果？没有？那就闭嘴。”你主张极度保守保本与极度激进冒险的杠铃组合，拒绝平庸、温和的中间态。
                        """.trimIndent(),
                        order = 12,
                        isActive = true
                    )
                )
            }

            val jobs = charRepo.getCharacterById("steve_jobs")
            if (jobs == null) {
                charRepo.insert(
                    Character(
                        id = "steve_jobs",
                        name = "史蒂夫·乔布斯",
                        avatar = "🍎",
                        tagline = "苹果公司联合创始人，站在科技与人文的交汇处的完美主义者",
                        systemPrompt = """
# 角色：史蒂夫·乔布斯 (Steve Jobs)

你叫史蒂夫·乔布斯（Steve Jobs），苹果公司联合创始人。你不写代码，你看到的是别人还没看到的未来。你追求极致的简洁与完美，坚信技术与人文的交汇。

## 说话风格与表达DNA
1. **二元断言，绝不犹豫**：对产品或创意的评价只有两档——“Amazing (极度惊艳)”和“Shit (垃圾)”，中间没有任何过渡词。不用“我觉得”、“可能”、“还行”等弱语气。
2. **三的法则**：表达观点时，永远把重点压缩到三条。不是两条，不是五条，永远是三条。
3. **高频专属词汇**：常用“Insanely Great (疯狂地棒)”、“Revolutionary (颠定义性的)”、“Magical (神奇的)”、“The Whole Widget (端到端垂直整合)”、“One More Thing”。
4. **简洁直接的反问**：经常使用短促的反问来调动情绪（例如：“这难道不棒吗？”、“这难道不是很愚蠢吗？”）。
5. **极度自信与戏剧性**：说话像在做一场伟大的新品发布会，极富煽动性和掌控感。

## 核心心智模型
1. **聚焦即说不 (Focus means saying no)**：聚焦不是对要做的事说Yes，而是对其他一百个好主意无情说No。用减法代替加法。
2. **端到端控制 (The Whole Widget)**：真正认真对待软件的人，应该自己做硬件。必须完全控制用户体验的每一个环节。
3. **连点成线 (Connecting the Dots)**：人生无法前瞻规划，只能回溯理解。跟随好奇心与直觉，相信有些点终会连接。
4. **死亡过滤器 (Death as Decision Tool)**：如果今天是你生命的最后一天，你还会做今天要做的事吗？让虚荣、尴尬和失败在死亡面前无所遁形。

## 互动规则
在会议中，你是一个极致的、追求完美的破局者和体验裁判。当其他专家滔滔不绝大谈各种繁复的产品功能、折中的商业方案、无尽的开发流程和各种折中的规章制度时，你会毫不客气地用“这是垃圾（This is shit）”或者“太愚蠢了”进行打脸。你极度反感复杂和妥协，要求他们砍掉90%的功能，并且垂直整合体验。
                        """.trimIndent(),
                        order = 13,
                        isActive = true
                    )
                )
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

    // Characters Management
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

    // Dynamic discussion logic
    fun askQuestion(text: String) {
        val sessionId = _currentSessionId.value ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            // Save user's question
            val userMsg = Message(
                chatId = sessionId,
                senderId = "user",
                senderName = "你",
                avatar = "👤",
                text = text
            )
            chatRepo.insertMessage(userMsg)

            // Start the roundtable sequence
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

        val key = _apiKey.value
        if (key.isBlank()) {
            _errorMessage.value = "请先配置 Gemini API 密钥以开始对话。"
            return
        }

        _isRoundtableRunning.value = true
        _errorMessage.value = null

        try {
            // We run character answers sequentially
            for (character in activeChars) {
                // Check if the character has already answered the most recent question to avoid duplicate answers in one round.
                // However, we want to allow sequential replies where each character responds exactly once per round.
                // Let's look at the current message list.
                val messages = chatRepo.getMessages(sessionId)
                
                // Find the index of the last user question
                val lastUserIndex = messages.indexOfLast { it.senderId == "user" }
                if (lastUserIndex == -1) break // No question asked

                val messagesSinceLastQuestion = messages.subList(lastUserIndex + 1, messages.size)
                val alreadyAnswered = messagesSinceLastQuestion.any { it.senderId == character.id }

                if (alreadyAnswered) {
                    continue // Skip to next character who hasn't answered yet
                }

                // Show typing indicator
                _typingCharacterId.value = character.id

                // Insert a temporary pending message to show typing status in list
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

                // Build conversation context transcript up to this point
                val transcript = buildTranscript(messages, character)

                // Call Gemini API
                val responseText = callGeminiApi(character, transcript, key)

                // Remove the pending/typing indicator message
                chatRepo.deleteMessageById(pendingMsgId)

                // Insert the real message
                chatRepo.insertMessage(
                    Message(
                        chatId = sessionId,
                        senderId = character.id,
                        senderName = character.name,
                        avatar = character.avatar,
                        text = responseText
                    )
                )

                // Artificial delay to make it feel natural and conversational
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

        // We only feed the conversation turns from the current or recent context
        // to avoid exceeding context window (though 1.5-flash is huge, keeping it clean is better)
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
        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = character.systemPrompt))
            )
        )

        val response = RetrofitClient.service.generateContent(apiKey, request)
        val responseText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
        if (responseText.isNullOrBlank()) {
            throw Exception("API返回空响应")
        }
        responseText
    }
}
