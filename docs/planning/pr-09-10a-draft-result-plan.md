# PR09-10A：阶段草稿与正式成果实施计划

> **执行方式**：当前对话按仓库内 Superpowers 等价流程执行 `brainstorming → writing-plans → test-driven-development → verification-before-completion → requesting-code-review → finishing-a-development-branch`。不使用 Worktree、并行子智能体或自动合并。
>
> **目标**：复用现有 Stage Draft / Confirmed Artifact Schema 与 `JianyuRepository` 事务，建立可恢复阶段草稿、明确确认的正式成果、修订链、来源构建、议题工作区独立组件和全局成果库。
>
> **状态**：本计划同时冻结阶段 A 与阶段 B。阶段 A 可在 Draft PR #46 开放期间实施；阶段 B 只能在 PR09-08 合并并同步最新 `main` 后实施。

## 1. 实际基线

```text
仓库：elio-zwd/AI-Skill-Roundtable
Base 分支：main
实际 Base SHA：d3cc0aa6d61297d64280ee9be0b7adc185386d0c
开发分支：feat/pr-09-10a-draft-result
开始时 Room：v9
开始时开放 PR：Draft PR #46
```

`d3cc0aa6d61297d64280ee9be0b7adc185386d0c` 是当前 `main` 最新提交，也是预期稳定基线的祖先和当前 Head。

## 2. Draft PR #46 状态与依赖

```text
PR：#46 feat: 实现点名回应与交叉讨论
状态：OPEN / DRAFT / 未合并
分支：feat/pr-09-08-directed-cross-discussion
核验 Head：388005b30510913d414314b1eb0e8c48931abfcc
修改文件数：21
计划数据库：Room v10
```

PR #46 当前修改：

```text
app/src/main/java/com/elio/jianyu/collaboration/CollaborationPolicies.kt
app/src/main/java/com/elio/jianyu/data/CollaborationDao.kt
app/src/main/java/com/elio/jianyu/data/CollaborationEntities.kt
app/src/main/java/com/elio/jianyu/data/CollaborationMigration.kt
app/src/main/java/com/elio/jianyu/data/CollaborationRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/CollaborationRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/CollaborationRuntimeRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/CoreDomain.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt
app/src/main/java/com/elio/jianyu/data/LegacyDatabaseMigrationSupport.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionContextBuilder.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionModels.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionPersistenceGateway.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionRunCoordinator.kt
app/src/test/java/com/elio/jianyu/collaboration/CollaborationPoliciesTest.kt
app/src/test/java/com/elio/jianyu/execution/ExecutionContextBuilderTest.kt
app/src/test/java/com/elio/jianyu/execution/ExecutionHistorySelectionTest.kt
docs/planning/pr-09-08-directed-cross-discussion-plan.md
tools/check-app-identity.ps1
```

PR #46 当前未修改 `ui/screens/resources/`，因此阶段 A 可独占成果库文件。最终 PR 描述必须写明：

```text
Depends on Draft PR #46 for final IssueExecution integration and Room v10 adaptation.
```

PR09-10A 在 PR #46 合并前不得进入最终工作区接线，也不得合并。

## 3. 并行文件所有权

### 阶段 A 可修改

```text
新增 app/src/main/java/com/elio/jianyu/result/
新增 app/src/main/java/com/elio/jianyu/ui/screens/result/
新增对应 JVM / Compose 测试
app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesViewModel.kt
app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesUiState.kt
app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesScreen.kt
app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesComponents.kt
成果相关计划、交接和验收文档
```

### 阶段 A 禁止修改

```text
CoreDomain.kt
CollaborationEntities.kt
ExecutionContextBuilder.kt
ExecutionRunCoordinator.kt
ExecutionRuntime*
RoundtableDatabase.kt
JianyuRepositoryDao.kt
RoomJianyuRepository.kt
JianyuRepositoryContract.kt
任何 Migration
任何 Schema JSON
IssueExecution*
JianyuAutomationTags.kt
JianyuAppRuntime.kt
```

每次写入 `Resources*` 前重新读取 PR #46 文件清单；若发生重叠立即停止写入并报告。

## 4. 当前 Draft / Artifact Schema

现有 Room v9 已包含：

