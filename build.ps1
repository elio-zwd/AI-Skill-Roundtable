param(
    [string]$OutputDir = "D:\Users\70455\Pictures",
    [string]$JavaHome,
    [switch]$Release
)

$ErrorActionPreference = 'Stop'

function Exit-Script($exitCode) {
    if ($null -eq $env:CI) {
        Write-Host "`n按回车键退出..." -ForegroundColor Gray
        Read-Host
    }
    exit $exitCode
}

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " 开始编译见域 APP 并导出 APK... " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. 探测 Java 环境
$javaExe = $null

if ($JavaHome) {
    $targetPath = Join-Path $JavaHome "bin/java.exe"
    if (Test-Path $targetPath) {
        $javaExe = $targetPath
        $env:JAVA_HOME = $JavaHome
        $env:Path = "$JavaHome\bin;" + $env:Path
    } else {
        Write-Error "您指定的 -JavaHome 路径下未找到 bin/java.exe: $targetPath"
        Exit-Script 1
    }
} elseif ($env:JAVA_HOME) {
    $targetPath = Join-Path $env:JAVA_HOME "bin/java.exe"
    if (Test-Path $targetPath) {
        $javaExe = $targetPath
        $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
    } else {
        Write-Warning "环境变量 JAVA_HOME 存在但路径下未找到 bin/java.exe: $targetPath"
    }
}

if (-not $javaExe) {
    $cmd = Get-Command java -ErrorAction SilentlyContinue
    if ($cmd) {
        $javaExe = $cmd.Source
    }
}

if (-not $javaExe) {
    Write-Error "未检测到 Java 运行环境。请安装 JDK 17 并设置 JAVA_HOME，或者执行：`n./build.ps1 -JavaHome `"C:\path\to\jdk-17`""
    Exit-Script 1
}

# 验证 Java 版本是否为 17
$javaVersionOutput = & $javaExe -version 2>&1
$isJ17 = $false
if ($javaVersionOutput -match 'version "(17\.[0-9a-zA-Z\.\+_]+)"') {
    $isJ17 = $true
}

if (-not $isJ17) {
    Write-Error "检测到的 Java 版本不是 JDK 17。当前输出: `n$javaVersionOutput`n请使用 JDK 17，或通过 -JavaHome 指定 JDK 17 路径。"
    Exit-Script 1
}

# ==========================================
# 2. 执行编译构建
# ==========================================
$buildType = if ($Release) { "Release" } else { "Debug" }
$gradleTask = if ($Release) { "assembleRelease" } else { "assembleDebug" }
$relativeApkSource = if ($Release) {
    "app\build\outputs\apk\release\app-release.apk"
} else {
    "app\build\outputs\apk\debug\app-debug.apk"
}

Write-Host "正在执行 Gradle 编译 ($buildType)..." -ForegroundColor Yellow
$projectRoot = $PSScriptRoot
if (-not $projectRoot) {
    $projectRoot = (Get-Location).Path
}

$gradleWrapper = Join-Path $projectRoot "gradlew.bat"
if (-not (Test-Path $gradleWrapper)) {
    Write-Error "未找到 gradlew.bat: $gradleWrapper"
    Exit-Script 1
}

Push-Location $projectRoot
try {
    & $gradleWrapper $gradleTask
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Gradle 编译失败！退出码: $LASTEXITCODE"
        Exit-Script $LASTEXITCODE
    }
} finally {
    Pop-Location
}

$sourceApkPath = Join-Path $projectRoot $relativeApkSource
if (-not (Test-Path $sourceApkPath)) {
    Write-Error "编译完成但未在预期路径找到 APK 文件: $sourceApkPath"
    Exit-Script 1
}

# ==========================================
# 3. 输出并导出 APK 到目标目录
# ==========================================
if (-not (Test-Path $OutputDir)) {
    Write-Host "目标目录不存在，正在创建: $OutputDir" -ForegroundColor Yellow
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$targetApkName = if ($Release) { "jianyu-release.apk" } else { "jianyu-debug.apk" }
$destinationPath = Join-Path $OutputDir $targetApkName

Write-Host "正在将 APK 复制到目标路径..." -ForegroundColor Yellow
Copy-Item -Path $sourceApkPath -Destination $destinationPath -Force

$apkItem = Get-Item $destinationPath
$apkSizeMB = [math]::Round($apkItem.Length / 1MB, 2)

Write-Host "==========================================" -ForegroundColor Green
Write-Host " 编译与导出成功！" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
Write-Host "APK 文件: $($apkItem.FullName)" -ForegroundColor Cyan
Write-Host "文件大小: $apkSizeMB MB ($($apkItem.Length) 字节)" -ForegroundColor Cyan
Write-Host "生成时间: $($apkItem.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss'))" -ForegroundColor Cyan
Write-Host "构建类型: $buildType" -ForegroundColor Cyan

Exit-Script 0
