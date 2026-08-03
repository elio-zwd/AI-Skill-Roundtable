# PR09-05 官方 Skill 目录实施计划

> **执行方式：** Superpowers 插件接口未调用；本任务读取仓库内固定保存的 Superpowers 6.2.0 Skill 文件，并按照 `project-workflow.md` 执行 brainstorming、writing-plans、test-driven-development、verification-before-completion、requesting-code-review 与 finishing-a-development-branch 的等价人工流程。

**目标：** 在不修改根导航、Room v7、Repository 冻结区和现有 Skill 正文的前提下，建立 44 项见域官方 Skill Catalog、正式官方 ID 校验、搜索筛选详情、收藏、最近使用接口和官方组合页面入口。

**实际 Base：** `main@78abf30b60d863ce0ac29323546e61971d50c9c9`

**开发分支：** `feat/pr-09-05-official-skill-catalog`

**PR09-03 输入：** 使用 `JianyuRepository` 的四个官方组合接口及 `OfficialSkillIdValidator`；不直接访问 DAO，不修改 `RoomJianyuRepository.kt`。

**PR09-04 sibling：** 开始开发时 GitHub 没有开放 PR，PR09-04 尚未出现；本 PR 仅暴露 `OfficialSkillCatalogRoute`，不修改 `App.kt`、根 Route、NavHost 或底部导航。

## 一、完成条件

1. Manifest 精确包含 44 个唯一官方 ID，研究目录 25 项全部有去向，现有 20 项全部映射到当前资产。
2. Catalog 区分 V1 目标、资产、发现、搜索、推荐、执行、发布、来源许可、风险、联网、资料与使用模式，禁止用单一 Boolean 混同。
3. 人物 Skill 风险不影响默认排序、搜索和推荐资格；风险只进入披露与边界元数据。
4. `office-document-productivity` 与 `original-expression-naturalizer` 固定存在，特殊边界可被列表、详情、组合成员和后续执行前消费。
5. `CatalogOfficialSkillIdValidator` 只验证官方身份；44 项均合法，未知或空 ID 拒绝，不联网、不访问数据库。
6. 收藏只持久化官方 ID；打开详情不写最近使用，只有显式 `recordSkillUsed` 才记录。
7. 官方组合只调用 `JianyuRepository`，支持成员顺序、可选默认职责、编辑、软删除和 `expectedUpdatedAt`。
8. 暴露稳定公共入口 `OfficialSkillCatalogRoute`，页面遵守 Route → Screen → Components。
9. Room 保持 v7，无 `8.json`，不调用 Gemini，不创建 ExecutionRun，不实现推荐算法。

## 二、44 项对账

### 现有 20 项资产

`zhang_xuefeng`、`elon_musk`、`richard_feynman`、`charlie_munger`、`naval_ravikant`、`steve_jobs`、`nassim_taleb`、`andrej_karpathy`、`zhang_yiming`、`paul_graham`、`ilya_sutskever`、`donald_trump`、`mr_beast`、`justin_sun`、`sigmund_freud`、`x_mentor`、`feng_ge`、`changpeng_zhao`、`duan_yongping`、`tim_cook`。

上述 20 项全部 `hasAsset=true`、`discoverable=true`、`searchable=true`、`recommendable=true`；当前执行状态由真实资产和门禁独立决定，风险不得改变排序。

### 研究目录新增 24 项

`civil-service-coach`、`public-document-coach`、`study-planner`、`career-navigator`、`resume-interview-coach`、`workplace-communication`、`manager-expectation-review`、`team-handover`、`meeting-to-action`、`report-proposal-writer`、`contract-checklist`、`hr-document-assistant`、`research-fact-checker`、`budget-consumption-coach`、`habit-wellbeing-coach`、`relationship-dialogue-practice`、`chinese-social-etiquette`、`culture-fortune-entertainment`、`content-creator`、`product-competition-analyst`、`software-copyright-organizer`、`patent-disclosure-organizer`、`office-document-productivity`、`original-expression-naturalizer`。

研究项 `zhangxuefeng-perspective` 合并到 `zhang_xuefeng`，不得生成第 45 项；`academic-ai-evasion` 只作为被排除的研究方向，正式 ID 为 `original-expression-naturalizer`。

## 三、Catalog 数据源方案比较

### 方案 A：Kotlin 静态对象

类型安全且无运行时解析失败，但 44 项治理元数据会形成大型源码文件，不利于内容审计、来源对账和非代码 Review。

