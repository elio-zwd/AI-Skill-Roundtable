# PR09-08 本地 AI 严格只读验收 Prompt

你现在负责 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` Draft PR #46 的本地严格只读验收。

目标：验证 PR09-08 的临时点名、显式一轮交叉讨论、透明整合、最小消息上下文、实际消息使用快照、Room v10、共享预算、Stop、重试、进程恢复、Compose 和外部设备语义契约。

> 当前正式复验 Head 由 PR #46 描述提供。开始前必须读取 PR，确保本地与远端 Head 字符级一致；不得沿用旧验收报告中的 Head。

## 一、绝对只读纪律

全过程不得：

```text
修改任何仓库文件
自动格式化
创建 Commit
推送
变基
合并
标记 Ready
删除分支
adb uninstall
adb shell pm clear
删除 App 数据
恢复会覆盖数据的模拟器快照
调用生产模型网络
使用真实 API Key
```

允许：

```text
fetch / checkout / pull --ff-only
读取文件和 Git 元数据
构建与测试
adb install -r 当前 Head 构建的 APK
Instrumentation 使用 Fake Network
外部 UIAutomator 使用稳定 testTag
将日志与证据写入仓库外 $env:TEMP
```

任何命令引起工作区变化，立即停止并报告 `FAIL`，不得自行修复。

## 二、精确目标

开始前从 Draft PR #46 描述读取并记录：

```text
Base 分支：main
目标分支：feat/pr-09-08-directed-cross-discussion
预期 Head SHA：以 PR #46 当前精确 Head 为准
Room：v10
```

必须确认 PR 保持 Draft，未经用户授权不得改变状态。

## 三、环境记录

记录：

```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber,OSArchitecture
$PSVersionTable.PSVersion
git --version
java -version
adb version
adb devices -l
Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
```

记录 Android SDK、Gradle、模拟器/真机型号、API Level、屏幕密度、字体缩放和主题。

## 四、检出与基线

```powershell
git fetch origin --prune
git checkout feat/pr-09-08-directed-cross-discussion
git pull --ff-only origin feat/pr-09-08-directed-cross-discussion

