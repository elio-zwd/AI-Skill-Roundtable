# Gemini API 协议说明

## 1. 基本信息

| 项目 | 值 |
|------|---|
| API 域名 | `https://generativelanguage.googleapis.com` |
| API 版本 | `v1beta` |
| 认证方式 | URL Query 参数 `?key=YOUR_API_KEY` |
| 请求方式 | POST |
| 内容类型 | `application/json` |
| 超时配置 | 300 秒（5分钟） |

## 2. 核心端点

### generateContent

```
POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}
```

### embedContent

```
POST https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent?key={api_key}
```

**支持的模型（当前项目使用）**：

| 模型名 | 用途 | 配置说明 |
|--------|------|------|
| `gemini-3.5-flash` | 主力对话思考模型 | 对话生成，开启 `thinkingConfig` (thinkingLevel = `"high"`) |
| `gemini-2.5-flash` | 联网接地检索模型 | 用于携带 `google_search` tools 产生联网搜索总结与网页接地引用 |
| `gemini-3.1-flash-lite` | Context Broker 决策路由器 | 低成本快速做本地文件与联网双决策判定 |
| `gemini-3.1-flash-live-preview` | Live 语音音频模型 | 用于 WebSocket Bidi 实时拉取音频 PCM 裸流（TTS） |
| `gemini-embedding-001` | 向量嵌入模型 | 获取 768 维文本相似度特征向量，供语义路由使用（注：原 text-embedding-004 已下线退休） |

## 3. 对话请求体结构 (主力模型)

```json
{
  "contents": [
    {
      "role": "user",
      "parts": [{ "text": "会议脑暴上下文记录" }]
    }
  ],
  "systemInstruction": {
    "parts": [{ "text": "角色扮演 systemPrompt（SKILL.md + Broker选中的辅助材料）" }]
  },
  "generationConfig": {
    "maxOutputTokens": 2048,
    "temperature": 0.9,
    "thinkingConfig": {
      "thinkingLevel": "high"
    }
  }
}
```

## 4. 向量请求体结构 (gemini-embedding-001)

```json
{
  "model": "models/gemini-embedding-001",
  "content": {
    "parts": [{ "text": "需要提取向量的文本" }]
  },
  "config": {
    "output_dimensionality": 768
  }
}
```

## 5. 错误处理规范

| HTTP 状态码 | 含义 | 处理策略 |
|-----------|------|---------|
| 200 | 成功 | 提取文本/向量返回 |
| 400 | 请求格式错误 | 抛出异常，记录日志 |
| 401 / 403 | API Key 无效 | 提示用户重新配置 Key |
| 429 | 超出配额（Rate Limited） | **熔断该 Key 24h，强制进行换绑与轮询** |
| 500 / 503 | 服务器错误 | 触发换绑重试 |

## 6. 会话级 API Key 绑定与隐式缓存机制

为了最大化提升大模型的 **Implicit Context Cache (隐式上下文缓存)** 命中率，避免每次轮询带来的冷启动，项目采用了 **Conversation-level API Key Binding (会话级密钥绑定)** 策略：

1. **绑定持久化**：
   - 使用 SharedPreferences 对每个 `sessionId` 进行 Key 绑定记录。
   - 会话一经绑定，整个脑暴对话（包括 7 个角色轮流作答及多次提问）全程强绑定同一个 API Key 发起请求。
2. **故障换绑与熔断机制**：
   - 除非遇到 API `429`（频控）或其他 Exception 连接报错，该绑定的 Key 不会改变。
   - 一旦触发 429 报错，将该 Key 写入熔断列表（24小时内禁用），并在剩余可用 Key 中重新选择第一个可用 Key，更新会话与 Key 的绑定关系，发起重试。
3. **缓存命中效果**：
   - 因为同一个会话里大部分请求在同一个 API Key 下运作，触发了 Gemini 内部的大文本隐式前缀缓存，使得每次对话首字延迟（TTFT）大幅缩短。

