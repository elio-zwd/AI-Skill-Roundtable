# PR09-11 本地 AI 严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 Draft PR #48 执行严格只读验收。

目标 PR：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable/pull/48
```

目标分支：

```text
feat/pr-09-11-advance-issue
```

## 一、最高纪律

本验收只能读取、构建、测试和记录证据。禁止：

- 修改任何源码、测试、文档、Schema 或配置；
- 自动格式化；
- 创建 Commit；
- 推送；
- 合并；
- 标记 Ready；
- 删除或重建分支；
- `adb uninstall`；
- `adb shell pm clear`；
- 使用生产网络；
- 使用真实 API Key；
- 绕过失败测试、降低断言或删除测试。

发现问题后只记录并反馈，不要修复。

## 二、锁定远端版本

先执行：

```powershell
git fetch origin --prune
git status --short
git branch --show-current
git rev-parse origin/main
git ls-remote origin refs/heads/feat/pr-09-11-advance-issue
gh pr view 48 --repo elio-zwd/AI-Skill-Roundtable --json number,state,isDraft,baseRefName,headRefName,baseRefOid,headRefOid,url,title
```

记录 PR #48 当前精确 `headRefOid`，再检出对应分支并使用 `--ff-only`：

```powershell
git checkout feat/pr-09-11-advance-issue
git pull --ff-only origin feat/pr-09-11-advance-issue
git rev-parse HEAD
git status --short
```

若实际 HEAD 与 PR `headRefOid` 不一致，立即停止并报告，不在错误版本上验收。

同时确认：

- PR 仍是 Draft；
- Base 为 `main`；
- `main` 包含 PR09-08 和 PR09-10A；
- Room 当前版本为 v11；
- `app/schemas/com.elio.jianyu.data.RoundtableDatabase/11.json` 存在；
- 分支不保留临时 Schema 同步工作流；
- 工作区开始时干净。

## 三、环境记录

完整记录：

```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber,OSArchitecture
$PSVersionTable.PSVersion
git --version
java -version
javac -version
.\gradlew.bat --version
adb version
adb devices -l
adb -s emulator-5554 shell getprop ro.product.model
adb -s emulator-5554 shell getprop ro.build.version.sdk
adb -s emulator-5554 shell getprop ro.build.version.release
Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
```

若设备 ID 不是 `emulator-5554`，使用实际在线设备 ID 并在报告中说明。

## 四、静态与构建门禁

逐项执行并记录退出码和关键日志：

```powershell
git diff --check
pwsh -File tools/check-app-identity.ps1
pwsh -File tools/check-sensitive-information.ps1
.\gradlew.bat --stop
.\gradlew.bat :app:compileDebugKotlin --stacktrace
.\gradlew.bat :app:testDebugUnitTest --stacktrace
.\gradlew.bat :app:lintDebug --stacktrace
.\gradlew.bat :app:assembleDebug --stacktrace
.\gradlew.bat :app:assembleRelease --stacktrace
.\gradlew.bat :app:assembleDebugAndroidTest --stacktrace
```

检查 Room Schema 新鲜度：

```powershell
git diff --exit-code -- app/schemas
Get-FileHash app/schemas/com.elio.jianyu.data.RoundtableDatabase/11.json -Algorithm SHA256
Select-String -Path app/schemas/com.elio.jianyu.data.RoundtableDatabase/11.json -Pattern 'stage_advancements|stage_advancement_skill_members|stage_advancement_materials|stage_advancement_artifacts'
```

不得为了生成 Schema 修改工作区；若构建产生差异，记录 `git diff -- app/schemas` 并判定失败。

## 五、设备 Instrumentation

在不卸载、不清数据的前提下执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --stacktrace
```

至少单独复跑以下测试类并记录项目数：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.StageAdvancementMigrationTest `
  --stacktrace

.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.StageAdvancementRepositoryDatabaseTest `
  --stacktrace

.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.execution.AdvanceIssueViewModelDatabaseTest `
  --stacktrace
```

迁移证据必须覆盖：

- v1→v11；
- v2→v11；
- 依次直到 v10→v11；
- v10 历史 Run Kind 完整；
- v10 Message Usage 完整；
- v10 Draft / Draft Revision / Artifact 完整；
- v10 AudioAsset 完整；
- v10 Stage 不被伪造 Advancement；
- `PRAGMA foreign_key_check` 返回 0；
- Room `11.json` 与实体一致。

## 六、项目本地验证工具

执行：

```powershell
pwsh -File tools/local-verification/Invoke-LocalVerification.ps1
python tools/device/cli.py --help
```

按照仓库工具帮助使用实际设备 ID，执行只读或测试允许的设备控制。禁止由工具间接执行卸载、清数据、生产网络或真实 Key。

## 七、推进议题 UI 与行为验收

使用调试 APK、Compose Instrumentation 和 `tools/device/cli.py` / 外部 UIAutomator，逐项验证：

### 7.1 零副作用

