# 见域官方 Skill 候选目录映射

> 状态：PR08-C 收尾规格。本文建立 44 项候选、研究来源、正式实现来源、原创性、许可和治理映射，不复制第三方 Skill 正文，不修改 App assets、`skills_config.json`、Schema 或生产代码。
>
> 启动基线：`main@d6db5e1b825bab60852b6365eef0ef6deb5bb970`
>
> 研究输入：`docs/skills-catalog@bd493672a35c77ff5c19fa3a3e2bb489bf60eb1b` 中的 `docs/skills/research-catalog/`
>
> 关联文档：[Skill 分类模型](./jianyu-skill-taxonomy.md)；[Skill 发现与推荐模型](./jianyu-skill-discovery-and-recommendation.md)。

## 1. 冻结结论

精确候选数量为 **44**：

```text
当前内置候选 20
+ 研究目录条目 25
- 重复的“张雪峰视角” 1
= 官方候选 44
```

用户最终确认：

- 44 项全部属于 V1 阶段目标，允许按 V1.0、V1.1 等批次开发和验收，不要求 V1.0 首次发布时全部同时可用；
- 现有 20 项全部可搜索、浏览，并在所有场景中正常参与自动推荐；人物争议或风险标签不得作为降低排名的依据；
- 第三方内容在许可证明确、适用且符合产品边界时优先复用，并履行署名、Notice 和修改说明；来源不明、无许可证或无法拆分时独立原创；
- `office-document-productivity` 保留 ID，正式中文定位为“办公文档助手”；
- `original-expression-naturalizer` 保留 ID，本 PR 不锁定用户侧中文名称，其职责是形成可直接发送、提交或使用的现实沟通文案。

“V1 阶段目标”“当前资产”“自动推荐资格”和“已可发布”是四个不同状态。未通过来源、许可、原创性、安全、质量和能力门禁的候选不得伪装成已完成。

## 2. 来源治理规则

### 2.1 需求研究来源与正式实现来源

| 字段 | 定义 | 治理结论 |
|---|---|---|
| 需求研究来源 | 用来发现用户需求、能力方向或常见工作流的第三方项目 | 不自动构成正式实现依赖；无许可证不等于能力方向必须删除 |
| 正式实现来源 | 实际进入见域提示词、工作流、示例、代码、脚本、模板或素材的具体来源 | 许可证明确、适用且符合产品边界时优先复用，并固定完整 40 位 Commit、目标文件、许可证、Notice 与修改说明 |
| 是否使用第三方具体表达 | 是否采用第三方正文、特殊结构、独特流程或示例 | 来源不明、无许可证、授权范围不清或无法与无授权部分拆分时，必须删除并独立原创 |
| 是否使用第三方代码或脚本 | 是否采用脚本、自动化、模板代码或依赖 | 必须单独核验许可证、供应链、平台能力与安全 |
| 原创性状态 | 是否完成与全部相关上游的逐项比较 | 未完成时只能写“原创性待核验” |
| 正式实现适用许可证 | 最终实际采用内容所受的许可证 | 不能由需求研究来源的许可证自动推导 |

默认实施规则：

1. 正式实现实际采用的第三方内容具有明确 MIT、Apache-2.0、CC0 等许可，且授权范围、商业使用、修改与分发条件适用于见域时，优先复用并履行相应义务。
2. 来源不明、无许可证、许可证不适用、授权范围无法确认，或者具体表达与无授权部分无法拆分时，改为见域独立原创。
3. 第三方项目只作为需求研究来源时，不自动成为实现依赖，也不自动给见域草案赋予同一许可证。

研究目录 Commit `bd493672a35c77ff5c19fa3a3e2bb489bf60eb1b` 声明草案借鉴能力方向和工作流、不复制第三方正文或脚本。但当前尚未完成与全部上游内容的逐项相似性核验，因此不能写成“已经确认完全原创”。

统一声明：

> 研究草案声明为原创改写，但尚未完成与全部上游内容的逐项相似性核验。

### 2.2 Commit 证据分层

| 版本 | 用途 |
|---|---|
| 外部仓库当前最新 Commit | 描述外部仓库当前状态，可能继续变化 |
| 本次审计固定 Commit | 复现 PR08-C 的研究时核验结果，不代表未来采用 |
| 未来正式实现实际采用 Commit | 如果 PR09 实际采用第三方内容，发布前必须锁定的版本 |

下文外部 Commit 均为**研究时核验版本，不代表未来正式实现采用版本**。不得用 `HEAD`、分支名、短 SHA 或带省略号的 SHA 作为精确证据。

### 2.3 许可与产品风险独立

