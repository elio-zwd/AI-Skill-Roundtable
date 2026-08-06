# PR09-10B 独立音频资产接口交接

## 1. 交接目标

本文件冻结 PR09-10B 合并后供 PR09-12 清理与生命周期功能消费的正式音频边界。正式链以 `audio_assets` 与 `AudioAssetEntity` 为事实源，不再依赖旧 `Message.audioFilePath`、`ChatDao.updateMessageAudio()` 或 `AudioTranscodeWorker`。

## 2. 运行时入口

App 组合层通过：

```kotlin
JianyuAppRuntime.audioRuntime
```

获得：

```kotlin
JianyuAudioRuntime(
    generationCoordinator,
    lifecycleService,
    playbackManager,
)
```

工作区入口为：

```kotlin
AudioEnabledIssueExecutionRoute
```

恢复、导航、重组与打开音频弹窗只读取 `StageResultService` 和 Room 中已有资产，不自动创建资产、不自动排队、不自动访问网络。

## 3. 正式来源契约

只允许：

- `AudioSourceReference.Message`：同一 issue / stage、`isPending == false` 的完成消息；
- `AudioSourceReference.Artifact`：同一 issue / stage 的 `ConfirmedArtifactEntity`。

草稿、Pending Message、跨 Issue、跨 Stage 和找不到的来源均被拒绝。Generation Key 由来源类型、稳定 ID、内容 Hash、声音配置、目标格式和参数版本组成，不包含正文或 API Key。

## 4. 文件契约

- 根目录：App 私有 `filesDir/jianyu-audio/`；
- 数据库仅保存相对路径；
- 生成先写唯一 `.part`，flush / sync、格式校验后同目录原子移动；
- 正式文件已存在时不覆盖；
- DB AVAILABLE CAS 失败时回滚刚提交的文件；
- Missing 只由显式对账标记；
- Orphan 只扫描和报告，PR09-10B 不自动删除。

正式 V1 生产 Gateway 输出 WAV：24 kHz、单声道、16-bit PCM。领域层保留 ADTS AAC 校验能力，但当前 BYOK Gateway 会在使用 Key 或联网前拒绝 AAC 生成请求。

## 5. 后台任务契约

- Worker：`AudioAssetGenerationWorker`；
- Scheduler：`WorkManagerAudioGenerationScheduler`；
- 唯一输入：`audio_asset_id`；
- 约束：网络已连接；
- 初次请求：`ExistingWorkPolicy.KEEP`；
- 用户显式重试：`ExistingWorkPolicy.REPLACE`；
- Worker 不携带来源正文、message id、文件路径或 API Key；
- 失败返回终态 Failure，不使用 WorkManager 自动 retry；
- 只有用户显式确认后，Coordinator 才创建 PENDING 资产并安排任务。

## 6. BYOK 契约

`ByokAudioGenerationGateway` 通过既有 `ApiKeyPool` 获取尝试顺序。Key 只在网络边界短暂持有：

- 无可用 Key 时不建立网络请求；
- 鉴权失败标记无效并尝试下一 Key；
- 限流 Key 进入既有冷却；
- 成功后记录最近使用 Key；
- 不写入数据库、Worker Data、文件名、日志或 UI 状态。

## 7. 状态与竞态契约

`RoomAudioAssetRepository` 在 Room v11 现有表上使用条件 UPDATE，不新增 Migration：

- `PENDING -> AVAILABLE / FAILED / CANCELED` 必须 CAS；
- `AVAILABLE -> MISSING` 必须 CAS；
- FAILED、MISSING、CANCELED 仅能由显式重试恢复为 PENDING；
- `purgeRequestedAt != null` 或 `deletedAt != null` 后，迟到成功不能写入 AVAILABLE；
- 取消先持久化 CANCELED，再取消 Work 和清理 `.part`；
- 删除请求先持久化 `purgeRequestedAt`，再取消 Pending Work；
- 相同 Generation Key 只复用同一资产。

## 8. PR09-12 可消费的清理接口

PR09-12 应从以下公开接口开始，不得绕过它们直接猜测文件：

```kotlin
AudioAssetLifecycleService.listAudioAssetsForIssue(issueId)
AudioAssetLifecycleService.listAudioAssetsForStage(issueId, stageId)
AudioAssetLifecycleService.reconcileFilesForIssue(issueId)
AudioAssetLifecycleService.inspectPurgeImpact(issueId)
AudioAssetLifecycleService.requestDelete(command)
```

建议清理顺序：

1. 调用 `inspectPurgeImpact` 展示资产数、Pending 数、已引用文件、字节数、Missing 与 Orphan 报告；
2. 获得用户明确确认；
3. 对每个目标资产调用 `requestDelete`，先形成持久化 `purgeRequestedAt` 防护；
4. 重新读取资产，确认没有运行中的正式任务或迟到回调可恢复 AVAILABLE；
5. PR09-12 新增专用前向 Repository 能力，在同一受控流程中删除正式文件并写入 `deletedAt`；
6. 物理清理失败时保留数据库事实和可重试信息，不伪装成功；
7. Orphan 必须单独列出并再次确认，不得与“删除议题”隐式绑定。

PR09-10B 故意不暴露“扫描后全部删除”或“按目录递归删除”API。PR09-12 不得调用 `deleteRecursively()`、不得清空整个音频根目录、不得仅凭文件名推断归属。

## 9. 旧链隔离

以下旧能力仍可能为历史 Roundtable 页面存在，但不得接入正式见域音频资产：

```text
Message.audioFilePath
Message.audioFormat
Message.audioSizeBytes
ChatDao.updateMessageAudio()
AudioTranscodeWorker
LiveApiClient.generateTtsWav()
```

PR09-12 清理正式资产时不得把旧 Message 路径字段当作 `audio_assets` 的替代事实源。

## 10. 重点回归

- Room v1→v11 连续迁移与外键检查；
- 双击生成只产生一个 Generation Key 资产与一个唯一任务；
- 取消、删除请求与迟到 Gateway 成功的顺序；
- DB 写失败后的正式文件回滚；
- 进程强停后只恢复数据库状态，不自动联网或排队；
- WAV 在 API 26、API 28 及目标设备上的播放、暂停、恢复、停止和切换；
- 360dp、200% 字号、键盘、TalkBack、明暗主题；
- 日志中不存在 API Key、正文、绝对路径和完整 Generation Key。
