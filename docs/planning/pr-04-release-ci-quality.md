# PR 04：Release 工程化与 CI 质量保障

> 状态：Planned  
> 优先级：P1  
> 前置依赖：PR 01、PR 02、PR 03 已合并  
> 后续依赖：PR 05  
> 覆盖审查项：F05、F06、F11、F13，以及 F12 的质量门禁部分

---

## 1. 任务目标

把项目从“作者电脑能运行的 Debug 原型”提升为具备基础发布能力和自动化质量门禁的 Android 开源项目。

完成后必须满足：

1. Release 不再使用 Debug 签名。
2. 包名、版本号、图标和构建类型有正式配置。
3. Release 可以生成未签名产物；在提供本地签名配置时可生成签名产物。
4. Release 开启代码和资源压缩，并通过实际构建验证。
5. Room Schema 可追踪；Release 不再使用破坏性迁移。
6. GitHub Actions 自动执行 Secret Scan、单测、Lint 和 Debug 构建。
7. 关键 Key、Retry、Telemetry、Migration 测试进入质量门禁。
8. 不依赖作者本机路径或本地私有文件才能通过公共 CI。

---

## 2. 固定决策

### 2.1 正式包名

默认目标：

```text
com.elio.skillroundtable
```

如果仓库所有者在执行前明确指定其他包名，以指定值为准；否则执行 AI 不得自行发明多个候选名。

注意：修改 `applicationId` 会让 Android 把它视为新应用，无法直接继承旧 `com.example.skillroundtable` 安装包的数据。由于当前仍处预研/公开发布前阶段，本 PR 默认接受这一变化，但交付报告必须明确说明。

`namespace` 与 Kotlin package 是否同时迁移：

- 本 PR 推荐同时迁移到 `com.elio.skillroundtable`，避免 namespace/applicationId 长期分裂。
- 必须使用 IDE/安全重构或全仓搜索验证，不得只改 Gradle 一行。
- 若迁移风险过高，可先只改 `applicationId`，但必须建立后续任务，并在文档说明 namespace 仍为旧值。默认优先完整迁移。

### 2.2 版本策略

从当前状态开始采用：

```text
versionCode: 单调递增整数
versionName: SemVer，例如 0.1.0
```

首次公开 Beta 建议：

```kotlin
versionCode = 1
versionName = "0.1.0"
```

不要继续用 `1.0` 暗示稳定正式版。

### 2.3 Release 签名

- 仓库不提交 keystore。
- 本地签名通过被 `.gitignore` 排除的 `keystore.properties` 或环境变量注入。
- 未提供签名配置时，`assembleRelease` 应生成 unsigned release，而不是回退到 Debug 签名。
- 公共 CI 不需要真实签名密钥。

### 2.4 Room 策略

- `exportSchema = true`
- Schema 提交到仓库。
- Release 移除 `fallbackToDestructiveMigration()`。
- Debug 如确需保留破坏性迁移，必须由 BuildConfig 或不同数据库构建配置明确隔离，不能影响 Release。

---

## 3. 本 PR 明确不做什么

- 不发布到 Google Play。
- 不创建或提交真实签名文件。
- 不申请商店账号。
- 不升级全部依赖。
- 不重新设计 UI。
- 不重写圆桌、Key 或遥测业务。
- 不实现完整端到端云测试。
- 不以关闭 Lint/测试代替修复。

---

## 4. 执行 AI 必须先读的文件

1. `AGENTS.md`
2. `docs/planning/pr-execution-master-plan.md`
3. 本文件
4. `app/build.gradle.kts`
5. 根目录 `build.gradle.kts`
6. `settings.gradle.kts`
7. `gradle/libs.versions.toml`
8. `gradle.properties`
9. `app/proguard-rules.pro`
10. `app/src/main/AndroidManifest.xml`
11. `app/src/main/res/` 下图标和资源
12. `RoundtableDatabase.kt`
13. 所有 `Migration` 类和测试
14. `.github/workflows/secret-scan.yml`
15. `tools/check-secrets.ps1`
16. `.gitignore`
17. PR 02/03 新增的测试目录
18. `LICENSE`
19. `README.md` 构建/发布说明

