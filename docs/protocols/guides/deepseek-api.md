# DeepSeek API 协议说明

## 1. 范围

应用通过官方 OpenAI 兼容接口接入 DeepSeek 文本模型。当前不把 DeepSeek 声明为 Gemini Live 语音或 Google Search 的替代品。

| 项目 | 当前实现 |
|---|---|
| API 域名 | `https://api.deepseek.com/` |
| 鉴权 | `Authorization: Bearer <用户导入的 Key>` |
| Key 验证 | `GET /models` |
| 文本生成 | `POST /chat/completions` |
| 当前可选模型 | `deepseek-v4-flash`、`deepseek-v4-pro` |

## 2. 请求边界

`DeepSeekTransport` 只发送单次协议请求。`ProviderKeyRepository` 提供该供应商的加密 Key 与会话绑定，`AiRequestExecutor` 负责同 Key 重试、Key 切换、429 冷却和 401/403 状态回写。

文本请求的最小结构如下：

```json
{
  "model": "deepseek-v4-flash",
  "messages": [
    { "role": "system", "content": "角色 Skill 指令" },
    { "role": "user", "content": "用户问题" }
  ],
  "stream": false
}
```

应用读取首个 choice 的 assistant 文本并安全显示。DeepSeek 当前在应用内返回完整文本后再更新圆桌 Pending 消息；它不会伪装为已接入 Google Search。

## 3. 错误处理

- 401 / 403：当前 Key 标记为不可用；
- 429：按 `Retry-After` 或本地退避冷却后切换 Key；
- 5xx / 网络错误：有限重试；
- 非成功 HTTP 响应不解析或显示服务商错误正文。

完整 Key 不写入日志、遥测、测试数据或文档。
