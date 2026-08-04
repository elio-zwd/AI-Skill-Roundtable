Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$toolRoot = Split-Path -Parent $PSScriptRoot
$modulePath = Join-Path $toolRoot 'LocalVerification.psm1'
$cliPath = Join-Path $toolRoot 'Invoke-LocalVerification.ps1'

if (-not (Test-Path -LiteralPath $modulePath -PathType Leaf)) {
    throw "RED：验收工具模块尚不存在：$modulePath"
}
if (-not (Test-Path -LiteralPath $cliPath -PathType Leaf)) {
    throw "RED：验收工具 CLI 尚不存在：$cliPath"
}

Import-Module $modulePath -Force

$script:AssertionCount = 0

function Assert-True {
    param(
        [Parameter(Mandatory)]
        [bool]$Condition,
        [Parameter(Mandatory)]
        [string]$Message
    )
    $script:AssertionCount++
    if (-not $Condition) {
        throw "断言失败：$Message"
    }
}

function Assert-Equal {
    param(
        [Parameter(Mandatory)]
        $Expected,
        [Parameter(Mandatory)]
        $Actual,
        [Parameter(Mandatory)]
        [string]$Message
    )
    $script:AssertionCount++
    if ($Expected -ne $Actual) {
        throw "断言失败：$Message；expected=[$Expected] actual=[$Actual]"
    }
}

