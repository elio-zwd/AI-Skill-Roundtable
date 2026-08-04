# PR09-09 本地 AI 严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 Draft PR #39 做独立、严格、只读验收。

目标 PR：

```text
https://github.com/elio-zwd/AI-Skill-Roundtable/pull/39
```

目标分支：

```text
feat/pr-09-09-material-context-source
```

本任务只允许读取、构建、测试、模拟器验证和报告，不得修改任何文件，不得自动格式化，不得提交、推送、变基、合并、标记 Ready、关闭 PR、删除分支或修改 GitHub 设置。发现问题后只输出证据，不得自行修复；所有修复回到远端开发对话完成。

## 一、报告必须记录

```text
操作系统与版本
PowerShell 版本
Git 版本
JDK 版本
Android SDK / Build Tools 版本
Gradle Wrapper 版本
adb 版本
模拟器或真机 ID、型号、Android API Level
验收开始和结束时间
目标 Base SHA
目标 Head SHA
工作区初始与最终状态
每条命令、退出码、测试总数、通过数、失败数、跳过数
关键失败日志与复现步骤
```

严格区分：

```text
已实际执行并通过
GitHub CI 已通过
仅完成静态检查
尚未验证
```

## 二、检出与只读门禁

在仓库根目录执行：

```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber,OSArchitecture
$PSVersionTable.PSVersion
git --version
java -version
adb version
Get-Date -Format "yyyy-MM-dd HH:mm:ss K"

git fetch origin --prune
git checkout feat/pr-09-09-material-context-source
git pull --ff-only origin feat/pr-09-09-material-context-source
git status --short
git rev-parse HEAD
git rev-parse origin/main
git merge-base HEAD origin/main
git rev-list --left-right --count origin/main...HEAD
git diff --name-status origin/main...HEAD
git diff --check origin/main...HEAD
git diff --exit-code
```

要求：

```text
工作区初始为空
Head 精确等于 PR #39 最新 Head
Base 为 main@228ec6f972684512fb6287d89c253da6c4aebd91，除非 PR 页面显示 main 后续合法变化
PR 仍为 Draft
没有其他任务分支内容混入
不存在 .github/pr09-*.patch* 或临时 Workflow
```

记录当前 Room 版本和 Schema：

```powershell
Select-String -Path app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt -Pattern "version\s*=|MIGRATION_8_9"
Get-FileHash app/schemas/com.elio.jianyu.data.RoundtableDatabase/9.json -Algorithm SHA256
```

## 三、敏感信息与架构门禁

```powershell
pwsh -NoProfile -File .\tools\check-secrets.ps1 -IncludeHistory
pwsh -NoProfile -File .\tools\check-app-identity.ps1

git grep -n -E "ResourceLifecycleDao|JianyuRepositoryDao" -- app/src/main/java/com/elio/jianyu/ui
git grep -n -E "Gemini|generateContent|streamGenerateContent" -- app/src/main/java/com/elio/jianyu/data/MaterialContext* app/src/main/java/com/elio/jianyu/ui/screens/context
git grep -n "ExecutionContextBuilder" -- app/src/main/java
git grep -n "ExecutionRunCoordinator" -- app/src/main/java
```

验收要求：

```text
UI、ViewModel、上下文组件不访问 DAO
MaterialContext Repository 不调用 Gemini 或网络 Gateway
项目仍只有一个 ExecutionContextBuilder
执行只使用现有 ExecutionRunCoordinator
没有把个人背景自动注入 System Prompt
没有默认全量资料或背景选择
敏感正文不出现在日志、异常、遥测、toString 或普通列表语义
```

## 四、干净构建

先停止旧 Daemon，再清理当前模块：

```powershell
.\gradlew.bat --stop
.\gradlew.bat :app:clean
.\gradlew.bat --no-daemon --stacktrace :app:compileDebugKotlin
.\gradlew.bat --no-daemon --stacktrace :app:compileDebugAndroidTestKotlin
.\gradlew.bat --no-daemon --stacktrace :app:testDebugUnitTest
.\gradlew.bat --no-daemon --stacktrace :app:lintDebug
.\gradlew.bat --no-daemon --stacktrace :app:assembleDebug
.\gradlew.bat --no-daemon --stacktrace :app:assembleRelease
```

每条命令记录退出码。不得把编译成功写成测试通过。

## 五、定向 JVM 测试

根据实际测试类名执行：

```powershell
.\gradlew.bat testDebugUnitTest --tests "*MaterialContextModelsTest"
.\gradlew.bat testDebugUnitTest --tests "*ExecutionContextUsageGateTest"
.\gradlew.bat testDebugUnitTest --tests "*ContextConfirmationUiStateTest"
.\gradlew.bat testDebugUnitTest --tests "*ResourcesUiStateTest"
.\gradlew.bat testDebugUnitTest --tests "*MaterialContextArchitectureTest"
.\gradlew.bat testDebugUnitTest --tests "*ExecutionRunCoordinatorTest"
.\gradlew.bat testDebugUnitTest --tests "*ExecutionContextBuilderTest"
```

