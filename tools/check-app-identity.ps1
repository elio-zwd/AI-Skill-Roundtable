# tools/check-app-identity.ps1
# 见域应用身份静态门禁：包名、目录映射、资源、Manifest、Room Schema、CI 与脚本。

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:FailedCategoryCount = 0
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$IdentityBaseSha = '4de0bfb0480ea84d3a88af12c11167a3a27c38dc'
$CurrentPackage = 'com.elio.jianyu'
$LegacyPackage = 'com.elio.skillroundtable'
$LegacySchema = 'app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json'
$CurrentIdentitySchema = 'app/schemas/com.elio.jianyu.data.RoundtableDatabase/5.json'
$MoveManifest = 'docs/testing/pr-09-01-package-move-manifest.txt'

function Pass {
    param([string]$Category, [string]$Message)
    Write-Host "[PASS - $Category] $Message" -ForegroundColor Green
}

function Fail {
    param([string]$Category, [string]$Message)
    Write-Host "[FAIL - $Category] $Message" -ForegroundColor Red
    $script:FailedCategoryCount++
}

function Get-TrackedFiles {
    param([string[]]$Paths)
    $output = @(& git ls-files -- @Paths 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "git ls-files 执行失败：$($output -join [Environment]::NewLine)"
    }
    $output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
}

function Normalize-Text {
    param([string]$Path)
    return (Get-Content $Path -Raw).Replace("`r`n", "`n").Replace("`r", "`n")
}

