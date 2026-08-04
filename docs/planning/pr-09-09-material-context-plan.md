# PR09-09：资料、个人背景与显式上下文实施计划

> 状态：已冻结、可执行  
> 实际 Base：`main@228ec6f972684512fb6287d89c253da6c4aebd91`  
> 开发分支：`feat/pr-09-09-material-context-source`  
> 目标 PR：`feat: 建立见域资料与个人背景`（Draft）  
> 当前 Room：v8  
> 目标 Room：v9

## 1. 结论

本 PR 采用“领域模型 + `JianyuRepository` 命令 + 既有 Entity 映射”方案，不建立第二个 Repository，不让 UI 或执行协调器访问 DAO。资料与个人背景保留不同产品对象和数据表，通过统一的上下文选择、确认、字符预检和实际使用快照接入 PR09-07 的单一 `ExecutionRunCoordinator`。

Room 必须从 v8 升至 v9：v8 无法明确表达停用、归档、软删除、请求清除和已清除的不同语义，且两类 Usage Snapshot 缺少 `networkAllowed` 与 `sensitive`。迁移采用保守默认值，不把旧记录伪造为已授权联网。

V1 仅支持用户粘贴文本、手动笔记，以及 URL 加用户提供的摘录和来源定位；本 PR 不宣称支持任意 PDF、Office、云盘、后台抓取或自动更新。

## 2. 已核验基线

- PR #38 已合并，合并后 `main` 为 `228ec6f972684512fb6287d89c253da6c4aebd91`。
- PR #38 Head 为 `4e494a7c45f02512880370b9ef5ce5bf62e3a133`。
- PR #38 对应 Secret Scan 与 Android CI 均为成功状态。
- 上游记录的本地基线为 JVM `276 / 276 PASS`、Instrumentation `112 / 112 PASS`。
- 当前无开放 PR；未发现其他任务占用本 PR 将修改的上下文组装、Repository 或 Room 文件。
- 当前数据库版本为 v8，并保留 `8.json`。

## 3. 上游冻结接口

继续使用且不删除、不改写既有字段语义：

```text
ExecutionStartCommand
ExecutionRetryCommand
ExecutionContextContribution
ExecutionRunCoordinator
ExecutionRecoverySnapshot
ExecutionParticipantResult
ExecutionError
ExecutionBudgetSnapshot
```

`ExecutionContextContribution` 的 `sourceId`、`sourceType`、`content`、`contentHash`、`userConfirmedAt`、`networkAllowed`、`sensitive` 保持原义。上下文顺序继续由 `ExecutionContextBuilder` 固定，默认上限继续为 24,000 characters。

## 4. 方案比较与选择

### 4.1 方案 A：直接扩展 Entity 并向 UI 暴露

拒绝。虽然文件少，但生命周期和确认状态会与 Room 事实耦合，UI 容易依赖 nullable 时间戳推断业务状态，且不利于 PR09-06 复用。

### 4.2 方案 B：领域模型、命令与快照映射

采用。公共调用方使用 `Material`、`PersonalContext`、`ContextSelectionDraft`、`ContextConfirmation`、`ContextPreparationResult` 和稳定错误码；Entity 仅作为数据层事实。

### 4.3 方案 C：合并为通用 ContextSource 表

拒绝。会重写既有 v7/v8 Schema，破坏资料与个人背景的产品差异，并扩大 Migration 风险。

## 5. 领域模型

新增或冻结以下公共模型：

```text
ContextSourceType：MATERIAL / PERSONAL_CONTEXT
ContextSourceLifecycle：ACTIVE / DISABLED / ARCHIVED / DELETED / PURGE_REQUESTED / PURGED
Material
PersonalContext
MaterialFilter
PersonalContextFilter
ContextSelectionItem
ContextSelectionDraft
ContextConfirmation
PreparedExecutionContext
ContextPreparationResult
ContextValidationError
ContextUsageSnapshot
ContextPurgeImpact
```

资料记录稳定 ID、Issue、可选 Stage、标题、来源类型、来源定位、正文、正文哈希、来源发布日期、采集时间、创建/更新时间、敏感标记和生命周期。个人背景记录稳定 ID、标题、正文、正文哈希、敏感标记、创建/更新时间和生命周期。

## 6. 生命周期

生命周期使用明确枚举表达，不再仅凭 `deletedAt` 猜测：

