# PR08：见域产品定义与体验设计多对话交接说明

> 总计划：`docs/planning/pr-08-jianyu-product-redesign-plan.md`
>
> 任务清单：`docs/planning/pr-08-jianyu-product-redesign-tasks.md`
>
> 产品输入：`docs/planning/pr-08-product-definition-working-notes.md`
>
> 当前仓库：`https://github.com/elio-zwd/AI-Skill-Roundtable`
>
> 目标仓库：`https://github.com/elio-zwd/jianyu-workbench`

本文件用于多个 ChatGPT / Codex 对话之间通过 GitHub 可靠交接。不同对话不共享可靠实时记忆，所有状态以 GitHub 分支、Commit、PR、评论和 CI 为准。

---

## 1. 当前顺序

现在不能直接启动 PR08-A～E。必须先按以下顺序处理：

```text
PR #20 产品定义与总规划
→ PR #21 议题阶段推进结构
→ PR #22 第 1～62 题规格收敛
→ 三者全部进入最新 main
→ 记录统一 main SHA
→ PR08-A～E 从同一 SHA 并行
→ PR08-F 串行整合
→ 用户批准后 PR09
```

品牌迁移预留分支 `docs/pr-08-brand-migration-planning` 目前只保存决策。前置 PR 完成后，应从最新 `main` 建立干净分支并只移植规划内容，不携带旧堆叠历史。

---

## 2. 共享冻结契约

### 品牌

```text
产品 / App：见域
口号：看见更多观点，打开认知边界
仓库目标：jianyu-workbench
官网目标：jianyu.my-elio.online
applicationId 目标：com.elio.jianyu
```

当前 App 尚未正式发布：

- 不保留旧 App；
- 不迁移旧包数据；
- 不设计旧 Room、Keystore、设置、私有文件或会话桥接；
- 后续按全新应用身份发布。

### 产品价值

- 现实支持；
- 思维拓展；
- 两类可跳过、单独或组合使用；
- 两类可在同一议题中双向切换，不是永久标签。

### 使用模式

- 单 Skill；
- 多 Skill；
- 单可邀请其他 Skill 升级为多；
- 多中点名某个 Skill 只产生临时定向回答；
- Skill 增删不删除历史和成果。

### 产品对象

- 问题是入口；
- Skill 是能力载体；
- 议题是持续容器；
- 阶段是议题内推进节点；
- 圆桌是多 Skill 协作形式；
- 自由追问继续当前阶段；
- “推进议题”进入新阶段目标；
- 成果包括判断、行动方案、知识笔记和可交付内容。

### Skill

- V1 只提供官方内置 Skill；
- 不支持用户创建、导入或公开市场；
- 原有人物 Skill 全部保留；
- 纳入研究目录全部 25 个条目；
- 去重后约 44 个官方候选；
- 推荐必须给理由并由用户确认。

### 圆桌与安全

- 圆桌不投票裁决；
- 输出共识、分歧、适用条件、建议和下一步；
- 高风险问题分级响应，不完全禁止；
- 不作诊断、法律结论或收益保证；
- 原规避 AIGC 检测类转化为合规的写作自然化能力。

### 阶段边界

- PR08 只做研究、规格、设计和迁移评估；
- PR09 才修改生产代码、Room、资源、包名、仓库设置和服务器；
- PR08-F 完成且用户批准前，不启动 PR09。

---

## 3. 并行原则

1. 一个对话只负责一个任务、分支和 PR；
2. A～E 必须从同一统一 `main` SHA 创建；
3. 开始前检查开放 PR 与相关近期 Commit；
4. 每次写入前重新读取目标分支最新文件；
5. 不修改其他对话独占路径；
6. 不修改生产代码、测试、Room、配置、Android 资源、仓库设置、DNS 或服务器；
7. 不直接修改 `main`；
8. 不强制更新、删除或覆盖其他分支；
9. 不假设其他对话结论已合并；
10. 跨 PR 引用必须使用具体 PR、Commit 或已合并文档；
11. 改变冻结契约时提交变更提案，不直接覆盖；
12. 未经用户授权不得合并；
13. PR08-F 不得与 A～E 并行；
14. PR09 只在 PR08-F 获批后开始。

---

## 4. 独占路径

