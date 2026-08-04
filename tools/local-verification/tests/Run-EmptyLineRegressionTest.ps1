Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$toolRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$modulePath = Join-Path $toolRoot 'local-verification\LocalVerification.psm1'
Import-Module $modulePath -Force

$assertions = 0
function Assert-True {
    param(
        [Parameter(Mandatory)]
        [bool]$Condition,
        [Parameter(Mandatory)]
        [string]$Message
    )

    $script:assertions++
    if (-not $Condition) {
        throw "断言失败：$Message"
    }
}

$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("jianyu-empty-line-regression-" + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempRoot -Force | Out-Null

try {
    $logPath = Join-Path $tempRoot 'gradle-like-failure.log'
    $content = @(
        'FAILURE: Build failed with an exception.',
        '',
        '* What went wrong:',
        '',
        'Execution failed for task compileDebugKotlin.'
    ) -join [Environment]::NewLine
    [System.IO.File]::WriteAllText($logPath, $content, [System.Text.UTF8Encoding]::new($false))

    $excerpt = Get-BoundedFailureExcerpt -LogPath $logPath -MaxLines 12 -MaxBytes 2048

    Assert-True ($excerpt.Lines.Count -gt 0) '包含空行的真实 Gradle 风格日志不得使摘录器崩溃'
    Assert-True ($excerpt.Lines -contains '') '失败摘录应允许并保留范围内的空行'
    Assert-True (($excerpt.Lines -join "`n") -match 'Execution failed for task') '失败摘录应保留空行后的诊断内容'

    Write-Host "[PASS] empty-line-regression | assertions=$assertions"
}
finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