function Write-Utf8File {
    param(
        [Parameter(Mandatory)]
        [string]$Path,
        [Parameter(Mandatory)]
        [string]$Content
    )
    $parent = Split-Path -Parent $Path
    if ($parent) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    [System.IO.File]::WriteAllText($Path, $Content, [System.Text.UTF8Encoding]::new($false))
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("jianyu-local-verification-tests-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

try {
    $junitRoot = Join-Path $tempRoot 'junit'
    New-Item -ItemType Directory -Path $junitRoot -Force | Out-Null

    Write-Utf8File -Path (Join-Path $junitRoot 'TEST-pass-a.xml') -Content @'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="PassA" tests="2" failures="0" errors="0" skipped="0">
  <testcase classname="example.PassA" name="first" time="0.01" />
  <testcase classname="example.PassA" name="second" time="0.02" />
</testsuite>
'@

    Write-Utf8File -Path (Join-Path $junitRoot 'TEST-pass-b.xml') -Content @'
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="PassB" tests="2" failures="0" errors="0" skipped="1">
    <testcase classname="example.PassB" name="third" time="0.03" />
    <testcase classname="example.PassB" name="skipped"><skipped message="disabled" /></testcase>
  </testsuite>
</testsuites>
'@

    $passEvidence = ConvertFrom-JUnitEvidence -Path @((Join-Path $junitRoot 'TEST-pass-*.xml'))
    Assert-Equal 'PASS' $passEvidence.Status '可解析且无失败的 JUnit 应为 PASS'
    Assert-Equal 4 $passEvidence.Total '多文件测试总数'
    Assert-Equal 3 $passEvidence.Passed '通过数'
    Assert-Equal 0 $passEvidence.Failed '失败数'
    Assert-Equal 0 $passEvidence.Errors '错误数'
    Assert-Equal 1 $passEvidence.Skipped '跳过数'

    Write-Utf8File -Path (Join-Path $junitRoot 'TEST-duplicate-and-failure.xml') -Content @'
<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="Failure" tests="3" failures="1" errors="1" skipped="0">
  <testcase classname="example.PassA" name="first" time="0.01" />
  <testcase classname="example.Failure" name="fails"><failure message="expected true">stack</failure></testcase>
  <testcase classname="example.Failure" name="errors"><error message="boom">stack</error></testcase>
</testsuite>
'@

    $failureEvidence = ConvertFrom-JUnitEvidence -Path @((Join-Path $junitRoot 'TEST-*.xml'))
    Assert-Equal 'FAIL' $failureEvidence.Status 'failure/error 应使状态为 FAIL'
    Assert-Equal 6 $failureEvidence.Total '重复测试身份不得重复计数'
    Assert-Equal 1 $failureEvidence.Failed 'failure 数量'
    Assert-Equal 1 $failureEvidence.Errors 'error 数量'
    Assert-Equal 2 $failureEvidence.FailedTests.Count '失败测试名称数量'
    Assert-True ($failureEvidence.FailedTests -contains 'example.Failure::fails') '包含 failure 测试身份'
    Assert-True ($failureEvidence.FailedTests -contains 'example.Failure::errors') '包含 error 测试身份'

    $missingEvidence = ConvertFrom-JUnitEvidence -Path @((Join-Path $junitRoot 'DOES-NOT-EXIST-*.xml'))
    Assert-Equal 'NOT_VERIFIED' $missingEvidence.Status '缺失 XML 不得误报 PASS'
    Assert-Equal 0 $missingEvidence.Total '缺失 XML 测试数为零'

    Write-Utf8File -Path (Join-Path $junitRoot 'TEST-malformed.xml') -Content '<testsuite><testcase>'
    $malformedEvidence = ConvertFrom-JUnitEvidence -Path @((Join-Path $junitRoot 'TEST-malformed.xml'))
    Assert-Equal 'NOT_VERIFIED' $malformedEvidence.Status '损坏 XML 不得误报 PASS'
    Assert-True ($malformedEvidence.Warnings.Count -gt 0) '损坏 XML 应包含警告'

    Write-Utf8File -Path (Join-Path $junitRoot 'TEST-zero.xml') -Content '<testsuite name="zero" tests="0" />'
    $zeroEvidence = ConvertFrom-JUnitEvidence -Path @((Join-Path $junitRoot 'TEST-zero.xml'))
    Assert-Equal 'NOT_VERIFIED' $zeroEvidence.Status '零测试 XML 不得误报 PASS'

    $logPath = Join-Path $tempRoot 'failure.log'
    $logLines = @()
    1..20 | ForEach-Object { $logLines += "normal-before-$_" }
    $logLines += 'Authorization: Bearer super-secret-token'
    $logLines += 'apiKey=AIza123456789012345678901234567890'
    $logLines += 'java.lang.AssertionError: expected true'
    $logLines += 'Caused by: java.lang.IllegalStateException: broken'
    1..200 | ForEach-Object { $logLines += "normal-after-$_" }
    Write-Utf8File -Path $logPath -Content ($logLines -join [Environment]::NewLine)

    $excerpt = Get-BoundedFailureExcerpt -LogPath $logPath -MaxLines 12 -MaxBytes 1024
    Assert-True ($excerpt.Lines.Count -le 12) '失败摘录不得超过行数上限'
    Assert-True ($excerpt.ByteCount -le 1024) '失败摘录不得超过字节上限'
    $excerptText = $excerpt.Lines -join "`n"
    Assert-True ($excerptText -match 'AssertionError') '失败摘录应包含断言错误'
    Assert-True ($excerptText -notmatch 'super-secret-token') 'Bearer Token 不得进入展示摘录'
    Assert-True ($excerptText -notmatch 'AIza123456789012345678901234567890') 'AIza 特征不得进入展示摘录'
    Assert-True ((Get-Content -LiteralPath $logPath -Raw) -match 'super-secret-token') '展示脱敏不得修改原始日志'

    $emitSuccess = Join-Path $tempRoot 'emit-success.ps1'
    Write-Utf8File -Path $emitSuccess -Content @'
[Console]::Out.WriteLine('RAW_STDOUT_SHOULD_STAY_IN_LOG')
[Console]::Error.WriteLine('RAW_STDERR_SHOULD_STAY_IN_LOG')
exit 0
'@
    $emitFailure = Join-Path $tempRoot 'emit-failure.ps1'
    Write-Utf8File -Path $emitFailure -Content @'
[Console]::Error.WriteLine('FAILURE: intentional failure')
[Console]::Error.WriteLine('password=do-not-display')
exit 2
'@

    $pwsh = (Get-Process -Id $PID).Path
    $evidenceRoot = Join-Path $tempRoot 'evidence'

    $successStep = Invoke-VerificationStep `
        -Name 'success-step' `
        -OutputDirectory $evidenceRoot `
        -Command $pwsh `
        -CommandArguments @('-NoProfile', '-File', $emitSuccess) `
        -RepositoryRoot (Split-Path -Parent (Split-Path -Parent $toolRoot))

    Assert-Equal 'PASS' $successStep.Status '退出码零且未声明 JUnit 的步骤应 PASS'
    Assert-Equal 0 $successStep.ExitCode '成功步骤退出码'
    Assert-True (Test-Path -LiteralPath $successStep.LogPath) '成功日志应存在'
    Assert-True (Test-Path -LiteralPath $successStep.EvidencePath) '成功步骤 JSON 应存在'
    $successLog = Get-Content -LiteralPath $successStep.LogPath -Raw
    Assert-True ($successLog -match 'RAW_STDOUT_SHOULD_STAY_IN_LOG') 'stdout 应写入日志'
    Assert-True ($successLog -match 'RAW_STDERR_SHOULD_STAY_IN_LOG') 'stderr 应写入日志'
    Assert-Equal (Get-FileHash -LiteralPath $successStep.LogPath -Algorithm SHA256).Hash.ToLowerInvariant() $successStep.LogSha256 '日志 Hash 应可复算'

    $failureStep = Invoke-VerificationStep `
        -Name 'failure-step' `
        -OutputDirectory $evidenceRoot `
        -Command $pwsh `
        -CommandArguments @('-NoProfile', '-File', $emitFailure) `
        -RepositoryRoot (Split-Path -Parent (Split-Path -Parent $toolRoot))

    Assert-Equal 'FAIL' $failureStep.Status '非零退出码应 FAIL'
    Assert-Equal 2 $failureStep.ExitCode '非零退出码不得被覆盖'
    Assert-True ($failureStep.FailureExcerpt.Lines.Count -gt 0) '失败步骤应包含有界摘录'
    Assert-True (($failureStep.FailureExcerpt.Lines -join "`n") -notmatch 'do-not-display') '失败展示应屏蔽密码'

    $missingJUnitStep = Invoke-VerificationStep `
        -Name 'missing-junit-step' `
        -OutputDirectory $evidenceRoot `
        -Command $pwsh `
        -CommandArguments @('-NoProfile', '-File', $emitSuccess) `
        -JUnitPath @((Join-Path $tempRoot 'missing-results\TEST-*.xml')) `
        -RepositoryRoot (Split-Path -Parent (Split-Path -Parent $toolRoot))

    Assert-Equal 'NOT_VERIFIED' $missingJUnitStep.Status '命令成功但测试证据缺失时应 NOT_VERIFIED'

    $summary = Write-VerificationSummary `
        -StepPath @($successStep.EvidencePath, $failureStep.EvidencePath, $missingJUnitStep.EvidencePath) `
        -OutputDirectory $evidenceRoot `
        -Repository 'elio-zwd/AI-Skill-Roundtable' `
        -BaseSha 'base-test' `
        -HeadSha 'head-test'

    Assert-Equal 'FAIL' $summary.Status '总体状态优先级应以 FAIL 为最高'
    Assert-True (Test-Path -LiteralPath $summary.JsonPath) '总体 JSON 应存在'
    Assert-True (Test-Path -LiteralPath $summary.MarkdownPath) '总体 Markdown 应存在'
    $summaryJson = Get-Content -LiteralPath $summary.JsonPath -Raw | ConvertFrom-Json
    Assert-Equal 3 $summaryJson.steps.Count '总体 JSON 步骤数'
    Assert-Equal 'FAIL' $summaryJson.status '总体 JSON 状态'

    $cliOutputRoot = Join-Path $tempRoot 'cli-evidence'
    $cliCapture = Join-Path $tempRoot 'cli-output.txt'
    $argumentJson = @('-NoProfile', '-File', $emitSuccess) | ConvertTo-Json -Compress
    $cliArguments = @(
        '-NoProfile', '-File', $cliPath,
        '-Name', 'cli-success',
        '-OutputDirectory', $cliOutputRoot,
        '-Command', $pwsh,
        '-CommandArgumentsJson', $argumentJson,
        '-RepositoryRoot', (Split-Path -Parent (Split-Path -Parent $toolRoot))
    )
    & $pwsh @cliArguments *> $cliCapture
    $cliExit = $LASTEXITCODE
    Assert-Equal 0 $cliExit '成功 CLI 退出码'
    $cliText = Get-Content -LiteralPath $cliCapture -Raw
    Assert-True ($cliText -match '\[PASS\] cli-success') 'CLI 应输出紧凑 PASS 行'
    Assert-True ($cliText -notmatch 'RAW_STDOUT_SHOULD_STAY_IN_LOG') 'CLI 不得回灌原始 stdout'
    Assert-True ($cliText -notmatch 'RAW_STDERR_SHOULD_STAY_IN_LOG') 'CLI 不得回灌原始 stderr'

    Write-Host "[PASS] local-verification-tools | assertions=$script:AssertionCount"
}
finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
