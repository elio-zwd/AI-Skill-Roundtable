# PR09-13A 本地严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 PR09-13A Draft PR 做严格只读验收。

```text
仓库：https://github.com/elio-zwd/AI-Skill-Roundtable
分支：security/pr-09-13a-backup-design
Base：40a106e66854c48efb008f434af1ed850128afdc
```

本轮只验收备份威胁模型、数据范围、Envelope、测试原型、公开向量、依赖隔离和 PR09-13B 交接，不实现生产导出、导入或恢复。

## 一、只读纪律

只允许：fetch、checkout、pull --ff-only、读取、构建、测试、安装 APK、受控 adb、读取日志/JUnit/APK/CI 和输出报告。

禁止：修改、格式化、自动修复、生成并提交 Schema、提交、推送、变基、合并、修改 PR 状态、Ready、自动合并、删除分支、降低断言或跳过失败。

任何命令导致工作区变化时立即停止，结论为：

```text
FAIL_WORKSPACE_MUTATED
```

## 二、精确 Head 锁

从 Draft PR 描述读取：

```text
最终验收锁定 Head：<完整40字符SHA>
```

执行：

```powershell
$branch = "security/pr-09-13a-backup-design"
$baseSha = "40a106e66854c48efb008f434af1ed850128afdc"
$expectedHead = "<从 Draft PR 描述复制>"

git fetch origin --prune
git checkout $branch
git pull --ff-only origin $branch

$localHead = (git rev-parse HEAD).Trim()
$remoteHead = (git rev-parse "origin/$branch").Trim()
$mergeBase = (git merge-base HEAD origin/main).Trim()

if ($expectedHead -notmatch '^[0-9a-f]{40}$') { throw "非法 Head" }
if ($localHead -ne $expectedHead) { throw "Local Head mismatch" }
if ($remoteHead -ne $expectedHead) { throw "Remote Head mismatch" }
if ($mergeBase -ne $baseSha) { throw "Merge Base mismatch" }

git status --short
git diff --exit-code
git diff --cached --exit-code
```

记录 Draft PR 编号/状态、Expected/Local/Remote Head、Merge Base 和当前 `main`。任一不一致：

```text
BLOCKED_HEAD_NOT_LOCKED
```

## 三、环境

```powershell
Get-CimInstance Win32_OperatingSystem |
  Select-Object Caption,Version,BuildNumber,OSArchitecture
$PSVersionTable.PSVersion
git --version
java -version
javac -version
.\gradlew.bat --version
adb version
adb devices -l
Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
```

有设备时记录型号、API、分辨率、密度和字号。

## 四、差异范围

```powershell
git diff --name-status "$baseSha...HEAD"
git diff --stat "$baseSha...HEAD"
git diff --check "$baseSha...HEAD"
```

只允许：

```text
app/build.gradle.kts
app/src/test/java/com/elio/jianyu/backup/design/**
docs/security/pr-09-13a-backup-threat-model.md
docs/architecture/pr-09-13a-backup-envelope-spec.md
docs/architecture/pr-09-13a-backup-data-scope.md
docs/planning/pr-09-13a-backup-security-plan.md
docs/planning/pr-09-13a-interface-handoff.md
docs/testing/pr-09-13a-backup-security-review-checklist.md
docs/testing/pr-09-13a-local-readonly-acceptance-prompt.md
docs/testing/vectors/pr-09-13a/**
```

强制零差异：

```powershell
git diff --exit-code "$baseSha...HEAD" -- app/src/main
git diff --exit-code "$baseSha...HEAD" -- app/src/androidTest
git diff --exit-code "$baseSha...HEAD" -- app/schemas
git diff --exit-code "$baseSha...HEAD" -- .github
git diff --exit-code "$baseSha...HEAD" -- app/src/main/AndroidManifest.xml
git diff --exit-code "$baseSha...HEAD" -- app/src/main/res/xml/backup_rules.xml
git diff --exit-code "$baseSha...HEAD" -- app/src/main/res/xml/data_extraction_rules.xml
```

越界即 `FAIL_SCOPE_VIOLATION`。

## 五、冻结文档审阅

逐份审阅：

```text
docs/security/pr-09-13a-backup-threat-model.md
docs/architecture/pr-09-13a-backup-envelope-spec.md
docs/architecture/pr-09-13a-backup-data-scope.md
docs/planning/pr-09-13a-interface-handoff.md
docs/testing/pr-09-13a-backup-security-review-checklist.md
```

检查无影响施工的占位词：

```powershell
git grep -n -I -E "TODO|TBD|待定|之后决定|实现时选择|任选一种" -- `
  docs/security/pr-09-13a-backup-threat-model.md `
  docs/architecture/pr-09-13a-backup-envelope-spec.md `
  docs/architecture/pr-09-13a-backup-data-scope.md `
  docs/planning/pr-09-13a-interface-handoff.md
```

以下值必须一致：

