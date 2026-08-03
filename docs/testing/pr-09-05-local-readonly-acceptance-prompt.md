# PR09-05 本地 AI 严格只读验收 Prompt

你现在对 GitHub 仓库 `elio-zwd/AI-Skill-Roundtable` 的 Draft PR09-05 执行独立、严格只读验收。

目标分支：

```text
feat/pr-09-05-official-skill-catalog
```

基线：

```text
main@78abf30b60d863ce0ac29323546e61971d50c9c9
```

## 一、只读纪律

全过程只允许：

- 拉取远端最新代码；
- 检出目标分支；
- 读取文件和 Git 历史；
- 构建、测试、Lint、安装和模拟器/设备验收；
- 记录命令、退出码、日志和截图。

严格禁止：

- 修改、格式化或生成源码；
- 接受 IDE 自动修复；
- 创建 Commit；
- 推送、变基、合并、关闭 PR；
- 标记 Ready；
- 删除分支；
- 修改 Gradle、Schema、测试或配置以绕过失败。

测试前后都必须执行：

```powershell
git status --short
git diff --exit-code
git diff --cached --exit-code
```

任何工作区变化都视为验收失败；若构建工具生成已跟踪文件变化，记录后恢复干净工作区并重新验证，不得提交。

## 二、精确 Head 门禁

```powershell
git fetch origin --prune
git checkout feat/pr-09-05-official-skill-catalog
git pull --ff-only origin feat/pr-09-05-official-skill-catalog
git rev-parse HEAD
git rev-parse origin/main
git merge-base origin/main HEAD
git status --short
```

把实际 Head 与 GitHub Draft PR 当前 Head 比较。二者不一致时停止，不得复用旧报告或旧测试证据。

记录：

- 操作系统；
- Shell；
- Git；
- JDK；
- Gradle Wrapper；
- Android SDK、adb、模拟器/设备型号和 API Level；
- 实际 Base、Merge Base、Head；
- Draft PR 编号和状态。

## 三、差异与禁止区

```powershell
git diff --name-status 78abf30b60d863ce0ac29323546e61971d50c9c9...HEAD
git diff --check 78abf30b60d863ce0ac29323546e61971d50c9c9...HEAD
```

确认没有修改：

```text
App.kt
根导航、Route 定义、NavHost、底部导航
JianyuRepositoryContract.kt
RoomJianyuRepository.kt
IssueExecutionRepositoryComponent.kt
PendingMessageRepositoryComponent.kt
ResourceRepositoryComponent.kt
UsageRepositoryComponent.kt
LifecycleRecoveryRepositoryComponent.kt
JianyuRepositoryTransactions.kt
JianyuRepositoryDao.kt
ChatSession.kt
RoundtableDatabase.kt
app/schemas/
```

确认：

- 没有第二套 NavHost；
- 没有 DAO 直连；
- 没有 Gemini、GenerativeModel 或 ExecutionRun 创建；
- 没有用户自定义 Skill、第三方导入或市场；
- Room 仍为 v7；
- 不存在 `8.json`；
- Manifest 未复制 `systemPrompt`、`system_prompt` 或第三方 Skill 正文。

## 四、44 项 Catalog 契约

读取：

```text
app/src/main/assets/official_skill_catalog_v1.json
```

使用 JSON 工具或脚本真实解析，禁止只靠目视抽查。验证：

1. 数量精确 44；
2. ID 精确唯一；
3. `defaultOrder` 精确为 1～44，且无重复；
4. 中文名、简介、领域、场景、输入、输出、资料要求不为空；
5. 四类主类型全部存在；
6. 三类主价值全部存在；
7. 只有一个 `zhang_xuefeng`；
8. 不存在 `zhangxuefeng-perspective`；
9. 不存在 `academic-ai-evasion`；
10. 存在 `office-document-productivity`；
11. 存在 `original-expression-naturalizer`；
12. 20 项历史资产路径与 `docs/skills/jianyu-skill-catalog-mapping.md` 精确一致；
13. 20 项历史人物/顾问均可发现、可搜索、可推荐；
14. 风险不改变其官方身份或默认排序；
15. 未通过门禁的项目不得标记可执行；
16. 官方身份、发布状态和执行状态是独立字段。

额外确认 44 项中：

- `hasAsset=true` 精确为现有 20 项；
- 所有 `hasAsset=true` 项都有合法 `assetPath`；
- 所有 `hasAsset=false` 项不伪造 `assetPath`；
- 所有 `executable=false` 项都有不可执行原因。

## 五、人物风险与声明

验证至少以下人物可以搜索到，且未因风险隐藏或降权：

```text
张雪峰
唐纳德·特朗普
查理·芒格
孙宇晨
赵长鹏
```

检查全部 `PERSON_PERSPECTIVE`：

- 有非本人声明；
- 明确不是本人真实意见；
- 不是专业执照或事实权威；
- 风险只影响披露、时效核验和输出边界；
- 默认排序仍由 Manifest `defaultOrder` 决定。

## 六、特殊 Skill

### 办公文档助手

确认列表提示和详情明确：

