# PR-B 见域 UI 自动化标签与核心场景本地验收

## 1. 验收目标

本验收用于证明 PR-B 已把现有 Compose UI 的关键节点暴露为稳定语义契约，并能被两类测试消费：

1. Compose Instrumentation 直接检查语义树；
2. PR-A `tools/device/cli.py` 通过 UIAutomator XML 的 `resource-id` 定位同一批 `testTag`。

本验收不测试 PR09-06 首页业务，不调用生产网络，不修改 Room/Repository/ExecutionRun 状态机。

## 2. 严格只读与设备边界

- 仓库只允许读取、构建、测试和验收；
- 不修改文件、不格式化、不提交、不推送、不标记 Ready、不合并；
- 所有截图、XML、JSON、日志和临时 Profile 必须位于仓库外；
- 不执行 `adb shell pm clear com.elio.jianyu`；
- 不删除 App 数据、不卸载其他包、不修改模拟器系统设置；
- 安装 Debug APK 时使用覆盖安装 `adb install -r`，保留现有数据；
- 不使用固定屏幕坐标；
- 报告不得粘贴完整 UI XML、完整截图二进制或用户隐私内容。

## 3. 冻结契约入口

生产入口：

```text
app/src/main/java/com/elio/jianyu/ui/automation/JianyuAutomationTags.kt
```

关键冻结标签：

```text
jianyu_app_content_root
app_bottom_navigation
app_destination_home
app_destination_issues
app_destination_skills
app_destination_resources
global_settings_button
page_back_button
home_screen
issues_screen
issue_execution_screen
official_skill_catalog
resources_screen
settings_screen
resources_material_library
resources_personal_context_library
resources_materials_content
resources_personal_context_content
issue_execution_participants
context_confirmation_validation_errors
```

`home_question_placeholder` 是 PR09-06 前的临时占位标签，不属于冻结契约。

## 4. 精确检出门禁

由验收 Prompt 提供最终 PR、Base、Branch 和 Head。执行：

```powershell
git fetch origin --prune
git checkout test/pr-b-ui-automation-contract
git pull --ff-only origin test/pr-b-ui-automation-contract

git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse origin/main
git diff --check origin/main...HEAD
```

任一条件不满足时停止：

- 当前分支不是 `test/pr-b-ui-automation-contract`；
- `HEAD` 与 Prompt 给出的精确 SHA 不一致；
- `origin/main` 与 Prompt 给出的 Base SHA 不一致；
- `git status --short` 非空。

## 5. 环境记录

记录原始输出：

```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber,OSArchitecture
$PSVersionTable.PSVersion
git --version
java -version
python --version
adb version
adb devices -l
```

同时记录模拟器型号、Android API、分辨率、DPI、方向和目标 Serial。

## 6. JVM、Lint 与 APK 构建

```powershell
.\gradlew.bat --stop
.\gradlew.bat :app:testDebugUnitTest --stacktrace
.\gradlew.bat :app:lintDebug --stacktrace
.\gradlew.bat :app:assembleDebug --stacktrace
```

必须记录每条命令的退出码、测试数量和失败项。重点核验：

- `JianyuUiAutomationArchitectureTest`；
- `JianyuAutomationTagsTest`；
- 冻结标签无重复；
- 静态标签符合 `lower_snake_case`；
- 动态 ID 拒绝中文、空格、超长值和用户文本；
- `home_question_placeholder` 未进入冻结清单。

## 7. 覆盖安装与 Instrumentation

先记录安装状态：

```powershell
$serial = '<真实设备 Serial>'
$package = 'com.elio.jianyu'
adb -s $serial shell pm path $package
```

使用当前精确 Head 构建出的 Debug APK覆盖安装，不清数据：

```powershell
$apk = Resolve-Path '.\app\build\outputs\apk\debug\app-debug.apk'
Get-FileHash $apk -Algorithm SHA256
adb -s $serial install -r $apk
```

执行 PR-B 定向测试：

```powershell
adb -s $serial shell am instrument -w `
  -e class com.elio.jianyu.ui.automation.JianyuUiAutomationNavigationTest `
  com.elio.jianyu.test/androidx.test.runner.AndroidJUnitRunner
```

执行既有恢复测试：

```powershell
adb -s $serial shell am instrument -w `
  -e class com.elio.jianyu.ui.MainNavigationRestorationTest `
  com.elio.jianyu.test/androidx.test.runner.AndroidJUnitRunner
```

最后执行全量设备回归：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --stacktrace
```

必须确认：

- 首页启动根节点唯一；
- 四个一级目的地均可通过标签切换；
- 设置打开和返回正常；
- 资料库与个人背景内容根节点互斥出现；
- App 根节点没有 ClickAction；
- Activity 重建后目的地与资源 Tab 恢复；
- 全量 Instrumentation 没有回归。

## 8. 仓库外 Profile 与证据目录

```powershell
$profile = Join-Path $env:TEMP 'jianyu-pr-b-device-profile.json'
Copy-Item '.\tools\device\profiles\ldplayer-main.example.json' $profile -Force

