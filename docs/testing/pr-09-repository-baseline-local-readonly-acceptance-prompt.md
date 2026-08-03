# 见域 Repository 基线设备测试修复：本地 AI 严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 Draft PR 进行严格只读验收。

目标分支：

```text
fix/pr-09-repository-baseline-instrumentation
```

目标：

```text
验证两项 Repository 基线 Instrumentation 失败已经真实修复，
并确认完整 RoomJianyuRepositoryDatabaseTest 与全量设备测试零失败。
```

本地 AI 只能读取、构建、安装、运行测试和输出报告。

严禁：

```text
修改任何源码、测试、文档、配置或 Schema
运行自动格式化
创建或修改 Commit
推送或强制推送
变基、合并、关闭 PR、标记 Ready
删除分支
修改 Git 配置
启动 PR09-07
以本地修补代替反馈失败
```

发现问题时，只输出原始日志、复现步骤、失败方法、退出码、关键堆栈和根因线索，不得自行修改。

---

## 一、记录环境

在 PowerShell 7 中执行并记录完整输出：

```powershell
Get-CimInstance Win32_OperatingSystem |
  Select-Object Caption, Version, BuildNumber, OSArchitecture

$PSVersionTable.PSVersion
git --version
java -version
adb version
adb devices -l
Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
```

要求：

1. 使用 JDK 17；
2. 只能有一个目标 Android 设备在线；
3. 记录设备 ID、型号、Android 版本和 API Level；
4. 设备离线、多设备冲突或 JDK 错误时停止测试并报告，不修改工程。

设备信息：

```powershell
$Device = (adb devices | Select-String "`tdevice$").ToString().Split("`t")[0]
adb -s $Device shell getprop ro.product.model
adb -s $Device shell getprop ro.build.version.release
adb -s $Device shell getprop ro.build.version.sdk
```

---

## 二、同步并锁定远端精确 Head

```powershell
git fetch origin --prune
git checkout fix/pr-09-repository-baseline-instrumentation
git pull --ff-only origin fix/pr-09-repository-baseline-instrumentation

$ExpectedHead = git rev-parse origin/fix/pr-09-repository-baseline-instrumentation
$ActualHead = git rev-parse HEAD

Write-Host "ExpectedHead=$ExpectedHead"
Write-Host "ActualHead=$ActualHead"

if ($ActualHead -ne $ExpectedHead) {
    throw "本地 HEAD 与远端目标分支不一致"
}

git status --short
git branch --show-current
git log -8 --oneline --decorate
git merge-base HEAD origin/main
git rev-parse origin/main
git diff --name-status origin/main...HEAD
git diff --stat origin/main...HEAD
git diff --check origin/main...HEAD
```

门禁：

1. 分支必须精确为 `fix/pr-09-repository-baseline-instrumentation`；
2. `HEAD` 必须等于远端目标分支 Head；
3. 初始 `git status --short` 必须无输出；
4. 净差异只能涉及：

```text
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt
docs/planning/pr-09-repository-baseline-fix-plan.md
docs/testing/pr-09-repository-baseline-local-readonly-acceptance-prompt.md
```

5. 不得出现生产代码、Entity、Migration、Database version、导航、Skill Catalog、ViewModel、Gemini、音频或 `app/schemas/` 修改；
6. `git diff --check` 必须退出码 0。

如远端分支在测试期间发生变化，停止验收，记录旧 Head 和新 Head，等待远端开发对话重新锁定，不继续沿用旧结果。

---

## 三、静态差异与语义核验

完整阅读：

```text
AGENTS.md
README.md
docs/planning/pr-09-repository-baseline-fix-plan.md
docs/planning/pr-09-03-repository-recovery-plan.md
docs/planning/pr-09-03-interface-handoff.md
app/src/androidTest/java/com/elio/jianyu/data/RoomJianyuRepositoryDatabaseTest.kt
app/src/main/java/com/elio/jianyu/data/IssueExecutionRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/main/java/com/elio/jianyu/data/LifecycleRecoveryRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
```

确认：

