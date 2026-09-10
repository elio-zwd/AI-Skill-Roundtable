# UI-02｜44 项 Skill 角色目录对账与发现分类修订

> 状态：草稿，待用户审查
>
> 日期：2026-09-10
>
> 关联规格：`docs/product/重构/UI界面/实施规格/UI-02/角色主页面-双布局-实施规格.md`
>
> 关联 Plan：`docs/superpowers/plans/2026-09-10-ui-02-role-page.md`（当前旧版暂不可执行，待本文审查后重写）
>
> 关联 PR：#59

## 1. 为什么需要这次修订

此前 UI-02 Plan 错把 `skills_config.json` / Room `Character` 中的早期 20 个阵容角色当成【角色】页全集。重新核查 `main` 后确认：

1. `OfficialSkillCatalogParser.EXPECTED_SKILL_COUNT = 44`，当前官方 Catalog 精确固定为 44 项；
2. `official_skill_execution_manifest_v2.json` 对 1～44 项全部提供正式执行资产，并标记为 `PUBLISHABLE`、`VERIFIED_IMPLEMENTATION_SOURCE`；
3. 当前 `OfficialSkillCatalogRoute` 已直接消费完整 `OfficialSkillCatalog`；
4. `skills_config.json` 与 `RoundtableViewModel.ensureCoreCharactersExist()` 仍主要维护旧 20 项 `Character`，属于对话运行兼容层，不再能代表当前官方角色全集；
5. `App.kt` 当前从官方目录发出的 `OfficialSkillUseRequest` 只是暂存 ID 并跳回 HOME，尚没有把第 21～44 项真正加入当前对话会话。

因此，UI-02 必须同时修正“角色页事实源”和“44 项进入对话”的边界，不能只换列表 UI。

---

## 2. 当前事实源重新定级

### 2.1 角色页全集

【角色】一级页面的全集以 **当前有效的 `OfficialSkillCatalog`** 为准，不再以 `Character` 行数、`skills_config.json` 长度或头像目录数量为准。

当前基线：44 项。

### 2.2 角色名称、能力和执行资格

从 `OfficialSkillDefinition` / 当前执行 Manifest 读取：

- `id`
- `nameZh`
- `summary`
- `primaryType`
- `primaryValue`
- `domainTags` / `scenarioTags`
- `availability`
- `personDisclaimer`
- `defaultOrder`
- 当前有效 `assetPath`

`Character` 不再反向覆盖这些官方字段。

### 2.3 `Character` 的新定位

Room `Character` 暂时保留为现有「对话」运行链的 **兼容执行适配层**：

- 当前 `RoundtableOrchestrator` 仍通过 `Character` 获取参与角色；
- 当前 `RoundtableViewModel` 仍通过 `character.skillAssetPath` 加载 SKILL 正文；
- 早期 20 项 `Character.avatar`、语音配置等可作为已有展示补充资料；
- 但它不再是“有哪些官方 Skill 角色”的权威清单。

后续实施必须让 44 项官方 Catalog 可以安全投影/同步到这个兼容层，而不是继续要求官方 Catalog 退回 20 项历史配置。

---

## 3. 44 项是否都应出现在【角色】页

### 3.1 判定原则

遵守 ADR-009：

- Skill 是内部能力载体；
- Skill 角色是用户实际交流的 AI 参与者；
- 纯后台工具如果没有独立交流身份，不应为了凑目录而伪装成角色；
- 但 `WORKFLOW_CAPABILITY` 只表示能力组织方式，本身不等于“后台工具”。

本次检查不是按 `primaryType` 一刀切，而是看正式资产是否具备：

1. 稳定的用户侧名称；
2. 明确的“角色与目标”或等价长期职责；
3. 可以围绕用户输入持续澄清、分析、生成和复核；
4. 当前正式执行资产；
5. 能作为对话参与者独立产生回复，而不是只能被后台函数调用。

### 3.2 结论

**当前 44 项全部保留为可发现的 Skill 角色。**

