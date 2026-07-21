$repos = @(
    "https://github.com/alchaincyf/karpathy-skill.git",
    "https://github.com/alchaincyf/zhang-yiming-skill.git",
    "https://github.com/alchaincyf/paul-graham-skill.git",
    "https://github.com/alchaincyf/ilya-sutskever-skill.git",
    "https://github.com/alchaincyf/trump-skill.git",
    "https://github.com/alchaincyf/mrbeast-skill.git",
    "https://github.com/alchaincyf/sun-yuchen-perspective.git",
    "https://github.com/alchaincyf/freud-skill.git",
    "https://github.com/alchaincyf/x-mentor-skill.git",
    "https://github.com/Walshyu/fengge-skill.git",
    "https://github.com/0xquqi/cz-skill.git",
    "https://github.com/zwbao/duan-yongping-skill.git",
    "https://github.com/heywanrong/tim-cook-skill.git"
)

$targetDir = Join-Path $PSScriptRoot "..\..\docs\skills"
$successList = @()
$failList = @()

if (-not (Test-Path $targetDir)) {
    New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
}

# 保存当前工作目录以备恢复
$origDir = Get-Location
Set-Location $targetDir

foreach ($repo in $repos) {
    $repoName = ($repo -split "/")[-1].Replace(".git", "")
    $localRepoPath = Join-Path $targetDir $repoName

    if (Test-Path $localRepoPath) {
        Write-Host "目录 $repoName 已存在，跳过克隆。" -ForegroundColor Yellow
        $successList += $repoName
        continue
    }

    Write-Host "开始克隆 $repoName (URL: $repo)..." -ForegroundColor Cyan
    $success = $false
    for ($i = 1; $i -le 3; $i++) {
        Write-Host "尝试第 $i 次克隆..."
        
        # 执行克隆
        git clone $repo
        
        if ($LASTEXITCODE -eq 0) {
            Write-Host "成功克隆 $repoName！" -ForegroundColor Green
            $success = $true
            break
        } else {
            Write-Host "克隆 $repoName 第 $i 次尝试失败。" -ForegroundColor Red
            if ($i -lt 3) {
                Write-Host "等待 5 秒后重试..."
                Start-Sleep -Seconds 5
            }
        }
    }

    if ($success) {
        $successList += $repoName
    } else {
        $failList += $repoName
    }
}

# 恢复原始工作目录
Set-Location $origDir

Write-Host "`n=== 克隆结果汇总 ===" -ForegroundColor Cyan
Write-Host "成功数量: $($successList.Count)" -ForegroundColor Green
Write-Host "失败数量: $($failList.Count)" -ForegroundColor Red

Write-Host "`n成功的仓库列表:" -ForegroundColor Green
foreach ($s in $successList) {
    Write-Host " - $s"
}

if ($failList.Count -gt 0) {
    Write-Host "`n失败的仓库列表:" -ForegroundColor Red
    foreach ($f in $failList) {
        Write-Host " - $f"
    }
}
