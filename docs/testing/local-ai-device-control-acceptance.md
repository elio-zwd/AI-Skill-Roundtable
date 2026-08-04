# 本地 AI Android 设备语义控制层严格只读验收

## 1. 目标

本验收用于验证 `tools/device/cli.py` 能在用户的雷电模拟器上稳定执行：

```text
确定唯一设备
→ 检查屏幕 Profile
→ 观察截图与 UI 树
→ 语义定位唯一控件
→ 执行动作
→ 等待并验证预期状态
→ 生成仓库外紧凑证据
```

本验收不修改仓库文件、不提交、不推送、不标记 Ready、不合并。

## 2. 前置条件

- Windows 10；
- PowerShell 7；
- Python 3.10 或以上；
- Android Platform Tools，`adb` 位于 PATH；
- 雷电模拟器已启动；
- 见域 Debug APK 已安装；
- 已知雷电 ADB Serial；
- 证据目录位于 `$env:TEMP` 或其他仓库外目录。

## 3. 锁定目标分支

在具体 PR 验收 Prompt 中填写精确 Base、Branch 与 Head。

```powershell
git fetch origin --prune
git checkout test/local-ai-device-control
git pull --ff-only origin test/local-ai-device-control

git status --short
git rev-parse HEAD
git rev-parse origin/main
git diff --check origin/main...HEAD
```

Head 不一致或工作区不干净时立即停止。

## 4. 设备 Profile

不要修改仓库内示例。复制到仓库外：

```powershell
$profile = Join-Path $env:TEMP 'jianyu-ldplayer-profile.json'
Copy-Item `
  .\tools\device\profiles\ldplayer-main.example.json `
  $profile
```

根据真实环境修改仓库外副本：

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

真实值通过以下命令确认：

```powershell
adb devices -l
adb -s <serial> shell wm size
adb -s <serial> shell wm density
adb -s <serial> shell getprop ro.build.version.sdk
```

## 5. 无设备单元测试

```powershell
python -m compileall -q tools/device
python -m unittest discover `
  -s tools/device/tests `
  -p 'test_*.py' `
  -v
python tools/device/cli.py --help
```

记录测试数量、退出码和失败项。不得仅凭 GitHub CI 推断本地通过。

## 6. 多设备误选门禁

当雷电和另一台设备同时在线时，不传 `--device` 或 `--profile`：

```powershell
python tools/device/cli.py doctor --json
$ambiguousExit = $LASTEXITCODE
```

预期：

```text
状态：FAIL
category：AMBIGUOUS_DEVICE
退出码：69
没有执行截图、点击或启动动作
```

只有一台设备在线时允许自动解析。

## 7. Profile 门禁

```powershell
python tools/device/cli.py doctor `
  --profile $profile `
  --json
$doctorExit = $LASTEXITCODE
```

通过条件：

```text
status=PASS
doctorExit=0
serial 与 Profile 一致
size/density/api/orientation 与 Profile 一致
```

故意将仓库外 Profile 的 density 改错后再执行一次，预期 `status=FAIL` 且列出 mismatch；恢复正确值继续。

## 8. 观察证据

```powershell
$evidenceRoot = Join-Path $env:TEMP 'jianyu-device-control-acceptance'
Remove-Item $evidenceRoot -Recurse -Force -ErrorAction SilentlyContinue

python tools/device/cli.py observe `
  --profile $profile `
  --output $evidenceRoot
$observeExit = $LASTEXITCODE
```

通过条件：

```text
observeExit=0
终端最多三行摘要
observation.png 存在且为有效 PNG
observation.xml 存在且包含 hierarchy
observation.json 可解析
JSON 包含 serial、前台 package/activity、屏幕参数、SHA-256、nodeCount
JSON 不嵌入完整 XML
所有证据位于仓库外
```

重新计算截图 SHA-256，与 JSON 对比。

## 9. 仓库内输出拒绝

```powershell
python tools/device/cli.py observe `
  --profile $profile `
  --output .\.device-evidence-must-not-exist `
  --json
$insideExit = $LASTEXITCODE
```

预期：

```text
category=OUTPUT_INSIDE_REPOSITORY
退出码=70
仓库内目录不存在
git status --short 为空
```

## 10. 语义定位

优先选择当前页面唯一且稳定的 `resource-id`、`content-desc` 或可见文字。

先从观察 XML 中人工选择一个不敏感、不会提交数据的控件，例如顶部设置入口或底部导航项。

示例：

