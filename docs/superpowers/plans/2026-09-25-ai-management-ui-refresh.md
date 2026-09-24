# AI 管理页 UI 重构实施计划

> 日期：2026-09-25  
> 分支：`codex/ai-management-ui-refresh`  
> 基线：`main@53321b6b26d7084e97be027fe1098bcb1fe403c5`

## 目标

按已确认的「我的」页视觉语言重构现有 AI 管理页，在不改变模型配置、BYOK Key 加密存储、验证、启停、删除与清空业务契约的前提下，把当前“所有控件平铺”的页面改成清晰的两层信息架构：

1. 首页只展示五种模型用途的当前选择，以及 Gemini / DeepSeek Key 状态；
2. 模型选择使用页面内 Bottom Sheet；
3. 单个提供商的 Key 导入、验证、Key 池和破坏性操作使用页面内 Bottom Sheet；
4. 保持 AI 管理为二级页面，不新增根底部导航，不改变全局导航契约。

## 设计约束

- 继续使用 Material 3、`MaterialTheme`、现有 Shape/Spacing/Color 语义 token。
- 视觉对齐「我的」：浅色背景、白色大圆角分组卡、细描边、紫色主强调、灰蓝次级文字。
- 五种调用用途放入同一分组卡；首页只显示当前模型，不展开所有 Provider / Model Chip。
- “联网检索”只展示 Gemini 支持的模型，继续遵守现有 `AiUseCase.supportedProviders`。
- 首页 Provider 状态使用“未配置 Key”或“X 个 Key · Y 个可用”，不显示完整 Key。
- 完整 Key 仍只进入现有本地加密仓库；本任务不修改网络协议、Key 存储格式或模型枚举。
- 保留现有稳定 testTag `api_key_manager`，同步扩展新交互的测试标签。
- 不新增全局 Route；Bottom Sheet 仅属于 AI 管理页面局部交互。

## Tasks

- [x] Task 1：补充 UI 状态与纯映射测试，覆盖双 Provider Key 状态、首页摘要文案和现有可用 Key 计数契约。
- [x] Task 2：重构 `AiManagementScreen` 首页为“模型配置 / API Key / 模型说明”三段分组卡。
- [x] Task 3：实现模型选择 Bottom Sheet，按用途限制 Provider 与 Model，并保留现有持久化回调。
- [x] Task 4：实现 Provider Key 管理 Bottom Sheet，保留批量导入、验证、启停、删除、清空和当前会话优先 Key 信息。
- [x] Task 5：调整 `AiManagementRoute` 同时观察 Gemini / DeepSeek Key 摘要，并将选中 Provider 作为详情状态传给 Screen。
- [x] Task 6：更新 Compose 回归测试，验证首页不再平铺导入框、模型与 Provider 入口可打开对应 Sheet、关键控件仍可达。
- [x] Task 7：静态自审：回读修改、检查与 `main` 的净差异，确认无导航/网络/密钥协议越界。
- [ ] Task 8：运行/委托验证：`compileDebugKotlin`、`testDebugUnitTest`、`lintDebug`、`assembleDebug`；UI 交互由本地 AI 做只读设备验收。

## 预期主要文件

- `app/src/main/java/com/elio/jianyu/ui/screens/settings/AiManagementScreen.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/settings/AiManagementRoute.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/settings/AiManagementUiState.kt`
- `app/src/test/java/com/elio/jianyu/ui/screens/settings/AiManagementUiStateTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/ui/screens/settings/SettingsScreenRegressionTest.kt`

## 风险

- 同时观察两个 Provider 的 Key Repository 时不能改变现有 Key 生命周期和加密存储行为。
- Bottom Sheet 状态与 Provider 切换必须避免显示前一个 Provider 的输入、结果或确认状态。
- 视觉重构不能删除已有 Key 操作能力，也不能把破坏性“清空”提升为首页主操作。
- Android 本地编译与设备 UI 证据不由 GitHub 文件写入本身提供，未实际运行前不得宣称通过。
