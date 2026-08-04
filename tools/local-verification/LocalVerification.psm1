Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Protect-VerificationDisplayText {
    [CmdletBinding()]
    param(
        [AllowEmptyString()]
        [string]$Text
    )

    if ($null -eq $Text) {
        return ''
    }

    $protected = [regex]::Replace(
        $Text,
        '(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+',
        'Bearer [REDACTED]'
    )
    $protected = [regex]::Replace(
        $protected,
        'AIza[0-9A-Za-z_-]{10,}',
        '[REDACTED_API_KEY]'
    )
    $protected = [regex]::Replace(
        $protected,
        '(?i)\b(api[_-]?key|token|secret|password)\b(\s*[:=]\s*)([^\s,;]+)',
        {
            param($match)
            return $match.Groups[1].Value + $match.Groups[2].Value + '[REDACTED]'
        }
    )
    return $protected
}

function Resolve-JUnitFiles {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string[]]$Path
    )

    $files = [System.Collections.Generic.Dictionary[string, System.IO.FileInfo]]::new(
        [System.StringComparer]::OrdinalIgnoreCase
    )

    foreach ($candidate in $Path) {
        if ([string]::IsNullOrWhiteSpace($candidate)) {
            continue
        }

        if (Test-Path -LiteralPath $candidate -PathType Container) {
            $matches = @(Get-ChildItem -LiteralPath $candidate -File -Filter '*.xml' -Recurse -ErrorAction SilentlyContinue)
        } else {
            $matches = @(Get-ChildItem -Path $candidate -File -ErrorAction SilentlyContinue)
        }

        foreach ($file in $matches) {
            $files[$file.FullName] = $file
        }
    }

    return @($files.Values | Sort-Object FullName)
}

function ConvertFrom-JUnitEvidence {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string[]]$Path
    )

    $files = @(Resolve-JUnitFiles -Path $Path)
    $warnings = [System.Collections.Generic.List[string]]::new()
    $testCases = @{}
    $severity = @{
        passed = 0
        skipped = 1
        failure = 2
        error = 3
    }

    if ($files.Count -eq 0) {
        return [pscustomobject][ordered]@{
            Status = 'NOT_VERIFIED'
            Total = 0
            Passed = 0
            Failed = 0
            Errors = 0
            Skipped = 0
            FailedTests = @()
            Files = @()
            Warnings = @('未找到匹配的 JUnit XML。')
        }
    }

    foreach ($file in $files) {
        try {
            [xml]$document = Get-Content -LiteralPath $file.FullName -Raw -ErrorAction Stop
            $nodes = @($document.SelectNodes('//testcase'))
            foreach ($node in $nodes) {
                $className = [string]$node.GetAttribute('classname')
                $testName = [string]$node.GetAttribute('name')
                if ([string]::IsNullOrWhiteSpace($className)) {
                    $className = '<unknown-class>'
                }
                if ([string]::IsNullOrWhiteSpace($testName)) {
                    $testName = '<unknown-test>'
                }

                $status = 'passed'
                $message = $null
                $errorNode = $node.SelectSingleNode('./error')
                $failureNode = $node.SelectSingleNode('./failure')
                $skippedNode = $node.SelectSingleNode('./skipped')
                if ($null -ne $errorNode) {
                    $status = 'error'
                    $message = [string]$errorNode.GetAttribute('message')
                } elseif ($null -ne $failureNode) {
                    $status = 'failure'
                    $message = [string]$failureNode.GetAttribute('message')
                } elseif ($null -ne $skippedNode) {
                    $status = 'skipped'
                    $message = [string]$skippedNode.GetAttribute('message')
                }

                $identity = "$className::$testName"
                $candidateCase = [pscustomobject][ordered]@{
                    Identity = $identity
                    ClassName = $className
                    Name = $testName
                    Status = $status
                    Message = $message
                    SourceFile = $file.FullName
                }

                if (-not $testCases.ContainsKey($identity)) {
                    $testCases[$identity] = $candidateCase
                } elseif ($severity[$status] -gt $severity[$testCases[$identity].Status]) {
                    $testCases[$identity] = $candidateCase
                }
            }
        } catch {
            $warnings.Add("JUnit XML 解析失败：$($file.FullName)：$($_.Exception.Message)")
        }
    }

    $cases = @($testCases.Values | Sort-Object Identity)
    if ($cases.Count -eq 0) {
        return [pscustomobject][ordered]@{
            Status = 'NOT_VERIFIED'
            Total = 0
            Passed = 0
            Failed = 0
            Errors = 0
            Skipped = 0
            FailedTests = @()
            Files = @($files.FullName)
            Warnings = @($warnings.ToArray()) + @('JUnit XML 中没有可统计的 testcase。')
        }
    }

    $passed = @($cases | Where-Object Status -eq 'passed').Count
    $failed = @($cases | Where-Object Status -eq 'failure').Count
    $errors = @($cases | Where-Object Status -eq 'error').Count
    $skipped = @($cases | Where-Object Status -eq 'skipped').Count
    $failedTests = @(
        $cases |
            Where-Object { $_.Status -in @('failure', 'error') } |
            ForEach-Object Identity
    )

    return [pscustomobject][ordered]@{
        Status = if (($failed + $errors) -gt 0) { 'FAIL' } else { 'PASS' }
        Total = $cases.Count
        Passed = $passed
        Failed = $failed
        Errors = $errors
        Skipped = $skipped
        FailedTests = $failedTests
        Files = @($files.FullName)
        Warnings = @($warnings.ToArray())
    }
}

