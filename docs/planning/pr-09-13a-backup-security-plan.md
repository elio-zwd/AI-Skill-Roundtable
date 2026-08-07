# PR09-13A：见域备份安全设计实施计划

> 状态：设计已获用户批准，文档、测试专用原型和公开测试向量已进入 Draft PR 验证阶段。
>
> 基线：`main@40a106e66854c48efb008f434af1ed850128afdc`
>
> 分支：`security/pr-09-13a-backup-design`
>
> 本计划按仓库内 `Superpowers:writing-plans`、`Superpowers:test-driven-development`、`Superpowers:systematic-debugging` 与 `Superpowers:verification-before-completion` 执行等价人工流程。当前对话只有 GitHub 远端能力，没有 Android 本地构建环境；任何通过结论必须来自精确 Head 的 GitHub CI、独立安全审查或本地严格只读验收。

## 1. 目标

冻结见域 V1 可移植加密备份与设备绑定恢复快照的威胁模型、数据范围、密码学算法、二进制 Envelope、错误模型、一致性和并发门禁，并提交不进入生产 Runtime 的 Kotlin/JVM 设计原型与公开测试向量，为 PR09-13B 提供无二义性的施工接口。

## 2. 冻结架构

- 可移植备份采用逻辑规范化记录流，不复制 Room 数据库文件。
- 设备绑定恢复快照采用 WAL checkpoint 后的一致性数据库主文件与受控正式音频副本。
- Portable 使用独立密码、Argon2id、随机 Root Key、AES-256-GCM Root Key 包装与 Tink `AES256_GCM_HKDF_1MB` Streaming AEAD。
- Snapshot 使用独立 Android Keystore Alias 包装随机 Snapshot Root Key，与 API Key Alias 完全分离。
- Header 和记录采用受限确定性 CBOR；外层使用固定字段和大端长度前缀；V1 不使用 ZIP、TAR 或压缩。
- 备份与 Purge、音频提交和数据库正式写入共享全局操作门禁。
- PR09-13A 只冻结协议与测试原型，不接入生产 Coordinator、UI、SAF、WorkManager、导入或恢复。

## 3. 不可越界事项

- 不修改 `RoundtableDatabase.kt`、Room Entity、DAO、Migration 或 `app/schemas/`。
- 不修改生产 Repository、Lifecycle、Audio Worker、UI、`JianyuAppRuntime`、Manifest、`backup_rules.xml` 或 `data_extraction_rules.xml`。
- 原型只允许位于 `app/src/test/java/com/elio/jianyu/backup/design/`。
- 第三方密码学依赖只允许 `testImplementation`，不得进入生产 APK。
- 测试向量不得包含真实用户数据、真实密码、API Key、Token、设备密钥或个人信息。
- 未知版本、算法、非法长度、超限参数和认证失败必须失败关闭。
- PR 保持 Draft；未经用户明确授权不得 Ready、合并、启用自动合并或删除分支。

## 4. 文件结构

### 4.1 安全、架构和交接

- `docs/security/pr-09-13a-backup-threat-model.md`
- `docs/architecture/pr-09-13a-backup-envelope-spec.md`
- `docs/architecture/pr-09-13a-backup-data-scope.md`
- `docs/planning/pr-09-13a-interface-handoff.md`
- `docs/testing/pr-09-13a-backup-security-review-checklist.md`
- `docs/testing/pr-09-13a-local-readonly-acceptance-prompt.md`

### 4.2 公开测试向量

- `docs/testing/vectors/pr-09-13a/portable-empty.json`
- `docs/testing/vectors/pr-09-13a/portable-unicode-record.json`
- `docs/testing/vectors/pr-09-13a/streaming-multisegment.json`
- `docs/testing/vectors/pr-09-13a/negative-vectors.json`

### 4.3 Kotlin/JVM 设计原型

- `BackupDesignConstants.kt`
- `CanonicalCborPrototype.kt`
- `BackupEnvelopePrototype.kt`
- `BackupCryptoPrototype.kt`
- `BackupRecordStreamPrototype.kt`
- `BackupDataScopePrototype.kt`
- 对应 `*Test.kt`、文档契约、依赖隔离和公开向量测试。

