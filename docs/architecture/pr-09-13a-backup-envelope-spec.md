# PR09-13A：见域备份 Envelope 规范

> Portable Format ID：`jianyu-portable-backup/1`
>
> Snapshot Format ID：`jianyu-device-snapshot/1`
>
> Envelope Version：`1`
>
> Manifest Version：`1`
>
> 本规范按字节冻结。PR09-13B 不得改变字段顺序、长度、算法、Canonical 编码或失败语义；需要变更时必须增加新的版本或算法 ID。

## 1. 方案选择

### 1.1 可移植备份

使用逻辑规范化记录流：

```text
Room v12 / 正式文件服务
→ 白名单 Mapper
→ Canonical Record Stream
→ Tink Streaming AEAD
→ Portable Envelope
```

Room 表名和字段不是 Portable Format 的永久接口。未来 Room v13+ 通过新的 Mapper 继续生成 Manifest v1；只有无法无损表达时才增加 Manifest Version。

### 1.2 设备绑定恢复快照

使用一致性物理快照：

```text
全局写冻结
→ WAL checkpoint(TRUNCATE)
→ 关闭 Room
→ 数据库主文件 + 正式音频
→ Snapshot Manifest
→ Device-bound Envelope
```

Snapshot 只用于当前设备回退，不支持跨设备差异合并。

## 2. 标识、扩展名和 MIME

| 类型 | Format ID | Magic | 扩展名 | MIME |
|---|---|---|---|---|
| Portable | `jianyu-portable-backup/1` | `JYBKP\r\n\x1A` | `.jybak` | `application/vnd.jianyu.backup` |
| Snapshot | `jianyu-device-snapshot/1` | `JYSNP\r\n\x1A` | `.jysnap` | `application/vnd.jianyu.snapshot` |

Portable Magic 字节：

```text
4A 59 42 4B 50 0D 0A 1A
```

Snapshot Magic 字节：

```text
4A 59 53 4E 50 0D 0A 1A
```

扩展名和 MIME 在 PR09-13A 冻结，但在 PR09-13B 完成前不得对用户宣称可用。

## 3. 整数和字符串规则

- 外层固定字段整数统一使用无符号大端序。
- 字符串使用 UTF-8。
- 密码先进行 Unicode NFC，再编码 UTF-8。
- 密码不执行 trim、大小写转换、NFKC 或空格折叠。
- 拒绝空密码、NUL、未配对 UTF-16 surrogate。
- 规范化后的密码长度必须为 1～1024 bytes。
- 时间使用 Unix Epoch Milliseconds，类型为非负 64-bit Integer。
- 稳定 ID 使用 ASCII lower-case 或 UUID 字符串，由各逻辑 Record Schema 约束；不得使用文件路径作为 ID。

## 4. 外层 Envelope 字节结构

所有 Portable/Snapshot 文件使用同一外层布局：

| 顺序 | 长度 | 字段 |
|---|---:|---|
| 1 | 8 | Magic |
| 2 | 2 | Envelope Version，固定 `0x0001` |
| 3 | 2 | Header Encoding，固定 `0x0001` |
| 4 | 4 | Header Length |
| 5 | Header Length | Canonical Header CBOR |
| 6 | 12 | Root Key Wrap Nonce |
| 7 | 32 | Wrapped Root Key Ciphertext |
| 8 | 16 | Root Key Wrap Tag |
| 9 | 至 EOF | Streaming AEAD Ciphertext |

约束：

- Header Length 最小 1，最大 4096。
- Root Key 固定 32 bytes，因此 AES-GCM 包装区固定 48 bytes。
- Wrap Nonce 固定 12 bytes。
- Tag 固定 16 bytes。
- 外层不允许可选字段、尾部 Footer 或未认证扩展区。
- Header Length 不包括前四项和 Key Wrap 区。

## 5. Canonical Header CBOR

### 5.1 受限确定性 CBOR

Header 使用 RFC 8949 确定性编码的受限子集：

- 顶层必须是 Map。
- Map Key 必须是正整数。
- 禁止重复 Key。
- Key 按编码后字节序排序；当前整数 Key 等价于数值升序。
- 整数和长度必须使用最短编码。
- 禁止 indefinite-length、Float、Tag、Simple Value、Text Map Key 和 Null。
- Byte String 与 Text String 必须使用 definite length。
- Parser 必须重新编码并与原始 Header 字节逐字节比较；语义相同但非 Canonical 的输入返回 `invalid_header`。