## 7. Gemini Live WebSocket API (TTS) 协议规范

为了实现低延迟的语音流式合成与直播交互，项目在 `LiveApiClient.kt` 中集成了 Gemini Live 协议：

### 7.1 WebSocket 端点与握手
- **协议**：Secure WebSocket (`wss`)
- **端点**：
  ```
  wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key={api_key}
  ```

### 7.2 帧消息格式
WebSocket 握手成功后，双方采用双向 JSON 帧格式进行实时通信。

#### 1. 客户端初始化配置 (Client Setup Frame)
客户端在连接打开后必须首先发送配置帧，声明目标模型和输出格式：
```json
{
  "setup": {
    "model": "models/gemini-3.1-flash-live",
    "generationConfig": {
      "responseModalities": ["AUDIO"],
      "speechConfig": {
        "voiceConfig": {
          "prebuiltVoiceConfig": {
            "voiceName": "Puck"
          }
        }
      }
    }
  }
}
```

#### 2. 客户端合成请求 (Client Content Frame)
配置完毕后，客户端发送合成的目标文本：
```json
{
  "clientContent": {
    "turns": [
      {
        "role": "user",
        "parts": [
          {
            "text": "请用你原本的声音，清晰平稳地读出这段文字：[目标文本内容]"
          }
        ]
      }
    ],
    "turnComplete": true
  }
}
```

#### 3. 服务端音频响应 (Server Content Frame)
服务端以多帧流式返回 Base64 编码的 PCM 裸音频块：
```json
{
  "serverContent": {
    "modelTurn": {
      "parts": [
        {
          "inlineData": {
            "mimeType": "audio/pcm;rate=24000",
            "data": "UklGRiS5AgBXQVZ..."
          }
        }
      ]
    },
    "turnComplete": false
  }
}
```
- **音频指标**：流式回传的数据为 24000Hz 采样率、单声道、16-bit PCM 编码裸数据。
- **状态判定**：通过服务端返回的 `"turnComplete": true` 来判断音频流已全部回传完成，随后主动关闭 WebSocket。

## 8. Google Search 联网接地工具协议

为了获取互联网最新实时事实，我们在调用 `gemini-2.5-flash` 进行内容生成时，开启了 `google_search` tools：

### 8.1 开启联网工具请求格式

在 `generateContent` 的 JSON 请求体中传入 `tools` 配置项开启谷歌搜索：

```json
{
  "contents": [
    {
      "parts": [
        {
          "text": "请针对以下搜索任务进行联网搜索并给出详细总结：\n任务：[searchQuery]\n脑暴背景：[prompt]"
        }
      ]
    }
  ],
  "tools": [
    {
      "google_search": {}
    }
  ]
}
```

### 8.2 联网响应与 Grounding 元数据格式

当请求携带 `google_search` 工具且模型通过搜索找到了答案，返回结果将包含 `groundingMetadata` 字段，用以指示搜索关键词与网页引用：

```json
{
  "candidates": [
    {
      "content": {
        "role": "model",
        "parts": [
          {
            "text": "联网搜索后的回答文本内容..."
          }
        ]
      },
      "groundingMetadata": {
        "webSearchQueries": [
          "搜索的关键词 1"
        ],
        "groundingChunks": [
          {
            "web": {
              "uri": "https://example.com/source-article",
              "title": "参考源文章网页标题"
            }
          }
        ]
      }
    }
  ]
}
```

- **解析逻辑**：
  Kotlin 段接收到此结果后，循环提取 `groundingChunks` 数组中的 `web.uri` 和 `web.title`。
  将其转化为 Markdown 样式：`- [文章标题](网页链接)`，并附加在总结后面送给最终回答模型。


## 9. API 熔断诊断与请求遥测日志协议

为了支撑 API 熔断调试面板的可视化展示，项目在 `ApiKeyPool` 中定义了请求拦截与状态统计的数据协议。

