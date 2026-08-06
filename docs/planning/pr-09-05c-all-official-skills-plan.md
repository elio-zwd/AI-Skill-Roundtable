# PR09-05C：全部 44 项官方 Skill 可执行化实施计划

> **执行工作流：** 当前会话未暴露可直接调用的 Superpowers 插件，已读取仓库内 `tools/ai/superpowers/skills/brainstorming`、`writing-plans` 与 `test-driven-development`，按等价人工流程执行。用户提供的远端开发 Prompt 已经冻结设计与验收标准，因此不再重复询问设计选择。
>
> **目标：** 保留 PR09-05B 首批 4 项历史发布事实，新增单一 v2 全量执行 Manifest、40 项原创生产资产、专项静态审计与启动前上下文资格门禁，使有效生产 Catalog 达到 44/44 可执行，同时在具体上下文不安全时拒绝创建 Run。
>
> **实际 Base：** `main@8c4f3df5510ff7c3fb36088c0867c521fdb16980`，Room v11。
>
> **并行约束：** PR09-12 为 Draft PR #50，分支 `feat/pr-09-12-archive-trash`，同样基于 `8c4f3df...`。本 PR 不修改 Room、Entity、DAO、Migration、Schema、Issue 生命周期、归档/回收站/Purge、音频清理状态机、IssuesRoute 或 PR09-12 独占接线。PR09-12 合并后，本 PR 必须同步其精确 Merge SHA 并重新执行全部验证，之后才可申请合并。

## 1. 完成条件

- 固定基础目录保持 44 项、稳定 ID 和 `defaultOrder` 1..44 不变。
- `official_skill_execution_batch_v1.json` 保持精确 4 项和首批限制。
- 新增 `official_skill_execution_manifest_v2.json`，精确发布 44 项。
- 40 项新增生产资产位于 `assets/skills/official/<stable-id>/SKILL.md`。
- 19 项人物视角具备 AI 模拟身份声明、公开来源及时效边界、观点不确定性和不得冒充本人章节。
- 所有 HIGH_STAKES / URGENT 项具备高后果、地区时效、现实专业复核和紧急情况章节。
- `patent-disclosure-organizer` 对禁止外传材料实行启动前拒绝。
- `office-document-productivity` 只生成 Markdown、纯文本和结构化表格内容。
- `original-expression-naturalizer` 不规避检测、不协助作弊、不伪造事实或身份。
- 静态资格与本次上下文资格分离；上下文失败不创建 Run、Message 或预算事实。
- Resolver、单/多 Skill、Directed、Cross、推荐和历史 Participant Snapshot 契约不回归。

## 2. 当前 44 项矩阵与顺序

| Order | Skill ID | 分组 |
|---:|---|---|
| 1 | `zhang_xuefeng` | 人物视角 |
| 2 | `elon_musk` | 人物视角 |
| 3 | `richard_feynman` | 人物视角 |
| 4 | `charlie_munger` | 人物视角 |
| 5 | `naval_ravikant` | 人物视角 |
| 6 | `steve_jobs` | 人物视角 |
| 7 | `nassim_taleb` | 人物视角 |
| 8 | `andrej_karpathy` | 人物视角 |
| 9 | `zhang_yiming` | 人物视角 |
| 10 | `paul_graham` | 人物视角 |
| 11 | `ilya_sutskever` | 人物视角 |
| 12 | `donald_trump` | 人物视角 |
| 13 | `mr_beast` | 人物视角 |
| 14 | `justin_sun` | 人物视角 |
| 15 | `sigmund_freud` | 人物视角 |
| 16 | `feng_ge` | 人物视角 |
| 17 | `changpeng_zhao` | 人物视角 |
| 18 | `duan_yongping` | 人物视角 |
| 19 | `tim_cook` | 人物视角 |
| 20 | `x_mentor` | 普通/敏感非人物型 |
| 21 | `public-document-coach` | 普通/敏感非人物型 |
| 22 | `resume-interview-coach` | 普通/敏感非人物型 |
| 23 | `study-planner` | PR09-05B 已发布 |
| 24 | `workplace-communication` | 普通/敏感非人物型 |
| 25 | `manager-expectation-review` | 普通/敏感非人物型 |
| 26 | `team-handover` | 普通/敏感非人物型 |
| 27 | `relationship-dialogue-practice` | 普通/敏感非人物型 |
| 28 | `chinese-social-etiquette` | 普通/敏感非人物型 |
| 29 | `meeting-to-action` | PR09-05B 已发布 |
| 30 | `report-proposal-writer` | PR09-05B 已发布 |
| 31 | `culture-fortune-entertainment` | 普通/敏感非人物型 |
| 32 | `content-creator` | 普通/敏感非人物型 |
| 33 | `research-fact-checker` | PR09-05B 已发布 |
| 34 | `product-competition-analyst` | 普通/敏感非人物型 |
| 35 | `civil-service-coach` | 高后果非人物型 |
| 36 | `career-navigator` | 高后果非人物型 |
| 37 | `contract-checklist` | 高后果非人物型 |
| 38 | `hr-document-assistant` | 高后果非人物型 |
| 39 | `budget-consumption-coach` | 高后果非人物型 |
| 40 | `habit-wellbeing-coach` | 高后果非人物型 |
| 41 | `software-copyright-organizer` | 高后果非人物型 |
| 42 | `patent-disclosure-organizer` | 高后果/禁止外传 |
| 43 | `office-document-productivity` | 特殊重构 |
| 44 | `original-expression-naturalizer` | 特殊重构 |

