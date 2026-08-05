# PR09-11 推进议题与阶段继承实施计划

> **执行方式：** 当前对话无法直接调用 Superpowers 插件，已读取仓库内 `brainstorming`、`writing-plans`、`test-driven-development` 与 `verification-before-completion` Skill，并按等价人工流程执行。禁止使用 Worktree、子智能体、自动合并。

## 1. 目标

在现有 `IssueExecution` 单一工作区中实现正式“推进议题”流程：用户通过方向、措施/自定义目标、摘要确认三步建立一个新 Stage；最终确认前零数据库写入，最终确认后在单一 Room 事务中写入 Stage、推进快照、计划阵容及继承关系；新 Stage 不自动创建 Run、Message、Draft、Artifact、Audio 或网络任务；尚未产生任何业务依赖的最新 Stage 可以通过“撤销新阶段”原子删除。

## 2. 实际基线与并行状态

```text
仓库：elio-zwd/AI-Skill-Roundtable
Base 分支：main
实际 Base SHA：b46bb9eebfed123ca1e7f5f2f6923c7ab53e0644
开发分支：feat/pr-09-11-advance-issue
当前 Room：v10
开放 PR：无
PR09-10B 分支：未发现
PR09-10B Draft PR：未发现
```

开始时 `main` 未超过施工单指定基线。若开发期间 `main` 前进，不主动变基或覆盖其他分支；先检查新提交与本分支文件重叠，再在 PR 描述记录实际差异与后续同步要求。

PR09-11 独占 Room、Repository 根实现、`IssueExecution` 工作区和 Stage/Advance 自动化标签。PR09-10B 后续开始时必须避开本计划列出的独占文件，直到本 PR 合并。

## 3. 已确认的产品契约

正式入口仅使用“推进议题”。自由追问继续当前 Stage；推进议题创建同一 Issue 中的新 Stage；新议题不属于本 PR。入口在当前 Stage 工作区始终可见，打开、返回、取消、关闭和 Activity/进程重建都不创建 Stage。

三步流程固定为：

1. 方向：`REALITY_SUPPORT`、`THINKING_EXPANSION` 至少选择一个，允许同时选择；双方向仍只创建一个 Stage。
2. 措施与目标：允许多选预设措施；预设措施按“现实支持在前、思维拓展在后”，各方向内部按产品清单顺序稳定排序；“自定义目标”可与预设措施组合。系统提供确定性目标建议，但正式 `objective` 始终取用户在第二步确认的非空编辑值，不从中文文案反推枚举。
3. 摘要：展示目标、方向、措施、默认继承内容、调整后阵容、重点资料、继承成果、预期输出以及当前运行/草稿提示。只有本步显式确认才写数据库。

任何方向、措施、目标、预期输出、阵容、资料或成果发生编辑后，旧摘要确认标记立即失效，必须重新进入确认态。

## 4. v10 与 v11 比较及最终决策

### 4.1 方案 A：只使用现有 `StageEntity`

`StageEntity` 只能表达 Stage 的身份、顺序、标题和目标，无法跨进程明确表达来源 Stage、方向、措施、确认时间、预期输出、operationId、继承关系和计划阵容。依赖 ViewModel、`SavedStateHandle` 或页面文案恢复这些事实不可接受。

### 4.2 方案 B：编码到 `title` / `objective`

会混淆用户正文与内部协议，也无法建立可校验外键、稳定排序和幂等冲突检测；禁止使用。

### 4.3 方案 C：最小 Room v11

采用。历史 Stage 不回填、不伪造推进关系。v10 Stage 升级后默认没有 Advancement Snapshot。

## 5. Room v11 最终 Schema

新增文件：

```text
app/src/main/java/com/elio/jianyu/data/StageAdvancementEntities.kt
app/src/main/java/com/elio/jianyu/data/StageAdvancementMigration.kt
```

### 5.1 `stage_advancements`

实体：`StageAdvancementEntity`

