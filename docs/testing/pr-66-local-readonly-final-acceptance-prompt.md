# PR #66 见域最终本地 AI 严格只读验收 Prompt

你现在负责 GitHub 仓库：

`elio-zwd/AI-Skill-Roundtable`

Draft PR：

`#66 feat: 完成见域 UI 收口与正式备份`

目标分支：

`codex/ui-05-artifacts`

Base：

`main@53321b6b26d7084e97be027fe1098bcb1fe403c5`

远端生产代码审查冻结点至少应包含：

`681aaf2c450bcb4da9016170674b3819823ebcd4`

开始验收时不要硬用旧 SHA。必须读取 PR #66 当前精确 `headRefOid`，并以它作为 Expected Head；如果当前 Head 不包含上面的冻结点，或 PR 已不是 Draft，停止并报告 `NOT_VERIFIED`。

## 一、绝对纪律

这是**严格只读验收**。

全过程禁止：

```text
修改任何源码、测试、文档、配置或资源
自动格式化
自动修复
更新依赖
创建 commit
push
rebase
merge
修改 PR Draft/Ready 状态
删除分支
git reset --hard
git clean
用真实生产 API Key
调用真实生产模型完成测试
把用户正文、资料正文、个人背景正文、完整 Prompt、API Key 写进日志或证据文件名
```

允许：

```text
git fetch / checkout / pull --ff-only
读取 Git/PR/源码/测试
Gradle 构建和测试
安装当前精确 Head 构建的 debug APK
Instrumentation / Compose test
使用仓库 Fake/Test Network
adb 启动、停止、读取必要日志
在仓库外临时目录保存验收证据
```

如果任何命令导致仓库工作区发生修改，立即停止并报告 `FAIL`；不得自行修复。

## 二、先锁定精确版本

Windows / PowerShell 优先：

```powershell
git fetch origin --prune
git checkout codex/ui-05-artifacts
git pull --ff-only origin codex/ui-05-artifacts

git status --short
git branch --show-current
git rev-parse HEAD
git rev-parse origin/main
git merge-base HEAD origin/main
git merge-base --is-ancestor 681aaf2c450bcb4da9016170674b3819823ebcd4 HEAD
git diff --check origin/main...HEAD
git diff --exit-code

gh pr view 66 --repo elio-zwd/AI-Skill-Roundtable --json state,isDraft,headRefName,headRefOid,baseRefName,baseRefOid,mergeable
```

门禁：

```text
branch = codex/ui-05-artifacts
PR = OPEN + Draft
HEAD == PR #66 headRefOid
baseRefName = main
681aaf2c... 是 HEAD 祖先
初始工作区干净
```

不满足则输出 `NOT_VERIFIED`，不要继续在错误版本上验收。

## 三、记录环境

只记录必要信息：

```powershell
Get-CimInstance Win32_OperatingSystem | Select-Object Caption,Version,BuildNumber,OSArchitecture
$PSVersionTable.PSVersion
git --version
java -version
.\gradlew.bat --version
adb version
adb devices -l
Get-Date -Format "yyyy-MM-dd HH:mm:ss K"
```

若设备在线，再记录：

```powershell
adb -s <DEVICE> shell getprop ro.product.model
adb -s <DEVICE> shell getprop ro.build.version.sdk
adb -s <DEVICE> shell wm size
adb -s <DEVICE> shell wm density
```

设备不可用时继续完成静态/JVM/构建部分，设备项统一标记 `NOT_VERIFIED`，不得虚构 PASS。

## 四、读取适用规则

验收前只读：

```text
AGENTS.md
app/src/main/java/com/elio/jianyu/ui/AGENTS.md
docs/planning/codex-2026-09-22-completion-report.md
docs/superpowers/specs/2026-09-22-ui-09-conversation-integration.md
docs/planning/pr-09-09-interface-handoff.md
docs/superpowers/specs/2026-09-22-ui-08-data-privacy-backup.md
```

不要扫描无关历史文档。

