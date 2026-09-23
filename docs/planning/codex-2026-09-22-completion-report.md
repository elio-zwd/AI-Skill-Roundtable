# 见域 PR #66 当前完成度与验收交接

更新时间：2026-09-23（UTC）
PR：#66 `feat: 完成见域 UI 收口与正式备份`
分支：`codex/ui-05-artifacts`
PR 状态：Draft，未合并
Base：`main@53321b6b26d7084e97be027fe1098bcb1fe403c5`
当前生产/测试修复 Head：`e84c503d264e7bfc2f6c49c3f1c0bdc6ef3ed88d`

> 本报告与复验 Prompt 会形成一个后续 docs-only 提交，因此 PR 的最终 Head 会比上面的代码修复 Head 更新。复验时必须以 PR #66 当时的精确 Head 为唯一目标，并确认该 Head 包含 `e84c503d...`。

## 1. 当前范围

PR #66 当前集中完成并收口：

1. UI-03：Skill 角色搜索、筛选、收藏、最近使用与二级发现页。
2. UI-04：资料总览、文本/链接/文件、DOCX、本地 PDF 明确失败边界、搜索、详情与生命周期。
3. UI-05：成果保存、筛选、Markdown、来源追溯与来源 Issue/Stage 回跳。
4. UI-06：个人背景新增/编辑/停用/删除、敏感列表脱敏、跨议题候选但默认不发送。
5. UI-07：主题、字号、内容密度、减少动效、高对比度、消息时间、敏感额外提醒、AI 管理与关于页。
6. UI-08：数据概览、可读导出、正式 Portable Backup、Device Snapshot、删除全部数据。
7. UI-09：持续对话与正式 Issue/Stage、资料、个人背景、成果闭环。
8. Runtime：Room 闭库/重开、generation 切换、ViewModelStore 重建、旧 DAO/旧 ViewModel 失效。
9. PR09-13B：Argon2id、AES-GCM、Tink Streaming AEAD、确定性 CBOR、认证 EOF、白名单 Mapper、Snapshot 和全局备份门禁。

PR09-14A/14B 的 Portable 正式导入、差异/冲突预览与数据库原子替换仍不属于本 PR。

## 2. 本次接手后的新增修复

在既有 UI-03～UI-09 和 PR09-13B 实现之上，本轮又确认并修复了以下问题：

- `79186c3`：修复“本次参考内容”错误回退到 pending（下一请求）选择；展示只读 active context。
- `01707be`：收紧 Device Snapshot 的 `PRAGMA wal_checkpoint(TRUNCATE)` 结果校验；没有结果行不再误判成功，必须返回预期列且第一列为 `SQLITE_OK(0)`。
- `f1a7b8d`：active context 在真实请求结束后保留，供用户查看最近一次实际使用内容；下一次请求成功准备时替换/清空。删除会话时同步清除该 session 的 pending、active、显式确认和 formal context，避免敏感摘录留在 ViewModel 内存。
- `6d95047`：Snapshot Catalog 不再依赖 `File.renameTo()` 覆盖已有 `index.json`；改为 `Files.move(..., REPLACE_EXISTING, ATOMIC_MOVE)`，不支持原子移动时安全降级，并新增已有索引替换回归测试。
- `a81260d`：让 UI-07“减少动效”真实作用于自定义 shimmer、pulse 与 bounce 动画，而不只是把偏好写进 CompositionLocal。
- `681aaf2`：可读 JSON 导出改为临时 SAF 文档 → 完整写入/fsync → 回读字节校验 → 最终改名；Repository/写入/校验失败时删除临时文档，不再留下空的或不完整的正式导出文件。
- `e84c503`：根据本地全量 Instrumentation 的 3 个稳定失败逐项复核根因后修复：
  - Settings 导航测试对滚动容器中的 AI 管理按钮先 `performScrollTo()` 再点击；生产 `SettingsScreen → onOpenAiManagement → App navigation` 接线本身未改。
  - UI Automation Navigation 不再要求资料页出现已废弃的个人背景子库，而是验证资料库后回到【我的】并通过正式“个人背景”入口进入 `PersonalContextRoute`。
  - 成果筛选取消测试不再用列表卡本身会合法出现的“通用阶段总结”文案判断 Sheet 是否存在；为成果类型 FilterChip 增加稳定 testTag，继续严格断言取消后 type/history 两个 apply callback 均为 0。