```text
stageId                 TEXT PRIMARY KEY，指向新 Stage
issueId                 TEXT，新旧 Stage 同属 Issue
sourceStageId           TEXT，指向推进来源 Stage
operationId             TEXT UNIQUE，稳定用户操作 ID
payloadHash             TEXT，规范化命令 SHA-256
realitySupport          INTEGER NOT NULL
thinkingExpansion       INTEGER NOT NULL
objective               TEXT NOT NULL
expectedOutput          TEXT NOT NULL
confirmedAt             INTEGER NOT NULL
createdAt               INTEGER NOT NULL
```

约束：

- `(stageId, issueId)` 指向 `stages(id, issueId)`；
- `(sourceStageId, issueId)` 指向 `stages(id, issueId)`；
- 新 Stage 与来源 Stage 不得相同；
- 两个方向至少一个为真；
- `objective`、`expectedOutput`、`operationId`、`payloadHash` 非空；
- `operationId` 唯一。

### 5.2 `stage_advancement_measures`

实体：`StageAdvancementMeasureEntity`

```text
stageId
issueId
measure                 稳定存储值
position                从 0 连续排序
```

主键 `(stageId, measure)`，唯一索引 `(stageId, position)`。措施枚举：

```text
clarify_next_step
form_execution_plan
analyze_execution_obstacles
generate_deliverable
set_checkpoints
introduce_counterargument
find_missing_perspectives
check_key_assumptions
compare_positions
deepen_question
custom_objective
```

不从中文文案解析枚举。`custom_objective` 只有在用户显式选择自定义目标时写入；正式目标文本仍保存在 Advancement 根记录。

### 5.3 `stage_advancement_skill_members`

实体：`StageAdvancementSkillMemberEntity`

```text
stageId
issueId
officialSkillId
position
responsibility
sourceRunId                 可空
sourceParticipantSnapshotId 可空
catalogVersionBasis          可空
confirmedAt
```

主键 `(stageId, officialSkillId)`，唯一索引 `(stageId, position)`。不复制 System Prompt、配置 JSON 或完整 Participant Snapshot。来自当前 Stage 最近 STANDARD 根 Run 时保存来源 Run / Snapshot；用户新增成员时保存 Catalog 版本依据。新执行前仍通过官方 Catalog 和执行资格重新解析，资格撤回只阻止新执行并显示原因，不删除计划历史。

### 5.4 `stage_advancement_materials`

实体：`StageAdvancementMaterialEntity`

```text
stageId
issueId
materialReferenceId
position
inheritedAt
```

主键 `(stageId, materialReferenceId)`，只引用 `MaterialReferenceEntity.id`，不复制资料正文、授权或敏感确认。

### 5.5 `stage_advancement_artifacts`

实体：`StageAdvancementArtifactEntity`

```text
stageId
issueId
artifactId
position
inheritedAt
```

主键 `(stageId, artifactId)`，只引用正式 `ConfirmedArtifactEntity.id`，不复制成果正文，不接受 Draft 或 Draft Revision ID。

### 5.6 TypeConverter 与 Migration

`StageAdvancementConverters` 只转换措施枚举。`StageAdvancementMigration.MIGRATION_10_11` 使用显式 `CREATE TABLE` / `CREATE INDEX`，不重建或改写历史表。`RoundtableDatabase` 升至 v11，加入五个实体和 Converter，迁移链固定为：

```text
1→2→3→4→5→6→7→8→9→10→11
```

`11.json` 必须由 Room/KSP 真实生成，禁止手写。当前对话没有可用本地 Android 构建环境，因此先通过 Draft PR 触发 CI；CI 即使因 Schema freshness 失败，也会上传 `room-schema-*` artifact。下载该 artifact 中真实 `11.json` 后提交，再重跑 CI。

## 6. 领域命令、幂等与原子事务

新增：

```kotlin
data class AdvanceIssueCommand(...)
data class AdvanceIssueResult(
    val stage: StageEntity,
    val advancement: StageAdvancementSnapshot,
)
data class StageAdvancementSnapshot(...)
```

命令包含：