至少核对：

```text
SHA-256 + UTF-8 + CRLF/CR→LF
个人背景默认未选择
小于 24,000 通过
正好 24,000 通过
24,001 拒绝
超限不截断
重复来源拒绝
同来源不同 Hash 冲突
未授权联网在 Runtime 创建前拒绝
敏感来源需要独立确认
Contribution 与 Usage Snapshot 必须描述同一正文
空上下文继续兼容 PR09-07
UI / ViewModel 不访问 DAO
```

报告 JVM 全量：

```text
测试类数
测试总数
通过数
失败数
跳过数
PR09-09 新增测试数
```

## 六、模拟器准备

选择一个在线设备，记录 ID：

```powershell
adb devices -l
adb -s <DEVICE_ID> shell getprop ro.product.model
adb -s <DEVICE_ID> shell getprop ro.build.version.sdk
adb -s <DEVICE_ID> uninstall com.elio.jianyu
adb -s <DEVICE_ID> uninstall com.elio.jianyu.test
```

卸载不存在可以记录为预期非零，不得当成产品失败。确保没有其他对话或测试同时占用该设备。

## 七、定向 Instrumentation

先安装并执行资料与背景数据层：

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.MaterialContextRepositoryTest

.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.MaterialContextMigrationTest
```

再执行 Compose 与工作区确认：

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.resources.ResourcesScreenTest

.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.context.ContextConfirmationDialogTest
```

同时运行现有关键回归：

```powershell
.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.ExecutionRuntimeDatabaseTest

.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoomJianyuRepositoryDatabaseTest

.\gradlew.bat connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoundtableDatabaseMigrationTest
```

定向测试必须验证：

```text
资料创建、相同命令幂等、相同 ID 不同正文冲突
资料编辑更新当前 Hash
来源发布日期、采集时间、本地创建时间不混淆
资料归档后不出现在 ACTIVE 列表，恢复后重新出现
个人背景创建、停用、归档、恢复与默认不选
选择后来源被编辑会在 Runtime 创建事务再次拒绝
Run、Participant、State、Budget、两类 Usage Snapshot 原子创建
相同幂等键相同上下文只产生一个 Run
相同幂等键不同上下文冲突
编辑来源不改变历史 Usage Snapshot
普通删除不改变历史
彻底清除需要 DELETED→PURGE_REQUESTED→PURGED
清除后当前和历史正文、标题、Hash、敏感标记不可恢复
匿名占位不暴露标题、来源类别、Hash、正文或敏感类别
PRAGMA foreign_key_check = 0
```

## 八、连续 Room Migration

Room 目标版本为 v9。执行现有完整迁移套件，并至少验证：

```text
v1→v9
v2→v9
v3→v9
v4→v9
v5→v9
v6→v9
v7→v9
v8→v9
```

重点核对：

```text
8.json 保留
9.json 与运行时生成结果完全一致
无 destructive migration
PR09-07 ExecutionRun、Participant、Budget 表结构和数据不漂移
旧 Material / Personal Context 数据保留
旧 Usage Snapshot networkAllowed=false
旧 Usage Snapshot sensitive=true
旧记录未被伪造为已授权联网
生命周期按 v8 事实保守迁移
新索引存在
PRAGMA foreign_key_check = 0
```

可以在只读验收过程中生成临时数据库和构建产物，但不得修改或提交源码；结束后工作区必须恢复干净。若 Room 编译导致 `9.json` 变化，视为失败并报告差异，不得覆盖远端文件。

## 九、全量设备测试

执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

PR #38 后基线为：

```text
112 / 112 PASS
```

PR09-09 不允许以“基线既有失败”放行。报告：

```text
总数
通过数
失败数
跳过数
PR09-09 新增 Instrumentation 数
最终总数
```

任何失败都保留：

```text
精确测试类与方法
命令
退出码
关键日志
复现步骤
可能原因
设备状态
```

不得自行修改测试或生产代码。

## 十、真实业务场景

在模拟器真实验证以下场景，并为每项记录 PASS / FAIL / NOT VERIFIED：