Push-Location $RepoRoot
try {
    Write-Host '=== 见域应用身份静态门禁检查 ==='

    $buildGradle = Get-Content 'app/build.gradle.kts' -Raw
    if ($buildGradle -match 'namespace\s*=\s*"com\.elio\.jianyu"' -and
        $buildGradle -notmatch 'namespace\s*=\s*"com\.elio\.skillroundtable"') {
        Pass 'Gradle Namespace' 'namespace 精确为 com.elio.jianyu'
    } else {
        Fail 'Gradle Namespace' 'namespace 不是唯一的新包值'
    }
    if ($buildGradle -match 'applicationId\s*=\s*"com\.elio\.jianyu"' -and
        $buildGradle -notmatch 'applicationId\s*=\s*"com\.elio\.skillroundtable"') {
        Pass 'Gradle ApplicationId' 'applicationId 精确为 com.elio.jianyu'
    } else {
        Fail 'Gradle ApplicationId' 'applicationId 不是唯一的新包值'
    }

    if (-not (Test-Path $MoveManifest -PathType Leaf)) {
        Fail 'Move Manifest' "缺失 $MoveManifest"
        $manifestPaths = @()
    } else {
        $manifestPaths = @(
            Get-Content $MoveManifest |
                Where-Object { $_ -match '^app/src/(main|test|androidTest)/java/com/elio/skillroundtable/' }
        )
        $mainCount = @($manifestPaths | Where-Object { $_ -like 'app/src/main/*' }).Count
        $unitCount = @($manifestPaths | Where-Object { $_ -like 'app/src/test/*' }).Count
        $androidCount = @($manifestPaths | Where-Object { $_ -like 'app/src/androidTest/*' }).Count
        $uniqueCount = @($manifestPaths | Sort-Object -Unique).Count
        if ($manifestPaths.Count -eq 110 -and $uniqueCount -eq 110 -and
            $mainCount -eq 71 -and $unitCount -eq 31 -and $androidCount -eq 8) {
            Pass 'Move Manifest' '固定 Base 清单为 Main 71 / Unit 31 / Android 8，共 110 个唯一文件'
        } else {
            Fail 'Move Manifest' "计数异常：main=$mainCount unit=$unitCount android=$androidCount total=$($manifestPaths.Count) unique=$uniqueCount"
        }
    }

    $missingTargets = @(
        foreach ($oldPath in $manifestPaths) {
            $newPath = $oldPath.Replace('/com/elio/skillroundtable/', '/com/elio/jianyu/')
            if (-not (Test-Path $newPath -PathType Leaf)) { $newPath }
        }
    )
    if ($missingTargets.Count -eq 0) {
        Pass 'Package Move Mapping' '110 个 Base 文件均存在唯一新路径目标'
    } else {
        Fail 'Package Move Mapping' "缺失迁移目标：$($missingTargets -join ', ')"
    }

    $currentRoots = @(
        'app/src/main/java/com/elio/jianyu',
        'app/src/test/java/com/elio/jianyu',
        'app/src/androidTest/java/com/elio/jianyu'
    )
    $legacyRoots = @(
        'app/src/main/java/com/elio/skillroundtable',
        'app/src/test/java/com/elio/skillroundtable',
        'app/src/androidTest/java/com/elio/skillroundtable'
    )
    $currentLabels = @('Main Source Dir', 'Unit Test Dir', 'Android Test Dir')
    $legacyLabels = @('Legacy Main Source Dir', 'Legacy Unit Test Dir', 'Legacy Android Test Dir')

    for ($index = 0; $index -lt $currentRoots.Count; $index++) {
        if (Test-Path $currentRoots[$index] -PathType Container) {
            Pass $currentLabels[$index] "新目录存在：$($currentRoots[$index])"
        } else {
            Fail $currentLabels[$index] "新目录缺失：$($currentRoots[$index])"
        }
        if (Test-Path $legacyRoots[$index]) {
            Fail $legacyLabels[$index] "旧目录仍存在：$($legacyRoots[$index])"
        } else {
            Pass $legacyLabels[$index] "旧目录已清除：$($legacyRoots[$index])"
        }
    }

    $currentTracked = @(Get-TrackedFiles -Paths $currentRoots)
    $identityTest = 'app/src/androidTest/java/com/elio/jianyu/identity/AppIdentityIsolationTest.kt'
    if ($currentTracked.Count -ge 111 -and $currentTracked -contains $identityTest) {
        Pass 'Tracked Source Count' "原 110 个映射文件和身份测试均保留；当前允许后续功能新增，count=$($currentTracked.Count)"
    } else {
        Fail 'Tracked Source Count' "原身份迁移文件或身份测试缺失：count=$($currentTracked.Count)"
    }

    $remainingLegacyTracked = @(Get-TrackedFiles -Paths $legacyRoots)
    if ($remainingLegacyTracked.Count -eq 0) {
        Pass 'Legacy Tracked Files' '三个旧包根目录没有已跟踪文件'
    } else {
        Fail 'Legacy Tracked Files' "旧目录仍有已跟踪文件：$($remainingLegacyTracked -join ', ')"
    }

    $kotlinFiles = @(
        foreach ($root in $currentRoots) {
            if (Test-Path $root) { Get-ChildItem $root -Recurse -File -Filter '*.kt' }
        }
    )
    $badPackages = @(
        foreach ($file in $kotlinFiles) {
            $content = Get-Content $file.FullName -Raw
            if ($content -notmatch '(?m)^package\s+com\.elio\.jianyu(?:\.|\s*$)') { $file.FullName }
        }
    )
    $legacyHits = @(
        foreach ($file in $kotlinFiles) {
            $content = Get-Content $file.FullName -Raw
            if (
                $content.Contains($LegacyPackage, [System.StringComparison]::Ordinal) -or
                $content -match 'com\\+\.elio\\+\.skillroundtable' -or
                $content -match 'com[/\\]+elio[/\\]+skillroundtable'
            ) {
                $file.FullName
            }
        }
    )
    if ($badPackages.Count -eq 0) {
        Pass 'Kotlin Package Declarations' '全部活动 Kotlin 文件声明 com.elio.jianyu 包'
    } else {
        Fail 'Kotlin Package Declarations' "异常 package 声明：$($badPackages -join ', ')"
    }
    if ($legacyHits.Count -eq 0) {
        Pass 'Active Source Legacy References' '活动 Kotlin 源码和测试无普通、转义或路径形式的旧包残留'
    } else {
        Fail 'Active Source Legacy References' "活动源码仍含旧包名或旧路径：$($legacyHits -join ', ')"
    }

    $uiAgents = Get-Content 'app/src/main/java/com/elio/jianyu/ui/AGENTS.md' -Raw
    if ($uiAgents -match 'app/src/main/java/com/elio/jianyu/ui/' -and
        $uiAgents -notmatch 'app/src/main/java/com/elio/skillroundtable/ui/') {
        Pass 'UI AGENTS Path' 'UI 目录规则已指向新包路径'
    } else {
        Fail 'UI AGENTS Path' 'UI 目录规则仍包含旧路径或缺失新路径'
    }

    $stringsXml = Get-Content 'app/src/main/res/values/strings.xml' -Raw
    if ($stringsXml -match '<string\s+name="app_name">见域</string>') {
        Pass 'App Name' 'app_name 精确为见域'
    } else {
        Fail 'App Name' 'app_name 未设置为见域'
    }

    $manifest = Get-Content 'app/src/main/AndroidManifest.xml' -Raw
    if ($manifest -match 'android:name="\.MainActivity"' -and
        $manifest -match 'android:label="@string/app_name"' -and
        $manifest -notmatch 'com\.elio\.(skillroundtable|jianyu)') {
        Pass 'Manifest Identity' 'Manifest 使用相对 MainActivity 和 app_name，未硬编码新旧包名'
    } else {
        Fail 'Manifest Identity' 'Manifest Activity、Label 或包名硬编码不符合约束'
    }

    $runScript = Get-Content 'run.ps1' -Raw
    if ($runScript -match "\`$packageName\s*=\s*'com\.elio\.jianyu'" -and
        $runScript -match "\`$activityName\s*=\s*'com\.elio\.jianyu\.MainActivity'" -and
        $runScript -match '开始编译并安装见域' -and
        $runScript -notmatch 'com\.elio\.skillroundtable') {
        Pass 'Run Script' '运行脚本使用见域名称、新包和新 Launcher'
    } else {
        Fail 'Run Script' '运行脚本身份常量不完整或仍含旧包'
    }

    if (Test-Path $LegacySchema -PathType Leaf) {
        Pass 'Legacy Room Schema' "旧 FQCN Schema 保留：$LegacySchema"
    } else {
        Fail 'Legacy Room Schema' "旧 FQCN Schema 缺失：$LegacySchema"
    }
    if (Test-Path $CurrentIdentitySchema -PathType Leaf) {
        Pass 'Room Identity Schema' "新 FQCN v5 身份基线保留：$CurrentIdentitySchema"
    } else {
        Fail 'Room Identity Schema' "新 FQCN v5 身份基线缺失：$CurrentIdentitySchema"
    }

    if ((Test-Path $LegacySchema) -and (Test-Path $CurrentIdentitySchema)) {
        try {
            $oldRaw = Get-Content $LegacySchema -Raw
            $newRaw = Get-Content $CurrentIdentitySchema -Raw
            $null = $oldRaw | ConvertFrom-Json
            $null = $newRaw | ConvertFrom-Json
            if ((Normalize-Text $LegacySchema) -ceq (Normalize-Text $CurrentIdentitySchema)) {
                Pass 'Room Schema Equivalence' '新旧 v5 身份 Schema 规范化换行后字节一致，JSON 可解析'
            } else {
                Fail 'Room Schema Equivalence' '新旧 v5 身份 Schema 存在换行符以外的差异'
            }
        } catch {
            Fail 'Room Schema Equivalence' "Schema JSON 解析失败：$($_.Exception.Message)"
        }

        $baseDiff = @(& git diff --exit-code $IdentityBaseSha -- $LegacySchema 2>&1)
        if ($LASTEXITCODE -eq 0) {
            Pass 'Legacy Schema Freeze' '旧 FQCN Schema 与固定身份 Base 完全一致'
        } else {
            Fail 'Legacy Schema Freeze' "旧 Schema 相对身份 Base 发生变化：$($baseDiff -join [Environment]::NewLine)"
        }
    }

    $databaseSource = Get-Content 'app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt' -Raw
    $requiredMigrations = @(
        'MIGRATION_1_2',
        'MIGRATION_2_3',
        'MIGRATION_3_4',
        'MIGRATION_4_5',
        'MIGRATION_5_6',
        'MIGRATION_6_7',
        'MIGRATION_7_8',
        'MIGRATION_8_9',
        'MIGRATION_9_10'
    )
    $missingMigrations = @($requiredMigrations | Where-Object { $databaseSource -notmatch [regex]::Escape($_) })
    $materialContextMigration = Get-Content 'app/src/main/java/com/elio/jianyu/data/MaterialContextMigration.kt' -Raw
    $collaborationMigration = Get-Content 'app/src/main/java/com/elio/jianyu/data/CollaborationMigration.kt' -Raw
    if ($databaseSource -match 'version\s*=\s*10' -and
        $missingMigrations.Count -eq 0 -and
        $databaseSource -match '"roundtable_database"' -and
        $materialContextMigration -match 'Migration\(8,\s*9\)' -and
        $collaborationMigration -match 'Migration\(9,\s*10\)') {
        Pass 'Room Runtime Contract' 'Room 已连续升级到 v10，v1→v10 迁移链和数据库名保持完整'
    } else {
        Fail 'Room Runtime Contract' "Room v10、连续迁移链或数据库名异常；缺失迁移=$($missingMigrations -join ', ')"
    }

    $keyStoreSource = Get-Content 'app/src/main/java/com/elio/jianyu/network/EncryptedApiKeyStore.kt' -Raw
    if ($keyStoreSource -match 'KEY_ALIAS\s*=\s*"skill_roundtable_api_key_v1"' -and
        $keyStoreSource -match 'FILE_NAME\s*=\s*"gemini_api_keys\.enc"' -and
        $keyStoreSource -match 'TRANSFORMATION\s*=\s*"AES/GCM/NoPadding"') {
        Pass 'Key Store Contract' 'Key alias、密文文件名和 AES-GCM 格式保持不变'
    } else {
        Fail 'Key Store Contract' 'Key Store 契约发生非预期变化'
    }

    $ciLines = Get-Content '.github/workflows/android-ci.yml'
    $ciYaml = $ciLines -join "`n"
    $invalidLegacyCiLines = @(
        $ciLines | Where-Object {
            $_ -match 'com\.elio\.skillroundtable' -and
            $_ -notmatch '^\s+LEGACY_(PACKAGE|LAUNCHER|SCHEMA):'
        }
    )
    $hasCurrentIdentitySchema =
        $ciYaml -match '(CURRENT_SCHEMA_V5|CURRENT_SCHEMA):\s*app/schemas/com\.elio\.jianyu\.data\.RoundtableDatabase/5\.json'
    if ($ciYaml -match 'CURRENT_PACKAGE:\s*com\.elio\.jianyu' -and
        $ciYaml -match 'CURRENT_LAUNCHER:\s*com\.elio\.jianyu\.MainActivity' -and
        $hasCurrentIdentitySchema -and
        $ciYaml -match 'LEGACY_PACKAGE:\s*com\.elio\.skillroundtable' -and
        $ciYaml -match 'LEGACY_LAUNCHER:\s*com\.elio\.skillroundtable\.MainActivity' -and
        $ciYaml -match 'LEGACY_SCHEMA:\s*app/schemas/com\.elio\.skillroundtable\.data\.RoundtableDatabase/5\.json' -and
        $ciYaml -match 'legacy-apk:' -and
        $ciYaml -match 'verify-app-coexistence\.ps1' -and
        $ciYaml -match 'AppIdentityIsolationTest' -and
        $ciYaml -match 'notClass=com\.elio\.jianyu\.identity\.AppIdentityIsolationTest' -and
        $invalidLegacyCiLines.Count -eq 0) {
        Pass 'CI Config' 'CI 保留见域身份、v5 身份 Schema、固定旧包 APK 与分阶段 Instrumentation 门禁'
    } else {
        Fail 'CI Config' "CI 身份常量、双包验收、测试顺序或旧包允许清单异常；非法旧包行数=$($invalidLegacyCiLines.Count)"
    }

    $readme = Get-Content 'README.md' -Raw
    $agents = Get-Content 'AGENTS.md' -Raw
    if ($readme -match 'App 名称：见域' -and
        $readme -match 'namespace / applicationId：com\.elio\.jianyu' -and
        $agents -match 'App 用户可见名称：见域' -and
        $agents -match 'namespace / applicationId：com\.elio\.jianyu' -and
        $agents -match 'app/src/main/java/com/elio/jianyu/') {
        Pass 'Current Identity Documentation' 'README 与 AGENTS 已记录当前见域身份'
    } else {
        Fail 'Current Identity Documentation' 'README 或 AGENTS 缺少当前见域身份事实'
    }

    Write-Host '================================'
    if ($script:FailedCategoryCount -gt 0) {
        Write-Host "静态身份检查未通过，共发现 $script:FailedCategoryCount 项失败。" -ForegroundColor Red
        exit 1
    }
    Write-Host '静态身份检查全数通过！' -ForegroundColor Green
    exit 0
} finally {
    Pop-Location
}
