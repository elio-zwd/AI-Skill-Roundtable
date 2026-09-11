# UI-02 角色页面｜第二轮视觉与会话桥只读验收 Prompt

> 分支：`codex/ui-02-role-spec`
>
> 用途：第一轮 Drive 原图对比后的第二轮本地验收。
>
> **只读验收。不得修改源码、测试、Gradle、资源或文档；不得 auto-fix；不得 commit / push / merge。**

## 目标

验证第一轮视觉校准后的 UI-02 是否：

1. 更接近选定 A/B 参考图；
2. 不再暴露内部 raw `domainTags`；
3. 真人 / 功能角色身份视觉符合已批准规则；
4. `meeting-to-action` 的“开始新对话”能稳定进入正式 Dialog；
5. `study-planner` 的“增加到当前会话”能进入同一当前会话；
6. recent 只因真正成功的使用动作产生。

本轮重点是**截图证据 + 会话 participant 证据**，不是修改 APP。

## 0. 工作区门禁

执行：

```powershell
git fetch origin
git switch codex/ui-02-role-spec
git pull --ff-only
git branch --show-current
git rev-parse HEAD
git status --short
```

要求：

- branch 必须为 `codex/ui-02-role-spec`；
- 开始前工作区 clean；
- 记录 HEAD；
- 验收产生的截图、UI dump、日志、报告全部写到**仓库外临时目录**，不得污染 worktree；
- 结束再次 `git status --short`，必须 clean。

若开始前不 clean、分支不对或 pull 失败：直接 FAIL，不做自动处理。

## 1. 本轮编译门禁

GitHub 远端已证明当前视觉代码可以通过：

```text
:app:compileDebugKotlin = PASS
```

聚合 AndroidTest 当前仍被仓库既有基线阻塞：

```text
app/src/androidTest/java/com/elio/jianyu/ui/screens/execution/IssueExecutionStopAvailabilityTest.kt:49
IssueExecutionBudgetUi(30, 1, 1, false)
```

生产构造器当前为：

```text
IssueExecutionBudgetUi(usedApiCalls: Int, closed: Boolean)
```

本轮本地执行：

```powershell
.\gradlew.bat --no-daemon :app:compileDebugKotlin
.\gradlew.bat --no-daemon :app:testDebugUnitTest `
  --tests "com.elio.jianyu.skill.role.SkillRolePresentationCatalogTest" `
  --tests "com.elio.jianyu.ui.screens.skills.SkillRoleCatalogProjectionTest" `
  --tests "com.elio.jianyu.skill.role.OfficialSkillConversationRoleAdapterTest"
