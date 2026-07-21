# PR 03：隐私、遥测与数据安全收口

> 状态：Planned  
> 优先级：P0  
> 前置依赖：PR 02 已合并，新的网络调用链和 Key Lease 已稳定  
> 后续依赖：PR 04、PR 05  
> 覆盖审查项：F04、F12（遥测职责拆分）

---

## 1. 任务目标

在保留调试能力的同时，建立明确的数据最小化规则：

1. 默认运行不保存完整 Prompt、system instruction、附件正文或模型回复。
2. Release 构建不把敏感正文写入 Logcat。
3. 调试正文日志必须由用户显式开启，并自动过期。
4. 遥测数据与 API Key 状态分离存储。
5. 用户能查看当前遥测级别、保留期限和占用量，并一键清空。
6. 云端 Interaction 的 `store` 行为有明确默认值、设置入口和隐私说明。
7. 遥测逻辑从 `GeminiApi.kt` 中拆出，能够单独测试。

---

## 2. 固定隐私决策

为避免执行 AI 自行选择，本 PR 固定采用以下默认值：

### 2.1 默认遥测级别

```text
METADATA_ONLY
```

默认只允许记录：

- 请求时间。
- 响应时间或耗时。
- 模型/端点名称。
- HTTP 状态码。
- 错误分类。
- Key 的内部 ID 或显示名。
- Token 使用量（API 返回时）。
- 是否发生重试、重试次数。

默认不得记录：

- 用户完整问题。
- system instruction。
- Skill 参考正文。
- Base64 附件解码内容。
- 模型完整回复。
- 搜索结果正文。
- Thought Summary。
- Interaction ID 完整值。
- 完整 URL query。

### 2.2 内容调试级别

提供：

```text
CONTENT_DEBUG
```

但必须满足：

- 用户手动开启。
- 开启前展示隐私警告。
- 最长自动保留 24 小时。
- 单条请求和响应均截断。
- 执行脱敏。
- Release 构建默认不可开启；如确需支持，必须再次确认并由 BuildConfig 明确控制。本 PR 默认 Release 禁用。

### 2.3 云端会话存储

默认：

```text
store = false
```

只有用户显式开启“云端会话链优化”后才允许 `store=true`。UI 文案必须说明：开启后会把请求上下文交给服务商用于维持 Interaction 链，具体保留受服务商政策约束。

PR 02 应已支持不依赖共享云端链的严格顺序圆桌；本 PR 不得为了保留旧链而牺牲默认隐私。

---

## 3. 本 PR 明确不做什么

- 不改变圆桌顺序和 Key 重试策略。
- 不修改 Release 包名和签名。
- 不实现账号系统或远程日志上传。
- 不上传遥测到自己的服务器。
- 不记录完整 Key。
- 不实现复杂的全文搜索型日志数据库。
- 不把普通聊天数据库整体加密重构纳入本 PR；可以记录为后续安全任务。

---

## 4. 执行 AI 必须先读的文件

1. `AGENTS.md`
2. `docs/planning/pr-execution-master-plan.md`
3. 本文件
4. PR 02 新增的网络调用、Retry、Key Lease 类
5. `app/src/main/java/com/example/skillroundtable/network/GeminiApi.kt`
6. `app/src/main/java/com/example/skillroundtable/network/ApiKeyPool.kt`
7. `app/src/main/java/com/example/skillroundtable/network/ApiKeyModels.kt`
8. `app/src/main/java/com/example/skillroundtable/ApiTelemetryScreen.kt` 或实际遥测 UI 文件
9. `app/src/main/java/com/example/skillroundtable/ApiKeyManagerScreen.kt`
10. `app/src/main/res/xml/backup_rules.xml`
11. `app/src/main/res/xml/data_extraction_rules.xml`
12. `AndroidManifest.xml`
13. `README.md` 隐私、Key 和遥测说明
14. `docs/features/debug-panel-and-telemetry-diagnostics.md`
15. `docs/architecture/system-architecture.md`

搜索全部敏感日志入口：

