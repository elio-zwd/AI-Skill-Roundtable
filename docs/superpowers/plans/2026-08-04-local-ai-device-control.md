# 本地 AI Android 设备语义控制层实施计划

## 目标

在不修改 Android 生产代码的前提下，为本地 AI 建立统一、可验证、低 Token 的雷电模拟器/ADB 设备控制入口。

## 基线与分支

```text
Base：main@b36f8222539d8f4d99de0321caac5f9ef3ec7de1
Branch：test/local-ai-device-control
PR 标题：test: 建立本地 AI 模拟器语义控制层
```

## 完成条件

- 设备解析不会在多设备场景静默选第一台；
- `doctor/observe/find/tap/wait/assert/launch` 可用；
- `tap` 能按预期选择器验证页面变化；
- 证据只能写到仓库外；
- 终端输出紧凑，失败保留有限诊断；
- 旧 `click.py/uidump.py/screencap.py/find_icon.py` 使用共享安全设备解析；
- Windows/Ubuntu 无设备单元测试通过；
- 本地雷电模拟器真实验收由本地 AI 执行；
- 不修改 Android UI、Room、Manifest、依赖和业务测试。

## Task 1：建立目录规则与数据模型

文件：

```text
tools/device/AGENTS.md
tools/device/__init__.py
tools/device/models.py
```

实现：

- 设备信息、屏幕信息、UI 节点、选择器、匹配结果的 dataclass；
- JSON 序列化；
- 稳定状态枚举与错误类型；
- 目录内真实性、安全和输出约束。

验证：

- 数据模型字段完整；
- JSON 输出不包含不可序列化对象；
- 错误信息不包含完整 XML。

## Task 2：实现共享 ADB 客户端与设备解析

文件：

```text
tools/device/adb_client.py
tools/device/profiles/ldplayer-main.example.json
```

实现：

- `adb devices -l` 解析；
- 显式 Serial、Profile、唯一在线设备三阶段解析；
- 多设备拒绝；
- `run`、`shell`、`exec-out` 统一超时与 stderr 限制；
- `wm size/density`、SDK、方向、前台 package/activity 查询；
- Profile 期望值只校验、不擅自修改设备。

验证：

- 多设备未指定时失败；
- unauthorized/offline 不可用；
- 指定不存在或状态异常 Serial 失败；
- 同一命令对象始终绑定同一 Serial。

## Task 3：实现观察、UI 树与选择器

文件：

```text
tools/device/observer.py
tools/device/selectors.py
tools/device/evidence.py
```

实现：

- `screencap -p` 直接写本地文件；
- `uiautomator dump` 拉取 XML；
- XML 节点解析和 bounds 中点；
- `resource-id/tag/content-desc/text-exact/text-contains`；
- 唯一性、歧义和显式 index；
- 仓库外目录检查；
- 观察和动作证据 JSON；
- SHA-256 与可见文本去重/限量。

验证：

- 空 XML、损坏 XML、无节点均明确失败；
- 多匹配不得静默取第一个；
- 输出目录位于仓库内时拒绝；
- JSON 不嵌入完整 XML。

## Task 4：实现统一 CLI

文件：

```text
tools/device/cli.py
```

命令：

```text
doctor
observe
find
wait
assert
tap
launch
```

实现：

- 统一 `--device/--profile/--repository-root/--output`；
- 统一选择器参数；
- `tap` 点击前后观察与 expected selector；
- `wait` 使用轮询条件与超时；
- `launch` 支持 warm/force-stop；
- 文本成功输出最多三行；
- `--json` 输出单个紧凑 JSON；
- 明确退出码。

验证：

- ADB tap 返回 0 但 expected 未出现时必须 FAIL；
- expected 出现时 PASS；
- 超时信息有限；
- 所有子命令使用同一解析器。

## Task 5：兼容旧工具入口

文件：

```text
tools/click.py
tools/uidump.py
tools/screencap.py
tools/find_icon.py
tools/adb_verbose_diagnose.py
```

实现：

- 删除重复“选择第一台设备”的实现；
- 复用共享安全解析；
- 保留现有参数与成功输出；
- 多设备未指定时明确失败；
- 修正 verbose 诊断中的无效 shell 管道调用；
- 不改变模板匹配算法。

验证：

- 单设备行为兼容；
- `-d` 行为兼容；
- 多设备场景失败；
- 旧脚本不会误操作另一台设备。

## Task 6：重写工具测试和 CI

文件：

```text
tools/device/tests/test_device_control.py
.github/workflows/device-control-tools.yml
test/test_adb_tools.py
```

实现：

- 标准库 `unittest`；
- Mock ADB，不要求在线设备；
- 移除旧页面“智囊/配置”和固定坐标作为 CI 成功条件；
- 保留在线设备测试为显式本地集成模式，不在普通 CI 静默跳过后报成功；
- Windows/Ubuntu 双平台 CI。

验证：

- 设备解析、Profile、屏幕解析、XML、选择器、等待、tap expected、仓库外证据均有测试；
- CI 不安装 OpenCV；
- CI 不依赖雷电模拟器。

## Task 7：更新文档

文件：

```text
tools/README.md
docs/testing/local-ai-device-control-acceptance.md
```

实现：

- 推荐统一 CLI；
- 明确旧脚本兼容但不再推荐组合盲操；
- 标注旧固定坐标指南为历史文档，不作为当前见域 UI 契约；
- 给出雷电 Profile、doctor、observe、find、tap expected 示例；
- 提供本地 AI 严格只读验收 Prompt。

## Task 8：静态核验与 Draft PR

执行：

```text
检查差异只涉及 tools、工具测试、工作流和文档
检查无 Android 生产代码变化
检查无密钥、用户设备 Serial 或个人路径硬编码
检查 JSON/错误输出边界
检查旧 CLI 兼容性
创建 Draft PR
```

## 远端可验证范围

当前 GitHub 插件环境可完成：

- 文件和调用链静态检查；
- 测试代码编写；
- Windows/Ubuntu GitHub Actions 读取；
- Secret scan 与 Android CI 读取；
- PR 差异审查。

当前环境不能真实启动用户的雷电模拟器，因此以下内容必须标记为本地未验证，交给本地 AI：

- 雷电 Serial/Profile 与实际环境匹配；
- 真实截图与 UI XML；
- 真实页面点击后 expected selector；
- 前后台恢复、系统弹窗和动画时序；
- 工具与 PowerShell 7.5/Windows 10 的完整端到端表现。

## 风险

- Compose 当前未统一暴露稳定 `testTag`，PR-A 只能依赖已有 resource-id/content-desc/text；
- UiAutomator XML 可能因动画或窗口切换短暂为空，需要有限重试；
- 雷电 ADB Serial 可能随实例或配置变化，Profile 必须由用户本地维护；
- 中文输入不属于首版稳定能力；
- 视觉模板匹配仍为最后兜底，不作为默认控制路径。
