# PR09-13A 公开测试向量

本目录包含见域备份设计原型的公开、确定性、无真实用户数据测试向量。

## 1. 文件

| 文件 | 作用 |
|---|---|
| `portable-empty.json` | ASCII 密码、空逻辑 Payload、完整 Portable Envelope |
| `portable-unicode-record.json` | Unicode NFC 密码、单条中文 Entity、完整 Portable Envelope |
| `streaming-multisegment.json` | 三 Segment Tink AES-GCM-HKDF 兼容向量，使用测试专用 4096-byte Segment |
| `negative-vectors.json` | 错误密码、篡改、截断、尾随、版本、资源上限和路径负向变换 |

## 2. 安全说明

- 所有密码都是公开测试字符串，不得用于真实备份。
- Salt、Root Key、Nonce、Streaming Salt 和 Nonce Prefix 都是固定测试输入。
- 固定输入只允许在 `app/src/test` 使用。
- 生产实现必须使用 `SecureRandom`，不得读取这些向量作为随机源。
- 向量中不存在 API Key、Token、设备密钥、用户正文或个人信息。

## 3. Portable 向量算法

1. 对密码执行 Unicode NFC，再编码 UTF-8。
2. 使用 Argon2id Profile 1：
   - Version `0x13`；
   - Memory `65536 KiB`；
   - Iterations `3`；
   - Parallelism `1`；
   - Salt `16 bytes`；
   - Output `32 bytes`。
3. Canonical Header 进入 Root Key Wrap AAD。
4. 使用 AES-256-GCM 包装固定 32-byte Root Key。
5. 使用 HKDF-SHA256 派生 Streaming IKM：

```text
ikm = root_key
salt = envelope_id
info = "jianyu/portable-backup/v1/stream"
length = 32
```

6. Streaming AAD：

```text
SHA-256(wrap_aad || wrap_nonce || wrapped_root_key_ciphertext_and_tag)
```

7. 依据 Tink AES-GCM-HKDF Streaming 规范生成流式密文。
8. 完整文件为：

```text
outer_prefix || wrap_nonce || wrapped_root_key_ciphertext_and_tag || streaming_ciphertext
```

## 4. 多 Segment 向量

`streaming-multisegment.json` 使用 `ciphertextSegmentSize=4096`，仅用于让公开夹具在较小体积下覆盖：

- Segment Index；
- Final Flag；
- 中间 Segment 最大长度；
- 最后一块不足完整 Segment；
- Segment 重排、重复和截断。

该 4096-byte Profile 不是 Envelope Algorithm ID 1。生产 Envelope Parser 必须只接受冻结的 1 MiB Profile；测试专用向量不得进入生产算法注册表。

## 5. 复现

JVM 测试必须：

- 使用 Bouncy Castle 1.84 复现 Argon2id KEK；
- 使用 JCE AES/GCM 复现 Root Key 包装；
- 使用设计参考实现复现固定 Streaming Ciphertext；
- 使用 Tink `AesGcmHkdfStreaming` 解密固定 Streaming Ciphertext；
- 比较完整文件 SHA-256；
- 对 `negative-vectors.json` 的每个变换验证稳定错误码。

## 6. 预期摘要

```text
portable-empty completeFileSha256:
e4cfc347ee072c765584b0737fa7f3048740271e4aaf52736593f9314130e36e

portable-unicode-record completeFileSha256:
5ac25980f1ae7c02eaaeea92d6555616183f58003929f8e36783b1d2ae26cc70

streaming-multisegment ciphertextSha256:
826bca2c616360dc15e7a7b3fa0c5aa7e83bc1b782363a7a4f0a20ce927ae753
```
