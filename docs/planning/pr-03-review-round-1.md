# PR03 隐私、遥测与数据安全审查记录

## 1. 审查范围

- 基线分支：`main`
- 基线提交：`b14ba04c304de33714c47bb74960f7a598d3ae3f`
- 被审初始提交：`8cdfe367776c2fd89a442e193d73d6b0753c5953`
- 工作分支：`refactor/pr-03-privacy-telemetry-hardening`

本轮重点检查：本地遥测、正文调试、敏感信息脱敏、Interaction 存储策略、Release/Debug 日志、API 异常、音频路径、旧数据迁移、备份规则和会话删除清理。

## 2. 已发现并修复的问题

### 2.1 过期事件未必从磁盘删除

启动时曾只更新内存列表，裁剪后的旧事件可能继续留在 Preferences。现在加载、读取和写入路径都会执行保留策略，并在列表变化时同步持久化。

### 2.2 遥测仓库并发覆盖

记录、清空、关闭正文调试和过期清理已统一串行化，避免多个网络请求同时基于旧快照写回而丢事件。

### 2.3 调试预览包含不必要的高敏感字段

正文调试预览现在额外处理：

- Interaction ID 仅掩码；
- Thought Summary 和签名直接省略；
- 搜索 query、snippet、result、grounding metadata 正文省略；
- URL query 删除；
- 长 Base64、长十六进制、API Key、Bearer Token、JWT、邮箱和手机号统一脱敏；
- 附件只保留 MIME 类型与编码长度，不解码正文。

### 2.4 默认及 Release 日志泄露面

- OkHttp：Debug 仅 `BASIC`，Release 为 `NONE`；
- Broker 原文、搜索词、模型回复和完整异常消息不再进入 Logcat；
- Live WebSocket、音频播放、转码、数据库、ViewModel 和 Orchestrator 操作日志统一通过 `PrivacySafeLogger`；
- `PrivacySafeLogger` 不输出 Throwable 堆栈，并安全吞掉 JVM 单测环境中的 Android Log 异常；
- `ApiExecutionException` 只暴露稳定错误分类、操作名和内部 Key ID。

### 2.5 云端 Interaction 开关此前没有真正形成角色链

现在增加进程内 `InteractionChainStore`：

- Key 为“会话 ID × 角色 ID”；
- 主回答只复用同一角色自己的上一条 Interaction；
- 截断续写成功后推进该角色游标；
- Broker、Embedding、标题和联网搜索不进入角色链；
- 默认关闭时强制 `store=false` 且不发送 `previousInteractionId`；
- 关闭开关清空全部本地游标；
- 删除本地会话清理该会话游标；
- 游标不写磁盘，不参与系统备份。

### 2.6 文档严重漂移

旧文档仍声称内置 10 个 Key、角色并发分组、随机延迟和完整 Prompt 日志。相关架构与诊断文档已按 PR01/PR02/PR03 的真实代码重写。

## 3. 新增或强化的测试

- `TelemetryRedactorTest`
- `TelemetryRetentionPolicyTest`
- `TelemetryEventFactoryTest`
- `CloudInteractionRequestPolicyTest`
- `InteractionChainStoreTest`
- `ApiExecutionExceptionPrivacyTest`

覆盖重点：

- OFF 不创建事件；
- Metadata 不保留正文；
- Content Debug 只保留受控预览；
- Release 拒绝 Content Debug；
- 7 天 / 24 小时保留和数量上限；
- Key、Token、JWT、邮箱、手机号和长编码数据脱敏；
- Interaction 链按会话和角色隔离；
- 会话清理不影响其他会话；
- API 异常消息不包含原始底层异常正文。

## 4. 静态确认结果

- 分支基于当前 `main`，无落后提交；
- API Key 密文仍位于 `noBackupFilesDir`；
- 遥测设置、事件与云端 Interaction 设置均排除自动备份和设备迁移；
- `.env.example` 仅含占位符，`.gitignore` 忽略 `.env`、本地配置、签名文件、构建产物和日志；
- 角色链缓存为纯 Kotlin 进程内结构，已单独通过 Kotlin 编译语法检查。

## 5. 尚未声明通过的门禁

当前远端提交没有 GitHub CI 状态检查；审查环境也无法解析 GitHub 域名，不能克隆仓库并运行 Android Gradle。所以下列项目必须在 Windows 10、JDK 17、Android SDK 完整环境中实际执行后，才可宣布 PR03 验收通过：

```powershell
./gradlew.bat clean
./gradlew.bat compileDebugKotlin
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
./gradlew.bat assembleDebug
./gradlew.bat assembleRelease
powershell -ExecutionPolicy Bypass -File .\tools\check-secrets.ps1
```

## 6. 建议静态扫描

```powershell
git grep -n -I -E 'Level\.BODY|peekBody\(1024 \* 512\)|e\.printStackTrace\(\)|Thought Summary' -- app/src/main
git grep -n -I 'telemetry_api_logs_json'
git grep -n -I -E 'AIza[0-9A-Za-z_-]{20,}|gh[pousr]_[0-9A-Za-z]{20,}|github_pat_[0-9A-Za-z_]+' -- .
git grep -n -I -E 'Log\.(d|w|e)\(' -- app/src/main/java
```

预期：

- 不存在 BODY 日志和 512 KiB 响应窥视；
- `telemetry_api_logs_json` 只出现在一次性迁移代码或历史说明；
- 不存在真实 Key / Token；
- 业务代码不直接打印 Prompt、回复、搜索词、文件路径、原始异常消息或堆栈；
- `android.util.Log` 的实际调用集中在 `PrivacySafeLogger`。

## 7. 设备验收

1. 全新安装后确认默认级别为“仅元数据”。
2. 发起主回答、Broker、搜索、Embedding 和标题请求；诊断页不得出现完整 Prompt、回复或搜索词。
3. 杀进程重启，确认默认遥测仍无正文。
4. Debug 构建显式开启正文调试，输入测试邮箱、手机号、假 Token 和假 JWT，确认预览已脱敏。
5. 确认附件只显示 MIME 类型与编码长度。
6. 将正文调试到期时间调整到过去，重启后应自动降级并删除预览。
7. 开启云端会话链，同一角色进入下一轮时可复用自己的游标；不同角色不得共享。
8. 关闭云端会话链后再次请求，必须发送 `store=false` 且无 `previousInteractionId`。
9. 删除会话后，对应本地角色游标应清空。
10. Release 构建通过 Logcat 验证：无请求/响应正文、搜索词、完整 URL query、Key 和 Throwable 堆栈。

## 8. 合并条件

只有在第 5 节所有命令成功、静态扫描符合预期、设备关键路径完成后，才建议合并到 `main`。