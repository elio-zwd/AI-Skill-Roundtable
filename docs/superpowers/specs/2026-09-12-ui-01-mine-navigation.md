# UI-01《我的 B + 四项一级导航》Implementation Spec

> 状态：已批准，作为 `codex/ui-01-mine-navigation` 唯一 PR 的实现合同。
>
> 依据：ADR-009、`docs/product/` 当前产品规范、已选定的「我的」B 方案图片，以及 UI-01 总规划。

## 1. 目标

把见域的一级导航收敛为四项：

```text
对话 / 角色 / 资料 / 我的
```

新增「我的」主页面 B：用个人背景 Hero 作为首屏主体，提供模型与 Key、数据、备份、遥测和应用偏好的入口。所有页面继续使用现有 Route → Screen → Components 分层；本批次不重做 UI-02 角色页面和 UI-03 之后的页面。

## 2. 用户可见合同

### 2.1 Root Bottom Navigation

- App 根 `Scaffold` 是唯一一级底栏所有者。
- 四项顺序固定为 `对话`、`角色`、`资料`、`我的`，四等分、选中态清晰、支持无障碍语义和稳定 testTag。
- 内部 `AppDestination` 可继续使用 `HOME`、`SKILLS`、`RESOURCES`、新增 `MINE`；不为文案改名整个代码架构。
- `HOME` 是对话根页并显示根底栏；设置、AI 管理、遥测等二级页不显示根底栏。
- `ISSUES` 不再是 top-level destination，不出现在根底栏，但仍是内部可达的 Issue graph。

### 2.2 Issue 内部导航

保留 Issue root、detail、Execution、deep link、`stageId` 和返回链。`navigateToIssue` 不得再依赖「ISSUES 必须是 top-level」这一假设；应使用独立的内部 graph parent 进入 Issue root，再打开 detail。不得删除 Issue 子系统或其执行能力。

### 2.3 对话底栏

删除前先由测试保护调用链，确认根底栏已承担导航后，退役旧的 `DialogBottomBar`、`DialogEvent.NavigateBottomTab`、`DialogRoute.onNavigateBottomTab` 和 App 中对应的 numeric bridge。Dialog 仍保留会话、角色、消息、输入和所有已验证交互。

### 2.4 UserAvatar

- Mine Hero 与 Dialog 用户消息统一调用公共 `UserAvatar`。
- UI-01 core 只使用现有 `R.drawable.avatar_user` 作为确定性 fallback。
- 不新增头像目录、选择器数据模型、持久化协议、图片权限或自定义图片源。
- 头像切换入口若因缺少产品合同不可实现，必须明确为 unavailable；不能把静态头像展示写成切换已完成。

### 2.5 「我的」B 页面

页面遵循已选定图片 `docs/product/重构/UI界面/我的/参考图片/02-我的主页面-B-个人控制台-选定.png` 和共享视觉基线：

- 顶部居中显示「我的」，右侧设置图标；不显示账号、昵称、会员或登录状态。
- 浅薰衣草弱氛围的个人背景 Hero：标题、非敏感说明、真实个人背景数量状态、摘要 Chip；不在主页面展开背景正文。
- Hero 的查看/编辑动作在 UI-01 不新增个人背景正式页面；缺少稳定直接页面时须显示清晰 unavailable 状态。
- 快捷控制为两列四张同权卡：`模型与 API Key`、`数据与隐私`、`备份与恢复`、`遥测与诊断`。
- `设置`、`关于见域`位于「应用偏好」分组；缺失页面必须显示 unavailable，而非空回调或假成功。
- 不实现登录、会员、用户名、云账号、Profile backend 或未批准的账户系统。

## 3. 真实能力与缺口

| 入口 | UI-01 行为 |
| --- | --- |
| 设置 | 进入现有 `SettingsRoute`，保留应用偏好及其真实 AI 管理/遥测入口 |
| AI 管理 | 进入现有 `AiManagementRoute`，使用当前 BYOK Key 池和模型配置 |
| 遥测与诊断 | 进入现有 `TelemetryRoute`，使用当前遥测状态和确认流程 |
| 个人背景正式页 | UI-06 负责；UI-01 只投影真实数量/状态并标记直接编辑入口 unavailable |
| 数据与隐私 | 当前没有可交付的稳定 runtime 页面，标记 unavailable，不实现删除/导出流程 |
| 备份与恢复 | 当前没有可交付的 runtime 页面，标记 unavailable，不伪造备份文件或恢复成功 |
| 关于见域 | 当前没有稳定页面实现，标记 unavailable，不写假版本/许可页面 |

所有状态投影必须来自现有 repository、AI 配置或 Telemetry 状态；无法读取时使用明确的错误/不可用文案，不以静态示例数据冒充用户数据。

## 4. 非目标

- 不修改 Gemini、Room、Key 加密、遥测协议或执行编排。
- 不删除旧 Official Skill 组合数据能力；不重做 UI-02 A/B/详情。
- 不升级依赖，不修 `check-app-identity.ps1` 历史 baseline。
- 不开始 UI-03、UI-04 或其他产品重构。

## 5. 验收合同

- JVM：导航 destination contract、Issue parent/back/deep-link contract、Mine 状态投影和可用性 reducer 有聚焦测试。
- Android UI：根底栏恰好四项，标签和选中态正确；Mine B 的 Hero、四张快捷卡、设置分组和 unavailable 语义可见；Dialog 不再有第二套底栏；`UserAvatar` 在 Mine/Dialog 共用。
- 回归：UI-02 角色目录、详情、Dialog、Issue root/detail/Execution/deep link/stageId/back、设置返回和 activity/process restoration。
- 视觉：Xiaomi 14 Ultra、Android 竖屏、1440×3200、zh-CN；截图/设备证据写入仓库外目录。
- 收口必须报告 `AVATAR_SWITCH: BLOCKED_PRODUCT_DECISION`，除非后续有明确头像来源与持久化合同或用户明确延期该功能。