```powershell
git grep -n -I 'HttpLoggingInterceptor\|TelemetryInterceptor\|ApiLog\|apiLogs\|telemetry_api_logs_json\|prompt =\|responseText =\|system_instruction\|Thought Summary\|peekBody\|Log\.d\|Log\.w\|Log\.e'
```

---

## 5. 数据分类

新增文档或代码注释时统一采用：

| 等级 | 示例 | 默认是否可持久化 |
|---|---|---|
| PUBLIC | 模型名、状态码、耗时 | 是 |
| OPERATIONAL | Key ID、重试次数、Token 数 | 是，但不得含 secret |
| SENSITIVE | 用户问题、模型回复、搜索词、Interaction ID | 默认否 |
| HIGHLY_SENSITIVE | API Key、私钥、附件完整正文 | 永不进入遥测 |

执行 AI 必须在代码中体现这一边界，而不是只写文档。

---

## 6. 推荐目标结构

建议新增：

```text
app/src/main/java/com/example/skillroundtable/telemetry/
├── TelemetryLevel.kt
├── TelemetryEvent.kt
├── TelemetryRepository.kt
├── TelemetryRedactor.kt
├── TelemetryRetentionPolicy.kt
└── TelemetryInterceptor.kt
```

建议将旧 `ApiLog` 重命名或替换为：

```kotlin
@Serializable
data class TelemetryEvent(
    val id: String,
    val timestamp: Long,
    val durationMs: Long,
    val endpoint: String,
    val model: String?,
    val keyId: String?,
    val statusCode: Int?,
    val failureType: String?,
    val retryCount: Int,
    val inputTokens: Int? = null,
    val outputTokens: Int? = null,
    val requestPreview: String? = null,
    val responsePreview: String? = null,
    val expiresAt: Long? = null
)
```

`requestPreview` 和 `responsePreview` 只能在 `CONTENT_DEBUG` 下存在。

---

## 7. 详细实施步骤

### 7.1 定义遥测级别

```kotlin
enum class TelemetryLevel {
    OFF,
    METADATA_ONLY,
    CONTENT_DEBUG
}
```

默认值：

```kotlin
TelemetryLevel.METADATA_ONLY
```

持久化设置使用单独 Preferences，例如：

```text
telemetry_settings
```

Key 状态继续使用自己的存储，但不能再把遥测事件写入 `gemini_api_key_prefs`。

设置字段建议：

```text
telemetry_level
content_debug_enabled_at
content_debug_expires_at
retention_hours
```

读取时必须校验过期时间；一旦过期，自动降回 `METADATA_ONLY` 并清除正文预览。

---

### 7.2 重构 HttpLoggingInterceptor

现状 Debug 使用 BODY 级别，即使 Key query 被替换，仍会打印请求/响应正文。

固定策略：

- Release：`Level.NONE`
- Debug 默认：`Level.BASIC` 或 `HEADERS`，但必须确保 URL query 中 Key 被完整遮蔽。
- 不使用 BODY 作为默认级别。
- 即使 `CONTENT_DEBUG` 开启，也优先通过受控 Telemetry Repository 保存截断预览，而不是把 BODY 输出到全局 Logcat。

建议：

```kotlin
level = if (BuildConfig.DEBUG) {
    HttpLoggingInterceptor.Level.BASIC
} else {
    HttpLoggingInterceptor.Level.NONE
}
```

URL 脱敏必须覆盖：

- `?key=`
- `&key=`
- `Authorization` Header（若未来加入）
- WebSocket URL

不得仅依赖一个正则后就记录整条原始 URL；最好在日志层重建不含 query secret 的 URL。

---

### 7.3 改造 TelemetryInterceptor

旧逻辑会：

- 读取完整 request body。
- 解码 Base64 附件。
- 提取 system instruction。
- 保存用户内容。
- peek 最多 512 KB 响应。
- 保存完整回复和 Thought Summary。

新逻辑：

#### OFF

- 不创建事件。
- 不读取 request body。
- 不 peek response body。
- 只继续请求。

