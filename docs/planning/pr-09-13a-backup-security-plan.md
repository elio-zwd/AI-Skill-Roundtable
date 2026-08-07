# PR09-13A：见域备份安全设计实施计划

> 状态：已获用户批准，进入设计文档、测试原型与公开测试向量阶段。
>
> 基线：`main@40a106e66854c48efb008f434af1ed850128afdc`
>
> 分支：`security/pr-09-13a-backup-design`
>
> 本计划执行仓库内 `Superpowers:writing-plans` 与 `Superpowers:test-driven-development` 的等价人工流程。当前对话具备 GitHub 远端读写能力，但没有可用 Android 本地构建环境；任何构建和测试结论必须来自精确 Head 的 GitHub CI 或后续本地严格只读验收。

## 1. 目标

冻结见域 V1 可移植加密备份与设备绑定恢复快照的威胁模型、数据范围、密码学算法、二进制 Envelope、错误模型、一致性和并发门禁，并提交不进入生产 Runtime 的 Kotlin/JVM 设计原型与公开测试向量，为 PR09-13B 提供无二义性的施工接口。

## 2. 架构结论

- 可移植备份采用逻辑规范化记录流，不复制 Room 数据库文件。
- 设备绑定恢复快照采用一致性物理数据库快照与受控正式音频副本。
- 可移植备份使用独立密码、Argon2id、随机 Root Key、AES-256-GCM Root Key 包装与 Tink AES256_GCM_HKDF_1MB Streaming AEAD。
- 设备快照使用独立 Android Keystore Alias 包装随机 Snapshot Root Key，与 API Key Alias 完全分离。
- Header 和记录使用受限确定性 CBOR；外层使用固定字段和大端长度前缀；V1 不使用 ZIP、TAR 或压缩。
- 备份与 Purge、音频提交、数据库写入共享全局操作门禁；PR09-13A 只冻结协议与测试原型，不接入生产 Coordinator。

## 3. 全局约束

- 不修改 `RoundtableDatabase.kt`、Room Entity、DAO、Migration 或 `app/schemas/`。
- 不修改生产 Repository、Lifecycle、Audio Worker、UI、`JianyuAppRuntime`、Manifest、`backup_rules.xml` 或 `data_extraction_rules.xml`。
- 不创建生产导出、导入、恢复、SAF、WorkManager 或设置页入口。
- 原型只允许位于 `app/src/test/java/com/elio/jianyu/backup/design/`。
- 原型类必须写明 `design prototype / not production API / not used by app runtime`。
- 第三方依赖仅允许 `testImplementation`；不得进入生产 `implementation`。
- 测试向量不得包含真实用户数据、真实密码、API Key、Token、设备密钥或个人信息。
- 所有未知版本、未知算法、非法长度、超限参数和认证失败必须失败关闭。
- PR 保持 Draft；未经用户明确授权不得 Ready、合并、启用自动合并或删除分支。

## 4. 文件结构

### 4.1 安全和架构文档

- `docs/security/pr-09-13a-backup-threat-model.md`：资产、攻击者、故障、边界、系统备份审计和风险接受。
- `docs/architecture/pr-09-13a-backup-envelope-spec.md`：算法、密钥层次、Canonical CBOR、Envelope、记录流、错误码和提交语义。
- `docs/architecture/pr-09-13a-backup-data-scope.md`：逐对象白名单、排除项、外部 URI、Purge 和音频规则。
- `docs/planning/pr-09-13a-interface-handoff.md`：PR09-13B 和 PR09-14A/14B 的冻结接口。
- `docs/testing/pr-09-13a-backup-security-review-checklist.md`：独立安全审查清单和判定标准。
- `docs/testing/pr-09-13a-local-readonly-acceptance-prompt.md`：精确 Head 的本地只读验收步骤。

### 4.2 公开测试向量

- `docs/testing/vectors/pr-09-13a/README.md`
- `docs/testing/vectors/pr-09-13a/portable-empty.json`
- `docs/testing/vectors/pr-09-13a/portable-unicode-record.json`
- `docs/testing/vectors/pr-09-13a/streaming-multisegment.json`
- `docs/testing/vectors/pr-09-13a/negative-vectors.json`

### 4.3 Kotlin/JVM 设计原型

- `BackupDesignConstants.kt`：Magic、版本、算法 ID、长度和资源上限。
- `BackupDesignError.kt`：稳定错误码和失败结果。
- `CanonicalCborPrototype.kt`：受限确定性 CBOR 编解码。
- `BackupEnvelopePrototype.kt`：Header 和 Envelope 严格解析。
- `BackupCryptoPrototype.kt`：Argon2id、HKDF、AES-GCM Root Key 包装与参考 Streaming AEAD。
- `BackupRecordStreamPrototype.kt`：记录长度、顺序、Complete 和 Transcript Hash。
- `BackupDataScopePrototype.kt`：数据白名单和排除规则。
- 对应 `*Test.kt`：正向、负向、公开向量和 Tink 兼容测试。

### 4.4 构建配置

仅修改 `app/build.gradle.kts` 的测试依赖：

