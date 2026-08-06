# PR09-05C：本地 AI 严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 Draft PR #51 进行严格只读本地验收。

仓库：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable
```

PR：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable/pull/51
```

目标分支：

```text
feat/pr-09-05c-all-official-skills
```

## 一、绝对纪律

本次验收只允许：

```text
读取
拉取
检出
构建
测试
安装测试 APK
执行自动化验收
收集日志
生成验收报告
```

禁止：

```text
修改任何文件
格式化文件
自动修复
提交 Commit
推送
变基
合并
关闭 PR
标记 Ready
启用自动合并
删除分支
强制更新分支
修改 Git 配置
修改 Gradle 配置
修改测试
降低断言
删除失败用例
调用生产网络
使用真实 API Key
adb uninstall
adb shell pm clear
清理用户应用数据
```

如果测试失败：

1. 保留原始失败日志；
2. 不修改源码；
3. 记录首个根因、完整复现命令和相关文件；
4. 把报告反馈给 PR09-05C 远端开发对话。

## 二、精确 Head 锁定

PR 文档不能硬编码自身最终 Commit SHA，否则每次更新文档都会产生新 SHA。因此，PR #51 描述中的以下字段是唯一验收锁：

```text
最终验收锁定 Head：<40位SHA>
```

开始前打开 PR #51 描述，复制该 SHA：

```powershell
$expectedHead = "<从 PR #51 描述复制的 40 位最终验收 SHA>"
```

执行：

```powershell
git fetch origin --prune
git checkout feat/pr-09-05c-all-official-skills
git pull --ff-only origin feat/pr-09-05c-all-official-skills

git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse origin/feat/pr-09-05c-all-official-skills
git rev-parse origin/main
git log -10 --oneline --decorate

$actualHead = (git rev-parse HEAD).Trim()
$remoteHead = (git rev-parse origin/feat/pr-09-05c-all-official-skills).Trim()
if ($actualHead -ne $expectedHead) {
    throw "HEAD mismatch: expected=$expectedHead actual=$actualHead"
}
if ($remoteHead -ne $expectedHead) {
    throw "Remote branch mismatch: expected=$expectedHead remote=$remoteHead"
}
if ((git status --short).Length -ne 0) {
    throw "Working tree must be clean before acceptance"
}
```

若 PR 描述尚未出现真实 40 位 SHA、值与远端 Head 不一致，或分支仍在变化：

```text
立即停止验收
结论：BLOCKED_HEAD_NOT_LOCKED
```

## 三、PR09-12 串行门禁

PR09-12 是 Draft PR #50。本 PR 只有在 PR09-12 已合并、PR09-05C 已同步其 Merge SHA 后，才能进行最终合并级验收。

检查 PR #51 描述是否记录：

```text
PR09-12 Merge SHA：<40位SHA>
同步后 Base：<origin/main SHA>
```

本地执行：

```powershell
$pr0912MergeSha = "<从 PR #51 描述复制>"
git merge-base --is-ancestor $pr0912MergeSha HEAD
if ($LASTEXITCODE -ne 0) {
    throw "PR09-12 merge commit is not an ancestor of PR09-05C HEAD"
}

git merge-base --is-ancestor origin/main HEAD
if ($LASTEXITCODE -ne 0) {
    throw "Latest origin/main is not an ancestor of PR09-05C HEAD"
}
```

若 PR09-12 仍未合并：

```text
允许执行开发阶段预验收
但最终结论只能为 BLOCKED_BY_PR09_12
不得建议 Ready 或合并
```

## 四、环境记录

记录真实版本：

```powershell
Get-CimInstance Win32_OperatingSystem |
  Select-Object Caption, Version, BuildNumber, OSArchitecture
$PSVersionTable.PSVersion
git --version
java -version
javac -version
.\gradlew.bat --version
adb version
adb devices -l
Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
```

必须记录：

- 操作系统；
- PowerShell；
- Git；
- Java / Javac；
- Gradle；
- Android SDK / adb；
- 设备序列号、API Level 和 Android 版本；
- 验收起止时间。

设备信息：

