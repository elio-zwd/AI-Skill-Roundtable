# PR 08：见域产品定义与体验设计多对话交接说明

> 总计划：`docs/planning/pr-08-jianyu-product-redesign-plan.md`
>
> 任务清单：`docs/planning/pr-08-jianyu-product-redesign-tasks.md`
>
> 工作笔记：`docs/planning/pr-08-product-definition-working-notes.md`
>
> 仓库：`https://github.com/elio-zwd/AI-Skill-Roundtable`

本文件用于多个 ChatGPT / Codex 对话之间通过 GitHub 可靠交接。不同对话不共享可靠实时记忆，所有状态以 GitHub 分支、Commit、PR、评论和 CI 为准。

---

## 1. 并行结论

规划 PR 合并后，可以同时开启五个独立对话：

- PR08-A：产品模型与术语；
- PR08-B：信息架构与核心交互；
- PR08-C：Skill 分类、发现与推荐；
- PR08-D：品牌与视觉设计系统；
- PR08-E：技术影响与 PR09 迁移评估。

A～E 均为文档、研究或设计任务，不修改生产代码，并使用互不重叠的独占路径。

PR08-F 负责最终整合，必须等 A～E 全部完成并经过用户审阅后再串行开启。

PR09 生产代码实现暂不启动。

---

## 2. 并行原则

1. 一个对话只负责一个任务、一个分支和一个 PR；
2. 五个并行分支必须从规划 PR 合并后的同一个 `main` SHA 创建；
3. 开始前检查全部开放 PR，确认独占路径没有被其他对话修改；
4. 每次写入前重新读取目标分支上的最新文件；
5. 不修改其他对话的独占路径；
6. 不修改生产代码、测试、Room、配置 Schema 或 Android 资源；
7. 不直接修改 `main`；
8. 不强制更新、删除或覆盖其他对话的分支；
9. 不假设其他对话的结论已经合并；
10. 需要引用其他并行任务时，只能引用已合并内容或明确的 PR / Commit；
11. 未经用户授权不得合并 PR；
12. 发现需要改变冻结契约时，在 PR 中提出变更提案，不直接覆盖总计划；
13. PR08-F 开始前必须确认 A～E 的最终状态和真实 Head SHA；
14. PR09 只能在 PR08-F 完成并获用户批准后开始。

---

## 3. 共享冻结契约

所有对话必须保持以下共识：

### 品牌

- 主品牌：见域；
- 口号：看见更多观点，打开认知边界；
- 组合显示：见域｜看见更多观点，打开认知边界。

### 产品价值

- 现实支持：处理生活、学习和工作中的具体事情；
- 思维拓展：从不同领域、人物、立场和思维模型重新理解问题；
- 两类可以在同一议题中双向跨界，不是互斥模式。

### 使用模式

- 单 Skill：解决明确问题或持续咨询；
- 多 Skill：协作推进复杂议题；
- 单可升级多，多中可指定单个 Skill 回应；
- 模式切换不丢失议题上下文。

### 产品对象

- Skill 是人物视角、专业顾问、任务助手和工作流能力的统一载体；
- 议题是持续容器；
- 圆桌是多 Skill 协作模式；
- 自由追问与“下一轮”并存；
- 成果包括判断、行动方案、知识笔记和可交付内容。

### 阶段边界

- PR08 只做产品、UX、Skill、品牌和技术迁移规格；
- PR09 才做生产代码实现。

---

## 4. 独占路径矩阵

| PR | 独占路径 | 禁止修改 |
|---|---|---|
| PR08-A | `docs/product/` | 其他 PR08 路径、生产代码 |
| PR08-B | `docs/design/ux/` | `docs/design/brand/`、其他 PR08 路径、生产代码 |
| PR08-C | `docs/skills/jianyu-*` | 现有 App assets、配置 Schema、其他 PR08 路径 |
| PR08-D | `docs/design/brand/` | `docs/design/ux/`、Compose 代码和资源 |
| PR08-E | `docs/architecture/jianyu-product-migration-assessment.md`、`docs/planning/pr-09-implementation-outline.md` | 生产代码、测试、数据库 |
| PR08-F | 最终正式文档路径 | 生产代码；开始前不得与 A～E 并行 |

确需修改冻结共享文档时，不直接写入。在当前 PR 描述中记录：

- 需要修改的文件；
- 原因；
- 对其他任务的影响；
- 建议由哪个整合 PR 处理。

---

## 5. 正确启动顺序

### 第一步：合并规划 PR

规划 PR 只包含：

