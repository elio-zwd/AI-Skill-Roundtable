# PR05-B：Interactions SSE 流式回复

## 目标

让圆桌主回答在模型生成过程中逐步显示，而不是等待完整响应后一次性出现。

## 范围

- 主回答使用 Interactions API SSE。
- 自动“请继续”请求同样使用 SSE，并在现有正文后追加增量。
- Broker、本地资料选择、联网搜索与 Embedding 继续使用原有非流式请求。
- 保留 PR05-A 的单角色 120 秒超时、整轮 8 分钟超时、主动取消和 Pending 清理。
- 保留既有 API Key 调度、预算与重试策略。
- 不修改 Room Schema 或数据库版本。

## 事件处理

按照 `Api-Revision: 2026-05-20`：

1. `interaction.created`：记录 interaction id。
2. `step.start`：只追踪 `model_output` 步骤索引。
3. `step.delta`：只拼接对应 `model_output` 的 `text` 增量。
4. `step.stop`：立即刷新当前累计文本。
5. `interaction.completed`：确认流完整结束并刷新最终文本。

`thought`、工具调用及其他内部步骤不会写入用户聊天记录。

## Pending 生命周期

1. 角色开始时插入 Pending 消息，并显示原有思考指示器。
2. 收到第一段模型文本后，更新同一条 Pending 消息的正文。
3. Compose 允许显示已有正文的 Pending 消息，同时隐藏重复的思考气泡和 TTS 按钮。
4. 正常完成后，编排器删除 Pending 并写入正式消息。
5. 取消、超时、网络失败或 App 异常重启时，继续沿用 PR05-A 的 Pending 清理逻辑。

## 重试语义

- 每次网络尝试开始前，将当前 Pending 恢复为“正在思考中...”。
- 如果某次流在输出一部分内容后失败，下一次尝试不会接在旧内容后面拼接。
- 请求预算的消耗方式与非流式执行引擎一致。
- `CancellationException` 直接向上传播，不会触发重试或换 Key。

## UI 写入节流

为避免每个 token 都触发 Room 写入和 Compose 重组，满足以下任一条件才更新界面：

- 距离上次更新至少约 75 ms；
- 文本新增至少 64 个字符；
- 当前步骤结束。

流完成时一定写入最终完整文本。

## 自动测试

- SSE 累加器只接收 `model_output` 文本，忽略 thought 文本。
- 多个 model output 步骤按顺序拼接。
- `interaction.failed` 被视为失败。
- 编排器在最终正式消息写入前持续更新 Pending 文本。

## CI 诊断记录

首次门禁在 `compileDebugUnitTestKotlin` 阶段发现新增回归测试残留补丁标记，主源码编译未受影响。该标记已经删除，并补充了受控诊断流程验证测试源码编译路径；所有临时诊断工作流、报告文件和 Gradle Wrapper 文件模式变化均未保留在最终差异中。

## 明确不包含

- 失败角色原位重试按钮。
- HTTP/网络/配额错误类型细分 UI。
- 并行角色生成。
- 流式展示 thought 或工具调用过程。
- Room Schema 变更。
