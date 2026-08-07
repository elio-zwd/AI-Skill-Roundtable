# PR09-13A 本地严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 PR09-13A Draft PR 做严格只读验收。

仓库：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable
```

目标分支：

```text
security/pr-09-13a-backup-design
```

Base：

```text
40a106e66854c48efb008f434af1ed850128afdc
```

本轮只验收备份威胁模型、数据范围、Envelope、测试原型、公开向量、依赖隔离和 PR09-13B 交接，不实现生产导出、导入或恢复。

## 一、严格只读纪律

只允许：

```text
fetch
checkout
pull --ff-only
读取
构建
测试
安装 APK
受控 adb 测试
读取日志、JUnit XML、APK 内容和 GitHub CI
输出验收报告
```

禁止：

```text
修改文件
格式化或自动修复
生成并提交 Schema
提交、推送、变基、合并
修改 PR 状态
Ready 或启用自动合并
删除分支
降低断言或跳过失败
修改 Gradle、Manifest、Room、测试向量或文档
```

任何命令导致工作区变化时立即停止，保存变化清单，结论为 `FAIL_WORKSPACE_MUTATED`。

## 二、精确 Head 锁

从 Draft PR 描述读取唯一字段：

```text
最终验收锁定 Head：<64位十六进制 SHA>
```

该字段必须是完整 40 字符 Git Commit SHA，不接受短 SHA、分支名或本 Prompt 中的历史值。

执行：

```powershell
$repo = "elio-zwd/AI-Skill-Roundtable"
$branch = "security/pr-09-13a-backup-design"
$baseSha = "40a106e66854c48efb008f434af1ed850128afdc"
$expectedHead = "<从 Draft PR 描述复制完整 SHA>"

git fetch origin --prune
git checkout $branch
git pull --ff-only origin $branch

$localHead = (git rev-parse HEAD).Trim()
$remoteHead = (git rev-parse "origin/$branch").Trim()
$mergeBase = (git merge-base HEAD origin/main).Trim()

if ($expectedHead -notmatch '^[0-9a-f]{40}$') { throw "PR 描述未提供合法完整 Head" }
if ($localHead -ne $expectedHead) { throw "Local Head mismatch" }
if ($remoteHead -ne $expectedHead) { throw "Remote Head mismatch" }
if ($mergeBase -ne $baseSha) { throw "Merge base mismatch" }

git status --short
git diff --exit-code
git diff --cached --exit-code
```

必须记录：

- Draft PR 编号和状态；
- PR 描述锁定 Head；
- Local Head；
- Remote Head；
- Merge Base；
- `main` 当前 SHA；
- 五者是否符合验收要求。

不一致时停止，结论：

```text
BLOCKED_HEAD_NOT_LOCKED
```

## 三、环境记录

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

如有模拟器/设备，再记录：

```powershell
$device = "emulator-5554"
adb -s $device shell getprop ro.product.model
adb -s $device shell getprop ro.build.version.sdk
adb -s $device shell getprop ro.build.version.release
adb -s $device shell wm size
adb -s $device shell wm density
adb -s $device shell settings get system font_scale
```

## 四、差异范围

```powershell
git diff --name-status "$baseSha...HEAD"
git diff --stat "$baseSha...HEAD"
git diff --check "$baseSha...HEAD"
```

允许范围：

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
git diff --exit-code "$baseSha...HEAD" -- app/src/main/AndroidManifest.xml
git diff --exit-code "$baseSha...HEAD" -- app/src/main/res/xml/backup_rules.xml
git diff --exit-code "$baseSha...HEAD" -- app/src/main/res/xml/data_extraction_rules.xml
git diff --exit-code "$baseSha...HEAD" -- .github
```

生产路径、Schema、Manifest、备份 XML 或 CI 出现未授权差异时判定：

```text
FAIL_SCOPE_VIOLATION
```

## 五、文件和占位内容检查

