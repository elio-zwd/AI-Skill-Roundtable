# PR09-10B：独立音频资产与受控生成实施计划

> **执行工作流：** 使用仓库内 Superpowers 6.2.0 的 `brainstorming`、`writing-plans`、`test-driven-development`、`systematic-debugging`、`verification-before-completion`、`requesting-code-review` 与 `finishing-a-development-branch` 等价流程。当前对话不使用 Worktree、子代理或并行代理。
>
> **目标：** 将见域正式音频从旧 `Message.audioFilePath` 路径链中隔离，建立以 `AudioAssetEntity` 为事实源、以用户显式确认为入口、可幂等生成、取消、重试、播放、缺失检测、孤儿识别和受控清理的独立业务链。
>
> **架构：** 阶段 A 只实现不依赖 PR09-11 共享文件的纯音频领域、Generation Key、受控文件存储、Gateway 契约、后台任务策略、协调状态机和播放管理器；阶段 B 必须等 PR09-11 合并后同步最新 `main`，再实现 Room Repository 适配、WorkManager Worker、BYOK Gateway 适配、Runtime/UI/自动化标签和 PR09-12 清理交接。
>
> **技术栈：** Kotlin 2.0.21、Android API 26+、WorkManager 2.9.0、Room、Java NIO、MediaPlayer、JUnit 4、Mockito Kotlin、GitHub Actions。

## 1. 精确基线与依赖状态

```text
仓库：elio-zwd/AI-Skill-Roundtable
Base：main@b46bb9eebfed123ca1e7f5f2f6923c7ab53e0644
当前 Room：v10
开发分支：feat/pr-09-10b-audio-assets
目标 PR：Draft
```

PR09-11 当前为 Draft PR #48：

```text
分支：feat/pr-09-11-advance-issue
Head：bc8be80e0401645378efc4c9cd48a6a23e35aa3e
状态：open / Draft / 未合并
```

PR #48 当前修改 `RoundtableDatabase.kt`、`RoomJianyuRepository.kt`、`JianyuRepositoryTransactions.kt` 和推进议题共享工作区相关文件。因此 PR09-10B 在其合并前不得修改这些文件，也不得建立竞争性的 Room v11。

## 2. 全局不变量

1. 用户最终确认前为零 `AudioAsset`、零文件、零 Work、零网络、零 Key 消耗。
2. 正式音频只关联一个完成 Message 或一个 `ConfirmedArtifactEntity`，两者必须二选一。
3. Pending Message、Draft、编辑器内存正文、跨 Issue/Stage 来源不得生成正式音频。
4. 正式链不写 `Message.audioFilePath/audioFormat/audioSizeBytes`，不调用 `ChatDao.updateMessageAudio()`。
5. Worker 不直接打开 `RoundtableDatabase`，不直接访问 DAO，不接收正文、API Key 或外部绝对路径。
6. `generationKey` 不包含正文，只包含稳定来源、内容 Hash、声音配置、目标格式和参数版本的规范化摘要。
7. 文件只写入 App 私有 `filesDir/jianyu-audio/`，数据库只保存相对路径。
8. 正式文件使用同目录 `.part` 临时文件，关闭、flush、`FileDescriptor.sync()`、格式校验后才原子重命名。
9. 空间不足时不得启动网络；失败不得修改 Message、Artifact、Draft、Run、Stage、预算或协作状态。
10. 取消、删除请求或清理请求后的迟到成功不得覆盖终态。
11. 缺失文件进入 `MISSING`；孤儿文件只扫描、统计和展示，不在启动时静默删除。
12. 不新增第五个顶层导航，不新增“音频”一级 Tab。
13. 不读取根目录 `.env`，不内置或回退到隐藏 Key；生产网络只消费现有 BYOK 体系。
14. 日志、错误、Work 名称和自动化标签不得包含正文、标题、绝对路径、API Key、请求 Payload 或自由文本音色名。
15. 未经用户授权，PR 保持 Draft，不标记 Ready、不合并、不删除分支。

## 3. 旧音频链审计

### 3.1 现有生产事实

旧链为：

