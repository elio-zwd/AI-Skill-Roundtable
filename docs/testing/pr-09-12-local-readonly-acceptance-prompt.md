# PR09-12 本地 AI 严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 PR09-12 Draft PR #50 做严格只读验收。

仓库：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable
```

目标分支：

```text
feat/pr-09-12-archive-trash
```

目标：验证用户主动归档、归档恢复与变化记录、关联新议题、无自动过期回收站、彻底清除影响预览、音频及后台任务受控清理、失败恢复、数据生命周期写入门禁和 Room v12 连续迁移。

## 一、绝对纪律

全过程只允许：

- 拉取、检出、读取、构建、测试、安装当前精确 Head 产物和收集证据。
- 在仓库外 `$env:TEMP` 写原始日志、截图和临时测试数据。

禁止：

- 修改任何仓库文件。
- 自动格式化、修复、生成后保留 Schema 差异。
- 提交、推送、变基、合并、标记 Ready、启用自动合并或删除分支。
- `git clean -fdx`。
- `adb uninstall`、`adb shell pm clear`、清空模拟器用户数据。
- 生产网络、真实 API Key、真实用户 Issue 的不可恢复 Purge。
- 固定坐标、OCR 或中文文案作为设备自动化主定位方式。

发现问题时只报告，不修改。失败必须给出命令、退出码、关键日志、复现步骤和可能原因。

## 二、验收环境记录

开始时记录：

```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber,OSArchitecture
$PSVersionTable.PSVersion
git --version
java -version
javac -version
.\gradlew.bat --version
adb version
adb devices -l
Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
```

记录：

- 操作系统、PowerShell、Git、JDK、Gradle、Android SDK、adb。
- 模拟器型号、API、Android 版本、屏幕密度和字号。
- 验收开始/完成时间。

## 三、基线锁定

```powershell
git fetch origin --prune
git checkout feat/pr-09-12-archive-trash
git pull --ff-only origin feat/pr-09-12-archive-trash