### 方案 B：版本化 JSON Manifest

目录事实源清晰，适合逐项审计、契约测试和资产映射；需要严格解析、Schema 语义校验及错误状态。

### 方案 C：Character 资产 + Overlay

可复用现有 20 项，但 Character 只覆盖历史人物执行资产，新 24 项没有对应资产，容易形成两个目录事实源。

### 最终方案

采用 **方案 B 为唯一目录事实源 + 现有 Character/Skill 资产仅作为执行内容来源**：

- `official_skill_catalog_v1.json` 只保存目录元数据、治理状态、标签、边界与 `assetPath`；
- 不保存或复制 System Prompt；
- `skills_config.json` 和 `assets/skills/` 继续作为现有 20 项实际执行资产来源；
- 契约测试逐项校验 20 项 `assetPath` 映射、44 项完整性和门禁状态；
- JSON 解析错误返回显式 Catalog 错误，离线仍可浏览有效本地 Manifest。

## 四、领域模型

新增以下稳定枚举与数据类：

- `OfficialSkillPrimaryType`：`PERSON_PERSPECTIVE`、`PROFESSIONAL_ADVISOR`、`TASK_ASSISTANT`、`WORKFLOW_CAPABILITY`。
- `OfficialSkillPrimaryValue`：`REALITY_SUPPORT`、`THINKING_EXPANSION`、`BOTH`。
- `OfficialSkillUseMode`：`SINGLE_ONLY`、`SINGLE_PREFERRED`、`MULTI_PREFERRED`、`BOTH`。
- `OfficialSkillNetworkRequirement`：`NOT_NEEDED`、`OPTIONAL`、`REQUIRED`、`PROHIBITED_FOR_MATERIAL`。
- `OfficialSkillMaterialRequirement`：`NONE`、`OPTIONAL`、`REQUIRED`、`USER_AUTHORIZED`、`SENSITIVE`、`TIME_BOUND`。
- `OfficialSkillRiskLevel`：`GENERAL`、`SENSITIVE`、`HIGH_STAKES`、`URGENT`。
- `OfficialSkillPublicationStatus`：`BLOCKED_REWORK`、`ORIGINALITY_OR_LICENSE_REVIEW`、`NOTICE_AND_DISCLOSURE_REQUIRED`、`PUBLISHABLE`。
- `OfficialSkillSourceStatus`：`EXISTING_ASSET_REVIEW_REQUIRED`、`ORIGINAL_DESIGN_REQUIRED`、`IMPLEMENTATION_SOURCE_PENDING`、`VERIFIED_IMPLEMENTATION_SOURCE`。
- `OfficialSkillAvailability`：分别记录 `v1Target`、`hasAsset`、`discoverable`、`searchable`、`recommendable`、`executable`。
- `OfficialSkillDefinition`：ID、名称、别名、简介、类型、价值、领域/场景/输入/输出标签、使用模式、联网、资料、风险、发布、来源、边界、不可执行原因、人物声明、特殊诚信边界、资产路径、稳定默认顺序。

## 五、文件结构

### 新增生产文件

- `app/src/main/assets/official_skill_catalog_v1.json`：44 项唯一目录事实源。
- `app/src/main/java/com/elio/jianyu/skill/catalog/OfficialSkillCatalogModels.kt`：可序列化模型、枚举和校验结果。
- `app/src/main/java/com/elio/jianyu/skill/catalog/OfficialSkillCatalog.kt`：Catalog 接口、内存实现、查找与稳定排序。
- `app/src/main/java/com/elio/jianyu/skill/catalog/OfficialSkillCatalogParser.kt`：JSON 解析、结构与语义校验、Android asset 加载。
- `app/src/main/java/com/elio/jianyu/skill/catalog/OfficialSkillCatalogQuery.kt`：搜索、筛选、收藏/最近使用过滤和稳定排序纯函数。
- `app/src/main/java/com/elio/jianyu/skill/catalog/CatalogOfficialSkillIdValidator.kt`：正式本地 `OfficialSkillIdValidator`。
- `app/src/main/java/com/elio/jianyu/skill/catalog/OfficialSkillPreferences.kt`：收藏、最近使用接口及 SharedPreferences 实现。
- `app/src/main/java/com/elio/jianyu/ui/screens/skills/OfficialSkillCatalogUiState.kt`：不可变状态、筛选器、事件和纯 reducer。
- `app/src/main/java/com/elio/jianyu/ui/screens/skills/OfficialSkillCatalogRoute.kt`：加载 Catalog/偏好/组合，调用 Repository，处理一次性事件。
- `app/src/main/java/com/elio/jianyu/ui/screens/skills/OfficialSkillCatalogScreen.kt`：列表、筛选、详情和组合容器。
- `app/src/main/java/com/elio/jianyu/ui/screens/skills/OfficialSkillCatalogComponents.kt`：展示组件和 Dialog。
- `docs/planning/pr-09-05-navigation-handoff.md`：PR09-04 接线和 Validator 注入说明。
- `docs/testing/pr-09-05-local-readonly-acceptance-prompt.md`：本地 AI 严格只读验收 Prompt。