| PR | 独占路径 |
|---|---|
| PR08-A | `docs/product/` |
| PR08-B | `docs/design/ux/` |
| PR08-C | `docs/skills/jianyu-*` |
| PR08-D | `docs/design/brand/` |
| PR08-E | `docs/architecture/jianyu-product-migration-assessment.md`、`docs/planning/pr-09-implementation-outline.md` |
| PR08-F | 最终产品、设计和 PR09 正式文档路径；开始前必须确认 A～E 已停止写入 |

需要修改共享规划文档时，不在并行 PR 中直接写入。应在 PR 描述中记录：

- 需要调整的契约；
- 依据；
- 影响范围；
- 替代方案；
- 交由 PR08-F 处理的建议。

---

## 5. 每个执行对话的启动要求

执行 AI 必须先：

1. 确认 GitHub 与 Superpowers 的真实能力；
2. 技能端点不可用时说明使用等价人工流程；
3. 阅读 README、根目录和目标目录 `AGENTS.md`；
4. 阅读 PR08 四份规划文档；
5. 阅读 PR #21 议题推进文档；
6. 阅读 PR #22 第 1～62 题规格、索引和补充决定；
7. 检查开放 PR、Base SHA、相关代码、测试和 CI；
8. 输出计划、预计文件、冻结契约和风险；
9. 创建独立分支后再写入；
10. 每次写入前重新读取最新文件；
11. 完成前执行真实性验证；
12. 创建 Draft PR，不自动合并；
13. 输出统一交付报告和只读验收 Prompt。

---

## 6. 统一交付报告

```markdown
# PR08-X 交付报告

## 仓库与分支
- 仓库：
- Base / Base SHA：
- 分支：
- Commit：
- PR：

## 完成范围
- [x] ...
- [ ] ...（原因）

## 修改文件
| 文件 | 用途 | 关键内容 |
|---|---|---|

## 冻结契约核对
- 品牌与技术标识：一致 / 提出变更
- 两类核心价值：一致 / 提出变更
- 单 / 多 Skill：一致 / 提出变更
- 议题与阶段推进：一致 / 提出变更
- Skill V1 边界：一致 / 提出变更
- PR08 / PR09 边界：一致 / 提出变更

## 关键决策
- 决策：
- 依据：
- 替代方案：
- 需要用户确认：

## 验证
| 检查 | 结果 | 证据 |
|---|---|---|
| 净差异 | PASS/FAIL | ... |
| 文档链接 | PASS/FAIL | ... |
| 术语一致性 | PASS/FAIL | ... |
| git diff --check | PASS/FAIL/未执行 | ... |
| CI | PASS/FAIL/未运行 | ... |

## 未验证与风险
- ...

## 给 PR08-F 的输入
- 必须继承：
- 需要解决的冲突：
- 暂缓项：
```

---

## 7. PR08-A 启动 Prompt

```text
你现在接手 PR08-A：见域产品模型与术语契约。

仓库：https://github.com/elio-zwd/AI-Skill-Roundtable
目标分支：docs/pr-08a-product-model
Base：PR #20、#21、#22 全部合并后的最新 main SHA。

先读取 README、AGENTS、PR08 四份规划文档、PR #21 议题推进文档、PR #22 第 1～62 题决策索引，以及当前会话/消息/角色/调度相关实现。

目标：定义问题、Skill、议题、阶段、消息、资料、成果、圆桌、个人背景及生命周期；统一用户文案与工程术语；明确“推进议题”取代旧“下一轮”。

独占写入：docs/product/
禁止修改生产代码和其他 PR08 路径。
完成后创建 Draft PR，不合并，并输出交付报告与只读验收 Prompt。
```

---

## 8. PR08-B 启动 Prompt

```text
你现在接手 PR08-B：见域信息架构与核心交互。

仓库：https://github.com/elio-zwd/AI-Skill-Roundtable
目标分支：docs/pr-08b-information-architecture
Base：PR #20、#21、#22 全部合并后的最新 main SHA。

先读取规划文档、PR #21/#22、当前导航、页面、测试和 testTag。

目标：设计问题优先首页、推荐确认、单/多 Skill、自由追问、显式交叉讨论、推进议题三步流程、阶段时间线、资料、成果、归档和恢复状态。

独占写入：docs/design/ux/
禁止修改生产代码和品牌目录。
完成后创建 Draft PR，不合并，并输出交付报告与只读验收 Prompt。
```

