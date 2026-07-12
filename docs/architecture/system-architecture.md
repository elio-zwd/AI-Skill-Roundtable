# 系统架构说明

## 1. 整体架构

本应用采用 Android MVVM 架构，使用 Jetpack Compose 构建声明式 UI。整个系统分为 UI 层、ViewModel 层、网络层与 Room 数据持久化层。

```
┌─────────────────────────────────────────────────────────┐
│                        UI 层                             │
│   MainActivity.kt（Compose）                            │
│   ├── RoundtableBrainstormScreen（圆桌脑暴页）           │
│   │   ├── RoundtableSeatingDiagram（席位图 + 双Switch）  │
│   │   ├── MessageBubble（消息气泡）                      │
│   │   └── TypingIndicatorBubble（打字指示器）            │
│   └── CharacterHallScreen（智囊大厅页）                  │
└─────────────────────────────────────────────────────────┘
                           ↕ StateFlow
┌─────────────────────────────────────────────────────────┐
│                      ViewModel 层                        │
│   RoundtableViewModel                                   │
│   ├── askQuestion() → runRoundtableSequence()           │
│   │   ├── 语义路由判定 (Cosine Similarity)              │
│   │   └── 顺序/相似度排序遍历激活角色 → callGeminiApi() │
│   ├── 角色管理（addOrUpdateCharacter / delete）          │
│   └── 会话管理（createNewSession / selectSession）       │
└─────────────────────────────────────────────────────────┘
            ↕ Coroutines/IO              ↕ Retrofit
┌──────────────────────┐    ┌───────────────────────────┐
│      数据层           │    │        网络层              │
│  RoundtableDatabase  │    │   GeminiApi.kt            │
│  ├── CharacterDao    │    │   ├── ApiKeyPool (会话级)  │
│  ├── ChatDao         │    │   ├── callBrokerRouter    │
│  └── Room v2 → v3    │    │   └── Retrofit Service    │
└──────────────────────┘    └───────────────────────────┘
                                          ↕ HTTPS
                               Google Gemini REST API
```

## 2. 核心数据模型

### Character（智囊角色）
```kotlin
@Entity(tableName = "characters")
data class Character(
    @PrimaryKey val id: String,     // 唯一标识 (例如 "elon_musk")
    val name: String,               // 中文名称 (例如 "埃隆·马斯克")
    val avatar: String,             // Emoji 头像 (例如 "🪐")
    val tagline: String,            // tagline 简介
    val systemPrompt: String,       // 动态加载并剥离 YAML frontmatter 后的系统提示词
    val skillAssetPath: String,     // 资产文件在 assets 中的相对路径
    val order: Int,                 // 席位默认发言顺序
    val isActive: Boolean,          // 是否处于激活状态
    val skillDescriptionVector: String = "" // 768维描述向量，以逗号分隔存储
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

### 3.3 会话级 API Key 轮询绑定与熔断保护
- **隐式缓存优化**：API 请求全程以 `sessionId` 为标识进行会话绑定。绑定后在同一个会话中一直使用相同的 API 密钥以激活 Gemini 的隐式上下文前缀缓存。
- **动态换绑**：一旦被绑定的 Key 请求接口返回 HTTP 429 报错，系统立刻将该 Key 熔断 24 小时，并在剩余可用 Key 中自动选出第一个对会话执行重新绑定。