#### METADATA_ONLY

- 记录端点、模型、状态码、耗时、错误类型、Key ID、重试、Token。
- 模型名优先从调用参数或 request tag 获取，不为提取模型而解析完整正文。
- 不调用 `requestBody.writeTo(buffer)`。
- 不调用 `peekBody()`。

#### CONTENT_DEBUG

只有在 Debug 构建且未过期时：

- 允许读取请求体，但最多读取固定字节数，例如 16 KB。
- 不解码 Base64 附件，只记：`[附件已省略，mimeType=..., encodedBytes=...]`。
- 生成 request/response preview 后立即脱敏和截断。
- 响应 peek 最大 32 KB，不再使用 512 KB。
- 不保存 Thought Summary；只可记录是否存在 thought step。
- Interaction ID 只保留前 6 后 4 或 hash。

建议把内容提取放入独立纯函数，便于测试。

---

### 7.4 实现统一脱敏器

新增：

```kotlin
class TelemetryRedactor {
    fun redact(input: String): String
}
```

最低覆盖：

- Google/Gemini Key：`AIza...`
- `key=` query 参数
- Bearer Token
- 常见 GitHub Token 前缀
- PEM 私钥头
- 邮箱
- 中国大陆手机号
- JWT 三段式 Token
- 超长连续 Base64/十六进制串

注意：

- 邮箱和手机号只用于调试预览脱敏，不应破坏正常网络请求。
- Redactor 只处理日志副本。
- 不要让脱敏器抛异常阻断主请求。

示例结果：

```text
user@example.com → [REDACTED_EMAIL]
13800138000 → [REDACTED_PHONE]
AIza... → [REDACTED_API_KEY]
Bearer abc... → Bearer [REDACTED_TOKEN]
```

---

### 7.5 截断规则

建议常量：

```kotlin
const val MAX_REQUEST_PREVIEW_CHARS = 2_000
const val MAX_RESPONSE_PREVIEW_CHARS = 2_000
const val MAX_ERROR_MESSAGE_CHARS = 500
```

截断后追加：

```text
…[已截断，原始长度 N]
```

禁止只用 `take(N)` 而不说明已截断，否则调试者可能误判内容完整。

---

### 7.6 独立遥测存储

禁止继续使用：

```text
gemini_api_key_prefs / telemetry_api_logs_json
```

建议选项：

#### 低复杂度方案（本 PR 推荐）

独立 Preferences：

```text
roundtable_telemetry_prefs
```

字段：

```text
telemetry_events_json
telemetry_schema_version
```

最多 100 条 Metadata Event；Content Debug 最多 20 条。

#### 更复杂方案

Room 独立表。除非现有 UI 已明显需要查询、分页，不建议本 PR引入。

存储要求：

- 写入前清除过期事件。
- Metadata 默认保留 7 天。
- Content Debug 最长保留 24 小时。
- 达到条数上限删除最旧事件。
- 存储失败不得阻断主请求。
- 事件 JSON 损坏时应安全清空并显示可理解提示。

---

### 7.7 迁移旧遥测数据

首次启动新版本时：

1. 检查旧 `gemini_api_key_prefs` 中的 `telemetry_api_logs_json`。
2. 不迁移其中 Prompt/回复正文。
3. 直接删除旧字段，或只转换状态码、耗时等 metadata；推荐直接删除以降低复杂度。
4. 写入一次迁移完成标记。
5. 不影响已加密 API Key 记录。

必须有测试或至少清晰的迁移函数，禁止在多个 UI 页面零散删除。

---

### 7.8 更新遥测 UI

遥测页面至少显示：

- 当前级别：关闭 / 仅元数据 / 临时正文调试。
- 内容调试剩余时间。
- 当前事件数量。
- 估算占用空间。
- “立即清空全部遥测”。
- “关闭正文调试并删除正文预览”。

开启正文调试前弹窗必须说明：

```text
开启后，应用会在本机临时保存经过脱敏和截断的请求/回复预览，最长 24 小时。请勿在调试期间输入密码、私钥或其他高度敏感信息。
```

