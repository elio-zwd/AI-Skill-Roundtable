# PR09-16A：见域完整 UI/UX 设计、交互原型与页面状态冻结

> 状态：设计冻结，等待后续独立视觉实现 PR 消费。
>
> 设计输入：用户确认的 A+B+C 混合方案；PR08 产品契约；main@3a6668b100945a250fdb1ef3ac760144d58bb25b 的 Android 基线。
>
> 实现边界：本文件不改变 Route、ViewModel、UiState 业务语义、Room、网络、Key、遥测、音频协议或现有稳定自动化标签。

## 1. 冻结结论

见域不是聊天软件皮肤，而是让用户在持续议题中看清问题、证据、不同观点、判断与下一步的移动工作台。设计采用内容优先、低装饰的编辑层级，优先服务阅读、比较与恢复。

| 来源 | 固定用途 | 禁止范围 |
|---|---|---|
| A「开域窗」 | App Icon 几何骨架、启动页开放边界、关键焦点环与品牌主色。 | 不将窗口图形误作扫描、相机或设计工具控件。 |
| B「域界折页」 | 资料、阶段、成果与长文阅读的分栏、页签、边线与留白。 | 不把工作区做成纯笔记或阅读器。 |
| C「多视线汇聚」 | 仅用于运行中、交叉讨论汇聚、恢复完成的短时反馈。 | 不作为常驻 Logo、导航图标、背景纹理或纯装饰动效。 |

UI/UX Pro Max 指引已经应用：文本对比度目标为 WCAG AA、Android 触控目标最小 48×48dp、异步操作超过 300ms 立即有反馈、错误在操作现场提供恢复路径、动态字号与减少动效为第一等约束、Compose 保持不可变 UiState 与事件驱动导航。

技能生成的「活动/会议 Landing Page」结构不适用于持续议题移动工作台，因此没有采用；保留其高对比、明确层级、避免通用企业模板的原则，并以现有产品契约和 Material 3 为最终约束。

## 2. 工程事实与不可破坏边界

| 项目 | 当前事实 | 后续视觉实现要求 |
|---|---|---|
| 技术栈 | Kotlin 2.0.21、Jetpack Compose、Material 3、Navigation Compose 2.8.4。 | Token 落在 ui/theme；Screen 只接收不可变 UiState。 |
| 顶层导航 | 首页、议题、Skill、资料与成果；设置为二级入口。 | 不改变 AppDestination 路由、返回链或顶层数量。 |
| 主题 | 当前只有深色主题；颜色、字体、圆角与间距 Token 较少。 | 新增浅色/暗色语义 Token 时保留 LegacyUiTokens 兼容语义。 |
| 执行状态 | IssueExecutionPhase 已有 Running、Partial Success、Stopped、Recovering、No API Key、Offline、Rate Limited 等。 | 只改变呈现，不重新定义阶段、重试、停止或恢复规则。 |
| 自动化 | JianyuAutomationTags 已冻结首页、执行、协作、成果、推进、回收站等语义标签。 | 保留所有既有标签；新增标签先加测试与迁移说明。 |

本轮文件域只限 docs/design/pr-09-16a。后续实现不得顺带重做首页、成果或推进议题的业务状态机。

## 3. 页面信息架构与导航

### 3.1 产品层级

| 层级 | 对象 | 入口 | 结果 |
|---|---|---|---|
| 入口 | 问题 | 首页 | 选择方向、保存议题或获取 Skill 建议。 |
| 持续议题 | 议题 | 议题列表、首页最近议题、成果深链 | 保存阶段、资料、阵容和历史。 |
| 推进节点 | 阶段 | 工作区时间线、推进议题 | 一个明确目标，不覆盖旧阶段。 |
| 能力 | Skill | 推荐确认、Skill 目录、阵容面板 | 单 Skill 或多 Skill 协作。 |
| 证据与产出 | 资料、个人背景、草稿、正式成果 | 资料与成果、工作区、确认面板 | 可核对来源并沉淀可用结果。 |

