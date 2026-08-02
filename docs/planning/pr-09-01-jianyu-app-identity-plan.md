# PR09-01：见域应用身份迁移实施计划

> **执行方式：** 本项目不使用 `subagent-driven-development`。计划获批后，按仓库适配规则使用 `executing-plans` 的等价人工流程串行实施；每个阶段完成后重新核对分支 Head、差异和验证证据。
>
> **计划状态：** 只读设计与文件级施工单。本文提交不修改 Android 生产代码、测试、资源、构建配置或 CI。

**目标：** 将 Android 应用从 `AI 智囊圆桌 / com.elio.skillroundtable` 迁移为 `见域 / com.elio.jianyu`，保持现有功能和 Room v5 语义等价，同时建立与旧包完全隔离的新应用沙箱。

**架构：** 采用完整身份迁移，而不是只改 `applicationId`。Gradle `namespace`、`applicationId`、Kotlin package、三类源码目录、Manifest 合并结果、Room Schema 输出、CI 和运行脚本统一切换到 `com.elio.jianyu`；旧包数据库、偏好、文件和 Android Keystore 不设计迁移桥。历史 Room Schema 原目录保留，新 FQCN Schema 由 Room/KSP 真实生成并验证内容等价。

**技术栈：** Kotlin 2.0.21、Android Gradle Plugin 现有版本、Gradle Wrapper 8.14、JDK 17、Jetpack Compose、Room v5、KSP、JUnit 4、AndroidX Test、API 30 Emulator、PowerShell 7、GitHub Actions。

## 全局约束

- Base 分支：`main`
- Base SHA：`4de0bfb0480ea84d3a88af12c11167a3a27c38dc`
- 开发分支：`refactor/pr-09-01-jianyu-app-identity`
- 目标 PR 标题：`refactor: 迁移见域应用身份与包路径`
- 目标 App 名称：`见域`
- 目标 `applicationId`：`com.elio.jianyu`
- 目标 `namespace`：`com.elio.jianyu`
- 旧包 `com.elio.skillroundtable` 的 Room、SharedPreferences、私有文件、`noBackupFilesDir` 和 Android Keystore Key 均不迁移。
- 新包 API Key 必须重新配置。
- 保持 Room `version = 5`，不修改 Entity、DAO、表、字段、索引、Migration 或数据库文件名。
- 保持 `EncryptedApiKeyStore` 的算法、格式、文件名和 Key alias 不变；隔离由 Android UID / 应用沙箱提供。
- 不落地正式 Logo、App Icon、完整主题或最终品牌视觉；现有临时图标继续使用。
- 不升级 Gradle、AGP、Kotlin、Room、Compose 或其他依赖。
- 不处理 Gradle 9 弃用警告，不进行无关重构或全库格式化。
- 未实际执行的命令不得写成通过；本地、模拟器和 GitHub CI 证据分开记录。
- 未经用户明确授权，不标记 Ready、不合并、不删除分支。

---

## 一、已确认的工程事实

1. `app/build.gradle.kts` 同时硬编码旧 `namespace` 和旧 `applicationId`。
2. 主 Manifest 使用相对 Activity 名 `.MainActivity`，没有仓库自定义 Provider 或硬编码 authority；仍需通过 merged manifest 审计 WorkManager 等依赖生成的 Provider authority。
3. `@string/app_name` 当前为 `AI 智囊圆桌`；本 PR 只把该应用身份名称改为 `见域`，不改其他旧产品文案。
4. Room 通过 `room.schemaLocation = app/schemas` 输出 Schema；类 FQCN 变化会生成：
   `app/schemas/com.elio.jianyu.data.RoundtableDatabase/5.json`。