## 五、静态门禁与完整构建

先停止旧 Gradle daemon：

```powershell
.\gradlew.bat --stop
```

依次执行，每条单独记录退出码；失败立即进入失败报告，不自动修：

```powershell
pwsh -NoProfile -File tools/check-app-identity.ps1
pwsh -NoProfile -File tools/check-secrets.ps1 -IncludeHistory

.\gradlew.bat :app:compileDebugKotlin
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:assembleDebugAndroidTest
```

不要把 `assembleDebugAndroidTest` 通过描述成 Instrumentation 已通过。

重点确认 JVM 回归至少包含/覆盖：

```text
RoundtableConversationPolicyTest
DialogCompletionContractTest
DialogUiStateTest
MaterialContextModelsTest
BackupProductionProtocolTest
BackupOperationGateTest
DeviceSnapshotCleanupTest
SnapshotCatalogFileReplaceTest
RepositoryBackupMapperCoverageTest
DataPrivacyIntegrityTest
ReducedMotionContractTest
ResourcesUiStateTest
PersonalContextDeletionPolicyTest
```

如果 JUnit XML 缺失、损坏或测试总数为 0，则该阶段 `NOT_VERIFIED`。

## 六、全量 Instrumentation

有可用模拟器/测试设备时执行：

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

不得用 AndroidTest APK“编译通过”替代实际设备测试。

失败定位时优先定向这些类，不修改代码：

```text
com.elio.jianyu.data.MaterialContextRepositoryTest
com.elio.jianyu.JianyuRuntimeLifecycleDatabaseTest
com.elio.jianyu.backup.BackupSnapshotKeystoreTest
com.elio.jianyu.ui.screens.resources.ResourcesScreenTest
com.elio.jianyu.ui.screens.resources.MaterialFileImporterTest
com.elio.jianyu.ui.screens.resources.ArtifactLibraryComponentsTest
com.elio.jianyu.ui.screens.skills.SkillRoleDiscoveryScreenTest
com.elio.jianyu.ui.screens.settings.SettingsScreenRegressionTest
```

同时确认 Room：

```text
version = 14
连续 migration 测试全部通过
PRAGMA foreign_key_check = 0 行
提交的 v14 schema 与当前生成 schema 一致
没有 destructive migration
```

## 七、设备/UI 验收通用要求

优先使用稳定 `testTag` / resource-id；不要把固定坐标、OCR、截图模板或中文正文作为主选择器。

使用测试数据或可丢弃测试环境。不要清除用户真实生产数据。

涉及模型回复的自动化必须使用 Fake/Test Network；没有安全 Fake 环境时，该项标记 `NOT_VERIFIED`，不要调用真实模型或真实 Key。

每个操作都检查：

```text
UI 是否与持久化事实一致
失败是否明确显示
失败后是否可以恢复/重试
Logcat 是否出现 closed database / stale DAO / IllegalStateException / SQLite misuse
没有正文/API Key/完整 Prompt 泄漏
```

## 八、对话验收

至少覆盖以下路径：

1. 新建会话。
2. 发送消息。
3. 生成中 Stop；已完成内容保留，Pending 收敛。
4. 单 Skill 回复。
5. 多 Skill 独立回复。
6. 点名回应只执行指定当前会话 Skill。
7. 显式交叉讨论；默认独立模式不能偷读其他角色输出。
8. 打开“选择资料”，选择当前 Issue/Stage 合法资料。
9. 选择个人背景；个人背景可跨议题候选，但默认不勾选。
10. 编辑本次摘录后确认，实际发送正文必须是编辑后的摘录。
11. 每项联网发送授权只对本次请求有效。
12. 敏感资料/个人背景必须逐请求明确确认。
13. 关闭“显示敏感资料发送提醒”后，仍必须要求敏感内容逐次确认；只减少额外提醒文案。
14. “本次参考内容”只能展示最近一次**实际执行**的 active context；不能显示下一请求 pending。
15. 回复结束后“本次参考内容”仍可查看最近一次实际执行内容。
16. 下一次请求没有选择资料时，不继承上一次 active。
17. 来源正文或 updatedAt/hash 在确认后变化时，执行前必须拒绝并要求重新确认。
18. prepare/usage 写入失败时：不消费 pending，不调用模型，不静默丢来源。
19. 失败角色 retry 必须重新打开上下文确认。
20. retry 不继承旧联网/敏感授权。
21. 明确确认“本次不带任何资料/个人背景”后允许 retry。
22. 取消后重新选择，确认顺序按真实新勾选顺序。
23. pending 确认重新打开，仍保留原确认顺序。
24. 删除会话后切到其他会话，不得显示被删除会话的 pending/active 参考内容。
25. 保存单条消息为成果。
26. 整理整段对话为 Markdown 成果。

