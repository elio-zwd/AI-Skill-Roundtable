# 环境配置说明

## 1. 操作系统与开发工具

| 项目 | 要求 / 实际值 |
|------|-------------|
| 操作系统 | Windows 10 x64 |
| Shell | PowerShell 7（`pwsh.exe`），禁止使用 `powershell.exe` |
| JDK 版本 | OpenJDK 17 |
| JDK 路径 | 用户本地安装目录（例：`C:\path\to\jdk-17`） |
| Gradle 版本 | 8.14-bin（默认官方在线下载，可选离线） |
| AGP 版本 | Android Gradle Plugin 8.7.2 |
| Compile SDK | 35 |
| Min SDK | 26（Android 8.0） |
| Target SDK | 35 |

## 2. JDK 环境初始化（每次新开终端必须执行）

```powershell
# 设置 JDK 17 环境（请替换为您的实际 JDK 路径）
$env:JAVA_HOME = "C:\path\to\jdk-17"
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
- 状态：🚧 待修复

---

## 6. 在线与离线构建方案选择

### 默认方案：官方在线 Wrapper 构建
项目默认配置使用官方的 Gradle Wrapper 下载并运行 Gradle，无需在机器上安装全局的 Gradle 软件。
- 确保您的网络能够顺畅访问 Gradle 官方分发地址（例如 `https://services.gradle.org`）。
- 首次构建时，Gradle 会自动下载并缓存所需要的 Gradle 版本（8.14）。

### 可选方案：本地离线缓存构建
在没有互联网连接，或者因代理/网络限制无法下载官方 Gradle 时的离线替代方案：
1. 下载 `gradle-8.14-bin.zip`（或 `-all.zip`）到本地路径。
2. 修改 `gradle/wrapper/gradle-wrapper.properties` 中 `distributionUrl` 的值，将其指向本地绝对路径或搭建的内部镜像源，例如：
   ```properties
   distributionUrl=file:///C:/path/to/gradle-8.14-bin.zip
   ```
   *注意：禁止提交任何包含本机绝对路径的修改到公共 Git 仓库中。*
3. 执行构建命令时，可以通过 `-o` 参数开启 Gradle 的离线模式（前提是所需的依赖包已经在全局依赖缓存中存在）：
   ```powershell
   ./gradlew.bat assembleDebug --offline
   ```

