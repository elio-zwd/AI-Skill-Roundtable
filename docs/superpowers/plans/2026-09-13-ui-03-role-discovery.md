# UI-03｜角色发现：搜索 / 筛选 / 收藏 / 最近 — Implementation Plan

> 状态：**APPROVED — READY FOR DISPATCH**
>
> 日期：2026-09-16（更新已批准状态）
>
> 对应规格：`docs/superpowers/specs/2026-09-13-ui-03-role-discovery-review-draft.md`
>
> 规格基线：`main@9a73d3a5364cc56b77444b322b212193aefdf20d`
>
> 本 Plan 规划角色发现的单一实现任务。用户已于 2026-09-16 确认规格 G1～G8（全部采纳推荐方案 B），门禁已解除，可从最新 main 切出实现分支执行。

## 1. Goal

在用户批准 UI-03 Review Draft 后，用一个独立 Implementation Task / branch / PR 完成角色发现闭环：

```text
角色一级页
  ├─ 搜索二级页
  │    └─ Filter ModalBottomSheet
  ├─ 收藏二级页
  │    └─ Filter ModalBottomSheet
  └─ 最近使用二级页
       └─（按批准范围）Filter ModalBottomSheet
```

并继续复用 UI-02：

- 44 项 `OfficialSkillCatalog`；
- `SkillRolePresentationCatalog`；
- `OfficialSkillPreferences` 收藏 / 最近持久化；
- 现有角色详情；
- 真实“开始新对话 / 增加到当前会话”成功链路；
- `JianyuRoleAvatar` 与真人 `AI 模拟角色` 标识。

## 2. User-review gates

生产实现开始前规格中的 G1～G8 决策门禁已全部获用户批准通过：

- [x] G1：独立二级搜索页（方案 B）；
- [x] G2：收藏 / 最近两个独立二级页（方案 B）；
- [x] G3：Filter Modal Bottom Sheet + staged apply（方案 B）；
- [x] G4：本地确定性相关性排序与真实匹配依据（方案 B）；
- [x] G5：本批不显示最近使用场景（方案 B）；
- [x] G6：新增 Preferences `clearRecentUses()` + 二次确认（方案 B）；
- [x] G7：真实 reload 才重试，否则稳定错误态（方案 B）；
- [x] G8：收藏 / 最近页复用属性筛选并隐藏恒真条件（方案 B）。

**Gate 全部门禁已通过，进入 implementation branch 创建与代码实施。**

## 3. Branch / PR policy（未来 Implementation Task）

- 从当时最新、已核实 `origin/main` 创建**一个**新 UI-03 implementation branch；
- 同一 Implementation Task 最多一个 branch + 一个 PR；
- 不复用 `codex/ui-03-spec` 作为生产实现分支；
- PR 形成完整、可审查单元后再创建；
- Rework / Local Acceptance FAIL / Review / CI 修复继续同一 branch / PR；
- 不创建 worktree，除非未来 Planner Dispatch 明确授权；
- 未经用户明确授权不 merge。

具体 branch 名由未来 Planner Dispatch 冻结，建议：`codex/ui-03-role-discovery`。

## 4. Architecture

### 4.1 延续当前 UI 分层

```text
AppNavHost / Skills graph
  → Role discovery Routes
      → immutable UiState
          → Screens
              → same-domain Components
```

职责：

- Route：收集 `OfficialSkillPreferences` Flow、管理 query/applied filter、调用本地 pure projection、处理 Preferences 写入与导航；
- Screen：只接收不可变 UiState 和 callbacks；
- Components：搜索卡、筛选 Chip、空态、Filter Sheet 等纯展示/局部交互；
- Preferences：只负责 favorite/recent 持久化；若 G6 批准，再加 clear-recent；
- 不为 UI-03 引入 Repository/Room/网络依赖。

### 4.2 推荐状态模型

实现时优先建立 UI-03 专用状态，而不是继续扩大 `OfficialSkillCatalogSection`：

```kotlin
data class SkillRoleSearchUiState(...)
data class SkillRoleFavoritesUiState(...)
data class SkillRoleRecentUiState(...)
data class SkillRoleFilterUiState(
    val applied: RoleDiscoveryFilters,
    val draft: RoleDiscoveryFilters,
    val resultCount: Int,
)
```

筛选的产品模型和底层 `OfficialSkillCatalogFilters` 分开：

- 产品模型只包含用户可见筛选；
- mapper 再映射到 Catalog filter / discovery category；
- 不把 risk/publication/useMode 等内部 enum 直接泄漏给 UI。

如果 G3 不批准 staged Sheet，则按批准后的交互重新简化，不保留两套 filter reducer。

### 4.3 搜索匹配纯逻辑