$evidenceRoot = Join-Path $env:TEMP 'jianyu-pr-b-ui-automation'
Remove-Item $evidenceRoot -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $evidenceRoot | Out-Null
```

按真实设备修改仓库外 Profile，至少包含：

```json
{
  "name": "jianyu-pr-b-device",
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

## 9. PR-A 工具无设备测试与 Profile 门禁

```powershell
python -m compileall -q tools/device
python -m unittest discover -s tools/device/tests -p 'test_*.py' -v
python tools/device/cli.py doctor --profile $profile --json
```

必须为 `PASS`，并记录单元测试数量与退出码。

## 10. 强停启动与首页标签

```powershell
python tools/device/cli.py launch `
  --profile $profile `
  --mode force-stop `
  --expect-by tag `
  --expect-value home_screen `
  --timeout 8000 `
  --output (Join-Path $evidenceRoot '01-launch-home') `
  --json
```

通过条件：

- `status=PASS`；
- 前台包为 `com.elio.jianyu`；
- `expectedNode.resourceId` 以 `/home_screen` 结尾；
- 没有执行 `pm clear`。

## 11. 四个一级入口语义点击

每次点击必须满足：预期页面在点击前不存在、目标节点 `clickable=true`、`tapSent=true`、点击后预期页面出现。

```powershell
python tools/device/cli.py tap --profile $profile --by tag --value app_destination_issues `
  --expect-by tag --expect-value issues_screen --timeout 8000 `
  --output (Join-Path $evidenceRoot '02-issues') --json

python tools/device/cli.py tap --profile $profile --by tag --value app_destination_skills `
  --expect-by tag --expect-value official_skill_catalog --timeout 8000 `
  --output (Join-Path $evidenceRoot '03-skills') --json

python tools/device/cli.py tap --profile $profile --by tag --value app_destination_resources `
  --expect-by tag --expect-value resources_screen --timeout 8000 `
  --output (Join-Path $evidenceRoot '04-resources') --json

python tools/device/cli.py tap --profile $profile --by tag --value app_destination_home `
  --expect-by tag --expect-value home_screen --timeout 8000 `
  --output (Join-Path $evidenceRoot '05-home') --json
```

四次均必须 `PASS`。不得以可见文字或固定坐标替代标签。

## 12. 设置打开与返回

```powershell
python tools/device/cli.py tap --profile $profile --by tag --value global_settings_button `
  --expect-by tag --expect-value settings_screen --timeout 8000 `
  --output (Join-Path $evidenceRoot '06-settings') --json

python tools/device/cli.py tap --profile $profile --by tag --value page_back_button `
  --expect-by tag --expect-value home_screen --timeout 8000 `
  --output (Join-Path $evidenceRoot '07-settings-back') --json
```

必须证明设置页出现后可通过语义返回首页。

## 13. 资料与个人背景分区

先进入资料与成果：

```powershell
python tools/device/cli.py tap --profile $profile --by tag --value app_destination_resources `
  --expect-by tag --expect-value resources_screen --timeout 8000 `
  --output (Join-Path $evidenceRoot '08-resources-open') --json

python tools/device/cli.py assert --profile $profile --by tag --value resources_materials_content `
  --output (Join-Path $evidenceRoot '09-materials-content') --json
```

再切换到个人背景：

```powershell
python tools/device/cli.py tap --profile $profile --by tag --value resources_personal_context_library `
  --expect-by tag --expect-value resources_personal_context_content --timeout 8000 `
  --output (Join-Path $evidenceRoot '10-personal-context') --json
```

返回资料库：

```powershell
python tools/device/cli.py tap --profile $profile --by tag --value resources_material_library `
  --expect-by tag --expect-value resources_materials_content --timeout 8000 `
  --output (Join-Path $evidenceRoot '11-materials-return') --json
```

当前 UI 中“资料库/个人背景”是“资料”一级 Tab 内的两个内容分区；不得把尚未实现的未来 UI 描述为现状。

## 14. 不可点击节点拒绝

先定位 App 根节点：

```powershell
python tools/device/cli.py find --profile $profile --by tag --value jianyu_app_content_root `
  --output (Join-Path $evidenceRoot '12-root-find') --json
```

确认返回节点 `clickable=false`，然后尝试点击：

```powershell
python tools/device/cli.py tap --profile $profile --by tag --value jianyu_app_content_root `
  --expect-by tag --expect-value __JIANYU_NEVER_EXISTS__ --timeout 500 `
  --output (Join-Path $evidenceRoot '13-root-reject') --json
$rootTapExit = $LASTEXITCODE
```

预期：

```text
status=FAIL
category=TARGET_NOT_CLICKABLE
rootTapExit=1
tap 未发送
```

不得把拒绝点击视为失败；这是安全契约的通过证据。

## 15. 收尾门禁

```powershell
git status --short
git diff --exit-code
git rev-parse HEAD
Get-ChildItem $evidenceRoot -Recurse | Select-Object FullName,Length
```

必须满足：

- 工作区干净；
- Head 未变化；
- 仓库内没有证据目录；
- 没有 Commit、Push、Ready、Merge；
- App 数据未清除；
- 报告只摘录必要字段和关键日志。

## 16. 报告结论规则

- 所有定向与全量测试通过，PR-A 标签点击、启动和不可点击拒绝均通过：`PASS`；
- 代码测试通过但外部 UIAutomator 无法识别标签：`FAIL`；
- 无可用设备、无安装授权或无法运行 Instrumentation：`NOT_VERIFIED`；
- 不得把 `NOT_VERIFIED` 写成 `PASS WITH NOTES` 后建议合并。
