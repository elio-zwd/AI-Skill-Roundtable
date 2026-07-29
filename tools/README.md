# 🛠️ 运行时 ADB 自动化工具集 (Root Tools)

本目录存放用于 Android 模拟器与真机（小米 14 Ultra）运行时自动化控制与 UI 交互定位的 API 级极简工具链。这套工具设计为 **“精简 API 模式”**，通常只输出最关键的路径或坐标信息，非常方便 AI 代理进行管道化流式调用（即“盲操点击”）。

---

## 🔍 与 `workspace/tools/` 的职能区分

为了保持项目工程目录的整洁，本目录与 `workspace/tools/` 进行了清晰的界限划分：

| 维度 | 根目录 `/tools/` (本目录) | 工程子目录 `/workspace/tools/` |
| :--- | :--- | :--- |
| **核心关注** | **运行时自动化 (Runtime Automation)** | **工程构建期编译 (Build-phase Compile)** |
| **主要职能** | 控制在线设备进行截图、点击、滑屏、Dump XML 解析、以及 OpenCV 多尺度视觉匹配。 | 提取名人 SKILL.md 介绍、自动生成向量特征入库 Seeding 库、批量重命名 Morandi JPG 头像等。 |
| **主要消费方** | 本地调试运行的 AI 代理（如 Antigravity）与 E2E 自动化测试。 | Android 编译 Gradle 脚本与预编译资产维护。 |

---

## 🚀 脚本使用指南

### Release APK 构建：`build-release-apk.ps1`

此脚本仅适用于 Windows PowerShell 7，使用本机 `keystore.properties` 或 `RELEASE_*` 环境变量构建并验证**已签名** Release APK；缺少完整签名配置时会在构建前失败，绝不会回退为 Debug 或上传未签名 APK。

```powershell
pwsh.exe -File .\tools\build-release-apk.ps1 -VersionName 1.1 -VersionCode 2
```

产物位于 `app\build\outputs\apk\release\`。签名材料必须保留在本机，禁止提交到仓库。

### Debug APK 构建：`build-debug-apk.ps1`

用于生成可安装的 Debug 测试包。脚本将 Gradle worker 限制为 1，不需要 Release keystore；仅适合测试或预发布分发，不可替代正式签名 Release。

```powershell
pwsh.exe -File .\tools\build-debug-apk.ps1 -VersionName 1.1 -VersionCode 2
```

产物位于 `app\build\outputs\apk\debug\app-debug.apk`。

所有脚本在有多台设备连接时，均会**自动选择第一台是在线 `device` 状态的设备**进行交互；也支持通过 `-d <deviceId>` 参数强制指定。

### 1. 极简屏幕截图：`screencap.py`
截取当前手机屏幕并拉取到本地，拉取后手机端临时文件会被自动清除。
* **命令**：
  ```bash
  python tools/screencap.py -o tmp_debug_media/my_screen.png
  ```
* **API 输出**：成功时**仅输出**本地 PNG 的绝对路径。

### 2. 极简屏幕触控：`click.py`
向设备模拟发送点击、长按、滑动轨迹、按键与文本。
* **点击**：`python tools/click.py 540 1900`
* **长按**：`python tools/click.py 540 1900 -l 1000` (长按 1 秒)
* **滑动**：`python tools/click.py -s 100 2000 100 500 400` (从 Y:2000 滑动到 500，历时 400ms)
* **物理按键**：`python tools/click.py -k 4` (发送 Back 物理返回键)
* **文字输入**：`python tools/click.py -t "马斯克"`
* **API 输出**：成功时仅输出一行 `OK: <具体操作结果>`。

### 3. XML 同义词定位查找：`uidump.py`
转储当前界面的 UI 层次树 XML 文件，并支持文字（含别名同义词推导）定位。
* **别名内置**：搜“设置”或“配置”时，会自动包含 `setting`, `setup`, `config`, `密钥配置` 属性，搜“菜单”或“抽屉”时会自动匹配 `menu`, `drawer`, `navigation` 等。
* **命令（搜文字坐标）**：
  ```bash
  python tools/uidump.py --find "设置"
  ```
* **API 输出**：若搜字成功，**仅打印中点 X Y 坐标**（如 `975 142`），失败时向 stderr 输出 ERROR。

### 4. 视觉级多尺度图像定位：`find_icon.py`
当界面没有文字（如自定义 Canvas 或占位符），读取 `tools/templates/` 目录下的特征图，在截屏中滑窗匹配。
* **多尺度自适应**：支持在 $0.5 \sim 1.5$ 范围内缩放特征图查找，因此能**完美兼容模拟器 (1080x2400) 与真机小米 14 Ultra (1440x3200)** 不同的 DPI 分辨率。
* **命令**：
  ```bash
  python tools/find_icon.py -t tools/templates/setting.png
  ```
* **API 输出**：成功匹配时，**仅输出中点 X Y 坐标**（如 `975 142`）。

### 5. 详细日志诊断：`adb_verbose_diagnose.py`
输出最完整的 adb 连接、屏幕分辨率、DPI、当前顶层 Activity 及转储耗时的 verbose 诊断报告，用于排查设备连通性故障。
* **命令**：
  ```bash
  python tools/adb_verbose_diagnose.py
  ```

---

## 📂 目录图标特征库 `tools/templates/`
本目录下存放用于视觉图像匹配的原始高保真特征小图：
* `setting.png`：齿轮设置图标特征图（中点 975 142）。
* `menu.png`：三横菜单图标特征图（中点 64 142）。
