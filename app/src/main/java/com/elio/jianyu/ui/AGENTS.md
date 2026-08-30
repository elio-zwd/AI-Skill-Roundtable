# UI 目录开发规则

> 本文件适用于 `app/src/main/java/com/elio/jianyu/ui/` 及其子目录。与根目录 `AGENTS.md` 同时生效；本文件对 UI 文件更具体。
>
> 产品语义必须遵守 `docs/decisions/adr-009-skill-role-conversation-product-model.md` 与 `docs/product/` 当前规范。不得仅因为内部工程名尚未迁移，就把用户体验定义成“圆桌 / 议题 / 主 Skill”。

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

- Screen 或 Components 自行查找业务 ViewModel；
- 一个页面域引用另一个页面域的内部组件；
- `navigation/` 或 `theme/` 引用 `screens/`；
- `App.kt` 持有页面专属 Dialog、Drawer、Toast 或表单状态；
- 同时保留 `<Domain>Route` 与接收 ViewModel 的兼容型 `<Domain>Screen`；
- 为目录对称创建无调用方的空壳接口或文件。

## 3. 页面域边界与产品语义

### 3.1 当前工程目录

当前 UI 页面域包括：

- `dialog/`：Top 1 对话与 Skill 角色交互；
- `skills/`：Skill 角色目录与详情；
- `resources/`：资料与成果；
- `settings/`：API Key、遥测等设置。

不得恢复已移除的旧圆桌、智囊大厅或旧音频库兼容页面。

### 3.2 当前目标 UI 语义

Top 1 用户页面正式语义为：**对话**。

用户在对话中面对的是 **Skill 角色**：

- 角色有头像、名称、稳定身份和表达风格；
- 多个角色默认视觉平级；
- 不使用“主 Skill / 副 Skill”；
- 不默认把第二角色设计成反方；
- 用户动作写“增加 Skill 角色”，不用“邀请 Skill”；
- `@角色`/本次回复角色只影响当前请求；
- 交叉讨论必须由用户显式触发。

“议题 / 推进议题 / 阶段时间线”不得因为旧数据模型存在而继续作为 Top 1 UI 的核心心智。

跨页面共享展示组件放入 `ui/components/`；不能为了复用把业务状态或页面事件放入公共组件。

## 4. Top 1 对话页面规则

当前移动 UI 设计基准：

```text
设备：Xiaomi 14 Ultra
平台：Android
方向：竖屏
画布：1440 × 3200
语言：简体中文
```

优先级：

1. 消息内容占据绝大多数可用空间；
2. 每条 AI 消息明确属于哪个 Skill 角色；
3. 参与角色可见，但角色条高度克制；
4. 输入区支持文本、发送/停止、附件/工具、@角色；
5. “增加 Skill 角色”容易找到；
6. 联网、思考强度、资料、交叉讨论、保存成果等高级能力默认收进二级入口或 Bottom Sheet。

不得长期铺在主屏：

- Run ID / Interaction ID；
- 执行历史；
- “上下文已确认”等工程状态；
- 大型阶段时间线；
- 主/副角色标识；
- 大型策略 Override 控制卡；
- 为展示功能而常驻的全部工具按钮。

Android UI 不采用 iPhone Dynamic Island、iOS Home Indicator 等 iOS 专属视觉作为适配基准。

## 5. Skill 角色视觉与身份规则

### 5.1 允许强角色感

Skill 角色可以使用：

- 人物化头像或肖像；
- 明确姓名；
- 稳定视觉识别；
- 第一人称自然表达；
- 与其他角色明显不同的表达气质。

“不能冒充真人”不能被实现成“角色必须像工具”。

### 5.2 真实人物型角色

真实人物型 Skill 角色应在角色详情、首次使用或其他合理位置显示类似：

> AI 模拟角色，基于可获得资料构建，不代表本人，也不保证复现本人当前或完整观点。

主聊天流不要求每条消息重复免责声明。

### 5.3 平级视觉

不得通过以下方式默认建立领导关系：

- 皇冠/队长图标；
- “主 Skill / 副 Skill”标签；
- 主角色永久放大头像；
- 把其他角色缩成从属成员；
- 新增角色默认标记为“反方”。

角色排列只表达参与、选择或最近互动，不表达权力。

## 6. 主题规则

- 全局颜色值只在 `ui/theme/Color.kt` 定义。
- Typography、Shapes、Spacing 分别由 `ui/theme/` 维护。
- `LegacyUiTokens.kt` 只允许把旧语义名映射到真实主题常量，不得出现新的 `Color(...)` 值。
- 新代码优先使用 `MaterialTheme` 与语义 Token，不新增页面私有的全局调色板。
- 视觉改版应同步更新截图/Compose 测试和说明。

## 7. 导航规则

### 7.1 导航纪律

- Route 字符串/枚举改名必须同步导航测试和返回链；
- 不保留已淘汰页面的兼容 Route；
- 页面局部 Dialog、BottomSheet、Drawer 不进入全局 NavHost；
- 顶部返回与系统返回应得到一致目的地；
- 后续 IA 以“对话 Top 1”为冻结点，其他一级入口仍可继续设计。

## 8. 测试标签

当前已有 testTag 属于工程测试契约，在没有专门迁移任务时不得随意删除：

- `new_session_button`
- `chat_input`
- `send_button`
- `stop_button`
- `api_key_manager`
- `telemetry_screen`
- `app_bottom_navigation`

这些 testTag 的旧命名不代表新的用户正式术语。

当 UI 定义迁移任务明确更新语义时，可以同步迁移 testTag，但必须：

1. 同一 PR 修改调用方和测试；
2. 不保留互相冲突的两套测试入口；
3. 确保关键交互继续有语义明确的测试覆盖。

## 9. 多角色独立性 UI/状态要求

UI 和状态层不能通过默认状态制造角色主从关系。

涉及多角色回复时，必须区分：

- 当前参与角色集合；
- 本次被 @/定向的角色；
- 当前正在生成的角色；
- 交叉讨论参与者。

这些状态都不是“主角色”。

如果 UI 展示“谁正在回答”，只表示运行状态，不得长期固化为角色权力。

实现交叉讨论入口时必须由用户显式操作；不得因为用户增加了第二个角色就自动把后续会话切成互辩模式。

## 10. 修改范围纪律

- 视觉需求与底层数据/网络重构尽量分开提交；
- 不以删除功能代替 UI 重构；
- 不为新 UI 保留两套互相冲突的正式入口；
- 用户文案变更若影响测试，应同步更新测试；
- Route、Room、网络、Key、遥测和音频协议只有在任务明确授权时修改；
- 产品规则冲突时先查 ADR-009 和根 `AGENTS.md`，不要按旧 PR08 “冻结术语”逆向修改新 UI。