1. Run 合法冲突请求使用相同 `idempotencyKey`、不同 Run ID；
2. 合法冲突请求的全部参与者 `runId` 与新 Run ID 一致；
3. 合法不同 payload 预期返回 `RepositoryError.IdempotencyConflict`；
4. 独立关系错配测试预期返回 `RepositoryError.ConstraintViolation`；
5. 错配请求不写入新 Run 或孤儿参与者；
6. 原 Run 与原参与者保持不变；
7. 生产 `require(command.participants.all { it.runId == command.run.id })` 未删除、未绕过；
8. 打开空数据库测试返回 `NotFound`，并验证 Issue、Stage、Lifecycle 均未创建；
9. 关闭数据库测试在 `close()` 前真实访问 `openHelper.writableDatabase` 并断言 `isOpen == true`；
10. 关闭后断言 `isOpen == false`，恢复返回 `StorageFailure`；
11. 内存数据库与文件数据库均覆盖关闭语义；
12. `SQLiteException`、`SQLiteConstraintException` 和 `CancellationException` 语义分别受测试保护；
13. 没有删除旧测试、降低断言或吞掉异常；
14. 没有生产代码修改。

记录 `RoomJianyuRepositoryDatabaseTest` 的实际 `@Test` 方法数量。当前静态预期为 17 项；以目标 Head 的源码和 Runner 实际输出为最终证据。

---

## 四、停止旧进程并清理构建输出

```powershell
.\gradlew.bat --stop
Write-Host "gradle_stop_exit=$LASTEXITCODE"

.\gradlew.bat :app:clean
Write-Host "clean_exit=$LASTEXITCODE"
```

仅清理 Gradle 构建产物，不运行 `git clean`，不删除未跟踪用户文件。

清理后再次确认：

```powershell
git status --short
```

源码工作区必须仍然干净。

---

## 五、远端要求的基础构建与 JVM 验证

逐条执行，每条记录命令、开始时间、结束时间、退出码和关键摘要：

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat compileDebugAndroidTestKotlin
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

不得用某一项通过推断其他项通过。

报告 JVM 测试的实际总数、通过数、失败数和跳过数；报告 Lint 错误与警告摘要；报告 Debug 和 Release APK 实际路径。

---

## 六、设备隔离

测试前记录并清理当前见域测试包：

```powershell
adb -s $Device shell pm path com.elio.jianyu
adb -s $Device shell pm path com.elio.jianyu.test

adb -s $Device uninstall com.elio.jianyu.test
adb -s $Device uninstall com.elio.jianyu
```

包不存在时允许 `uninstall` 返回非零，但必须记录原始输出。不得卸载旧包 `com.elio.skillroundtable`，除非另一个明确验收任务要求。

---

## 七、先运行两个原失败方法

### 7.1 Run 幂等冲突

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryDatabaseTest#runAndParticipantsAreAtomicAndIdempotencyKeyDetectsConflict

Write-Host "run_conflict_exit=$LASTEXITCODE"
```

必须记录：

- 测试总数；
- 通过数；
- 失败数；
- 退出码；
- 测试方法全名；
- 若失败，完整关键堆栈和断言位置。

### 7.2 关闭数据库恢复错误

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryDatabaseTest#closedDatabaseReturnsStorageFailureInsteadOfEmptyIssue

Write-Host "closed_database_exit=$LASTEXITCODE"
```

同样记录测试统计、退出码和关键日志。

如果当前 Gradle Runner 不接受 `Class#method` 格式，只允许调整 Runner 过滤参数格式，例如使用引号或 Runner 实际支持的等价方法过滤；不得修改测试源码。报告中必须写明最终实际使用的命令。

任何目标方法失败时，停止“通过”结论，但继续收集能够安全执行的诊断信息；不得自行修复。

---

## 八、运行新增边界测试

按 Runner 支持格式分别运行或在完整类中确认以下方法：

```text
runParticipantRelationMismatchReturnsConstraintViolationWithoutWrites
openEmptyDatabaseReturnsNotFoundWithoutCreatingDomainRows
closedFileDatabaseReturnsStorageFailureInsteadOfEmptyIssue
transactionExecutionMapsSQLiteExceptionToStorageFailure
transactionExecutionMapsSQLiteConstraintToConstraintViolation
transactionExecutionPropagatesCancellationException
```

每项必须真实通过，不能只依赖编译成功。

---

## 九、运行完整 Repository 数据库测试类

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryDatabaseTest

