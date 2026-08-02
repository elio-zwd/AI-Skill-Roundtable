# PR09-01 见域应用身份与双包隔离验收

## 1. 验收目标

确认 Android 应用身份已从：

```text
AI 智囊圆桌 / com.elio.skillroundtable
```

迁移为：

```text
见域 / com.elio.jianyu
```

并验证：

- 新旧 APK 可以同时安装；
- 两个包拥有不同 Android UID 和私有目录；
- 新包不会自动读取旧包 Room、SharedPreferences、私有文件或 Android Keystore Key；
- 安装新包不会覆盖、卸载或清理旧包；
- 新包 API Key 需要重新配置；
- Room v5 结构和迁移链保持不变。

本验收不设计跨包数据迁移，也不把旧包数据复制到新包。

## 2. 精确基线与产物

```text
旧包基线 Commit：4de0bfb0480ea84d3a88af12c11167a3a27c38dc
旧包 applicationId：com.elio.skillroundtable
旧包 Launcher：com.elio.skillroundtable.MainActivity

当前包 applicationId：com.elio.jianyu
当前包 Launcher：com.elio.jianyu.MainActivity
```

需要准备：

1. 从固定旧包基线构建的 Debug APK；
2. 从 PR #31 精确待验收 Head 构建的 Debug APK；
3. 同一 Head 的 AndroidTest APK，或可运行 `connectedDebugAndroidTest` 的完整仓库；
4. API 30 或更高版本 Emulator / 真机；
5. Android SDK `adb` 与 `aapt`；
6. 仅用于本次验收、完成后可以删除的测试 API Key。

禁止使用真实生产 Key、用户真实会话或不可恢复的数据执行验收。

## 3. 仓库只读门禁

开始前记录：

```powershell
git status --short
git branch --show-current
git rev-parse HEAD
git merge-base origin/main HEAD
git diff --check
pwsh.exe -NoProfile -File .\tools\check-secrets.ps1 -IncludeHistory
pwsh.exe -NoProfile -File .\tools\check-app-identity.ps1
```

验收过程中不得：

- 修改、格式化或自动修复仓库文件；
- 创建 Commit、push、rebase 或 force push；
- 标记 PR Ready 或合并 PR；
- 静默卸载 App、清除 App 数据或删除用户 Key。

## 4. 自动双包身份检查

执行：

```powershell
pwsh.exe -NoProfile -File .\tools\verify-app-coexistence.ps1 `
  -LegacyApk <旧包Debug APK> `
  -CurrentApk <见域Debug APK> `
  -Install `
  -CreatePrivateFileSentinel
```

脚本验证：

- 两个 APK 内 applicationId 和 Launcher 正确；
- 两个包同时安装；
- UID 不同；
- `dataDir` 不同并分别包含对应包名；
- 两个 Launcher 均可启动；
- 旧包私有文件哨兵在见域 UID 下不可见。

脚本不会自动卸载、清除数据或删除任一 App。

如需清理脚本创建的文件哨兵，显式执行：

```powershell
pwsh.exe -NoProfile -File .\tools\verify-app-coexistence.ps1 `
  -LegacyApk <旧包Debug APK> `
  -CurrentApk <见域Debug APK> `
  -CleanupPrivateFileSentinel
```

## 5. 全新见域身份测试

身份测试必须独立运行在全新见域安装上，不能依赖完整 Instrumentation 套件的类执行顺序。

在专用测试设备确认可以清理见域测试安装后，记录并执行：

```powershell
adb uninstall com.elio.jianyu.test
adb uninstall com.elio.jianyu

.\gradlew.bat --no-daemon --stacktrace connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.identity.AppIdentityIsolationTest
```

预期全部通过：

```text
targetPackageAndLauncher_useJianyuIdentity
privateDirectoriesAndDatabase_areScopedToJianyuSandbox
freshInstall_hasNoLegacyStateSessionsOrApiKeys
legacyNamedKeystoreAlias_isNotVisibleInFreshJianyuSandbox
```

其中 `freshInstall_hasNoLegacyStateSessionsOrApiKeys` 明确要求：

- 测试前见域数据库文件不存在；
- 见域 Key 密文文件不存在；
- 旧包文件和 SharedPreferences 哨兵在见域沙箱不可见；
- 见域会话和消息为空；
- `EncryptedApiKeyStore.read()` 为空且不产生解密错误；
- 空读取不会创建密钥文件。

如果见域包此前已经运行、写入 Key 或创建会话，该测试前置条件不成立。只能在专用测试设备明确记录后清理见域测试包，不能让脚本静默清理。

## 6. 完整 Instrumentation 套件

身份测试通过后，再次清理见域测试安装，并排除身份类运行剩余套件：

```powershell
adb uninstall com.elio.jianyu.test
adb uninstall com.elio.jianyu