.\gradlew.bat --no-daemon :app:lintDebug
.\gradlew.bat --no-daemon :app:assembleDebug
.\gradlew.bat --no-daemon :app:assembleDebugAndroidTest
```

规则：

- `compileDebugKotlin / targeted JVM / lintDebug / assembleDebug` 按真实结果 PASS/FAIL；
- 若 `assembleDebugAndroidTest` 唯一失败仍是上述 `IssueExecutionStopAvailabilityTest.kt:49`，记 `BLOCKED_BASELINE`；
- 不得改、删、注释、跳过该旧测试来制造 AndroidTest PASS；
- 若出现任何 `SkillRolePageScreen.kt / SkillRoleDetailScreen.kt / UI-02` 新编译错误，则本轮直接 FAIL 并回报首个错误。

## 2. 安装与设备

确认目标：

```powershell
adb devices
adb shell wm size
adb shell wm density
```

优先 Xiaomi 14 Ultra 1440×3200；若只能使用模拟器，也可继续，但必须在报告里记录真实尺寸、density、font scale。

安装：

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## 3. 进入 UI-02 的方式：避免上一轮 deep-link back stack 干扰

当前 UI-01 共享底栏仍是外部依赖，因此可以用 deep link **只做 bootstrap**：

```powershell
adb shell am start -a android.intent.action.VIEW -d "jianyu://skills/meeting-to-action" com.elio.jianyu
```

随后：

1. 等详情页稳定出现；
2. **不要按系统返回键作为正式 UI-02 入口**；
3. 点击当前旧壳层底部的 `Skill` 一级项，让导航明确落到 Skill 顶层目录；
4. 确认页面显示 `Skill 角色`、搜索框、分类 Chips，且底部 `Skill` 处于选中；
5. 从这一刻开始再做本轮所有目录/详情/会话动作。

如果可以通过 APP 自身其它正常一级入口直接到 UI-02，可使用正常入口；核心要求是：**开始会话桥验收时，不要把 deep-link 详情残留在 back stack 当成目录入口。**

## 4. 第二轮必须采集的截图

所有截图保存在仓库外临时目录。文件名固定：

```text
01-role-all-top-r2.png
02-role-featured-complete-r2.png
03-role-all-list-r2.png
04-role-category-life-tools-r2.png
05-role-search-meeting-to-action-r2.png
06-role-detail-person-top-r2.png
07-role-detail-person-bottom-r2.png
08-role-detail-meeting-to-action-r2.png
09-start-new-meeting-dialog-r2.png
10-add-current-study-planner-dialog-r2.png
11-recent-after-two-success-actions-r2.png
12-search-empty-state-r2.png
```

### 4.1 A 页【全部】

采集 `01 / 02 / 03`：

- 标题区比上一轮更紧凑；
- 标题 `Skill 角色`；
- 搜索框；
- 8 个 Chips；
- 标题必须是 `推荐角色`，不得出现 `为你推荐`；
- 固定推荐顺序：纳瓦尔 → 理查德·费曼 → 纳西姆·塔勒布；
- Hero 应有明显的大人物主视觉；
- 两张次卡应有“左侧视觉 + 右侧信息”的层级；
- `全部角色` 向下可见；
- 页面不得显示 `career_workplace / creator_business / personal_finance` 等 raw token。

### 4.2 B 页【生活工具】

采集 `04`：

- 分类说明卡；
- 右侧轻量节点/连线品牌纹理；
- 5 个主发现角色；
- 双列卡上半部有明显身份视觉；
- 功能型角色使用非真人视觉，不能出现凭空生成的人脸；
- 卡片下半部是名称、摘要、产品语义标签。

### 4.3 搜索与空态

采集 `05 / 12`：

- 搜索 `meeting-to-action` 命中“会议纪要与行动项助手”；
- 输入 `__ui02_no_such_role__` 显示明确空态；
- 搜索、筛选、打开详情本身不得写 recent。

## 5. 详情页视觉

### 5.1 真人：纳瓦尔

采集 `06 / 07`：

顶部：

- 标题 `Skill 角色详情`；
- 大身份 Hero；
- 姓名；
- `AI 模拟角色`；
- 用户可理解的分类/角色类型；
- 不得出现 raw `domainTags`。

正文到底部：

- 适合的问题；
- 工作方式；
- 输入要求；
- 输出形式；
- 边界；
- 来源与能力依据；
- AI 模拟说明；
- 固定 `开始新对话 / 增加到当前会话` 双动作；
- 系统状态栏、底部手势区不得遮挡内容或按钮。

### 5.2 功能角色：meeting-to-action

采集 `08`：

- 功能角色使用稳定非真人身份视觉；
- 不显示真人 disclaimer；
- 官方 Skill 的能力/输入/输出/边界仍完整；
- 不显示 raw `domainTags`。

## 6. 会话桥：本轮必须稳定取证

### 6.1 开始新对话：meeting-to-action

从**顶层 UI-02 目录**搜索并打开 `meeting-to-action`，点击 `开始新对话`。

不要点击后立即截图。最多等待 10 秒观察状态稳定：

- `Skill 角色详情` 应消失；
- 应进入正式 Dialog / 对话页；
- participant 区必须能识别“会议纪要与行动项助手”；
- 若 UI 有输入框，确认对话输入区域存在。

可每 1 秒执行一次只读 UI dump 辅助判断：

```powershell
adb shell uiautomator dump /sdcard/ui02.xml
adb pull /sdcard/ui02.xml <临时目录>\ui02-start-new.xml
```

成功后采集 `09-start-new-meeting-dialog-r2.png`。

若 10 秒后仍稳定停留详情：

- `MANUAL_START_NEW_BRIDGE: FAIL`；
- 保存截图 + 最后一份 UI dump；
- 不自动修复；
- 不因为 recent 出现就改判 PASS。

若成功进入 Dialog，可发送一条最简单测试问题，例如：

```text
请把这句话整理成一个行动项：明天完成测试。
```

只检查有没有“角色不存在 / Skill asset 不存在 / 空 Prompt”类错误；不评价模型答案质量。若网络/API 本身不可用，单独记录 `MODEL_CALL: BLOCKED_ENVIRONMENT`，只要 participant 接入成功，不把它等同于会话桥失败。

### 6.2 增加到当前会话：study-planner

在上一步已经存在当前会话后：

1. 通过底部 `Skill` 进入 UI-02 顶层目录；
2. 搜索 `study-planner`；
3. 打开“学习规划师”；
4. 此时 `增加到当前会话` 应为 enabled；
5. 点击；
6. 最多等待 10 秒直到回到 Dialog；
7. participant 必须同时包含：
   - 会议纪要与行动项助手；
   - 学习规划师。

采集 `10-add-current-study-planner-dialog-r2.png`。

若按钮仍显示“当前没有可加入的会话”：记录 FAIL，并截图；不得新建第二个会话绕开问题。

## 7. Recent 语义

完成两次真实成功动作后，再回 UI-02【全部】，采集 `11`。

要求：

- `最近使用` 出现；
- 至少能看到刚成功使用的角色；
- 只打开详情、搜索、切分类、收藏不得额外制造 recent；
- 成功开始新对话 / 成功加入当前会话才可写 recent。

如果动作失败但 recent 仍新增，记为 FAIL。

## 8. Drive 上传

使用用户已建立的 UI Visual Drive 根目录：

`AI-Skill-Roundtable-UI-Visual-Evidence`

不要覆盖上一轮：

`20260911-0836__a1cc5e6`

新建本轮 run，命名：

```text
<YYYYMMDD-HHmm>__<HEAD前7位>__visual-r2
```

建议目录：

```text
00-environment/
01-role-catalog/
02-role-category/
03-role-detail/
04-conversation-bridge/
05-recent-empty/
```

同时生成并上传：

### `screenshot-index.md`

每张图片记录：

```text
- file:
  screen:
  action:
  expected:
  actual:
  result: PASS / FAIL / BLOCKED_ENVIRONMENT
