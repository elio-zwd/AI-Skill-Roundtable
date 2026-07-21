param(
    [string]$JavaHome,
    [switch]$SkipInstall,
    [switch]$NoLogcat
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
Write-Host " 开始编译并安装 AI-Skill-Roundtable... " -ForegroundColor Cyan
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
    Write-Error "未检测到 Java 运行环境。请安装 JDK 17 并设置 JAVA_HOME，或者执行：`n./run.ps1 -JavaHome `"C:\path\to\jdk-17`""
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
# 2. 如果指定了 -SkipInstall，直接执行编译并退出
# ==========================================
if ($SkipInstall) {
    Write-Host "[SkipInstall] 仅执行编译构建，不检测设备及安装..." -ForegroundColor Yellow
    .\gradlew.bat assembleDebug
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Gradle 编译失败！退出码: $LASTEXITCODE"
        Exit-Script $LASTEXITCODE
    }
    Write-Host "编译成功！生成的 APK 位于: app/build/outputs/apk/debug/app-debug.apk" -ForegroundColor Green
    Exit-Script 0
}

# ==========================================
# 3. 只有安装和启动模式才检测 ADB 和设备
# ==========================================
# 检测 adb 命令
$adbCmd = Get-Command adb -ErrorAction SilentlyContinue
if (-not $adbCmd) {
    Write-Error "未在 PATH 中检测到 adb 命令。请确保已安装 Android SDK Platform-Tools 并加入到系统环境变量 PATH 中。"
    Exit-Script 1
}

# 检测设备连接
$devices = adb devices 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Error "执行 'adb devices' 失败，退出码: $LASTEXITCODE"
    Exit-Script $LASTEXITCODE
}

$hasDevice = $false
$isUnauthorized = $false
$targetDeviceId = $null

$lines = $devices | Where-Object { $_ -and $_ -notmatch "List of devices" }
foreach ($line in $lines) {
    if ($line -match '^([^\s]+)\s+device$') {
        $targetDeviceId = $Matches[1]
        $hasDevice = $true
        break
    } elseif ($line -match '^([^\s]+)\s+unauthorized$') {
        $isUnauthorized = $true
    }
}

if (-not $hasDevice) {
    if ($isUnauthorized) {
        Write-Error "检测到有设备连接，但状态为 'unauthorized'。请解锁手机并接受 USB 调试授权。"
    } else {
        Write-Error "未检测到任何已连接的 Android 设备或模拟器。请确认设备已通过 USB 连接并开启 USB 调试。"
    }
    Exit-Script 1
}

Write-Host "已选择目标设备进行运行与调试: $targetDeviceId" -ForegroundColor Cyan

# ==========================================
# 4. 编译与安装并启动
# ==========================================
Write-Host "正在编译并安装 Debug APK..." -ForegroundColor Yellow
.\gradlew.bat installDebug
if ($LASTEXITCODE -ne 0) {
    Write-Error "Gradle 编译或安装失败，退出码: $LASTEXITCODE"
    Exit-Script $LASTEXITCODE
}

Write-Host "`n安装成功！正在拉起手机上的 App..." -ForegroundColor Green

$packageName = 'com.example.skillroundtable'
$activityName = 'com.example.skillroundtable.MainActivity'

# 启动 Activity 并获取输出及退出码（指定目标设备）
$amStartOutput = adb -s $targetDeviceId shell am start -n "$packageName/$activityName" 2>&1
$amExitCode = $LASTEXITCODE

if ($amExitCode -ne 0 -or $amStartOutput -match "Error:" -or $amStartOutput -match "Exception") {
    Write-Error "拉起 App 失败！退出码: $amExitCode。输出详情:`n$amStartOutput"
    Exit-Script 1
}

# 如果指定了 -NoLogcat，直接成功退出
if ($NoLogcat) {
    Write-Host "拉起成功 (-NoLogcat)。" -ForegroundColor Green
    Exit-Script 0
}

# ==========================================
# 5. 获取 PID 并流式输出日志
# ==========================================
Write-Host "正在获取应用进程 PID..." -ForegroundColor Yellow
Start-Sleep -Seconds 1.5

$appPid = adb -s $targetDeviceId shell pidof -s $packageName
if (-not $appPid) {
    # 兜底再尝试一次
    Start-Sleep -Seconds 1.0
    $appPid = adb -s $targetDeviceId shell pidof -s $packageName
}

if ($appPid) {
    $appPidStr = "$appPid".Trim()
    Write-Host "成功获取 PID: $appPidStr！开始流式输出日志..." -ForegroundColor Green
    Write-Host "--------------------------------------------------" -ForegroundColor Gray
    Write-Host "提示：按 Ctrl + C 可以安全退出日志追踪。" -ForegroundColor Yellow
    Write-Host "--------------------------------------------------" -ForegroundColor Gray
    
    # 清理历史日志缓存，让输出从现在开始（指定目标设备）
    adb -s $targetDeviceId logcat -c
    # 追踪当前 PID 的日志（屏蔽移动屏幕产生的手势/渲染噪音，保留点击等业务日志）
    adb -s $targetDeviceId logcat --pid=$appPidStr ViewRootImpl:S ViewPostImeInputStage:S Choreographer:S OpenGLRenderer:S InputMethodManager:S libEGL:S InputEventReceiver:S MotionEvent:S '*:V'
} else {
    Write-Warning "未检测到运行中的进程，请确认 App 是否在手机上正常启动。"
    Write-Host "正在退出日志模式。" -ForegroundColor Yellow
    Exit-Script 0
}
