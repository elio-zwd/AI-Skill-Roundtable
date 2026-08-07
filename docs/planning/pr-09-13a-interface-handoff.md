# PR09-13A → PR09-13B 接口交接

> 本文件是 PR09-13B 加密导出与设备快照的施工合同。
>
> PR09-13B 必须等待 PR09-13A 完成独立安全审查、本地严格只读验收、用户授权并实际合并后启动。
>
> 本文件中的决定不得在 PR09-13B 重新选择；发现无法实现时必须停止并回到独立设计修订 PR。

## 1. 上游基线

```text
PR09-13A Base：main@40a106e66854c48efb008f434af1ed850128afdc
PR09-13A Branch：security/pr-09-13a-backup-design
Room：v12
Portable Format ID：jianyu-portable-backup/1
Snapshot Format ID：jianyu-device-snapshot/1
Envelope Version：1
Manifest Version：1
```

PR09-13B 的实际 Base 必须是 PR09-13A 合并后的最新 `main`，不得从本分支继续开发。

## 2. 产品边界

PR09-13B 实现：

- 可移植加密导出；
- 设备绑定恢复快照创建、验证、列表、备注和用户主动删除；
- 回退前快照创建接口，但不实现数据库恢复替换；
- 输出空间预检、取消、失败清理、崩溃恢复和并发门禁；
- 最小设置/UI/SAF 接线；
- 创建后完整验证。

PR09-13B 不实现：

- Portable Backup 导入；
- 差异预览；
- 数据去重和冲突选择；
- 当前数据库替换；
- Snapshot 恢复执行；
- Room Entity、Migration 或 Schema 变更；
- Android Auto Backup 规则修改；
- 账号、云同步或后台自动备份；
- 外部 URI 原始文件复制；
- 用户自定义加密算法或 KDF 参数。

## 3. 格式标识

### 3.1 Portable

```text
Format ID：jianyu-portable-backup/1
Magic Hex：4A 59 42 4B 50 0D 0A 1A
扩展名：.jybak
MIME：application/vnd.jianyu.backup
```

### 3.2 Snapshot

```text
Format ID：jianyu-device-snapshot/1
Magic Hex：4A 59 53 4E 50 0D 0A 1A
扩展名：.jysnap
MIME：application/vnd.jianyu.snapshot
```

最终扩展名只能在完整验证和发布后出现。临时文件必须使用 `.partial-<opaque-id>` 或 `.part`，且不能被 UI 识别为有效备份。

## 4. 密码和 KDF

### 4.1 密码编码

- Unicode NFC；
- UTF-8；
- 不 trim；
- 不大小写转换；
- 不使用 NFKC；
- 拒绝空密码、NUL、未配对 surrogate；
- NFC 后长度 1～1024 bytes。

### 4.2 KDF

```text
KDF ID：1
Profile ID：1
算法：Argon2id
Version：0x13
Memory：65,536 KiB
Iterations：3
Parallelism：1
Salt：16 bytes
Output：32 bytes
```

V1 文件不保存任意 `m/t/p`，只保存 Profile ID。PR09-13B Writer 只能写 Profile 1。资源不足返回 `kdf_resource_unavailable`，禁止降低参数、禁止回退 scrypt/PBKDF2。

## 5. AEAD 和密钥层次

### 5.1 Portable

```text
Password
→ Argon2id Profile 1
→ 32-byte KEK

SecureRandom
→ 32-byte Portable Root Key

KEK + 12-byte Wrap Nonce + Wrap AAD
→ AES-256-GCM
→ 32-byte Wrapped Root Key Ciphertext + 16-byte Tag

HKDF-SHA256(
  ikm = Portable Root Key,
  salt = envelope_id,
  info = "jianyu/portable-backup/v1/stream",
  length = 32
)
→ Tink Streaming IKM
```

### 5.2 Snapshot

```text
SecureRandom
→ 32-byte Snapshot Root Key

Android Keystore Alias：jianyu_backup_snapshot_wrap_v1
算法：AES-256-GCM
Key Slot：1
```

Snapshot Key 不可导出，不要求用户认证，不复用 API Key Alias `skill_roundtable_api_key_v1`。Alias 删除或失效时返回 `snapshot_key_unavailable`，不得创建新 Key 尝试解密旧快照。

### 5.3 Streaming AEAD

```text
Algorithm ID：1
Tink Template：AES256_GCM_HKDF_1MB
HKDF：HMAC-SHA256
Derived AES Key：32 bytes
Ciphertext Segment：1,048,576 bytes
Output Prefix：RAW
First Segment Offset：0
Key Version：0
```