### 4.4 测试依赖

```kotlin
// PR09-13A design prototype only; not packaged in the production APK.
testImplementation("org.bouncycastle:bcprov-jdk15to18:1.84")
testImplementation("com.google.crypto.tink:tink-android:1.23.0")
```

`bcprov-jdk18on:1.84` 不再使用。精确 Head `a075ff8bd2a4a70bc8dd12621cdd8ab99e64315d` 的 GitHub Actions 已证明该多版本 JAR 含 Java 25 class（major 69），当前 Android Jetifier/JDK 17 构建链在 `JetifyTransform` 阶段失败，测试尚未启动。最小修复是在不改变 Bouncy Castle 版本、Argon2id API、许可证或密码学参数的前提下，改用官方同版本 `bcprov-jdk15to18:1.84`；不通过全局 Jetifier 忽略名单绕过依赖检查。

## 5. 实施任务与提交边界

### Task 1：威胁模型与数据范围

产出：资产、攻击者、故障、不承诺边界、Android 系统备份审计和逐对象白名单。

提交意图：`docs: 建立见域备份威胁模型`

### Task 2：Envelope、密码学和错误协议

产出：Argon2id Profile、AES-GCM Root Key 包装、Tink Streaming AEAD、Magic、Header、AAD、Record、Complete、版本、资源上限和错误码。

提交意图：`docs: 冻结备份格式与密钥生命周期`

### Task 3：测试先行和最小原型

测试覆盖：

- Header 非 Canonical、未知字段、重复 Key、超限长度；
- KDF Profile 在实际分配前校验；
- 错误密码、Header、Wrapped Key、Ciphertext 和 Tag 篡改；
- Manifest/Complete、顺序、截断、认证后尾随；
- API Key、绝对路径、Pending、Orphan、Purge 中 Issue 排除；
- Argon2id、HKDF、Root Key Wrap、Tink 多 Segment 和完整文件 SHA-256 向量。

最小原型只位于测试源集，不接入生产 Runtime。

提交意图：`test: 增加备份格式原型与公开测试向量`

### Task 4：审查、验收和交接

产出：PR09-13B 接口交接、独立安全审查清单、本地严格只读验收 Prompt、依赖和生产 APK 隔离门禁。

提交意图：`docs: 添加PR09-13B接口交接与验收门禁`

## 6. 真实验证顺序

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

还必须执行：

- Secret Scan；
- 公开测试向量逐字节复现；
- Tink 官方实现兼容解密；
- 测试依赖许可证和生产 APK 排除检查；
- 文档中的算法、Magic、错误码和版本交叉一致性检查；
- PR #52 建立的普通全量 Instrumentation 与外部进程恢复专项；
- 精确 Head GitHub CI；
- 本地严格只读验收；
- 独立安全审查。

## 7. 当前证据状态

已取得的历史诊断证据：

- `a075ff8bd2a4a70bc8dd12621cdd8ab99e64315d` 的 Secret Scan 通过；
- 同一 Head 的生产 `compileDebugKotlin` 通过；
- JVM 阶段在测试启动前因 `bcprov-jdk18on:1.84` 的 Jetifier major 69 兼容问题失败；
- 因 JVM 阶段失败，后续 Lint、Debug 和 Release 结果不得视为通过。

上述结果只用于根因分析，不能复用于修复后的新 Head。修复后的全部 CI、JVM 向量、本地验收和独立安全审查必须重新执行。

## 8. 完成声明门禁

只有同时取得以下新鲜证据，才可把安全审查结论提升为 `PASS` 或 `PASS_WITH_NOTES`：

- 精确最终 Head 的三项 GitHub CI 全绿；
- JVM 原型和公开向量真实执行通过；
- Lint、Debug、Release 和 AndroidTest APK 构建通过；
- 原型和 BC/Tink 测试依赖未进入生产 APK；
- 本地严格只读验收完成；
- 独立安全审查没有阻断问题；
- 工作区、Schema、Manifest 和生产 Runtime 无漂移。

在此之前统一使用：

```text
INSUFFICIENT_EVIDENCE
```
