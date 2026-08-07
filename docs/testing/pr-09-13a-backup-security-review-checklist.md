# PR09-13A 备份安全独立审查清单

> 审查对象：PR09-13A Draft PR 的精确 Head。
>
> 当前结论：`INSUFFICIENT_EVIDENCE`。
>
> 在精确 Head CI、本地严格只读验收和独立审查完成前，不得改写为 `PASS` 或 `PASS_WITH_NOTES`。

## 1. 判定值

只允许：

```text
PASS
PASS_WITH_NOTES
FAIL
INSUFFICIENT_EVIDENCE
```

- `PASS`：全部核心决定已冻结，原型与向量真实通过，无阻断风险。
- `PASS_WITH_NOTES`：核心决定已冻结且可实施；只有不影响安全语义的真机性能、OEM 或长期兼容说明。
- `FAIL`：存在密码学、格式、一致性、数据范围或失败关闭缺陷。
- `INSUFFICIENT_EVIDENCE`：测试、CI、本地验证或独立证据不足。

## 2. 精确范围

审查必须确认：

- Base 精确为 `40a106e66854c48efb008f434af1ed850128afdc`；
- 分支为 `security/pr-09-13a-backup-design`；
- PR 保持 Draft；
- 差异只包含安全/架构/计划/测试文档、公开向量、测试源集原型和两项 `testImplementation`；
- `app/src/main` 无变化；
- `app/schemas` 无变化；
- Manifest、系统备份 XML、Room、Repository、Lifecycle、Audio Worker 和 UI 无生产实现变化。

任一生产 Runtime 差异没有独立授权时结论为 `FAIL`。

## 3. 威胁模型

- [ ] 攻击者取得备份文件。
- [ ] 离线密码猜测。
- [ ] Header、Wrapped Key、Ciphertext 和 Tag 篡改。
- [ ] 截断和尾随。
- [ ] Segment 删除、重复、重排和跨文件拼接。
- [ ] Header 降级和未知版本。
- [ ] 恶意 KDF 参数资源耗尽。
- [ ] 超大文件、超大声明和大量小条目。
- [ ] 路径穿越、绝对路径和外部 URI Grant。
- [ ] Nonce 重用和随机数失败。
- [ ] 进程终止、空间不足、目标存在和取消。
- [ ] Purge、Audio Worker、Run 和数据库写入竞态。
- [ ] Android Auto Backup 与 Device Transfer 边界。
- [ ] 临时明文、日志、错误和遥测泄露。
- [ ] 不承诺边界准确，没有“绝对安全”声明。

缺少任一核心场景时结论为 `FAIL`。

## 4. KDF

- [ ] V1 Writer 只使用 Argon2id。
- [ ] Profile 固定为 64 MiB、3 次、并行度 1、Version 0x13。
- [ ] Salt 固定 16 bytes，输出固定 32 bytes。
- [ ] 密码执行 NFC + UTF-8，不执行 trim/NFKC/大小写转换。
- [ ] 密码长度在 KDF 前限制为 1～1024 bytes。
- [ ] 文件只保存 Profile ID，不接受任意 `m/t/p`。
- [ ] 未知 Profile 在分配前拒绝。
- [ ] 资源不足失败，不降级 PBKDF2/scrypt。
- [ ] Argon2id 公开向量可重复。
- [ ] Bouncy Castle 只在测试源集，未注册全局 Provider。

## 5. 密钥层次

- [ ] 备份密码只派生 KEK。
- [ ] 每个文件使用新的随机 Root Key。
- [ ] Root Key 使用 AES-256-GCM 包装。
- [ ] Portable/Snapshot 使用不同 HKDF `info`。
- [ ] Snapshot Alias 与 API Key Alias 不同。
- [ ] Header 不保存设备身份或 Alias。
- [ ] 修改密码创建新备份，不覆盖唯一旧备份。
- [ ] 密保和应用锁不能重置或解密旧备份。
- [ ] 未声明无法保证的 JVM 内存物理清零。

## 6. AEAD 与 Nonce