```powershell
$changed = git diff --name-only "$baseSha...HEAD"
$changed | ForEach-Object { Write-Host $_ }

git grep -n -I -E "TODO|TBD|待定|之后决定|实现时选择|任选一种" -- `
  docs/security/pr-09-13a-backup-threat-model.md `
  docs/architecture/pr-09-13a-backup-envelope-spec.md `
  docs/architecture/pr-09-13a-backup-data-scope.md `
  docs/planning/pr-09-13a-interface-handoff.md
```

仓库历史文件可能存在 TODO；本门禁只检查本 PR 的冻结设计文档。出现影响 PR09-13B 施工的占位内容即 `FAIL`。

## 六、构建与 JVM 测试

按顺序执行并记录退出码：

```powershell
.\gradlew.bat --stop
.\gradlew.bat --no-daemon :app:compileDebugKotlin --stacktrace
.\gradlew.bat --no-daemon :app:testDebugUnitTest --stacktrace
.\gradlew.bat --no-daemon :app:lintDebug --stacktrace
.\gradlew.bat --no-daemon :app:assembleDebug --stacktrace
.\gradlew.bat --no-daemon :app:assembleRelease --stacktrace
.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest --stacktrace
```

任一命令非 0 即 `FAIL`。

单独运行备份原型测试：

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest `
  --tests "com.elio.jianyu.backup.design.*" `
  --stacktrace
```

记录：

- 测试总数；
- Passed/Failed/Skipped；
- 每个测试类耗时；
- Argon2 相关测试总耗时；
- 峰值进程内存，如本地工具可可靠记录；
- JUnit XML 路径。

## 七、公开测试向量复现

逐一检查：

```text
docs/testing/vectors/pr-09-13a/portable-empty.json
docs/testing/vectors/pr-09-13a/portable-unicode-record.json
docs/testing/vectors/pr-09-13a/streaming-multisegment.json
docs/testing/vectors/pr-09-13a/negative-vectors.json
```

必须证明：

- ASCII 和 Unicode/NFC 密码产生固定 KEK；
- Header、Wrap AAD、Wrapped Root Key、Streaming IKM 和 AAD 逐字节匹配；
- 完整密文 SHA-256 匹配；
- 参考实现可解密；
- Tink `AesGcmHkdfStreaming` 可解密多 Segment 向量；
- MANIFEST/ENTITY/COMPLETE Hash 和数量通过；
- 错误密码、Header/Key/Ciphertext/Tag 篡改、截断和附加失败；
- 未知版本、超限 Header 和未知 KDF Profile 在资源分配前拒绝；
- 认证后的 COMPLETE 尾随记录返回 `trailing_data`。

不能仅检查 JSON 字段存在；必须由测试代码实际计算。

## 八、依赖与生产 APK 隔离

检查 Gradle：

```powershell
git diff "$baseSha...HEAD" -- app/build.gradle.kts
```

只允许：

```text
testImplementation("org.bouncycastle:bcprov-jdk18on:1.84")
testImplementation("com.google.crypto.tink:tink-android:1.23.0")
```

禁止 `implementation`、`api`、`androidTestImplementation` 或生产 SourceSet 引用。

检查 APK：

```powershell
$debugApk = "app/build/outputs/apk/debug/app-debug.apk"
$releaseApk = Get-ChildItem app/build/outputs/apk/release -Filter "*.apk" | Select-Object -First 1