```text
operationId
issueId
sourceStageId
newStageId
newStageTitle
objective
方向集合
稳定排序措施
expectedOutput
计划阵容
继承 Material ID
继承 Artifact ID
confirmedAt
```

规范化 payload 使用明确字段顺序、UTF-8、长度前缀编码后计算 SHA-256，不使用 `toString()`、JSON Map 顺序或可见中文文案。事务规则：

```text
相同 operationId + 相同 payloadHash
→ 返回已存在 Stage，idempotent=true

相同 operationId + 不同 payloadHash
→ RepositoryError.IdempotencyConflict("advance_issue", operationId)
```

首次写入时，在同一 `JianyuRepositoryTransactions.transaction("advance_issue")` 中：

1. 校验 Issue、来源 Stage 存在且来源是当前最新 Stage；
2. 校验命令方向、措施、目标、预期输出、稳定 ID 和顺序；
3. 校验计划 Skill、Material、Artifact 均存在且属于允许范围；
4. 创建 `StageEntity(sequenceIndex = max + 1)`；
5. 写 Advancement 根；
6. 写措施、阵容、资料、成果关系；
7. 返回完整快照。

任一步失败由 Room 事务整体回滚。事务中不调用网络、不停止 Run、不创建 Run/Message/Draft/Artifact/Audio、不准备上下文、不消费预算。

保留已有 `createStage()` 只用于兼容现有调用方；正式“推进议题”UI 只能调用 `advanceIssue()`。

## 7. 阶段继承

继承是关系，不复制对象：

- 资料：默认候选来自当前 Issue 可用 `MaterialReferenceEntity`，用户明确选中的 ID 写入 `stage_advancement_materials`；不继承 `networkAllowed`、`sensitiveConfirmed`、上次 Usage 或模型上下文选择。
- 成果：默认候选为当前 Issue 已确认的 `ConfirmedArtifactEntity`，选择 ID 写入 `stage_advancement_artifacts`；Draft、未保存编辑器正文和 Draft Revision 不能作为正式继承成果。
- 草稿：旧 Stage Draft 与 Revision 保留。若编辑器存在未持久化正文，UI 必须提供“保存草稿后继续 / 放弃未保存修改后继续 / 返回编辑”，不得静默丢失，也不得自动确认成果。
- 个人背景：不建立继承表，默认不选择；后续每次执行继续走既有上下文确认。
- 判断、分歧、行动项：通过旧 Stage 正式 Draft/Artifact 和摘要展示，不复制正文到推进表；正式目标与预期输出只保存用户本次确认值。

无资料、无成果、无 Draft 均允许推进。

## 8. 统一阵容读取策略

扩展现有 `CurrentStageRosterPolicy`，不建立第二套协作事实源。新增中立 DTO：

```kotlin
data class CurrentStageRosterMember(
    val officialSkillId: String,
    val position: Int,
    val responsibility: String,
    val sourceRunId: String?,
    val sourceParticipantSnapshotId: String?,
)

sealed interface CurrentStageRosterSource {
    data class StandardRun(...)
    data class AdvancementPlan(...)
    data object NoRoster
}
```

统一规则：

```text
当前 Stage 存在最新 STANDARD 根 Run
→ StandardRun，读取其 Participant Snapshot

否则存在该 Stage 的已确认 Advancement Skill Member
→ AdvancementPlan

否则
→ NoRoster
```

Directed/Cross Run 永不成为长期阵容来源。新 Stage 的首次正式执行在创建真实 Run 时，通过官方 Catalog / Resolver 按计划 Skill ID 重新生成本次 Participant Snapshot；不创建假 Run、NOT_STARTED 占位 Run或全 Catalog 自动成员。

## 9. 活动运行处理

打开三步流程只读取状态。第三步确认前重新读取 Room，检查：

- 活动 STANDARD / Directed / Cross Response / Synthesis Run；
- `AWAITING_SYNTHESIS` / `PARTIAL_SUCCESS` Discussion；
- 草稿保存中和未持久化编辑器正文。

有网络运行时提供：

```text
等待当前运行完成
明确停止当前运行后推进
取消推进
```