### 3.2 导航图

> 启动与初始化 → 无 API Key 引导或首页 → 问题输入 → Skill 推荐与理由 → 资料与个人背景确认 → 最终确认 → 议题执行工作区。
>
> 首页也可「仅保存议题」→ 议题列表与详情 → 工作区。非工作区页面的底栏固定：首页、议题、Skill、资料与成果。进入议题执行工作区后，底栏退出，由聊天输入器独占底部安全区域；工作区功能菜单可打开阶段草稿/正式成果或推进议题三步面板。议题列表管理归档和回收站；设置由全局入口进入。

1. 启动页只展示必要初始化；无法使用时进入相应引导，不永久阻断本地内容访问。
2. 工作区、推荐确认、资料详情、成果详情、回收站、设置均是二级承载；系统返回等于顶部返回。工作区不显示产品级底栏，返回/收起工作区后恢复。
3. 所有 Bottom Sheet 有标题、关闭动作、焦点回归点；复杂表单在 360dp 或 200% 字号下升级为全屏页面。
4. 旧圆桌、智囊和音频库仍保持兼容性，不能被本次视觉语义静默替代。

产品级底栏采用统一的 24dp 线性矢量图标加短标签，而不是纯文字。四项分别使用首页、议题对话、Skill 星芒、资料/成果册页图形；选中态以 48×32dp 的浅色图标容器、标签字重和语义色共同表达，不能只依赖颜色。图标必须来自同一 Material Symbols Rounded 或同一向量集；不使用 emoji。工作区隐藏该底栏，聊天输入器替代其底部位置。

## 4. 核心流程

### 从问题到执行

1. 首页输入问题。空输入时主按钮禁用并说明所需内容。
2. 用户可独立选择现实支持、思维拓展，或同时选择；两者不自动拆成两条主线。
3. 获取建议后显示骨架屏。每个推荐给出角色、职责、推荐理由、风险/时效和选择状态。
4. 用户改为单 Skill 或多 Skill，可增加、删除、排序并确认职责。
5. 在资料与个人背景确认层逐项选择本次带入；敏感信息只显示范围提示。
6. 最终确认重述目标、阵容、资料、背景和输出形式；点击开始后才创建或执行运行。

### 工作区协作与恢复

1. 多 Skill 默认独立回应，每张回应卡保留状态、重试、停止后不完整输出和来源范围。
2. 点名回应在输入区上方显示「正在定向给」芯片；结束后回到默认多 Skill，不改变阵容。
3. 交叉讨论须显式选择参与者、焦点和整合者；完成后以「本次交叉讨论」分组，不伪装成一致结论。
4. 部分成功同时显示已完成和需处理成员，可原位重试失败成员或使用已完成结果继续。
5. 进程重建、网络恢复和可恢复运行只显示唯一恢复动作，不重复提交模型请求。

### 草稿、成果与推进

1. 阶段草稿的自动保存状态只能表示保存，不能误称正式成果。
2. 用户明确确认后才保存正式成果；成果页包含类型、版本、来源、所属议题和阶段。
3. 推进议题固定三步：方向 → 措施/自定义目标 → 下一阶段摘要确认。
4. 只有第三步确认后创建新阶段；未运行阶段可撤销，历史不被回写。

## 5. 全局状态体系：每个页面都必须覆盖

每个核心页面由「页面骨架 + 全局状态条 + 局部操作状态」组合覆盖下表的 14 个状态。缓存内容始终优先保留，不能用空白页替代异常。