解密验证必须一直读到认证 EOF；读取 Manifest 后提前停止不能视为通过。

## 6. 生产依赖

PR09-13B 使用：

```kotlin
implementation("org.bouncycastle:bcprov-jdk18on:1.84")
implementation("com.google.crypto.tink:tink-android:1.23.0")
```

约束：

- Bouncy Castle 只调用轻量级 Argon2 API，不注册全局 Provider；
- Tink 使用官方 Streaming AEAD，不复制或修改库内密码学实现；
- 依赖升级必须独立审查并重跑全部公开向量；
- 必须记录 R8 后 Debug/Release APK 增量；
- 必须验证依赖不引入 Native ABI；
- Apache-2.0 和 Bouncy Castle License 必须进入第三方许可登记；
- PR09-13A 的测试参考实现不能复制到生产 Runtime。

## 7. Envelope

固定布局：

```text
Magic[8]
EnvelopeVersion:u16be = 1
HeaderEncoding:u16be = 1
HeaderLength:u32be
CanonicalHeader[HeaderLength]
WrapNonce[12]
WrappedRootKeyCiphertext[32]
WrappedRootKeyTag[16]
TinkStreamingCiphertext[to EOF]
```

Header 最大 4096 bytes。

Header Key：

```text
1 format_kind
2 kdf_id
3 kdf_profile_id
4 kdf_salt
5 key_wrap_algorithm_id
6 streaming_algorithm_id
7 serialization_id
8 envelope_id
9 required_feature_bits
10 device_key_slot
```

Portable 精确 Key 集：`1..9`。Snapshot 精确 Key 集：`1,2,3,5,6,7,8,9,10`。V1 禁止未知字段、重复 Key、Float、Tag、Null、indefinite length 和非最短编码。

### 7.1 AAD

```text
wrap_aad = Magic || EnvelopeVersion || HeaderEncoding || HeaderLength || CanonicalHeader

stream_aad = SHA-256(
  wrap_aad || WrapNonce || WrappedRootKeyCiphertext || WrappedRootKeyTag
)
```

## 8. Record Stream

```text
record_length:u32be
canonical_record_cbor[record_length]
```

记录类型：

```text
1 MANIFEST
2 ENTITY
3 BLOB_START
4 BLOB_CHUNK
5 BLOB_END
255 COMPLETE
```

顺序：

- 第一条必须是 MANIFEST；
- ENTITY/BLOB_* 的 `sequence` 从 0 连续递增；
- 每个 Blob 的 `chunk_index` 从 0 连续递增；
- 最后一条必须是 COMPLETE；
- COMPLETE 后必须立即认证 EOF。

`manifest_sha256` 精确计算 Canonical MANIFEST CBOR 字节，不包含四字节长度前缀。

`transcript_sha256` 精确计算从 MANIFEST 的四字节长度前缀开始，到 COMPLETE 前最后一条 Record 末尾的全部原始规范字节。

### 8.1 稳定 Entity Type Registry

PR09-13B Writer 只能写以下 ID，每项 `entity_schema_version=1`：

```text
issue
stage
execution_run
participant_snapshot
participant_state
run_budget
message
message_usage
cross_discussion
stage_summary_draft
stage_summary_draft_revision
confirmed_artifact
artifact_message_source
artifact_run_source
artifact_draft_source
artifact_material_source
material_reference
material_usage
personal_context_entry
personal_context_usage
stage_advancement
stage_advancement_measure
stage_advancement_skill_member
stage_advancement_material
stage_advancement_artifact
archive_event
resume_event
issue_relation
audio_asset
official_skill_combination
official_skill_combination_member
safe_user_setting
```

Room 表名不直接写入格式。每种 Entity 使用独立 Mapper 和确定性字段顺序。

### 8.2 兼容 ChatSession

- `ChatSession` 不作为独立 Portable Entity Type。
- `Issue.legacyChatSessionId` 和 `Message.chatId` 是当前 Room 兼容实现细节，不作为跨设备稳定 ID。
- Mapper 只导出与正式 Issue 关联的 Message 及 Issue 关系。
- PR09-14A/14B 在候选数据库中按 Issue 重建兼容 ChatSession，并把新本地数值 ID 回填到 Message。
- 未与正式 Issue 关联的 standalone legacy ChatSession 不进入 PR09-13B 的 Portable Backup；创建备份前必须显示其数量，并以 `unsupported_legacy_data` 阻止“全部数据”备份，不能静默遗漏。