### 9.1 遥测日志数据结构 (`ApiLog`)

每一次针对 Gemini API（包括 generateContent, embedContent 等）的网络请求，在响应结束或捕获到异常时，都会生成一条 `ApiLog` 记录：

```kotlin
data class ApiLog(
    val keyId: String,          // 调用的 API Key 标识 (如 "w1")
    val model: String,          // 调用的模型名称 (如 "gemini-3.5-flash")
    val requestTime: Long,      // 请求开始时间戳 (Ms)
    val responseTime: Long,     // 请求结束时间戳 (Ms)
    val statusCode: Int,        // HTTP 状态码 (200 代表成功，429 代表频控等，异常时存 -1)
    val errorMessage: String?,  // 错误异常信息 (无则为 null)
    val prompt: String          // 发送给模型的完整 Prompt 文本 (用于可视化 Debug 与审查)
)
```

- **维护规则**：最近的 50 条遥测日志以内存队列 (`CopyOnWriteArrayList`) 形式存储，采用头插法 (`add(0, log)`) 以保证最新请求置顶，超过 50 条自动丢弃尾部旧数据。

### 9.2 Key 状态实体 (`KeyStatus`)

备用 Key 池中每一个 Key 的实时熔断状态由下述结构表达：

```kotlin
data class KeyStatus(
    val id: String,                 // 密钥 ID (如 "w1")
    val isBanned: Boolean,          // 当前是否被熔断 (Ban)
    val banExpireTime: Long,        // 熔断到期时间戳 (未熔断则为 0)
    val remainingBanTimeMs: Long,   // 剩余熔断时间 (Ms)
    val isManualDisabled: Boolean   // 当前是否被手动单独禁用
)
```

- **剩余时间计算**：
  \[\text{remaining} = \max(0, \text{banExpireTime} - \text{System.currentTimeMillis()})\]
- **重置协议**：通过调用 `ApiKeyPool.clearBans(context)` 可以清除所有在 SharedPreferences 中写入的 `ban_{keyId}` 状态，使得被禁用的 Key 立即恢复为可用状态。


## 10. Interactions API (流式脑暴会话) 协议规范

为了实现多智囊角色间的上下文共享并降低大文本 Payload 开销，项目引入了 Interactions API：

### 10.1 核心端点与请求头
```
POST https://generativelanguage.googleapis.com/v1beta/interactions?key={api_key}
```
- **必备 Header**：`Api-Revision: 2026-05-20` (必须指定此特定版本以激活 Interactions 功能)

### 10.2 请求体结构
```json
{
  "previousInteractionId": "v1_xxxx_xxxx", // (可选) 云端上一次会话步骤的 ID，用于链式上下文共享
  "interaction": {
    "systemInstruction": {
      "parts": [{ "text": "systemPrompt 文本（SKILL.md 及本地参考文件直拼）" }]
    },
    "input": {
      "parts": [{ "text": "用户提问 或 当前智囊需要接着脑暴的历史发言记录" }]
    },
    "generationConfig": {
      "thinkingConfig": {
        "thinkingLevel": "high" // 开启 high 最强思维推理
      },
      "thinkingSummaries": "auto" // 自动在响应中输出思维摘要节点
    }
  }
}
```

### 10.3 响应体结构 (Interaction)
```json
{
  "name": "v1/interactions/xxxx", // 本次交互生成的唯一 ID，用于下一次传给 previousInteractionId
  "steps": [
    {
      "modelOutput": {
        "parts": [{ "text": "大模型回复的脑暴内容" }]
      }
    },
    {
      "thinking": {
        "text": "大模型在生成前长文本推理思维链过程（Thinking）"
      }
    },
    {
      "googleSearchResult": {
        "webPages": [
          {
            "uri": "https://example.com/grounding-page",
            "title": "接地引用页面标题"
          }
        ]
      }
    }
  ]
}
```

