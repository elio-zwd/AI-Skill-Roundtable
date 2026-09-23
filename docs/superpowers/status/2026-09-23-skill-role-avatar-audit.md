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