### 5.2 Header Key

| Key | 字段 | 类型 | Portable | Snapshot |
|---:|---|---|---|---|
| 1 | `format_kind` | uint | `1` | `2` |
| 2 | `kdf_id` | uint | `1` | `0` |
| 3 | `kdf_profile_id` | uint | `1` | `0` |
| 4 | `kdf_salt` | bstr(16) | 必须 | 禁止 |
| 5 | `key_wrap_algorithm_id` | uint | `1` | `1` |
| 6 | `streaming_algorithm_id` | uint | `1` | `1` |
| 7 | `serialization_id` | uint | `1` | `1` |
| 8 | `envelope_id` | bstr(16) | 必须 | 必须 |
| 9 | `required_feature_bits` | uint | `0` | `0` |
| 10 | `device_key_slot` | uint | 禁止 | `1` |

V1 禁止任何未知 Header Key。未来可选能力必须通过新的 Envelope Version 或 Required Feature Bit 注册，不能依赖旧 Parser 忽略字段。

### 5.3 算法 ID

```text
kdf_id:
  0 = none/device-bound
  1 = Argon2id

key_wrap_algorithm_id:
  1 = AES-256-GCM, 12-byte nonce, 16-byte tag

streaming_algorithm_id:
  1 = Tink AES256_GCM_HKDF_1MB, RAW, key version 0

serialization_id:
  1 = Jianyu deterministic CBOR record stream v1
```

未知 ID 返回对应 `unsupported_*` 错误，不允许自动降级。

## 6. KDF Profile

### 6.1 Portable Profile 1

```text
KDF：Argon2id
Argon2 Version：0x13
Memory：65,536 KiB
Iterations：3
Parallelism：1
Salt：16 bytes
Output：32 bytes
```

Header 只保存 `kdf_id=1`、`kdf_profile_id=1` 和 16-byte Salt，不保存可任意修改的 m/t/p。生产 Parser 根据 Profile Registry 得到固定参数。

### 6.2 参数策略

- V1 Writer 只能写 Profile 1。
- Reader 只接受已注册 Profile ID。
- 未知 Profile 返回 `kdf_parameters_out_of_policy`。
- KDF 参数检查必须发生在内存分配和 Argon2 执行前。
- KDF 资源分配失败返回 `kdf_resource_unavailable`，不得改用 PBKDF2 或降低参数。
- scrypt 和 PBKDF2-HMAC-SHA256 只保留为未来独立 Profile/Envelope 候选，V1 不读取、不写入、不回退。

## 7. 密钥层次

### 7.1 Portable

```text
password UTF-8 after NFC
  → Argon2id Profile 1
  → KEK (32 bytes)

SecureRandom
  → Portable Root Key (32 bytes)

KEK + Wrap Nonce + Root Key Wrap AAD
  → AES-256-GCM
  → Wrapped Root Key Ciphertext (32) + Tag (16)

HKDF-SHA256(
  ikm = Portable Root Key,
  salt = envelope_id,
  info = "jianyu/portable-backup/v1/stream",
  length = 32
)
  → Streaming IKM
```

### 7.2 Snapshot

```text
SecureRandom
  → Snapshot Root Key (32 bytes)

Android Keystore Alias "jianyu_backup_snapshot_wrap_v1"
  + Wrap Nonce
  + Root Key Wrap AAD
  → AES-256-GCM
  → Wrapped Snapshot Root Key

HKDF-SHA256(
  ikm = Snapshot Root Key,
  salt = envelope_id,
  info = "jianyu/device-snapshot/v1/stream",
  length = 32
)
  → Streaming IKM
```

设备快照 Alias 必须与现有 API Key Alias `skill_roundtable_api_key_v1` 分离。Alias 不写入 Header；Snapshot Header 只保存无设备身份含义的 `device_key_slot=1`。

### 7.3 密钥生命周期