```text
StageSummaryDraftEntity
StageSummaryDraftRevisionEntity
ConfirmedArtifactEntity
ArtifactMessageSourceEntity
ArtifactRunSourceEntity
ArtifactDraftSourceEntity
ArtifactMaterialSourceEntity
```

冻结约束：

- 不创建第二套 Draft / Artifact 表；
- 当前草稿由 `(issueId, stageId)` 唯一；
- Revision 由 `(issueId, stageId, revisionNumber)` 唯一；
- Artifact 通过 `revisionOfArtifactId` 指向直接前序；
- Artifact 与 Message / Run / Draft Revision / Material Usage 的来源行独立持久化。

## 5. 当前 Repository 事务

复用：

```kotlin
suspend fun saveStageDraft(command: SaveStageDraftCommand)
suspend fun abandonStageDraft(issueId: String, stageId: String)
suspend fun confirmArtifact(command: ConfirmArtifactCommand)
suspend fun recoverIssue(issueId: String)
suspend fun listIssueNavigation(states: Set<IssueLifecycleState>)
```

现有事务已经提供：

- Revision 连续性；
- 同 Stage 单当前草稿；
- Revision 内容快照；
- 放弃仅删除当前草稿；
- Artifact 与全部来源原子写入；
- Artifact ID 幂等；
- 相同 ID 不同 payload 冲突；
- 来源同 Issue / Stage 校验。

阶段 A 不修改 Repository 公共契约。业务层通过 `JianyuRepository` 编排现有事务。

## 6. 草稿定义

草稿是当前 Stage 的可编辑、已持久化但未经用户最终确认的阶段内容。草稿：

- 可以创建、编辑、自动保存、显式保存、恢复、放弃；
- 可以积累不可变 Revision；
- 可以从消息或既有成果创建；
- 只有用户最终确认后才成为 Artifact。

草稿不会因退出、创建 Run、App 重启、推进议题、点名或交叉讨论自动删除、替换、过期或确认。

## 7. 成果定义

正式成果是用户明确确认保存的判断、行动方案、知识笔记或可交付内容。最终确认前不调用 `confirmArtifact()`。

正式成果不可原位覆盖；修改必须创建新 `ConfirmedArtifactEntity`，并通过 `revisionOfArtifactId` 指向直接前序。旧版本及来源永久保留。

## 8. 成果类型

新增纯 Kotlin 枚举 `ArtifactType`：

```text
GENERAL_SUMMARY → 通用阶段总结
ACTION_PLAN → 行动方案
DECISION_RECORD → 决策记录
KNOWLEDGE_NOTE → 知识笔记
DELIVERABLE → 交付稿
```

默认 `GENERAL_SUMMARY`。V1 `contentFormat` 固定为 `markdown`。PDF、DOCX、Markdown 文件和分享链接不是 Artifact Type。

## 9. 通用总结方案比较

### 方案 A：隐藏模型调用

拒绝。会产生第二条模型路径、绕过 Coordinator、增加预算和不可见调用，并与 PR09-08 的 Run Kind / Room v10 冲突。

### 方案 B：自动调用 `meeting-to-action`

拒绝。会让草稿创建耦合执行状态、创建新 Run 和额外网络调用，并破坏并行文件所有权。

### 方案 C：本地确定性结构 + 用户选择来源

采用。`GenericStageDraftBuilder` 返回：

```markdown
## 阶段概述

## 已形成的判断

## 主要分歧

## 行动项

## 待确认事项
```

创建过程不调用网络、Coordinator、Run、Pending Message 或预算。用户可以显式选择当前 Stage 已完成消息并透明导入，导入后仍是草稿。

## 10. 消息来源选择

新增 `StageMessageCandidate` 与 `StageMessageSelectionPolicy`：

- 仅接受 `issueId`、`stageId` 与当前 Stage 精确一致的 Message；
- `isPending == true` 不可选；
- 默认空选择；
- 重复 Message ID 拒绝；
- 顺序为 `timestamp → id`；
- 不自动选择资料或个人背景；
- 取消选择零写入；
- 导入文本按稳定标题和消息顺序追加，不调用模型。

## 11. 自动保存

`StageResultViewModel` 使用 UI 内存编辑状态和可取消 debounce Job：

```text
输入变化
→ 标记 DIRTY
→ 取消旧 Job
→ 800 ms 空闲后 flush
→ saveStageDraft
→ 成功后标记 SAVED 并记录 Revision
```

