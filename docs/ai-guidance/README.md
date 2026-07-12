# AI 工作结论与对话记录

本目录记录 AI 在项目工作过程中的关键决策、对话结论和待办事项，避免重复讨论。

---

## 对话记录索引

| 日期 | 对话 ID | 主题 | 结论文件 |
|------|---------|------|---------|
| 2026-07-12 | 53f0e8f6 | 项目初始化 + Skills 引擎升级计划 | [2026-07-12-init.md](2026-07-12-init.md) |

---

## 已确认技术决策汇总

| 决策编号 | 决策内容 | 确认时间 |
|---------|---------|---------|
| ADR-001 | Skills 加载：SKILL.md 打包至 `assets/skills/`，运行时 AssetManager 读取 | 2026-07-12 |
| ADR-002 | API 多 Key 机制：10 Key 池 + 429 熔断 24h + lastUsed 轮换（参考 life-archive-app） | 2026-07-12 |
| ADR-003 | 主力模型 `gemini-2.0-flash`，备用 `gemini-1.5-flash` | 2026-07-12 |
| ADR-004 | 开发期数据库使用 `fallbackToDestructiveMigration()`，发布前切换正式 Migration | 2026-07-12 |