5. 当前数据库名仍是 `roundtable_database`。由于 Android 数据目录按包/UID 隔离，不需要改数据库名。
6. 当前 Key alias 为 `skill_roundtable_api_key_v1`，密文文件为 `gemini_api_keys.enc`。Android Keystore 和 `noBackupFilesDir` 均按应用 UID 隔离，不通过改 alias 或格式制造隔离。
7. `.github/workflows/android-ci.yml` 硬编码旧包、旧 Launcher Activity 和旧 Schema 路径，必须同步迁移。
8. `run.ps1` 硬编码旧包和旧 Activity，必须同步迁移。
9. `UiArchitectureGuardrailTest` 硬编码源码包根和 import 正则，必须同步迁移。
10. 当前备份规则排除 API Key 和遥测敏感偏好；包名变化后 Android 不会把旧包备份恢复到新包，本 PR不修改备份格式。

## 二、方案比较与选择

### 方案 A：完整身份与源码包迁移（采用）

- 同步迁移 `applicationId`、`namespace`、Kotlin package 和目录。
- 新旧 App 可并存，所有私有数据天然隔离。
- 后续 PR09 不需要长期兼容包或混合 import。
- 代价是本 PR 文件移动较多，需要严格的目录清单、旧引用审计和构建验证。

### 方案 B：只改 `applicationId`，保留旧 namespace/package（拒绝）

- 可获得新沙箱，但代码仍长期使用旧产品标识。
- 与已冻结目标 `namespace = com.elio.jianyu` 冲突。
- 后续每个 PR 都要处理新旧标识并存，增加错误和回滚成本。

### 方案 C：新包读取或导入旧包数据（拒绝）

- 违反“不迁移旧包数据和 Key”的冻结边界。
- 需要跨包导出、签名、权限或数据桥，扩大安全面。
- 也会把应用身份迁移与数据库/加密协议修改耦合在同一 PR。

---

## 三、文件地图

### 3.1 本计划阶段创建

- `docs/planning/pr-09-01-jianyu-app-identity-plan.md`：本文件级施工单。

### 3.2 实施阶段新建

- `app/src/androidTest/java/com/elio/skillroundtable/identity/AppIdentityIsolationTest.kt`
  - 首个 RED 测试文件；在目录迁移阶段移动为：
    `app/src/androidTest/java/com/elio/jianyu/identity/AppIdentityIsolationTest.kt`。
- `tools/check-app-identity.ps1`
  - 静态身份门禁：检查 Gradle、源码目录、Manifest、App 名称、CI、脚本、Schema 和旧包残留允许清单。
- `tools/verify-app-coexistence.ps1`
  - 双 APK / 双包只读验收脚本；不修改仓库文件，不自动卸载旧包。
- `app/schemas/com.elio.jianyu.data.RoundtableDatabase/5.json`
  - 由迁移后 Room/KSP 真实生成，不手工编造。
- `docs/testing/pr-09-01-app-identity-acceptance.md`
  - 记录新旧包并存、首次状态、数据隔离、Key 重新配置和 APK 包名检查步骤。

### 3.3 目录级原子移动

以下每一条都使用 `git mv`；源目录最终必须不存在，目标目录中的每个 Kotlin 文件同步修改 `package` 和 import。执行前先把 `git ls-files` 输出保存到验收日志，作为逐文件清单；任何源目录下未进入目标目录的已跟踪文件都阻塞提交。

- `app/src/main/java/com/elio/skillroundtable/`
  → `app/src/main/java/com/elio/jianyu/`
- `app/src/test/java/com/elio/skillroundtable/`
  → `app/src/test/java/com/elio/jianyu/`
- `app/src/androidTest/java/com/elio/skillroundtable/`
  → `app/src/androidTest/java/com/elio/jianyu/`

目录移动范围包括但不限于：

- `MainActivity.kt`
- `audio/**`
- `data/**`
- `network/**`
- `roundtable/**`
- `skill/**`
- `telemetry/**`
- `ui/**`
- `viewmodel/**`
- `ui/AGENTS.md`
- 全部单元测试和 AndroidTest。