- 许可证允许，不代表人物、金融、法律、医疗、心理、隐私、商标、肖像或人格权风险已经解决。
- 产品风险高，不等于许可证不允许。
- 根目录许可证不自动覆盖书籍摘录、图片、二维码、音频、外部文章、视频文字稿、子模块或搬运内容。
- 风险标签用于推荐理由、信息披露、输出边界、时效核验和专业转介，不得作为降低人物 Skill 排名的依据。
- 发布状态采用：`阻断重构 > 原创性或许可待核验 > 补 Notice 与声明 > 可发布`。

## 3. 研究目录 25 项完整对账

| # | 研究 ID | 研究名称 | 官方映射 | 处理结果 |
|:---:|---|---|---|---|
| 01 | `zhangxuefeng-perspective` | 张雪峰视角（现有 App 资产） | `zhang_xuefeng` | 去重合并；不新增第 45 项 |
| 02 | `civil-service-coach` | 考公备考教练 | `civil-service-coach` | 保留 |
| 03 | `public-document-coach` | 公文与材料教练 | `public-document-coach` | 保留 |
| 04 | `study-planner` | 学习规划师 | `study-planner` | 保留 |
| 05 | `career-navigator` | 职业发展顾问 | `career-navigator` | 保留 |
| 06 | `resume-interview-coach` | 简历与面试教练 | `resume-interview-coach` | 保留 |
| 07 | `workplace-communication` | 职场沟通陪练 | `workplace-communication` | 保留 |
| 08 | `manager-expectation-review` | 领导预期复盘助手 | `manager-expectation-review` | 保留 |
| 09 | `team-handover` | 团队交接与协作助手 | `team-handover` | 保留 |
| 10 | `meeting-to-action` | 会议纪要与行动项助手 | `meeting-to-action` | 保留 |
| 11 | `report-proposal-writer` | 汇报与方案写作助手 | `report-proposal-writer` | 保留 |
| 12 | `contract-checklist` | 合同要点检查助手 | `contract-checklist` | 保留 |
| 13 | `hr-document-assistant` | 人事文档助手 | `hr-document-assistant` | 保留 |
| 14 | `research-fact-checker` | 调研与事实核查助手 | `research-fact-checker` | 保留 |
| 15 | `budget-consumption-coach` | 预算与消费决策助手 | `budget-consumption-coach` | 保留 |
| 16 | `habit-wellbeing-coach` | 习惯与身心管理教练 | `habit-wellbeing-coach` | 保留 |
| 17 | `relationship-dialogue-practice` | 关系沟通练习伙伴 | `relationship-dialogue-practice` | 保留 |
| 18 | `chinese-social-etiquette` | 人情世故与礼仪助手 | `chinese-social-etiquette` | 保留 |
| 19 | `culture-fortune-entertainment` | 传统命理文化陪伴 | `culture-fortune-entertainment` | 保留 |
| 20 | `content-creator` | 内容策划与文案助手 | `content-creator` | 保留 |
| 21 | `product-competition-analyst` | 产品与竞品分析助手 | `product-competition-analyst` | 保留 |
| 22 | `software-copyright-organizer` | 软件著作权材料整理助手 | `software-copyright-organizer` | 保留 |
| 23 | `patent-disclosure-organizer` | 专利交底材料整理助手 | `patent-disclosure-organizer` | 保留 |
| 24 | `office-document-productivity` | 办公文档助手 | `office-document-productivity` | 保留 ID；能力重构中 |
| 25 | `academic-ai-evasion` | 原研究方向 | `original-expression-naturalizer` | 排除检测对抗目标；保留新能力方向和现有 ID |

研究目录全部 25 项均有去向：23 项保持同 ID，1 项去重合并，1 项排除检测对抗目标并重构为现有 `original-expression-naturalizer`。张雪峰只保留一个官方候选。

## 4. 44 项官方候选