```text
RoundtableViewModel
→ LiveApiClient.generateTtsWav(context, apiKey, message.text, voiceName, cache/tts_<messageId>.wav)
→ 非唯一 WorkManager 请求
→ AudioTranscodeWorker(message_id, wav_path)
→ RoundtableDatabase.chatDao().updateMessageAudio()
→ 删除 WAV
→ AudioPlaybackManager(messageId, absolutePath)
```

问题：

- 正文、Key 和输出绝对路径直接穿过旧调用链；
- Work 输入包含任意绝对路径；
- Worker 绕过 Repository 并直接访问 ChatDao；
- Work 不按稳定生成键唯一化；
- 播放身份是 Message ID，不是 AudioAsset ID；
- 缓存未命中可能直接联网；
- 文件生命周期与 Message 路径字段绑定；
- 旧架构文档描述的是聊天音频缓存，不是正式成果资产。

### 3.2 兼容策略

旧 `AudioTranscodeWorker`、`AudioPlaybackManager`、`LiveApiClient` 与 Message 音频字段当前仍有真实旧聊天调用方，因此本 PR 不凭推断删除。阶段 A 新增静态架构测试，确保正式 `audio/assets/` 代码不引用：

```text
ChatDao
ChatRepository.updateMessageAudio
Message.audioFilePath
AudioTranscodeWorker
RoundtableDatabase
```

阶段 B 接入后，旧链继续标记为 Legacy，只服务旧聊天页面；正式见域 Issue/Stage/Artifact/Message 不进入旧链。

## 4. 正式 AudioAsset Schema 与 v10/v11 边界

现有 `audio_assets` 已具备：

```text
id / issueId / stageId
sourceMessageId / sourceArtifactId
storagePath / mimeType / format / sizeBytes
fileState / generationKey
createdAt / updatedAt / deletedAt / purgeRequestedAt
```

现有外键可约束来源与 Asset 同属 Issue/Stage，唯一索引可约束 `generationKey` 和 `storagePath`。阶段 A 不修改 Entity、DAO、Database、Migration 或 Schema JSON。

`AudioFileState` 增加：

```text
CANCELED("canceled")
```

原因：取消必须持久化为可恢复终态，不能与 `FAILED` 混淆。枚举以字符串存储，新增可识别值不改变 Room 表结构，不产生 Schema JSON 差异；必须新增转换器测试，确认旧四种值仍可读取、未知值仍拒绝。

若阶段 B 发现最终 PR09-11 接口无法表达必要的比较并交换更新、Issue 级查询或删除影响，不自行升级 Room；先记录缺口，再只通过 PR09-11 最终 v11 既有 DAO/Repository 扩展表达。

## 5. 方案比较与选择

### 5.1 方案 A：WAV 后台转 AAC

优点：可复用旧 Live PCM/WAV 输出和 MediaCodec 思路。缺点：存在两阶段文件、源文件删除顺序、转码取消、半文件和额外恢复状态；旧 Worker 的持久化边界不可复用。

### 5.2 方案 B：Gateway 直接输出最终可播放格式

优点：每个 Generation Key 对应一个正式 Asset 和一个最终文件，原子提交、缺失检测、重试和清理最简单。缺点：生产 Gateway 必须确认服务输出格式；旧 Live API 若只返回 PCM/WAV，需要在 Gateway 内部完成受控封装或转换。

### 5.3 方案 C：原始和派生音频各建一个 Asset

优点：保留最高质量原始数据。缺点：来源关系、空间、删除、恢复和 PR09-12 清理均翻倍，V1 没有用户价值支撑。

### 5.4 决策

V1 选择 **方案 B**。正式 Gateway 对协调器只暴露一个最终可播放输出；允许 Gateway 内部使用临时 PCM/WAV，但中间文件不是 AudioAsset，必须位于受控目录并在成功/失败/取消后清理。正式资产默认目标格式由生产适配验证后冻结；阶段 A 测试覆盖 WAV 与 ADTS AAC 校验，不宣称未验证的线上模型必然直接返回 AAC。

## 6. 阶段 A 文件结构

### 6.1 新增生产文件

```text
app/src/main/java/com/elio/jianyu/audio/assets/
├── AudioAssetDomain.kt
├── AudioGenerationKey.kt
├── AudioFileStore.kt
├── AudioGenerationGateway.kt
├── AudioGenerationCoordinator.kt
├── AudioGenerationWorkPolicy.kt
└── AudioAssetPlaybackManager.kt
```

