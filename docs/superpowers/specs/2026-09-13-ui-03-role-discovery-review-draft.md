# UI-03｜角色发现：搜索 / 筛选 / 收藏 / 最近 — 用户审查草稿

> 状态：**APPROVED BY USER (2026-09-16)**
>
> 日期：2026-09-16（更新已批准状态）
>
> Task：`UI-03-SPEC`
>
> 基线：`main@9a73d3a5364cc56b77444b322b212193aefdf20d`
>
> 规划分支：`codex/ui-03-spec`
>
> 本文交互契约与产品决策门禁已全部获用户批准（G1～G8 均采用推荐方案 B）。

## 1. 目的与范围

UI-02 已把 44 项 `OfficialSkillCatalog` 角色、发现分类、详情和真实对话接入落到 `main`。UI-03 负责补齐角色发现链路中仍不完整的四组能力：

1. 搜索：从【角色】一级页进入更专注的角色检索；
2. 筛选：用面向用户的条件缩小搜索/收藏/最近结果；
3. 收藏：稳定查看、搜索、筛选和取消收藏；
4. 最近使用：按真实成功使用记录快速重新开始对话；
5. 状态：加载、空、无结果、失败与恢复动作保持一致。

本 Draft 的目标是把候选设计 `角色/05、08、09、10` 转成可审查、可实现、可测试的契约，同时明确哪些内容已有工程事实、哪些只是候选图建议。

### 1.1 非目标

本批不改变：

- 44 项官方角色全集、7 个发现分类 + “全部”、UI-02 固定“推荐角色”；
- `OfficialSkillCatalog` / execution Manifest 的事实源地位；
- 角色详情的真实“开始新对话 / 增加到当前会话”链路；
- 多角色平级、独立回应、真实人物型 `AI 模拟角色` 身份规则；
- Room Schema、Gemini/网络协议、模型参数、Skill 资产内容；
- UI-01 的四项一级导航；
- Official Skill 组合编辑能力；
- 任何角色排名、热度、好友在线、未读红点或“主角色”关系。

## 2. 权威输入与覆盖关系

本文按以下顺序处理冲突：

1. `docs/decisions/adr-009-skill-role-conversation-product-model.md`
2. `docs/product/jianyu-terminology.md`
3. `docs/product/jianyu-product-model.md`
4. `docs/product/jianyu-prd.md`
5. UI-02 已审查 Spec / Plan / 当前 `main` 实现与测试
6. UI 重构交接与候选说明：`角色/05、08、09、10`

候选说明是设计输入，不是已批准产品事实。尤其是“最相关”“两条匹配理由”“最近使用场景”“清除记录”“加载失败时查看最近使用”等内容，必须通过本 Draft 的审查 Gate 才能进入实现。

## 3. 当前工程事实审计

### 3.1 当前实际入口与分层

当前【角色】一级入口是：

```text
OfficialSkillNavigationRoute
  → SkillRoleCatalogRoute
      → SkillRolePageScreen
          → SkillRoleCatalogComponents / 局部组件
```

角色详情已是 `SKILLS_GRAPH` 内的二级导航，由 `navigateToSkillDetail(skillId)` 进入 `SkillRoleDetailRoute`，系统返回与顶部返回可回到角色图内父级。UI-03 应沿用这个导航模型，而不是恢复旧 `OfficialSkillCatalogRoute` 的 Tab / Dialog 心智。

### 3.2 搜索

当前 `SkillRolePageScreen` 在一级角色页内直接显示搜索框，placeholder 为：

`搜索角色、能力或问题`

`SkillRoleCatalogRoute` 持有 `query`，并将它传入 `projectSkillRoleCatalog`。底层 `OfficialSkillCatalogQuery` 当前：

- 对查询做 `trim + lowercase`；
- 搜索字段包括 `id / nameZh / aliases / summary / domainTags / scenarioTags / outputTags / typicalScenarios`；
- 仅返回 `discoverable + searchable` 的角色；
- 查询与当前 filters 取交集；
- 结果按 `defaultOrder + id` 稳定排序；
- **没有“相关性分数”或“最相关”排序算法**；
- **没有可直接展示的“为什么适合”匹配证据模型**。