| # | Skill ID | 名称 | 主类型 | 当前状态 | 正式实现来源 / 许可与原创性 | V1 缺口 |
|:---:|---|---|---|---|---|---|
| 01 | `zhang_xuefeng` | 张雪峰 | 人物视角 | 现有资产；V1 保留；可搜索、浏览和自动推荐 | 独立上游候选见第 5 节；未来实际采用版本待定 | 补齐来源、声明、时效、内容和资产治理 |
| 02 | `elon_musk` | 埃隆·马斯克 | 人物视角 | 同上 | 同上 | 同上 |
| 03 | `richard_feynman` | 理查德·费曼 | 人物视角 | 同上 | 同上 | 同上 |
| 04 | `charlie_munger` | 查理·芒格 | 人物视角 | 同上 | 同上 | 同上；金融风险 |
| 05 | `naval_ravikant` | 纳瓦尔 | 人物视角 | 同上 | 同上 | 同上；金融风险 |
| 06 | `steve_jobs` | 史蒂夫·乔布斯 | 人物视角 | 同上 | 同上 | 同上 |
| 07 | `nassim_taleb` | 纳西姆·塔勒布 | 人物视角 | 同上 | 同上 | 同上；金融风险 |
| 08 | `andrej_karpathy` | 安德烈·卡帕斯 | 人物视角 | 同上 | 同上 | 同上；高时效 |
| 09 | `zhang_yiming` | 张一鸣 | 人物视角 | 同上 | 同上 | 同上；高时效 |
| 10 | `paul_graham` | 保罗·格雷厄姆 | 人物视角 | 同上 | 同上 | 同上 |
| 11 | `ilya_sutskever` | 伊利亚·苏茨克维尔 | 人物视角 | 同上 | 同上 | 同上；高时效 |
| 12 | `donald_trump` | 唐纳德·特朗普 | 人物视角 | 同上 | 同上 | 同上；政治敏感、高时效 |
| 13 | `mr_beast` | 吉米·唐纳森（MrBeast） | 人物视角 | 同上 | 同上 | 同上；高时效 |
| 14 | `justin_sun` | 孙宇晨 | 人物视角 | 同上 | 上游许可证尚未核验，不作许可结论 | 金融风险、加密合规、来源待核验 |
| 15 | `sigmund_freud` | 西格蒙德·弗洛伊德 | 人物视角 | 同上 | 独立上游候选见第 5 节 | 心理健康边界与专业转介 |
| 16 | `x_mentor` | X 增长导师 | 专业顾问 | 现有历史阵容资产；V1 保留；可搜索、浏览和自动推荐 | 独立上游候选见第 5 节 | 非本人/非官方声明、高时效、来源与资产治理 |
| 17 | `feng_ge` | 峰哥亡命天涯 | 人物视角 | 现有资产；V1 保留；可搜索、浏览和自动推荐 | 独立上游候选见第 5 节 | 高时效、来源与资产治理 |
| 18 | `changpeng_zhao` | 赵长鹏（CZ） | 人物视角 | 同上 | 上游许可证尚未核验，不作许可结论 | 金融风险、加密合规、来源待核验 |
| 19 | `duan_yongping` | 段永平 | 人物视角 | 同上 | 独立上游候选见第 5 节 | 金融风险、来源与资产治理 |
| 20 | `tim_cook` | 蒂姆·库克 | 人物视角 | 同上 | 独立上游候选见第 5 节 | 高时效、音频/语音资产治理 |
| 21 | `civil-service-coach` | 考公备考教练 | 专业顾问 | 研究草案；V1 纳入；正式实现待完成 | 正式实现方案待形成；许可明确且适用的内容优先复用，来源不明或无法拆分部分独立原创；原创性待核验 | 考试版本、来源与作弊边界 |
| 22 | `public-document-coach` | 公文与材料教练 | 任务助手 | 同上 | 同上 | 单位规范与审批 |
| 23 | `study-planner` | 学习规划师 | 任务助手 | 同上 | 同上 | 效果承诺与个人差异 |
| 24 | `career-navigator` | 职业发展顾问 | 专业顾问 | 同上 | 同上 | 就业与薪资时效 |
| 25 | `resume-interview-coach` | 简历与面试教练 | 任务助手 | 同上 | 同上 | 不得伪造履历 |
| 26 | `workplace-communication` | 职场沟通陪练 | 专业顾问 | 同上 | 同上 | 操控、歧视与报复风险 |
| 27 | `manager-expectation-review` | 领导预期复盘助手 | 任务助手 | 同上 | 同上 | 隐私、冒充与动机臆测 |
| 28 | `team-handover` | 团队交接与协作助手 | 工作流能力 | 同上 | 同上 | 组织隐私与保密 |
| 29 | `meeting-to-action` | 会议纪要与行动项助手 | 工作流能力 | 同上 | 同上 | 敏感会议与责任确认 |
| 30 | `report-proposal-writer` | 汇报与方案写作助手 | 任务助手 | 同上 | 同上 | 事实、数据与引用 |
| 31 | `contract-checklist` | 合同要点检查助手 | 任务助手 | 同上 | 同上 | 法律边界与人工复核 |
| 32 | `hr-document-assistant` | 人事文档助手 | 任务助手 | 同上 | 同上 | 歧视、隐私与审批 |
| 33 | `research-fact-checker` | 调研与事实核查助手 | 工作流能力 | 同上 | 同上 | 来源、联网与信息时效 |
| 34 | `budget-consumption-coach` | 预算与消费决策助手 | 专业顾问 | 同上 | 同上 | 金融边界与收益承诺 |
| 35 | `habit-wellbeing-coach` | 习惯与身心管理教练 | 专业顾问 | 同上 | 同上 | 健康与危机转介 |
| 36 | `relationship-dialogue-practice` | 关系沟通练习伙伴 | 任务助手 | 同上 | 同上 | 同意边界、操控与冒充 |
| 37 | `chinese-social-etiquette` | 人情世故与礼仪助手 | 专业顾问 | 同上 | 同上 | 地区差异与违规送礼 |
| 38 | `culture-fortune-entertainment` | 传统命理文化陪伴 | 专业顾问 | 同上 | 同上 | 仅供娱乐、禁止重大决策指令 |
| 39 | `content-creator` | 内容策划与文案助手 | 任务助手 | 同上 | 同上 | 版权、事实与风格模仿 |
| 40 | `product-competition-analyst` | 产品与竞品分析助手 | 专业顾问 | 同上 | 同上 | 公开信息与商业机密 |
| 41 | `software-copyright-organizer` | 软件著作权材料整理助手 | 工作流能力 | 同上 | 同上 | 权属、法律边界与材料真实性 |
| 42 | `patent-disclosure-organizer` | 专利交底材料整理助手 | 工作流能力 | 同上 | 同上 | 涉密、权属与法律边界 |
| 43 | `office-document-productivity` | 办公文档助手 | 工作流能力 | 能力方向纳入 V1；能力重构中；当前不可发布 | 见域 Android 能力独立设计；第三方仅为研究来源；必须独立原创 | 完成 Android 能力、来源、隐私与质量验收 |
| 44 | `original-expression-naturalizer` | 去AI化助手 | 任务助手 | 能力方向纳入 V1；阻断重构；当前不可发布 | 见域从零原创设计；不得复制检测对抗提示词 | 完成现实沟通文案、诚信、原创性与质量验收 |