原因：当前人物视角、专业顾问、任务助手和 6 个工作流型 Skill 的正式资产都具有用户侧角色名称与持续交互职责。抽查 `meeting-to-action`、`research-fact-checker`、`team-handover`、`software-copyright-organizer`、`patent-disclosure-organizer`、`office-document-productivity` 后，没有发现只能作为隐藏后台工具、无法作为独立对话主体的条目。

这不意味着所有角色都必须画成真人：

- `PERSON_PERSPECTIVE`：人物型 Skill 角色；
- `PROFESSIONAL_ADVISOR`：功能型顾问角色；
- `TASK_ASSISTANT`：功能型任务角色；
- `WORKFLOW_CAPABILITY`：功能型工作流角色。

当前 44 项主类型数量：

| 主类型 | 数量 | 角色页处理 |
|---|---:|---|
| 人物视角 | 19 | 独立 Skill 角色；真人型需 `AI 模拟角色` 声明 |
| 专业顾问 | 9 | 独立功能型 Skill 角色 |
| 任务助手 | 10 | 独立功能型 Skill 角色 |
| 工作流能力 | 6 | 独立功能型 Skill 角色；不因“工作流”被隐藏 |
| **合计** | **44** | **全部进入角色目录** |

如果未来新增真正的纯工具型 Skill，应另设 `visibleAsRole=false` 或等价产品字段；不能从 `WORKFLOW_CAPABILITY` 自动推断隐藏。

---

## 4. 发现分类：保留原五类，新增两类

原 UI 图和 v0.2 规格中的五类：

- 思考方法
- 职业成长
- 研究学习
- 产品创造
- 沟通表达

对 44 项仍然有价值，不应为了数据变化完全推翻选定视觉。问题主要是新增的办公/专业事务与生活类角色没有自然入口。

因此建议采用 **7 个主发现分类 + 全部**：

| 分类 | 用户侧说明 | 典型角色 |
|---|---|---|
| 思考方法 | 检查假设、比较框架、识别风险与盲区 | 芒格、纳瓦尔、塔勒布、弗洛伊德 |
| 职业成长 | 求职、职业选择、职场协作与考试路径 | 张雪峰、职业发展顾问、考公备考教练 |
| 研究学习 | 学习理解、技术研究、事实核查 | 费曼、卡帕斯、苏茨克维尔、学习规划师、事实核查助手 |
| 产品创造 | 产品、创业、经营、增长与竞品判断 | 马斯克、乔布斯、张一鸣、产品与竞品分析助手 |
| 沟通表达 | 内容、传播、谈判、表达与文案 | MrBeast、X 增长导师、内容策划助手、去AI化助手 |
| 办公事务 | 会议、文档、合同、人事、交接与申报材料 | 会议行动助手、合同检查、软著/专利整理、办公文档助手 |
| 生活支持 | 消费、自我管理、关系、礼仪与文化陪伴 | 预算消费助手、身心管理教练、关系沟通伙伴、人情礼仪助手 |

Chip 顺序固定为：

`全部 / 思考方法 / 职业成长 / 研究学习 / 产品创造 / 沟通表达 / 办公事务 / 生活支持`

Chip 继续单行横向滚动，不增加第二行分类栏。

分类是**发现入口**，不是能力边界。每个角色只配置一个 `primaryDiscoveryCategory`，搜索和详情仍使用官方 Catalog 的多维标签呈现其跨领域能力。

---

## 5. 44 项主发现分类完整映射

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
| 34 | `budget-consumption-coach` | 预算与消费决策助手 | 专业顾问 | 生活支持 |
| 35 | `habit-wellbeing-coach` | 习惯与身心管理教练 | 专业顾问 | 生活支持 |
| 36 | `relationship-dialogue-practice` | 关系沟通练习伙伴 | 任务助手 | 生活支持 |
| 37 | `chinese-social-etiquette` | 人情世故与礼仪助手 | 专业顾问 | 生活支持 |
| 38 | `culture-fortune-entertainment` | 传统命理文化陪伴 | 专业顾问 | 生活支持 |
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
- 生活支持：5
- 合计：44

