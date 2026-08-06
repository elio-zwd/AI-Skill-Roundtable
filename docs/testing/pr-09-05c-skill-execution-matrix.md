# PR09-05C：44 项官方 Skill 执行审计矩阵

> 本文档冻结矩阵生成方式、稳定 ID 清单、验证入口和判定规则。
>
> `Primary Type`、`Risk Level`、`Network Requirement` 必须在测试运行时直接从 `official_skill_catalog_v1.json` 读取，不在本文复制第二套元数据，避免 Catalog 更新后文档静默漂移。
>
> JVM 权威输出：`OfficialSkillExecutionAuditMatrixTest.printAndVerifyAll44SkillExecutionRows`。
>
> 只有测试命令真实执行且对应列零失败时，验收报告才可把测试契约名称改写为 `PASS`。

## 1. 矩阵字段

每一行必须输出：

```text
Skill ID
Primary Type
Risk Level
Network Requirement
Asset Exists
Static Eligibility
Context Gate
Resolver
Single Run
Multi Run
Directed
Cross
UI Disclosure
```

字段来源：

| 字段 | 唯一来源或验证入口 |
|---|---|
| Skill ID / Primary Type / Risk Level / Network Requirement | v2 生效后的 `OfficialSkillCatalog` |
| Asset Exists / Static Eligibility / Context Gate | `OfficialSkillExecutionAuditMatrixTest` |
| Resolver | `OfficialSkillExecutionManifestV2AndroidTest` |
| Single Run / Multi Run | `ExecutionRunCoordinatorTest` 及 Fake Gateway 执行用例 |
| Directed / Cross | `IssueCollaborationCoordinatorTest` 及设备端协作用例 |
| UI Disclosure | `HomeScreenTest` 与 UIAutomator 最小场景 |

## 2. 稳定 44 项清单

| Order | Skill ID | 正式资产策略 |
|---:|---|---|
| 1 | `zhang_xuefeng` | PR09-05C 原创人物资产 |
| 2 | `elon_musk` | PR09-05C 原创人物资产 |
| 3 | `richard_feynman` | PR09-05C 原创人物资产 |
| 4 | `charlie_munger` | PR09-05C 原创人物资产 |
| 5 | `naval_ravikant` | PR09-05C 原创人物资产 |
| 6 | `steve_jobs` | PR09-05C 原创人物资产 |
| 7 | `nassim_taleb` | PR09-05C 原创人物资产 |
| 8 | `andrej_karpathy` | PR09-05C 原创人物资产 |
| 9 | `zhang_yiming` | PR09-05C 原创人物资产 |
| 10 | `paul_graham` | PR09-05C 原创人物资产 |
| 11 | `ilya_sutskever` | PR09-05C 原创人物资产 |
| 12 | `donald_trump` | PR09-05C 原创人物资产 |
| 13 | `mr_beast` | PR09-05C 原创人物资产 |
| 14 | `justin_sun` | PR09-05C 原创人物资产 |
| 15 | `sigmund_freud` | PR09-05C 原创历史人物资产 |
| 16 | `feng_ge` | PR09-05C 原创人物资产 |
| 17 | `changpeng_zhao` | PR09-05C 原创人物资产 |
| 18 | `duan_yongping` | PR09-05C 原创人物资产 |
| 19 | `tim_cook` | PR09-05C 原创人物资产 |
| 20 | `x_mentor` | PR09-05C 原创非人物资产 |
| 21 | `public-document-coach` | PR09-05C 原创非人物资产 |
| 22 | `resume-interview-coach` | PR09-05C 原创非人物资产 |
| 23 | `study-planner` | PR09-05B 首批资产，v2 保留历史路径 |
| 24 | `workplace-communication` | PR09-05C 原创非人物资产 |
| 25 | `manager-expectation-review` | PR09-05C 原创非人物资产 |
| 26 | `team-handover` | PR09-05C 原创非人物资产 |
| 27 | `relationship-dialogue-practice` | PR09-05C 原创非人物资产 |
| 28 | `chinese-social-etiquette` | PR09-05C 原创非人物资产 |
| 29 | `meeting-to-action` | PR09-05B 首批资产，v2 保留历史路径 |
| 30 | `report-proposal-writer` | PR09-05B 首批资产，v2 保留历史路径 |
| 31 | `culture-fortune-entertainment` | PR09-05C 原创非人物资产 |
| 32 | `content-creator` | PR09-05C 原创非人物资产 |
| 33 | `research-fact-checker` | PR09-05B 首批资产，v2 保留历史路径 |
| 34 | `product-competition-analyst` | PR09-05C 原创非人物资产 |
| 35 | `civil-service-coach` | PR09-05C 原创高后果资产 |
| 36 | `career-navigator` | PR09-05C 原创高后果资产 |
| 37 | `contract-checklist` | PR09-05C 原创高后果资产 |
| 38 | `hr-document-assistant` | PR09-05C 原创高后果资产 |
| 39 | `budget-consumption-coach` | PR09-05C 原创高后果资产 |
| 40 | `habit-wellbeing-coach` | PR09-05C 原创高后果资产 |
| 41 | `software-copyright-organizer` | PR09-05C 原创高后果资产 |
| 42 | `patent-disclosure-organizer` | PR09-05C 原创禁止外传资产 |
| 43 | `office-document-productivity` | PR09-05C 特殊能力重构资产 |
| 44 | `original-expression-naturalizer` | PR09-05C 特殊诚信重构资产 |