```text
ACTIVE
DISABLED
ARCHIVED
DELETED
PURGE_REQUESTED
PURGED
```

- 停用：保留并可管理，但不可用于新执行。
- 归档：从默认活跃列表隐藏，可恢复。
- 删除：软删除，历史使用快照保持。
- 请求清除：已完成影响范围确认，等待受控清除事务。
- 已清除：当前正文与敏感元数据不可恢复；历史位置为匿名占位。

`PersonalContextEntryEntity.isEnabled` 在 v9 保留兼容，但 Repository 必须与生命周期同步；公共调用方不得直接推断该字段。

## 7. Room v9 决策与迁移

新增 `MaterialContextMigration.MIGRATION_8_9`，加入 `RoundtableDatabase.ALL_MIGRATIONS`，保留 v8 Schema，不使用 destructive migration。

最小字段：

- `material_references`：`lifecycleState`、`sensitive`、`disabledAt`、`archivedAt`、`purgedAt`。
- `personal_context_entries`：`lifecycleState`、`sensitive`、`disabledAt`、`archivedAt`、`purgedAt`。
- `material_usage_snapshots`：`networkAllowed`、`sensitive`。
- `personal_context_usage_snapshots`：`networkAllowed`、`sensitive`。

v8 旧来源按既有事实迁移：已删除记录为 `DELETED`，已请求清除记录为 `PURGE_REQUESTED`，其余资料为 `ACTIVE`；个人背景根据 `isEnabled` 迁移为 `ACTIVE` 或 `DISABLED`。旧 Usage Snapshot 使用 `networkAllowed=false`、`sensitive=true`，优先保护隐私，不伪造历史授权。

真实 `9.json` 必须由 Room 编译生成并从 CI artifact 提取提交，禁止手写 Identity Hash。

## 8. Hash 规范

使用 SHA-256、UTF-8。输入先把 `\r\n` 与单独 `\r` 统一为 `\n`，不做 trim、摘要、截断或语义改写；哈希对象必须与用户预览并实际发送的精确正文一致。空白正文在进入执行前拒绝。日志、异常、匿名占位和遥测不得输出正文或原始 Hash。

## 9. Repository 公共入口

所有入口加入现有 `JianyuRepository`，由 `RoomJianyuRepository` 转发给唯一的 `MaterialContextRepositoryComponent`：

### 9.1 资料

```text
createMaterial
updateMaterial
getMaterial
listMaterials
changeMaterialLifecycle
getMaterialPurgeImpact
purgeMaterial
```

### 9.2 个人背景

```text
createPersonalContext
updatePersonalContext
getPersonalContext
listPersonalContexts
changePersonalContextLifecycle
getPersonalContextPurgeImpact
purgePersonalContext
```

### 9.3 上下文

```text
prepareExecutionContext
listRunContextUsage
```

写命令使用稳定 ID 和 `expectedUpdatedAt`，支持幂等与乐观并发。错误复用 `RepositoryError`，通过稳定 `stateCode` / `constraintCode` 区分来源不存在、停用、归档、删除、清除、选择过期、Hash 冲突、重复来源、未授权联网、敏感确认缺失、超限、空正文和 Usage 冲突。

## 10. 选择草稿与确认

`ContextSelectionDraft` 保存候选、用户选择顺序、摘录、预期更新时间/Hash、联网授权和敏感确认，但不等于确认。个人背景默认无选中项。

`prepareExecutionContext` 必须：

1. 读取并逐项验证当前来源；
2. 验证生命周期、Issue/Stage 关系和乐观并发；
3. 拒绝重复 `sourceType + sourceId`；
4. 拒绝同来源不同 Hash；
5. 验证敏感项已显式确认；
6. 验证全部选中项允许本次发送到模型服务；
7. 计算正文、历史消息预估、当前问题和固定模板占用；
8. 在 24,000 characters 内按确认顺序生成 Contribution；
9. 同一确认时间按 `sourceType`、再按 `sourceId` 确定排序；
10. 构造不可变 Usage Snapshot 写入载荷。

取消确认不创建 Run、Pending、Usage Snapshot，不消费预算、不调用网络；选择草稿保留到用户主动清空。

## 11. 字符边界

在进入 `ExecutionContextBuilder` 前完成预检。小于或等于 24,000 通过，超过时返回 `context_too_large`，并保留逐项字符数、总占用和剩余额度。禁止静默截断、丢弃、摘要或模型压缩；用户可主动选择摘录、缩短文本或移除来源后重新确认。

