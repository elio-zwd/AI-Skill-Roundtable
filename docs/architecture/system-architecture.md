# 系统架构说明

## 1. 整体架构

应用采用 Android MVVM、Jetpack Compose、Room、Retrofit/OkHttp、Kotlin Coroutines 和 WorkManager。生产核心控制流由 `RoundtableOrchestrator` 负责，`RoundtableViewModel` 桥接 UI、数据库和网络网关。

```text
MainActivity
  │
  ▼
SkillRoundtableTheme + MainAppContent
  │
  ├─ AppNavHost / AppDestination
  │    ├─ RoundtableRoute
  │    ├─ CharacterHallRoute
  │    ├─ AudioLibraryRoute
  │    ├─ AiManagementRoute
  │    └─ TelemetryRoute
  │
  ├─ Route：收集 StateFlow、连接事件与副作用
  ├─ Screen：不可变 UiState + Event callback
  └─ Components：展示与局部交互
           │
           ▼
RoundtableViewModel / AiManager / TelemetryRepository
           │
           ├─ RoundtableOrchestrator
           │    ├─ RoundtableBudgetManager / RequestBudgetTracker
           │    ├─ RoundtableDatabaseGateway
           │    └─ CharacterAnswerGateway
           ├─ Room repositories
           ├─ ProviderKeyRepository / AiRequestExecutor
           ├─ GeminiRestTransport / GeminiInteractionsTransport / DeepSeekTransport
           └─ GeminiLiveAudioTransport / WorkManager audio pipeline
```

PR07 只重构 UI 结构、主题、导航和测试门禁，没有重新设计视觉，也没有改变 Room、网络、SSE、TTS、Key 或遥测业务语义。

## 2. Compose UI 架构

### 2.1 Activity 与 App

`MainActivity.kt` 只负责：

1. `enableEdgeToEdge()`；
2. 挂载 `SkillRoundtableTheme`；
3. 创建全屏 `Surface`；
4. 调用 `MainAppContent()`。

`ui/App.kt` 只负责：

- 创建 `NavHostController`；
- 解析当前 `AppDestination`；
- 控制底部导航是否显示；
- 将页面 Route 注入 `AppNavHost`；
- 连接顶层与二级导航。

页面专属 Dialog、Toast、Drawer、输入或表单状态不得放在 `App.kt`。

### 2.2 Navigation

稳定目的地：

| 类型 | Destination | Route | 底部导航 |
|---|---|---|---|
| 顶层 | `ROUNDTABLE` | `roundtable` | 显示 |
| 顶层 | `CHARACTERS` | `characters` | 显示 |
| 顶层 | `AUDIO_LIBRARY` | `audio-library` | 显示 |
| 二级 | `API_KEYS`（界面名称：AI 管理） | `settings/api-keys` | 隐藏 |
| 二级 | `TELEMETRY` | `settings/telemetry` | 隐藏 |

圆桌直接打开遥测时，导航栈按 `ROUNDTABLE → API_KEYS → TELEMETRY` 构造；`API_KEYS` 页面展示为“AI 管理”，使系统返回与顶部返回都先回 AI 管理，再回圆桌。

### 2.3 Route / Screen / Components / UiState

| 层 | 可以做 | 不可以做 |
|---|---|---|
| Route | 收集 Flow；调用 ViewModel/Repository/服务；处理 Toast、Dialog 状态和 IO 副作用 | 绘制大量页面细节；复制业务算法 |
| Screen | 根据不可变 UiState 选择空、加载、运行、错误、失败等视觉状态；派发 Event | 获取全局 ViewModel；直接访问 Repository 或网络 |
| Components | 展示、局部展开、点击和输入控件 | 跨页面调用；持有业务生命周期 |
| UiState / reducer | 纯映射、纯状态派生、事件归约 | Context、IO、协程、数据库或网络调用 |

依赖方向固定为：

```text
App → Route → Screen → Components
          └→ ViewModel / Repository / service
```

同级页面域不得引用其他页面域内部组件；跨页面通用展示组件放在 `ui/components/`。

