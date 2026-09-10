# UI-02｜44 项 Skill 角色目录对账与发现分类修订

> 状态：已审查；最终 R1～R4 以 `2026-09-10-ui-02-role-catalog-decisions.md` 为准
>
> 日期：2026-09-10
>
> 关联规格：`docs/product/重构/UI界面/实施规格/UI-02/角色主页面-双布局-实施规格.md`
>
> 最终决定：`docs/superpowers/specs/2026-09-10-ui-02-role-catalog-decisions.md`
>
> 关联 Plan：`docs/superpowers/plans/2026-09-10-ui-02-role-page.md`
>
> 关联 PR：#59

## 1. 对账结论

此前 UI-02 Plan 错把 `skills_config.json` / Room `Character` 中早期 20 个阵容角色当成【角色】页全集。重新核查 `main` 后确认：

1. `OfficialSkillCatalogParser.EXPECTED_SKILL_COUNT = 44`，当前官方 Catalog 精确固定为 44 项；
2. `official_skill_execution_manifest_v2.json` 对 1～44 项全部提供正式执行资产，并标记 `PUBLISHABLE`、`VERIFIED_IMPLEMENTATION_SOURCE`；
3. 当前 `OfficialSkillCatalogRoute` 已直接消费完整 `OfficialSkillCatalog`；
4. `skills_config.json` 与 `RoundtableViewModel.ensureCoreCharactersExist()` 仍主要维护旧 20 项 `Character`，属于对话运行兼容层，不再能代表当前官方角色全集；
5. 当前 `OfficialSkillUseRequest` 尚没有把第 21～44 项真正加入 Dialog participant 链。

因此：【角色】页全集以当前有效 **OfficialSkillCatalog 44 项**为准；Room `Character` 只保留为旧 Dialog/Orchestrator 的兼容执行模型。

## 2. 44 项均作为可发现 Skill 角色

遵守 ADR-009，`WORKFLOW_CAPABILITY` 只表示能力组织方式，并不自动等于隐藏后台工具。本次抽查人物视角、专业顾问、任务助手以及工作流型正式资产后，当前 44 项都具备稳定用户侧名称、明确角色/目标和独立交流职责。

当前主类型数量：

| 主类型 | 数量 |
|---|---:|
| 人物视角 | 19 |
| 专业顾问 | 9 |
| 任务助手 | 10 |
| 工作流能力 | 6 |
| **合计** | **44** |

未来如果新增真正的纯工具型 Skill，再单独增加 `visibleAsRole=false` 或等价产品字段；不能从 `WORKFLOW_CAPABILITY` 自动推断隐藏。

## 3. 最终发现分类

用户审查后的分类为：

`全部 / 思考方法 / 职业成长 / 研究学习 / 产品创造 / 沟通表达 / 办公事务 / 生活工具`

其中原审计提议的 `生活支持` 已正式改名为 **`生活工具`**。

每个 Skill 只配置一个 `primaryDiscoveryCategory` 作为一级发现入口；搜索和详情仍使用 OfficialSkillCatalog 的多维标签呈现跨领域能力。

## 4. 44 项主发现分类完整映射

