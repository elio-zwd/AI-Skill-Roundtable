# Skill 审计与项目映射

本表基于本机 Superpowers 6.2.0 的 `skills/` 实际目录，不根据名称猜测内容。引入正文的 Skill 原文位于 `skills/<name>/SKILL.md`。

| Skill | 是否引入 | 原因 | 是否需要项目适配 |
|---|---|---|---|
| `brainstorming` | 是 | 新功能、架构和交互设计前澄清需求与方案 | 是；设计文档路径、中文规则和 PR08/PR09 门禁由项目规则覆盖 |
| `dispatching-parallel-agents` | 否 | 本项目一个对话只负责一个任务、分支和 PR，不模拟多对话子智能体 | 不适用 |
| `executing-plans` | 是 | 按已批准计划执行文档或代码任务 | 是；以 GitHub/单工作区流程替代其对多代理环境的假设 |
| `finishing-a-development-branch` | 是 | 完成验证、推送分支和准备 PR 的收尾流程 | 是；未经授权不得合并、标记 Ready 或删除分支 |
| `receiving-code-review` | 是 | 处理 GitHub 审查意见时逐条验证技术正确性 | 是；遵守本项目的评论、分支和测试规则 |
| `requesting-code-review` | 是 | 在重大任务完成后准备审查上下文 | 是；优先使用 GitHub PR 或当前可用的只读审查能力 |
| `subagent-driven-development` | 否 | 依赖独立子代理编排，不符合本项目协作边界 | 不适用 |
| `systematic-debugging` | 是 | Bug、测试失败和异常行为需要先找根因 | 是；命令、Android 测试和 Windows 环境按项目事实调整 |
| `test-driven-development` | 是 | 适合的功能或 Bug 修复可用红-绿-重构流程 | 是；按 Kotlin、JUnit、Compose 和 Room 现有测试约定执行 |
| `using-git-worktrees` | 否 | 当前工作流不假设本地 Worktree，且仓库要求独立分支即可 | 不适用 |
| `using-superpowers` | 否 | 这是插件启动/自举流程，不应作为项目内开发依赖复制 | 不适用 |
| `verification-before-completion` | 是 | 防止把未执行的构建、测试、CI 或审查写成已通过 | 是；按仓库规定的 Windows 命令和证据分类执行 |
| `writing-plans` | 是 | 多步实现需要可执行、可审查的计划 | 是；使用仓库现有 `docs/planning/` 约定，不自动创建冲突目录 |
| `writing-skills` | 否 | 本 PR 不创建或维护新的 Skill | 不适用 |

## 引入的辅助资料

仅保留已引入正文直接引用且有离线阅读价值的 Markdown：brainstorming 的规格审阅提示、writing-plans 的计划审阅提示、systematic-debugging 的根因/纵深防御/条件等待资料、test-driven-development 的测试编写资料、requesting-code-review 的审查者提示。未引入脚本、代理配置、图标、视觉伴侣或运行状态文件。
