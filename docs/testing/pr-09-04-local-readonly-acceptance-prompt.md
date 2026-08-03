# PR09-04 最终集成本地 AI 严格只读验收 Prompt

你现在负责 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` Draft PR #36 的最终本地严格只读验收。

```text
PR：https://github.com/elio-zwd/AI-Skill-Roundtable/pull/36
分支：feat/pr-09-04-jianyu-navigation-shell
Base：main（PR09-05 已合并）
```

本轮验收对象已不是旧 Skill 占位页，而是：

- 四目的地导航壳：`首页 / 议题 / Skill / 资料与成果`；
- 已合并 PR09-05 的 44 项官方 Skill Catalog；
- `OfficialSkillCatalogRoute` 正式接线；
- 统一 `JianyuAppRuntime`、Catalog runtime、Repository 与官方 ID Validator；
- `jianyu://skills/{skillId}` 正式详情深链；
- 临时 `SkillPlaceholderRoute` 已删除。

## 一、最高优先级纪律

全过程只允许读取、拉取、检出、构建、测试、安装、启动、查询和记录结果。

严禁：

- 修改任何源码、测试、文档、配置、锁文件或受跟踪生成物；
- 自动格式化、IDE 自动修复；
- Commit、Push、Rebase、Merge；
- 修改 PR Draft / Ready 状态；
- 修改分支引用、删除分支；
- 向远端写评论或 Review；
- 因失败而直接改代码；
- 使用 `git reset --hard`、`git clean`、`git restore` 清理未知文件。

构建产生受跟踪文件变化时立即停止，记录来源并判为工作区污染。只允许删除能够证明由本次验收生成的临时截图、XML Dump 或脚本。

## 二、单设备硬门禁与环境记录

首先记录：

```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber,OSArchitecture
$PSVersionTable.PSVersion
git --version
java -version
./gradlew --version
adb version
adb devices -l
```

必须同时记录：

- 开始/结束时间（ISO 8601）；
- JDK、Gradle、Android SDK Platform、Build Tools；
- 唯一设备序列号、型号、API、分辨率、density；
- 字体缩放、显示缩放、TalkBack 版本；
- GitHub CLI 和 GitHub Actions 读取能力。

若 `adb devices -l` 存在多台在线设备，停止。不得混用真机和模拟器结果。

## 三、动态锁定 PR 精确 Head

优先执行：

```powershell
$expectedHead = gh pr view 36 --repo elio-zwd/AI-Skill-Roundtable --json headRefOid --jq '.headRefOid'
$expectedBase = gh pr view 36 --repo elio-zwd/AI-Skill-Roundtable --json baseRefOid --jq '.baseRefOid'
gh pr view 36 --repo elio-zwd/AI-Skill-Roundtable --json isDraft,state,mergeable

git fetch origin --prune
git checkout feat/pr-09-04-jianyu-navigation-shell
git pull --ff-only origin feat/pr-09-04-jianyu-navigation-shell
git rev-parse HEAD
git merge-base HEAD origin/main
git rev-list --left-right --count origin/main...HEAD
git status --short
```

门禁：

- `HEAD == $expectedHead`；
- PR 仍为 Open / Draft；
- Base 为当前 `main`；
- `main` 必须已包含 PR #35；
- PR 分支必须包含普通双父 Merge Commit `chore: 同步PR09-05到导航壳`；
- 工作区必须无输出。

任何一项不满足，停止后续验收。

## 四、差异与架构静态审计

执行：

```powershell
git diff --name-status origin/main...HEAD
git diff --stat origin/main...HEAD
git diff --check origin/main...HEAD
git log --oneline --decorate origin/main..HEAD
```

重点确认：

1. Room 仍为 v7；没有 Entity、DAO、Migration、Schema 漂移；
2. 未修改 Catalog JSON 数量和正式 ID；
3. `SkillPlaceholderRoute.kt` 已不存在；
4. `App.kt` 接入 `OfficialSkillNavigationRoute`；
5. `JianyuAppRuntime` 只创建一份 Catalog runtime 和一份共享 Repository；
6. Catalog 成功时，`RoomJianyuRepository` 使用同一 runtime 的正式 Validator；
7. Catalog 加载失败时保持拒绝策略，不允许任意 Skill ID；
8. Issues 与 Skill 页面消费同一 Repository；
9. UI/navigation 不直连 DAO；
10. 没有 Gemini 执行、ExecutionRun 创建、资料/成果写入或最终视觉重做。

执行只读搜索：

```powershell
git grep -n "SkillPlaceholderRoute\|SkillDetailPlaceholderRoute" HEAD -- app/src

git grep -n "createOfficialSkillCatalogRuntime\|officialSkillIdValidator\|OfficialSkillNavigationRoute" HEAD -- app/src/main/java

git grep -n -E "chatDao\(|coreDomainDao\(|resourceLifecycleDao\(|jianyuRepositoryDao\(" HEAD -- app/src/main/java/com/elio/jianyu/ui
```