| # | Skill ID | 用户侧名称 | 官方主类型 | 主发现分类 |
|---:|---|---|---|---|
| 01 | `zhang_xuefeng` | 张雪峰 | 人物视角 | 职业成长 |
| 02 | `elon_musk` | 埃隆·马斯克 | 人物视角 | 产品创造 |
| 03 | `richard_feynman` | 理查德·费曼 | 人物视角 | 研究学习 |
| 04 | `charlie_munger` | 查理·芒格 | 人物视角 | 思考方法 |
| 05 | `naval_ravikant` | 纳瓦尔 | 人物视角 | 思考方法 |
| 06 | `steve_jobs` | 史蒂夫·乔布斯 | 人物视角 | 产品创造 |
| 07 | `nassim_taleb` | 纳西姆·塔勒布 | 人物视角 | 思考方法 |
| 08 | `andrej_karpathy` | 安德烈·卡帕斯 | 人物视角 | 研究学习 |
| 09 | `zhang_yiming` | 张一鸣 | 人物视角 | 产品创造 |
| 10 | `paul_graham` | 保罗·格雷厄姆 | 人物视角 | 产品创造 |
| 11 | `ilya_sutskever` | 伊利亚·苏茨克维尔 | 人物视角 | 研究学习 |
| 12 | `donald_trump` | 唐纳德·特朗普 | 人物视角 | 沟通表达 |
| 13 | `mr_beast` | 吉米·唐纳森（MrBeast） | 人物视角 | 沟通表达 |
| 14 | `justin_sun` | 孙宇晨 | 人物视角 | 沟通表达 |
| 15 | `sigmund_freud` | 西格蒙德·弗洛伊德 | 人物视角 | 思考方法 |
| 16 | `x_mentor` | X 增长导师 | 专业顾问 | 沟通表达 |
| 17 | `feng_ge` | 峰哥亡命天涯 | 人物视角 | 沟通表达 |
| 18 | `changpeng_zhao` | 赵长鹏（CZ） | 人物视角 | 产品创造 |
| 19 | `duan_yongping` | 段永平 | 人物视角 | 产品创造 |
| 20 | `tim_cook` | 蒂姆·库克 | 人物视角 | 产品创造 |
| 21 | `civil-service-coach` | 考公备考教练 | 专业顾问 | 职业成长 |
| 22 | `public-document-coach` | 公文与材料教练 | 任务助手 | 办公事务 |
| 23 | `study-planner` | 学习规划师 | 任务助手 | 研究学习 |
| 24 | `career-navigator` | 职业发展顾问 | 专业顾问 | 职业成长 |
| 25 | `resume-interview-coach` | 简历与面试教练 | 任务助手 | 职业成长 |
| 26 | `workplace-communication` | 职场沟通陪练 | 专业顾问 | 职业成长 |
| 27 | `manager-expectation-review` | 领导预期复盘助手 | 任务助手 | 职业成长 |
| 28 | `team-handover` | 团队交接与协作助手 | 工作流能力 | 办公事务 |
| 29 | `meeting-to-action` | 会议纪要与行动项助手 | 工作流能力 | 办公事务 |
| 30 | `report-proposal-writer` | 汇报与方案写作助手 | 任务助手 | 办公事务 |
| 31 | `contract-checklist` | 合同要点检查助手 | 任务助手 | 办公事务 |
| 32 | `hr-document-assistant` | 人事文档助手 | 任务助手 | 办公事务 |
| 33 | `research-fact-checker` | 调研与事实核查助手 | 工作流能力 | 研究学习 |
| 34 | `budget-consumption-coach` | 预算与消费决策助手 | 专业顾问 | 生活工具 |
| 35 | `habit-wellbeing-coach` | 习惯与身心管理教练 | 专业顾问 | 生活工具 |
| 36 | `relationship-dialogue-practice` | 关系沟通练习伙伴 | 任务助手 | 生活工具 |
| 37 | `chinese-social-etiquette` | 人情世故与礼仪助手 | 专业顾问 | 生活工具 |
| 38 | `culture-fortune-entertainment` | 传统命理文化陪伴 | 专业顾问 | 生活工具 |
| 39 | `content-creator` | 内容策划与文案助手 | 任务助手 | 沟通表达 |
| 40 | `product-competition-analyst` | 产品与竞品分析助手 | 专业顾问 | 产品创造 |
| 41 | `software-copyright-organizer` | 软件著作权材料整理助手 | 工作流能力 | 办公事务 |
| 42 | `patent-disclosure-organizer` | 专利交底材料整理助手 | 工作流能力 | 办公事务 |
| 43 | `office-document-productivity` | 办公文档助手 | 工作流能力 | 办公事务 |
| 44 | `original-expression-naturalizer` | 去AI化助手 | 任务助手 | 沟通表达 |

分类数量：

- 思考方法：4
- 职业成长：6
- 研究学习：5
- 产品创造：8
- 沟通表达：7
- 办公事务：9
- 生活工具：5
- 合计：44

## 5. 角色展示事实源

