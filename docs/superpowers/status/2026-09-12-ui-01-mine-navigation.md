# UI-01《我的 B + 四项一级导航》验收状态

> 更新日期：2026-09-12
>
> 分支：`codex/ui-01-mine-navigation`
>
> PR：[#61](https://github.com/elio-zwd/AI-Skill-Roundtable/pull/61)（Open + Draft）
>
> 代码提交：`98dfbb3` `feat: 实现我的页与四项一级导航`

## 当前结论

UI-01 的核心范围已完成，现有 PR #61 内容达到 merge-ready。四项根导航、Mine B、Issue 内部图、唯一 Root BottomNav、canonical UserAvatar、真实能力入口和不可用能力标记均已落地；UI-03 未开始。

本批次继续遵守“只保留一个 Draft PR”的约束，不创建额外 PR，也不在本状态收口中扩大到 UI-03。

## 完成映射

- [x] Root BottomNav 固定为 `对话 / 角色 / 资料 / 我的`，`ISSUES` 仅保留为内部 Issue 图入口。
- [x] Issue root、detail、Execution、deep link、`stageId` 和返回链保留；外部 Issue 深链先进入内部根页，再进入详情，避免 Navigation 默认合成栈跳过根页。
- [x] 删除 `DialogBottomBar`、`NavigateBottomTab`、`onNavigateBottomTab` 和旧 numeric bridge，Dialog 不再拥有第二套底栏。
- [x] 新增公共 `UserAvatar`，Mine Hero 与 Dialog 用户消息统一使用 `R.drawable.avatar_user`；没有头像目录、持久化或自定义图片源。
- [x] Mine B 已接入个人背景数据投影、当前模型、可用 Key 数量和遥测状态；读取失败显示明确状态，不伪造成功。
- [x] 设置、AI 管理、遥测与诊断进入现有真实页面；数据与隐私、备份与恢复、关于见域和个人背景查看/编辑按当前产品决策显示为不可用，不添加假页面。
- [x] 根导航、二级页返回、Activity 重建、Issue 深链和 UI-02 角色/对话回归测试已补齐。

## 验证证据

### AGY MCP

AGY 只负责执行昂贵验证并回传压缩证据，未修改源码：

- `jianyu_compile_test`：PASS，task `task-814b392706204bae94912bfd376dd49f`，exit code 0，0 errors，26 warnings，约 89 秒。
- `jianyu_lint_assemble`：PASS，task `task-d70d39505f8445adbc43be4c9ac82b8d`，exit code 0，0 errors，19 warnings，约 239 秒。

### 本地设备与构建

- `pwsh.exe -NoProfile -Command "& .\\gradlew.bat --no-daemon :app:assembleDebugAndroidTest"`：PASS，`BUILD SUCCESSFUL`。
- 定向 `connectedDebugAndroidTest`：PASS，`20/20`，0 failed；设备为 `emulator-5554`，分辨率 `1080×2400`。
- 设备 UI：PASS。四项根导航、Mine 页面、设置/AI 管理/遥测入口、禁用状态、Activity 重建和返回链均已通过 UI dump/截图核对。
- 外部 URI `jianyu://issues/issue-deep-link?stageId=stage-7`：PASS，真实 Activity 能解析并进入 Issue Execution；返回后 UI dump 为内部 `issues_screen`，没有 Root BottomNav。
- Mine 视觉证据：`C:\Users\70455\.codex\visualizations\2026\09\12\ui-01-mine.png`。

AGY 的 26/19 条 warning 为编译/静态检查警告，不含错误；其中包含现有依赖或弃用提示。没有将远端 CI 状态写成本地验证结果。历史 `tools/check-app-identity.ps1` Android CI 身份门禁仍按 `PRE_EXISTING_BASELINE` 记录，本任务未修改它。

## 产品决策与合并门禁

- `AVATAR_SWITCH: BLOCKED_PRODUCT_DECISION`：当前不实现头像切换，避免伪造头像目录、存储和自定义图片源。
- `MERGE_READY: YES`
- `BLOCKERS: UI-01 核心范围无阻塞；PR #61 按目标约束保持 Draft，等待最终合入授权。`
- `EXTRA_PRS_CREATED: 0`

