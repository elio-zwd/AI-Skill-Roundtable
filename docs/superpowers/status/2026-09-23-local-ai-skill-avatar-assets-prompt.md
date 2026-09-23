# 本地 AI 剩余资源任务：重裁 19 张旧公众人物头像

GPT 已完成并提交：
- 19 张新增/替换人物正式头像（含 `x_mentor`）
- 6 张工具正式视觉
- 统一 Kotlin 视觉路径
- legacy `skills_config.json` / metadata 路径迁移
- 资源完整性、尺寸、页面、对话、执行快照测试

你现在只负责 **19 张现有公众人物头像的机械重裁 + 聚焦验证 + commit/push**。

## 仓库与分支

仓库：`elio-zwd/AI-Skill-Roundtable`
目标分支：`codex/skill-role-avatars`

开始前：
1. `git fetch origin`
2. checkout `codex/skill-role-avatars`
3. pull 最新内容
4. 确认当前 HEAD 包含提交 `f9199a8a53f354c0b678f670ae79e12f0906ef37` 或其后继
5. 不 merge / 不 rebase / 不 force push

## 允许修改

仅允许创建/覆盖：

`app/src/main/assets/avatars/portraits/<skillId>.jpg`

只处理以下 19 项：

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

源文件均在：

`app/src/main/assets/avatars/<skillId>.jpg`

**不要修改或覆盖：**
- 已存在的新增人物 `avatars/portraits/*.jpg`
- `avatars/tools/*.png`
- Kotlin / JSON / tests / docs / Gradle / Room
- 根目录旧 JPG（先保留，GPT 后续确认删除）

## 根因

旧 JPG 大多把“圆形底板 + 圆外浅色/白色画布”直接烤进 1:1 图片。
在详情大头像中，即使 `ContentScale.Crop` 也会显示明显圆外留白。

目标不是换脸或重生成，而是 **对原图做更紧的 1:1 头肩裁切**。

## 裁切标准

每张最终输出：
- JPEG
- 1:1
- 至少 512×512；建议 512×512
- JPEG quality 82–88
- 主体头肩明显充满画面
- 不保留明显圆外白边/浅色大边框
- 不截头顶、耳朵、下巴
- 不改变人物身份
- 不做生成式换脸

对大多数旧图，可围绕中心人物把原图放大约 1.20～1.35 倍后做中心/主体感知正方形裁切；最终以 contact sheet 人工判断为准，不机械套同一倍率。

特别要求：
- `donald_trump`：只能裁切/缩放原图，不生成新肖像
- `justin_sun`：最终构图不得保留衣服上的 “Web3” 字样
- `changpeng_zhao`：最终构图不得保留衣服上的品牌 Logo
- 若单纯安全裁切无法同时满足身份完整与去文字/Logo，**不要用修图生成填充**；标记 FAIL 并返回给 GPT

输出：

`app/src/main/assets/avatars/portraits/<skillId>.jpg`

完成后 `avatars/portraits/` 应总计 **38 张 JPG**；`avatars/tools/` 应保持 **6 张 PNG**。

## 视觉验收

生成临时 contact sheet（不要提交）检查：
- 19 张人物主体尺度基本一致
- 没有明显圆外白边
- 没有截脸/截头
- `justin_sun` 无 Web3
- `changpeng_zhao` 无品牌 Logo
- 与已提交的新增 v2 人物头像在大图密度上大致协调

## 聚焦测试

Windows：

`gradlew.bat testDebugUnitTest --tests "com.elio.jianyu.skill.role.OfficialSkillVisualAssetTest" --tests "com.elio.jianyu.skill.LegacySkillConfigVisualPathTest" --tests "com.elio.jianyu.execution.ExecutionSkillResolverVisualArchitectureTest" --tests "com.elio.jianyu.ui.screens.skills.SkillRoleCatalogProjectionTest" --tests "com.elio.jianyu.ui.screens.skills.SkillRoleVisualRenderingArchitectureTest"`

macOS/Linux：

`./gradlew testDebugUnitTest --tests "com.elio.jianyu.skill.role.OfficialSkillVisualAssetTest" --tests "com.elio.jianyu.skill.LegacySkillConfigVisualPathTest" --tests "com.elio.jianyu.execution.ExecutionSkillResolverVisualArchitectureTest" --tests "com.elio.jianyu.ui.screens.skills.SkillRoleCatalogProjectionTest" --tests "com.elio.jianyu.ui.screens.skills.SkillRoleVisualRenderingArchitectureTest"`

预期：PASS。

如果失败：
- 只修资源
- 不修改 Kotlin/test 来迎合失败
- 返回失败测试、文件路径、关键错误

## Commit / Push

测试 PASS 后：
1. `git status --short`
2. 必须只出现上述 19 张 `avatars/portraits/*.jpg`
3. commit：
   `assets: 重裁现有 Skill 人物头像`
4. push 到：
   `origin codex/skill-role-avatars`

禁止 merge / rebase / force push。

## 返回给 GPT

```
RESULT: PASS | FAIL
HEAD_BEFORE:
COMMIT_SHA:
PUSHED: YES | NO

COUNTS:
portraits_total: 38
tools_total: 6
recropped_existing: 19

TEST:
command:
result:

CHECKS:
no_outer_blank: PASS/FAIL
no_web3_justin_sun: PASS/FAIL
no_logo_changpeng_zhao: PASS/FAIL
no_face_or_head_cutoff: PASS/FAIL

CHANGED_PATHS:
app/src/main/assets/avatars/portraits/ (19 files)

ISSUES:
<仅失败时填写>
```