“停止后推进”由 Route 调用既有 `IssueExecutionViewModel.stop()` 或协作 Stop 入口，等待终态持久化后重新加载 Advancement 候选；用户必须再次确认摘要。停止和创建 Stage 不在同一个事务，Stop 失败时不创建 Stage。`AWAITING_SYNTHESIS`、`PARTIAL_SUCCESS` 可以保留在旧 Stage 并继续推进，但摘要必须显示未完成事实；不自动 Synthesis。

迟到回调仍按 Run/Stage 外键和既有状态机写回旧 Stage，不得改变新 Stage。

## 10. 未运行 Stage 撤销

正式入口为“撤销新阶段”。Repository 在事务中重新校验：

- Stage 是当前 Issue 最新 Stage；
- `sequenceIndex > 0`；
- Stage 具有 Advancement 根关系；
- Run、Message、Draft、Draft Revision、Artifact、Material Usage、Personal Context Usage、AudioAsset、Discussion、Message Usage 和已知后台业务任务计数均为 0。

推进关系本身不计为业务依赖。满足条件时按外键依赖顺序删除：

```text
stage_advancement_measures
stage_advancement_skill_members
stage_advancement_materials
stage_advancement_artifacts
stage_advancements
stages
```

任一步失败整体回滚。重复点击在 Stage 已被删除后返回稳定 NotFound/已完成 UI 结果，不删除前一 Stage、Issue、全局资料、旧消息、旧成果或 Catalog。只要创建过任何 Run，即使失败或停止，也永久禁止“未运行撤销”。

## 11. UI 与恢复

新增同域文件：

```text
app/src/main/java/com/elio/jianyu/ui/screens/execution/AdvanceIssueUiState.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/AdvanceIssueViewModel.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/AdvanceIssueFlow.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/StageTimeline.kt
```

保持：

```text
IssueExecutionRoute
→ AdvanceIssueViewModel
→ AdvanceIssueUiState
→ IssueExecutionScreen / AdvanceIssueFlow
```

流程使用工作区内 Dialog，不创建第二个顶层页面状态机。`SavedStateHandle` 只保存未确认表单草稿、步骤和稳定 ID，不保存已创建事实。恢复时只读取 Room 并重建候选；不会自动调用 `advanceIssue()`。

UiState 覆盖：

```text
Idle
LoadingCandidates
DirectionStep
MeasureStep
SummaryStep
WaitingForRun
StoppingCurrentRun
CreatingStage
Created
CreateFailure
IdempotencyConflict
UndoAvailable
Undoing
UndoFailure
RestoredDraft
StorageFailure
```

稳定状态与一次性导航事件分离。`creating/undoing/stopping` 操作锁防止双击、Stop 后重复创建、导航重放和撤销双击。创建成功事件导航到新 Stage；导航本身不是事实源。

阶段时间线按 `sequenceIndex` 排序，明确标记当前 Stage。查看历史 Stage 时，“推进议题”仍以数据库最新 Stage 为来源；若当前路由展示的 Stage 不是最新 Stage，按钮先提示返回当前 Stage，不能从历史 Stage 创建分叉。

## 12. 自动化标签

扩展 `JianyuAutomationTags`：

```text
AdvanceIssue
StageTimeline
```

静态标签：

```text
issue_advance_button
stage_timeline
stage_timeline_current
advance_issue_dialog
advance_direction_step
advance_direction_reality_support
advance_direction_thinking_expansion
advance_measure_step
advance_custom_objective
advance_summary_step
advance_inherited_materials
advance_inherited_artifacts
advance_roster
advance_expected_output
advance_confirm
advance_cancel
advance_wait_for_run
advance_stop_current_run
advance_failure
stage_undo_button
stage_undo_confirmation
```

动态标签：

```text
stage_timeline_item_<stableStageId>
advance_roster_member_<stableSkillId>
advance_material_<stableMaterialId>
advance_artifact_<stableArtifactId>
```

动态部分统一调用 `normalizedStableId()`；不得包含标题、正文、姓名或 Prompt。新增静态标签加入 `frozenStaticTags` 并由唯一性测试冻结。

