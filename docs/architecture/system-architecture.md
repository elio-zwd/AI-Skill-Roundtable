# 系统架构说明

## 1. 整体架构

本应用采用 Android MVVM 架构，Jetpack Compose 构建声明式 UI。

```
┌─────────────────────────────────────────────────────────┐
│                        UI 层                             │
│   MainActivity.kt（Compose）                            │
│   ├── RoundtableBrainstormScreen（圆桌脑暴页）           │
│   │   ├── RoundtableSeatingDiagram（席位图）             │
│   │   ├── MessageBubble（消息气泡）                      │
│   │   └── TypingIndicatorBubble（打字指示器）            │
│   └── CharacterHallScreen（智囊大厅页）                  │
└─────────────────────────────────────────────────────────┘
                           ↕ StateFlow
┌─────────────────────────────────────────────────────────┐
│                      ViewModel 层                        │
│   RoundtableViewModel                                   │
│   ├── askQuestion() → runRoundtableSequence()           │
│   │   └── 顺序遍历激活角色 → callGeminiApi()            │
│   ├── 角色管理（addOrUpdateCharacter / delete）          │
│   └── 会话管理（createNewSession / selectSession）       │
└─────────────────────────────────────────────────────────┘
           ↕ Coroutines/IO              ↕ Retrofit
┌──────────────────────┐    ┌───────────────────────────┐
│      数据层           │    │        网络层              │
│  RoundtableDatabase  │    │   GeminiApi.kt            │
│  ├── CharacterDao    │    │   ├── ApiKeyPool（待建）   │
│  ├── ChatDao         │    │   ├── KeyCircuitBreaker    │
│  └── Room v1 → v2    │    │   └── Retrofit Service    │
└──────────────────────┘    └───────────────────────────┘
                                          ↕ HTTPS
                               Google Gemini REST API
                               generativelanguage.googleapis.com
```

## 2. 核心数据模型

### Character（智囊角色）
```kotlin
@Entity(tableName = "characters")
data class Character(
    @PrimaryKey val id: String,     // 如 "elon_musk"
    val name: String,               // 如 "埃隆·马斯克"
    val avatar: String,             // Emoji，如 "🪐"
    val tagline: String,            // 一句话简介
    val systemPrompt: String,       // 角色扮演指令（从 SKILL.md 加载）
    val skillAssetPath: String,     // SKILL.md 在 assets 中的路径（v2 新增）
    val order: Int,                 // 发言顺序
    val isActive: Boolean           // 是否参与本次圆桌
)
```

### ChatSession（会话）
```kotlin
@Entity(tableName = "chat_sessions")
data class ChatSession(id, title, createdAt)
```

### Message（消息）
```kotlin
@Entity(tableName = "messages")
data class Message(id, chatId, senderId, senderName, avatar, text, isPending, timestamp)
```

## 3. 核心业务流程

### 圆桌答复流程（runRoundtableSequence）

```
用户提问
    ↓
保存用户消息到 Room
    ↓
获取所有激活角色列表（按 order 排序）
    ↓
for each character:
    检查该角色本轮是否已答过（避免重复）
        ↓
    显示打字指示器（_typingCharacterId）
        ↓
    插入 isPending=true 占位消息
        ↓
    buildTranscript()：
        取最近 15 条消息 → 格式化为「角色名：内容」
        拼接「现在轮到你发言」指令
        ↓
    callGeminiApi(character, transcript, apiKey)：
        使用角色的 systemPrompt 作为 systemInstruction
        使用 transcript 作为 user message
        ← 返回文本回复
        ↓
    删除占位消息，插入真实回复消息
        ↓
    delay(1200ms)（自然节奏感）
        ↓
    → 下一个角色
        ↓
所有角色答完，isRoundtableRunning = false
```

## 4. Skills 加载流程（待实现）

```
应用启动
    ↓
SkillLoader.loadAllSkills(context)
    ↓
遍历 assets/skills/ 目录
    ↓
读取各 SKILL.md 文件
    ↓
stripFrontmatter() 去除 YAML 头部
    ↓
Character.systemPrompt = 完整 SKILL.md 正文
    ↓
存入 Room 数据库（ON CONFLICT REPLACE）
```

## 5. API Key 轮询流程（待实现）

```
callWithKeyRotation()
    ↓
ApiKeyPool.getNextAvailableKey()
    → 过滤掉熔断中的 Key
    → 优先选用上次未使用的 Key
        ↓
尝试请求 gemini-2.0-flash
    ↓ 成功
返回响应
    ↓ 失败 429
banKey(24h) → 尝试下一个 Key
    ↓ 所有 Key 均熔断
抛出「当前无可用 API Key」错误
```