因此候选图中的“排序：最相关”不能在没有新增确定性排序规则时直接照抄。

### 3.3 筛选

当前 `OfficialSkillCatalogFilters` 已能表达：

- `primaryTypes`
- `primaryValues`
- `useModes`
- `networkRequirements`
- `materialRequirements`
- `risks`
- `publicationStatuses`
- `executableOnly`
- `favoritesOnly`
- `recentOnly`

当前一级【角色】实际入口 `SkillRoleCatalogRoute` 会把 `filters` 传入 `projectSkillRoleCatalog`，筛选会影响结果集。

但当前可见筛选 UI 只是 `AlertDialog`，只暴露：

- 角色类型；
- “只看当前可执行”；
- 清除；
- 完成。

候选 UI-03 Bottom Sheet 需要的“发现分类 / 联网 / 可使用资料 / 已收藏 / 最近使用”等面向用户的筛选维度尚未以新角色页产品形态完整暴露。

另外，一级页的发现分类 Chip 是单独的页面局部状态，不等同于 `OfficialSkillCatalogFilters`。

### 3.4 收藏

`OfficialSkillPreferences` 已提供：

```text
favoriteIds: StateFlow<Set<String>>
setFavorite(skillId, favorite): Boolean
```

生产实现 `SharedPreferencesOfficialSkillPreferences`：

- 使用本地 `SharedPreferences`：`official_skill_catalog_preferences_v1`；
- 收藏保存为稳定官方 Skill ID 集合；
- 无效 ID 拒绝；
- `commit()` 成功后才更新 Flow；
- 收藏写入失败时 Route 会给用户错误消息；
- 收藏与打开详情是独立事件和独立点击热区。

当前新角色页用标题栏收藏图标在同一个根 Screen 内切换 `DISCOVER ↔ FAVORITES`；并不是独立收藏二级页面。

### 3.5 最近使用

`OfficialSkillPreferences` 已提供：

```text
recentUses: StateFlow<List<RecentOfficialSkillUse>>
recordSkillUsed(skillId, usedAt): Boolean
```

当前事实：

- `RecentOfficialSkillUse` 只承载 `skillId + usedAt`；
- 无效 ID / 非正时间戳拒绝；
- 同角色只保留最新一次；
- 按 `usedAt` 倒序、再以 `skillId` 稳定排序；
- 默认最多 20 个不同角色；
- SharedPreferences 以 JSON 持久化；
- 打开详情调用 `onSkillDetailViewed()`，明确**不会**写最近使用；
- `App.kt` 只有在“开始新对话”或“增加到当前会话”真实成功后才调用 `recordSkillUsed()`；
- 当前一级角色页只在“全部”状态展示最多 2 张最近角色卡；
- 当前没有独立“最近使用”页面；
- 当前没有 `clearRecent` / `clearRecentUses` API；
- 当前数据**没有最近使用场景、会话标题、触发动作类型**。

因此 UI-03 不得伪造“上次用于职业规划”等场景文本。

### 3.6 正常 / 空 / 错误状态

当前新角色页已有：

- `isLoading`：居中 `CircularProgressIndicator`；
- Catalog / Presentation 加载失败：`角色目录暂不可用` 状态卡；
- 收藏为空：收藏根模式内空态；
- 搜索无结果：根页内空态；
- 具体分类无结果：分类空态。

当前不足：

- Fatal Catalog / Presentation 失败态没有真实重试动作；
- 错误消息可能直接来自底层加载器，不应把内部细节长期暴露给普通用户；
- 当前 Catalog 来自本地 assets，失败不能一律写成“请检查网络”；
- fatal runtime 失败时连 `OfficialSkillPreferences` runtime 都未建立，不能假装仍可稳定展示“最近使用”。

### 3.7 当前测试保障

现有测试至少覆盖：

- `OfficialSkillCatalogQueryTest`
  - 名称 / ID / alias / domain / scenario / output 查询；
  - trim / 大小写归一；
  - type / value / risk / executable / favorite / recent 过滤。
