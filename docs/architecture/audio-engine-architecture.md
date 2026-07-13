# 离线语音引擎与转码架构设计说明

本文档详细阐述了 AI 智囊圆桌 v2.0 中新增的 **Gemini Live WebSocket 语音合成 (TTS)** 以及 **后台 MediaCodec 音频压缩转码引擎** 的设计与实现方案。

---

## 1. 整体架构与模块协作

音频引擎作为独立的数据加工与媒体播放单元，主要由网络层、播控层、后台编解码层与 Room 持久化层协同运作。

```
                       [ 发言气泡点击 🔊 ]
                               │
                               ▼
            ┌──────────────────────────────────────┐
            │       AudioPlaybackManager.kt        │ ◀─── 订阅/推送状态
            └──────────────────────────────────────┘
                               │
                   [ 缓存未命中，发起合成 ]
                               │
                               ▼
            ┌──────────────────────────────────────┐
            │           LiveApiClient.kt           │
            └──────────────────────────────────────┘
                               │
                     [ WebSocket 流式收包 ]
                               │
                               ▼
            ┌──────────────────────────────────────┐
            │           字节内存缓冲区             │
            └──────────────────────────────────────┘
                               │
                     [ 包装 44 字节 WAV 头 ]
                               │
                               ▼
                      ( 本地无损 WAV 文件 )
                               │
                    [ 立即触发 MediaPlayer 播 ]
                               │
                   [ 触发 WorkManager 后台任务 ]
                               │
                               ▼
            ┌──────────────────────────────────────┐
            │        AudioTranscodeWorker.kt       │
            └──────────────────────────────────────┘
                               │
                    [ MediaCodec 硬件编码 ]
                               │
                               ▼
                      ( 高保真 AAC 文件 )
                               │
                 [ 数据库路径更新 & WAV 物理删除 ]
```

---

## 2. 详细设计实现

### 2.1 Gemini Live WebSocket 收流设计 (`LiveApiClient`)
由于官方 Live 接口只支持双向 WebSocket 以实现低延迟交互，我们采用 OkHttp 的 `WebSocket` 封装拉取流：
- **握手与建连**：
  ```
  wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key={apiKey}
  ```
- **配置与初始化**：客户端建立连接后发送 `setup` 帧，声明模型为 `gemini-3.1-flash-live-preview`，将 `responseModalities` 限定为 `AUDIO`，并通过 `voiceConfig` 动态配置对应智囊音色。
- **PCM 累加与 WAV 头部拼装**：
  流式接收 Base64 编码的 PCM 裸流（24000Hz, 单声道, 16-bit）并在内存中累加。流结束时，调用 `writeWavFile()`，在字节流前部写入 **44 字节的标准 RIFF/WAVE 头**：
  ```
  [0-3]   "RIFF"
  [4-7]   文件总大小 - 8
  [8-11]  "WAVE"
  [12-15] "fmt " (格式标记)
  [16-19] 16 (过渡子块大小)
  [20-21] 1 (PCM 编码格式)
  [22-23] 1 (声道数)
  [24-27] 24000 (采样率)
  [28-31] 48000 (波形音频数据传输速率 = 采样率 * 声道 * 采样位宽 / 8)
  [32-33] 2 (数据块对齐 = 声道 * 采样位宽 / 8)
  [34-35] 16 (采样位宽)
  [36-39] "data" (数据子块标记)
  [40-43] 音频裸流字节大小
  ```
  直存为 `.wav` 文件后，不进行任何编解码等待，立即回调 UI 并启动 `MediaPlayer` 播放，保证用户交互响应在数百毫秒内完成。

### 2.2 音频播放管理器 (`AudioPlaybackManager`)
- 采用单例设计，持有 Android `MediaPlayer` 对象。
- 维护 `currentPlayingMessageId: StateFlow<Long?>` 以及 `isPlaying: StateFlow<Boolean>`。
- 支持暂停、恢复、停止及淡出释放逻辑。当用户点击另一条消息时，播控器自动重置前置播放，重新拉取缓存。

### 2.3 后台 WorkManager + MediaCodec 转码器 (`AudioTranscodeWorker`)
WAV 无损音频体积极大（1分钟音频约 2.8 MB），极易撑爆用户存储空间。我们在音频播放后，使用 Android `WorkManager` 静默提交后台转码任务：
- **执行环境**：继承自 `CoroutineWorker`，自动在系统的 Background Thread 中调度硬件编码资源，不阻塞 UI 渲染。
- **转码核心（MediaCodec）**：
  - 初始化音频格式 `mediaFormat`（MIME = `audio/mp4a-latm`，比特率 = `64Kbps`，采样率 = `24000Hz`，通道数 = `1`）。
  - 配置 `MediaCodec` 为编码器，并将裸音频流数据持续送入输入 Buffer。
  - 从输出 Buffer 轮询编码后的 AAC 数据帧。
- **ADTS 头部拼装（保证通用性）**：
  为了使生成的 `.aac` 文件在任何原生音频组件中直接可播，我们跳过了通常需要完整封装器输出的 `MediaMuxer`（在异常退出时易损坏文件），而是选择在每一帧编码数据前物理拼接上 **7 字节的 ADTS (Audio Data Transport Stream) 头部**：
  ```
  Byte 0: 0xFF (同步字高8位)
  Byte 1: 0xF1 (同步字低4位, MPEG-4, 无保护)
  Byte 2: 配置文件(AAC LC), 采样率索引(24kHz对应6), 声道(1)
  Byte 3-5: 包含 ADTS 头在内的帧长度信息
  Byte 6: 缓冲度充盈度 (0x7F)
  ```
- **事务与垃圾清理**：
  转码结束后：
  1. 调用 Room `ChatDao` 写入数据库，更新消息音频文件路径为 `.aac`。
  2. 更改 `audioFormat` 值为 `"aac"`，重新计算文件大小并写入 `audioSizeBytes`。
  3. 执行本地文件系统安全操作：**物理删除大 WAV 文件**，仅保存 AAC 音频，空间占用**缩减 85% 以上**。

---

## 3. 音频库界面交互设计
- **卡片渲染**：离线音频文件存在时渲染 🔊 播放控制器，展示实时统计的大小与格式标签（🔴 WAV 原始大文件 / 🟢 AAC 已转码）。
- **手动瘦身**：允许用户在音频库页面中手动一键触发 WAV ➔ AAC 转码。
- **批量优化与删除**：顶部工具栏具备「全部转码」和「清空全部音频」接口，充分保障用户的本地磁盘空间隐私与可控。
