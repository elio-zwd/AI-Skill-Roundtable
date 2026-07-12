# 环境配置说明

## 1. 操作系统与开发工具

| 项目 | 要求 / 实际值 |
|------|-------------|
| 操作系统 | Windows 10 x64 |
| Shell | PowerShell 7（`pwsh.exe`），禁止使用 `powershell.exe` |
| JDK 版本 | OpenJDK 17（`jdk-17.0.19+10`） |
| JDK 路径 | `D:\My_Elio\dev-tools\jdk-17.0.19+10` |
| Gradle 版本 | 8.14-all（离线缓存） |
| AGP 版本 | Android Gradle Plugin 8.7.2 |
| Compile SDK | 35 |
| Min SDK | 26（Android 8.0） |
| Target SDK | 35 |

## 2. JDK 环境初始化（每次新开终端必须执行）

```powershell
$env:JAVA_HOME = "D:\My_Elio\dev-tools\jdk-17.0.19+10"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# 验证
java -version
# 应输出：openjdk version "17.x.x" ...
```

## 3. API 密钥配置

密钥通过根目录 `.env` 文件注入，由 `app/build.gradle.kts` 中的 `getEnvValue()` 读取并注入 `BuildConfig`。

```
# .env 文件格式（不提交 Git）
GEMINI_API_KEY=AIzaSy...你的真实Key
```

密钥在代码中通过 `BuildConfig.GEMINI_API_KEY` 访问，或从 ViewModel 的 SharedPreferences 读取（用户在设置页面配置）。

## 4. 本地与生产环境差异

| 项目 | 本地开发 | 生产 |
|------|---------|------|
| API Key | `.env` 文件注入 | 用户在 App 内自行填写 |
| 数据库 | `fallbackToDestructiveMigration()` | 正式 Migration |
| 编译模式 | Debug | Release（待配置签名） |
| 日志级别 | OkHttp BODY 级别日志 | 关闭敏感日志 |

## 5. 已知编译问题与解决方案

**Bug 01：Gradle 版本与 AGP 不匹配**
- 现象：AGP 8.7.2 要求 Gradle ≥ 8.9，旧 Gradle 7.6.4 在评估阶段崩溃
- 解决：从本地已有项目（palmformance）复制 Gradle 8.14-all Wrapper 配置，离线复用缓存
- 文档：[../issues/01-gradle-version-mismatch.md](../issues/01-gradle-version-mismatch.md)

**Bug 02：API 模型名错误**
- 现象：`GeminiApi.kt` 中 `gemini-3.5-flash` 不存在，所有请求返回 404
- 解决：修改为 `gemini-2.0-flash`（主）/ `gemini-1.5-flash`（备）
- 状态：🚧 待修复
