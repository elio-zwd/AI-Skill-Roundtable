# Skill 角色头像与工具视觉实施 Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 44 个官方 Skill 建立稳定、统一的视觉身份：仅工具型 Skill 使用非人物工具视觉，其余 Skill 角色使用正式人物头像，并修复已有头像在大尺寸展示时留白/主体过小的问题。

**Architecture:** 以 `OfficialSkillPrimaryType.WORKFLOW_CAPABILITY` 作为“工具型 Skill”边界；其余 `PERSON_PERSPECTIVE`、`PROFESSIONAL_ADVISOR`、`TASK_ASSISTANT` 均按人物角色展示。所有官方 Skill 的视觉资源路径由单一解析函数生成，角色主页、搜索、收藏、最近使用、详情和对话兼容层共用同一规则；资源缺失时保留确定性 fallback 作为故障兜底，而不是正常展示方案。

**Tech Stack:** Android / Kotlin / Jetpack Compose / Material 3 / Android Assets / JUnit / Android Instrumentation Tests / GitHub / Google Drive

**Spec:** 本任务为已批准的 Bounded 变更；产品契约依据 `docs/decisions/adr-009-skill-role-conversation-product-model.md`、`docs/product/jianyu-product-model.md`、`docs/product/jianyu-prd.md` 与本 Plan。

## Global Constraints

- 开发基线为 `codex/ui-05-artifacts`，实现分支为 `codex/skill-role-avatars`。
- 官方 Skill 总数保持 44，不修改 Skill Prompt、能力定义或执行资格。
- `WORKFLOW_CAPABILITY` 仅使用工具视觉，不生成拟真人头像。
- `PERSON_PERSPECTIVE`、`PROFESSIONAL_ADVISOR`、`TASK_ASSISTANT` 使用人物头像。
- 现有 20 个头像不能因为“文件已存在”而默认视为合格；必须检查大尺寸裁切、空白边和主体比例。
- 视觉资源不得依赖图片内文字表达角色名称，不使用品牌 Logo 充当角色头像。
- 不修改 Room Schema，不新增数据库 Migration，不把 legacy `skills_config.json` 恢复成官方目录事实源。
- 资源异常时必须可安全回退；fallback 仅用于故障，不作为正常生产视觉。
- 不直接修改 `main`，未经用户授权不合并 PR。
- 网页环境未实际执行 Android 本地构建时，不得宣称本地编译或设备测试通过。

---

### Task 1: 固定 44 Skill 的视觉类型与资源路径契约

**Files:**
- Modify: `app/src/test/java/com/elio/jianyu/ui/screens/skills/SkillRoleCatalogProjectionTest.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/skills/SkillRoleCatalogUiState.kt`
- Create if useful: `app/src/main/java/com/elio/jianyu/skill/role/OfficialSkillVisuals.kt`

**Interfaces:**
- Consumes: `OfficialSkillDefinition.primaryType`, `OfficialSkillDefinition.id`
- Produces: `OfficialSkillVisualKind` and/or `officialSkillVisualAssetPath(definition)`
- Rule: `WORKFLOW_CAPABILITY -> TOOL`; all other official primary types -> `PORTRAIT`

- [ ] **Step 1: 写失败测试**
  - 断言 44 个官方 Skill 均能得到稳定视觉路径。
  - 断言 6 个 `WORKFLOW_CAPABILITY` 全部被分类为工具视觉。
  - 断言其余 38 个 Skill 全部被分类为人物头像。
  - 断言视觉路径只由官方 ID / 类型决定，不依赖 legacy Character 是否存在。

- [ ] **Step 2: 验证测试当前失败**
  - 运行聚焦 JVM 测试。
  - 预期失败点：当前 `avatarAssetPath` 只来自 `legacyAvatarPaths`，大量非 legacy 角色为空。