职责：

- `AudioAssetDomain.kt`：正式来源、配置、稳定错误码、状态转换请求和清理/孤儿模型。
- `AudioGenerationKey.kt`：规范化输入与 SHA-256 稳定 Key，不保留正文。
- `AudioFileStore.kt`：相对路径、目录穿越防护、空间预检、`.part` 协议、格式校验、原子提交、缺失和孤儿扫描。
- `AudioGenerationGateway.kt`：网络生成 Port 与测试 Fake；Gateway 只向受控临时目标写最终格式。
- `AudioGenerationCoordinator.kt`：显式请求、幂等复用、取消、重试、生成结果提交和迟到结果防护。
- `AudioGenerationWorkPolicy.kt`：Unique Work 名称、初次/重试策略和 Worker 输入白名单的纯逻辑。
- `AudioAssetPlaybackManager.kt`：以 `audioAssetId` 为身份的单实例播放状态机；无缓存未命中联网行为。

### 6.2 修改文件

```text
app/src/main/java/com/elio/jianyu/data/ResourceLifecycle.kt
```

仅增加 `AudioFileState.CANCELED`；不改 Entity、Room 版本或转换器结构。

### 6.3 新增 JVM 测试

```text
app/src/test/java/com/elio/jianyu/audio/assets/AudioGenerationKeyTest.kt
app/src/test/java/com/elio/jianyu/audio/assets/AudioFileStoreTest.kt
app/src/test/java/com/elio/jianyu/audio/assets/AudioGenerationCoordinatorTest.kt
app/src/test/java/com/elio/jianyu/audio/assets/AudioGenerationWorkPolicyTest.kt
app/src/test/java/com/elio/jianyu/audio/assets/AudioAssetPlaybackManagerTest.kt
app/src/test/java/com/elio/jianyu/audio/assets/AudioAssetArchitectureTest.kt
app/src/test/java/com/elio/jianyu/data/AudioFileStateConverterTest.kt
```

阶段 A 不修改 UI、Runtime、Room Repository 或中央自动化标签。

## 7. 核心接口

### 7.1 来源与配置

```kotlin
sealed interface AudioAssetSource {
    val issueId: String
    val stageId: String
    val contentHash: String

    data class CompletedMessage(
        override val issueId: String,
        override val stageId: String,
        override val contentHash: String,
        val messageId: Long,
    ) : AudioAssetSource

    data class ConfirmedArtifact(
        override val issueId: String,
        override val stageId: String,
        override val contentHash: String,
        val artifactId: String,
    ) : AudioAssetSource
}

data class AudioGenerationConfig(
    val voiceProfileId: String,
    val targetFormat: AudioTargetFormat,
    val parameterVersion: Int,
)
```

`voiceProfileId` 只能来自产品稳定配置，不接受用户自由文本进入 Key、文件名、日志或标签。

### 7.2 Repository Port

阶段 A 在 `audio/assets` 定义独立 Port，不修改共享 `JianyuRepository`：

```kotlin
interface AudioAssetRepositoryPort {
    suspend fun loadSource(command: LoadAudioSourceCommand): AudioSourceLoadResult
    suspend fun findByGenerationKey(generationKey: String): AudioAssetRecord?
    suspend fun createPending(command: CreatePendingAudioCommand): AudioAssetWriteResult
    suspend fun loadAsset(audioAssetId: String): AudioAssetRecord?
    suspend fun markAvailable(command: MarkAudioAvailableCommand): AudioAssetWriteResult
    suspend fun markFailed(command: MarkAudioFailedCommand): AudioAssetWriteResult
    suspend fun markMissing(command: MarkAudioMissingCommand): AudioAssetWriteResult
    suspend fun markCanceled(command: MarkAudioCanceledCommand): AudioAssetWriteResult
    suspend fun requestDelete(command: RequestAudioDeleteCommand): AudioAssetWriteResult
    suspend fun listForIssue(issueId: String): List<AudioAssetRecord>
}
```

阶段 B 的 Room 组件实现此 Port，并通过比较并交换语义拒绝迟到结果。Worker/Coordinator 只依赖 Port，不依赖 DAO。

### 7.3 Gateway

