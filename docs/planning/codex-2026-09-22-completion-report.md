# 见域当前分支完成度与远端审查交接

更新时间：2026-09-23（UTC；本轮远端收口）
PR：#66 `feat: 完成见域 UI 收口与正式备份`
分支：`codex/ui-05-artifacts`
状态：Draft，未合并

> 本报告是当前跨对话恢复入口。聊天中的旧 Head、旧测试结果或“未提交草稿”描述若与本文件和 GitHub 当前分支冲突，以 GitHub 当前分支、PR、Actions 与本文件为准。

## 1. 当前范围

本分支集中完成并收口：

1. UI-03：Skill 角色发现、搜索、筛选、收藏与最近使用。
2. UI-04：资料总览、新增、文件读取、详情与生命周期。
3. UI-05：成果保存、筛选、详情与来源追溯。
4. UI-06：个人背景维护、敏感展示边界与删除。
5. UI-07：主题、字号/密度、减少动效、高对比度、消息时间、额外敏感提醒、AI 管理与关于页。
6. UI-08：数据概览、可读导出、正式 Portable Backup、Device Snapshot 与删除全部数据。
7. UI-09：Top 1 对话与正式 Issue/Stage、资料、个人背景、成果的统一闭环。
8. Runtime：Room 闭库/重开、按 generation 重建 ViewModelStore、旧句柄失效。
9. PR09-13B：Argon2id、AES-GCM、Tink Streaming AEAD、确定性 CBOR、认证 EOF、白名单 Mapper、Snapshot 与全局备份门禁。

Portable 导入、差异/冲突预览和数据库原子替换仍属于 PR09-14A/14B，不在本 PR 中。

## 2. 本轮远端收口的关键修复

在原有 UI-03～UI-09 与 PR09-13B 实现之上，本轮继续完成了以下确定性修复：

- 设备快照闭库维护改为协程安全门禁；Runtime maintenance 不再通过旧 Runtime provider 取依赖；Android Keystore 使用 provider 生成随机 GCM IV；Snapshot 操作由应用级 scope 持有。
- `BackupOperationGate` 支持写租约内安全读取 Repository，避免备份 Writer 自锁。
- Portable SAF 只在临时文档完整写入、重新读取、解密并验证到认证 EOF 后发布最终文件名。
- 正式备份 Mapper 补齐冻结 registry 中此前缺失的 `participant_state`、`run_budget`、`message_usage`、`cross_discussion`、`archive_event`、`resume_event`、`issue_relation` 与 `safe_user_setting`。
- Portable/Snapshot 创建前阻止未与任何正式 Issue 关联的 standalone ChatSession 或未归属 Issue/Stage 的消息，返回 `unsupported_legacy_data`，不再静默遗漏。
- Snapshot 在 after-reopen 或 Catalog 发布失败时同时回滚 `.part` 和已改名的正式 `.jysnap`；清理失败返回 `temporary_cleanup_failed`。
- “删除所有本地数据”与备份共用写门禁，并把 App 私有 Device Snapshot 与 snapshot wrapping key 纳入清理；外部 SAF 导出/Portable 文件明确不自动删除。
- 可读数据导出和本地数据概览改为失败关闭；Repository 任一必要数据源失败时不再用空列表伪装成功。
- 个人背景列表不显示敏感正文预览。
- 对话选定资料/个人背景在下一次请求开始时原子消费，只允许同一问题的失败角色重试复用，不自动带入下一条用户请求。
- 敏感资料/个人背景必须逐次明确确认；设置只控制额外提醒文案，不能永久跳过敏感发送授权。
- 消息成果使用稳定消息时间，整段对话成果使用内容哈希进入 ID，重复保存走 Repository 幂等语义。
- 身份静态门禁已从 PR09-01 的一次性迁移文件清单升级到当前 Room v14、多 Provider Key Store 与当前文档事实，不再要求已被正式重构删除的旧文件继续存在。
- 数据页与资料页的过期自动化/完成合同已同步到当前 UI-04/UI-06 信息架构。