git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse origin/main
git merge-base HEAD origin/main
git merge-base --is-ancestor d3cc0aa6d61297d64280ee9be0b7adc185386d0c HEAD
git diff --name-status origin/main...HEAD
git diff --check origin/main...HEAD
git diff --exit-code
```

门禁：

- 分支精确；
- Head 与 PR #46 一致；
- `d3cc0aa6...` 是 Head 祖先；
- 工作区初始干净；
- PR 差异不包含无关首页推荐、Catalog 资产正文、音频、归档、最终视觉或旧 RoundtableOrchestrator 接线。

## 五、低 Token 验证工具

必须使用：

```text
tools/local-verification/Invoke-LocalVerification.ps1
```

原始日志、JUnit XML 摘要、Lint、APK Hash、设备日志和 UIAutomator 证据保存到：

```powershell
$EvidenceRoot = Join-Path $env:TEMP ("jianyu-pr-09-08-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force $EvidenceRoot | Out-Null
```

不得把用户问题、消息正文、资料正文、个人背景正文、完整 Prompt 或 API Key 放入证据文件名。

若 JUnit XML 缺失、损坏或测试数为 0，结论必须是 `NOT_VERIFIED`，不得写 PASS。

## 六、静态门禁与 Secret scan

```powershell
pwsh -NoProfile -File tools/check-app-identity.ps1
```

另执行仓库已有 Secret scan / 敏感信息门禁。检查：

```text
Room version = 10
MIGRATION_9_10 已注册
9.json 保留
10.json 存在且为 Room 编译生成
无 destructiveMigration
无密钥、Token、密码、个人信息或构建产物提交
```

## 七、低 Token 包装构建

使用 `Invoke-LocalVerification.ps1` 分别包装：

```text
:app:compileDebugKotlin
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleRelease
:app:assembleDebugAndroidTest
:app:connectedDebugAndroidTest
```

不得把 `assembleDebugAndroidTest` 通过描述为 Instrumentation 已通过。

建议顺序：

```powershell
.\gradlew.bat --stop
```

然后通过低 Token 工具执行全量 JVM、Lint、Debug/Release、AndroidTest APK 和 connected tests。

全量测试已覆盖目标类时，不重复运行相同定向测试；只在失败定位时补充定向命令。

## 八、JVM 必验

确认全量 JVM 零失败，并特别核对：

```text
CollaborationPoliciesTest
ExecutionHistorySelectionTest
ExecutionContextBuilderTest
CollaborationArchitectureTest
JianyuUiAutomationArchitectureTest
IssueExecutionArchitectureTest
```

重点断言：

1. 正式阵容只来自最新 STANDARD 根 Run；
2. Directed、Cross Synthesis 和 retry 不改变阵容；
3. 点名精确一位当前可执行成员；
4. Cross 至少两位且不重复；
5. meeting-to-action 必须透明且仍通过执行资格；
6. NO_HISTORY 不回退为整个 Stage；
7. 同一 Response Run 成员输出互不可见；
8. Synthesis Prompt 保留共识、分歧、条件、不确定性、建议和下一步；
9. 不投票裁决，不改写参与者原意；
10. UI/ViewModel 不访问 DAO/Retrofit/API Key；
11. 协作层只调用唯一 ExecutionRunCoordinator；
12. 没有第二套 Participant 或预算状态机；
13. 没有接入旧 RoundtableOrchestrator。

## 九、Room Migration

必须实际执行：

```text
v1→v10
v2→v10
v3→v10
v4→v10
v5→v10
v6→v10
v7→v10
v8→v10
v9→v10
```

特别运行/核对：

```text
CollaborationMigrationTest
ExecutionRuntimeMigrationTest
ResourceLifecycleMigrationTest
现有连续 Migration 测试套件
```

两个连续迁移断言的方法名与预期链必须已更新到 v10：

```text
allMigrationsRemainContinuousFromVersion1ToVersion10
[(1,2), (2,3), (3,4), (4,5), (5,6), (6,7), (7,8), (8,9), (9,10)]
```

先定向复跑原失败类：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.ExecutionRuntimeMigrationTest,com.elio.jianyu.data.ResourceLifecycleMigrationTest
```

定向通过后仍必须执行全量 `:app:connectedDebugAndroidTest`；不得用定向结果替代全量设备回归。

v9→v10 至少验证：

```text
旧 Run runKind = STANDARD
旧 Run historyScope = FULL_STAGE
parentRunId/discussionId = null
旧 Participant State 完整
旧 Budget 完整
旧 Material/Personal Usage 完整
cross_discussion_sessions 存在
execution_message_usage_snapshots 存在
所有新索引存在
PRAGMA foreign_key_check 返回 0 行
```

不得使用 destructive migration，不得手工修改 Schema JSON。

## 十、原子性与幂等 Instrumentation

使用 InMemory 或测试文件数据库、Fake Network，实际验证：

### Directed

1. 用户 Message、Run、Participant Snapshot/State、Budget、Context Usage、Message Usage 同事务创建；
2. 双击同命令只产生一条用户 Message 和一个 Run；
3. 同键同 payload 幂等成功；
4. 同键不同 payload 返回冲突；
5. 任一写入失败全部回滚；
6. 写入失败零网络、零预算消费、无 Pending；
7. 参与者数量精确为 1；
8. `triggerMessageId` 指向用户 Message；
9. Run Kind 为 DIRECTED_RESPONSE；
10. 不改变正式阵容。

### Cross Response

1. Discussion、用户 Message、Response Run、N 个 Participant、Budget、Usage 同事务创建；
2. 少于两人、重复成员、非阵容成员、不可执行成员阻断；
3. 初始预算至少 N+1；
4. 每位成员输入相互独立；
5. A 不读取 B 输出，B 不读取 A 输出；
6. 每位成员有独立输出 Message 和 Participant State；
7. Response 成功后预算保持开放；
8. 部分失败状态为 PARTIAL_SUCCESS；
9. 全员失败不创建 Synthesis；
10. Stop 保留已完成输出并阻止 Synthesis。

### Synthesis

1. 是独立 CROSS_DISCUSSION_SYNTHESIS Run；
2. `parentRunId` 指向 Response 根 Run；
3. `retryOfRunId` 不表达 Response/Synthesis 关系；
4. 只读取本次讨论实际成功输出；
5. 不读取整个 Stage、其他 Run 或失败成员虚构内容；
6. meeting-to-action Participant Snapshot 可见；
7. 整合调用消费 Response 根预算；
8. 部分成功只有用户明确选择后才整合；
9. Synthesis 失败保留 Response 输出；
10. Synthesis retry 只重试整合，不重复成员；
11. retry 使用原 Message Usage Snapshot；
12. 不自动切换整合 Skill。

## 十一、Message Usage Snapshot

实际验证：

```text
contentSnapshot 等于模型实际输入正文
contentHash 与 ContextContentHasher 一致
usageOrder 稳定
不允许 Pending
不允许其他 Issue
不允许其他 Stage
不允许重复 Message
Message 后续变化不修改历史快照
Synthesis 聚合 Response retry 后的实际成功输出
Synthesis retry 复制原快照
```

检查 Hash：UTF-8、CRLF/CR→LF、不 trim、不静默截断。

## 十二、共享预算

实际验证：

```text
Directed 默认 maxApiCalls = 3
Cross 默认 maxApiCalls = maxOf(8, participantCount * 2 + 2)
Response 根预算 maxApiCalls >= N+1
成员调用依次消费
每次消费保留剩余成员 + 整合调用
Response 成功不关闭预算
部分失败 retry 不返还已用调用
Synthesis 共享 Response rootRunId
Synthesis 成功后关闭预算
Synthesis 失败 retry 不重复成员
用户停止整个讨论后预算关闭
进程恢复不追加调用
```

## 十三、Stop 与迟到回调

使用 Fake Streaming Network：

1. Response 运行中停止；
2. Synthesis 运行中停止；
3. Stop 先持久化 Run/Participant/Discussion 终态再取消 Job；
4. 已完成输出保留；
5. 未完成 Pending 被关闭；
6. 迟到文本不得覆盖 STOPPED/FAILED/SUCCEEDED；
7. 未发出网络前立即停止仍有可解释状态；
8. 不重复执行成功成员。

## 十四、进程强停与恢复

在 Fake Network 场景实际执行：

```text
点名运行中断
Cross Responding 中断
部分成功
全部回应完成、等待整合
Synthesis 运行中断
Synthesis 失败
Synthesis 成功
用户停止
```

两阶段强停：

```powershell
adb shell am force-stop com.elio.jianyu
adb shell monkey -p com.elio.jianyu -c android.intent.category.LAUNCHER 1
```

恢复后验证：

```text
只读取 Room
零自动网络调用
零自动 retry
零自动 Synthesis
零重复用户 Message
零重复 Run/Participant/Usage
AWAITING_SYNTHESIS 显示“成员回应已完成，等待继续整合”
用户点击后才启动整合
```

## 十五、Compose 与布局

Instrumentation/Compose 实际验证：

```text
协作输入区
当前阵容
点名入口
交叉讨论入口
无阵容状态
点名 Dialog
交叉 Dialog
精确一人选择
至少两人门禁
透明整合者
预计 N+1 调用
历史消息选择
资料与个人背景入口
部分失败动作
待整合动作
整合重试
停止状态
恢复状态
```

设备/配置覆盖：

```text
360dp
200% 字号
明色主题
暗色主题
键盘弹出
长问题
长 Skill 名
三个以上参与者
长错误文本
TalkBack 焦点顺序
触控目标
状态不只靠颜色
```

若无法真实验证 TalkBack/200% 字号，结论必须标记 `NOT_VERIFIED` 或 `PASS WITH NOTES`，不得假装通过。

## 十六、自动化标签契约

确认 `JianyuAutomationTags.Collaboration` 是唯一来源，静态标签真实存在于生产 UI：

```text
issue_collaboration_input
issue_directed_response_button
issue_cross_discussion_button
issue_collaboration_roster
directed_response_dialog
directed_response_confirm
directed_response_failure
cross_discussion_dialog
cross_discussion_focus_input
cross_discussion_integrator
cross_discussion_confirm
cross_discussion_status
cross_discussion_retry_failed
cross_discussion_synthesize_available
cross_discussion_resume_synthesis
cross_discussion_failure
```

动态标签：

```text
directed_participant_<stableSkillId>
cross_discussion_participant_<stableSkillId>
cross_discussion_message_<stableMessageId>
cross_discussion_session_<stableDiscussionId>
```

验证动态部分经过 `normalizedStableId()`，不含中文名、问题、正文、完整文本或敏感内容。

确认 Scaffold 根节点仍：

```kotlin
testTagsAsResourceId = true
```

## 十七、安装纪律

只允许：

```powershell
adb install -r <当前精确 Head 构建的 debug APK>
```

安装前记录 APK 路径、SHA-256、包名和构建 Head。不得卸载或清数据。

## 十八、外部设备语义控制

必须使用：

```text
tools/device/cli.py
```

优先 `--by tag`。禁止固定坐标、中文文案主选择器、OCR、图片模板首选和生产网络。

### 最小外部路径

Instrumentation 可先建立确定性测试 Issue、Stage 和 STANDARD 正式阵容，但不得清除已有 App 数据。

随后：

```text
启动 App
→ 打开议题
→ 打开确定性测试 Issue
→ 断言协作输入区
→ 输入问题
→ 打开点名 Dialog
→ 选择一位参与者
→ 断言“本次仅该 Skill”
→ 取消
→ 断言零 Message/Run/Usage 副作用
→ 打开交叉 Dialog
→ 选择至少两位参与者
→ 断言 meeting-to-action 透明整合者
→ 断言预计调用次数 N+1
→ 取消
→ 断言零副作用
```

外部 UIAutomator 不启动真实模型。完整 Directed/Response/Synthesis 使用 Fake Network Instrumentation。

## 十九、隐私检查

检查 Logcat、异常、JUnit、Lint、工具证据和文件名：

```text
无用户问题正文
无历史消息正文
无资料正文
无个人背景正文
无完整 Prompt
无 API Key
无 Token
无正文型自动化标签
无正文型证据文件名
```

## 二十、收尾

```powershell
.\gradlew.bat --stop
git status --short
git diff --exit-code
git rev-parse HEAD
git rev-parse origin/feat/pr-09-08-directed-cross-discussion
```

要求：

```text
工作区干净
Head 精确不变
分支精确不变
无提交、无推送、无 PR 状态变化
```

## 二十一、报告格式

最终报告必须包含：

1. 结论：PASS / PASS WITH NOTES / FAIL / NOT_VERIFIED；
2. PR 链接、Base、Branch、精确 Head；
3. 环境与设备；
4. 只读纪律；
5. 低 Token 工具命令、退出码、JUnit 数量；
6. JVM、Lint、Debug、Release、AndroidTest APK；
7. 原失败两个迁移类的定向复验结果；
8. 全量 Instrumentation 数量与零失败证据；
9. v1→v10 和 v9→v10；
10. Directed 原子性/幂等；
11. Cross 独立回应；
12. 部分失败与仅整合成功内容；
13. Synthesis 与独立 retry；
14. Message Usage；
15. 共享预算；
16. Stop 与迟到回调；
17. 强停恢复零自动联网；
18. Compose、360dp、200% 字号、TalkBack；
19. 外部 UIAutomator；
20. 隐私扫描；
21. 工作区收尾；
22. 未验证项、风险和复现步骤。

本地 AI 发现失败时只报告，不得自行修改、提交或推送。