选择 800 ms 是为了避免每字符 Revision，同时保持移动端输入反馈及时。显式保存、成果确认前和 Route 离开前的受控动作立即 flush。

相同正文与最后成功持久化正文一致时不创建新 Revision。保存中、成功、失败和冲突是稳定 UiState，不只通过 Snackbar 表达。

## 12. Revision 与并发冲突

`StageDraftEditorPolicy` 计算下一 Revision：

```text
expected = persistedRevision + 1
```

每次保存命令携带当前已知 `persistedRevision` 生成的下一 Revision。若 Repository 返回 `revision_not_contiguous`，映射为 `StageDraftConflict`，保留编辑器正文并提供“重新加载”。不自动覆盖较新版本。

双击保存在 ViewModel 操作锁和相同内容判定下只发出一次有效写入；Repository 幂等作为第二层保护。

## 13. 放弃草稿

UI 使用二次确认。确认后调用：

```kotlin
abandonStageDraft(issueId, stageId)
```

成功后仅清除当前编辑草稿；历史 Draft Revision、Artifact、Message、Run、Material Usage 和 Stage 保留。重复放弃视为幂等成功。

## 14. 成果确认

`StageResultService.confirmArtifact()` 顺序固定：

```text
flush 当前未保存内容
→ 等待 saveStageDraft 成功
→ 取得精确 Draft Revision
→ 校验 Artifact Type 和标题
→ 构建来源预览
→ 用户最终确认
→ confirmArtifact(artifact + sources)
→ 重新 recoverIssue
```

最终确认前不创建 Artifact、不删除 Draft、不推进 Stage、不导出文件、不创建音频。

## 15. Artifact 来源

`ArtifactSourceBuilder` 必须：

- 自动加入本次精确 Draft Revision；
- 加入用户选定已完成 Message；
- 从选定 Message 的 `executionRunId` 去重得到 Run 来源；
- 仅加入与所选 Run 真实匹配的 `MaterialUsageSnapshotEntity`；
- 所有来源校验同 Issue / Stage；
- 按稳定 ID 排序并去重；
- 不创建 Personal Context 直接来源表，个人背景继续通过 Run Usage 链追溯。

当前公共 `recoverIssue()` 未返回 Artifact 来源关联行。阶段 A 可以真实构建并写入来源，但全局成果详情无法通过公共接口精确读取既有来源行。阶段 B 在 PR09-08 合并后先评估把现有来源行加入 `IssueRecoveryResources` 的最小只读扩展；若扩展会修改共享 Repository 契约，则独立记录接口变更和兼容性，不从 UI 猜测来源。

## 16. Artifact Revision

新增 `ArtifactRevisionResolver`，输入同一 Issue 的 Artifact 列表，输出：

- 最新版本；
- 从根到最新的稳定链；
- 孤儿引用；
- 自循环；
- 多节点循环；
- 跨 Issue / Stage；
- 分叉。

V1 创建策略禁止同一前序出现第二个子版本。确认修订前先 `recoverIssue()`，若前序已有子版本则返回 `artifact_revision_fork`，不调用 Repository。Resolver 对已有异常数据只读展示错误，不删除或改写历史。

## 17. 读取模型

新增：

```text
StageDraftSnapshot
StageMessageCandidate
StageDraftSaveStatus
ArtifactType
ArtifactLibraryItem
ArtifactRevisionChain
ArtifactRevisionProblem
ArtifactSourcePreview
StageResultWorkspace
```

领域服务只依赖 `JianyuRepository`，Screen / Components 不访问 Repository 或 DAO。

## 18. 全局成果库

并行阶段采用方案 A：

```text
listIssueNavigation(ACTIVE, ARCHIVED)
→ 对每个 Issue 调用 recoverIssue(issueId)
→ 构建 ArtifactLibraryItem
→ ArtifactRevisionResolver 标识最新版本和历史
```

成果库：

- 只显示 `ConfirmedArtifactEntity`；
- 默认只显示每条修订链最新版本；
- 可展开历史；
- 搜索标题和本地摘要；
- 按 `ArtifactType` 筛选；
- 显示 Issue / Stage；
- 完整正文只在详情中按用户操作显示；
- 不显示 Draft、Pending Message、导出文件或音频任务。

