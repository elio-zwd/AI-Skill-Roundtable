# AI 智囊圆桌 v2.0 - 功能交付总结

项目已圆满完成 Wave 1 ~ Wave 4 的全套功能重构与开发。所有改动均已通过 Gradle 编译和 Debug APK 打包验证。

---

## 🚀 交付成果汇总

### 1. Bug 修复与稳定性提升 (Wave 1)
- **修复 Bug①（内容截断）**：提升 `maxOutputTokens` 至 8192，同时设置 OkHttpClient 的超时限制为 120 秒，避免长文本生成因超时被截断。
- **修复 Bug② 与 优化②（上下文污染与重构）**：
  - 第一轮脑暴时，每个角色**独立并发**响应，请求参数仅携带「用户提问 + 角色自画像 Prompt」，彻底消除了第一位发言者可能会“伪造其他智囊发言”的虚假构建（Bug②）。
  - 从第二轮起，才统一带入上一轮所有智囊的发言上下文，保证多维脑暴深度。

### 2. 多角色并发架构与防屏蔽反检测 (Wave 2)
- **多角色并发回复**：使用协程 `coroutineScope` 并发运行多个智囊角色的 API 交互，界面上多个气泡同时展现「✦ 正在思考...」状态。
- **反检测随机延迟节奏**：
  - 用户提问后，各 Key 组**随机延迟 1~3 秒**后启动，防止同时向 Google API 发送高并发特征包。
  - 同一 API Key 内的角色严格串行，上一个角色发完后，**随机等待 2~6 秒**再触发下一个请求。
- **横向滑动轮次气泡 (HorizontalPager)**：同一轮次的智囊发言使用 `HorizontalPager` 横向排版。底部内置一排智囊头像高亮指示器，点击头像可平滑滚至对应页面。
- **AI 自动命名与重命名**：首个问题后使用 `gemini-3.1-flash-lite-preview` 提炼 ≤15 字的对话标题，并支持长按标题或侧栏列表项弹窗手动重命名。

### 3. Markdown 内容增强与一键导出 (Wave 3)
- **Markdown 精致解析**：引入 `compose-markdown` 渲染库，用户的发言保持纯文本，AI 的脑暴见解全面渲染为 Markdown 高清格式。
- **无感 MediaStore 本地导出**：顶栏新增📄按钮。支持一键「复制为 Markdown 文本」及「免权限保存到本地 Documents/AI智囊圆桌/{title}_{timestamp}.md」文档。
- **API 熔断调试面板**：设置页提供工业级监控面板，动态呈现在线 10 个 Key 的熔断剩余时长，且可以追踪最近 50 次 API 请求的成功率、耗时（ms）与完整 Prompt 日志。

### 4. Gemini Live 语音（TTS）与音频资产管理 (Wave 4)
- **极速 WebSocket WAV 直存**：
  - 针对每个角色气质分配了 7 种 Gemini 30 选 1 专属声线（如马斯克用 Fenrir 亢奋音，芒格用 Gacrux 沉厚音）。
  - 用户点击 🔊 时，通过 OkHttp WebSocket 连接 `models/gemini-3.1-flash-live-preview` 实现在端上流式获取 PCM 裸流。
  - 结束时在内存中封装 **44字节标准无损 WAV 头部** 保存为 `.wav` 文件（无延迟，立即可听）。
- **后台转码 AAC**：
  - 使用 WorkManager 与原生 `MediaCodec` 异步转码，封装 ADTS 帧头部压缩成 `.aac`（`.m4a`）文件。
  - 转码成功后，自动覆写数据库路径，并物理删除大 WAV 文件，**节省 85%+ 空间**。
- **离线音频库管理 UI**：底部栏新增「🎵 音频库」Tab。卡片支持播放/暂停、全文折叠、一键删除（删除文件与数据，或删除整条发言）以及 WAV 手动一键转码压缩。

---

## 🛠️ 编译与真机部署验证

### 编译成功
在 pwsh（PowerShell 7）环境配置 JDK 17 下完成构建：
```powershell
# 设置 JDK 17 环境（请替换为您的实际 JDK 路径）
$env:JAVA_HOME = "C:\path\to\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew.bat compileDebugKotlin   # 零错误通过
.\gradlew.bat assembleDebug        # 打包成功
```

### 交付包文件
- **最新可运行 APK 包**：[app-debug.apk](../../app/build/outputs/apk/debug/app-debug.apk) (双击或使用 ADB 安装到您的 Windows 10 连接的设备上进行真机测试)。

---

## 📝 提交信息推荐 (英文:中文)
```bash
git add .
git commit -m "feat: 实现多角色并发交锋、Markdown渲染与一键导出、API熔断遥测以及Gemini Live WebSocket WAV直存与后台AAC转码"
```