若 G4 批准推荐方案，新增可 JVM 测试的纯模型，例如：

```text
SkillRoleSearchMatch
  - role
  - relevanceTier
  - evidence: List<UserVisibleMatchEvidence>
```

规则：

1. name/alias exact-prefix；
2. name/alias contains；
3. summary/typicalScenarios；
4. 其他 searchable metadata；
5. tie → defaultOrder + id。

`evidence` 只从用户可读字段产生，最多 2 条。raw snake_case tags 可参与匹配，但没有用户可读映射时不产生展示证据。

若用户拒绝“最相关”，不新增 rank 模型，继续 `defaultOrder`，并移除“按相关性”文案。

## 5. Expected file areas

以下是未来实现的预期文件范围；真正 Dispatch 需在当时重新审计后精确授权。

### 5.1 Navigation / Route

预计修改：

- `app/src/main/java/com/elio/jianyu/ui/navigation/AppNavHost.kt`
- 当前导航 route 定义文件（若 `JianyuNavigationRoutes` 在同文件或相邻文件）
- `app/src/main/java/com/elio/jianyu/ui/App.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/skills/OfficialSkillNavigationRoute.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/skills/SkillRoleCatalogRoute.kt`

预计新增（名称可按现有目录风格调整）：

- `SkillRoleSearchRoute.kt`
- `SkillRoleFavoritesRoute.kt`
- `SkillRoleRecentRoute.kt`

要求：

- 全部留在 `SKILLS_GRAPH`；
- 二级页不显示 top-level bottom navigation；
- back stack 与现有 detail route 一致；
- 不创建另一个并行的角色一级入口。

### 5.2 Screen / Components / UiState

预计修改：

- `SkillRolePageScreen.kt`
- `SkillRoleCatalogUiState.kt`
- `SkillRoleCatalogComponents.kt`

预计新增或拆分：

- `SkillRoleDiscoveryUiState.kt`
- `SkillRoleSearchScreen.kt`
- `SkillRoleFavoritesScreen.kt`
- `SkillRoleRecentScreen.kt`
- `SkillRoleFilterSheet.kt`（若 G3 批准）

要求：

- 搜索 / 收藏 / 最近二级页共享**展示组件**可以放同域 Components；
- 不把页面 query / filter / persistence 状态塞进公共组件；
- 不跨页面域引用其他 screen 的内部组件；
- 不复制 `JianyuRoleAvatar`。

### 5.3 Query / Projection

预计修改：

- `app/src/main/java/com/elio/jianyu/skill/catalog/OfficialSkillCatalogQuery.kt`（只在批准的 rank/evidence 需要时）
- `app/src/main/java/com/elio/jianyu/ui/screens/skills/SkillRoleCatalogUiState.kt` 或新增同域 pure projection 文件

目标：

- multi discovery category；
- 用户可见 filter mapper；
- search match evidence / rank（如批准）；
- favorites/recent subset；
- recent date grouping。

不修改 Official Catalog JSON / Presentation Manifest，除非未来审计发现缺失用户可读 metadata；这类缺口应先回 Planner，不在 UI-03 实现中临时编造。

### 5.4 Preferences（仅 G6 批准时）

预计修改：

- `app/src/main/java/com/elio/jianyu/skill/catalog/OfficialSkillPreferences.kt`

新增最小契约：

```kotlin
suspend fun clearRecentUses(): Boolean
```

要求：

- InMemory 与 SharedPreferences 实现一致；
- SharedPreferences `commit()` 成功后才发布空 Flow；
- 只清 `KEY_RECENT_USES`；
- 不触碰 `KEY_FAVORITE_IDS`；
- 不触碰 Room 会话、participantIds 或消息；
- 失败保留原值并返回 false。

不新增 Room Migration。

## 6. Task breakdown（未来一个 Implementation Task 内完成）

### Task 1 — 先固定纯逻辑与测试

**目标**

在 UI 改造前先让 query/filter/recent grouping 成为可测试纯逻辑。

**测试先行**

- search normalization；
- 批准后的 relevance tier；
- 可读 match evidence 0～2 条，不暴露 raw token；
- multi discovery category；
- role type / network / material / favorite / recent 交集；
- stable tie order；
- recent `今天 / 昨天 / 更早` 日期边界；
- recent 20 条、去重、倒序现有契约无回归。

### Task 2 — 建立二级导航（按 G1/G2）

**目标**

把用户批准的 search / favorites / recent 形态接入 `SKILLS_GRAPH`。

**验收**

- 根页入口可达；
- 顶部返回 / 系统返回一致；
- 二级页无 top-level bottom navigation；
- detail 可以从二级页打开，返回后仍回对应二级页；
- root scroll/category state 不因来回导航无理由丢失。