```text
Argon2id：0x13 / 65,536 KiB / 3 / 1 / Salt 16 / Output 32
Envelope Version：1
Manifest Version：1
Header：4,096 bytes
Record：1,048,576 bytes
Blob Chunk：262,144 bytes
Tink：AES256_GCM_HKDF_1MB / 1.23.0
Bouncy Castle：bcprov-jdk15to18:1.84
Portable Magic：4A5959424B500D0A1A
Snapshot Magic：4A59534E500D0A1A
Snapshot Alias：jianyu_backup_snapshot_wrap_v1
```

`bcprov-jdk18on:1.84` 只能出现在“已被 CI 否决的候选/历史根因”说明中，不得作为当前测试或生产依赖。

## 六、构建和 JVM

```powershell
.\gradlew.bat --stop
.\gradlew.bat --no-daemon :app:compileDebugKotlin --stacktrace
.\gradlew.bat --no-daemon :app:testDebugUnitTest --stacktrace
.\gradlew.bat --no-daemon :app:lintDebug --stacktrace
.\gradlew.bat --no-daemon :app:assembleDebug --stacktrace
.\gradlew.bat --no-daemon :app:assembleRelease --stacktrace
.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

再运行专项：

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest `
  --tests "com.elio.jianyu.backup.design.*" `
  --stacktrace
```

任一非 0 即 `FAIL`。记录测试数、Passed/Failed/Skipped、各类耗时、Argon2 总耗时、JUnit XML 和可可靠获取的峰值内存。

## 七、公开向量

检查并由代码实际复现：

```text
docs/testing/vectors/pr-09-13a/portable-empty.json
docs/testing/vectors/pr-09-13a/portable-unicode-record.json
docs/testing/vectors/pr-09-13a/streaming-multisegment.json
docs/testing/vectors/pr-09-13a/negative-vectors.json
```

必须证明：

- ASCII、Unicode/NFC 密码产生固定 KEK；
- Header、Wrap AAD、Wrapped Root Key、Streaming IKM/AAD 逐字节匹配；
- 完整文件 SHA-256 匹配；
- 参考实现和 Tink `AesGcmHkdfStreaming` 可解密对应向量；
- MANIFEST/ENTITY/COMPLETE 数量、Hash 和 `total_plaintext_bytes_before_complete` 正确；
- 错误密码和 Header/Key/Stream/Ciphertext/Tag 篡改统一认证失败；
- 截断、原始密文附加、认证后尾随、未知版本、超限 Header、未知 KDF Profile、非法路径和超大声明失败关闭。

不能只检查 JSON 字段存在。

## 八、依赖和 APK 隔离

Gradle 只允许：

```text
testImplementation("org.bouncycastle:bcprov-jdk15to18:1.84")
testImplementation("com.google.crypto.tink:tink-android:1.23.0")
```

执行：

```powershell
git grep -n -I "bcprov-jdk18on:1.84" -- app/build.gradle.kts app/src/test
```

预期无当前依赖命中。禁止 `implementation`、`api`、`androidTestImplementation` 或生产 SourceSet 引用测试密码学依赖。

检查生产 APK：

```powershell
$debugApk = "app/build/outputs/apk/debug/app-debug.apk"
$releaseApk = Get-ChildItem app/build/outputs/apk/release -Filter "*.apk" | Select-Object -First 1

apkanalyzer dex packages $debugApk |
  Select-String "org.bouncycastle|com.google.crypto.tink|com.elio.jianyu.backup.design"
apkanalyzer dex packages $releaseApk.FullName |
  Select-String "org.bouncycastle|com.google.crypto.tink|com.elio.jianyu.backup.design"
```

预期无匹配。若 `apkanalyzer` 不可用，用 `jadx` 或等价只读工具；不能仅凭 Gradle Scope 推断 APK 排除成功。

## 九、原型生产隔离

```powershell
git grep -n -I -E `
  "BackupCryptoPrototype|BackupEnvelopePrototype|BackupRecordStreamPrototype|BackupDataScopePrototype|com\.elio\.jianyu\.backup\.design" `
  -- app/src/main
```

预期无输出。还要确认 `JianyuAppRuntime`、UI、ViewModel、Worker 和 Coordinator 无引用，没有生产按钮、SAF、导入、恢复或正式用户文件流程，固定 RNG 只存在测试源集。

## 十、数据、Purge 和 Audio

确认：

- 使用逐对象白名单，不复制 App 目录；
- API Key、Key 密文、Keystore、应用锁、Token、绝对路径、Cache、Pending、Orphan、`.part` 和 Purged 数据永久排除；
- `FAILED_RETRYABLE` Purge Issue 不进入完整备份；
- 备份开始和提交前双检查 Source Token；
- 降级 Relation 不保留来源标题或正文；
- 只从正式 AudioAsset + AudioFileStore 枚举 AVAILABLE 文件；
- 不使用旧 `Message.audioFilePath`；
- Standalone Legacy ChatSession 不能被“全部数据”备份静默遗漏；
- 外部 URI 只保存可移植元数据，不复制原文件或 Grant。

