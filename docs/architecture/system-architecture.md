# 系统架构说明

## 1. 整体架构

本应用采用 Android MVVM 架构，使用 Jetpack Compose 构建声明式 UI。整个系统分为 UI 层、ViewModel 层、网络及音频编解码层与 Room 数据持久化层。

```
┌─────────────────────────────────────────────────────────────┐
│                            UI 层                            │
│   MainActivity.kt（Compose 主路由）                          │
│   ├── RoundtableBrainstormScreen（圆桌脑暴页）               │
│   │   ├── RoundtableSeatingDiagram（席位图控制）             │
│   │   ├── HorizontalPager（分轮次横向滑动发言卡片）          │
│   │   └── TypingIndicatorBubble（多角色思考状态 indicator）  │
│   ├── CharacterHallScreen（智囊大厅 + 组合预设一键应用）     │
│   │   └── CharacterDetailBottomSheet（画像 SKILL.md 可视化）│
│   └── AudioLibraryScreen（🎵 离线音频库管理界面）            │
└─────────────────────────────────────────────────────────────┘
                               ↕ StateFlow
┌─────────────────────────────────────────────────────────────┐
│                          ViewModel 层                       │
│   RoundtableViewModel                                       │
│   ├── askQuestion() → runRoundtableSequence()               │
│   │   ├── 语义路由判定 (Cosine Similarity)                  │
│   │   ├── 组间并发 + 组内串行调度 + 随机错峰与防封间隔延迟   │
│   │   └── 多角色并发触发 → callGeminiApi()                  │
│   ├── 离线音频与转码业务 (transcodeAudio)                   │
│   └── 组合预设管理 (applyGroup / saveCustomGroup)           │
└─────────────────────────────────────────────────────────────┘
         ↕ Coroutines/IO       ↕ Retrofit / WS      ↕ WorkManager
┌──────────────────────┐  ┌─────────────────────────┐  ┌─────────────┐
│       数据层         │  │         网络层          │  │  音频引擎   │
│  RoundtableDatabase  │  │  GeminiApi.kt           │  │ (audio/)    │
│  ├── CharacterDao    │  │  ├── ApiKeyPool         │  │ ├──Playback │
│  ├── ChatDao         │  │  ├── callBrokerRouter   │  │ │  Manager  │
│  └── Room v3 → v5    │  │  └── LiveApiClient (WS) │  │ └──Transcode│
└──────────────────────┘  └─────────────────────────┘  └─────────────┘
                                       ↕ HTTPS / WSS
                            Google Gemini REST & Live API
```

## 2. 核心数据模型

### 2.1 Character（智囊角色）
```kotlin
@Entity(tableName = "characters")
data class Character(
    @PrimaryKey val id: String,     // 唯一标识 (例如 "elon_musk")
    val name: String,               // 中文名称
    val avatar: String,             // Emoji 头像
    val tagline: String,            // tagline 简介
    val systemPrompt: String,       // 动态加载并剥离 YAML frontmatter 后的提示词
    val skillAssetPath: String,     // 资产文件在 assets 中的相对路径
    val order: Int,                 // 默认发言顺序
    val isActive: Boolean,          // 是否处于激活状态
    val skillDescriptionVector: String = "", // 768维描述向量，以逗号分隔存储
    val voiceConfig: String = "Aoede" // 角色匹配的 Gemini Live 预设声音名
)
```

### 2.2 Message（发言消息）
```kotlin
@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val chatId: Long,
    val senderId: String,
    val senderName: String,
    val avatar: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isPending: Boolean = false,
    val roundIndex: Int = 0,             // 轮次索引，用于 HorizontalPager 物理分组
    val audioFilePath: String? = null,   // 离线音频本地绝对路径 (WAV/AAC)
    val audioFormat: String? = null,     // 音频文件编码格式 ("wav"/"aac")
    val audioSizeBytes: Long = 0L        // 音频大小（字节）供音频库体积统计
)
```


## 3. 核心业务与控制流

