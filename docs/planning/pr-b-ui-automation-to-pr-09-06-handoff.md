# PR-B → PR09-06 UI 自动化契约交接

## 1. 交接结论

PR-B 只冻结当前真实存在的导航壳、页面根节点、资料/个人背景分区、执行参与者区域和上下文错误区域。

PR09-06 实现首页真实业务时，必须复用：

```text
JianyuAutomationTags.App
JianyuAutomationTags.Navigation
JianyuAutomationTags.Shell
JianyuAutomationTags.Screen
JianyuAutomationTags.Context
```

不得复制一套新的首页自动化常量体系，不得修改 PR-B 已冻结标签的字符串值。

## 2. PR-B 已冻结的首页相关契约

```text
jianyu_app_content_root
app_bottom_navigation
app_destination_home
home_screen
global_settings_button
```

这些标签只描述稳定节点职责，与中文文案无关。PR09-06 可以重做首页内容，但必须保留 `home_screen` 作为唯一首页根节点。

## 3. 临时占位标签的处理

当前标签：

```text
home_question_placeholder
```

性质：

- 只服务 PR09-06 前的占位卡片；
- 不在 `JianyuAutomationTags.frozenStaticTags` 中；
- 不能被新测试当作长期输入框或推荐入口；
- PR09-06 删除占位卡片时可以同时删除该标签；
- 删除前应迁移仍引用它的测试。

## 4. PR09-06 建议新增标签

以下名称是交接约定，不代表 PR-B 已经实现对应节点。只有真实 UI 节点落地并有测试后，才加入冻结清单。

### 4.1 问题输入

```text
home_question_input
```

要求：

- 标签放在真实可编辑输入节点上；
- 不把问题正文、标题或摘要拼入标签；
- 输入为空、非空、恢复后均保持同一标签；
- 输入内容不得出现在证据文件名或动态 `resource-id` 中。

### 4.2 发起推荐

```text
home_recommendation_request_button
```

要求：

- 标签放在真实可点击按钮上；
- disabled 状态仍保持同一标签；
- 不以“发送”“下一步”等可见文案作为选择器。

### 4.3 推荐结果区域

```text
home_recommendation_result
```

要求：

- 空态、加载态、失败态和成功态由子状态标签区分时，根标签保持稳定；
- 推荐理由、Skill 名称、用户问题不得拼入标签；
- 推荐项如需动态标签，只允许稳定 Skill ID。

可选动态格式：

```text
home_recommendation_skill_<stableSkillId>
```

调用方必须通过 `normalizedStableId` 门禁。

### 4.4 确认推荐

```text
home_recommendation_confirm_button
```

要求：

- 只有用户主动确认才推进；
- 标签放在执行确认动作的按钮上；
- 不与“重新推荐”“取消”共用标签。

### 4.5 打开上下文确认

```text
home_context_confirmation_button
```

打开后复用 PR-B 已冻结的上下文契约：

```text
context_confirmation_dialog
context_confirmation_total
context_confirmation_validation_errors
context_confirmation_confirm
context_confirmation_cancel
```

不得为首页复制 `home_context_dialog` 等第二套同义标签。

## 5. 动态标签安全规则

允许：

```text
home_recommendation_skill_official-skill-id
```

禁止：

```text
home_recommendation_skill_法律顾问
home_question_我是否应该辞职
home_recommendation_<用户姓名>
```

所有动态部分必须满足：

- 来自稳定内部 ID；
- 只含 ASCII 字母、数字、点、下划线、连字符；
- 长度不超过 128；
- 不包含标题、正文、姓名、邮箱、手机号或其他隐私内容；
- 不同节点类型使用不同固定前缀，避免碰撞。

## 6. PR09-06 必补测试

至少新增：

1. 首页真实输入框存在、唯一、可编辑；
2. 问题文案改变不会改变标签；
3. 推荐按钮 disabled/enabled 时标签不变；
4. 推荐请求不会因为页面重组重复触发；
5. 推荐结果根节点在加载/失败/成功间保持稳定；
6. 确认推荐必须由用户点击触发；
7. 上下文确认复用 PR-B Dialog 标签；
8. 动态 Skill 标签拒绝不安全 ID；
9. Activity 重建后草稿与当前步骤恢复；
10. UIAutomator 能通过 `tag` 定位真实输入、推荐确认和上下文确认入口。

## 7. 禁止事项

PR09-06 不得：

- 修改 `app_destination_home`、`home_screen` 或 `global_settings_button`；
- 把中文文案升级为自动化主选择器；
- 用问题正文生成动态标签；
- 让 `home_question_placeholder` 继续代表真实输入框；
- 为同一节点同时保留新旧两个长期标签；
- 为通过测试而降低唯一性、点击性或恢复断言；
- 在 UI 测试中调用生产网络。