## 3. Manifest 方案

### 3.1 方案比较

- **覆盖 v1：拒绝。** 会破坏首批四项历史发布事实、回滚和审计。
- **多个增量批次：不采用。** 虽可表达批次，但需要额外加载顺序、重复覆盖和缺失批次语义，增加运行时复杂度。
- **单一 v2 全量 Manifest：采用。** v1 保持历史记录；生产 Runtime 默认读取 v2；v2 精确列出 44 项，任一条目失败时整体安全失败。

### 3.2 v2 契约

文件：`app/src/main/assets/official_skill_execution_manifest_v2.json`

- `schemaVersion = 2`。
- `batchId = jianyu-official-skill-execution-manifest-v2`。
- 精确 44 项，ID 与基础目录集合完全相等。
- 每项 `expectedDefaultOrder` 与基础目录一致。
- 每项 `PUBLISHABLE`、`VERIFIED_IMPLEMENTATION_SOURCE`。
- 每项正式资产路径安全、至少两条边界和来源摘要。
- 人物、高后果、特殊 Skill 由基础元数据与资产正文进行专项审计。
- v1 解析入口和 3～5 项、禁人物、禁高后果规则保持不变。
- 默认 Runtime 读取 v2；显式传入 v1 仍按首批规则解析。

## 4. 资产策略

- PR09-05B 的四项资产继续使用原路径，不复制。
- 剩余 40 项全部新建见域原创生产资产，历史目录保留但不作为 v2 正式路径。
- 共同章节：角色与目标、适用场景、输入要求、执行步骤、输出结构、事实与来源规则、资料与个人背景边界、联网规则、风险与限制、不得执行的行为。
- 每项资产必须拥有不同的职责、输入、执行步骤、输出结构、中止条件和协作责任；测试以正文哈希和标准化内容唯一性拒绝完全重复资产。
- 资产不得包含 TODO、TBD、API Key、`.env`、隐藏系统 Prompt 字段或第三方整段复制。

## 5. 人物视角

- 19 项资产增加：AI 模拟身份声明、公开来源与时效边界、观点不确定性、不得冒充本人。
- 只模拟公开可描述的思考框架、判断偏好和问题检查角度，不复刻独特措辞或人格。
- 不以第一人称宣称本人身份，不虚构授权、私人信息、引用、当前职位或当前事件。
- 动态事实必须标注核验日期或“未完成实时核验”。
- 高后果场景不把人物视角当专业资质，必须建议现实专业复核。
- 新建 `docs/skills/official-person-skill-source-ledger-v2.md`，逐项记录来源类型、核验日期、原创性、直接引文、声明、许可和未核验事项。

## 6. 高后果和联网门禁

