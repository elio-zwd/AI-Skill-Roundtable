# 设置 JDK 17 环境变量（确保在未配置环境的终端也能编译）
$env:JAVA_HOME = "D:\My_Elio\dev-tools\jdk-17.0.19+10"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path

Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " 开始编译并安装 AI-Skill-Roundtable... " -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan

# 1. 编译并安装
.\gradlew.bat installDebug
if ($LASTEXITCODE -ne 0) {
    Write-Error "编译或安装失败，请检查手机连接及编译错误！"
    exit $LASTEXITCODE
}

Write-Host "`n[1/3] 安装成功！正在拉起手机上的 App..." -ForegroundColor Green

# 2. 启动 MainActivity
adb shell am start -n com.example.skillroundtable/com.example.skillroundtable.MainActivity | Out-Null

# 3. 等待进程启动并获取 PID
Write-Host "[2/3] 正在获取应用进程 PID..." -ForegroundColor Yellow
Start-Sleep -Seconds 1.5

$packageName = "com.example.skillroundtable"
$appPid = adb shell pidof -s $packageName

if (-not $appPid) {
    # 兜底再尝试一次
    Start-Sleep -Seconds 1
    $appPid = adb shell pidof -s $packageName
}

if ($appPid) {
    # 强转为 String 避免数字类型没有 Trim 方法，并修剪空白字符
    $appPidStr = "$appPid".Trim()
    
    Write-Host "[3/3] 成功获取 PID: $appPidStr！开始流式输出日志..." -ForegroundColor Green
    Write-Host "--------------------------------------------------" -ForegroundColor Gray
    Write-Host "提示：按 Ctrl + C 可以随时退出日志追踪。" -ForegroundColor Yellow
    Write-Host "--------------------------------------------------" -ForegroundColor Gray
    
    # 清理历史日志缓存，让输出从现在开始
    adb logcat -c
    # 追踪当前 PID 的日志
    adb logcat --pid=$appPidStr
} else {
    Write-Warning "未检测到运行中的进程，请确认 App 是否在手机上正常启动。"
    Write-Host "正在流式输出全局过滤日志（包含 Roundtable 关键字）作为备用：" -ForegroundColor Yellow
    adb logcat | Select-String "Roundtable"
}
