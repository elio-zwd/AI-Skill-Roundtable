# 见域｜AI-Skill-Roundtable（当前仓库）

> 看见更多观点，打开认知边界。

本仓库包含一套可运行的 Android 多角色聊天应用基线。Android App 的用户可见名称、`namespace`、`applicationId` 和 Kotlin 包路径已经迁移为“见域 / `com.elio.jianyu`”；持续议题、阶段、资料成果和完整见域体验仍在 PR09 后续阶段实施。仓库名目前仍为 `AI-Skill-Roundtable`。

---

## 1. 当前可运行 Android 基线

当前工程使用以下技术标识：

```text
仓库：elio-zwd/AI-Skill-Roundtable
App 名称：见域
namespace / applicationId：com.elio.jianyu
Kotlin 包路径：app/src/main/java/com/elio/jianyu/
默认版本名：0.1.0
```

当前已经实现的主要能力包括：

- 原生 Android、Kotlin、Jetpack Compose 和 Material 3；
- Room 本地会话和消息数据；
- 多人物型 Skill 的问答与圆桌协作基线；
- Gemini REST、Interactions 和 Live WebSocket；
- 联网搜索、Markdown 渲染、停止、继续和失败重试；
- 用户自行导入的 BYOK Key 池，最多 50 个；
- Android Keystore + AES-GCM 的 Key 本地保护；
- 流式音频、TTS 与本地音频管理；
- 遥测设置和当前 Compose Route / Screen / UiState 架构。

当前 Android App **尚未实现**见域目标中的持续议题、阶段时间线、资料与成果双层管理、正式“推进议题”流程、最终品牌视觉和新数据模型。

### 当前工程版本

| 项目 | 当前值 |
|---|---|
| Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Navigation Compose | 2.8.4 |
| Room | v5 |
| JDK | 17 |
| Gradle Wrapper | 8.14 |
| Compile / Target SDK | 35 |
| Min SDK | 26 |
| applicationId | `com.elio.jianyu` |

---

## 2. 目标产品：见域

> 见域是一款面向个人的多智能体思考与行动工作台。用户围绕持续议题，调用人物视角、专业顾问、任务助手和工作流能力，通过自由追问与分阶段推进，沉淀判断、行动方案和知识成果。

当前已完成的技术身份：

```text
App 名称：见域
applicationId / namespace：com.elio.jianyu
```

仍属于未来目标：

```text
目标仓库名：jianyu-workbench
目标官网：jianyu.my-elio.online
```

仓库名和官网尚未迁移，不能视为当前工程状态。

### 2.1 核心产品关系

```text
问题是入口
→ Skill 是能力载体
→ 单 Skill / 多 Skill 是使用模式
→ 议题持续承载背景、资料、过程与成果
→ 自由追问 / 推进议题 / 专业工作流
→ 判断 / 行动方案 / 知识成果
```

### 2.2 两类核心价值

正式名称为：

- **现实支持**：处理生活、学习和工作中的具体事情，形成计划、沟通、研究、决策或可交付成果；
- **思维拓展**：引入不同人物、领域、立场、反方意见和思维模型，发现盲区并拓展认知边界。

两类价值：

- 可以跳过；
- 可以单独选择或组合；
- 可以在同一议题中反复切换；
- 不是永久标签；
- 不自动把同一议题拆成两条主线。

“生活与工作”“思维与视角”“现实行动”不是正式分类名称。

### 2.3 单 Skill 与多 Skill

- 单 Skill 与多 Skill 并列；
- 单 Skill 可邀请其他 Skill，升级为多 Skill；
- 多 Skill 中点名某个 Skill 只产生临时定向回答；
- 临时定向回答不自动退出多 Skill；
- Skill 增删不删除历史回答和成果；
- 系统可以推荐 Skill，但必须由用户确认。

V1 只提供官方内置 Skill，不支持用户创建、导入第三方 Skill 或公开市场。原有人物型 Skill 保留，并纳入研究目录全部 25 个条目；去重后官方候选约 44 个，最终清单由 PR08-C 核验来源、许可、安全和移动端可行性。

### 2.4 议题、阶段与自由追问

- **议题**是持续容器；
- **阶段**是同一议题中的明确推进节点；
- **自由追问**继续当前阶段；
- 一个议题保持单一主线；
- 新阶段不会覆盖旧阶段；
- 需要独立主线时由用户显式创建或关联新议题。

### 2.5 推进议题

正式入口名称为 **推进议题**。

- 入口始终可用；
- 阶段成熟时可以增强提示；
- 不强迫用户先完成当前阶段；
- 不等于让全部 Skill 重复回答；
- “下一轮”只作为历史术语。