## 十一、Android 系统备份

核对未修改的：

```text
app/src/main/AndroidManifest.xml
app/src/main/res/xml/backup_rules.xml
app/src/main/res/xml/data_extraction_rules.xml
```

设计文档必须准确说明：`allowBackup=true`；Cloud/Device Transfer 当前只 include SharedPreferences；Room、`filesDir/jianyu-audio` 和 `noBackupFilesDir` 不在当前 include；API Key prefs 排除；其他 SharedPreferences 通配风险已记录；正式发布前另建收紧 PR。

## 十二、Secret 和 Schema

```powershell
git grep -n -I -E `
  "AIza[0-9A-Za-z_-]+|sk-[0-9A-Za-z_-]+|api[_-]?key\s*=|Authorization:\s*Bearer|BEGIN (RSA|OPENSSH|EC) PRIVATE KEY" `
  -- app/src/test/java/com/elio/jianyu/backup docs/security docs/architecture docs/planning docs/testing

pwsh -NoProfile -File tools/check-app-identity.ps1
git diff --exit-code "$baseSha...HEAD" -- app/schemas
git diff --exit-code "$baseSha...HEAD" -- app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
```

固定测试密码必须明确是公开夹具。任何真实秘密、Schema、Room Version 或 Migration 变化即 `FAIL`。

## 十三、设备回归

有 API 28 模拟器时执行普通全量：

```powershell
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest --stacktrace
```

要求套件完整结束；PR #52 约定的 External Process 两项只按明确 Assume 跳过；其他 Failure/Error 为 0。

再按 PR #52 的既有 Prompt，以一次安装和直接 `adb shell am instrument` 执行外部进程恢复 step1/step2；两阶段之间禁止重装、清数据或运行 connected task。

PR09-13A 没有 Android 生产 Keystore 原型，因此不新增 Keystore Instrumentation；这不代表 PR09-13B 的设备密钥行为已验证。

## 十四、性能记录

记录当前环境的 Argon2id Profile 1 中位耗时、JVM 峰值内存、多 Segment 向量耗时和原型测试总耗时。桌面/模拟器数据不得表述为低端或中端真机结论。

PR09-13B 仍须真机验证：低端/受限设备、中端设备、50 MB、500 MB、取消响应和 R8 后 APK 增量。

## 十五、GitHub CI

只接受 `$expectedHead` 对应的：

```text
Secret scan
Android UI Test Compile
Android CI
```

记录 Workflow、Run ID、Job、SHA、状态、结论和失败日志。旧 Head 结果不得复用。

## 十六、终检

```powershell
git status --short
git diff --exit-code
git diff --cached --exit-code
git rev-parse HEAD
git rev-parse "origin/$branch"
```

工作区必须干净，Head 三权一致。

## 十七、判定

### PASS

必须同时满足：Head 锁定、范围正确、JVM/向量/Lint/Debug/Release/AndroidTest APK/设备门禁通过、最终 Head CI 全绿、生产 APK 无原型依赖、Secret/Schema 通过、独立安全审查无阻断、工作区干净。

### PASS_WITH_NOTES

只允许保留：PR09-13B 的低端/中端真机性能、OEM SAF 能力和生产 R8 后 APK 增量。不能用 Notes 掩盖测试失败、格式歧义、认证缺陷、数据遗漏或隔离失败。

### FAIL

任一核心安全、格式、范围、构建、测试、CI 或隔离门禁失败。

### INSUFFICIENT_EVIDENCE

缺少精确 Head、CI、本地输出、向量真实复现或独立安全审查证据。

## 十八、报告格式

```markdown
# PR09-13A 本地严格只读验收报告

## 1. 最终结论
PASS / PASS_WITH_NOTES / FAIL / INSUFFICIENT_EVIDENCE

## 2. 精确目标
- PR：
- Base：
- Branch：
- Expected/Local/Remote Head：
- Merge Base：

## 3. 环境
- OS / PowerShell / Git / JDK / Gradle / adb / 设备：

## 4. 差异范围
- Changed Files：
- 生产、Schema、Manifest/XML 差异：

## 5. 构建与测试
| 命令 | 结果 | 数量/耗时 | 证据 |
|---|---|---|---|

## 6. 测试向量
- Empty / Unicode / Multi-Segment / Negative：

## 7. 安全审查
- KDF / AEAD / Envelope / Record / Scope / Purge / Audio / Auto Backup：

## 8. 依赖与 APK 隔离
- BC/Tink Scope：
- Debug/Release APK：
- License：

## 9. CI
- Run / SHA / 结论：

## 10. 未验证项
- 真机性能 / OEM SAF / 生产 APK 增量：

## 11. PR09-13B 启动建议
允许 / 不允许，并说明原因。

## 12. 终检
- git status：
- Head 三权：
```
