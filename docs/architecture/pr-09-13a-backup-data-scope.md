# PR09-13A：见域备份数据范围

> 本文冻结可移植备份与设备绑定快照的数据白名单。生产实现必须从正式 Repository/Service 枚举对象，不允许复制整个 App 目录或扫描目录猜测所有权。

## 1. 总原则

- 使用逐对象白名单；未列入白名单的对象默认排除。
- 数据库字段名不是永久备份协议；PR09-13B 必须通过显式 Mapper 生成稳定 Manifest/Record。
- 可移植备份以逻辑对象和稳定关系为事实源。
- 设备快照以一致性数据库主文件、Snapshot Manifest 和受控正式音频文件为事实源。
- 已成功 Purge 的对象、正文、索引、标题和文件永远排除。
- API Key、Keystore、应用锁和授权材料永远排除。
- 外部 URI 默认只保存可移植引用事实，不读取或复制外部原始文件。

## 2. 可移植备份默认白名单

### 2.1 Issue 和 Stage

包含：

- `IssueEntity` 的稳定 ID、标题、目标、当前阶段引用、生命周期允许的正式字段和时间字段。
- `StageEntity` 的稳定 ID、Issue 关系、顺序、目标、继承摘要和正式状态。
- Stage Advancement、Measure、Skill Member、Material 和 Artifact 关系。

条件：

- Issue 不存在 Purge Operation；
- `purgeRequestedAt == null`；
- Issue 不处于不可完整恢复的中间状态；
- 所有关系通过外键和业务不变量检查。

排除：

- UI 临时选择；
- 未确认推进表单；
- 内存中的编辑器输入；
- 旧备份中的清理意图。

### 2.2 ExecutionRun、Participant 和 Message

包含：

- `ExecutionRunEntity` 的稳定 ID、类型、状态、Issue/Stage 关系和时间事实。
- `ExecutionParticipantSnapshotEntity` 的稳定 Skill ID、顺序、职责、风险/声明和必要的历史执行配置快照。
- `ExecutionParticipantStateEntity` 的终态或可恢复事实。
- `ExecutionRunBudgetEntity` 的预算和实际使用事实，不包含 API Key。
- 非 Pending 的 `Message`。
- Execution Message Usage、Material Usage、Personal Context Usage 等本次实际使用快照。
- Cross Discussion Session 的稳定参与者、焦点和结果关系。

排除或阻止：

- `isPending == true` 的 Message；
- 仍为 Running/Queued 且无法稳定收敛的 Run；
- 仅存在内存中的重试计划；
- API Key ID、Key 尝试顺序、Session Key 绑定、Authorization Header；
- 生产日志和网络错误正文。

备份开始时发现活动 Run：

```text
active_work_in_progress
```

不得自动停止用户任务，也不得把 Pending 内容当作正式历史。

### 2.3 Draft、Revision 和 Artifact

包含：

- `StageSummaryDraftEntity`；
- `StageSummaryDraftRevisionEntity`；
- `ConfirmedArtifactEntity`；
- Artifact Message、Run、Draft、Material Source 关系；
- 正式内容格式、类型、修订链和确认时间。

排除：

- Compose/Editor 未保存内存；
- 自动建议但未持久化的文本；
- 临时渲染缓存和缩略图；
- 已 Purge 来源的正文或标题缓存。

### 2.4 MaterialReference 和 Material Usage

包含：

- App 数据库中正式保存的 MaterialReference 内容、内容 Hash、来源类型和非敏感来源事实；
- Material Usage Snapshot 的实际使用内容、确认时间、联网授权状态和内容状态；
- 来源发布时间、捕获时间和核验时间等正式字段。

外部来源规则：

- `https://`、`http://` 等公开来源 URL 可以作为加密后的引用元数据保存。
- `content://`、`file://`、绝对路径和平台 Grant 不直接写入可移植备份。
- 对不可移植 Locator，只保存脱敏显示名称、MIME、来源类型、内容 Hash 和 `unavailableAfterImport=true`。
- 不读取、不复制、不删除用户外部原始文件。
- URI 权限不得假定跨设备可恢复。

未来复制外部内容必须是独立、明确授权、单独显示容量和许可影响的功能，不属于 V1 默认备份。

### 2.5 Personal Context

包含：

- `PersonalContextEntryEntity` 的标题、正文、Hash、启用/归档等正式状态和时间；
- `PersonalContextUsageSnapshotEntity` 的本次实际使用事实和内容状态。

