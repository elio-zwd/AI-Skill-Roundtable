# Gemini 2.5 联网默认模型与模型目录扩展设计

## 背景

当前分支已经把 Gemini 3.1～3.8 文本调用统一到 Interactions API，并允许支持 Google Search 的 Gemini 模型用于 `WEB_GROUNDING`。用户要求保留现有 3.x 联网能力，同时新增 `gemini-2.5-flash`、`gemini-2.5-flash-lite` 和 `gemini-3.5-flash-lite`，并将“联网检索”的默认模型调整为 `gemini-2.5-flash`。

2026-09-26 官方 Interactions 文档已经将 `gemini-2.5-flash`、`gemini-2.5-flash-lite` 和 `gemini-3.5-flash-lite` 列为支持模型，因此本次不新增第二套 Gemini 协议链路。

## 设计决策

1. **继续使用单一 Interactions 主链路。** Skill 角色回答、资料决策和联网接地继续构造 `CreateInteractionRequest`；不为 2.5 新增 GenerateContent 专用分支。
2. **保留现有联网能力。** 3.x 现有模型的 `supportsWebGrounding` 不删除、不降级；新增三个模型也按官方能力开放 Google Search。
3. **按用途定义默认模型。**
   - Gemini 通用默认模型仍为 `gemini-3.8-flash`。
   - `AiUseCase.WEB_GROUNDING` 默认模型改为 `gemini-2.5-flash`。
   - 用户已经保存的明确模型选择继续优先，不因默认值变化被覆盖。
4. **思考档位按模型归一化。**
   - 3.8 / 3.7 / 2.5 Flash / 2.5 Flash-Lite 不接收应用统一档位 `minimal`，在 Interactions 请求边界映射为 `low`。
   - 3.6 / 3.5 Flash / 3.5 Flash-Lite / 3.1 Flash-Lite 保留 `minimal`。
5. **联网资料仍与最终回答解耦。** `WEB_GROUNDING` 先产生独立检索摘要，再作为补充上下文交给最终回答模型；最终回答模型可以继续是 Gemini 或 DeepSeek。本次不扩展新的结果实体或缓存层。
6. **新增模型必须可访问。** 模型选择 BottomSheet 的模型数量由 5 个增加到 8 个后需要支持纵向滚动，避免小屏设备无法选择位于底部的 2.5 模型。

## 模型目录

新增：

- `gemini-3.5-flash-lite`
- `gemini-2.5-flash`
- `gemini-2.5-flash-lite`

Gemini 模型展示顺序保持按代际由新到旧；2.5 模型位于现有 3.x 模型之后。

## 错误与兼容性

- Google 当前限制部分未曾活跃使用 2.5 的账号访问 2.5 系列。见域仍按用户要求把 2.5 Flash 作为联网默认；真实 Key 无访问权限时沿用现有请求错误与 Key 重试机制，不静默切换模型。
- 不修改 Room Schema，不迁移已保存的模型枚举名；没有保存值的用途才使用新的默认值。
- 会话标题目前仍可继续使用现有 GenerateContent 实现，本次不做无关协议迁移。

## 验证

- JVM：模型目录、通用默认、联网用途默认、思考档位映射、Interactions JSON + Google Search 工具。
- AndroidTest：AI 管理模型选择页能看到新增模型，联网检索用途能看到 2.5 Flash / Flash-Lite。
- 本地真实 Key：至少验证一次 `gemini-2.5-flash` 的 Interactions + `google_search`；若账号无 2.5 权限，记录服务端错误，不修改代码绕过。