jar tf $debugApk | Select-String -Pattern "bouncycastle|crypto/tink|backup/design"
jar tf $releaseApk.FullName | Select-String -Pattern "bouncycastle|crypto/tink|backup/design"
```

预期无匹配。若 APK 使用 `classes.dex` 无法直接按类名判断，使用 `apkanalyzer` 或 `jadx` 只读检查：

```powershell
apkanalyzer dex packages $debugApk | Select-String -Pattern "org.bouncycastle|com.google.crypto.tink|com.elio.jianyu.backup.design"
apkanalyzer dex packages $releaseApk.FullName | Select-String -Pattern "org.bouncycastle|com.google.crypto.tink|com.elio.jianyu.backup.design"
```

生产 APK 包含原型或测试密码学依赖即 `FAIL`。

## 九、原型生产隔离

```powershell
git grep -n -I -E "BackupCryptoPrototype|BackupEnvelopePrototype|BackupRecordStreamPrototype|BackupDataScopePrototype|com\.elio\.jianyu\.backup\.design" -- app/src/main
```

预期无输出、退出码 1。

还要确认：

- `JianyuAppRuntime` 无引用；
- UI/Route/ViewModel 无引用；
- Worker/Coordinator 无引用；
- 没有生产导出按钮、SAF、导入或恢复代码；
- 没有正式 `.jybak/.jysnap` 用户流程；
- 固定 RNG 只存在测试夹具。

## 十、安全设计交叉一致性

逐份审阅：

```text
docs/security/pr-09-13a-backup-threat-model.md
docs/architecture/pr-09-13a-backup-envelope-spec.md
docs/architecture/pr-09-13a-backup-data-scope.md
docs/planning/pr-09-13a-interface-handoff.md
docs/testing/pr-09-13a-backup-security-review-checklist.md
```

检查以下值在全部文件一致：

```text
Argon2id：64 MiB / 3 / 1 / Salt 16 / Output 32
Envelope Version：1
Manifest Version：1
Header：4096 bytes
Record：1 MiB
Blob Chunk：256 KiB
Tink：AES256_GCM_HKDF_1MB
BC：1.84
Tink Android：1.23.0
Portable Magic：4A5959424B500D0A1A
Snapshot Magic：4A59534E500D0A1A
Snapshot Alias：jianyu_backup_snapshot_wrap_v1
```

任何冲突即 `FAIL`。

## 十一、Purge、Audio 和数据白名单静态审查

必须确认：

- 复用 PR09-12 Purge 状态，不建立第二套生命周期；
- `FAILED_RETRYABLE` 不作为完整 Issue；
- 已 Purge 数据不可复活；
- 降级 Relation 不保留来源标题或正文；
- 只从 AudioAsset 和 AudioFileStore 枚举正式音频；
- 不使用旧 `Message.audioFilePath`；
- `.part`、Orphan、Cache、绝对路径和外部原文件排除；
- API Key、Key 密文、Keystore、应用锁、Token 永久排除；
- Standalone Legacy ChatSession 不能被“全部数据”备份静默遗漏；
- 外部 URI 只保存可移植元数据。

## 十二、Android 系统备份边界

核对当前未修改文件：

```text
app/src/main/AndroidManifest.xml
app/src/main/res/xml/backup_rules.xml
app/src/main/res/xml/data_extraction_rules.xml
```

确认设计文档准确表述：

- `allowBackup=true`；
- Cloud/Device Transfer 当前只 include SharedPreferences；
- Room、`filesDir/jianyu-audio` 和 `noBackupFilesDir` 不在当前 include；
- API Key prefs 继续排除；
- 其他 SharedPreferences 的通配风险被记录；
- 本 PR 不修改系统备份配置；
- 正式发布前需要独立收紧门禁。

## 十三、Secret Scan

```powershell
git grep -n -I -E `
  "AIza[0-9A-Za-z_-]+|sk-[0-9A-Za-z_-]+|api[_-]?key\s*=|Authorization:\s*Bearer|BEGIN (RSA|OPENSSH|EC) PRIVATE KEY" `
  -- app/src/test/java/com/elio/jianyu/backup docs/security docs/architecture docs/planning docs/testing