N+1 读取在 V1 通过受控 IO 协程和确定性聚合实现。只有阶段 B 的真实测试证明不可接受时，才提出最小专用查询接口。

## 19. 成果库状态隔离

`ResourcesUiState.Content` 增加独立 `artifactLibrary` 字段：

```text
ArtifactLibraryLoading
ArtifactLibraryEmpty
ArtifactLibraryContent
ArtifactLibraryPartialFailure
ArtifactLibraryFailure
```

资料、个人背景仍使用现有主状态。Artifact 加载失败不会把整个 `ResourcesUiState` 变为 Failure，也不会阻止资料 Tab 使用。

## 20. 议题工作区独立组件

阶段 A 新增：

```text
StageDraftResultPanel
StageDraftEditor
ArtifactConfirmationDialog
ArtifactDetailPanel
```

组件只接收不可变 `StageResultUiState` 与回调，可独立 Preview / Compose 测试。阶段 A 不修改任何 `IssueExecution*` 文件。

## 21. 无成果推进边界

本 PR 不实现推进议题，不修改 `createStage()` 或阶段推进语义。纯领域测试固定：

- Stage 可以没有草稿；
- Stage 可以没有 Artifact；
- StageResultService 不提供“必须有成果才能推进”的门禁；
- Stage 推进不得自动确认 Draft。

## 22. UiState

稳定状态：

```text
StageResultLoading
StageResultEmpty
StageDraftCreating
StageDraftEditing
StageDraftSaving
StageDraftSaved
StageDraftSaveFailure
StageDraftConflict
ArtifactConfirming
ArtifactConfirmed
ArtifactConfirmationFailure
ArtifactRevisionCreating
ArtifactSourceFailure
StageResultStorageFailure
```

一次性事件只用于 Snackbar、打开 Dialog、导航和滚动。`SAVED`、失败和冲突必须保留在稳定状态中。

## 23. 自动化标签

阶段 A 独立组件先使用 `StageResultTestTags` / `ArtifactLibraryTestTags` 局部常量，动态 ID 必须经过与 `normalizedStableId()` 等价的稳定净化函数，且不得包含正文、标题、姓名或中文类型名。

阶段 B 在 PR09-08 合并后把正式标签迁移到 `JianyuAutomationTags.StageResult` 与 `JianyuAutomationTags.Artifacts`，并保留：

```text
stage_result_panel
stage_draft_empty
stage_draft_create_button
stage_draft_editor
stage_draft_save_button
stage_draft_saving
stage_draft_saved
stage_draft_save_failure
stage_draft_abandon_button
stage_draft_abandon_confirmation
stage_artifact_confirm_button
artifact_confirmation_dialog
artifact_confirmation_confirm
artifact_confirmation_cancel
artifact_library
artifact_library_empty
artifact_library_failure
artifact_search
artifact_type_filter
artifact_detail
artifact_revision_history
artifact_sources
artifact_open_issue
```

Scaffold 根节点继续 `testTagsAsResourceId = true`。

## 24. 隐私与错误

稳定错误码：

```text
draft_save_failed
draft_revision_conflict
artifact_source_mismatch
artifact_confirmation_failed
artifact_revision_invalid
artifact_revision_fork
artifact_load_failed
```

日志、标签、路由、异常标题和证据文件名不得包含 Draft、Artifact、Message、Material、Personal Context 正文、完整 Prompt 或 API Key。

## 25. 阶段 A TDD 顺序

1. `ArtifactTypeTest`：类型与默认值；
2. `GenericStageDraftBuilderTest`：确定性模板、无外部依赖；
3. `StageMessageSelectionPolicyTest`：Pending、跨 Issue / Stage、重复和顺序；
4. `StageDraftEditorPolicyTest`：相同内容、连续 Revision、冲突映射；
5. `ArtifactRevisionResolverTest`：单链、孤儿、自循环、多节点循环、跨 Stage、分叉、最新版本；
6. `ArtifactSourceBuilderTest`：Draft / Message / Run / Material 来源与去重；
7. `ArtifactLibraryAggregatorTest`：仅 Confirmed、搜索、筛选、最新版本、部分失败；
8. `ResourcesUiStateTest`：Artifact 状态不影响资料；
9. 独立 StageResult / ArtifactLibrary Compose 测试。

