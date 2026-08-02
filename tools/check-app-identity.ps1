# tools/check-app-identity.ps1
# 见域应用身份静态门禁检查脚本

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:FailedCategoryCount = 0

function Report-IdentityFailure {
    param (
        [string]$Category,
        [string]$Message
    )
    Write-Host "[FAIL - $Category] $Message" -ForegroundColor Red
    $script:FailedCategoryCount++
}

function Report-IdentitySuccess {
    param (
        [string]$Category,
        [string]$Message
    )
    Write-Host "[PASS - $Category] $Message" -ForegroundColor Green
}

Write-Host "=== 见域应用身份静态门禁检查 ==="

# 1. 检查 app/build.gradle.kts 中的 namespace 和 applicationId
$buildGradle = Get-Content "app/build.gradle.kts" -Raw
if ($buildGradle -match 'namespace\s*=\s*"com\.elio\.jianyu"') {
    Report-IdentitySuccess "Gradle Namespace" "namespace 已正确设置为 com.elio.jianyu"
} else {
    Report-IdentityFailure "Gradle Namespace" "app/build.gradle.kts 中 namespace 未修改为 com.elio.jianyu"
}

if ($buildGradle -match 'applicationId\s*=\s*"com\.elio\.jianyu"') {
    Report-IdentitySuccess "Gradle ApplicationId" "applicationId 已正确设置为 com.elio.jianyu"
} else {
    Report-IdentityFailure "Gradle ApplicationId" "app/build.gradle.kts 中 applicationId 未修改为 com.elio.jianyu"
}

# 2. 检查主源码与单测源码根目录
if (Test-Path "app/src/main/java/com/elio/jianyu") {
    Report-IdentitySuccess "Main Source Dir" "新主源码目录 app/src/main/java/com/elio/jianyu 已存在"
} else {
    Report-IdentityFailure "Main Source Dir" "新主源码目录 app/src/main/java/com/elio/jianyu 缺失"
}

if (Test-Path "app/src/main/java/com/elio/skillroundtable") {
    Report-IdentityFailure "Legacy Main Source Dir" "旧主源码目录 app/src/main/java/com/elio/skillroundtable 仍残留"
} else {
    Report-IdentitySuccess "Legacy Main Source Dir" "旧主源码目录已完全清除"
}

if (Test-Path "app/src/test/java/com/elio/skillroundtable") {
    Report-IdentityFailure "Legacy Unit Test Dir" "旧单测目录 app/src/test/java/com/elio/skillroundtable 仍残留"
} else {
    Report-IdentitySuccess "Legacy Unit Test Dir" "旧单测目录已完全清除"
}

# 3. 检查 Room 新 Schema 输出
$newSchemaPath = "app/schemas/com.elio.jianyu.data.RoundtableDatabase/5.json"
if (Test-Path $newSchemaPath) {
    Report-IdentitySuccess "Room Schema" "新 Room Schema 5.json 已存在于 $newSchemaPath"
} else {
    Report-IdentityFailure "Room Schema" "新 Room Schema 5.json 缺失于 $newSchemaPath"
}

# 4. 检查 .github/workflows/android-ci.yml
$ciYaml = Get-Content ".github/workflows/android-ci.yml" -Raw
if ($ciYaml -match 'com\.elio\.skillroundtable') {
    Report-IdentityFailure "CI Config" ".github/workflows/android-ci.yml 包含旧包名或旧 Schema 路径硬编码 (com.elio.skillroundtable)"
} else {
    Report-IdentitySuccess "CI Config" ".github/workflows/android-ci.yml 已更新为新包名引用"
}

# 5. 检查 run.ps1
$runPs1 = Get-Content "run.ps1" -Raw
if ($runPs1 -match 'com\.elio\.skillroundtable') {
    Report-IdentityFailure "Run Script" "run.ps1 包含旧包名或旧 Activity 硬编码 (com.elio.skillroundtable)"
} else {
    Report-IdentitySuccess "Run Script" "run.ps1 已更新为新包名与 Activity 引用"
}

Write-Host "================================"
if ($script:FailedCategoryCount -gt 0) {
    Write-Host "静态身份检查未通过，共发现 $script:FailedCategoryCount 类不符合约束的未符合项。" -ForegroundColor Red
    exit 1
} else {
    Write-Host "静态身份检查全数通过！" -ForegroundColor Green
    exit 0
}
