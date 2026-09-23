# 见域当前分支完成度与远端审查交接

更新时间：2026-09-22（Asia/Shanghai）

## 1. Git 起点与当前分支

- 工作分支：`codex/ui-05-artifacts`
- 共同基线：`origin/main` 与本分支的 merge-base 为 `53321b6`（`Merge pull request #62 from elio-zwd/codex/ui-03-spec`）。
- 本分支首个施工提交：`9849f09 feat: 完成 UI-03 角色发现纯逻辑与 Preferences 扩展`。
- 本次报告更新前的代码审查基线：`ccab195 docs: 增加远端审查交接汇报`；推送目标为 `origin/codex/ui-05-artifacts`。本报告提交后远端文档 Head 会继续前进，但代码审查基线仍以 `ccab195` 为准。
- 本报告更新时，工作区另有一批**未提交、未推送**的备份快照修复；它们不属于远端 AI 当前可见的 Git 提交，不能当作分支已完成能力。若远端 AI 只从 GitHub 拉取代码，应以 `ccab195` 为代码基线，并将这些本地差异视为待审查草稿。
- 工作区中原有的 `tools/ai/filter_chat_page.js` 是用户未跟踪文件，本次没有读取、修改或提交它。

### 1.1 未提交的本地草稿（请勿误认为已交付）

本地曾针对设备快照首次点击无结果的问题做过诊断性修复，但按当前交接安排暂不提交代码。涉及文件包括：

- `app/src/main/java/com/elio/jianyu/backup/BackupOperationGate.kt`：把不能跨挂起边界持有的线程锁改为协程安全互斥门禁。
- `app/src/main/java/com/elio/jianyu/backup/DeviceSnapshotService.kt`、`app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt`、`app/src/main/java/com/elio/jianyu/ui/screens/mine/BackupRoute.kt`：收口维护态闭库、运行时 lease 释放和操作取消边界，并补充安全诊断日志。
- `app/src/main/java/com/elio/jianyu/backup/BackupCrypto.kt`、`app/src/main/java/com/elio/jianyu/backup/BackupEnvelopeWriter.kt`：适配 Android Keystore 随机化 AES-GCM IV 的快照封装路径。
- `app/src/main/java/com/elio/jianyu/ui/components/JianyuPageShell.kt`、`app/src/test/java/com/elio/jianyu/backup/BackupOperationGateTest.kt`：防重复点击及挂起后跨 dispatcher 回归覆盖。
- `app/src/main/java/com/elio/jianyu/data/RoomIssueLifecycleV12Repository.kt`、`app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeCoordinator.kt`：诊断性日志改动，需远端 AI 判断是否保留。

这些改动在本地构建/定向测试和模拟器手工操作中曾得到正向结果，但尚未形成提交；远端审查应重新检查其并发、生命周期、密钥提供方和日志取舍，不能直接据此宣称修复已合入。

## 2. 已完成的主要阶段

1. UI-03：角色发现、搜索、筛选、收藏、最近使用和二级导航。
2. UI-04：资料总览、资料新增/详情、资料与对话上下文联动。
3. UI-05：成果保存、成果详情、来源追溯和对话入口统一。
4. UI-06：个人背景维护、启停、删除和生命周期语义。
5. UI-07：设置、AI 管理、遥测与诊断、关于页和身份披露。
6. UI-08：数据隐私、导出边界、权限说明和备份入口。
7. UI-09：对话统一、资料/成果联动、会话删除边界和恢复边界。
8. 运行时收口：Room 闭库重开、恢复健康检查、旧句柄失效和业务读写门禁。
9. PR09-13B 正式备份核心：Portable Backup、Device Snapshot、Argon2id、AES-256-GCM、Tink Streaming AEAD、确定性 CBOR、严格记录流/EOF 校验、快照目录和进程级并发门禁。
10. 备份校验收口：实体注册表、字段/记录计数限制、公开向量、错误码和测试隔离修正。

对应的近期阶段提交包括：

- `32a7baf` 正式加密备份与设备快照核心
- `43ac953` 正式备份导出边界
- `3af2730` 备份全局并发门禁
- `27a2105` 正式实现与剩余发布门禁文档
- `55f890e` 备份记录类型与字段校验
- `9328b37` 快照备注入口与回归阶段整理
- `198ec84` 移除会污染全局数据库的设备测试夹具

## 3. 已执行验证

- `compileDebugKotlin testDebugUnitTest compileDebugAndroidTestSources lintDebug assembleDebug assembleDebugAndroidTest`：通过。
- 单元测试报告：543 tests，0 failures，0 errors。
- `connectedDebugAndroidTest`：241 tests，0 failures，0 errors，2 skipped。
  - 跳过的两项是需要显式 ADB 外部进程协调的恢复测试，不代表已完成真实跨进程验收。