第 01～20 项在所有场景中正常参与自动推荐。风险标签不得改变其正常排序或推荐资格，只用于理由、披露、输出边界、时效核验和专业转介。第 43、44 项不得写成“补 Notice 即可上线”。

## 5. 当前 20 项直接上游证据

`nuwa-skill` 是蒸馏、生成或组织人物 Skill 的元工具或方法论来源，不是所有人物正文的直接上游。下表根据本仓库 README、克隆脚本、安装说明和独立仓库逐项记录直接上游。

所有 20 项统一显示：

> 这是基于公开资料生成的 AI 模拟视角，不代表本人，也不保证复现其当前或完整观点。

| # | Skill ID | 当前本仓库路径 | 直接上游仓库 | 研究时核验 Commit | LICENSE 路径 / Blob SHA | 附带资产 | 来源核验状态 / 治理标签 |
|:---:|---|---|---|---|---|---|---|
| 01 | `zhang_xuefeng` | `app/src/main/assets/skills/zhangxuefeng-skill-main/SKILL.md` | `alchaincyf/zhangxuefeng-skill`；目标 `SKILL.md` | `a9a71563a39f1ba8e5421d1b9b44e318c691f37b` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 图片已确认（README 指向 `assets/hero.gif`）；其余待逐目录核验 | 上游与许可证文件已核验；本仓库一致性、素材和人格权待核验；[非本人声明][高时效][生产资产清理] |
| 02 | `elon_musk` | `app/src/main/assets/skills/elon-musk-skill-main/SKILL.md` | `alchaincyf/elon-musk-skill`；目标 `SKILL.md` | `5a7d8cf0f23ca6071d18ed8c5c80e8996459a443` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 尚未逐目录核验 | 同上；[非本人声明][高时效][生产资产清理] |
| 03 | `richard_feynman` | `app/src/main/assets/skills/feynman-skill-main/SKILL.md` | `alchaincyf/feynman-skill`；目标 `SKILL.md` | `5ae5c5079909ef8654cc9815fe58fb3b89bfcb4c` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 尚未逐目录核验 | 同上；[非本人声明][生产资产清理] |
| 04 | `charlie_munger` | `app/src/main/assets/skills/munger-skill-main/SKILL.md` | `alchaincyf/munger-skill`；目标 `SKILL.md` | `2d5d7a388a0c4c7865accda39f1f2e741c886d9d` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 尚未逐目录核验 | 同上；[非本人声明][金融风险][生产资产清理] |
| 05 | `naval_ravikant` | `app/src/main/assets/skills/naval-skill-main/SKILL.md` | `alchaincyf/naval-skill`；目标 `SKILL.md` | `259e452ef6f6c2bfdbe30368f7c85bc683fe1949` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 尚未逐目录核验 | 同上；[非本人声明][金融风险][生产资产清理] |
| 06 | `steve_jobs` | `app/src/main/assets/skills/steve-jobs-skill-main/SKILL.md` | `alchaincyf/steve-jobs-skill`；目标 `SKILL.md` | `cd724b0e2e2d9e83a436063b5b915294b5925d28` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 尚未逐目录核验 | 同上；[非本人声明][生产资产清理] |
| 07 | `nassim_taleb` | `app/src/main/assets/skills/taleb-skill-main/SKILL.md` | `alchaincyf/taleb-skill`；目标 `SKILL.md` | `48303e725d24a8865731baa4869caa4d49014704` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 尚未逐目录核验 | 同上；[非本人声明][金融风险][生产资产清理] |
| 08 | `andrej_karpathy` | `app/src/main/assets/skills/karpathy-skill/SKILL.md` | `alchaincyf/karpathy-skill`；目标 `SKILL.md` | `fb9ec5b891616b36743f4560e77e57860768aceb` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 尚未逐目录核验 | 同上；[非本人声明][高时效][生产资产清理] |
| 09 | `zhang_yiming` | `app/src/main/assets/skills/zhang-yiming-skill/SKILL.md` | `alchaincyf/zhang-yiming-skill`；目标 `SKILL.md` | `6708af58665dfb60154592d04dc203e0b74045a2` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 尚未逐目录核验 | 同上；[非本人声明][高时效][生产资产清理] |
| 10 | `paul_graham` | `app/src/main/assets/skills/paul-graham-skill/SKILL.md` | `alchaincyf/paul-graham-skill`；目标 `SKILL.md` | `8de3d2bf4e0c301ea3caf015b189307f8d8d8dc0` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 尚未逐目录核验 | 同上；[非本人声明][生产资产清理] |
| 11 | `ilya_sutskever` | `app/src/main/assets/skills/ilya-sutskever-skill/SKILL.md` | `alchaincyf/ilya-sutskever-skill`；目标 `SKILL.md` | `056284b63c2d4648c3c1fa15162011d08f85a717` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 尚未逐目录核验 | 同上；[非本人声明][高时效][生产资产清理] |
| 12 | `donald_trump` | `app/src/main/assets/skills/trump-skill/SKILL.md` | `alchaincyf/trump-skill`；目标 `SKILL.md` | `4bdb94895a01a84b9f55d90ae5889747c0736757` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 尚未逐目录核验 | 同上；[非本人声明][高时效][政治敏感][生产资产清理] |
| 13 | `mr_beast` | `app/src/main/assets/skills/mrbeast-skill/SKILL.md` | `alchaincyf/mrbeast-skill`；目标 `SKILL.md` | `504c360a0b35c6f8a4e635f8857480e1655ab070` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 尚未逐目录核验 | 同上；[非本人声明][高时效][生产资产清理] |
| 14 | `justin_sun` | `app/src/main/assets/skills/sun-yuchen-perspective/SKILL.md` | `alchaincyf/sun-yuchen-perspective`；目标 `SKILL.md` | `330e8eda1555707bcc0b37dfebf03f3c0dae7aa0` | 根目录 `LICENSE` 未取得 | 尚未逐目录核验 | 尚未核验，不作许可结论；[非本人声明][高时效][金融风险][加密合规][来源待核验][生产资产清理] |
| 15 | `sigmund_freud` | `app/src/main/assets/skills/freud-skill/SKILL.md` | `alchaincyf/freud-skill`；目标 `SKILL.md` | `f277002784c4dbd54300b301c11e5f1d8e6110aa` | `LICENSE` / `60797866e4ebc7b608a87e590eba4f3a817511a3` | 尚未逐目录核验 | 上游与许可证文件已核验；本仓库一致性和素材待核验；[非本人声明][心理健康][生产资产清理] |
| 16 | `x_mentor` | `app/src/main/assets/skills/x-mentor-skill/SKILL.md` | `alchaincyf/x-mentor-skill`；目标 `SKILL.md` | `6e618864d3a56b2bb57351d94135381674355507` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 尚未逐目录核验 | 同上；[非本人声明][高时效][生产资产清理] |
| 17 | `feng_ge` | `app/src/main/assets/skills/fengge-skill/SKILL.md` | `Walshyu/fengge-skill`；目标 `SKILL.md` | `e5a65288d97dff323c32c01ae1f21bdb1ab1995b` | `LICENSE` / `e4a5b379535353699bbd3aa11c58cd57f178cecc` | 尚未逐目录核验 | 上游与许可证文件已核验；方法论提及 Nuwa 不改变直接上游；[非本人声明][高时效][生产资产清理] |
| 18 | `changpeng_zhao` | `app/src/main/assets/skills/cz-skill/SKILL.md` | `0xquqi/cz-skill`；目标 `SKILL.md` | `1c60c0dce3ed77b487484cb699df9478d658fce9` | 根目录 `LICENSE` 未取得 | `research/` 索引已确认；其余待核验 | 尚未核验，不作许可结论；[非本人声明][高时效][金融风险][加密合规][来源待核验][生产资产清理] |
| 19 | `duan_yongping` | `app/src/main/assets/skills/duan-yongping-skill/SKILL.md` | `zwbao/duan-yongping-skill`；目标 `SKILL.md` | `611d91825f387de13208a8ad3bfd6c28ea961564` | `LICENSE` / `6d9c82e52d9fcbc3e6817379bb8c5b299db98d18` | 尚未逐目录核验 | 上游与许可证文件已核验；本仓库一致性和素材待核验；[非本人声明][金融风险][生产资产清理] |
| 20 | `tim_cook` | `app/src/main/assets/skills/tim-cook-skill/SKILL.md` | `heywanrong/tim-cook-skill`；目标 `SKILL.md` | `27ec6d319bcf139ecc0dca513401c4f107126df6` | `LICENSE` / `b95c3fcf6bcd81713196a8523188be50fa712edf` | 音频/语音工作流已由上游说明确认；其余待核验 | 上游与许可证文件已核验；音频及素材另审；[非本人声明][高时效][生产资产清理] |