- `OfficialSkillPreferencesTest`
  - 收藏只接受有效官方 ID；
  - 打开详情不写最近；
  - 最近去重、倒序、上限；
  - 无效历史 ID 隔离。
- `SkillRoleCatalogScreenTest`
  - `推荐角色` / 非 `为你推荐`；
  - 最近为空时区块隐藏；
  - 分类视图；
  - 收藏点击不触发详情。
- UI-02 既有详情、导航、真实 use action 与 recent-use 运行时证据继续作为回归边界。

UI-03 实现需要在这些保障上增量扩展，不删除或弱化既有断言。

## 4. Draft UX 契约

以下为**开发 AI 推荐稿**；带 Gate 的项目必须由用户确认后才能转成生产实现。

### 4.1 【角色】一级页入口关系

推荐保留 UI-02 主页面的发现结构，但把“深度发现”动作改成明确二级入口：

- 点击一级页搜索区域 → 打开【搜索 Skill 角色】二级页；
- 点击顶部收藏图标 → 打开【收藏的角色】二级页；
- 一级页“最近使用”只保留少量最近卡 + `查看全部` → 打开【最近使用】二级页；
- 角色卡仍进入现有全屏角色详情；
- 二级页均不显示一级 Bottom Navigation；
- 二级页顶部返回与 Android 系统返回必须回到原角色页，并保留原角色页滚动/分类状态。

推荐原因：搜索、收藏、最近都有独立搜索/筛选/空态需求，继续塞进一个根 Screen 的“模式切换”会扩大根页状态耦合，也不符合候选 05/09 的二级页面方向。

**Gate G1 / G2：**见第 7 节。

### 4.2 搜索二级页

#### 页面结构

从上到下：

1. 顶部返回；
2. 单行搜索输入；
3. 清除输入；
4. 筛选入口；存在已应用筛选时显示可感知选中态；
5. 已应用筛选 Chip（可逐项移除）+ `清除全部`；
6. 结果计数；
7. 单列结果卡；
8. 搜索无结果状态。

搜索框文案继续使用：

`搜索角色、能力或问题`

进入页面后推荐自动聚焦搜索框；系统键盘打开时结果列表仍应可滚动，返回键第一层按 Android 默认收键盘，之后返回角色页。

#### 查询范围

继续以本地 Official Catalog 为唯一事实源。面向用户支持：

- 角色名称；
- 别名；
- 简介/定位；
- 典型场景；
- 可公开展示的能力/输出描述；
- 用户想解决的问题中能命中上述字段的关键词。

内部 raw tag 可以继续参与检索，但**不能把 `career_workplace` 等内部 token 原样展示给用户**。

#### “最相关”排序

候选图写了“最相关”，而当前工程只有 `defaultOrder`。推荐 UI-03 新增**确定性的本地相关性层级**，不调用 LLM、不联网：

1. 名称/别名精确或前缀命中；
2. 名称/别名包含命中；
3. `summary / typicalScenarios` 命中；
4. 其他可搜索 metadata 命中；
5. 同层使用 `defaultOrder + id` 稳定打破平局。

页面可以显示 `按相关性排序`，但不展示虚假的百分比、评分或“AI 推荐分”。

如果用户不批准新增本地相关性排序，则 UI 文案必须改为不声称“最相关”，继续使用稳定目录顺序。

**Gate G4：**与“匹配依据”一并确认。

#### “为什么适合”/匹配依据

推荐不强制“每张卡两条理由”。只在本地、可展示 metadata 有证据时显示 0～2 条简短“匹配依据”，例如：

- `匹配名称：规划教练`
- `适合场景：职业选择与转岗规划`
- `相关输出：行动方案`

证据来源仅允许当前 `OfficialSkillDefinition` 的用户可读字段（优先 `nameZh / aliases / summary / typicalScenarios / outputForms`）与 UI-02 已审查的展示元数据。

禁止：

- 让模型临时生成理由；
- 为凑两条而编造“非常适合你”；
- 暴露 raw domain/scenario/output token；
- 把编辑精选、可靠性、人物名气或使用次数包装成匹配理由。