对 24k 门禁至少验证：

```text
实际 SKILL.md Prompt 计入 base
thinking directive 计入 base
当前问题/历史消息计入
正好 24,000 可通过
24,001 拒绝
不静默截断
不静默丢最后一项
```

## 九、资料验收

覆盖：

```text
资料总览
新增文本
新增链接
新增普通文件
DOCX 正文提取
PDF 明确失败：不得把二进制伪装成文本
搜索
详情
ACTIVE / DISABLED / ARCHIVED / DELETED / PURGE_REQUESTED / PURGED 生命周期
敏感资料列表不展示完整正文
```

文件选择后不得长期保留不必要的 persistable URI permission。

## 十、成果验收

覆盖：

```text
保存草稿
取消成果确认：不得产生正式 Artifact
确认成果
筛选
筛选取消：不应错误应用 draft filter
Markdown 渲染
最新/历史 Revision
来源 Message / Run / Draft Revision / Material Usage Snapshot
来源不可用时明确失败，不从正文猜来源
“打开来源会话”回到正确 Issue + Stage
```

## 十一、个人背景验收

覆盖：

```text
列表敏感正文脱敏
新增
编辑
敏感开关
停用
重新启用
删除
删除确认
跨议题可选择
从不默认发送
删除后历史 usage snapshot 仍按产品契约保留/匿名化
```

## 十二、设置验收

逐项实际切换并离开/返回页面确认持久化：

```text
主题：系统 / 浅色 / 深色
字号
内容密度
减少动效
高对比度
消息时间
敏感额外提醒
```

特别验证“减少动效”：

```text
首页 skeleton 不再 shimmer
自定义 pulse 不再循环缩放/闪烁
bounce 组件不再产生按压缩放
```

敏感额外提醒关闭后仍必须执行请求级敏感确认。

## 十三、数据隐私验收

### 13.1 可读导出失败关闭

成功路径：

```text
导出 JSON 可重新读取
内容完整
不包含完整 API Key
```

失败注入（Repository 读取失败、目标写失败、回读校验失败）：

```text
不得报告成功
不得留下最终正式 JSON
临时 .partial-* 应被删除
若 provider 无法清理，UI 必须明确提示临时文件未清理
```

### 13.2 删除全部数据

只在可丢弃测试数据/模拟器上执行。

删除前建立可确认的：

```text
Issue/Stage/Message
Material
Artifact
Personal Context
AppPreferences
ConversationSessionPreferences / roundtable_settings
Official Skill favorites/recent
BYOK 测试 Key
模型配置
Telemetry
CloudInteractionSettings
音频/cache
Device Snapshot
snapshot wrapping key
```

删除后确认：

```text
Room 业务数据已清
SharedPreferences 已清
BYOK 已清
模型配置已 reset
遥测已清
云端交互设置关闭
AppPreferences 恢复默认
Official Skill 偏好已清
音频/cache 已清
Device Snapshot 目录已清
Android Keystore snapshot wrapping key 已删除
外部 SAF 导出的 JSON / Portable 文件不会被 App 擅自删除
```

任一清理失败时 UI 不得显示整体成功。

## 十四、Portable Backup 验收

覆盖：