补充结论：

1. `alchaincyf/sun-yuchen-perspective@330e8eda1555707bcc0b37dfebf03f3c0dae7aa0` 的 README 显示 MIT 文字或徽章，但该 Commit 下根目录 `LICENSE` 未取得，因此尚未核验，不作许可结论。
2. `0xquqi/cz-skill@1c60c0dce3ed77b487484cb699df9478d658fce9` 的 README 写有 MIT，但该 Commit 下根目录 `LICENSE` 未取得，因此尚未核验，不作许可结论。
3. 其余取得许可证文件的项目，只能说明对应文件在研究时核验版本中存在；是否与本仓库现有资产一致、是否包含未覆盖素材、未来是否采用，仍需 PR09 前逐项确认。
4. `[生产资产清理]` 在“尚未逐目录核验”的项目中表示阻断核验任务，不表示已断言每个目录都含二维码或音频。

## 6. 第 21～44 项研究来源与实现来源

下表中的需求研究来源来自研究目录固定 Commit `bd493672a35c77ff5c19fa3a3e2bb489bf60eb1b`。这些仓库帮助发现需求，不自动构成正式实现依赖。

| # | Skill ID | 需求研究来源 | 正式实现来源 | 第三方具体表达 / 代码脚本 | 原创性与许可状态 |
|:---:|---|---|---|---|---|
| 21 | `civil-service-coach` | `24kchengYe/human-skill-tree`；`bytesagain/ai-skills`；`jnMetaCode/agency-agents-zh` | 正式实现方案待形成；按“许可明确优先复用、来源不明独立原创”执行 | 规格阶段未采用 | 原创性待核验；研究来源不构成实现依赖 |
| 22 | `public-document-coach` | `24kchengYe/human-skill-tree`；`claude-office-skills/skills` | 同上 | 同上 | 同上 |
| 23 | `study-planner` | `24kchengYe/human-skill-tree`；`jnMetaCode/agency-agents-zh` | 同上 | 同上 | 同上 |
| 24 | `career-navigator` | `24kchengYe/human-skill-tree`；`alchaincyf/zhangxuefeng-skill` | 同上 | 同上 | 同上 |
| 25 | `resume-interview-coach` | `claude-office-skills/skills`；`proficientlyjobs/proficiently-claude-skills` | 同上 | 同上 | 同上 |
| 26 | `workplace-communication` | `24kchengYe/human-skill-tree`；`tmstack/awesome-persona-skills` | 同上 | 同上 | 同上 |
| 27 | `manager-expectation-review` | `vogtsw/boss-skills`；`tmstack/awesome-persona-skills` | 同上 | 同上 | 同上 |
| 28 | `team-handover` | `titanwings/colleague-skill`；`tmstack/awesome-persona-skills` | 同上 | 同上 | 同上 |
| 29 | `meeting-to-action` | `claude-office-skills/skills`；`bytesagain/ai-skills` | 同上 | 同上 | 同上 |
| 30 | `report-proposal-writer` | `claude-office-skills/skills`；`KKKKhazix/khazix-skills` | 同上 | 同上 | 同上 |
| 31 | `contract-checklist` | `claude-office-skills/skills`；`tinh2/skills-hub-registry` | 同上 | 同上 | 同上 |
| 32 | `hr-document-assistant` | `claude-office-skills/skills` | 同上 | 同上 | 同上 |
| 33 | `research-fact-checker` | `KKKKhazix/khazix-skills`；`claude-office-skills/skills` | 同上 | 同上 | 同上 |
| 34 | `budget-consumption-coach` | `24kchengYe/human-skill-tree`；`bytesagain/ai-skills` | 同上 | 同上 | 同上 |
| 35 | `habit-wellbeing-coach` | `24kchengYe/human-skill-tree`；`bytesagain/ai-skills` | 同上 | 同上 | 同上 |
| 36 | `relationship-dialogue-practice` | `24kchengYe/human-skill-tree`；`xixu-me/awesome-persona-distill-skills` | 同上 | 同上 | 同上 |
| 37 | `chinese-social-etiquette` | `24kchengYe/human-skill-tree` | 同上 | 同上 | 同上 |
| 38 | `culture-fortune-entertainment` | `tmstack/awesome-persona-skills` | 同上 | 同上 | 同上 |
| 39 | `content-creator` | `KKKKhazix/khazix-skills`；`bytesagain/ai-skills` | 同上 | 同上 | 同上 |
| 40 | `product-competition-analyst` | `Fokkyp/claude-skills`；`jnMetaCode/agency-agents-zh` | 同上 | 同上 | 同上 |
| 41 | `software-copyright-organizer` | `Fokkyp/SoftwareCopyright-Skill` | 同上 | 同上 | 同上 |
| 42 | `patent-disclosure-organizer` | `handsomestWei/patent-disclosure-skill` | 同上 | 同上 | 同上 |
| 43 | `office-document-productivity` | `dachent/skills`；`anthropics/skills` | 见域 Android 办公文档能力独立设计 | 不采用 Anthropic 文档类正文、模板、资源或脚本；不引入桌面执行承诺 | 必须独立原创；能力重构中；研究来源不构成实现依赖 |
| 44 | `original-expression-naturalizer` | 研究项 `academic-ai-evasion`；`redbaronyyyyy-eng/humanizer-zh-academic` | 见域从零原创设计的现实沟通文案能力 | 不采用检测对抗提示词、独特流程或第三方检测脚本 | 必须独立原创；阻断重构；研究来源不构成实现依赖 |

