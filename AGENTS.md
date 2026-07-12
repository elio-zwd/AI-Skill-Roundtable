# Antigravity Agent 协同与项目规范指南 (AGENTS.md)

本文件定义了 **SkillRoundtable** 项目的技术方向、开发规范、调试指南、推荐专属技能以及本地文档结构，以便后续的 AI 代理（Agent）能够高效、无缝地参与本项目的开发与维护。

---

## 1. 技术方向与开发规范

### 1.1 技术栈与架构方向
* **工程类型**: 原生 Android 应用项目（Native Android App）。
* **UI 框架**: **Jetpack Compose** (现代声明式 UI)。
* **编程语言**: **Kotlin 2.0.21** (使用 Kotlin DSL 编译脚本 `.gradle.kts`)。
* **数据持久化**: **Room Database** (SQLite ORM 映射)。
* **网络与序列化**: **Retrofit** + **OkHttp**，使用 **kotlinx.serialization** 进行 JSON 解析。
* **核心运行环境**: **JDK 17** 兼容（已做向下修改，以兼容本地 JDK 17 环境）。

### 1.2 开发与提交流程规范
1. **中文语言原则**: 所有生成的开发报告、代码注释、文档更新必须使用 **中文**。
2. **Git 提交信息格式**: 必须使用 `English: 中文` 的格式。
   * *示例*：`fix: 恢复NFC写入稳定性，使用与NfcVWriter一致的逻辑`
3. **Windows 10 兼容性**: 交付的脚本、命令必须完全适配 Win10 x64 平台。在调用 Windows PowerShell 时，默认使用 `pwsh.exe`（PowerShell 7），切勿调用老旧的 `powershell.exe`。
4. **API 密钥安全**: API 密钥统一从根目录下的 `.env` 读取，禁止硬编码任何 Key 到源码中。

### 1.3 开发调试与编译方法
在项目根目录下，使用 PowerShell 7 运行以下命令进行本地编译：
```powershell
# 临时配置使用 JDK 17 环境
$env:JAVA_HOME="D:\My_Elio\dev-tools\jdk-17.0.19+10"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

# 编译 Debug 测试包
.\gradlew.bat assembleDebug
```

---

## 2. 推荐专属技能 (Agent Skills)

在此项目中开发或调试时，AI 代理可检索并加载全局技能目录中的以下专属技能以提升代码交付标准：

* **`@android-cli`**：
  * *用途*：自动化执行 Android SDK 诊断、App 部署、设备日志监控等命令行操作。
* **`@design-taste-frontend`** / **`@high-end-visual-design`**：
  * *用途*：当开发或重构 Compose 界面时自动加载，用以提供高审美的 UI 细节设计、色彩配比及精致的动效呼吸感。
* **`@full-output-enforcement`**：
  * *用途*：防止 AI 在生成大文件或复杂 Compose 页面代码时偷懒使用 `// TODO` 或省略号，强制输出完整代码。
* **`@antigravity-guide`**：
  * *用途*：解答关于 Antigravity IDE 工具链、技能配置和 MCP 服务器使用的疑问。

---

## 3. 本地文件夹结构划分

为保证代码编写与文档沉淀各司其职，AI 代理已自动为本项目规划并创建了以下物理目录：

```
AI-Skill-Roundtable/
├── app/                        # [核心工作区] 现有的 Android 应用源代码编写区
├── workspace/                  # [工作区辅助目录]
│   ├── tests/                  # 代码测试与测试用例运行区
│   └── tools/                  # 开发辅助脚本和命令行工具区
├── docs/                       # [文档区]
│   ├── protocols/              # 协议文档、开发规约记录区
│   ├── bugs/                   # 简洁 Bug 记录与解决方案沉淀区
│   ├── goals/                  # 工作阶段目标、开发任务安排排期记录
│   └── hardware/               # 涉及的物理硬件参数及硬件知识记录
```

---

## 4. 核心文档与 Bug 解决方案汇总

### 4.1 核心本地技术文档
* **安卓编译技术指南**: [docs/android-compilation-guide.md](file:///d:/My_Elio/AI-Skill-Roundtable/docs/android-compilation-guide.md)
  * *简介*：记录了如何在不下载外部软件工具、不安装 Android Studio 下进行本地 JDK 17 离线编译的完整方案。

### 4.2 简洁 Bug 与解决方案记录
* **Bug 01: Gradle 版本冲突导致评估阶段崩溃**：
  * *文档链接*：[docs/bugs/01-gradle-version-mismatch.md](file:///d:/My_Elio/AI-Skill-Roundtable/docs/bugs/01-gradle-version-mismatch.md)
  * *简述*：在使用旧版 Gradle 7.6.4 生成 Wrapper 时，由于 Android Gradle Plugin 8.7.2 要求 Gradle $\ge 8.9$，在配置评估阶段触发了构建崩溃。
  * *解决方案*：绕过本地生成，直接从 `palmformance` 项目复制配置好的 `Gradle 8.14-all` 编译脚本与 Wrapper 配置文件，离线复用本地缓存成功完成编译。
