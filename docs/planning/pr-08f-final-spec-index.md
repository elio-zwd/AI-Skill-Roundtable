# PR08-F 最终规格索引

> Base：`main@1c3a1329f088d4329f5804005703031a15b59114`
>
> 本索引用于说明 PR08-F 之后的规格权威层级。本文和链接文档描述目标产品，不代表生产代码已经实现。

## 1. 权威层级

### 第一层：PR08-F 统一规格

出现冲突时，以下文档优先于 PR08-A～E 中的“待 PR08-F 决定”“候选”“初步大纲”等表述：

1. [`见域产品需求文档`](../product/jianyu-prd.md)
2. [`PR08-F 跨规格整合决策记录`](./pr-08f-integration-decisions.md)
3. [`见域最终视觉方向统一比较计划`](../design/brand/jianyu-visual-comparison-plan.md)
4. [`PR09 生产实施总计划`](./pr-09-jianyu-implementation-plan.md)
5. [`PR09 生产实施任务清单`](./pr-09-jianyu-implementation-tasks.md)
6. [`PR09 多对话开发交接说明`](./pr-09-jianyu-handoff.md)

### 第二层：PR08-A～E 专项详细规格

以下文档继续作为详细设计依据，但其中留给 PR08-F 的未决项已经由第一层文档收敛：

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
- `docs/planning/pr-09-implementation-outline.md`

第二层文档不得用于推翻用户已经在 PR08-F 确认的决定。

### 第三层：历史输入与决策证据

- PR #20～#22 规划与第 1～62 题决策；
- PR #24～#28 描述、评论和验收记录；
- A～E 合并后 `main` 的终极只读整体验收报告。

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
- V1 可保存官方 Skill 组合，不开放自定义 Skill。

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
- 其他不依赖品牌资产的 PR09 基础工程仍需等待 PR08-F 整体批准后才能启动。

## 4. PR08-F 完成门禁

- [x] A～E 已合并并完成合并后只读验收；
- [x] 产品与交互冲突已取得用户决定；
- [x] 五项 PR #24 评论决定已吸收；
- [x] 正式 PRD 已建立；
- [x] PR09 Plan、Tasks 和 Handoff 已建立；
- [x] 最终视觉比较方法已建立；
- [ ] 完成候选 0 / A / B / C 的统一页面预览；
- [ ] 用户确认最终视觉方向；
- [ ] 将最终视觉决定同步进决策记录与品牌规格；
- [ ] PR08-F Draft PR 完成远端 CI 与本地只读验收；
- [ ] 用户明确批准 PR08-F；
- [ ] 合并 PR08-F 后才允许启动 PR09。
