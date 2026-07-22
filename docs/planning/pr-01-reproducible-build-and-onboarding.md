# PR 01：可复现构建与新贡献者上手

> 状态：Planned  
> 优先级：P0  
> 前置依赖：无  
> 后续依赖：PR 02、PR 03、PR 04、PR 05  
> 覆盖审查项：F01、F07（安装与环境部分）

---

## 1. 任务目标

让一个从未接触过本项目的开发者，在标准 Windows 10/11、JDK 17、Android SDK 环境下完成：

1. Clone 仓库。
2. 使用仓库自带 Gradle Wrapper 下载或复用 Gradle。
3. 编译 Debug APK。
4. 连接 Android 设备后安装并启动。
5. 首次启动后在 App 内导入自己的 Gemini API Key。

整个过程不得依赖作者电脑中的 `D:`、`E:` 盘目录、个人 SDK 布局、离线压缩包或未提交文件。

---

## 2. 本 PR 明确不做什么

- 不修改圆桌业务逻辑。
- 不重构 API Key 调度。
- 不升级 Kotlin、AGP、Compose、Room 或 Retrofit。
- 不实现 Release 签名。
- 不改变包名。
- 不恢复 `.env -> BuildConfig.[GEMINI_API_KEY]` 注入.
- 不删除现有本地离线开发能力；只把它改成可选配置，不能成为默认路径。

---

## 3. 执行 AI 必须先读的文件

按顺序阅读：

1. `AGENTS.md`
2. `docs/planning/pr-execution-master-plan.md`
3. `gradle/wrapper/gradle-wrapper.properties`
4. `gradlew.bat`
5. `run.ps1`
6. `app/build.gradle.kts`
7. `settings.gradle.kts`
8. `gradle.properties`
9. `.gitignore`
10. `.env.example`
11. `README.md`
12. `docs/environment/environment-setup.md`
13. `docs/environment/android-compilation-guide.md`
14. `tools/README.md`
15. `test/README.md`

如果某个文件不存在，不要自行创造同名替代品；先在交付报告中记录，再按本任务要求决定是否新增。

---

## 4. 修改前基线检查

执行并保存结果：

```powershell
git status --short
git branch --show-current
git log -5 --oneline
Get-Content gradle/wrapper/gradle-wrapper.properties
Get-Content run.ps1
```

扫描个人绝对路径和本地文件 URL：

```powershell
git grep -n -I -E '([A-Za-z]:\\|file:///|My_Elio|MobileApp|jdk-17\.0\.19|gradle-8\.14-all\.zip)'
```

说明：

- 命中 Windows 命令示例不一定是错误。
- 命中作者机器真实路径必须清理。
- `file:///` 指向仓库文档的链接必须改成相对链接。
- 离线环境示例只能放在“可选配置”章节，不能作为默认构建方式。

---

## 5. 详细实施步骤

### 5.1 修复 Gradle Wrapper

目标文件：

```text
gradle/wrapper/gradle-wrapper.properties
```

当前问题：`distributionUrl` 指向作者电脑本地 Gradle ZIP。

实施要求：

1. 保持当前项目原本计划使用的 Gradle 版本，不在本 PR 升级版本。
2. 将默认地址改成 Gradle 官方 HTTPS 地址。
3. 优先使用 `-bin.zip`，除非项目确实依赖完整源码包。
4. 只保留一个生效的 `distributionUrl`。
5. 可在注释中保留“企业镜像/离线镜像如何覆盖”的说明，但不能保留作者磁盘地址。

期望形式：

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

如果现有 Wrapper 不支持某个字段，不要强行增加；以当前 Wrapper 可识别配置为准。

验证：

```powershell
./gradlew.bat --version
./gradlew.bat tasks
```

验收点：日志中不得出现 `file:///E:/`、`file:///D:/` 等作者路径。

---

### 5.2 重写 `run.ps1` 的环境探测

目标文件：

```text
run.ps1
```

目标行为：脚本不写死 JDK 和 ADB 路径；自动探测并给出可理解错误。

建议实现结构：

```powershell
param(
    [string]$JavaHome,
    [switch]$SkipInstall,
    [switch]$NoLogcat
)

$ErrorActionPreference = 'Stop'
```

#### Java 解析顺序

