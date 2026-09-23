# UI-08：数据与隐私、结构化导出与正式备份边界

## 目标

把“我的”页里的数据与备份入口接成可核验的本地能力：展示真实数据概览，提供不包含完整 API Key 的结构化可读导出，接入 PR09-13B 正式 Portable Backup 与 Device Snapshot，并把“删除所有本地数据”变成明确输入确认后的失败关闭操作。

## 安全边界

- 可读导出只通过 Android SAF 写入用户选择的位置；不会上传云端，不把完整 API Key 写入文件；任一 Repository 数据源读取失败时整体导出失败，不静默漏项。
- 正式 Portable Backup 使用冻结的 PR09-13A/13B 协议：Argon2id、AES-256-GCM Root Key 包装、Tink Streaming AEAD、受限确定性 CBOR、认证 EOF 与白名单 Mapper。API Key、Keystore 材料、Token、绝对路径、Pending/Running 数据和已 Purge 内容永久排除。
- Device Snapshot 保存在 App 私有 `noBackupFilesDir`，使用独立 Android Keystore wrapping key；只有文件完整验证、数据库重开健康检查和 Snapshot Catalog 发布全部成功后才保留正式 `.jysnap`。失败必须同时回滚临时文件和尚未发布的正式文件。
- Portable 导入、差异/冲突预览和数据库替换仍属于 PR09-14A/14B；当前版本不得伪装成已经可以恢复 Portable Backup。
- “删除所有本地数据”与备份操作共用写门禁；数据库清空后还必须清理模型配置、BYOK 密文文件、遥测、云端交互设置、App 偏好、音频/缓存、App 私有 Device Snapshot 与 snapshot wrapping key。通过 SAF 另存到外部位置的 JSON/Portable 文件不自动删除。
- 旧包 `com.elio.skillroundtable` 不访问、不卸载、不清除。

## 页面

- 数据与隐私：会话、资料/成果、个人背景真实计数；云端交互与遥测入口；可读导出；删除全部数据及明确影响范围。
- 备份与恢复：创建正式加密 Portable Backup、创建设备绑定 Snapshot、查看/备注/删除 Snapshot；导入入口只展示“尚未开放”的真实边界。

## 验证

- 可读导出 JSON 不含完整 Key、绝对路径和旧包数据；任何数据源读取失败不生成伪成功文件。
- Portable Writer 重新打开并验证到认证 EOF 后才发布；冻结 entity registry 与完整白名单 Mapper 不得静默漏项。
- Device Snapshot 的 Keystore 随机 IV、闭库重开、失败回滚、旧会话预检和 Catalog 发布边界有专项测试。
- 删除确认文字错误时不可执行；成功只在全部 App 自有存储清理成功后报告，设备快照与 wrapping key 必须纳入。
- 运行 `compileDebugKotlin`、`testDebugUnitTest`、`lintDebug`、`assembleDebug` 和 `assembleDebugAndroidTest`。

## 尚未关闭的发布门禁

- PR09-14A/14B 的隔离导入、差异/冲突预览、候选数据库验证和数据库原子替换；
- 独立安全审查、跨版本向量、R8/依赖许可登记；
- API 26/28 等受限设备上的 Argon2id、大文件、取消与 SAF Provider 能力；
- 真实设备 Snapshot 重开/恢复和真实跨进程恢复验收。
