# 见域当前分支完成度与远端审查交接

更新时间：2026-09-22（Asia/Shanghai）

## 1. Git 起点与当前分支

- 工作分支：`codex/ui-05-artifacts`
- 共同基线：`origin/main` 与本分支的 merge-base 为 `53321b6`（`Merge pull request #62 from elio-zwd/codex/ui-03-spec`）。
- 本分支首个施工提交：`9849f09 feat: 完成 UI-03 角色发现纯逻辑与 Preferences 扩展`。
- 本次交接时的最新提交会在本文件提交后更新；推送目标为 `origin/codex/ui-05-artifacts`。
- 工作区中原有的 `tools/ai/filter_chat_page.js` 是用户未跟踪文件，本次没有读取、修改或提交它。

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

### 4.1 设备快照 UI 操作未形成可见结果

在最终模拟器人工检查中点击 `backup_snapshot_create`（节点 bounds `[84,1191][426,1317]`）并等待后：

- UI 没有显示“操作结果”或成功/错误卡片；
- `run-as com.elio.jianyu` 下没有出现 `no_backup/jianyu-backup/snapshots/` 快照文件；
- 应用未崩溃，节点仍可点击。

初步怀疑是 UI 持有 Runtime lease 时调用 `withDatabaseClosed`，导致快照闭库等待无法完成；也可能是点击后的异步异常没有回显。请远端 AI 用日志、协程/lease 状态和一个不污染全局数据库的专项测试复现，不要重新加入会删除共享 Room 数据库的全量 AndroidTest 夹具。

### 4.2 Portable 导入与数据库替换尚未实现

PR09-14A/14B 仍未开放：Portable 隔离导入、格式/版本检查后的差异预览、冲突确认、幂等合并策略、数据库原子替换、回退/恢复执行均不在当前实现中。UI 必须继续明确显示“导入尚未开放”，不能把导出文件误当作可恢复能力。

### 4.3 外部发布门禁尚未关闭

当前本地证据不能替代：独立安全审查、跨版本向量验证、R8/依赖许可登记、受限设备性能、真实设备恢复和真实跨进程恢复。GitHub CI 在本次本地工作中没有被声称为通过。

### 4.4 角色真实性与来源治理仍需持续维护

真实人物型 Skill 角色的来源、授权、更新时间和 AI 模拟身份披露需要继续逐项登记；不得将生成内容描述成真人当前发言、授权或背书。

## 5. 远端审查建议顺序

1. 从本分支最新 Head 阅读本文件、`README.md`、`docs/planning/pr-09-13b-production-implementation.md` 和 PR09-13A 接口交接文档。
2. 先复查快照 UI 的 lease/闭库/错误回显问题，再设计隔离专项测试。
3. 运行本地同等 Gradle 门禁并检查 GitHub Actions；不要把 2 个 skipped 测试写成通过。
4. 另开 PR09-14A/14B 设计和实现分支，先完成隔离导入/预览，再实现原子替换；不要在当前分支偷偷扩大 Room schema 或恢复边界。
5. 保留 `tools/ai/filter_chat_page.js` 这个用户未跟踪文件，不要在自动清理中删除或覆盖。