- HIGH_STAKES / URGENT 资产增加：高后果边界、当前地区与时效、现实专业复核条件、紧急情况处理。
- 不提供医疗诊断、正式法律意见、收益保证、行政/人事/知识产权最终判断，不生成作弊、欺诈或伪造材料。
- `REQUIRED`：未确认网络授权时拒绝启动；无真实来源时不得声称实时核验。
- `OPTIONAL`：离线只给稳定框架，动态事实明确未核验。
- `NOT_NEEDED`：不得自动扩大网络权限。
- `PROHIBITED_FOR_MATERIAL`：若存在敏感正文、商业秘密、未公开技术方案或用户标记禁止外传，拒绝外部模型调用和 Run 创建；只允许用户自行脱敏摘要或本地通用材料清单模式。

## 7. 静态资格与上下文资格

### 7.1 静态资格

扩展 `OfficialSkillExecutionEligibility`：

- 审计共同章节、路径、可读性、发布状态、来源状态和正文非空。
- 根据 `primaryType` 审计人物专项章节。
- 根据 `riskLevel` 审计高后果专项章节。
- 根据固定 ID 审计 Office、Naturalizer、Patent 和 Fortune 诚信边界。
- 错误仅暴露稳定 Skill ID、错误码和短说明。

### 7.2 上下文资格

新增 `OfficialSkillExecutionContextEligibility`：

输入包含：资料是否提供/授权、敏感确认、网络授权、材料是否允许外传、高后果确认、人物声明确认、上下文字符数、使用模式和 Stage 是否可执行。

稳定拒绝码：

- `required_material_missing`
- `material_authorization_required`
- `sensitive_material_confirmation_required`
- `network_authorization_required`
- `material_external_transfer_prohibited`
- `high_stakes_confirmation_required`
- `person_disclaimer_confirmation_required`
- `context_budget_exceeded`
- `use_mode_not_supported`
- `stage_not_executable`

Resolver 在构造 Participant Snapshot 前执行上下文审计；任一失败抛出 `InvalidExecutionSkillException`。`ExecutionRunCoordinator.start` 当前在 `createRuntime` 前调用 Resolver，因此拒绝时零 Run、零 Message、零预算。

## 8. 首页、Skill 详情与推荐

- 现有推荐策略不按风险或人物身份全局降权，继续按问题匹配、主价值、可执行性和固定顺序排序。
- `riskDisclosure` 已包含 `personDisclaimer` 和边界；增加稳定的上下文确认字段并在最终确认前传入 Resolver。
- 全部 44 项保持可发现、可搜索、可手动选择、可推荐和可加入阵容。
- 无关人物不得因知名度加分；风险只加强披露和确认，不改变全局排名。
- Naturalizer 诚信边界在 Skill 详情、推荐确认、当前阵容和运行结果附近保持可追溯。

## 9. Resolver、Coordinator、Directed 与 Cross

- 不创建第二个 Catalog、Coordinator、预算或网络 Gateway。
- 复用 `OfficialCatalogExecutionSkillResolver` 和当前 `ExecutionRunCoordinator`。
- Participant Snapshot 保存稳定 ID、资产路径、完整系统 Prompt 和 Catalog 定义快照；后续 Catalog 更新不改写历史。
- 点名只影响一次，Cross 只使用选定成员。
- `meeting-to-action` 保持默认透明整合者；人物 Skill 不自动承担综合裁决。
- 高后果成员失败不得静默回退到其他成员或通用 Prompt。

## 10. TDD 与内部批次

### 批次 0：全量发布契约

1. 先提交 v1/v2 Manifest、资产结构和专项审计失败测试。
2. 测试必须在实现前存在；当前远端会话不能执行 Gradle，因此 RED 状态记录为“测试代码已先写，尚未实际执行”。
3. 实现 v2 模型、解析和默认 Runtime 接线。

### 批次 A：11 项普通/敏感非人物型

- 逐项原创资产。
- 验证共同章节、差异性、Resolver 和推荐资格。

### 批次 B：8 项高后果非人物型

- 逐项原创资产。
- 增加高后果和上下文确认测试。
- Patent 增加禁止外传测试。

### 批次 C：19 项人物视角

- 逐项原创资产、人物声明和来源台账。
- 验证不冒充、无第一人称真实身份、无虚构授权和时效边界。

### 批次 D：2 项特殊重构