- [ ] Root Key Wrap Nonce 固定 12 bytes，每文件随机。
- [ ] Streaming Salt 32 bytes、Nonce Prefix 7 bytes，每文件随机。
- [ ] Segment Nonce 精确为 Prefix + `u32be(index)` + Final Byte。
- [ ] Segment Index 不可重复。
- [ ] Tink `AES256_GCM_HKDF_1MB` 参数精确。
- [ ] Header、Wrapped Root Key 和算法通过 AAD 绑定。
- [ ] 错误密码和认证篡改统一 `authentication_failed`。
- [ ] 调用方必须读到认证 EOF。
- [ ] Tink 官方实现能解密参考向量。

## 7. Envelope

- [ ] Portable/Snapshot Magic 不同。
- [ ] Envelope Version、Header Encoding 和 Header Length 使用大端固定字段。
- [ ] Header 最大 4096 bytes。
- [ ] Header 使用确定性 CBOR 受限子集。
- [ ] 未知字段、重复 Key、Float、Tag、Null、indefinite length 和非最短编码拒绝。
- [ ] Portable 与 Snapshot 精确字段集合不同。
- [ ] Required Feature 非零失败关闭。
- [ ] Header 不泄露标题、正文、Skill、文件名、来源或身份。

## 8. Record Stream

- [ ] 每条记录使用 `u32be length + canonical CBOR`。
- [ ] Record 最大 1 MiB。
- [ ] MANIFEST 必须第一条且唯一。
- [ ] COMPLETE 必须最后一条且唯一。
- [ ] ENTITY/BLOB 全局 Sequence 连续。
- [ ] Blob Chunk Index 连续。
- [ ] Blob Start/Chunk/End 顺序和 Hash 一致。
- [ ] Transcript Hash 范围明确。
- [ ] Manifest Hash 只覆盖 Canonical MANIFEST CBOR。
- [ ] COMPLETE 后任何认证明文返回 `trailing_data`。
- [ ] 未知 Entity Type 不能被静默跳过。

## 9. 资源限制

- [ ] Header、Record、Chunk、条目、Blob、单 Blob 和总明文均有硬上限。
- [ ] 所有长度在分配前检查。
- [ ] 所有加法使用溢出安全实现。
- [ ] 恶意 KDF Profile 在分配前拒绝。
- [ ] V1 不使用压缩，消除压缩炸弹面。
- [ ] 500 MB 数据不会按文件大小线性增加内存。

桌面 JVM 数据不得替代 Android 真机峰值内存结论。

## 10. 数据范围

- [ ] 使用逐对象白名单，不复制 App 目录。
- [ ] Issue、Stage、Run、Participant、Message、Draft、Artifact、Material、Personal Context、Lifecycle、Audio 和 Skill 组合均被明确处理。
- [ ] API Key、Keystore、应用锁、Token、绝对路径、Cache、Pending、Orphan 和 `.part` 永久排除。
- [ ] APK 静态 Skill 资产不重复导出。
- [ ] 历史 Participant/Skill Snapshot 保留必要执行语义。
- [ ] Standalone Legacy ChatSession 不被静默遗漏；“全部数据”备份应明确阻止并显示数量。
- [ ] 外部 URI 不保存 Grant，不复制原文件。

## 11. Purge 和 Relation

- [ ] Purge 已请求或执行中的 Issue 整体拒绝。
- [ ] `FAILED_RETRYABLE` 不作为完整可恢复 Issue。
- [ ] 备份期间不能开始新 Purge。
- [ ] 开始和发布前两次检查 Source Token。
- [ ] 已 Purge 数据不能通过缓存、标题、索引或音频复活。
- [ ] 降级 Relation 只保留“来源已清除”事实。
- [ ] 旧备份中的 Purge 请求不成为新清理意图。

## 12. 音频

- [ ] 只从 `AudioAssetEntity` 和 `AudioFileStore` 枚举。
- [ ] 只包含 AVAILABLE、未删除、未请求 Purge 的正式文件。
- [ ] 不扫描目录猜关联。
- [ ] 不使用旧 `Message.audioFilePath` 作为正式事实源。
- [ ] 路径是受控相对路径。
- [ ] 文件存在、格式、大小和 Hash 均验证。
- [ ] 文件变化导致整个备份 `source_changed`。
- [ ] `.part` 和 Orphan 永久排除。

## 13. 一致性和提交

