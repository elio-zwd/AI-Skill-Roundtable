# PR #66 三项 UI Instrumentation 修复后严格只读复验

仓库：`elio-zwd/AI-Skill-Roundtable`

PR：#66（必须仍为 OPEN / Draft）

分支：`codex/ui-05-artifacts`

代码修复祖先必须包含：

`e84c503d264e7bfc2f6c49c3f1c0bdc6ef3ed88d`

## 1. 纪律

严格只读：

- 不修改任何文件；
- 不自动修复；
- 不格式化；
- 不 commit；
- 不 push；
- 不 merge；
- 不修改 PR Draft/Ready；
- 不使用真实生产 API Key；
- 不用真实生产模型跑测试。

开始时 fetch，并以 PR #66 当前 `headRefOid` 为 Expected Head。本地 HEAD 必须完全一致，且 `git merge-base --is-ancestor e84c503d... HEAD` 必须退出 0。

开始/结束都执行：

```powershell
git status --short
git diff --exit-code
git branch --show-current
git rev-parse HEAD
gh pr view 66 --repo elio-zwd/AI-Skill-Roundtable --json state,isDraft,headRefName,headRefOid,baseRefName,baseRefOid,mergeable
```

## 2. 先执行编译与静态回归

```powershell
.\gradlew.bat --stop
pwsh -NoProfile -File tools/check-app-identity.ps1
pwsh -NoProfile -File tools/check-secrets.ps1 -IncludeHistory

.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:assembleDebugAndroidTest
```

任一失败立即报告，不修。

## 3. 定向重跑原 3 个失败

使用同一测试设备，逐个执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.JianyuNavigationShellScreenTest#settings_preservesAiManagementTelemetryAndBackCallbacks"

.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.automation.JianyuUiAutomationNavigationTest#resourcesLibraryAndMinePersonalContextExposeCurrentContentRoots"

.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.resources.ArtifactLibraryComponentsTest#filtersOpenInSheetAndCancelDoesNotApplyDraftChanges"
```

必须验证：

### A. Settings

- AI 管理动作滚动到可点击区域后 callback = 1；
- Telemetry callback = 1；
- Back callback = 1；
- 不是删除回调断言来制造绿色。

### B. Resources / Personal Context

- 【资料】总览进入资料库后 `MATERIALS_CONTENT` 可见；
- 返回资料总览；
- 切到【我的】；
- 点击正式“个人背景”入口；
- `PersonalContextTestTags.SCREEN` 可见；
- 不要求恢复已废弃的“资料页个人背景子库”。

### C. Artifact filter cancel

- 打开前 `ArtifactLibraryTestTags.TYPE_FILTER` 不存在；
- 打开筛选 Sheet 后存在；
- 通过稳定 `typeFilter(GENERAL_SUMMARY)` 节点修改 draft；
- 点击取消后 Sheet 消失；
- `typeApplyCount == 0`；
- `historyApplyCount == 0`。

## 4. 全量 Instrumentation

三项定向全部 PASS 后，再执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

必须报告：

- 测试类数；
- 测试总数；
- passed；
- failed；
- skipped。

只有 failed = 0 才能把 Full Instrumentation 标为 PASS。

## 5. 保持此前 PASS 边界

若全量回归出现新失败，按真实失败报告，不要只盯上述三项。特别留意：

- UI-03～UI-09；
- Portable Backup；
- Device Snapshot；
- Runtime reopen；
- Privacy；
- Room migration / foreign key；
- AndroidTest 设备状态。

## 6. 最终返回

结论只允许：

`PASS / PASS_WITH_NOTES / FAIL / NOT_VERIFIED`

若 PASS，返回紧凑证据：

- Expected / Actual Head；
- Static/Secret；
- Kotlin/JVM/Lint；
- Debug/Release/R8；
- AndroidTest APK；
- 三项定向测试；
- Full Instrumentation 总数；
- Final git cleanliness；
- 尚未人工验证的 TalkBack / 360dp / 200% 字号。

若 FAIL，每项只返回：

- 失败阶段；
- 失败命令；
- 退出码；
- 文件/行号；
- 第一条关键错误；
- 最小复现步骤；
- 必要少量日志；
- 是否稳定复现。

不要修改代码，把报告交回远端 GPT。
