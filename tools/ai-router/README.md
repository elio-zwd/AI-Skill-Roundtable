# 本地 AI Router（MVP）

> **状态：当前不使用（保留历史实验参考）。**
>
> 本目录不属于见域当前正式开发/验收流程，也不是“网页版 GPT ↔ 本地 AI”的现行协作通道。
> 除非未来有新的明确设计、评审和启用授权，否则不要启动 `router.py`、不要配置真实 Drive/OAuth/ChatGPT 浏览器发送，也不要依赖本目录完成任务交接。
> 当前项目继续采用人工/显式 Prompt 方式让本地 AI 执行构建、Instrumentation、真机/UI 验收。

这是独立于 Android 产品代码的本地编排工具。它只做：

```text
Drive files.get(id, fields=id,modifiedTime)
  → 新 PENDING event
  → 按 to 字段选固定收件人
  → 发送文本 + Drive 链接，或写入 Local AI request
  → 本地 cursor 记录结果
```

它不判断任务、PASS/FAIL 或下一步；不修改 Drive 控制文件、生产代码、Git、`TASK-REGISTRY`、`CURRENT-STATE` 或 `RELAY-CONTROL`；也不会从 ChatGPT 历史页面推断状态。

## 控制文件协议

控制文件必须是普通的、上传到 Drive 的 UTF-8 `.json` 文件，不是 Google Docs。Watcher 只在 `modifiedTime` 变化时用 `alt=media` 下载其小型 JSON 内容。

```json
{
  "protocol_version": 1,
  "event_id": "TEST-0001",
  "status": "PENDING",
  "from": "PLANNER",
  "to": "ENGINEER",
  "type": "TEST",
  "message": "中控连接测试 TEST-0001",
  "drive_url": "https://drive.google.com/",
  "conversation_url": "USE_CONFIG",
  "created_at": "2026-09-13T00:00:00Z"
}
```

`from` / `to` 只能是 `PLANNER`、`ENGINEER`、`LOCAL_AI`；核心状态只能是 `PENDING`、`CLAIMED`、`SENT`、`ACKED`、`BLOCKED`。`event_id` 必须唯一。`conversation_url` 仅作审计字段，永远不会覆盖本机的固定对话 URL。

Router 不会回写控制文件状态：Drive 是单事件输入，`work/ai-router/cursor.json` 是本地执行账本。

## 配置

运行态文件统一位于（且已被仓库忽略）：

```text
work/ai-router/config.json
work/ai-router/cursor.json
work/ai-router/router.log
work/ai-router/local-ai-request.md
```

初始化模板：

```powershell
python .\tools\ai-router\router.py init-config
```

编辑 `work/ai-router/config.json`。同名环境变量可覆盖 JSON 配置。必需字段：

| 字段 | 用途 |
| --- | --- |
| `GOOGLE_DRIVE_FILE_ID` | 原始 JSON 控制文件的 Drive file ID |
| `GOOGLE_CREDENTIALS_PATH` | OAuth token 文件路径 |
| `PLANNER_CONVERSATION_URL` | Planner 的唯一长期 ChatGPT 对话 URL |
| `ENGINEER_CONVERSATION_URL` | Engineer 的唯一长期 ChatGPT 对话 URL |
| `POLL_INTERVAL_SECONDS` | 默认 15 秒，最小 5 秒 |
| `CHATGPT_PROJECT_URL` | 已保留的项目 URL 配置，不参与路由 |

如果使用 `@modelcontextprotocol/server-gdrive` 生成的 token 格式，还需要 `GOOGLE_OAUTH_CLIENT_SECRETS_PATH` 指向对应 `gcp-oauth.keys.json`，以便 `google-auth` 用 refresh token 自动续期。两个文件均不得放入仓库或日志。

初始设置中保持：

```json
"ENABLE_BROWSER_SEND": false
```

只有完成 Phase 2 测试并明确打开该开关后，Router 才允许向网页 ChatGPT 发送。

## 启动与验收命令

```powershell
# 只检查本机文件、依赖与配置，不访问 Drive。
python .\tools\ai-router\router.py doctor

# Phase 1：读取一次 Drive metadata；若变更，下载并打印 event_id / to / message / drive_url。
python .\tools\ai-router\router.py observe

# 正常运行一次。ENGINEER/PLANNER 在发送开关关闭时会安全地记录 BLOCKED。
python .\tools\ai-router\router.py once

# 常驻 watcher；无 modifiedTime 变化时不下载内容、不输出事件、不路由。
python .\tools\ai-router\router.py watch

# 离线回归测试。
python .\tools\ai-router\test_router.py
```

## 幂等与异常处理

发送前先原子写入本地 `CLAIMED`。只有以下两项都成立才记为 `SENT`：输入框已清空，且已观察到 ChatGPT 的“正在生成 / Stop”控件。浏览器崩溃、网络错误或确认不充分都记为 `BLOCKED`，相同 `event_id` 不会自动重发；即使 Drive 文件再次更新，仍返回 `NOOP_EVENT_*_REQUIRES_RECONCILIATION`。

同一个 `event_id` 出现不同 payload hash 时，Router 记为 `BLOCKED_EVENT_ID_CONFLICT`。人工已有送达证据时才可显式对账：

```powershell
python .\tools\ai-router\router.py reconcile `
  --event-id EVT-000001 `
  --state SENT `
  --result MANUALLY_CONFIRMED_SENT
```

## ChatGPT 浏览器边界

浏览器只通过 loopback CDP（默认 `http://127.0.0.1:9222`）连接用户已登录的 Chromium。它只会打开或复用配置中的精确 URL，定位输入框、发送按钮和 Stop 状态；不会调用 `page.content()`、消息 `innerText`、对话 turn 选择器、OCR 或截图。因此不会读取 ChatGPT 历史聊天正文。

若未登录、输入框找不到、对话仍在生成，或发送后没有可靠确认，事件不会标记 `SENT`。

## Local AI 路由

`LOCAL_AI` 当前有意采用可靠的文件交接：写入 `work/ai-router/local-ai-request.md`，并记录 `LOCAL_AI_TRIGGER_NOT_IMPLEMENTED` / `BLOCKED`。它不会伪装为已经触发 Codex，也不会启动一个新的工程师对话。已有未处理 request 文件时不会覆盖。

本机 `codex exec resume` 确实支持指定 session ID 或 `--last`，但它不能可靠地标识和唤醒当前桌面 Codex 长期任务；第一版因此不自动调用它。待明确提供稳定的 Local AI session 标识和触发契约后，再把该文件交接替换为显式的本地触发器。