用户确认后才开启。

列表默认只展示 metadata。只有内容调试事件才出现“查看预览”。

不得显示完整 Key；只显示 `K1`、keyId 或掩码后四位。

---

### 7.9 增加一键清空

清空操作应：

1. 删除持久化事件。
2. 清空内存事件缓存。
3. 不删除 API Key。
4. 不清除 Key 冷却状态，除非用户另点对应按钮。
5. UI 立即刷新。
6. 返回明确成功/失败提示。

建议 API：

```kotlin
fun clearAllTelemetry(): Boolean
```

并增加：

```kotlin
fun disableContentDebugAndPurgePreviews()
```

---

### 7.10 云端 Interaction 设置

新增设置模型：

```kotlin
enum class CloudInteractionMode {
    DISABLED,
    ENABLED
}
```

默认 `DISABLED`。

请求构造规则：

- Disabled：`store=false`、`previousInteractionId=null`。
- Enabled：允许 `store=true` 和角色级 Interaction 链。
- 开启前必须说明会话内容会发送至 Google Gemini，本地 App 无法控制服务商侧保留策略。
- 用户关闭后清理本地缓存的 Interaction ID。
- 删除本地会话时同步删除本地 Interaction ID；如 API 不支持远程删除，文档必须坦诚说明。

注意：所有模型请求本来就会发送内容给服务商；这里的设置控制的是“是否额外使用持久化 Interaction 链”，不能误导用户以为关闭后完全离线。

---

### 7.11 备份规则

检查：

```text
app/src/main/res/xml/backup_rules.xml
app/src/main/res/xml/data_extraction_rules.xml
```

要求：

- API Key 存储继续排除。
- 新遥测 Preferences 也排除 cloud backup 和 device transfer。
- 不要用 `<include sharedpref path="."/>` 后遗漏新的敏感 Preferences。
- 最好采用明确 exclude，或者把备份策略改为最小允许列表；若改变范围可能影响聊天记录备份，必须在交付报告说明。

---

## 8. 单元测试要求

建议新增：

```text
app/src/test/java/com/example/skillroundtable/telemetry/TelemetryRedactorTest.kt
app/src/test/java/com/example/skillroundtable/telemetry/TelemetryRetentionPolicyTest.kt
app/src/test/java/com/example/skillroundtable/telemetry/TelemetryEventFactoryTest.kt
app/src/test/java/com/example/skillroundtable/telemetry/TelemetryMigrationTest.kt
```

最低覆盖：

### Redactor

- [ ] Gemini Key 被遮蔽。
- [ ] query 中 `key=` 被遮蔽。
- [ ] Bearer Token 被遮蔽。
- [ ] 邮箱和手机号被遮蔽。
- [ ] JWT 被遮蔽。
- [ ] 普通中文内容不会全部误删。
- [ ] 超长输入不会抛异常。

### Level

- [ ] OFF 不创建事件。
- [ ] METADATA_ONLY 不读取/保存正文。
- [ ] CONTENT_DEBUG 保存脱敏截断预览。
- [ ] Release 强制不能进入 CONTENT_DEBUG。

### Retention

- [ ] Content Debug 24 小时后过期。
- [ ] Metadata 7 天后清理。
- [ ] 超过条数限制删除最旧。
- [ ] 关闭 Content Debug 后 purge 预览。

### Migration

- [ ] 旧 `telemetry_api_logs_json` 被删除。
- [ ] API Key Preferences 的其他字段不受影响。
- [ ] 损坏 JSON 不导致应用启动失败。

### Cloud Interaction

- [ ] 默认请求 `store=false`。
- [ ] 未开启时不附带 previousInteractionId。
- [ ] 开启时只使用角色级链。
- [ ] 关闭后清理本地链 ID。

---

## 9. 必须执行的验证

```powershell
./gradlew.bat compileDebugKotlin
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
./gradlew.bat assembleDebug
```

静态扫描：

