# PR09-13A：见域备份威胁模型

> 适用范围：可移植加密备份 `jianyu-portable-backup/1` 与设备绑定恢复快照 `jianyu-device-snapshot/1`。
>
> 查阅日期：2026-08-07。
>
> 本文冻结安全目标与不承诺边界，不代表生产导出、导入或恢复功能已经实现。

## 1. 安全目标

### 1.1 可移植加密备份

- 导出文件始终加密，不存在明文导出模式。
- 使用独立备份密码，不复用 Android Keystore、API Key、应用锁 PIN、密保答案或账号凭据。
- 攻击者仅取得备份文件时，不能在不知道密码的情况下读取或无痕修改内容。
- 错误密码、密文篡改和合法 Header 后的认证失败不形成可区分的密码 Oracle。
- Header 降级、Segment 删除、复制、重排、跨文件拼接、截断和尾随数据必须被拒绝。
- 备份必须表示一个一致的数据时间点，不输出数据库与音频处于不同时间点的混合快照。
- 已成功 Purge 的数据不得通过新备份、缓存、索引、标题、关系或音频清单复活。
- API Key、访问令牌、Keystore 密钥和应用锁凭据永不进入备份。

### 1.2 设备绑定恢复快照

- 使用独立 Android Keystore 设备密钥，不使用备份密码。
- 快照只能在持有对应设备密钥的应用沙箱中解密。
- 快照不包含备份密码、应用锁凭据、API Key 或 API Key Keystore Alias。
- 创建完成后立即完整验证；查看或恢复前再次验证。
- 损坏快照保留并标记不可用，只有用户可以删除。
- 容量不足只阻止创建并提示，不自动清理其他快照。
- 回退前必须先创建并验证新的“回退前快照”。

## 2. 资产

### 2.1 用户数据

- Issue、Stage、Stage Advancement 和措施选择。
- ExecutionRun、Participant Snapshot、Participant State、Budget 和实际使用快照。
- Message、交叉讨论记录和定向回应历史。
- Stage Summary Draft、Revision、Confirmed Artifact 和来源关系。
- MaterialReference、Material Usage、来源时间和内容。
- PersonalContextEntry、Usage Snapshot 和敏感标记。
- Archive Event、Resume Event、Issue Relation 和“来源已清除”事实。
- AudioAsset 元数据与 `AVAILABLE` 的受控正式音频文件。
- 官方 Skill 组合、成员、顺序、默认职责和历史 Skill/Participant Snapshot。
- 显式白名单中的非敏感设置，如 Skill 收藏和最近使用记录。

### 2.2 安全资产

- 备份密码。
- Argon2id 派生出的 KEK。
- 随机 Portable Backup Root Key。
- 随机 Device Snapshot Root Key。
- 设备快照专用 Android Keystore Key。
- Root Key 包装 Nonce、Streaming Salt、Nonce Prefix 和 Envelope ID。
- Envelope Version、Manifest Version、算法 ID、Required Feature Bits。
- Manifest、记录顺序、Transcript Hash、Blob Hash 和完成标记。

## 3. 信任边界

### 3.1 可信边界

- 见域当前应用进程内、通过正式 Repository 和 Lifecycle/Audio Service 读取的持久化事实。
- Android 应用私有目录和 `noBackupFilesDir` 的正常平台隔离。
- Android Keystore 在未被系统攻破时提供的不可导出密钥边界。
- 经版本锁定并通过测试的密码学库实现。

### 3.2 不可信输入

- 用户通过 SAF 选择的任何备份文件。
- Envelope Header 中的版本、长度、算法、KDF 和大小声明。
- Manifest、Entity、Blob、路径、URI、条目数量和内容长度。
- 旧版本、未来版本、第三方伪造文件和随机字节。
- 文件提供方的 rename、delete、reopen、长度和剩余空间行为。
- 从旧备份读取的 Purge 请求、内部幂等键、绝对路径和外部 URI Grant。

### 3.3 共享状态边界

单个 Room `@Transaction` 不能覆盖：

- 数据库主文件、WAL 和 SHM；
- 正式音频文件；
- WorkManager 和进行中的网络/音频任务；
- App 进程内 Room 单例、Flow 和 Coordinator；
- SAF 目标 Provider。

因此备份、Purge、音频提交和未来恢复替换必须共享显式全局操作门禁。

## 4. 攻击者与故障场景