### 3.4 直接修改

- `app/build.gradle.kts`
  - `namespace`、`applicationId` 改为 `com.elio.jianyu`；其余 SDK、版本、签名和依赖保持不变。
- `app/src/main/res/values/strings.xml`
  - 只把 `app_name` 改为 `见域`。
- `.github/workflows/android-ci.yml`
  - 更新目标包、Launcher Activity、Schema 路径；新增旧包活动引用审计和新旧 Schema 等价检查；保留现有构建、Lint、签名和 Emulator 作业。
- `run.ps1`
  - 启动包和 Activity 改为 `com.elio.jianyu` / `com.elio.jianyu.MainActivity`；标题文案改为“见域”。
- `README.md`
  - 把当前工程事实更新为“见域 / com.elio.jianyu”，明确旧包数据和 Key 不迁移；历史规划链接不改写。
- `AGENTS.md`
  - 迁移完成后把“当前实现事实”更新为新身份，保留旧包不迁移边界和历史说明。
- `docs/environment/package-and-branding.md`
  - 新增本次从 `com.elio.skillroundtable` 到 `com.elio.jianyu` 的身份迁移、并存与数据隔离说明；旧的示例包迁移作为历史背景保留并明确已过时。
- `docs/environment/room-migrations.md`
  - 当前 Schema 路径改为新 FQCN；说明旧 FQCN `5.json` 仅作历史基线保留，数据库语义和版本未变。
- `app/src/test/java/com/elio/jianyu/ui/UiArchitectureGuardrailTest.kt`
  - 更新包根、源目录探测和 import 正则。
- 所有移动后的 `*.kt`
  - `package com.elio.skillroundtable...` → `package com.elio.jianyu...`
  - `import com.elio.skillroundtable...` → `import com.elio.jianyu...`
  - 全限定调用同步迁移；业务代码、类型名和方法签名不改。
- `app/src/main/java/com/elio/jianyu/ui/AGENTS.md`
  - 仅更新适用目录路径，不改 UI 分层规则。

### 3.5 只读检查，不预设修改

- `app/src/main/AndroidManifest.xml`
  - 相对 Activity 和资源引用可继续使用；除非 merged manifest 检查发现实际旧包硬编码，否则不制造无意义差异。
- `app/proguard-rules.pro`
  - 当前无包路径规则；只通过 `assembleRelease` / R8 验证，不修改。
- `app/src/main/res/xml/backup_rules.xml`
- `app/src/main/res/xml/data_extraction_rules.xml`
  - 继续排除敏感偏好；不改变备份策略。
- 根 `build.gradle.kts`、`settings.gradle.kts`、`gradle.properties`、版本目录和 Wrapper
  - 不涉及身份，不修改。

### 3.6 保留，不删除

- `app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json`
  - 作为旧 FQCN 历史基线保留，禁止删除或改写。
- 历史 PR08 / PR09 规划文档中明确描述“旧包”的字符串
  - 作为决策证据保留。

### 3.7 删除

只删除因 `git mv` 变为空的旧包目录：

- `app/src/main/java/com/elio/skillroundtable/`
- `app/src/test/java/com/elio/skillroundtable/`
- `app/src/androidTest/java/com/elio/skillroundtable/`

不删除任何 Kotlin 业务文件、测试、历史 Schema 或历史文档。

---

## 四、旧包字符串允许清单

`com.elio.skillroundtable` 在活动工程中默认禁止。允许保留的情况必须同时满足“路径允许 + 语义明确”：

1. `app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json`
   - 旧 FQCN 历史 Schema 路径，文件内容不可修改。
2. `docs/planning/pr-09-01-jianyu-app-identity-plan.md`
3. `docs/testing/pr-09-01-app-identity-acceptance.md`
4. `docs/environment/package-and-branding.md`
5. `docs/environment/room-migrations.md`
6. `tools/check-app-identity.ps1`
7. `tools/verify-app-coexistence.ps1`
   - 上述文件需要引用旧包以描述审计、双包测试或历史边界。