- [ ] **Step 3: 最小实现统一视觉解析**
  - 建立单一视觉类型/路径解析。
  - 推荐路径约定：
    - 人物：`avatars/portraits/<skillId>.jpg`
    - 工具：`avatars/tools/<skillId>.png` 或等价统一格式。
  - 旧路径如需过渡，只在本任务内一次性迁移资源并同步调用方，不新增长期兼容层。

- [ ] **Step 4: 运行聚焦 JVM 测试并确认通过**

- [ ] **Step 5: 更新本 Plan 对应 checkbox 并提交原子 Commit**

---

### Task 2: 建立资源完整性测试与 44 项资源清单

**Files:**
- Create/Modify: `app/src/test/java/com/elio/jianyu/skill/role/OfficialSkillVisualAssetTest.kt`
- Read: `app/src/main/assets/official_skill_catalog_v1.json`
- Assets: `app/src/main/assets/avatars/**`

**Exact classification:**
- 工具视觉 6 项：
  - `team-handover`
  - `meeting-to-action`
  - `research-fact-checker`
  - `software-copyright-organizer`
  - `patent-disclosure-organizer`
  - `office-document-productivity`
- 人物头像 38 项：上述 6 项之外的全部官方 Skill。

- [ ] **Step 1: 写失败的资源完整性测试**
  - 逐项检查 44 个官方 Skill 的预期资源文件真实存在。
  - 人物头像与工具视觉使用各自目录/扩展规则。
  - 测试失败时输出缺失 Skill ID，避免只报数量。

- [ ] **Step 2: 验证当前测试失败**
  - 预期至少缺失当前未覆盖的 24 项，且现有 20 项仍位于旧目录。

- [ ] **Step 3: 生成迁移/新增资源清单**
  - 记录：Skill ID、中文名、视觉类型、旧资源、目标资源、是否需重新生成/裁切。
  - 清单保存进本 Plan 的执行记录或独立 status 文档；不新增无必要产品元数据 Schema。

- [ ] **Step 4: 不修改生产实现，先保留 RED 状态进入资源制作**

---

### Task 3: 处理现有 20 个头像的大图留白与构图问题

**Files:**
- Replace/Move: `app/src/main/assets/avatars/*.jpg` -> 统一人物头像目录
- Optional working copies: Google Drive `AI-Skill-Roundtable/skill-role-visuals-2026-09-23/portraits-existing/`

**Interfaces:**
- Input: 当前 20 张人物/角色头像原资源。
- Output: 适合列表和详情大头像的 1:1 人物头像，脸/头肩主体占比稳定，无明显空白边。

- [ ] **Step 1: 逐张检查 20 张现有资源**
  - 记录明显问题：空白边、主体过小、构图偏移、背景大面积无信息区域、文字化头像。
  - GitHub 二进制读取受限时，使用 Google Drive 工作副本或本地 AI 对仓库 checkout 的图片做只读检查。

- [ ] **Step 2: 对不合格资源做重裁切/重生成**
  - 优先保留人物可识别性。
  - 统一正方形、头肩近景、视觉重心居中。
  - 不在头像内写姓名或角色标题。

- [ ] **Step 3: 统一压缩**
  - 在不明显损伤头像质量的前提下控制 APK 体积。
  - 记录最终像素尺寸和文件大小分布。

- [ ] **Step 4: 将工作副本保存到 Google Drive，并同步最终资源到 GitHub 分支**

---

### Task 4: 为缺失的 18 个非工具角色生成正式人物头像

**Files:**
- Create: `app/src/main/assets/avatars/portraits/<skillId>.jpg`
- Working copies: Google Drive `AI-Skill-Roundtable/skill-role-visuals-2026-09-23/portraits-new/`