### 2.4 页面域

- `roundtable/`：会话、抽屉、消息、轮次、席位、输入、停止、继续、失败重试、导出、TTS 入口。
- `characters/`：分组、角色列表、详情、启停、新增、编辑、删除。
- `library/`：合成进度与失败、音频列表、播放、删除、已有转码入口。
- `settings/`：AI 管理与遥测页面；共享设置域组件。

### 2.5 主题

全局颜色、Typography、Shapes 和 Spacing 的唯一真实来源位于 `ui/theme/`。`LegacyUiTokens.kt` 仅保留兼容别名，不得重复定义颜色值。PR08 新代码应优先通过 `MaterialTheme` 和语义 Token 访问主题。

### 2.6 自动门禁

PR07-F 增加三类门禁：

1. JVM 静态架构测试：限制 MainActivity、App、Route/Screen 依赖与跨页面引用；
2. Navigation Compose 测试：冷启动、顶层切换、遥测二级返回链；
3. Compose UI 测试：顶层导航、圆桌关键标签、智囊加载/空状态、音频空状态、AI 管理和遥测入口。

完整命令与真机清单见 `docs/testing/pr-07-ui-regression-checklist.md`。

## 3. 圆桌编排边界

### 3.1 问题级角色快照

- 首次执行时对全部激活角色进行可选语义排序。
- 按排序结果锁定最多 15 位参与角色并保存对象快照。
- 后续轮次复用首次快照，不引入第 16 位角色。
- 角色在问题处理中被停用、编辑或删除，不会使旧问题死锁，也不会由新角色补位。

### 3.2 严格串行

- 每位角色在前一位角色发言完成后才开始。
- 每个角色读取当前问题范围内的前序发言。
- 不使用多组并发、随机分组或随机延迟。
- 固定最小请求间隔只用于基础速率保护，不改变串行语义。

### 3.3 API 调用记录

- 应用不按累计 API 调用次数截断用户请求；可用次数由用户的 API 服务商配额决定。
- 每次实际网络尝试都会计数，供运行状态与历史记录展示。
- 同 Key 重试和换 Key 都会计入实际调用次数，不会占用或预留应用内次数额度。
- 用户停止、单个请求超时、网络错误和服务商限流仍按各自语义处理。

## 4. 网络与 API Key 架构

### 4.1 BYOK

- 项目不内置生产 API Key。
- 用户 Key 由 Android Keystore + AES-GCM 加密，密文写入 `noBackupFilesDir`。
- UI 和遥测只读取内部 Key ID、显示名与掩码摘要。

### 4.2 Key Lease 与重试

`ProviderKeyRepository` 按提供商生成确定的尝试顺序：preferred Key、当前会话绑定 Key、其他可用候选、last-used 轮转状态。业务层只读取 Key ID 与显示名，明文仅由 `AiRequestExecutor` 在 transport 调用瞬间解析。

AI 管理分为三块：

1. `AiConfigurationRepository`：分别持久化对话标题、资料决策、联网检索、圆桌回答和议题执行的文本提供商与模型；Gemini 可选 `gemini-3.6-flash`、`gemini-3.5-flash`、`gemini-3.1-flash-lite`，DeepSeek 可选 V4 Flash / Pro；
2. `ProviderKeyRepository` 与 `AiRequestExecutor`：按提供商隔离加密 Key、轮换、重试、错误分类和冷却状态；
3. `GeminiRestTransport`、`GeminiInteractionsTransport`、`GeminiLiveAudioTransport` 与 `DeepSeekTransport`：分别只实现对应 REST、SSE、Live WebSocket、OpenAI 兼容 Chat Completions 协议。

每一种文本调用用途独立读取自己的模型选择；联网检索仅可选择支持 Google Search 的 Gemini 模型。嵌入模型与 Gemini Live 语音模型因 API 协议限定，保持固定且会在 AI 管理页说明。

重试策略：

