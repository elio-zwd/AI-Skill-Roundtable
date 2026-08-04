# 运行时与本地验收工具

本目录包含：

- Android 构建辅助脚本；
- 本地 AI 使用的 ADB 设备语义控制层；
- 旧版截图、点击、UI Dump 与视觉模板兼容入口；
- 低 Token 本地验收证据工具。

## 1. 推荐入口：`tools/device/cli.py`

新的统一入口遵循：

```text
确定唯一设备
→ 观察当前状态
→ 语义定位控件
→ 执行动作
→ 等待并验证预期状态
→ 保存仓库外紧凑证据
```

查看帮助：

```powershell
python tools/device/cli.py --help
```

### 1.1 设备安全规则

设备选择顺序：

1. `--device <adb-serial>`；
2. `--profile <json>` 中的 `serial`；
3. 只有恰好一台在线设备时才允许自动选择。

多台设备在线且没有指定目标时，工具会返回 `AMBIGUOUS_DEVICE`，不会静默选择第一台。

### 1.2 雷电 Profile

复制示例到仓库外，再按实际环境修改：

```powershell
$profile = Join-Path $env:TEMP 'jianyu-ldplayer-profile.json'
Copy-Item `
  .\tools\device\profiles\ldplayer-main.example.json `
  $profile
```

示例：

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

Profile 只做门禁检查，不擅自修改雷电配置。

### 1.3 检查设备

```powershell
python tools/device/cli.py doctor `
  --profile $profile `
  --json
```

检查：

- Serial 和在线状态；
- 分辨率；
- DPI；
- API；
- 横竖屏；
- 当前前台 package/activity。

### 1.4 观察当前页面

```powershell
$evidence = Join-Path $env:TEMP 'jianyu-device-evidence'

python tools/device/cli.py observe `
  --profile $profile `
  --output $evidence
```

仓库外生成：

```text
observation.png
observation.xml
observation.json
```

终端只输出紧凑摘要，不输出完整 XML。

### 1.5 语义定位

支持：

```text
tag
resource-id
content-desc
text-exact
text-contains
```

`tag` 是 `resource-id` 的友好别名，为后续 Compose `testTagsAsResourceId` 契约预留。

```powershell
python tools/device/cli.py find `
  --profile $profile `
  --by content-desc `
  --value '设置' `
  --output $evidence `
  --json
```

默认要求唯一匹配。多个候选不会静默选择第一个；确需选择时显式传 `--index`。

### 1.6 点击并验证

```powershell
python tools/device/cli.py tap `
  --profile $profile `
  --by content-desc `
  --value '设置' `
  --expect-by text-exact `
  --expect-value '<设置页唯一文字>' `
  --timeout 5000 `
  --output $evidence `
  --json
```

只有预期状态出现才返回 `PASS`。

不提供 `--expect-*` 时，动作返回 `NOT_VERIFIED` 和退出码 `2`；ADB 点击命令返回零不等于 UI 验收通过。

### 1.7 等待与断言

```powershell
python tools/device/cli.py wait `
  --profile $profile `
  --by text-exact `
  --value '首页' `
  --timeout 5000
```

```powershell
python tools/device/cli.py assert `
  --profile $profile `
  --by content-desc `
  --value '设置'
```

### 1.8 启动与强停恢复

```powershell
python tools/device/cli.py launch `
  --profile $profile `
  --mode force-stop `
  --timeout 8000 `
  --output $evidence `
  --json
```

首版仅支持：

```text
warm
force-stop
```

不实现 `pm clear`、卸载或重装等破坏性动作。

## 2. 证据安全与 Token 约束

截图、UI XML、动作 JSON 和日志必须写在仓库外。

如果 `--output` 指向仓库内部，统一入口会返回：

```text
OUTPUT_INSIDE_REPOSITORY
exit=70
```

成功输出最多三行；失败只返回有限错误、候选摘要和证据路径。

## 3. 旧工具兼容入口

旧工具继续保留：

```text
tools/screencap.py
tools/click.py
tools/uidump.py
tools/find_icon.py
tools/adb_verbose_diagnose.py
```

它们现在共享安全设备解析：

- 显式 `-d <serial>` 行为保留；
- 恰好一台在线设备时可自动解析；
- 多台在线设备且未指定时明确失败。

### 截图

```powershell
python tools/screencap.py `
  -d emulator-5554 `
  -o $env:TEMP\jianyu-screen.png
```

成功时仍只输出 PNG 绝对路径。

### 坐标操作

```powershell
python tools/click.py -d emulator-5554 540 1900
python tools/click.py -d emulator-5554 540 1900 -l 1000
python tools/click.py -d emulator-5554 -s 100 2000 100 500 400
python tools/click.py -d emulator-5554 -k 4
```

这些入口只证明 ADB 命令已发送，不提供动作后 UI 验证。新的自动化流程应优先使用 `tools/device/cli.py tap --expect-*`。

`adb shell input text` 对中文输入并不稳定，因此旧 `-t` 不作为可靠中文输入契约。

### UI Dump

```powershell
python tools/uidump.py `
  -d emulator-5554 `
  -o $env:TEMP\jianyu-window.xml
```

旧 `--find` 仍可使用，但只返回第一个模糊文字坐标；需要唯一性、节点字段和歧义检测时使用统一 CLI。

### 视觉模板

```powershell
python tools/find_icon.py `
  -d emulator-5554 `
  -t tools/templates/setting.png
```

依赖本地 OpenCV 与 NumPy。视觉模板仅作为语义定位失败后的兜底。

`tools/templates/` 中部分图标和固定坐标来自历史 UI，不是当前见域页面的稳定契约。

### 详细诊断

```powershell
python tools/adb_verbose_diagnose.py -d emulator-5554
```

## 4. 工具测试

不需要真实模拟器：

```powershell
python -m compileall -q tools/device
python -m unittest discover `
  -s tools/device/tests `
  -p 'test_*.py' `
  -v
python tools/device/cli.py --help
```

Windows 与 Ubuntu GitHub Actions 使用 `.github/workflows/device-control-tools.yml` 执行同一套测试。

真实设备旧入口集成测试必须显式设置 Serial：

```powershell
$env:JIANYU_ADB_DEVICE = 'emulator-5554'
python test/test_adb_tools.py -v
```

可选设置当前页面可见文字：

```powershell
$env:JIANYU_FIND_TEXT = '首页'
```

## 5. 本地严格验收

完整只读验收流程：

```text
docs/testing/local-ai-device-control-acceptance.md
```

## 6. APK 构建

### Debug APK

```powershell
pwsh.exe -File .\tools\build-debug-apk.ps1 `
  -VersionName 1.1 `
  -VersionCode 2
```

产物：

```text
app\build\outputs\apk\debug\app-debug.apk
```

### Release APK

```powershell
pwsh.exe -File .\tools\build-release-apk.ps1 `
  -VersionName 1.1 `
  -VersionCode 2
```

Release 构建必须使用本机签名配置，不会回退为 Debug。签名材料禁止提交。

## 7. 低 Token 本地验收证据

```text
tools/local-verification/
```

该工具负责包装 Gradle/JUnit 等命令，把完整日志保存在仓库外，只向本地 AI 提供紧凑证据。它与 `tools/device/` 分工如下：

| 目录 | 职责 |
|---|---|
| `tools/device/` | 设备观察、语义定位、交互和状态验证 |
| `tools/local-verification/` | 构建、测试命令和 JUnit 的低 Token 证据 |
