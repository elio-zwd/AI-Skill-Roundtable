# 本地 AI 资源处理任务：Skill 角色头像与工具视觉

你现在只负责 **二进制视觉资源机械处理 + 聚焦验证**。GPT 继续负责 Kotlin、目录投影、对话适配、PR 审查和后续代码修改。

## 仓库与分支

仓库：`elio-zwd/AI-Skill-Roundtable`

目标分支：`codex/skill-role-avatars`

开始前：

1. `git fetch origin`
2. checkout `codex/skill-role-avatars`
3. pull 最新内容
4. 确认提交 `562cf2c8ba941786ec9492e5b74a2ce3a57f38e0` 是当前 HEAD 的祖先
5. 不要切换到 main，不要 merge，不要 rebase

## 允许修改的范围

仅允许创建/覆盖以下最终资源：

- `app/src/main/assets/avatars/portraits/*.jpg`
- `app/src/main/assets/avatars/tools/*.png`

**不要修改：**
- Kotlin
- JSON
- Gradle
- tests
- docs
- Room schema
- 旧 `app/src/main/assets/avatars/*.jpg` 根目录文件

旧根目录资源先保留，后续由 GPT 在确认 legacy 路径迁移后统一处理。

## Google Drive 源图

Google Drive 根目录：`skill-role-visuals-2026-09-23`

如果你的环境能直接访问 Drive，按下面 Drive ID 获取；如果不能，请让用户把对应文件下载到临时目录后再继续，不要自己重新生成替代图。

### portrait-sheet-1-v2.png
Drive ID：`11X7dbuTytrRU6c3Mi3Z83Upkg79etoxh`

> 只使用 v2；旧 `portrait-sheet-1.png` 不采用。

3×2，左→右、上→下：
1. civil-service-coach
2. public-document-coach
3. study-planner
4. resume-interview-coach
5. workplace-communication
6. manager-expectation-review

### portrait-sheet-2-v2.png
Drive ID：`1OBJOptiF92eqS69hJW6nEQlvoF99PEhl`

3×2：
1. report-proposal-writer
2. contract-checklist
3. hr-document-assistant
4. budget-consumption-coach
5. habit-wellbeing-coach
6. relationship-dialogue-practice

### portrait-sheet-3-v2.png
Drive ID：`1Rt3FA18tTpfTlhuZ0pA6wP7OAls17g3O`

3×2：
1. chinese-social-etiquette
2. culture-fortune-entertainment
3. content-creator
4. product-competition-analyst
5. original-expression-naturalizer
6. x_mentor

`x_mentor` 必须使用这里的新图，旧图含 “X Growth Mentor” 文字，不允许继续使用。

### career-navigator-v2.png
Drive ID：`1g0AAC2Cl0XkVk1iJ2sDFoDTIhusHVD4I`

直接作为人物头像源图，做统一尺寸/压缩即可。旧 `career-navigator.jpg` 写实工作稿不采用。

### tool-sheet-1.png
Drive ID：`1RNpQTFb7TB_b4Qa851WJorqKw7VCURxj`

3×2：
1. team-handover
2. meeting-to-action
3. research-fact-checker
4. software-copyright-organizer
5. patent-disclosure-organizer
6. office-document-productivity

工具视觉不得出现人物头像。每个 panel 需要按主体重新裁成 1:1，不允许直接拉伸。

## 现有 19 个真人/人物视角头像

从当前仓库的 `app/src/main/assets/avatars/*.jpg` 读取并处理以下 19 项：

- andrej_karpathy
- changpeng_zhao
- charlie_munger
- donald_trump
- duan_yongping
- elon_musk
- feng_ge
- ilya_sutskever
- justin_sun
- mr_beast
- nassim_taleb
- naval_ravikant
- paul_graham
- richard_feynman
- sigmund_freud
- steve_jobs
- tim_cook
- zhang_xuefeng
- zhang_yiming

共同问题：旧资源把圆形底板与圆外空白烤进了方形 JPG，详情页放大后会出现明显空白。

处理标准：
- 输出 1:1
- 512×512 或更高；建议 512×512 JPEG quality 82–88
- 头肩近景
- 主体明显充满画面
- 不留圆外白边/大面积无信息留白
- 不要切掉头顶、耳朵、下巴
- 不改变人物身份，不做生成式换脸