```kotlin
fun interface AudioGenerationGateway {
    suspend fun generate(request: AudioGenerationRequest): AudioGenerationResult
}
```

请求只包含经过来源校验后在进程内读取的正文、稳定声音配置、目标格式和由 `AudioFileStore` 创建的受控临时目标。API Key 由生产 Gateway 内部通过现有 BYOK 体系租用，不进入 Request、Work Data、数据库或日志。

### 7.4 FileStore

```kotlin
interface AudioFileStore {
    fun preflight(requiredBytes: Long): AudioStoragePreflight
    fun createPendingTarget(audioAssetId: String, format: AudioTargetFormat): PendingAudioTarget
    fun validatePending(target: PendingAudioTarget): AudioFileValidation
    fun commit(target: PendingAudioTarget): AudioCommittedFile
    fun resolve(relativePath: String): AudioFileResolution
    fun removeTemporary(target: PendingAudioTarget)
    fun scanOrphans(referencedRelativePaths: Set<String>): AudioOrphanReport
}
```

## 8. Generation Key 与幂等

规范化材料：

```text
sourceType
sourceStableId
issueId
stageId
sourceContentHash
voiceProfileId
targetFormat
parameterVersion
```

Key：

```text
audio:v1:<sha256-lowercase-hex>
```

禁止包含正文。相同 Key 行为：

- `AVAILABLE`：返回已有资产，不排队；
- `PENDING` 且任务有效：返回已有资产/Work；
- `FAILED/MISSING/CANCELED`：普通创建返回“需要显式重试”，只有重试命令可重新排队；
- 同 Key 但来源或配置规范化 Payload 不一致：返回幂等冲突；
- 双击确认：Repository 唯一约束和 Coordinator 串行门禁共同保证一个 Asset、一个 Unique Work。

## 9. 文件协议

根目录：

```text
filesDir/jianyu-audio/
```

文件名只由内部 Asset ID 的 SHA-256 摘要和固定扩展名组成：

```text
<asset-id-hash>.wav
<asset-id-hash>.aac
<asset-id-hash>.wav.part
<asset-id-hash>.aac.part
```

流程：

```text
空间预检
→ 创建同目录 .part
→ Gateway 写入
→ close + flush + FileDescriptor.sync
→ 校验文件头和非零有效载荷
→ Files.move(ATOMIC_MOVE)
→ 返回相对路径、MIME、格式和 sizeBytes
→ Repository CAS 标记 AVAILABLE
```

若平台不支持同目录原子移动，返回稳定 `atomic_move_unavailable`，不回退为复制后覆盖。若文件已提交但数据库 CAS 失败，Coordinator 删除本次新正式文件或将其报告为孤儿，绝不把数据库标为 AVAILABLE。

路径规则：

- 拒绝绝对路径；
- 拒绝 `..`、符号链接逃逸和根目录外规范化路径；
- 来源 ID、标题、正文和声音自由文本不得参与文件名；
- `resolve()` 只解析数据库相对路径；
- 孤儿扫描不删除文件。

## 10. 后台任务策略

Unique Work：

```text
audio-generation:<sha256(generationKey)>
```

初次生成使用：

```text
ExistingWorkPolicy.KEEP
```

显式重试在 Repository 已将终态重新置为 PENDING 后使用：

```text
ExistingWorkPolicy.REPLACE
```

Worker Data 唯一允许字段：

```text
audio_asset_id
```

不得出现正文、Key、来源 ID、绝对路径、格式自由文本或 API Key。阶段 A 先冻结 `AudioGenerationWorkPolicy` 纯逻辑；实际 `CoroutineWorker`、WorkerFactory/依赖注入和 WorkManager 调度在阶段 B 随 Runtime 一次接线，避免建立未安装的全局 Service Locator。

Worker 执行顺序：

```text
读取 Asset
→ 确认仍为 PENDING 且未 deleted/purge requested
→ 读取并重新校验来源
→ 空间预检
→ 调用 Gateway
→ 校验并原子提交文件
→ 再次读取 Asset
→ CAS markAvailable
```

任何一步发现 `CANCELED`、删除请求、清理请求或来源失效，清理临时文件并停止；迟到成功不得覆盖终态。

## 11. 取消、重试、缺失、孤儿与空间不足

### 11.1 取消