### 4.3 筛选 Bottom Sheet

推荐把当前 `AlertDialog` 改为 Material 3 `ModalBottomSheet`，页面局部 Sheet，不进入全局 NavHost。

#### 交互模型

Sheet 使用**草稿筛选状态**：

- 打开 Sheet：复制当前已应用筛选；
- 在 Sheet 内修改：只改草稿，不即时重排背景列表；
- `重置`：只清空草稿；
- `取消` / Scrim / 系统返回：丢弃草稿，保留已应用筛选；
- `查看 N 个角色`：提交草稿、关闭 Sheet、刷新结果；
- Sheet 内容可滚动，底部操作区固定；
- `N` 必须由本地当前 query + 草稿筛选实时计算，不做网络请求。

这样才能让候选图中的“取消 / 查看 N 个角色”具有真实语义；当前 AlertDialog 的即时 toggle 模型需要调整。

#### 推荐可见筛选维度

**A. 发现分类（多选）**

使用已审查的 7 个发现分类：

`思考方法 / 职业成长 / 研究学习 / 产品创造 / 沟通表达 / 办公事务 / 生活工具`

“全部”通过“不选择任何分类”表达，不额外和多选项冲突。

**B. 角色类型（多选）**

直接映射当前权威 `OfficialSkillPrimaryType`，使用用户可懂文案：

- `AI 模拟人物` → `PERSON_PERSPECTIVE`
- `专业顾问` → `PROFESSIONAL_ADVISOR`
- `任务助手` → `TASK_ASSISTANT`
- `工作流能力` → `WORKFLOW_CAPABILITY`

不引入候选图里与当前模型重叠但边界不清的“专业角色 / 思维方法角色”第二套类型体系；“思考方法”已经是发现分类。

**C. 联网需求**

推荐显示产品语义，不直接暴露 enum：

- `不限`
- `可不联网`：`NOT_NEEDED`
- `可选联网`：`OPTIONAL`
- `需要联网`：`REQUIRED`

`PROHIBITED_FOR_MATERIAL` 属于资料安全边界，不能简单包装成普通联网偏好；若需要用户侧筛选，应先单独审查文案。V1 推荐不暴露。

**D. 资料使用**

推荐只提供一个用户有意义的条件：

`可使用资料`

它匹配具有 `OPTIONAL / REQUIRED / USER_AUTHORIZED / SENSITIVE / TIME_BOUND` 任一资料能力的角色。更细的敏感/时效枚举继续用于详情边界，不放进角色发现 Sheet。

**E. 我的使用**

- `已收藏`
- `最近使用`

二者是普通筛选条件，可与其他条件取交集。

#### 不放入 UI-03 筛选 Sheet

虽然底层 Filter Model 有这些字段，本轮不直接暴露：

- risk level；
- publication status；
- use mode；
- “当前可执行”工程门禁；
- 模型厂商 / temperature / Prompt / Agent 参数。

这些不是普通用户发现角色的首要心智；执行资格仍在底层过滤和详情动作中保证。

**Gate G3 / G8：**Sheet 形态与收藏/最近页面上的筛选范围需要确认。

### 4.4 收藏的角色二级页

推荐页面：

- 标题：`收藏的角色`
- 返回；
- 本页搜索 placeholder：`搜索收藏`
- 筛选入口；
- 单列角色卡：头像、名称、简介、0～2 个可读标签/匹配依据、收藏按钮、Chevron；
- 点击整卡进入现有详情；
- 点击实心收藏按钮只取消收藏，不进入详情；
- 真人型角色继续显示 `AI 模拟角色`；
- 不显示底部一级导航。

#### 收藏搜索与筛选

搜索范围与主搜索一致，但数据先限定为 `favoriteIds`。

推荐允许使用**同一套通用角色属性筛选**（发现分类 / 角色类型 / 联网 / 可使用资料），但不再显示“已收藏”这个恒真条件，也不显示“最近使用”以免收藏页再次变成多模式聚合页。

空态分两类：

1. 真正无收藏：
   - `还没有收藏的 Skill 角色`
   - `收藏后，可以更快找到常用的思考方式。`
   - 主操作：`浏览全部角色` → 返回角色一级页。
