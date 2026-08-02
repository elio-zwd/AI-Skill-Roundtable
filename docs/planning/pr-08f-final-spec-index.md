# PR08-F 最终规格索引

> Base：`main@1c3a1329f088d4329f5804005703031a15b59114`
>
> 本索引用于说明 PR08-F 之后的规格权威层级。本文和链接文档描述目标产品，不代表生产代码已经实现。

## 1. 唯一入口与权威层级

本文是 PR08-F 之后查找见域规格的**唯一权威入口**。它负责把产品行为、分域细化、视觉门禁、实施路线图和历史证据分层；被链接文档不会因为列在同一索引中而取得相同权威。

### 第一层：规范性产品规格

1. [`见域产品需求文档`](../product/jianyu-prd.md)：统一产品对象、范围、核心流程与验收边界；
2. [`第 1～62 题决策登记`](./pr-08-jianyu-product-spec-decision-index.md)：逐题保存全部已确认决定，不得因 PRD 摘要而失效；
3. [`PR08-F 跨规格整合决策记录`](./pr-08f-integration-decisions.md)：记录 PR #24 五项补充决定、PR08-F 启动七项决定、本轮修正补充决定和跨规格收敛理由。

第一层三份文档必须互相一致。发现冲突时，不能通过“优先级”静默覆盖，而应阻止 PR08-F 完成并修正文档。整合决策记录不得在没有用户确认的情况下增加或推翻产品行为。

### 第二层：PR08-A～E 分域详细规格

以下文档继续作为对应领域的详细设计依据；它们必须服从第一层产品契约，并不得保留已经由 PR08-F 关闭的待定项：

- `docs/product/jianyu-product-model.md`
- `docs/product/jianyu-terminology.md`
- `docs/design/ux/jianyu-information-architecture.md`
- `docs/design/ux/jianyu-core-flows.md`
- `docs/design/ux/jianyu-screen-state-matrix.md`
- `docs/skills/jianyu-skill-taxonomy.md`
- `docs/skills/jianyu-skill-discovery-and-recommendation.md`
- `docs/skills/jianyu-skill-catalog-mapping.md`
- `docs/design/brand/jianyu-brand-system.md`
- `docs/design/brand/jianyu-visual-design-system.md`
- `docs/design/brand/jianyu-screen-specs.md`
- `docs/architecture/jianyu-product-migration-assessment.md`

第二层文档可以细化页面状态、Skill 治理、品牌承载和迁移风险，但不得改变第 1～62 题、PR #24 五项补充决定或 PR08-F 已记录决定。

### 第三层：视觉决策门禁

- [`见域最终视觉方向统一比较计划`](../design/brand/jianyu-visual-comparison-plan.md)

该文档只定义候选 0 / A / B / C 的同条件比较和确认流程。最终视觉未确认前，任何候选都不是规范性品牌资产。

### 第四层：PR09 实施路线图

- [`PR09 生产实施总计划`](./pr-09-jianyu-implementation-plan.md)
- [`PR09 生产实施任务清单`](./pr-09-jianyu-implementation-tasks.md)
- [`PR09 多对话开发交接说明`](./pr-09-jianyu-handoff.md)

这些文档只规定实施依赖、分工、失败测试、回滚和验收门禁，不能覆盖第一、二层产品行为。每个 PR09 实施 PR 仍须先编写精确到文件、接口、测试和提交边界的可执行计划。

### 第五层：历史输入与决策证据

- PR #20～#22 规划、审阅稿与补充稿；
- PR #24～#28 描述、评论和验收记录；
- A～E 合并后 `main` 的终极只读整体验收报告。
- [`PR09 初步实施大纲`](./pr-09-implementation-outline.md)，仅保留为 PR08-E 历史输入，已由第四层路线图取代。

历史术语和候选只用于追溯，不自动成为当前正式规格。

## 2. 已由 PR08-F 关闭的事项

- 首页支持仅保存议题、不立即运行；
- 两类方向使用可独立选择的方向卡，可单选或组合；
- 未运行新阶段允许撤销且无倒计时；
- 阶段总结草稿跨进程持久保存且不自动过期；
- 普通删除进入无自动过期回收站；
- 运行中归档由用户选择等待完成或停止后归档；
- “资料与成果”使用一个一级入口和两个页内 Tab；
- `original-expression-naturalizer` 用户侧中文名为“去AI化助手”；
- 去AI化助手不得用于规避检测、伪造事实或删除诚信声明；
- 44 项 Skill 属于 V1 阶段目标，按门禁分批交付；
- 新 `testTag` 采用稳定语义命名并保留一个验收周期兼容；
- `com.elio.jianyu` 使用全新应用沙箱和初始数据库，不迁移旧包数据；
- 用户可不生成成果直接推进；
- 归档议题可恢复继续；
- 个人背景按议题显式带入；
- 显式交叉讨论默认单次生效；
- V1 可保存官方 Skill 组合、顺序和可选默认职责，不开放自定义 Skill；
- 默认职责只描述组合内成员分工，不改写官方 Skill 正文、系统边界或安全规则。

## 3. 唯一未关闭的用户产品门禁

最终品牌视觉仍待统一比较后由用户确认：

```text
候选 0：原有视觉版本
候选 A：开域窗（当前推荐深化，未批准）
候选 B：域界折页
候选 C：多视线汇聚
允许：混合方案
```

未确认前：

- 最终 Logo 未冻结；
- 最终 App Icon 未冻结；
- 最终主视觉未冻结；
- PR09-16 品牌视觉实现不得启动；
- 用户已授权：PR08-F 经另一个 AI 对修正 Head 只读复核、用户批准并合并后，可先启动不依赖最终视觉的 PR09-01～15；
- PR09-17 仍须等待 PR09-16 和最终视觉回归完成。

## 4. PR08-F 完成门禁

- [x] A～E 已合并并完成合并后只读验收；
- [x] 产品与交互冲突已取得用户决定；
- [x] 五项 PR #24 评论决定已吸收；
- [x] 正式 PRD 与第 1～62 题规范性决策登记已建立；
- [x] PR09 路线图、任务清单和交接说明已建立；
- [x] 最终视觉比较方法已建立；
- [ ] 完成候选 0 / A / B / C 的统一页面预览；
- [ ] 用户确认最终视觉方向；
- [ ] 将最终视觉决定同步进决策记录与品牌规格；
- [ ] PR08-F Draft PR 完成远端 CI 与本地只读验收；
- [ ] 由另一个 AI 对修正后的精确 Head 完成严格只读复核；
- [ ] 用户在独立复核后明确批准 PR08-F；
- [ ] 合并 PR08-F 后才允许启动 PR09。