```text
用户确认取消
→ WorkManager.cancelUniqueWork
→ Repository CAS 标记 CANCELED
→ 删除 .part
→ 保留 AudioAsset 元数据
```

取消不删除来源。阶段 A Coordinator 覆盖迟到 Gateway 成功、迟到文件提交和重复取消。

### 11.2 重试

显式重试必须重新读取来源并重新计算 Key。来源内容 Hash 或配置变化时生成新 Asset；同 Key 的 `FAILED/MISSING/CANCELED` 可重置为 PENDING 并 REPLACE 唯一 Work。`AVAILABLE` 不允许覆盖。

### 11.3 缺失

播放或对账发现数据库 `AVAILABLE` 但文件不存在时，CAS 标记 `MISSING`，UI 后续提供“重新生成”和受生命周期控制的“移除记录”。不得自动联网。

### 11.4 孤儿

`scanOrphans()` 返回：

```text
relativePath
sizeBytes
lastModifiedAt
reasonCode
```

不返回绝对路径，不自动删除。PR09-12 消费扫描结果并在用户确认后清理。

### 11.5 空间不足

`preflight()` 在网络前比较 `usableSpace` 与：

```text
max(estimatedOutputBytes, minimumReservationBytes) + safetyMarginBytes
```

不足返回 `insufficient_storage`，不创建正式文件、不调用 Gateway、不修改来源对象。

## 12. 播放

新 `AudioAssetPlaybackManager` 以 `audioAssetId` 为唯一身份，状态：

```text
Idle
Preparing(audioAssetId)
Playing(audioAssetId)
Paused(audioAssetId)
Failed(audioAssetId, errorCode)
```

支持 play/pause/resume/stop/切换资产/release。播放只消费 `AudioFileStore.resolve()` 成功的本地文件；缺失返回 `audio_file_missing`，不发起网络、不改变来源对象。Android `MediaPlayer` 通过小型 Player Port 包装，JVM 测试验证状态机；真实编解码/播放留给设备验收。

## 13. UI 与自动化标签（阶段 B）

阶段 B 在 PR09-11 合并后接入：

- 完成 Message：生成、生成中、播放、暂停、重试、缺失；Pending Message 不显示入口。
- Artifact 详情：生成、已有音频列表、格式、大小、状态、播放、取消、重试。
- 全局成果列表不展开完整控制；详情页承载操作。
- 继续使用“资料与成果 → 成果”，不增加顶层导航或一级音频 Tab。

中央标签在阶段 B 加入 `JianyuAutomationTags.Audio`，只接受稳定内部 ID。静态标签采用施工单冻结列表；动态标签不得包含正文、标题、路径、Key 或音色自由文本。

## 14. 阶段 A TDD 任务

### Task A1：计划与架构门禁

**文件：**

- 创建：`docs/planning/pr-09-10b-audio-assets-plan.md`
- 创建测试：`app/src/test/java/com/elio/jianyu/audio/assets/AudioAssetArchitectureTest.kt`

- [ ] 测试正式目录不引用 `ChatDao`、`RoundtableDatabase`、`AudioTranscodeWorker`、`Message.audioFilePath`。
- [ ] 测试 PR09-11 独占文件在阶段 A Diff 中未被修改。
- [ ] 测试正式 Work Data 白名单只有 `audio_asset_id`。
- [ ] 远端执行测试，先观察缺少正式目录/契约的预期 RED。
- [ ] 最小实现后重新执行并观察 GREEN。

### Task A2：来源、配置、Key 与取消状态

**文件：**

- 创建：`AudioAssetDomain.kt`
- 创建：`AudioGenerationKey.kt`
- 修改：`data/ResourceLifecycle.kt`
- 测试：`AudioGenerationKeyTest.kt`
- 测试：`AudioFileStateConverterTest.kt`

- [ ] 先测试 Message/Artifact 二选一、稳定 Key、正文不泄露、配置差异产生不同 Key。
- [ ] 先测试 `CANCELED` 往返转换与旧值兼容。
- [ ] 远端确认测试因类型/枚举缺失而 RED。
- [ ] 实现最小领域类型、规范化和 SHA-256。
- [ ] 远端确认目标测试 GREEN。

### Task A3：受控 FileStore

**文件：**

