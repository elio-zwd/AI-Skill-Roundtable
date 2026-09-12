# UI-01《我的 B + 四项一级导航》Implementation Plan

> 历史目标分支：`codex/ui-01-mine-navigation`
>
> PR #61 已于 2026-09-11 合入 `main`，merge commit：`871057b33d37396f9e560ac18887d3aa0083c6ef`。
>
> **Post-Merge 修正（2026-09-12）：** 原 Task 9 的运行证据来自 `emulator-5554 / 1080×2400`，不能勾选为 Xiaomi 14 Ultra 1440×3200 真机最终验收；Mine 摘要 Chip 也被 post-merge audit 发现仍使用静态示例。当前修复与最终设备门禁见 `2026-09-12-ui-postmerge-audit-fixes.md`。

## 执行顺序

### Task 0：刷新基线并持久化合同

- [x] 从包含 UI-02 的最新 `origin/main` 创建本分支。
- [x] 持久化本 Spec 与本 Plan。
- [x] 创建唯一 UI-01 Draft PR，并确认没有同名 branch/PR。

### Task 1：冻结四项 Root destination contract

- [x] 先更新/新增 JVM 与 Android UI 测试，固定顺序和正式文案：`对话 / 角色 / 资料 / 我的`。
- [x] 增加 `MINE` destination、route、稳定标签和根底栏选中规则。
- [x] 让 `HOME` 在 App 根 Scaffold 中显示唯一底栏。

### Task 2：迁移 Issue internal graph parent

- [x] 先保护 Issue root、detail、Execution、deep link、`stageId` 和 back chain。
- [x] 将 `navigateToIssue` 改为内部 graph parent 进入，不再把 `ISSUES` 当作 top-level。
- [x] 从 bottom navigation 移除 `ISSUES`，保留生产 caller、navigation graph 和测试覆盖。

### Task 3：建立唯一 Root BottomNav

- [x] 将底栏的所有权固定在 `App.kt` 根 `Scaffold`。
- [x] 先以测试证明根底栏覆盖四项切换，再移除 Dialog 第二套底栏。
- [x] 删除 `DialogBottomBar`、`NavigateBottomTab`、`onNavigateBottomTab` 及 App numeric bridge；同步删除失效测试，不保留兼容层。
- [x] 修正 Insets，确保 Dialog composer 不被唯一根底栏遮挡。

### Task 4：统一 canonical UserAvatar

- [x] 在 `ui/components/` 增加公共 `UserAvatar`，fallback 固定为 `R.drawable.avatar_user`。
- [x] Mine Hero 与 Dialog 用户消息改用同一组件。
- [x] 为共用组件补 Android UI 测试；不实现头像 catalog、存储或自定义图片源。

### Task 5：建立 Mine UiState / data projection

- [x] 新增 `mine/` 页面域的 Route、Screen、UiState 和必要展示组件。
- [x] 通过现有 repository 读取个人背景数量/读取错误。
- [x] 通过现有 `AiManager` 与 `TelemetryRepository` 投影当前模型、可用 Key 数量和遥测状态。
- [x] 为数据缺失/读取失败定义可读状态；不吞异常或伪造成功。
- [ ] **Post-Merge 修正门禁：** 摘要 Chip 不能使用设计图固定示例冒充用户数据。原合入版本在此项存在偏差；修复已进入 `codex/ui-postmerge-audit-fixes`，待本地验证后才能重新勾选。

### Task 6：实现 Mine B 视觉

- [x] 实现居中标题、设置入口、个人背景 Hero、摘要 Chip、2×2 快捷卡和应用偏好分组。
- [x] 使用 `MaterialTheme` 语义颜色、现有主题形状/间距和共享视觉基线；不新增页面硬编码颜色。
- [x] 个人背景主页面不展示敏感正文，正式查看/编辑入口在 UI-06 前明确 unavailable。
- [ ] **Post-Merge 修正门禁：** 快捷卡正式文案 `模型与 API Key` 与真实摘要 Chip 需在目标设备重新验收。

### Task 7：接入真实能力并标记缺口

- [x] 设置行进入现有 `SettingsRoute`。
- [x] AI 管理进入现有 `AiManagementRoute`。
- [x] 遥测与诊断进入现有 `TelemetryRoute`。
- [x] 数据与隐私、备份与恢复、关于见域使用统一 unavailable 组件/状态；不添加假页面和空操作。

### Task 8：导航恢复、无障碍与回归

- [x] 验证根底栏四项切换、二级页返回、activity/process restoration。
- [x] 验证 Issue root/detail/Execution、deep link、stageId 和 back chain。
- [x] 验证 UI-02 角色目录/详情、Dialog 和设置返回；确认没有第二个 Dialog 底栏或旧 numeric bridge。
- [x] 检查稳定 testTag、contentDescription、点击热区和中文文案。

### Task 9：最终设备 UI 验收

- [ ] 在 **Xiaomi 14 Ultra** 上完成必要 instrumentation、ADB/UI dump、截图和状态验证；历史 `emulator-5554 / 1080×2400` 不能替代该门禁。
- [ ] 以 Xiaomi 14 Ultra **1440×3200 portrait zh-CN** 作为最终视觉基准；证据写到仓库外 Google Drive。
- [x] 历史 UI-01 分支曾通过 AGY compile/test/lint/assemble；该证据不自动覆盖 post-merge 修复分支。
- [ ] 对 post-merge 修复 HEAD 重新执行必要 compile/test/assemble、回读文件与 diff 检查；仅在新鲜证据通过后收口。

## 预期文件范围

历史 UI-01 范围：

- `app/src/main/java/com/elio/jianyu/ui/App.kt`
- `app/src/main/java/com/elio/jianyu/ui/navigation/`
- `app/src/main/java/com/elio/jianyu/ui/components/UserAvatar.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/dialog/`
- `app/src/main/java/com/elio/jianyu/ui/screens/mine/`
- 受影响的导航、头像和 Mine 测试
- 本 Plan 与对应 status/PR 对账文档

当前 post-merge 修复不得扩展到 UI-03、Room Schema、Gemini transport、Gradle/依赖或历史 `check-app-identity.ps1` baseline。