- 每次创建新文件都生成新的 Root Key、Envelope ID、Wrap Nonce、Tink Salt 和 Nonce Prefix。
- Root Key 和 KEK 只在操作内存中短暂存在，用后覆盖尽力清理；不宣称 JVM 能保证物理清零。
- 修改备份密码必须创建全新备份，不原地重包唯一旧备份。
- 忘记密码时不能由密保、应用锁或设备 Key 解密旧 Portable Backup。
- 删除 Snapshot Alias 后，现有 Snapshot 返回 `snapshot_key_unavailable`，不得创建替代 Key 尝试解密。

## 8. AAD

### 8.1 Root Key Wrap AAD

```text
wrap_aad =
  Magic
  || EnvelopeVersion:u16
  || HeaderEncoding:u16
  || HeaderLength:u32
  || CanonicalHeader
```

因此 Magic、版本、KDF、算法、Envelope ID、Feature Bits 和 Device Key Slot 的任何变化都会使 Root Key 解包失败。

### 8.2 Streaming Associated Data

```text
stream_aad = SHA-256(
  wrap_aad
  || wrap_nonce[12]
  || wrapped_root_key_ciphertext[32]
  || wrapped_root_key_tag[16]
)
```

Tink Streaming AEAD 的 Associated Data 使用上述固定 32-byte 摘要。Associated Data 不含用户正文，也不在 Header 泄露数据范围。

## 9. Tink Streaming AEAD Profile

```text
KeyValue / IKM：32 bytes
HKDF Hash：HMAC-SHA256
Derived AES Key：32 bytes
Ciphertext Segment Size：1,048,576 bytes
First Segment Offset：0
Output Prefix：RAW
Key Version：0
```

Tink 流格式：

```text
stream_header:
  header_length:u8 = 40
  stream_salt[32]
  nonce_prefix[7]

segment_i:
  AES-GCM(
    key = HKDF-SHA256(streaming_ikm, stream_salt, stream_aad, 32),
    nonce = nonce_prefix[7] || segment_index:u32be || final_flag:u8,
    aad = empty,
    plaintext = segment_plaintext
  )
```

`final_flag=0x00` 表示后续还有 Segment，`0x01` 表示最后一个 Segment。

调用方必须持续读取解密流直到认证 EOF；只读取 Manifest 或前几个 Record 后停止不能视为完整验证。

## 10. 明文 Record Stream

Streaming AEAD 内部明文由连续 Record 构成：

```text
record_length:u32be
record_cbor[record_length]
```

约束：

- `record_length` 必须为 1～1,048,576。
- 读取长度前检查总大小和整数溢出。
- Record CBOR 使用与 Header 相同的受限确定性 CBOR 子集。
- 第一条必须是 `MANIFEST`。
- 最后一条必须是 `COMPLETE`。
- `COMPLETE` 后必须立即认证 EOF。

### 10.1 Record Type

```text
1   MANIFEST
2   ENTITY
3   BLOB_START
4   BLOB_CHUNK
5   BLOB_END
255 COMPLETE
```

每个 Record 顶层是 Map，Key `1` 固定为 `record_type`。

### 10.2 MANIFEST

| Key | 字段 | 类型 |
|---:|---|---|
| 1 | record_type | `1` |
| 2 | manifest_version | `1` |
| 3 | format_id | text |
| 4 | created_at | uint64 |
| 5 | app_version_name | text |
| 6 | app_version_code | uint |
| 7 | source_room_version | uint |
| 8 | logical_entry_count | uint |
| 9 | blob_count | uint |
| 10 | total_declared_plaintext_bytes | uint64 |
| 11 | required_feature_bits | uint |
| 12 | backup_scope | array of text stable IDs |

`format_id` 必须与 Magic/Header 一致。

### 10.3 ENTITY

| Key | 字段 | 类型 |
|---:|---|---|
| 1 | record_type | `2` |
| 2 | sequence | uint，从 0 连续递增 |
| 3 | logical_entry_id | text |
| 4 | entity_type | text |
| 5 | entity_schema_version | uint |
| 6 | payload | deterministic CBOR value |
| 7 | payload_sha256 | bstr(32) |

Entity Type 必须来自 PR09-13B Registry；未知 Entity Type 在 PR09-14A 返回 `unsupported_required_feature`，不得跳过后继续导入。

### 10.4 BLOB_START