## 3. JVM 矩阵生成

执行：

```powershell
.\gradlew.bat :app:testDebugUnitTest `
  --tests "com.elio.jianyu.skill.catalog.OfficialSkillExecutionAuditMatrixTest" `
  --info --stacktrace
```

从测试标准输出保存完整 TSV。必须确认：

- 恰好 44 个数据行；
- Skill ID 唯一；
- 顺序为 1..44；
- `Asset Exists = PASS_JVM`；
- `Static Eligibility = PASS_JVM`；
- `Context Gate = PASS_JVM`；
- 类型、风险和联网值直接来自 Catalog；
- 不把其余测试契约名称误写成 PASS。

## 4. APK Asset 与 Resolver

执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.skill.catalog.OfficialSkillExecutionManifestV2AndroidTest `
  --stacktrace
```

真实通过后，矩阵 `Resolver` 列可统一记为 `PASS`，并保留以下证据：

- 44 项逐项从安装 APK 读取正式资产；
- System Prompt 非空；
- Snapshot 的稳定 ID 与资产路径正确；
- 人物声明未确认时 Resolver 拒绝；
- 显式 v1 回滚后精确恢复四项可执行。

## 5. Single / Multi / Directed / Cross

这些列不得只依据“Resolver 可以读资产”判定。必须分别保留：

- Fake Gateway 调用次数；
- Participant Snapshot；
- Message 类型；
- 选定成员集合；
- 点名只影响一次；
- Cross 不引入未选成员；
- Synthesis 仍由透明整合者承担；
- 高后果失败不静默回退到其他成员；
- 历史 Snapshot 不随 Catalog 更新漂移。

只有对应测试类和设备场景真实零失败后，才可把各列记为 `PASS`。

## 6. UI Disclosure

至少验证：

- 人物详情显示 AI 模拟身份声明；
- 人物加入阵容后，最终开始前显示并要求人物声明确认；
- 高后果 Skill 显示专业复核和紧急边界，并要求风险确认；
- `REQUIRED` 网络项要求明确联网授权；
- `patent-disclosure-organizer` 对受限正文显示阻断，开始按钮不可用；
- `original-expression-naturalizer` 显示不规避检测、不作弊、不伪造、不冒充；
- `office-document-productivity` 只显示内容生成能力，不出现桌面控制承诺。

## 7. 结果状态词

验收报告只使用以下状态：

- `PASS`：对应命令实际执行、退出码 0、零失败；
- `FAIL`：对应命令或断言真实失败；
- `NOT_RUN`：尚未执行；
- `CONTRACT_DEFINED`：测试代码或验证入口已存在，但尚不能代表通过；
- `BLOCKED_BY_PR09_12`：需要 PR09-12 合并后重新执行；
- `PASS_WITH_NOTES`：自动化通过，但仍存在无法自动验证的物理交互或外部环境限制。

不得使用“应该通过”“静态看起来通过”代替真实状态。