| 状态 | 呈现 | 主要操作 | TalkBack 首次播报 |
|---|---|---|---|
| Empty | 说明没有什么、如何开始；插图不承载关键文字。 | 创建、导入或清除筛选。 | 「空状态，〈对象〉尚无内容。」 |
| Loading | 保留标题与布局，内容用固定尺寸骨架；300ms 后说明。 | 返回，不允许重复提交。 | 「正在加载〈对象〉。」 |
| Content | 正常信息层级、筛选和主任务。 | 正常。 | 页面标题和计数。 |
| Running | 顶部细状态条 + 卡内 C 汇聚反馈，不遮挡已读内容。 | 停止、查看进度。 | 「正在生成，已完成 X/Y。」 |
| Partial Success | 已完成内容和失败项并列，提供双计数。 | 重试失败项、继续使用已完成项。 | 「部分完成，X 项完成，Y 项需要处理。」 |
| Failure | 原位错误卡，说明原因、影响范围、恢复路径。 | 重试、编辑输入、查看本地内容。 | 「操作失败：〈原因〉。可用操作：〈动作〉。」 |
| Offline | 已缓存内容可读，联网动作禁用并说明原因。 | 查看本地内容、稍后重试。 | 「当前离线，已显示本地内容。」 |
| No API Key | 非破坏性执行资格卡，不阻断本地浏览。 | 去设置添加 Key。 | 「尚未配置可用 API Key。」 |
| Rate Limited | 标记受影响运行；不承诺不可靠倒计时。 | 仅实际允许的重试。 | 「服务暂时限流，尚未重复提交。」 |
| Canceled | 明确是用户取消，已完成内容保留。 | 保留、重新开始。 | 「操作已取消。」 |
| Stopped | 显示不完整边界和已保留内容，不改写为失败。 | 继续、重试或查看结果。 | 「生成已停止，部分内容已保留。」 |
| Recovering | 上次运行摘要 + 单一恢复进度，不能看似新运行。 | 取消恢复、查看历史。 | 「正在恢复上一次运行。」 |
| Restored | 一次性成功提示，焦点回到恢复对象。 | 打开对象。 | 「已恢复〈对象〉。」 |
| Unavailable | 说明不可用能力和原因，不误报成网络或 Key 问题。 | 更换模型/Skill、去设置或返回。 | 「〈能力〉当前不可用：〈原因〉。」 |

状态优先级为：不可恢复风险 > 本地恢复 > 无 Key/离线/不可用 > 运行 > 内容。高优先级不遮盖危险确认；较低优先级保留在对应卡片。

## 6. 页面规格

