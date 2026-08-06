# PR09-05C：全部官方 Skill 执行接口交接

> 状态：Draft PR #51 开发交接文档。
>
> 基线：`main@8c4f3df5510ff7c3fb36088c0867c521fdb16980`，Room v11。
>
> 串行门禁：PR09-12（Draft PR #50）必须先合并；本 PR 同步其最终 Merge SHA、重新执行全部验证后，才可申请 Ready 或合并。

## 1. 发布事实

### v1 首批发布

- 文件：`app/src/main/assets/official_skill_execution_batch_v1.json`
- Schema：1
- Batch ID：`jianyu-official-skill-execution-batch-v1`
- 精确包含 4 项：
  - `study-planner`
  - `meeting-to-action`
  - `report-proposal-writer`
  - `research-fact-checker`
- v1 的 3～5 项限制、人物禁令和高后果禁令保持不变。
- v1 继续表达 PR09-05B 的历史发布事实，不被 v2 覆盖或改写。

### v2 全量发布

- 文件：`app/src/main/assets/official_skill_execution_manifest_v2.json`
- Schema：2
- Manifest ID：`jianyu-official-skill-execution-manifest-v2`
- 精确包含固定 Catalog 的 44 项，按 `defaultOrder` 1..44 排列。
- 生产 Runtime 默认读取 v2。
- 任一 ID 缺失、重复、未知、顺序不一致、资产路径非法、来源未核验、发布状态未就绪或专项边界失败时，Runtime 整体安全失败。
- 不允许静默跳过失败 Skill、只回退四项、使用占位 Prompt 或硬编码通用 Prompt。

## 2. 资产路径与历史资产

- PR09-05B 四项继续使用：`assets/skills/<stable-id>/SKILL.md`。
- PR09-05C 新增 40 项使用：`assets/skills/official/<stable-id>/SKILL.md`。
- 历史人物或研究目录保留，但不作为 v2 正式运行资产。
- 每项正式资产均包含角色、场景、输入、步骤、输出、事实来源、隐私、联网、风险和禁止行为章节。
- 人物与高后果资产还包含各自专项章节；资产正文不得完全重复。

## 3. 静态执行资格

入口：`OfficialSkillExecutionEligibility`。

静态资格回答：

> 该 Skill 是否具备一份可发布、可读取、来源核验且边界完整的正式执行资产？

审计范围：

- 固定 Catalog 身份与 V1 目标；
- 可发现、可搜索、可推荐；
- `PUBLISHABLE`；
- `VERIFIED_IMPLEMENTATION_SOURCE`；
- APK Asset 存在、可读、非空、路径安全；
- 共同章节完整；
- 人物、高后果和特殊项专项章节；
- 无 TODO、TBD、密钥、环境变量或占位内容；
- Office、Naturalizer、Patent 和 Fortune 固定诚信边界。

失败只返回稳定 Skill ID、错误码和短说明，不返回资产正文、用户正文或密钥。

## 4. 本次上下文资格

入口：`OfficialSkillExecutionContextEligibility`。

上下文资格回答：

> 一个静态可执行 Skill 在本次资料、授权、联网、风险、模式和阶段状态下能否启动？

稳定错误码：

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

`HomeStartCoordinator` 在保存议题前执行预检；`OfficialCatalogExecutionSkillResolver` 在创建 Participant Snapshot 前再次防御性复核。预检拒绝时不创建 Issue、Run、Message、Context Usage 或预算事实。

## 5. 首页执行同意

模型：`HomeExecutionConsentSnapshot`。

显式确认：

- `networkAuthorized`
- `highStakesConfirmed`
- `personDisclaimerConfirmed`
- `restrictedMaterialPresent`
- `materialMayLeaveDevice`

以下变化使旧确认整体失效：

- 问题变化；
- 价值方向变化；
- 重新推荐；
- 阵容成员变化；
- 职责变化；
- 顺序或单/多模式变化；
- 上下文选择变化。

UI 稳定标签：

- `home_execution_network_authorization`
- `home_execution_high_stakes_confirmation`
- `home_execution_person_disclaimer_confirmation`
- `home_execution_restricted_material_block`

## 6. 人物视角

- 精确 19 项。
- 每项均声明是 AI 模拟公开思考框架，不代表本人，不保证复现当前或完整观点。
- 不使用第一人称真实身份，不虚构授权、私人信息、引文、当前职位、政策或事件。
- 动态事实必须带核验日期或明确“未完成实时核验”。
- 人物视角不被用作专业资质，也不因人物知名度获得默认排序优势。
- 风险不作为全局隐藏或降权理由，而是在最终开始前加强披露与确认。
- 来源台账：`docs/skills/official-person-skill-source-ledger-v2.md`。
- 来源台账不得进入模型上下文、用户消息、日志、标签或网络请求正文。

## 7. 高后果边界

所有 `HIGH_STAKES` / `URGENT` 资产包含：

- 高后果边界；
- 当前地区与时效；
- 现实专业复核条件；
- 紧急情况处理。

统一原则：

- 不提供医疗诊断；
- 不提供正式法律意见；
- 不保证金融收益；
- 不替代人事、教育、行政或知识产权最终判断；
- 不生成作弊、欺诈或伪造材料；
- 当前法律、政策、职位、价格和考试信息必须核验；
- 紧急风险优先现实帮助。

## 8. 禁止外传材料