---

## 6. 角色页展示事实源建议

新增一个极小的 UI 展示 Manifest，例如：

`app/src/main/assets/skill_role_presentation_v1.json`

只保存 UI-02 官方 Catalog 中缺失、且必须由产品人工决定的字段：

```json
{
  "schemaVersion": 1,
  "entries": [
    {
      "skillId": "richard_feynman",
      "primaryDiscoveryCategory": "RESEARCH_LEARNING",
      "featuredOrder": 3
    }
  ]
}
```

允许字段仅包括：

- `skillId`
- `primaryDiscoveryCategory`
- `featuredOrder`（可空）

不得复制：名称、summary、主类型、风险、发布状态、assetPath、Prompt、能力标签等官方 Catalog 已有事实。

加载时必须与当前 `OfficialSkillCatalog` 做集合校验：

- 44 个官方 ID 必须一一有分类；
- 不允许未知 ID；
- 不允许重复 ID；
- `featuredOrder` 若存在必须唯一、连续；
- Catalog 新增/删除角色时，不允许静默漏分类。

这样“角色发现分类”是独立产品事实，但角色身份和能力仍由唯一官方 Catalog 决定。

---

## 7. 固定「推荐角色」建议

因为用户已确认当前没有真实个性化推荐，所以首页推荐区必须是**编辑精选/固定展示**，不从 `defaultOrder` 假装推导。

建议初始固定三项：

1. Hero：`research-fact-checker`｜调研与事实核查助手
2. 次级：`product-competition-analyst`｜产品与竞品分析助手
3. 次级：`richard_feynman`｜理查德·费曼

理由：

- 对应选定 A 图原先“研究 / 产品 / 思考”的三种发现心智；
- 覆盖工作流、专业顾问、人物视角三种不同类型；
- 不以名气、排名或风险等级作为可靠性评分；
- 文案仍只写 `推荐角色`，不写 `为你推荐`。

实现上使用显式 `featuredOrder=1..3`，不要用 Catalog `defaultOrder` 的前三项替代。

若用户不希望冻结具体三项，也可以保留 `featuredOrder` 机制，在 UI 实施前另行指定；但不能退回“自动取 defaultOrder 前三项”。

---

## 8. 头像与角色视觉策略

### 8.1 人物型角色

- 继续优先复用仓库已有的稳定人物头像资产；
- 卡片保留短标签 `AI 模拟角色`；
- 详情/首次使用显示完整非本人声明；
- 找不到稳定人物头像时必须使用明确的稳定 fallback，不得把候选图裁剪当生产资产。

### 8.2 功能型角色

第 16、21～44 中的非人物角色不伪装成真人。

建议使用统一的**功能角色身份图形**：

- 根据 `primaryDiscoveryCategory` 使用稳定弱色；
- 角色名首字/简化图形作为稳定身份标识；
- 不使用随机头像；
- 不把 fallback 文本当“临时占位后忘记处理”，而是作为正式的非真人角色视觉方案；
- 后续若专门设计插画头像，可保持 Skill ID 不变替换视觉资产。

角色列表需要“人物感”，但人物感来自稳定姓名、能力定位、头像/标识与持续行为，不要求所有功能角色都画成人脸。

---

## 9. 44 项进入「对话」的真实链路缺口

角色页显示 44 项只是第一步。当前代码还有以下真实缺口：

```text
OfficialSkillCatalog（44）
  └─ OfficialSkillUseRequest
       └─ App.kt 暂存 skillId + 跳 HOME
            └─ 当前没有真正加入 Dialog 会话

Dialog / RoundtableViewModel
  └─ CharacterRepository
       └─ ensureCoreCharactersExist()
            └─ skills_config.json（旧 20）
```

因此若不修，会出现：

- 角色页可看到 44 项；
- 第 21～44 项可以从官方执行器运行；
- 但无法作为 `currentParticipantIds` 中真正的对话 Skill 角色。

### 9.1 推荐的最小兼容方案

