[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]*$')]
    [string]$Name,

    [Parameter(Mandatory)]
    [string]$OutputDirectory,

    [Parameter(Mandatory)]
    [string]$Command,

    [string]$CommandArgumentsJson = '[]',

    [string[]]$JUnitPath = @(),

    [string]$DisplayCommand,

    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,

    [ValidateRange(1, 1000)]
    [int]$MaxExcerptLines = 120,

    [ValidateRange(128, 1048576)]
    [int]$MaxExcerptBytes = 32768
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$modulePath = Join-Path $PSScriptRoot 'LocalVerification.psm1'
Import-Module $modulePath -Force

try {
    $parsedArguments = $CommandArgumentsJson | ConvertFrom-Json -ErrorAction Stop
    $commandArguments = @($parsedArguments | ForEach-Object { [string]$_ })
} catch {
    Write-Error "CommandArgumentsJson 不是有效 JSON 数组：$($_.Exception.Message)"
    exit 64
}

try {
    $result = Invoke-VerificationStep `
        -Name $Name `
        -OutputDirectory $OutputDirectory `
        -Command $Command `
        -CommandArguments $commandArguments `
        -JUnitPath $JUnitPath `
        -DisplayCommand $DisplayCommand `
        -RepositoryRoot $RepositoryRoot `
        -MaxExcerptLines $MaxExcerptLines `
        -MaxExcerptBytes $MaxExcerptBytes
} catch {
    Write-Error ("验收步骤无法执行：" + $_.Exception.Message)
    exit 70
}

$durationSeconds = [Math]::Round($result.DurationMilliseconds / 1000.0, 1)
if ($null -ne $result.JUnit) {
    $testSummary = "total=$($result.JUnit.Total) | passed=$($result.JUnit.Passed) | failed=$($result.JUnit.Failed) | errors=$($result.JUnit.Errors) | skipped=$($result.JUnit.Skipped)"
} else {
    $testSummary = 'tests=n/a'
}

Write-Host "[$($result.Status)] $($result.Name) | exit=$($result.ExitCode) | $testSummary | duration=${durationSeconds}s"
Write-Host "stepJson=$($result.EvidencePath)"
Write-Host "logSha256=$($result.LogSha256)"

if ($result.Status -eq 'FAIL') {
    if ($null -ne $result.JUnit) {
        foreach ($failedTest in @($result.JUnit.FailedTests)) {
            Write-Host "failedTest=$failedTest"
        }
    }
    if ($null -ne $result.FailureExcerpt) {
        foreach ($line in @($result.FailureExcerpt.Lines)) {
            Write-Host $line
        }
        if ($result.FailureExcerpt.Truncated) {
            Write-Host '[TRUNCATED] 失败摘录达到输出上限；完整日志仅保存在 logPath。'
        }
    }
    Write-Host "logPath=$($result.LogPath)"
    if ($result.ExitCode -gt 0 -and $result.ExitCode -le 255) {
        exit $result.ExitCode
    }
    exit 1
}

if ($result.Status -eq 'NOT_VERIFIED') {
    if ($null -ne $result.JUnit) {
        foreach ($warning in @($result.JUnit.Warnings)) {
            Write-Host "warning=$warning"
        }
    }
    exit 3
}

exit 0