| 页面 | 高保真承载与主要交互 | 非理想状态答案 |
|---|---|---|
| 启动与初始化 | A 开放边界几何、应用名和「正在准备你的本地工作区」；无无限全屏动画。 | 存储不可用、恢复失败、版本不兼容分别说明；提供重试/安全退出/影响范围，绝不默认建议清空数据。 |
| 首页问题输入 | 可增长多行输入、可见标签、清空按钮；现实支持青绿卡与思维拓展紫色卡等权；主「获取建议」、次「仅保存议题」、近期议题。 | 推荐 Loading 固定高度骨架；Failure 保留问题和方向；No API Key 仅替换模型操作，「仅保存议题」始终可用。 |
| Skill 推荐与理由 | 推荐摘要 + 可编辑阵容。每卡：名称/类型、职责、推荐理由、适用条件/风险/时效、选择/排序/移除。 | 不可用卡保留并说明原因和替换建议，不能无解释消失；所有状态保留用户选择。 |
| 单/多 Skill 阵容 | 单 Skill 显示「邀请更多 Skill」；多 Skill 显示数量、顺序、职责、调整入口。人物 Skill 有 AI 模拟视角提示；去AI化助手有诚信边界。 | 目录不可用、限流或离线时保持当前阵容与编辑草稿。 |
| 资料/个人背景确认 | 「本次资料、个人背景、敏感项」分段，项有来源、时间、摘要、敏感标识、带入复选；底部汇总。 | 失败、离线、恢复保留选择草稿；冲突只提示需要重新确认的项目。 |
| API Key/模型不可用 | 可复用执行资格卡，明确本地内容仍可用；前往设置为主操作。 | Key 全不可用仅显示数量和非敏感原因；模型不可用不能暗中换模型；离线说明缓存边界。 |
| 议题执行工作区 | Conversation-first：顶部仅保留议题/阶段、阵容计数和功能菜单；消息流是唯一主体；聊天输入器固定在底部安全区域，工作区不显示产品底栏。≥600dp 可有辅助栏，长文最大 720dp。 | 运行、部分成功、停止、恢复、限流、无 Key 都压缩为消息流上方的一行状态轨，不挤占对话高度。 |
| 多 Skill 独立回应 | 每条回应采用聊天消息组：左侧为 Skill 身份和短职责，右侧为不换字的「AI 生成」与运行状态标识，随后是气泡正文、来源/反馈操作；不使用大面积功能卡。 | 失败成员不清空成功消息；部分成功在状态轨给出完成/失败计数。 |
| 点名回应 | 固定输入器顶部显示可移除的「定向给某 Skill」上下文；点名入口收在输入器的加号操作面板。 | 完成后回到常规阵容；点名失败只重试该运行。 |
| 交叉讨论 | 输入器的加号操作面板发起；消息流内明确分组显示参与者、焦点、整合者、成功/失败成员与综合状态。 | 不能显示成全体共识；可重试失败成员或综合步骤。 |
| 阶段草稿 | 顶部显示未保存、保存中、已保存版本、失败、冲突；正文编辑保持内容。 | 保存失败保留编辑内容，冲突给重新加载/合并动作。 |
| 正式成果 | B 式页眉显示类型、版本、来源、议题、阶段、更新时间；复制、来源、修订。 | 确认中阻止重复提交；确认成功后才改称正式成果。 |
| 推进议题 | 全屏三步，顶部步骤和当前名称，底部上一步/继续：方向 → 措施/目标 → 摘要确认。 | 第三步才创建；失败保留选择；运行中先选择等待完成或停止当前运行。 |
| 议题列表与详情 | 顶部只显示「议题」和设置入口；紧随其后是进行中、已归档、回收站三个可切换页签。页签下直接列出标题、阶段、更新时间、运行/恢复、成果，不显示产品模型术语或重复大标题。 | 空/加载/失败/离线都保留当前页签和区段标题，避免误认为内容被删除。 |
| 归档与回收站 | 运行中归档只给「等待完成后归档」或「停止生成并归档」；回收站无自动过期。 | 恢复保留连续历史；彻底清除双确认，默认焦点不放最终确认。 |
| 资料库与成果库 | 产品级「资料与成果」内有资料/成果页签和个人背景分段，不新增第五个底栏入口。 | 来源失效不等于删除副本；离线说明外部链接不可访问；失败保留筛选。 |
| Skill 目录 | 搜索、筛选、收藏、组合、详情；身份信息不只依赖头像。 | 不可用 Skill 仍可发现，显示原因与替代/返回。 |
| 设置 | 外观与无障碍、模型与 API Key、遥测与诊断、数据与恢复按风险升序。 | Key 只显示掩码和既有验证状态；删除/清空/导入保持确认语义。 |

### 工作区聊天核心布局冻结

工作区的第一任务是「读、追问、比较和继续这段 AI 对话」，不是展示所有功能入口。后续 Android 实现必须以消息流为中心，使用 LazyColumn 承载长会话；不得用一组可无限增长的 Column 卡片模拟聊天。