条件：

- 进入备份的全部正文始终处于加密 Payload 内；
- Header 和文件名不得泄露 Personal Context 的标题、敏感类别或数量明细；
- 普通删除与历史 Usage 的既有产品语义保持不变；
- 彻底清除后的正文不得通过 Usage Snapshot、缓存或索引恢复。

### 2.6 Archive、Resume 和 Relation

包含：

- 未 Purge Issue 的 Archive Event；
- 未 Purge Issue 的 Resume Event；
- Issue Relation 的正式关系类型和时间；
- 来源已清除时的 `sourcePurgedAt` 和“来源已清除”事实。

来源已 Purge 时必须排除：

- `sourceIssueId` 的旧值；
- `sourceArchiveEventId` 的旧值；
- 来源标题、Archive Summary、Message、Material、Artifact、Personal Context 和音频；
- 任何可用于重建来源 Issue 的缓存字段。

导入不得根据降级 Relation 自动创建来源 Issue。

### 2.7 AudioAsset 和正式音频

元数据包含条件：

- AudioAsset 属于白名单 Issue/Stage；
- `fileState == AVAILABLE`；
- `deletedAt == null`；
- `purgeRequestedAt == null`；
- 来源 Message/Artifact 正式存在且属于同一 Issue/Stage；
- `storagePath` 是 `AudioFileStore` 可解析的受控相对路径；
- 文件真实存在、大小与记录一致、格式验证通过；
- 创建备份前后文件大小和 Hash 未变化。

包含：

- 稳定 AudioAsset ID；
- 来源类型和稳定来源 ID；
- MIME、格式、大小、内容 SHA-256；
- Blob Logical ID；
- 正式音频字节。

排除：

- PENDING、FAILED、CANCELED、MISSING 或删除中的资产；
- `.part` 文件；
- Orphan 文件；
- 旧 `Message.audioFilePath` 链；
- `generationKey` 全值；
- 绝对路径；
- 根据目录或文件名猜测的 Issue 关系。

任一应包含的 AVAILABLE 文件缺失或变化时，整个备份失败：

```text
source_changed
```

不得仅跳过音频并报告完整成功。

### 2.8 官方 Skill 组合和历史快照

包含：

- Official Skill Combination；
- Combination Member；
- 成员顺序和用户设置的默认职责；
- 历史 Participant Snapshot 中执行所需的稳定 Skill ID、版本、职责和边界快照。

不包含：

- APK 内随版本发布的静态 `SKILL.md` 正文；
- `official_skill_execution_manifest_v2.json` 原文件；
- 来源台账正文；
- 当前 APK 可以重新提供的图标或静态资源。

历史执行需要的 Snapshot 不能只保存“当前 Skill ID”，否则未来 Catalog 更新会改写历史含义。生产 Mapper 必须保存已冻结的历史执行字段，但不得复制 API Key、用户问题以外的无关日志或完整 APK 资产。

### 2.9 非敏感用户设置

只允许通过稳定逻辑记录导出：

- 自动下一轮开关；
- 语义路由开关；
- 搜索模式；
- 官方 Skill 收藏；
- 官方 Skill 最近使用稳定 ID 和时间；
- 经安全审查明确允许的展示偏好。

禁止复制整个 SharedPreferences XML。以下即使存在于 SharedPreferences 也排除：

- API Key Session 绑定；
- Key 冷却、封禁和 last-used Key；
- 遥测正文；
- 云端 Interaction 游标；
- 设备 ID、安装 ID、Token；
- 应用锁或认证材料。

## 3. 永久排除列表

以下对象不得进入 Portable Backup、Device Snapshot Manifest 或诊断日志：

- Gemini/API Key 明文；
- `gemini_api_keys.enc`；
- API Key 指纹、完整 Key ID 和 Session Key 绑定；
- Android Keystore 密钥材料；
- Keystore Alias 清单；
- 备份密码、KEK、Root Key、派生子密钥；
- 应用锁 PIN、密码 Hash、认证 Token、密保答案；
- OAuth/访问令牌、Cookie、Authorization Header；
- 外部 URI Grant Token；
- 绝对文件路径；
- Cache、Code Cache；
- `.part`、临时明文和未发布候选文件；
- Orphan 文件；
- Pending Message；
- 未确认编辑器内存；
- 生产日志和异常堆栈中的用户正文；
- 已成功 Purge 的任何正文、标题、索引、缩略图或音频；
- 用户外部 URI 指向的原始文件；
- APK 自带且可由版本重新提供的静态 Skill 资产；
- 设备绑定恢复快照本身，除非用户在未来明确选择独立导出，而 V1 默认禁止。