```

固定测试密码必须明确是公开夹具；任何真实秘密或疑似生产 Token 即 `FAIL`。

检查向量中不存在：

- 真实用户标题/正文；
- 真实邮箱、电话、地址；
- 真实 API Key；
- 仓库 Token；
- 设备 ID；
- 私钥。

## 十四、Schema 与身份门禁

```powershell
pwsh -NoProfile -File tools/check-app-identity.ps1
git diff --exit-code "$baseSha...HEAD" -- app/schemas
git diff --exit-code "$baseSha...HEAD" -- app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
```

必须无 Schema、Room Version、Migration 或生产数据库变化。

## 十五、设备 Instrumentation 回归

若存在 API 28 模拟器，运行普通全量：

```powershell
.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest --stacktrace
```

要求：

- 套件完整结束；
- PR #52 约定的 External Process 两项只以明确 Assume 跳过；
- 其他 Failure/Error 为 0；
- 不出现 Room v12、Lifecycle、Skill v2 回归。

再按 PR #52 的既有 Prompt 使用一次安装和直接 `adb shell am instrument` 执行外部进程恢复 step1/step2。两阶段之间禁止重新安装、清数据或运行 connected task。

本 PR 没有 Android 生产原型，因此不增加 Keystore Instrumentation；这不等于 PR09-13B 的 Keystore 行为已验证。

## 十六、性能记录

在当前环境记录：

- Argon2id Profile 1 单次和多次中位耗时；
- JVM 峰值内存；
- 公开 9000-byte 多 Segment 向量耗时；
- 原型测试总耗时。

这些数据只能标为当前桌面/JVM或模拟器证据，不能声称代表低端和中端 Android 真机。

PR09-13B 真机门禁仍需覆盖：

```text
低端/受限设备
常见中端设备
50 MB
500 MB
取消响应
R8 后 APK 增量
```

## 十七、GitHub CI

检查精确 `$expectedHead` 对应：

```text
Secret scan
Android UI Test Compile
Android CI
```

记录：

- Workflow 名称；
- Run ID；
- 绑定 SHA；
- 状态；
- 结论；
- 失败 Job 和关键日志。

旧 Head 的绿色结果不能复用。

## 十八、终检

```powershell
git status --short
git diff --exit-code
git diff --cached --exit-code
git rev-parse HEAD
git rev-parse "origin/$branch"
```

工作区必须干净，Head 仍与 PR 描述锁定值一致。

## 十九、判定

### PASS

仅当以下全部满足：

- Head 锁定；
- 范围正确；
- JVM、向量、Lint、Debug、Release、AndroidTest APK 全部通过；
- 精确 Head CI 全绿；
- 原型未进入生产 APK；
- Secret Scan 通过；
- Schema 无漂移；
- 威胁模型、Envelope、数据范围和交接无核心歧义；
- 独立安全审查没有阻断问题；
- 工作区干净。

### PASS_WITH_NOTES

只允许以下非核心保留项：

- 低端/中端 Android 真机性能仍待 PR09-13B 实测；
- OEM SAF Provider 能力仍待生产流程验证；
- 生产依赖 R8 后 APK 增量仍待 PR09-13B 实测。

不得用 Notes 掩盖测试失败、算法歧义、认证缺陷、数据遗漏或生产隔离失败。

### FAIL

任一核心安全、格式、范围、构建、测试、CI 或隔离门禁失败。

### INSUFFICIENT_EVIDENCE

缺少精确 Head、CI、本地命令输出、向量真实复现或独立安全审查证据。

## 二十、报告格式

```markdown
# PR09-13A 本地严格只读验收报告

## 1. 最终结论
PASS / PASS_WITH_NOTES / FAIL / INSUFFICIENT_EVIDENCE

## 2. 精确目标
- PR：
- Base：
- Branch：
- Expected Head：
- Local/Remote Head：
- Merge Base：

## 3. 环境
- OS：
- PowerShell：
- Git：
- JDK/Javac：
- Gradle：
- adb/设备：

## 4. 差异范围
- Changed Files：
- 生产路径差异：
- Schema 差异：

## 5. 构建与测试
| 命令 | 结果 | 数量/耗时 | 证据 |
|---|---|---|---|

## 6. 测试向量
- Portable Empty：
- Unicode：
- Multi-Segment/Tink：
- Negative：

## 7. 安全审查
- KDF：
- AEAD/Nonce：
- Envelope：
- Record/Complete：
- Data Scope：
- Purge/Audio：
- Android Auto Backup：

## 8. 依赖与 APK 隔离
- BC/Tink Scope：
- Debug APK：
- Release APK：
- License：

## 9. CI
- Run：
- SHA：
- 结论：

## 10. 未验证项
- 真机性能：
- OEM SAF：
- 生产 APK 增量：

## 11. PR09-13B 启动建议
允许 / 不允许，并说明原因。

## 12. 终检
- git status：
- Head 三权：
```