```kotlin
// PR09-13A design prototype only; not packaged in production APK.
testImplementation("org.bouncycastle:bcprov-jdk18on:1.84")
testImplementation("com.google.crypto.tink:tink-android:1.23.0")
```

## 5. TDD 执行任务

### Task 1：威胁模型与数据边界

**产出**：威胁模型和逐对象白名单。

- [ ] 记录当前 Room v12、Lifecycle/Purge、AudioAsset、MaterialReference、PersonalContext 和系统备份事实。
- [ ] 覆盖离线猜测、篡改、截断、重排、拼接、资源耗尽、路径穿越、进程终止、空间不足和并发竞态。
- [ ] 明确已控制解锁设备、用户泄露密码、闪存物理擦除和被攻破 OS 不在承诺范围内。
- [ ] 冻结已 Purge 数据不可复活、外部 URI 默认只保存引用事实、API Key 永久排除。

**提交**：`docs: 建立见域备份威胁模型`

### Task 2：Envelope、密码学和错误协议

**产出**：无歧义字节格式和 PR09-13B 接口。

- [ ] 冻结 Argon2id Profile 1：64 MiB、3 次、并行度 1、16-byte Salt、32-byte 输出。
- [ ] 冻结 AES-256-GCM Root Key 包装和 Tink AES256_GCM_HKDF_1MB。
- [ ] 冻结 Magic、Header、AAD、记录流、Complete、版本和 Required Feature 规则。
- [ ] 冻结 Header 4096-byte、Record 1 MiB、条目、Blob 和总大小上限。
- [ ] 冻结 Portable 与 Snapshot 的独立密钥、扩展名、MIME 和错误码。

**提交**：`docs: 冻结备份格式与密钥生命周期`

### Task 3：测试先行和最小原型

**RED 设计**：先提交以下期望行为测试；当前远端环境不能执行，测试的真实 RED/GREEN 由精确 Head CI 和本地验收补证。

- [ ] Header 非 Canonical、未知字段、重复 Key、超限长度失败。
- [ ] KDF Profile 不精确匹配时在分配前失败。
- [ ] 错误密码、Header、Wrapped Key、Ciphertext 和 Tag 篡改统一认证失败。
- [ ] 记录缺失 Manifest/Complete、重复、乱序、截断和尾随失败。
- [ ] Data Scope 排除 API Key、绝对路径、Pending、Orphan、Purge 中 Issue。
- [ ] 固定向量验证 Argon2id、Wrapped Root Key、记录流、密文和 SHA-256。

**GREEN 设计**：实现只满足上述测试的测试源集原型，不接入生产代码。

- [ ] 实现受限 CBOR。
- [ ] 实现严格 Envelope Parser。
- [ ] 实现 Argon2id、HKDF、AES-GCM 包装。
- [ ] 实现官方 Tink 规范兼容的确定性 Streaming Reference，仅供公开向量。
- [ ] 使用 Tink `AesGcmHkdfStreaming` 解密参考向量，验证兼容性。
- [ ] 实现记录流和白名单原型。

**提交**：`test: 增加备份格式原型与公开测试向量`

### Task 4：审查、验收和交接

- [ ] 完成独立安全审查清单。
- [ ] 完成本地严格只读验收 Prompt。
- [ ] 确认交接无 `TODO`、`TBD`、“待定”或“实现时选择”。
- [ ] 确认原型未被 `app/src/main`、UI、Runtime 或生产 Coordinator 引用。
- [ ] 确认无 Room Schema、Manifest、系统备份 XML 或生产依赖变化。

**提交**：`docs: 添加PR09-13B接口交接与验收门禁`

## 6. 验证命令

本地严格只读验收至少执行：

```powershell
.\gradlew.bat --stop
.\gradlew.bat --no-daemon :app:compileDebugKotlin --stacktrace
.\gradlew.bat --no-daemon :app:testDebugUnitTest --stacktrace
.\gradlew.bat --no-daemon :app:lintDebug --stacktrace
.\gradlew.bat --no-daemon :app:assembleDebug --stacktrace
.\gradlew.bat --no-daemon :app:assembleRelease --stacktrace
.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest --stacktrace

git diff --exit-code 40a106e66854c48efb008f434af1ed850128afdc...HEAD -- app/schemas
git diff --exit-code 40a106e66854c48efb008f434af1ed850128afdc...HEAD -- app/src/main
git status --short
```

还需执行：

- Secret Scan；
- 公开测试向量复现；
- 测试依赖许可证和生产 APK 排除检查；
- 文档链接、错误码、Magic 和版本交叉一致性检查；
- GitHub CI 精确 Head 状态读取。

## 7. 完成声明门禁

只有同时取得以下真实证据，才可把安全审查结论提升为 `PASS` 或 `PASS_WITH_NOTES`：

- 精确 Head CI 全绿；
- JVM 原型和公开向量真实执行通过；
- Debug、Release、Lint 和 AndroidTest APK 构建通过；
- 本地严格只读验收完成；
- 独立安全审查没有未冻结的核心密码学决定；
- 工作区和 Schema 无漂移。

在此之前统一使用：

```text
INSUFFICIENT_EVIDENCE
```