| Key | 字段 | 类型 |
|---:|---|---|
| 1 | record_type | `3` |
| 2 | sequence | uint |
| 3 | logical_entry_id | text |
| 4 | blob_type | text |
| 5 | mime_type | text |
| 6 | declared_size | uint64 |
| 7 | declared_sha256 | bstr(32) |
| 8 | chunk_size | uint，固定 `262144` |

Blob 使用逻辑 ID，不携带原始路径或文件名。

### 10.5 BLOB_CHUNK

| Key | 字段 | 类型 |
|---:|---|---|
| 1 | record_type | `4` |
| 2 | sequence | uint |
| 3 | logical_entry_id | text |
| 4 | chunk_index | uint，从 0 连续递增 |
| 5 | data | bstr，最大 262144 bytes |

### 10.6 BLOB_END

| Key | 字段 | 类型 |
|---:|---|---|
| 1 | record_type | `5` |
| 2 | sequence | uint |
| 3 | logical_entry_id | text |
| 4 | actual_size | uint64 |
| 5 | actual_sha256 | bstr(32) |
| 6 | chunk_count | uint |

### 10.7 COMPLETE

| Key | 字段 | 类型 |
|---:|---|---|
| 1 | record_type | `255` |
| 2 | record_count_before_complete | uint |
| 3 | entity_count | uint |
| 4 | blob_count | uint |
| 5 | total_plaintext_bytes_before_complete | uint64 |
| 6 | transcript_sha256 | bstr(32) |
| 7 | manifest_sha256 | bstr(32) |

`transcript_sha256` 计算范围是从第一条 MANIFEST 的 `record_length` 开始，到 COMPLETE 前最后一条 Record 末尾的全部原始规范字节。COMPLETE 本身不进入 Transcript，避免循环依赖。

## 11. 顺序和完整性规则

- 全局 `sequence` 对 ENTITY/BLOB_* 从 0 连续递增。
- MANIFEST 和 COMPLETE 不带 sequence。
- 每个 Blob 只能出现一次 BLOB_START 和一次 BLOB_END。
- BLOB_CHUNK 必须位于对应 Start/End 之间。
- `chunk_index` 必须从 0 连续递增；重复、缺失或倒序返回 `chunk_order_invalid` 或 `duplicate_chunk`。
- BLOB_END 的 size、Hash、chunk_count 必须与实际流一致。
- COMPLETE 的数量、总字节和 Hash 必须与解析结果一致。
- Manifest 声明和 COMPLETE 实际统计不一致返回 `verification_failed`。
- COMPLETE 后存在任何明文或密文尾随数据返回 `trailing_data`。

## 12. 资源上限

```text
Header Length：4,096 bytes
单 Record CBOR：1,048,576 bytes
单 Blob Chunk：262,144 bytes
逻辑条目总数：1,000,000
Blob 总数：10,000
单 Blob：68,719,476,736 bytes（64 GiB）
总声明明文：1,099,511,627,776 bytes（1 TiB）
密码 NFC UTF-8：1～1,024 bytes
```

实现必须使用溢出安全加法，在分配 ByteArray、启动 Argon2、创建临时文件或解压前完成检查。V1 不压缩，因此不存在解压后大小或压缩比字段。

## 13. 版本兼容

### 13.1 Envelope Version

- Reader 只接受 `1`。
- 未知值返回 `unsupported_envelope_version`。
- 不根据 Magic 猜测旧格式，不回退明文或 Markdown。

### 13.2 Manifest Version

- V1 Reader 只接受 `1`。
- 未知值返回 `unsupported_manifest_version`。
- Room Schema Version 与 Manifest Version 分离。

### 13.3 Required Feature Bits

V1 只允许 `0`。任何非零值返回：

```text
unsupported_required_feature
```

未来 Reader 只有显式注册并完整实现某一 Bit 后才能接受。

## 14. 错误码

稳定错误码：

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

错误密码、Root Key Wrap 篡改和合法 Header 后的 AEAD 认证错误统一为 `authentication_failed`。

## 15. 输出提交语义

### 15.1 Portable SAF

```text
创建 <target>.partial-<opaque-id>
→ 流式加密写入
→ flush / sync
→ 重新打开并完整解密验证到认证 EOF
→ Provider rename 为最终 .jybak
→ 用户可见成功
```

要求：