- 只生成和整理文档内容；
- 不控制桌面 Office；
- 不自动打开、点击、签署、提交或操作本地软件；
- 文件能力受当前 App 实际能力限制。

### 去AI化助手

确认列表、详情、组合成员位置包含或可消费以下边界：

- 只处理用户真实内容；
- 不规避检测；
- 不伪造事实；
- 不伪造经历；
- 不代写受限独立内容；
- 不删除必要诚信声明；
- 不冒充他人。

## 七、搜索、筛选与排序

实际运行 JVM 测试并在模拟器/设备操作页面，验证：

- 中文名；
- 官方 ID；
- 别名；
- 简介；
- 领域；
- 场景；
- 输出类型；
- 大小写归一；
- 首尾空白；
- 无结果；
- 稳定默认排序。

筛选验证：

- 四类主类型；
- 现实支持 / 思维拓展 / 两者皆可；
- 使用模式；
- 联网要求；
- 资料要求；
- 风险等级；
- 发布状态；
- 当前可执行；
- 收藏；
- 最近使用。

状态必须区分：

```text
可发现
可推荐
可执行
待门禁
阻断重构
许可或原创性待核验
```

## 八、Validator

验证 `CatalogOfficialSkillIdValidator`：

- 44 个 Manifest ID 全部通过；
- 未知、空和首尾带空白 ID 拒绝；
- 风险人物通过；
- 当前不可执行候选仍作为官方 ID 通过；
- 不联网；
- 不访问数据库；
- 不存在生产“全部允许”实现；
- Catalog 加载失败时 Repository 仍使用拒绝型策略。

## 九、收藏与最近使用

验证收藏：

- 收藏后 Activity 重建和进程重启仍存在；
- 取消收藏有效；
- 只保存稳定官方 ID；
- 未知 ID 被隔离；
- 不保存 Prompt、详情正文或用户敏感内容。

验证最近使用：

- 打开详情不会新增记录；
- 搜索、筛选、收藏、加入组合不会新增记录；
- 只有显式 `recordSkillUsed(skillId, usedAt)` 才写入；
- 排序按 `usedAt` 降序稳定；
- 未知 ID 不进入历史。

## 十、官方组合

通过页面和 Repository 测试验证：

- 只允许 44 项官方 ID；
- 名称可创建和编辑；
- 成员顺序稳定；
- 同一 Skill 不重复；
- position 不重复；
- 可选默认职责可以保存；
- 默认职责不进入官方 Prompt；
- 默认职责不能覆盖系统边界或安全规则；
- `expectedUpdatedAt` 冲突不会静默覆盖；
- 删除是软删除；
- 删除组合不改写历史参与者快照；
- 页面只调用 `JianyuRepository`，不直接访问 DAO。

## 十一、构建与测试命令

先确认 JDK 17，然后执行并记录每条命令、退出码和关键日志：

```powershell
.\gradlew.bat --version
.\gradlew.bat :app:testDebugUnitTest --stacktrace
.\gradlew.bat :app:lintDebug --stacktrace
.\gradlew.bat :app:assembleDebug :app:assembleRelease --stacktrace
```

连接真实可用模拟器或设备后执行：

```powershell
adb devices -l
.\gradlew.bat :app:connectedDebugAndroidTest --stacktrace
```

不得因为没有设备就声称 Instrumentation 通过；应明确标记“未执行”。

## 十二、界面与可访问性

在至少一个实际设备/模拟器核验：

- 360 dp 宽度；
- 系统大字体；
- 明暗主题；
- 旋转或 Activity 重建；
- TalkBack 焦点和可读标签；
- 搜索框、筛选、收藏、详情、组合按钮触控目标；
- 长文本可滚动，不被截断；
- 离线状态下本地 Catalog 仍可浏览；
- Catalog 数据损坏时显示错误，不放宽 Validator。

## 十三、Secret 与来源扫描

检查差异中没有：

- API Key、Token、密码、证书或个人信息；
- 来源不明的第三方 Prompt 正文；
- 把“研究来源”写成“正式实现来源”；
- 未经证据声称许可证或原创性已经完成核验。

## 十四、最终干净性

所有验证结束后执行：

```powershell
git status --short
git diff --exit-code
git diff --cached --exit-code
git rev-parse HEAD
```

最终报告必须区分：

```text
已实际执行并通过
GitHub CI 已通过
仅完成静态核对
尚未验证
```

报告至少包含：

1. 最终 PASS / FAIL / PASS WITH NOTES；
2. 精确 Base、Merge Base 和 Head；
3. 环境版本；
4. 差异文件清单；
5. 44 项解析统计；
6. 20 项历史资产逐项映射结果；
7. 人物风险与声明结果；
8. 特殊 Skill 边界结果；
9. 搜索筛选结果；
10. Validator 结果；
11. 收藏和最近使用结果；
12. 官方组合结果；
13. 架构与 Room v7 守卫；
14. 每条命令、退出码和关键日志；
15. 设备/UI/TalkBack 结果；
16. Secret 与许可扫描；
17. 最终工作区干净性；
18. 失败项、复现步骤和可能原因。

只把报告反馈给远端开发对话；不要修改、提交、推送、合并或标记 Ready。
