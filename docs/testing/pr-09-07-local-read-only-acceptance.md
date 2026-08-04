# PR09-07 本地 AI 严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 Draft PR #38 做独立、严格、只读验收。

目标：验证 PR09-07 的持久化 ExecutionRun 状态机、Room v8、单/多 Skill 顺序执行、流式原位更新、停止、预算、部分成功、子 Run 重试、进程恢复和基础执行工作区。

## 一、严格只读纪律

全过程只允许：

- 拉取、检出、读取；
- 构建、测试、Lint、APK/R8；
- 模拟器或真机 Instrumentation；
- adb 安装、启动、强停、读取日志和数据库检查；
- 记录环境、命令、退出码和关键日志。

禁止：

- 修改任何源码、测试、文档、配置或生成文件；
- 自动格式化；
- 更新依赖；
- 创建 Commit；
- 推送、变基、合并；
- 修改 Draft/Ready 状态；
- 删除分支；
- 在失败后自行修复。

发现问题后，只向远端开发对话报告：精确 Head、命令、退出码、关键日志、复现步骤、可能原因和最终工作区状态。

## 二、目标与基线

```text
仓库：https://github.com/elio-zwd/AI-Skill-Roundtable
PR：#38
分支：feat/pr-09-07-execution-run
清理后 Base：main@872876e7ff626ef7b5860bbce220919f7190a34f
原始等价基线：85e439508a060e7b4d4a0446ec5e5ecc0709107a
```

PR #37 既有绿色基线：

```text
JVM：239 / 239
RoomJianyuRepositoryDatabaseTest：17 / 17
全量 Instrumentation：101 / 101
Lint Error：0
Debug / Release：通过
Room：v7
```

验收开始时用 PR #38 当前远端 Head 作为唯一目标，不使用本文档提交时的历史 Head。

## 三、环境记录

记录原始输出：

```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber,OSArchitecture
$PSVersionTable.PSVersion
java -version
git --version
adb version
adb devices -l
Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
```

同时记录：JDK、Gradle Wrapper、Android SDK、Build Tools、设备 ID、设备型号、API Level、ABI。

## 四、精确检出与只读门禁

```powershell
git fetch origin --prune
git checkout feat/pr-09-07-execution-run
git pull --ff-only origin feat/pr-09-07-execution-run
git status --short
git rev-parse HEAD
git rev-parse origin/main
git merge-base HEAD origin/main
git rev-list --left-right --count origin/main...HEAD
git diff --name-status origin/main...HEAD
git diff --check origin/main...HEAD
git diff --exit-code
```

若安装 `gh`，额外执行：

```powershell
gh pr view 38 --repo elio-zwd/AI-Skill-Roundtable --json state,isDraft,headRefName,headRefOid,baseRefName,mergeable
```

必须确认：

- Head 与 PR #38 当前 `headRefOid` 完全一致；
- PR 保持 Draft；
- 分支名正确；
- 工作区干净；
- 没有本地提交或未跟踪修改。

## 五、基础构建与静态门禁

```powershell
.\gradlew.bat --stop
.\gradlew.bat :app:clean
pwsh -NoProfile -File tools/check-app-identity.ps1
.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:compileDebugAndroidTestKotlin
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
```

记录每条命令退出码，不得只写“通过”。

## 六、定向 JVM 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*ExecutionStateMachineTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*ExecutionBudgetPolicyTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*ExecutionErrorMapperTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*ExecutionContextBuilderTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*ExecutionRunCoordinatorTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*IssueExecutionArchitectureTest"
.\gradlew.bat :app:testDebugUnitTest --tests "*ExecutionTelemetryRedactionTest"
```

重点核对：

- `COMPLETED` 不由新链产生；
- 无成功但仍有 active 成员时保持 `RUNNING`；
- OPTIONAL 不占用 REQUIRED 预留；
- `CancellationException` 不转普通失败；
- 同批参与者不读取本批其他输出；
- `roundIndex` 不使用 Stage `sequenceIndex`；
- 无 Key 时不创建 Pending；
- A 成功/B 失败后只重试 B；
- 停止后迟到片段不能覆写消息；
- `keyId/key_id` 被脱敏。

## 七、Room v8 与 Migration

确认：

```text
Room version = 8
7.json 保留
8.json 存在且由真实 Room 编译导出
Migration 连续：1→2→3→4→5→6→7→8
无 destructive migration
```

运行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.ExecutionRuntimeMigrationTest

.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.ExecutionRuntimeDatabaseTest
```