第 21～42 项统一适用声明：

> 研究草案声明为原创改写，但尚未完成与全部上游内容的逐项相似性核验。正式实现按“许可明确优先复用、来源不明独立原创”逐项确定。

### 6.1 `anthropics/skills` 定向核验

仓库 `anthropics/skills` 真实存在。本次研究时核验版本为：

```text
anthropics/skills@b29e7cf65e5cb78a5ac33d582270551bc74a14eb
```

该版本必须按子目录区分：

| 范围 | 许可证证据 | 结论 |
|---|---|---|
| 示例 Skill，例如 `skills/internal-comms/` | `skills/internal-comms/LICENSE.txt`，Apache-2.0，Blob `4f881c52d1f72f4cfb720e339e2d35c3058d01a9` | 可以按具体目录、文件和 Apache-2.0 条件研究或适配，不能用该结论覆盖其他目录 |
| `skills/docx/` | `skills/docx/LICENSE.txt`，Blob `c55ab42224874608473643de0a85736b7fec0730` | source-available，不是开源；不得默认复制、保留、改编或分发 |
| `skills/pdf/` | `skills/pdf/LICENSE.txt`，Blob `c55ab42224874608473643de0a85736b7fec0730` | 同上 |
| `skills/pptx/` | `skills/pptx/LICENSE.txt`，Blob `c55ab42224874608473643de0a85736b7fec0730` | 同上 |
| `skills/xlsx/` | `skills/xlsx/LICENSE.txt`，Blob `c55ab42224874608473643de0a85736b7fec0730` | 同上 |