```powershell
$device = (adb devices | Select-String "\tdevice$").ToString().Split("`t")[0]
adb -s $device shell getprop ro.build.version.sdk
adb -s $device shell getprop ro.build.version.release
adb -s $device shell getprop ro.product.model
```

禁止自动选择多个设备。若存在多个在线设备，明确指定序列号。

## 五、只读差异与所有权检查

执行：

```powershell
git diff --check origin/main...HEAD
git diff --name-status origin/main...HEAD
git diff --stat origin/main...HEAD
git status --short
```

确认没有修改以下 PR09-12 独占范围，除非 PR 描述已明确记录合并后最小接线：

```text
RoundtableDatabase.kt
任何 Entity / DAO / Migration
app/schemas/
Issue Lifecycle
Archive / Trash / Purge
IssuesRoute / IssuesViewModel
音频清理状态机
PR09-12 独占的 App Runtime 接线
```

必须确认：

```text
没有 Room Entity 变化
没有 DAO 变化
没有 Migration
没有 Schema JSON 变化
没有数据库降级
没有 destructive migration
```

本 PR无 Schema 变化时，报告只能写：

```text
Migration 测试：按条件跳过，未声称通过
```

## 六、Secret 与隐私静态扫描

执行仓库已有 Secret scan，并额外只读搜索：

```powershell
git grep -n -I -E "AIza[0-9A-Za-z_-]+|sk-[0-9A-Za-z_-]+|api[_-]?key\s*=|Authorization:\s*Bearer|BEGIN (RSA|OPENSSH|EC) PRIVATE KEY" -- `
  app/src/main `
  app/src/test `
  app/src/androidTest `
  docs

git grep -n -I -E "TODO|TBD|占位内容|待补充" -- `
  app/src/main/assets/skills/official `
  app/src/main/assets/official_skill_execution_manifest_v2.json
```

允许文档中解释“不得包含 TODO/TBD”的文字；真正资产和 Manifest 不得含占位项或密钥。

确认错误、标签和日志不会包含：

- Skill Asset 正文；
- 用户问题正文；
- 用户资料正文；
- 人物来源台账；
- API Key；
- 外部请求正文。

## 七、构建与 JVM 测试

先停止旧 Gradle Daemon，但不得清理源码或用户文件：

```powershell
.\gradlew.bat --stop
```

按顺序执行并记录每条命令退出码、耗时和测试数量：

```powershell
.\gradlew.bat :app:compileDebugKotlin --stacktrace
.\gradlew.bat :app:testDebugUnitTest --stacktrace
.\gradlew.bat :app:lintDebug --stacktrace
.\gradlew.bat :app:assembleDebug --stacktrace
.\gradlew.bat :app:assembleRelease --stacktrace
.\gradlew.bat :app:assembleDebugAndroidTest --stacktrace
```

然后专项执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.elio.jianyu.skill.catalog.OfficialSkillExecutionManifestV2Test" `
  --tests "com.elio.jianyu.skill.catalog.OfficialSkillExecutionContextEligibilityTest" `
  --tests "com.elio.jianyu.skill.catalog.OfficialSkillExecutionAuditMatrixTest" `
  --tests "com.elio.jianyu.home.OfficialSkillHomeRecommendationV2Test" `
  --tests "com.elio.jianyu.home.HomeExecutionConsentWorkflowTest" `
  --tests "com.elio.jianyu.home.HomeStartContextGateTest" `
  --stacktrace --info
```

必须从测试输出保存 44 行 TSV 矩阵，字段为：

```text
Skill ID
Primary Type
Risk Level
Network Requirement
Asset Exists
Static Eligibility
Context Gate
Resolver
Single Run
Multi Run
Directed
Cross
UI Disclosure
```

JVM 测试只允许把前三个验证列记为 `PASS_JVM`。其余列必须等对应 Android 或设备测试真实通过后再改写为 `PASS`。

## 八、Manifest 与资产专项核验

必须证明：

### v1

- `official_skill_execution_batch_v1.json` 精确 4 项；
- 稳定 ID 为：
  - `study-planner`
  - `meeting-to-action`
  - `report-proposal-writer`
  - `research-fact-checker`