```text
创建 Portable Backup
最终文件可完整读取
正确密码可通过已有 crypto/verification 测试验证认证 EOF
错误密码认证失败
API Key / Token / Keystore / 绝对路径 / Pending / Running / PURGED 正文不进入备份
创建失败不得留下最终 .jybak
临时 .partial-* 不残留
provider 不支持 rename/delete/reopen 时失败关闭
```

Portable 正式导入、差异预览、数据库替换属于 PR09-14A/14B，本 PR UI 必须明确显示“尚未开放”，不得假装恢复成功。

## 十五、Device Snapshot 与 Runtime 验收

覆盖：

```text
创建 Snapshot
创建第二个 Snapshot（验证已有 index.json 可安全替换）
编辑 Snapshot 备注
删除 Snapshot
Snapshot 创建失败后无孤儿 .jysnap
无残留 .part
Catalog index.json 无损坏
```

重点验证 WAL：

```text
创建前执行 PRAGMA integrity_check
执行 PRAGMA wal_checkpoint(TRUNCATE)
checkpoint 必须实际返回结果行
返回 busy / 非 0 时失败
不能把“无结果行”当成功
```

Snapshot 操作后继续使用 App：

```text
Room 已重开
Repository 可继续查询
对话可继续打开/发送（Fake Network）
资料页可继续读取
成果页可继续读取
ViewModel 使用新 generation
无 closed database
无 stale DAO
无旧 ViewModel 持有已关闭数据库
foreign_key_check = 0
```

## 十六、角色发现 UI-03

覆盖：

```text
一级角色页
点击只读搜索入口进入独立搜索页
搜索名称/别名/简介/场景
本地确定性相关性排序
筛选 staged apply / 取消
收藏页
最近使用页
最近使用清除二次确认
清 recent 不影响 favorites / 会话
详情打开/返回
从详情真实开始新会话 / 加入当前会话后才记录 recent
```

不要把一级页只读搜索框的旧 `SearchChanged` 兼容事件当成内联搜索要求。

## 十七、布局、无障碍与隐私

至少检查：

```text
360dp
200% 字号
明色
暗色
键盘弹出
长标题/长错误文本
TalkBack 焦点顺序（环境支持时）
触控目标
状态不只靠颜色
```

隐私检查：

```text
Logcat 无 API Key
无 Token
无完整 Prompt
无资料/个人背景完整正文
无正文型 testTag
无正文型证据文件名
```

不能实际执行的 TalkBack/布局项标记 `NOT_VERIFIED` 或 `PASS_WITH_NOTES`，不能虚构 PASS。

## 十八、收尾只读检查

```powershell
.\gradlew.bat --stop
git status --short
git diff --exit-code
git branch --show-current
git rev-parse HEAD
```

必须满足：

```text
最终工作区干净
branch 仍为 codex/ui-05-artifacts
HEAD 与开始锁定的 PR #66 headRefOid 完全一致
没有 commit
没有 push
没有 merge
PR 仍为 Draft
```

## 十九、返回格式

总体结论只允许：

```text
PASS
PASS_WITH_NOTES
FAIL
NOT_VERIFIED
```

先给一个紧凑矩阵：

```text
版本/只读门禁
Static/Secret
Kotlin/JVM/Lint
Debug APK
Release/R8
AndroidTest APK compile
Full Instrumentation
UI-03
UI-04
UI-05
UI-06
UI-07
UI-08
UI-09
Portable Backup
Device Snapshot
Runtime reopen
Privacy
Accessibility
Final git cleanliness
```

如果 PASS，只给每组必要证据：命令、退出码、测试数量、设备、关键断言。

如果 FAIL，**不要返回成千上万行正常日志**。每个失败只返回：

```text
失败阶段
失败命令
退出码
错误文件
错误行号
第一条关键错误
最小复现步骤
必要日志片段（只保留能证明根因的少量上下文）
是否稳定复现
```

不要修改代码。把报告返回给远端 GPT，由远端 GPT 复核根因并修复。
