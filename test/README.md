# 🧪 运行期集成与自动化测试 (Root Test)

本目录存放针对根目录下自动化控制工具链 (`/tools/`) 的集成校验测试用例，确保截图、物理操作模拟、智能同义词联想和 OpenCV 图像搜索工具在设备运行期完全处于可用状态。

---

## 🔍 与 `workspace/tests/` 的职能区分

| 维度 | 根目录 `/test/` (本目录) | 工程子目录 `/workspace/tests/` |
| :--- | :--- | :--- |
| **主要目标** | **自动化交互工具链的验证 (Integration Validation)** | **业务数据与特定算法的单元测试 (Unit Tests)** |
| **测试场景** | 检测 `screencap.py`, `click.py`, `uidump.py`, `find_icon.py` 的 API 输出和返回码正确性。 | 预留作 Room 数据库 Migration、余弦相似度推荐算法等业务逻辑的单元测试。 |

---

## 🚀 运行测试方法

在运行测试前，请确保您的 Android 模拟器或物理手机已启动并连接至 ADB。

### 1. 运行全部工具链校验
在项目根目录下直接执行 Python 测试脚本：
```bash
python test/test_adb_tools.py
```

### 2. 测试覆盖的断言点
* **`test_screencap_silent`**：验证截图能成功在 `tmp_debug_media/` 下落地，且物理文件大小正常（>1KB），控制台仅打印文件路径。
* **`test_uidump_silent`**：验证 `uidump.py` 可以正常 dump 出层次 XML 文件。
* **`test_uidump_find_text`**：验证在当前屏幕上搜索“智囊”等常用字，匹配成功后能够输出合法的 X Y 坐标。
* **`test_uidump_synonyms`**：验证搜别名（如输入“配置”）时能够智能同义词推荐匹配到 content-desc 含有“设置”的齿轮节点，输出其模拟器坐标。
* **`test_find_icon_image`**：验证调用 OpenCV 多尺度图像搜索查找 `tools/templates/setting.png` 齿轮特征时，输出的坐标值是否落在模拟器基准中点范围（X: 965~985，Y: 132~152）内。