- 创建：`AudioFileStore.kt`
- 测试：`AudioFileStoreTest.kt`

- [ ] 先测试相对路径、路径穿越、绝对路径、零字节、损坏 WAV/AAC、空间不足、原子移动失败、缺失和孤儿扫描。
- [ ] 使用临时目录和可注入 `usableSpace`/移动器，不依赖 Android Context。
- [ ] 实现 `.part`、sync、头校验、同目录原子移动和只读孤儿报告。
- [ ] 不提供静默删除孤儿 API。

### Task A4：Gateway、Repository Port 与协调器

**文件：**

- 创建：`AudioGenerationGateway.kt`
- 创建：`AudioGenerationCoordinator.kt`
- 测试：`AudioGenerationCoordinatorTest.kt`

- [ ] 先测试显式确认前零写入/零 Gateway。
- [ ] 先测试 AVAILABLE/PENDING 复用、失败终态要求显式重试、双击幂等。
- [ ] 先测试 Pending Message、跨 Issue/Stage、缺失来源拒绝。
- [ ] 先测试空间不足不调用 Gateway。
- [ ] 先测试取消、删除请求和迟到成功不覆盖终态。
- [ ] 先测试 DB 标记失败后不留下正式文件。
- [ ] 使用内存 Fake Repository/Gateway/FileStore 验证真实状态转换，不验证 Mock 调用细节替代行为。

### Task A5：Work 策略

**文件：**

- 创建：`AudioGenerationWorkPolicy.kt`
- 测试：`AudioGenerationWorkPolicyTest.kt`

- [ ] 测试相同 Key 生成相同 Work 名。
- [ ] 测试 Work 名不包含来源 ID、正文、声音自由文本或完整 Key。
- [ ] 测试初次 KEEP、显式重试 REPLACE。
- [ ] 测试 Worker Data 仅含 `audio_asset_id`。

### Task A6：AudioAsset 播放状态机

**文件：**

- 创建：`AudioAssetPlaybackManager.kt`
- 测试：`AudioAssetPlaybackManagerTest.kt`

- [ ] 测试播放、暂停、恢复、停止、切换资产、释放。
- [ ] 测试缺失文件和播放器异常。
- [ ] 测试不存在自动联网依赖。
- [ ] 实现 Player Port 和单活动资产状态机。

## 15. 阶段 B 任务门禁

只有 PR09-11 合并后执行：

1. 获取最新 `origin/main`，确认 PR #48 合并 Commit 属于 `main` 祖先。
2. 将当前分支合并或变基到最新 `main`，禁止复制 PR09-11 旧分支文件覆盖主线。
3. 读取 `docs/planning/pr-09-11-interface-handoff.md`。
4. 记录最终 Room 版本和阶段推进接口。
5. 重新检查开放 PR 与共享文件。

阶段 B 预计文件按最终主线重新核实，当前不得提前写入：

```text
app/src/main/java/com/elio/jianyu/data/AudioAssetRepositoryContract.kt
app/src/main/java/com/elio/jianyu/data/AudioAssetRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/audio/assets/AudioAssetGenerationWorker.kt
app/src/main/java/com/elio/jianyu/audio/assets/WorkManagerAudioGenerationScheduler.kt
app/src/main/java/com/elio/jianyu/audio/assets/ByokAudioGenerationGateway.kt
app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionRoute.kt
app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionScreen.kt
app/src/main/java/com/elio/jianyu/ui/screens/result/（最终 Artifact 详情文件）
app/src/main/java/com/elio/jianyu/ui/automation/JianyuAutomationTags.kt
```

阶段 B 必须补：

- Room Repository 的来源校验、Issue/Stage 查询、Generation Key 幂等和 CAS 终态；
- WorkManager Worker 与唯一调度；
- 现有 BYOK Key Pool 的生产 Gateway 包装；
- Message/Artifact UI 最终确认；
- 进程恢复只恢复任务视图，不自动生成或重试；
- 最终 Room 连续 Migration、Schema freshness、Instrumentation 与 Compose 测试；
- PR09-12 清理接口交接。

## 16. PR09-12 接口交接

完成阶段 B 后创建：

```text
docs/planning/pr-09-10b-interface-handoff.md
```

冻结：

