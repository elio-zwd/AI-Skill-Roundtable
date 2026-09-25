# 用户自定义头像 Design / Spec

日期：2026-09-24  
状态：Approved  
分支：\`codex/user-avatar-upload\`  
基线：\`codex/skill-role-avatars\`

## 1. 目标

允许用户在「我的」页手动选择一张本地图片作为自己的头像，并让该头像在：

- 「我的」个人背景卡；
- 对话中的用户消息头像；

保持一致、即时刷新，并在 App 重启后继续存在。

第一版同时提供「恢复默认头像」。

## 2. 产品边界

本 Spec 明确覆盖并替代 \`docs/superpowers/specs/2026-09-12-ui-01-mine-navigation.md\` 中「UserAvatar 只允许固定 \`R.drawable.avatar_user\`、不提供自定义图片源」的旧限制。其余 UI-01 契约继续有效。

本功能只处理**用户自己的头像**，不修改 Skill 角色头像体系。

## 3. 用户流程

### 3.1 入口

「我的」→「个人背景」卡片左侧头像右下角铅笔按钮。

铅笔按钮从“不可用占位”升级为正式可点击入口。

### 3.2 操作 Sheet

点击铅笔后展示 Material 3 Bottom Sheet：

- 从相册选择
- 恢复默认头像（仅存在自定义头像时显示或启用）
- 取消

### 3.3 选择图片

使用 Android \`ActivityResultContracts.PickVisualMedia\` 选择单张图片。

不申请：

- \`READ_MEDIA_IMAGES\`
- \`READ_EXTERNAL_STORAGE\`

不保存 Photo Picker 返回的外部 \`content://\` URI 作为长期事实。

## 4. 持久化与文件协议

### 4.1 正式文件位置

自定义头像固定保存到 App 私有目录：

\`filesDir/user-profile/avatar.jpg\`

临时写入使用同目录临时文件或 \`AtomicFile\`，成功后才发布正式文件。

### 4.2 不进入 Room

用户头像不新增：

- Room Entity；
- Room Migration；
- Schema 字段。

当前只有一个用户头像槽位，固定文件路径已经能表达完整状态。

### 4.3 不进入现有备份

当前 \`backup_rules.xml\` / \`data_extraction_rules.xml\` 只 include SharedPreferences；\`filesDir/user-profile/avatar.jpg\` 不进入当前系统备份。

现有可移植备份白名单也不包含用户头像。

因此 V1 语义明确为：

> 自定义头像是本设备本地展示偏好；重装、换设备或当前备份恢复后可以回到默认头像。

本任务不扩大 PR09-13 备份协议。

## 5. 图像导入协议

选择图片后 Repository 在后台线程完成：

1. 打开外部 URI；
2. 读取图片尺寸；
3. 按需要下采样，避免直接解码超大原图；
4. 读取 EXIF orientation；
5. 修正常见 90° / 180° / 270° 方向；
6. 以中心为基准裁成 1:1；
7. 缩放为 512×512；
8. 将透明区域合成到稳定背景后输出 JPEG；
9. JPEG quality 约 88–92；
10. 原子写入 \`filesDir/user-profile/avatar.jpg\`；
11. 发布头像变更通知。

选择失败、解码失败或写入失败时：

- 保留原有自定义头像；
- 不删除已有文件；
- UI 提示失败；
- 不把错误状态伪装成成功。

## 6. Repository

新增：

\`app/src/main/java/com/elio/jianyu/data/UserAvatarRepository.kt\`

职责：

- \`observeAvatar()\`
- \`importAvatar(uri)\`
- \`resetToDefault()\`
- 固定私有文件读写
- EXIF / crop / scale / compress
- 进程内变更通知

不保存外部 URI。

### 6.1 同步模型

Repository 使用进程内共享 revision/change flow 通知多个实例。

原因：

- MineRoute 与 DialogRoute 可以各自拥有轻量 Repository 实例；
- 任一实例完成 import/reset 后，另一个 Route 的观察流立即收到变化；
- 进程重启后通过固定私有文件重新加载；
- 不需要为了通知而引入 SharedPreferences 持久化状态。

## 7. UI 分层

### 7.1 Route

\`MineRoute\`：

- 创建/记忆 UserAvatarRepository；
- 收集头像 Snapshot；
- 注册 Photo Picker；
- 处理 import/reset side effect；
- 管理 Bottom Sheet；
- 把当前自定义头像提供给 MineScreen。

\`DialogRoute\`：

- 收集同一用户头像 Snapshot；
- 把当前自定义头像提供给 Dialog UI。

### 7.2 Screen / Component

\`MineScreen\`：

- 铅笔按钮变为可点击；
- 只通过 callback 告知 Route 用户要编辑头像；
- 不直接访问 ContentResolver / filesDir。

\`UserAvatar\`：

- 自定义头像存在时优先显示；
- 否则继续使用内置 \`R.drawable.avatar_user\` fallback；
- fallback 保留当前用于裁掉默认资源白底的显示规则；
- 自定义头像不额外使用默认头像的 1.5x zoom。

为了避免把头像 Bitmap 参数一路穿过所有 Dialog message component，可使用 UI 层 \`CompositionLocal\` 只承载当前已解码的 \`ImageBitmap?\`。CompositionLocal 只保存展示状态，不做文件 IO。

## 8. 自动化标签

旧：

\`AVATAR_SWITCH_UNAVAILABLE\`

替换为正式入口：

\`AVATAR_EDIT_BUTTON\`

不保留旧“不可用”测试契约。

新增 Bottom Sheet 相关 test tag：

- avatar action sheet
- pick image action
- reset default action

## 9. 错误与恢复

### 9.1 选择取消

用户取消系统 Photo Picker：

- 无状态变化；
- 不 Toast 错误。

### 9.2 非法/损坏图片

- import 失败；
- 保留当前头像；
- Toast：无法读取所选图片，请换一张重试。

### 9.3 写入失败

- 保留旧头像；
- Toast：头像保存失败，请重试。

### 9.4 恢复默认

- 删除正式自定义头像文件；
- 发布变更；
- Mine 与 Dialog 同时回退到默认头像。

## 10. 明确不做

V1 不做：

- 手势拖动/缩放裁剪器；
- 多头像历史；
- 云头像；
- 账号头像；
- 头像同步；
- 把头像加入现有备份；
- 保存外部相册 URI；
- 读取用户整套相册；
- 人脸检测或 AI 自动美化；
- 修改 Skill 角色头像。

## 11. 验收标准

### 功能

- 铅笔按钮可点击；
- 可通过系统 Photo Picker 选择一张图片；
- 选择后「我的」头像立即更新；
- 对话用户消息头像同步更新；
- App 重启后头像仍存在；
- 恢复默认后两处同时恢复内置头像；
- 取消选择不改变头像；
- 非法图片不破坏已有头像。

### 图片结果

- 最终文件 512×512；
- 正方形；
- 无明显方向错误；
- 不长期持有外部 URI；
- 不依赖相册读取权限。

### 工程

- 不新增 Room Migration；
- 不修改 Skill 头像系统；
- 不扩大当前备份协议；
- Screen/Component 不执行持久化 IO；
- 本地 AI 完成 Gradle 与真机只读验收。
