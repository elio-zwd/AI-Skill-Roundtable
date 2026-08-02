# 见域项目工作流

本文件把 Superpowers 工作流映射到 AI-Skill-Roundtable 当前工程。仓库 `AGENTS.md`、用户要求、安全规则和任务专用施工单优先于本文件或任何通用 Skill。

## 推荐顺序

1. 新功能、架构和交互设计：使用 `brainstorming`，先澄清目标、边界、约束和验收标准。
2. 编写实施方案：使用 `writing-plans`，让计划指向真实文件、调用链、测试和验证命令。
3. 按批准计划开发：使用 `executing-plans`；每个对话只处理一个任务、一个分支和一个 PR。
4. Bug 和测试失败：使用 `systematic-debugging`，先建立可复现证据和根因链，再提出修复。
5. 适合 TDD 的任务：使用 `test-driven-development`；Android 任务按当前 Kotlin/Gradle/Room 测试约定落地。
6. 完成前验证：使用 `verification-before-completion`，逐条执行命令并以输出为证据，不把推断写成通过。
7. 开发完成后请求审查：使用 `requesting-code-review`；在本项目中优先使用 GitHub PR 或当前可用的只读审查能力。
8. 处理审查意见：使用 `receiving-code-review`，逐条核对上下文、复现问题并验证修复，不盲目照改。
9. 准备 PR 和收尾：使用 `finishing-a-development-branch`；本项目通常推送独立分支并创建 Draft/普通 PR，未经用户授权不得合并。

## 上游指令覆盖

当上游 Skill 的通用流程与本项目的协作边界冲突时，执行以下替代行为：

| 上游 Skill 指令 | 本项目替代行为 |
|---|---|
| 使用 `using-git-worktrees` | 使用当前独立分支和工作区；无明确授权不创建 Worktree。 |
| 使用 `subagent-driven-development` | 使用 `executing-plans` 串行执行，不模拟其他独立对话。 |
| 分派 reviewer subagent | 使用 GitHub PR 独立只读审查或用户另行启动的审查对话。 |
| 启动 Visual Companion | 能力不存在时跳过，不视为流程失败。 |
| 使用 Forge CLI 创建 PR | CLI 不可用时使用 GitHub 插件或 GitHub PR 创建页。 |
| 自动选择合并、清理分支 | 必须等待用户明确授权。 |

被本仓库排除的 Skill，即使在上游原始正文中被标为 REQUIRED，也不得在本项目中自动调用；统一执行本节定义的替代流程。

## 本项目适配规则

- GitHub 插件不等于本地终端。插件可查询或创建远端对象，不代表本机已有完整 CLI、测试环境或可写工作区。
- 无法执行测试时必须明确写“未执行”及原因，不得声称测试通过。
- 远端 CI 与本地测试分开记录；只在实际读取到对应结果后声明通过。
- 未经用户授权不得合并 PR、标记 Ready、删除分支或强制推送。
- 一个对话只负责一个任务、一个分支和一个 PR；不使用多对话模拟子智能体。
- 不在无本地工作区时使用 Worktree 流程；本项目当前主要采用单工作区独立分支。
- 本仓库当前要求 Windows 10 x64 和 PowerShell 7；命令示例优先使用 `pwsh.exe`，不假定 WSL。
- `AGENTS.md` 的产品契约、PR08/PR09 门禁、文件范围和安全要求优先。
- Skill 不得覆盖用户要求、安全规则、仓库规则或更具体目录的 `AGENTS.md`。
- 本目录不是运行时资源，不加入 Android Manifest、Gradle、Room、CI 构建依赖或 App 资产。

## 能力降级

当 Superpowers 插件接口不可调用但本目录可读时，直接阅读对应 `SKILL.md`，按上述顺序执行等价人工流程，并在交付中说明插件接口不可调用。若本地文件不可读，则只使用本文件的适配规则，不声称已经执行上游 Skill。