```powershell
python tools/device/cli.py find `
  --profile $profile `
  --by content-desc `
  --value '设置' `
  --output $evidenceRoot `
  --json
```

通过条件：

- 返回唯一节点；
- 包含 resourceId、text、contentDescription、className；
- 包含 enabled、clickable、selected、checked；
- 包含 bounds 和 center；
- 0 个匹配明确失败；
- 多个匹配在未提供 `--index` 时明确失败，不静默选择第一个。

## 11. 点击与预期状态验证

选择一个可安全打开、可返回的入口。

```powershell
python tools/device/cli.py tap `
  --profile $profile `
  --by content-desc `
  --value '设置' `
  --expect-by text-exact `
  --expect-value '<设置页唯一可见文字>' `
  --timeout 5000 `
  --output $evidenceRoot `
  --json
$tapExit = $LASTEXITCODE
```

通过条件：

```text
只有预期控件出现时 status=PASS
tapExit=0
before.json/before.png/before.xml 存在
after.json/after.png/after.xml 存在
tap-result.json 存在
before/after SHA-256 可复算
```

### 错误预期测试

把 `--expect-value` 改为一个绝不出现的值，并缩短 timeout：

```powershell
python tools/device/cli.py tap `
  --profile $profile `
  --by content-desc `
  --value '<安全返回或切换控件>' `
  --expect-by text-exact `
  --expect-value '__JIANYU_NEVER_EXISTS__' `
  --timeout 500 `
  --output $evidenceRoot `
  --json
$timeoutExit = $LASTEXITCODE
```

预期：

```text
status=FAIL
category=SELECTOR_TIMEOUT
tap-result.json 仍存在
after 观察证据仍存在
终端不输出完整 XML
```

### 不提供预期状态

```powershell
python tools/device/cli.py tap ...（不传 expect 参数）
```

预期状态必须是 `NOT_VERIFIED`，退出码 `2`，不得报告 PASS。

## 12. 启动与强停恢复

```powershell
python tools/device/cli.py launch `
  --profile $profile `
  --mode force-stop `
  --timeout 8000 `
  --output $evidenceRoot `
  --json
$launchExit = $LASTEXITCODE
```

通过条件：

- 强停后重新拉起 `com.elio.jianyu`；
- 前台 package 验证通过；
- `launch-result.json` 和观察证据存在；
- 不执行 `pm clear`、卸载或重装。

## 13. 旧工具兼容

显式传入同一 Serial：

```powershell
$serial = (Get-Content $profile -Raw | ConvertFrom-Json).serial

python tools/screencap.py -d $serial -o (Join-Path $evidenceRoot 'legacy.png')
python tools/uidump.py -d $serial -o (Join-Path $evidenceRoot 'legacy.xml')
python tools/click.py -d $serial -k 4
python tools/adb_verbose_diagnose.py -d $serial
```

确认旧参数可用。当多设备在线且省略 `-d` 时，旧工具必须拒绝执行。

`find_icon.py` 需要本地已安装 OpenCV/NumPy；其视觉匹配仅作为兜底，不是本 PR 的默认语义控制路径。

## 14. 收尾门禁

```powershell
git status --short
git diff --exit-code
git rev-parse HEAD
```

必须满足：

- 工作区干净；
- 没有仓库内证据目录；
- 没有 Commit、Push、PR 状态修改；
- 所有证据位于仓库外；
- 未输出或上传用户隐私内容。

## 15. 报告格式

```text
# PR-A 本地 AI 模拟器语义控制层严格只读验收报告

## 1. 最终结论
PASS / FAIL / NOT_VERIFIED

## 2. 精确目标
PR、Base、Branch、Head

## 3. 环境
Windows、PowerShell、Python、ADB、雷电版本、Serial

## 4. 设备门禁
多设备拒绝、Profile、分辨率、DPI、API、方向

## 5. 无设备测试
测试数量、退出码

## 6. Observe
截图、XML、JSON、Hash、Token 输出

## 7. Find
唯一、缺失、歧义、节点字段

## 8. Tap
正确预期、错误预期、NOT_VERIFIED、before/after 证据

## 9. Launch
warm/force-stop、前台验证

## 10. 旧工具兼容
显式 Serial、多设备拒绝

## 11. 安全与只读纪律
仓库外证据、工作区干净、无破坏性动作

## 12. 未验证项
不得推断通过

## 13. 最终建议
保持 Draft / 可标记 Ready / 必须修复
```

不要粘贴完整 UI XML、完整截图二进制、完整 logcat 或用户输入内容。