1. 如果调用者传入 `-JavaHome`，验证 `$JavaHome/bin/java.exe` 存在。
2. 否则如果环境变量 `JAVA_HOME` 存在，验证 `$env:JAVA_HOME/bin/java.exe`。
3. 否则使用 `Get-Command java -ErrorAction SilentlyContinue`。
4. 执行 `java -version`，确认主版本为 17。
5. 若不是 17，停止并输出明确提示，不要偷偷覆盖用户环境。

错误示例应说明：

```text
未检测到 JDK 17。请安装 JDK 17，并设置 JAVA_HOME，或执行：
./run.ps1 -JavaHome "C:\path\to\jdk-17"
```

#### ADB 解析顺序

1. 使用 `Get-Command adb`。
2. 找不到时提示用户通过 Android SDK Platform-Tools 安装，并把 `platform-tools` 加入 PATH。
3. 执行 `adb devices`。
4. 只有存在状态为 `device` 的设备时才执行安装和启动。
5. 如果是 `unauthorized`，提示用户解锁手机并接受 USB 调试授权。

#### Gradle 执行

- 默认执行 `./gradlew.bat installDebug`。
- `-SkipInstall` 时只执行 `assembleDebug`。
- 每一步检查 `$LASTEXITCODE`。
- 失败时返回相同非零退出码。
- 不允许编译失败后继续尝试启动 App。

#### Logcat 行为

- `-NoLogcat` 时不进入持续日志模式。
- 正常模式先获取包 PID，再执行 `adb logcat --pid=<pid>`。
- 无 PID 时输出说明并退出，不要默认打印全部系统日志。
- `Ctrl+C` 应能正常结束。

#### 包名来源

短期可以定义一个脚本变量：

```powershell
$packageName = 'com.elio.skillroundtable'
```

但必须在注释中说明 PR 04 改包名时同步更新。更理想的做法是通过 Gradle task 或单独项目配置读取，但不要为了这一点过度重构。

---

### 5.3 明确 API Key 的真实配置方式

必须核对：

- `app/build.gradle.kts` 已不再将 `.env` 写入 `BuildConfig`。
- App 当前通过 `ApiKeyManagerScreen` 和 `EncryptedApiKeyStore` 在运行时导入 BYOK Key。

README 必须把主流程改成：

1. 编译和安装 App。
2. 打开 App。
3. 进入“Gemini API Key 管理”。
4. 粘贴一个或多个用户自己的 Key。
5. App 在 Android Keystore 保护下加密保存。
6. 验证 Key 后开始使用。

不得继续写：

```text
复制 .env.example 为 .env，填入 GEMINI_API_KEY，然后 Android App 自动读取。
```

`.env.example` 的处理规则：

- 如果 Python 构建辅助脚本仍使用 `.env`，保留模板。
- 在模板和文档中明确：`.env` 仅供本地辅助脚本使用，不会自动进入 Android APK。
- 如果没有任何脚本仍读取 `.env`，可以在本 PR 删除无用字段，但删除前必须搜索：

```powershell
git grep -n -I 'GEMINI_API_KEY\|GEMINI_API_KEYS\|\.env'
```

禁止未经搜索就删除 `.env.example`。

---

### 5.4 修复 README 安装章节

README 的安装章节至少包含：

#### 环境要求

- Windows 10/11 或其他支持 Android Gradle 构建的系统。
- JDK 17。
- Android SDK Platform 35。
- Android SDK Build Tools。
- Android SDK Platform-Tools（需要真机调试时）。
- Git。

不要写作者本机 JDK 绝对路径。

#### 构建命令

Windows：

```powershell
./gradlew.bat assembleDebug
```

macOS/Linux（如果项目 Wrapper 脚本可用）：

```bash
./gradlew assembleDebug
```

APK 路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

#### 安装方式

```powershell
./gradlew.bat installDebug
```

或使用：

```powershell
./run.ps1
```

必须说明 `run.ps1` 是便利脚本，不是唯一构建方式。

#### 首次启动

说明应用内导入 BYOK Key，不回到 `.env` 旧流程。

#### 常见问题

至少覆盖：

- `JAVA_HOME is not set`
- Java 版本不是 17
- `adb` 不存在
- 设备 `unauthorized`
- Gradle 下载失败
- Android SDK Platform 35 缺失
- 没有可用 API Key

---

### 5.5 清理个人路径与失效链接

修改范围：

- `README.md`
- `AGENTS.md` 中纯环境路径部分
- `docs/environment/`
- `tools/README.md`
- `test/README.md`
- 其他被基线搜索命中的文档

规则：

- GitHub 文档链接使用相对路径，例如：