- Provider 必须支持同目录临时文件、reopen、rename 和 delete。
- 无安全 rename 能力时返回 `target_write_failed`，不得直接写最终文件名。
- 目标已存在时不覆盖，创建新名称。
- 失败或取消只允许保留带 `.partial-` 的无效密文；清理失败返回 `temporary_cleanup_failed`。

### 15.2 Device Snapshot

```text
noBackupFilesDir/jianyu-backup/snapshots/<id>.jysnap.part
→ 流式写入
→ flush / fsync
→ 完整验证
→ 同目录原子 rename 为 .jysnap
→ 发布 Snapshot Index
```

Snapshot Index 只在最终文件验证后写入。索引写入失败时快照不显示为可用，并进入可恢复对账流程。

## 16. 一致性和全局门禁

PR09-13B 必须实现一个唯一 `BackupOperationGate`：

- 进程内公平读写锁；
- `noBackupFilesDir/jianyu-backup/operation.lock` 的 `FileChannel` 排他锁；
- 备份/快照持有全局写锁；
- 业务写入、Purge 请求/执行、音频提交/删除持有读锁；
- 两个备份不能并行；
- 发现另一个进程持锁返回 `operation_already_running`；
- V1 不支持独立多进程 Worker。

### 16.1 Portable 一致性

- 获取写锁后检查 Lifecycle、Purge Operation、Running Run、Pending Message 和 Audio Work。
- 活动工作返回 `active_work_in_progress`，不自动停止。
- 在锁内读取全部逻辑记录和正式音频。
- 开始和发布前两次生成 Source Token；差异返回 `source_changed`。

### 16.2 Snapshot 一致性

- 获取写锁；
- 确认无活动工作；
- 执行 `wal_checkpoint(TRUNCATE)`；
- 关闭 Room 单例；
- 复制主数据库文件，不复制已清空的 WAL/SHM；
- 加密正式音频；
- 验证数据库 Hash、Manifest、音频 Hash；
- 重新打开 Room 并执行最小读取和外键检查。

不得声称单个 Room `@Transaction` 覆盖数据库文件、音频和 WorkManager。

## 17. 依赖冻结

PR09-13A 测试原型：

```kotlin
testImplementation("org.bouncycastle:bcprov-jdk15to18:1.84")
testImplementation("com.google.crypto.tink:tink-android:1.23.0")
```

PR09-13B 生产候选使用相同版本与构件：

```kotlin
implementation("org.bouncycastle:bcprov-jdk15to18:1.84")
implementation("com.google.crypto.tink:tink-android:1.23.0")
```

选择 `bcprov-jdk15to18:1.84` 的原因是当前项目固定 JDK 17 和 Android Jetifier 构建链。`bcprov-jdk18on:1.84` 是多版本 JAR，并在精确 Head `a075ff8bd2a4a70bc8dd12621cdd8ab99e64315d` 的 GitHub Actions 中因 `META-INF/versions/25` class major 69 无法被当前 Jetifier 扫描，导致测试启动前失败。该失败不是 Argon2id 算法或公开向量失败。V1 不通过 Jetifier ignorelist 绕过依赖检查，也不降低 Bouncy Castle 版本。

生产采用前还必须满足：

- 精确版本无阻断安全公告；
- 许可证登记完成；
- R8 后 APK 增量实测；
- API 26、API 28 和目标真机兼容；
- 公开向量和 Tink 兼容测试通过。

不得注册 Bouncy Castle 为全局 Provider；只使用轻量级 Argon2 API。生产依赖升级必须增加兼容性 PR，不得在无向量验证时直接更新。

## 18. 测试向量规则

公开向量固定：

- ASCII 密码和 Unicode NFC 密码；
- Salt、Envelope ID、Root Key、Wrap Nonce；
- Streaming Salt 和 Nonce Prefix；
- Header、Record Stream、KEK、Wrapped Root Key；
- Streaming Ciphertext 和完整文件 SHA-256；
- 空逻辑 Payload、单文本 Entity、小型二进制 Blob；
- 多 Segment 和不足完整 Segment 的最后一块；
- 错误密码、Header/Ciphertext/Tag 篡改、截断、尾随、重排、重复和超限声明。

生产随机数接口不得接受测试固定值；固定输入只存在 `app/src/test`。
