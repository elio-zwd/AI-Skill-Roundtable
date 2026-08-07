# PR09-13B Runtime Lifecycle 前置 PR 本地严格只读验收 Prompt

你现在只负责对 GitHub Draft PR #54 做本地严格只读验收。

仓库：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable
```

目标 PR：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable/pull/54
```

本验收只允许读取、构建、测试和记录结果。不得修改、格式化、提交、推送、标记 Ready、合并、关闭 PR 或删除分支。

## 一、目标与边界

本 PR 是 PR09-13B 的数据库与 Runtime 生命周期前置修正，只验证：

- Room 单例安全关闭、清空和重新创建；
- App Runtime 世代和消费者租约；
- 闭库后异常/取消时的可靠重开；
- Compose 清理旧 ViewModelStore；
- Worker 全流程持有 Runtime 租约；
- Room v12、Migration、Schema、Manifest 和系统备份 XML 保持不变。

本 PR 不应包含：

- KDF、AEAD、CBOR 或 Record Stream 生产实现；
- `.jybak`、`.jysnap`、SAF 或 Snapshot Index；
- Portable 导入、差异预览、数据库替换或恢复；
- Room Entity、DAO、Migration 或 Schema v13；
- destructive migration。

## 二、开始前锁定精确目标

执行并记录：

```powershell
git fetch origin --prune
git checkout refactor/pr-09-13b-runtime-lifecycle-prep
git pull --ff-only origin refactor/pr-09-13b-runtime-lifecycle-prep

git rev-parse HEAD
git rev-parse origin/refactor/pr-09-13b-runtime-lifecycle-prep
git rev-parse origin/main
git merge-base HEAD origin/main
git status --short
```

要求：

- Local Head = Remote Head；
- Base/merge-base 必须与 PR 页面显示一致；
- 开始和结束时工作区均干净；
- 若远端 Head 在验收过程中变化，停止并报告 `HEAD_MOVED`，不要继续对旧 Head 给出通过结论。

## 三、记录环境

至少记录：

```text
操作系统与版本
CPU
RAM
JDK 与 javac 版本
Gradle Wrapper 版本
Android SDK / Build Tools
adb 版本
模拟器或真机型号
API Level
ABI
可用磁盘空间
验收开始和完成时间
```

执行：

```powershell
java -version
javac -version
.\gradlew.bat --version
adb version
adb devices -l
```

## 四、Diff 范围核对

执行：

```powershell
git diff --stat origin/main...HEAD
git diff --name-status origin/main...HEAD
git diff --check origin/main...HEAD
```

逐项确认：

- 修改仅服务 Runtime 生命周期前置修正；
- 没有密码学、备份格式、SAF、导入或恢复生产代码；
- 没有无关依赖升级、格式化或重构；
- 没有测试删除、断言降低、异常吞掉或安全控制绕过；
- 没有反射修改 Room 单例；
- 没有第二套 `Room.databaseBuilder` 用于生产数据库；
- 没有强制租约清零或强制解锁；
- 没有自动停止 Run、Purge 或 Audio。

## 五、冻结文件与 Room 检查

执行：

```powershell
git diff --exit-code origin/main...HEAD -- app/schemas
git diff --exit-code origin/main...HEAD -- app/src/main/AndroidManifest.xml
git diff --exit-code origin/main...HEAD -- app/src/main/res/xml/backup_rules.xml
git diff --exit-code origin/main...HEAD -- app/src/main/res/xml/data_extraction_rules.xml
```

静态确认：

```text
RoundtableDatabase version = 12
不存在 app/schemas/.../13.json
不存在 MIGRATION_12_13
不存在 fallbackToDestructiveMigration
数据库名仍为 roundtable_database
Migration 1→12 顺序不变
```

搜索：

```powershell
git grep -n "fallbackToDestructiveMigration\|MIGRATION_12_13\|version = 13" -- app/src app/schemas
git grep -n "Room.databaseBuilder" -- app/src/main
```

生产 `Room.databaseBuilder` 应只保留受控的 `RoundtableDatabase` 创建点；测试 Builder 不属于违规。

## 六、静态生命周期审查

逐文件检查：

