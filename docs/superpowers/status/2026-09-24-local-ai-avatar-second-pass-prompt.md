# 本地 AI 第二次视觉返工：旧人物头像去除圆形底板/圆外空白

日期：2026-09-24

仓库：`elio-zwd/AI-Skill-Roundtable`  
目标分支：`codex/skill-role-avatars`

## 背景

第一次本地处理 commit：

`002a0af7f0493e34f7792120ca44685d9166d46b`

已经解决：
- 19 张旧人物头像进入 `avatars/portraits/`
- 512×512、可解码、聚焦测试 PASS
- `justin_sun` 的 Web3 字样已去除
- `changpeng_zhao` 的品牌 Logo 已去除
- 没有明显切头/切脸

但 GPT 对 19 张最终 JPG 逐张原图复核后发现：第一次报告中的 `no_outer_blank: PASS` 不成立。

多数图片仍能一眼看到：
- 圆形底板；
- 圆环；
- 与内圆背景不同的四角画布；
- “一个圆头像嵌在方形图片里”的视觉。

这会在角色详情的 rounded-rectangle 大头像中继续形成空白/不自然边角。

## 开始前

1. `git fetch origin`
2. checkout `codex/skill-role-avatars`
3. pull 最新内容
4. 阅读：
   - `docs/superpowers/status/2026-09-23-skill-role-avatar-audit.md`
5. 确认 `002a0af7f0493e34f7792120ca44685d9166d46b` 是当前 HEAD 的祖先。
6. 不 merge、不 rebase、不 force push。

## 只返工以下 16 张

`app/src/main/assets/avatars/portraits/`

- andrej_karpathy.jpg
- charlie_munger.jpg
- donald_trump.jpg
- duan_yongping.jpg
- elon_musk.jpg
- feng_ge.jpg
- ilya_sutskever.jpg
- nassim_taleb.jpg
- naval_ravikant.jpg
- paul_graham.jpg
- richard_feynman.jpg
- sigmund_freud.jpg
- steve_jobs.jpg
- tim_cook.jpg
- zhang_xuefeng.jpg
- zhang_yiming.jpg

以下 3 张已经通过 GPT 独立复核，**不要修改**：
- changpeng_zhao.jpg
- justin_sun.jpg
- mr_beast.jpg

## 最关键的视觉标准

这次不是只看“有没有白边”。

### 必须 FAIL 的情况

只要最终 512×512 中满足以下任一项，就必须继续返工：

- 能看出完整或近完整的圆形/椭圆头像底板；
- 能沿着人物背景辨认出明显圆弧边界；
- 四角出现与人物底板明显不同的白色、灰色、蓝色等外层画布楔形；
- 有明显圆环边框；
- 一眼看起来像“圆头像贴在方形图片上”。

特别明显的当前问题：
- `donald_trump`：浅色圆形底板仍完整可辨，外围留白大。
- `elon_musk`：圆环边界几乎完整可见。
- `tim_cook`：白色圆环 + 外层蓝色角落仍很明显。

### 必须 PASS 的状态

最终图片应表现为：

- 人物/背景在整个 1:1 方形画布中连续；
- 不再能判断原图曾经是圆形头像；
- 在 160×160 缩略预览中没有圆外空白；
- 在 100% 原图中四角和主体背景视觉连续；
- 头顶、耳朵、下巴完整；
- 肩部可以自然出框；
- 不改变人物身份、五官、原画风。

## 允许的处理方式

优先按以下顺序：

### 方案 A：进一步紧裁

如果不切头/耳朵/下巴：
- 放大人物；
- 让原来的圆形底板边界越出最终 512×512 方形边界；
- 最终再缩放为 512×512。

### 方案 B：非生成式背景延展

如果继续裁会伤到人物：
- 使用原图已有背景色/纹理对四角做延展；
- 可用 Pillow / OpenCV / 图像编辑器的 clone/fill/edge extension；
- 使四角与圆内背景连续；
- 不得透明；
- 不得新增文字/Logo；
- 不得生成式改变人物或五官。

### 禁止

- 不使用生成式换脸；
- 不重新生成公众人物肖像；
- 不修改 Kotlin / JSON / tests / docs；
- 不修改 3 张已通过图片；
- 不修改 18 个新人物头像；
- 不修改 6 个工具视觉。

## 视觉检查流程

处理 16 张后：

1. 生成临时 contact sheet，不提交 contact sheet。
2. contact sheet 同时看 160×160 等效缩略效果。
3. 对每张逐项确认：
   - `visible_circle_boundary = NO`
   - `outer_corner_wedge = NO`
   - `visible_ring = NO`
   - `head_cutoff = NO`
4. 再单独打开：
   - donald_trump
   - elon_musk
   - tim_cook
   确认圆底板/圆环已经完全不可辨认。

**不要再用“图片四周没有透明像素”作为 no_outer_blank 的判断。**
本次关注的是“圆形构图边界是否仍嵌在方形图里”。

## 测试

处理完成后运行：

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.elio.jianyu.skill.role.OfficialSkillVisualAssetTest" --tests "com.elio.jianyu.skill.LegacySkillConfigVisualPathTest" --tests "com.elio.jianyu.execution.ExecutionSkillResolverVisualArchitectureTest" --tests "com.elio.jianyu.ui.screens.skills.SkillRoleCatalogProjectionTest" --tests "com.elio.jianyu.ui.screens.skills.SkillRoleVisualRenderingArchitectureTest"
```

必须 PASS。

再运行：

```powershell
git status --short
```

只允许 16 张指定 JPG 有修改。

## Commit / Push

全部满足视觉标准、聚焦测试 PASS 后：

Commit：

`assets: 去除旧 Skill 头像圆形底板留白`

push：

`origin codex/skill-role-avatars`

禁止 merge。

## 返回结果

只返回：

```
RESULT: PASS | FAIL
HEAD_BEFORE:
COMMIT_SHA:
PUSHED: YES | NO

REWORKED_COUNT:
UNCHANGED_APPROVED:
- changpeng_zhao.jpg
- justin_sun.jpg
- mr_beast.jpg

TEST:
command:
result:

VISUAL_CHECK:
visible_circle_boundary_all_no: PASS/FAIL
outer_corner_wedge_all_no: PASS/FAIL
visible_ring_all_no: PASS/FAIL
head_cutoff_all_no: PASS/FAIL

CRITICAL:
donald_trump: PASS/FAIL
elon_musk: PASS/FAIL
tim_cook: PASS/FAIL

CHANGED_PATHS:
<只列文件名>

ISSUES:
<仅失败时填写>
```
