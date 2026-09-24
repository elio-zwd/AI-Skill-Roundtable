# Skill 角色视觉资源审查状态

日期：2026-09-23  
分支：`codex/skill-role-avatars`  
PR：#67

## 视觉契约

- 官方 Skill：44
- 人物头像：38
- 工具视觉：6
- 工具型边界：仅 `OfficialSkillPrimaryType.WORKFLOW_CAPABILITY`
- 正常生产状态不得依赖两个字/文字块 fallback
- 图片内不得依赖角色名文字或品牌 Logo 表达身份

## 现有 20 张头像审查

共同问题：现有资源大多把“圆形底板 + 圆外浅色/白色画布”直接烤进 1:1 JPG。详情页使用大尺寸头像时，圆外画布会形成明显空白。处理原则不是单纯调整 Compose，而是把人物做成更紧的头肩近景，让底板/背景延伸到最终方形边界之外。

### 可通过统一紧裁处理

- `andrej_karpathy.jpg`
- `charlie_munger.jpg`
- `donald_trump.jpg`
- `duan_yongping.jpg`
- `elon_musk.jpg`
- `feng_ge.jpg`
- `ilya_sutskever.jpg`
- `mr_beast.jpg`
- `nassim_taleb.jpg`
- `naval_ravikant.jpg`
- `paul_graham.jpg`
- `richard_feynman.jpg`
- `sigmund_freud.jpg`
- `steve_jobs.jpg`
- `tim_cook.jpg`
- `zhang_xuefeng.jpg`
- `zhang_yiming.jpg`

### 紧裁时必须额外消除文字/Logo

- `x_mentor.jpg`：下方存在 “X Growth Mentor” 文字；正式头像必须裁掉/去除。
- `justin_sun.jpg`：衣服存在 “Web3” 文字；正式头像必须裁掉/去除。
- `changpeng_zhao.jpg`：衣服存在品牌 Logo；正式头像必须裁掉/去除。

### 统一目标

- 1:1 正方形
- 头肩近景
- 发型/耳朵不被错误截断
- 人脸视觉重心居中
- 不保留圆外白边、明显边框或大面积无信息留白
- 适配 48dp 小头像和详情页大头像
- 最终人物资源路径：`app/src/main/assets/avatars/portraits/<skillId>.jpg`

## 新增 18 个人物头像

- `civil-service-coach`
- `public-document-coach`
- `study-planner`
- `career-navigator`
- `resume-interview-coach`
- `workplace-communication`
- `manager-expectation-review`
- `report-proposal-writer`
- `contract-checklist`
- `hr-document-assistant`
- `budget-consumption-coach`
- `habit-wellbeing-coach`
- `relationship-dialogue-practice`
- `chinese-social-etiquette`
- `culture-fortune-entertainment`
- `content-creator`
- `product-competition-analyst`
- `original-expression-naturalizer`

生成要求：成熟可信、职业身份有区分、无品牌、无图片内角色名称文字、不使用真实公众人物面孔替代这些虚构/专业角色。

## 工具视觉 6 项

- `team-handover`：交接/协作结构
- `meeting-to-action`：会议内容转行动项
- `research-fact-checker`：来源/证据/核查
- `software-copyright-organizer`：软件材料/归档
- `patent-disclosure-organizer`：技术方案/专利交底
- `office-document-productivity`：办公文档/结构化处理

生成要求：非人像；使用物件/文档/节点/核查等视觉隐喻；不使用两个字文字块；不把名称烤进图片。

## 工作目录

Google Drive：`skill-role-visuals-2026-09-23`

子目录：
- `portraits-existing`
- `portraits-new`
- `tools`

## 当前验证状态

- 视觉类型契约：38 人物 / 6 工具，代码与单元测试已建立。
- 资源完整性门禁：已建立，当前预期 RED，因为正式资源尚未补齐。
- 现有 20 张：已人工检查原图。
- 页面真实裁切效果：待资源入库后由本地 AI 在设备/模拟器只读验收。


