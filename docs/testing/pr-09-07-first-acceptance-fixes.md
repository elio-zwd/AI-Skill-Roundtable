# PR09-07 首轮严格只读验收失败修复记录

## 1. 验收基线

首轮本地严格只读验收目标：

```text
PR：#38
Head：e46a2ccc93b325030693fc97438ca915c10f1715
JVM：276 完成，275 通过，1 失败
Instrumentation：112 完成，108 通过，4 失败
```

首轮报告同时确认：Kotlin 编译、AndroidTest 编译、Lint、Debug APK、Release/R8、Room v8 核心事务、预算、强停恢复与安全扫描均已通过。

## 2. 已修复项

### 2.1 导航架构守卫

旧断言仍要求 `App.kt` 组装 `IssueRecoveryRoute`。PR09-07 已将 Issue 深链接入 `IssueExecutionRoute`，因此更新为检查新执行工作区，不删除 DAO/Repository 隔离断言。

### 2.2 Migration 连续性守卫

`ResourceLifecycleMigrationTest` 的连续链从：

```text
1→2→3→4→5→6→7
```

更新为：

```text
1→2→3→4→5→6→7→8
```

6→7 和 5→7 的历史专项测试继续保持原目标版本，不被改写为 v8 测试。

### 2.3 v7→v8 旧 Run 回填测试

失败测试仍期待新增运行表为空，但生产 Migration 已明确执行保守回填。测试现验证：

- 旧活跃 Run 收敛为 `retryable`；
- 旧参与者收敛为 `retryable/process_interrupted`；
- 创建一个根预算记录；
- `usedApiCalls=0`，不伪造不可证明的历史调用；
- `closed=true`，不允许升级后获得免费调用；
- 四个新增索引存在；
- `PRAGMA foreign_key_check=0`。

复合外键定义未被盲目修改，因为 Room 实体与 Migration 都使用：

```text
(participantSnapshotId, runId) → execution_participant_snapshots(id, runId)
```

父表存在同列顺序的唯一索引。

### 2.4 只读提示 Compose 断言

原测试先按前半句定位节点，再对同一节点执行默认精确文本断言。修正为分别使用 `substring=true` 查找两段用户可见文案，并验证节点可见，不依赖控制台编码猜测。

### 2.5 执行中停止

不仅修改按钮断言，同时修正生产行为：

- `operationInProgress=true` 时 Stop 按钮保持可用；
- `IssueExecutionViewModel.stop()` 绕过普通操作忙碌门禁；
- Coordinator 仍先持久化 STOPPED、成员状态和 Pending，再取消网络 Job；
- 执行期间每 120ms 从 Repository 刷新持久化状态，使流式文本、预算和成员状态可见；
- 重试与中断恢复仍受普通忙碌门禁保护。

## 3. Room Schema

Android CI #341 在 Head `f5d69d31d11d4f71c7ab80551bcd4cd7de517286` 上真实通过：

- 静态身份门禁；
- Kotlin 编译；
- 全量 JVM；
- Lint；
- Debug APK；
- Release/R8；
- 包迁移与 Schema 结构校验。

该 Run 唯一失败是未提交生成的 `8.json`。随后从固定产物 `room-schema-1` 提取并校验：

```text
文件大小：96800 bytes
SHA-256：a426e6635e6996b8605573de71a075629810dfb7e193354c7f80eac968919f72
Room version：8
identityHash：abf8f4df199cbed578f1ca72a40b0232
```

真实编译产物已提交到：

```text
app/schemas/com.elio.jianyu.data.RoundtableDatabase/8.json
```

未手工构造或修改 Schema。

## 4. 复验要求

后续本地 AI 应从 PR #38 动态读取当前 `headRefOid`，至少重新执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.ResourceLifecycleMigrationTest'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.ExecutionRuntimeMigrationTest'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.execution.IssueExecutionScreenTest'
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.execution.IssueExecutionStopAvailabilityTest'
.\gradlew.bat :app:connectedDebugAndroidTest
```

复验仍须严格只读，不得修改、提交、推送、标记 Ready 或合并。