**18 项人物角色：**
- `civil-service-coach`
- `public-document-coach`
- `study-planner`
- `career-navigator`
- `resume-interview-coach`
- `workplace-communication`
- `manager-expectation-review`
- `report-proposal-writer`
- `contract-checklist`
- `hr-document-assistant`
- `budget-consumption-coach`
- `habit-wellbeing-coach`
- `relationship-dialogue-practice`
- `chinese-social-etiquette`
- `culture-fortune-entertainment`
- `content-creator`
- `product-competition-analyst`
- `original-expression-naturalizer`

- [ ] **Step 1: 为 18 项建立统一生成规范**
  - 成熟可信、非品牌化、无图片内文字。
  - 每个角色根据名称、summary、发现分类做职业/气质区分。
  - 不使用真实公众人物面孔去替代虚构/专业角色。

- [ ] **Step 2: 生成并逐项筛选头像**
  - 每项只保留一个正式生产版本。
  - 头像构图满足 Task 3 的大头像标准。

- [ ] **Step 3: 保存原始/工作版本到 Google Drive，最终压缩版本进入 GitHub**

- [ ] **Step 4: 重新运行资源完整性测试**
  - 此时人物资源部分应全部满足契约；工具 6 项仍保持 RED，进入 Task 5。

---

### Task 5: 为 6 个工具型 Skill 生成非人物工具视觉

**Files:**
- Create: `app/src/main/assets/avatars/tools/<skillId>.png`（或计划执行时选定的统一格式）
- Working copies: Google Drive `AI-Skill-Roundtable/skill-role-visuals-2026-09-23/tools/`

- [ ] **Step 1: 定义统一工具视觉语言**
  - 使用物件、文档、流程节点、核查、会议、知识产权等视觉隐喻。
  - 不画人物脸，不使用“前两个字”文字块，不把角色名称烤进图片。
  - 保持与人物头像相同的视觉密度与圆角裁切适配。

- [ ] **Step 2: 分别制作 6 项工具视觉**
  - `team-handover`: 交接/协作结构
  - `meeting-to-action`: 会议内容 -> 行动项
  - `research-fact-checker`: 来源/证据/核查
  - `software-copyright-organizer`: 软件材料/归档
  - `patent-disclosure-organizer`: 技术方案/专利交底
  - `office-document-productivity`: 办公文档/结构化处理

- [ ] **Step 3: 保存 Google Drive 工作版本和 GitHub 最终资源**

- [ ] **Step 4: 运行资源完整性测试，确认 44 项全部有正式视觉资源**

---

### Task 6: 统一角色主页、搜索、收藏、最近使用、详情的渲染链路