| 场景 | 风险 | 冻结防护 |
|---|---|---|
| 攻击者取得 `.jybak` | 离线读取用户正文 | Argon2id + 随机 Root Key + AEAD；明文 Header 最小化 |
| 离线密码猜测 | 弱密码被暴力破解 | Argon2id 固定 Profile；不提供密码提示或校验 Oracle；UI 后续提示使用长密码 |
| Header 单字节篡改 | 降级 KDF、算法或格式 | Header 进入 Root Key Wrap AAD；精确 Profile 匹配 |
| Wrapped Root Key 篡改 | 替换密钥或拼接 Payload | AES-GCM Tag；Wrapped Key 摘要进入 Streaming AAD |
| Ciphertext/Tag 篡改 | 修改正文或附件 | Tink Streaming AEAD 认证失败 |
| Segment 删除或截断 | 接受不完整备份 | Tink Final Segment + 必须读到 EOF + COMPLETE Record |
| Segment 重排/复制 | 重组内容 | Segment Index 和 Final Bit 进入 GCM Nonce；记录序号和 Transcript Hash |
| 跨文件拼接 | 替换后半部分 | 每文件随机 Streaming Salt/Nonce Prefix；Streaming AAD 绑定完整 Envelope |
| 尾随数据 | 隐藏第二载荷或格式混淆 | COMPLETE 后必须立即 EOF，否则 `trailing_data` |
| 未知版本/算法 | 解释错误或降级 | 未知版本、KDF、AEAD、序列化和 Required Feature 全部安全拒绝 |
| 恶意 KDF 参数 | 内存/CPU 资源耗尽 | Header 只接受注册 Profile ID；分配前精确检查，不执行任意 m/t/p |
| 超大 Header/Record | 内存耗尽 | Header 4096 bytes、Record 1 MiB 硬上限 |
| 超大文件/大量小文件 | 磁盘、CPU、对象耗尽 | 条目、Blob、单 Blob、总大小上限；流式处理；溢出安全加法 |
| 路径穿越 | 覆盖应用或系统文件 | V1 不保存容器路径；Blob 使用逻辑 ID；拒绝绝对路径、`.`、`..` 和 NUL |
| 压缩炸弹 | 解压耗尽 | V1 不压缩，不使用 ZIP/TAR |
| Nonce 重用 | GCM 机密性失效 | 每文件随机 Root Key、Wrap Nonce、Streaming Salt 和 7-byte Nonce Prefix；测试 RNG 与生产 RNG 隔离 |
| 随机数失败 | 重复 Key/Nonce | `SecureRandom` 异常立即失败；不得回退固定值、时间戳或弱 PRNG |
| 进程终止 | 半文件被误认为成功 | `.partial-*` 临时密文、fsync、完整验证、受控发布；最终扩展名只在验证后出现 |
| 空间不足 | 截断或自动删除旧数据 | 预检 + 写入错误失败；不自动删除快照或备份 |
| 目标已存在 | 覆盖唯一旧备份 | 永不覆盖；生成新文件名或返回失败 |
| 用户取消 | 留下可误用文件 | 停止写入，删除临时文件；删除失败使用非最终扩展名并记录稳定错误 |
| Purge 与备份竞态 | 已删除数据复活或混合状态 | 全局门禁；开始前和提交前双检查 Lifecycle/Purge/版本 Token |
| Audio Worker 与备份竞态 | DB 元数据与文件不一致 | 活动音频任务阻止备份；只含 AVAILABLE 且验证过的正式文件 |
| 数据读取期间发生变化 | 不同时间点混合 | 全局写冻结 + 开始/结束 Token；变化则 `source_changed` |
| 系统自动备份 | 绕过见域加密备份 | 手动备份与 Android 系统备份明确分离；系统备份单独审计和后续收紧 |
| 临时明文残留 | 沙箱或闪存取证 | 可移植导出不落地明文；未来导入临时明文仅在 `noBackupFilesDir`，立即清理 |
| 日志/错误泄露 | 密码、正文或路径暴露 | 只记录阶段、稳定错误码、数量和非敏感耗时；禁止正文、完整路径和密码学材料 |

## 5. 生命周期和 Purge 不变量

PR09-12 已建立唯一 Purge 状态机：

```text
REQUESTED
WAITING_FOR_TASKS
CANCELING_TASKS
DELETING_FILES
READY_FOR_DATABASE_PURGE
DATABASE_PURGING
FAILED_RETRYABLE
COMPLETED
```

冻结规则：

- 任何存在 Purge Operation 的 Issue 不进入新备份。
- `FAILED_RETRYABLE` 不得描述为完整可恢复 Issue。
- 备份创建期间不得为目标 Issue 启动新 Purge。
- 备份开始前和最终发布前再次检查 Lifecycle、Purge Operation、对象版本和音频状态。
- 任何影响范围变化导致整个备份失败，不输出部分成功文件。
- 已成功 Purge 的数据不得通过索引、旧 Relation 标题、缓存、缩略图或音频清单出现。
- 来源 Relation 已降级时只保存 `relationType`、`sourcePurgedAt` 和“来源已清除”事实。
- 导入不得把旧备份中的 `purgeRequestedAt` 或 Operation 当作新的用户清理意图。

## 6. Android 系统备份审计

当前生产配置：

- `AndroidManifest.xml`：`android:allowBackup="true"`。
- `backup_rules.xml`：存在 `<include domain="sharedpref" path="."/>`，并排除 API Key 与遥测相关偏好。
- `data_extraction_rules.xml`：Cloud Backup 和 Device Transfer 都只 include `sharedpref`，再排除相同敏感偏好。

Android 官方规则说明：一旦配置 `<include>`，只备份显式包含的范围；`noBackupFilesDir` 永远不会参与 Auto Backup。

