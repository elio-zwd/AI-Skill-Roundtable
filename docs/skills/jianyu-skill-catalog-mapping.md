# 见域官方 Skill 候选目录映射

> 状态：PR08-C 精确目录。本文只建立候选与治理映射，不复制第三方 Skill 正文，不修改 App assets、`skills_config.json`、Schema 或生产代码。
>
> 启动基线：`main@d6db5e1b825bab60852b6365eef0ef6deb5bb970`
>
> 研究输入：`docs/skills-catalog@bd493672a35c77ff5c19fa3a3e2bb489bf60eb1b` 中的 `docs/skills/research-catalog/`
>
> 关联文档：[Skill 分类模型](./jianyu-skill-taxonomy.md)；[Skill 发现与推荐模型](./jianyu-skill-discovery-and-recommendation.md)。

## 1. 结论

精确候选数量为 **44**：

```text
当前内置候选 20
+ 研究目录条目 25
- 重复的“张雪峰视角” 1
= 官方候选 44
```

其中：

- 20 项为当前 Android 基线已接入资产，但人物声明、来源、许可、时效和风险治理元数据仍需补齐；
- 22 项有研究分支中的原创 App 草案，但尚未接入；
- 1 项“Windows 文档生产力助手”因 Android 能力、脚本、依赖、许可和隐私待评估而暂缓；
- 1 项由“规避 AIGC 检测类”合规重构为“原创表达与写作自然化助手”，完成原创重写和合规审查前暂缓；
- 研究条目“张雪峰视角”合并到现有 `zhang_xuefeng`，不新增重复候选。

“官方候选”不等于“已批准发布”。任何许可待核验项在核验完成前不得复制、分发或接入第三方正文、代码、脚本、头像或素材。

## 2. 证据与许可规则

### 2.1 当前资产

当前 20 项来自：

- `app/src/main/assets/skills/`
- `app/src/main/assets/skills_config.json`
- `workspace/tools/extract_skills_metadata.py`
- 根目录 `README.md` 的当前内置人物型 Skill 清单

当前仓库存在资产不等于已完成上游许可证核验。除张雪峰研究记录能指向 `alchaincyf/zhangxuefeng-skill` 外，其余项目未在本任务输入中形成完整许可台账，因此统一标记为“待核验”。

### 2.2 研究目录

研究分支声明：

- 只把公开项目作为能力方向和工作流参考；
- App 草案为原创改写；
- 不复制第三方 `SKILL.md` 正文、脚本、角色语料、肖像或私有数据；
- 每个项目正式接入前必须重新核验许可证、上游版本、安全和隐私风险。

已记录的许可证事实仅包括：

- `bytesagain/ai-skills`：研究记录注明 MIT 声明；
- `proficientlyjobs/proficiently-claude-skills`：研究记录注明 MIT 声明；
- `product-on-purpose/pm-skills`：研究记录注明 Apache-2.0，但它不是下表产品竞品候选的直接来源之一。

其余来源或混合来源不得据此推断整体许可兼容。表中使用“待核验”是阻断状态，不是负面结论。

## 3. 研究目录 25 项完整对账

