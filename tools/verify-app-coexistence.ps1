# tools/verify-app-coexistence.ps1
# PR09-01 双包并存与 Android UID / 私有文件隔离验收。
# 默认只读设备状态；只有显式传入 -Install 或 -CreatePrivateFileSentinel 才修改测试设备。

[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$LegacyApk,

    [Parameter(Mandatory)]
    [string]$CurrentApk,

    [string]$DeviceId,
    [string]$AaptPath,
    [switch]$Install,
    [switch]$CreatePrivateFileSentinel,
    [switch]$CleanupPrivateFileSentinel
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$LegacyPackage = 'com.elio.skillroundtable'
$LegacyLauncher = 'com.elio.skillroundtable.MainActivity'
$CurrentPackage = 'com.elio.jianyu'
$CurrentLauncher = 'com.elio.jianyu.MainActivity'
$SentinelName = 'pr0901_legacy_private_sentinel.txt'

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory)] [string]$FilePath,
        [Parameter(Mandatory)] [string[]]$Arguments,
        [string]$Description = $FilePath
    )

    $output = @(& $FilePath @Arguments 2>&1)
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "$Description 失败（退出码 $exitCode）：`n$($output -join [Environment]::NewLine)"
    }
    return $output
}

function Resolve-Aapt {
    if ($AaptPath) {
        if (-not (Test-Path $AaptPath -PathType Leaf)) {
            throw "指定的 aapt 不存在：$AaptPath"
        }
        return (Resolve-Path $AaptPath).Path
    }

    $aaptCommand = Get-Command aapt -ErrorAction SilentlyContinue
    if ($aaptCommand) {
        return $aaptCommand.Source
    }

    $sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { $env:ANDROID_SDK_ROOT }
    if (-not $sdkRoot) {
        throw '未找到 aapt；请传入 -AaptPath，或配置 ANDROID_HOME / ANDROID_SDK_ROOT。'
    }

    $candidate = Get-ChildItem (Join-Path $sdkRoot 'build-tools') -Recurse -File |
        Where-Object { $_.Name -in @('aapt', 'aapt.exe') } |
        Sort-Object FullName -Descending |
        Select-Object -First 1
    if (-not $candidate) {
        throw "Android SDK 中未找到 aapt：$sdkRoot"
    }
    return $candidate.FullName
}

function Resolve-TargetDevice {
    param([Parameter(Mandatory)] [string]$AdbPath)

    if ($DeviceId) {
        return $DeviceId
    }

    $lines = Invoke-CheckedCommand -FilePath $AdbPath -Arguments @('devices') -Description 'adb devices'
    $devices = @(
        $lines |
            Where-Object { $_ -match '^([^\s]+)\s+device$' } |
            ForEach-Object { [regex]::Match($_, '^([^\s]+)').Groups[1].Value }
    )
    if ($devices.Count -ne 1) {
        throw "需要且只能自动选择 1 台 online 设备，当前数量：$($devices.Count)。请使用 -DeviceId。"
    }
    return $devices[0]
}

function Assert-ApkIdentity {
    param(
        [Parameter(Mandatory)] [string]$Aapt,
        [Parameter(Mandatory)] [string]$Apk,
        [Parameter(Mandatory)] [string]$ExpectedPackage,
        [Parameter(Mandatory)] [string]$ExpectedLauncher
    )

    if (-not (Test-Path $Apk -PathType Leaf)) {
        throw "APK 不存在：$Apk"
    }
    $resolvedApk = (Resolve-Path $Apk).Path
    $badging = Invoke-CheckedCommand -FilePath $Aapt -Arguments @('dump', 'badging', $resolvedApk) -Description "aapt dump badging $resolvedApk"
    $badgingText = $badging -join "`n"
    if ($badgingText -notmatch "package: name='$([regex]::Escape($ExpectedPackage))'") {
        throw "APK 包名不是预期值 $ExpectedPackage：$resolvedApk"
    }
    if ($badgingText -notmatch "launchable-activity: name='$([regex]::Escape($ExpectedLauncher))'") {
        throw "APK Launcher 不是预期值 $ExpectedLauncher：$resolvedApk"
    }
    Write-Host "[PASS] APK 身份：$ExpectedPackage / $ExpectedLauncher" -ForegroundColor Green
    return $resolvedApk
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory)] [string]$Adb,
        [Parameter(Mandatory)] [string]$Target,
        [Parameter(Mandatory)] [string[]]$Arguments,
        [string]$Description = 'adb'
    )
    return Invoke-CheckedCommand -FilePath $Adb -Arguments (@('-s', $Target) + $Arguments) -Description $Description
}

function Get-PackageUid {
    param(
        [Parameter(Mandatory)] [string]$Adb,
        [Parameter(Mandatory)] [string]$Target,
        [Parameter(Mandatory)] [string]$PackageName
    )

    $lines = Invoke-Adb -Adb $Adb -Target $Target -Arguments @('shell', 'cmd', 'package', 'list', 'packages', '-U') -Description '读取设备包与 UID'
    $escaped = [regex]::Escape($PackageName)
    $match = $lines | Where-Object { $_ -match "^package:$escaped\s+uid:(\d+)" } | Select-Object -First 1
    if (-not $match) {
        throw "设备上未找到包或 UID：$PackageName"
    }
    return [int]([regex]::Match($match, 'uid:(\d+)').Groups[1].Value)
}

