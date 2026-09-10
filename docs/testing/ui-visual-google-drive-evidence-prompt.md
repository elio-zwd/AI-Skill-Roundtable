# 见域 UI 视觉证据｜本地 AI → Google Drive 归档 Prompt

> 用途：让本地验收 AI 将仓库 UI 参考图、模拟器/真机截图和每轮验收摘要稳定上传到 Google Drive，供远端开发 AI 后续直接读取原图并做视觉校准。
>
> 本流程只收集/上传证据，不修改源码、测试、Gradle、产品文档，不 commit/push/merge。

## 目标 Drive 根目录

优先复用；不存在则创建：

`AI-Skill-Roundtable-UI-Visual-Evidence`

目录结构：

```text
AI-Skill-Roundtable-UI-Visual-Evidence/
├─ 00-reference-ui/
│  └─ repo-ui-images/
│     └─ <保留 docs/product/重构/UI界面/ 下原始相对目录>
├─ 10-runtime-screenshots/
│  └─ UI-02/
│     └─ <YYYYMMDD-HHmm>__<short-head>/
│        ├─ 00-environment/
│        ├─ 01-role-catalog/
│        ├─ 02-role-category/
│        ├─ 03-role-detail/
│        ├─ 04-conversation-bridge/
│        └─ 90-known-misroute-or-errors/
└─ 20-reports/
   └─ UI-02/
      └─ <YYYYMMDD-HHmm>__<short-head>/
         ├─ screenshot-index.md
         └─ acceptance-summary.txt
```

每次验收创建新的 run 文件夹，禁止覆盖旧 run。

## 1. 工作区门禁

