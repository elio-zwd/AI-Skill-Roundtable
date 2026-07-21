# 智囊大厅 Bento 线性化、物理弹簧交互、熔断调试与自动化工具链完工报告 (v2.2)

本项目遵循 `@minimalist-ui`（Notion 风格）与 `@design-taste-frontend` 规范，完成了主脑暴屏的极致空间窄窄化重构、智囊大厅（Tab 1）Bento 线性扁平化重构、物理弹簧阻尼微交互（`bounceClick`）、API 熔断调试遥测面板（UI 修复接入）、以及根目录 API 级运行时 ADB 控制与 OpenCV 图像特征匹配工具链的交付！

---

## 📸 1. Notion 极简 UI 重构与视觉效果 (Notion-Styled Aesthetics)

为彻底消灭廉价的演示风（Demo-feeling）与 Slop 感，界面完成了颠覆性的极净化升级：

- **主脑暴屏 Header 瘦身**：顶部 AppBar padding 减半，将“自动顺延发言”和“专家排序”两个 Switch 开关移出首屏，重新收纳于左侧滑 Drawer（会议历史抽屉）底部；头像 LazyRow 缩小至 32dp 并隐藏汉字标签，与联网模式选择器（智能/强制/关闭）水平并列在同一行，为主脑暴区释放了 **140dp (约 15% 纵向面积)** 的巨大开阔视野。
- **大厅 Tab 1 Bento 线性名册**：废除了原本臃肿、带横向细分割线且占用大段空间的角色设定卡片，高度从 140dp 降至 64dp。操作按钮收拢进 `MoreVert` 图标触发的 `DropdownMenu` 浮动层中；高危的 Switch 重构为圆角 6dp、具有薄荷绿（入席）和灰色（旁听）效果的微型可点击“状态胶囊 Box”。
- **物理弹簧微交互 (`bounceClick`)**：使用 Compose 手势指针编写了无侵入式物理阻尼弹性缩放 Modifier。在按下时缩放至 0.96f，松手时使用 Spring 顺滑回弹，覆盖了全屏所有触控件（TabBar、Chip、Icon按钮、卡片、状态胶囊）。
- **高定 Canvas 占位符（替代 Emoji）**：剔除廉价 Emoji。设计了 `MinimalistPulseIndicator` 代替 🧠 大脑（三层同心圆 alpha 脉动发光环）和 `MinimalistAudioEmptyIndicator` 代替 🎵（6 根随时间波动的条形矩形声谱波形）。

---

## ⚙️ 2. API 熔断遥测调试面板 (ApiDebugPanelDialog)

针对之前密钥配置 Dialog 无 UI 显示调试日志的 Bug，重新设计并接入了**诊断遥测面板**：
- **入口路径**：齿轮（配置密钥 Dialog） $\rightarrow$ 橙黄色超链接“熔断诊断与遥测日志”。
- **熔断状态看板**：实时计算并以 HH:mm:ss 倒计时列出 10 个内置 Key（`w1` 到 `w10`）当前在本地 SharedPreferences 中的熔断解禁倒计时。
- **遥测日志折叠卡片**：使用内存队列维护最近 50 条网络请求。卡片支持点击折叠/展开，展开后能完整审查发送给大模型的 **Prompt 文本** 与底层的 HTTP 网络连接报错堆栈，极为方便在线调试与网络排错。
- **手动干预**：底部提供“清除熔断状态”按钮，点击可一键重置所有 Key 的熔断计时器。

---

## 🚀 3. API 级极简运行时 ADB 自动化工具链 (Root /tools/)

在根目录下设计了面向 AI 代理与 E2E 自动化测试的运行时控制脚本，并在 `.gitignore` 中追加了隔离目录 `/tmp_debug_media/`：

1. **[screencap.py](../../tools/screencap.py)**：极简截图拉取。成功时**仅输出**本地保存的绝对路径。
2. **[click.py](../../tools/click.py)**：模拟点击（`x y`）、长按（`-l Ms`）、滑动（`-s`）、文本输入（`-t`）与物理按键（`-k`），成功时仅输出 `OK: ...`。
3. **[uidump.py](../../tools/uidump.py)**：XML 树智能搜寻。内置了菜单/设置/添加/存组等常用按钮的“同义词联想映射”。匹配成功时，**仅输出中点物理坐标值：`X Y`** (如 `975 142`)。
4. **[find_icon.py](../../tools/find_icon.py)**：视觉级多尺度图像特征搜寻（基于 OpenCV 和 NumPy）。支持在 $0.5 \sim 1.5$ 范围内自适应缩放特征图查找，**完美兼容模拟器 (1080x2400) 与真机小米 14 Ultra (1440x3200) 不同 DPI 与分辨率**。
5. **[adb_verbose_diagnose.py](../../tools/adb_verbose_diagnose.py)**：Verbose 版详细诊断脚本，用于排查设备 DPI、物理分辨率、顶层 Activity 焦点及 ADB 连通性。
6. **[ui-space-layout-guide.md](../architecture/ui-space-layout-guide.md)**：新增 UI 空间感定位指南。记录了在 $1080 \times 2400$ 规格下所有常用按钮在模拟器和小米 14 Ultra（1.33333 倍拉伸）真机上的坐标对照与盲操连招使用说明。

---

## 🧪 4. 自动化测试与编译检查 (Validation & Test)

- **Gradle 编译**：在 JDK 17 环境下运行 `.\gradlew.bat compileDebugKotlin` 验证通过（`BUILD SUCCESSFUL`），用户重构的代码 100% 编译安全。
- **集成测试套件**：编写并运行自动化测试用例 `python test/test_adb_tools.py`，全部 5 个测试用例（截图、XML 转储、同义词推导匹配、OpenCV 视觉特征识别定位）顺利通过：
  ```text
  Ran 5 tests in 7.402s
  OK
  ```
- **Git 版本同步**：所有重构代码、工具链 Python 脚本、高保真图标模板及 README 说明文件均已安全推入本地 Git 分支。
