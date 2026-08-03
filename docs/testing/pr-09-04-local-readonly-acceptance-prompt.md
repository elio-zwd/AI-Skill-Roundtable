# PR09-04 本地 AI 严格只读验收 Prompt

你现在负责 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` Draft PR #36 的本地严格只读验收。

目标 PR：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable/pull/36
```

目标分支：

```text
feat/pr-09-04-jianyu-navigation-shell
```

基线：

```text
main@78abf30b60d863ce0ac29323546e61971d50c9c9
```

## 一、最高优先级纪律

全过程只允许读取、拉取、检出、构建、测试、安装、启动、查询和记录结果。

严禁：

- 修改任何源文件、测试、文档、配置、锁文件或生成物清单；
- 自动格式化或 IDE 自动修复；
- 创建 Commit；
- 推送；
- 变基；
- 合并；
- 修改 PR Draft / Ready 状态；
- 修改分支引用；
- 删除分支；
- 向远端写评论或 Review；
- 使用测试失败作为理由直接修改代码。

若构建工具产生受 Git 跟踪文件变化，立即停止，记录变化来源，不得提交，并在报告中判为工作区污染。

## 二、验收开始前记录环境

记录真实原始输出：

```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object Caption, Version, BuildNumber, OSArchitecture
$PSVersionTable.PSVersion
git --version
java -version
./gradlew --version
adb version
adb devices -l
```

同时记录：

- 验收开始时间和结束时间，使用 ISO 8601；
- Android SDK Platform / Build Tools；
- 模拟器或真机型号、Android API、分辨率和 density；
- TalkBack 版本；
- 当前字体缩放和显示缩放；
- 是否具备 GitHub CLI；
- 是否具备网络访问 GitHub Actions。

## 三、解析并锁定 PR 精确 Head

优先使用 GitHub CLI 只读解析：

```powershell
$expectedHead = gh pr view 36 --repo elio-zwd/AI-Skill-Roundtable --json headRefOid --jq '.headRefOid'
$expectedBase = gh pr view 36 --repo elio-zwd/AI-Skill-Roundtable --json baseRefOid --jq '.baseRefOid'
$expectedState = gh pr view 36 --repo elio-zwd/AI-Skill-Roundtable --json isDraft,state,mergeable
$expectedHead
$expectedBase
$expectedState
```

若无 GitHub CLI，只读打开 PR 页面并记录页面显示的最新 Head SHA；不得凭 Prompt 中历史 SHA 猜测。

检出：

```powershell
git fetch origin --prune
git checkout feat/pr-09-04-jianyu-navigation-shell
git pull --ff-only origin feat/pr-09-04-jianyu-navigation-shell
git status --short
git rev-parse HEAD
git merge-base HEAD origin/main
git rev-list --left-right --count origin/main...HEAD
```

门禁：

- `git rev-parse HEAD` 必须等于 `$expectedHead`；
- merge-base 必须是本次 PR 实际合法基线或其后同步的最新 `main`；
- 工作区必须干净；
- PR 必须仍为 Draft；
- 不满足任一项则停止后续验收并报告。

## 四、差异范围与禁止区审计

执行：

```powershell
git diff --name-status origin/main...HEAD
git diff --stat origin/main...HEAD
git diff --check origin/main...HEAD
git log --oneline --decorate origin/main..HEAD
```

确认没有修改以下禁止区：

```text
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/data/IssueExecutionRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/PendingMessageRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/ResourceRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/UsageRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/LifecycleRecoveryRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt
app/src/main/java/com/elio/jianyu/data/ChatSession.kt
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/schemas/
```

同时确认：

- 没有修改 PR09-05 Catalog / Skill 页面内部文件；
- 没有修改 Gemini 调度或音频任务；
- 没有引入最终 Logo、正式品牌资产或最终主题重做；
- `MainActivity.kt` 仍然只挂载主题与 `MainAppContent`；
- Room 版本仍为 v7；
- 没有 Schema 漂移。

## 五、Secret 与敏感内容扫描

执行仓库已有 Secret scan，并额外只读搜索：

```powershell
git grep -n -I -E '(AIza[0-9A-Za-z_-]{20,}|BEGIN (RSA|OPENSSH|EC) PRIVATE KEY|api[_-]?key\s*[:=]\s*["''][^"'']+["''])' HEAD -- .
git grep -n -I -E '(prompt=|apiKey=|materialBody=|artifactBody=|personalContext=)' HEAD -- app/src/main/java/com/elio/jianyu/ui app/src/main/AndroidManifest.xml
```

第二条允许测试代码中的拒绝断言，但生产 Route、Manifest 和深链中不得携带这些敏感字段。

## 六、构建、JVM、Lint 与 APK

从干净工作区执行，逐条记录命令、退出码和关键日志：

```powershell
./gradlew --stop
./gradlew :app:compileDebugKotlin --stacktrace
./gradlew :app:testDebugUnitTest --stacktrace
./gradlew :app:lintDebug --stacktrace
./gradlew :app:assembleDebug --stacktrace
./gradlew :app:assembleRelease --stacktrace
```

