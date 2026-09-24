# 全部分支收口与主干集成 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (\`- [ ]\`) syntax for tracking.

**Goal:** 在不丢失已完成能力、不把旧基线倒灌回主干的前提下，把当前仍有价值的 UI、头像、AI 管理和独立工具工作有序集成到 \`main\`，关闭/归档已被覆盖的 PR，并在最终 \`main\` 上完成一次统一验证。

**Architecture:** 把当前仓库视为“主 UI 串行栈 + 独立功能分支 + 历史遗留分支”。先合并已经完整验收的 PR #66，随后沿堆叠关系处理 #67 → #68；独立的 #69 在最新主干上重新同步后再集成；\`ai-router-mvp\` 作为隔离工具单独 PR。#57、#65、#55 及无 PR 的设计/研究分支只做语义审计，禁止整分支盲合。

**Tech Stack:** Git / GitHub Pull Requests / GitHub Actions / Android / Kotlin / Jetpack Compose / Material 3 / Room / JVM Tests / Android Instrumentation / PowerShell 7 / Python

**Spec / Authority:**
- \`AGENTS.md\`
- \`app/src/main/java/com/elio/jianyu/ui/AGENTS.md\`
- \`docs/decisions/adr-009-skill-role-conversation-product-model.md\`
- \`docs/product/jianyu-terminology.md\`
- \`docs/product/jianyu-product-model.md\`
- \`docs/product/jianyu-prd.md\`
- \`docs/superpowers/plans/2026-09-23-skill-role-avatars.md\`
- \`docs/superpowers/specs/2026-09-24-user-avatar-upload.md\`
- \`docs/superpowers/plans/2026-09-24-user-avatar-upload.md\`
- \`docs/superpowers/plans/2026-09-25-ai-management-ui-refresh.md\`
- \`docs/planning/codex-2026-09-22-completion-report.md\`

## Global Constraints

- 不直接修改 \`main\`；所有修复都在对应现有任务分支完成。
- 不把旧分支整棵 merge 进当前主干，除非本 Plan 明确允许。
- 不 force-push，不重写已共享历史；堆叠 PR 通过普通 merge commit 保持祖先关系。
- #66 → #67 → #68 是串行堆叠链。#66、#67 必须使用普通 merge commit，不使用 squash/rebase merge。
- #69、\`ai-router-mvp\` 是独立工作，必须在主 UI 链稳定后处理。
- #57、#65、#55 和无 PR 的 docs/design 分支先做语义审计；默认不整分支合并。
- 任何 PR 的 Head 在执行期间变化时，先重新锁定 Base/Head/CI；不得沿用本 Plan 中过期 SHA 宣称验证仍有效。
- 不自动删除远端分支。关闭 PR、删除分支、最终 merge 都需要执行者确认当前用户授权范围；没有明确授权时保留分支并报告。
- 本地 AI 只做只读构建/测试/真机验收，不修改生产代码、不 commit、不 push、不 merge。
- 已通过的代码树若只增加 docs-only 提交，不要求重复本地设备全量 251 项；必须明确区分“代码验收 Head”和“docs-only Head”。
- 出现失败先读取失败步骤和首个根因；不得删除测试、降低断言、吞异常或用重跑代替修复。
- 每个阶段结束都更新本 Plan checkbox、记录实际 Head、CI、本地验收和未验证项。
- 最终 \`main\` 只有在所有计划内生产功能合并完并通过最终统一门禁后，才能宣称“全部收口完成”。

---

## Snapshot：计划编写时的仓库事实（2026-09-25）

> 执行前必须重新查询。此表只用于说明当前依赖关系，不是永久事实。

| 项目 | 状态 | Head / 关键事实 |
| --- | --- | --- |
| \`main\` | 当前基线 | \`53321b6b26d7084e97be027fe1098bcb1fe403c5\` |
| PR #66 \`codex/ui-05-artifacts\` | OPEN / Draft / clean | \`10d4b04255d7bea187f45c5e7f28796b1a18c3ad\`；Secret/UI Test Compile/Android CI 全 PASS；代码验收 Head \`4c8f339...\` 本地 568 JVM + 251 Instrumentation 全绿 |
| PR #67 \`codex/skill-role-avatars\` | OPEN / Draft / unstable | \`362e40553f4dcc54240917204d9a6435b80e6e8e\`；base=#66；Android CI PASS、Secret PASS、AndroidTest compile FAIL |
| PR #68 \`codex/user-avatar-upload\` | CLOSED / Draft / 未 merge | \`913b2ba2490a99af20b3fca6bdba1e98d0d123fb\`；base=#67；JVM 1 fail + AndroidTest compile fail |
| PR #69 \`codex/ai-management-ui-refresh\` | CLOSED / Draft / 未 merge | \`ab8ff6daa34a8499072ce703299709ef8b1594f7\`；独立基于当前旧 main；Android CI static identity gate fail |
| PR #64 \`codex/ui-03-role-discovery\` | OPEN | #66 的祖先，已被 #66 包含 |
| PR #65 \`codex/fix-app-identity-gate\` | OPEN | 单文件 \`tools/check-app-identity.ps1\`；与 #66 的更新版脚本不同 |
| PR #57 \`codex/core-loop-p0\` | OPEN / dirty | 旧基线，4 个独有提交；禁止整分支 merge |
| PR #55 \`docs/pr-09-12g-gemini-interactions-rules\` | OPEN / Draft | 旧文档分支，需对照当前 ADR/产品文档裁决 |
| \`codex/ai-router-mvp\` | 无 PR | 旧基线上的 1 个独立 commit，只新增 \`tools/ai-router/*\` |
| \`design/pr-08d-topic-route-html-prototypes\` | 无 PR | 旧设计原型分支 |
| \`docs/skills-catalog\` | 无 PR | 旧 research/catalog 文档分支 |
| PR #59/#60/#61/#62/#63 对应分支 | 已 merged | 仅属于清理候选，不再重复集成 |

### 当前已知失败根因

1. **PR #67 AndroidTest compile**
   - Run: \`36029079282\`
   - 错误：\`AddSkillRoleBottomSheetTest.kt:3:33 Unresolved reference 'assertExists'\`
   - 当前文件显式导入 \`androidx.compose.ui.test.assertExists\`；仓库其他 UI 测试主要使用当前依赖支持的 \`assertIsDisplayed\` / \`assertDoesNotExist\`。
   - #67 曾在实现 Head \`80129136455f42e07352d2e595cfdf7bcbf2e873\` 完整通过；当前 Head 比该 Head 多 10 个提交，失败属于这段新增增量，不能推翻整个头像实现的历史验收结论。

2. **PR #68 JVM**
   - Run: \`36033635178\`
   - 584 tests / 1 failed。
   - 失败：\`UserAvatarPrivacyArchitectureTest.avatarUpload_doesNotRequestBroadPhotoPermissionsOrPersistExternalUri\`，\`UserAvatarPrivacyArchitectureTest.kt:19\`。
   - 当前第 19 行要求生产源码必须直接包含字符串 \`user-profile/avatar.jpg\`，而实现通过 \`USER_PROFILE_DIRECTORY = "user-profile"\` + \`AVATAR_RELATIVE_PATH = "$USER_PROFILE_DIRECTORY/avatar.jpg"\` 组合出同一路径；行为测试应优先验证最终私有文件位置，架构测试不得用脆弱的源码拼接形式误判。

3. **PR #68 AndroidTest compile**
   - Run: \`36033635275\`
   - 与 #67 相同：\`AddSkillRoleBottomSheetTest.kt\` 的 \`assertExists\` unresolved reference。
   - #68 在 #67 之上，因此应先让父分支 #67 修复并合并，再把最新主干同步进 #68，不重复做两套父分支修复。

4. **PR #69 Android CI**
   - Run: \`36037057613\`
   - 失败步骤：\`Run static app identity gate\`。
   - 报告的是旧迁移目标、旧 Room v13、旧 Key Store / README 身份基线，与 #66 已通过的新 identity gate 规则不一致。
   - #69 的 UI/AndroidTest compile 与 Secret scan 已通过；该失败首先按“分支基线过旧”处理，而不是先改 AI 管理业务代码。

---

# Phase A：锁定执行基线

### Task 1：重新读取规则与当前 GitHub 拓扑

**Files:** Read only
- \`AGENTS.md\`
- \`app/src/main/java/com/elio/jianyu/ui/AGENTS.md\`
- 本 Plan
- 上述 Spec/Plan/完成报告

**Produces:** 一份执行时快照：最新 \`main\` SHA、所有相关 PR 的 state/draft/base/head/mergeable、当前 Head Actions。

- [ ] **Step 1: 读取根规则、UI 规则和本 Plan。**
- [ ] **Step 2: 查询 \`main\`、PR #55/#57/#64/#65/#66/#67/#68/#69。**
- [ ] **Step 3: 查询分支：**
  - \`codex/ai-router-mvp\`
  - \`codex/androidtest-budget-baseline\`
  - \`codex/core-loop-p0\`
  - \`codex/fix-app-identity-gate\`
  - \`codex/skill-role-avatars\`
  - \`codex/ui-01-mine-navigation\`
  - \`codex/ui-02-role-spec\`
  - \`codex/ui-03-role-discovery\`
  - \`codex/ui-03-spec\`
  - \`codex/ui-05-artifacts\`
  - \`codex/ui-postmerge-audit-fixes\`
  - \`codex/user-avatar-upload\`
  - \`codex/ai-management-ui-refresh\`
  - \`design/pr-08d-topic-route-html-prototypes\`
  - \`docs/pr-09-12g-gemini-interactions-rules\`
  - \`docs/skills-catalog\`
- [ ] **Step 4: 如果 #66/#67/#68 的祖先关系不再是 #66 → #67 → #68，停止后续 merge，先更新本 Plan 的 Snapshot 和集成策略。**
- [ ] **Step 5: 记录执行时精确 SHA。**

**Gate:** 不修改任何生产代码；只在状态与本 Plan 相符或已更新 Plan 后进入 Phase B。

---

# Phase B：先落地已完整验收的 #66

### Task 2：把 PR #66 作为主 UI 基线合入 main

**Branch / PR:** \`codex/ui-05-artifacts\` / #66  
**Expected historical Head:** \`10d4b042...\`

**Why first:** #66 已包含 UI-03 discovery、UI-04～UI-09、备份、Runtime 等，并且是 #67 的直接父基线。

- [ ] **Step 1: 再次确认 #66 当前 Head 的 Secret scan、Android UI Test Compile、Android CI 全 PASS。**
- [ ] **Step 2: 确认 #66 当前 \`mergeable_state=clean\`，且 base 是最新 \`main\`。**
- [ ] **Step 3: 确认从已完整设备验收的代码 Head \`4c8f339...\` 到当前 #66 Head 只有 docs-only 变化，或若有新代码则重新执行对应验证。**
- [ ] **Step 4: 在获得用户明确 merge 授权后，将 #66 标记 Ready。**
- [ ] **Step 5: 使用普通 merge commit 合并 #66；禁止 squash/rebase merge。**
- [ ] **Step 6: 锁定新的 \`main\` SHA。**
- [ ] **Step 7: 查询新 main 的 Secret scan / Android UI Test Compile / Android CI；全部成功才进入下一阶段。**

**Expected verification:**
- 由于 #66 的代码树已经做过本地全量设备验收，若 merge 本身没有冲突/额外代码修改，不要求重新本地跑 251 项。
- 新 main CI 必须成功。

**Commit/merge policy:** GitHub merge commit；保留 #66 Head 作为 main 祖先，为 #67 retarget 提供干净 ancestry。

---

### Task 3：关闭被 #66 完整覆盖的 UI-03 旧 PR

**PR:** #64 \`codex/ui-03-role-discovery\`

- [ ] **Step 1: 用 ancestry/compare 再确认 #64 Head 是已合并 #66 Head 的祖先。**
- [ ] **Step 2: 确认 #64 没有 #66 不包含的独有 commit。**
- [ ] **Step 3: 在允许关闭 PR 的授权范围内，将 #64 关闭并注明 superseded by #66。**
- [ ] **Step 4: 不删除远端 branch，留到最终清理 Task。**

**Gate:** 不 merge #64。

---

### Task 4：语义审计 PR #65 identity gate，禁止整 PR 盲合

**PR:** #65 \`codex/fix-app-identity-gate\`  
**Files:** \`tools/check-app-identity.ps1\`

- [ ] **Step 1: 在 #66 已进入 main 后比较：**
  \`\`\`powershell
  git diff main...origin/codex/fix-app-identity-gate -- tools/check-app-identity.ps1
  \`\`\`
- [ ] **Step 2: 对照最新 main 的 identity gate，检查 #65 是否仍有“当前产品身份契约必需、main 缺失”的规则。**
- [ ] **Step 3A: 若无缺失规则：关闭 #65 为 superseded，不 merge。**
- [ ] **Step 3B: 若有真实缺失规则：在从最新 main 创建的一个小修复分支中只移植必要 hunk，新增/更新对应静态测试或脚本自验证；不要 merge #65 整个旧脚本。**
- [ ] **Step 4: 运行：**
  \`\`\`powershell
  pwsh -NoProfile -File tools/check-app-identity.ps1
  pwsh -NoProfile -File tools/check-secrets.ps1 -IncludeHistory
  \`\`\`
- [ ] **Step 5: 若产生小修复 PR，先 CI 绿再 merge。**

**Gate:** 最新 main 的 identity gate 自身必须 PASS；不能把 #65 的历史“固定文件清单”规则重新引入而阻止已经批准的产品重构。

---

# Phase C：修复并合入 Skill 角色头像 #67

### Task 5：把 #67 从 stacked base retarget 到最新 main

**Branch / PR:** \`codex/skill-role-avatars\` / #67  
**Original parent:** #66

- [ ] **Step 1: 确认 #66 Head 已是 \`main\` 祖先。**
- [ ] **Step 2: 将 PR #67 base 从 \`codex/ui-05-artifacts\` 改为 \`main\`。**
- [ ] **Step 3: 检查 PR diff 只剩 #67 自身头像/视觉增量，不重新显示 #66 的 100+ 文件。**
- [ ] **Step 4: 保持 Draft，先修当前失败。**

---

### Task 6：修 #67 当前 AndroidTest compile 根因

**Files:**
- Modify: \`app/src/androidTest/java/com/elio/jianyu/ui/screens/dialog/overlays/AddSkillRoleBottomSheetTest.kt\`
- Read: \`app/src/main/java/com/elio/jianyu/ui/screens/dialog/overlays/AddSkillRoleBottomSheet.kt\`
- Read: \`app/src/test/java/com/elio/jianyu/ui/screens/dialog/overlays/AddSkillRoleBottomSheetArchitectureTest.kt\`

- [ ] **Step 1: 本地/CI 复现 \`:app:assembleDebugAndroidTest\` 的 unresolved \`assertExists\`。**
- [ ] **Step 2: 检查当前 Compose UI Test 版本和仓库已使用的断言 API。**
- [ ] **Step 3: 仅修改测试为当前依赖支持、且语义等价的存在/可见断言；优先复用仓库已有 \`assertIsDisplayed\` 模式。**
- [ ] **Step 4: 运行聚焦 AndroidTest APK compile：**
  \`\`\`powershell
  .\gradlew.bat :app:assembleDebugAndroidTest
  \`\`\`
- [ ] **Step 5: 运行 #67 聚焦 JVM：**
  \`\`\`powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "*OfficialSkillVisualAssetTest*" --tests "*OfficialSkillConversationRoleAdapterTest*" --tests "*SkillRoleCatalogProjectionTest*" --tests "*UserAvatarArchitectureTest*" --tests "*AddSkillRoleBottomSheetArchitectureTest*"
  \`\`\`
- [ ] **Step 6: Commit：**
  \`\`\`text
  test: 修复 Skill 角色头像 AndroidTest 断言兼容
  \`\`\`

**Do not:** 删除 \`recommendedAndAllSkills_renderCanonicalAvatarImages\` 测试或降低“两个角色都显示正式头像”的断言。

---

### Task 7：重新验收 #67 当前 Head，而不是复用旧 8012913 结论

**Known history:** \`8012913...\` 曾完整 PASS；当前 Head 在其上新增 10 commits。

- [ ] **Step 1: 运行：**
  \`\`\`powershell
  pwsh -NoProfile -File tools/check-app-identity.ps1
  pwsh -NoProfile -File tools/check-secrets.ps1 -IncludeHistory
  .\gradlew.bat :app:compileDebugKotlin
  .\gradlew.bat :app:testDebugUnitTest
  .\gradlew.bat :app:lintDebug
  .\gradlew.bat :app:assembleDebug
  .\gradlew.bat :app:assembleRelease
  .\gradlew.bat :app:assembleDebugAndroidTest
  \`\`\`
- [ ] **Step 2: 本地 AI 只读执行角色头像聚焦 Instrumentation / UI：**
  - 角色发现首页
  - 搜索
  - 收藏
  - 最近使用
  - 详情大头像
  - Add Skill Role Bottom Sheet
  - 对话角色头像一致性
  - 38 portraits + 6 tools
  - 人物模拟免责声明仍存在
- [ ] **Step 3: 若聚焦设备测试有新失败，GPT/执行 AI 按 systematic-debugging 修复后重跑。**
- [ ] **Step 4: 当前 Head GitHub Secret/UI Test Compile/Android CI 全绿。**
- [ ] **Step 5: 更新 #67 Plan/PR 描述中的最终验证 Head。**
- [ ] **Step 6: 用户授权后将 #67 Ready，并使用普通 merge commit 合入 main。**
- [ ] **Step 7: 新 main CI 全绿。**

**Gate:** #67 不得带任何已知 FAIL 进入 main。

---

# Phase D：同步、修复并合入用户自定义头像 #68

### Task 8：把最新 main 同步到 #68，消除父分支重复失败

**Branch / PR:** \`codex/user-avatar-upload\` / #68（当前 closed/unmerged）

- [ ] **Step 1: #67 已成功 merge main 后，checkout \`codex/user-avatar-upload\`。**
- [ ] **Step 2: 普通 merge 最新 \`main\` 到该分支；不 rebase、不 force-push。**
- [ ] **Step 3: 解决冲突时以当前 main 的 #67 头像实现为父事实，只保留 #68 的用户头像增量。**
- [ ] **Step 4: 确认 \`AddSkillRoleBottomSheetTest\` 已继承 #67 修复，不在 #68 重复另做一套。**
- [ ] **Step 5: 重新打开 PR #68，并将 base 设置为 \`main\`。**
- [ ] **Step 6: 检查 PR diff 约束在用户头像相关文件；不得重新包含 #66/#67 整体。**

---

### Task 9：修 #68 的用户头像隐私架构测试根因

**Files:**
- Modify: \`app/src/test/java/com/elio/jianyu/data/UserAvatarPrivacyArchitectureTest.kt\`
- Read/Modify only if behavior actually wrong: \`app/src/main/java/com/elio/jianyu/data/UserAvatarRepository.kt\`
- Test: \`app/src/androidTest/java/com/elio/jianyu/data/UserAvatarRepositoryAndroidTest.kt\`

- [ ] **Step 1: 复现当前 JVM failure。**
- [ ] **Step 2: 确认正式路径行为仍是 \`filesDir/user-profile/avatar.jpg\`。**
- [ ] **Step 3: 保留 Android 行为测试对最终文件路径、512×512、原子替换、reset、损坏图片不破坏旧头像的验证。**
- [ ] **Step 4: 把架构测试从“源码必须出现单个 literal \`user-profile/avatar.jpg\`”改为验证真实架构边界：**
  - 私有目录常量/路径组合明确；
  - 不请求 \`READ_MEDIA_IMAGES\` / \`READ_EXTERNAL_STORAGE\`；
  - 不保存 \`content://\` URI；
  - 不用 SharedPreferences / Room 持久化头像；
  - backup XML 不新增 filesDir include。
- [ ] **Step 5: 先运行聚焦 JVM，再运行全量 JVM。**
- [ ] **Step 6: Commit：**
  \`\`\`text
  test: 修正用户头像隐私架构门禁
  \`\`\`

**Do not:** 为让测试绿而把生产实现改成更脆弱的硬编码单字符串；也不得删除“私有文件路径”约束。

---

### Task 10：完成 #68 原 Plan 未完成的本地设备验收

**Must verify on exact PR Head:**
- Photo Picker 单图选择；
- 不弹广泛相册权限；
- 输出 512×512 JPEG；
- Mine 头像即时刷新；
- Dialog 用户消息头像同步；
- 杀进程/重启后仍存在；
- 恢复默认后 Mine/Dialog 同时回退；
- 取消 picker 无状态变化；
- 非法图片不破坏已有头像；
- 不进入 Room、当前备份协议不扩大。

- [ ] **Step 1: Gradle/静态：**
  \`\`\`powershell
  pwsh -NoProfile -File tools/check-app-identity.ps1
  pwsh -NoProfile -File tools/check-secrets.ps1 -IncludeHistory
  .\gradlew.bat :app:compileDebugKotlin
  .\gradlew.bat :app:testDebugUnitTest
  .\gradlew.bat :app:lintDebug
  .\gradlew.bat :app:assembleDebug
  .\gradlew.bat :app:assembleRelease
  .\gradlew.bat :app:assembleDebugAndroidTest
  \`\`\`
- [ ] **Step 2: 本地 AI 只读运行 Repository/Mine/Dialog 聚焦 Instrumentation。**
- [ ] **Step 3: 本地 AI 真机/模拟器执行上述 Photo Picker 功能矩阵。**
- [ ] **Step 4: GitHub 三项 Actions 全绿。**
- [ ] **Step 5: 更新 #68 Plan checkbox 与 PR 描述。**
- [ ] **Step 6: 用户授权后使用普通 merge commit 合入 main。**
- [ ] **Step 7: 新 main CI 全绿。**

---

# Phase E：在最新 main 上重新接入 AI 管理 UI #69

### Task 11：恢复 #69，并先解决“旧基线”而不是改业务

**Branch / PR:** \`codex/ai-management-ui-refresh\` / #69（当前 closed/unmerged）

- [ ] **Step 1: #68 已 merge main 后，将最新 main 普通 merge 到 \`codex/ai-management-ui-refresh\`。**
- [ ] **Step 2: 冲突仅围绕以下 6 个功能文件处理：**
  - \`AiManagementRoute.kt\`
  - \`AiManagementScreen.kt\`
  - \`AiManagementUiState.kt\`
  - \`AiManagementUiStateTest.kt\`
  - \`SettingsScreenRegressionTest.kt\`
  - \`docs/superpowers/plans/2026-09-25-ai-management-ui-refresh.md\`
- [ ] **Step 3: 对 \`tools/check-app-identity.ps1\`、Room、Backup、Avatar、Dialog 等非 #69 任务文件一律保留最新 main 版本。**
- [ ] **Step 4: 运行 identity gate；预期旧“Package Move Mapping / Room v13 / Key Store Contract / README”失败因同步最新 main 而消失。**
- [ ] **Step 5: 若 identity gate 仍失败，先比较最新 main 脚本与分支脚本，禁止修改 AI 管理 UI 来规避脚本问题。**
- [ ] **Step 6: 重新打开 PR #69，base=\`main\`。**

---

### Task 12：验证 #69 没有改变模型/BYOK 业务契约

- [ ] **Step 1: 运行 \`AiManagementUiStateTest\` 和设置页聚焦 JVM/UI tests。**
- [ ] **Step 2: 全量：**
  \`\`\`powershell
  pwsh -NoProfile -File tools/check-app-identity.ps1
  pwsh -NoProfile -File tools/check-secrets.ps1 -IncludeHistory
  .\gradlew.bat :app:compileDebugKotlin
  .\gradlew.bat :app:testDebugUnitTest
  .\gradlew.bat :app:lintDebug
  .\gradlew.bat :app:assembleDebug
  .\gradlew.bat :app:assembleRelease
  .\gradlew.bat :app:assembleDebugAndroidTest
  \`\`\`
- [ ] **Step 3: 本地 AI 只读 UI 验收：**
  - 首页五种模型用途只显示当前选择；
  - 联网检索只显示 Gemini 支持模型；
  - 模型选择 Bottom Sheet；
  - Gemini/DeepSeek Key 状态摘要；
  - Provider Key Bottom Sheet；
  - 批量导入、验证、启停、删除、清空能力仍可达；
  - 不显示完整 Key；
  - Bottom Sheet 切 Provider 不串前一个 Provider 的输入/状态；
  - Back 行为正确。
- [ ] **Step 4: GitHub Actions 全绿。**
- [ ] **Step 5: 更新 #69 Plan Task 8 与 PR 描述。**
- [ ] **Step 6: 用户授权后普通 merge commit 合 main。**
- [ ] **Step 7: 新 main CI 全绿。**

---

# Phase F：独立工具 ai-router-mvp

### Task 13：在最新 main 上整理并单独 PR 本地 AI Router

**Branch:** \`codex/ai-router-mvp\`  
**Unique commit:** \`f55904066a09f6cc403e14a6623489e2e070cc97\`

**Scope:** 只允许 \`tools/ai-router/*\` 与必要 docs/ignore 调整；不触碰 Android 产品代码。

- [ ] **Step 1: 比较最新 main 与该分支，确认唯一业务增量仍是 \`tools/ai-router/*\`。**
- [ ] **Step 2: 普通 merge 最新 main 到 \`codex/ai-router-mvp\`；若会产生大量无意义冲突，则创建新的 \`codex/ai-router-mvp-refresh\` 从 main 出发，只 cherry-pick \`f5590406...\`。只有在现有分支无法安全快进/合并时才用新 refresh branch。**
- [ ] **Step 3: 运行离线测试：**
  \`\`\`powershell
  python .\tools\ai-router\test_router.py
  \`\`\`
- [ ] **Step 4: 运行 doctor（不得访问真实 Drive）：**
  \`\`\`powershell
  python .\tools\ai-router\router.py doctor
  \`\`\`
- [ ] **Step 5: 检查真实 token、OAuth client secrets、cursor/log/request 文件均未提交。**
- [ ] **Step 6: 创建独立 PR 到 main；描述清楚 Router 不读取 ChatGPT 历史正文、不自动触发 Local AI、不回写 Drive 控制文件。**
- [ ] **Step 7: 该工具 PR 通过离线测试/Secret scan 后，再由用户决定是否 merge。**

**Do not:** 把 Router 合进 Android feature PR；不打开真实浏览器发送作为 CI/自动验收。

---

# Phase G：历史分支/文档分支语义审计

### Task 14：审计 PR #57 core-loop-p0，禁止整分支 merge

**PR:** #57 \`codex/core-loop-p0\`  
**Current:** dirty against main  
**Unique commits at plan time:**
- \`24a5e3e\` feat: 完成议题协作核心闭环
- \`3d84382\` style: 合并本地 UI 补充调整
- \`14cd489\` style: 升级全应用背景色调与工作区收纳抽屉
- \`399b8e3\` docs: 归档 PR09 UI 设计原型资产

- [ ] **Step 1: 在所有新功能合入 main 后，对每个 commit 做 semantic diff，不以 commit SHA 是否祖先作为唯一判断。**
- [ ] **Step 2: 对 \`24a5e3e\` 检查当前 main 已存在的点名回应、交叉讨论、Stage/Issue collaboration、相关 Repository 和测试；若当前行为已覆盖，则标 superseded。**
- [ ] **Step 3: 对 \`3d84382\` / \`14cd489\` 对照当前 ADR/UI 规范与 #66 后的现状；旧视觉不能覆盖当前已验收 UI。**
- [ ] **Step 4: 对 \`399b8e3\` 仅判断设计证据是否值得归档；不把 screenshots/XML 当生产代码依赖。**
- [ ] **Step 5: 若发现当前 main 真缺失且仍符合现行产品契约的行为，创建**新的小分支**从最新 main 移植最小代码和测试；禁止 merge #57。**
- [ ] **Step 6: 若无缺失，获得授权后关闭 #57 为 superseded。**

---

### Task 15：审计 PR #55 Gemini 规则文档

**PR:** #55 \`docs/pr-09-12g-gemini-interactions-rules\`

- [ ] **Step 1: 对照当前 Accepted ADR、Gemini API guide、产品模型和 #66 后实际网络实现。**
- [ ] **Step 2: 把 #55 文档内容分为：仍有效 / 已被覆盖 / 与当前实现冲突。**
- [ ] **Step 3A: 若全部被更新文档覆盖：关闭 #55，不 merge。**
- [ ] **Step 3B: 若有仍有效且 main 缺失的规则：在 docs-only 小 PR 中只移植有效部分，并明确 supersession；不要整分支 merge。**

---

### Task 16：审计无 PR 的 design / research 分支

**Branches:**
- \`design/pr-08d-topic-route-html-prototypes\`
- \`docs/skills-catalog\`

- [ ] **Step 1: 确认两者均不承载当前生产 App 唯一实现。**
- [ ] **Step 2: 检查 main 是否已有等价/更新的品牌设计、Skill catalog 文档。**
- [ ] **Step 3: 若只是历史研究/原型，保持远端归档，不进入 main。**
- [ ] **Step 4: 若团队明确希望把研究资料纳入主仓文档，创建 docs-only PR，从最新 main 只移植文档目录；不得夹带旧生产实现。**

---

# Phase H：最终 main 统一验证

### Task 17：最终代码/安全/构建门禁

**Target:** 所有计划内生产功能已进入 \`main\` 后的精确 SHA。

- [ ] **Step 1: 锁定最终 main SHA，并确认无未解释的 open feature PR 仍包含计划内生产能力。**
- [ ] **Step 2: Windows 10 / JDK 17 执行：**
  \`\`\`powershell
  .\gradlew.bat --stop
  pwsh -NoProfile -File tools/check-app-identity.ps1
  pwsh -NoProfile -File tools/check-secrets.ps1 -IncludeHistory
  .\gradlew.bat :app:compileDebugKotlin
  .\gradlew.bat :app:testDebugUnitTest
  .\gradlew.bat :app:lintDebug
  .\gradlew.bat :app:assembleDebug
  .\gradlew.bat :app:assembleRelease
  .\gradlew.bat :app:assembleDebugAndroidTest
  \`\`\`
- [ ] **Step 3: 检查 Room committed schema current、release/R8、APK artifacts。**
- [ ] **Step 4: GitHub main 的 Secret scan、Android UI Test Compile、Android CI 全部成功。**

---

### Task 18：最终本地 AI 只读全量设备验收

**Rules:** 不改代码、不自动修复、不 commit/push/merge、不使用真实生产 API Key。

- [ ] **Step 1: 执行 \`:app:connectedDebugAndroidTest\` 全量；记录测试类数、passed/failed/skipped。**
- [ ] **Step 2: 复核主流程：**
  - UI-03～UI-09；
  - Skill 角色 38 portraits + 6 tools；
  - 用户自定义头像 Photo Picker / 重启 / reset；
  - AI 管理模型用途与双 Provider Key Sheet；
  - Portable Backup；
  - Device Snapshot；
  - Runtime reopen；
  - Privacy。
- [ ] **Step 3: 视觉/可用性：**
  - 明暗主题；
  - reduced motion；
  - 高对比度；
  - 360dp；
  - 200% 字号；
  - 键盘；
  - TalkBack 真实朗读若测试设备具备 TTS。
- [ ] **Step 4: 最终 Git cleanliness：**
  \`\`\`powershell
  git status --short
  git diff --exit-code
  git rev-parse HEAD
  \`\`\`
- [ ] **Step 5: 只有 0 failed 且无新的高风险人工缺陷，才能标记最终集成 PASS。**

---

# Phase I：PR 与远端分支清场

### Task 19：关闭 superseded PR

在最终 main PASS 后：

- [ ] **Step 1: #64 若已由 #66 覆盖，关闭。**
- [ ] **Step 2: #65 若语义审计确认已被 main 覆盖，关闭。**
- [ ] **Step 3: #57 若无移植项，关闭。**
- [ ] **Step 4: #55 若已被当前文档覆盖，关闭。**
- [ ] **Step 5: #68/#69 若已通过各自原 PR 合并则保持 merged 记录；若使用替代 refresh PR，则在旧 PR 留 superseded 说明后关闭。**

**Requires:** 用户允许关闭这些 PR；否则只报告“可关闭”，不执行。

---

### Task 20：删除已 merged / superseded 的远端分支

**Candidates:**
- \`codex/androidtest-budget-baseline\`
- \`codex/ui-01-mine-navigation\`
- \`codex/ui-02-role-spec\`
- \`codex/ui-03-spec\`
- \`codex/ui-postmerge-audit-fixes\`
- \`codex/ui-03-role-discovery\`
- \`codex/ui-05-artifacts\`
- \`codex/skill-role-avatars\`
- \`codex/user-avatar-upload\`
- \`codex/ai-management-ui-refresh\`
- \`codex/fix-app-identity-gate\`
- 其他经审计确认已无独有价值的旧分支

- [ ] **Step 1: 删除前逐个确认对应提交已在 main 可达，或 PR 明确 superseded 且无唯一未保存工作。**
- [ ] **Step 2: 输出拟删除清单给用户。**
- [ ] **Step 3: 只有得到明确删除授权后才删除远端分支。**
- [ ] **Step 4: \`design/*\` / \`docs/*\` 归档分支默认保留，除非用户明确要求清理。**
- [ ] **Step 5: 不删除 \`main\`，不 force delete 未确认分支。**

---

# Final Completion Criteria

只有同时满足以下条件，执行 AI 才能报告“全部分支收口完成”：

- [ ] #66 已以 merge commit 进入 main。
- [ ] #67 当前 Head 修复、完整验证并进入 main。
- [ ] #68 同步最新 parent/main、修复 JVM/AndroidTest、真机用户头像流程 PASS 并进入 main。
- [ ] #69 在最新 main 上重新同步，identity gate 与 AI 管理 UI 验收 PASS 并进入 main。
- [ ] \`ai-router-mvp\` 已形成独立可审 PR，并按用户选择 merge 或明确保留未集成状态。
- [ ] #64/#65/#57/#55 已完成语义审计，不能存在“是否还需要合并”的未知状态。
- [ ] design/research 分支已明确标记为“归档保留”或 docs-only 集成，不混入生产代码。
- [ ] 最终 main GitHub CI 全绿。
- [ ] 最终 main 本地 JVM / Release / AndroidTest APK / 全量 Instrumentation PASS。
- [ ] Secret / identity / Room / R8 / backup/runtime/privacy 门禁无回退。
- [ ] 没有通过删除测试、降低断言或恢复旧兼容实现制造绿色。
- [ ] PR/branch 清理状态有明确记录；未获删除授权的分支明确标记“保留”。

---

# Execution Handoff

执行者开始前必须先完成 **Task 1**，不要直接从本 Plan 中的旧 SHA 开始 merge。

推荐执行批次：

1. **Batch 1:** Task 1–4（#66 + #64/#65 收口）
2. **Batch 2:** Task 5–7（#67）
3. **Batch 3:** Task 8–10（#68）
4. **Batch 4:** Task 11–12（#69）
5. **Batch 5:** Task 13–16（Router + 历史/文档分支审计）
6. **Batch 6:** Task 17–20（最终 main 验收 + 清理）

每个 Batch 结束必须记录：
- 当前 main SHA；
- 当前 PR Head；
- 新增 commits；
- GitHub Actions；
- 本地 AI 验收；
- 尚未验证项；
- 下一 Batch 是否满足前置条件。

任何一个 Batch 有未解释失败时停止推进后续 merge，先修复当前 Batch。