先执行：

```powershell
git status --short
./gradlew.bat tasks
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
./gradlew.bat assembleDebug
./gradlew.bat assembleRelease
```

记录修改前 Release 构建结果；如果因 Debug signing 或本地路径失败，写入基线。

---

## 5. 详细实施步骤

### 5.1 规范 applicationId、namespace 和 package

目标文件：

```text
app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/java/**
app/src/test/java/**
app/src/androidTest/java/**
```

实施顺序：

1. 全仓搜索旧包名：

```powershell
git grep -n -I 'com\.example\.skillroundtable'
```

2. 修改 `namespace` 和 `applicationId`。
3. 迁移 Kotlin 文件 package 声明与目录。
4. 更新 Manifest 中相对/绝对组件名。
5. 更新 ADB/run 脚本包名。
6. 更新测试 package 和 import。
7. 更新 ProGuard/R8 规则中的包名。
8. 更新文档命令中的 Activity 启动路径。
9. 再次搜索旧包名，保留项必须有理由。

禁止简单字符串替换二进制文件或 Gradle 缓存。

验证：

```powershell
./gradlew.bat compileDebugKotlin
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

设备验收：确认新包名能安装和启动。

---

### 5.2 配置正式版本信息

建议在 `defaultConfig` 中设置：

```kotlin
versionCode = 1
versionName = "0.1.0"
```

可选优化：从 Gradle property 读取 CI 覆盖值：

```kotlin
versionCode = providers.gradleProperty("VERSION_CODE")
    .orElse("1")
    .get()
    .toInt()

versionName = providers.gradleProperty("VERSION_NAME")
    .orElse("0.1.0")
    .get()
```

如果采用覆盖方式，必须保证无参数时本地构建成功，并在 README 说明。

禁止通过 Git commit 数自动生成不可预测 versionCode，除非有成熟发布流程。

---

### 5.3 重构 Release 签名

新增本地模板文档或示例，但不新增真实 `keystore.properties`。

`.gitignore` 应包含：

```gitignore
keystore.properties
*.jks
*.keystore
```

建议 `keystore.properties.example`：

```properties
storeFile=/absolute/path/to/release.jks
storePassword=change_me
keyAlias=release
keyPassword=change_me
```

注意：示例值不能是真实密码。

Gradle 行为：

1. 如果 `keystore.properties` 存在且字段完整，创建 `release` signingConfig。
2. 如果不存在，Release 不设置 signingConfig，生成 unsigned APK/AAB。
3. 绝不使用：

```kotlin
signingConfig = signingConfigs.getByName("debug")
```

4. 错误配置时给出明确 Gradle 错误，不静默回退 Debug。
5. 可支持环境变量供未来 CI Release 使用，但公共 PR CI 不要求 secrets。

必须验证：

- 无本地签名文件时 `assembleRelease` 成功生成 unsigned 产物。
- `apksigner verify` 或 Gradle 输出证明它不是 Debug 签名。
- 不读取仓库内不存在的作者绝对路径。

---

### 5.4 开启 R8 和资源压缩

Release 建议：

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
    )
}
```

执行 AI 必须实际构建，不得只打开开关。

重点验证：

- Kotlin serialization DTO 不被错误删除。
- Room 实体/DAO/Database 正常。
- Retrofit 接口注解正常。
- WorkManager 能实例化 Worker。
- Compose 页面可打开。
- 通过反射或 JSON 名称读取的类型正常。
- Assets 下 Skills、JSON、头像不被误删。

如果 R8 报错或运行闪退：