- “推进议题”入口在当前工作区始终可见；
- 打开后关闭：零新 Stage；
- 第一步取消：零新 Stage；
- 第二步返回：方向、措施、目标、阵容和继承选择保留；
- 第三步取消：零新 Stage；
- Activity 重建：不自动创建 Stage；
- `am force-stop` 后恢复：只恢复未确认表单，不自动创建 Stage；
- 导航重放：不重复创建 Stage。

### 7.2 三步确认

验证：

1. 第一步至少选择一个方向；
2. “现实支持”和“思维拓展”可同时选择；
3. 双方向最终只创建一个 Stage；
4. 第二步措施可多选，顺序稳定；
5. 自定义目标不能为空；
6. 编辑目标、措施、阵容或继承项后，旧摘要确认失效；
7. 第三步显示目标、方向、措施、默认继承、Skill 阵容、资料、成果、预期输出和运行/草稿提示；
8. 最终确认双击只创建一个 Stage。

### 7.3 原子创建与继承

数据库核对：

- Stage、Advancement、Measures、Roster、Material 关系、Artifact 关系同时存在；
- 继承的是稳定 ID，不复制 Material 或 Artifact；
- 无成果时可以推进；
- 无 Draft 时可以推进；
- Draft 不自动确认、不复制；
- Personal Context 默认不选；
- `networkAllowed` 不继承；
- `sensitiveConfirmed` 不继承；
- 不创建 Run；
- 不创建 Pending Message；
- 不创建 Draft / Artifact / AudioAsset；
- 不调用生产网络；
- 不消耗预算。

### 7.4 阵容

验证：

- 最新 STANDARD 根 Run Participant Snapshot 优先；
- Directed / Cross / Retry 不改变长期阵容；
- 新 Stage 没有 Run 时读取 Advancement 计划阵容；
- 连续推进仍保留原始 STANDARD Snapshot 或 Catalog 版本依据；
- 不使用全部 Catalog 自动补成员；
- 可在第二步调整阵容；
- 被撤回或不可执行 Skill 不被静默替换，界面显示原因并允许调整；
- 不创建假的 `NOT_STARTED` Run。

### 7.5 运行中推进

准备存在活动 STANDARD Run、Directed Run、Cross Response 或 Synthesis 的场景：

- 入口仍能打开；
- 不自动 Stop；
- “等待当前运行完成”不创建 Stage；
- “明确停止当前运行后推进”复用现有 Stop；
- 等待所有终态持久化；
- 重新读取 Room；
- 必须再次确认摘要；
- Stop 失败不创建 Stage；
- `AWAITING_SYNTHESIS` / `PARTIAL_SUCCESS` 可留在旧 Stage；
- 不自动 Synthesis；
- 旧 Stage 迟到回调不修改新 Stage。

### 7.6 撤销新阶段

验证：

- 初始 Stage 无撤销入口；
- 最新、从未运行且无任何业务依赖的 Stage 可撤销；
- 撤销后前一 Stage 恢复为当前节点；
- 任意 Run（包括 FAILED / STOPPED）永久阻止“未运行撤销”；
- Message、Draft、Revision、Artifact、Material Usage、Personal Context Usage、AudioAsset、Discussion、Message Usage 任一存在都阻止；
- 双击撤销幂等；
- 失败时不半删；
- 不删除前一 Stage、旧消息、旧成果、Issue、全局资料或 Skill Catalog。

## 八、布局、可访问性与主题

至少覆盖：

- 360dp 宽度；
- 系统字体 200%；
- 软键盘打开时第二步目标与预期输出可编辑、按钮可访问；
- TalkBack 可识别阶段时间线、当前 Stage、推进方向、阵容、资料、成果、确认、取消和撤销；
- 明亮主题；
- 暗色主题；
- 中央自动化标签可通过 UIAutomator `resource-id` 或 Compose semantics 定位；
- 动态标签只含稳定 ID，不含标题、正文、姓名或 Prompt。

TalkBack 实时语音和物理手势若环境无法自动化，必须标为 `PASS WITH NOTES` 或“未验证”，不能伪装为已验证。

## 九、工作区清洁复核

全部测试结束后执行：

```powershell
git status --short
git diff --exit-code
git diff --check
git rev-parse HEAD
```

若出现任何文件变化，报告变化来源并判定只读纪律失败；不得自行清理或恢复，以免掩盖证据。

## 十、报告格式

最终报告必须包含：

1. `PASS`、`PASS WITH NOTES` 或 `FAIL`；
2. PR URL、Base SHA、分支、精确 Head SHA；
3. 操作系统、JDK、Gradle、Android SDK、adb 和设备信息；
4. 每条命令、退出码、测试数量、失败数量和关键日志；
5. v1～v11、v10→v11、Schema 和外键证据；
6. 三步确认、双方向单 Stage、双击幂等、无成果推进、Stop 后再次确认、进程强停恢复、撤销门禁和 UIAutomator 证据；
7. 360dp、200% 字号、键盘、TalkBack、明暗主题结果；
8. 未验证项；
9. 失败项的最小复现步骤、日志路径和可能原因；
10. 最终 `git status --short` 与 `git diff --exit-code` 证据。

将报告完整反馈给 PR09-11 远端开发对话，不要修改、提交、推送或合并。