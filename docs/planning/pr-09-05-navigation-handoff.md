# PR09-05 → PR09-04 官方 Skill 目录接线说明

## 一、交接结论

PR09-05 只提供官方 Skill Catalog、页面内部状态与公共 Route、收藏/最近使用存储、官方组合 UI 以及正式 `OfficialSkillIdValidator`。根导航、`App.kt`、底部导航、深链、返回栈和最终 Repository 装配仍由 PR09-04 独占。

PR09-04 合入本 PR 后，应在 App 组合层创建一次 `OfficialSkillCatalogRuntime`，并让页面与 Repository 使用同一个 Catalog 实例，避免目录和 ID 校验出现两个事实源。

## 二、公共入口

```kotlin
@Composable
fun OfficialSkillCatalogRoute(
    repository: JianyuRepository,
    runtime: OfficialSkillCatalogRuntime,
    onUseSkill: (OfficialSkillUseRequest) -> Unit,
    modifier: Modifier = Modifier,
)
```

Catalog 初始化失败时可使用：

```kotlin
@Composable
fun OfficialSkillCatalogRoute(
    repository: JianyuRepository,
    runtimeResult: OfficialSkillCatalogRuntimeResult,
    onUseSkill: (OfficialSkillUseRequest) -> Unit,
    modifier: Modifier = Modifier,
)
```

失败分支只显示本地 Catalog 错误，不会降级成“允许任意官方 ID”。

## 三、推荐装配顺序

```kotlin
val catalogRuntimeResult = createOfficialSkillCatalogRuntime(appContext)

val repository = when (catalogRuntimeResult) {
    is OfficialSkillCatalogRuntimeResult.Success -> RoomJianyuRepository(
        dao = database.jianyuRepositoryDao(),
        officialSkillIdValidator = catalogRuntimeResult.runtime.validator,
    )
    is OfficialSkillCatalogRuntimeResult.Failure -> RoomJianyuRepository(
        dao = database.jianyuRepositoryDao(),
        // 初始化失败时继续使用默认 RejectingOfficialSkillIdValidator，禁止放宽校验。
    )
}
```

实际 `RoomJianyuRepository` 现有构造参数应保持项目原有命名和依赖；上例只强调 `officialSkillIdValidator` 的注入位置，不要求 PR09-04 重写其他依赖。

页面接线：

```kotlin
OfficialSkillCatalogRoute(
    repository = repository,
    runtimeResult = catalogRuntimeResult,
    onUseSkill = { request ->
        // PR09-04 只负责导航/上抛；不得在此直接创建 ExecutionRun 或调用 Gemini。
        onOfficialSkillIntent(request.skillId, request.intent)
    },
)
```

## 四、正式 Validator 语义

`CatalogOfficialSkillIdValidator` 只验证：

```text
该 ID 是否属于本地 44 项官方 Catalog
```

它不验证：

```text
当前是否已发布
当前是否可执行
是否存在风险标签
是否需要联网或资料
```

因此：

- 44 项官方候选均可作为官方组合成员保存；
- 风险人物仍是合法官方 ID；
- 待许可、待重构或当前不可执行的候选仍保持官方身份；
- 页面必须继续展示执行和发布状态，不得把“可保存组合”解释为“可以运行”。

## 五、使用回调与最近使用

`onUseSkill` 只返回：

```kotlin
OfficialSkillUseRequest(
    skillId = ...,
    intent = ...,
)
```

PR09-05 不会：

- 创建 `ExecutionRun`；
- 调用 Gemini；
- 自动写入最近使用；
- 自动推荐或自动邀请 Skill。

PR09-06 / PR09-07 真正完成确认并进入使用流程后，调用同一 runtime 中的：

```kotlin
runtime.preferences.recordSkillUsed(
    skillId = officialSkillId,
    usedAt = clock(),
)
```

打开详情、搜索、筛选、收藏或加入组合均不得记录为最近使用。

## 六、Route 所有权边界

PR09-04 可以：

- 在现有根 NavHost 中调用 `OfficialSkillCatalogRoute`；
- 为其定义根级 Route；
- 决定底部导航入口、返回栈和深链；
- 在 App 组合层构造 Catalog runtime 和 Repository；
- 把 `onUseSkill` 转交给后续确认流程。

PR09-04 不应：

- 复制或重建第二份 Catalog；
- 使用“全部允许”的 Validator；
- 根据风险或发布状态把官方 ID 判为非官方；
- 把打开详情写入最近使用；
- 在根导航层直接访问 DAO；
- 在接线时启动 Gemini 或创建 ExecutionRun；
- 修改 Manifest 内的 44 项状态以绕过门禁。

## 七、公共文件

```text
app/src/main/assets/official_skill_catalog_v1.json
app/src/main/java/com/elio/jianyu/skill/catalog/OfficialSkillCatalogRuntime.kt
app/src/main/java/com/elio/jianyu/skill/catalog/CatalogOfficialSkillIdValidator.kt
app/src/main/java/com/elio/jianyu/ui/screens/skills/OfficialSkillCatalogRoute.kt
```

页面内部的 `Screen`、`Components` 和 `UiState` 不应被根导航直接依赖。

## 八、合并顺序与冲突处理

推荐：

1. PR09-05 先完成本地严格只读验收并合并；
2. PR09-04 同步最新 `main`；
3. PR09-04 只在其独占的根导航与 App 组合层接线；
4. 重新执行 PR09-04 的导航、返回栈、深链和 Activity 重建测试；
5. 同时执行本 PR 的 Catalog、Validator、组合和页面回归测试。

若 PR09-04 已经创建同名 Route 或临时占位页，应删除其占位实现并调用本 PR 公共入口，不得保留两套 Skill 页面。

## 九、失败策略

Catalog 加载失败时：

- 页面显示明确错误；
- Repository 继续使用拒绝型 Validator；
- 不允许保存未知官方 ID；
- 不从旧硬编码列表或 `Character` 自动推断 44 项；
- 不联网拉取替代 Catalog。

这保证离线正常时目录可浏览，目录本身损坏时则安全失败。