function Get-BoundedFailureExcerpt {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$LogPath,
        [ValidateRange(1, 1000)]
        [int]$MaxLines = 120,
        [ValidateRange(128, 1048576)]
        [int]$MaxBytes = 32768,
        [ValidateRange(0, 50)]
        [int]$Before = 3,
        [ValidateRange(0, 100)]
        [int]$After = 8,
        [string[]]$Pattern = @(
            'FAILED',
            'FAILURE:',
            'AssertionError',
            'Exception',
            'Caused by:',
            'There were failing tests',
            'Process .* finished with non-zero exit value'
        )
    )

    if (-not (Test-Path -LiteralPath $LogPath -PathType Leaf)) {
        return [pscustomobject][ordered]@{
            Lines = @()
            ByteCount = 0
            Truncated = $false
            MatchCount = 0
            Warning = "日志不存在：$LogPath"
        }
    }

    $compiledPatterns = @($Pattern | ForEach-Object { [regex]::new($_, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase) })
    $matchLines = [System.Collections.Generic.List[int]]::new()
    $lineNumber = 0
    foreach ($line in [System.IO.File]::ReadLines($LogPath)) {
        $lineNumber++
        foreach ($regex in $compiledPatterns) {
            if ($regex.IsMatch($line)) {
                $matchLines.Add($lineNumber)
                break
            }
        }
    }

    if ($matchLines.Count -eq 0) {
        return [pscustomobject][ordered]@{
            Lines = @()
            ByteCount = 0
            Truncated = $false
            MatchCount = 0
            Warning = $null
        }
    }

    $ranges = [System.Collections.Generic.List[object]]::new()
    foreach ($matchLine in $matchLines) {
        $start = [Math]::Max(1, $matchLine - $Before)
        $end = $matchLine + $After
        if ($ranges.Count -eq 0 -or $start -gt ($ranges[$ranges.Count - 1].End + 1)) {
            $ranges.Add([pscustomobject]@{ Start = $start; End = $end })
        } else {
            $ranges[$ranges.Count - 1].End = [Math]::Max($ranges[$ranges.Count - 1].End, $end)
        }
    }

    $selected = [System.Collections.Generic.List[string]]::new()
    $byteCount = 0
    $rangeIndex = 0
    $lineNumber = 0
    $truncated = $false
    $utf8 = [System.Text.UTF8Encoding]::new($false)

    foreach ($line in [System.IO.File]::ReadLines($LogPath)) {
        $lineNumber++
        while ($rangeIndex -lt $ranges.Count -and $lineNumber -gt $ranges[$rangeIndex].End) {
            $rangeIndex++
        }
        if ($rangeIndex -ge $ranges.Count) {
            break
        }
        if ($lineNumber -lt $ranges[$rangeIndex].Start) {
            continue
        }

        $displayLine = Protect-VerificationDisplayText -Text $line
        $lineBytes = $utf8.GetByteCount($displayLine + "`n")
        if ($selected.Count -ge $MaxLines -or ($byteCount + $lineBytes) -gt $MaxBytes) {
            $truncated = $true
            break
        }
        $selected.Add($displayLine)
        $byteCount += $lineBytes
    }

    return [pscustomobject][ordered]@{
        Lines = @($selected.ToArray())
        ByteCount = $byteCount
        Truncated = $truncated
        MatchCount = $matchLines.Count
        Warning = $null
    }
}