- 增加最小必要 keep 规则。
- 每条 keep 规则写注释说明对应库/反射入口。
- 禁止使用 `-keep class ** { *; }` 全局关闭压缩意义。

---

### 5.5 配置正式应用图标

当前 Manifest 使用系统默认图标。应：

1. 创建标准 adaptive icon 资源。
2. 提供 foreground/background。
3. Manifest 使用：

```xml
android:icon="@mipmap/ic_launcher"
android:roundIcon="@mipmap/ic_launcher_round"
```

4. Debug/Release 可共用图标；若有 Debug 标识可后续处理。
5. 不使用未经授权的第三方图片。
6. 图标来源/设计说明进入 PR 05 的第三方资产治理；本 PR 至少确保是项目自有或明确可用资源。

如果仓库没有可用品牌图，执行 AI 不得擅自下载网络 Logo；可以使用项目内已有、用户原创的简单几何图标，或保留清晰 TODO 并把“默认系统图标”列为未完成阻断项。不能声称发布就绪却仍用系统默认图标。

---

### 5.6 Room Schema 导出

在 `app/build.gradle.kts` 为 KSP 配置 Schema 目录。根据当前 Room/KSP 版本使用正确写法，例如：

```kotlin
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}
```

数据库注解：

```kotlin
@Database(..., exportSchema = true)
```

Schema 目录：

```text
app/schemas/
```

要求：

- 当前 v5 Schema 生成并提交。
- 后续每次版本变更都提交新 JSON。
- 不手工伪造 Schema JSON。
- CI 构建后检查工作树没有未提交 Schema 变化，防止开发者忘记更新。

---

### 5.7 移除 Release 破坏性迁移

当前 `fallbackToDestructiveMigration()` 可能导致聊天数据丢失。

推荐实现：

#### 简单方案

直接删除 `fallbackToDestructiveMigration()`，确保从所有已公开版本到当前版本的 Migration 都完整。

#### 若 Debug 必须允许

创建数据库 Builder 工厂：

```kotlin
private fun buildDatabase(context: Context): RoundtableDatabase {
    val builder = Room.databaseBuilder(...)
        .addMigrations(...)

    if (BuildConfig.DEBUG) {
        builder.fallbackToDestructiveMigration()
    }

    return builder.build()
}
```

但要注意：Debug 破坏性迁移可能掩盖 Migration 缺失。更推荐所有构建都不使用 destructive fallback。

执行 AI 必须盘点：

- 当前数据库版本。
- 历史公开/开发版本：1、2、3、4、5。
- 是否存在 1→2、2→3 迁移。
- 如果旧版本从未对外发布，可在文档明确最低受支持升级版本；不能假装所有路径都支持。

支持策略必须写进 `docs/` 和交付报告。

---

### 5.8 增加 Room Migration Test

新增 Android Instrumentation Test，使用 Room Testing：

```text
app/src/androidTest/java/.../RoundtableDatabaseMigrationTest.kt
```

加入依赖：

```kotlin
androidTestImplementation("androidx.room:room-testing:<same-room-version>")
```

测试至少覆盖：

- 3 → 4
- 4 → 5
- 如果补齐 1 → 2、2 → 3，则逐一覆盖
- 从最早受支持版本连续迁移到 5

验证字段：

- `messages.roundIndex`
- `messages.audioFilePath`
- `messages.audioFormat`
- `messages.audioSizeBytes`
- `characters.voiceConfig`
- `character_groups` 表和关键列

必须使用导出的 Schema，而不是只检查 SQL 不报错。

CI 中可把 Instrumentation Migration Test 放到单独 emulator job；如果暂时不运行 emulator，至少提交测试并配置后续 nightly job，但本 PR 最好完成自动执行。

---

### 5.9 补强核心单元测试

整合 PR 02、PR 03 测试，确保 CI 自动运行。

最低范围：

#### Key 与重试

- Key Parser。
- Key Attempt Plan 不重复。
- Retry Policy 分类。
- Key Provider 去重。
- 预算限制。