预期：占位 Route 无结果；统一 runtime 与正式 Route 有结果；UI DAO 搜索无结果。

## 五、Secret 与深链敏感字段门禁

执行仓库 Secret scan，并额外执行：

```powershell
git grep -n -I -E '(AIza[0-9A-Za-z_-]{20,}|BEGIN (RSA|OPENSSH|EC) PRIVATE KEY|api[_-]?key\s*[:=]\s*["''][^"'']+["''])' HEAD -- .

git grep -n -I -E '(prompt=|apiKey=|materialBody=|artifactBody=|personalContext=)' HEAD -- app/src/main/java/com/elio/jianyu/ui app/src/main/AndroidManifest.xml
```

生产 Route、Manifest 和深链只能携带稳定 ID 或 Tab，不得携带 Prompt、API Key、资料正文、成果正文或个人背景正文。

## 六、构建、JVM、Lint 与 APK

逐条执行并记录命令、退出码、耗时和关键日志：

```powershell
./gradlew --stop
./gradlew :app:compileDebugKotlin --stacktrace
./gradlew :app:testDebugUnitTest --stacktrace
./gradlew :app:lintDebug --stacktrace
./gradlew :app:assembleDebug --stacktrace
./gradlew :app:assembleRelease --stacktrace
./gradlew :app:compileDebugAndroidTestKotlin --stacktrace
```

读取 `.github/workflows/`，按仓库 CI 的同等参数补做 Release signing、R8、包名、Migration 和 Schema 门禁，不得降低要求。

必须确认以下 JVM 测试实际执行：

```text
JianyuNavigationArchitectureTest
AppDestinationTest
JianyuNavigationRoutesTest
JianyuDeepLinkManifestTest
OfficialSkillCatalogArchitectureTest
CatalogOfficialSkillIdValidatorTest
OfficialSkillCatalogManifestTest
OfficialSkillCatalogQueryTest
OfficialSkillCatalogRiskSearchTest
OfficialSkillCombinationDraftValidatorTest
OfficialSkillPreferencesTest
IssuesNavigationLoaderTest
IssuesUiStateTest
UiArchitectureGuardrailTest
```

## 七、定向 Instrumentation

只在唯一在线设备上分别执行：

```powershell
./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.AppBottomNavigationTest" --stacktrace

./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.navigation.AppNavHostTest" --stacktrace

./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.skills.OfficialSkillCatalogScreenTest" --stacktrace

./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.MainNavigationRestorationTest" --stacktrace

./gradlew :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.JianyuNavigationShellScreenTest" --stacktrace
```

必须从 XML 报告记录每个测试类的：

```text
tests / failures / errors / skipped
```

不得只凭 `BUILD SUCCESSFUL` 猜测数量。

## 八、全量 Instrumentation 与 Base/Head A/B

执行：

```powershell
./gradlew :app:connectedDebugAndroidTest --stacktrace
```

记录测试总数、通过数、失败数和全部失败方法。

若仍只有以下两项 Room 测试失败：

```text
RoomJianyuRepositoryDatabaseTest#runAndParticipantsAreAtomicAndIdempotencyKeyDetectsConflict
RoomJianyuRepositoryDatabaseTest#closedDatabaseReturnsStorageFailureInsteadOfEmptyIssue
```

必须在同一设备、同一 Gradle 参数、同一安装清理口径下，对当前 Base `origin/main` 与精确 Head 分别运行这两个方法。只有 Base 与 Head 表现一致，才能写“PR #36 引入的新失败数为 0”。不得猜测 Android 或 SQLite 根因。

如果出现任何 Head 独有失败，结论必须为 FAIL。

## 九、四目的地与正式 Skill Catalog 人工验收

冷启动后按顺序操作：

```text
首页 → 议题 → Skill → 资料与成果 → 首页
```

验证：

- 底部精确只有四项且顺序正确；
- 设置不在底部导航；
- 旧圆桌、智囊大厅、音频库不在底部导航；
- 当前项有辅助技术可识别的选中状态；
- 重复点击当前目的地不产生重复返回层级；
- 切换后各自状态按设计恢复；
- Skill 页面展示正式“官方 Skill”界面，不出现“正在接入”占位文案；
- 总数显示 44；
- 搜索、筛选、收藏、最近、组合入口可操作；
- `office-document-productivity`、`original-expression-naturalizer`、`zhang_xuefeng` 可发现；
- 不存在 `academic-ai-evasion`；
- 查看详情不写入“最近使用”；
- 不可执行 Skill 不触发模型、运行或后台任务。

## 十、正式 Skill 深链

先安装 Debug APK，再执行：

```powershell
adb shell am force-stop com.elio.jianyu
adb shell am start -W -a android.intent.action.VIEW -d "jianyu://skills/zhang_xuefeng" com.elio.jianyu
```

