# 本地 AI Android 设备语义控制层设计

## 1. 背景

仓库现有 `tools/screencap.py`、`tools/click.py`、`tools/uidump.py`、`tools/find_icon.py` 可以完成截图、坐标点击、UI 树查找和模板匹配，但它们仍属于单次“盲操”工具：

- 多台设备在线时默认选择第一台；
- 点击成功只代表 ADB 命令返回零，不代表页面完成预期变化；
- UI 查找仅返回坐标，缺少匹配节点、唯一性和可点击状态证据；
- 失败时没有统一保存点击前后截图、UI 树和紧凑诊断；
- 旧测试与固定坐标文档仍耦合历史“智囊圆桌”页面；
- 本地 AI 需要自行拼接大量 ADB 命令，容易误选设备、误判成功并消耗上下文。

PR-A 建立统一的本地 AI Android 设备控制入口。首要目标是稳定控制用户已配置为目标真机屏幕规格的雷电模拟器，同时保持对其他 ADB 模拟器和真机的通用兼容。

## 2. 目标

本 PR 必须提供：

1. **确定设备**：所有动作绑定同一显式 Serial 或设备 Profile；多设备在线时禁止隐式选择第一台。
2. **先观察后动作**：一次观察生成截图、UI XML 和紧凑 JSON 摘要。
3. **语义定位**：优先按 `resource-id/testTag`、`content-desc`、精确文字和包含文字定位，不依赖固定坐标。
4. **动作后校验**：点击可等待预期控件或页面出现；ADB 命令成功不等同于 UI 成功。
5. **可恢复证据**：失败时保存仓库外证据，终端只显示有限摘要，不灌入完整 XML、截图或日志。
6. **安全边界**：清数据、卸载、重装等破坏性动作默认禁止，必须显式授权。
7. **兼容入口**：现有脚本继续可用，但共享安全设备解析，不再在多设备场景静默选第一台。

## 3. 非目标

本 PR 不包含：

- 修改见域 Compose 生产 UI 或补充 `testTag`；
- 引入 Appium、Maestro、uiautomator2 服务端或额外常驻进程；
- 引入 YAML 解析依赖或完整场景 DSL；
- 管理雷电安装目录、创建模拟器实例或修改雷电全局配置；
- 代替 Android Instrumentation、Compose UI Test 或人工视觉验收；
- 自动读取、上传或分析用户隐私数据。

稳定 Compose `testTag` 与核心业务场景属于后续 PR-B。

## 4. 总体结构

```text
tools/device/
├── AGENTS.md
├── __init__.py
├── adb_client.py
├── models.py
├── selectors.py
├── observer.py
├── evidence.py
├── cli.py
├── profiles/
│   └── ldplayer-main.example.json
└── tests/
    └── test_device_control.py
```

统一入口：

```powershell
python tools/device/cli.py <command>
```

首版命令：

```text
doctor
observe
find
tap
wait
assert
launch
```

## 5. 设备解析契约

设备解析顺序：

1. 命令行 `--device`；
2. Profile 中的 `serial`；
3. 仅当 ADB 中恰好有一台 `device` 状态设备时自动选择；
4. 无设备、设备未授权、设备离线或多台在线且未指定时失败。

禁止：

- 在多台设备在线时选择第一台；
- 将 `unauthorized` 或 `offline` 当作可用设备；
- 截图、查找和点击各自重新选择不同设备。

Profile 示例：

```json
{
  "name": "ldplayer-main",
  "serial": "emulator-5554",
  "package": "com.elio.jianyu",
  "expected": {
    "size": "1080x2400",
    "density": 420,
    "api": 28,
    "orientation": "portrait"
  }
}
```

Profile 中的硬件值仅是门禁期望，不由工具擅自修改设备。

## 6. 观察契约

`observe` 在仓库外目录生成：

```text
screenshot.png
window.xml
observation.json
```

`observation.json` 至少包含：

