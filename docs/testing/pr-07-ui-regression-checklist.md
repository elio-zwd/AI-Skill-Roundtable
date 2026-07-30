# PR07 UI 基础重构最终回归清单

## 1. 用途

本清单用于 PR07-F、PR08 基线确认和后续 UI 变更回归。执行者必须记录真实环境、命令、退出码、关键日志和测试前后工作区状态，不得把静态阅读或远端 CI 误写成本地通过。

## 2. 自动化命令

Windows / JDK 17：

```powershell
git status --short
git rev-parse HEAD
.\gradlew.bat --version
.\gradlew.bat clean
.\gradlew.bat compileDebugKotlin
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat connectedDebugAndroidTest
pwsh.exe -File .\tools\check-secrets.ps1 -IncludeHistory
git diff --check
git status --short
```

补充核对：

```powershell
git diff --exit-code -- app/schemas
git status --porcelain --untracked-files=all -- app/schemas
```

若 `connectedDebugAndroidTest` 因 SDK、UTP、TLS、设备或模拟器环境失败，必须记录第一条根因错误，并明确“测试未开始”还是“测试用例失败”。

## 3. 自动门禁覆盖

### JVM 单元测试

- `AppDestinationTest`：目的地顺序、标签、route 唯一性、起始目的地、二级返回链。
- `UiArchitectureGuardrailTest`：MainActivity、App、Route/Screen、跨页面依赖、theme/navigation、兼容 Token。
- Roundtable UiState 映射与消息分组测试。
- CharacterHall UiState、表单和事件 reducer 测试。
- AudioLibrary UiState/文案映射测试。
- API Key 与 Telemetry UiState/文案映射测试。

### Compose / Instrumentation Test

- `AppBottomNavigationTest`：三个顶层目的地标签和点击回调。
- `AppNavHostTest`：冷启动、顶层切换、圆桌→API Key→遥测返回链。
- `RoundtableScreenTest`：新建会话、输入、发送/停止、失败重试/忽略标签。
- `CharacterHallScreenTest`：加载、空状态、分组栏和新增角色入口。
- `AudioLibraryScreenTest`：音频库根节点与空状态。
- `SettingsScreenRegressionTest`：API Key 根节点/导入控件与遥测根节点。
- 现有 Room Migration 与 EncryptedApiKeyStore Instrumentation Test。

## 4. 真机完整回归

建议设备：Xiaomi 14 Ultra 或等价 Android 14 真机；另保留一个 API 30 模拟器作为 CI 对照。

### 圆桌

- [ ] 冷启动进入圆桌，无崩溃、白屏或布局阻塞；
- [ ] 新建、切换、重命名、删除历史会议；
- [ ] 发送问题，SSE 正常流式更新；
- [ ] 运行中停止，停止后可再次提问；
- [ ] 手动继续下一角色；
- [ ] 完成本轮后进入下一轮；
- [ ] 部分角色失败后原位重试；
- [ ] 忽略失败状态后页面恢复；
- [ ] Markdown 复制和本地保存；
- [ ] TTS 合成与播放入口。

### 智囊

- [ ] 预设分组和自定义分组应用；
- [ ] 保存、删除自定义分组；
- [ ] 角色入席/旁听；
- [ ] 详情 BottomSheet 加载、空内容与关闭；
- [ ] 新增、编辑、删除自定义角色；
- [ ] 受保护角色不可删除。

### 音频库

- [ ] 连接、配置、生成、保存状态显示；
- [ ] 真实已生成音频时长更新；
- [ ] 失败状态和关闭；
- [ ] 播放、展开、删除；
- [ ] 现有 WAV→AAC 转码入口。

### API Key 与遥测

- [ ] Key 单个/批量导入、去重和上限提示；
- [ ] 验证、启停、删除、清空；
- [ ] Key 只显示掩码，无明文泄露；
- [ ] 当前会话 Key 展示；
- [ ] 遥测 OFF / METADATA_ONLY / CONTENT_DEBUG；
- [ ] CONTENT_DEBUG 风险确认与关闭清理；
- [ ] 云端 Interaction 风险确认与关闭；
- [ ] 遥测事件展开和全部清理。

### 导航与生命周期

- [ ] 圆桌、智囊、音频三个顶层页面连续切换；
- [ ] API Key 顶部返回与系统返回一致；
- [ ] 遥测顶部返回与系统返回一致；
- [ ] 圆桌直达遥测后返回 API Key，再返回圆桌；
- [ ] 二级页不显示底部导航；
- [ ] 横竖屏切换；
- [ ] 后台恢复；
- [ ] Activity 重建；
- [ ] 进程被系统回收后重新启动；
- [ ] 全流程无崩溃和白屏。

## 5. 结果记录模板

| 项目 | 结果 | 证据 |
|---|---|---|
| Base / Head SHA |  |  |
| 初始 `git status --short` |  |  |
| `clean` |  |  |
| `compileDebugKotlin` |  |  |
| `testDebugUnitTest` |  |  |
| `lintDebug` |  |  |
| `assembleDebug` |  |  |
| `assembleRelease` |  |  |
| `connectedDebugAndroidTest` |  |  |
| Secret Scan |  |  |
| Room Schema |  |  |
| `git diff --check` |  |  |
| 最终 `git status --short` |  |  |
| 真机型号 / Android |  |  |
| 真机场景 |  |  |
| 未验证项 |  |  |

## 6. 失败反馈要求

失败时提供：

1. 精确 HEAD；
2. 操作系统、JDK、Gradle、Android SDK、设备和 Android 版本；
3. 完整命令与退出码；
4. 第一条根因错误和关键日志；
5. 是否可稳定复现；
6. 失败发生在构建、安装、测试启动还是具体用例；
7. 可能原因，但不得把推测写成结论；
8. 测试前后工作区是否保持干净。