- 首批 3～5 项、禁人物、禁高后果规则没有被删除；
- v1 可显式回滚。

### v2

- `official_skill_execution_manifest_v2.json` 精确 44 项；
- ID 唯一；
- 与基础 Catalog 集合完全一致；
- 顺序与 `defaultOrder` 1..44 完全一致；
- 缺少、重复、未知、第 45 项和顺序错误均安全失败；
- 任一资产失败时 Runtime 整体失败，不静默保留部分项。

### 资产

- 40 项新增生产资产真实存在；
- 原四项历史资产路径保持；
- 44 项 UTF-8 可读、非空；
- 所有共同章节完整；
- 资产正文不完全重复；
- 不含 TODO、TBD、密钥、环境变量或隐藏系统 Prompt 字段；
- 19 项人物声明完整；
- 高后果章节完整；
- Office、Naturalizer、Patent、Fortune 固定边界完整。

## 九、Android Instrumentation

确认在线设备后执行全量：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --stacktrace
```

再执行 44 项 Resolver 专项：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.skill.catalog.OfficialSkillExecutionManifestV2AndroidTest `
  --stacktrace
```

再执行首页 Compose 专项：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.home.HomeScreenTest `
  --stacktrace
```

必须证明：

- 44 项逐项从安装 APK 读取正式资产；
- 每项 System Prompt 非空；
- 稳定 ID、资产路径、Participant Snapshot 正确；
- v1 回滚后精确四项；
- 人物声明未确认时 Resolver 拒绝；
- 首页人物、高后果、联网确认可见；
- Patent 敏感正文阻止开始；
- 开始按钮状态正确；
- 原有四项无回归。

## 十、自动化工具

执行仓库工具的帮助和只读检查：

```powershell
python tools/device/cli.py --help
Get-Help tools/local-verification/Invoke-LocalVerification.ps1 -Full
```

按仓库工具实际参数执行本 PR 的只读验证流程。禁止传入：

```text
uninstall
pm clear
生产端点
真实 API Key
会修改源码或 Git 状态的参数
```

工具执行前后都必须检查：

```powershell
git status --short
git diff --exit-code
git diff --cached --exit-code
```

## 十一、UIAutomator 最小场景

使用稳定自动化标签，不通过易变中文文本定位主要控件。

### 场景 A：原四项之外的 Skill

```text
打开首页
→ 输入可匹配非首批 Skill 的问题
→ 获取建议
→ 确认推荐结果不只包含原四项
→ 打开 Skill 页面并搜索该稳定 ID
→ 断言详情可见
```

### 场景 B：人物 Skill

```text
打开人物 Skill 详情
→ 断言 AI 模拟身份声明可见
→ 用于新问题
→ 进入推荐确认
→ 断言人物风险说明可见
→ 进入最终确认
→ 断言 home_execution_person_disclaimer_confirmation 可见
→ 未勾选时开始按钮不可用
→ 取消
→ 数据库确认零 Run、零 Message、零预算 Usage
```

### 场景 C：高后果 Skill

```text
打开高后果 Skill
→ 进入最终确认
→ 断言 home_execution_high_stakes_confirmation 可见
→ 未确认时阻止开始
→ 确认后使用 Fake Gateway 完成测试运行
→ 不调用生产网络
```

### 场景 D：需要联网的 Skill

```text
选择 networkRequirement=REQUIRED 的 Skill
→ 断言 home_execution_network_authorization 可见
→ 未授权时阻止开始
→ 授权后 Fake Gateway 可执行
→ 无真实来源时结果不得声称已实时检索
```

### 场景 E：自然表达优化

```text
打开 original-expression-naturalizer
→ 断言不规避 AI 检测
→ 断言不协助学术作弊
→ 断言不伪造事实和经历
→ 断言不冒充他人
→ 推荐确认、当前阵容和运行结果附近仍可见诚信边界
```

### 场景 F：专利敏感材料

```text
打开 patent-disclosure-organizer
→ 选择用户标记禁止外传的敏感材料
→ 断言 home_execution_restricted_material_block 可见
→ 开始按钮不可用
→ 断言零外部网络调用
→ 断言零 Issue、零 Run、零 Message、零 Context Usage、零预算
→ 改用用户自行脱敏摘要后重新确认
→ 使用 Fake Gateway 完成测试运行
```

### 场景 G：办公文档

```text
打开 office-document-productivity
→ 断言只描述 Markdown、纯文本、结构化表格内容
→ 不出现控制 Word/Excel/PowerPoint、点击桌面、自动保存、签署或提交能力
```

### 场景 H：阵容变化使确认失效

```text
完成联网、人物和高后果确认
→ 修改成员、职责、顺序或单/多模式
→ 断言旧确认全部失效
→ 开始按钮不可用
→ 重新确认后方可执行
```

## 十二、Directed / Cross / Synthesis

使用 Fake Gateway 验证：

```text
STANDARD
DIRECTED_RESPONSE
CROSS_DISCUSSION_RESPONSE
CROSS_DISCUSSION_SYNTHESIS
```

必须确认：

- 单 Skill 和多 Skill 均可运行；
- 点名只影响一次；
- Cross 只使用已选成员；
- 不引入未选人物；
- 人物 Disclaimer 随 Participant Snapshot 可追溯；
- 高后果失败不回退到其他成员；
- `meeting-to-action` 继续承担默认透明整合；
- 不创建第二个 Coordinator；
- 不创建第二套预算；
- 不创建第二套网络 Gateway；
- Catalog 更新后历史 Snapshot、Run 和 Message 不漂移。

## 十三、数据库只读检查

只允许查询，不允许清理或写入测试之外的数据。

验证拒绝场景后：

```text
Issue = 0
Run = 0
Message = 0
Context Usage = 0
预算事实 = 0
```

验证成功 Fake Gateway 场景后：

- Participant Snapshot 的 `sourceId` 为稳定官方 ID；
- `skillAssetPath` 指向正式 APK Asset；
- `systemPrompt` 非空；
- 历史 Snapshot 不被后续 Catalog 重载改写。

不得在报告中复制完整 System Prompt 或用户敏感正文。

## 十四、最终工作区清洁

所有测试结束后执行：

```powershell
git status --short
git diff --exit-code
git diff --cached --exit-code
git rev-parse HEAD
```

必须满足：

```text
工作区干净
无未跟踪构建外文件被写入仓库目录
HEAD 仍精确等于 $expectedHead
无 Commit
无 Push
无分支变更
```

若测试工具产生受 Git 跟踪文件变化：

```text
验收失败
不要自行恢复或提交
记录文件和原因并反馈远端开发对话
```

## 十五、验收报告格式

报告必须包含：

1. 最终结论：`PASS` / `PASS_WITH_NOTES` / `FAIL` / `BLOCKED_BY_PR09_12` / `BLOCKED_HEAD_NOT_LOCKED`；
2. 仓库、PR、Base、Branch、Expected Head、Actual Head；
3. PR09-12 Merge SHA 与祖先检查；
4. 环境和时间；
5. changed files 与 PR09-12 所有权检查；
6. v1 精确四项结果；
7. v2 精确 44 项结果；
8. 44 行完整审计矩阵；
9. 40 项新增资产统计；
10. 19 项人物声明与来源台账结果；
11. 高后果门禁；
12. Office / Naturalizer / Patent / Fortune 专项；
13. 上下文资格拒绝码；
14. 首页推荐与手动选择；
15. Resolver；
16. Single / Multi；
17. Directed / Cross / Synthesis；
18. JVM 测试数量和零失败证据；
19. Lint；
20. Debug / Release；
21. AndroidTest APK；
22. 全量 Instrumentation 数量和零失败证据；
23. UIAutomator 场景；
24. Secret 与隐私扫描；
25. v2 → v1 回滚；
26. 工作区清洁；
27. 尚未验证项；
28. 已知风险与重点回归区域；
29. 精确失败命令、日志和复现步骤。

必须严格使用：

```text
已实际执行并通过
GitHub CI 已通过
仅完成静态检查
尚未执行
按条件跳过
等待本地 AI 验收
```

不得把：

```text
测试代码已存在
编译通过
旧 Head 的结果
同步 PR09-12 前的结果
```

表述为最终设备验收通过。