- 产品定义工作笔记；
- PR08 总计划；
- PR08 任务清单；
- PR08 多对话交接说明。

规划 PR 合并前，不启动 A～E 的写入。

### 第二步：记录统一基线

规划 PR 合并后，记录：

```text
main SHA：<规划 PR 合并后的真实 SHA>
```

所有 A～E 分支都从该 SHA 创建。

### 第三步：同时开启五个对话

分别使用本文件第 8～12 节的 Prompt。

### 第四步：逐个审阅，不急于合并

每个 PR 都需要检查：

- 是否遵守独占路径；
- 是否改变冻结契约；
- 是否把目标设计写成当前已实现；
- 是否有未注明的假设；
- 是否给 PR08-F 留下明确输入。

### 第五步：合并 A～E

用户批准后，按冲突最小原则合并。由于路径独立，通常无强制顺序；若某 PR 引用另一 PR 的未合并内容，先合并被依赖项。

### 第六步：开启 PR08-F

PR08-F 从 A～E 全部合并后的最新 `main` 创建，负责冲突清理、最终规格和 PR09 正式拆分。

---

## 6. 每个新对话的统一启动要求

将对应 Prompt 发给新对话后，执行 AI 必须先：

1. 确认 GitHub 与 Superpowers 的真实可用能力；
2. 说明正在使用适用的 Superpowers 技能；若技能端点未暴露，明确使用等价人工流程；
3. 阅读 `README.md`、`AGENTS.md`、PR08 四份规划文档；
4. 检查开放 PR；
5. 确认规划 PR 已合并和目标 Base SHA；
6. 读取本任务全部相关实现、测试、文档和已有设计；
7. 输出计划、预计修改文件、冻结契约和风险；
8. 创建独立分支后再写入；
9. 每次写文件前重新读取目标分支最新版本；
10. 完成前使用等价的 `verification-before-completion` 流程；
11. 创建 Draft PR，不自动合并；
12. 输出给后续对话或本地 AI 的只读验收 Prompt。

---

## 7. 统一交付报告模板

```markdown
# PR08-X 交付报告

## 1. 仓库与分支
- 仓库：
- Base：
- 分支：
- Commit：
- PR：

## 2. 完成范围
- [x] ...
- [ ] ...（原因）

## 3. 修改文件
| 文件 | 用途 | 关键内容 |
|---|---|---|
| path | ... | ... |

## 4. 冻结契约核对
- 品牌：一致 / 提出变更
- 两类核心价值：一致 / 提出变更
- 单 / 多 Skill：一致 / 提出变更
- 跨界原则：一致 / 提出变更
- PR08 / PR09 边界：一致 / 提出变更

## 5. 关键决策
- 决策：
- 依据：
- 替代方案：
- 需要用户确认：

## 6. 验证
| 检查 | 结果 | 证据 |
|---|---|---|
| 文档链接 | PASS/FAIL | ... |
| 术语一致性 | PASS/FAIL | ... |
| git diff --check | PASS/FAIL/未执行 | ... |

## 7. 未验证与风险
- ...

## 8. 给 PR08-F 的输入
- 必须继承：
- 需要解决的冲突：
- 暂缓项：
```

---

## 8. PR08-A 新对话 Prompt

```text
你现在接手 AI-Skill-Roundtable 的 PR08-A：见域产品模型与术语契约。

仓库：
https://github.com/elio-zwd/AI-Skill-Roundtable

目标分支：
docs/pr-08a-product-model

前置条件：
PR08 规划文档 PR 已合并。先读取最新 main SHA；若规划 PR 尚未合并，不要写入。

必须先读取：
1. README.md
2. AGENTS.md
3. docs/planning/pr-execution-master-plan.md
4. docs/planning/pr-08-product-definition-working-notes.md
5. docs/planning/pr-08-jianyu-product-redesign-plan.md
6. docs/planning/pr-08-jianyu-product-redesign-tasks.md
7. docs/planning/pr-08-jianyu-parallel-handoff.md
8. docs/architecture/system-architecture.md
9. 当前会话、消息、角色和轮次相关实体或文档

工作流：
优先使用 Superpowers:writing-plans；完成前使用 Superpowers:verification-before-completion；准备 PR 时使用 Superpowers:finishing-a-development-branch。技能端点未暴露时明确使用等价人工流程。

目标：
把已确认的见域产品共识整理成稳定的产品对象、术语和边界。重点定义现实支持、思维拓展及双向跨界；单 Skill、多 Skill 及切换；问题、Skill、议题、咨询、圆桌、轮次、资料、行动和成果。

独占写入：
docs/product/

禁止：
- 不修改生产代码；
- 不修改其他 PR08 独占目录；
- 不更改品牌、口号、两类核心价值和两种使用模式；
- 不提前设计 Room Schema。

完成后：
创建 Draft PR，标题：docs: 定义见域产品模型与术语。不合并。输出统一交付报告和只读验收 Prompt。
```