## 生成源图（Google Drive）

根目录：`skill-role-visuals-2026-09-23`

### 正式人物源图 v2

> v1 写实源图不采用；以下 v2 为正式裁切来源，统一为接近现有头像的 2D 编辑插画风。

#### portrait-sheet-1-v2.png

- Drive ID：`11X7dbuTytrRU6c3Mi3Z83Upkg79etoxh`
- 3×2 映射（左→右、上→下）：
  1. `civil-service-coach`
  2. `public-document-coach`
  3. `study-planner`
  4. `resume-interview-coach`
  5. `workplace-communication`
  6. `manager-expectation-review`

#### portrait-sheet-2-v2.png

- Drive ID：`1OBJOptiF92eqS69hJW6nEQlvoF99PEhl`
- 3×2 映射：
  1. `report-proposal-writer`
  2. `contract-checklist`
  3. `hr-document-assistant`
  4. `budget-consumption-coach`
  5. `habit-wellbeing-coach`
  6. `relationship-dialogue-practice`

#### portrait-sheet-3-v2.png

- Drive ID：`1Rt3FA18tTpfTlhuZ0pA6wP7OAls17g3O`
- 3×2 映射：
  1. `chinese-social-etiquette`
  2. `culture-fortune-entertainment`
  3. `content-creator`
  4. `product-competition-analyst`
  5. `original-expression-naturalizer`
  6. `x_mentor`

#### career-navigator-v2.png

- Drive ID：`1g0AAC2Cl0XkVk1iJ2sDFoDTIhusHVD4I`
- 单图，最终输出为 `career-navigator.jpg`

### 工具视觉源图

- 文件：`tool-sheet-1.png`
- Drive ID：`1RNpQTFb7TB_b4Qa851WJorqKw7VCURxj`
- 3×2 映射（左→右、上→下）：
  1. `team-handover`
  2. `meeting-to-action`
  3. `research-fact-checker`
  4. `software-copyright-organizer`
  5. `patent-disclosure-organizer`
  6. `office-document-productivity`

工具源图每格可能不是正方形，最终必须逐格做中心/主体感知的 1:1 裁切，不能简单拉伸。

## 当前 CI 证据

Head `562cf2c8ba941786ec9492e5b74a2ce3a57f38e0`：
- `compileDebugKotlin`：PASS
- `Android UI Test Compile`：PASS
- `testDebugUnitTest`：573 tests / 1 failed
- 唯一失败：`OfficialSkillVisualAssetTest.productionAssets_coverEveryOfficialSkillVisual`
- `SkillRoleVisualRenderingArchitectureTest` 已进入同一 JVM 测试批次且未失败，主页/详情统一图片渲染契约成立。


## 2026-09-24 资源落库进展

已由 GPT 直接完成并提交到 GitHub：

- 新增/替换人物正式头像：19 张
  - 18 个此前缺失的人物角色
  - `x_mentor` 新头像（已移除旧 “X Growth Mentor” 文字）
- 工具正式视觉：6 张
- 全部为 1:1、512×512 最终资源
- Google Drive 同步目录：`skill-role-visuals-2026-09-23/final-assets/`
- GitHub 资源提交：
  - `0e7b97bc79fccc81ad80bfd577a213e4e72465b1`
  - `5f859a7351434640a21038de25a1ea980fbee01c`
  - `4d56d6d0f36304290f75628ad6b9ad5240e17d23`
  - `84ad1d69b57e43501b47c37d287a4507049bb54e`
  - `f9199a8a53f354c0b678f670ae79e12f0906ef37`

当前唯一资源缺口：19 张旧公众人物头像仍需从 `avatars/<id>.jpg` 机械重裁到 `avatars/portraits/<id>.jpg`，解决圆形底板/圆外留白。

本地 AI 任务已收敛为只处理这 19 张，见：
`docs/superpowers/status/2026-09-23-local-ai-skill-avatar-assets-prompt.md`

