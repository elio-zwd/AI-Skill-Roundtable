# PR09-13B：正式备份与设备快照施工记录

本文件记录当前分支已经落地的 PR09-13B 生产实现，不能替代外部安全审查或真机验收。

## 已实现

- `app/src/main/java/com/elio/jianyu/backup/` 提供 V1 Envelope、受限确定性 CBOR、Canonical Record Stream、Argon2id Profile 1、AES-256-GCM Root Key 包装、Tink AES256-GCM-HKDF-1MB Streaming AEAD 和稳定错误码。
- Portable Writer 只使用随机 Envelope ID、Salt、Root Key 和 Wrap Nonce；创建后重新打开并读取到认证 EOF，再通过 Manifest/Record/COMPLETE 校验。
- Device Snapshot 使用独立 `jianyu_backup_snapshot_wrap_v1` Android Keystore Alias，在 WAL checkpoint 后通过 `JianyuAppRuntimeProvider.withDatabaseClosed` 复制主数据库和正式 AVAILABLE 音频，重开后执行最小查询与 `foreign_key_check`。
- `BackupOperationGate` 为公平进程内读写锁加 `noBackupFilesDir/jianyu-backup/operation.lock` 文件锁。Runtime 创建的 Repository、Audio 和 Lifecycle 事务通过注册表自动取得业务读锁。
- Snapshot Index 只在文件完整验证后发布；备注、列表和用户主动删除只操作非敏感索引与受控文件名。
- UI 已切换到正式 Portable 导出与 Device Snapshot；旧 PBKDF2/JSON 文件和旧导入入口不再作为产品能力。Portable 导入、差异预览和数据库替换属于 PR09-14A/14B。

## 当前验证

公开 Portable 向量、错误密码、版本错误、记录流完整性、生产 Writer、Snapshot Writer 和跨进程文件锁均有 JVM 测试。正式交付前仍须按根目录 `AGENTS.md` 运行完整 Android 编译、单测、lint、设备测试、密钥扫描和最终模拟器验收。

## 尚未关闭的发布门禁

- 独立安全审查、跨版本向量和 R8/依赖许可登记；
- 受限设备（包括 API 26/28）上的 Argon2id、50/500 MB 文件、取消和 SAF Provider 能力；
- 真机 Snapshot 重开与恢复验收；
- PR09-14A/14B 的隔离导入、差异/冲突预览和数据库原子替换。