```text
app/src/main/java/com/elio/jianyu/runtime/JianyuRuntimeLifecycle.kt
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt
app/src/main/java/com/elio/jianyu/ui/App.kt
app/src/main/java/com/elio/jianyu/audio/AudioTranscodeWorker.kt
app/src/main/java/com/elio/jianyu/audio/work/AudioAssetGenerationWorker.kt
app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeWorker.kt
```

必须确认：

1. `closeAndClear(expected)` 只能关闭当前预期实例；
2. `INSTANCE` 与关闭动作在同一 companion 临界区；
3. `getDatabase()` 不返回 `isExplicitlyClosed=true` 的实例；
4. 新租约与维护切换由同一状态锁协调；
5. 维护开始后不再发放新租约；
6. 不存在强制释放其他调用方租约的接口；
7. 租约关闭幂等；
8. 旧世代释放不影响新世代；
9. 闭库后重开位于 `NonCancellable` 收尾区；
10. 重开失败时不重新发布旧 Runtime；
11. `cause.message` 不直接进入 UI；
12. Compose 先 `ViewModelStore.clear()` 再释放根 Runtime 租约；
13. Runtime 世代变化创建新 ViewModelStore 和 NavController；
14. 三个 Worker 的完整正式操作均在 `withRuntime` 租约内；
15. Worker 取消仍向上传播；
16. 没有在锁内执行模型网络等待；
17. 后续固定锁顺序被文档明确为 Backup 写锁→Maintenance Mutex→租约归零→Room close。

如发现死锁、租约泄漏、旧 DAO 复活、半关闭实例暴露或取消后无法重开，结论必须为 `FAIL`。

## 七、JVM 专项与全量测试

执行：

```powershell
.\gradlew.bat --stop
.\gradlew.bat :app:testDebugUnitTest --tests "com.elio.jianyu.runtime.RuntimeLeaseRegistryTest" --stacktrace
.\gradlew.bat :app:testDebugUnitTest --stacktrace
```

记录：

- 专项测试数量；
- 全量测试数量；
- 通过、失败、跳过；
- 失败测试名和关键堆栈；
- 是否存在测试挂起或死锁。

不得把“任务成功”推断为某个测试类实际执行；必须核对 XML：

```powershell
Get-ChildItem app/build/test-results/testDebugUnitTest -Filter '*.xml' |
  Select-String -Pattern 'RuntimeLeaseRegistryTest|tests=|failures=|errors=|skipped='
```

## 八、编译、Lint 与构建

逐项执行并记录真实结果：

```powershell
.\gradlew.bat :app:compileDebugKotlin --stacktrace
.\gradlew.bat :app:lintDebug --stacktrace
.\gradlew.bat :app:assembleDebug --stacktrace
.\gradlew.bat :app:assembleRelease --stacktrace
.\gradlew.bat :app:assembleDebugAndroidTest --stacktrace
```

检查：

- Kotlin/Java 编译无错误；
- Lint 无阻断错误；
- Debug/Release/AndroidTest APK 真实生成；
- Release/R8 无新增阻断警告；
- 没有因本 PR 引入 Native ABI；
- 没有把测试类打入生产 APK。

## 九、API 26/28 Runtime Lifecycle 专项

至少在 API 26 和 API 28 各执行一次：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.JianyuRuntimeLifecycleDatabaseTest

.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.RuntimeMaintenanceHostTest
```

若同一时间只连接一个设备，分别启动对应 API 模拟器后执行并记录设备序列号。

必须从 XML 或 instrumentation 输出确认以下用例真实执行：

- 维护等待现有租约；
- 旧数据库关闭；
- 新 Runtime/数据库实例不同；
- 关闭前数据重开后仍存在；
- 新 Repository 可继续写入；
- 旧 Repository 返回存储失败；
- `whileClosed` 异常后重开；
- 闭库期间取消后重开；
- `afterReopen` 失败后 Runtime 仍可用；
- 维护期间同步 `get()` 不返回旧 Runtime；
- Compose 维护页面不创建正常 App 内容；
- Unavailable 页面提供稳定重试入口。

如果 API 26 或 API 28 缺失，最终结论不得为 `PASS`，应为 `INSUFFICIENT_EVIDENCE` 或 `PASS_WITH_NOTES`，并明确缺失项。

## 十、全量设备回归

在主验收设备执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --stacktrace
```

