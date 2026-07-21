# 隐私、遥测与 API 诊断技术说明

本文说明 AI Skill Roundtable 当前的 API Key 诊断、本地遥测、云端 Interaction 存储和调试日志边界。代码行为以 PR02 的串行圆桌与 Key Lease 编排为基础，并由 PR03 增加隐私加固。

## 1. API Key 与请求诊断

### 1.1 BYOK 与本地密钥存储

- 项目不内置生产 API Key；用户通过本地 BYOK 方式导入。
- 完整 Key 使用 Android Keystore 派生密钥进行 AES-GCM 加密，密文写入 `noBackupFilesDir`。
- UI、遥测和日志只允许出现内部 Key ID 或显示名，禁止记录完整 Key。
- Key 校验、禁用、冷却和会话绑定状态仍由 `ApiKeyPool` 管理，遥测事件不再写入 Key Preferences。

### 1.2 确定性重试与预算

- 圆桌角色严格串行执行，不使用随机延迟。
- HTTP 5xx 的同 Key 重试退避固定为 1 秒、2 秒。
- `Retry-After` 支持秒数解析。
- REQUIRED 与 OPTIONAL 请求使用原子预算预留；OPTIONAL 请求不得消耗为后续主回答保留的额度。
- 当前问题剩余额度不足以完成整轮时，不启动半轮回答。

## 2. 本地遥测级别

### 2.1 `OFF`

- 不创建本地遥测事件。
- 不读取请求正文。
- 不读取或 `peek` 响应正文。

### 2.2 `METADATA_ONLY`（默认）

默认只保存：

- 时间与耗时；
- HTTP 方法和不含 query 的端点路径；
- 模型名；
- 内部 Key ID；
- HTTP 状态码和稳定错误分类；
- 可用时的重试或 Token 元数据。

默认不保存：

- 用户问题、system instruction、Skill 正文；
- 附件正文或 Base64 解码内容；
- 模型完整回复；
- 搜索词、搜索结果正文或 Thought Summary；
- 完整 Interaction ID；
- URL query、Authorization 或完整异常消息。

### 2.3 `CONTENT_DEBUG`

- 仅 Debug 构建可用。
- 必须由用户在隐私警告后显式开启。
- 最长 24 小时，到期自动降回 `METADATA_ONLY` 并删除已有正文预览。
- 请求体最多读取 16 KiB，响应最多 `peek` 32 KiB。
- 请求和响应预览各最多 2,000 字符，并再次经过统一脱敏。
- 附件只显示 MIME 类型和编码长度，不解码正文。
- Interaction ID 只保留掩码；Thought Summary、签名和搜索正文直接省略。

## 3. 保留与本地存储

- 遥测设置存储在 `telemetry_settings`。
- 遥测事件存储在 `roundtable_telemetry_prefs`。
- 云端 Interaction 开关存储在 `roundtable_privacy_settings`。
- 三者均从 Android 自动备份和设备迁移中排除。
- Metadata 最长保留 7 天且最多 100 条。
- Content Debug 预览最长保留 24 小时且最多 20 条。
- 启动、读取和写入时都会执行裁剪；过期事件会同步从持久化存储删除。
- 旧 `gemini_api_key_prefs/telemetry_api_logs_json` 会在首次迁移时删除；删除失败时保留迁移未完成状态，下次启动重试。
- 诊断页展示当前级别、到期时间、事件数和估算占用，并支持一键清空。

## 4. 云端 Interaction 存储

- 默认强制 `store=false`。
- 用户显式开启“云端会话链优化”后，调用方明确请求 `store=true` 时才允许持久化 Interaction。
- `previousInteractionId` 只有在同一请求实际允许 `store=true` 时才会发送。
- 主回答仍以 `previousInteractionId=null` 开始；不恢复多角色共享 Interaction ID。
- 关闭云端会话链不会阻止普通 Gemini 请求发送，只是不额外启用持久化 Interaction 链。
- 服务商侧保留和删除受其政策约束，本应用无法保证远端删除。

## 5. Logcat 边界

- Release 构建的 OkHttp 日志级别为 `NONE`。
- Debug 构建默认只启用 OkHttp `BASIC`，不打印 BODY。
- 应用内操作日志统一通过 Debug-only 的 `PrivacySafeLogger` 输出，并在写入 Logcat 前脱敏和截断。
- Broker 原文、搜索词、模型回复、文件路径、完整异常消息和 Throwable 堆栈不进入操作日志。
- 即使开启 `CONTENT_DEBUG`，正文预览也只写入受保留策略控制的本地遥测仓库，不写入全局 Logcat。

## 6. 音频与导出数据

- Gemini Live 返回的 PCM 音频先写入应用缓存 WAV，再由 WorkManager 转为 AAC；日志不记录本地文件路径。
- 用户主动导出的 Markdown 会包含其会话正文，这是显式导出行为，不属于遥测。
- 普通聊天数据库整体加密不在 PR03 范围内，后续安全 PR 可单独评估。
