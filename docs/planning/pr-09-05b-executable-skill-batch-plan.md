# PR09-05B 首批可执行官方 Skill 实施计划

> **等价工作流：** Superpowers 插件接口当前不可调用；本计划按仓库内 `brainstorming`、`writing-plans`、`test-driven-development`、`verification-before-completion`、`requesting-code-review` 与 `finishing-a-development-branch` 流程执行。

**Goal：** 在不改变 44 项官方 Skill 身份、默认顺序和 Room v9 的前提下，发布首批四项非人物型原创可执行 Skill，并用可自动验证的门禁连接首页、正式 Resolver 与 `ExecutionRunCoordinator`。

**Base：** `main@bc1331f10aadbe67f336c843ec1074d67170eda2`

**Branch：** `feat/pr-09-05b-executable-skill-batch`

**Architecture：** `official_skill_catalog_v1.json` 继续承担 44 项稳定身份、分类与历史治理事实；新增 `official_skill_execution_batch_v1.json` 只记录本批正式发布覆写，由现有 `OfficialSkillCatalogParser` 在生产加载时合并成唯一有效 Catalog。合并后先经过纯 Kotlin `OfficialSkillExecutionEligibility` 确定性审计，再由 Android 资产读取器验证 APK 内 `SKILL.md`；`OfficialCatalogExecutionSkillResolver` 启动前复用同一门禁，不创建第二套 Catalog、Resolver 或状态机。

**Tech Stack：** Kotlin 2.0.21、kotlinx.serialization、Android assets、JUnit 4、Compose/Room 现有生产链、GitHub Actions。

## Global Constraints

- Catalog 总数保持 44，正式 ID 与 `defaultOrder` 不变。
- Room 保持 v9；不修改 Entity、DAO、Migration 或 Schema。
- 不激活人物视角、医疗、法律、投资等高后果专业裁决能力。
- 不修改 `ExecutionRunCoordinator`、`ExecutionStateMachine`、`ExecutionBudgetPolicy`、Repository 或网络 Gateway。
- 所有新 Skill 资产均为见域原创中文内容，不复制第三方完整 Prompt。
- 不使用真实 API Key，不调用生产网络。
- Draft PR 保持 Draft；未经授权不标记 Ready、不合并、不删除分支、不启动 PR09-08。

## 候选比较与最终范围

| 候选 | 现有风险/依赖 | 决定 | 理由 |
|---|---|---|---|
| `study-planner` | `GENERAL`、无需联网、资料可选 | 发布 | 低风险计划与行动拆解，适合单/多 Skill |
| `meeting-to-action` | `SENSITIVE`、无需联网、需授权会议材料 | 发布 | 将会议内容整理为结论与行动项，边界可明确 |
| `report-proposal-writer` | `SENSITIVE`、联网可选、需授权材料 | 发布 | 形成可编辑汇报/方案，覆盖文档与结果表达 |
| `research-fact-checker` | `SENSITIVE`、当前事实需联网、资料可选且有时效 | 发布 | 覆盖事实、来源、推断与不确定性核查 |
| `team-handover` | `SENSITIVE`、需敏感交接材料 | 暂缓 | 与会议行动和汇报交付能力重叠，本批不扩大隐私面 |
| `office-document-productivity` | `BLOCKED_REWORK`、`ORIGINAL_DESIGN_REQUIRED` | 拒绝 | 仍受 Android 实际文件能力边界阻断 |
| `product-competition-analyst` | `SENSITIVE`、强依赖实时联网与外部信息 | 暂缓 | 本批先验证更通用的核查能力，避免扩大商业信息边界 |

## 文件结构

### 新增

- `app/src/main/assets/official_skill_execution_batch_v1.json`：四项正式发布覆写，不能新增官方 ID。
- `app/src/main/assets/skills/study-planner/SKILL.md`
- `app/src/main/assets/skills/meeting-to-action/SKILL.md`
- `app/src/main/assets/skills/report-proposal-writer/SKILL.md`
- `app/src/main/assets/skills/research-fact-checker/SKILL.md`
- `app/src/main/java/com/elio/jianyu/skill/catalog/OfficialSkillExecutionEligibility.kt`：纯 Kotlin 执行资格审计。
- `app/src/main/java/com/elio/jianyu/skill/catalog/AndroidOfficialSkillAssetReader.kt`：APK assets 读取适配器。
- `app/src/test/java/com/elio/jianyu/skill/catalog/OfficialSkillExecutionEligibilityTest.kt`
- `app/src/test/java/com/elio/jianyu/skill/catalog/OfficialSkillExecutableBatchTest.kt`
- `app/src/test/java/com/elio/jianyu/home/HomeExecutableSkillIntegrationTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/execution/OfficialCatalogExecutionSkillResolverIntegrationTest.kt`
- `docs/testing/pr-09-05b-local-readonly-acceptance-prompt.md`

### 修改

- `app/src/main/java/com/elio/jianyu/skill/catalog/OfficialSkillCatalogModels.kt`：执行发布 Manifest 模型。
- `app/src/main/java/com/elio/jianyu/skill/catalog/OfficialSkillCatalogParser.kt`：解析、合并与元数据门禁。
- `app/src/main/java/com/elio/jianyu/skill/catalog/OfficialSkillCatalogRuntime.kt`：生产加载时验证真实资产并安全失败。
- `app/src/main/java/com/elio/jianyu/execution/ExecutionSkillResolver.kt`：启动前复用执行资格审计。
- `app/src/test/java/com/elio/jianyu/skill/catalog/OfficialSkillCatalogManifestTest.kt`：有效 Catalog 数量和发布状态契约。