- AudioAsset 状态与终态优先级；
- Message/Artifact 来源规则；
- Generation Key 与 Work 唯一标识；
- 文件根、相对路径和 `.part` 命名；
- Issue 级资产查询；
- 取消 Issue 全部音频 Work；
- Missing/Orphan 扫描；
- 删除请求和 Purge Impact；
- 物理清理顺序：取消任务→冻结状态→删除临时文件→删除正式文件→提交清理结果；
- 清理失败恢复和重复清理幂等；
- PR09-12 不删除来源 Artifact/Message，不重写音频生成状态机。

## 17. 验证命令与证据分类

远端 CI/本地验收至少执行：

```powershell
pwsh.exe -NoProfile -File tools/secret-scan.ps1
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat assembleDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest
```

阶段 B 另执行：

```text
Room Schema freshness
最终 Room v1→最新版本连续 Migration
Fake Audio Gateway 设备测试
外部 UIAutomator 稳定标签测试
360dp / 200% 字号 / 明暗主题 / TalkBack 语义检查
真实设备本地编码与播放点检
```

自动化门禁不得调用生产 TTS、不得使用真实 API Key。当前 GitHub 连接器没有本地 Android 工作区，因此只有实际读取到 GitHub Actions 输出后才能写“CI 已通过”；设备测试与本地编解码只能标记为未验证，交由严格只读本地验收执行。

## 18. 提交边界

阶段 A：

```text
docs: 制定PR09-10B音频资产实施计划
test: 增加音频资产失败场景
feat: 建立音频生成与文件存储模型
feat: 增加音频资产播放管理器
test: 验证音频文件与后台任务策略
```

阶段 B：

```text
chore: 同步PR09-11合并后的主线
feat: 接入音频资产Repository与运行时
feat: 接入消息与成果音频入口
test: 冻结音频资产自动化标签
test: 验证最终Room版本音频兼容
docs: 冻结PR09-12音频清理接口
```

每个 Commit 保持原子性。禁止把 Room/Runtime/UI 接线提前混入阶段 A。

## 19. 风险与回滚

### 风险

1. 现有 `audio_assets` 没有独立错误码和参数 Payload 字段；V1 错误码留在 Work/Service 状态，不能把同 Key 不同 Payload 的完整规范化材料持久化。阶段 B 必须通过来源和配置重新计算验证，不在日志保存正文。
2. `generationKey` 唯一索引允许每个配置一个 Asset，但显式重试需复用原记录而非插入同 Key 新记录。
3. 生产 Live API 当前返回 PCM/WAV，最终 AAC 是否可直接生成尚未验证；正式 Gateway 不能假定服务能力。
4. Android 文件系统对 `ATOMIC_MOVE` 的支持需真实设备验证；不支持时按稳定错误处理，不静默降级为非原子复制。
5. MediaPlayer 的 ADTS AAC 兼容性和厂商差异需设备测试。
6. PR09-11 最终接口可能改变共享 Repository 组件构造，阶段 B 必须以交接文档为准。

### 回滚

- 阶段 A 可整体回滚新增 `audio/assets` 与测试；只增加 `CANCELED` 枚举值，不修改数据库结构。
- 阶段 B 回滚先关闭 UI/Runtime/Worker 调度入口，保留可识别的 AudioAsset 元数据和文件，不降级 Room。
- 不删除旧 Message 音频字段或旧聊天链，避免回滚破坏旧调用方。
- 已写正式文件但未成功提交数据库时，按孤儿报告交给受控清理，不在启动时删除。

## 20. 完成判定

PR09-10B 只有同时满足以下条件才可申请 Ready：

- 阶段 A 与阶段 B 均完成；
- PR09-11 已合并且当前分支已同步最新 `main`；
- 使用最终 Room 版本且没有竞争 Migration；
- 正式链不写旧 Message 音频字段、不直接访问 ChatDao/DAO；
- 用户确认、来源二选一、Generation Key 幂等、唯一 Work、文件原子提交、取消/重试/迟到结果、Missing/Orphan/空间不足均有测试；
- 音频失败不影响 Message、Artifact、Draft、Run、Stage、预算或协作状态；
- UI 不自动生成、不新增顶层导航；
- PR09-12 交接完成；
- CI 和本地严格只读验收证据按实际状态记录；
- 用户明确授权标记 Ready 或合并。