function Get-PackageDataDir {
    param(
        [Parameter(Mandatory)] [string]$Adb,
        [Parameter(Mandatory)] [string]$Target,
        [Parameter(Mandatory)] [string]$PackageName
    )

    $lines = Invoke-Adb -Adb $Adb -Target $Target -Arguments @('shell', 'dumpsys', 'package', $PackageName) -Description "dumpsys package $PackageName"
    $line = $lines | Where-Object { $_ -match '^\s*dataDir=' } | Select-Object -First 1
    if (-not $line) {
        throw "无法读取 dataDir：$PackageName"
    }
    return ($line -replace '^\s*dataDir=', '').Trim()
}

$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adbCommand) {
    throw '未找到 adb，请将 Android SDK Platform-Tools 加入 PATH。'
}
$adb = $adbCommand.Source
$aapt = Resolve-Aapt
$targetDevice = Resolve-TargetDevice -AdbPath $adb

$legacyApkPath = Assert-ApkIdentity -Aapt $aapt -Apk $LegacyApk -ExpectedPackage $LegacyPackage -ExpectedLauncher $LegacyLauncher
$currentApkPath = Assert-ApkIdentity -Aapt $aapt -Apk $CurrentApk -ExpectedPackage $CurrentPackage -ExpectedLauncher $CurrentLauncher

Write-Host "目标设备：$targetDevice" -ForegroundColor Cyan
Write-Host '本脚本不会自动卸载、清除数据或删除任一 App。' -ForegroundColor Yellow

if ($Install) {
    Invoke-Adb -Adb $adb -Target $targetDevice -Arguments @('install', '-r', $legacyApkPath) -Description '安装/更新旧包 APK' | Out-Host
    Invoke-Adb -Adb $adb -Target $targetDevice -Arguments @('install', '-r', $currentApkPath) -Description '安装/更新见域 APK' | Out-Host
}

$legacyUid = Get-PackageUid -Adb $adb -Target $targetDevice -PackageName $LegacyPackage
$currentUid = Get-PackageUid -Adb $adb -Target $targetDevice -PackageName $CurrentPackage
if ($legacyUid -eq $currentUid) {
    throw "新旧包 UID 不应相同：uid=$legacyUid"
}
Write-Host "[PASS] 双包同时存在且 UID 不同：legacy=$legacyUid current=$currentUid" -ForegroundColor Green

$legacyDataDir = Get-PackageDataDir -Adb $adb -Target $targetDevice -PackageName $LegacyPackage
$currentDataDir = Get-PackageDataDir -Adb $adb -Target $targetDevice -PackageName $CurrentPackage
if ($legacyDataDir -eq $currentDataDir) {
    throw "新旧包 dataDir 不应相同：$legacyDataDir"
}
if ($legacyDataDir -notmatch [regex]::Escape($LegacyPackage)) {
    throw "旧包 dataDir 未包含旧包名：$legacyDataDir"
}
if ($currentDataDir -notmatch [regex]::Escape($CurrentPackage)) {
    throw "见域 dataDir 未包含新包名：$currentDataDir"
}
Write-Host "[PASS] 双包私有目录不同：`n  legacy=$legacyDataDir`n  current=$currentDataDir" -ForegroundColor Green

Invoke-Adb -Adb $adb -Target $targetDevice -Arguments @('shell', 'am', 'start', '-W', '-n', "$LegacyPackage/$LegacyLauncher") -Description '启动旧包' | Out-Host
Invoke-Adb -Adb $adb -Target $targetDevice -Arguments @('shell', 'am', 'start', '-W', '-n', "$CurrentPackage/$CurrentLauncher") -Description '启动见域' | Out-Host
Write-Host '[PASS] 新旧 Launcher 均可启动' -ForegroundColor Green

if ($CreatePrivateFileSentinel) {
    $createCommand = "run-as $LegacyPackage sh -c 'printf pr0901-legacy-sentinel > files/$SentinelName'"
    Invoke-Adb -Adb $adb -Target $targetDevice -Arguments @('shell', $createCommand) -Description '创建旧包私有文件哨兵' | Out-Null

    $verifyLegacy = "run-as $LegacyPackage sh -c 'test -f files/$SentinelName'"
    Invoke-Adb -Adb $adb -Target $targetDevice -Arguments @('shell', $verifyLegacy) -Description '确认旧包哨兵存在' | Out-Null

    $verifyCurrent = "run-as $CurrentPackage sh -c 'test ! -e files/$SentinelName'"
    Invoke-Adb -Adb $adb -Target $targetDevice -Arguments @('shell', $verifyCurrent) -Description '确认见域看不到旧包哨兵' | Out-Null
    Write-Host '[PASS] run-as 私有文件哨兵证明跨 UID 不可见' -ForegroundColor Green
}

if ($CleanupPrivateFileSentinel) {
    $cleanupCommand = "run-as $LegacyPackage sh -c 'rm -f files/$SentinelName'"
    Invoke-Adb -Adb $adb -Target $targetDevice -Arguments @('shell', $cleanupCommand) -Description '清理旧包私有文件哨兵' | Out-Null
    Write-Host '[PASS] 已清理本脚本创建的旧包私有文件哨兵' -ForegroundColor Green
}

Write-Host ''
Write-Host '自动检查已完成。以下业务数据边界仍须按验收文档人工确认：' -ForegroundColor Cyan
Write-Host '1. 旧包中创建会话并导入专用测试 Key；'
Write-Host '2. 见域首次启动会话为空、Key 列表为空并要求重新配置；'
Write-Host '3. 再次打开旧包，旧会话和测试 Key 仍存在；'
Write-Host '4. 验收后只删除专用测试 Key/哨兵，不自动卸载或清除 App 数据。'