此前已经完成、且本轮确认未回退的关键修复仍包括：

- UI-09 资料必须匹配当前正式 Issue；Stage 资料必须匹配当前 Stage。
- 个人背景允许跨议题复用，但每次请求默认未授权。
- 保存 `expectedSourceHash`、`expectedSourceUpdatedAt`、用户编辑后的实际摘录、本次联网授权和敏感逐次确认。
- 模型执行前 Repository 重新读取并校验来源。
- `prepareExecutionContext` 与 legacy Dialog `runId=null` Usage Snapshot 在同一 Room 事务中完成。
- UI/ViewModel 不自行构造 Usage Entity；Dialog 没有第二套 Usage Writer。
- prepare/usage 写入失败不消费 pending、不调用模型、不静默丢来源。
- retry 不继承上一请求授权；点击失败重试前必须重新确认，也允许明确确认空选择后 retry。
- `confirmationOrder` 使用真实用户勾选顺序；取消后重新选择获得新的实际顺序；重新打开 pending 确认保留原顺序。
- 24,000 字符门禁把真正加载的 `SKILL.md` 与 thinking directive 计入 base context；不重复计算 legacy `Character.systemPrompt`。
- 敏感内容永远需要逐请求确认；“显示敏感资料发送提醒”只控制额外文案。
- Portable/Snapshot 失败路径回滚临时和未发布正式文件；Snapshot after-reopen/Catalog 发布失败不保留孤儿正式 `.jysnap`。
- 删除全部数据覆盖数据库、App 偏好、会话偏好、官方 Skill 偏好、BYOK、模型配置、遥测、云端交互设置、音频/cache、Device Snapshot 与 wrapping key。
- Runtime maintenance 重开成功并健康检查后才发布新 generation；UI ViewModelStore 按 generation 清空重建。

## 3. GPT 定向代码自审结论

本轮按 PR #66 当前 diff 定向审查了 UI-03～UI-09、备份、数据隐私和 Runtime 的高风险链路。

已核对：

- UI-03 根页搜索是只读跳转入口，真实搜索在独立二级页；根 Route 的旧 `SearchChanged` no-op 不会吞真实输入。
- UI-03 最近使用清除存在二次确认，且只清 recent，不删除收藏、会话或历史消息。
- UI-04 文件读取不会把 PDF 二进制伪装成文本；DOCX 有真实本地解析；PDF 返回明确不支持边界。
- UI-05 成果确认取消不会创建正式成果；成果库使用持久化来源关系，不从正文猜测；来源回跳携带 Issue + Stage。
- UI-06 敏感个人背景在列表脱敏；编辑时才显示正文；停用/删除使用正式生命周期接口。
- UI-07 主题、字号、密度、高对比度、消息时间和减少动效均有真实消费方。
- UI-08 可读导出、Portable 和 Snapshot 均按失败关闭处理；删除全部数据的清理范围与 UI 文案一致。
- UI-09 没有发现第二条 Dialog Usage 写入路径；pending/active/retry 授权隔离、敏感逐次确认、来源变更拒绝和确认顺序均有对应实现。
- Runtime 闭库后不继续持有旧 Repository/DAO；generation 更新后清空旧 ViewModelStore。
- PR 文件范围集中在 UI-03～UI-09、备份/Runtime、对应测试/文档和身份门禁；未发现需要从 PR 剔除的明显无关业务修改。

在上述清单内，GPT 静态自审当前没有保留一个已知的重大未解决代码问题。

## 4. GitHub 远端验证事实

### 4.1 交接 Head `5b82271b218d283f99e897804a3c3b07cf0be0cb`

已重新查询并确认：

- Secret scan：PASS。
- Android UI Test Compile：PASS。
- Android CI run `35841239780`：PASS。

该 Android CI 的 build job 已成功经过：

- static app identity gate；
- debug Kotlin compile；
- debug JVM unit tests；
- debug lint；
- debug APK；
- package migration / schema / debug APK verification；
- ephemeral release signing setup/validation/cleanup；
- optimized release APK；
- release package / R8 / unsigned artifact verification；
- committed Room schema verification；
- generated Room schema upload；
- test/lint/R8 reports upload；
- debug APK upload；
- release APK upload。

`legacy-apk` 与 `migration-tests` 只在手工 `workflow_dispatch` 条件下运行，因此普通 PR push 下为 skipped；这不代表 Instrumentation 已执行。

