# PR09-12G：Gemini 3.6 Flash 与 Interactions API 产品规则冻结

> 状态：已冻结的产品规则；本文件只定义后续实施必须遵守的行为，不包含 Android、Room、Provider 或 UI 的具体实现方案。
>
> 基线：`main@a9eb508a24c0489910446370f267ce24abbbd258`
>
> 官方资料核验日期：2026-08-11

## 1. 目标与范围

见域主文本链路后续以 Gemini Interactions API 为方向，Gemini 3.6 Flash（`gemini-3.6-flash`）是主文本模型的候选目标。本次冻结只处理以下产品规则：

- 议题内思考深度的默认值、自动选择与用户临时覆盖；
- 单次 Interaction、圆桌 / 交叉讨论 Run 的锁定边界；
- Interactions API 的短期连续上下文与隐式缓存边界；
- Room 与服务端 Interaction 的长期事实源分工；
- 每个 Run 的实际模型与思考深度快照。

本文件不把 Gemini 3.6 Flash 设为现有代码的默认模型，不改变任何现有请求，也不冻结模型目录、具体 Provider、模型回退、界面、数据库字段、迁移、费用或配额方案。

## 2. 术语与层次

### 2.1 模型与思考深度分开

“模型选择”与“思考深度”是两个独立概念：

- 模型：例如 `gemini-3.6-flash`；
- 思考深度：Gemini Interactions API 的 `thinking_level`，可为 `minimal`、`low`、`medium` 或 `high`；
- 产品层自动：`AUTO` 不是 API 的 `thinking_level` 值，而是“由见域在本次 Run 开始前解析为一档实际强度”的产品策略。

后续实现不得把显示名称、模型 ID、议题默认策略和某一次 API 请求的实际参数混为同一字段或同一概念。

### 2.2 议题默认值与临时覆盖

每个议题保存一个默认思考策略：

~~~text
AUTO | minimal | low | medium | high
~~~

- 议题的初始默认策略为 `AUTO`，直到用户明确设置一档固定强度；
- 用户可以在非运行中的时点更改议题默认策略，新的默认值只影响之后的新 Run；
- 每轮开始前允许用户设置一次临时覆盖；临时覆盖只影响本轮，不反写议题默认值；
- 已开始或已结束的 Run 不会因默认策略或临时覆盖的后续变化而被追溯修改。

## 3. 思考深度优先级与自动路由

### 3.1 优先级

同一次主文本 Run 的有效思考深度按以下优先级确定：

~~~text
本轮用户临时覆盖
> 议题中用户保存的固定默认值
> AUTO 自动路由
> Gemini 官方默认值
~~~

只要用户选择的是固定档位，系统不得静默改为另一档。系统可以说明风险并建议用户提高强度，但最终仍由用户决定继续、临时提高或取消。

### 3.2 AUTO 的任务粒度

只有议题默认策略为 `AUTO` 且本轮没有用户临时覆盖时，系统才可以按任务选择实际档位：

- `minimal`：短答、简单改写、标题等极轻量主文本任务；
- `low`：简单问答、结构化整理或低复杂度分析；
- `medium`：普通单 Skill 回应和一般圆桌分析；
- `high`：多资料综合、复杂推理、交叉讨论收束和阶段总结。

这是面向主文本输出的产品策略，不代表每个内部辅助任务都必须使用同一档位。标题、分类、路由、资料匹配等辅助任务继续由各自的模型与策略决定；它们不得伪装为用户指定的主文本思考深度。

## 4. Interaction 与 Run 的锁定边界

1. 每次新的主文本 Interaction 在真正发出请求前，必须解析并锁定实际模型与实际 `thinking_level`。
2. 当前 Interaction 已开始流式或非流式运行后，不能在原请求中改变模型或 `thinking_level`。
3. 用户在运行中如需更改，只能继续当前请求，或先停止并在其结束后以新的 Interaction 重新执行；新配置从下一次 Interaction 生效。
4. 即使下一次 Interaction 使用同一个 `previous_interaction_id`，也允许重新指定新的 `generation_config.thinking_level`。它不会自动继承上一轮的生成配置。
5. 普通单 Skill 回应以一次主文本 Interaction 为一个锁定单位。
6. 一次完整的圆桌或交叉讨论 Run 内，主要角色回应与该 Run 的主综合使用同一个已解析的主文本思考深度，保证同一轮结果可比较；下一 Run 可以重新解析或由用户覆盖。
7. 当用户修改议题默认策略时，运行中的 Interaction 仍保持原快照；变化只从下一次新 Interaction 开始生效。