## 3. 关键近期提交

按主题列出本轮最重要的提交，GitHub 当前 Head 可能因本报告提交继续前进：

- `37c30b3`：修复设备快照生命周期与密钥封装
- `c29adb3`：修正备份完整性与隐私界面
- `ed4175d`：收紧 SAF 临时文档发布
- `ddbd204`：收紧对话上下文与资料成果链路
- `b54c1a8`：让应用设置真正作用于界面
- `0204644`：允许备份写锁内安全读取 Repository
- `b9a3655`：更新身份门禁到当前工程事实
- `d561d96`：补齐正式备份白名单映射
- `273eb89`：更新完成合同到当前 UI 架构
- `ca56809`：回滚失败后的正式快照文件
- `33be939`：删除全部数据时清理设备快照
- `7300d39`：强制敏感上下文逐次确认
- `9c7a183`：对齐隐私与正式备份当前合同

## 4. 当前验证事实

本轮已经得到的 GitHub 证据：

- Secret scan 在多个近期 Head 上通过，包括正式备份白名单与隐私修复之后的 Head。
- Android UI Test Compile 在 `a878a29` 上通过。
- Android CI 在 `a878a29` 已通过身份静态门禁与 Kotlin 编译，并执行到 552 个 JVM 测试；当时只有 2 个失败，均为已经确认过期的静态合同：
  - `DialogCompletionContractTest.backupRouteUsesFormalExportAndKeepsImportExplicitlyClosed`
  - `JianyuUiAutomationArchitectureTest.corePages_exposeStableAutomationRegions`
- 上述两条旧合同已在 `273eb89` 修正；之后又新增了 Snapshot 失败回滚、删除全部数据隐私边界和敏感上下文逐次确认，因此**最终结论必须以本报告之后最新 Head 的 Actions 为准**。

不要把旧 Head 的通过结果写成最新 Head “所有测试通过”。

## 5. 仍需关闭的验证

GitHub：

- 最新 Head Android CI：等待最终结论。
- 最新 Head Android UI Test Compile：等待最终结论。
- 最新 Head Secret scan：等待最终结论。

本地/设备：

- GPT 远端没有执行用户电脑上的 Android 本地构建。
- 仍需只读本地 AI 执行完整 Gradle 门禁、模拟器/真机交互、UI-03～UI-09 关键路径与日志检查。
- PR09-13B 仍需独立安全审查、跨版本向量、R8/依赖许可登记、受限设备性能与真实设备 Snapshot/恢复门禁。
- PR09-14A/14B 尚未实现。

## 6. 本地最终验收重点

只读验收至少覆盖：

- 对话：新建、发送/停止、单角色/多角色、资料选择、敏感逐次确认、本次参考内容、保存消息成果、整理整段对话成果。
- 资料：总览、新增文本/链接/文件、DOCX、PDF 明确失败、详情、搜索、生命周期。
- 成果：保存确认、筛选取消语义、详情、Markdown、来源追溯和来源会话回跳。
- 个人背景：敏感列表脱敏、编辑、停用、删除。
- 设置：主题、字号/密度、减少动效、高对比度、消息时间、敏感额外提醒；额外提醒关闭后仍必须逐次确认敏感内容。
- 数据隐私：可读导出失败关闭；删除全部数据后数据库、偏好、BYOK、遥测、音频、Device Snapshot 与 wrapping key 的清理结果。
- 备份：Portable 创建、错误密码/失败路径、Snapshot 创建/备注/删除、失败后无孤儿正式 `.jysnap`。
- Runtime：Snapshot 后 App 正常重开、对话/资料继续可用、无旧 DAO/closed database 异常。

## 7. 合并边界

PR #66 当前保持 Draft。除非用户明确要求，不自动合并。

合并前最低要求：

1. 最新 Head GitHub Actions 无未解释失败；
2. 本地只读验收给出结构化 PASS，或失败项已由 GPT 分析修复并重新验证；
3. 不把 PR09-14A/14B、独立安全审查或真机恢复等未完成项写成已完成。