## 12. 联网授权与敏感信息

`networkAllowed` 只表示用户允许本次正文发送给生产模型服务，不表示自动访问 URL、搜索、抓取或永久授权。任一贡献为 `false` 时，Coordinator 在创建 Runtime 前拒绝：零 Run、零 Pending、零预算消费、零网络调用，不得静默移除来源后继续。

`sensitive=true` 不代表禁止使用；用户显式确认后可发送。正文不得进入日志、异常、遥测、`toString()`、Crash breadcrumb 或普通列表语义。敏感列表默认只显示标题与“内容已隐藏”。

## 13. Usage Snapshot 与原子启动

采用扩展 Runtime 创建事务方案：

```text
CreateExecutionRuntimeCommand
  ├── ExecutionRun
  ├── Participant Snapshot
  ├── Participant State
  ├── Budget
  ├── Material Usage Snapshot
  └── Personal Context Usage Snapshot
```

`ExecutionRuntimeRepositoryComponent.createExecutionRuntime()` 在同一 Room 事务内验证并写入以上事实。任何 Usage 写入失败，整个事务回滚，Run 仍不存在；Coordinator 不创建 Pending、不消费预算、不调用网络。网络调用仍严格位于数据库事务外。

幂等比较加入 Usage Snapshot：相同幂等键和完全相同上下文返回既有 Runtime；相同幂等键但正文、Hash、授权、敏感标记或来源集合不同，返回冲突。

## 14. 重试

重试是新的发送。工作区展示原 Run 的历史 Usage Snapshot，但默认未确认；用户重新查看、授权和确认后才创建子 Run。不得自动读取来源当前版本替换旧快照，也不得自动继承已清除或已撤销授权正文。新子 Run 写入自己的 Usage Snapshot；原 Run 快照、成功成员和预算事实保持。

## 15. 恢复

进程恢复读取历史 Usage Snapshot 展示“本次实际使用”，不自动联网、不自动确认、不自动创建新快照、不替换为来源当前正文。已清除快照显示匿名占位，不能直接重试该内容。

## 16. 普通删除与彻底清除

普通删除只改变当前来源生命周期，不改历史 Run、Message、成果来源或 Usage Snapshot。

彻底清除前计算并展示关联 Issue 数、Stage 数、Usage Snapshot 数和 Run 数，需要二次确认。清除事务：

- 当前来源标题、正文、来源定位、来源类型/类别与原 Hash受控清空；
- 当前生命周期变为 `PURGED`；
- 历史 Usage Snapshot 的内容状态改为 `PURGED`；
- 快照标题、来源类型、定位、正文、Hash、敏感类别清空；
- `networkAllowed=false`、`sensitive=false`；
- 保留稳定关系和外键，不删除历史模型回答，不自动重跑。

用户界面统一显示不含来源类型、标题、Hash 或敏感类别的“内容已清除”匿名占位。

## 17. UI 架构

遵守：

```text
Route → ViewModel → Screen → Components
```

### 17.1 资料与成果

保留四个一级目的地和“资料 / 成果”两个顶层 Tab。在“资料”Tab 内加入资料库、个人背景、归档/删除筛选；支持搜索、新建、编辑、详情、停用、归档、恢复、删除、清除影响范围、加载、空、局部失败和存储失败。

### 17.2 议题执行工作区

在执行前和重试前展示“添加资料”“添加个人背景”“已选内容”“字符占用”“联网发送确认”和最终确认。打开工作区不自动勾选背景、不创建 Run、不写快照、不联网。单项读取失败仅影响该项，用户可移除或重试，不能静默改为无资料运行。

未确认草稿使用可保存状态恢复；Activity 重建后仍为未确认，不自动开始。

## 18. UiState

资料与上下文状态至少表达：

```text
Loading
Empty
Content
Editing
Confirming
PartialFailure
StorageFailure
StaleSelection
ContextTooLarge
NetworkPermissionRequired
PurgeConfirmation
PurgedPlaceholder
```

Snackbar、导航等一次性事件与来源、选择、授权、字符预算和生命周期稳定状态分离。

## 19. 搜索与筛选

资料支持标题、来源类型、生命周期、Issue、Stage；个人背景支持标题和生命周期。本地搜索不记录正文，不引入云端搜索、Embedding、自动语义分类或自动敏感分类上传。