## 5. Interactions API、连续上下文与缓存

### 5.1 主文本链路

主文本链路的目标协议为 Gemini Interactions API。只有用户已显式开启既有“云端会话链优化”并完成其隐私提示确认时，才可以采用：

~~~text
store=true
+ 已完成且仍可用的 previous_interaction_id
~~~

该用户开关未启用或未确认时，请求必须使用 `store=false`，且不得传入 `previous_interaction_id`。启用后的链仅用于短期连续讨论中的服务端上下文、Thought 关联及更高的隐式缓存命中机会；它不是长期恢复机制，也不改变 Room 的事实源地位。每次新 Interaction 仍须显式携带本次需要的工具、系统指令与生成配置；这些参数是 Interaction 级别的，而不是由 `previous_interaction_id` 永久继承。

### 5.2 缓存边界

Interactions API 只使用官方提供的隐式缓存：

- 不设计“显式 Cache 开关”；
- 不创建、管理或承诺显式 Cache 对象；
- 不提供或承诺 Cache TTL；
- 不把缓存命中、成本节省、延迟改善或连续推理效果作为必然结果；
- 可通过稳定的公共前缀、短时间相近请求和有效的 `previous_interaction_id` 提高命中机会，但这不是业务正确性依赖。

缓存改善的是重复上下文的处理效率与连续性机会，不会自行提高模型强度、上下文窗口或推理正确性。

### 5.3 长短期事实源

Interactions 只是短期连续上下文链，不能成为见域的长期议题存储：

- Room 是议题、阶段、资料、成果、消息、Run 及其快照的长期事实源；
- Interaction ID 失效、服务端保存期结束、用户切换设备或服务端不可用，不得改变已保存的见域事实；
- 后续恢复是否新建 Interaction 或重建必要上下文，属于实现阶段的独立决策，但必须以 Room 已保存事实为准。

## 6. Run 不可变快照

每个主文本 Run 在开始时保存不可变的运行快照，至少表达：

~~~text
实际模型 ID
实际 thinking_level
思考深度来源
~~~

其中“思考深度来源”至少区分：

~~~text
ROUND_USER_OVERRIDE    本轮用户临时覆盖
ISSUE_USER_DEFAULT     议题中用户保存的固定默认值
AUTO_ROUTED            系统仅在 AUTO 下按任务解析
~~~

快照记录的是本次真正发送的值，而不是当前设置页或议题随后显示的值。用户之后修改默认策略、应用更新路由规则或可选模型，均不得改写历史 Run 的实际模型、强度和来源。

## 7. 后续实施的固定边界

后续任何实现 PR 都必须遵守：

- 不以升级模型字符串代替模型能力、请求兼容性和运行快照设计；
- 不让自动策略越过用户的固定选择；
- 不在流式生成中伪造“即时切换强度”；
- 不将 `previous_interaction_id`、隐式缓存或服务端保留期作为长期恢复机制；
- 不把 Interactions API 不支持的显式缓存或 TTL 暴露成见域产品能力；
- 不让辅助任务的低成本路由覆盖用户对主文本回答的强度选择；
- 不静默修改历史 Run 的模型、思考深度或来源。

## 8. 官方依据

以下链接用于核对本文件涉及的、会随 Gemini 平台演进的 API 事实；后续实施前必须再次核验：

- [Gemini 3.6 Flash 模型页](https://ai.google.dev/gemini-api/docs/models/gemini-3.6-flash)：模型 ID、支持能力及上下文限制。
- [Interactions API 概览](https://ai.google.dev/gemini-api/docs/interactions-overview)：`store=true`、`previous_interaction_id`、Interaction 级参数与服务端状态管理。
- [Gemini Thinking（Interactions API）](https://ai.google.dev/gemini-api/docs/thinking)：Gemini 3.6 Flash 支持的 `minimal / low / medium / high`。
- [Context caching（Interactions API）](https://ai.google.dev/gemini-api/docs/caching)：Interactions API 只支持隐式缓存，不支持显式 Cache 对象。

## 9. 非目标

本冻结文档不授权下列事项：

- 修改 Android 生产代码、Room Schema、Migration、测试或现有 Provider；
- 立即迁移主文本模型、辅助模型、Live / TTS、Embedding 或联网搜索模型；
- 增加模型选择页、默认模型迁移、跨模型回退或价格策略；
- 引入显式缓存、TTL、服务端代理或新的隐私模式；
- 创建或修改长期持久化字段的具体实现。

这些事项必须在后续独立实施 PR 中重新读取本文件、官方文档、最新 `main` 与开放 PR 后决定。