---

## 9. PR08-C 启动 Prompt

```text
你现在接手 PR08-C：见域 Skill 分类、发现与推荐。

仓库：https://github.com/elio-zwd/AI-Skill-Roundtable
目标分支：docs/pr-08c-skill-taxonomy
Base：PR #20、#21、#22 全部合并后的最新 main SHA。

先读取规划文档、PR #22 第 59 题、skills_config、现有 Skill 和研究目录。

目标：建立四类 Skill、两类主价值、多标签、风险和推荐规则；整理原有人物 Skill 与研究目录 25 项，去重后形成约 44 个官方候选；处理 Windows 文档助手和写作自然化助手边界。

独占写入：docs/skills/jianyu-*
禁止复制第三方正文或修改 App assets。
完成后创建 Draft PR，不合并，并输出交付报告与只读验收 Prompt。
```

---

## 10. PR08-D 启动 Prompt

```text
你现在接手 PR08-D：见域品牌与视觉设计系统。

仓库：https://github.com/elio-zwd/AI-Skill-Roundtable
目标分支：design/pr-08d-jianyu-brand-system
Base：PR #20、#21、#22 全部合并后的最新 main SHA。

冻结值：App“见域”、仓库“jianyu-workbench”、官网“jianyu.my-elio.online”、口号“看见更多观点，打开认知边界”。

目标：定义品牌解释、Logo、App Icon、视觉 Token、关键组件和页面规格；覆盖现实支持、思维拓展、单/多 Skill、阶段推进、资料、成果、高风险和错误状态。

独占写入：docs/design/brand/
禁止修改 Compose 和 Android 资源。
完成后创建 Draft PR，不合并，并输出交付报告与只读验收 Prompt。
```

---

## 11. PR08-E 启动 Prompt

```text
你现在接手 PR08-E：见域技术迁移评估与 PR09 初步拆分。

仓库：https://github.com/elio-zwd/AI-Skill-Roundtable
目标分支：docs/pr-08e-technical-migration
Base：PR #20、#21、#22 全部合并后的最新 main SHA。

冻结值：applicationId 最终改为 com.elio.jianyu；App 未发布，不保留旧 App，不迁移旧包数据。

目标：只读审计导航、调度、Room、Skill、音频、测试和 CI；评估 applicationId、namespace、包路径、Room Schema、CI、脚本、仓库改名、官网和服务器迁移；输出 PR09 原子拆分、测试和回滚。

独占写入：
- docs/architecture/jianyu-product-migration-assessment.md
- docs/planning/pr-09-implementation-outline.md

禁止修改生产代码、测试、数据库、仓库设置或服务器。
完成后创建 Draft PR，不合并，并输出交付报告与只读验收 Prompt。
```

---

## 12. PR08-F 启动条件

PR08-F 暂不启动。A～E 全部完成后，启动 Prompt 必须填写真实 PR、Commit 和合并 SHA，并要求：

- 先建立术语、流程、视觉和技术冲突清单；
- 影响产品行为的冲突必须请求用户确认；
- 整合第 1～62 题和品牌迁移决定；
- 输出正式产品定义、PRD、体验规格、技术迁移计划及 PR09 Plan/Tasks/Handoff；
- 不修改生产代码；
- 不自动合并。

---

## 13. 给本地验收 AI 的统一只读要求

```text
请只读验收指定 PR08 文档 PR：

1. 拉取远端并检出指定 PR Head；
2. 核对 Base、Head、工作区和修改范围；
3. 不修改、不提交、不推送、不合并；
4. 阅读 AGENTS、README、PR08 四份规划文档和当前 PR；
5. 核对第 1～62 题、品牌、阶段推进、Skill V1 和 PR08/PR09 边界；
6. 检查当前事实、目标设计和待实施内容是否区分；
7. 检查路径、链接、术语和示例；
8. 执行 git diff --check；
9. 读取当前 Head 的 CI；
10. 记录系统、工具版本、命令、输出、失败项和可能原因；
11. 不把未运行的测试写成通过。
```