### 3.1 向量语义路由 (Vector Semantic Routing)
当用户在 UI 开启“专家先发”模式时，流程如下：
1. 用户提交问题。
2. ViewModel 调用 `RetrofitClient.embedContentWithFallback(..., questionText)` 获取提问的 768 维特征向量。
3. 计算该问题向量与每一个处于 Active 状态角色的 `skillDescriptionVector` 相似度（使用 **余弦相似度算法** ）。
4. 对可用角色按照相似度由高到低重新排序。
5. 按照重排后的“专家先发”顺序开始圆桌循环发言。

### 3.2 双模型 Context Broker 决策流水线 (Dual-Model Broker Pipeline)
为了在搭载百万级上下文的 Gemini 模型中最高效地拼合 Few-shot 示例及参考文献，且避免 Token 膨胀冷启动，系统设计了双模型动态 Broker 机制：
```
           用户输入与脑暴历史 (prompt)
                      │
                      ▼
 ┌──────────────────────────────────────────┐
 │       Broker (gemini-3.1-flash-lite)     │  1. 快速判定并分析最相关资料
 └──────────────────────────────────────────┘
                      │
        返回选定的文件名 JSON 数组 (例如 ["01-writings.md"])
                      │
                      ▼
 ┌──────────────────────────────────────────┐
 │            SkillLoader 加载拼合           │  2. 动态读取并与 SKILL.md 组装
 └──────────────────────────────────────────┘
                      │
                拼合后的完整 Prompt
                      │
                      ▼
 ┌──────────────────────────────────────────┐
 │       主力模型 (gemini-3.5-flash)        │  3. 开启高强度思考 (Thinking Config)
 └──────────────────────────────────────────┘
                      │
                 输出角色作答
```

### 3.3 会话级 API Key 并发分组、熔断保护与防屏蔽延迟
- **多 Key 并发分组**：会话开始时将所有活跃智囊角色随机乱序，并打乱分成多组（每组 1~3 个角色），每个组绑定一个内置 API Key。
- **组间并发与组内串行**：绑定不同 Key 的角色组之间**并行**发起 API 请求；绑定相同 Key 组内的智囊角色则**串行**发起请求，以最大化复用 Gemini 内部的前缀缓存。
- **错峰与间隔随机延迟 (Anti-blocking)**：
  - 用户发言后，各个 Key 组的首次请求会随机错开 **1~3 秒** 启动。
  - 同一个 Key 组内，上一个智囊作答完毕后，会随机等待 **2~6 秒**，再发起该组内下一智囊的请求。
- **动态换绑**：一旦某组的 Key 请求接口返回 HTTP 429 报错，该 Key 被写回 SharedPreferences 熔断 24 小时，并在剩余可用 Key 中重选第一个绑定至该组。

### 3.4 Gemini Live 语音合成 (TTS) 与后台 MediaCodec 异步转码
离线语音交互逻辑分为两阶段流水线：
1. **第一阶段：WAV 流式拉取与秒级开播**：
   - 用户点击气泡上的 🔊 按钮时，首先在 `LocalFiles` 检查以 `messageId` 命名的音频缓存（支持 `.wav` 和 `.aac` 格式）。
   - 若无缓存，启动 `LiveApiClient` 建立 WebSocket，配置对应的 `voiceConfig` 音色，向 Live API 提交文字生成纯 `AUDIO` 多模态流。
   - 流式接收 PCM 音频包并在内存累加，结束后追加 44 字节 WAV 头文件并写入 `.wav` 路径，直接交由 `MediaPlayer` 播放，避免转码导致的长时间阻塞。
2. **第二阶段：后台异步转码 (AAC 瘦身)**：
   - 播放触发后或在音频管理库中，可通过 WorkManager 后台自动触发 `AudioTranscodeWorker` 异步任务。
   - `MediaCodec` 会在后台线程读取 PCM 音频输入流进行 AAC 编码压缩。
   - 为确保原生播放器的通用识别，转码过程中每帧添加 **7 字节 ADTS 头部**，最终保存为 `.aac`（`.m4a`）文件。
   - 成功后，自动覆写数据库记录，并安全删除占用存储空间的原始 `.wav` 文件（体积减少 85%+）。