核对 XML 总数，重点回归：

- Room v1→v12 连续迁移；
- Issue 创建和恢复；
- Run 与 Pending Message；
- Cross Discussion；
- Stage Advancement；
- Draft 与 Artifact；
- Material 与 Personal Context；
- AudioAsset 创建、生成和删除；
- PR09-12 Archive/Trash/Purge；
- Settings 和导航；
- UI 自动化标签契约。

不得只执行新增测试后宣称全量设备测试通过。

## 十一、External Process Recovery 两阶段

必须沿用 PR #52 的直接 ADB 两阶段流程，不能依赖普通全量测试中的 Assume Skip。

先清理目标 App 数据，再分别执行 Step 1 和 Step 2。记录：

- Instrumentation runner；
- 两条精确 `adb shell am instrument` 命令；
- Step 1 输出；
- App 进程终止/重建证据；
- Step 2 输出；
- 最终 PASS/FAIL。

如无法执行，明确标记 `NOT_RUN`，最终结论不能写完整 PASS。

## 十二、并发和取消压力验证

新增专项至少循环执行 20 次：

```powershell
1..20 | ForEach-Object {
  .\gradlew.bat :app:connectedDebugAndroidTest `
    -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.JianyuRuntimeLifecycleDatabaseTest
  if ($LASTEXITCODE -ne 0) { throw "Runtime lifecycle iteration $_ failed" }
}
```

观察：

- 是否挂起；
- 是否出现数据库锁死；
- 是否出现 `Cannot access database on a closed connection`；
- 是否出现租约计数负数；
- 是否出现旧 Runtime 被重新发布；
- 是否存在测试间污染。

压力循环不是性能基准，只用于发现时序竞态。

## 十三、日志和隐私检查

检查 Logcat、测试输出和代码：

```powershell
adb logcat -d | Select-String -Pattern 'password|api.?key|root.?key|absolute path|user content|database path'
git grep -n "cause.message\|stackTraceToString" -- app/src/main/java/com/elio/jianyu
```

确认新增 Runtime UI、状态、自动化标签、Worker 错误和日志不包含：

- 用户正文或标题；
- API Key；
- 密码；
- 文件绝对路径；
- 数据库路径；
- 原始异常消息；
- Snapshot 或备份敏感元数据。

## 十四、GitHub CI 核对

锁定最终 Head，读取该 Head 对应的全部 GitHub Actions：

- Secret Scan；
- Android UI Test Compile；
- Android CI；
- 仓库实际配置的其他必需检查。

记录 Run ID、Run Number、Conclusion 和 URL。

旧 Head 的成功结果不能替代最终 Head。

## 十五、工作区终检

执行：

```powershell
git status --short
git diff --exit-code
git diff --cached --exit-code
git rev-parse HEAD
git rev-parse origin/refactor/pr-09-13b-runtime-lifecycle-prep
```

必须确认：

- 工作区无修改；
- 无未跟踪文件；
- 无测试生成文件被纳入 Git；
- Local/Remote Head 仍与验收目标一致。

## 十六、报告格式

输出 Markdown 报告，至少包括：

1. 最终结论：`PASS` / `PASS_WITH_NOTES` / `FAIL` / `INSUFFICIENT_EVIDENCE`；
2. PR、Base、Branch、Expected/Local/Remote Head；
3. 环境；
4. Diff 和禁止范围；
5. Room v12、Schema、Manifest/XML 检查；
6. JVM 专项与全量结果；
7. Compile/Lint/Debug/Release/AndroidTest 构建结果；
8. API 26 结果；
9. API 28 结果；
10. 全量 Instrumentation 结果；
11. External Process Recovery 结果；
12. 并发压力结果；
13. 日志与隐私检查；
14. GitHub CI；
15. 未验证项；
16. 已知风险；
17. 工作区终检。

判定规则：

- 任一数据库数据丢失、旧 DAO 复活、重开失败未进入 Unavailable、取消后数据库保持关闭、死锁、Room/Schema 越界或全量回归失败：`FAIL`；
- API 26/28、全量设备或 External Process Recovery 缺失：不得写完整 `PASS`；
- 所有要求均有真实证据且通过，才可写 `PASS`。

完成后只提交验收报告给远端开发对话，不修改任何仓库文件。