### Task 3 — 搜索页

**目标**

实现批准后的搜索结构。

**关键行为**

- query 输入 / 清除；
- 键盘与列表滚动；
- result count；
- 筛选入口和已应用 Chip；
- “按相关性”只在 G4 批准并真实实现时显示；
- 0～2 条真实 match evidence；
- no-result 恢复动作；
- 真人轻量 `AI 模拟角色`；
- 收藏按钮独立于详情。

### Task 4 — Filter Modal Bottom Sheet（按 G3/G8）

**目标**

替换当前新角色页的 `AlertDialog` 产品形态。

**关键行为**

- staged draft；
- Reset 只改 draft；
- Cancel/Scrim/System back 丢 draft；
- Apply 提交；
- `查看 N 个角色` 与当前 query + draft 一致；
- Sheet 可滚动，底部 actions 固定；
- 只显示已批准产品维度。

若 G3 未批准 Bottom Sheet，则按批准版本实现，不保留 Dialog + Sheet 两套正式入口。

### Task 5 — 收藏页

**目标**

让收藏成为稳定、可恢复的二级列表。

**关键行为**

- favorite-only 数据源；
- 本页搜索；
- 批准的属性筛选；
- unfavorite 独立点击；
- 真正空收藏 vs 搜索/筛选无结果；
- `浏览全部角色` 回角色页；
- Preferences 写失败不提前删除 UI 真值。

### Task 6 — 最近使用页

**目标**

把真实 `recentUses` 完整展示为可再次使用的入口。

**关键行为**

- usedAt 倒序；
- 今天 / 昨天 / 更早；
- 不伪造 recent scene；
- `开始对话` 复用现有真实 bridge；
- 成功后更新 timestamp；
- 失败不写 recent；
- 若批准 filter，仅复用适用属性；
- 真人 `AI 模拟角色`。

### Task 7 — clear recent（仅 G6 批准）

**TDD**

- InMemory clear 只清 recent；
- SharedPreferences clear 成功更新 Flow；
- clear 失败不清内存值；
- favoriteIds 不变；
- UI confirm / cancel；
- clear 后 recent 页空态；
- 不删除会话/角色/participant 的断言或集成验证。

### Task 8 — Loading / Empty / Failure

**目标**

统一 05/08 候选状态，但只提供真实可执行动作。

**要求**

- loading；
- favorites empty；
- search no result；
- filtered empty；
- fatal catalog/presentation failure；
- fatal 文案不默认归因网络；
- Retry 只有存在真正 reload callback 时出现；
- fatal runtime 无 catalog/preferences 时不展示伪“查看最近”。

如果实现真实 runtime reload 会扩到 `JianyuAppRuntime` 生命周期重构，应停止扩 scope，交 Planner 决定是否拆出依赖 Task。

### Task 9 — 回归与可访问性

- 48dp 热区；
- TalkBack contentDescription；
- 窄屏；
- fontScale；
- 长名字 / 长 summary；
- 键盘；
- 系统返回；
- Sheet dismiss；
- 最近 20 条滚动；
- UI-02 detail / start new / add current 回归。

## 7. Test plan

### 7.1 JVM / unit

预计新增或扩展：

- `OfficialSkillCatalogQueryTest.kt`
- `OfficialSkillPreferencesTest.kt`（G6 批准时）
- `SkillRoleCatalogProjectionTest.kt`
- UI-03 专用 pure state/reducer/search matcher tests

必须覆盖：

- 搜索命中和顺序；
- 筛选交集；
- match evidence；
- favorites/recent 不互相污染；
- clear recent 语义（如批准）；
- 日期分组边界；
- invalid official IDs 仍被隔离。

### 7.2 Compose / AndroidTest

预计新增/扩展 `app/src/androidTest/java/com/elio/jianyu/ui/screens/skills/`：

- 根页三个入口；
- 搜索页输入、清除、无结果；
- Filter Sheet reset/cancel/apply/result count；
- 收藏取消不触发详情；
- 收藏空态；
- 最近分组与 start action；
- real-person AI 标记；
- top/system back；
- 二级页无 bottom nav；
- detail round-trip；
- stable testTag。

### 7.3 App navigation / integration

扩展当前 `AppNavHostTest` 或同级导航测试：

- Roles → Search → Detail → Back → Search → Back → Roles；
- Roles → Favorites / Recent；
- top-level navigation state 恢复；
- Deep link / existing detail route 不回归。

如 G6 批准 clear recent，再增加 Preferences Android persistence 定向测试；不因此创建数据库设备测试。

## 8. Local Acceptance plan

未来开发 Report 后，Planner 应锁 exact commit，并让 Local AI 只读执行至少：

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

