# UI-01《我的 B + 四项一级导航》历史状态与 Post-Merge 修正

> 原批次：`codex/ui-01-mine-navigation`
>
> 原 PR：[#61](https://github.com/elio-zwd/AI-Skill-Roundtable/pull/61)
>
> PR #61 已于 **2026-09-11** 合入 `main`；merge commit：`871057b33d37396f9e560ac18887d3aa0083c6ef`。
>
> 本文件保留 UI-01 当时的验收证据，但 **不再作为当前修复分支完成状态来源**。当前状态见：`docs/superpowers/status/2026-09-12-ui-postmerge-audit-fixes.md`。

## 已落入 main 的 UI-01 主体

- Root BottomNav 固定为 `对话 / 角色 / 资料 / 我的`；`ISSUES` 为内部 Issue 图入口。
- Issue root/detail/Execution/deep link/`stageId`/返回链保留。
- Dialog 第二套 BottomNav、旧数字 bridge 已退役。
- Mine 与 Dialog 共用 canonical `UserAvatar`。
- Mine 接入个人背景 repository、当前模型、可用 Key 数量与遥测状态。
- 设置、AI 管理、遥测与诊断进入已有真实页面；未实现能力保持 unavailable。
- UI-03 未包含在 UI-01 中。

## 2026-09-12 Post-Merge Audit 修正

UI-01 合入后重新审查 `4bb47dd..871057b`，确认原状态文档有两类过度结论：

1. **Mine 个人背景摘要并非完全真实投影。** 合入版本仍固定展示 `职业目标 / 可用时间 / 表达偏好` 三个示例 Chip，即使 repository 没有对应 PersonalContext；这与 UI-01 Spec“不得把设计示例冒充用户数据”冲突。
2. **快捷卡冻结文案偏移。** Spec 为 `模型与 API Key`，合入实现写成 `AI 管理`。

上述两项已在唯一修复分支 `codex/ui-postmerge-audit-fixes` 中修复：摘要 Chip 只来自 repository 返回的真实、非敏感、非空 PersonalContext title，最多 3 项；0 项时不再伪造示例 Chip；快捷卡恢复 `模型与 API Key`。

## 原批次验证证据（历史）

以下证据只证明当时 UI-01 分支/环境，不证明 post-merge 修复分支：

- AGY `jianyu_compile_test`：当时 PASS。
- AGY `jianyu_lint_assemble`：当时 PASS。
- `assembleDebugAndroidTest`：当时 PASS。
- 定向 `connectedDebugAndroidTest`：当时 20/20 PASS。
- 设备环境：`emulator-5554`，**1080×2400**。
- 当时验证了四项根导航、Mine 页面、设置/AI 管理/遥测入口、unavailable 状态、Activity 重建、Issue deep link/back 链。

### 视觉证据边界

原批次运行设备是 **1080×2400 Android Emulator**。因此原文中的“设备 UI PASS”只能解释为该 emulator 上的历史运行证据，**不能等价为 Xiaomi 14 Ultra 1440×3200 portrait zh-CN 真机最终验收**。

当前项目唯一最终视觉目标仍是：

```text
Xiaomi 14 Ultra
1440 × 3200
portrait
zh-CN
```

该目标设备的 post-merge 修复验证当前为：`NOT_RUN`，待本地 AI 只读验收。

## 当前事实

```text
PR_61: MERGED
MAIN_MERGE_COMMIT: 871057b33d37396f9e560ac18887d3aa0083c6ef
POSTMERGE_AUDIT: FOUND_UI01_SPEC_DRIFT
POSTMERGE_FIX_BRANCH: codex/ui-postmerge-audit-fixes
CURRENT_LOCAL_BUILD: NOT_RUN
CURRENT_XIAOMI_14_ULTRA_UI: NOT_RUN
```

不得再使用本文件旧的 `Open + Draft` / `MERGE_READY` 表述描述当前仓库状态。