---

## 9. PR08-B 新对话 Prompt

```text
你现在接手 AI-Skill-Roundtable 的 PR08-B：见域信息架构与核心交互设计。

仓库：
https://github.com/elio-zwd/AI-Skill-Roundtable

目标分支：
docs/pr-08b-information-architecture

前置条件：
PR08 规划文档 PR 已合并。必须从规划 PR 合并后的最新 main SHA 创建分支。

必须先读取：
README.md、AGENTS.md、PR08 四份规划文档、ui/App.kt、ui/navigation/、圆桌/角色/音频/设置页面和相关 UI 测试。

工作流：
使用 Superpowers:brainstorming 和 writing-plans；完成前使用 verification-before-completion；准备 PR 时使用 finishing-a-development-branch。不可调用时使用等价人工流程。

目标：
设计首页、快速提问、显式创建议题、Skill 发现、单 Skill 咨询、多 Skill 协作、单转多、多中指定单个回应、现实支持与思维拓展跨界、自由追问、下一轮、历史议题、资料、行动和成果的完整流程与状态矩阵。

独占写入：
docs/design/ux/

冻结要求：
自由追问是一级操作；下一轮是结构化推进；两类价值可以在同一议题中双向切换；不强制所有用户先填复杂表单。

禁止：
- 不修改生产代码；
- 不修改品牌视觉目录；
- 不假设全部候选 Skills 已接入；
- 不以漂亮页面替代交互状态。

完成后：
创建 Draft PR，标题：docs: 设计见域信息架构与核心流程。不合并。输出统一交付报告和只读验收 Prompt。
```

---

## 10. PR08-C 新对话 Prompt

```text
你现在接手 AI-Skill-Roundtable 的 PR08-C：见域 Skill 分类、发现与推荐模型。

仓库：
https://github.com/elio-zwd/AI-Skill-Roundtable

目标分支：
docs/pr-08c-skill-taxonomy

前置条件：
PR08 规划文档 PR 已合并。必须从同一规划基线创建分支。

必须先读取：
README.md、AGENTS.md、PR08 四份规划文档、skills_config.json、现有代表性 SKILL.md、docs/skills/how-to-add-new-character.md，以及 docs/skills-catalog 研究分支中的候选目录和风险记录。

工作流：
使用 Superpowers:brainstorming 和 writing-plans；完成前使用 verification-before-completion；准备 PR 时使用 finishing-a-development-branch。不可调用时使用等价人工流程。

目标：
建立人物视角、专业顾问、任务助手、工作流能力四类 Skill；现实支持/思维拓展主价值；多标签、风险、输入输出、单/多 Skill 适配；搜索、筛选、推荐、跨界推荐和默认阵容规则；映射现有 20 位角色与候选 22 个 Skills。

独占写入：
- docs/skills/jianyu-skill-taxonomy.md
- docs/skills/jianyu-skill-discovery-and-recommendation.md
- docs/skills/jianyu-skill-catalog-mapping.md

禁止：
- 不复制第三方 Skill 正文；
- 不修改 App assets 或配置 Schema；
- 不把人物名气等同于专业可靠性；
- 不宣称候选目录已通过全部许可核验。

完成后：
创建 Draft PR，标题：docs: 建立见域 Skill 分类与推荐模型。不合并。输出统一交付报告和只读验收 Prompt。
```

---

## 11. PR08-D 新对话 Prompt

```text
你现在接手 AI-Skill-Roundtable 的 PR08-D：见域品牌与视觉设计系统。

仓库：
https://github.com/elio-zwd/AI-Skill-Roundtable

目标分支：
design/pr-08d-jianyu-brand-system

前置条件：
PR08 规划文档 PR 已合并。必须从规划 PR 合并后的最新 main SHA 创建分支。

必须先读取：
README.md、AGENTS.md、PR08 四份规划文档、当前 ui/theme、主要页面实现或截图、此前 A/B/C/D 视觉预览和用户已经确认的“见域”品牌共识。

工作流：
使用 Superpowers:brainstorming 和 writing-plans；完成前使用 verification-before-completion；准备 PR 时使用 finishing-a-development-branch。不可调用时使用等价人工流程。

目标：
建立见域品牌解释、Logo 方向、视觉设计 Token、组件语言和关键页面规格。视觉比例遵循 70% 高级生产力工具、20% 智库编辑部、10% AI 动态反馈。需要表达现实支持、思维拓展、单 Skill、多 Skill 和跨界切换，但不能形成两套割裂产品。

独占写入：
docs/design/brand/

禁止：
- 不修改 Compose 代码和 Android 资源；
- 不修改 UX 独占目录；
- 不使用满屏霓虹或难以落地的复杂仪表盘；
- 不把生成图片中的错字作为正式文案；
- 不未经用户确认锁定最终 Logo。

完成后：
创建 Draft PR，标题：design: 建立见域品牌与视觉设计系统。不合并。输出统一交付报告和只读验收 Prompt。
```