UI-02 不重写整套对话数据库和 Orchestrator。采用“官方 Catalog 为事实源，Character 为兼容适配层”的最小迁移：

1. 从当前有效 `OfficialSkillCatalog` 生成 44 项 conversation role profile；
2. 对 20 个已有同 ID Character，仅保留仍有价值的本地视觉/语音补充字段；名称、summary、assetPath、order、可执行状态由官方 Catalog 覆盖；
3. 对第 21～44 项创建对应 Character 兼容行，使现有 `RoundtableOrchestrator` 可以按 Skill ID 找到并加载正式 SKILL 资产；
4. 不新增 Room 表、不改 Character 主键、不改消息 senderId 协议；
5. 该同步必须在“创建/增加角色”动作完成前可等待，不能依赖异步 onCreate seeding 竞态；
6. `开始新对话` 使用原子动作：创建会话 + 指定首个 Skill ID，再发布为当前会话；
7. `增加到当前会话` 只有在目标官方 Skill 已成功解析为可对话角色后才修改 participantIds；
8. 成功使用后才写 `OfficialSkillPreferences.recordSkillUsed()`；打开详情、收藏、筛选都不写最近使用。

### 9.2 不采用的方案

- 不把 `skills_config.json` 扩成新的 44 项权威 Catalog；
- 不维护两套 44 项名称/summary/assetPath；
- 不因为旧 Character 只有 20 项就把角色页砍回 20 项；
- 不把 `OfficialSkillUseRequest` 的“跳 HOME”当作已经成功加入对话。

---

## 10. 对 UI-02 v0.2 的覆盖关系

本文审查通过后，对现有 UI-02 v0.2 规格做以下定向覆盖：

1. “角色全集”从旧 Character 视角改为当前有效 OfficialSkillCatalog 44 项；
2. 分类 Chip 从原 5 类扩为 7 类，原 5 类名称与视觉顺序保持；新增 `办公事务 / 生活支持`；
3. 所有 44 项均进入【角色】目录，不因 `WORKFLOW_CAPABILITY` 自动隐藏；
4. 固定推荐由显式 `featuredOrder` 决定，不取 `defaultOrder` 前三名；
5. 角色页展示模型以 OfficialSkillDefinition 为主，Character 只作为旧对话执行兼容层和少量视觉补充；
6. UI-02 的完成条件增加：“第 21～44 项至少能真实完成开始新对话 / 增加到当前会话，并能在对话中作为 participant 被解析”。

其余已经审查的 A/B 双布局、视觉基线、搜索、收藏、最近使用、详情页和“推荐角色”非个性化文案规则继续有效。

---

## 11. 需要用户审查的决定

### R1｜44 项是否全部作为 Skill 角色进入目录

**建议：全部 44 项进入。**

工作流主类型不等于后台工具；当前 6 个工作流资产都具有用户侧角色身份和独立交互职责。

### R2｜发现分类是否从 5 类扩到 7 类

**建议：保留原五类，新增 `办公事务 / 生活支持`。**

这样最大程度延续已选 B 图，同时完整覆盖新增角色，不把合同、会议、身心、消费等硬塞进错误分类。

### R3｜固定推荐三项

**建议：`调研与事实核查助手 / 产品与竞品分析助手 / 理查德·费曼`。**

这是编辑精选，不是个性化，也不是排名。

### R4｜后 24 项功能角色头像

**建议：使用稳定的非真人功能角色身份图形，不为了“人物化”生成虚构人脸。**

真人型角色继续用人物头像并标记 `AI 模拟角色`。

---

## 12. 状态边界

在 R1～R4 获得用户审查前：

- 本文保持“草稿”；
- 原 UI-02 Plan 保持“需修订 / 不可施工”；
- 不开始 Android 生产代码；
- 不把 44 项分类映射写入生产 assets；
- 不宣称 44 项已经可以从新角色页进入对话。

审查通过后，下一步才是重写 `docs/superpowers/plans/2026-09-10-ui-02-role-page.md`，把 44 项数据源、7 分类和对话兼容迁移拆成可执行 TDD Task。