# Gemini 模型请求与思考档位契约

> 文档状态：2026-09-02 在线核验
>
> 适用范围：见域的 Gemini 文本对话、资料决策、联网接地和会话标题请求。语音、图像、视频等专用模型另有协议，不能把本文的文本请求体直接套用到它们。
>
> 核验来源：[Gemini 模型总览](https://ai.google.dev/gemini-api/docs/models)、[Gemini Thinking](https://ai.google.dev/gemini-api/docs/generate-content/thinking)、[Interactions with thinking](https://ai.google.dev/gemini-api/docs/thought-signatures)。模型能力会变化；新增或替换模型前必须重新核验上述官方文档。

## 1. 目的

Gemini 的“思考档位”不是所有模型通用的四选一字符串：

- `gemini-3.7-flash` 不支持 `minimal`，传入会返回错误，极简请求必须使用 `low`；
- Gemini 3.6 / 3.5 Flash、Gemini 3.5 / 3.1 Flash-Lite 与 Gemini 3 Flash 支持 `minimal`；
- Gemini 3.1 Pro 不支持 `minimal`；
- Gemini 2.5 系列不使用 `thinkingLevel`，而使用数值 `thinkingBudget`。

因此，调用方不得仅按 UI 文案把“极简”固定映射成同一个 API 值，必须先按模型能力选择合法参数。

## 2. 当前应用已配置的 Gemini 文本模型

`app/src/main/java/com/elio/jianyu/network/AiProvider.kt` 当前只允许下列 Gemini 模型进入文本模型选择。它们均可使用 Gemini 3 的 `thinkingLevel` 格式。

| 模型 ID | 默认思考档位 | 合法 `thinkingLevel` | “极简”请求值 | 当前用途 |
|---|---:|---|---|---|
| `gemini-3.6-flash` | `medium` | `minimal`、`low`、`medium`、`high` | `minimal` | 对话、资料决策、联网接地、标题 |
| `gemini-3.5-flash` | `medium` | `minimal`、`low`、`medium`、`high` | `minimal` | 对话、资料决策、联网接地、标题 |
| `gemini-3.1-flash-lite` | `minimal` | `minimal`、`low`、`medium`、`high` | `minimal` | 对话、资料决策、联网接地、标题 |

当前 `RoundtableViewModel` 只会对这三种模型把“极简”映射为 `minimal`，这是合法的。该结论不能自动推广给将来加入的模型。

## 3. 可作为后续文本候选的 Gemini 3 模型

下表是截至本文核验日，官方模型目录中与文本生成/Interactions 相关的其他 Gemini 3 模型。它们尚未加入应用的 `AiModel` 枚举。

| 模型 ID | 默认思考档位 | 合法 `thinkingLevel` | “极简”请求值 | 加入应用前的要求 |
|---|---:|---|---|---|
| `gemini-3.7-flash` | `medium` | `low`、`medium`、`high` | `low` | 必须增加模型级能力测试；禁止传 `minimal` |
| `gemini-3.5-flash-lite` | `minimal` | `minimal`、`low`、`medium`、`high` | `minimal` | 核验成本与使用场景后再开放 |
| `gemini-3.1-pro-preview` | `high` | `low`、`medium`、`high` | `low` | Preview 模型需额外评估稳定性 |
| `gemini-3-flash-preview` | `high` | `minimal`、`low`、`medium`、`high` | `minimal` | Preview 模型需额外评估稳定性 |

`minimal` 表示大多数简单请求几乎不进行思考，不等于绝对关闭思考；Gemini 3 文本模型仍会按任务复杂度进行最低限度的推理。

## 4. 请求体格式

### 4.1 Interactions API

见域对话、资料决策和联网接地的主链路使用 Interactions API。其字段为 `snake_case`：

```json
{
  "model": "gemini-3.5-flash",
  "input": "用户当前问题",
  "system_instruction": "Skill 角色设定与已选择资料",
  "generation_config": {
    "max_output_tokens": 4096,
    "thinking_level": "minimal",
    "thinking_summaries": "auto"
  },
  "store": true
}
```

要点：

- 端点为 `POST /v1beta/interactions`；
- 思考档位放在 `generation_config.thinking_level`，不是 `thinkingConfig`；
- `thinking_summaries: "auto"` 只控制思考摘要是否返回，不改变思考档位；
- 流式请求额外传 `stream: true`，界面只能展示 `model_output` 的 `text`，不得把 `thought` 或摘要写入用户消息；
- `store: true` 与 `previous_interaction_id` 仅可用于同一 Skill 角色的链路，不能让角色 A 的链路成为角色 B 默认回答的上下文。

### 4.2 GenerateContent API

会话标题等仍使用 GenerateContent API。其字段为 `camelCase`：

```json
{
  "contents": [
    {
      "parts": [{ "text": "为这条问题生成简短标题" }]
    }
  ],
  "generationConfig": {
    "maxOutputTokens": 40,
    "thinkingConfig": {
      "thinkingLevel": "minimal"
    }
  }
}
```

不得把 Interactions 的 `generation_config.thinking_level` 原样发送到 GenerateContent，也不得把 GenerateContent 的 `thinkingConfig.thinkingLevel` 发送到 Interactions。

### 4.3 Gemini 2.5 系列

Gemini 2.5 系列不支持 `thinkingLevel`。必须使用 `thinkingBudget`：

```json
{
  "generationConfig": {
    "thinkingConfig": {
      "thinkingBudget": 1024
    }
  }
}
```

| 模型 | 请求字段 | 范围与特殊值 |
|---|---|---|
| `gemini-2.5-pro` | `thinkingBudget` | `128`～`32768`；不能关闭；`-1` 为动态思考 |
| `gemini-2.5-flash` | `thinkingBudget` | `0`～`24576`；`0` 关闭；`-1` 为动态思考 |
| `gemini-2.5-flash-lite` | `thinkingBudget` | `0` 或 `512`～`24576`；`0` 关闭；`-1` 为动态思考 |

Gemini 2.5 目前不是见域的可选文本模型。若要加入，必须单独实现模型协议分派，不能复用当前 `InteractionGenerationConfig.thinkingLevel`。

## 5. 专用模型边界

截至本文核验日，官方模型总览还列出下列 Gemini 专用模型。它们不属于见域的普通文本对话模型；表中“无”表示不得假设可以传普通文本对话的 `thinkingLevel`，而非断言该模型永远没有内部推理能力。

| 模型 ID | 协议类别 | 普通文本 `thinkingLevel` | 进入 `AiModel` 前必须完成 |
|---|---|---|---|
| `gemini-3.1-flash-image` | 图像生成/编辑 | 无 | 图像请求与响应解析 |
| `gemini-3.1-flash-lite-image` | 图像生成/编辑 | 仅 `minimal`、`high` | 图像请求与响应解析，禁止假设支持 `low`、`medium` |
| `gemini-3-pro-image` | 图像生成/编辑 | 无 | 图像请求与响应解析 |
| `gemini-3.5-live-translate-preview` | Live 音频翻译 | 无 | Live WebSocket setup 与音频帧解析 |
| `gemini-3.1-flash-live-preview` | Live 实时音频 | 无 | Live WebSocket setup 与音频帧解析 |
| `gemini-3.1-flash-tts-preview` | 文本转语音 | 无 | TTS setup、PCM/WAV 处理与设备验收 |
| `gemini-3.5-transcribe` | 音频转写 | 无 | 转写输入、分段与时间戳解析 |
| `gemini-3.5-transcribe-live` | 实时音频转写 | 无 | 实时转写协议与断线恢复 |
| `gemini-omni-1.1-flash` | 视频生成/编辑 | 无 | 视频请求、轮询和资源生命周期 |
| `gemini-2.5-flash-image` | 图像生成/编辑 | 无 | 图像请求与响应解析 |
| `gemini-2.5-flash-native-audio-preview-12-2025` | Live 实时音频 | 无 | Live WebSocket setup 与音频帧解析 |
| `gemini-2.5-flash-preview-tts` | 文本转语音 | 无 | TTS setup、PCM/WAV 处理与设备验收 |
| `gemini-2.5-pro-preview-tts` | 文本转语音 | 无 | TTS setup、PCM/WAV 处理与设备验收 |
| `gemini-embedding-001` | 向量嵌入 | 无 | `embedContent` 维度与相似度回归测试 |

Live 与 TTS 使用实时音频协议和模型专属 setup，不能发送普通 Interactions 文本请求体；Transcribe 的输出是转写，也不得作为 Skill 角色文本回答模型。任何专用模型进入 `AiModel` 前，必须新增对应协议文档、传输实现和网络契约测试。

## 6. 实现与测试规则

1. 新增 Gemini 模型时，先在本文件增加其模型 ID、端点类别、默认值和合法思考档位，再改 `AiModel`；
2. 不得为“极简”保留全局固定值。模型不支持 `minimal` 时，映射到 `low`；
3. 每个新增模型至少测试：请求 JSON 字段名、每个 UI 档位的合法映射、非法档位被拒绝；
4. 模型选择、请求体编码和失败重试必须在无真实 API Key 的 JVM 测试中覆盖；
5. 上线前使用用户自己的测试 Key 在目标端点做一次最小请求验证，验证结果不得提交 Key 或完整 Prompt。

## 7. 变更记录

| 日期 | 变化 | 依据 |
|---|---|---|
| 2026-09-02 | 初建模型请求与思考档位矩阵；明确 Gemini 3.7 / 3.1 Pro 的“极简”必须使用 `low`，而当前应用的三种 Gemini 文本模型可使用 `minimal`。 | 官方模型总览与 Thinking 文档 |
