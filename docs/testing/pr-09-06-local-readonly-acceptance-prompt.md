# PR09-06 本地 AI 严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 Draft PR #44 进行严格只读验收。

## 一、只读纪律

只允许：

- 拉取远端最新代码；
- 检出 PR 分支；
- 读取文件；
- 构建、测试和设备验收；
- 将证据保存到仓库外；
- 输出报告。

禁止：

- 修改或格式化任何文件；
- 创建 Commit；
- 推送、变基或合并；
- 修改 PR 状态；
- 卸载 App；
- `adb shell pm clear`；
- 删除 App 数据；
- 恢复模拟器快照覆盖现有数据；
- 使用生产网络或真实 API Key；
- 固定坐标、中文文案、OCR 或图片模板作为首页主定位方式。

验收开始前从 PR #44 读取并记录：

```text
expectedHead=<PR #44 最新 Head SHA>
expectedBase=<PR #44 当前 Base SHA>
branch=feat/pr-09-06-home-recommendation
```

如果检出的 Head 与 `expectedHead` 不一致，立即停止并报告，不得在旧版本上验收。

## 二、环境记录

记录：

- 操作系统和版本；
- PowerShell 版本；
- Git 版本；
- JDK 版本；
- Gradle 版本；
- adb 版本；
- 设备 ID、型号、Android API；
- 验收开始和结束时间。

## 三、检出门禁

```powershell
git fetch origin --prune
git checkout feat/pr-09-06-home-recommendation
git pull --ff-only origin feat/pr-09-06-home-recommendation

git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse origin/main
git merge-base HEAD origin/main
git diff --name-status origin/main...HEAD
git diff --check origin/main...HEAD
git diff --exit-code
```

要求：

- 分支名精确匹配；
- HEAD 精确匹配 `expectedHead`；
- 初始工作区干净；
- 差异范围只包含 PR09-06 首页、推荐、测试、标签和文档；
- 不得修改 Database、DAO、Migration、Room Schema、执行状态机、预算策略、`tools/device/` 或 `tools/local-verification/`。

检查：

```powershell
git diff --name-only origin/main...HEAD -- app/schemas
git diff --name-only origin/main...HEAD -- tools/device tools/local-verification
git grep -n "home_question_placeholder" -- app/src/main app/src/test app/src/androidTest
git grep -n "testTagsAsResourceId = true" -- app/src/main/java/com/elio/jianyu/ui/App.kt
```

Room 必须保持 v9，`9.json` 不得漂移。`home_question_placeholder` 不得存在于生产或测试代码。

## 四、仓库外证据目录

```powershell
$expectedHead = git rev-parse HEAD
$evidenceRoot = Join-Path $env:TEMP "jianyu-pr-09-06-$expectedHead"
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
```

所有原始 Gradle 日志、JUnit XML、截图、UI XML、JSON 和 profile 必须位于 `$evidenceRoot` 或其他仓库外目录。证据文件名不得包含问题正文、资料正文、个人背景或 Prompt。

## 五、低 Token 验收工具

先阅读：

```text
tools/local-verification/AGENTS.md
docs/testing/local-verification-evidence-protocol.md
docs/testing/local-read-only-acceptance-template.md
```

使用：

```text
tools/local-verification/Invoke-LocalVerification.ps1
```

包装并按顺序执行：

```text
:app:compileDebugKotlin
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleRelease
:app:assembleDebugAndroidTest
:app:connectedDebugAndroidTest
```

首轮不默认使用 `--stacktrace`。成功步骤只输出摘要、JSON 证据路径、JUnit 统计和日志 SHA-256；不要把完整 Gradle 日志发给远端 AI。

JUnit XML 是测试统计主要事实：

- XML 缺失、损坏或零测试：`NOT_VERIFIED`；
- 全量测试通过且 XML 包含 PR09-06 目标测试时，不重复运行相同定向测试；
- 只有失败、缺失或统计不完整时才定向重跑，并只读取有界失败摘录。

## 六、全量构建与测试

必须实际执行并记录退出码、耗时、JUnit 统计和证据 SHA-256：

1. Kotlin Debug 编译；
2. 全量 JVM；
3. Lint；
4. Debug APK；
5. Release/R8；
6. `:app:assembleDebugAndroidTest`；
7. 全量 `connectedDebugAndroidTest`。

