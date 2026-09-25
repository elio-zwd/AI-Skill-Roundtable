# Gemini 模型请求与思考档位契约

> 文档状态：2026-09-26 在线核验
>
> 适用范围：见域的 Gemini 文本对话、资料决策、联网接地和会话标题请求。语音、图像、视频等专用模型另有协议，不能把本文的文本请求体直接套用到它们。
>
> 核验来源：[Interactions API](https://ai.google.dev/gemini-api/docs/interactions-overview)、[Gemini Thinking](https://ai.google.dev/gemini-api/docs/thinking)、[Google Search Grounding](https://ai.google.dev/gemini-api/docs/google-search)、[Gemini 3.8 Flash](https://ai.google.dev/gemini-api/docs/latest-model)。模型能力会变化；新增或替换模型前必须重新核验官方文档。

## 1. 目的

Gemini 的“思考档位”不是所有模型通用的四选一字符串：

- `gemini-3.8-flash` 与 `gemini-3.7-flash` 不支持 `minimal`，传入会返回错误，极简请求必须使用 `low`；
- `gemini-3.6-flash`、`gemini-3.5-flash` 与 `gemini-3.1-flash-lite` 支持 `minimal`；
- Gemini 3.1 Pro 不支持 `minimal`；
- Gemini 2.5 系列在旧 GenerateContent 思考协议中使用不同参数，不能直接套用 Gemini 3 的当前 Interactions 配置。

因此，调用方不得仅按 UI 文案把“极简”固定映射成同一个 API 值，必须先按模型能力选择合法参数。

## 2. 当前应用已配置的 Gemini 文本模型

`app/src/main/java/com/elio/jianyu/network/AiProvider.kt` 允许下列 Gemini 模型进入文本模型选择。Gemini 默认模型为 `gemini-3.8-flash`；已有用户明确保存的模型选择不应因为默认值变化而被覆盖。

| 模型 ID | 默认思考档位 | 合法 `thinking_level` | “极简”请求值 | Google Search |
|---|---:|---|---|---|
| `gemini-3.8-flash` | `medium` | `low`、`medium`、`high` | `low` | 支持 |
| `gemini-3.7-flash` | `medium` | `low`、`medium`、`high` | `low` | 支持 |
| `gemini-3.6-flash` | `medium` | `minimal`、`low`、`medium`、`high` | `minimal` | 支持 |
| `gemini-3.5-flash` | `medium` | `minimal`、`low`、`medium`、`high` | `minimal` | 支持 |
| `gemini-3.1-flash-lite` | `minimal` | `minimal`、`low`、`medium`、`high` | `minimal` | 支持 |

UI 的“极简 / 均衡 / 深度”先映射为应用统一档位 `minimal / medium / high`，真正构造 Gemini Interactions 请求时再执行模型级归一化。这样 3.8 / 3.7 不会收到非法的 `minimal`，3.6 / 3.5 / 3.1 Flash-Lite 仍保留其合法的最低档位。

## 3. 可作为后续文本候选的 Gemini 3 模型

下表是当前未加入应用文本模型选择、但可能在后续评估的 Gemini 3 模型。

| 模型 ID | 默认思考档位 | 合法 `thinking_level` | “极简”请求值 | 加入应用前的要求 |
|---|---:|---|---|---|
| `gemini-3.5-flash-lite` | `minimal` | `minimal`、`low`、`medium`、`high` | `minimal` | 核验成本与使用场景后再开放 |
| `gemini-3.1-pro-preview` | `high` | `low`、`medium`、`high` | `low` | Preview 模型需额外评估稳定性 |
| `gemini-3-flash-preview` | `high` | `minimal`、`low`、`medium`、`high` | `minimal` | Preview 模型需额外评估稳定性 |

`minimal` 表示大多数简单请求只进行最低限度思考，不等于绝对关闭推理。

## 4. 请求体格式

### 4.1 Interactions API

见域的 Skill 角色回答、资料决策和联网接地主链路使用 Interactions API。字段采用 `snake_case`：

```json
{
  "model": "gemini-3.8-flash",
  "input": "用户当前问题",
  "system_instruction": "Skill 角色设定与已选择资料",
  "generation_config": {
    "max_output_tokens": 4096,
    "thinking_level": "low",
    "thinking_summaries": "auto"
  },
  "store": true
}
```

要点：

- 端点为 `POST /v1beta/interactions`；
- 3.5～3.8 Flash 均在官方 Interactions 支持模型列表中；
- 思考档位放在 `generation_config.thinking_level`，不是 GenerateContent 的 `thinkingConfig`；
- 3.8 / 3.7 的 `thinking_level` 只能使用 `low / medium / high`；
- `thinking_summaries: "auto"` 只控制思考摘要返回，不改变思考档位；
- 流式请求额外传 `stream: true`，界面只能展示 `model_output` 的文本，不得把 `thought` 或摘要写入用户消息；
- Google Search 使用 `tools: [{"type": "google_search"}]`，3.5～3.8 Flash 均支持；
- `previous_interaction_id` 只继承会话历史，`tools`、`system_instruction` 与 `generation_config` 仍是单次 Interaction 作用域，需要按当前请求重新指定；
- `store=false` 时不能把该 Interaction 作为后续 `previous_interaction_id` 使用；
- `store: true` 与 `previous_interaction_id` 在见域中只能用于同一 Skill 角色的链路，不能让角色 A 的链路成为角色 B 默认回答的上下文。

当前客户端仍携带 `Api-Revision: 2026-05-20`，请求和响应解析均已按 `steps` / `model_output` 工作。官方迁移窗口结束后服务端已默认新版 schema，因此该 Header 不再是 3.5～3.8 Flash 的模型能力依赖；本次不为新增模型顺手清理传输层 Header。

### 4.2 GenerateContent API

会话标题等当前仍使用 GenerateContent API。它与 Interactions 是不同请求结构，不能混用字段：

```json
{
  "contents": [
    {
      "parts": [{ "text": "为这条问题生成简短标题" }]
    }
  ]
}
```

不得把 Interactions 的 `generation_config.thinking_level` 原样发送到 GenerateContent，也不得把 GenerateContent 的 `thinkingConfig.thinkingLevel` 发送到 Interactions。

## 5. 专用模型边界

官方模型目录中的图像、Live、TTS、转写、视频和嵌入模型不属于见域的普通文本对话模型。它们进入 `AiModel` 前必须按各自协议补齐请求、响应、传输实现与测试，不能假设普通文本 Interactions 配置直接兼容。

## 6. 实现与测试规则

1. 新增 Gemini 模型时，先在本文件记录其模型 ID、端点类别、默认值和合法思考档位，再改 `AiModel`；
2. 不得为“极简”保留全局固定值。模型不支持 `minimal` 时，必须在网络请求边界映射到 `low`；
3. 每个新增模型至少测试：模型 ID、模型选择范围、请求 JSON 字段名、每个 UI 档位的合法映射、非法档位被拒绝；
4. 模型选择和请求体编码应能在无真实 API Key 的 JVM 测试中覆盖；
5. 上线前使用用户自己的测试 Key 在目标 Interactions 端点做一次最小请求验证，验证结果不得提交 Key 或完整 Prompt。

## 7. 变更记录

| 日期 | 变化 | 依据 |
|---|---|---|
| 2026-09-26 | 加入 Gemini 3.8 / 3.7 Flash 契约；默认 Gemini 调整为 3.8 Flash；明确 3.8 / 3.7 的“极简”必须归一化为 `low`，并核对 Google Search 与 Interactions 多轮规则。 | 官方 Interactions、Thinking、Google Search、Gemini 3.8 Flash 文档 |
| 2026-09-02 | 初建模型请求与思考档位矩阵；明确 Gemini 3.7 / 3.1 Pro 的“极简”必须使用 `low`，而当时应用内三种 Gemini 文本模型可使用 `minimal`。 | 官方模型总览与 Thinking 文档 |