- Office 冻结为内容生成能力。
- Naturalizer 冻结为真实内容表达优化并保留完整诚信边界。

## 11. 测试矩阵

### JVM

- v1 精确四项、首批规则不变。
- v2 精确 44、集合相等、顺序一致、重复/缺失/未知/第 45 项失败。
- 44 个资产存在、可读、章节完整、正文不重复、无占位和秘密。
- 人物、高后果、特殊项专项门禁。
- 上下文资格全部稳定拒绝码和修正后通过。
- 推荐覆盖四种主类型、三种主价值，风险和人物不全局降权。

### Instrumentation

- APK assets 中 44 项逐项可读并通过 Resolver。
- 单 Skill、多 Skill、Directed、Cross、Synthesis、Fake Gateway 和历史 Snapshot。
- 未确认风险/人物/网络/敏感材料时零 Run、零 Message、零预算。

### Compose / UIAutomator

- 搜索原有四项之外的 Skill。
- 人物详情与推荐确认显示 AI 模拟声明。
- 高后果未确认阻止启动。
- Naturalizer 显示诚信边界且无规避检测文案。
- Patent 敏感材料模式零网络、零 Run。

## 12. 允许修改与所有权

主要修改：

- `app/src/main/assets/official_skill_execution_manifest_v2.json`
- `app/src/main/assets/skills/official/`
- `app/src/main/java/com/elio/jianyu/skill/catalog/`
- `app/src/main/java/com/elio/jianyu/execution/ExecutionSkillResolver.kt`
- `app/src/main/java/com/elio/jianyu/home/` 中最小确认字段接线
- 相关 JVM、AndroidTest、Compose 测试
- `docs/skills/`、`docs/planning/`、`docs/testing/`

禁止修改 PR #50 当前所有文件，尤其：

- `RoundtableDatabase.kt`
- Entity / DAO / Migration / Schema
- `JianyuAppRuntime.kt`
- `JianyuAudioRuntime.kt`
- `JianyuRepositoryTransactions.kt`
- `RoomJianyuRepository.kt`
- `app/src/main/java/com/elio/jianyu/lifecycle/`
- `ui/screens/issues/`
- `JianyuLifecycleAutomationTags.kt`

若后续运行时接线必须触及 `JianyuAppRuntime.kt`，等待 PR09-12 合并后重新读取最新文件并进行单独最小提交。

## 13. 验证与真实性

远端可执行或读取的验证分别记录：

- `git diff --check`
- Secret scan
- `compileDebugKotlin`
- `testDebugUnitTest`
- `lintDebug`
- `assembleDebug`
- `assembleRelease`
- `assembleDebugAndroidTest`
- GitHub CI Job、步骤和日志

当前没有可用 Android 本地终端，不能声称 Gradle、Instrumentation 或 UIAutomator 已通过。Room 无改动，Migration 仅记录“按条件跳过”，不写成通过。

## 14. PR09-12 合并后同步

1. 读取 PR #50 最终 Head、CI、验收和 Merge SHA。
2. 确认 Merge SHA 属于最新 `origin/main`。
3. 非强制同步最新 `main`，解决冲突时不得覆盖 PR09-12 文件。
4. 确认最终 Room 版本和生命周期接口。
5. 重新执行全部 JVM、Lint、Build、AndroidTest、Instrumentation 与 UIAutomator；同步前结果不得复用为最终结果。
6. PR 保持 Draft，等待用户明确授权后才可 Ready 或合并。

## 15. 回滚

- v1 与 v2 均保留；生产选择通过常量/构建期路径切换。
- 严重回归时只让新 Runtime 临时读取 v1，不删除 v2、资产、Catalog、历史 Participant Snapshot 或 Run。
- 不降级 Room，不使用 destructive migration，不把失败 Skill 回退到硬编码通用 Prompt。

## 16. 交接

- 新建 `docs/planning/pr-09-05c-interface-handoff.md`，冻结 v2、静态/上下文资格、人物、高后果、Patent、回滚、PR09-15 隐私终审和 PR09-17 端到端范围。
- 新建 `docs/testing/pr-09-05c-local-readonly-acceptance-prompt.md`，锁定最终 Head，要求本地 AI 只读构建、测试和设备验收，不修改、提交、推送或合并。
