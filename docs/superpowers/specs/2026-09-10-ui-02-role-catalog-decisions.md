# UI-02｜44 项 Skill 角色目录决策

> 状态：已审查
>
> 日期：2026-09-10
>
> 关联分析：`docs/superpowers/specs/2026-09-10-ui-02-44-role-catalog-audit.md`
>
> 关联 UI 规格：`docs/product/重构/UI界面/实施规格/UI-02/角色主页面-双布局-实施规格.md`
>
> 关联 PR：#59

本文记录用户对 44 项角色目录复核后的最终决定。若与前置审计草稿中的 R1～R4 文案冲突，以本文为准。

## D44-1｜角色全集

**当前 OfficialSkillCatalog 的 44 项全部作为可发现 Skill 角色进入【角色】一级页面。**

`WORKFLOW_CAPABILITY` 只表示能力组织方式，不自动等于隐藏后台工具。未来若新增真正无独立对话身份的纯工具，再单独增加 `visibleAsRole=false` 或等价产品字段。

角色身份、名称、能力、发布/执行资格以当前有效 `OfficialSkillCatalog` 与执行 Manifest 为权威事实源；旧 `skills_config.json` / Room `Character` 不再决定角色全集。

## D44-2｜发现分类

采用 **7 个主发现分类 + 全部**：

`全部 / 思考方法 / 职业成长 / 研究学习 / 产品创造 / 沟通表达 / 办公事务 / 生活工具`

其中前置审计草稿中的 `生活支持` 正式改名为 **`生活工具`**。

44 项主分类映射沿用前置审计的一对一映射，仅将分类显示名 `生活支持` 替换为 `生活工具`：

- `budget-consumption-coach`
- `habit-wellbeing-coach`
- `relationship-dialogue-practice`
- `chinese-social-etiquette`
- `culture-fortune-entertainment`

上述 5 项的 `primaryDiscoveryCategory` 统一归入 `LIFE_TOOLS`。

分类只是发现入口，不限制角色在官方 Catalog 中的跨领域能力与多维标签。

## D44-3｜固定「推荐角色」改为精神/思想型编辑精选

当前角色页没有真实用户个性化排序，因此区块标题继续固定为 **`推荐角色`**，不得使用 `为你推荐`。

固定编辑精选改为偏思想、认知、人生观与精神启发的三项：

1. Hero：`naval_ravikant`｜纳瓦尔
2. 次级：`richard_feynman`｜理查德·费曼
3. 次级：`nassim_taleb`｜纳西姆·塔勒布

选择理由：

- 纳瓦尔：人生选择、长期积累、自主与判断；
- 费曼：求真、好奇、理解与验证；
- 塔勒布：不确定性、风险、脆弱性与反脆弱思考。

这三项只是产品编辑精选，不表示可靠性排名、人物权威性、热度或针对当前用户的推荐结果。实现使用显式 `featuredOrder=1..3`，不得退回 `defaultOrder` 前三项。

## D44-4｜头像策略

- 真人/真实人物模拟角色：优先复用仓库已有稳定人物头像，并在列表使用轻量 `AI 模拟角色` 标记；详情/首次使用显示完整非本人声明。
- 功能型角色：使用稳定的非真人身份图形，不为了“人物化”生成虚构人脸。
- 功能型角色可以采用分类弱色 + 名称缩写/稳定图形形成身份连续性；不得使用随机头像。

## D44-5｜对话接入边界

UI-02 不能只做到“44 项可浏览”。完成条件必须包含：

- 第 21～44 项可以从角色详情真实执行“开始新对话”；
- 第 21～44 项可以真实执行“增加到当前会话”；
- 成功加入后能成为当前 Dialog `participantIds` 中可解析、可生成回复的 Skill 角色；
- 失败时不得先写最近使用或伪装为已加入。

为避免把旧 20 项再扩成第二套权威目录，实施采用：

`OfficialSkillCatalog（权威） → Conversation role compatibility adapter → 旧 Character/Orchestrator 兼容执行`

而不是：

`skills_config.json（20） → 扩写为新的 44 项权威目录`。

## 状态

D44-1～D44-5 已审查。下一步允许按本决策重写 UI-02 Implementation Plan；**Plan 本身仍需用户批准后才能进入 Android 生产代码实施。**