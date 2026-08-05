# PR09-10A 本地 AI 严格只读最终验收 Prompt

你现在负责 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` Draft PR #47 的本地严格只读最终验收。

目标：验证 PR09-10A 在已合并 PR09-08 / Room v10 基线上完成阶段草稿、草稿 Revision、正式成果、成果 Revision、真实来源追溯、成果库和最终 IssueExecution 共享工作区接线，同时不破坏点名回应、交叉讨论、共享预算、Stop、迟到回调和强停恢复安全边界。

> 预期 Head SHA 由远端开发对话最终回复及 PR #47 当前 Head 提供。开始前必须读取 PR #47，确保本地 Head 与远端 Head 字符级一致；不得沿用阶段 A 报告中的旧 Head。

## 一、绝对只读纪律

全过程不得：

```text
修改任何仓库文件
自动格式化
自动修复
创建 Commit
推送
变基
合并
标记 Ready
删除分支
git clean
git reset --hard
adb uninstall
adb shell pm clear
删除 App 用户数据
恢复覆盖用户数据的模拟器快照
调用生产模型网络
使用真实 API Key
```

允许：

```text
fetch / checkout / pull --ff-only
读取文件和 Git 元数据
构建与测试
adb install -r 当前精确 Head 构建的 APK
Instrumentation 使用 Fake Network
外部 UIAutomator 使用稳定 testTag
把日志和证据写入仓库外 $env:TEMP
```

任何命令引起工作区变化，立即停止并报告 `FAIL`，不得自行修复。

## 二、精确目标

开始前从 Draft PR #47 描述和远端开发对话记录：

```text
仓库：https://github.com/elio-zwd/AI-Skill-Roundtable
PR：https://github.com/elio-zwd/AI-Skill-Roundtable/pull/47
Base：main@6379146e354cb0bf14365572bb4fa673cc88f727
Branch：feat/pr-09-10a-draft-result
Expected Head：以 PR #47 当前精确 Head 为准
Room：v10
PR 状态：Draft
```

必须确认：

```text
6379146e354cb0bf14365572bb4fa673cc88f727 是当前 Head 祖先
PR09-08 / PR #46 已合并
当前分支没有 Room v11
没有修改 MIGRATION_9_10
没有修改 schemas/.../10.json
```

## 三、环境记录

执行并记录：

```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber,OSArchitecture
$PSVersionTable.PSVersion
git --version
java -version
.\gradlew.bat --version
adb version
adb devices -l
Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
```

记录：

```text
Android SDK
Gradle
设备 ID
型号
API Level
分辨率
密度
字体缩放
主题
测试开始和结束时间
```

设备不可用时，JVM/静态部分可以继续；所有设备部分标记 `NOT_VERIFIED`，不得虚构。

## 四、检出和基线门禁

```powershell
git fetch origin --prune
git checkout feat/pr-09-10a-draft-result
git pull --ff-only origin feat/pr-09-10a-draft-result

