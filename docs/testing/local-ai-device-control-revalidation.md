# PR #42 点击可验证性与见域端到端补验

## 背景

首次雷电验收证明了设备解析、Profile、观察、证据、选择器、等待、断言和旧工具兼容可用，但同时暴露两个不能忽略的假阳性条件：

1. 目标节点 `clickable=false` 时仍可发送坐标 tap；
2. `--expect-*` 指定的状态在点击前已经存在时，等待逻辑可立即返回并误报 `PASS`。

此外，当 `com.elio.jianyu` 未安装时，Launcher 上的工具测试不能替代见域 App 内真实页面切换和 `launch` 验证。

## 修复后的点击契约

`tap` 必须遵守：

- 目标节点 `enabled=true`；
- 目标节点 `clickable=true`；
- 提供 `--expect-*` 时，预期节点在点击前必须不存在；
- 点击发送后，预期节点必须在超时前出现；
- 只有同时满足以上条件才能返回 `PASS`。

具体结果：

- `clickable=false`：返回 `TARGET_NOT_CLICKABLE`，不发送 tap；
- 预期状态点击前已存在：返回 `NOT_VERIFIED`、退出码 `2`、`tapSent=false`；
- 未提供预期状态：发送点击，但返回 `NOT_VERIFIED`、退出码 `2`；
- 预期状态点击后出现：返回 `PASS`；
- 预期状态未出现：返回 `SELECTOR_TIMEOUT`，保存 before/after 和失败 JSON。

## 最终验收判定

以下任一情况存在时，PR #42 不得判定为完整 `PASS`：

- 见域 App 未安装；
- `launch --mode force-stop` 未通过；
- 只在 Launcher 上验证，未进入见域页面；
- 正确预期在点击前已经存在；
- 点击目标 `clickable=false`；
- 没有证明 `tapSent=true`；
- 没有证明预期节点从“点击前不存在”变为“点击后存在”。

此时应使用 `NOT_VERIFIED` 或 `PASS WITH BLOCKER`，不得建议 Ready 或合并。

## 设备安装边界

仓库必须继续严格只读。为了完成见域 App 端到端验收，可以在用户明确授权后对专用雷电模拟器执行有限设备变更：

1. 记录测试前 `com.elio.jianyu` 是否已安装；
2. 只安装当前精确 Head 构建出的 Debug APK；
3. 不执行 `pm clear`，不修改雷电系统配置；
4. 验收结束后，如果测试前未安装，则卸载测试包以恢复原状态；
5. 安装、卸载、包版本、APK SHA-256 和恢复结果必须写入报告；
6. 未获得用户授权时不得安装，并必须将 App 内场景标记为 `NOT_VERIFIED`。

## 最小补验范围

锁定修复后的精确 Head 后，仅需重复：

- 28 项设备控制单元测试；
- `clickable=false` 拒绝；
- 预期状态点击前已存在时 `NOT_VERIFIED` 且 `tapSent=false`；
- 在见域 App 内选择一个真实可点击入口；
- 证明预期节点点击前不存在、点击后出现；
- `launch --mode force-stop`；
- 最终工作区干净和设备状态恢复。

无需重复 Android 全量 JVM、Lint、Room Migration 或 Release 构建，除非最新 Head 的 GitHub Actions 出现失败。