重点确认 JVM XML 包含：

```text
HomeWorkflowTest
HomeRecommendationPolicyTest
HomeStartCoordinatorTest
JianyuAutomationTagsTest
JianyuUiAutomationArchitectureTest
```

重点确认 Instrumentation XML 包含：

```text
HomeScreenTest
```

同时确认既有 Room v1→v9 连续迁移、Repository、ExecutionRunCoordinator、Material Context 和 UI 自动化测试没有回归，`foreign_key_check = 0`。

## 七、安装边界

只允许：

```powershell
adb install -r <由当前精确 Head 构建的 debug APK>
```

安装前记录 APK 路径和 SHA-256。不得卸载或清除 App 数据。

## 八、设备语义工具

阅读：

```text
tools/device/AGENTS.md
docs/testing/pr-b-ui-automation-local-acceptance.md
```

创建仓库外 profile 和证据目录，实际运行：

```text
doctor
launch
assert
find
tap
wait
```

首页关键操作必须使用：

```text
--by tag
```

不得使用固定坐标或中文文案作为主选择器。

### 1. 强停启动首页

```powershell
python tools/device/cli.py launch `
  --profile $profile `
  --mode force-stop `
  --expect-by tag `
  --expect-value home_screen `
  --timeout 8000 `
  --output (Join-Path $evidenceRoot '01-launch-home') `
  --json
```

### 2. 真实问题输入框

```powershell
python tools/device/cli.py assert `
  --profile $profile `
  --by tag `
  --value home_question_input `
  --output (Join-Path $evidenceRoot '02-question-input') `
  --json
```

确认 `home_question_input` 唯一，且输入内容变化不会改变标签。

### 3. 示例问题填充

先使用 `find` 查询：

```text
home_example_question_career-transition
```

再点击，并要求点击前不存在、点击后出现：

```text
home_question_clear_button
```

### 4. 双价值方向

分别点击并验证：

```text
home_direction_reality_support
home_direction_thinking_expansion
```

验证可单选、可组合，选中状态不只依赖颜色。通过 Instrumentation/Repository 事实确认双方向仍只有一个 Issue、一个初始 Stage 和一个 Run ID。

### 5. 推荐请求

```powershell
python tools/device/cli.py tap `
  --profile $profile `
  --by tag `
  --value home_recommendation_request_button `
  --expect-by tag `
  --expect-value home_recommendation_result `
  --timeout 8000 `
  --output (Join-Path $evidenceRoot '05-recommendation') `
  --json
```

该路径必须是本地 Catalog 推荐，不得调用生产网络。检查：

- 推荐来源明确是本地官方 Catalog；
- 推荐理由和职责非空；
- 风险、时效、联网、资料和预期输出可见；
- 动态 Skill 标签只含稳定 ASCII ID；
- 不可执行 Skill 不能勾选进入最终启动。

### 6. 单/多 Skill 调整

实际验证：

- 选择/移除成员；
- 切换单/多 Skill；
- 调整顺序；
- 修改职责；
- 调整后需要重新确认；
- 返回 Skill 页面后选择仍存在。

若生产 Catalog 当前没有可执行成员，外部设备路径记录 `NoExecutableSkill` 的真实行为；完整可执行调整由 Fake Instrumentation 验证，不得修改 Catalog 数据绕过门禁。

### 7. 打开上下文确认

点击：

```text
home_recommendation_confirm_button
```

预期出现：

```text
context_confirmation_dialog
```

若实现先出现 `home_context_confirmation_button`，先点击该按钮再断言 Dialog。

### 8. 上下文默认值

检查所有资料和个人背景候选：

- 默认未选；
- 默认未允许联网；
- 敏感来源默认未二次确认；
- 总字符边界为 24,000；
- 超限不静默截断；
- 取消后零 Run、零 Usage、零预算、零网络。

### 9. 确认空选择或明确选择

点击：

```text
context_confirmation_confirm
```

预期出现：

```text
home_context_confirmed_summary
home_final_review
```

确认最终页面展示问题摘要、价值方向、模式、阵容、职责、理由、风险/时效、资料/背景、联网状态、预期输出和“将创建一个 Issue/Stage 并开始模型调用”。