2. 有收藏但当前搜索/筛选无结果：
   - `没有符合条件的收藏角色`
   - 操作：`清除搜索与筛选`

取消最后一个收藏后应自然进入“真正无收藏”状态，不弹成功 Toast 干扰。

### 4.5 最近使用二级页

推荐页面：

- 标题：`最近使用`
- 返回；
- 最近记录按 `usedAt` 倒序；
- 视觉分组：`今天 / 昨天 / 更早`，仅由本地时间计算；
- 每行显示头像、名称、时间、`开始对话`；
- 真人型角色继续显示 `AI 模拟角色`；
- 不显示底部一级导航。

#### 最近场景文案

当前 `RecentOfficialSkillUse` 只有 `skillId + usedAt`，因此本批默认：

- **不显示“最近使用场景”**；
- 不从历史会话猜测场景；
- 不用角色 summary 冒充“上次使用场景”；
- 可以显示角色固定 summary 作为角色简介，但视觉上必须与“最近记录”分开。

若未来确实要显示场景，应独立扩展 recent 数据模型并定义来源，不在 UI-03 里偷偷复用会话标题或生成文本。

**Gate G5。**

#### `开始对话`

推荐复用 UI-02 已有“开始新对话”成功链路。只有真实成功才再次刷新该角色的 recent timestamp；失败不写最近。

#### 清除最近记录

候选图有 `清除记录`，当前 Preferences 无 API。

开发 AI 推荐：**纳入 UI-03，但必须作为明确的 Preferences 小扩展并二次确认**：

- 顶部弱操作：`清除记录`
- 点击 → 确认 Dialog；
- 明确文案：只清除“最近使用角色”记录；
- 不删除收藏；
- 不删除会话；
- 不删除角色；
- 不改变当前会话 participant；
- 确认后调用新增 `clearRecentUses(): Boolean`；
- SharedPreferences 写成功后才更新 Flow；
- 失败保留原记录并提示失败。

不新增 Room，不碰会话历史 Schema。

若用户不批准，则整个 UI-03 不显示清除入口，也不新增 API。

**Gate G6。**

### 4.6 加载 / 空 / 无结果 / 失败

#### 正常加载

- 一级页首次加载可使用现有轻量 Loading；
- 二级搜索/收藏/最近读取的都是本地 Catalog + Preferences，正常情况下不需要网络 Loading；
- Sheet 结果计数为本地同步/纯计算，不显示网络进度。

#### 搜索无结果

保持搜索框与已应用筛选可见：

- 标题：`没有找到匹配的角色`
- 说明：`试试角色名称、能力或你想解决的问题。`
- 若有筛选：主操作 `清除筛选`
- 若只有 query：操作 `清除搜索`

不自动切回“全部”或偷偷取消条件。

#### Fatal Catalog / Presentation 加载失败

推荐文案：

- `角色暂时无法加载`
- `角色目录读取失败，请重试。收藏和最近记录不会被主动删除。`

不写“请检查网络”，因为当前 Catalog / Presentation 来自本地 assets，失败未必与网络有关。

“重试”只有在实现提供**真实重新执行 Catalog/Presentation load** 的入口时才能显示。不能放一个只重组 Compose、实际仍复用同一失败对象的假重试按钮。

当前 `OfficialSkillCatalogRuntimeResult.Failure` 不包含 Preferences runtime，因此 fatal 失败时**不推荐显示“查看最近使用”**：没有 Catalog 就无法安全映射角色身份与可执行事实。

若实现阶段发现重建 App Runtime 才能重试且范围明显超出 UI-03，则本批先显示稳定错误态 + 返回/设置等真实可用动作，把 runtime reload 独立规划。

**Gate G7。**

### 4.7 返回、键盘、窄屏与可访问性

所有 UI-03 页面必须满足：