对 `office-document-productivity` 的正式结论：

- Anthropic 文档类 Skill 只作为能力研究来源；
- 不默认复制其正文、脚本、模板或资源；
- 见域 Android 办公文档助手独立重新设计；
- 不把 Anthropic 桌面、文件生成或执行能力承诺带入 Android V1；
- 未来若采用其他 Apache-2.0 示例目录的具体内容，仍需固定实际采用 Commit、文件、Notice 和修改说明。

## 7. 两个特殊 Skill

### 7.1 `office-document-productivity`

正式中文定位：**办公文档助手**。

允许范围：

- Word 正文、大纲和结构；
- PPT 逐页大纲和演讲要点；
- Excel 字段设计、公式思路和数据整理逻辑；
- 会议纪要、周报、月报、项目计划；
- 通知、申请、汇报材料；
- 模板、检查清单；
- 可复制文本、Markdown、表格或 CSV。

禁止宣称直接控制 Windows Office、执行 COM/VBA/宏/PowerShell、操作桌面文件系统、自动修改电脑文件或保证复杂格式完全保真。

状态：

```text
能力方向纳入 V1
正式实现尚未完成
通过重构、来源、能力与质量验收前不可发布
```

### 7.2 `original-expression-naturalizer`

保留现有 Skill ID，用户侧中文名称冻结为“去AI化助手”。名称出现时必须就近显示固定边界：“让真实内容更像你本人表达，不用于规避检测或伪造事实。”