特别检查：
- `justin_sun`：最终裁切不得保留衣服上的 “Web3” 字样
- `changpeng_zhao`：最终裁切不得保留衣服上的品牌 Logo
- `donald_trump`：只做原图裁切/缩放，不生成新肖像
- `x_mentor`：不要处理旧图，用 Drive 新图

输出到：
`app/src/main/assets/avatars/portraits/<skillId>.jpg`

## 新增 18 个人物角色

从 Drive 人物源图裁切后统一输出 **1:1、至少 512×512 的 JPEG**：

- civil-service-coach.jpg
- public-document-coach.jpg
- study-planner.jpg
- career-navigator.jpg
- resume-interview-coach.jpg
- workplace-communication.jpg
- manager-expectation-review.jpg
- report-proposal-writer.jpg
- contract-checklist.jpg
- hr-document-assistant.jpg
- budget-consumption-coach.jpg
- habit-wellbeing-coach.jpg
- relationship-dialogue-practice.jpg
- chinese-social-etiquette.jpg
- culture-fortune-entertainment.jpg
- content-creator.jpg
- product-competition-analyst.jpg
- original-expression-naturalizer.jpg

加上 `x_mentor.jpg` 和现有 19 真人视角，`portraits` 最终应有 **38 张 JPG**。

## 工具视觉输出

逐格主体感知裁切为 **1:1、至少 512×512 的 PNG**，输出到 `app/src/main/assets/avatars/tools/`：

- team-handover.png
- meeting-to-action.png
- research-fact-checker.png
- software-copyright-organizer.png
- patent-disclosure-organizer.png
- office-document-productivity.png

最终应有 **6 张 PNG**。

## 视觉检查

生成最终文件后，做一张临时 contact sheet 供你自己检查，但不要提交 contact sheet。

检查：
- 38 人物头像主体尺度是否基本一致
- 没有圆外白边
- 没有图片内角色名
- x_mentor 没有文字
- justin_sun 没有 Web3
- changpeng_zhao 没有品牌 Logo
- 6 工具图不含人脸
- 工具图中心主体在 1:1 裁切后没有被截断

如果某一项不合格，只返工该文件。

## 聚焦测试

资源处理完成后运行：

Windows：
`gradlew.bat testDebugUnitTest --tests "com.elio.jianyu.skill.role.OfficialSkillVisualAssetTest" --tests "com.elio.jianyu.ui.screens.skills.SkillRoleCatalogProjectionTest" --tests "com.elio.jianyu.ui.screens.skills.SkillRoleVisualRenderingArchitectureTest"`

macOS/Linux：
`./gradlew testDebugUnitTest --tests "com.elio.jianyu.skill.role.OfficialSkillVisualAssetTest" --tests "com.elio.jianyu.ui.screens.skills.SkillRoleCatalogProjectionTest" --tests "com.elio.jianyu.ui.screens.skills.SkillRoleVisualRenderingArchitectureTest"`

预期：PASS。

如果失败：
- 不修改 Kotlin/test 来迎合失败
- 只修资源文件
- 返回失败测试、文件路径、关键错误

## Commit / Push

聚焦测试 PASS 后：

1. `git status --short`
2. 确认只有 `app/src/main/assets/avatars/portraits/**` 和 `app/src/main/assets/avatars/tools/**`
3. commit message：
   `assets: 添加 Skill 角色正式视觉资源`
4. push 到：
   `origin codex/skill-role-avatars`

禁止：
- merge
- rebase
- force push
- 修改 PR base
- 修改 Kotlin/JSON/tests/docs

## 返回给 GPT 的结果

只返回压缩结果：

```
RESULT: PASS | FAIL
HEAD_BEFORE:
COMMIT_SHA:
PUSHED: YES | NO

COUNTS:
portraits_jpg: 38
tools_png: 6

TEST:
command:
result:

CHECKS:
no_outer_blank: PASS/FAIL
no_text_x_mentor: PASS/FAIL
no_web3_justin_sun: PASS/FAIL
no_logo_changpeng_zhao: PASS/FAIL
tool_images_no_faces: PASS/FAIL

CHANGED_PATHS:
<只列目录和数量，不贴二进制 diff>

ISSUES:
<仅失败时填写关键问题>
```