旧写实 v1 生成稿明确不采用；正式新增人物使用 v2 2D 编辑插画。


## 独立复核 — 2026-09-24 / commit `002a0af7f0493e34f7792120ca44685d9166d46b`

本地 AI 报告中的以下事实已由 GPT 复核成立：

- PR #67 Head 与报告 commit 一致。
- 该 commit 只新增 19 张 `app/src/main/assets/avatars/portraits/*.jpg`。
- `changpeng_zhao` 已无品牌 Logo。
- `justin_sun` 已无 “Web3” 字样。
- 19 张均未出现明显切头/切脸。
- GitHub Android CI：PASS。
- Android UI Test Compile：PASS。
- Secret scan：PASS。
- Android CI 内 `compileDebugKotlin`、完整 `testDebugUnitTest`、`lintDebug`、`assembleDebug`、optimized release APK、Room schema verify 均 PASS。

但 GPT 对 19 张最终图逐张原图复核后，**不接受 `no_outer_blank: PASS`**。

### 视觉根因仍存在

多数旧头像虽然被放大，但仍然能看到“圆形底板/圆环边界 + 圆外角落画布”。角色详情使用 rounded-rectangle 大头像时，这些圆外区域仍会表现为明显空白或不自然边角。

### 当前可接受

以下 3 张已无明显“内圆 + 外角落”边界，可暂不返工：

- `changpeng_zhao.jpg`
- `justin_sun.jpg`
- `mr_beast.jpg`

### 必须二次返工

以下 16 张仍能直接辨认出圆形底板、圆环或圆外角落：

- `andrej_karpathy.jpg`
- `charlie_munger.jpg`
- `donald_trump.jpg`
- `duan_yongping.jpg`
- `elon_musk.jpg`
- `feng_ge.jpg`
- `ilya_sutskever.jpg`
- `nassim_taleb.jpg`
- `naval_ravikant.jpg`
- `paul_graham.jpg`
- `richard_feynman.jpg`
- `sigmund_freud.jpg`
- `steve_jobs.jpg`
- `tim_cook.jpg`
- `zhang_xuefeng.jpg`
- `zhang_yiming.jpg`

其中最明显：
- `donald_trump`：人物外仍有完整浅色圆形底板，外围留白明显。
- `elon_musk`：圆环边界几乎完整可见。
- `tim_cook`：白色圆环和外层蓝色角落非常明显。

### 第二次验收标准

最终 512×512 图中：

1. 不得出现完整或近完整的圆形/椭圆底板轮廓。
2. 不得在四角看到与人物背景明显不同的“圆外画布楔形区域”。
3. 不得保留明显圆环边框。
4. 优先方案：进一步紧裁，使圆形底板边界越出最终方形画布。
5. 如果继续裁切会切掉头发、耳朵或下巴，可对背景做非生成式延展/填充，使四角与主体背景连续；不得透明。
6. 人物身份、五官和原插画内容不得生成式重画。
7. 头顶、耳朵、下巴不得被切断；肩部允许部分出框。
8. 在 160×160 预览和 100% 原图两个尺度上均不能一眼识别出“一个圆头像嵌在方形图片里”。

本次第二次返工只处理上述 16 张，不需要重做已通过的 3 张。


## 待收口：旧根目录重复资源

当前 `app/src/main/assets/avatars/` 根目录仍保留 20 张旧 JPG，共 7,517,990 bytes（约 7.17 MiB）。

它们暂时保留，作为第二次视觉返工的源/回退，不在返工前删除。

第二次视觉通过后：
1. 定点确认当前生产代码、`skills_config.json`、metadata 生成脚本和测试不再引用 `avatars/<id>.jpg`；
2. 删除根目录 20 张旧 JPG，只保留 `avatars/portraits/` 与 `avatars/tools/`；
3. 重新运行 JVM、lint、assemble 和相关 UI compile，确认 APK 不依赖旧副本。
