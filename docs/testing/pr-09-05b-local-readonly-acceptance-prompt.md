# PR09-05B 本地 AI 严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 Draft PR #45 进行严格只读验收。

目标 PR：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable/pull/45
```

目标分支：

```text
feat/pr-09-05b-executable-skill-batch
```

## 一、只读纪律

只允许：

- 拉取远端最新代码；
- 检出 PR #45 的精确 Head；
- 读取文件、PR、Commit、CI 与测试报告；
- 构建、测试、覆盖安装和设备验收；
- 使用测试 Fake Network Gateway；
- 将原始证据保存到仓库外；
- 输出验收报告。

严格禁止：

- 修改、格式化、生成或删除仓库文件；
- 创建 Commit、推送、变基、合并或强制更新分支；
- 标记 Ready、修改 PR 状态或删除分支；
- `adb uninstall`；
- `adb shell pm clear`；
- 删除、迁移或重置现有 App 数据；
- 恢复模拟器快照覆盖现有数据；
- 使用真实 API Key；
- 调用生产模型或生产网络；
- 修改 Catalog/Skill 资产以绕过门禁；
- 使用固定坐标、中文文案、OCR 或图片模板作为 UI 主定位方式；
- 启动 PR09-08。

开始前从 PR #45 实时读取：

```text
expectedHead=<PR #45 最新 Head SHA>
expectedBase=<PR #45 当前 Base SHA>
expectedBranch=feat/pr-09-05b-executable-skill-batch
```

若检出的 Head、Base 或分支不精确匹配，立即停止并报告，不得在旧版本或猜测版本上验收。

## 二、环境记录

记录：

- 操作系统、版本和架构；
- PowerShell 版本；
- Git 版本；
- JDK 版本；
- Gradle 版本；
- adb 版本；
- Python 版本；
- 设备 ID、型号和 Android API；
- 验收开始、结束时间和时区。

## 三、检出与差异门禁

```powershell
git fetch origin --prune
git checkout feat/pr-09-05b-executable-skill-batch
git pull --ff-only origin feat/pr-09-05b-executable-skill-batch

