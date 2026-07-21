# AGENTS.md — AI 智囊圆桌（AI-Skill-Roundtable）

> AI 代理工作规范。进入本仓库后，必须先阅读本文件，再阅读 `docs/planning/pr-execution-master-plan.md` 和当前任务对应的 PR 施工单。

---

## 1. 项目概况

**AI 智囊圆桌**是一款原生 Android 多角色聊天应用。项目包含 20 个 Skill 角色、Room 本地会话、Gemini REST / Interactions / Live WebSocket 调用、联网搜索、Markdown 展示与音频管理。

当前仓库正在按以下顺序进行五阶段重构：

```text
PR 01 可复现构建与上手
→ PR 02 圆桌与 Key 编排
→ PR 03 隐私与遥测
→ PR 04 Release、CI 与数据迁移
→ PR 05 开源治理与文档统一
```

禁止并行执行 PR 02、PR 03、PR 04。它们会同时影响网络层、调度层、测试或 Gradle 配置。

---

## 2. 当前可信工程事实

| 项目 | 当前值 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| 数据库 | Room，当前版本 v5 |
| JDK | JDK 17 |
| Gradle | Wrapper 8.14 `-bin` |
| Compile / Target SDK | 35 |
| Min SDK | 26 |
| 网络 | Retrofit、OkHttp、WebSocket |
| API Key 模式 | 用户自行导入的 BYOK Key 池 |
| Key 存储 | Android Keystore + AES-GCM，密文位于 `noBackupFilesDir` |
| Key 数量上限 | 最多 50 个用户自有 Key |

### API Key 重要说明

- 仓库**不包含任何内置、备用或只读硬编码 API Key**。
- `ApiKeyPool` 管理的是用户在 App 中自行导入的 BYOK Key。
- 用户可以逐个启用、禁用、验证或删除 Key。
- Android App 在编译和运行时都不读取根目录 `.env`。
- `.env` 只供开发者手动运行本地 Python / PowerShell 辅助脚本时使用。

任何文档再次出现“内置 10 个 Key”“w1-w10 内置密钥”“Key 写在 `ApiKeyPool.kt`”等描述，都应视为旧架构残留并修正。

---

## 3. 开始任务前必须执行

1. 阅读本文件。
2. 阅读 `docs/planning/pr-execution-master-plan.md`。
3. 阅读当前 PR 的详细施工单。
4. 执行：

```powershell
git status --short
git branch --show-current
git log -5 --oneline
```

5. 读取任务文档列出的“必须先读文件”。
6. 在修改前列出计划修改文件、预期行为和验证命令。

---

## 4. 构建与运行

### Windows 构建

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

.\gradlew.bat --version
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

### 仅构建，不要求 adb 或设备

```powershell
.\run.ps1 -SkipInstall -NoLogcat
```

### 安装、启动与日志

```powershell
.\run.ps1
```

`run.ps1` 是便利脚本，不是唯一构建入口。公共构建流程必须能够直接使用仓库自带 Gradle Wrapper。

---

## 5. 目录结构

```text
AI-Skill-Roundtable/
├── app/
│   └── src/main/
│       ├── java/com/example/skillroundtable/
│       │   ├── data/          # Room 实体、DAO、数据库、Repository
│       │   ├── network/       # Gemini API、BYOK Key、遥测、Live WebSocket
│       │   ├── skill/         # Skill 加载与本地资料读取
│       │   └── viewmodel/     # 当前圆桌业务编排
│       ├── assets/skills/     # 20 个角色的 Skill 资产
│       └── assets/skills_config.json
├── docs/
│   ├── architecture/
│   ├── bugs/
│   ├── decisions/
│   ├── environment/
│   ├── planning/
│   ├── protocols/
│   └── skills/
├── tools/                     # ADB、截图和运行期调试工具
├── test/                      # 交互与工具链测试
├── workspace/
│   ├── tools/                 # 元数据、Embedding、头像等本地辅助脚本
│   └── tests/
├── .env.example               # 本地辅助脚本模板，不供 App 使用
├── AGENTS.md
└── README.md
```

---

## 6. 敏感信息处理规则

| 信息类型 | 正确位置 | 是否提交 Git |
|---|---|---|
| Android App BYOK Key | App 内导入；Keystore 加密后存入 `noBackupFilesDir` | ❌ |
| 本地辅助脚本 Key | 根目录 `.env` | ❌ |
| 模板占位符 | `.env.example` | ✅ |
| 签名文件 / 私钥 / 证书 | 开发者本机或安全 CI Secret | ❌ |

禁止：

- 将真实 Key 写入源码、Markdown、提交信息、日志样例或测试夹具。
- 将 Key 放入 `BuildConfig`、资源文件或 APK assets。
- 以“脱敏”“只读代码”“私人仓库”为理由提交真实密钥。
- 在交付报告中回显完整 Key。

---

## 7. 修改范围纪律

- 不修改当前 PR 范围外的业务、UI 或依赖。
- 不顺手升级 Kotlin、AGP、Compose、Room、Retrofit。
- 不通过吞异常、删除测试或降低断言让验证通过。
- 不保留两套互相冲突的新旧实现。
- 修改数据库实体时必须同步版本、Migration、Schema 和测试。
- 修改包名、Activity 或 applicationId 时，必须同步脚本和文档。
- 历史 ADR 可以保留历史背景，但必须明确状态，不能把旧实现写成当前事实。

---

## 8. 测试与验收

基础修改至少执行：

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
```

涉及资源、Manifest、Gradle 或完整集成时再执行：

```powershell
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

涉及数据库时必须执行 Migration Test；涉及 UI、设备、TTS 或运行期行为时，应使用真机或模拟器验收，并记录未覆盖场景。

所有交付报告必须区分：

- 已通过自动验证
- 已通过人工验证
- 未验证
- 因环境阻塞无法验证

禁止使用“100% 完成”“Zero Risk”“圆满完成”等没有证据支持的绝对结论。

---

## 9. 当前已知重点问题

1. **PR 02**：严格顺序圆桌、上下文一致性、Key Lease、错误分类与请求预算。
2. **PR 03**：默认正文遥测、敏感内容持久化、日志保留期限与云端 `store` 策略。
3. **PR 04**：Release 签名、正式包名、R8、CI、Secret Scan、Room Migration。
4. **PR 05**：README / AGENTS 最终统一、TTS 描述、第三方 Skill / 头像来源与 AI 模拟声明。

不要把这些问题描述成已经完成。

---

## 10. 核心文档

| 文档 | 路径 |
|---|---|
| 五阶段总控计划 | `docs/planning/pr-execution-master-plan.md` |
| PR 01 施工单 | `docs/planning/pr-01-reproducible-build-and-onboarding.md` |
| PR 02 施工单 | `docs/planning/pr-02-roundtable-key-orchestration.md` |
| PR 03 施工单 | `docs/planning/pr-03-privacy-telemetry-hardening.md` |
| PR 04 施工单 | `docs/planning/pr-04-release-ci-quality.md` |
| PR 05 施工单 | `docs/planning/pr-05-open-source-governance.md` |
| Android 编译指南 | `docs/environment/android-compilation-guide.md` |
| 系统架构 | `docs/architecture/system-architecture.md` |
| Gemini API 协议 | `docs/protocols/gemini-api.md` |
| 新增角色指南 | `docs/skills/how-to-add-new-character.md` |
| 工具说明 | [tools/README.md](tools/README.md) |