## 13. TDD 任务顺序

### Task 1：纯领域策略失败测试

先新增 JVM 测试，覆盖方向至少一个、措施稳定排序、双方向单 Stage 命令、目标非空、编辑使摘要确认失效、规范化 payload hash、统一 Roster 来源优先级。由于当前对话无法运行 Gradle，首次提交只声明“测试代码已写，RED 未实际执行”，不能声称已观察失败。

### Task 2：Repository 原子创建与撤销失败测试

新增 Room Instrumentation 测试，覆盖原子写入、同键同 payload、同键不同 payload、外键失败零半写、Material/Artifact 不复制、无成果推进、任意 Run 阻止撤销、Discussion/Message Usage/Audio 等阻止撤销、失败不半删。

### Task 3：Room v11 Migration 测试

更新连续 Migration 测试到 v11，覆盖 v1～v11、v10→v11、旧 Run Kind、Message Usage、Draft/Artifact/Audio 保留、v10 Stage 无 Advancement、`PRAGMA foreign_key_check` 为 0、Schema freshness。

### Task 4：ViewModel 状态机测试

覆盖打开/取消零写入、步骤返回保留选择、双击操作锁、Activity/进程恢复不创建、运行中等待与 Stop 后再次确认、创建成功一次性导航、撤销双击。

### Task 5：Compose / Instrumentation

覆盖入口始终显示、三步流程、双方向、目标编辑、继承摘要、运行中提示、撤销、360dp、200% 字号、键盘、TalkBack 语义和明暗主题。外部 UIAutomator 使用中央标签。

## 14. 预计修改文件

### 数据与迁移

```text
Create  app/src/main/java/com/elio/jianyu/data/StageAdvancementEntities.kt
Create  app/src/main/java/com/elio/jianyu/data/StageAdvancementMigration.kt
Create  app/src/main/java/com/elio/jianyu/data/StageAdvancementRepositoryComponent.kt
Modify  app/src/main/java/com/elio/jianyu/data/JianyuRepositoryContract.kt
Modify  app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt
Modify  app/src/main/java/com/elio/jianyu/data/IssueExecutionRepositoryComponent.kt
Modify  app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
Modify  app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
Generate app/schemas/com.elio.jianyu.data.RoundtableDatabase/11.json
```

### 阵容与运行时

```text
Modify  app/src/main/java/com/elio/jianyu/collaboration/CollaborationPolicies.kt
Modify  app/src/main/java/com/elio/jianyu/collaboration/IssueCollaborationCoordinator.kt
Modify  app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt
```

### 工作区 UI

```text
Create  app/src/main/java/com/elio/jianyu/ui/screens/execution/AdvanceIssueUiState.kt
Create  app/src/main/java/com/elio/jianyu/ui/screens/execution/AdvanceIssueViewModel.kt
Create  app/src/main/java/com/elio/jianyu/ui/screens/execution/AdvanceIssueFlow.kt
Create  app/src/main/java/com/elio/jianyu/ui/screens/execution/StageTimeline.kt
Modify  app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionRoute.kt
Modify  app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionScreen.kt
Modify  app/src/main/java/com/elio/jianyu/ui/automation/JianyuAutomationTags.kt
Modify  app/src/main/java/com/elio/jianyu/ui/App.kt（只做依赖与导航接线）
```

### 测试与文档

```text
Create/Modify app/src/test/java/com/elio/jianyu/.../StageAdvancement*Test.kt
Create/Modify app/src/androidTest/java/com/elio/jianyu/.../StageAdvancement*Test.kt
Modify        app/src/androidTest/java/com/elio/jianyu/data/RoundtableDatabaseMigrationTest.kt
Modify        自动化标签与 IssueExecution UI 测试
Create        docs/planning/pr-09-11-interface-handoff.md
Create        docs/testing/pr-09-11-local-readonly-acceptance-prompt.md
```

只修改完成本任务所需文件；不升级依赖，不修改 PR09-10B 音频实现，不建立第二个工作区。

## 15. 验证与证据分类