在仓库根目录执行：

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
```

记录 branch / HEAD。源码工作区必须 clean。

允许把临时截图/元数据写到被 Git 忽略的：

`app/build/ui-visual-evidence/<run-id>/`

不得写入受版本控制源码路径。

## 2. 上传仓库 UI 原始参考图

扫描：

`docs/product/重构/UI界面/`

递归收集其中所有 `.png` / `.jpg` / `.jpeg` / `.webp`。

要求：

- 按仓库原始相对目录上传到 `00-reference-ui/repo-ui-images/`；
- 保留原文件名；
- 不截图代替原文件；
- 不裁剪、不缩放、不压缩、不转码；
- 同名且 SHA256 相同可跳过上传；不同则保留新版本，不静默覆盖；
- 至少确保 UI-02 两张选定参考图存在：
  - `01-角色主页面-A-推荐优先.png`
  - `02-角色主页面-B-分类目录.png`

生成参考图索引，至少记录：仓库相对路径、文件名、像素尺寸、SHA256、Drive 文件 URL/ID。

## 3. 记录模拟器环境

执行并保存到 `00-environment/environment.txt`：

```powershell
adb devices
adb shell wm size
adb shell wm density
adb shell settings get system font_scale
adb shell settings get system user_rotation
```

同时记录：

- Git branch
- Git HEAD
- 当前日期时间
- emulator/device serial
- Android API/version（可读取时）
- 是否默认字体/默认显示缩放

UI-02 优先使用 1080×2400 竖屏模拟器。它与 Xiaomi 14 Ultra 1440×3200 都是 9:20，可用于主布局比例迭代；真机差异留给发布前 Insets/字体/厂商系统行为抽验。

## 4. UI-02 正确入口门禁

当前 UI-01 尚未合入时，一级底栏仍可能显示：

`首页 / 议题 / Skill / 资料与成果`

**UI-02【角色】主页面必须从一级底栏 `Skill` 进入。**

进入后必须先确认页面出现：

- `Skill 角色`
- `和不同的思考方式对话`
- 搜索框 `搜索角色、能力或问题`
- 分类 Chip

不要把【对话】页里的“增加 Skill 角色”BottomSheet 当成 UI-02 主页面。

若看到文案：

`没有找到匹配的 Skill 角色`

且界面是 BottomSheet，应标记为 `legacy-dialog-role-picker`，放入 `90-known-misroute-or-errors/`，不要把它当作 UI-02 主页面截图。

## 5. 当前 UI-02 必拍截图

使用原始 `adb screencap` PNG，不裁剪/缩放；建议文件名如下：

```text
01-role-all-top.png
02-role-all-after-scroll.png
03-role-category-life-tools.png
04-role-search-meeting-to-action.png
05-role-detail-person-top.png
06-role-detail-person-bottom-actions.png
07-role-detail-meeting-to-action.png
08-start-new-meeting-to-action-dialog.png
09-add-current-study-planner-dialog.png
10-recent-after-successful-use.png
11-search-empty-state.png
```

如果某一步无法到达，也要保留失败画面并在文件名加 `FAIL-` 前缀，不伪造正常截图。

截图至少覆盖：

1. `全部` 顶部：标题、搜索、8 分类、推荐区；
2. `全部` 下方：最近使用（若真实存在）与全部角色；
3. `生活工具` 分类；
4. 在 UI-02 主页面搜索 `meeting-to-action`；
5. 真人角色详情；
6. 详情底部两个会话动作；
7. `meeting-to-action` 功能角色详情；
8. 成功“开始新对话”后的 Dialog participant；
9. 成功向现有会话加入 `study-planner`；
10. 成功使用后的 recent；
11. 搜索无结果状态。

## 6. 旧误入路径截图也保留

若本地已经有历史截图，例如：

- `03-role-catalog-initial.png`
- `09-search-meeting-to-action.png`
- `11-role-detail-card-tap.png`

不要删除。若确认其实来自【对话】里的旧增加角色 BottomSheet，则上传到：

`90-known-misroute-or-errors/legacy-dialog-role-picker/`

并在 index 中注明“不是 UI-02 一级角色页，仅作为误入路径/兼容缺口证据”。

## 7. screenshot-index.md

为每张 runtime 图记录：

```text
- file: 04-role-search-meeting-to-action.png
  screen: UI-02 / Role catalog / Search
  action: 一级底栏 Skill → 搜索 meeting-to-action
  expected: 命中会议纪要与行动项助手
  actual: <一句话>
  result: PASS | FAIL | BLOCKED
```

不要写长篇 UI 分析；远端 AI 会直接看原图。

## 8. acceptance-summary.txt

若本轮已有本地验收结果，将结构化摘要原样上传，不上传整份 Gradle/logcat 正常日志。

## 9. Google Drive 上传要求

使用已连接的 Google Drive 插件直接完成文件夹创建和上传。

要求：

- 普通 PNG/JPG 按原始文件上传；
- 保留层级和文件名；
- 上传后读取/核对 Drive 元数据，确认文件存在；
- 不生成公开分享权限；保持用户 Drive 默认私有权限；
- 返回实际观察到的 Drive URL/ID，不猜 URL。

## 10. 完成回报

最后再次执行：

```powershell
git status --short
git rev-parse HEAD
```

工作区必须仍 clean，HEAD 不得变化。

只回报：

```text
UI VISUAL DRIVE EVIDENCE
BRANCH: <branch>
HEAD: <sha>
WORKTREE_AFTER: CLEAN / DIRTY
DRIVE_ROOT: <actual folder url/id>
REFERENCE_FOLDER: <actual folder url/id>
LATEST_RUNTIME_FOLDER: <actual folder url/id>
LATEST_REPORT_FOLDER: <actual folder url/id>
REFERENCE_IMAGE_COUNT: <n>
RUNTIME_SCREENSHOT_COUNT: <n>
SCREENSHOT_INDEX: <actual url/id>
ACCEPTANCE_SUMMARY: <actual url/id or none>
UPLOAD_ERRORS: <none or concise list>
```

不要修改任何代码来“配合截图”。