8. PR08-F 及更早的历史规划、决策和迁移评估文档
   - 只在明确描述旧实现、历史基线或“不迁移”时保留。

以下活动范围必须零残留：

```text
app/src/main/java/com/elio/jianyu/**
app/src/test/java/com/elio/jianyu/**
app/src/androidTest/java/com/elio/jianyu/**
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/res/values/strings.xml
.github/workflows/android-ci.yml（双包验收常量除外）
run.ps1
README.md 的当前状态段落
AGENTS.md 的当前工程事实段落
```

---

## 五、测试驱动实施任务

### Task 1：建立会失败的应用身份与空沙箱测试

**文件：**

- 创建：`app/src/androidTest/java/com/elio/skillroundtable/identity/AppIdentityIsolationTest.kt`
- 创建：`tools/check-app-identity.ps1`

**接口：**

- 消费：当前 `InstrumentationRegistry.targetContext`、`RoundtableDatabase`、`EncryptedApiKeyStore`。
- 产出：新包身份、私有目录、首次用户状态和旧引用的可重复门禁。

- [ ] **Step 1：编写首个失败测试**

测试至少包含：

```kotlin
@Test
fun targetPackage_usesJianyuApplicationId() {
    assertEquals("com.elio.jianyu", context.packageName)
}

@Test
fun privateDirectories_areScopedToJianyuSandbox() {
    val expectedSegment = "com.elio.jianyu"
    listOf(context.dataDir, context.filesDir, context.cacheDir, context.noBackupFilesDir)
        .forEach { directory ->
            assertTrue(directory.canonicalPath.contains(expectedSegment))
            assertFalse(directory.canonicalPath.contains("com.elio.skillroundtable"))
        }
}

@Test
fun freshInstall_hasNoUserSessionsOrApiKeys() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    try {
        val database = RoundtableDatabase.getDatabase(context, scope)
        assertTrue(database.chatDao().getAllSessions().first().isEmpty())
        assertTrue(EncryptedApiKeyStore(context).read().isEmpty())
        assertFalse(File(context.noBackupFilesDir, "gemini_api_keys.enc").exists())
    } finally {
        scope.cancel()
    }
}
```

“空状态”只约束用户会话、消息、偏好、文件和 Key；允许数据库创建表并播种官方预置角色/分组。

- [ ] **Step 2：运行 RED**

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.skillroundtable.identity.AppIdentityIsolationTest
```

预期：`targetPackage_usesJianyuApplicationId` 失败，实际包名为 `com.elio.skillroundtable`。不得接受“测试类不存在”“编译失败”作为有效 RED。

- [ ] **Step 3：运行静态门禁 RED**

```powershell
pwsh.exe -NoProfile -File .\tools\check-app-identity.ps1
```

预期：明确报告旧 `namespace`、旧 `applicationId`、旧源码根、旧 CI / 脚本引用和缺失新 Schema，而不是无分类失败。

- [ ] **Step 4：提交测试门禁**

```text
test: 增加新旧应用身份隔离测试
```

该提交只允许加入测试和检查脚本，不改生产代码。

### Task 2：迁移 Gradle 身份、源码目录和 Kotlin package

**文件：**

- 修改：`app/build.gradle.kts`
- 移动：三类 `com/elio/skillroundtable/` 源码目录到 `com/elio/jianyu/`
- 修改：所有移动后的 Kotlin package/import
- 修改：移动后的 `ui/AGENTS.md`

**接口：**

- 产出：`com.elio.jianyu.BuildConfig`、`com.elio.jianyu.MainActivity`、新包下完全等价的业务类型。
- 不改变：类名、公共方法、Room Entity/DAO/Database 结构、导航 Route、testTag 和运行状态机。

- [ ] 执行前记录三个源目录的完整已跟踪文件清单和数量。
- [ ] 使用 `git mv` 完成目录迁移，不复制后删除。
- [ ] 仅替换 Kotlin `package`、`import` 和真实全限定引用。
- [ ] 运行旧路径审计，确认三个旧目录不存在。
- [ ] 运行：

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
```