git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse origin/main
git merge-base HEAD origin/main
git merge-base --is-ancestor bc1331f10aadbe67f336c843ec1074d67170eda2 HEAD
git diff --name-status origin/main...HEAD
git diff --check origin/main...HEAD
git diff --exit-code
```

要求：

- 分支名精确匹配；
- HEAD 精确等于 `expectedHead`；
- Base 精确等于 PR #45 当前 Base；
- 初始工作区完全干净；
- 不存在与其他开放 PR 的重叠修改；
- 差异只涉及 PR09-05B 的 Catalog 执行发布、Skill 资产、资格门禁、Runtime/Resolver 接线、测试和文档。

必须确认以下文件没有变化：

```text
app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt
app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt
app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionRunCoordinator.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionStateMachine.kt
app/src/main/java/com/elio/jianyu/execution/ExecutionBudgetPolicy.kt
app/src/main/java/com/elio/jianyu/data/MaterialContextRepositoryComponent.kt
app/src/main/java/com/elio/jianyu/ui/App.kt
tools/device/
tools/local-verification/
```

执行：

```powershell
git diff --name-only origin/main...HEAD -- app/schemas
git diff --name-only origin/main...HEAD -- app/src/main/java/com/elio/jianyu/data
git diff --name-only origin/main...HEAD -- tools/device tools/local-verification
```

上述命令不得显示 Schema、数据库、DAO、Migration 或工具修改。Room 必须仍为 v9，不得出现 `10.json`。

## 四、仓库外低 Token 证据

先阅读：

```text
tools/local-verification/AGENTS.md
docs/testing/local-verification-evidence-protocol.md
docs/testing/local-read-only-acceptance-template.md
```

建立仓库外证据目录：

```powershell
$expectedHead = git rev-parse HEAD
$evidenceRoot = Join-Path $env:TEMP "jianyu-pr-09-05b-$expectedHead"
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
```

所有原始 stdout/stderr、JUnit XML、截图、UI XML、JSON、APK Hash 和设备 profile 必须位于 `$evidenceRoot` 或其他明确的仓库外目录，不得提交。

所有 Gradle 步骤使用：

```text
tools/local-verification/Invoke-LocalVerification.ps1
```

遵循：

```text
docs/testing/local-verification-evidence-protocol.md
```

成功时只输出摘要、步骤 JSON、JUnit 统计和日志 SHA-256；失败时只读取有界失败摘录。禁止把完整成功日志粘贴给远端 AI。

## 五、静态 Catalog 与资产审计

核对：

```text
app/src/main/assets/official_skill_catalog_v1.json
app/src/main/assets/official_skill_execution_batch_v1.json
app/src/main/assets/skills/study-planner/SKILL.md
app/src/main/assets/skills/meeting-to-action/SKILL.md
app/src/main/assets/skills/report-proposal-writer/SKILL.md
app/src/main/assets/skills/research-fact-checker/SKILL.md
```

必须精确统计并报告：

```text
基础 Catalog 总数 = 44
基础 Catalog executable 数量 = 0
有效生产 Catalog 总数 = 44
有效生产 Catalog executable 数量 = 4
本 PR 新增 executable 数量 = 4
本 PR 新增 SKILL.md 数量 = 4
```

四项正式 ID 必须精确为：

```text
study-planner
meeting-to-action
report-proposal-writer
research-fact-checker
```

验证：

- 没有第 45 项；
- 44 项正式 ID、`defaultOrder` 和基础 Catalog 历史状态未被改写；
- 四项 ID 分别保持 order 23、29、30、33；
- 四项均为非人物型；
- 四项均不是 `HIGH_STAKES` 或 `URGENT`；
- 四项有效状态均为 `PUBLISHABLE`；
- 四项有效来源均为 `VERIFIED_IMPLEMENTATION_SOURCE`；
- 四项 `nonExecutableReason == null`；
- 四项 `hasAsset/discoverable/searchable/recommendable/executable == true`；
- 其他 40 项仍为 `executable=false` 并保留准确阻断原因；
- `office-document-productivity` 与 `original-expression-naturalizer` 仍保持 `BLOCKED_REWORK`；
- 人物型 Skill 未被发布；
- `academic-ai-evasion` 未成为正式 ID；
- `original-expression-naturalizer` 的诚信边界未降低。

逐项检查四个 `assetPath`：

- 必须是相对 `skills/<directory>/SKILL.md`；
- 不含绝对路径、盘符、反斜杠、空段、`.` 或 `..`；
- 文件真实存在于 APK assets；
- UTF-8 可读；
- 非空；
- 不引用开发机本地路径或不存在的附属文件；
- 不包含 API Key、Token、第三方完整 Prompt 或恶意系统提示覆盖指令。

每项 `SKILL.md` 必须包含：

```text
角色与目标
适用场景
输入要求
执行步骤
输出结构
事实与来源规则
资料与个人背景边界
联网规则
风险与限制
不得执行的行为
```

检查正文明确禁止：虚构事实或来源、未授权个人信息、越权联网、绕过 Context Gate、自动发送/提交、冒充现实人物或组织、高风险最终裁决。

## 六、全量构建与测试

按顺序实际执行并记录退出码、耗时、JUnit 统计、步骤 JSON 和日志 SHA-256：

```text
:app:compileDebugKotlin
:app:testDebugUnitTest
:app:lintDebug
:app:assembleDebug
:app:assembleRelease
:app:assembleDebugAndroidTest
:app:connectedDebugAndroidTest
```

首轮不默认使用 `--stacktrace`。JUnit XML 缺失、损坏或零测试时必须标记 `NOT_VERIFIED`。

全量 JVM XML 必须包含并通过：

```text
OfficialSkillExecutionEligibilityTest
OfficialSkillExecutableBatchTest
HomeExecutableSkillIntegrationTest
OfficialSkillCatalogManifestTest
ExecutionRunCoordinatorTest
```

全量 Instrumentation XML 必须包含并通过：

```text
OfficialCatalogExecutionSkillResolverIntegrationTest
ExecutableSkillCoordinatorIntegrationTest
```

若全量测试已证明目标类运行且全部通过，不得重复运行相同定向测试。只有失败、缺失或 XML 不完整时才定向重跑。

必须真实报告：

- JVM tests / failures / errors / skipped；
- Instrumentation tests / failures / errors / skipped；
- AndroidTest APK 是否真实生成；
- Debug/Release APK 路径与 SHA-256；
- Lint error/warning 统计；
- R8/Release 状态；
- Room Schema 当前性。

## 七、Resolver 与 Coordinator 真实性验收

必须证明以下测试实际使用：

```text
真实 official_skill_catalog_v1.json
真实 official_skill_execution_batch_v1.json
真实 APK assets SKILL.md
OfficialCatalogExecutionSkillResolver
ExecutionContextBuilder
Fake ExecutionNetworkGateway
内存测试 ExecutionPersistenceGateway
ExecutionRunCoordinator
```

单 Skill 至少验证：

```text
study-planner
→ 执行资格审计
→ Resolver
→ 非空 System Prompt
→ Participant Snapshot
→ Fake Gateway 流式更新
→ 单条完成 Message
→ Run SUCCEEDED
```

多 Skill 至少验证：

```text
research-fact-checker
report-proposal-writer
→ position 0/1 稳定
→ 职责冻结
→ 两位独立调用
→ 两条独立 Message
→ 无重复成员
→ Run SUCCEEDED
```

同时确认现有测试仍覆盖：

- 重复 Skill 拒绝；
- 未知 Skill 拒绝；
- 不可执行 Skill 拒绝；
- 缺失资产/空 Prompt 拒绝；
- 无 Key；
- 离线；
- Provider 失败；
- 部分失败；
- 全员失败；
- 用户停止；
- 迟到回调；
- 预算不足；
- Context Gate 失败。

所有网络测试必须为 Fake，不得读取真实 Key 或调用生产模型。

## 八、首页生产路径

通过 JVM、Instrumentation 和外部设备语义工具验证：

1. 生产 Catalog 能发现至少一个正式可执行 Skill；
2. 单方向问题能选择一个真实可执行成员；
3. 双方向问题能选择至少两个不同的真实可执行成员；
4. 不可执行候选仍可查看但不能进入最终启动；
5. 首页生产路径不依赖 Fake Catalog；
6. 首页生产代码没有硬编码本批四个 ID；
7. 双方向仍只创建一条 Issue/Stage 主线；
8. Catalog 页面正确显示“可执行”状态；
9. Skill 详情开始入口只对正式可执行成员开放；
10. 收藏、浏览、查看详情不会被记录为实际使用。

## 九、覆盖安装与设备语义验收

只允许：

```powershell
adb install -r <当前精确 Head 构建的 debug APK>
```

安装前记录 APK 路径与 SHA-256。不得卸载或清数据。

阅读：

```text
tools/device/AGENTS.md
docs/testing/pr-b-ui-automation-local-acceptance.md
app/src/main/java/com/elio/jianyu/ui/automation/JianyuAutomationTags.kt
```

使用：

```text
tools/device/cli.py
```

按正式稳定标签执行：

```text
doctor
launch
assert
find
tap
wait
```

主路径：

```text
强停启动首页
→ assert home_screen
→ find/tap 正式示例问题标签
→ assert home_question_input
→ tap home_recommendation_request_button
→ wait/assert home_recommendation_result
→ 确认结果至少包含一个正式可执行 Skill
→ tap home_recommendation_confirm_button
→ 进入 context_confirmation_dialog
→ 完成空选择或明确选择的用户确认
→ assert home_final_review
```

要求：

- 主定位使用 `--by tag`；
- 不使用固定坐标或中文名称作为身份；
- 不恢复 `home_question_placeholder`；
- 标签不得包含问题正文、Skill 正文、资料正文或个人背景；
- `testTagsAsResourceId = true` 保持有效；
- 外部语义路径停在最终确认，不点击生产模型启动；
- 真实执行闭环只由 Fake Network Instrumentation 验证。

保留并验证以下冻结标签：

```text
home_screen
home_question_input
home_recommendation_request_button
home_recommendation_result
home_recommendation_confirm_button
home_final_review
issue_execution_screen
```

## 十、数据、隐私与历史冻结

确认：

- Room 仍为 v9；
- 没有新增 Entity、DAO、Migration 或 `10.json`；
- 新 Catalog/Skill 资产属于版本化 APK assets，不写入用户数据库；
- 历史 Run 继续使用 Participant Snapshot；
- 当前 Catalog 变化不改写历史 `systemPrompt`、ID、职责或 position；
- 未经用户确认的资料和个人背景不会进入请求；
- 任何错误、日志、自动化标签和证据文件名不包含 Skill 正文、问题正文、资料正文、个人背景或 API Key；
- `git grep` 和 Secret scan 无硬编码生产密钥。

## 十一、GitHub CI 核验

读取 PR #45 最新 Head 对应的：

- Secret scan；
- Android CI；
- Android UI Test Compile；
- Review Thread；
- mergeability。

必须区分：

```text
本地实际执行并通过
GitHub CI 已通过
仅完成静态检查
尚未验证
```

GitHub CI 绿色不得写成设备 Instrumentation 已通过；`connectedDebugAndroidTest` 只能由本地真实执行或明确的设备 CI 证明。

## 十二、收尾门禁

```powershell
.\gradlew.bat --stop
git status --short
git diff --exit-code
git rev-parse HEAD
```

要求：

- 工作区完全干净；
- Head 仍精确等于 `expectedHead`；
- 未创建 Commit、未推送、未变基、未合并、未修改 PR Draft 状态；
- App 数据未清除；
- 没有使用真实 API Key 或生产网络。

## 十三、最终报告格式

输出：

1. 最终结论：`PASS` / `FAIL` / `NOT_VERIFIED`；
2. PR 链接、Base、Branch、精确 Head；
3. 环境与时间；
4. 只读纪律与工作区状态；
5. 修改文件范围审计；
6. Room v9 与 Schema 结果；
7. Catalog 总数、基础/有效 executable 数量；
8. 四项正式可执行 Skill 与资产路径；
9. 未发布候选及原因；
10. 执行资格门禁结果；
11. JVM 统计和目标测试类；
12. AndroidTest APK 编译结果；
13. Instrumentation 统计和目标测试类；
14. 真实 Resolver 单/多 Skill 结果；
15. Fake Network Coordinator 单/多 Skill 结果；
16. 首页生产推荐与最终确认结果；
17. 设备语义标签路径；
18. Secret scan、Android CI、Android UI Test Compile；
19. 隐私与敏感信息检查；
20. 尚未验证项；
21. 已知风险和重点回归区域；
22. 收尾工作区和 Head 复核。

失败时必须提供：

- 精确失败命令；
- 退出码；
- 失败测试身份；
- 有界日志摘录；
- 复现步骤；
- 可能原因；
- 不得修改代码尝试修复。