| # | 研究 ID | 研究名称 | 官方映射 | 处理结果 |
|---:|---|---|---|---|
| 01 | `zhangxuefeng-perspective` | 张雪峰视角（现有 App 资产） | 合并到 `zhang_xuefeng` | 重复项；保留现有资产，不新增第 45 项 |
| 02 | `civil-service-coach` | 考公备考教练 | `civil-service-coach` | 候选草案 |
| 03 | `public-document-coach` | 公文与材料教练 | `public-document-coach` | 候选草案 |
| 04 | `study-planner` | 学习规划师 | `study-planner` | 候选草案 |
| 05 | `career-navigator` | 职业发展顾问 | `career-navigator` | 候选草案 |
| 06 | `resume-interview-coach` | 简历与面试教练 | `resume-interview-coach` | 候选草案 |
| 07 | `workplace-communication` | 职场沟通陪练 | `workplace-communication` | 候选草案 |
| 08 | `manager-expectation-review` | 领导预期复盘助手 | `manager-expectation-review` | 候选草案 |
| 09 | `team-handover` | 团队交接与协作助手 | `team-handover` | 候选草案 |
| 10 | `meeting-to-action` | 会议纪要与行动项助手 | `meeting-to-action` | 候选草案 |
| 11 | `report-proposal-writer` | 汇报与方案写作助手 | `report-proposal-writer` | 候选草案 |
| 12 | `contract-checklist` | 合同要点检查助手 | `contract-checklist` | 候选草案 |
| 13 | `hr-document-assistant` | 人事文档助手 | `hr-document-assistant` | 候选草案 |
| 14 | `research-fact-checker` | 调研与事实核查助手 | `research-fact-checker` | 候选草案 |
| 15 | `budget-consumption-coach` | 预算与消费决策助手 | `budget-consumption-coach` | 候选草案 |
| 16 | `habit-wellbeing-coach` | 习惯与身心管理教练 | `habit-wellbeing-coach` | 候选草案 |
| 17 | `relationship-dialogue-practice` | 关系沟通练习伙伴 | `relationship-dialogue-practice` | 候选草案 |
| 18 | `chinese-social-etiquette` | 人情世故与礼仪助手 | `chinese-social-etiquette` | 候选草案 |
| 19 | `culture-fortune-entertainment` | 传统命理文化陪伴 | `culture-fortune-entertainment` | 候选草案 |
| 20 | `content-creator` | 内容策划与文案助手 | `content-creator` | 候选草案 |
| 21 | `product-competition-analyst` | 产品与竞品分析助手 | `product-competition-analyst` | 候选草案 |
| 22 | `software-copyright-organizer` | 软件著作权材料整理助手 | `software-copyright-organizer` | 候选草案 |
| 23 | `patent-disclosure-organizer` | 专利交底材料整理助手 | `patent-disclosure-organizer` | 候选草案 |
| 24 | `office-document-productivity` | Windows 文档生产力助手 | `office-document-productivity` | 暂缓；仅保留 Android 可行子能力 |
| 25 | `academic-ai-evasion` | 规避 AIGC 检测类 | 改名为 `original-expression-naturalizer` | 原目标排除；合规重构后作为暂缓候选 |

对账保证研究目录全部 25 项均被处理：23 项保持同 ID，1 项去重合并，1 项合规改名；没有静默丢弃。

## 4. 官方候选精确清单

### 4.1 当前内置 20 项

