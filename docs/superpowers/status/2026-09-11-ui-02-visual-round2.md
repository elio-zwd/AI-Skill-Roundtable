# UI-02 第二轮视觉校准状态

> 日期：2026-09-11
>
> 分支：`codex/ui-02-role-spec`
>
> PR：#59（Draft）

## 当前事实

- 第一轮 Drive 视觉证据已由开发 AI 直接读取原始 PNG。
- 第一轮视觉校准已实现：UI-02 专用主页面、A 页 Hero/次卡、B 页大身份视觉、分类装饰、详情页结构化卡片、raw `domainTags` 隐藏。
- 第一轮视觉代码基线：`17dfaa3de236393428456bc739440fc55028a299`。
- GitHub `Android UI Test Compile` 对该代码执行：
  - `:app:compileDebugKotlin`：PASS；
  - `:app:compileDebugAndroidTestKotlin`：BLOCKED_BASELINE；
  - 唯一关键错误仍为 `app/src/androidTest/java/com/elio/jianyu/ui/screens/execution/IssueExecutionStopAvailabilityTest.kt:49` 的旧 `IssueExecutionBudgetUi` 构造参数失配。
- Secret scan：PASS。
- 主 Android CI 仍受仓库历史 identity gate 影响，不作为 UI-02 功能结论。

## 会话桥判断

上一轮截图中 `meeting-to-action` 后续进入 recent，但点击后的截图未稳定显示 Dialog。

源码复核确认：

- `createNewSessionWithSkillRole()` 只有在目标 session + participant 状态真正 settle 后才返回 `true`；
- App 只在 Boolean success 后写 recent；
- 因此当前证据不足以判定桥接实现失败，也不能仅凭 recent 判定人工验收通过。

第二轮必须从明确的 Skill 顶层目录进入详情，避免 deep-link 详情残留 back stack；动作后最多等待 10 秒并结合 UI dump 取证。

## 当前 Task

- [x] 第一轮视觉证据读取与差异审查。
- [x] 第一轮视觉校准代码实现。
- [x] 生产 Kotlin 远端编译通过。
- [x] 第二轮只读验收 Prompt 已持久化：`docs/testing/ui-02-role-page-local-visual-round2.md`。
- [ ] 第二轮本地 build / visual / conversation bridge 验收。
- [ ] 开发 AI 读取第二轮 Drive 原始 PNG。
- [ ] 基于第二轮证据进行必要的最终视觉校准。
- [ ] UI-02 最终代码审查与完成判断。

## 第二轮要求

本地 AI：只读，不修改代码，不 auto-fix，不 commit/push/merge。

重点输出：

- A 页 / B 页 / 详情页第二轮截图；
- raw domain tag 不可见；
- `meeting-to-action` 开始新会话后的 participant 证据；
- `study-planner` 加入当前会话后的双 participant 证据；
- recent 只由成功 use action 产生；
- Drive 新 run，不覆盖 `20260911-0836__a1cc5e6`。

PR #59 继续保持 Draft，不自动合并。