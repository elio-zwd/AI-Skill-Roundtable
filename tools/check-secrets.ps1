param(
    [switch]$IncludeHistory
)

$ErrorActionPreference = "Stop"
$patterns = @(
    'AIza[0-9A-Za-z_-]{20,}'
)
$combinedPattern = $patterns -join '|'
$violations = [System.Collections.Generic.List[string]]::new()

$trackedSensitiveFiles = git ls-files -- .env local.properties secrets.properties keystore.properties '*.jks' '*.keystore' '*.p12' '*.pem'
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
$stagedAddedLines = $stagedPatch | Where-Object { $_ -match '^\+' -and $_ -notmatch '^\+\+\+' }
foreach ($match in ($stagedAddedLines | Select-String -Pattern $combinedPattern)) {
    $violations.Add("暂存区疑似密钥: 第 $($match.LineNumber) 行")
}

if ($IncludeHistory) {
    # PR09-09 曾通过固定路径上传压缩源码补丁；这些文件已删除，且其每个分片均经过
    # Git Blob 与 SHA-256 双重校验。随机 Base64 片段可能偶然匹配 API Key 正则。
    # 这里只排除这两类已删除的传输分片历史；工作树、暂存区和所有生产源码仍完整扫描。
    $gitLogArgs = @(
        'log',
        '-p',
        '--no-color',
        'HEAD',
        '--',
        '.',
        ':!*.apk',
        ':!*.aab',
        ':(exclude).github/pr09-core.patch.gz.b64.part-*',
        ':(exclude).github/pr09-ui.patch.gz.b64.part-*'
    )
    $historyMatches = & git $gitLogArgs | Select-String -Pattern $combinedPattern
    # 早期生成器单测曾提交一个明确的示例字符串。仅跳过该历史 diff 行的精确哈希，
    # 仍扫描同文件其他历史行及当前工作树，避免把测试目录整体排除。
    $knownFixtureLineHashes = @(
        'd7d29c4ab8882f8a966cdc45756654ce18c510ce67105a8e353824e5e4f8af5d',
        'c9fa7bec547044b9be0855553829c6a309381875ff69df70ea9a307a9e256dd7'
    )
    foreach ($match in $historyMatches) {
        $lineBytes = [System.Text.Encoding]::UTF8.GetBytes($match.Line)
        $lineHash = [Convert]::ToHexString(
            [System.Security.Cryptography.SHA256]::HashData($lineBytes)
        ).ToLowerInvariant()
        if ($lineHash -in $knownFixtureLineHashes) { continue }
        $violations.Add("HEAD 可达历史疑似密钥: 第 $($match.LineNumber) 行")
    }
}

if ($violations.Count -gt 0) {
    $violations | ForEach-Object { Write-Error $_ }
    exit 1
}

Write-Host "OK: 未在检查范围内发现 Gemini API Key 或禁止跟踪的敏感文件。"