| 区域 | 固定位置 | 内容与行为 |
|---|---|---|
| 顶部上下文条 | 工作区顶端，56dp 内。 | 返回、短议题标题、当前阶段、阵容数量和更多菜单。标题过长省略但 TalkBack 读完整；不重复展示大标题、方向卡和阶段卡。 |
| 消息状态轨 | 消息流顶部，仅在有意义时显示。 | Content 不显示「内容可用」提示；Running、Partial Success、Stopped、Recovering 等用一行文字、数量和轻量动作表达。停止和重试不能做成高卡片。 |
| 对话流 | 视觉和滚动主体。 | 用户消息右侧，Skill 消息左侧。每条 Skill 消息必须显示名称、职责、AI 生成标识、正文与来源范围；功能反馈为轻量图标/文字，不出现占满宽度的大卡。 |
| 固定输入器 | 工作区底部安全区域，位于 IME 之上。 | 永远可见标签「继续当前阶段」，多行输入与发送在同一行；发送占据固定 48dp 命中区。输入获得焦点时列表仍可滚动到最近消息。 |
| 输入器操作面板 | 加号按钮触发，悬浮于输入器上方。 | 点名回应、交叉讨论、带入资料等按需打开；默认收起，避免常驻按钮条抢占对话空间。打开后 TalkBack 先读面板标题，关闭后焦点回加号。 |
| 工作区更多菜单 | 顶部更多按钮触发。 | 整理阶段草稿、确认成果、推进议题、查看资料、归档和停止等非连续发言动作都在此处；不能固定在消息流底部。 |
| 底部导航 | 工作区隐藏。 | 聊天输入器独占底部位置；离开工作区后恢复首页、议题、Skill、资料与成果的产品级底栏。 |

工作区的 Content 状态不显示状态卡。只有 Running、Partial Success、Stopped、Recovering、Offline、No API Key、Rate Limited、Unavailable 等会影响下一步的状态，才以状态轨或与受影响消息相邻的行内组件显示。这样正常对话从第一条消息开始，不被「系统已经正常」的信息打断。

在 360dp 与 200% 字号下，顶部上下文条允许两行但不超过 96dp；输入器可增长为 2–5 行且不被键盘遮盖；加号操作面板升级为全屏 Bottom Sheet。TalkBack 工作区阅读顺序改为：短议题上下文 → 有效状态轨 → 用户消息与 Skill 消息的时间顺序 → 固定输入器 → 输入器打开的操作面板。产品级底栏不参与该页面的阅读顺序。

## 7. Design Token

所有页面引用语义 Token；组件不能散落硬编码颜色。下列是后续 Compose 视觉实现的候选冻结值。

| Token | 浅色 | 暗色 | 用途 |
|---|---:|---:|---|
| color.brand.primary | #4F46E5 | #A5B4FC | 主操作、选中、焦点。 |
| color.brand.primaryStrong | #4338CA | #C7D2FE | 按下/高对比强调。 |
| color.brand.container | #EEF2FF | #29285A | A 开放边界容器。 |
| color.direction.practical | #0F766E | #5EEAD4 | 现实支持标签与路径。 |
| color.direction.perspective | #6D28D9 | #C4B5FD | 思维拓展标签与假设。 |
| color.surface.canvas | #F8FAFC | #10131A | 页面背景。 |
| color.surface.base | #FFFFFF | #171B24 | 主卡/Sheet。 |
| color.surface.raised | #FFFFFF | #252C39 | 悬浮卡、输入区。 |
| color.text.primary | #111827 | #F8FAFC | 标题/正文。 |
| color.text.secondary | #475569 | #CBD5E1 | 元信息。 |
| color.border.default | #CBD5E1 | #334155 | B 编辑边线。 |
| color.status.success | #047857 | #5EEAD4 | 已完成；不单独依赖颜色。 |
| color.status.warning | #A15C00 | #FBBF24 | 注意、部分成功、限流。 |
| color.status.danger | #B42318 | #FDA4AF | 失败、不可恢复动作。 |
| color.status.info | #1D4ED8 | #93C5FD | 恢复、来源信息。 |

实际实现必须检查普通文字 4.5:1、非文本和大文字 3:1；不满足的颜色组合不能提交。