## Task 1：执行资格契约 RED

- [ ] 新增纯 Kotlin 测试，覆盖未知 ID、缺失资产、非法路径、读取失败、空正文、发布未就绪、来源未验证、缺失边界、缺失输入/输出/隐私/联网规则、残留 `nonExecutableReason`。
- [ ] 断言错误码顺序稳定，错误详情不包含完整 Skill 正文。
- [ ] 通过 GitHub Actions 记录测试在生产实现前失败的 RED 证据。

## Task 2：生产门禁 GREEN

- [ ] 实现 `OfficialSkillExecutionEligibilityCode`、审计结果和资产读取接口。
- [ ] 固定合法路径为相对 `skills/<directory>/SKILL.md`，拒绝绝对路径、反斜杠、盘符、空段和 `..`。
- [ ] 固定正文必需章节：角色与目标、适用场景、输入要求、执行步骤、输出结构、事实与来源规则、资料与个人背景边界、联网规则、风险与限制、不得执行的行为。
- [ ] 审计结果仅包含 Skill ID、稳定错误码和短说明，不返回正文。

## Task 3：首批原创资产

- [ ] 为四项 Skill 编写原创 `SKILL.md`。
- [ ] 每项明确合法输入、输出、事实和来源、不确定性、资料授权、联网、敏感信息、高后果决策和禁止行为。
- [ ] 不声明不存在的实时搜索能力；`research-fact-checker` 在未联网时必须明确“未核验”。
- [ ] 不自动创建日历、发送消息、签署、审批或调用外部软件。

## Task 4：执行发布 Manifest 与合并门禁

- [ ] 新增只含四个既有 ID 的执行发布 Manifest。
- [ ] 合并时将四项设置为 `hasAsset/recommendable/executable=true`、`PUBLISHABLE`、`VERIFIED_IMPLEMENTATION_SOURCE`、清空 `nonExecutableReason` 并写入真实 `assetPath`。
- [ ] 拒绝未知/重复 ID、非 V1、人物型、高后果、非可发布、来源未验证、非法路径或缺失边界覆写。
- [ ] 验证合并后仍为 44 项，顺序完全不变，恰有 4 项可执行。

## Task 5：生产 Runtime 与 Resolver

- [ ] `createOfficialSkillCatalogRuntime()` 同时读取稳定目录和执行发布 Manifest。
- [ ] 对所有有效 Catalog 中 `executable=true` 的条目执行真实 Android assets 审计；任一失败则整个 Runtime 安全失败。
- [ ] Resolver 解析每个选择前复用审计，继续拒绝重复、未知、不可执行、缺失路径和空 Prompt。
- [ ] 保持 Participant Snapshot 的 ID、position、responsibility、System Prompt 与 configurationJson 冻结语义。

## Task 6：首页与真实解析集成

- [ ] JVM 测试用有效生产 Catalog 验证首页存在单 Skill 与多 Skill 可执行推荐。
- [ ] 验证不可执行候选不能进入最终启动，生产路径不依赖 Fake Catalog 或硬编码首批 ID。
- [ ] AndroidTest 使用真实 assets 与正式 Resolver 验证一项和至少两项 Skill、顺序、职责、非空 Prompt、重复/未知/不可执行拒绝。

## Task 7：Coordinator 集成与回归

- [ ] 复用现有 `ExecutionRunCoordinatorTest` 已覆盖的 Fake Gateway 单 Skill、多 Skill、无 Key、离线、部分/全员失败、停止、迟到回调和预算门禁。
- [ ] 新增 Resolver→Participant Snapshot→Coordinator 的最小真实资产集成场景；若 JVM 无 Android assets 环境，则放入 Instrumentation，网络继续使用 Fake Gateway。
- [ ] 不修改状态机、预算、Repository 或生产 Gateway。

## Task 8：验证与交接

- [ ] GitHub CI：Secret scan、应用身份、`compileDebugKotlin`、全量 JVM、Lint、Debug/Release、R8、AndroidTest APK、Room Schema。
- [ ] 统计有效 Catalog 总数、可执行数、新增资产数、JUnit 数量及失败/错误/跳过。
- [ ] 创建严格只读本地验收 Prompt，使用 `Invoke-LocalVerification.ps1` 与 `tools/device/cli.py`，禁止卸载、清数据、真实 Key 和生产网络。
- [ ] 检查 PR 差异、敏感信息、Room v9、未修改禁止文件、工作分支状态。
- [ ] 创建 Draft PR 并保持 Draft，等待本地 AI 验收与用户授权。

## 失败与回滚

- 任何一项资产或发布元数据审计失败，生产 Catalog Runtime 整体安全失败，不降级为任意 ID 或旧硬编码列表。
- 回滚只需移除执行发布 Manifest 中对应覆写或将其从本批清单撤回；稳定 44 项目录、历史 Run、Participant Snapshot、Message 与 Room v9 不变。
- 不删除执行资格测试；回滚后测试应明确证明该项重新不可执行。
