# AGENTS.md — AI 智囊圆桌（AI-Skill-Roundtable）

> AI 代理工作规范。进入仓库后先阅读本文件，再阅读距离目标文件最近的 `AGENTS.md`、总控计划和当前任务施工单。更具体目录中的规则优先。

---

## 1. 项目与当前阶段

**AI 智囊圆桌**是一款原生 Android 多角色聊天应用，包含 Room 本地会话、Gemini REST / Interactions / Live WebSocket、联网搜索、Markdown、BYOK Key 池、遥测与音频管理。

当前可信开发阶段：

```text
PR01～PR05：业务正确性、隐私、发布与治理基础
→ PR06：音频相关独立工作
→ PR07：Compose UI 基础架构重构
→ PR08：UI/UX 视觉重设计（后续阶段）
```

PR07 只建立结构、主题、导航、Route / Screen / Component / UiState 边界和回归门禁，**没有重新设计视觉**。PR08 才允许逐屏进行视觉改版。

---

## 2. 当前可信工程事实

| 项目 | 当前值 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| 导航 | Navigation Compose 2.8.4 |
| 数据库 | Room v5 |
| JDK | JDK 17 |
| Gradle | Wrapper 8.14 `-bin` |
| Compile / Target SDK | 35 |
| Min SDK | 26 |
| 网络 | Retrofit、OkHttp、WebSocket |
| API Key | 用户自行导入的 BYOK Key 池，最多 50 个 |
| Key 存储 | Android Keystore + AES-GCM，密文位于 `noBackupFilesDir` |

### API Key 不可变事实

- 仓库不包含内置、备用或只读硬编码生产 Key。
- `ApiKeyPool` 只管理用户在 App 内自行导入的 Key。
- Android App 编译和运行时都不读取根目录 `.env`。
- `.env` 仅供开发者手动运行本地辅助脚本。
- 文档中出现“内置 10 个 Key”“w1-w10 内置密钥”等描述时，应视为旧架构残留。

---

## 3. 开始任务前必须执行

1. 阅读根目录 `AGENTS.md`。
2. 阅读目标目录下最近的 `AGENTS.md`。
3. 阅读 `docs/planning/pr-execution-master-plan.md` 和当前任务施工单。
4. 执行：

```powershell
git status --short
git branch --show-current
git log -5 --oneline
```

5. 检查开放 PR、目标 Base SHA、相关 CI 与评论。
6. 读取调用链、关联测试、配置和文档，不得只读取单个目标文件。
7. 修改前列出预计文件、行为冻结点、验证命令和主要风险。
8. 从最新目标基线创建独立分支，不直接修改 `main`。

---

## 4. 目录结构与职责

```text
app/src/main/java/com/elio/skillroundtable/
├── MainActivity.kt                 # Android Activity 入口，只挂载主题与 MainAppContent
├── data/                           # Room 实体、DAO、数据库、Repository
├── network/                        # Gemini、BYOK Key、Live WebSocket
├── telemetry/                      # 遥测、脱敏、云端 Interaction 设置
├── skill/                          # Skill 资产读取
├── viewmodel/                      # UI 与业务编排桥接
└── ui/
    ├── AGENTS.md                   # UI 目录更具体规则
    ├── App.kt                      # 顶层 Scaffold、NavHost 组装、底部导航
    ├── LegacyUiTokens.kt           # 仅兼容别名；真实颜色值只在 theme 中维护
    ├── navigation/                 # AppDestination、NavHost、顶层/二级导航契约
    ├── theme/                      # 唯一全局颜色、主题、形状、间距定义
    ├── components/                 # 跨页面通用展示组件
    └── screens/
        ├── roundtable/             # 圆桌 Route / Screen / Components / UiState
        ├── characters/             # 智囊 Route / Screen / Components / UiState
        ├── library/                # 音频库 Route / Screen / Components / UiState
        └── settings/               # API Key、遥测 Route / Screen / Components / UiState
```

文档目录：

```text
docs/
├── architecture/                  # 当前架构与稳定接口
├── planning/                      # PR 计划、任务和交接
├── testing/                       # 回归清单与验收说明
├── environment/                   # 构建环境
├── protocols/                     # API/协议
└── skills/                        # 角色扩展说明
```

---

## 5. UI 架构规则

目标文件位于 `ui/` 时，还必须遵守 `app/src/main/java/com/elio/skillroundtable/ui/AGENTS.md`。

核心规则：

- `MainActivity.kt` 只保留 Activity 入口，建议不超过约 80 行。
- `ui/App.kt` 只负责顶层导航和页面 Route 组装，不保存页面专属 Dialog、Drawer、Toast 或业务状态。
- `Route` 收集 Flow、调用 ViewModel/Repository/单例服务、处理页面副作用。
- `Screen` 只接收不可变 `UiState` 与事件回调，不查找全局 ViewModel。
- `Components` 只负责展示和局部交互，不访问其他页面内部实现。
- 页面域不得跨包引用其他页面域的内部组件。
- `navigation/` 与 `theme/` 不得包含页面专属逻辑。
- 全局主题颜色只在 `ui/theme/` 定义；`LegacyUiTokens.kt` 只能做别名，禁止新增颜色值。
- 不保留同一页面的新旧两套入口、导航或重复 Composable。
- 新抽象必须有真实调用方和测试，不创建空壳接口。
- 已存在的 `testTag` 属于稳定测试契约；修改前必须同步测试并说明兼容影响。

