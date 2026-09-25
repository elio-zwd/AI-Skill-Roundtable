# Skill Knowledge + Gemini Embedding 2 设计规格

> 状态：已批准设计的持久化规格
>
> 日期：2026-09-26
>
> 实现分支：`codex/skill-knowledge-embedding`
>
> 基线：`codex/gemini-37-38-flash@8a7810f4af086da30401f32d92ea37f4fecb0d66`
>
> 关联产品契约：ADR-009、`docs/product/jianyu-product-model.md`、`docs/superpowers/specs/2026-09-22-ui-04-resources.md`

## 1. 目标

把官方 Skill 从“一个主 `SKILL.md` Prompt + Broker 偶尔挑几份整篇 Markdown”升级为：

> **Skill = 永久在线的角色核心（Core Prompt） + 可语义检索的结构化 Skill Knowledge。**

本次使用固定模型 `gemini-embedding-2` 为 Skill 自带知识建立语义索引。运行时根据用户当前问题检索当前 Skill 自己的知识片段，形成结构化 Context Pack，再交给现有回答模型。

同时在【资料】中增加独立的【Skill 资料】区域，让用户可以查看每个 Skill 角色随 App 提供的核心文件、参考资料和示例，并允许用户显式选择其他 Skill 的资料作为当前会话上下文。

## 2. 当前问题

当前代码存在两条不同但都不完整的 Skill 上下文路径：

1. `RoundtableViewModel`：
   - 始终加载 `SKILL.md`；
   - 读取 `skills_summaries.json`；
   - 调用文本 Broker 从文件摘要中选择少量 Markdown 文件；
   - 将被选中的整份 Markdown 拼进最终 Prompt。
2. 正式 `ExecutionRunCoordinator -> ExecutionContextBuilder`：
   - 冻结并加载 `SKILL.md`；
   - 支持用户确认资料与个人背景；
   - 当前没有加载 Skill 的 references / research / examples。

这会造成：
- 角色人格通常存在，但角色知识结构可能缺块；
- 文件级选择过粗，一份长文档会占用大量上下文；
- 摘要 Broker 看不到具体段落，容易漏掉同一问题需要的其他知识维度；
- 两条执行路径对同一个 Skill 角色拥有不同知识行为。

## 3. 核心决策

### 3.1 `SKILL.md` 永远是 Role Core，不交给向量召回来决定

`SKILL.md` 继续作为每次请求都必须存在的角色核心，承担：
- persona / 身份；
- 思考方式；
- 表达风格；
- 行为边界；
- 领域和工具规则。

Embedding 不替代 `SKILL.md`，也不允许“因为相似度低所以本轮不加载 SKILL.md”。

### 3.2 Skill Knowledge 与用户【我的资料】是两个领域对象

Skill Knowledge：
- 隶属于官方 Skill；
- 随 App 资产发布；
- 默认只读；
- 不归属于某一个会话；
- 不复用 `MaterialReferenceEntity.issueId` 数据模型；
- 自动参与该 Skill 自己的知识检索。

用户资料：
- 由用户上传、粘贴或保存；
- 有会话归属和生命周期；
- 继续使用现有 `MaterialReferenceEntity` / Material Context 逻辑。

UI 上二者都位于【资料】一级域，但底层不混成同一种实体。

### 3.3 不使用 Google File Search 作为核心

本次使用：
- 开发期：`gemini-embedding-2` 生成官方 Skill Knowledge 的文档向量；
- App 运行时：只为用户当前查询生成 query embedding；
- Android 本地：对当前 Skill 的预生成向量执行 cosine similarity 检索。

原因：
- Skill 资产是静态 App 资源，重复上传到远端 Store 没有必要；
- 见域仍需独立组合 Skill Knowledge、用户资料、联网检索；
- 本地索引更容易维持 Skill 角色隔离、可测试性和来源可追踪性。

### 3.4 固定 Embedding 协议

本期不新增 Embedding 模型选择 UI。

固定：
- model：`gemini-embedding-2`
- output dimensionality：`768`
- query 格式：`task: search result | query: {currentUserInput}`
- document 格式：`title: {title} | text: {headingPath}\n{chunkText}`

回答模型仍由现有 AI 管理配置决定，Embedding 与最终回答 provider 解耦。

### 3.5 不新增隐私提示

按用户明确决定：
- 不新增 Embedding 隐私提示；
- 不新增同意弹窗；
- 不新增 Banner；
- 不新增“首次使用 Skill Knowledge”确认；
- 不因本功能增加额外阻断流程。

现有用户资料的 `sensitive`、上下文确认、网络权限等既有行为不在本任务中删除或放宽。

### 3.6 默认角色知识严格隔离