| 分类 | Token | 值/规则 |
|---|---|---|
| 字体 | type.display / headline / title | 32/40sp、24/32sp、20/28sp；display 只用于首页或成果标题，最多两行。 |
| 字体 | type.body / label / meta | 16/24sp、14/20sp、12/16sp；正文不小于 14sp。 |
| 字体家族 | type.family | Android sans-serif 为默认；正式嵌入字体先完成许可和离线包体评估。 |
| 间距 | space | 4、8、12、16、24、32、40、48dp；360dp 横向 16dp，≥600dp 为 24dp。 |
| 圆角 | radius | 8、12、16、24dp；卡片默认 16dp，输入 12dp，芯片 999dp。 |
| 阴影 | elevation | 0、1、2 级；优先留白与边线。 |
| 图标 | icon | 18、20、24dp；Material Symbols Rounded 或单一向量图标集，禁止 emoji 结构图标。 |

组件规则：主按钮在进行中保持宽度、显示短进度并禁止重复提交；输入始终有可见标签，最小高 56dp，错误在字段邻近；所有点击项最小 48dp 命中区；Skill 身份按名称、类型、职责、可用性、风险/边界和选择状态表达。

## 8. 无障碍、适配与动效

| 规则 | 冻结要求 |
|---|---|
| 360dp | 单列、16dp 横向边距；横向滚动只允许短芯片/时间线，不能承载必读正文或主操作。 |
| 412dp | 可显示更宽行动组；卡片仍不做双列文字阅读。 |
| ≥600dp | 工作区可加辅助栏；正文最大宽度 720dp。 |
| 200% 字号 | 卡片纵向增长；按钮文字可两行但不截断；Sheet 有全屏替代；停止、保存、确认不能被隐藏。 |
| TalkBack 顺序 | 普通页面：标题 → 全局状态 → 内容列表 → 主操作 → 次操作 → 底栏。工作区按聊天核心布局的专用顺序；弹窗关闭后回触发点。 |
| 状态播报 | 错误、停止、部分成功、恢复完成只在变化时播报；流式正文不逐字符播报。 |
| 手势替代 | 滑动、长按、拖拽都有命名按钮替代。 |
| 微交互 | 150–220ms，透明度、颜色、轻微 elevation，不改变布局边界。 |
| 面板与页面 | 220–300ms，遵循 Android 返回方向。 |
| C 运行反馈 | 三线汇聚图标 180ms 一次，低对比、只解释状态。减少动效时变静态进度图标/文字。 |
| 禁止 | 粒子爆炸、头像旋转、逐字闪烁、超过 500ms 的装饰等待、用动画隐藏失败。 |

## 9. 自动化标签规划

JianyuAutomationTags 及 Legacy 标签全部保留。以下只是后续建议，必须与实际 Compose UI Test 同 PR 引入，只接受稳定内部 ID，不能拼入标题、Skill 名称、消息正文或用户数据。

| 页面/功能 | 保留标签示例 | 后续新增建议 |
|---|---|---|
| 首页与推荐 | home_question_input、home_recommendation_result、home_recommendation_failure | recommendation_reason、recommendation_availability、recommendation_roster_editor |
| 上下文确认 | context_confirmation_dialog、context_confirmation_confirm | context_sensitive_notice、context_included_count |
| 工作区 | issue_execution_status、issue_execution_stop、issue_execution_retry | workspace_global_status、workspace_mode_independent、workspace_mode_directed、workspace_mode_cross |
| 协作 | directed_response_dialog、cross_discussion_status、cross_discussion_retry_failed | directed_response_scope、cross_discussion_summary_scope |
| 草稿/成果 | stage_draft_saving、stage_draft_conflict、artifact_confirmation_dialog | artifact_formal_status、artifact_source_scope |
| 推进议题 | advance_direction_step、advance_summary_step、advance_confirm | advance_step_indicator、advance_created_undo_notice |
| 生命周期 | issue_archive_dialog、issue_purge_final_confirm | issue_lifecycle_state、issue_restored_notice |
| 执行资格 | settings_api_keys_entry、api_key_manager | execution_eligibility_notice、model_unavailable_notice |

命名格式固定为「页面域_对象或动作_状态」，不以可见中文文案作为测试选择器。

## 10. 页面到领域对象映射