如仓库 CI 使用额外 Release signing / R8 / Schema 命令，读取 `.github/workflows/` 后按同等参数执行，不得自行降低门禁。

必须单独核对：

- `AppDestinationTest`；
- `JianyuNavigationRoutesTest`；
- `JianyuDeepLinkManifestTest`；
- `IssuesNavigationLoaderTest`；
- `IssuesUiStateTest`；
- `JianyuNavigationArchitectureTest`；
- 原有 `UiArchitectureGuardrailTest`；
- 全量现有 JVM 测试。

不得只运行新增测试后声称全量通过。

## 七、Instrumentation 与 Compose UI Test

设备在线后执行：

```powershell
./gradlew :app:connectedDebugAndroidTest --stacktrace
```

重点确认以下测试类真实执行：

```text
AppBottomNavigationTest
AppNavHostTest
JianyuNavigationShellScreenTest
MainNavigationRestorationTest
```

如全量设备测试受历史无关失败阻塞，仍需：

1. 记录全量失败原始日志；
2. 单独执行上述 PR09-04 测试类；
3. 明确区分 PR09-04 失败与历史基线失败；
4. 不修改测试或降低断言。

## 八、四个一级目的地人工验收

冷启动后确认默认首页。

按顺序点击：

```text
首页 → 议题 → Skill → 资料与成果 → 首页
```

逐项验证：

- 底部导航精确只有四项；
- 顺序精确；
- 设置不在底部导航；
- 旧圆桌、智囊大厅、音频库不在底部导航；
- 每一项可进入对应页面壳；
- 当前项有可被辅助技术识别的选中状态；
- 重复点击当前项不会在系统返回时出现重复页面；
- 切换目的地后返回各自已保存状态。

记录每一步的当前 Route、可见标题和返回结果。

## 九、全局设置与旧设置链

从首页、议题、Skill、资料与成果分别打开全局设置并返回，验证：

- 顶部设置入口存在且可读；
- 系统返回和顶部返回一致；
- 返回原一级目的地；
- API Key 入口可进入现有页面；
- Telemetry 入口可进入现有页面；
- API Key / Telemetry 原稳定 `testTag` 仍存在；
- 旧 Roundtable → API Key → Telemetry 返回链仍正确。

## 十、资料与成果 Tab

验证：

- 首次进入默认资料 Tab；
- 可切换成果 Tab；
- 两个 Tab 的选中状态不只依赖颜色；
- 从成果 Tab 切走再返回时状态按设计保存；
- Activity 重建后仍在原 Tab；
- 无效 `tab` 深链回退到资料；
- 页面不会创建资料、成果或访问 `ResourceLifecycleDao`。

## 十一、议题只读导航与恢复定位

准备至少三类 fixture：

```text
ACTIVE
ARCHIVED
TRASHED
```

每个 fixture 至少包含稳定 Issue ID；其中一个包含多个 Stage 和一个活跃或可恢复 Run。

验证：

- 议题页显示活跃、归档和回收站分区；
- 显示生命周期、当前 Stage 和可恢复 Run 数量；
- 空、加载、失败状态可区分；
- 点击 Issue 通过稳定 ID 打开恢复页；
- 指定 Stage 能定位到所属 Stage；
- 不属于 Issue 的 Stage 显示失败；
- 无效 ID 显示失败且不创建数据；
- Archived / Trashed Issue 能定位；
- 页面加载前后数据库业务行数不增加；
- 不发生 `saveIssue`、`createStage`、`createExecutionRun` 或生命周期写入。

可通过测试 fixture 前后 SQL/DAO 只读计数或已有 Repository 测试辅助证明，但不得直接修改生产数据库文件。

## 十二、旧 Route 兼容

分别通过测试或显式导航进入：

```text
roundtable
characters
audio-library
settings/api-keys
settings/telemetry
```

验证：

- 页面仍可打开；
- 不出现在新底部导航；
- 返回调用来源；
- 以下旧稳定标签未删除：

```text
new_session_button
chat_input
send_button
stop_button
retry_failed_characters_button
dismiss_failed_characters_button
character_hall
audio_library
api_key_manager
telemetry_screen
```

## 十三、外部深链验收

先确保 Debug APK 已安装，然后执行：

```powershell
adb shell am force-stop com.elio.jianyu
adb shell am start -W -a android.intent.action.VIEW -d "jianyu://resources?tab=artifacts" com.elio.jianyu
adb shell am force-stop com.elio.jianyu
adb shell am start -W -a android.intent.action.VIEW -d "jianyu://resources?tab=invalid" com.elio.jianyu
adb shell am force-stop com.elio.jianyu
adb shell am start -W -a android.intent.action.VIEW -d "jianyu://issues/<真实稳定IssueID>" com.elio.jianyu
adb shell am force-stop com.elio.jianyu
adb shell am start -W -a android.intent.action.VIEW -d "jianyu://issues/<真实稳定IssueID>?stageId=<所属稳定StageID>" com.elio.jianyu
adb shell am force-stop com.elio.jianyu
adb shell am start -W -a android.intent.action.VIEW -d "jianyu://issues/invalid%2Fid" com.elio.jianyu
adb shell am force-stop com.elio.jianyu
adb shell am start -W -a android.intent.action.VIEW -d "jianyu://skills/decision-reviewer" com.elio.jianyu
```

