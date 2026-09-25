# 本地 AI 最后单图修复：steve_jobs.jpg

仓库：`elio-zwd/AI-Skill-Roundtable`  
分支：`codex/skill-role-avatars`

## 只允许修改

`app/src/main/assets/avatars/portraits/steve_jobs.jpg`

其余任何文件都不要修改。

## 问题

第三次返工后，GPT 原图复核确认其他 10 张已通过。

`steve_jobs.jpg` 当前唯一残留问题：
- 图片底部，手部与深色衣服交界附近，有一小段明显白色细线/弧线；
- 视觉上像旧圆环/描边残留。

## 修复要求

- 只清除这段白色细线/弧线；
- 使用非生成式方式（Pillow/OpenCV/图像编辑器均可）；
- 参考周围深色衣服/背景进行像素级修补；
- 不改变脸、眼镜、头部、手部轮廓；
- 不改变人物身份和原画风；
- 不生成新肖像；
- 保持 512×512 JPEG；
- 不修改 Kotlin、JSON、tests、docs 或其他图片。

## 验证

100% 原图确认：
- white_arc_artifact = NO
- hand_shape_changed = NO
- face_changed = NO
- new_artifact = NO

运行：

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.elio.jianyu.skill.role.OfficialSkillVisualAssetTest" --rerun-tasks
```

必须 PASS。

然后：
```powershell
git status --short
```

必须只有：
`app/src/main/assets/avatars/portraits/steve_jobs.jpg`

Commit：
`assets: 清理 Steve Jobs 头像残留描边`

push 到：
`origin codex/skill-role-avatars`

禁止 merge / rebase / force push。

返回：
```
RESULT: PASS | FAIL
HEAD_BEFORE:
COMMIT_SHA:
PUSHED: YES | NO
TEST:
VISUAL:
white_arc_artifact: PASS/FAIL
hand_shape_changed: NO/YES
face_changed: NO/YES
new_artifact: NO/YES
```