#### Telemetry

- Redactor。
- Level 行为。
- Retention。
- 旧日志迁移。

#### Roundtable

- Transcript 顺序。
- 串行 Orchestrator。
- Pending 清理。
- 角色失败后继续。

#### KeyStore

Android Keystore 需要 Instrumentation 或 Robolectric 适配。

至少增加 Instrumentation 测试覆盖：

- 写入后读回。
- 文件中不存在明文 Key。
- 清空后读为空。
- 损坏密文时安全失败。
- `noBackupFilesDir` 路径正确。

如 CI Emulator 成本暂时过高，可把 KeyStore 测试与 Migration Test 放同一个 Android Emulator job。

---

### 5.10 扩展 Secret Scan

当前 PowerShell 规则仅覆盖 `AIza...`。

目标：

1. 保留项目自定义敏感文件检查。
2. 增加通用 Secret Scanner，例如 Gitleaks。
3. 扫描工作树、暂存区和可达历史。
4. 避免把示例占位符误报为真实 Key。
5. CI 失败信息不回显完整密钥。

自定义脚本可扩展的模式包括：

- Google/Gemini API Key。
- GitHub PAT 常见前缀。
- AWS Access Key。
- PEM Private Key Header。
- Bearer/JWT 可疑长串。

但不要依靠脆弱正则覆盖所有秘密；通用扫描器作为主防线。

GitHub Workflow 权限最小化：

```yaml
permissions:
  contents: read
```

不要给写仓库、issue、PR 的权限。

---

### 5.11 建立 GitHub Actions CI

建议文件：

```text
.github/workflows/android-ci.yml
```

触发：

```yaml
on:
  push:
    branches: [main]
  pull_request:
```

主构建 Job 推荐 Ubuntu：

1. Checkout。
2. Setup JDK 17。
3. Validate Gradle Wrapper。
4. 配置 Gradle 缓存。
5. `./gradlew testDebugUnitTest`。
6. `./gradlew lintDebug`。
7. `./gradlew assembleDebug`。
8. `./gradlew assembleRelease`（unsigned）。
9. 上传 Lint 报告和 APK 作为失败排查 Artifact（注意公开仓库 Artifact 可被有权限的人访问，不能包含 Key）。

Secret Scan 可独立 Job：

- Gitleaks。
- `tools/check-secrets.ps1 -IncludeHistory`。

Dependency Review 仅在 PR 触发：

```yaml
if: github.event_name == 'pull_request'
```

Android Emulator Job：

- 运行 Migration Test 和 KeyStore Instrumentation Test。
- 如耗时过高，可仅 PR 和 main 运行，不在每个普通 push 重复。
- 使用 API 级别应不低于 minSdk 且兼容 Keystore AES-GCM；建议 API 30 或 35。

所有第三方 Actions 应固定到明确大版本或 commit SHA，并在文档说明更新方式。

---

### 5.12 CI 不得依赖秘密

公共 CI 必须：

- 不需要 Gemini API Key。
- 不执行真实 Gemini 网络请求。
- 使用 Fake Client、Mock 或纯逻辑测试。
- 不需要 Release keystore。
- 不需要作者本地 Android 设备。
- 不读取 `.env`。

如果测试当前会触发真实 API，应先重构注入 Fake，而不是把 Key 放进 GitHub Secrets 让每个 PR 消耗额度。

---

## 6. 预计修改文件

主要：

```text
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/java/**（包名迁移时）
app/src/test/java/**
app/src/androidTest/java/**
app/schemas/**
RoundtableDatabase.kt
.gitignore
.github/workflows/android-ci.yml
.github/workflows/secret-scan.yml
tools/check-secrets.ps1
```

可能新增：

```text
keystore.properties.example
app/src/main/res/mipmap-*/ic_launcher*
app/src/main/res/drawable/ic_launcher_foreground.xml
```

可能更新：