```json
{
  "schemaVersion": 1,
  "serial": "emulator-5554",
  "foregroundPackage": "com.elio.jianyu",
  "activity": "com.elio.jianyu.MainActivity",
  "screen": {
    "width": 1080,
    "height": 2400,
    "density": 420,
    "api": 28,
    "orientation": "portrait"
  },
  "screenshotSha256": "...",
  "nodeCount": 42,
  "visibleTexts": ["首页", "议题", "Skill", "资料与成果"]
}
```

终端不得输出完整 XML。成功最多输出三行摘要和证据路径。

## 7. 选择器契约

支持：

```text
resource-id
content-desc
text-exact
text-contains
```

`tag` 作为 `resource-id` 的友好别名，为 PR-B 的 Compose `testTagsAsResourceId` 预留。

匹配节点包含：

```text
resourceId
text
contentDescription
className
clickable
enabled
selected
checked
bounds
center
```

默认要求唯一匹配：

- 0 个匹配：失败；
- 1 个匹配：通过；
- 多个匹配：失败并返回有限候选摘要；
- 只有显式 `--index` 才允许从多个候选中选择。

## 8. 动作与等待契约

`tap` 流程：

```text
确认设备与前台包
→ 观察当前 UI
→ 定位唯一节点
→ 检查 enabled
→ 保存 before 证据
→ 点击中心坐标
→ 可选等待 expected selector
→ 保存 after 证据
→ 输出 PASS/FAIL
```

禁止把 `adb shell input tap` 的零退出码直接写成 UI PASS。

等待必须使用轮询条件和超时，不使用固定睡眠作为成功依据。

## 9. 启动与破坏性操作

`launch` 首版支持：

```text
warm       直接拉起
force-stop 强停后拉起
```

以下能力即使后续实现，也必须要求 `--allow-destructive`：

```text
pm clear
uninstall
reinstall
```

PR-A 首版不实现上述破坏性模式。

## 10. 证据与 Token 控制

所有证据目录必须位于仓库外。若未提供 `--output`，使用系统临时目录。

每次动作证据至少包含：

```json
{
  "schemaVersion": 1,
  "action": "tap",
  "serial": "emulator-5554",
  "selector": {"by": "tag", "value": "settings_entry"},
  "matchedNode": {"bounds": [936, 84, 1032, 180]},
  "expected": {"by": "tag", "value": "settings_screen"},
  "result": "PASS",
  "durationMilliseconds": 684,
  "beforeScreenshotSha256": "...",
  "afterScreenshotSha256": "..."
}
```

成功输出最多三行。失败只输出：

- 错误类别；
- 选择器和有限候选摘要；
- before/after/JSON 证据路径；
- 不超过固定上限的 ADB stderr。

## 11. 兼容策略

现有脚本保留原参数和成功输出格式，但设备选择改用共享解析器：

- 显式 `-d/--device` 行为不变；
- 恰好一台在线设备时行为不变；
- 多台设备在线且未指定时由“选第一台”改为明确失败；
- README 更新为推荐使用统一 CLI。

## 12. 测试策略

CI 不依赖真实模拟器，使用标准库 `unittest` 和 Mock/Fake ADB 覆盖：

- 单设备自动解析；
- 多设备拒绝隐式选择；
- 显式 Serial 状态校验；
- Profile 加载与期望门禁；
- `wm size/density`、API、方向和前台 Activity 解析；
- UI XML 节点解析；
- 唯一、缺失和歧义匹配；
- 仓库内输出拒绝；
- 等待超时与成功；
- tap 的动作后预期校验；
- 紧凑 JSON/文本输出。

GitHub Actions 在 Windows 与 Ubuntu 上运行。

## 13. 验收标准

PR-A 完成需要满足：

1. 多设备在线且未指定 Serial 时所有入口均拒绝执行；
2. 同一次操作全程使用同一 Serial；
3. `observe` 能生成仓库外截图、XML 与紧凑 JSON；
4. `find` 能返回完整节点摘要并检测歧义；
5. `tap --expect-*` 只有预期状态出现才 PASS；
6. 失败证据可定位问题且不回灌完整 XML；
7. 旧脚本不再静默选择第一台设备；
8. Windows/Ubuntu 无设备单元测试通过；
9. 本地雷电模拟器只读验收通过；
10. 不修改 Android 生产代码、Room、Manifest 或业务行为。