| 设计承载 | 现有实现来源 | 已可消费状态 | 实现限制 |
|---|---|---|---|
| 首页、方向、推荐 | HomeUiState、HomeWorkflowState、ContextConfirmationUiState。 | question、recommendationVisible、contextConfirmation、finalReviewVisible。 | 不改变获取推荐、仅保存议题与最终开始顺序。 |
| 议题与阶段 | IssueEntity、StageEntity、IssuesUiState、IssueLifecycleUiState。 | lifecycleState、currentStage、可恢复运行计数。 | 只重排视觉，不定义新生命周期。 |
| 执行 | IssueExecutionUiState、IssueExecutionPhase、ExecutionRunEntity、ExecutionParticipantStateEntity。 | Loading、Failure、Content；READY、RUNNING、PARTIAL_SUCCESS、STOPPED、RECOVERING、NO_API_KEY、OFFLINE、RATE_LIMITED 等。 | 不改变预算、停止、重试或恢复资格。 |
| 单/多 Skill 协作 | IssueCollaborationUiState、DirectedResponseRunUi、CrossDiscussionSessionUi。 | 阵容、点名、交叉讨论、失败成员、综合重试。 | 点名结束不退出多 Skill；交叉讨论不伪装为共识。 |
| 推进议题 | AdvanceIssueUiState、AdvanceIssueDraft、StageAdvancementEntity。 | 方向、措施、资料、成果、阵容、撤销资格。 | 只有摘要确认创建新阶段。 |
| 草稿/成果 | StageResultUiState、StageDraftSaveStatus、StageArtifactConfirmationStatus。 | 草稿保存、失败、冲突、确认成果。 | 自动保存草稿不显示成正式成果。 |
| 资料/背景/成果库 | ResourcesUiState、ArtifactLibraryUiState、MaterialUiItem、PersonalContextUiItem。 | 来源、敏感性、生命周期、筛选、深链。 | 不在视觉层扩大资料持久化或来源承诺。 |
| Skill 目录 | OfficialSkillCatalogUiState、OfficialSkillDefinition、组合编辑状态。 | 加载、搜索、筛选、收藏、组合、目录错误。 | 不改变官方 Skill 可用性与治理规则。 |
| API Key/设置 | ApiKeyManagerUiState、SettingsTone、TelemetryUiState。 | 可用 Key 数、掩码状态、确认、诊断。 | 不显示真实 Key，不修改 Key 池策略。 |
| 启动、模型不可用 | 当前无独立全局启动 UiState；执行页已有 executionAvailable 和失败信息。 | 执行资格、局部失败。 | 如需全局初始化状态，先独立定义不可变 UiState 与测试。 |

## 11. 后续实现验收与待办

后续视觉实现 PR 必须：逐页检查浅/暗主题、360dp、412dp、≥600dp、1.3×/2.0× 字号；让首页、推荐、上下文、工作区、草稿、推进、议题、资料/成果、Skill、设置覆盖第 5 节所有状态；用 TalkBack 验证标题、状态、Skill 身份、运行计数、错误恢复、危险确认与焦点回归；保留既有稳定 testTag，并给新关键动作添加标签与 UI Test；按实际改动执行编译、单测、Lint、assemble、设备/模拟器和秘密检查。

| 项目 | 当前状态 | 后续动作 |
|---|---|---|
| 最终 Logo / Adaptive Icon | 方向已定，正式资产未核验。 | 商标、商店相似度、字体许可与 Android 掩膜测试。 |
| 亮色主题 | Token 已定，生产代码尚无亮色主题。 | 主题实现时验证切换不丢状态。 |
| 启动全局状态 | 设计已给出承载，当前无专用 UiState。 | 如确有必要，先提交小范围状态契约与测试。 |
| 运行状态可视化 | 大部分已有状态可映射。 | 对照 errorCode 与 availability 字段逐项做 Compose 测试。 |
| 原型 | 可交互说明稿，不是生产前端或 Android 代码。 | 以本文件和真实 UiState 为准，原型不成为业务真相来源。 |