**Files:**
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/skills/SkillRolePageScreen.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/skills/SkillRoleDetailScreen.kt`
- Modify as needed:
  - `SkillRoleSearchScreen.kt`
  - `SkillRoleFavoritesScreen.kt`
  - `SkillRoleRecentScreen.kt`
- Modify tests:
  - `app/src/androidTest/java/com/elio/jianyu/ui/components/JianyuRoleAvatarTest.kt`
  - 相关 skills screen tests

- [ ] **Step 1: 写失败 UI/逻辑测试**
  - 非工具角色走图片人物头像，不再进入两个字 Box。
  - 工具角色走工具视觉图片，不再进入两个字 Box。
  - 详情页与列表页使用同一 asset path。
  - 图片不存在时仍保留可访问名称与 fallback。

- [ ] **Step 2: 验证测试失败原因是当前分支按角色类型手工分叉/路径为空**

- [ ] **Step 3: 最小修改 Screen**
  - 所有官方角色视觉统一交给 `JianyuRoleAvatar` 或等价共享组件。
  - 删除正常路径下基于 `name.take(2)` 的角色身份视觉实现。
  - 保留故障 fallback，不保留重复路径推导函数。

- [ ] **Step 4: 运行聚焦测试**

- [ ] **Step 5: 检查角色大图 `ContentScale.Crop` 与容器裁切组合，避免 UI 再制造额外留白**

---

### Task 7: 统一对话兼容层的视觉身份

**Files:**
- Modify: `app/src/main/java/com/elio/jianyu/skill/role/OfficialSkillConversationRoleAdapter.kt`
- Modify: `app/src/androidTest/java/com/elio/jianyu/skill/role/OfficialSkillConversationRoleAdapterAndroidTest.kt`
- Check: 对话头像消费组件，如 `SkillRoleAvatar.kt` / `DialogMessageComponents.kt`

- [ ] **Step 1: 写失败测试**
  - 人物角色兼容 Character 使用正式人物路径。
  - 工具型 Skill 使用正式工具视觉路径。
  - 不再把普通人物角色降级成 `deterministicFunctionalRoleAvatar(name)` 文字。

- [ ] **Step 2: 验证测试当前失败**

- [ ] **Step 3: 用 Task 1 的共享视觉解析替换 adapter 自己的类型分支**

- [ ] **Step 4: 检查消息、参与角色条、选择 Sheet 是否消费同一 Character avatar 路径**

- [ ] **Step 5: 运行聚焦测试**

---

### Task 8: 代码与资源回归验证

**Files:** 不新增功能；只修复本任务引入问题。

- [ ] **Step 1: 静态回读**
  - 检查 44 Skill 分类数量：38 人物 + 6 工具。
  - 检查没有遗漏旧路径、重复视觉解析、正常路径文字头像。
  - 检查图片资源命名和大小写与 Skill ID 完全一致。

- [ ] **Step 2: 运行 JVM 测试**
  - `./gradlew testDebugUnitTest` 或 Windows 对应 Wrapper 命令。

- [ ] **Step 3: 运行 Android 构建/静态检查**
  - `compileDebugKotlin`
  - `lintDebug`
  - `assembleDebug`
  - 网页环境不能执行时，明确标记为待本地验证，不虚构通过。

- [ ] **Step 4: 运行相关 Instrumentation tests**
  - 头像组件、角色发现页、详情页、对话角色视觉。
  - 若当前环境不可执行，生成本地 AI 只读验收 Prompt。

- [ ] **Step 5: `git diff --check` / PR 文件范围检查**
  - 确认不包含无关重构和依赖升级。

---

### Task 9: 本地 AI 视觉验收与问题闭环

**Acceptance matrix:**
- 角色主页：首页精选、分类列表。
- 搜索页。
- 收藏页。
- 最近使用页。
- 角色详情页大头像。
- 新建对话/增加角色后的参与角色条。
- Skill 角色消息头像。

- [ ] **Step 1: 生成只读本地 AI 验收 Prompt**
  - 禁止修改、提交、push、merge。
  - 要求返回 PASS/FAIL、截图编号、问题 Skill ID、页面、复现步骤。

- [ ] **Step 2: 验收重点**
  - 38 个人物角色均显示人物头像。
  - 6 个工具 Skill 均显示非人物视觉。
  - 无正常状态的纯文字头像。
  - 详情大头像无明显白边/空白区域，头肩主体没有因 Crop 被截断。
  - 同一 Skill 在所有页面和对话中视觉一致。

- [ ] **Step 3: 对本地 AI 报告的真实问题逐项复现/分析后修复**
  - 不因 Review 意见未经验证地改代码。

---

### Task 10: PR 收口

- [ ] **Step 1: 回读本 Plan，更新全部 checkbox 与未验证项**
- [ ] **Step 2: 创建 Draft PR（若尚未创建）**
  - Base 应匹配当前集成策略；在 `ui-05-artifacts` 尚未合入 main 时，优先以其作为依赖基线或明确 stacked PR 关系。
- [ ] **Step 3: PR 描述记录**
  - 38 人物 / 6 工具视觉契约。
  - 新增/替换资源数量。
  - 实际执行测试与未执行设备验收。
  - Google Drive 工作资产位置。
  - 回滚方式。
- [ ] **Step 4: 不自动 merge，等待用户最终授权。