### 新增测试文件

- `app/src/test/java/com/elio/jianyu/skill/catalog/OfficialSkillCatalogManifestTest.kt`
- `app/src/test/java/com/elio/jianyu/skill/catalog/OfficialSkillCatalogQueryTest.kt`
- `app/src/test/java/com/elio/jianyu/skill/catalog/CatalogOfficialSkillIdValidatorTest.kt`
- `app/src/test/java/com/elio/jianyu/skill/catalog/OfficialSkillPreferencesTest.kt`
- `app/src/test/java/com/elio/jianyu/ui/OfficialSkillCatalogArchitectureTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/ui/OfficialSkillCatalogScreenTest.kt`

### 禁止修改文件

`App.kt`、根导航与 Route 定义、底部导航，以及 PR09-03 交接列出的全部 Repository/DAO/Database/Schema 冻结文件。

## 六、搜索、筛选和排序

`OfficialSkillCatalogQuery` 统一处理：

- 搜索字段：中文名、ID、别名、简介、领域、场景、输出标签；使用 `trim()` 和小写归一化。
- 筛选：主类型、主价值、使用模式、联网、资料、风险、发布、可执行、收藏、最近使用。
- 默认排序：Manifest `defaultOrder` 升序，再按 ID；任何风险字段不进入排序权重。
- 状态文案：可发现、可推荐、可执行、待门禁、阻断重构、许可或原创性待核验分别由独立字段映射。

## 七、收藏和最近使用

`OfficialSkillPreferences` 只读写以下最小数据：

- 收藏：`Set<String>` 官方 ID；加载时隔离未知 ID。
- 最近使用：`skillId + usedAt`；显式 `recordSkillUsed(skillId, usedAt)` 才写入。
- 打开详情、搜索、筛选、收藏不写最近使用。
- 不保存 Prompt、详情正文、用户输入或敏感材料。

SharedPreferences 名称固定为 `official_skill_catalog_preferences_v1`，目录升级通过版本化名称隔离；非法或删除 ID 不映射到其他 Skill。

## 八、官方组合

Route 仅通过 `JianyuRepository`：

- 首次加载调用 `listOfficialSkillCombinations()`；
- 创建/编辑构造 `OfficialSkillCombinationEntity` 和按位置排序的 `OfficialSkillCombinationMemberEntity`；
- 保存传递 `expectedUpdatedAt`，冲突映射为明确 UI 错误；
- 删除调用 `deleteOfficialSkillCombination`，保持软删除；
- UI 禁止重复 ID、重复位置和空名称；
- 默认职责只作为成员元数据，不写入 Manifest、Prompt 或系统边界。

## 九、公共 Route 与 PR09-04 接线

稳定入口：

```kotlin
@Composable
fun OfficialSkillCatalogRoute(
    repository: JianyuRepository,
    catalog: OfficialSkillCatalog,
    preferences: OfficialSkillPreferences,
    onUseSkill: (OfficialSkillUseRequest) -> Unit,
)
```

另提供 Android 组合辅助函数用于从 `Context` 加载本地 Manifest、创建 SharedPreferences store 和 `CatalogOfficialSkillIdValidator`。PR09-04 负责在 App 组合层构造同一 Catalog 实例，并把 Validator 注入 `RoomJianyuRepository`；本 PR 不修改装配入口。

`onUseSkill` 只上抛稳定 ID 和用户意图，不创建 Run、不调用模型；调用方真正进入使用流程后再调用 `recordSkillUsed`。

## 十、状态矩阵

UiState 明确表示：初次加载、正常、空搜索、无收藏、无最近使用、Catalog 整体错误、单项元数据错误、组合加载失败、组合保存冲突、未知 ID、不可执行、许可/原创性待核验、离线可浏览、详情/组合 Dialog 状态。