`unsupported_legacy_data` 作为 PR09-13B 新增稳定业务预检错误码，不属于 Envelope Parser 错误码。

## 9. 数据白名单

Portable 默认包含：

- Issue、Stage；
- Run、Participant、State、Budget；
- 非 Pending Message 和实际使用快照；
- Draft、Revision、Artifact 与来源关系；
- MaterialReference、Material Usage；
- Personal Context Entry、Usage；
- Stage Advancement 全部正式子关系；
- Archive、Resume、Relation；
- AudioAsset 元数据和 AVAILABLE 正式音频；
- 官方 Skill 组合和历史 Participant/Skill Snapshot；
- 显式安全设置白名单。

永久排除：

- API Key、API Key 密文文件、Key ID/绑定/冷却；
- Keystore 材料和 Alias 清单；
- 备份密码、应用锁和密保；
- Token、Header、Cookie、URI Grant；
- 绝对路径、Cache、`.part`、Orphan；
- Pending Message、Running Run、正在生成的音频；
- 遥测正文和异常中的用户内容；
- 已 Purge 数据；
- 外部 URI 原始文件；
- APK 静态 Skill 资产；
- 设备快照目录。

## 10. Audio 规则

只允许从正式 `AudioAssetEntity` 与 `AudioFileStore.resolve(relativePath)` 枚举：

```text
fileState = AVAILABLE
deletedAt = null
purgeRequestedAt = null
来源 Message/Artifact 存在且属于同一 Issue/Stage
路径是受控相对路径
文件存在
格式、大小和 SHA-256 通过
```

Blob ID 使用稳定 AudioAsset ID，不保存原始文件名和绝对路径。任一应包含文件缺失或变化时整个备份返回 `source_changed`。

## 11. Material 外部 URI

- `http/https` 可以作为加密引用元数据保存；
- `content/file/绝对路径` 不保存原 Locator；
- 只保存脱敏显示信息、MIME、来源类型、Hash 和 `unavailableAfterImport=true`；
- 不读取、复制或删除外部原始文件；
- 导入后必须显示引用可能不可用。

## 12. Purge 与并发门禁

实现唯一 `BackupOperationGate`：

```text
进程内公平读写锁
+
noBackupFilesDir/jianyu-backup/operation.lock 的 FileChannel 排他锁
```

- 备份和快照持有写锁；
- 正式业务写、Purge 请求/执行、音频提交/删除持有读锁；
- 两个备份不能并行；
- 另一个进程持锁返回 `operation_already_running`；
- V1 不支持独立多进程 Worker。

以下 Issue 整体拒绝：

```text
REQUESTED
WAITING_FOR_TASKS
CANCELING_TASKS
DELETING_FILES
READY_FOR_DATABASE_PURGE
DATABASE_PURGING
FAILED_RETRYABLE
```

来源 Relation 已降级时只导出 `relationType`、`sourcePurgedAt` 和“来源已清除”事实。

## 13. Portable 一致性

流程：

1. 获取全局写锁；
2. 预检 Lifecycle/Purge/Run/Pending/Audio Work；
3. 生成 Source Token；
4. 在一致性 Room 读取边界内读取逻辑对象；
5. 在锁内流式读取 AVAILABLE 音频；
6. 重新生成 Source Token；
7. Token 不一致返回 `source_changed`；
8. 写完临时密文；
9. 重新打开并完整验证到认证 EOF；
10. 安全发布。

不允许按 Issue 部分成功，不允许变化后向已有密文追加增量。

## 14. Snapshot 一致性

流程：

1. 获取全局写锁；
2. 确认无 Run、Purge、Audio Worker 和相关 WorkManager；
3. 数据库完整性预检；
4. `wal_checkpoint(TRUNCATE)`；
5. 关闭 Room 单例；
6. 复制主数据库文件，不复制已清空 WAL/SHM；
7. 加密 AVAILABLE 音频；
8. 验证数据库 Hash、Manifest 和音频 Hash；
9. 重新打开 Room；
10. 执行最小查询和 `PRAGMA foreign_key_check`。

失败时必须尝试重新打开原 Room；不能把 App 留在关闭数据库状态。

## 15. SAF 提交语义

只支持可以完成以下操作的 Provider：

- 创建同目录临时 Document；
- 写入、flush/sync；
- 重新打开读取；
- rename；
- delete。

