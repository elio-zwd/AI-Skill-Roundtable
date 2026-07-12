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

**支持的模型（当前项目使用）**：

| 模型名 | 用途 | 状态 |
|--------|------|------|
| `gemini-2.0-flash` | 主力模型（速度快，质量好） | ✅ 已验证存在 |
| `gemini-1.5-flash` | 备用降级模型 | ✅ 已验证存在 |
| ~~`gemini-3.5-flash`~~ | ❌ 不存在，是 Bug | ❌ 禁止使用 |

## 3. 请求体结构

```json
{
  "contents": [
    {
      "role": "user",
      "parts": [{ "text": "圆桌会议记录 + 轮到你发言的指令" }]
    }
  ],
  "systemInstruction": {
    "parts": [{ "text": "角色扮演 systemPrompt（来自 SKILL.md）" }]
  },
  "generationConfig": {
    "maxOutputTokens": 2048,
    "temperature": 0.9
  },
  "safetySettings": [
    { "category": "HARM_CATEGORY_HARASSMENT", "threshold": "BLOCK_NONE" },
    { "category": "HARM_CATEGORY_HATE_SPEECH", "threshold": "BLOCK_NONE" },
    { "category": "HARM_CATEGORY_SEXUALLY_EXPLICIT", "threshold": "BLOCK_NONE" },
    { "category": "HARM_CATEGORY_DANGEROUS_CONTENT", "threshold": "BLOCK_NONE" }
  ]
}
```

## 4. 响应体结构

```json
{
  "candidates": [
    {
      "content": {
        "parts": [{ "text": "角色的回答文本" }],
        "role": "model"
      },
      "finishReason": "STOP"
    }
  ],
  "usageMetadata": {
    "promptTokenCount": 500,
    "candidatesTokenCount": 200,
    "totalTokenCount": 700
  }
}
```

提取回答：`candidates[0].content.parts[0].text`

## 5. 错误处理规范

| HTTP 状态码 | 含义 | 处理策略 |
|-----------|------|---------|
| 200 | 成功 | 提取文本返回 |
| 400 | 请求格式错误 | 抛出异常，记录日志 |
| 401 / 403 | API Key 无效 | 提示用户重新配置 Key |
| 429 | 超出配额（Rate Limited） | **熔断该 Key 24h，切换下一个 Key** |
| 500 / 503 | 服务器错误 | 重试一次，失败则报错 |

## 6. API Key 轮询机制（参考 life-archive-app）

参见：`D:\My_Elio\life-archive-app\composables\useGemini.js`

核心逻辑：
- 维护一个 Key 池（10 个 Key）
- 记录上次使用的 Key ID，下次优先选其他 Key（避免集中请求单 Key）
- 429 → 熔断 24h（记录到 SharedPreferences）
- 所有 Key 熔断 → 向用户报错「当前 API 配额已耗尽」

## 7. 安全注意事项

- API Key 不得写入任何可提交的文件
- 日志输出时需脱敏：`AIza[0-9A-Za-z_-]{20,}` → `[API_KEY_REDACTED]`
- 生产包需关闭 OkHttp BODY 级别日志