```text
run.ps1
README.md
docs/environment/*
```

---

## 7. 必须执行的验证

本地：

```powershell
./gradlew.bat clean
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
./gradlew.bat assembleDebug
./gradlew.bat assembleRelease
```

包名搜索：

```powershell
git grep -n -I 'com\.example\.skillroundtable'
```

签名检查：

```powershell
# 根据本机 Android SDK 路径执行
apksigner verify --verbose app/build/outputs/apk/release/app-release-unsigned.apk
```

若文件名不同，以实际 Gradle 输出为准。

Schema：

```powershell
Get-ChildItem app/schemas -Recurse
```

确认构建后 `git status` 不出现意外未提交 Schema 差异。

CI：

- 创建 PR 后所有必需 Job 通过。
- 手工查看 Lint 报告。
- 确认日志没有密钥或请求正文。

设备/模拟器：

1. 安装 Debug。
2. 启动所有主要 Tab。
3. 导入测试 Key。
4. 发起一轮 2～3 角色对话。
5. 测试音频 Worker 可实例化。
6. 执行数据库 Migration Test。
7. 执行 KeyStore Test。
8. 安装 Release unsigned 不可直接安装是正常的；提供本地测试签名后验证签名 Release 可安装。

---

## 8. 验收清单

- [ ] `applicationId` 不再是 `com.example...`。
- [ ] namespace/package 迁移结果一致，或明确记录暂缓原因。
- [ ] `versionName` 使用 SemVer Beta 值。
- [ ] Release 不使用 Debug signingConfig。
- [ ] 无签名配置时可生成 unsigned Release。
- [ ] 仓库不包含 keystore 和真实密码。
- [ ] Release 开启 minify 和 shrinkResources。
- [ ] R8 后主要功能烟雾测试通过。
- [ ] Manifest 不使用系统默认图标。
- [ ] `exportSchema=true`。
- [ ] Schema JSON 已提交。
- [ ] Release 不使用 destructive migration。
- [ ] 已支持的 Migration 路径有测试。
- [ ] KeyStore 加解密有安全测试。
- [ ] CI 包含 Unit Test、Lint、Assemble Debug、Assemble Release。
- [ ] Secret Scan 不再只依赖 Gemini 正则。
- [ ] CI 不需要 API Key 或签名 secrets。
- [ ] GitHub Actions 使用最小权限。

---

## 9. 禁止事项

- 禁止提交 `.jks`、`.keystore`、密码或 `keystore.properties`。
- 禁止 Release 回退使用 Debug 签名。
- 禁止为了 assembleRelease 通过而关闭 R8。
- 禁止用全局 keep 规则让压缩失去意义。
- 禁止保留 Release destructive migration。
- 禁止修改数据库版本却不导出 Schema。
- 禁止 CI 调用真实 Gemini API。
- 禁止把 API Key 放入公共 PR Workflow。
- 禁止忽略 Lint 或使用 `abortOnError=false` 掩盖问题。
- 禁止一次性升级所有依赖。
- 禁止为了通过 Secret Scan 删除真实功能或回显疑似密钥原文。

---

## 10. 交付报告必须额外回答

1. 最终 applicationId、namespace 和 Kotlin package 分别是什么？
2. 旧包名还出现在哪些位置，为什么？
3. 无签名配置时 Release 产物是什么？
4. 如何配置本地 Release 签名？
5. 如何证明没有使用 Debug 签名？
6. R8 增加了哪些 keep 规则，各自原因是什么？
7. 当前支持哪些 Room 升级路径？哪些明确不支持？
8. Migration Test 覆盖哪些版本？
9. KeyStore Test 覆盖哪些异常？
10. CI 有哪些 Jobs、分别运行哪些命令？
11. Secret Scan 使用了哪些工具？
12. 公共 CI 为什么不需要 API Key 和 keystore？
13. GitHub Actions 最终运行结果和链接是什么？