---

## 12. PR08-E 新对话 Prompt

```text
你现在接手 AI-Skill-Roundtable 的 PR08-E：见域产品迁移技术评估与 PR09 初步拆分。

仓库：
https://github.com/elio-zwd/AI-Skill-Roundtable

目标分支：
docs/pr-08e-technical-migration

前置条件：
PR08 规划文档 PR 已合并。必须从规划 PR 合并后的最新 main SHA 创建分支。

必须先读取：
README.md、AGENTS.md、PR08 四份规划文档、ui/App.kt、navigation、全部页面域、RoundtableViewModel、调度器、Room 实体/DAO/Database/Migration、skills_config.json、Skill 加载器、音频数据关系、测试和 CI。

工作流：
使用 Superpowers:writing-plans；需要调查异常或不一致时使用 systematic-debugging 的等价流程；完成前使用 verification-before-completion；准备 PR 时使用 finishing-a-development-branch。

目标：
只读审计当前实现，评估议题模型、单/多 Skill、Skill Schema、资料/行动/成果、音频兼容、历史数据迁移和品牌导航改造。输出可回滚的迁移建议和 PR09 初步依赖图，但不得写生产代码，也不得把暂定方案写成已批准。

独占写入：
- docs/architecture/jianyu-product-migration-assessment.md
- docs/planning/pr-09-implementation-outline.md

禁止：
- 不修改生产代码、测试或数据库；
- 不忽略旧会话、音频、自定义角色和 Room Migration；
- 不建议一次性重写核心调度；
- 不声称未执行的测试已通过。

完成后：
创建 Draft PR，标题：docs: 评估见域产品迁移与实现边界。不合并。输出统一交付报告和只读验收 Prompt。
```

---

## 13. PR08-F 启动 Prompt 模板

PR08-F 暂不立即启动。A～E 全部完成后，使用以下模板并补齐真实 PR 编号、Commit 和合并 SHA：

```text
你现在接手 AI-Skill-Roundtable 的 PR08-F：见域最终产品与体验规格整合。

仓库：
https://github.com/elio-zwd/AI-Skill-Roundtable

目标分支：
docs/pr-08f-final-product-spec

Base：
A～E 全部合并后的最新 main SHA：<填写真实 SHA>

必须先读取：
README.md、AGENTS.md、PR08 四份规划文档，以及 PR08-A～E 的 PR、Diff、评论、Commit 和交付报告。

目标：
解决术语、流程、分类、视觉和技术评估之间的冲突；输出正式产品定义、PRD、最终信息架构、状态矩阵、设计系统验收规格，以及 PR09 正式 Plan、Tasks、Handoff。

要求：
- 先列冲突清单，不直接选择；
- 影响产品行为的冲突必须请求用户确认；
- 不修改生产代码；
- 不合并；
- 输出给 PR09 多对话执行的只读交接 Prompt。
```

---

## 14. 给本地验收 AI 的统一只读 Prompt

```text
请只读验收 AI-Skill-Roundtable 的 PR08 文档 PR。

要求：
1. 拉取远端最新代码；
2. 检出对应 PR 分支；
3. 不修改文件、不提交、不推送、不合并；
4. 阅读 AGENTS.md、PR08 总计划、任务清单、交接文档和当前 PR 修改；
5. 检查独占路径是否被遵守；
6. 检查品牌、两类核心价值、跨界原则、单/多 Skill 和 PR08/PR09 边界是否一致；
7. 检查当前事实、目标设计和待确认内容是否明确区分；
8. 检查 Markdown 链接、文件路径、术语和示例是否自洽；
9. 执行 git diff --check；
10. 记录操作系统、Git 版本、命令、结果和关键日志；
11. 将失败项、冲突、复现步骤和可能原因反馈给远端开发对话。
```
