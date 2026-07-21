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

应用采用安全的密钥管理中心（BYOK 模式），不在源码或 APK 中硬编码或硬注入任何 API Key，保证发布安全。

### 客户端密钥配置
- 启动应用后，点击首页的密钥设置（齿轮图标）进入 **API Key 管理中心**。
- 支持单个或批量导入（支持方括号、逗号、换行等多种分隔格式）。
- 密钥经由 Android Keystore 加密（AES-256-GCM）后安全存储在本地 `noBackupFilesDir` 下，且已从自动备份规则中排除。
- 界面永不回显明文，仅显示掩码指纹，确保运行时安全。

### 本地脚本工具配置
若需在 PC 端运行向量生成或摘要生成等 Python 脚本，需在项目根目录创建 `.env` 文件：
```env
# .env 文件（已被 .gitignore 忽略，禁止提交）
GEMINI_API_KEY=AIzaSy...你的真实Key
```
本地 Python 工具在构建期将自动读取该环境变量。

## 4. 本地与生产环境差异

| 项目 | 本地开发 | 生产 |
|------|---------|------|
| API Key | 客户端管理中心手动导入 / 本地脚本从 `.env` 读取 | 客户端管理中心手动导入 / 预留服务器下发 |
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