## 20. 生产文件

### 新增

```text
app/src/main/java/com/elio/jianyu/data/MaterialContextModels.kt
app/src/main/java/com/elio/jianyu/data/MaterialContextRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/MaterialContextMigration.kt
app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesUiState.kt
app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesViewModel.kt
app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesScreen.kt
app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesComponents.kt
app/src/main/java/com/elio/jianyu/ui/screens/context/ContextConfirmationUiState.kt
app/src/main/java/com/elio/jianyu/ui/screens/context/ContextConfirmationComponents.kt
```

### 修改

```text
app/src/main/java/com/elio/jianyu/data/ResourceLifecycle.kt
app/src/main/java/com/elio/jianyu/data/ResourceLifecycleEntities.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/data/ExecutionRuntimeRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/ExecutionRuntimeRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionModels.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionRunCoordinator.kt
app/src/main/java/com/elio/jianyu/ui/App.kt
app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionViewModel.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionUiState.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionScreen.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionComponents.kt
```

如实现证明某个新增 UI 文件不需要，将逻辑保留在同层现有文件，不跨层放置。

## 21. 测试文件

新增或扩展：

```text
app/src/test/java/com/elio/jianyu/data/MaterialContextModelsTest.kt
app/src/test/java/com/elio/jianyu/data/ContextSelectionValidatorTest.kt
app/src/test/java/com/elio/jianyu/data/ContextPrivacyTest.kt
app/src/test/java/com/elio/jianyu/execution/ExecutionContextUsageGateTest.kt
app/src/test/java/com/elio/jianyu/ui/MaterialContextArchitectureTest.kt
app/src/androidTest/java/com/elio/jianyu/data/MaterialContextRepositoryTest.kt
app/src/androidTest/java/com/elio/jianyu/data/MaterialContextMigrationTest.kt
app/src/androidTest/java/com/elio/jianyu/data/ExecutionRuntimeContextUsageTest.kt
app/src/androidTest/java/com/elio/jianyu/data/MaterialContextPurgeTest.kt
app/src/androidTest/java/com/elio/jianyu/ui/screens/resources/ResourcesScreenTest.kt
app/src/androidTest/java/com/elio/jianyu/ui/screens/execution/IssueContextConfirmationTest.kt
```

并更新现有 Room 连续迁移、Repository、Coordinator、导航和架构守卫测试。

## 22. TDD 首个失败测试

首个测试提交只新增以下失败场景，不含生产实现：

1. SHA-256 对规范化换行后的精确正文稳定；
2. 个人背景选择默认空；
3. 未授权联网的 Contribution 在 `createRuntime` 前被拒绝，Fake 网络调用数为零；
4. 24,001 characters 在 Builder 前返回 `context_too_large`；
5. 相同来源不同 Hash 返回冲突；
6. Runtime 与两类 Usage Snapshot 原子创建；
7. v8→v9 旧快照不会被迁移为已授权联网。

预期 Red 原因是新领域 API、v9 字段和原子命令尚不存在。CI 红色日志作为 Red 证据；随后按最小实现推进到 Green。

## 23. 架构守卫

测试必须确认：

- UI、ViewModel、上下文组件不引用 DAO；
- Repository 不调用 Gemini；
- Context Adapter 不调用网络；
- 只有一个 `ExecutionRunCoordinator` 和一个 `ExecutionContextBuilder`；
- 状态机、预算、Stop、迟到回调和成功成员过滤语义未改变；
- 不存在默认全量背景或自动全量资料注入。

## 24. 禁止修改

```text
44 项 Skill Catalog 定义
ExecutionRunStatus / ExecutionParticipantStatus 枚举语义
预算消费策略
Stop 顺序与迟到回调防护
网络 Gateway 与 API Key Pool
根导航四目的地
成果、音频、Issue 归档业务
最终品牌视觉
Gradle 与依赖版本
```

若必须改写 PR09-07 核心状态机才能继续，停止写入并报告独立接口缺口。

## 25. Commit 边界

```text
docs: 制定PR09-09资料与背景实施计划
test: 增加资料与背景领域失败场景
feat: 增加资料与背景Room v9迁移
test: 完善资料背景连续迁移验证
feat: 建立资料与个人背景Repository
feat: 增加执行上下文确认与使用快照
feat: 接入资料与背景管理页面
test: 完善资料背景设备与隐私验证
docs: 冻结PR09-06上下文接口交接
```