git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse origin/main
git merge-base HEAD origin/main
git merge-base --is-ancestor 6379146e354cb0bf14365572bb4fa673cc88f727 HEAD
git diff --name-status origin/main...HEAD
git diff --check origin/main...HEAD
git diff --exit-code
```

门禁：

```text
分支精确
Head 与 PR #47 完全一致
Base 合并提交是 Head 祖先
初始工作区干净
PR 保持 Draft
```

如果 Head 不匹配，立即停止并给出 `NOT_VERIFIED`。

## 五、适用规则和接口交接

验收前读取：

```text
AGENTS.md
app/src/main/java/com/elio/jianyu/ui/AGENTS.md
docs/planning/pr-09-08-interface-handoff.md
docs/planning/pr-09-10a-draft-result-plan.md
docs/planning/pr-09-10a-interface-handoff.md
```

核对实现没有越过以下边界：

```text
不建立第二套执行 Coordinator
不建立第二套预算状态机
不建立第二套 Draft / Artifact 表
不接入旧 RoundtableOrchestrator
不从正文猜测成果来源
不因无正式成果阻止 Stage 推进
不自动创建、确认或导出成果
```

## 六、低 Token 证据目录和工具

必须使用：

```text
tools/local-verification/Invoke-LocalVerification.ps1
```

证据目录：

```powershell
$EvidenceRoot = Join-Path $env:TEMP ("jianyu-pr-09-10a-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Force $EvidenceRoot | Out-Null
```

不得把以下内容放进证据文件名：

```text
用户问题
草稿正文
成果正文
消息正文
资料正文
个人背景正文
Prompt
API Key
```

若 JUnit XML 缺失、损坏或测试数量为 0，结论必须是 `NOT_VERIFIED`。

## 七、静态门禁与 Secret scan

执行：

```powershell
pwsh -NoProfile -File tools/check-app-identity.ps1
pwsh -NoProfile -File tools/check-secrets.ps1 -IncludeHistory
```

检查：

```text
Room version = 10
MIGRATION_9_10 已注册
9.json 和 10.json 均存在
没有 Room v11
没有 destructiveMigration
没有提交密钥、Token、密码、个人信息或构建产物
```

检查中央自动化标签：

```text
JianyuAutomationTags.Artifacts
JianyuAutomationTags.StageResult
```

动态标签只能使用稳定内部 ID；中文、空格、正文和超长值必须被拒绝。

## 八、低 Token 构建

通过包装器依次真实执行：

```text
:app:compileDebugKotlin
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleRelease
:app:assembleDebugAndroidTest
```

开始前：

```powershell
.\gradlew.bat --stop
```

记录每条命令、退出码、耗时、日志路径和 Hash。

不得把 `assembleDebugAndroidTest` 通过描述为设备测试通过。

## 九、全量 JVM 单元测试

必须执行全量：

```text
:app:testDebugUnitTest
```

重点核对：

```text
StageResultDomainTest
StageResultServiceTest
StageDraftAutosavePolicyTest
StageResultOperationGatesTest
ArtifactLibraryLoaderTest
ArtifactLibraryUiStateTest
ArtifactLibraryAggregatorSourceTest
JianyuAutomationTagsTest
IssueExecutionArchitectureTest
CollaborationPoliciesTest
ExecutionHistorySelectionTest
ExecutionContextBuilderTest
CollaborationArchitectureTest
JianyuUiAutomationArchitectureTest
```

必验行为：

1. 通用草稿模板确定且不调用模型；
2. 默认成果类型为 `GENERAL_SUMMARY`；
3. 800 ms debounce；
4. 相同内容不创建新 Revision；
5. 保存串行化；
6. Revision 冲突不覆盖；
7. 确认成果单飞；
8. 精确 Draft Revision 作为来源；
9. Pending、跨 Issue、跨 Stage、孤立消息被拒绝；
10. 用户触发消息因无 `participantSnapshotId` 不可成为成果输出来源；
11. 四类 Run Kind 均可识别；
12. `FULL_STAGE / EXPLICIT_MESSAGES / NO_HISTORY` 原样展示；
13. 实际 Message Usage 数量来自 Room v10 快照；
14. 成果来源只映射持久化关系，不从正文猜测；
15. 修订孤儿、自循环、多节点循环、跨域和分叉可检测；
16. 成果库默认只显示最新版本；
17. 资料、个人背景和成果状态隔离；
18. UI 不访问 DAO、Retrofit、API Key 或网络 Gateway；
19. 没有第二个执行 Coordinator 或预算 Map。

## 十、Room v1→v10 和 v9→v10 Migration

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

重点类：

```text
CollaborationMigrationTest
ExecutionRuntimeMigrationTest
ResourceLifecycleMigrationTest
现有连续 Migration 测试套件
```

断言连续链：

```text
[(1,2), (2,3), (3,4), (4,5), (5,6), (6,7), (7,8), (8,9), (9,10)]
```

v9→v10 至少验证：

```text
旧 Run 默认 runKind = STANDARD
旧 Run 默认 historyScope = FULL_STAGE
旧 Draft 保留
旧 Draft Revision 保留
旧 Artifact 保留
旧 Artifact 来源关系保留
旧 Material / Personal Usage 保留
cross_discussion_sessions 存在
execution_message_usage_snapshots 存在
PRAGMA foreign_key_check = 0 行
```

PR09-10A 没有新增 Schema，因此不得出现 10→11 Migration 或新 Schema JSON。

## 十一、Repository 与来源关系设备测试

实际运行全量 `connectedDebugAndroidTest`。失败定位时至少定向运行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryDatabaseTest,com.elio.jianyu.data.ArtifactSourceRecoveryDatabaseTest"
```

必验：

```text
第一版 Draft Revision
连续 Revision
非连续 Revision 拒绝
同 Stage 单一当前 Draft
相同正文幂等
放弃当前 Draft
历史 Revision 保留
Artifact 原子写入
Message / Run / Draft / Material 来源 FK
跨 Issue / Stage 来源拒绝
Artifact 幂等和冲突
Artifact 修订不覆盖旧版本
recoverIssue 恢复 Draft / Revision / Artifact
listArtifactSourcesForIssue 恢复四类来源
放弃 Draft 后来源关系仍存在
Room version = 10
foreign_key_check = 0
```

`ArtifactSourceRecoveryDatabaseTest` 中的交叉讨论整合 Run 必须按状态机从 `NOT_STARTED` 转为成功，不得绕过执行状态机直接写成功状态。

## 十二、Compose 与共享工作区设备测试

至少定向运行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.resources.ArtifactLibraryComponentsTest,com.elio.jianyu.ui.screens.result.StageResultComponentsTest,com.elio.jianyu.ui.screens.execution.IssueExecutionStageResultScreenTest"
```

重点确认阶段 A 曾失败的两个场景已经拆分且通过：

```text
成果库 Empty 与 Failure 各自只调用一次 setContent
阶段保存 Failure 与 Conflict 各自只调用一次 setContent
```

不得再出现：

```text
IllegalStateException: Cannot call setContent twice per test!
```

共享工作区必须同时显示：

```text
执行状态
共享预算
上下文入口
点名回应
交叉讨论
阶段草稿与成果面板
```

草稿面板不能替换、遮蔽或复制执行状态区。

## 十三、点名、交叉讨论和成果来源联合回归

使用 Fake Network / 测试 Repository，验证：

### STANDARD

```text
完成成员输出可选
用户触发消息不可选
Pending 不可选
Run Kind 和 FULL_STAGE 可见
```

### DIRECTED_RESPONSE

```text
点名用户 Message 不可选
被点名成员完成输出可选
Run Kind = DIRECTED_RESPONSE
History Scope = EXPLICIT_MESSAGES 或实际持久化值
Message Usage 数量准确
不改变正式阵容
```

### CROSS_DISCUSSION_RESPONSE

```text
不同成员完成输出可独立选择
失败或停止成员的未完成 Pending 不可选
部分成功输出明确标注不代表完整结论
不把同一轮成员输出互相错误共享
```

### CROSS_DISCUSSION_SYNTHESIS

```text
整合输出可选
Run Kind = CROSS_DISCUSSION_SYNTHESIS
来源 Run 指向真实整合 Run
Material Usage 来自对应 Run 的真实快照
Synthesis retry 不重复成员输出
```

成果确认后，实际检查四类来源表中的行与用户最终选择一致。

## 十四、共享预算回归

PR09-10A 创建、编辑、自动保存、放弃和确认成果都不得消费执行预算。

验证：

```text
创建通用草稿前后 usedApiCalls 不变
从选定消息创建草稿前后 usedApiCalls 不变
800 ms 自动保存前后 usedApiCalls 不变
显式保存前后 usedApiCalls 不变
成果确认前后 usedApiCalls 不变
成果库读取前后 usedApiCalls 不变
来源读取前后 usedApiCalls 不变
```

同时回归 PR09-08：

```text
Directed 默认预算
Cross N+1 预留
成员调用与整合共享根预算
Stop 后预算关闭
失败重试不返还已消费调用
恢复不追加调用
```

## 十五、Stop 与迟到回调

使用 Fake Streaming Network 回归：

```text
STANDARD 运行中 Stop
DIRECTED_RESPONSE 运行中 Stop
CROSS_DISCUSSION_RESPONSE 运行中 Stop
CROSS_DISCUSSION_SYNTHESIS 运行中 Stop
```

验证：

1. 先持久化终态再取消 Job；
2. 已完成输出保留；
3. 未完成 Pending 关闭；
4. 迟到文本不得覆盖 STOPPED / FAILED / SUCCEEDED；
5. StageResult 只读取已持久化的完成 Message；
6. StageResult 不会因为 Stop 自动创建草稿；
7. StageResult 不会因为迟到回调自动更新 Artifact；
8. Stop 后不会自动整合或确认成果。

## 十六、强停恢复与零自动副作用

至少覆盖：

```text
无 Draft
Draft 已保存
Draft 有多个 Revision
Draft 保存失败
Draft Revision 冲突
Artifact 已确认
Artifact 有修订链
点名运行中断
Cross Response 中断
等待整合
Synthesis 中断
用户 Stop
```

两阶段强停：

```powershell
adb shell am force-stop com.elio.jianyu
adb shell monkey -p com.elio.jianyu -c android.intent.category.LAUNCHER 1
```

恢复后验证：

```text
只读取 Room
Draft 恢复到最后已保存 Revision
未保存内存正文不得伪装为已保存
Artifact 和来源关系完整
共享预算不增加
零自动网络
零自动 retry
零自动 Synthesis
零自动 Artifact 确认
零自动 Stage 推进
零重复用户 Message
零重复 Run / Participant / Usage
```

记录 Fake Network 调用计数或其他可核验证据。没有调用计数证据，不得声称“零自动联网”。

## 十七、外部 UIAutomator 语义验收

使用：

```text
tools/device/cli.py
```

优先 `--by resource-id` / 稳定 testTag。禁止把固定坐标、中文正文、OCR 或图片模板作为主定位。

### 17.1 成果库

```text
启动 App
→ 资料与成果
→ 成果 Tab
→ 断言 artifact_library / artifact_library_empty / artifact_library_failure 中真实状态
→ 打开成果卡 artifact_item_<stable-id>
→ 断言 artifact_detail
→ 断言 artifact_sources
→ 返回对应 Issue / Stage
```

### 17.2 草稿最小流程

使用没有生产网络依赖的测试议题：

```text
打开 IssueExecution
→ 断言 issue_execution_screen
→ 断言 stage_result_panel
→ 点击 stage_draft_create_button
→ 断言 stage_draft_editor
→ 编辑草稿
→ 等待 800 ms 以上
→ 断言 stage_draft_saved
→ 强停并重启
→ 重新打开相同 Issue / Stage
→ 断言已保存草稿恢复
```

### 17.3 正式成果

```text
保存草稿
→ 点击 stage_artifact_confirm_button
→ 断言 artifact_confirmation_dialog
→ 取消一次，确认零 Artifact
→ 再次打开
→ 最终确认
→ 断言阶段成果卡 stage_artifact_<stable-id>
→ 成果库中断言 artifact_item_<stable-id>
```

确认 Artifact ID 必须来自稳定内部 ID，不得从标题或正文生成标签。

## 十八、布局与无障碍

至少验证：

```text
360dp 宽度
200% 字号
明色主题
暗色主题
键盘弹出
长草稿
长成果标题
多条来源消息
长错误文本
TalkBack 焦点顺序
触控目标
状态不只靠颜色
```

如果无法真实验证 TalkBack、200% 字号或 360dp，报告必须标记 `PASS_WITH_NOTES` 或 `NOT_VERIFIED`。

## 十九、隐私检查

检查 Logcat、截图、XML、HTML、命令日志和证据文件名。

不得泄漏：

```text
草稿完整正文
成果完整正文
消息完整正文
资料完整正文
个人背景完整正文
用户问题
完整 Prompt
API Key
Secret
```

生产 UI 可以在用户主动打开详情后显示正文，但自动化标签、Logcat、证据文件名和错误码不得包含正文。

发现正文或密钥泄漏，直接判定 `FAIL`。

## 二十、收尾

执行：

```powershell
.\gradlew.bat --stop
git status --short
git diff --exit-code
git branch --show-current
git rev-parse HEAD
```

必须确认：

```text
最终工作区干净
分支未改变
Head 未改变
无 Commit
无 Push
无 PR 状态变化
```

## 二十一、最终结论枚举

只允许：

```text
PASS
PASS_WITH_NOTES
FAIL
NOT_VERIFIED
```

不得因远端 CI 通过而把未执行的本地设备、强停、UIAutomator、TalkBack 或隐私检查写成 PASS。

## 二十二、报告格式

报告必须包含：

1. 最终结论；
2. PR、Base、Branch、Expected / Actual Head；
3. 环境与时间；
4. 严格只读纪律；
5. 构建命令、退出码和证据；
6. JVM 总数、失败数和重点类；
7. Room v1→v10 / v9→v10 Migration；
8. Repository 和成果来源设备测试；
9. Compose 和共享工作区测试；
10. STANDARD / Directed / Cross Response / Synthesis 来源联合回归；
11. 共享预算；
12. Stop 与迟到回调；
13. 强停恢复和网络调用计数；
14. UIAutomator 每一步、标签和结果；
15. 360dp / 200% / 主题 / TalkBack；
16. 隐私扫描；
17. 未验证事项；
18. 失败项、稳定复现步骤和可能原因；
19. 最终工作区、分支和 Head。

本地 AI 不得自行修复。把完整报告反馈给 PR09-10A 远端开发对话。
