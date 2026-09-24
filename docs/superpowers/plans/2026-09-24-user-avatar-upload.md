# 用户自定义头像 Implementation Plan

> **For agentic workers:** 按 Task 顺序执行；每完成一组任务更新 checkbox。生产代码遵循 TDD：先写失败测试，再做最小实现。

**Goal:** 在「我的」页支持从系统 Photo Picker 选择用户头像，保存为 App 私有 512×512 JPEG，并让「我的」与对话用户消息即时共享；支持恢复默认头像。

**Spec:** \`docs/superpowers/specs/2026-09-24-user-avatar-upload.md\`

**Branch:** \`codex/user-avatar-upload\`

**Base:** \`codex/skill-role-avatars\`

**Verification policy:** GPT 负责代码、测试与 GitHub 自审；Gradle / Instrumentation / 真机 UI 由本地 AI 执行并返回压缩证据。除非用户另行要求，不等待 GitHub CI 作为推进前置条件。

---

## Global Constraints

- 不新增 Room Entity / Migration。
- 不申请 \`READ_MEDIA_IMAGES\` 或 \`READ_EXTERNAL_STORAGE\`。
- 不保存外部 Photo Picker URI。
- 不把头像加入当前系统备份或 PR09-13 可移植备份。
- 不修改 Skill 角色头像体系。
- 不升级 Kotlin / AGP / Compose / Room 等无关依赖。
- 自定义头像正式文件固定为 \`filesDir/user-profile/avatar.jpg\`。
- 导入失败不能破坏已有头像。
- Mine / Dialog Screen 不执行文件持久化 IO。
- PR 保持 Draft；未经用户授权不 merge。

---

### Task 1：建立 UserAvatarRepository 图像协议

**Files:**
- Create: \`app/src/main/java/com/elio/jianyu/data/UserAvatarRepository.kt\`
- Create: \`app/src/androidTest/java/com/elio/jianyu/data/UserAvatarRepositoryAndroidTest.kt\`
- Optional test fixture helper under androidTest only

- [ ] **Step 1: 写失败测试**
  - 导入横向图片后输出文件存在。
  - 输出为 512×512。
  - 输出路径固定为 \`filesDir/user-profile/avatar.jpg\`。
  - 第二次导入原子替换旧文件。
  - reset 删除正式文件。
  - 新 Repository 实例能重新读取重启前保存的头像。
  - 导入损坏图片失败且旧头像保持不变。

- [ ] **Step 2: 验证 RED**
  - 本地 AI 运行聚焦 AndroidTest，预期因 Repository 尚不存在/行为缺失失败。

- [ ] **Step 3: 最小实现 Repository**
  - ContentResolver 读取。
  - bounds decode + inSampleSize。
  - framework EXIF orientation。
  - center crop 1:1。
  - scale 512×512。
  - JPEG quality 90 左右。
  - \`AtomicFile\` 或等价原子发布。
  - 进程内共享 revision Flow。
  - import/reset 后发布变化。

- [ ] **Step 4: 本地 AI 运行 Repository AndroidTest → PASS**

---

### Task 2：升级公共 UserAvatar 为自定义头像优先

**Files:**
- Modify: \`app/src/main/java/com/elio/jianyu/ui/components/UserAvatar.kt\`
- Modify/Create tests:
  - \`app/src/test/java/com/elio/jianyu/ui/components/UserAvatarArchitectureTest.kt\`
  - \`app/src/androidTest/java/com/elio/jianyu/ui/components/UserAvatarTest.kt\`（如当前不存在则新增）

- [ ] **Step 1: 写失败测试**
  - 提供自定义 \`ImageBitmap\` 时不显示 fallback drawable。
  - 无自定义头像时继续显示默认头像。
  - 默认头像保留当前 1.5x 顶部裁切规则。
  - 自定义头像不使用默认头像 zoom。

- [ ] **Step 2: 验证 RED**

- [ ] **Step 3: 最小实现**
  - 增加 \`LocalUserAvatarImage\` 或等价 UI 展示状态入口。
  - UserAvatar 只负责显示，不读取 filesDir。
  - 自定义头像优先；null 时 fallback。

- [ ] **Step 4: 聚焦测试 PASS**

---

### Task 3：MineRoute 接入 Photo Picker 与正式编辑入口

**Files:**
- Modify: \`app/src/main/java/com/elio/jianyu/ui/screens/mine/MineRoute.kt\`
- Modify: \`app/src/main/java/com/elio/jianyu/ui/screens/mine/MineScreen.kt\`
- Modify: \`app/src/main/java/com/elio/jianyu/ui/screens/mine/MineUiState.kt\`
- Modify: \`app/src/main/java/com/elio/jianyu/ui/automation/JianyuAutomationTags.kt\`
- Modify: \`app/src/androidTest/java/com/elio/jianyu/ui/screens/mine/MineScreenTest.kt\`
- Create if useful: Mine avatar action sheet component/test

- [ ] **Step 1: 写失败 UI 测试**
  - 头像铅笔按钮 enabled。
  - 点击分发 \`onEditAvatar\`。
  - 旧 \`AVATAR_SWITCH_UNAVAILABLE\` 契约不存在。
  - 正式 \`AVATAR_EDIT_BUTTON\` testTag 存在。

- [ ] **Step 2: 验证 RED**

- [ ] **Step 3: MineRoute 接入 Repository**
  - remember UserAvatarRepository。
  - collect avatar snapshot。
  - decode 为 UI \`ImageBitmap\`。
  - 提供给 MineScreen / UserAvatar。

- [ ] **Step 4: 接入 \`PickVisualMedia\`**
  - 仅单图。
  - picker cancel 不提示错误、不改状态。
  - import 在 coroutine 中执行。
  - success/failure Toast。

- [ ] **Step 5: Material 3 Bottom Sheet**
  - 从相册选择。
  - 恢复默认头像。
  - 取消/手势关闭。
  - reset 只在有自定义头像时可用。

- [ ] **Step 6: Mine UI 测试 PASS**

---

### Task 4：DialogRoute 同步用户头像

**Files:**
- Modify: \`app/src/main/java/com/elio/jianyu/ui/screens/dialog/DialogRoute.kt\`
- Check: \`app/src/main/java/com/elio/jianyu/ui/screens/dialog/components/DialogMessageComponents.kt\`
- Add/Modify related UI tests

- [ ] **Step 1: 写失败契约测试**
  - DialogRoute 观察 UserAvatarRepository。
  - DialogScreen 树获得同一自定义头像展示状态。
  - UserMessageBubble 继续只调用公共 UserAvatar，不自行读文件。

- [ ] **Step 2: 验证 RED**

- [ ] **Step 3: 最小实现**
  - DialogRoute collect avatar snapshot。
  - 将已解码 ImageBitmap 通过共享 UI provider 提供给 DialogScreen。
  - 不修改消息数据库模型，不把头像路径写进 Message。

- [ ] **Step 4: 聚焦测试 PASS**

---

### Task 5：备份、权限与安全边界门禁

**Files:**
- Add unit/architecture tests as needed
- Read only unless required:
  - \`app/src/main/AndroidManifest.xml\`
  - \`app/src/main/res/xml/backup_rules.xml\`
  - \`app/src/main/res/xml/data_extraction_rules.xml\`

- [ ] **Step 1: 添加门禁**
  - Manifest 不出现相册读取权限。
  - 用户头像不写 SharedPreferences。
  - 用户头像不写 Room。
  - backup XML 不新增 filesDir include。
  - Repository 不持久化 content URI 字符串。

- [ ] **Step 2: 静态回读 PASS**

---

### Task 6：本地 AI 功能验证

**Target commands:**
- Repository 聚焦 AndroidTest
- MineScreen 聚焦 AndroidTest
- UserAvatar 聚焦测试
- Dialog 用户头像相关测试
- \`compileDebugKotlin\`
- \`testDebugUnitTest\`
- \`assembleDebug\`
- 必要时 \`connectedDebugAndroidTest\`

- [ ] **Step 1: 生成只读本地 AI 验收 Prompt**
- [ ] **Step 2: 本地 AI 返回编译/测试证据**
- [ ] **Step 3: 真机验证**
  - 「我的」点击铅笔。
  - 从系统 Photo Picker 选图。
  - 头像立即更新。
  - 对话用户消息同步。
  - 杀进程/重启后仍存在。
  - 恢复默认后两处同时回退。
  - 取消 picker 不改变头像。
  - 不出现相册权限请求。
- [ ] **Step 4: 对真实问题定点修复并复验**

---

### Task 7：PR 收口

- [ ] **Step 1: 回读 Spec / Plan / diff**
- [ ] **Step 2: 确认仅包含用户头像功能相关文件**
- [ ] **Step 3: 更新 Plan checkbox 与实际验证**
- [ ] **Step 4: 更新 Draft PR 描述**
- [ ] **Step 5: 保持 Draft，不自动 merge，等待用户集成授权**