普通独立回应：
- 角色 A 只自动检索角色 A 的 Skill Knowledge；
- 角色 B 只自动检索角色 B 的 Skill Knowledge；
- 不建立“所有 Skill 共用一个全局 Top-K”；
- 检索 query 默认只使用当前用户问题，不使用同一批次其他 AI 角色的回答。

用户显式选择其他 Skill 的资料时，才允许跨角色带入，并作为“用户明确选择的 Skill 资料”而不是当前角色自己的知识呈现。

## 4. Skill Markdown 分类

以官方 Catalog 的 `assetPath` 解析 Skill 根目录。根目录下所有 Markdown 均进入 Skill Knowledge Manifest，但分为：

1. `CORE`
   - `SKILL.md`
   - UI 可查看；
   - 每次作为 Core Prompt 使用；
   - 不进入向量召回，避免同一内容重复注入。

2. `KNOWLEDGE`
   - `references/**/*.md`
   - `research/**/*.md`
   - `examples/**/*.md`
   - 进入分块和向量索引；
   - 自动召回。

3. `SUPPORTING`
   - `README.md`、`README_EN.md` 等说明型 Markdown；
   - UI 可查看；
   - 默认不进入自动召回，避免安装说明、仓库介绍污染角色回答。

非 Markdown 工具脚本、图片、音频和许可证不属于本期 Skill Knowledge 文本索引。

## 5. 开发期索引产物

新增确定性生成器，从官方 Catalog / Skill assetPath 出发扫描实际 Skill 目录。

每个 Knowledge 文档：
- 保留 `skillId`；
- `documentId`；
- `relativePath`；
- `title`；
- `documentType`；
- `contentHash`；
- Markdown heading path；
- chunk 顺序。

### 5.1 分块规则

Markdown-aware chunking：
- 标题层级作为 `headingPath`；
- 优先按段落边界切分；
- 单 chunk 目标最大 1800 字符；
- 相邻 chunk 最多 200 字符重叠；
- 不产生空 chunk；
- 不跨 Skill、跨文档合并。

### 5.2 资产格式

版本化输出：
- `app/src/main/assets/skill_knowledge/manifest.json`
- `app/src/main/assets/skill_knowledge/index-v1.bin`

`manifest.json` 保存元数据、chunk offset、contentHash、vector offset/length；向量采用 little-endian Float32 存在二进制文件，不把 768 维浮点数组展开成大型 JSON。

索引生成使用开发者本地 `GEMINI_API_KEY`，Key 不写入输出、不提交 Git。

## 6. 运行时检索

新增共享 `SkillKnowledgeRetriever`，两条执行路径都调用它。

输入：
- `ownerSkillId`
- `currentUserInput`
- `maxContextCharacters`

过程：
1. 使用 `gemini-embedding-2` 生成 768 维 query vector；
2. 只加载 `ownerSkillId` 对应 Knowledge chunk 向量；
3. cosine similarity 排序；
4. 先取 Top 12 candidate；
5. 最终最多 8 个 chunk；
6. 同一个 document 最多 2 个 chunk；
7. 按 score 从高到低加入，达到 Skill Knowledge 字符预算即停止；
8. 返回来源路径、标题、heading、正文、score、retrievalOrder。

默认 Skill Knowledge 预算为 9,000 字符；它属于整体 24,000 字符上下文门禁的一部分，不绕过现有 `ExecutionContextBuilder.maxContextCharacters`。

如果 query embedding 或索引读取失败：
- 不回退到旧 `skills_summaries.json` Broker；
- 不阻塞最终回答；
- 保留 `SKILL.md` Core；
- 记录 privacy-safe 错误；
- UI 可以显示非阻断的“Skill 资料暂时未加载”，但不弹隐私提示。

## 7. Context Pack

最终角色上下文明确分层：

```text
[ROLE CORE]
SKILL.md

[SKILL KNOWLEDGE MAP]
该角色可用的 Knowledge 文档标题/类型列表

[RETRIEVED SKILL KNOWLEDGE]
[source: path#heading]
chunk...
...

[USER CONFIRMED CONTEXT]
用户明确选择的资料 / 个人背景 / 跨角色 Skill 资料

[QUESTION / HISTORY]
现有会话输入
```

Skill Knowledge Map 只包含短元数据，不把所有正文常驻 Prompt。

## 8. 两条执行路径必须统一

### 8.1 Top 1 对话 / `RoundtableViewModel`

移除“本地 Skill 文件选择”职责：
- 不再读取 `skills_summaries.json` 决定 `selectedFiles`；
- 不再把被选中的整份 Markdown 拼入 Prompt；
- 使用共享 `SkillKnowledgeRetriever` 获取 chunk Context Pack。

