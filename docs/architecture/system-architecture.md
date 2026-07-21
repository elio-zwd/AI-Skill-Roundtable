# 系统架构说明

## 1. 整体架构

应用采用 Android MVVM、Jetpack Compose、Room、Retrofit/OkHttp、Kotlin Coroutines 和 WorkManager。核心控制流由生产 `RoundtableOrchestrator` 负责，ViewModel 只桥接 UI、数据库和网络网关。

```text
Compose UI
  ├─ 圆桌会话、角色与组合管理
  ├─ API Key 管理
  ├─ 隐私、遥测与 API 诊断
  └─ 音频库
          │ StateFlow
          ▼
RoundtableViewModel
          │
          ├─ RoundtableOrchestrator
          │    ├─ RoundtableBudgetManager / RequestBudgetTracker
          │    ├─ RoundtableDatabaseGateway
          │    └─ CharacterAnswerGateway
          │
          ├─ Room repositories
          ├─ RetrofitClient / ApiKeyScheduler
          └─ LiveApiClient / WorkManager audio pipeline
```

## 2. 圆桌编排边界

### 2.1 问题级角色快照

- 首次执行时对全部激活角色进行可选的语义排序。
- 按排序结果锁定最多 6 位参与角色，并保存角色对象快照。
- 后续轮次复用首次快照，不引入第 7 位角色。
- 角色在问题处理中被停用、编辑或删除，不会使旧问题死锁，也不会由新角色补位。

### 2.2 严格串行

- 每位角色在前一位角色发言完成后才开始。
- 每个角色读取当前问题范围内的前序发言。
- 不使用多组并发、随机分组或随机延迟。
- 固定的最小请求间隔只用于基础速率保护，不改变串行语义。

### 2.3 请求预算

- 每个问题共享最多 30 次 API 请求预算。
- REQUIRED 与 OPTIONAL 请求采用原子预留。
- 同 Key 重试和换 Key 都在同一操作预算内执行，不得抢占后续角色主回答预留。
- 若剩余额度不足以完成当前整轮，不启动半轮回答。
- 问题预算当前只保存在进程内；进程重启后的预算持久化仍是已知限制。

## 3. 网络与 API Key 架构

### 3.1 BYOK

- 项目不内置生产 API Key。
- 用户 Key 通过 Android Keystore + AES-GCM 加密，密文写入 `noBackupFilesDir`。
- UI 和遥测只读取内部 Key ID、显示名与掩码摘要。

### 3.2 Key Lease 与重试

`ApiKeyScheduler` 生成确定的尝试顺序，综合：

1. 调用方 preferred Key；
2. 当前会话绑定 Key；
3. 其他可用候选；
4. last-used 轮转状态。

重试策略：

- 5xx：同 Key 最多重试两次，退避 1 秒、2 秒；
- 408 / 网络异常：按策略有限重试；
- 429：解析 `Retry-After` 秒数并冷却后切换 Key；
- 401 / 403：标记当前 Key 不可用并切换；
- 400 / 404 / 序列化错误：停止当前操作。

异常对外只暴露稳定错误分类、操作名和内部 Key ID，不拼接原始 URL、服务商错误正文或 Throwable 消息。

## 4. Broker、搜索与主回答

每位角色的回答链路为：

```text
当前问题与圆桌前序发言
        │
        ├─ 可选 Broker：选择必要的本地 example/reference 文件
        ├─ 可选 Google Search：受 OPTIONAL 预算和每角色查询数限制
        └─ 主回答：REQUIRED 请求，读取 Skill 与已选资料
```

- Broker 和搜索属于可选请求，预算不足时直接跳过。
- 搜索词、Broker 原文和搜索结果正文不写入操作日志或默认遥测。
- 主回答请求的 `previousInteractionId` 固定从 `null` 开始，不恢复多角色共享云端链。
- 只有用户显式启用云端会话链后，同一角色的截断续写才允许使用该次响应的 Interaction ID。

## 5. 遥测与隐私架构

网络请求经过独立 `telemetry` 包：

```text
OkHttp request
   │
   ├─ TelemetryInterceptor
   │    ├─ OFF：直接透传
   │    ├─ METADATA_ONLY：不读取 request/response body
   │    └─ CONTENT_DEBUG：受构建类型、到期时间、大小限制控制
   │
   ├─ TelemetryPreviewExtractor
   ├─ TelemetryRedactor
   └─ TelemetryRepository
```

### 5.1 默认元数据

默认只持久化时间、耗时、端点路径、模型、内部 Key ID、状态码和错误分类。默认不保存 Prompt、system instruction、附件正文、模型回复、搜索正文、Thought Summary、完整 Interaction ID 或 URL query。

### 5.2 临时正文调试

- 仅 Debug 构建可启用；
- 用户必须确认隐私警告；
- 24 小时自动过期；
- request 最多读取 16 KiB，response 最多 `peek` 32 KiB；
- 预览各最多 2,000 字符；
- Base64 附件、Thought Summary、签名和搜索正文直接省略；
- Interaction ID 掩码，所有预览统一脱敏。

### 5.3 保留与备份

- Metadata：最长 7 天、最多 100 条；
- Content Debug：最长 24 小时、最多 20 条；
- 过期数据在启动、读取和写入时裁剪，并从磁盘同步删除；
- 遥测设置、事件和云端 Interaction 开关使用独立 Preferences；
- 上述 Preferences 均排除 Android 自动备份和设备迁移。

## 6. 日志架构

- Release 的 OkHttp 日志级别为 `NONE`。
- Debug 默认只使用 `BASIC`，不打印 BODY。
- 应用操作日志统一经 `PrivacySafeLogger`：仅 Debug 输出，写入前脱敏和截断，不输出 Throwable 堆栈或原始异常消息。
- 内容调试预览只进入受控的本地遥测仓库，不进入全局 Logcat。

## 7. Room 与音频

### 7.1 Room

Room 保存角色、会话、消息、组合和音频索引。普通聊天数据库整体加密不属于 PR03；后续安全工作可单独设计迁移方案。

### 7.2 音频

- Gemini Live WebSocket 返回 PCM 音频；
- 应用先在缓存目录生成 WAV；
- WorkManager 使用 MediaCodec 转为带 ADTS 头的 AAC；
- 成功后更新 Room 索引并删除临时 WAV；
- 日志不记录音频路径或用户正文。