Write-Host "repository_class_exit=$LASTEXITCODE"
```

要求：

1. 完整类失败数为 0；
2. 当前静态预期 17 项测试全部执行；
3. 原两项失败和新增边界测试全部包含在结果中；
4. `foreignKeyCheckRemainsClean` 通过；
5. 不存在测试被意外跳过或过滤遗漏。

如 Runner 输出数量不是 17，必须对照源码逐项核对，解释过滤、参数或 Runner 行为，不得直接放行。

---

## 十、运行全量 Instrumentation

在确保只有一个目标设备在线后执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
Write-Host "full_instrumentation_exit=$LASTEXITCODE"
```

必须记录：

```text
总数
通过
失败
跳过
退出码
设备 ID
报告路径
```

完成条件：

```text
失败数 = 0
退出码 = 0
```

不得继续使用“Base 既有失败”“PASS WITH BASELINE FAILURES”或类似备注放行。两项原失败必须真实变为通过。

重点确认：

- Repository 原两项失败已通过；
- 新增边界测试已通过；
- 导航与 Skill Catalog 测试无回归；
- 进程恢复、Migration、DAO、身份隔离和其他既有 Instrumentation 无新增失败。

---

## 十一、Room v7、Schema 与数据库完整性

执行：

```powershell
Select-String -Path app\src\main\java\com\elio\jianyu\data\RoundtableDatabase.kt `
  -Pattern "version\s*=\s*7"

Get-ChildItem app\schemas -Recurse -Filter 8.json

git diff --exit-code origin/main...HEAD -- app/schemas
git status --short -- app/schemas
```

要求：

1. Room 版本仍为 v7；
2. 不存在 `8.json`；
3. `app/schemas/` 相对 `origin/main` 无差异；
4. 构建后没有生成未提交 Schema；
5. `foreignKeyCheckRemainsClean` 已在设备测试中真实通过。

---

## 十二、Secret scan 与差异检查

```powershell
pwsh.exe -NoProfile -File .\tools\check-secrets.ps1 -IncludeHistory
Write-Host "secret_scan_exit=$LASTEXITCODE"

git diff --check origin/main...HEAD
git diff --name-status origin/main...HEAD
git status --short
git diff --exit-code
git diff --cached --exit-code
```

要求：

- Secret scan 退出码 0；
- `git diff --check` 退出码 0；
- 最终工作区无源码或文档修改；
- `git status --short` 无输出；
- 没有 staged 或 unstaged diff；
- 最终 `HEAD` 仍等于开始时记录的 `$ExpectedHead`。

最终 Head 核验：

```powershell
$FinalHead = git rev-parse HEAD
Write-Host "ExpectedHead=$ExpectedHead"
Write-Host "FinalHead=$FinalHead"

if ($FinalHead -ne $ExpectedHead) {
    throw "验收期间 HEAD 发生变化"
}
```

---

## 十三、最终报告格式

输出中文严格只读验收报告，至少包含：

1. 最终结论：`PASS` 或 `FAIL`；
2. 仓库、PR、分支、Base SHA、精确 Head SHA；
3. 操作系统、PowerShell、Git、JDK、Gradle、ADB、设备与 API Level；
4. 初始与最终工作区状态；
5. 差异文件清单；
6. 静态根因核对；
7. `compileDebugKotlin` 结果；
8. JVM 测试总数与结果；
9. `compileDebugAndroidTestKotlin` 结果；
10. Lint、Debug APK、Release/R8 结果；
11. 第一项目标测试命令、统计、退出码；
12. 第二项目标测试命令、统计、退出码；
13. 六项新增边界测试结果；
14. 完整 `RoomJianyuRepositoryDatabaseTest` 总数与结果；
15. 全量 Instrumentation 总数、通过、失败、跳过和退出码；
16. Room v7、无 `8.json`、Schema 无差异；
17. `PRAGMA foreign_key_check` 结果；
18. 导航与 Skill Catalog 回归结果；
19. Secret scan 和 `git diff --check`；
20. 最终 Head 精确不变；
21. 尚未验证项；
22. 失败项、完整复现步骤和关键日志。

必须严格区分：

```text
已实际执行并通过
已实际执行但失败
GitHub CI 已通过
仅完成静态检查
尚未验证
```

只有以下条件全部满足才可输出 `PASS`：

```text
两个原失败方法通过
新增边界测试通过
完整测试类零失败
全量 connectedDebugAndroidTest 零失败
JVM、Lint、Debug、Release 均通过
Room v7 且 Schema 无变化
foreign_key_check 无错误
工作区干净
Head 精确不变
```

即使验收 PASS，也不得标记 Ready、合并、删除分支或启动 PR09-07；这些动作必须等待用户明确授权。