function Assert-ExternalOutputDirectory {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string]$OutputDirectory,
        [Parameter(Mandatory)]
        [string]$RepositoryRoot
    )

    $outputFull = [System.IO.Path]::GetFullPath($OutputDirectory).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $repoFull = [System.IO.Path]::GetFullPath($RepositoryRoot).TrimEnd([System.IO.Path]::DirectorySeparatorChar, [System.IO.Path]::AltDirectorySeparatorChar)
    $repoPrefix = $repoFull + [System.IO.Path]::DirectorySeparatorChar

    if ($outputFull.Equals($repoFull, [System.StringComparison]::OrdinalIgnoreCase) -or
        $outputFull.StartsWith($repoPrefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "验收输出目录必须位于仓库外：output=$outputFull repo=$repoFull"
    }

    return $outputFull
}

function Invoke-VerificationStep {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]*$')]
        [string]$Name,
        [Parameter(Mandatory)]
        [string]$OutputDirectory,
        [Parameter(Mandatory)]
        [string]$Command,
        [string[]]$CommandArguments = @(),
        [string[]]$JUnitPath = @(),
        [string]$DisplayCommand,
        [Parameter(Mandatory)]
        [string]$RepositoryRoot,
        [ValidateRange(1, 1000)]
        [int]$MaxExcerptLines = 120,
        [ValidateRange(128, 1048576)]
        [int]$MaxExcerptBytes = 32768
    )

    $outputFull = Assert-ExternalOutputDirectory -OutputDirectory $OutputDirectory -RepositoryRoot $RepositoryRoot
    $logDirectory = Join-Path $outputFull 'logs'
    $stepDirectory = Join-Path $outputFull 'steps'
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    New-Item -ItemType Directory -Path $stepDirectory -Force | Out-Null

    $logPath = Join-Path $logDirectory "$Name.log"
    $evidencePath = Join-Path $stepDirectory "$Name.json"
    Remove-Item -LiteralPath $logPath -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath $evidencePath -Force -ErrorAction SilentlyContinue

    if ([string]::IsNullOrWhiteSpace($DisplayCommand)) {
        $displayParts = @($Command) + @($CommandArguments)
        $DisplayCommand = ($displayParts | ForEach-Object { Protect-VerificationDisplayText -Text ([string]$_) }) -join ' '
    } else {
        $DisplayCommand = Protect-VerificationDisplayText -Text $DisplayCommand
    }

    $startedAt = [DateTimeOffset]::UtcNow
    $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
    $exitCode = 127
    try {
        $global:LASTEXITCODE = 0
        & $Command @CommandArguments *> $logPath
        $exitCode = [int]$LASTEXITCODE
    } catch {
        $errorText = $_ | Out-String
        [System.IO.File]::AppendAllText($logPath, $errorText, [System.Text.UTF8Encoding]::new($false))
        $exitCode = 127
    } finally {
        $stopwatch.Stop()
    }
    $endedAt = [DateTimeOffset]::UtcNow

    if (-not (Test-Path -LiteralPath $logPath -PathType Leaf)) {
        [System.IO.File]::WriteAllText($logPath, '', [System.Text.UTF8Encoding]::new($false))
    }
    $logSha256 = (Get-FileHash -LiteralPath $logPath -Algorithm SHA256).Hash.ToLowerInvariant()

    $junit = $null
    if ($JUnitPath.Count -gt 0) {
        $junit = ConvertFrom-JUnitEvidence -Path $JUnitPath
    }

    $status = 'PASS'
    if ($exitCode -ne 0) {
        $status = 'FAIL'
    } elseif ($null -ne $junit -and $junit.Status -eq 'FAIL') {
        $status = 'FAIL'
    } elseif ($null -ne $junit -and $junit.Status -ne 'PASS') {
        $status = 'NOT_VERIFIED'
    }

    $failureExcerpt = $null
    if ($status -eq 'FAIL') {
        $failureExcerpt = Get-BoundedFailureExcerpt `
            -LogPath $logPath `
            -MaxLines $MaxExcerptLines `
            -MaxBytes $MaxExcerptBytes
    }

    $evidence = [pscustomobject][ordered]@{
        SchemaVersion = 1
        Name = $Name
        Status = $status
        DisplayCommand = $DisplayCommand
        ExitCode = $exitCode
        StartedAt = $startedAt.ToString('o')
        EndedAt = $endedAt.ToString('o')
        DurationMilliseconds = [int64]$stopwatch.ElapsedMilliseconds
        LogPath = [System.IO.Path]::GetFullPath($logPath)
        LogSha256 = $logSha256
        JUnit = $junit
        FailureExcerpt = $failureExcerpt
        EvidencePath = [System.IO.Path]::GetFullPath($evidencePath)
    }

    $json = $evidence | ConvertTo-Json -Depth 12
    [System.IO.File]::WriteAllText($evidencePath, $json, [System.Text.UTF8Encoding]::new($false))
    return $evidence
}

function Write-VerificationSummary {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)]
        [string[]]$StepPath,
        [Parameter(Mandatory)]
        [string]$OutputDirectory,
        [Parameter(Mandatory)]
        [string]$Repository,
        [Parameter(Mandatory)]
        [string]$BaseSha,
        [Parameter(Mandatory)]
        [string]$HeadSha
    )

    $steps = [System.Collections.Generic.List[object]]::new()
    foreach ($path in $StepPath) {
        try {
            $step = Get-Content -LiteralPath $path -Raw -ErrorAction Stop | ConvertFrom-Json -ErrorAction Stop
            $steps.Add($step)
        } catch {
            $steps.Add([pscustomobject][ordered]@{
                Name = [System.IO.Path]::GetFileNameWithoutExtension($path)
                Status = 'NOT_VERIFIED'
                ExitCode = $null
                EvidencePath = $path
                Warning = $_.Exception.Message
            })
        }
    }

    $status = 'PASS'
    if (@($steps | Where-Object Status -eq 'FAIL').Count -gt 0) {
        $status = 'FAIL'
    } elseif (@($steps | Where-Object Status -eq 'NOT_VERIFIED').Count -gt 0) {
        $status = 'NOT_VERIFIED'
    }

    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $jsonPath = Join-Path $OutputDirectory 'verification-summary.json'
    $markdownPath = Join-Path $OutputDirectory 'verification-summary.md'
    $summary = [pscustomobject][ordered]@{
        schemaVersion = 1
        repository = $Repository
        baseSha = $BaseSha
        headSha = $HeadSha
        generatedAt = [DateTimeOffset]::UtcNow.ToString('o')
        status = $status
        steps = @($steps.ToArray())
    }

    [System.IO.File]::WriteAllText(
        $jsonPath,
        ($summary | ConvertTo-Json -Depth 14),
        [System.Text.UTF8Encoding]::new($false)
    )

    $markdown = [System.Collections.Generic.List[string]]::new()
    $markdown.Add('# 本地验收证据摘要')
    $markdown.Add('')
    $markdown.Add("- Repository：``$Repository``")
    $markdown.Add("- Base：``$BaseSha``")
    $markdown.Add("- Head：``$HeadSha``")
    $markdown.Add("- Result：**$status**")
    $markdown.Add('')
    $markdown.Add('| Step | Status | Exit | Tests | Log SHA-256 |')
    $markdown.Add('|---|---:|---:|---:|---|')
    foreach ($step in $steps) {
        $tests = '-'
        if ($null -ne $step.PSObject.Properties['JUnit'] -and $null -ne $step.JUnit) {
            $tests = "$($step.JUnit.Passed)/$($step.JUnit.Total), fail=$($step.JUnit.Failed), error=$($step.JUnit.Errors), skip=$($step.JUnit.Skipped)"
        }
        $hash = if ($null -ne $step.PSObject.Properties['LogSha256']) { [string]$step.LogSha256 } else { '-' }
        $exit = if ($null -ne $step.PSObject.Properties['ExitCode'] -and $null -ne $step.ExitCode) { [string]$step.ExitCode } else { '-' }
        $safeName = ([string]$step.Name).Replace('|', '\|')
        $markdown.Add("| $safeName | $($step.Status) | $exit | $tests | $hash |")
    }
    [System.IO.File]::WriteAllLines($markdownPath, $markdown, [System.Text.UTF8Encoding]::new($false))

    return [pscustomobject][ordered]@{
        Status = $status
        StepCount = $steps.Count
        JsonPath = [System.IO.Path]::GetFullPath($jsonPath)
        MarkdownPath = [System.IO.Path]::GetFullPath($markdownPath)
    }
}

Export-ModuleMember -Function @(
    'ConvertFrom-JUnitEvidence',
    'Get-BoundedFailureExcerpt',
    'Invoke-VerificationStep',
    'Write-VerificationSummary'
)