实施新增极小的 `skill_role_presentation_v1.json`，只保存：

- `skillId`
- `primaryDiscoveryCategory`
- `featuredOrder`（可空）

不得复制名称、summary、主类型、风险、发布状态、assetPath、Prompt、能力标签等 OfficialSkillCatalog 已有事实。

加载时与当前 OfficialSkillCatalog 做集合校验：44 个官方 ID 一一有分类；拒绝未知/重复/缺失 ID；Catalog 增删角色时不能静默漏分类。

## 6. 固定「推荐角色」

当前没有真实个性化推荐，所以推荐区是明确的产品**编辑精选**。

用户将 R3 改为偏思想、认知、人生观与精神启发的方向，固定三项为：

1. Hero：`naval_ravikant`｜纳瓦尔
2. 次级：`richard_feynman`｜理查德·费曼
3. 次级：`nassim_taleb`｜纳西姆·塔勒布

理由分别偏向：人生选择与自主；求真、好奇与理解；不确定性、风险与反脆弱。

实现使用显式 `featuredOrder=1..3`。这不是个性化、可靠性排名、人物权威性或热度排序；文案固定为 `推荐角色`。

## 7. 头像策略

- 真人/真实人物模拟角色：优先复用仓库稳定人物头像；卡片显示轻量 `AI 模拟角色`；详情/首次使用显示完整非本人声明。
- 功能型角色：稳定非真人身份图形，可由分类弱色 + 名称缩写/稳定图形构成；不生成虚构人脸，不使用随机身份图。

## 8. 44 项进入对话的真实缺口与最小兼容方案

当前问题：OfficialSkillCatalog 已经有 44 项，但 Dialog/RoundtableViewModel 仍通过旧 CharacterRepository 解析 participantIds；`addSkillRoleToCurrentSession(skillId)` 在 Character 不存在时直接返回。因此 21～44 目前不能仅靠角色目录 UI 自动变成真正对话角色。

采用最小兼容方案：

```text
OfficialSkillCatalog（44，权威）
→ 用户执行开始/加入对话
→ OfficialSkillConversationRoleAdapter
→ 按需 upsert legacy Character
→ participantIds
→ 现有 RoundtableOrchestrator / SkillLoader
```

关键约束：

1. 浏览角色页不批量写 Character；
2. 同 ID 旧 Character 仅可提供 avatar/voice/vector 等补充，官方 name/summary/assetPath/order/执行资格不得被旧数据覆盖；
3. 新角色使用当前 execution Manifest 生效后的正式 assetPath；
4. 不新增 Room 表、不改 Character 主键、不改消息 senderId 协议；
5. “开始新对话”必须在单一 coroutine 业务序列内完成 upsert → create session → participantIds → 发布当前会话，避免空会话竞态；
6. “增加到当前会话”写入前后校验当前 session 未切换；
7. 成功使用后才写 recent；失败不写。

## 9. 对 UI-02 原规格的定向覆盖

本审计与最终决策对原 UI-02 规格做以下覆盖：

- 角色全集：44 项 OfficialSkillCatalog；
- 分类：从原五类扩为 7 类，新增 `办公事务 / 生活工具`；
- 44 项全部进入角色目录；
- 固定推荐：纳瓦尔 / 费曼 / 塔勒布；
- 推荐顺序由显式 `featuredOrder` 决定，不使用 `defaultOrder` 冒充推荐算法；
- 角色页展示模型以 OfficialSkillDefinition 为主；Character 只是旧对话兼容层与视觉补充；
- 完成条件包含 21～44 的真实 start-new / add-current / participant 解析。

其余已经审查的 A/B 双布局、视觉基线、搜索、收藏、最近使用、详情页和 `推荐角色` 非个性化文案继续有效。

## 10. 状态边界

44 项目录设计已审查。新的 Implementation Plan 已按本结论重写，但 **Plan 仍需用户批准后才能进入 Android 生产实现**。

当前不声称：

- Android 已编译；
- JVM/Instrumentation 已通过；
- 44 项已经从角色页真实进入对话；
- UI 已完成真机截图验收。