Provider 不具备安全临时文件和 rename 能力时返回 `target_write_failed`，禁止直接写最终 `.jybak`。目标已存在时不覆盖。

## 16. 稳定错误码

Envelope/验证错误码以 `pr-09-13a-backup-envelope-spec.md` 为准，包括：

```text
invalid_magic
unsupported_envelope_version
unsupported_manifest_version
unsupported_kdf
kdf_parameters_out_of_policy
kdf_resource_unavailable
unsupported_aead
unsupported_required_feature
invalid_header
authentication_failed
truncated_payload
trailing_data
chunk_order_invalid
duplicate_chunk
entry_limit_exceeded
entry_size_exceeded
total_size_exceeded
path_invalid
source_changed
purge_in_progress
active_work_in_progress
insufficient_space
target_write_failed
verification_failed
operation_canceled
temporary_cleanup_failed
snapshot_key_unavailable
snapshot_corrupted
database_checkpoint_failed
database_integrity_failed
operation_already_running
```

PR09-13B 业务预检额外允许：

```text
unsupported_legacy_data
provider_capability_missing
```

错误密码和所有合法 Header 后的认证篡改统一为 `authentication_failed`。

## 17. 公开测试向量

路径：

```text
docs/testing/vectors/pr-09-13a/portable-empty.json
docs/testing/vectors/pr-09-13a/portable-unicode-record.json
docs/testing/vectors/pr-09-13a/streaming-multisegment.json
docs/testing/vectors/pr-09-13a/negative-vectors.json
```

PR09-13B 生产实现必须逐字节复现 Portable 正向向量并通过全部负向向量。生产 RNG 不得接受固定向量输入。

## 18. PR09-13B 可修改文件

允许新增：

```text
app/src/main/java/com/elio/jianyu/backup/**
app/src/test/java/com/elio/jianyu/backup/**
app/src/androidTest/java/com/elio/jianyu/backup/**
app/src/main/java/com/elio/jianyu/ui/screens/settings/backup/**
docs/testing/pr-09-13b-*.md
docs/planning/pr-09-13b-*.md
```

允许最小修改：

```text
app/build.gradle.kts
app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt
设置导航中备份入口对应的既有文件
正式 Audio/Lifecycle 接口的门禁接入点
```

禁止修改：

```text
RoundtableDatabase.kt
app/schemas/
Room Entity、DAO、Migration
PR09-12 Purge 状态机语义
官方 Skill Catalog/资产
AndroidManifest.xml
backup_rules.xml
data_extraction_rules.xml
导入和数据库替换代码
```

若全局门禁需要为既有写入口增加适配器，必须只增加依赖注入/门禁调用，不改变原业务状态机。

## 19. PR09-13B 禁止重新讨论的决定

- 混合架构；
- Argon2id Profile 1；
- Bouncy Castle 1.84；
- Tink Android 1.23.0；
- AES-256-GCM Root Key 包装；
- Tink AES256_GCM_HKDF_1MB；
- 两层 HKDF 域分离；
- 受限确定性 CBOR；
- 长度前缀 Record Stream；
- V1 不压缩；
- Magic、扩展名、MIME、字段和错误码；
- API Key 永久排除；
- Portable/Snapshot Key 分离；
- Purge 中 Issue 整体拒绝；
- SAF Provider 不安全时失败关闭；
- 创建后完整验证才报告成功。

## 20. PR09-14A/14B 未来需求

PR09-14A Parser 必须：

- 在隔离区完成结构、认证、资源上限和业务不变量校验；
- 完整读取到认证 EOF；
- 生成分类汇总和逐项差异；
- 识别相同数据和冲突；
- 不写当前库；
- 对外部引用显示不可用状态；
- 不恢复旧 Purge 意图。

PR09-14B 必须：

- 替换前创建并验证 Snapshot；
- 在候选数据库中重建兼容 ChatSession 数值 ID；
- 原子切换数据库和正式附件；
- 提交点前不修改当前库；
- 失败恢复原库；
- 回退前再创建并验证快照；
- 保持降级 Relation 的“来源已清除”事实。

## 21. 回滚

PR09-13B 回滚：

- 可以关闭新导出和快照入口；
- 可以停止创建新文件；
- 不得删除用户已创建的 `.jybak` 或 `.jysnap`；
- 不得删除 Snapshot Keystore Alias，除非用户明确选择删除全部设备快照并确认不可恢复；
- 格式实现回滚后仍需保留只读验证或明确显示“不支持的版本”。
