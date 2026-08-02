# PR09-01 应用身份迁移计划自检与规范性修正

> 对应计划：[`pr-09-01-jianyu-app-identity-plan.md`](./pr-09-01-jianyu-app-identity-plan.md)
>
> 状态：计划自检修正。本文与主计划共同组成施工单；若两者冲突，以本文为准。
>
> 精确 Base：`4de0bfb0480ea84d3a88af12c11167a3a27c38dc`

## 1. 自检结论

主计划方向正确，但“目录移动范围包括但不限于”不满足任务要求的逐文件门禁。当前 GitHub 连接器可以搜索和读取文件，却不能对目录执行递归列表；因此不能把搜索结果上限伪装成完整文件树。

本修正使用固定 Base Commit 的 Git tree 集合精确定义全部移动文件。该定义不是 `TODO` 或动态候选：Base SHA 已冻结，同一命令在任何完整 Git 工作区中都会得到唯一确定结果。

## 2. 规范性逐文件移动集合

实施阶段必须先执行：

```powershell
$BaseSha = '4de0bfb0480ea84d3a88af12c11167a3a27c38dc'

$MainFiles = git ls-tree -r --name-only $BaseSha -- `
  app/src/main/java/com/elio/skillroundtable
$UnitTestFiles = git ls-tree -r --name-only $BaseSha -- `
  app/src/test/java/com/elio/skillroundtable
$AndroidTestFiles = git ls-tree -r --name-only $BaseSha -- `
  app/src/androidTest/java/com/elio/skillroundtable

@(
  '# PR09-01 package move manifest',
  "Base: $BaseSha",
  '',
  '## main',
  $MainFiles,
  '',
  '## test',
  $UnitTestFiles,
  '',
  '## androidTest',
  $AndroidTestFiles
) | Set-Content `
  docs/testing/pr-09-01-package-move-manifest.txt `
  -Encoding utf8
```

主计划的移动文件精确定义为：

```text
M = M_main ∪ M_test ∪ M_androidTest