| # | Skill ID | 名称 | 主类型 | 主价值 | 主要领域 | 来源 | 许可 | 主要风险 | 展示 | 接入状态 |
|---:|---|---|---|---|---|---|---|---|---|---|
| 01 | `zhang_xuefeng` | 张雪峰 | 人物视角 | 现实支持+思维拓展 | 教育/升学/职业 | `app/src/main/assets/skills/zhangxuefeng-skill-main/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：真人模拟、教育就业时效 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 02 | `elon_musk` | 埃隆·马斯克 | 人物视角 | 思维拓展 | 科技/工程/创业 | `app/src/main/assets/skills/elon-musk-skill-main/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 中：真人模拟、企业与时事信息 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 03 | `richard_feynman` | 理查德·费曼 | 人物视角 | 现实支持+思维拓展 | 学习/科学解释 | `app/src/main/assets/skills/feynman-skill-main/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 中：真人模拟、历史资料准确性 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 04 | `charlie_munger` | 查理·芒格 | 人物视角 | 思维拓展 | 决策/商业/投资 | `app/src/main/assets/skills/munger-skill-main/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：真人模拟、金融边界 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 05 | `naval_ravikant` | 纳瓦尔 | 人物视角 | 思维拓展 | 职业/财富/人生 | `app/src/main/assets/skills/naval-skill-main/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：真人模拟、金融与人生建议 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 06 | `steve_jobs` | 史蒂夫·乔布斯 | 人物视角 | 思维拓展 | 产品/设计/创新 | `app/src/main/assets/skills/steve-jobs-skill-main/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 中：真人模拟、历史语料 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 07 | `nassim_taleb` | 纳西姆·塔勒布 | 人物视角 | 思维拓展 | 风险/决策/金融 | `app/src/main/assets/skills/taleb-skill-main/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：真人模拟、金融风险 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 08 | `andrej_karpathy` | 安德烈·卡帕斯 | 人物视角 | 现实支持+思维拓展 | AI/工程/学习 | `app/src/main/assets/skills/karpathy-skill/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：真人模拟、快速变化技术 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 09 | `zhang_yiming` | 张一鸣 | 人物视角 | 思维拓展 | 产品/管理/组织 | `app/src/main/assets/skills/zhang-yiming-skill/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 中：真人模拟、企业信息 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 10 | `paul_graham` | 保罗·格雷厄姆 | 人物视角 | 思维拓展 | 创业/写作/产品 | `app/src/main/assets/skills/paul-graham-skill/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 中：真人模拟、创业建议 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 11 | `ilya_sutskever` | 伊利亚·苏茨克维尔 | 人物视角 | 思维拓展 | AI研究/安全 | `app/src/main/assets/skills/ilya-sutskever-skill/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：真人模拟、快速变化技术 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 12 | `donald_trump` | 唐纳德·特朗普 | 人物视角 | 思维拓展 | 谈判/传播/政治 | `app/src/main/assets/skills/trump-skill/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：真人模拟、政治与时效 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 13 | `mr_beast` | 吉米·唐纳森（MrBeast） | 人物视角 | 现实支持+思维拓展 | 内容/传播/商业 | `app/src/main/assets/skills/mrbeast-skill/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：真人模拟、平台与时效 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 14 | `justin_sun` | 孙宇晨 | 人物视角 | 思维拓展 | 营销/加密资产/传播 | `app/src/main/assets/skills/sun-yuchen-perspective/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：真人模拟、金融与声誉 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 15 | `sigmund_freud` | 西格蒙德·弗洛伊德 | 人物视角 | 思维拓展 | 心理理论/自我反思 | `app/src/main/assets/skills/freud-skill/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：心理健康、历史理论局限 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 16 | `x_mentor` | X 增长导师 | 专业顾问 | 现实支持 | 内容增长/社交媒体 | `app/src/main/assets/skills/x-mentor-skill/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 中：平台规则、聚合来源 | 顾问卡 | 现有已接入；治理元数据待补 |
| 17 | `feng_ge` | 峰哥亡命天涯 | 人物视角 | 思维拓展 | 旅行/社会观察/内容 | `app/src/main/assets/skills/fengge-skill/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：真人模拟、在世人物与时效 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 18 | `changpeng_zhao` | 赵长鹏（CZ） | 人物视角 | 思维拓展 | 加密资产/创业/风险 | `app/src/main/assets/skills/cz-skill/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：真人模拟、金融与监管 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 19 | `duan_yongping` | 段永平 | 人物视角 | 思维拓展 | 经营/投资/决策 | `app/src/main/assets/skills/duan-yongping-skill/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：真人模拟、金融边界 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |
| 20 | `tim_cook` | 蒂姆·库克 | 人物视角 | 思维拓展 | 运营/供应链/管理 | `app/src/main/assets/skills/tim-cook-skill/SKILL.md` | 待核验；发布前阻断第三方再分发结论 | 高：真人模拟、企业与时效 | 人物卡+非本人声明 | 现有已接入；治理元数据待补 |

### 4.2 研究目录新增 24 项

| # | Skill ID | 名称 | 主类型 | 主价值 | 主要领域 | 来源 | 许可 | 主要风险 | 展示 | 接入状态 |
|---:|---|---|---|---|---|---|---|---|---|---|
| 21 | `civil-service-coach` | 考公备考教练 | 专业顾问 | 现实支持 | 考试/学习/职业 | 24kchengYe/human-skill-tree；bytesagain/ai-skills；jnMetaCode/agency-agents-zh | 混合来源待逐仓核验（含 MIT 声明源） | 中：考试版本、来源与作弊边界 | 顾问卡 | 候选草案；未接入 |
| 22 | `public-document-coach` | 公文与材料教练 | 任务助手 | 现实支持 | 公文/办公/写作 | 24kchengYe/human-skill-tree；claude-office-skills/skills | 混合来源待逐仓核验 | 中：单位规范与审批 | 助手卡 | 候选草案；未接入 |
| 23 | `study-planner` | 学习规划师 | 任务助手 | 现实支持 | 学习/考试/健康 | 24kchengYe/human-skill-tree；jnMetaCode/agency-agents-zh | 混合来源待逐仓核验 | 中：效果承诺与个人差异 | 助手卡 | 候选草案；未接入 |
| 24 | `career-navigator` | 职业发展顾问 | 专业顾问 | 现实支持+思维拓展 | 职业/学习/财务 | 24kchengYe/human-skill-tree；alchaincyf/zhangxuefeng-skill | 混合来源待逐仓核验 | 高：就业与薪资时效 | 顾问卡 | 候选草案；未接入 |
| 25 | `resume-interview-coach` | 简历与面试教练 | 任务助手 | 现实支持 | 求职/写作/沟通 | claude-office-skills/skills；proficientlyjobs/proficiently-claude-skills | 混合来源待逐仓核验（含 MIT 声明源） | 高：不得伪造履历 | 助手卡 | 候选草案；未接入 |
| 26 | `workplace-communication` | 职场沟通陪练 | 专业顾问 | 现实支持 | 职场/关系/沟通 | 24kchengYe/human-skill-tree；tmstack/awesome-persona-skills | 混合来源待逐仓核验 | 中：操控、歧视与报复风险 | 顾问卡 | 候选草案；未接入 |
| 27 | `manager-expectation-review` | 领导预期复盘助手 | 任务助手 | 现实支持 | 职场/办公/关系 | vogtsw/boss-skills；tmstack/awesome-persona-skills | 混合来源待逐仓核验 | 高：隐私、冒充与动机臆测 | 助手卡 | 候选草案；未接入 |
| 28 | `team-handover` | 团队交接与协作助手 | 工作流能力 | 现实支持 | 协作/办公/写作 | titanwings/colleague-skill；tmstack/awesome-persona-skills | 混合来源待逐仓核验 | 中：组织隐私与保密 | 工作流卡 | 候选草案；未接入 |
| 29 | `meeting-to-action` | 会议纪要与行动项助手 | 工作流能力 | 现实支持 | 会议/办公/协作 | claude-office-skills/skills；bytesagain/ai-skills | 混合来源待逐仓核验（含 MIT 声明源） | 中：敏感会议与责任确认 | 工作流卡 | 候选草案；未接入 |
| 30 | `report-proposal-writer` | 汇报与方案写作助手 | 任务助手 | 现实支持 | 办公/写作/商业 | claude-office-skills/skills；KKKKhazix/khazix-skills | 混合来源待逐仓核验 | 中：事实、数据与引用 | 助手卡 | 候选草案；未接入 |
| 31 | `contract-checklist` | 合同要点检查助手 | 任务助手 | 现实支持 | 法律/办公/写作 | claude-office-skills/skills；tinh2/skills-hub-registry | 混合来源待逐仓核验 | 高：法律边界与人工复核 | 助手卡+高风险标识 | 候选草案；未接入 |
| 32 | `hr-document-assistant` | 人事文档助手 | 任务助手 | 现实支持 | 人事/办公/写作 | claude-office-skills/skills | 待核验 | 高：歧视、隐私与审批 | 助手卡+风险提示 | 候选草案；未接入 |
| 33 | `research-fact-checker` | 调研与事实核查助手 | 工作流能力 | 现实支持+思维拓展 | 研究/写作/商业 | KKKKhazix/khazix-skills；claude-office-skills/skills | 混合来源待逐仓核验 | 高：来源、联网与信息时效 | 工作流卡 | 候选草案；未接入 |
| 34 | `budget-consumption-coach` | 预算与消费决策助手 | 专业顾问 | 现实支持 | 财务/消费/生活 | 24kchengYe/human-skill-tree；bytesagain/ai-skills | 混合来源待逐仓核验（含 MIT 声明源） | 高：金融边界与收益承诺 | 顾问卡+高风险标识 | 候选草案；未接入 |
| 35 | `habit-wellbeing-coach` | 习惯与身心管理教练 | 专业顾问 | 现实支持 | 健康/习惯/生活 | 24kchengYe/human-skill-tree；bytesagain/ai-skills | 混合来源待逐仓核验（含 MIT 声明源） | 高：健康与危机转介 | 顾问卡+高风险标识 | 候选草案；未接入 |
| 36 | `relationship-dialogue-practice` | 关系沟通练习伙伴 | 任务助手 | 现实支持 | 关系/沟通/健康 | 24kchengYe/human-skill-tree；xixu-me/awesome-persona-distill-skills | 混合来源待逐仓核验 | 高：同意边界、操控与冒充 | 助手卡+风险提示 | 候选草案；未接入 |
| 37 | `chinese-social-etiquette` | 人情世故与礼仪助手 | 专业顾问 | 现实支持 | 礼仪/关系/职场 | 24kchengYe/human-skill-tree | 待核验 | 中：地区差异与违规送礼 | 顾问卡 | 候选草案；未接入 |
| 38 | `culture-fortune-entertainment` | 传统命理文化陪伴 | 专业顾问 | 思维拓展 | 传统文化/娱乐/关系 | tmstack/awesome-persona-skills | 待核验 | 高：仅供娱乐、禁止重大决策指令 | 顾问卡+娱乐声明 | 候选草案；未接入 |
| 39 | `content-creator` | 内容策划与文案助手 | 任务助手 | 现实支持 | 内容/写作/传播 | KKKKhazix/khazix-skills；bytesagain/ai-skills | 混合来源待逐仓核验（含 MIT 声明源） | 中：版权、事实与风格模仿 | 助手卡 | 候选草案；未接入 |
| 40 | `product-competition-analyst` | 产品与竞品分析助手 | 专业顾问 | 现实支持+思维拓展 | 产品/研究/商业 | Fokkyp/claude-skills；jnMetaCode/agency-agents-zh | 混合来源待逐仓核验 | 中：公开信息与商业机密 | 顾问卡 | 候选草案；未接入 |
| 41 | `software-copyright-organizer` | 软件著作权材料整理助手 | 工作流能力 | 现实支持 | 知识产权/办公/写作 | Fokkyp/SoftwareCopyright-Skill | 待核验 | 高：权属、法律边界与材料真实性 | 工作流卡+高风险标识 | 候选草案；未接入 |
| 42 | `patent-disclosure-organizer` | 专利交底材料整理助手 | 工作流能力 | 现实支持 | 专利/技术/写作 | handsomestWei/patent-disclosure-skill | 待核验 | 高：涉密、权属与法律边界 | 工作流卡+高风险标识 | 候选草案；未接入 |
| 43 | `office-document-productivity` | Windows 文档生产力助手 | 工作流能力 | 现实支持 | 文档/表格/演示/办公 | dachent/skills；anthropics/skills | 混合来源、脚本与依赖许可待核验 | 高：Android 能力、文件隐私与脚本供应链 | 工作流卡+能力限制 | 暂缓；仅保留移动端可行子能力 |
| 44 | `original-expression-naturalizer` | 原创表达与写作自然化助手 | 任务助手 | 现实支持 | 写作/原创/合规 | 研究项 academic-ai-evasion 的合规重构；概念来源 redbaronyyyyy-eng/humanizer-zh-academic | 概念来源待核验；最终正文必须原创 | 高：学术诚信、身份与虚假内容 | 助手卡+合规声明 | 暂缓；完成合规重写后再评审 |

## 5. 展示规则

### 5.1 人物卡

人物卡必须显示：

- “AI 模拟视角”；
- 非本人声明；
- 来源类别和最后核验日期；
- 时效或争议提示；
- 适合提供的视角；
- 不适合作为专业裁决的边界。

人物名称可用于发现和选择，但知名度、职位、财富或粉丝数不进入可靠性判断。

### 5.2 顾问卡

顾问卡必须显示：

- 专业范围；
- 可提供的一般信息和分析框架；
- 适用地区、版本或时效；
- 何时需要现实专业人员；
- 是否需要联网和资料。

### 5.3 助手卡

助手卡必须显示：

- 目标任务；
- 所需输入；
- 预期输出；
- 用户需要复核的事实、数据和权限；
- 不会自动提交、签署或冒充用户完成现实动作。

### 5.4 工作流卡

工作流卡必须显示：

- 主要步骤；
- 前置材料；
- 用户确认点；
- 中止和失败条件；
- 最终成果；
- 外部工具、桌面能力或依赖限制。

## 6. 重叠分析

### 6.1 高重叠组

| 能力组 | 相关候选 | 处理原则 |
|---|---|---|
| 教育与职业 | 张雪峰、职业发展顾问、考公备考教练、学习规划师、简历与面试教练 | 张雪峰提供人物视角；其他候选分别负责通用职业、考公、学习和求职交付 |
| 创业、产品与经营 | 埃隆·马斯克、乔布斯、张一鸣、保罗·格雷厄姆、蒂姆·库克、段永平、产品与竞品分析助手 | 推荐时指定创新、产品、组织、创业、运营、经营或研究职责 |
| 投资与风险 | 芒格、塔勒布、纳瓦尔、孙宇晨、赵长鹏、预算与消费决策助手 | 人物视角不得替代金融顾问；预算助手仅处理一般消费和风险教育 |
| 内容与增长 | MrBeast、峰哥、X 增长导师、内容策划与文案助手、原创表达与写作自然化助手 | 区分人物视角、平台增长、内容生产和合规编辑 |
| 办公写作 | 公文教练、汇报方案助手、会议行动助手、人事文档助手、团队交接助手、Windows 文档生产力助手 | 区分文体、会议转行动、人事、交接和桌面工具边界 |
| 研究与证据 | 费曼、卡帕斯、调研与事实核查助手、产品与竞品分析助手 | 人物负责解释或技术视角；核查助手负责来源和时效 |

推荐系统遇到同组候选时，应说明重叠点和新增价值，由用户决定保留、替换或分配不同问题。

### 6.2 不应合并的近似项

- “张雪峰”与“职业发展顾问”不合并：前者是人物视角，后者是通用顾问。
- “公文与材料教练”与“汇报与方案写作助手”不合并：文体规范和成果目标不同。
- “调研与事实核查助手”与“产品与竞品分析助手”不合并：前者是证据工作流，后者是产品领域分析。
- “内容策划与文案助手”与“原创表达与写作自然化助手”不合并：前者从选题到发布，后者只做真实内容的合规编辑。
- “会议纪要与行动项助手”与“团队交接与协作助手”不合并：一个聚焦单次会议，一个聚焦持续交接。

## 7. 缺口

当前 44 项仍有以下缺口，PR08-C 只记录，不擅自扩充候选：

- 缺少不依赖具体名人的通用“反方与关键假设检查”能力；
- 缺少独立的来源引用管理和证据冲突处理能力；
- 缺少获得专业审查的医疗、法律和金融正式能力；当前只有一般信息、整理和转介边界；
- 缺少 Android 端真实 Office 文件解析、编辑和渲染验证能力；
- 缺少对 Skill 来源、版本、许可证和更新日期的自动治理工具；
- 缺少多语言翻译、本地化和无障碍内容审校的独立候选；
- 缺少面向长期议题的专门“阶段整合者”，当前由系统整合职责承担。

是否新增候选由 PR08-F 或后续独立规划决定，不能因此把未审查能力伪装成已存在。

## 8. 暂缓与阻断项

### 8.1 全体许可待核验

当前 44 项均未在本 PR 中完成逐仓许可证审计。处理方式：

- 已接入资产保持现状，不在本 PR 重分发或改写；
- 新候选不得进入正式 App assets；
- 接入前记录仓库、精确版本、许可证文件、版权声明、素材许可和衍生要求；
- 混合来源必须分别核验，不能用其中一个 MIT 声明覆盖全部来源；
- 许可不兼容时只保留抽象能力需求并重新原创设计。

### 8.2 Windows 文档生产力助手

暂缓原因：

- 研究来源涉及 Windows Office 自动化、脚本和复杂上游；
- Android 无法直接执行 COM、VBA、宏、PowerShell 或桌面 Office 操作；
- 文件读写、格式保真、公式、宏、隐私和供应链风险未评估；
- 来源和依赖许可未核验。

可保留的移动端范围仅为内容组织、模板、步骤、检查清单和可复制成果。

### 8.3 原创表达与写作自然化助手

原研究项“规避 AIGC 检测类”的规避目标被排除。新的候选只保留合规编辑能力，且必须：

- 使用原创名称、说明和正文；
- 不复制对抗检测提示；
- 不承诺不可检测；
- 不帮助隐瞒不允许的 AI 使用；
- 不伪造经历、数据、引用、身份或原创过程；
- 在接入前完成学术诚信、招聘诚信和平台规则审查。

## 9. 接入门禁

候选从目录进入 App 前必须全部满足：

1. 来源和精确版本可追溯。
2. 许可证与素材许可已核验。
3. Skill 正文为原创或具有明确可用许可。
4. 类型、价值、标签、模式和展示状态完整。
5. 高风险边界、时效和人工复核提示完整。
6. 人物型候选有非本人声明。
7. Android 能力真实可行，不依赖未声明桌面脚本。
8. 隐私、敏感材料和联网策略明确。
9. 与现有阵容的重复和新增价值已说明。
10. 用户确认前不会自动加入或执行。
11. 自动化测试、静态检查和本地验收方案已建立。
12. 由 PR08-F 冻结最终规格，并在用户批准后由 PR09 独立实施。

## 10. 本任务未做

- 未复制研究分支的第三方来源正文；
- 未把研究草案合并到 `main`；
- 未修改 `app/src/main/assets/skills/`；
- 未修改 `skills_config.json`；
- 未修改 Room、Kotlin、Compose、资源或测试；
- 未实际核验全部第三方许可证；
- 未把暂缓候选描述为当前可用；
- 未提供用户创建、导入或公开市场能力。