---

## 6. PR08 视觉改版边界

PR08 可以修改：

- 纯 `Screen` 和 `Components` 的布局、排版、层级、视觉组件和动画；
- `ui/theme/` 中的颜色、Typography、Shapes、Spacing；
- 页面内可访问性、响应式布局和视觉状态表达；
- 必要的新视觉组件及其 Compose UI Test。

PR08 不得随意修改：

- `AppDestination.route`、顶层/二级目的地分类和返回路径；
- Route 与 ViewModel 的业务调用语义；
- `UiState` / Event 对现有业务状态的含义；
- SSE、停止、继续、失败重试、TTS、Key、遥测、音频和 Room 行为；
- Room Schema、网络协议、API Key 安全存储；
- 稳定 `testTag`。

详细边界见 `docs/architecture/pr-08-ui-design-stable-interfaces.md`。

---

## 7. 修改范围纪律

- 只修改完成当前需求所必需的文件。
- 不顺手升级 Kotlin、AGP、Compose、Navigation、Room、Retrofit。
- 不以删除功能代替修复，不吞异常，不降低断言。
- 不保留两套互相冲突的新旧实现。
- 修改数据库实体时同步版本、Migration、Schema 和测试。
- 修改包名、Activity 或 `applicationId` 时同步脚本、Manifest、CI 和文档。
- 历史 ADR 可保留背景，但必须明确历史状态，不能写成当前事实。
- 发现无关业务 Bug 时记录并另开 PR，不在结构或视觉 PR 中顺带修复。

---

## 8. 敏感信息处理

| 信息类型 | 正确位置 | 是否提交 Git |
|---|---|---|
| Android App BYOK Key | App 内导入；Keystore 加密后存入 `noBackupFilesDir` | 否 |
| 本地辅助脚本 Key | 根目录 `.env` | 否 |
| 模板占位符 | `.env.example` | 是 |
| 签名文件、私钥、证书 | 本机或安全 CI Secret | 否 |

禁止将真实 Key 写入源码、Markdown、提交信息、日志样例、测试夹具、`BuildConfig`、资源或 assets。交付报告不得回显完整 Key。

---

## 9. 构建、测试与验证

Windows 基础环境：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat --version
```

基础修改至少执行：

```powershell
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
```

UI、资源、Manifest、Gradle 或完整集成修改执行：

```powershell
.\gradlew.bat clean
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat connectedDebugAndroidTest
pwsh.exe -File .\tools\check-secrets.ps1 -IncludeHistory
git diff --check
git status --short
```

涉及 Room 时必须核对 Schema 与 Migration Test；涉及 UI、设备、TTS、系统返回或 Activity 重建时必须使用真机或模拟器，并记录设备、Android 版本和未覆盖场景。

交付报告必须区分：

- 已实际执行并通过；
- GitHub CI 已通过；
- 仅静态检查；
- 因环境阻塞未执行；
- 等待真机验收。

禁止使用“100% 完成”“Zero Risk”“圆满完成”等没有证据支持的绝对结论。

---

## 10. GitHub Actions 查询与重跑纪律

1. 先读取 PR 元数据，锁定 Base、Head 和状态。
2. 每个新 Head SHA 最多主动查询一次关联 Workflow Run。
3. 全部 Workflow 成功后立即停止，不继续下载日志或 Artifact。
4. 只有失败或取消时才读取 Job；只读取失败 Job 的步骤摘要。
5. 步骤摘要不足时才读取该失败 Job 完整日志。
6. Artifact 仅在本地验收明确需要 APK、测试报告或 Schema 时读取。
7. 遇到 `403`、`429`、abuse detection 或 secondary rate limit 后停止调用并记录，不连续试探。
8. 只有 Runner、网络或服务端瞬时故障才允许直接重跑；代码问题必须先修复并产生新 Head。
9. 不为触发 CI 创建空提交或无业务意义文件。

---

## 11. Commit、PR 与交接

- Commit 标题使用“英文类型: 中文描述”。
- Commit 保持原子性，不自动添加 `Co-Authored-By`。
- PR 描述至少包含背景、实现、修改文件、验证、风险、本地验收和回滚建议。
- 未经用户明确授权不得合并、删除他人分支或强制更新分支。
- 一个对话只负责一个任务、一个分支和一个 PR。
- 多对话通过 Issue、Commit、PR 描述和评论交接，不依赖口头记忆。
- 完成后提供本地 AI 只读验收 Prompt，要求不修改、不提交、不推送、不合并。

---

## 12. 核心文档

| 文档 | 路径 |
|---|---|
| 总控计划 | `docs/planning/pr-execution-master-plan.md` |
| PR07 总计划 | `docs/planning/pr-07-ui-foundation-refactor-plan.md` |
| PR07 任务清单 | `docs/planning/pr-07-ui-foundation-refactor-tasks.md` |
| PR07 多对话交接 | `docs/planning/pr-07-ui-foundation-parallel-handoff.md` |
| 系统架构 | `docs/architecture/system-architecture.md` |
| PR08 稳定接口 | `docs/architecture/pr-08-ui-design-stable-interfaces.md` |
| UI 最终回归清单 | `docs/testing/pr-07-ui-regression-checklist.md` |
| Android 编译指南 | `docs/environment/android-compilation-guide.md` |
| Gemini API 协议 | `docs/protocols/gemini-api.md` |
| 新增角色指南 | `docs/skills/how-to-add-new-character.md` |
