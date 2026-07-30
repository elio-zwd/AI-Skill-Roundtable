# PR08 UI/UX 视觉重设计稳定接口

## 1. 结论

PR07 完成的是 Compose UI 基础结构重构，不是视觉重设计。PR08 可以逐屏改变外观和交互呈现，但必须建立在现有 Route、导航、状态和业务接口之上，不能借视觉改版重新打开业务层、Room、网络或协议重构。

PR08 的稳定基线应使用 PR07-F 合并后的 `main` SHA；开发前必须从 GitHub PR 和 `main` 重新确认真实 SHA，不使用本文中的历史规划 SHA。

## 2. 稳定分层

```text
MainActivity
  → MainAppContent / AppNavHost
      → Domain Route
          → Domain Screen
              → Components
```

### 不得改变的职责

- `MainActivity`：Android 入口。
- `App.kt`：顶层 Scaffold、NavHost、底部导航和 Route 组装。
- `Route`：收集 Flow、连接业务事件和副作用。
- `Screen`：不可变 UiState + Event callback。
- `Components`：展示与局部交互。

视觉改版应优先发生在 `Screen`、`Components` 和 `theme`，而不是把页面逻辑搬回 `App` 或 ViewModel。

## 3. 稳定导航契约

| Destination | Route | 类型 | 返回要求 |
|---|---|---|---|
| `ROUNDTABLE` | `roundtable` | 顶层 | 冷启动默认页 |
| `CHARACTERS` | `characters` | 顶层 | 顶层切换保存状态 |
| `AUDIO_LIBRARY` | `audio-library` | 顶层 | 顶层切换保存状态 |
| `API_KEYS` | `settings/api-keys` | 二级 | 返回进入前页面 |
| `TELEMETRY` | `settings/telemetry` | 二级 | 圆桌直达时先回 API Key |

PR08 不得随意改 route 字符串、顶层/二级分类、底部导航显示规则或系统返回链。确需改变产品导航，必须独立需求、独立方案和独立 PR。

## 4. 稳定业务与状态接口

### 圆桌

必须保留：

- 新建、切换、删除、重命名会话；
- 问题发送与停止生成；
- SSE 流式正文、Pending、运行中状态；
- 自动/手动继续本轮和下一轮；
- 失败角色原位重试和忽略；
- 联网搜索模式；
- Markdown 复制、保存；
- TTS 合成和播放入口；
- 历史会议抽屉和系统返回行为。

### 智囊

必须保留：

- 预设与自定义分组应用；
- 保存、删除自定义分组；
- 角色入席/旁听；
- 角色详情加载；
- 新增、编辑、删除自定义角色；
- 受保护角色不可删除。

### 音频库

必须保留：

- 合成连接、配置、生成、保存、失败状态；
- 已接收音频时长；
- 播放、删除、展开；
- 现有 WAV→AAC 转码入口。

Seek、时间轴和新的 AAC 功能不属于视觉改版。

### API Key 与遥测

必须保留：

- Key 批量导入、去重、验证、启停、删除、清空；
- Key 掩码和安全存储；
- 当前会话 Key 展示；
- 遥测 OFF / METADATA_ONLY / CONTENT_DEBUG；
- 遥测清理、24 小时正文调试和风险确认；
- 云端 Interaction 开关及风险确认。

## 5. 稳定 testTag

至少保留：

```text
app_bottom_navigation
new_session_button
chat_input
send_button
stop_button
retry_failed_characters_button
dismiss_failed_characters_button
character_hall
audio_library
api_key_manager
telemetry_screen
```

其他已有动态标签同样应保持语义。需要更换标签时，必须在同一 PR 更新 Compose UI Test、验收脚本和兼容说明。

## 6. PR08 可以修改的层

- `ui/theme/Color.kt`、`Theme.kt`、Typography、Shapes、Spacing；
- 各页面 `Screen` 与 `Components` 的布局、卡片、层级、留白、图标、字体和动效；
- 深色主题视觉值；
- 页面局部状态的视觉表达；
- 无障碍描述、触控尺寸和响应式布局；
- 与视觉直接相关的预览、截图和 Compose UI Test。

## 7. PR08 不得夹带的修改

- ViewModel、Orchestrator、Repository 的业务语义重写；
- Room Schema、Migration 或实体修改；
- Gemini、SSE、WebSocket、TTS、Key 或遥测协议修改；
- 新增亮色主题、Material 3 Adaptive，除非单独批准；
- Seek、时间轴、AAC 新功能；
- 无关业务 Bug；
- 同时保留新旧两套导航或页面实现；
- 跨页面引用其他页面内部组件。

## 8. 推荐 PR08 拆分

```text
PR08-A：视觉规范、主题 Token 与组件基线
PR08-B：圆桌页面视觉改版
PR08-C：智囊页面视觉改版
PR08-D：音频库视觉改版
PR08-E：API Key 与遥测视觉改版
PR08-F：全局回归、截图与可访问性收口
```

共享 `App.kt`、`navigation/` 和 `theme/` 应先确定后冻结；页面域可在共享契约稳定后并行。

## 9. 每个视觉 PR 的门禁

1. 确认 Base 是 PR07-F 合并后的最新 `main`。
2. 读取本文件、UI `AGENTS.md` 和对应页面 Route/UiState/TestTags。
3. 明确截图或设计稿与行为冻结点。
4. 不修改 Route 与业务接口，除非独立说明并获批。
5. 运行 compile、unit、lint、debug/release、Compose instrumentation。
6. 真机验证顶层切换、二级返回、系统返回、Activity 重建和目标页面全流程。
7. 对比改版前后业务状态，不只检查截图。
8. PR 描述列出视觉变化、行为不变项、未验证项和回滚方法。