预期：编译和单测通过；失败时先修复遗漏 import / 路径，不顺带重构业务。

- [ ] 提交：

```text
refactor: 迁移见域应用包名与源码路径
```

### Task 3：同步 App 名称、运行脚本与活动文档

**文件：**

- 修改：`app/src/main/res/values/strings.xml`
- 修改：`run.ps1`
- 修改：`README.md`
- 修改：`AGENTS.md`
- 修改：`docs/environment/package-and-branding.md`
- 修改：`docs/environment/room-migrations.md`

- [ ] `app_name` 改为 `见域`，不修改图标、主题和其他产品文案。
- [ ] `run.ps1` 使用新包和新 Activity；保留现有 JDK、ADB、安装和日志逻辑。
- [ ] 当前状态文档改为新身份；历史说明明确标注旧包。
- [ ] 执行：

```powershell
pwsh.exe -NoProfile -File .\tools\check-app-identity.ps1
.\run.ps1 -SkipInstall
```

- [ ] 提交：

```text
docs: 更新见域应用身份与本地运行说明
```

若实现后发现文档与脚本必须和构建配置一起通过同一门禁，可将本任务与 Task 4 合并为一个原子 `build` Commit，但不得把业务修改混入。

### Task 4：生成新 Room Schema 并证明语义未变

**文件：**

- 保留且不改：`app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json`
- 生成：`app/schemas/com.elio.jianyu.data.RoundtableDatabase/5.json`
- 修改：`app/src/androidTest/java/com/elio/jianyu/data/RoundtableDatabaseMigrationTest.kt` 的 package/import（目录迁移已完成），迁移断言本身不降低。

- [ ] 删除本地构建输出，不删除已提交 Schema。
- [ ] 运行：

```powershell
.\gradlew.bat compileDebugKotlin
```

- [ ] 确认 KSP 真实生成新 FQCN `5.json`。
- [ ] 比较旧、新 `5.json`：文件内容应字节一致；至少必须保证 `formatVersion`、数据库 `version`、`identityHash`、Entity、字段、索引和 setup queries 完全一致。
- [ ] 确认 `RoundtableDatabase version` 仍为 5，`ALL_MIGRATIONS` 未变化。
- [ ] 运行：

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoundtableDatabaseMigrationTest
```

- [ ] 提交：

```text
build: 同步见域Room Schema输出路径
```

### Task 5：同步 CI、APK 与 merged manifest 门禁

**文件：**

- 修改：`.github/workflows/android-ci.yml`
- 只读检查：`app/src/main/AndroidManifest.xml`
- 只读检查：`app/proguard-rules.pro`

**CI 门禁：**

- 活动源码、测试、Gradle 和脚本中禁止旧包残留；允许清单中的测试/历史说明除外。
- `app/schemas/com.elio.jianyu.data.RoundtableDatabase/5.json` 必须存在。
- 旧 FQCN Schema 必须继续存在且未修改。
- Debug APK：包名 `com.elio.jianyu`，Launcher `com.elio.jianyu.MainActivity`。
- Release APK：包名 `com.elio.jianyu`；公共 CI 仍验证为 unsigned。
- merged manifest 中 Activity、WorkManager initializer/provider authority 不得包含 `com.elio.skillroundtable`。
- `assembleRelease` 与 R8 报告不得出现旧类路径错误。

- [ ] 执行：

```powershell
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

- [ ] 使用 Android SDK 工具检查两个 APK：

```powershell
aapt dump badging app\build\outputs\apk\debug\app-debug.apk
aapt dump badging app\build\outputs\apk\release\app-release-unsigned.apk
```