验证：

- v7 活跃 Run 确定性迁移为 `retryable/process_interrupted`；
- 旧根预算 `usedApiCalls=0`、`closed=true`，既不伪造历史消耗，也不能恢复免费额度；
- 参与者状态和预算外键正确；
- 失败事务不残留子 Run、快照或状态；
- 并发消费不超过预算；
- `PRAGMA foreign_key_check` 为 0；
- Pending 原位关闭且部分文本保留。

## 八、Compose 工作区

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.execution.IssueExecutionScreenTest
```

人工验证：

- Issue 深链进入同一个 NavHost 内的执行工作区；
- 打开页面只恢复数据库，不调用网络、不创建 Run、不创建 Pending；
- 展示 Issue、Stage、Run、参与者顺序、成员状态、流式文本、预算；
- Stop、收敛中断、重试失败成员按钮行为正确；
- 无 Key、离线、限流、预算耗尽、存储失败文案可区分；
- 系统返回与顶部返回一致；
- 四个一级目的地没有变化；
- 360dp、200% 字号、明暗主题可用；
- TalkBack 环境具备时验证语义与焦点顺序。

## 九、全量设备回归

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

必须报告：

```text
全量总数
通过数
失败数
跳过数
PR09-07 新增数
```

PR #37 之后既有全量基线是 101/101，不能用“基线已有失败”放行。

## 十、外部强停两阶段验收

使用测试提供的 Fake Gateway 或确定性设备夹具，不调用真实 Gemini，不写入生产 Key。

阶段一：

1. 创建 Run；
2. A 成功；
3. B 处于 streaming 且已有部分文本；
4. C queued；
5. 记录 Run、成员状态、Pending Message、预算和调用次数；
6. 执行：

```powershell
adb -s <DEVICE_ID> shell am force-stop com.elio.jianyu
```

阶段二：

1. 重新启动 App；
2. 确认没有自动网络调用；
3. 确认没有第二个 Run、Stage 或 Pending；
4. A 保持成功且 Message 数量仍为 1；
5. B 部分文本保留、Pending 已收敛；
6. B/C 显示可恢复；
7. 已消费/预留预算不归零；
8. 用户明确重试后只执行 B/C；
9. A 不重复；
10. `foreign_key_check` 为 0。

记录 Fake Gateway 的真实调用顺序和次数。

## 十一、安全检查

在仓库差异、构建产物和测试日志中检查：

- 无 API Key、Header、完整 Prompt、资料正文；
- 无硬编码生产 Key；
- 不读取根目录 `.env`；
- Run、预算、错误表不保存 Key 或可逆 Key 标识；
- 错误记录仅有稳定错误码与安全摘要；
- `CancellationException` 原样传播；
- Repository 不调用网络；
- UI、Screen、Coordinator 不访问 DAO；
- 新 Issue 不调用旧 `RoundtableOrchestrator` 或 `RoundtableDatabaseGateway`。

## 十二、收尾干净性

```powershell
git status --short
git diff --exit-code
git rev-parse HEAD
```

最终工作区必须完全干净，Head 必须仍等于开始时锁定的 PR #38 `headRefOid`。

## 十三、报告格式

按以下顺序输出：

1. 最终结论：PASS / FAIL；
2. 精确 Head；
3. OS、PowerShell、Git、JDK、Gradle、Android SDK、adb、设备；
4. 每条命令与退出码；
5. JVM 总数与定向测试；
6. Room v8 / Migration / Schema；
7. Instrumentation 总数、通过、失败、跳过、新增；
8. Fake Gateway 单、多 Skill、流式、停止、迟到片段、部分成功、全部失败、无 Key、离线、限流、预算、重试；
9. 两阶段外部强停原始证据；
10. UI 与无障碍；
11. 安全检查；
12. 工作区最终状态；
13. 失败项、复现步骤和可能原因。

不得自行修改、提交、推送、变基、标记 Ready 或合并。所有修复返回 PR09-07 远端开发对话完成。
