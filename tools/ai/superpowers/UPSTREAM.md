# Superpowers 上游记录

- 名称：Superpowers
- 本地版本：6.2.0
- 本地来源目录：`%USERPROFILE%\.codex\plugins\cache\openai-curated-remote\superpowers\6.2.0`（泛化路径；不同机器的插件缓存位置可能不同）
- 上游仓库：https://github.com/obra/superpowers
- 上游版本或 Commit：`v6.2.0`，解析到 `3dcbd5c4b48e02263fbf4a3c01e3fe4f81d584d9`
- 原作者：Jesse Vincent；上游 README 另注明 Prime Radiant 社区参与
- 许可证：MIT License，许可证全文见本目录 `LICENSE`
- 引入日期：2026-08-02
- 引入的 Skill：见 [skill-mapping.md](skill-mapping.md)，共 9 个核心工作流 Skill
- 未引入的 Skill：`dispatching-parallel-agents`、`subagent-driven-development`、`using-git-worktrees`、`using-superpowers`、`writing-skills`
- 其他未引入内容：插件 Manifest、图标、代理配置、视觉伴侣及其脚本、测试辅助脚本和未被引入 Skill 直接需要的资料
- 本地修改：Skill 正文及引入的辅助 Markdown 未修改；项目规则、路径和平台限制写在本目录适配层中
- 许可证结论：上游根目录存在明确 MIT License，允许复制、修改和再分发；本目录保留版权声明和许可证全文，不将第三方正文标记为本项目原创
- 敏感信息审计：未发现 Token、API Key、Cookie、密码、私有服务地址或私有对话记录；来源记录仅保留经过泛化的插件缓存路径，不记录具体 Windows 用户目录；视觉伴侣会话脚本未引入
- 后续更新方式：先获取新的上游版本和 Commit，重新核验许可证、来源、文件清单和敏感信息，再逐项比较并更新本目录；不得直接覆盖项目适配文档
