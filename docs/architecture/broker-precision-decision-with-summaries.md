# 架构说明 — 基于 Skill Summary 的 Broker 资料选择（历史方案）

> **状态：已废弃 / Historical**
>
> 本文只保留为历史背景，不再描述当前生产架构。
> 当前实现以 `docs/superpowers/specs/2026-09-26-skill-knowledge-embedding-design.md`
> 为权威设计。

## 历史背景

早期多角色对话为了避免把完整 Markdown 发送给资料决策 Broker，曾预生成
`skills_summaries.json`，再由文本模型根据摘要选择若干本地 Markdown 文件，最终把整篇文件拼接进角色 Prompt。

这套机制解决了“只看文件名难以判断内容”的问题，但仍存在几个结构性缺陷：

- 本地知识选择依赖额外文本 Broker；
- 选择粒度是整篇文件，不是与当前问题相关的 chunk；
- Top 1 对话与正式 Execution 的知识加载语义不一致；
- Summary、原 Markdown 和 Broker 选择形成了第二套本地知识事实源；
- Embedding/检索失败时容易诱导实现回退到旧双轨。

## 当前替代方案

自 Skill Knowledge + Gemini Embedding 2 架构起：

- `SKILL.md` 永远作为 Role Core 直接加载；
- `references/**`、`research/**`、`examples/**` 中的 Knowledge Markdown 在开发期分块；
- 文档向量固定使用 `gemini-embedding-2`、768 维并预生成进 APK assets；
- 运行时只为当前用户问题生成 query embedding；
- Android 本地只在当前 Skill 范围内做 cosine retrieval；
- 最多取 12 个候选、8 个最终 chunk、同一 document 最多 2 个；
- Skill Knowledge 默认最多占 9,000 字符，并继续受整体 24,000 字符门禁约束；
- Top 1 `RoundtableViewModel` 与正式 `ExecutionRunCoordinator` 共用同一 Retriever 语义；
- `AiUseCase.MATERIAL_BROKER` 仅保留为联网检索决策，不再选择本地文件；
- 用户只有通过“带入当前会话”显式选择时，其他 Skill 的文档才可进入当前上下文；
- 不提供“Embedding 失败 → Summary Broker”的兼容回退。

## 已删除的旧生产构件

以下构件不再属于生产架构：

- `app/src/main/assets/skills_summaries.json`
- `workspace/tools/generate_summaries.py`
- `workspace/tools/generate_summaries_ai.py`
- Broker 输出中的 `selectedFiles`
- `SkillLoader.loadSelectedFiles`

历史提交仍可用于追溯旧方案，不应据本文恢复旧双轨。
