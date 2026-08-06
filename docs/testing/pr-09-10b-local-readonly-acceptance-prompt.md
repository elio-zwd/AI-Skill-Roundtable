# PR09-10B 本地 AI 严格只读验收 Prompt

你现在负责对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 Draft PR #49 执行严格只读验收。

目标：验证 PR09-10B 独立 AudioAsset 业务链，包括 Room Repository、显式生成、WorkManager、BYOK Gateway、受控文件、播放、取消、重试、Missing/Orphan 检查、删除请求和 PR09-12 交接。

## 一、严格只读纪律

只允许：拉取、检出、读取、编译、测试、安装测试 APK、运行测试和收集日志。

禁止：

- 修改、格式化或删除仓库文件；
- 创建 Commit、推送、变基、合并、标记 Ready；
- 使用 `git clean`、`git reset --hard` 或其他破坏命令；
- 删除真实 App 用户数据、调用 `pm clear` 或卸载用户当前使用的包；
- 在报告中暴露 API Key、来源正文、文件绝对路径或完整 Generation Key；
- 用真实付费 Key 运行自动化压力测试。

Gradle/KSP 可能产生 `build/` 或未跟踪 Schema。必须如实记录，不能提交或删除；结束时分别报告已跟踪差异和未跟踪文件。

## 二、锁定目标

```powershell
git fetch origin --prune
git checkout feat/pr-09-10b-audio-assets
git pull --ff-only origin feat/pr-09-10b-audio-assets
git status --short
git rev-parse HEAD
git rev-parse origin/main
git merge-base --is-ancestor origin/main HEAD
```

从 GitHub PR #49 页面或 API 获取当前精确 Head，并要求 `git rev-parse HEAD` 完全一致。若 Head 已变化，停止并报告，不得在旧 Head 上给出 PASS。

记录操作系统、PowerShell、Git、JDK/Javac、Gradle、ADB、设备型号、Android 版本和 API Level。

## 三、差异与架构门禁

```powershell
git diff --name-status origin/main...HEAD
git diff --check origin/main...HEAD
pwsh -NoProfile -File tools/check-app-identity.ps1
```

静态核对：

1. Room 保持 v11，没有 PR09-10B 竞争 Migration 或 Schema 漂移；
2. 正式链以 `audio_assets` 为事实源；
3. `audio/assets/` 不引用 `ChatDao`、`RoundtableDatabase`、旧 `AudioTranscodeWorker`、`updateMessageAudio`、`audioFilePath`、`LiveApiClient`、Retrofit、OkHttp 或环境变量 Key；
4. Worker Data 只有 `audio_asset_id`；
5. 正式音频代码不写旧 Message 音频字段；
6. 恢复、导航、重组和打开音频工作区不会自动创建资产、排队、联网或使用 Key；
7. Orphan 仅报告，不存在扫描后自动递归删除。

## 四、构建与 JVM 门禁

```powershell
.\gradlew.bat --stop
.\gradlew.bat compileDebugKotlin --stacktrace
.\gradlew.bat testDebugUnitTest --stacktrace
.\gradlew.bat lintDebug --stacktrace
.\gradlew.bat assembleDebug --stacktrace
.\gradlew.bat assembleRelease --stacktrace
.\gradlew.bat assembleDebugAndroidTest --stacktrace
git diff --exit-code -- app/schemas
```

从 XML 报告统计测试套件、用例、Failure、Error、Skipped；单独统计 `audio/assets`、`network/audio`、音频工作区和标签测试。Lint 分别统计 Error 与 Warning。

## 五、Room 音频 Repository 专项

在可隔离的测试设备/模拟器上执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest `
  -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.AudioAssetRepositoryDatabaseTest `
  --stacktrace
```

核对：

- 完成 Message 与确认 Artifact 可加载；
- Pending、跨 Issue、跨 Stage 被拒绝；
- Generation Key 幂等只生成一个资产；
- AVAILABLE、FAILED、CANCELED、MISSING 与删除请求使用数据库 CAS；
- `purgeRequestedAt` 后迟到成功不能覆盖；
- CANCELED 只有显式重试且来源/配置/Key 匹配时恢复 PENDING；
- 无 Room v12、无 destructive migration。

## 六、全量设备测试

先确认测试设备不会破坏真实用户数据，再执行：

```powershell
adb devices
.\gradlew.bat connectedDebugAndroidTest --stacktrace
```

如全量存在既有失败，必须：

1. 记录精确测试类、方法、堆栈、Logcat 和退出码；
2. 单独复现失败用例；
3. 判断是否由 PR09-10B 引入，但不得仅凭包名直接宣称“无关”；
4. 把“未执行”“跳过”“进程崩溃”和“断言失败”分开报告。

## 七、设备与人工重点场景

使用测试数据和测试 Key；没有安全测试 Key 时，将真实联网生成标为“未验证”，不得伪造通过。

1. 打开议题工作区，确认存在唯一音频入口，没有新增顶层导航；
2. 页面恢复、旋转、导航返回、进程强停后重开：不自动生成、不自动排队、不自动联网；
3. 点击完成消息或确认成果的“生成音频”：确认框出现；取消后资产、Work、文件和 Key 使用均不变化；
4. 确认后只创建一个 PENDING AudioAsset 和一个唯一 Work；连续双击不重复；
5. 无 Key、离线、超时、鉴权失败、限流、空响应、空间不足均显示可理解失败且不破坏来源对象；
6. WAV 文件位于 App 私有 `filesDir/jianyu-audio/`，数据库只保存相对路径；
7. `.part` 唯一、正式文件不覆盖、DB AVAILABLE 失败时正式文件回滚；
8. 播放、暂停、恢复、停止、切换与旧播放器释放；缺失文件不能继续播放；
9. 取消先持久化 CANCELED，迟到成功不覆盖；
10. FAILED、MISSING、CANCELED 只能经显式确认重试；
11. “检查缺失与孤儿”只在点击后运行，AVAILABLE 缺失文件被标记 MISSING，孤儿只显示数量/报告、不删除；
12. “请求删除”先确认，只写 `purgeRequestedAt` 并阻止迟到回调，不物理删除正式文件；
13. 检查 360dp、200% 字号、键盘、TalkBack 语义、明暗主题和滚动可达性；
14. 检查日志不存在 API Key、正文、绝对路径和完整 Generation Key。

## 八、进程恢复与后台任务

使用测试资产验证：

- PENDING 任务被系统恢复时只按既有 WorkManager 语义继续；
- App 自身启动、页面恢复或状态读取不会重新 enqueue；
- Work 输入只有 `audio_asset_id`；
- Worker 业务失败返回 Failure，等待用户显式重试，不调用 WorkManager 自动 retry；
- 显式重试使用 REPLACE，普通请求使用 KEEP。

## 九、结束状态

```powershell
git status --short
git diff --exit-code
git diff --exit-code -- app/schemas
```

报告必须包含：

1. 最终结论：`PASS`、`PASS_WITH_NOTES` 或 `FAIL`；
2. 仓库、PR、Base、分支、预期与实际 Head；
3. 环境版本和时间；
4. 每条命令、退出码和关键日志；
5. JVM、Lint、构建、AndroidTest 的精确数量；
6. Room/CAS、WorkManager、BYOK、文件、播放、UI、恢复、Missing/Orphan、删除请求的结果；
7. 已实际执行、仅静态检查、未验证三类状态；
8. 失败项的复现步骤与可能根因；
9. 已知风险和重点回归；
10. 最终 Git 状态。

全程不得修改、提交、推送、标记 Ready 或合并。