.\gradlew.bat --no-daemon --stacktrace connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.notClass=com.elio.jianyu.identity.AppIdentityIsolationTest
```

这样可以避免其他 UI 或数据库测试先创建状态，污染“全新安装”断言。

GitHub Actions 使用相同顺序：

1. 全新安装单独运行 `AppIdentityIsolationTest`；
2. 再次清理临时见域安装；
3. 排除身份类运行其余 Instrumentation 测试。

## 7. 旧包业务数据保留与新包空状态

以下步骤需要人工操作，因为不能通过仓库脚本伪造用户 API Key 或直接修改生产数据库：

1. 启动旧包 `com.elio.skillroundtable`；
2. 创建一条名称清晰的测试会话，例如 `PR09-01 legacy session`；
3. 在旧包 API Key 管理页导入专用测试 Key；
4. 关闭并重新打开旧包，确认会话和 Key 仍存在；
5. 安装并启动见域 `com.elio.jianyu`；
6. 确认见域会话列表为空；
7. 确认见域 API Key 列表为空，并要求用户重新配置 Key；
8. 再次打开旧包，确认测试会话和测试 Key 仍存在；
9. 验收结束后，仅在旧包 UI 中删除专用测试 Key和测试会话。

通过条件：

- 新包看不到旧包业务数据；
- 旧包数据没有被安装新包覆盖或删除；
- 没有跨包导入、复制或共享 Keystore Key。

## 8. UID、目录与 Keystore 证据

记录：

```powershell
adb shell cmd package list packages -U | Select-String 'com.elio.(skillroundtable|jianyu)'
adb shell dumpsys package com.elio.skillroundtable | Select-String 'userId=|dataDir='
adb shell dumpsys package com.elio.jianyu | Select-String 'userId=|dataDir='
```

必须证明：

- 两个包同时存在；
- UID 不同；
- dataDir 不同；
- 旧包已通过正常 UI 保存专用测试 Key；
- 全新见域包的同名 alias `skill_roundtable_api_key_v1` 不可见；
- 见域 `EncryptedApiKeyStore.read()` 为空；
- 再次打开旧包时测试 Key 仍可读。

保持相同 alias 是为了验证 Android UID 隔离，不得通过改 alias、算法或文件格式规避测试。

## 9. Room Schema 验收

必须同时存在：

```text
app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json
app/schemas/com.elio.jianyu.data.RoundtableDatabase/5.json
```

验证：

```powershell
pwsh.exe -NoProfile -File .\tools\check-app-identity.ps1
.\gradlew.bat --no-daemon --stacktrace compileDebugKotlin
git diff --exit-code -- app/schemas
```

要求：

- 旧 FQCN Schema 相对固定 Base 未修改；
- 新 FQCN Schema 由 Room/KSP 真实生成；
- 两份 JSON 均可解析；
- 规范化 CRLF/LF 后内容完全一致；
- `formatVersion`、数据库 `version = 5`、`identityHash`、实体、字段、索引、外键和 setup queries 一致；
- 构建后没有未提交或被改写的 Schema。

Windows 工作区可能把旧 Git Blob 检出为 CRLF，而 KSP 生成 LF，因此不能只用原始工作区 SHA-256 判断语义差异。

## 10. APK 与 merged manifest 验收

执行：

```powershell
.\gradlew.bat --no-daemon --stacktrace lintDebug
.\gradlew.bat --no-daemon --stacktrace assembleDebug
.\gradlew.bat --no-daemon --stacktrace assembleRelease

aapt dump badging app\build\outputs\apk\debug\app-debug.apk
aapt dump badging app\build\outputs\apk\release\app-release-unsigned.apk
```

要求：

```text
Debug package：com.elio.jianyu
Debug Launcher：com.elio.jianyu.MainActivity
Release package：com.elio.jianyu
公共 CI Release APK：unsigned
```

检查 merged manifest：

```powershell
Get-ChildItem app\build\intermediates\merged_manifests -Recurse -Filter AndroidManifest.xml |
  ForEach-Object {
    Select-String -Path $_.FullName -Pattern 'com\.elio\.(skillroundtable|jianyu)'
  }
```

任何 Activity、Provider authority 或初始化器中的旧活动包名都阻塞通过。

## 11. 最终报告

报告必须区分：

- 本地实际执行并通过；
- GitHub CI 实际通过；
- 自动双包脚本通过；
- 人工 UI 数据/Key 隔离通过；
- 因设备或权限未执行的项目。

至少记录：

1. 操作系统、Shell、Git、JDK、Gradle、Android SDK 和设备版本；
2. 精确 Head、Base、分支和 PR 状态；
3. 构建、单测、Lint、Debug/Release APK；
4. 独立身份测试和剩余 Instrumentation 套件；
5. APK 包名、Launcher、merged manifest 和 R8；
6. 新旧 Schema 比较结果；
7. 双包 UID、dataDir、私有文件哨兵；
8. 旧会话、旧 Key、新包空状态和旧包数据保留；
9. Secret scan、`git diff --check` 和最终干净工作区；
10. 尚未验证事项与复现步骤。

未经用户明确授权，验收完成后仍不得标记 Ready、合并 PR 或删除分支。