- 5xx：同 Key 最多重试两次，退避 1 秒、2 秒；
- 408 / 网络异常：按策略有限重试；
- 429：解析 `Retry-After`，冷却后切换 Key；
- 401 / 403：标记当前 Key 不可用并切换；
- 400 / 404 / 序列化错误：停止当前操作。

异常对外只暴露稳定错误分类、操作名和内部 Key ID，不拼接原始 URL、服务商错误正文或 Throwable 消息。

## 5. Broker、搜索与主回答

```text
当前问题与圆桌前序发言
        │
        ├─ 可选 Broker：选择本地 example/reference
        ├─ 可选 Google Search：受每角色查询上限控制
        └─ 主回答：REQUIRED 请求，读取 Skill 与已选资料
```

- Broker 与搜索不会因累计 API 调用次数跳过。
- 搜索词、Broker 原文和搜索结果正文不写入默认遥测。
- 云端 Interaction 默认关闭，所有请求强制 `store=false` 且不发送 `previousInteractionId`。
- 用户显式开启后，主回答按“会话 ID × 角色 ID”维护进程内游标。
- Broker、Embedding、标题生成和联网搜索不进入角色 Interaction 链。
- 关闭开关或删除会话会清理对应游标；游标不写入磁盘或系统备份。

## 6. 遥测与隐私架构

```text
OkHttp request
   │
   ├─ TelemetryInterceptor
   │    ├─ OFF：直接透传
   │    ├─ METADATA_ONLY：不读取 request/response body
   │    └─ CONTENT_DEBUG：受构建类型、到期时间、大小限制控制
   ├─ TelemetryPreviewExtractor
   ├─ TelemetryRedactor
   └─ TelemetryRepository
```

### 6.1 默认元数据

默认只持久化时间、耗时、端点路径、模型、内部 Key ID、状态码和错误分类，不保存 Prompt、system instruction、附件正文、模型回复、搜索正文、Thought Summary、完整 Interaction ID 或 URL query。

### 6.2 临时正文调试

- 仅 Debug 构建可启用；
- 用户必须确认隐私警告；
- 24 小时自动过期；
- request 最多读取 16 KiB，response 最多 `peek` 32 KiB；
- 预览各最多 2,000 字符；
- Base64 附件、Thought Summary、签名和搜索正文直接省略；
- Interaction ID 掩码，预览统一脱敏。

### 6.3 保留与备份

- Metadata：最长 7 天、最多 100 条；
- Content Debug：最长 24 小时、最多 20 条；
- 过期数据在启动、读取和写入时裁剪；
- 遥测与云端 Interaction 设置使用独立 Preferences；
- 对应 Preferences 排除 Android 自动备份和设备迁移。

## 7. 日志架构

- Release OkHttp 日志级别为 `NONE`。
- Debug 默认只使用 `BASIC`，不打印 BODY。
- 应用操作日志经 `PrivacySafeLogger` 脱敏和截断，不输出原始异常正文。
- 内容调试预览只进入本地遥测仓库，不进入全局 Logcat。

## 8. Room 与音频

### 8.1 Room

Room 保存角色、会话、消息、组合和音频索引。修改实体时必须同步版本、Migration、Schema 和测试。

### 8.2 音频

- Gemini Live WebSocket 返回 PCM；
- 应用先在缓存目录生成 WAV；
- WorkManager 使用 MediaCodec 转为带 ADTS 头的 AAC；
- 成功后更新 Room 索引并删除临时 WAV；
- 日志不记录音频路径或用户正文。

## 9. 已知技术债与非 PR07 范围

- `RoundtableViewModel` 仍是多个页面域的集中桥接点，PR07 不重写业务层。
- `AiManagementRoute` 和 `TelemetryRoute` 仍直接连接现有单例服务，PR07 只建立 UI 边界。
- `LegacyUiTokens.kt` 仍作为兼容别名存在，但没有重复颜色值。
- 当前没有亮色主题、Material 3 Adaptive 或大屏专用布局。
- Seek、时间轴、AAC 新功能和无关业务 Bug 不属于 PR07。
- 视觉、品牌、排版和动效改版属于 PR08。
