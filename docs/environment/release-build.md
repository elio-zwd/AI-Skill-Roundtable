# Release 构建与签名

## 1. 公共构建

仓库在没有任何签名配置时可以生成经过 R8 和资源压缩的 unsigned Release APK：

```powershell
.\gradlew.bat assembleRelease
```

预期产物：

```text
app/build/outputs/apk/release/app-release-unsigned.apk
```

公共 GitHub Actions 不读取 API Key、`.env`、`local.properties` 或 Release keystore，也不会回退使用 Debug 签名。

## 2. 本地 Release 签名

复制模板：

```powershell
Copy-Item keystore.properties.example keystore.properties
```

然后填写本机真实配置：

```properties
storeFile=/absolute/path/to/release.jks
storePassword=change_me
keyAlias=release
keyPassword=change_me
```

`keystore.properties`、`*.jks` 和 `*.keystore` 已被 Git 忽略，禁止提交到仓库。

也可以使用以下环境变量代替属性文件：

```text
RELEASE_STORE_FILE
RELEASE_STORE_PASSWORD
RELEASE_KEY_ALIAS
RELEASE_KEY_PASSWORD
```

四项必须同时提供。只提供部分配置会让 Gradle 明确失败，避免静默回退到 Debug 签名。

## 3. 版本覆盖

默认版本：

```text
versionCode = 1
versionName = 0.1.0
```

CI 或本地可通过 Gradle Property 覆盖：

```powershell
.\gradlew.bat assembleRelease -PVERSION_CODE=2 -PVERSION_NAME=0.1.1
```

## 4. 签名验证

使用 Android SDK Build Tools 中的 `apksigner`：

```powershell
apksigner verify --verbose app/build/outputs/apk/release/app-release-unsigned.apk
```

unsigned APK 验证失败属于预期结果；配置本地签名后生成的 APK 则应验证成功。

## 5. 当前边界

本阶段仅完成 Release 构建配置、签名注入、版本策略和 R8/资源压缩。正式包名、应用图标、Room Schema 与 Migration 仍由 PR04 后续阶段处理。