远端 GitHub 插件无法执行 Gradle 时，RED / GREEN 只记录为“测试代码先于生产代码提交、尚未实际执行”，不得声称已经观察到失败或通过。

## 26. Repository Instrumentation

复用现有真实事务测试并增加：

- Revision 1 / 2 连续；
- 非连续拒绝；
- 同内容不由业务层重复提交；
- abandon 保留 Revision / Artifact；
- Artifact 与全部来源原子；
- 来源跨 Issue / Stage 拒绝；
- Confirm 幂等；
- Artifact 修订旧版本保留；
- recoverIssue 恢复 Draft / Revision / Artifact；
- `PRAGMA foreign_key_check = 0`。

阶段 A 不修改 Room；阶段 B 在 Room v10 上重跑 v1→v10 与 v9→v10 数据保持测试。

## 27. Compose 测试

工作区独立组件覆盖空草稿、编辑、保存中、保存成功、失败、冲突、放弃确认、成果确认、来源预览、修订入口和长正文。

成果库覆盖 Loading、Empty、Failure、PartialFailure、Content、搜索、筛选、Artifact Card、历史、详情和返回 Issue 回调。

360dp、200% 字号、明暗主题、TalkBack 和键盘行为由本地设备验收实际执行。

## 28. 阶段 A 文件清单

计划创建：

```text
app/src/main/java/com/elio/jianyu/result/ArtifactType.kt
app/src/main/java/com/elio/jianyu/result/StageDraftModels.kt
app/src/main/java/com/elio/jianyu/result/GenericStageDraftBuilder.kt
app/src/main/java/com/elio/jianyu/result/StageMessageSelectionPolicy.kt
app/src/main/java/com/elio/jianyu/result/StageDraftEditorPolicy.kt
app/src/main/java/com/elio/jianyu/result/ArtifactSourceBuilder.kt
app/src/main/java/com/elio/jianyu/result/ArtifactRevisionResolver.kt
app/src/main/java/com/elio/jianyu/result/ArtifactLibraryAggregator.kt
app/src/main/java/com/elio/jianyu/ui/screens/result/StageResultUiState.kt
app/src/main/java/com/elio/jianyu/ui/screens/result/StageResultComponents.kt
对应 JVM / Android Compose 测试
```

计划修改：

```text
ResourcesUiState.kt
ResourcesViewModel.kt
ResourcesScreen.kt
ResourcesComponents.kt
ResourcesRoute.kt
```

不为目录对称创建无调用方文件。

## 29. 阶段 B 同步与接线

PR09-08 合并后执行：

```powershell
git fetch origin --prune
git rev-parse origin/main
git merge-base --is-ancestor <PR09-08_MERGE_SHA> origin/main
```

使用普通 merge 同步最新 `main`，不 rebase、不强推、不覆盖 PR09-08 文件。随后：

1. 读取 `docs/planning/pr-09-08-interface-handoff.md`；
2. 确认 Room v10；
3. 确认 Draft / Artifact 表未漂移；
4. 适配 Run Kind 与 Message Usage；
5. 评估 Artifact 来源关联行的最小只读恢复扩展；
6. 完成 `IssueExecution*` 最小接线；
7. 把标签迁移到 `JianyuAutomationTags`；
8. 在 `JianyuAppRuntime` 装配 StageResultService；
9. 不创建 Room v11，除非现有表确实无法表达且用户另行批准。

## 30. PR09-08 兼容测试

阶段 B 增加：

- `STANDARD`、`DIRECTED_RESPONSE`、`CROSS_DISCUSSION_RESPONSE`、`CROSS_DISCUSSION_SYNTHESIS` 的已完成消息可被用户选择；
- 不通过中文文案推断 Run Kind；
- Pending / 失败输出不可作为来源；
- Message Usage 可在来源预览中展示；
- PR09-10A 不修改 Discussion 状态、不自动重试协作 Run。

## 31. Commit 边界

阶段 A：

```text
docs: 制定PR09-10A草稿与成果实施计划
test: 增加阶段草稿与成果领域失败场景
feat: 建立通用阶段草稿与修订模型
feat: 增加成果类型与来源追溯
feat: 接入全局成果库
test: 完善成果库与独立组件场景
```

阶段 B：

```text
chore: 同步PR09-08合并后的主线接口
feat: 接入议题工作区草稿与成果入口
test: 冻结草稿与成果自动化标签
test: 验证Room v10草稿成果兼容
docs: 冻结PR09-10B与PR09-11成果接口
```