```

### `acceptance-summary.txt`

只写压缩结果，不附完整日志。

## 9. 最终回报格式

只返回：

```text
UI-02 VISUAL ROUND 2
BRANCH: codex/ui-02-role-spec
HEAD: <sha>
WORKTREE_BEFORE: CLEAN / DIRTY
WORKTREE_AFTER: CLEAN / DIRTY

COMPILE_DEBUG_KOTLIN: PASS / FAIL
JVM_TARGETED: PASS / FAIL
LINT_DEBUG: PASS / FAIL
ASSEMBLE_DEBUG: PASS / FAIL
ASSEMBLE_ANDROID_TEST: PASS / FAIL / BLOCKED_BASELINE
VISUAL_A_PAGE: PASS / FAIL
VISUAL_B_PAGE: PASS / FAIL
VISUAL_DETAIL: PASS / FAIL
RAW_DOMAIN_TAGS_HIDDEN: PASS / FAIL
MANUAL_START_NEW_BRIDGE: PASS / FAIL
MANUAL_ADD_CURRENT_BRIDGE: PASS / FAIL
RECENT_USE_SEMANTICS: PASS / FAIL
MODEL_CALL: PASS / FAIL / BLOCKED_ENVIRONMENT / NOT_RUN
UI01_NAV_DEPENDENCY: PRESENT / RESOLVED

FIRST_UI02_FAILURE_COMMAND: <none or command>
BASELINE_BLOCKER: <none or IssueExecutionStopAvailabilityTest.kt:49>
ERROR_FILE_LINE: <none or path:line>
KEY_ERROR: <最多8行>
VISUAL_ISSUES:
- <最多5条>

DRIVE_RUN_FOLDER: <url>
SCREENSHOT_INDEX: <url>
ACCEPTANCE_SUMMARY: <url>
RUNTIME_SCREENSHOT_COUNT: <n>
UPLOAD_ERRORS: none / <简述>
```

最后再次执行：

```powershell
git status --short
```

必须保持 clean。