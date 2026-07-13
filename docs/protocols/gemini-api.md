# Gemini API 协议说明

## 1. 基本信息

| 项目 | 值 |
|------|---|
| API 域名 | `https://generativelanguage.googleapis.com` |
| API 版本 | `v1beta` |
| 认证方式 | URL Query 参数 `?key=YOUR_API_KEY` |
| 请求方式 | POST |
| 内容类型 | `application/json` |
| 超时配置 | 60 秒 |

## 2. 核心端点

### generateContent

```
POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={api_key}
```

### embedContent

```
POST https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent?key={api_key}
```

**支持的模型（当前项目使用）**：

| 模型名 | 用途 | 配置说明 |
|--------|------|------|
| `gemini-3.5-flash` | 主力对话思考模型 | 对话生成，开启 `thinkingConfig` (thinkingLevel = `"high"`) |
| `gemini-3.1-flash-lite-preview` | Context Broker 决策路由器 | 低成本快速做 Few-shot 与 Reference 文件载入判定 |
| `gemini-3.1-flash-live-preview` | Live 语音音频模型 | 用于 WebSocket Bidi 实时拉取音频 PCM 裸流（TTS） |
| `text-embedding-004` | 向量嵌入模型 | 获取 768 维文本相似度特征向量，供语义路由使用 |

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

## 4. 向量请求体结构 (text-embedding-004)

```json
{
  "model": "models/text-embedding-004",
  "content": {
    "parts": [{ "text": "需要提取向量的文本" }]
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
    "model": "models/gemini-3.1-flash-live-preview",
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