git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse origin/main
git merge-base HEAD origin/main
git log -15 --oneline --decorate
git diff --name-status origin/main...HEAD
git diff --check origin/main...HEAD
git diff --exit-code
```

要求：

- 分支精确为 `feat/pr-09-12-archive-trash`。
- 工作区开始时干净。
- 记录实际 Head、Base 与 merge-base。
- 确认 PR #50 仍为 Draft。
- 确认没有启动 PR09-13A/13B/14A/14B。
- 如实际 Head 与远端开发对话给出的预期 Head 不一致，立即停止并报告。

## 四、差异与安全审查

检查：

```powershell
git diff --stat origin/main...HEAD
git diff --check origin/main...HEAD
git diff origin/main...HEAD -- app/src/main app/src/test app/src/androidTest docs tools .github
```

重点确认：

- Room 仅升级 v11→v12，没有 v13。
- 历史 Schema 未修改或删除。
- `12.json` 是 KSP 生成格式，版本 12。
- 无 destructive migration。
- 无 `deleteRecursively()` 用于 Issue/Audio Purge。
- 无绝对路径信任、目录猜 Issue、外部 URI 删除。
- Worker Data 只含 `purge_operation_id`。
- 无自动过期、倒计时、定时清空或低空间自动 Purge。
- 无 API Key、Token、密码、密钥、用户正文或绝对路径进入日志/标签。
- 无 Co-Authored-By。
- 无无关依赖升级、官方 Skill Catalog 修改或大范围重构。

执行 Secret scan 与身份门禁：

```powershell
pwsh -NoProfile -File tools/check-app-identity.ps1
```

按仓库现有 Secret scan 工作流或脚本执行；记录真实命令与结果。

## 五、统一本地验证脚本

使用：

```text
tools/local-verification/Invoke-LocalVerification.ps1
```

原始日志放在仓库外：

```powershell
$EvidenceRoot = Join-Path $env:TEMP ("jianyu-pr09-12-" + (Get-Date -Format "yyyyMMdd-HHmmss"))
New-Item -ItemType Directory -Path $EvidenceRoot -Force | Out-Null
```

先停止旧 Gradle Daemon，但不要清理用户数据：

```powershell
.\gradlew.bat --stop
```

执行并逐项记录退出码：

```text
:app:compileDebugKotlin
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleRelease
:app:assembleDebugAndroidTest
:app:connectedDebugAndroidTest
```

要求明确区分：

- 已实际执行并通过。
- GitHub CI 已通过。
- AndroidTest APK 仅编译通过。
- 设备 Instrumentation 已实际执行并通过。
- 尚未验证。

不得把 `assembleDebugAndroidTest` 写成设备测试通过。

## 六、Room v12 与 Schema

核对：

```text
app/schemas/com.elio.jianyu.data.RoundtableDatabase/12.json
```

记录：

- `database.version = 12`
- `identityHash`
- 文件 SHA-256
- 实体数量
- 四张新表：
  - `issue_archive_events`
  - `issue_resume_events`
  - `issue_relations`
  - `issue_purge_operations`

执行 Migration Instrumentation：

- v1→v12
- v2→v12
- 依次直到 v11→v12
- v11 Stage Advancement 完整
- v11 AudioAsset 完整
- v11 Draft/Artifact 完整
- v11 Collaboration 完整
- 历史 ARCHIVED/TRASHED 保留
- 历史 `purgeRequestedAt != null` 保守迁移为可解释重试状态
- 不伪造 Archive/Resume/Relation
- 每条路径 `PRAGMA foreign_key_check = 0`

测试完成后：

```powershell
git diff --exit-code -- app/schemas
```

必须无 Schema 漂移。

## 七、Repository 与写入门禁专项

执行并记录相关 JVM/Instrumentation 测试：

- Archive Event 与 Lifecycle 原子。
- 相同 operationId + 相同 payload 幂等。
- 不同 payload 冲突且不覆盖旧事件。
- Resume Event 与 Lifecycle 原子。
- Change Note 必填或显式“暂无变化”。
- 恢复不创建 Stage/Run。
- 关联新议题只创建独立 Issue、唯一初始 Stage、Lifecycle 与 Relation。
- 不复制 Message/Run/Draft/Artifact/Material 正文。
- 普通删除保存 previousState。
- 从回收站恢复到原 ACTIVE 或 ARCHIVED。
- 旧 `archiveIssue()`、`restoreIssue()`、`requestIssuePurge()` 不能绕过 v12 事实表。

ARCHIVED 状态验证拒绝：

- 创建 Run。
- 点名回应。
- Cross Response/Synthesis。
- 推进 Stage。
- 保存/修改 Draft。
- 确认 Artifact。
- 生成或重试音频。
- 新增 Context Usage。

TRASHED/Purge 状态验证拒绝全部业务写入，同时确认：

- STOPPED/FAILED/CANCELED 等终态收敛仍能落库。
- 已有 AVAILABLE 音频可只读播放。
- 恢复后业务门禁解除。
- 迟到音频 Gateway 成功不能把 CANCELED/PURGE_REQUESTED/DELETED 恢复为 AVAILABLE。

## 八、归档活动任务流程

使用受控测试 Issue，覆盖：

1. 打开归档对话，立即返回，数据库零变化。
2. 再次打开并取消，数据库零变化。
3. 活动 STANDARD Run 阻止直接归档。
4. Directed Run 阻止直接归档。
5. Cross Response/Synthesis 阻止直接归档。
6. Pending Message 阻止。
7. Audio PENDING/Work 阻止。
8. “等待”不停止任务、不归档。
9. “停止”复用正式 Execution/Collaboration/Audio Stop。
10. Stop 失败不归档。
11. Stop 后重新读取 Room，旧确认失效。
12. 用户重新查看和最终确认后才归档。
13. 归档简报由本地确定性事实生成，不调用模型。
14. 简报可编辑，旧 Archive Event 不覆盖。
15. 归档不删除历史、成果、资料或音频。

## 九、恢复与关联新议题

### 9.1 继续原议题

验证：

- 归档简报只读展示。
- Change Note 为空且未选择“暂无变化”时不能确认。
- 显式选择“暂无变化”允许确认。
- Resume Event 单独保存，不修改 Archive Event。
- 不创建 Stage/Run、模型调用、音频生成、资料/背景选择或网络授权。
- 返回原议题当前 Stage。
- 双击确认只创建一条 Resume Event。
- 进程重建不自动恢复。

### 9.2 创建关联新议题

验证：

- UI 明确“关联的新议题，不是原议题的新 Stage”。
- 新标题与初始目标可编辑。
- 双击确认只创建一个目标 Issue、一个初始 Stage、一个 Relation。
- 原 Issue 保留。
- 不复制全部 Message、Run、Participant、Draft、Artifact、Material 或 Personal Context。
- 来源 Issue 清除后目标 Issue 仍存在。
- Relation 降级为“来源议题已清除”，不保留来源标题或正文。

## 十、回收站与无自动过期

验证：

- ACTIVE → TRASHED，previousState=ACTIVE。
- ARCHIVED → TRASHED，previousState=ARCHIVED。
- 恢复回原状态。
- App 启动不自动清空。
- 时间推进不自动 Purge。
- 无倒计时、过期字段或定时 Worker。
- 容量不足只显示警告、文件占用和手动管理入口。
- 不自动删除任何 Issue、AudioAsset 或 Orphan。

## 十一、Purge 影响预览

在测试数据库和受控测试目录中创建完整测试 Issue，验证实际统计：

- Stage、Stage Advancement、Measure、Skill、Material/Artifact 继承。
- Run、Participant Snapshot/State、Budget。
- Message、Pending Message、Cross Discussion、Message Usage。
- Material Reference/Usage、Personal Context Usage。
- Draft、Revision、Artifact 与所有 Source。
- AudioAsset、正式文件、`.part`、Pending Work、Missing。
- Archive/Resume/Relation。
- 兼容 ChatSession。
- 关联新议题数量。
- 外部/不可删除对象。

验证：

- 不统计其他 Issue。
- 正式文件字节来自真实文件。
- Orphan 独立报告，不进入目标文件集合。
- `sourceLocator` 不作为 App 文件删除依据。
- 外部 URI 不读取、不删除。
- Impact 顺序与 Hash 稳定。
- 影响变化后旧确认失效，必须重新查看。

## 十二、Purge 双确认与取消

设备语义场景：

```text
回收站测试 Issue
→ 打开 Purge 影响预览
→ 第一次确认
→ 进入最终确认
→ 返回/取消
→ 证明没有 Operation、没有 purgeRequestedAt、没有文件或数据库删除
```

再次执行：

```text
影响预览
→ 第一次确认
→ 最终不可恢复确认
→ 创建唯一 Purge Operation
→ 冻结业务写入
→ 调度唯一 Work
```

验证：

- 没有预选同意复选框。
- 双击最终确认只创建一个 Operation/Work。
- 文件删除开始前可安全取消。
- 取消后原子清除 Operation 与 purgeRequestedAt。
- 文件删除开始后 UI 不提供或拒绝完整取消。
- 取消竞态推进时重新调度同一 Operation，不留下无 Worker 的半清理状态。

## 十三、Purge 文件阶段

使用受控测试目录和测试 AudioAsset：

1. `reconcileFilesForIssue()`。
2. `inspectPurgeImpact()`。
3. 每个目标资产先 `requestDelete()`。
4. 取消 Pending Work。
5. 删除目标正式文件。
6. 清理该资产正式 `.part`。
7. 不删除 Orphan。
8. 不删除其他 Issue 文件。
9. 路径穿越、绝对路径和非受控路径被拒绝。
10. 源目录没有 `deleteRecursively()`。

失败注入：

- 正式文件删除失败：
  - 不执行数据库最终清理。
  - Issue、Lifecycle、AudioAsset、Operation 保留。
  - 失败阶段/稳定错误码持久化。
  - 可显式重试。
- `.part` 删除失败：同样不得进入数据库清理。
- 迟到 Gateway 成功不得复活资产。
- 重试不得删除其他 Issue 文件。

## 十四、Purge 数据库阶段

执行 `IssuePurgeDatabaseCleanerTest` 及完整 Instrumentation：

成功路径验证：

- 目标 Issue 数据按真实 FK 图删除。
- Issue 最后删除。
- Lifecycle 与 Operation 一并消失。
- 来源 Relation 降级，目标 Issue 保留。
- 全局 PersonalContextEntry、官方 Skill、组合、Key、其他 Issue 保留。
- 兼容 ChatSession 仅在无合法引用时删除。
- `foreign_key_check = 0`。

失败路径验证：

- 人为制造跨 Issue 外键阻断。
- 任一 SQL 失败时整个事务回滚。
- Issue、Stage、Run、Archive、Relation、Lifecycle、Operation 均保留。
- 不留下部分数据库删除。

数据库阶段失败但文件已清理：

- UI 显示“文件已清理，数据库收尾失败”。
- Operation 保留 `DATABASE_PURGE` 失败阶段。
- 重试只执行数据库阶段，不重复文件删除。
- 不恢复已删除物理文件。

## 十五、进程强停与恢复

使用测试专用 Issue 和受控目录：

- `REQUESTED` 强停。
- `CANCELING_TASKS` 强停。
- `DELETING_FILES` 文件间强停。
- `READY_FOR_DATABASE_PURGE` 强停。
- `DATABASE_PURGING` 事务前强停。
- 数据库失败后强停。

恢复验证：

- 只从 Room 恢复同一 Operation。
- 已有唯一 Work 时复用，不重复调度。
- Work 缺失且状态安全可继续时只恢复同一 Operation。
- 不创建第二个 Operation。
- 不自动调用模型或音频生成。
- 已删除文件可通过对账识别。
- 失败 Operation 不无限后台自动重试；需用户显式重试。
- 完成后导航移除 Issue。

## 十六、设备安装纪律

使用当前精确 Head 产物覆盖安装：

```powershell
adb install -r <当前精确Head构建的app-debug.apk>
```

禁止卸载、清数据或替换其他分支 APK。

安装后记录：

```powershell
adb shell dumpsys package com.elio.jianyu | Select-String version
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.version.release
```

## 十七、设备语义自动化

使用：

```text
tools/device/cli.py
```

优先 `--by tag`。正式标签至少覆盖：

```text
issues_active_section
issues_archived_section
issues_trashed_section
issue_archive_button
issue_archive_dialog
issue_archive_wait
issue_archive_stop
issue_archive_summary
issue_archive_confirm
issue_archive_cancel
issue_resume_button
issue_resume_change_note
issue_resume_no_change
issue_resume_confirm
issue_related_new_button
issue_related_new_title
issue_related_new_objective
issue_related_new_confirm
issue_move_to_trash
issue_trash_confirm
issue_restore_from_trash
issue_purge_button
issue_purge_impact
issue_purge_first_confirm
issue_purge_final_confirm
issue_purge_progress
issue_purge_retry
issue_purge_failure
```

动态标签只允许稳定 ID，必须验证拒绝标题、正文、路径、Key 和完整 Hash。

设备主流程：

```text
活跃议题
→ 打开归档
→ 取消并确认零副作用
→ 再次归档
→ 查看/编辑归档简报
→ 最终确认
→ 已归档列表
→ 继续原议题
→ 填写变化
→ 恢复
→ 移入回收站
→ 恢复
→ 再次移入回收站
→ Purge 影响预览
→ 第一次确认
→ 取消最终确认并确认零清理
```

真实不可恢复 Purge 只能使用验收期间创建的测试专用 Issue。

## 十八、可访问性与布局

至少验证：

- 360dp 宽度。
- 200% 字号。
- 竖屏。
- 键盘导航和输入法。
- TalkBack 语义顺序与操作名称。
- 明暗主题。
- 归档、继续、关联、恢复、Purge 操作不横向溢出或被裁剪。
- Dialog 可滚动，最终确认按钮可到达。
- 无依赖颜色单独表达危险状态。
- Purge 完成/失败是稳定页面状态，不只存在 Snackbar。

TalkBack 实时语音与物理手势如无法自动证明，必须标记 `PASS WITH NOTES`，不得伪造人工证据。

## 十九、隐私与日志

检查 Logcat、测试日志、WorkManager Data、自动化标签和异常报告，不得出现：

- Issue 标题。
- Archive Summary。
- Resume Change Note。
- Message/Draft/Artifact/Material/Personal Context 正文。
- API Key。
- 文件绝对路径。
- 完整 generation key、impact hash 或 payload hash。

只允许稳定错误码，例如：

```text
archive_active_work
archive_state_changed
resume_note_required
related_issue_conflict
trash_active_work
purge_impact_changed
purge_audio_cancel_failed
purge_file_delete_failed
purge_database_failed
purge_storage_unavailable
```

## 二十、GitHub CI

读取 PR #50 最新精确 Head 对应的 Workflow/Job：

- Secret scan。
- 应用身份门禁。
- `compileDebugKotlin`。
- `testDebugUnitTest`。
- `lintDebug`。
- `assembleDebug`。
- `assembleRelease`。
- `assembleDebugAndroidTest`。
- Room Schema freshness。
- Migration 连续性相关检查。

必须确认 CI 对应当前精确 Head，不能引用被新提交取消或旧 Head 的通过结果作为最终结论。

## 二十一、收尾只读证明

```powershell
.\gradlew.bat --stop
git status --short
git diff --exit-code
git diff --exit-code -- app/schemas
git rev-parse HEAD
git rev-parse origin/main
```

要求：

- 工作区完全干净。
- Head 与开始时精确一致。
- 未修改 Schema、源码、测试、文档或构建配置。
- 无提交、推送、合并、Ready 状态变化。

## 二十二、验收报告格式

最终报告必须包含：

1. 最终结论：`PASS` / `PASS WITH NOTES` / `FAIL`。
2. PR 链接、Base、Branch、精确 Head。
3. 环境与时间。
4. 只读纪律证明。
5. 差异文件清单与安全审查。
6. Room v12、Identity Hash、Schema SHA-256。
7. v1→v12 和 v11→v12 Migration 结果。
8. JVM 数量与结果。
9. Lint、Debug、Release、AndroidTest APK 构建结果。
10. Instrumentation 数量与结果。
11. Archive/Resume/Related/Trash 结果。
12. Purge Impact、双确认、文件、数据库、失败恢复结果。
13. 进程强停恢复结果。
14. 其他 Issue、PersonalContextEntry、Skill、Key 保留证明。
15. 360dp、200% 字号、键盘、TalkBack、明暗主题。
16. UIAutomator/设备语义结果。
17. 隐私日志扫描结果。
18. GitHub CI 最新 Head 结果。
19. 尚未验证项与原因。
20. 已知风险和重点回归区域。
21. 收尾工作区干净与 Head 不变证明。

若任何不可恢复 Purge 测试触及用户现有数据、工作区不干净、Schema 漂移、Migration/Instrumentation 有失败、其他 Issue 被误删或数据库出现半删除，最终结论必须为 `FAIL`。