现有 Broker 若仍负责 `SearchMode.AUTO` 的联网判断，只保留联网决策，不再负责本地 Skill Knowledge 选择。

### 8.2 正式 Execution

`ExecutionContextBuilder` 保持纯函数：
- 不在 Builder 内做网络或 Asset IO；
- `ExecutionRunCoordinator` 在 build 前调用 Skill Knowledge 检索；
- 通过新的 immutable knowledge contribution 参数传入 Builder；
- Builder 负责格式化和统一字符预算。

标准独立回应检索自己的 Skill；显式交叉讨论仍遵守 ADR-009，不因为向量库存在而自动引入其他角色知识/回答。

## 9. 【资料】中的 Skill 资料区域

【资料】总览保留现有“资料 / 成果”主关系，并新增独立 Skill 资料入口。

建议结构：

```text
资料

[资料]        [成果]
文件、链接…   方案、笔记…

Skill 资料
角色自带的知识与参考来源
44 个角色 · N 篇 Markdown
查看全部 >
```

Skill 资料页：
- 按 Skill 角色分组；
- 支持角色名 / 文档标题 / 路径搜索；
- 展示 `CORE / KNOWLEDGE / SUPPORTING` 类型；
- 文档详情只读；
- 展示所属 Skill、类型、相对路径、正文；
- 不提供编辑、删除、归档、清除等用户 Material 生命周期动作。

UI 不把 Skill 资料伪装成用户上传资料。

## 10. 显式跨角色 Skill 资料

用户可以在 Skill 资料详情执行“带入当前会话”。

规则：
- 这是用户显式动作；
- 不改变任何角色默认知识库；
- 当前角色仍只自动检索自己的 Skill Knowledge；
- 选中的其他 Skill 文档作为 `ContextSourceType.SKILL_KNOWLEDGE` 进入用户确认上下文；
- `networkAllowed=true`、`sensitive=false`；
- 不触发隐私确认；
- 必须保存使用快照，使历史 Run 不依赖未来 App 版本中的 Markdown 内容。

新增 `SkillKnowledgeUsageSnapshotEntity`，保存：
- run / issue / stage；
- source skill id；
- document id；
- title / path / content snapshot；
- content hash；
- userConfirmedAt / createdAt。

Room 从 v14 升到 v15，禁止 destructive migration。

## 11. 来源与可解释性

自动检索的 chunk 在模型上下文中带：
- Skill 名称 / id；
- document title；
- relativePath；
- headingPath。

模型不得把 AI 生成内容伪装成原文引用。若 UI 后续展示来源，可直接使用这些稳定 metadata 定位到【Skill 资料】详情。

## 12. 删除的旧机制

当新检索链路完整接入并通过测试后：
- 删除 `RoundtableViewModel.skillsSummaries` / `loadSkillsSummariesOnce`；
- 删除本地资料 Broker 的 `selectedFiles` 解析；
- 删除不再使用的 `SkillLoader.loadSelectedFiles`；
- 删除生产依赖的 `skills_summaries.json`；
- 旧摘要生成脚本若无其他调用方，一并删除或迁为历史文档，不保留双轨正式实现。

联网决策能力不因删除本地 Broker 而丢失。

## 13. 非目标

本期不做：
- 用户【我的资料】Embedding / RAG；
- Google File Search Store；
- 图片、音频、视频 embedding；
- Embedding 模型切换 UI；
- 云端向量数据库；
- 自动跨 Skill 全局检索；
- Skill Markdown 在线编辑；
- 新的隐私提示；
- 依赖升级或无关架构重构。

## 14. 验收

必须验证：

1. `SKILL.md` 在任何检索结果下都存在；
2. 当前角色只自动检索自己的 Knowledge；
3. 768 维 query 与 packaged index 维度一致；
4. Top-K、每文档最多 2 chunk、9k 字符预算稳定；
5. 检索失败仍能用 Core Prompt 回答；
6. 两条执行路径使用同一 Retriever；
7. 旧 `skills_summaries.json` 不再参与本地知识选择；
8. Skill 资料 UI 可浏览 Core / Knowledge / Supporting Markdown；
9. Skill 资料不可编辑、删除、归档；
10. 用户显式跨角色带入后写入 v15 usage snapshot；
11. 未显式选择时不存在跨角色知识泄漏；
12. 不出现新的隐私提示、同意弹窗或 Banner；
13. Room 14→15 Migration、Schema、既有数据保持；
14. `compileDebugKotlin`、`testDebugUnitTest`、`lintDebug`、`assembleDebug`；
15. 相关 AndroidTest、模拟器 UI 验收；
16. 真实 Gemini API 验证 query embedding 返回 768 维，并用一个真实 Skill 问题证明能召回预期文档/heading。