### 4.2 本地 AI 首轮最终验收：`d0bafd7f957ca1d3e3795d568226cee8419d1c72`

用户本地 AI 已在 Windows 10 x64、JVM 17.0.19、Gradle 8.14、`emulator-5554`（SDK 28）上严格只读执行最终验收。

已实际通过：

- app identity gate；
- 全历史 Secret scan；
- `:app:compileDebugKotlin`；
- `:app:testDebugUnitTest`：568 passed / 0 failed / 0 skipped；
- `:app:lintDebug`；
- `:app:assembleDebug`；
- `:app:assembleRelease` + R8；
- `:app:assembleDebugAndroidTest`；
- UI-03、UI-04、UI-06、UI-07、UI-08、UI-09；
- Portable Backup、Device Snapshot、Runtime reopen、Privacy。

全量 Instrumentation 共 61 个测试类 / 251 项：248 PASS，3 FAIL。三个失败均 100% 稳定复现：

1. `JianyuNavigationShellScreenTest.settings_preservesAiManagementTelemetryAndBackCallbacks`：
   对滚动容器内 AI 管理按钮未先滚动到可触摸区域，回调计数为 0。
2. `JianyuUiAutomationNavigationTest.resourcesLibrary_exposesMaterialsAndPersonalContextContentRoots`：
   测试仍按旧 UI 要求资料页包含个人背景子库，与 UI-06 已批准“个人背景在【我的】管理”的当前产品结构冲突。
3. `ArtifactLibraryComponentsTest.filtersOpenInSheetAndCancelDoesNotApplyDraftChanges`：
   测试用“通用阶段总结”文本不存在判断 Filter Sheet 尚未打开，但成果列表卡本身合法显示同一成果类型文案。

GPT 已逐项复核为测试定位/过期契约问题，而非对应生产行为失效，并在 `e84c503d264e7bfc2f6c49c3f1c0bdc6ef3ed88d` 修复。没有删除测试、降低业务断言或吞异常。

### 4.3 修复后 Head `e84c503d264e7bfc2f6c49c3f1c0bdc6ef3ed88d`

更新本报告时：

- Secret scan run `35859330017`：PASS。
- Android CI run `35859329868`：IN PROGRESS。
- Android UI Test Compile run `35859329869`：IN PROGRESS。

最终编译和设备验收仍按用户要求交给本地 AI；上述运行中的 GitHub workflow 只作为辅助证据，不替代本地复验。

## 5. 尚未执行/尚未完成的验证

当前仍需要：

- 在包含 `e84c503...` 的 PR #66 最新 Head 上重新执行 Android 编译/测试门禁。
- 定向重跑上述 3 个失败 Instrumentation。
- 再跑全量 `connectedDebugAndroidTest`，确认 251 项或当前测试总数无失败。
- TalkBack 真实屏幕朗读手势：仍需配置 TTS 的真机人工走查。
- 360dp 与 200% 字号人工视觉走查：自动化语义不能替代视觉审核。
- PR09-13B 独立安全审查、跨版本向量、依赖许可登记、受限设备性能：尚未完成。
- PR09-14A/14B：尚未实现。

## 6. 最终本地只读验收

最终验收 Prompt 已保存：

`docs/testing/pr-66-local-readonly-final-acceptance-prompt.md`

本轮 3 个 Instrumentation 修复后的复验 Prompt：

`docs/testing/pr-66-local-readonly-retest-after-ui-test-fixes.md`

本地 AI 必须：

- 严格只读仓库；
- 不修改、不自动修复、不 commit、不 push、不 merge、不改变 Draft 状态；
- 以 PR #66 当前精确 Head 为唯一目标；
- 执行 Gradle/JVM/Lint/Debug/Release/AndroidTest APK；
- 有设备时执行全量 Instrumentation 和指定 UI/Runtime/备份路径；
- 使用 Fake/Test Network，不使用真实生产 API Key；
- 失败只返回命令、文件/行号、第一条关键错误、最小复现步骤和必要日志，不回传大量正常日志。

## 7. PR 最终状态边界

PR #66 继续保持 Draft，不自动 merge。

在本地只读验收给出 PASS（或所有 FAIL 经 GPT 修复并重新验证）之前，不宣布 PR 完成，也不建议自动改成 Ready。

若本地验收通过且 PR 当前 Head 没有新的未解释失败，则可由用户人工决定是否将 PR #66 标记 Ready / Merge。