1. 新建文本资料，填写标题、来源类型、来源定位、来源发布日期、采集时间和正文；
2. 编辑资料后当前 Hash 变化；
3. 新建敏感个人背景；
4. 打开 Issue 工作区时所有个人背景默认不选；
5. 资料与个人背景可分别选择；
6. 可编辑实际发送摘录；
7. 未勾选“允许本次发送”时确认按钮禁用；
8. 敏感项未二次确认时确认按钮禁用；
9. 字符数实时显示，总数正好 24,000 可确认；
10. 超过 24,000 时阻止执行且不截断正文；
11. 取消确认后不创建 Run、Pending、Usage，不消费预算、不联网；
12. 取消后重新打开仍保留未确认草稿；
13. 确认后 Run ID 与两类 Usage Snapshot 一致；
14. 实际发送内容与 Usage Snapshot 正文和 Hash 一致；
15. 流式执行、Stop 与迟到回调仍符合 PR09-07；
16. 重试不重复成功成员；
17. 重试先展示原 Run 实际使用来源，但默认未确认；
18. 重试不会自动读取资料库当前正文替换历史；
19. 普通删除后来源不再用于新执行，但历史回答和快照不变；
20. 彻底清除前显示关联 Issue、Stage、Usage、Run 数量并要求二次确认；
21. 清除后历史位置显示统一匿名占位；
22. 清除后不可直接重试原正文；
23. 单个候选读取失败只影响该项，不破坏整个 Issue 页面；
24. 进程强停并恢复后不自动联网、不自动确认、不自动创建新快照；
25. 恢复页展示该 Run 实际使用来源，而非来源当前版本。

## 十一、隐私与日志

清空 Logcat 后执行资料创建、敏感背景选择、确认、运行、失败、停止、重试和清除：

```powershell
adb -s <DEVICE_ID> logcat -c
# 执行业务场景
adb -s <DEVICE_ID> logcat -d > pr09-09-logcat.txt
```

在本地临时日志中搜索测试正文、标题、Hash、API Key 特征和错误内容：

```powershell
Select-String -Path .\pr09-09-logcat.txt -Pattern "资料全文包含资料确认摘录|跨议题背景|AIza|contentHash|sourceHash"
```

要求：

```text
Logcat 无资料正文
Logcat 无个人背景正文
异常与 Snackbar 无正文
遥测无正文
无 API Key
匿名占位无原始标题、来源类型、Hash、正文或敏感类别
敏感列表未进入详情时不暴露完整正文
```

本地临时日志不得提交；验收结束后删除，并确认工作区干净。

## 十二、UI 与无障碍

至少验证：

```text
360dp 窄屏
200% 系统字体
明亮主题
暗色主题
TalkBack
Activity 重建
系统返回键
资料/成果根 Tab 返回栈
资料库/个人背景页内切换
搜索与生命周期筛选
编辑 Dialog
上下文确认 Dialog
清除影响与二次确认 Dialog
加载、空、局部失败、存储失败、超限、未授权、陈旧选择和匿名占位状态
```

无障碍重点：

```text
选中、联网授权、敏感确认均有可理解标签
TalkBack 不在普通列表自动朗读完整敏感正文
按钮禁用原因可见
200% 字号不遮挡确认和取消操作
Activity 重建不会把未确认草稿变成已确认，也不会自动运行
```

## 十三、PR09-07 回归

必须复验：

```text
ExecutionRun 状态枚举未改变
Participant 状态枚举未改变
预算消费规则未改变
Stop 顺序未改变
迟到回调不会覆盖终态
成功成员在重试中被过滤
相同命令幂等
不同上下文同幂等键冲突
网络调用不在 Room 事务中
```

至少运行现有执行协调器 JVM 与设备测试，并在报告中单独列出统计。

## 十四、收尾只读检查

完成所有测试后执行：

```powershell
Remove-Item -ErrorAction SilentlyContinue .\pr09-09-logcat.txt
.\gradlew.bat --stop
git status --short
git diff --exit-code
git rev-parse HEAD
Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
```

必须满足：

```text
工作区为空
无源码、Schema、配置或测试文件变化
Head 与开始时完全一致
未创建 Commit
未推送
未修改 PR 状态
```

## 十五、最终报告格式

```text
# PR09-09 本地严格只读验收报告

## 1. 最终结论
PASS / FAIL / PASS WITH NOTES

## 2. 精确目标
PR、分支、Base、Head

## 3. 环境
OS、Shell、Git、JDK、Gradle、SDK、adb、设备

## 4. 只读门禁
初始/最终 status、diff、Head

## 5. 构建
每条命令、退出码、结果

## 6. JVM
总数、通过、失败、跳过、新增数

## 7. Instrumentation
定向与全量统计、PR #38 基线对比

## 8. Migration
v1→v9～v8→v9、Schema Hash、foreign_key_check

## 9. 关键业务场景
逐项 PASS / FAIL / NOT VERIFIED

## 10. 隐私与无障碍
Logcat、TalkBack、200% 字号、明暗主题

## 11. PR09-07 回归
状态机、预算、Stop、重试、迟到回调

## 12. 失败项
测试名、命令、退出码、关键日志、复现、可能原因

## 13. 未验证项
明确列出，禁止用推测填补

## 14. 工作区收尾
最终 status、diff、Head
```

本地 AI 只提交报告，不做任何修复。若结论不是完整 PASS，Draft PR #39 必须继续保持 Draft，并把报告反馈给远端开发对话。