远端 CI / 可执行环境最终应执行：

```text
git diff --check
Secret scan
compileDebugKotlin
testDebugUnitTest
lintDebug
assembleDebug
assembleRelease
assembleDebugAndroidTest
Room Schema freshness
Migration 连续性
```

当前 GitHub 插件本身不能执行 Gradle。只有读取到 GitHub Actions 成功状态、Job 步骤和日志后，才记录为“GitHub CI 已通过”；未运行设备测试一律记录为“尚未验证，等待本地只读验收”。

本地只读验收使用：

```text
tools/local-verification/Invoke-LocalVerification.ps1
tools/device/cli.py
```

禁止卸载/清空 App、生产网络、真实 API Key、修改文件、提交、推送、合并。

## 16. Commit 组织

```text
docs: 制定PR09-11推进议题实施计划
test: 增加推进议题失败场景
test: 增加阶段继承与撤销失败场景
feat: 建立阶段推进与继承领域模型
feat: 增加Room v11阶段推进迁移
feat: 实现推进议题原子事务
feat: 实现三步确认与阶段时间线
test: 冻结推进议题自动化标签
test: 验证推进恢复与未运行阶段撤销
docs: 冻结PR09-12生命周期接口
```

在 GitHub 内容 API 无法把多文件变更可靠拆成上述全部粒度时，仍保持每个 Commit 单一意图，不把计划、Schema、UI 和交接文档混成无法回滚的单次提交。

## 17. 风险与缓解

1. **远端无本地 Gradle 环境：** 通过 Draft PR CI 生成真实 Schema artifact；任何未执行命令明确标注，不虚构结果。
2. **`IssueExecution` 已承载多个状态机：** Advancement 使用独立 ViewModel/UiState，但只通过现有 Route/Screen 组合，不复制执行、协作或成果状态机。
3. **新 Stage 首次执行无 Run Snapshot：** 统一 Roster Provider 使用计划成员，并在真实 Run 创建时重新通过 Catalog Resolver 生成 Participant Snapshot。
4. **Stop 与创建竞态：** Stop 终态持久化后重新读取 Room并要求再次确认；Repository 在写事务中再次检查来源 Stage 和活动事实。
5. **撤销漏检依赖：** 集中 DAO 计数快照和 Instrumentation 测试覆盖每一类现有 Stage 依赖；未知后台任务不能静默忽略。
6. **PR09-10B 冲突：** 开发期间定期检查开放 PR/分支及目标文件；发现重叠立即停止覆盖并报告。

## 18. 回滚

PR 未合并前直接关闭 Draft PR并保留分支供审查。合并后如需代码回滚，只能新增前向修复 PR；不得把 Room 从 v11 降回 v10，不得删除 `11.json` 或使用 destructive migration。新表没有历史回填，关闭 UI 入口即可停止产生新推进记录；已有 Stage/Advancement 关系必须保留并由后续前向迁移处理。

## 19. PR09-12 交接

`docs/planning/pr-09-11-interface-handoff.md` 将冻结：

- Advancement 五表与稳定存储值；
- 当前 Stage = Issue 中最大 `sequenceIndex` 的规则；
- 来源 Stage 和继承关系；
- Stage 计划阵容与 STANDARD 根 Run 优先级；
- Material/Artifact 只引用不复制；
- Context 授权不继承；
- 活动 Run 的等待/停止/再次确认流程；
- 未运行最新 Stage 的物理撤销边界；
- 历史 Stage 回顾与迟到回调隔离；
- PR09-12 归档、清理和生命周期能力必须保留的关系；
- Room 只能前向升级，禁止物理降级。

## 20. 完成门禁

只有以下证据齐备才可申请用户验收：

- Draft PR 存在且保持 Draft；
- 代码差异与计划一致；
- 真实 KSP `11.json` 已提交；
- GitHub CI 结果已读取并记录；
- 设备与外部 UIAutomator 未执行项有只读验收 Prompt；
- PR09-10B 文件未被覆盖；
- 未标记 Ready、未合并、未删除分支。