M_main = git ls-tree -r --name-only 4de0bfb... -- app/src/main/java/com/elio/skillroundtable
M_test = git ls-tree -r --name-only 4de0bfb... -- app/src/test/java/com/elio/skillroundtable
M_androidTest = git ls-tree -r --name-only 4de0bfb... -- app/src/androidTest/java/com/elio/skillroundtable
```

每个源文件 `p ∈ M` 的唯一目标路径通过以下纯替换得到：

```text
/main/java/com/elio/skillroundtable/    → /main/java/com/elio/jianyu/
/test/java/com/elio/skillroundtable/    → /test/java/com/elio/jianyu/
/androidTest/java/com/elio/skillroundtable/ → /androidTest/java/com/elio/jianyu/
```

不得排除 `ui/AGENTS.md`、无 package 声明的辅助文件或搜索未命中的已跟踪文件。

新增实施文件：

```text
docs/testing/pr-09-01-package-move-manifest.txt
```

该清单必须与首个测试门禁一起提交，后续 rename 后运行以下检查：

```powershell
$OldFiles = @($MainFiles + $UnitTestFiles + $AndroidTestFiles)
$MissingTargets = foreach ($OldPath in $OldFiles) {
  $NewPath = $OldPath `
    -replace '/main/java/com/elio/skillroundtable/', '/main/java/com/elio/jianyu/' `
    -replace '/test/java/com/elio/skillroundtable/', '/test/java/com/elio/jianyu/' `
    -replace '/androidTest/java/com/elio/skillroundtable/', '/androidTest/java/com/elio/jianyu/'
  if (-not (Test-Path $NewPath)) { $NewPath }
}

if ($MissingTargets) {
  $MissingTargets | ForEach-Object { Write-Error "缺失迁移目标：$_" }
  exit 1
}

$RemainingOldFiles = git ls-files -- `
  app/src/main/java/com/elio/skillroundtable `
  app/src/test/java/com/elio/skillroundtable `
  app/src/androidTest/java/com/elio/skillroundtable
if ($RemainingOldFiles) {
  $RemainingOldFiles | ForEach-Object { Write-Error "旧目录仍有文件：$_" }
  exit 1
}
```

## 3. Room Schema 修正

已从精确 Base 核对：

```text
存在：app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json
不存在：app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/1.json
```

因此本 PR 的明确处理是：

1. 保留旧 FQCN 的 `5.json`，Blob 和内容不得变化；
2. 迁移数据库类 package 后运行 KSP，真实生成新 FQCN 的 `5.json`；
3. 新旧 `5.json` 必须字节一致；
4. 不伪造 `1.json`～`4.json`，不新增 Migration；
5. 现有 `RoundtableDatabaseMigrationTest` 继续使用代码创建旧版本数据库并由新 FQCN 的 v5 Schema 验证最终结构。

验证命令：

```powershell
$OldSchema = 'app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json'
$NewSchema = 'app/schemas/com.elio.jianyu.data.RoundtableDatabase/5.json'

if (-not (Test-Path $OldSchema) -or -not (Test-Path $NewSchema)) {
  throw '新旧 Room 5.json 必须同时存在'
}

$OldHash = (Get-FileHash $OldSchema -Algorithm SHA256).Hash
$NewHash = (Get-FileHash $NewSchema -Algorithm SHA256).Hash
if ($OldHash -ne $NewHash) {
  throw "Room Schema 语义外差异：old=$OldHash new=$NewHash"
}
```

## 4. Android Keystore 可执行断言

主计划中的“新 UID 命名空间初始不可见”必须落为测试，不只写成平台推断。

`AppIdentityIsolationTest.kt` 增加：

```kotlin
@Test
fun legacyNamedKeystoreAlias_isNotVisibleInFreshJianyuSandbox() {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    assertFalse(
        "新包首次启动不得看见旧包 UID 下的同名 Key",
        keyStore.containsAlias("skill_roundtable_api_key_v1"),
    )
}
```

执行条件：

1. 使用干净的新包安装；
2. 旧包已通过正常 API Key 管理页写入测试 Key；
3. 两个包同时安装，且 `adb shell dumpsys package` / `cmd package list packages -U` 显示 UID 不同；
4. 新包测试必须仍返回 alias 不可见，`EncryptedApiKeyStore.read()` 为空；
5. 再打开旧包确认测试 Key 仍可读取。

若新包在测试前已经自行写入 Key，该断言无效，必须清理测试设备中的新包数据并重新执行；清理是破坏性设备动作，须由本地验收者明确记录，脚本不静默执行。

## 5. 首个 RED 的精确性

首个 RED 必须满足：

```text
测试类成功编译并启动
失败测试：targetPackage_usesJianyuApplicationId
Expected：com.elio.jianyu
Actual：com.elio.skillroundtable
```

以下不算有效 RED：

- 文件路径错误；
- 测试类找不到；
- import 或语法编译失败；
- Emulator 未启动；
- 与身份无关的既有测试失败。

## 6. Commit 边界修正

第一笔测试提交应包含：

```text
app/src/androidTest/java/com/elio/skillroundtable/identity/AppIdentityIsolationTest.kt
tools/check-app-identity.ps1
docs/testing/pr-09-01-package-move-manifest.txt
```

Commit：

```text
test: 增加新旧应用身份隔离测试
```

目录迁移提交必须让 Git 识别为 rename；若远端文件 API 只能制造大量 delete/add，则停止生产实施，转交具备本地 Git 工作区的执行环境。

## 7. 本轮尚未验证

当前对话没有可执行 Android 构建的本地仓库工作区，因此以下均尚未执行：

- `git ls-tree` 文件清单生成；
- 首个 RED；
- Gradle 编译、单测、Lint、Debug/Release 构建；
- Emulator Instrumentation；
- 双包安装和 UID / 数据 / Key 隔离；
- 新 Room Schema 生成与 SHA-256 比对。

本轮真实完成的只有 GitHub 只读仓库研究、计划文档和计划自检修正。