- `pwsh.exe -File .\tools\check-secrets.ps1 -IncludeHistory`：通过，未发现 Gemini API Key 或禁止跟踪敏感文件。
- 模拟器：`emulator-5554`，1080×2400，安装并启动 `com.elio.jianyu` Debug APK。
- 人工页面检查已确认主页、资料、我的、设置、关于和备份页面的稳定根节点/入口存在，未见 `FATAL EXCEPTION`。

## 4. 需要远端 AI 优先复查的问题

### 4.1 设备快照 UI 操作曾卡住，已有未提交本地修复待审

在远端 Head `ccab195` 对应的代码上，点击 `backup_snapshot_create`（节点 bounds `[84,1191][426,1317]`）曾没有形成可见结果，也没有生成快照文件。后续本地工作区诊断出多个叠加问题：挂起函数跨线程释放 `ReentrantReadWriteLock`、维护态仍走运行时 provider、Android Keystore 不接受调用方指定的 GCM IV，以及 UI scope 在运行时重建时取消快照协程。

本地未提交草稿分别改为协程 `Mutex`、维护态直接使用音频存储、让 Keystore 生成随机 IV，并把快照操作放到 `NonCancellable` 边界；随后在 `emulator-5554` 手工点击成功生成了 `no_backup/jianyu-backup/snapshots/` 下的快照文件，且未见 `FATAL EXCEPTION`。这些结果**不等于远端分支已修复**：请远端 AI 优先审查上述 diff，重点检查锁/lease 生命周期、失败恢复、密钥提供方约束、取消语义和日志隐私，并自行决定是否重写或合入。

不要重新加入会删除共享 Room 数据库的全量 AndroidTest 夹具；专项测试必须使用隔离数据库或受控快照目录。

### 4.1.1 远端审查修复进展

远端 AI 已按本报告重新核对 GitHub 上的实际生产代码，并确认以下根因存在：

- `BackupOperationGate` 使用线程绑定的 `ReentrantReadWriteLock` 跨越挂起边界；
- Snapshot 的 `beforeClose` 在 Runtime 已进入 `Maintenance` 后又调用 `JianyuAppRuntimeProvider.get()`；
- Android Keystore Key 启用了随机化加密，但 Snapshot Writer 仍由调用方指定 GCM IV；
- Snapshot 由 Compose `rememberCoroutineScope` 直接拥有，而 Runtime 世代切换会销毁旧 UI scope。

当前远端修复改为：协程安全的公平读写门禁、维护态直接使用独立 `AudioFileStore`、由 Android Keystore provider 生成并回写实际 GCM IV、由应用级 Snapshot operation scope 持有闭库维护。并新增跨 dispatcher 门禁回归与真实 Android Keystore 随机 IV Instrumentation 测试。

以上内容属于**代码修复已提交、验证待执行**状态；在 GitHub CI 或本地 Android 验收给出新证据前，不得把本节写成“所有测试通过”或“真机已验证”。

### 4.2 Portable 导入与数据库替换尚未实现

PR09-14A/14B 仍未开放：Portable 隔离导入、格式/版本检查后的差异预览、冲突确认、幂等合并策略、数据库原子替换、回退/恢复执行均不在当前实现中。UI 必须继续明确显示“导入尚未开放”，不能把导出文件误当作可恢复能力。

### 4.3 外部发布门禁尚未关闭

当前本地证据不能替代：独立安全审查、跨版本向量验证、R8/依赖许可登记、受限设备性能、真实设备恢复和真实跨进程恢复。GitHub CI 在本次本地工作中没有被声称为通过。

### 4.4 角色真实性与来源治理仍需持续维护

真实人物型 Skill 角色的来源、授权、更新时间和 AI 模拟身份披露需要继续逐项登记；不得将生成内容描述成真人当前发言、授权或背书。

## 5. 远端审查建议顺序

1. 从本分支当前远端 Head `ccab195` 阅读本文件、`README.md`、`docs/planning/pr-09-13b-production-implementation.md` 和 PR09-13A 接口交接文档；不要假设本地未提交草稿存在于 GitHub。
2. 先复查快照 UI 的 lease/闭库/取消/错误回显问题；如采用本地草稿，先补并发、Keystore 和失败恢复的隔离专项测试。
3. 运行本地同等 Gradle 门禁并检查 GitHub Actions；不要把 2 个 skipped 测试写成通过。
4. 另开 PR09-14A/14B 设计和实现分支，先完成隔离导入/预览，再实现原子替换；不要在当前分支偷偷扩大 Room schema 或恢复边界。
5. 保留 `tools/ai/filter_chat_page.js` 这个用户未跟踪文件，不要在自动清理中删除或覆盖。