Commit 原子、测试先行、不添加 `Co-Authored-By`。

## 32. CI 与验证

远端至少读取：

```text
git diff --check
Secret scan
应用身份门禁
compileDebugKotlin
testDebugUnitTest
lintDebug
assembleDebug
assembleRelease
assembleDebugAndroidTest
Room Schema 当前性
```

分别记录阶段 A Head 和同步 PR09-08 后最终 Head。`assembleDebugAndroidTest` 只代表 APK 编译，不代表设备测试。

## 33. 本地严格只读验收

最终创建：

```text
docs/testing/pr-09-10a-local-readonly-acceptance-prompt.md
```

使用 `tools/local-verification/Invoke-LocalVerification.ps1` 和 `tools/device/cli.py --by tag`，日志写入仓库外 `$env:TEMP`。安装只允许 `adb install -r`，禁止 uninstall、pm clear、删除用户数据、生产网络和真实 API Key。

## 34. 风险与对应门禁

| 风险 | 门禁 |
|---|---|
| 草稿自动成为成果 | 最终确认前零 `confirmArtifact` 测试 |
| 每字符 Revision | 800 ms debounce + 同内容判定 |
| 保存失败丢正文 | UiState 保留编辑内容测试 |
| 并发静默覆盖 | 非连续 Revision → Conflict |
| 放弃误删历史 | Repository Instrumentation |
| 确认未 flush | Service 顺序测试 |
| 来源与正文不一致 | Draft Revision 强制来源 + 原子事务 |
| 跨 Issue / Stage 来源 | Builder + Repository 双层校验 |
| 覆盖旧 Artifact | 新 ID + `revisionOfArtifactId` |
| Revision 循环 / 分叉 | Resolver + 创建前门禁 |
| 成果库显示 Draft | Aggregator 只接收 Artifact |
| 成果失败破坏资料 | 独立 ArtifactLibraryState |
| 修改 PR09-08 共享文件 | 阶段 A 禁止清单 |
| 中文推断 Run Kind | 阶段 B 枚举测试 |
| 成果被当导出文件 | ArtifactType 测试 |
| 无成果阻止推进 | 架构测试无该门禁 |
| 正文进入标签 / 日志 | 静态隐私测试和 Secret scan |

## 35. 回滚

可回滚 UI 接线和成果库入口，但保留：

- 已保存 Draft；
- Draft Revision；
- Confirmed Artifact；
- Artifact 来源；
- PR09-08 Room v10；
- 历史 Run / Message / Usage。

不降级数据库，不删除用户内容，不恢复草稿自动过期或 AI 输出自动成为正式成果。

## 36. PR09-10B 交接

最终创建 `docs/planning/pr-09-10a-interface-handoff.md`，冻结：

- Artifact 稳定 ID、Type、Revision、Source；
- 读取 Artifact 内容和最新版本的方法；
- 音频只能关联 Message 或 Artifact；
- 音频失败或删除不修改 Artifact；
- Artifact 不是文件；
- 导出不是 Artifact Type；
- PR09-10B 不重写确认事务。

## 37. PR09-11 交接

冻结：

- 当前 Stage Draft、Artifacts、最新 Artifact、无 Artifact 状态；
- Draft 保存状态；
- 推进不得自动确认 Draft；
- 推进不得要求 Artifact；
- 旧 Stage Draft / Artifact 保留；
- 新 Stage 只继承引用，不复制冲突副本；
- PR09-11 不修改 Artifact 历史或删除当前 Draft。

## 38. 完成判定

阶段 A 完成表示：领域策略、成果库和独立组件已提交到 Draft PR，且没有越过 PR09-08 文件所有权。它不表示 PR09-10A 整体完成。

PR09-10A 整体完成必须同时满足：

- PR09-08 已合并并同步；
- Room 使用 v10 且未创建 v11；
- 最终工作区接线和中央标签完成；
- 全量 JVM / Lint / Debug / Release / AndroidTest 编译有真实证据；
- 全量设备 Instrumentation 和外部语义场景由本地 AI 实际验收；
- Draft PR 保持 Draft；
- 未启动 PR09-10B 或 PR09-11；
- 用户未授权前不标记 Ready、不合并、不删除分支。
