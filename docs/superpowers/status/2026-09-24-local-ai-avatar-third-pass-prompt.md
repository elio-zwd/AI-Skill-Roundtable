# 本地 AI 第三次定点视觉返工：11 张旧人物头像伪影修复

日期：2026-09-24

仓库：`elio-zwd/AI-Skill-Roundtable`  
目标分支：`codex/skill-role-avatars`

## 背景

第二次返工 commit：

`a769ed8feb7fc35b4c87e071e16340715fb986ad`

对应 GitHub：
- Android CI：PASS
- Android UI Test Compile：PASS
- Secret scan：PASS

但 GPT 对 16 张第二次返工图逐张原图复核后，发现其中 11 张仍有明显处理伪影。

这次只做定点修复，不再泛化处理。

## 开始前

1. `git fetch origin`
2. checkout `codex/skill-role-avatars`
3. pull 最新内容
4. 阅读：
   - `docs/superpowers/status/2026-09-23-skill-role-avatar-audit.md`
5. 确认 `a769ed8feb7fc35b4c87e071e16340715fb986ad` 是当前 HEAD 的祖先。
6. 不 merge、不 rebase、不 force push。

## 已通过，不要修改

以下 8 张已经通过 GPT 原图视觉复核：

- andrej_karpathy.jpg
- donald_trump.jpg
- duan_yongping.jpg
- zhang_xuefeng.jpg
- zhang_yiming.jpg
- changpeng_zhao.jpg
- justin_sun.jpg
- mr_beast.jpg

## 只修以下 11 张

目录：

`app/src/main/assets/avatars/portraits/`

### 1. elon_musk.jpg
问题：
- 左下、右下仍有旧圆环/圆形边框弧线。

要求：
- 只移除圆环弧线；
- 背景连续；
- 不改变人物面部、头发、衣服。

### 2. charlie_munger.jpg
问题：
- 头发顶部左右有矩形补块/阶梯边。

要求：
- 恢复自然头发外轮廓；
- 不能有矩形补块；
- 不能把头发平切。

### 3. feng_ge.jpg
问题：
- 头发左上/侧面有明显矩形补边。

要求：
- 去掉矩形伪影；
- 保留自然发型。

### 4. ilya_sutskever.jpg
问题：
- 卷发顶部被水平矩形边界截平；
- 有块状补边。

要求：
- 恢复自然卷发顶部；
- 禁止平切。

### 5. nassim_taleb.jpg
问题：
- 头顶有不自然水平直线/平顶。

要求：
- 恢复自然头部/发际圆弧；
- 背景连续。

### 6. naval_ravikant.jpg
问题：
- 头发顶部被水平直线截平。

要求：
- 恢复自然发型上缘。

### 7. paul_graham.jpg
问题：
- 头发顶部有水平平切和矩形边。

要求：
- 恢复自然头发轮廓。

### 8. richard_feynman.jpg
问题：
- 头发顶部有非常明显的水平平切。

要求：
- 恢复自然蓬松头发轮廓；
- 不得再出现直线截边。

### 9. sigmund_freud.jpg
问题：
- 头顶平直矩形边；
- 侧面有台阶状补边。

要求：
- 恢复自然头部/发际轮廓。

### 10. steve_jobs.jpg
问题：
- 手部/衣服下方仍有白色圆弧/圆环残留。

要求：
- 去掉白色圆弧；
- 保留手和衣服主体；
- 背景连续。

### 11. tim_cook.jpg
问题：
- 肩部/躯干周围仍有明显白色圆环/描边。

要求：
- 去掉白色圆环；
- 人物与背景直接连续；
- 不要破坏肩膀轮廓。

## 推荐处理策略

优先使用：
- 当前 `avatars/portraits/*.jpg`
- 原始根目录 `app/src/main/assets/avatars/*.jpg`

做非生成式修复。

允许：
- Pillow
- OpenCV
- 图像编辑器
- 复制/克隆背景
- 基于原始图的前景提取与重新合成
- 调整画布
- 背景延展
- 重新缩放/定位

禁止：
- 生成式换脸；
- 重新生成公众人物；
- 改变人物身份/五官；
- 简单矩形填色导致新的头顶平切；
- 改 8 张已通过头像；
- 修改 Kotlin / JSON / tests / docs；
- 修改 18 个新增人物头像；
- 修改 6 个工具图。

## 视觉验收标准

每张都必须同时满足：

- visible_circle_boundary = NO
- outer_corner_wedge = NO
- visible_ring = NO
- rectangular_fill_artifact = NO
- horizontal_head_cut = NO
- head_cutoff = NO
- face_changed = NO
- background_continuous = YES

重点看：
- 100% 原图；
- 160×160 缩略预览。

不要只看 alpha / 是否有透明像素。

## 测试

处理完成后运行：

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.elio.jianyu.skill.role.OfficialSkillVisualAssetTest" --tests "com.elio.jianyu.skill.LegacySkillConfigVisualPathTest" --tests "com.elio.jianyu.execution.ExecutionSkillResolverVisualArchitectureTest" --tests "com.elio.jianyu.ui.screens.skills.SkillRoleCatalogProjectionTest" --tests "com.elio.jianyu.ui.screens.skills.SkillRoleVisualRenderingArchitectureTest" --rerun-tasks
```

必须 PASS。

再运行：

```powershell
git status --short
```

只允许上述 11 张 JPG 有修改。

## Commit / Push

全部满足视觉标准、聚焦测试 PASS 后：

Commit：

`assets: 修复旧 Skill 头像处理伪影`

push：

`origin codex/skill-role-avatars`

禁止 merge。

## 返回结果

```
RESULT: PASS | FAIL
HEAD_BEFORE:
COMMIT_SHA:
PUSHED: YES | NO

REWORKED_COUNT: 11

UNCHANGED_APPROVED:
- andrej_karpathy.jpg
- donald_trump.jpg
- duan_yongping.jpg
- zhang_xuefeng.jpg
- zhang_yiming.jpg
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
rectangular_fill_artifact_all_no: PASS/FAIL
horizontal_head_cut_all_no: PASS/FAIL
head_cutoff_all_no: PASS/FAIL
background_continuous_all_yes: PASS/FAIL

CRITICAL:
elon_musk: PASS/FAIL
richard_feynman: PASS/FAIL
tim_cook: PASS/FAIL

CHANGED_PATHS:
<11 个文件名>

ISSUES:
<仅失败时填写>
```
