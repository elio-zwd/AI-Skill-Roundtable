# Android Keystore 安全测试

## 被测实现

```text
app/src/main/java/com/elio/skillroundtable/network/EncryptedApiKeyStore.kt
```

密钥池使用：

- Android Keystore；
- AES-256-GCM；
- 128-bit GCM authentication tag；
- 每次写入由 `Cipher` 生成新的 IV；
- `AtomicFile` 防止中断写入留下半文件；
- `Context.noBackupFilesDir` 排除系统备份。

## Instrumentation 测试

```text
app/src/androidTest/java/com/elio/skillroundtable/network/EncryptedApiKeyStoreTest.kt
```

覆盖：

1. 多条 `ApiKeyRecord` 写入后可完整读回；
2. 加密文件位于 `noBackupFilesDir`；
3. 文件内容不包含 API Key 或显示名称明文；
4. `clear()` 删除加密文件并恢复空记录；
5. 密文或认证标签损坏时安全返回空记录并提供错误状态；
6. 每个测试结束后清理测试文件和 Android Keystore 测试别名。

测试使用明显的非真实占位字符串，不需要 Gemini API Key，不调用网络，也不读取 GitHub Secrets。

## 执行方式

本地设备或模拟器：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

GitHub Actions 会在 API 30 x86_64 Emulator 上运行同一组 Instrumentation Test，并上传测试报告。

## 安全边界

测试证明当前实现能够：

- 避免将用户 API Key 明文写入文件；
- 使用 Android Keystore 托管不可导出的 AES 密钥；
- 检测 AES-GCM 密文或认证标签损坏；
- 将持久化文件放入不参与系统备份的目录。

测试不证明设备已免受 root、系统级恶意软件、运行时内存读取或被攻陷操作系统的攻击。