- [ ] 检查 merged manifest：

```powershell
Get-ChildItem app\build\intermediates\merged_manifests -Recurse -Filter AndroidManifest.xml |
  ForEach-Object { Select-String -Path $_.FullName -Pattern 'com\.elio\.(skillroundtable|jianyu)' }
```

- [ ] 提交：

```text
build: 同步见域构建配置与CI路径
```

### Task 6：验证双包并存与跨包数据隔离

**文件：**

- 创建：`tools/verify-app-coexistence.ps1`
- 创建：`docs/testing/pr-09-01-app-identity-acceptance.md`
- 完成：`app/src/androidTest/java/com/elio/jianyu/identity/AppIdentityIsolationTest.kt`

**前置产物：**

- 旧包 Debug APK：从精确 Base `4de0bfb0480ea84d3a88af12c11167a3a27c38dc` 构建。
- 新包 Debug APK：从当前 PR Head 构建。
- 新包 Release APK：从当前 PR Head 构建。
- API 30+ Emulator 或真机。

**自动检查：**

1. 安装旧包并启动；确认 `com.elio.skillroundtable` 存在。
2. 在旧包创建一条用户会话、一个 SharedPreferences 哨兵和一个私有文件哨兵。
3. 在旧包 API Key 管理页导入仅用于验收的测试 Key；确认旧包显示该 Key。
4. 安装新包；确认两包同时存在且 UID 不同。
5. 启动新包；运行 `AppIdentityIsolationTest`。
6. 新包必须：
   - 无旧会话和消息；
   - 无旧 SharedPreferences 哨兵；
   - 无旧私有文件哨兵；
   - `EncryptedApiKeyStore.read()` 为空；
   - Android Keystore 中同名 alias 在新 UID 命名空间内初始不可见；
   - 首次使用 API 前要求重新配置 Key。
7. 再启动旧包，确认旧会话、哨兵和测试 Key 仍存在，证明安装新包没有覆盖或清理旧包。
8. 验收结束后只删除测试 Key 和哨兵；不在脚本中自动卸载任一 App，避免误删用户开发数据。

**Keystore 证明边界：**

- 保持 alias `skill_roundtable_api_key_v1` 不变。
- 以“旧包已存在测试 Key + 两包 UID 不同 + 新包同名 alias 初始不可见 + 新包 Key 列表为空”为证据。
- 不通过修改 KDF、AEAD、alias 或密文格式规避真实隔离验证。

- [ ] 运行完整 Instrumentation：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

- [ ] 提交：

```text
test: 增加见域双包并存与沙箱验收
```

### Task 7：完成前验证与 Draft PR 更新

- [ ] 重新读取当前分支 Head 和开放 PR，确认没有并行冲突。
- [ ] 执行并记录真实退出码：

```powershell
git diff --check
pwsh.exe -NoProfile -File .\tools\check-secrets.ps1 -IncludeHistory
pwsh.exe -NoProfile -File .\tools\check-app-identity.ps1
.\gradlew.bat --no-daemon --stacktrace compileDebugKotlin
.\gradlew.bat --no-daemon --stacktrace testDebugUnitTest
.\gradlew.bat --no-daemon --stacktrace lintDebug
.\gradlew.bat --no-daemon --stacktrace assembleDebug
.\gradlew.bat --no-daemon --stacktrace assembleRelease
.\gradlew.bat --no-daemon --stacktrace connectedDebugAndroidTest
```

- [ ] 审计：

```powershell
git grep -n 'com\.elio\.skillroundtable'
git status --short
```

- [ ] 对每个保留的旧包引用逐项归类为历史 Schema、旧包验收常量或历史说明；任何未归类引用阻塞完成。
- [ ] 核对 GitHub Actions 的 Secret scan、Android CI build 和 Emulator job。
- [ ] 更新 Draft PR 描述，区分：本地已执行、GitHub CI 已通过、真机/模拟器未验证。
- [ ] 使用精确 Head 准备独立只读代码审查 Prompt。
- [ ] 不标记 Ready、不合并。