- 顶部返回和 Android 系统返回到相同父级；
- 搜索页不显示一级 Bottom Navigation；
- Bottom Sheet 系统返回先关闭 Sheet，不退出搜索页；
- 键盘不遮住结果列表的最后可操作项；
- 交互热区至少 48dp；收藏按钮继续与整卡点击分离；
- `contentDescription` 使用动作语义，例如 `收藏 研究员` / `取消收藏 研究员`；
- 412dp 左右目标宽度保持候选视觉；较窄设备不得横向溢出；
- `fontScale > 1.15` 等放大字体下优先纵向增长，不截断主要动作；
- 长名称、长 summary、空列表、20 条最近记录均可滚动；
- Sheet 高度不足时内容滚动、底部操作固定；
- 真实人物型角色在搜索/收藏/最近卡上保留轻量 `AI 模拟角色` 标识。

## 5. 状态与数据契约草案

### 5.1 推荐的页面状态边界

不要继续让一个 `OfficialSkillCatalogSection` 同时承担“一级页模式”和“二级导航页面”。

推荐将 UI-03 二级页面状态显式拆开：

```text
RoleRoot
RoleSearch(query, appliedFilters, draftFilters?)
RoleFavorites(query, appliedFilters)
RoleRecent(appliedFilters?)
RoleFilterSheet(draftFilters, resultCount)
```

Route 持有查询、筛选、Preferences Flow 和副作用；Screen 只接收不可变 UiState + callbacks；Components 不自行找 Repository/ViewModel。

### 5.2 查询、筛选与排序都必须是纯逻辑

建议新增/提取可 JVM 测试的纯函数：

- search normalization；
- match evidence；
- deterministic relevance tier；
- visible filter mapping；
- multi-discovery-category intersection；
- favorite/recent subset；
- recent date grouping。

这些逻辑不能藏在 Composable 的临时 `remember` 中。

### 5.3 失败与持久化

- 收藏与 clear-recent（若批准）以 Preferences 写成功为提交点；
- UI 不先乐观删除后再吞失败；
- 搜索与筛选是临时 UI 状态，不写 SharedPreferences；
- 最近记录继续只由真实 use success 产生；
- fatal Catalog load 不清空 Preferences；
- 不为 UI-03 新增 Room 表或 Migration。

## 6. 候选稿与当前事实的主要差异

| 候选内容 | 当前事实 | Draft 处理 |
|---|---|---|
| 搜索是独立二级页 | 当前根页 inline search | 推荐迁为二级页，Gate G1 |
| `排序：最相关` | 当前稳定 `defaultOrder` | 推荐确定性本地相关性层级，Gate G4 |
| 每卡“两条匹配理由” | 当前无 match evidence model | 改为 0～2 条真实本地匹配依据，Gate G4 |
| Filter 为 Modal Bottom Sheet | 当前 AlertDialog | 推荐迁移，增加 draft/apply/cancel 语义，Gate G3 |
| 收藏独立二级页 | 当前根页 `FAVORITES` 模式 | 推荐独立页，Gate G2 |
| 最近独立二级页 | 当前根页最多 2 张 recent 卡 | 推荐独立页，Gate G2 |
| 最近使用场景 | 当前只有 `skillId + usedAt` | 本批不显示场景，Gate G5 |
| 清除最近 | 当前无 API | 可新增 Preferences 小扩展，Gate G6 |
| 加载失败“检查网络” | Catalog 本地 asset | 改为“目录读取失败”；不假定网络 |
| 失败后“查看最近使用” | fatal runtime 无 Preferences/Catalog | 默认不提供，Gate G7 |
| 筛选覆盖收藏/最近 | 当前旧 Filter Model 能表达，但新页面形态未定 | 推荐共享属性筛选、页面恒真条件不重复，Gate G8 |

## 7. 用户审查决策表

> 用户已于 2026-09-16 明确回复“全部同意”，G1～G8 决策门禁全部按推荐方案（方案 B）通过批准。