```markdown
[工具说明](../../tools/README.md)
```

- 不允许：

```markdown
[file](file:///d:/My_Elio/...)
```

- Windows 命令示例使用通用占位路径：

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-17'
```

- 如果保留离线 Gradle 配置，写成用户本地覆盖示例，不提交到 Wrapper 默认值。

---

### 5.6 新增或更新环境文档

优先更新现有：

```text
docs/environment/environment-setup.md
docs/environment/android-compilation-guide.md
```

避免创建内容重复的第三份文档。

建议结构：

1. 支持环境。
2. JDK 17 安装与验证。
3. Android SDK 安装。
4. Clone 与 Wrapper 构建。
5. 真机连接。
6. App 内 Key 配置。
7. 离线构建作为高级可选项。
8. 常见错误排查。

离线构建章节必须说明：

- 依赖用户自己的 Gradle 缓存或镜像。
- 不能提交 `file:///` 本机路径。
- 不保证所有首次构建场景完全离线。

---

## 6. 预计修改文件

最低限度：

```text
gradle/wrapper/gradle-wrapper.properties
run.ps1
README.md
docs/environment/environment-setup.md
docs/environment/android-compilation-guide.md
```

按扫描结果可能修改：

```text
AGENTS.md
.env.example
tools/README.md
test/README.md
workspace/tools/README.md
```

如果实际修改文件明显超过上述范围，执行 AI 必须在交付报告中解释原因。

---

## 7. 必须执行的验证

### 7.1 静态扫描

```powershell
git grep -n -I -E '([A-Za-z]:\\|file:///|My_Elio|MobileApp)'
```

允许通用示例路径，但不得出现作者真实目录。

```powershell
git grep -n -I 'BuildConfig.[GEMINI_API_KEY]'
```

结果必须为空，或只出现在明确解释“旧架构已移除”的历史文档中；最好同步删除过时表述。

### 7.2 构建验证

```powershell
./gradlew.bat --version
./gradlew.bat clean
./gradlew.bat compileDebugKotlin
./gradlew.bat testDebugUnitTest
./gradlew.bat assembleDebug
```

### 7.3 脚本验证

至少测试：

1. 已设置正确 `JAVA_HOME`。
2. 未设置 `JAVA_HOME` 但 `java` 在 PATH。
3. Java 不是 17 时脚本明确失败。
4. 找不到 ADB 时输出明确提示。
5. `-SkipInstall -NoLogcat` 可只构建后退出。

无法在当前环境覆盖某场景时，必须写人工验证步骤。

---

## 8. 验收清单

- [ ] Wrapper 使用官方 HTTPS 地址。
- [ ] 仓库默认构建不依赖 `D:` 或 `E:` 盘文件。
- [ ] `run.ps1` 不写死 JDK 路径。
- [ ] `run.ps1` 能检测 JDK 17 和 ADB。
- [ ] README 不再声称 Android App 从 `.env` 获取 Key。
- [ ] README 指导用户在 App 内导入 BYOK Key。
- [ ] 所有 GitHub 文档链接不使用 `file:///`。
- [ ] 环境文档区分默认在线 Wrapper 与可选离线方案。
- [ ] `compileDebugKotlin` 通过。
- [ ] `testDebugUnitTest` 通过。
- [ ] `assembleDebug` 通过。

---

## 9. 常见错误和禁止做法

### 错误：直接删除 Wrapper 或要求用户安装全局 Gradle

不允许。项目应使用仓库自带 Wrapper，确保版本一致。

### 错误：把官方 URL改成另一个作者本地镜像

不允许。公共仓库默认值必须可由陌生用户访问。

### 错误：为了让 README 成立而恢复 BuildConfig Key

不允许。运行时 BYOK + Keystore 才是当前正确架构。

### 错误：顺便升级所有依赖

不允许。依赖升级属于独立任务，会扩大构建故障面。

### 错误：构建失败就只修改文档声称成功

不允许。必须给出真实命令结果。

---

## 10. 交付报告必须额外回答

除总控模板外，本 PR 必须回答：

1. 最终 Wrapper URL 是什么？
2. `run.ps1` 如何解析 Java？
3. `.env.example` 目前由哪些脚本使用？
4. README 的 Key 配置入口改成了什么？
5. 全仓还剩哪些绝对路径，为什么可以保留？
6. 在全新 Gradle 缓存环境下是否验证过下载和构建？若没有，人工验证方法是什么？
