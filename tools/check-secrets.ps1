param(
    [switch]$IncludeHistory
)

$ErrorActionPreference = "Stop"
$patterns = @(
    'AIza[0-9A-Za-z_-]{20,}'
)
$combinedPattern = $patterns -join '|'
$violations = [System.Collections.Generic.List[string]]::new()

$trackedSensitiveFiles = git ls-files -- .env local.properties secrets.properties '*.jks' '*.keystore' '*.p12' '*.pem'
if ($LASTEXITCODE -ne 0) {
    throw "无法读取 Git 跟踪文件列表。"
}
foreach ($file in $trackedSensitiveFiles) {
    if ($file) { $violations.Add("禁止跟踪敏感文件: $file") }
}

$gitGrepArgs = @('grep', '-n', '-I', '-E', $combinedPattern, '--', ':!*.apk', ':!*.aab')
$workingMatches = & git $gitGrepArgs 2>$null
if ($LASTEXITCODE -notin @(0, 1)) {
    throw "工作树密钥扫描失败。"
}
foreach ($match in $workingMatches) {
    $violations.Add("工作树疑似密钥: $($match -replace ':.+$', ': [已遮蔽]')")
}

$gitArchArgs = @('grep', '-n', '-I', '-E', 'REDACTED_GEMINI_API_KEY|BuildConfig\.GEMINI_API_KEY', '--', ':!tools/check-secrets.ps1', ':!docs/*')
$forbiddenArchitecture = & git $gitArchArgs 2>$null
if ($LASTEXITCODE -notin @(0, 1)) {
    throw "密钥架构扫描失败。"
}
foreach ($match in $forbiddenArchitecture) {
    $violations.Add("工作树仍包含旧密钥架构: $($match -replace ':.+$', ': [已遮蔽]')")
}

$stagedPatch = git diff --cached --unified=0 --no-color
foreach ($match in ($stagedPatch | Select-String -Pattern $combinedPattern)) {
    $violations.Add("暂存区疑似密钥: 第 $($match.LineNumber) 行")
}

if ($IncludeHistory) {
    $gitLogArgs = @('log', '-p', '--no-color', 'HEAD', '--', '.', ':!*.apk', ':!*.aab')
    $historyMatches = & git $gitLogArgs | Select-String -Pattern $combinedPattern
    foreach ($match in $historyMatches) {
        $violations.Add("HEAD 可达历史疑似密钥: 第 $($match.LineNumber) 行")
    }
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "OK: 未在检查范围内发现 Gemini API Key 或禁止跟踪的敏感文件。"