| Gate | 当前事实 | 候选 / 可选方向 | 开发 AI 推荐 | 推荐理由 | 用户确认结果 (2026-09-16) |
|---|---|---|---|---|---|
| G1 搜索入口 | 根角色页 inline search | A 保持 inline；B 独立二级搜索页 | **B** | 搜索拥有筛选、结果、无结果、键盘等完整状态；二级页更清晰，符合候选 05 | **已批准 B（独立二级搜索页）** |
| G2 收藏 / 最近形态 | 收藏是根模式；最近只显示最多 2 卡 | A 根模式；B 两个二级页 | **B** | 与搜索同层、状态隔离、返回语义清楚；根页继续保留少量最近入口 | **已批准 B（两个独立二级页）** |
| G3 筛选容器 | 当前 AlertDialog，toggle 即时作用 | A 继续 Dialog；B Modal Bottom Sheet + draft/apply | **B** | 候选视觉一致；取消/重置/查看 N 个角色才有真实语义 | **已批准 B（Modal Bottom Sheet + staged apply）** |
| G4 相关性 / 匹配依据 | 当前无分数，按 defaultOrder；无理由模型 | A 不声称相关；B 本地确定性 rank + 0～2 条证据 | **B** | 可解释、可测试、不调用 LLM、不制造百分比 | **已批准 B（本地确定性 rank + 0～2 条真实匹配依据）** |
| G5 最近场景 | 只有 skillId + usedAt | A 伪装 summary 为场景；B 不显示场景；C 扩数据模型 | **B** | 不伪造事实，也避免本批扩大到会话历史 Schema | **已批准 B（本批不显示场景文字）** |
| G6 清除最近 | 无 clear API | A 本批不提供；B 增加 Preferences `clearRecentUses` + 确认 | **B** | 用户控制明确，改动局部且不需 Room；但必须单独批准 | **已批准 B（增加 Preferences `clearRecentUses` + 二次确认）** |
| G7 加载失败恢复 | fatal runtime 无 retry，也无可用 Preferences runtime | A 假“检查网络”；B 真实 reload 才显示重试，否则稳定错误态 | **B** | 不做假按钮、不误报网络原因；不在 Catalog 缺失时伪装可展示最近 | **已批准 B（真实 reload 才显示重试，否则稳定错误态）** |
| G8 收藏 / 最近页筛选 | 当前没有独立页 | A 无筛选；B 共享角色属性筛选，隐藏页面恒真条件 | **B** | 用户可缩小长列表，但不会在收藏页重复“已收藏”这种无意义条件 | **已批准 B（共享角色属性筛选，隐藏页面恒真条件）** |

### 7.1 用户决策落地要求

后续 Implementation Task 的冻结方向为：

- 一级页搜索 / 收藏 / 最近改为二级导航入口；
- 搜索页采用本地可解释相关性；
- 筛选改 Modal Bottom Sheet + draft/apply/cancel；
- 收藏与最近各自独立页；
- recent 场景不伪造；
- 增加 `clearRecentUses()`；
- fatal error 只提供真实可执行的恢复动作；
- 收藏/最近页复用属性筛选但移除恒真条件。

## 8. 验收口径（供后续实现使用）

后续 UI-03 实现至少要能验证：

1. 从【角色】根页进入搜索 / 收藏 / 最近，再用顶部与系统返回恢复到角色页；
2. 二级页不显示一级 Bottom Navigation；
3. 搜索字段命中和相关性顺序符合已批准规则；
4. 匹配依据只来自可读本地 metadata，不出现 raw tag 或 LLM 临时理由；
5. Filter Sheet 的 reset / cancel / apply / count 语义正确；
6. 收藏跨页面同步、持久化，取消最后一项进入正确空态；
7. 最近只由真实成功 use 写入，详情浏览不写入；
8. recent 最多 20、去重、倒序、今天/昨天/更早分组正确；
9. 若批准 clear：确认后只清 recent，不删收藏、会话或参与角色；
10. 搜索无结果保留 query / filter 可见性并可恢复；
11. fatal load failure 不错误宣称网络原因，不展示假重试；
12. 真人型角色保留 `AI 模拟角色`；
13. 窄屏、放大字体、键盘、系统返回和 48dp 热区通过；
14. UI-02 角色详情和真实对话 bridge 无回归。

## 9. 当前状态

**APPROVED — READY FOR IMPLEMENTATION**

本文规格与门禁已全部通过用户审查，可作为 UI-03 生产实现的权威契约。