`patent-disclosure-organizer` 的 Catalog 网络要求保持 `PROHIBITED_FOR_MATERIAL`。

允许模式：

- 用户自行提供不含商业秘密的脱敏摘要；或
- 只返回本地通用材料清单，不读取敏感正文。

禁止模式：

- 未公开技术方案、商业秘密、原始交底书、源代码、图纸或用户标记禁止外传的材料发送到外部模型或检索服务。

当前架构若检测到受限正文会在保存和创建 Run 前拒绝；不记录伪造 Usage，不消耗执行预算。

## 9. 特殊 Skill

### `office-document-productivity`

- 只生成 Markdown、纯文本和结构化表格内容。
- 不宣称控制 Word、Excel、PowerPoint 或桌面系统。
- 不点击、保存、签署、发送或提交外部文件。
- 不保证复杂格式保真。

### `original-expression-naturalizer`

- 只优化用户自己的真实内容，不改变事实和责任归属。
- 不规避 AI 检测；
- 不协助学术作弊；
- 不伪造经历或事实；
- 不删除必要声明；
- 不冒充他人；
- 不代写必须由本人独立完成的受限内容；
- 不改写其他 Skill 的观点；
- 不自动获得其他成员完整输出。

## 10. 推荐、搜索与手动选择

- v2 生效后 44 项均 `discoverable/searchable/recommendable/executable`。
- 推荐排序继续依据问题匹配、主价值、静态可执行性和稳定 `defaultOrder`。
- 风险与人物身份不产生全局降权；人物知名度也不产生加分。
- 多 Skill 推荐保持稳定 ID 唯一、职责明确、顺序可编辑；任何变化需要重新确认。
- 参数化 JVM 契约逐项验证 44 项可手动进入合法待确认阵容。

## 11. Resolver 与历史快照

- `ExecutionSkillSelection` 可携带本次 `OfficialSkillExecutionContext`。
- 首页新运行必须携带上下文快照；已经冻结的历史 Participant 在点名、交叉讨论或透明整合中可继续复用原快照。
- Resolver 从 APK Asset 读取正式 System Prompt，并将稳定 ID、路径、配置和 Prompt 冻结到 Participant Snapshot。
- 后续 Catalog 或 Manifest 更新不得改写历史 Participant、Run 或 Message。
- Resolver 不创建第二个 Catalog、Coordinator、预算或网络 Gateway。

## 12. 单 Skill、多 Skill、点名与交叉讨论

支持：

- `STANDARD`
- `DIRECTED_RESPONSE`
- `CROSS_DISCUSSION_RESPONSE`
- 符合整合职责时的 `CROSS_DISCUSSION_SYNTHESIS`

约束：

- 点名只影响一次；
- Cross 只使用已选成员；
- 人物声明随 Participant 配置可追溯；
- 高后果失败不静默回退到其他成员；
- `meeting-to-action` 继续作为当前默认透明整合者；
- 任意人物 Skill 不自动成为裁决者。

## 13. v2 → v1 回滚

回滚入口是显式传入：

```kotlin
OfficialSkillCatalogParser.V1_EXECUTION_PUBLICATION_ASSET_PATH
```

回滚只影响新运行的执行资格：

- 新 Runtime 加载 v1 后只有历史四项可执行；
- v2 Manifest 与 40 项资产继续保留；
- 不删除固定 44 项目录；
- 不改写历史 Participant、Run、Message；
- 不降级 Room；
- 不使用 destructive migration。

APK Instrumentation 契约验证显式读取 v1 后精确恢复四项执行发布。

## 14. PR09-15 隐私终审重点

PR09-15 至少复核：

1. Skill Asset 不进入普通日志、遥测或错误全文；
2. 用户问题和资料正文不进入日志、动态标签或网络错误；
3. 人物来源台账不进入 System Prompt；
4. API Key、Token 和 Authorization Header 不进入错误；
5. 自动化标签只使用稳定 ID；
6. 禁止外传材料在任何 Gateway 前被拒绝；
7. 敏感确认只保存状态与稳定引用，不复制正文；
8. Participant Snapshot 中 System Prompt 的访问、导出和保留范围；
9. v1/v2 Manifest 失败错误不暴露资产内容；
10. 远程模型请求的最小化、脱敏、审计和删除策略。

## 15. PR09-17 端到端测试范围

PR09-17 至少覆盖：

- 44 项逐项 APK Asset 与 Resolver；
- 单 Skill 和多 Skill；
- Directed、Cross Response、透明 Synthesis；
- 人物、高后果和联网确认；
- 阵容变化导致旧确认失效；
- Patent 受限正文零网络、零 Run；
- Naturalizer 诚信边界在所有入口可见；
- Office 不出现桌面控制能力；
- v2 默认启动与 v1 显式回滚；
- Catalog 更新后历史 Snapshot 不漂移；
- Fake Gateway 覆盖，禁止生产网络与真实 API Key。

## 16. 尚未完成的串行事项

- PR09-12 尚未合并，因此本 PR 尚未取得其 Merge SHA。
- 尚未同步 PR09-12 合并后的最新 `main`。
- 同步后必须重新执行 JVM、Lint、Debug/Release、AndroidTest、Instrumentation 和 UIAutomator；同步前结果不得作为最终合并证据。
- 本 PR 保持 Draft，未经用户授权不得 Ready 或合并。