三步确认：

1. 选择推进方向；
2. 选择具体措施或自定义目标；
3. 确认下一阶段摘要。

只有最终确认后才创建新阶段。

**思维拓展**措施：

- 引入反方意见；
- 查找遗漏视角；
- 检查关键假设；
- 比较不同立场；
- 深入某个问题；
- 自定义目标。

**现实支持**措施：

- 明确下一步；
- 形成执行计划；
- 分析执行阻碍；
- 生成成果交付；
- 设置检查节点；
- 自定义目标。

新阶段默认继承议题背景、已确认资料、已保存成果、当前 Skill 阵容、判断、分歧和行动项；用户可以调整目标、Skill、资料和输出形式。

### 2.6 资料、成果与圆桌结果

资料与成果采用：

- 议题内管理；
- 全局资料库和成果库汇总；
- 保留来源、所属议题和阶段关系；
- 音频作为成果的一种输出形式。

圆桌不投票裁决真理。结果应包含：

- 共识；
- 分歧；
- 适用条件；
- 明确建议；
- 下一步。

系统可以提供推荐与理由，用户保留最终决定权。

---

## 3. 本地数据与安全目标

见域 V1 的目标行为：

- 本地保存，不做账号和自动云同步；
- 支持手动加密导入导出；
- 本地库由设备安全密钥保护；
- 应用锁可选，支持生物识别或应用 PIN；
- 非空数据库导入先预览差异；
- 相同数据自动去重，冲突数据由用户选择；
- 替换当前库前创建并验证恢复快照；
- 导入先隔离校验，正式写入采用原子操作；
- 失败不得修改当前库，并展示失败阶段和可读错误；
- 快照加密、创建后验证、恢复前再次验证；
- 快照保留到用户主动删除；
- 容量不足只提醒，不自动清理；
- 回退前创建“回退前快照”；
- 支持手动快照和备注；
- 加密导出默认不包含设备绑定恢复快照。

这些是**目标产品行为和数据安全目标**，尚未在当前 Android App 中实现；具体 Room Schema、算法、文件格式、容量阈值和事务实现由 PR08-E 评估、PR08-F 冻结、PR09 实施。

当前 App 尚未正式发布。新包 `com.elio.jianyu` 使用独立 Android UID 和私有沙箱，不读取旧包 `com.elio.skillroundtable` 的 Room、Keystore、偏好设置、私有文件或本地会话；用户需要在新包中重新配置 API Key。项目不提供跨包数据桥接。

---

## 4. PR08 / PR09 顺序

```text
PR #20：已合并
→ PR #21：已合并
→ PR #22：已合并
→ PR08-A～E：已完成
→ PR08-F：已完成并获得用户批准
→ PR09：正在分阶段实施生产代码、数据模型、品牌和技术标识迁移
```

PR08 只做研究、规格、设计和迁移评估，不修改 Android 生产代码。PR09 按独立任务和 Draft PR 分阶段实施，并在完成前执行构建、测试、CI 和只读验收。

项目开发工作流适配说明：[`tools/ai/superpowers/README.md`](tools/ai/superpowers/README.md)。该目录仅供开发时参考，不是 Android 构建或运行时依赖。

---

## 5. 当前内置人物型 Skill

当前 Android 基线包含约 20 个人物型 Skill：

| 人物 | 资产目录 |
|---|---|
| 埃隆·马斯克 | `elon-musk-skill-main/` |
| 理查德·费曼 | `feynman-skill-main/` |
| 查理·芒格 | `munger-skill-main/` |
| 纳瓦尔 | `naval-skill-main/` |
| 史蒂夫·乔布斯 | `steve-jobs-skill-main/` |
| 纳西姆·塔勒布 | `taleb-skill-main/` |
| 张雪峰 | `zhangxuefeng-skill-main/` |
| 安德烈·卡帕斯 | `karpathy-skill/` |
| 张一鸣 | `zhang-yiming-skill/` |
| 保罗·格雷厄姆 | `paul-graham-skill/` |
| 伊利亚·苏茨克维尔 | `ilya-sutskever-skill/` |
| 唐纳德·特朗普 | `trump-skill/` |
| 吉米·唐纳森（MrBeast） | `mrbeast-skill/` |
| 孙宇晨 | `sun-yuchen-perspective/` |
| 西格蒙德·弗洛伊德 | `freud-skill/` |
| X 增长导师 | `x-mentor-skill/` |
| 峰哥亡命天涯 | `fengge-skill/` |
| 赵长鹏（CZ） | `cz-skill/` |
| 段永平 | `duan-yongping-skill/` |
| 蒂姆·库克 | `tim-cook-skill/` |

