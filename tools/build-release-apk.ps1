[CmdletBinding()]
param(
    [Parameter()]
    [ValidatePattern('^\d+(?:\.\d+){1,3}(?:-[0-9A-Za-z.-]+)?$')]
    [string]$VersionName = '1.1',

    [Parameter()]
    [ValidateRange(1, 2147483647)]
    [int]$VersionCode = 2
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$signingPropertiesPath = Join-Path $projectRoot 'keystore.properties'
$requiredSigningKeys = @('storeFile', 'storePassword', 'keyAlias', 'keyPassword')
$environmentSigningKeys = @{
    storeFile = 'RELEASE_STORE_FILE'
    storePassword = 'RELEASE_STORE_PASSWORD'
    keyAlias = 'RELEASE_KEY_ALIAS'
    keyPassword = 'RELEASE_KEY_PASSWORD'
}

function Get-ReleaseSigningValues {
    $propertyValues = @{}
    if (Test-Path -LiteralPath $signingPropertiesPath -PathType Leaf) {
        foreach ($line in Get-Content -LiteralPath $signingPropertiesPath) {
            if ($line -match '^\s*([^#;][^=]*)=(.*)$') {
                $propertyValues[$matches[1].Trim()] = $matches[2].Trim()
            }
        }
    }

    $values = @{}
    foreach ($key in $requiredSigningKeys) {
        $value = $propertyValues[$key]
        if ([string]::IsNullOrWhiteSpace($value)) {
            $value = [Environment]::GetEnvironmentVariable($environmentSigningKeys[$key])
        }
        $values[$key] = $value
    }
    return $values
}

$signingValues = Get-ReleaseSigningValues
$missingKeys = @(
    $requiredSigningKeys | Where-Object { [string]::IsNullOrWhiteSpace($signingValues[$_]) }
)
if ($missingKeys.Count -gt 0) {
    throw "缺少完整 Release 签名配置。请在 keystore.properties 或 RELEASE_* 环境变量中提供：$($missingKeys -join ', ')。脚本不会构建或输出未签名 APK。"
}

$storeFilePath = [string]$signingValues['storeFile']
if (-not [System.IO.Path]::IsPathRooted($storeFilePath)) {
    $storeFilePath = Join-Path $projectRoot $storeFilePath
}
if (-not (Test-Path -LiteralPath $storeFilePath -PathType Leaf)) {
    throw 'Release keystore 文件不存在。请检查 storeFile 或 RELEASE_STORE_FILE；脚本不会回退为 Debug 或未签名 APK。'
}

$gradleWrapper = Join-Path $projectRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "未找到 Gradle Wrapper：$gradleWrapper"
}

Push-Location $projectRoot
try {
    & $gradleWrapper 'assembleRelease' "-PVERSION_NAME=$VersionName" "-PVERSION_CODE=$VersionCode" '--no-daemon'
    if ($LASTEXITCODE -ne 0) {
        throw "Release APK 构建失败，Gradle 退出码：$LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

$releaseOutputDirectory = Join-Path $projectRoot 'app\build\outputs\apk\release'
$apkFiles = @(Get-ChildItem -LiteralPath $releaseOutputDirectory -Filter '*.apk' -File)
if ($apkFiles.Count -ne 1) {
    throw "Release APK 输出不符合预期：在 $releaseOutputDirectory 找到 $($apkFiles.Count) 个 APK。"
}

$apkPath = $apkFiles[0].FullName
if ($apkFiles[0].Name -match 'unsigned') {
    throw 'Gradle 仅生成未签名 APK。为防止发布不可安装的产物，脚本拒绝继续。'
}

$androidSdkRoots = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT) |
    Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
    Select-Object -Unique
$apksigner = $null
foreach ($sdkRoot in $androidSdkRoots) {
    $buildToolsDirectory = Join-Path $sdkRoot 'build-tools'
    if (Test-Path -LiteralPath $buildToolsDirectory -PathType Container) {
        $candidate = Get-ChildItem -LiteralPath $buildToolsDirectory -Directory |
            Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } |
            Where-Object { Test-Path -LiteralPath $_ -PathType Leaf } |
            Select-Object -First 1
        if ($candidate) {
            $apksigner = $candidate
            break
        }
    }
}
if ($null -eq $apksigner) {
    throw '未找到 Android SDK Build Tools 的 apksigner.bat。请设置 ANDROID_HOME 或 ANDROID_SDK_ROOT。'
}

& $apksigner verify --verbose --print-certs $apkPath
if ($LASTEXITCODE -ne 0) {
    throw "APK 签名验证失败，apksigner 退出码：$LASTEXITCODE"
}

Write-Host "已构建并验证签名的 Release APK：$apkPath"
Write-Host "版本：$VersionName（versionCode $VersionCode）"