## 4. Purge 和备份竞态

### 4.1 Issue 纳入判定

| Lifecycle/Purge 状态 | 可进入新备份 |
|---|---|
| ACTIVE，无 Purge | 是 |
| ARCHIVED，无 Purge | 是 |
| TRASHED，无 Purge | 是，仍属于用户可恢复数据 |
| REQUESTED～DATABASE_PURGING | 否，`purge_in_progress` |
| FAILED_RETRYABLE | 否，`purge_in_progress` |
| 已成功 Purge | 对象必须不存在，任何残留均为安全失败 |

TRASHED 不等于 Purged；只要没有 Purge Operation，回收站数据仍属于用户数据并可进入备份。导入后不得自动执行旧备份中的清理请求。

### 4.2 双检查

备份开始前生成 Source Token，至少包含：

- 选中 Issue 的稳定排序集合；
- Lifecycle 状态和更新时间；
- Purge Operation 状态；
- 各对象类型数量；
- 最大 `updatedAt` 或等价版本事实；
- AVAILABLE AudioAsset 列表、大小和 Hash；
- 活动 Run/Worker 数量。

读取并加密完成、发布目标文件前重新生成 Token。任何差异返回：

```text
source_changed
```

不允许逐 Issue 部分成功，也不允许把变化后的对象增量补到已有密文末尾。

## 5. Portable Backup 与 Device Snapshot 差异

| 范围 | Portable Backup | Device Snapshot |
|---|---|---|
| 数据库 | 逻辑记录 | WAL checkpoint 后的主数据库文件 |
| 音频 | 逻辑 Blob | 同样枚举的受控正式文件 |
| SharedPreferences | 显式逻辑白名单 | 不复制原始偏好；Snapshot Manifest 只记录恢复必要的已批准设置 |
| API Key | 永久排除 | 永久排除 |
| 备份密码 | 由用户输入，不保存 | 不适用 |
| 设备密钥 | 不依赖 | Android Keystore 专用 Snapshot Key |
| 跨设备 | 支持 | 不支持 |
| 差异预览 | 支持未来 PR09-14A | 不支持；只用于本设备回退 |
| Room 版本 | 由 Manifest Mapper 解耦 | 强兼容检查，只允许受支持 Schema |

## 6. Manifest 数据统计

加密 Manifest 可以包含：

- 创建时间；
- App Version、Room Source Version、Manifest Version；
- 选择的 Issue 数量；
- 各逻辑对象类型数量；
- Blob 数量和总字节；
- 是否存在外部引用不可恢复标记；
- Required Feature Bits；
- 每个 Logical Entry 的稳定 ID、类型、版本、长度和 Hash。

明文 Header 不得包含：

- Issue 标题；
- 用户正文；
- Skill 阵容；
- 文件名和音频标题；
- Material 来源 URL；
- Personal Context；
- 对象数量和总字节；
- API Key 状态；
- 用户身份或设备信息。

## 7. PR09-14A/14B 导入要求

本数据范围必须允许后续实现：

- 在隔离区完成格式、认证、资源上限和业务不变量校验；
- 以稳定 ID、内容 Hash、来源和版本生成差异；
- 相同数据自动去重；
- 冲突选择当前、备份或两者；
- 非空数据库在写入前展示分类汇总和逐项预览；
- 正式替换前创建并验证设备快照；
- 提交点前不修改当前库；
- 数据库、音频和后台任务一致切换；
- 失败保持当前库不变；
- 不恢复旧 Purge 意图和已清除来源。

## 8. 数据范围验收矩阵

PR09-13A 原型至少静态验证：

- 白名单包含 Issue、Stage、Run、Participant、Message、Draft、Artifact、Material、Personal Context、Lifecycle、Relation、Audio 和 Skill Combination。
- API Key、Keystore、应用锁、Token、绝对路径、Pending、Cache、Orphan 和 `.part` 永久排除。
- `content://` 和 `file://` 不作为可恢复 URI 写入。
- AVAILABLE 音频必须通过受控相对路径枚举。
- Purge 中和 `FAILED_RETRYABLE` Issue 被拒绝。
- 已降级 Relation 不携带来源标题或正文。
- 静态 Skill 资产不重复进入备份。