因此当前设计事实为：

- Room 数据库不进入系统 Cloud Backup/Device Transfer。
- `filesDir/jianyu-audio` 不进入系统 Cloud Backup/Device Transfer。
- `noBackupFilesDir/gemini_api_keys.enc` 不进入系统备份。
- `gemini_api_key_prefs.xml` 被显式排除。
- 其他 SharedPreferences，包括部分非敏感使用偏好，当前可能进入系统备份。

结论：未发现数据库、正式音频或完整 API Key 被系统云备份的紧急漏洞；PR09-13A 不修改 Manifest/XML。但通配 SharedPreferences 会使未来新增偏好默认进入系统备份，应在正式发布备份功能前通过独立安全 PR 收紧：

- Cloud Backup 禁用或改为显式空白策略；
- Device Transfer 使用独立的非敏感字段白名单；
- UI 和文档不得把 Android 系统备份称为“见域加密备份”。

## 7. 错误和密码 Oracle

以下情况统一对用户返回：

```text
authentication_failed
```

- 错误备份密码；
- Root Key Wrap Ciphertext/Tag 被修改；
- 合法 Header 后的 Streaming Header、Ciphertext 或 Tag 被修改；
- 与当前 Envelope 不匹配的 Payload 拼接。

结构错误可以在运行 KDF 前返回具体错误，例如：

```text
invalid_magic
unsupported_envelope_version
unsupported_kdf
kdf_parameters_out_of_policy
invalid_header
entry_limit_exceeded
```

不得返回：

- 密码是否“接近正确”；
- 哪个 Segment Tag 失败；
- 派生密钥、Root Key、Nonce、Salt；
- Issue 标题、正文、文件名、完整 URI 或绝对路径。

## 8. 不承诺边界

本设计不承诺抵御：

- 已完全控制、解锁并取得系统/root 权限的恶意设备；
- 运行时 Hook、内存抓取或已被攻破的 Android OS；
- 被攻破、恶意或存在未知漏洞的密码学库和 Provider；
- 用户主动泄露、重复使用或选择弱备份密码；
- 用户把解密后的数据复制到不安全位置；
- 闪存磨损均衡导致的物理残留和可证明安全擦除；
- 屏幕截图、键盘、无障碍服务或恶意输入法泄露密码；
- 供应链攻击、签名密钥失陷或恶意应用版本。

因此对外描述必须使用“降低风险”“认证加密”“设备绑定”等准确语言，不得宣称“绝对安全”“无法破解”或“彻底擦除”。

## 9. 外部权威资料记录

| 来源 | 查阅版本/日期 | 结论 | 限制 |
|---|---|---|---|
| RFC 9106 Argon2 | RFC 9106，2021-09；查阅 2026-08-07 | Argon2id、16-byte Salt、测试向量和内存困难型 KDF 依据 | RFC 的推荐配置需结合移动端资源评估 |
| OWASP Password Storage Cheat Sheet | 查阅 2026-08-07 | Argon2id 优先，scrypt 次选，PBKDF2 用于兼容/FIPS | Web 密码存储建议需映射为备份密码派生策略 |
| RFC 5869 HKDF | RFC 5869；查阅 2026-08-07 | 子密钥域分离和 Extract/Expand | HKDF 不替代密码 KDF |
| NIST SP 800-38D | 2007 正式版；查阅 2026-08-07 | AES-GCM 和 Tag 规则 | NIST 正在修订，未来变化需新算法/Envelope 版本 |
| Google Tink Streaming AEAD | 页面更新 2025-03/04；查阅 2026-08-07 | Segment Index、Final Bit、Streaming AAD 和约 1 MiB 内存 | 调用方仍必须完整读取到 EOF |
| Android Auto Backup 文档 | 查阅 2026-08-07 | include/exclude、Cloud Backup、Device Transfer 和 noBackup 边界 | OEM/系统迁移行为仍需设备验收 |
| Android Keystore 文档 | 查阅 2026-08-07 | 设备绑定、不可导出 AES Key 和 GCM 支持 | 被攻破 OS 不在保证范围 |
| Bouncy Castle 1.84 | 发布 2026-04-14；查阅 2026-08-07 | 纯 Java Argon2id 原型候选 | 原始 JAR 较大，必须测 R8 后 APK 增量 |
| Tink Android 1.23.0 | 查阅 2026-08-07 | Android API 24+、Apache-2.0、Streaming AEAD | 生产升级必须重新运行向量和兼容测试 |

官方链接：

- https://www.rfc-editor.org/rfc/rfc9106
- https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html
- https://www.rfc-editor.org/rfc/rfc5869
- https://csrc.nist.gov/pubs/sp/800/38/d/final
- https://developers.google.com/tink/streaming-aead
- https://developers.google.com/tink/streaming-aead/aes_gcm_hkdf_streaming
- https://developer.android.com/identity/data/autobackup
- https://developer.android.com/privacy-and-security/keystore
- https://www.bouncycastle.org/latest_releases.html
- https://developers.google.com/tink/setup/java