根据真实差异可以合并相邻原子提交，但不得夹带无关重构或添加 `Co-Authored-By`。

## 26. 远端验证

当前环境没有本地 Android 工作区。通过 GitHub Actions 实际执行：

```text
compileDebugKotlin
testDebugUnitTest
lintDebug
assembleDebug
assembleRelease / R8
app identity gate
Room schema current gate
secret scan
```

每次失败先读取具体 Job/Step/日志，定位根因后做单一修复。CI 生成的 Room `9.json` 从 `room-schema-*` artifact 提取后提交，再重新运行全部门禁。

普通 PR CI 若未运行设备测试，必须明确写“远端未实际执行设备 Instrumentation；等待本地 AI 严格只读验收”。

## 27. 本地验收

完成后创建 `docs/testing/pr-09-09-local-read-only-acceptance.md`，要求本地 AI：拉取远端分支；只读取、构建、测试和模拟器验收；不得修改、格式化、提交、推送、变基、合并或改变 PR 状态；记录 OS、工具版本、命令、退出码、测试统计和关键日志；最终工作区干净且 Head 不变。

至少执行全量 JVM、Lint、Debug/Release、完整连续 Migration、资料/背景/Usage/原子 Runtime/Purge/Compose 定向测试和全量 `connectedDebugAndroidTest`，并核对 PR #38 后 `112 / 112` 基线无回归。

## 28. PR09-06 交接

完成时创建 `docs/planning/pr-09-09-interface-handoff.md`，冻结公共命令、选择草稿、确认、准备结果、错误码、Hash、排序、24,000 字符边界、联网授权、Usage 写入时点、重试、清除和文件所有权。PR09-06 只能消费接口，不访问 DAO、不自行拼接全文、不静默超限。

## 29. CI 与 Review

Draft PR 创建后保持 Draft。实现完成后检查最新 Head 的 CI、差异、Review Thread 和提交列表；Critical/Important 问题在当前分支修复。未经用户明确授权不标记 Ready、不合并、不删除分支、不启动 PR09-06。

## 30. 风险

- v8→v9 Migration 或 Schema Identity 漂移；
- 旧快照被误标记为已联网授权；
- 正文进入日志、异常或语义树；
- 背景默认选中；
- Run 创建后 Usage 写入失败；
- 重试读取当前正文替换历史；
- 清除导致孤儿外键或匿名占位泄密；
- 字符边界静默截断；
- PR09-07 状态机、预算、Stop 或迟到回调回归；
- Compose 重建越过确认门禁。

## 31. 回滚

关闭资料/背景和上下文入口时保留已写数据与 v9 Schema。若 v9 有缺陷，只能通过新的前向 Migration 修复，不回退为 v8，不删除历史 Usage Snapshot。执行集成可关闭确认入口但不得恢复静默全量背景传递，也不得回滚 PR09-07 Run、预算或 Participant 数据。

## 32. 完成判据

只有同时满足以下条件才可宣称实现完成：

- 资料库和个人背景库通过公共 Repository 可用；
- 背景默认不选；
- 资料与背景必须显式确认；
- 未授权联网时零 Run、Pending、预算和网络调用；
- 24,000 超限不截断；
- Hash 与实际正文一致；
- Run 与 Usage Snapshot 原子一致；
- 编辑、普通删除不改历史；
- 清除产生不泄密匿名占位且外键完整；
- 重试重新确认且不自动读取当前正文；
- 恢复不自动联网；
- PR09-07 状态机和预算无回归；
- v9 Schema、Migration、CI 与设备验收均有真实证据；
- PR 保持 Draft，PR09-06 未启动。

## 33. 未验证项

计划提交阶段尚未修改生产代码、尚未运行本分支 CI、尚未生成 `9.json`、尚未执行设备 Instrumentation、窄屏/200% 字号/明暗主题/TalkBack/Activity 重建尚未验收。这些项目只能在后续真实 CI 和本地严格只读验收后更新状态。

## 34. Superpowers 声明

Superpowers 插件接口未调用；本任务读取仓库内保存的 Superpowers 6.2.0 Skill 文件，并按照项目适配规则执行等价人工流程。使用的流程包括 brainstorming、writing-plans、test-driven-development、systematic-debugging、verification-before-completion、requesting-code-review 和 finishing-a-development-branch；未使用 worktree、并行子智能体或自动合并。