外部语义验收停在最终确认，不点击真实生产启动。

## 九、Instrumented Fake 启动闭环

通过自动化测试替身验证：

1. 最终确认前零网络、零 Run、零 Pending、零预算；
2. 双击推荐只调用一次；
3. 迟到推荐不覆盖新问题；
4. 推荐失败保留问题和方向；
5. Catalog 失败安全降级；
6. 无合适 Skill 与无可执行 Skill；
7. 仅保存只创建一个 Issue/Stage，零 Run/Participant/Usage/Pending/网络；
8. 保存失败保留草稿和同一稳定 ID；
9. `PreparedExecutionContext` 的 contributions 与 usage 同时传入启动命令；
10. Runtime 写入失败零 Pending、零预算、零网络；
11. 启动成功导航到精确 Issue/Stage；
12. 导航不重复启动；
13. 启动失败进入 `SavedNotStarted`；
14. 重试复用同一 Issue/Stage；
15. Activity recreate 恢复草稿但不自动推荐、保存或开始。

## 十、真实数据与幂等检查

在不清数据的前提下，以测试创建的稳定 ID 查询 Room 或通过现有 Repository 测试验证：

- 仅保存：Issue=1、Stage=1、Run=0、Participant=0、Usage=0、Pending=0；
- 双方向启动：Issue=1、Stage=1、Run≤1；
- 重复提交同 payload 返回幂等；
- 相同键不同 payload 返回冲突；
- `foreign_key_check = 0`；
- Room 版本仍为 9。

不要直接修改数据库。

## 十一、视觉与无障碍

实际检查并保存仓库外证据：

1. 360dp 窄屏；
2. 200% 字号；
3. 明亮主题；
4. 深色主题；
5. 键盘弹出后输入与主操作可达；
6. 长问题；
7. 长推荐理由；
8. 多 Skill；
9. TalkBack 朗读顺序；
10. 方向和选择状态不只靠颜色；
11. 返回栈：首页→Skill→返回、首页→Issue 工作区→返回；
12. 底部导航标签仍可见。

若某项因环境能力无法真实验证，明确写 `NOT_VERIFIED`，不得凭截图推测通过。

## 十二、隐私与安全

扫描源码、Logcat 和证据文件：

- 无问题正文日志；
- 无资料正文日志；
- 无个人背景正文日志；
- 无推荐 Prompt；
- 无 API Key；
- 自动化标签不含用户正文或中文名称；
- 证据文件名不含用户正文；
- 不读取 `.env`；
- 无硬编码生产 Key。

只回传必要的有界日志摘录，敏感内容需遮蔽。

## 十三、GitHub CI 核验

读取 PR #44 最新 Head 对应的：

- Secret scan；
- Android CI；
- Android UI Test Compile；
- Review Thread；
- 当前 mergeability。

必须区分：

- 本地实际执行并通过；
- GitHub CI 已通过；
- 仅静态检查；
- 尚未验证。

## 十四、收尾门禁

```powershell
.\gradlew.bat --stop
git status --short
git diff --exit-code
git rev-parse HEAD
```

要求：

- 工作区完全干净；
- Head 仍精确等于 `expectedHead`；
- 未创建 Commit、未推送、未变基、未合并、未修改 PR 状态；
- App 数据未被清除。

## 十五、报告格式

输出：

1. 最终结论：`PASS` / `FAIL` / `NOT_VERIFIED`；
2. PR、Base、Branch、精确 Head；
3. 环境；
4. 差异与禁止文件检查；
5. 每条构建命令、退出码、耗时、JUnit 统计、证据路径和 SHA-256；
6. 首页领域/JVM 测试结果；
7. 全量 Instrumentation 结果；
8. 外部设备语义路径；
9. 仅保存议题事实；
10. 最终启动与失败恢复事实；
11. Activity recreate；
12. 360dp、大字体、主题、TalkBack、键盘和返回栈；
13. 隐私检查；
14. CI 状态；
15. 未验证项；
16. 失败复现步骤、关键有界日志与可能根因；
17. 最终工作区与 Head 复核。

如果失败，将完整报告反馈给负责 PR #44 的远端开发对话；不要自行修复源码。