人物模拟应清楚说明其为 AI 生成视角，不代表本人；来源、时效和适用边界由后续 Skill 规格进一步收敛。

---

## 6. 环境要求

| 工具 | 要求 |
|---|---|
| OS | Windows 10 x64 或其他支持 Android 构建的系统 |
| Shell | PowerShell 7（推荐） |
| JDK | JDK 17 |
| Android SDK | Platform 35 与对应 Build Tools |
| Gradle | 使用仓库 Gradle Wrapper 8.14 |
| API Key | 用户在 Android 客户端运行时导入 Gemini API Key |

---

## 7. 安装与启动

### 一键运行

```powershell
.\run.ps1
```

脚本会检查 JDK 和 adb 环境，编译、安装并启动 `com.elio.jianyu/.MainActivity`，同时输出日志。

### 手动构建

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

Debug APK：

```text
app\build\outputs\apk\debug\app-debug.apk
```

### 导入 Gemini API Key

1. 启动 App；
2. 打开 API Key 管理入口；
3. 输入单个或批量 Key；
4. Key 由 Android Keystore + AES-GCM 保护并保存到 `noBackupFilesDir`；
5. 界面只显示掩码。

Android App 编译和运行时不读取根目录 `.env`。`.env` 仅供开发者手动运行本地辅助脚本。

---

## 8. 构建与测试

```powershell
.\gradlew.bat clean
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
```

涉及 UI、设备、音频、系统返回或 Activity 重建时，还应运行相应 Instrumentation Test 或真机回归，并记录设备和 Android 版本。

---

## 9. 安全说明

- 仓库不包含内置生产 Key；
- 不要提交 `.env`、Keystore、签名密码、证书或真实用户数据；
- Android App 只管理用户自行导入的 BYOK Key；
- Release 签名通过本地 `keystore.properties` 或完整的 `RELEASE_*` 环境变量配置；
- 文档中的目标安全行为不代表当前实现已经通过完整安全审计；
- 不使用“绝对安全”“零风险”或“永不丢失”等未经验证的承诺。

---

## 10. 目录说明

```text
AI-Skill-Roundtable/
├── app/                  # 当前 Android 应用
├── docs/
│   ├── architecture/     # 当前架构与迁移评估
│   ├── planning/         # PR 计划、规格、任务和交接
│   ├── testing/          # 回归清单与验收说明
│   ├── environment/      # 构建环境
│   ├── protocols/        # API 与协议
│   └── skills/           # Skill 资料与研究目录
├── tools/                # 运行时辅助工具
├── test/                 # 自动化交互工具链测试
└── workspace/            # 构建期资产处理辅助区
```

PR09 后续计划中的新代码和文档目录只有在对应任务实际创建后，才可描述为当前工程事实。

---

## 11. 规划文档

- [产品定义工作笔记](docs/planning/pr-08-product-definition-working-notes.md)
- [见域产品定义与体验设计总计划](docs/planning/pr-08-jianyu-product-redesign-plan.md)
- [见域产品定义与体验设计任务清单](docs/planning/pr-08-jianyu-product-redesign-tasks.md)
- [见域产品定义与体验设计多对话交接](docs/planning/pr-08-jianyu-parallel-handoff.md)
- [推进议题契约](docs/planning/pr-08-jianyu-issue-advancement-planning.md)
- [产品规格审阅稿](docs/planning/pr-08-jianyu-product-spec-review-draft.md)
- [第 1～62 题决策索引](docs/planning/pr-08-jianyu-product-spec-decision-index.md)
- [第 56～61 题补充决定](docs/planning/pr-08-jianyu-product-spec-supplement-56-61.md)
- [第 62 题补充决定](docs/planning/pr-08-jianyu-product-spec-supplement-62.md)
- [PR09-01 应用身份迁移计划](docs/planning/pr-09-01-jianyu-app-identity-plan.md)

---

## 12. 当前状态说明

已经完成：

- App 用户可见名称迁移为“见域”；
- `namespace`、`applicationId` 和 Kotlin 包路径迁移为 `com.elio.jianyu`；
- 新旧应用身份隔离测试和静态门禁。

仍待后续 PR09 任务实施：

- 新 FQCN Room Schema 正式生成、CI 和构建门禁同步；
- 议题、阶段、推进议题、资料库和成果库；
- 约 44 个官方候选 Skill 的正式接入；
- 最终品牌 Logo、页面视觉和官网；
- 新数据模型、导入导出与恢复快照实现；
- 仓库名迁移为 `jianyu-workbench`。