```powershell
git grep -n -I 'Level.BODY\|telemetry_api_logs_json\|peekBody(1024 \* 512)\|Thought Summary'
```

预期：

- Release/默认路径没有 BODY 日志。
- 旧持久化字段只可出现在一次性迁移代码或历史说明中。
- 不再保存 Thought Summary。

设备验收：

1. 默认启动并发起请求。
2. 遥测页只能看到模型、状态码、耗时等 metadata。
3. 杀进程重启后，仍不存在完整 Prompt/回复。
4. 开启 Content Debug，确认出现警告。
5. 输入包含测试邮箱、手机号、假 Token 的文本，确认预览脱敏。
6. 确认附件只显示元信息，不显示解码全文。
7. 点击清空，重启后事件仍为空。
8. 将过期时间模拟到过去，重启后自动降级。
9. Release 构建检查 Logcat，不输出请求/响应正文。

---

## 10. 预计修改文件

新增：

```text
app/src/main/java/com/example/skillroundtable/telemetry/*
app/src/test/java/com/example/skillroundtable/telemetry/*
```

修改：

```text
app/src/main/java/com/example/skillroundtable/network/GeminiApi.kt
app/src/main/java/com/example/skillroundtable/network/ApiKeyPool.kt
app/src/main/java/com/example/skillroundtable/network/ApiKeyModels.kt
app/src/main/java/com/example/skillroundtable/ApiTelemetryScreen.kt
app/src/main/java/com/example/skillroundtable/ApiKeyManagerScreen.kt
app/src/main/res/xml/backup_rules.xml
app/src/main/res/xml/data_extraction_rules.xml
```

可能修改：

```text
RoundtableViewModel.kt 或 PR 02 新的请求构造器
README.md
docs/features/debug-panel-and-telemetry-diagnostics.md
docs/architecture/system-architecture.md
```

---

## 11. 验收清单

- [ ] 默认级别是 METADATA_ONLY。
- [ ] 默认不保存完整 Prompt、system instruction、附件和回复。
- [ ] Release 不启用 BODY 日志。
- [ ] Release 不允许 CONTENT_DEBUG。
- [ ] Content Debug 必须显式确认。
- [ ] Content Debug 自动在 24 小时内过期。
- [ ] 预览长度受限并显示截断信息。
- [ ] API Key、Token、邮箱、手机号和 JWT 被脱敏。
- [ ] 附件不被默认 Base64 解码持久化。
- [ ] Thought Summary 不进入日志。
- [ ] 遥测使用独立存储。
- [ ] 新遥测存储排除系统备份。
- [ ] 旧明文遥测数据被清理。
- [ ] 用户可以一键清空。
- [ ] 默认 `store=false`。
- [ ] 开启云端链前有清晰说明。
- [ ] 单元测试、Lint、Debug 构建通过。

---

## 12. 禁止事项

- 禁止为方便排错默认保存完整 Prompt。
- 禁止把完整 Key 写入任何日志或异常消息。
- 禁止在 Release 开启 BODY。
- 禁止将遥测上传到第三方或作者服务器。
- 禁止解码并持久化附件正文。
- 禁止无限保留 Content Debug 数据。
- 禁止只隐藏 UI 而继续后台保存正文。
- 禁止用“本地保存所以绝对安全”之类误导文案。
- 禁止把关闭云端 Interaction 描述成“内容不会发送给模型服务商”。

---

## 13. 交付报告必须额外回答

1. 默认 TelemetryLevel 是什么？
2. Metadata Event 包含哪些字段？
3. 哪些字段永远不进入遥测？
4. Content Debug 如何开启、何时过期？
5. 请求和响应预览分别截断到多少字符？
6. Redactor 覆盖哪些敏感模式？
7. 旧 `telemetry_api_logs_json` 如何处理？
8. 新遥测数据存在哪里，是否被系统备份？
9. Release Logcat 如何证明没有正文？
10. `store` 默认值是什么，用户如何切换？
11. 关闭云端链后哪些本地状态会被清理？
12. 哪些自动化测试证明默认不保存用户正文？
