# UI 目录开发规则

> 本文件适用于 `app/src/main/java/com/elio/skillroundtable/ui/` 及其子目录。与根目录 `AGENTS.md` 同时生效；本文件对 UI 文件更具体。

## 1. 固定分层

```text
MainActivity
  → MainAppContent / AppNavHost
      → <Domain>Route
          → <Domain>Screen
              → Components
```

- `MainActivity`：只挂载主题和 `MainAppContent`。
- `App.kt`：只组装 Scaffold、底部导航、NavHost 和页面 Route。
- `Route`：收集状态、调用 ViewModel/Repository/服务、处理 Toast、Dialog 状态和其他副作用。
- `Screen`：只接收不可变 `UiState` 和事件回调。
- `Components`：只负责展示和局部交互。
- `UiState`：包含页面显示所需的稳定状态、纯映射和纯 reducer；不得发起 IO。

## 2. 依赖方向

允许：

```text
App → navigation/theme/common components/domain Routes
Route → ViewModel/data/network/telemetry + same-domain Screen
Screen → same-domain UiState/Components + common components/theme
Components → same-domain models + common components/theme
```

禁止：

- Screen 或 Components 查找 `RoundtableViewModel`；
- 一个页面域引用另一个页面域的内部组件；
- `navigation/` 或 `theme/` 引用 `screens/`；
- `App.kt` 持有页面专属 Dialog、Drawer、Toast 或表单状态；
- 同时保留 `<Domain>Route` 与接收 ViewModel 的兼容型 `<Domain>Screen`；
- 为目录对称创建无调用方的空壳接口或文件。

## 3. 页面域边界

- `roundtable/`：会话抽屉、消息、席位、轮次、输入、停止、继续、失败重试、导出、TTS 入口。
- `characters/`：分组、角色列表、详情、启停、新增、编辑、删除。
- `library/`：合成进度/失败、音频列表、播放、删除、现有转码入口。
- `settings/`：API Key 与遥测页面；允许同属设置域的共享组件。

跨页面共享展示组件放入 `ui/components/`；不能为了复用把业务状态或页面事件放入公共组件。

## 4. 主题规则

- 全局颜色值只在 `ui/theme/Color.kt` 定义。
- Typography、Shapes、Spacing 分别由 `ui/theme/` 维护。
- `LegacyUiTokens.kt` 只允许把旧语义名映射到真实主题常量，不得出现新的 `Color(...)` 值。
- 新代码优先使用 `MaterialTheme` 与语义 Token，不新增页面私有的全局调色板。
- PR08 可以调整视觉值，但必须在同一 PR 中更新截图/Compose 测试和说明。

## 5. 导航规则

稳定目的地：

- 顶层：`ROUNDTABLE`、`CHARACTERS`、`AUDIO_LIBRARY`；
- 二级：`API_KEYS`、`TELEMETRY`。

要求：

- Route 字符串和返回链属于稳定接口；
- 顶层切换使用 `navigateToTopLevel`；
- 二级页面使用 `navigateToSecondary`；
- 圆桌直接进入遥测时必须保留 `ROUNDTABLE → API_KEYS → TELEMETRY` 返回链；
- 页面局部 Dialog、BottomSheet、Drawer 不进入全局 NavHost；
- 顶部返回与系统返回应得到一致目的地。

## 6. 测试标签

下列标签属于稳定契约，视觉改版不得随意删除或改名：

- `new_session_button`
- `chat_input`
- `send_button`
- `stop_button`
- `retry_failed_characters_button`
- `dismiss_failed_characters_button`
- `character_hall`
- `audio_library`
- `api_key_manager`
- `telemetry_screen`
- `app_bottom_navigation`

新增关键交互时同步添加语义明确的 `testTag` 与 Compose UI Test。

## 7. PR08 修改边界

可修改：Screen、Components、theme、布局、动画、字体、颜色、间距、可访问性和响应式表现。

不得随意修改：Route 副作用、ViewModel 方法、UiState/Event 业务含义、导航路由/返回链、Room/网络/Key/遥测/音频协议以及稳定 `testTag`。

视觉需求必须与结构或业务重构分开提交，便于逐屏回滚。
