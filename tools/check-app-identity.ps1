# tools/check-app-identity.ps1
# 见域应用身份静态门禁：包名、目录映射、资源、Manifest、Room Schema、CI 与脚本。

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:FailedCategoryCount = 0
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$BaseSha = '4de0bfb0480ea84d3a88af12c11167a3a27c38dc'
$CurrentPackage = 'com.elio.jianyu'
$LegacyPackage = 'com.elio.skillroundtable'
$CurrentLauncher = 'com.elio.jianyu.MainActivity'
$LegacySchema = 'app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json'
$CurrentSchema = 'app/schemas/com.elio.jianyu.data.RoundtableDatabase/5.json'
$MoveManifest = 'docs/testing/pr-09-01-package-move-manifest.txt'

function Report-IdentityFailure {
    param(
        [Parameter(Mandatory)] [string]$Category,
        [Parameter(Mandatory)] [string]$Message
    )
    Write-Host "[FAIL - $Category] $Message" -ForegroundColor Red
    $script:FailedCategoryCount++
}

function Report-IdentitySuccess {
    param(
        [Parameter(Mandatory)] [string]$Category,
        [Parameter(Mandatory)] [string]$Message
    )
    Write-Host "[PASS - $Category] $Message" -ForegroundColor Green
}

function Get-TrackedFiles {
    param([Parameter(Mandatory)] [string[]]$Paths)

    $output = @(& git ls-files -- @Paths 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "git ls-files 执行失败：$($output -join [Environment]::NewLine)"
    }
    return @($output | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
}

function Get-NormalizedText {
    param([Parameter(Mandatory)] [string]$Path)
    return (Get-Content $Path -Raw).Replace("`r`n", "`n").Replace("`r", "`n")
}

Push-Location $RepoRoot
try {
    Write-Host '=== 见域应用身份静态门禁检查 ==='

    # 1. Gradle 身份必须精确为新包，且不得残留旧包。
    $buildGradle = Get-Content 'app/build.gradle.kts' -Raw
    if (
        $buildGradle -match 'namespace\s*=\s*"com\.elio\.jianyu"' -and
        $buildGradle -notmatch 'namespace\s*=\s*"com\.elio\.skillroundtable"'
    ) {
        Report-IdentitySuccess 'Gradle Namespace' 'namespace 精确为 com.elio.jianyu'
    } else {
        Report-IdentityFailure 'Gradle Namespace' 'namespace 不是唯一的新包值'
    }

    if (
        $buildGradle -match 'applicationId\s*=\s*"com\.elio\.jianyu"' -and
        $buildGradle -notmatch 'applicationId\s*=\s*"com\.elio\.skillroundtable"'
    ) {
        Report-IdentitySuccess 'Gradle ApplicationId' 'applicationId 精确为 com.elio.jianyu'
    } else {
        Report-IdentityFailure 'Gradle ApplicationId' 'applicationId 不是唯一的新包值'
    }

    # 2. 使用固定 Base move manifest 验证 110 个历史文件逐一映射。
    if (-not (Test-Path $MoveManifest -PathType Leaf)) {
        Report-IdentityFailure 'Move Manifest' "缺失 $MoveManifest"
    } else {
        $manifestPaths = @(
            Get-Content $MoveManifest |
                Where-Object { $_ -match '^app/src/(main|test|androidTest)/java/com/elio/skillroundtable/' }
        )
        $mainCount = @($manifestPaths | Where-Object { $_ -like 'app/src/main/*' }).Count
        $unitCount = @($manifestPaths | Where-Object { $_ -like 'app/src/test/*' }).Count
        $androidCount = @($manifestPaths | Where-Object { $_ -like 'app/src/androidTest/*' }).Count
        $uniqueCount = @($manifestPaths | Sort-Object -Unique).Count

        if (
            $manifestPaths.Count -eq 110 -and
            $uniqueCount -eq 110 -and
            $mainCount -eq 71 -and
            $unitCount -eq 31 -and
            $androidCount -eq 8
        ) {
            Report-IdentitySuccess 'Move Manifest' '固定 Base 清单为 Main 71 / Unit 31 / Android 8，共 110 个唯一文件'
        } else {
            Report-IdentityFailure 'Move Manifest' (
                "清单计数异常：main=$mainCount unit=$unitCount androidTest=$androidCount " +
                "total=$($manifestPaths.Count) unique=$uniqueCount"
            )
        }

        $missingTargets = [System.Collections.Generic.List[string]]::new()
        foreach ($oldPath in $manifestPaths) {
            $newPath = $oldPath.Replace('/com/elio/skillroundtable/', '/com/elio/jianyu/')
            if (-not (Test-Path $newPath -PathType Leaf)) {
                $missingTargets.Add($newPath)
            }
        }
        if ($missingTargets.Count -eq 0) {
            Report-IdentitySuccess 'Package Move Mapping' '110 个 Base 文件均存在唯一新路径目标'
        } else {
            Report-IdentityFailure 'Package Move Mapping' (
                "缺失 $($missingTargets.Count) 个迁移目标：$($missingTargets -join ', ')"
            )
        }
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

    foreach ($root in $currentRoots) {
        $category = switch -Wildcard ($root) {
            '*src/main/*' { 'Main Source Dir' }
            '*src/test/*' { 'Unit Test Dir' }
            default { 'Android Test Dir' }
        }
        if (Test-Path $root -PathType Container) {
            Report-IdentitySuccess $category "新目录存在：$root"
        } else {
            Report-IdentityFailure $category "新目录缺失：$root"
        }
    }

    foreach ($root in $legacyRoots) {
        $category = switch -Wildcard ($root) {
            '*src/main/*' { 'Legacy Main Source Dir' }
            '*src/test/*' { 'Legacy Unit Test Dir' }
            default { 'Legacy Android Test Dir' }
        }
        if (Test-Path $root) {
            Report-IdentityFailure $category "旧目录仍存在：$root"
        } else {
            Report-IdentitySuccess $category "旧目录已清除：$root"
        }
    }

    $currentTracked = Get-TrackedFiles -Paths $currentRoots
    if (
        $currentTracked.Count -eq 111 -and
        $currentTracked -contains 'app/src/androidTest/java/com/elio/jianyu/identity/AppIdentityIsolationTest.kt'
    ) {
        Report-IdentitySuccess 'Tracked Source Count' '新包下共 111 个已跟踪文件（Base 110 + 身份测试 1）'
    } else {
        Report-IdentityFailure 'Tracked Source Count' (
            "新包已跟踪文件数量或身份测试异常：count=$($currentTracked.Count)"
        )
    }

    $remainingLegacyTracked = Get-TrackedFiles -Paths $legacyRoots
    if ($remainingLegacyTracked.Count -eq 0) {
        Report-IdentitySuccess 'Legacy Tracked Files' '三个旧包根目录没有已跟踪文件'
    } else {
        Report-IdentityFailure 'Legacy Tracked Files' (
            "旧目录仍有已跟踪文件：$($remainingLegacyTracked -join ', ')"
        )
    }

    # 3. Kotlin 包声明和活动源码旧包残留。
    $kotlinFiles = @(
        foreach ($root in $currentRoots) {
            if (Test-Path $root) {
                Get-ChildItem $root -Recurse -File -Filter '*.kt'
            }
        }
    )
    $badPackageDeclarations = [System.Collections.Generic.List[string]]::new()
    $legacySourceHits = [System.Collections.Generic.List[string]]::new()
    foreach ($file in $kotlinFiles) {
        $content = Get-Content $file.FullName -Raw
        if ($content -notmatch '(?m)^package\s+com\.elio\.jianyu(?:\.|\s*$)') {
            $badPackageDeclarations.Add($file.FullName)
        }
        if ($content.Contains($LegacyPackage, [System.StringComparison]::Ordinal)) {
            $legacySourceHits.Add($file.FullName)
        }
    }

    if ($badPackageDeclarations.Count -eq 0) {
        Report-IdentitySuccess 'Kotlin Package Declarations' '全部活动 Kotlin 文件声明 com.elio.jianyu 包'
    } else {
        Report-IdentityFailure 'Kotlin Package Declarations' (
            "存在异常 package 声明：$($badPackageDeclarations -join ', ')"
        )
    }

    if ($legacySourceHits.Count -eq 0) {
        Report-IdentitySuccess 'Active Source Legacy References' '活动 Kotlin 源码和测试无完整旧包名残留'
    } else {
        Report-IdentityFailure 'Active Source Legacy References' (
            "活动源码仍含旧包名：$($legacySourceHits -join ', ')"
        )
    }

    $uiAgents = Get-Content 'app/src/main/java/com/elio/jianyu/ui/AGENTS.md' -Raw
    if (
        $uiAgents -match 'app/src/main/java/com/elio/jianyu/ui/' -and
        $uiAgents -notmatch 'app/src/main/java/com/elio/skillroundtable/ui/'
    ) {
        Report-IdentitySuccess 'UI AGENTS Path' 'UI 目录规则已指向新包路径'
    } else {
        Report-IdentityFailure 'UI AGENTS Path' 'UI 目录规则仍包含旧路径或缺失新路径'
    }

    # 4. App 名称、Manifest 与运行脚本。
    $stringsXml = Get-Content 'app/src/main/res/values/strings.xml' -Raw
    if ($stringsXml -match '<string\s+name="app_name">见域</string>') {
        Report-IdentitySuccess 'App Name' 'app_name 精确为见域'
    } else {
        Report-IdentityFailure 'App Name' 'app_name 未设置为见域'
    }

    $manifest = Get-Content 'app/src/main/AndroidManifest.xml' -Raw
    if (
        $manifest -match 'android:name="\.MainActivity"' -and
        $manifest -match 'android:label="@string/app_name"' -and
        $manifest -notmatch 'com\.elio\.(skillroundtable|jianyu)'
    ) {
        Report-IdentitySuccess 'Manifest Identity' 'Manifest 使用相对 MainActivity 和 app_name，未硬编码新旧包名'
    } else {
        Report-IdentityFailure 'Manifest Identity' 'Manifest Activity、Label 或包名硬编码不符合约束'
    }

    $runScript = Get-Content 'run.ps1' -Raw
    if (
        $runScript -match "\`$packageName\s*=\s*'com\.elio\.jianyu'" -and
        $runScript -match "\`$activityName\s*=\s*'com\.elio\.jianyu\.MainActivity'" -and
        $runScript -match '开始编译并安装见域' -and
        $runScript -notmatch 'com\.elio\.skillroundtable'
    ) {
        Report-IdentitySuccess 'Run Script' '运行脚本使用见域名称、新包和新 Launcher'
    } else {
        Report-IdentityFailure 'Run Script' '运行脚本身份常量不完整或仍含旧包'
    }

    # 5. Room v5、旧 Schema 冻结和新旧 Schema 语义一致性。
    if (Test-Path $LegacySchema -PathType Leaf) {
        Report-IdentitySuccess 'Legacy Room Schema' "旧 FQCN Schema 保留：$LegacySchema"
    } else {
        Report-IdentityFailure 'Legacy Room Schema' "旧 FQCN Schema 缺失：$LegacySchema"
    }

    if (Test-Path $CurrentSchema -PathType Leaf) {
        Report-IdentitySuccess 'Room Schema' "新 FQCN Schema 存在：$CurrentSchema"
    } else {
        Report-IdentityFailure 'Room Schema' "新 FQCN Schema 缺失：$CurrentSchema"
    }

    if ((Test-Path $LegacySchema) -and (Test-Path $CurrentSchema)) {
        try {
            $oldRaw = Get-Content $LegacySchema -Raw
            $newRaw = Get-Content $CurrentSchema -Raw
            $null = $oldRaw | ConvertFrom-Json
            $null = $newRaw | ConvertFrom-Json
            $oldNormalized = Get-NormalizedText $LegacySchema
            $newNormalized = Get-NormalizedText $CurrentSchema
            if ($oldNormalized -ceq $newNormalized) {
                Report-IdentitySuccess 'Room Schema Equivalence' '新旧 5.json 规范化换行后字节一致，JSON 可解析'
            } else {
                Report-IdentityFailure 'Room Schema Equivalence' '新旧 5.json 存在换行符以外的差异'
            }
        } catch {
            Report-IdentityFailure 'Room Schema Equivalence' "Schema JSON 解析失败：$($_.Exception.Message)"
        }

        $gitDiffOutput = @(& git diff --exit-code $BaseSha -- $LegacySchema 2>&1)
        if ($LASTEXITCODE -eq 0) {
            Report-IdentitySuccess 'Legacy Schema Freeze' '旧 FQCN Schema 与固定 Base 完全一致'
        } else {
            Report-IdentityFailure 'Legacy Schema Freeze' (
                "旧 FQCN Schema 相对固定 Base 发生变化：$($gitDiffOutput -join [Environment]::NewLine)"
            )
        }
    }

    $databaseSource = Get-Content 'app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt' -Raw
    if (
        $databaseSource -match 'version\s*=\s*5' -and
        $databaseSource -match 'MIGRATION_1_2' -and
        $databaseSource -match 'MIGRATION_2_3' -and
        $databaseSource -match 'MIGRATION_3_4' -and
        $databaseSource -match 'MIGRATION_4_5' -and
        $databaseSource -match '"roundtable_database"'
    ) {
        Report-IdentitySuccess 'Room Runtime Contract' 'Room version、迁移链和数据库名保持原契约'
    } else {
        Report-IdentityFailure 'Room Runtime Contract' 'Room version、迁移链或数据库名发生非预期变化'
    }

    $keyStoreSource = Get-Content 'app/src/main/java/com/elio/jianyu/network/EncryptedApiKeyStore.kt' -Raw
    if (
        $keyStoreSource -match 'KEY_ALIAS\s*=\s*"skill_roundtable_api_key_v1"' -and
        $keyStoreSource -match 'FILE_NAME\s*=\s*"gemini_api_keys\.enc"' -and
        $keyStoreSource -match 'TRANSFORMATION\s*=\s*"AES/GCM/NoPadding"'
    ) {
        Report-IdentitySuccess 'Key Store Contract' 'Key alias、密文文件名和 AES-GCM 格式保持不变'
    } else {
        Report-IdentityFailure 'Key Store Contract' 'Key Store 契约发生非预期变化'
    }

    # 6. CI 必须同时验证新身份、历史 Schema 和隔离测试执行顺序。
    $ciPath = '.github/workflows/android-ci.yml'
    $ciLines = Get-Content $ciPath
    $ciYaml = $ciLines -join "`n"
    $invalidLegacyCiLines = @(
        $ciLines | Where-Object {
            $_ -match 'com\.elio\.skillroundtable' -and
            $_ -notmatch '^\s+LEGACY_(PACKAGE|SCHEMA):'
        }
    )
    if (
        $ciYaml -match 'CURRENT_PACKAGE:\s*com\.elio\.jianyu' -and
        $ciYaml -match 'CURRENT_LAUNCHER:\s*com\.elio\.jianyu\.MainActivity' -and
        $ciYaml -match 'CURRENT_SCHEMA:\s*app/schemas/com\.elio\.jianyu\.data\.RoundtableDatabase/5\.json' -and
        $ciYaml -match 'LEGACY_PACKAGE:\s*com\.elio\.skillroundtable' -and
        $ciYaml -match 'LEGACY_SCHEMA:\s*app/schemas/com\.elio\.skillroundtable\.data\.RoundtableDatabase/5\.json' -and
        $ciYaml -match 'AppIdentityIsolationTest' -and
        $ciYaml -match 'notClass=com\.elio\.jianyu\.identity\.AppIdentityIsolationTest' -and
        $invalidLegacyCiLines.Count -eq 0
    ) {
        Report-IdentitySuccess 'CI Config' 'CI 使用新包并仅通过显式 LEGACY 常量保留旧身份验收引用'
    } else {
        Report-IdentityFailure 'CI Config' (
            "CI 身份常量、隔离测试顺序或旧包允许清单异常；非法旧包行数=$($invalidLegacyCiLines.Count)"
        )
    }

    # 7. 当前状态文档不得继续把新身份描述为待实施。
    $readme = Get-Content 'README.md' -Raw
    $agents = Get-Content 'AGENTS.md' -Raw
    if (
        $readme -match 'App 名称：见域' -and
        $readme -match 'namespace / applicationId：com\.elio\.jianyu' -and
        $agents -match 'App 用户可见名称：见域' -and
        $agents -match 'namespace / applicationId：com\.elio\.jianyu' -and
        $agents -match 'app/src/main/java/com/elio/jianyu/'
    ) {
        Report-IdentitySuccess 'Current Identity Documentation' 'README 与 AGENTS 已记录当前见域身份'
    } else {
        Report-IdentityFailure 'Current Identity Documentation' 'README 或 AGENTS 仍缺少当前见域身份事实'
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