Compose 布局使用 Material 3 自适应宽度、可滚动内容、语义标签和最小触控目标；窄屏、大字体、明暗主题和 Activity 重建由状态提升及测试覆盖。

## 十一、TDD 顺序

### Task 1：Catalog 完整性与解析

1. 先创建 `OfficialSkillCatalogManifestTest`，断言 44、唯一 ID、20 项映射、张雪峰去重、两个特殊 ID、状态分离和未过门禁不可执行。
2. 运行 `pwsh.exe -NoProfile -Command ".\\gradlew.bat :app:testDebugUnitTest --tests '*OfficialSkillCatalogManifestTest'"`，预期在生产类型/Manifest 不存在时失败。
3. 创建模型、Manifest 和解析器，使测试通过。
4. 提交：`test: 增加官方Skill目录契约测试` 与 `feat: 建立44项官方Skill Catalog`。

### Task 2：查询、筛选与 Validator

1. 先添加中文名、ID、领域、场景、大小写、空白、无结果、稳定排序、四类/主价值/风险不降权测试。
2. 添加 44 ID 合法、未知/空拒绝、风险与发布状态不影响官方身份测试。
3. 实现纯查询引擎与正式 Validator。
4. 提交：`feat: 增加Skill搜索筛选与详情`。

### Task 3：收藏、最近使用与组合状态

1. 先添加收藏持久/取消、未知 ID 隔离、详情不记录、显式记录、最近使用排序和不保存正文测试。
2. 实现接口、内存测试实现和 SharedPreferences 实现。
3. 添加组合编辑纯校验和 Repository 结果映射测试；生产 Route 只消费现有接口。
4. 提交：`feat: 增加官方组合与Catalog校验`。

### Task 4：Compose 页面与架构守卫

1. 先添加列表、搜索、筛选、状态标签、详情、收藏、组合、特殊边界、无障碍测试。
2. 实现 Route、Screen、Components 和纯 reducer。
3. 添加静态架构测试，确认未修改根导航、未新增 NavHost、无 DAO/Gemini/ExecutionRun 调用、Room 仍为 v7。
4. 提交：`test: 完善Skill目录Compose与架构验证`。

### Task 5：交接、验证和 Draft PR

1. 创建 PR09-04 接线说明和本地 AI 只读验收 Prompt。
2. 静态核对所有禁止文件未变化、Manifest 无 Prompt、特殊边界完整。
3. 查询当前 Head 的 GitHub Actions；只有真实结果才写通过。
4. 创建 Draft PR，不标记 Ready、不合并。

## 十二、验证命令

本地 AI 应执行：

```powershell
pwsh.exe -NoProfile -Command ".\\gradlew.bat :app:testDebugUnitTest"
pwsh.exe -NoProfile -Command ".\\gradlew.bat :app:lintDebug"
pwsh.exe -NoProfile -Command ".\\gradlew.bat :app:assembleDebug :app:assembleRelease"
pwsh.exe -NoProfile -Command ".\\gradlew.bat :app:connectedDebugAndroidTest"
```

远端开发对话当前没有可用本地仓库和 Android SDK，不能声称上述命令已执行；只进行 GitHub 差异、类型、调用链和 CI 证据核对。

## 十三、风险与回滚

- **Manifest 录入错误：** 契约测试对 44 项、ID、顺序、资产路径和门禁状态逐项断言；解析错误显示 Catalog 错误，不降级为“全部允许”。
- **PR09-04 冲突：** 不修改 `App.kt`/navigation；若 sibling 后续出现，只比较其文件清单并通过公共 Route 接线。
- **偏好错配：** 只保存稳定 ID，加载时用 Catalog 过滤；目录版本变更使用版本化 store。
- **组合冲突：** 保留 `expectedUpdatedAt`，不捕获并覆盖新版本。
- **UI 规模：** 页面组件按职责拆分，不引入第二 NavHost 或全局 ViewModel。
- **回滚：** 整体回滚本 PR 即可；无 Room Migration、无 Schema、无已有正文修改，收藏 SharedPreferences 可被忽略或清理，不影响数据库。

## 十四、未验证项

- Gradle 编译、JVM 测试、Lint、Debug/Release、Compose Instrumentation、360 dp、大字体和 TalkBack 必须由本地 AI 或 GitHub CI 实际执行。
- PR09-04 尚无开放 PR，最终接线需要其创建后重新比较文件与构造入口。
- 44 个 Skill 正文的完整原创性和许可逐项核验不属于本 PR；Manifest 如实标记门禁，不把研究来源写成正式实现来源。