- [ ] `BackupOperationGate` 同时包含进程内锁和 FileChannel 锁。
- [ ] 备份/快照与 Purge、音频提交、正式写入互斥。
- [ ] Portable 表示单一逻辑时间点。
- [ ] Snapshot 在 WAL checkpoint 后复制主数据库。
- [ ] Room 关闭失败、重开失败和外键失败有稳定错误。
- [ ] 临时密文完成 fsync 和完整解密验证后才发布。
- [ ] SAF Provider 不支持安全 rename 时失败关闭。
- [ ] 目标文件存在时不覆盖。
- [ ] 用户取消和进程终止不会留下有效扩展名半文件。

## 14. Android 系统备份

- [ ] 手动加密备份与 Android Auto Backup 明确分离。
- [ ] 当前规则只 include SharedPreferences 的事实准确。
- [ ] Room、正式音频和 `noBackupFilesDir` 当前不进入系统备份。
- [ ] 通配 SharedPreferences 风险已记录。
- [ ] Manifest/XML 未在 PR09-13A 未授权修改。
- [ ] 正式发布前存在独立系统备份收紧门禁。

## 15. 依赖和许可证

- [ ] Bouncy Castle 1.84 官方版本、许可证和发布时间有记录。
- [ ] Tink Android 1.23.0 官方版本、API 24+ 支持和 Apache-2.0 有记录。
- [ ] 两项只为 `testImplementation`。
- [ ] 生产 APK 不包含两项测试依赖。
- [ ] 无 Native ABI。
- [ ] R8 后生产增量被列为 PR09-13B 实测门禁。
- [ ] 依赖升级需要重跑向量。

## 16. 测试向量

- [ ] 固定 ASCII 密码。
- [ ] 固定 Unicode/NFC 密码。
- [ ] 固定 Salt、Envelope ID、Root Key、Wrap Nonce、Streaming Salt、Nonce Prefix。
- [ ] 空 Payload。
- [ ] 单条文本 Entity。
- [ ] 多 Segment 和最后不足完整 Segment。
- [ ] 固定 KEK、Wrapped Key、密文和 SHA-256。
- [ ] 错误密码。
- [ ] Header、Wrapped Key、Streaming Header、Ciphertext 和 Tag 篡改。
- [ ] 截断、原始密文附加、认证记录尾随。
- [ ] 未知版本、超限 Header、未知 KDF Profile。
- [ ] 路径和总大小超限。
- [ ] 向量不含真实秘密或个人信息。

## 17. 原型隔离

- [ ] 全部原型位于 `app/src/test/java/com/elio/jianyu/backup/design/`。
- [ ] 每个原型标注不是生产 API。
- [ ] `app/src/main` 无原型引用。
- [ ] `JianyuAppRuntime`、UI、Coordinator、Worker 无原型引用。
- [ ] 测试固定随机输入不能被生产调用。
- [ ] 无生产导出、导入、恢复或 SAF 接线。

## 18. 错误和日志

- [ ] 稳定错误码完整。
- [ ] 未知版本安全拒绝。
- [ ] 不静默明文回退。
- [ ] 不静默弱 KDF 回退。
- [ ] 不忽略认证失败。
- [ ] 不产生“部分成功”的伪备份。
- [ ] 日志只含阶段、错误码、数量和非敏感耗时。
- [ ] 密码、密钥、Nonce、正文、完整路径不进入日志。

## 19. 交接可施工性

- [ ] PR09-13B 不需要重新选择算法、参数、格式或数据范围。
- [ ] 可修改和禁止修改文件范围清楚。
- [ ] PR09-14A/14B 的解析、预览、兼容 ChatSession 重建和原子替换要求清楚。
- [ ] 文档不存在影响施工的占位内容或相互矛盾。
- [ ] 回滚不删除用户已有备份或快照。

## 20. 最终审查报告模板

```markdown
# PR09-13A 独立安全审查报告

## 1. 结论
PASS / PASS_WITH_NOTES / FAIL / INSUFFICIENT_EVIDENCE

## 2. 精确目标
- Base：
- Head：
- PR：
- 审查时间：

## 3. 阻断问题
- 无 / 按严重度列出

## 4. 重要说明
- 算法与参数：
- Envelope：
- 数据范围：
- Purge/并发：
- Android 系统备份：

## 5. 测试证据
- JVM：
- 向量：
- Lint/Build：
- CI：
- 本地只读验收：

## 6. 未验证项
- 真机性能：
- OEM/SAF：
- 生产 APK 增量：

## 7. PR09-13B 启动建议
允许 / 不允许，并说明原因。
```