随后按当时环境执行定向 instrumentation / adb UI 路径。

### 8.1 设备 / UI 路径

建议 Xiaomi 14 Ultra 1440×3200 基准 + 一款较窄 Android viewport：

1. 打开【角色】；
2. 进入搜索；
3. 输入可命中名称、能力、问题的 query；
4. 打开筛选 Sheet，验证 reset/cancel/apply；
5. 打开真人角色详情再返回；
6. 收藏一个角色 → 收藏页可见 → 取消收藏；
7. 完成真实 Start New 或 Add Current → 最近页出现该角色；
8. detail-only 浏览不新增 recent；
9. clear recent（若批准）确认只清 recent；
10. 触发无结果状态并恢复；
11. 检查系统返回、键盘、font scale、窄屏。

### 8.2 Visual Evidence

使用项目既有 UI Visual Evidence Root：

`https://drive.google.com/drive/folders/10pL1Ax3ZoJG9jPqLaDBSIyzaUvugHETC`

每轮新建 run，不覆盖历史图。至少保存：

- 搜索正常结果；
- Filter Sheet；
- 收藏正常/空；
- 最近正常/空；
- 搜索无结果；
- fatal failure（若可稳定注入）；
- 窄屏或大字体关键页。

原图不裁剪、不美化；记录设备、分辨率、density、fontScale、主题、commit SHA。

## 9. Verification commands for future implementation

按根 `AGENTS.md`：

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
.\gradlew.bat assembleDebugAndroidTest
git diff --check
git status --short
```

存在对应 instrumentation 时执行定向设备测试。未真实执行的项必须写 `NOT_RUN`，不能写 PASS。

## 10. Scope guard

未来 UI-03 Implementation Task 默认**允许**：

- skills UI Route/Screen/Components/UiState；
- skills graph navigation；
- pure catalog query/projection；
- `OfficialSkillPreferences` 的 clear-recent 小扩展（仅 G6 批准）；
- 对应 unit / AndroidTest。

默认**禁止**：

- Room Schema / Migration；
- Gemini / network protocol；
- Prompt / Skill asset；
- Official Catalog 44 项身份或 Presentation Manifest 内容；
- UI-01 导航产品结构重写；
- UI-02 role detail / conversation bridge 业务重写；
- API Key / telemetry / resources / Mine 页面；
- 依赖升级；
- 并行第二 branch / PR；
- merge。

若实施中发现必须突破上述边界，停止扩 scope，报告 Planner。

## 11. Risks and rollback

### 风险

1. **搜索“最相关”伪语义**：没有明确算法就不能写“最相关”。
2. **内部 tag 泄漏**：现有 query 会搜索 raw tags；展示层必须过滤。
3. **状态复制**：root/search/favorites/recent 若各自复制 query/filter reducer，容易漂移；应共享 pure state logic。
4. **Preferences 写失败**：不能乐观更新后吞失败。
5. **Recent 场景伪造**：当前模型无场景。
6. **假重试**：Compose 重组不等于重新加载 runtime。
7. **导航重复**：不得恢复旧 Official Catalog Tab/Dialog 作为第二套正式入口。
8. **TestTag 破坏**：已有稳定 testTag 需要迁移时必须同时改测试，不保留冲突入口。

### 回滚

实现 PR 应保持 UI-03 为一个可独立回滚单元：

- 导航二级页、Screen、filter mapper、Preferences clear（如有）同 PR；
- 不做数据 Schema Migration，因此回滚不需要数据库 downgrade；
- 若 `clearRecentUses` 已被用户调用，回滚代码不会自动恢复被用户主动清除的 recent 记录；PR 描述必须如实说明；
- 不删除收藏和会话数据。

## 12. Definition of Done（未来 Implementation Task）

只有以下同时满足，未来 UI-03 才可进入 `READY_FOR_LOCAL_ACCEPTANCE`：

- 用户 G1～G8 已批准且实现与批准项一致；
- 搜索 / 筛选 / 收藏 / 最近和状态闭环完成；
- no fake relevance / match reason / recent scene / retry；
- Preferences 行为真实且失败不伪装成功；
- UI-02 角色详情与对话 bridge 回归不破坏；
- unit / compile / lint / assemble / relevant AndroidTest 按 Dispatch 真实运行并记录；
- Visual Evidence 按 Local Acceptance 要求采集；
- Git diff 只包含未来 Dispatch 授权文件；
- 形成单一 reviewable PR，未 merge。

当前本文状态已更新为：

**APPROVED — READY FOR DISPATCH**

用户已于 2026-09-16 批准 Review Draft 与 G1～G8 决策，本 Plan 正式生效作为未来 Implementation Task 的执行蓝图。
