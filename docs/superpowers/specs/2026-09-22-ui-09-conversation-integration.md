# UI-09：对话、资料与成果统一闭环

## 目标

把首页的持续对话入口与正式 Room 领域统一，避免聊天消息、资料和成果各自停留在临时兼容路径。用户在对话中明确选择的资料/个人背景只进入本次模型请求；角色之间仍保持独立，不默认继承其他角色生成内容。

## 正式关系

- 每个首页对话建立稳定的 `dialog-session-{sessionId}` 议题和 `dialog-node-{sessionId}` 对话节点，并通过 `legacyChatSessionId` 保留会话容器关系。
- 新写入和既有消息均补齐 `issueId/stageId`；消息来源可被成果表反查。
- “保存为成果”写入 `confirmed_artifacts` 与 `artifact_message_sources`，重复点击按 Repository 幂等规则处理。
- “整理为成果”保存完整 Markdown 会话，并保留全部已完成消息作为来源。

## 对话内资料动作

- “选择资料”显示资料/个人背景候选、摘录编辑、单次联网许可和敏感内容二次确认；未确认的内容不发送给模型。
- “本次参考内容”只展示本次明确选择，不把其他角色的输出当作默认上下文。
- “添加文件”通过 SAF 读取文本并保存为当前对话的正式资料；不可读取的二进制文件不会伪造成功。

## 验证

- `compileDebugKotlin`、`testDebugUnitTest`、`compileDebugAndroidTestSources`。
- `lintDebug`、`assembleDebug`、`assembleDebugAndroidTest`。
- 设备验收放在全部阶段完成后：创建对话、发送/停止回复、选择资料、保存成果、打开资料页反查来源、备份导入和删除确认。