---

## 六、原子 Commit 边界

建议顺序：

```text
test: 增加新旧应用身份隔离测试
refactor: 迁移见域应用包名与源码路径
build: 同步见域Room Schema输出路径
build: 同步见域构建配置与CI路径
docs: 更新见域应用身份与验收说明
```

若 GitHub 远端文件 API 无法表达真实 rename，生产实施应交由具备本地 Git 工作区的执行环境完成；不得用数十个互不关联的“删除 + 新建”远端 Commit 破坏审查可读性。当前对话不会在计划批准前修改生产文件。

## 七、回滚方案

1. PR 未合并：关闭 Draft PR 或普通 revert 当前分支 Commit，不修改 `main`。
2. PR 已合并但未发布：对 PR 合并 Commit 执行普通 revert，恢复旧 `applicationId`、包目录、CI 和脚本。
3. 新旧 App 数据相互独立，不存在跨包数据库回滚或 Key 回迁。
4. 回滚不得删除设备上的 `com.elio.jianyu` 或 `com.elio.skillroundtable` 数据；卸载属于外部破坏性动作，必须另行授权。
5. 历史旧 Schema 始终保留；回滚新身份时删除的只能是本 PR 新生成的新 FQCN Schema。

## 八、禁止触碰文件与行为

- 不修改任何 Entity、DAO、表名、字段、索引、数据库版本和 Migration SQL。
- 不修改 Repository 写事务、圆桌执行状态机、根导航行为、Skill 产品行为和 testTag。
- 不修改 `EncryptedApiKeyStore` 的 KDF、AEAD、alias、文件名、序列化格式和错误语义。
- 不修改音频格式、备份格式、导入算法或遥测协议。
- 不修改现有 App Icon、Logo、主题 Token 和正式视觉资产。
- 不修改仓库名、官网、DNS、Release 或服务器。
- 不修改 Gradle Wrapper、依赖版本或版本目录。

## 九、计划自检

- 规格覆盖：应用名称、包名、namespace、源码/测试目录、Manifest、Provider、Room、CI、R8、脚本、并存、空沙箱、Key 重新配置均有对应任务。
- 范围一致：没有领域模型、数据库语义、导航、状态机或正式视觉实现。
- TDD：第一个测试在当前旧包上应以明确包名断言失败，不以编译错误代替 RED。
- Schema：旧文件保留，新文件由 KSP 生成，版本和 identityHash 不变。
- 隔离：自动测试与双 APK 人工验收分开；不把 Android 平台推断冒充实际设备证据。
- 占位扫描：本文不包含影响施工的 `TODO`、`TBD` 或未选择候选。
- 能力限制：当前对话仅有 GitHub 远端能力，没有可执行 Android 构建的本地工作区；计划中的命令均尚未执行。

## 十、计划只读复核门禁

复核对象必须锁定本计划 Commit 和分支 Head，检查：

1. Base 精确为 `4de0bfb0480ea84d3a88af12c11167a3a27c38dc`；
2. 当前差异只有本计划文档；
3. 文件地图覆盖 Gradle、三类源码、Manifest、Room、CI、R8、脚本、文档和测试；
4. 首个 RED 能在旧包上因身份断言失败；
5. 旧 Schema 保留、新 Schema 真实生成且无数据库语义变化；
6. 双包验收能分别证明并存、旧数据仍在、新包不可见；
7. Key alias 未被修改，跨 UID 隔离证据可执行；
8. 旧包字符串允许清单不会放过活动代码残留；
9. Commit 边界可独立审查和回滚；
10. 未夹带 PR09-02A、视觉、依赖升级或无关重构。

计划通过只读复核且用户允许继续后，才可执行 Task 1 的测试写入。