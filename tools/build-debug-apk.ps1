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
$gradleWrapper = Join-Path $projectRoot 'gradlew.bat'

if (-not (Test-Path -LiteralPath $gradleWrapper -PathType Leaf)) {
    throw "未找到 Gradle Wrapper：$gradleWrapper"
}

Push-Location $projectRoot
try {
    & $gradleWrapper 'assembleDebug' "-PVERSION_NAME=$VersionName" "-PVERSION_CODE=$VersionCode" '-Dorg.gradle.workers.max=1' '--no-daemon'
    if ($LASTEXITCODE -ne 0) {
        throw "Debug APK 构建失败，Gradle 退出码：$LASTEXITCODE"
    }
}
finally {
    Pop-Location
}

$apkPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
if (-not (Test-Path -LiteralPath $apkPath -PathType Leaf)) {
    throw "未找到 Debug APK：$apkPath"
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
    throw "Debug APK 签名验证失败，apksigner 退出码：$LASTEXITCODE"
}

Write-Host "已构建并验证 Debug APK：$apkPath"
Write-Host "版本：$VersionName（versionCode $VersionCode）"