职责：

> 根据用户提供的真实事实、关系背景、行动目标，以及用户确认带入的其他 Skill 行动建议或材料，生成可直接发送、提交或实际使用的现实沟通文案。

它可以生成请假、申请、说明、道歉、拒绝、协商、催办、求职邮件、自我介绍、申请理由、工作汇报，以及发给领导、同事、客户、老师、家人或朋友的信息。

它不得整理多个 Skill 的讨论意见、统一圆桌观点、改写其他 Skill 的分析结论或掩盖分歧；不得以检测对抗为目标、伪造事实或身份、隐瞒明确禁止的 AI 使用、删除必要声明，也不得复制原研究来源中的检测对抗提示词。

状态：

```text
能力方向纳入 V1
正式实现尚未完成
必须从零原创设计
阻断重构并完成原创性、诚信与质量验收前不可发布
```

## 8. 展示与推荐规则

### 8.1 人物与历史阵容资产

- 第 01～20 项全部可搜索、浏览，并在所有场景中正常参与自动推荐。
- 全部显示 `[非本人声明]`。
- 高时效、金融、加密、政治或心理场景可以叠加风险标签，并强化来源、时效、免责声明、输出边界和专业转介。
- 风险标签不得作为降低排名、全局隐藏或改成只能手动邀请的依据。
- 人物知名度、财富、职位和粉丝量不得作为专业可靠性依据。

### 8.2 普通顾问、助手和工作流

- 普通 Skill 可以只执行通用治理规则，不要求每项至少一个专项标签。
- 推荐时显示目标任务、输入、输出、风险、时效和当前发布状态。
- 未通过门禁的候选可以显示为研究、开发或能力重构状态，但不能伪装成可执行。

## 9. 接入门禁

候选进入正式 App 版本前必须满足：

1. 候选仍在 44 项 V1 清单内，Skill ID 唯一。
2. 需求研究来源与正式实现来源分别记录。
3. 许可明确且适用的正式实现来源优先复用；来源不明、无许可证或无法拆分时独立原创。
4. 正式采用的第三方内容固定完整 40 位 Commit、目标文件和许可证。
5. 许可证、Notice、修改说明和附带素材权利已核验。
6. 原创实现完成必要的逐项相似性核验。
7. 人物和历史阵容资产具有非本人声明、时效和风险规则。
8. Android 能力真实可行，不依赖未声明的桌面执行。
9. 隐私、敏感材料、联网和专业转介边界明确。
10. 自动推荐理由和风险治理规则可解释；不得因人物争议或风险标签降低排名、全局隐藏或限制为手动邀请。
11. 自动化测试、静态检查和本地验收方案已建立。
12. PR08-F 最终规格已统一；用户批准并合并 PR08-F 后，才由 PR09 独立实施。

## 10. 本任务未做

- 未修改 Android 生产代码、assets、`skills_config.json`、Room、Kotlin、Compose、资源、Gradle、CI 或测试；
- 未删除二维码、音频、脚本或其他生产资产；
- 未把研究分支草案合并进 App；
- 未完成 44 项正式实现；
- 未替未来正式实现选择实际采用的第三方 Commit；
- 未把研究时核验版本写成未来发布版本；
- 未提供用户创建、第三方导入或公开市场；
- 未虚构精确工期。

## 11. 验收条件

- 官方候选保持 44 项，编号 01～44 连续，Skill ID 唯一。
- 44 项属于 V1 阶段目标，允许按 V1.0、V1.1 等批次达到发布条件。
- 研究目录 25 项全部有去向，张雪峰只出现一个官方候选。
- 现有 20 项直接上游不再统一写成 `nuwa-skill`。
- 外部精确证据全部使用完整 40 位 Commit。
- `anthropics/skills` 被正确记录为存在，并区分示例目录与文档目录许可证。
- 第 21～44 项明确区分需求研究来源和正式实现来源。
- 正式实现遵循“许可明确优先复用，来源不明、无许可证或无法拆分时独立原创”。
- 未完成比对的草案只标记“原创性待核验”。
- 全部 20 项具有非本人声明，并在所有场景中正常参与自动推荐；风险标签不得降低其排名。
- 办公文档助手和现实沟通文案能力的状态不与“可发布”重叠。
- `original-expression-naturalizer` 的用户侧中文名称为“去AI化助手”，目录、详情、推荐确认、首次调用确认和成果导出说明均须就近显示诚信边界。
- 发布优先级保持“阻断重构 > 原创性或许可待核验 > 补 Notice 与声明 > 可发布”。
