# 本地 AI 严格只读验收模板

> 使用前替换 `<PR_NUMBER>`、`<BRANCH>`、`<BASE_SHA>`、`<HEAD_SHA>` 和具体测试命令。本模板必须与 [`local-verification-evidence-protocol.md`](./local-verification-evidence-protocol.md) 一起使用。

## 一、任务边界

对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 PR #`<PR_NUMBER>` 做独立、严格、只读验收。

```text
目标分支：<BRANCH>
Base：<BASE_SHA>
Head：<HEAD_SHA>
```

只允许读取、构建、测试、模拟器验证和报告。不得修改源码、自动格式化、提交、推送、变基、合并、标记 Ready、关闭 PR、删除分支或修改 GitHub 设置。

## 二、Token 与证据纪律

1. 完整命令输出不得直接进入 AI 上下文。
2. 所有 stdout/stderr 写入仓库外 `$env:TEMP` 目录。
3. 成功步骤只报告结果、退出码、统计、步骤 JSON 路径和日志 SHA-256。
4. 失败时只读取工具生成的失败测试名称和有界摘录。
5. 首次 Gradle 执行不使用 `--stacktrace`；证据不足时才定向重跑。
6. 全量测试通过且 XML 证明目标测试已运行后，不重复执行相同定向测试。
7. 不复制完整成功日志、全部 Task 或全部通过测试名称。

## 三、初始化

```powershell
$prNumber = '<PR_NUMBER>'
$expectedHead = '<HEAD_SHA>'
$evidenceRoot = Join-Path $env:TEMP "jianyu-pr-$prNumber-$expectedHead"
New-Item -ItemType Directory -Path $evidenceRoot -Force | Out-Null
```

记录环境时允许直接输出短文本：

```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber,OSArchitecture
$PSVersionTable.PSVersion
git --version
java -version
adb version
Get-Date -Format 'yyyy-MM-dd HH:mm:ss K'
```

## 四、只读门禁

执行并记录精确结果：

```powershell
git fetch origin --prune
git checkout <BRANCH>
git pull --ff-only origin <BRANCH>
git status --short
git rev-parse HEAD
git rev-parse origin/main
git merge-base HEAD origin/main
git diff --name-status origin/main...HEAD
git diff --check origin/main...HEAD
git diff --exit-code
```

确认：

```text
工作区初始为空
Head 精确等于 <HEAD_SHA>
未混入其他任务分支
PR 状态与用户授权一致
```

## 五、静默执行函数

```powershell
function Invoke-EvidenceStep {
    param(
        [Parameter(Mandatory)][string]$Name,
        [Parameter(Mandatory)][string]$Command,
        [string[]]$Arguments = @(),
        [string[]]$JUnitPath = @()
    )

    $argumentJson = $Arguments | ConvertTo-Json -Compress
    pwsh -NoProfile -File .\tools\local-verification\Invoke-LocalVerification.ps1 `
        -Name $Name `
        -OutputDirectory $evidenceRoot `
        -Command $Command `
        -CommandArgumentsJson $argumentJson `
        -JUnitPath $JUnitPath

    return $LASTEXITCODE
}
```

## 六、构建与 JVM 示例

```powershell
Invoke-EvidenceStep `
    -Name 'compile-debug' `
    -Command '.\gradlew.bat' `
    -Arguments @('--no-daemon','--console=plain','--warning-mode=summary',':app:compileDebugKotlin')

Invoke-EvidenceStep `
    -Name 'jvm-full' `
    -Command '.\gradlew.bat' `
    -Arguments @('--no-daemon','--console=plain','--warning-mode=summary',':app:testDebugUnitTest') `
    -JUnitPath @('.\app\build\test-results\testDebugUnitTest\TEST-*.xml')
```

若 `jvm-full` 完整通过，并且 JUnit XML 包含本 PR 目标测试类，不再重复执行相同定向 JVM 测试。

## 七、Instrumentation 示例

```powershell
Invoke-EvidenceStep `
    -Name 'instrumentation-full' `
    -Command '.\gradlew.bat' `
    -Arguments @('--no-daemon','--console=plain','--warning-mode=summary','connectedDebugAndroidTest') `
    -JUnitPath @('.\app\build\outputs\androidTest-results\connected\**\TEST-*.xml')
```

若全量失败，再按失败测试类定向重跑；若全量通过且目标测试在 XML 中，禁止重复执行所有定向类。

## 八、失败处理

当步骤为 FAIL：

1. 读取终端给出的失败测试名称；
2. 阅读受限摘录；
3. 查看对应 `steps/<name>.json`；
4. 仍不足时仅查询日志局部：

```powershell
Select-String -Path (Join-Path $evidenceRoot 'logs\<name>.log') `
    -Pattern 'FAILED|FAILURE:|AssertionError|Exception|Caused by:' `
    -Context 3,8 |
    Select-Object -First 80
```

禁止直接执行：

```powershell
Get-Content <完整日志>
```

只有现有证据不足时，才允许为单个失败步骤加入 `--stacktrace` 重跑。

## 九、人工业务场景

人工 UI、TalkBack、进程恢复、Logcat 隐私等场景继续逐项记录：

```text
PASS
FAIL
NOT_VERIFIED
```

人工场景无法由 JUnit XML 替代，也不得因自动化测试通过而推断通过。

## 十、收尾

```powershell
.\gradlew.bat --stop
git status --short
git diff --exit-code
git rev-parse HEAD
Get-Date -Format 'yyyy-MM-dd HH:mm:ss K'
```

必须确认工作区为空、Head 未变化、没有提交或推送。

## 十一、最终报告

最终报告只包含：

```text
精确 PR、Base、Head
环境摘要
每个步骤的 PASS / FAIL / NOT_VERIFIED
退出码
测试总数、通过、失败、错误、跳过
步骤 JSON 路径与日志 SHA-256
失败测试名称和必要摘录
人工场景结果
未验证项
初始与最终工作区状态
```

不得包含完整成功日志、全部通过测试方法或无关 Gradle 输出。