验证：

- 冷启动直接打开张雪峰官方 Skill 详情；
- 详情使用 Catalog 正式 ID；
- 关闭详情或系统返回进入 Skill 列表；
- 不写入最近使用；
- 全局设置入口可进入并返回。

然后执行：

```powershell
adb shell am force-stop com.elio.jianyu
adb shell am start -W -a android.intent.action.VIEW -d "jianyu://skills/not-official" com.elio.jianyu
```

验证：

- 显示明确“未知官方 Skill ID”错误；
- 不打开伪造详情；
- 不保存组合、收藏或最近使用；
- 不调用模型、不创建 ExecutionRun；
- 系统返回合理。

同时继续验证：

```text
jianyu://resources?tab=artifacts
jianyu://resources?tab=invalid
jianyu://issues/<真实IssueID>
jianyu://issues/<真实IssueID>?stageId=<真实StageID>
```

确认 Manifest 只暴露 `issues`、`skills`、`resources` 三个 BROWSABLE host。

## 十一、设置、返回栈、重建与旧 Route

验证：

- 从四个一级目的地分别进入设置并返回原位置；
- Skill 详情返回 Skill 列表；
- API Key、Telemetry 入口和旧稳定 testTag 保留；
- `roundtable`、`characters`、`audio-library` 仍可显式进入，但不在底栏；
- 资料/成果 Tab 在 Activity recreate 后恢复；
- Skill 搜索、Section、筛选状态在 Activity recreate 后按实现恢复；
- “Activity recreate”和真实进程被杀后恢复分别记录；
- 重建不自动调用模型、不创建业务数据。

## 十二、360dp、200% 字号、横竖屏、明暗主题

在最终接线页面实际验证：

- 360dp 等效宽度；
- 字号 200%；
- 竖屏和横屏；
- 明暗主题；
- 四个底栏标签可理解；
- 官方 Skill 搜索框、筛选按钮、Section Tab、列表和详情对话框可操作；
- 无不可达按钮、严重遮挡或无限布局；
- 不能用旧三目的地截图替代当前页面证据。

## 十三、真实 TalkBack 门禁

必须启用真实 TalkBack，通过焦点移动与手势操作验证，不能只使用 UIAutomator/XML Dump。

至少验证：

- “首页 / 议题 / Skill / 资料与成果”朗读顺序和选中状态；
- 全局设置与返回按钮；
- 官方 Skill 搜索框、筛选按钮；
- 发现/收藏/最近/组合 Tab；
- Skill 卡片、收藏按钮、详情关闭；
- 深链详情的返回行为；
- 同一控件不重复朗读；
- 关键控件可被聚焦和激活。

无法真实执行时必须写“尚未验证”，不得将 uidump 写成 TalkBack PASS。

## 十四、最终 Git 清洁门禁

结束前执行：

```powershell
git rev-parse HEAD
git status --short
git diff --exit-code
git diff --cached --exit-code
git ls-remote origin refs/heads/feat/pr-09-04-jianyu-navigation-shell
```

要求：

- 本地 Head 仍等于验收开始锁定的 PR Head；
- 远端分支未移动；
- `git status --short` 无输出；
- 两个 diff 命令退出码均为 0。

## 十五、结论规则

### PASS

仅当精确 Head、CI、JVM、Lint、APK、AndroidTest、正式 Skill 深链、重建、真实 TalkBack和工作区门禁全部通过。

### PASS WITH NOTES

只允许以下情况：

- 两项 Room 测试经严格 Base/Head A/B 证明为 Base 既有失败；
- 真实 TalkBack因明确环境限制无法执行，但其他自动化与人工交互全部通过。

### FAIL

包括但不限于：

- Head 不一致或验收中移动；
- 工作区不干净；
- 多设备结果混用；
- 编译、JVM、Lint、APK 或 PR 自有测试失败；
- Head 独有 Instrumentation 失败；
- Skill 仍为占位页；
- 正式 ID 深链错误、未知 ID 被接受；
- Catalog 与 Repository Validator 不是同一事实源；
- 详情查看错误写入最近使用；
- 删除/降低测试或放宽安全边界；
- 将 XML Dump 冒充真实 TalkBack。

## 十六、报告格式

报告必须分别列出：

1. 最终结论；
2. PR URL、Base、分支、精确 Head；
3. 环境与唯一设备；
4. 差异范围与禁止区；
5. GitHub CI 状态；
6. 实际执行并通过；
7. 实际执行但失败；
8. 仅静态核对；
9. 尚未验证；
10. Base/Head A/B 原始结果；
11. 正式 Skill Catalog、深链、设置和返回栈；
12. 重建、响应式与 TalkBack；
13. 最终工作区状态；
14. 已知风险和重点回归区域。

禁止笼统写“全部通过”；必须提供命令、退出码、测试数量和关键日志。