将尖括号内容替换为验收 fixture 中真实稳定 ID，不把尖括号原样执行。

验证：

- 冷启动进入正确页面；
- 成果和无效 Tab 行为正确；
- Issue / Stage 定位正确；
- 无效 Issue ID 显示失败；
- Skill 深链进入稳定详情接口或当前明确占位；
- 系统返回进入合理父级，而不是退出到错误页面；
- 深链过程不创建任何业务数据。

额外执行：

```powershell
adb shell dumpsys package com.elio.jianyu | Select-String -Pattern "jianyu|issues|skills|resources|BROWSABLE"
```

确认 Manifest 只暴露 `issues`、`skills`、`resources` 三个 host。

## 十四、Activity 与进程重建

Activity 重建：

1. 进入资料与成果；
2. 切换成果 Tab；
3. 执行旋转或 Activity recreate；
4. 验证仍为资料与成果、成果 Tab。

真实进程重建：

1. 分别停留在议题、Skill、资料与成果页面；
2. 使用开发者选项“不保留活动”或可复现的进程强停方案；
3. 通过系统任务恢复；
4. 验证恢复到稳定目的地或由 Android 明确重新冷启动首页；
5. 不得跳到错误旧页面；
6. 不得自动调用模型、创建数据或重复恢复写入。

必须把“Activity recreate”和“进程被杀后恢复”分开记录，不能用前者代替后者。

## 十五、360dp、200% 字号、横竖屏与 TalkBack

在 360dp 等效宽度设备执行：

- 默认字号；
- 字号 200%；
- 竖屏；
- 横屏或可调整窗口；
- 明色主题；
- 暗色主题。

验证：

- 四个关键导航文案不消失；
- “资料与成果”允许两行但不截断为不可理解文本；
- 设置按钮可点击；
- 标题和副标题不重叠；
- 页面内容可滚动；
- 主要动作不被底部导航遮挡；
- 切换窗口尺寸不丢失目的地和 Tab。

TalkBack 验证：

- 顺序为页面标题、设置/返回、页面内容、底部导航；
- 每个导航项读出名称和选中状态；
- 设置、返回、资料 Tab、成果 Tab 有明确语义；
- 选中状态不只靠颜色；
- 无重复或空白朗读节点阻断主要流程。

## 十六、PR09-05 Skill 接线状态

验收开始时读取 PR #35 与 PR #36：

- 若 PR #35 尚未合并，PR #36 的 Skill 页面应明确显示占位，并在报告中判定“正式 Skill 接线尚未完成”；不得因此假装整个 PR 已完成。
- 若 PR #35 已合并且 PR #36 已同步，验证正式 `OfficialSkillCatalogRoute`、同一 Catalog runtime、正式 Validator 和公共回调边界。
- 根导航不得复制 Catalog、筛选、详情、收藏、组合或 ViewModel。
- 打开详情、搜索、筛选或收藏不得自动记录最近使用。
- 根导航不得创建 `ExecutionRun` 或调用 Gemini。

## 十七、GitHub CI 复核

只读取当前精确 Head 对应的 Checks：

```powershell
gh pr checks 36 --repo elio-zwd/AI-Skill-Roundtable
```

记录：

- Workflow 名称；
- Run 编号；
- Job；
- 状态与结论；
- 对应 Head SHA；
- 失败步骤和关键日志。

不得复用旧 Head、被取消 Run 或 PR #35 的 CI 结果。

## 十八、结束时工作区干净性

执行：

```powershell
git status --short
git diff --exit-code
git diff --cached --exit-code
git rev-parse HEAD
```

必须满足：

- `git status --short` 无输出；
- 工作区和暂存区无差异；
- 最终 HEAD 仍等于验收开始锁定的 `$expectedHead`；
- 没有 Commit、Push、Merge 或分支修改。

## 十九、报告格式

最终报告必须包含：

1. 结论：`PASS`、`PASS WITH NOTES` 或 `FAIL`；
2. PR URL、Base、分支、精确 Head；
3. 操作系统、工具版本、设备参数；
4. 全部命令、退出码和关键原始日志；
5. 差异文件与禁止区审计；
6. Secret scan；
7. 编译、JVM、Lint、Debug、Release、R8、Schema；
8. Instrumentation 真实执行结果；
9. 四目的地、设置、Tab、旧 Route；
10. 返回栈、Activity 重建、进程重建；
11. 外部深链；
12. 360dp、200% 字号、横竖屏、明暗主题、TalkBack；
13. PR09-05 Skill 接线状态；
14. GitHub CI；
15. 未验证项；
16. 失败复现步骤与可能原因；
17. 最终工作区干净性。

若失败，只反馈证据、复现步骤和可能原因给远端开发对话；不得自行修改、提交、推送或合并。
