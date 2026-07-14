# 熔断调试面板与语音/文件导出功能技术说明书

本说明书详细介绍 **AI 智囊圆桌** 的核心调试机制（API 熔断诊断与遥测日志），并同步补全音频后台转码（Tab 2）与一键导出功能的技术实现细节。

---

## 1. API 熔断诊断与请求日志遥测

为了解决 Google Gemini API 固有的 QPM（每分钟请求数）与 RPM 限制，防止因并发拉取 7 位智囊角色作答导致账号被封禁或持续返回 HTTP `429` 错误，系统设计了底层的 **API 降级熔断与遥测诊断机制**。

### 1.1 内置备用 Key 池与本地 24h 熔断
- **Key 池管理**：在 [ApiKeyPool](file:///d:/My_Elio/AI-Skill-Roundtable/app/src/main/java/com/example/skillroundtable/network/ApiKeyPool.kt) 中预设了 10 个备用 API 密钥（标识为 `w1` 到 `w10`）。
- **24小时熔断（Ban）**：一旦某个 API Key 在调用时返回了 HTTP `429`（配额超限）或发生特定的网络异常，系统会调用 `ApiKeyPool.banKey(context, keyId)`，在本地 SharedPreferences 中写入该 Key 的过期时间戳（当前系统毫秒值 + 24小时）。在过期时间戳到达前，该 Key 会被标记为 Banned 并在 Key 选择器中被自动屏蔽。

### 1.2 会话级 Key 锁定与缓存优化
- **隐式上下文缓存 (Implicit Context Cache)**：Gemini API 具备隐式缓存机制，当相同的 API 密钥连续接收到前缀相同的超长 Prompt 时，会自动命中缓存，缩短首次字延迟（TTFT）。
- **锁定策略**：系统通过 `getOrBindSessionKey(context, sessionId)` 将当前脑暴会话的 `sessionId` 锁定在某一个未熔断的特定 Key 上。只有当该 Key 触发 429 熔断报错或连接超时时，系统才会自动切换并绑定至下一个可用的备用 Key。

### 1.3 错峰与随机延迟节拍 (Anti-blocking)
在并发拉取所有参会角色的回答时，为了防范短时间内高并发请求被 API 网关屏蔽，系统设计了双重控制流：
1. **组间并发**：根据可用 Key 数量，将所有参会角色随机分入不同的 Key 组（每组 1~3 人）。各组之间**同时（并发）**发起网络请求。不同组的首次请求会在启动时随机错开 **1~3 秒**，实现微观错峰。
2. **组内串行**：绑定相同 Key 的组内成员必须**串行**排队请求，以便最大化命中前缀缓存。前一个角色回答完毕后，该组必须强制随机休眠 **2~6 秒**，再拉起组内下一个角色的请求，规避频控。

### 1.4 调试面板 (ApiDebugPanelDialog)
调试面板为开发者与高阶用户提供了一个直观的频控诊断浮层：
- **入口路径**：点击主屏右上角齿轮（密钥配置）图标 $\rightarrow$ 弹窗内点击橙黄色高对比度超链接“熔断诊断与遥测日志”即可唤起。
- **内置 Key 熔断倒计时**：实时列出 `w1` 到 `w10` 的熔断倒计时（格式化为 `HH:mm:ss`，若已解禁则显示“可用”）。
- **最近 50 条遥测日志**：流式展现内存中最近 50 条 API 请求。每条日志卡片包含状态码（如 `200` 绿标，`429` / `-1` 红标）、模型名、耗时等。
  - **Prompt 折叠展开**：用户可点击任意一条日志卡片将其展开，直观审查发给大模型的**完整 Prompt 文本**与底层 HTTP 报错堆栈，极利于提示词调试与网络排错。
  - **一键重置**：面板底部提供“清除熔断状态（重置计时）”按钮，点击可同步清除 Preference 里的熔断记录，方便在调试时立即重试。

---

## 2. 离线语音管理与后台 ADTS-AAC 转码 (Tab 2)

为了支持首次秒播同时最大化节省手机存储空间，语音收录与播放采用了 **WAV直存播放 $\rightarrow$ 后台 MediaCodec 异步打包转码** 的二级流水线：

### 2.1 WAV 裸流直存与极速秒播
- **拉取与直存**：使用 Gemini Live WebSocket 协议，配置对应的 `voiceConfig` 专属音色建立 PCM 流式接收。流传输结束时，在内存数据头部追加 **44 字节的标准 WAV 头信息**（含采样率 16kHz、单声道、16位深度），直接以 `.wav` 文件写入本地私有目录。
- **秒播机制**：WAV 文件生成后，不经过任何耗时的压缩转码，立即送入原生 `MediaPlayer` 进行首次发声播放，消除了用户的等待感知。

### 2.2 后台 WorkManager 异步转码 (AAC 压缩)
- **触发时机**：播放完成后，或者用户在 Tab 2（音频库）界面手动触发转码时，系统会将该 WAV 文件信息包装并提交给 WorkManager。
- **MediaCodec 编码**：系统拉起 `AudioTranscodeWorker` (CoroutineWorker)，在后台线程调用 Android 原生的 `MediaCodec` 编码器，将 PCM 音频帧输入队列，异步编码输出 AAC 高保真音频数据。
- **ADTS 头部封装**：为了使输出的 AAC 文件能被各种系统播放器零依赖解码播放，在写入文件时，每一帧 AAC 帧前都会手动封装一个 **7 字节的 ADTS (Audio Data Transport Stream) 头部**。
- **物理覆写与空间释放**：AAC 封装写入完毕后，覆写 Room 数据库中该消息记录的 `audioFormat = "aac"` 和最新文件大小，并同步调用物理删除 API 彻底抹除原 `.wav` 文件。转码后的 AAC 文件体积相比 WAV **缩减了 85% 以上**（如 2MB 物理文件压缩至 240KB 左右），极大释放了磁盘空间。

---

## 3. 免动态权限 Markdown 一键导出

在圆桌脑暴结束后，用户可一键将整场脑暴纪要导出为标准的 Markdown 文档。

### 3.1 MediaStore 物理沙盒写入
传统的 Android 文件物理写入往往需要申请高危的 `WRITE_EXTERNAL_STORAGE` 动态权限，容易引起用户安全警惕。为此，项目采用了原生的 **MediaStore API 写入沙盒**：
- **存储路径**：利用 `MediaStore.Downloads` 端点，在公共下载目录中自动创建 `AI_Brainstorm/` 子文件夹。
- **逻辑实现**：
  ```kotlin
  val values = ContentValues().apply {
      put(MediaStore.Downloads.DISPLAY_NAME, "${title}.md")
      put(MediaStore.Downloads.MIME_TYPE, "text/markdown")
      put(MediaStore.Downloads.RELATIVE_PATH, "Download/AI_Brainstorm")
  }
  ```
  通过 `contentResolver.insert` 获取沙盒 Uri，再打开输出流写入生成的 Markdown 字节，全程**完全零动态权限申请**，极佳地保护了系统隐私并提升了应用合规度。
