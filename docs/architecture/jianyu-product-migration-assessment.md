# 见域产品迁移与实现边界评估

> 状态：PR08-E 技术评估稿，供 PR08-F 整合与用户审批使用。
> 审计基线：`main@d6db5e1b825bab60852b6365eef0ef6deb5bb970`。
> 当前仓库：`elio-zwd/AI-Skill-Roundtable`。
> 目标仓库：`elio-zwd/jianyu-workbench`。
> 目标官网：`jianyu.my-elio.online`。
> 目标 Android `applicationId`：`com.elio.jianyu`。
> 冻结数据边界：旧 App 不保留；旧包 `com.elio.skillroundtable` 的 Room、Keystore、偏好设置、私有文件和会话均不迁移。
> 本文只评估技术影响和候选实现，不代表生产代码、数据库、DNS、官网或仓库设置已经迁移。

---

## 1. 结论

见域不能通过“替换应用名、包名和首页文案”完成迁移。当前工程是一套以 `ChatSession + Message + Character` 为核心的多人物顺序圆桌基线；目标产品则以“议题 + 阶段 + Skill + 资料 + 成果 + 来源关系”为核心。两者存在可复用的网络、流式生成、Key 调度、部分失败保留、音频处理和 Compose 分层基础，但现有导航、状态归属、Room Schema、调度请求语义和数据安全能力均不足以直接承载目标产品。

建议 PR09 采用以下总策略：

1. **按全新应用身份迁移**：同步修改 `applicationId`、namespace、Kotlin/测试包路径、Manifest、Activity、Room Schema 路径、脚本、CI 和发布元数据；不设计旧包数据桥接。
2. **先冻结新领域模型，再迁移页面和调度**：不能继续把用户消息主键当作运行 ID，也不能把 `roundIndex` 当作阶段。
3. **保留编排基础，替换编排契约**：将现有顺序生成、流式占位、预算和部分失败恢复抽为执行引擎；用显式的运行类型、参与者快照、阶段关系和持久化运行状态承载单 Skill、多 Skill、定向回应与交叉讨论。
4. **把资料、成果和音频提升为正式领域对象**：当前音频只是 `Message` 的可选文件字段，不足以表达“音频作为成果输出”。
5. **把第 35～49 题作为独立高风险数据工程实施**：加密导入导出、隔离校验、差异预览、快照、原子替换和回退不能建立在现有 Markdown 导出上，也不能与频繁变动的 Room Schema 并行开发。
6. **仓库改名、官网和发布入口放在实现稳定后执行**：先完成代码内品牌和链接清单，再进行仓库设置、DNS、HTTPS、隐私、条款和下载入口切换。
7. **PR08-F 批准前不启动 PR09 实施**：本文中的表名、算法和文件格式均为候选；另一个 AI 对修正 Head 完成只读复核、用户批准且 PR08-F 合并后，用户已授权 PR09-01～15 不等待最终视觉确认。

---

## 2. 审计范围与证据

本次只读审计覆盖以下真实调用链、配置和测试，不依据文件名推断行为。

| 领域 | 主要证据 |
|---|---|
| 顶层入口与导航 | `app/src/main/java/com/elio/skillroundtable/ui/App.kt`、`ui/navigation/AppDestination.kt`、`ui/navigation/AppNavHost.kt` |
| 会话、消息与页面状态 | `viewmodel/RoundtableViewModel.kt`、`ui/screens/roundtable/RoundtableRoute.kt`、`data/ChatSession.kt` |
| 圆桌编排与失败恢复 | `roundtable/RoundtableOrchestrator.kt`、`roundtable/RoundtableBudget.kt` 及相关测试 |
| Skill 配置与加载 | `skill/SkillLoader.kt`、`app/src/main/assets/skills_config.json`、`skills_summaries.json`、`assets/skills/` |
| Room | `data/RoundtableDatabase.kt`、`Character.kt`、`CharacterGroup.kt`、`ChatSession.kt`、`app/schemas/`、`RoundtableDatabaseMigrationTest.kt` |
| 音频 | `RoundtableViewModel.kt`、`audio/AudioTranscodeWorker.kt`、`AudioPlaybackManager.kt`、音频库 Route |
| 导出与删除 | `RoundtableViewModel.exportConversation`、`ConversationExport.kt`、`ChatRepository.deleteSession` |
| API Key | `network/ApiKeyPool.kt`、`EncryptedApiKeyStore.kt`、`network/keys/ApiKeyScheduler.kt` |
| 遥测与云端链 | `telemetry/TelemetryRepository.kt`、`InteractionChainStore.kt`、`CloudInteractionSettings.kt` |
| 包名、构建与发布 | `app/build.gradle.kts`、`settings.gradle.kts`、`AndroidManifest.xml`、`proguard-rules.pro`、`run.ps1` |
| CI 与测试 | `.github/workflows/android-ci.yml`、`app/src/test/`、`app/src/androidTest/` |
| 产品冻结契约 | PR #21、PR #22、PR08 总计划、任务清单、决策索引及“推进议题”文档 |

审计不包含实际 Gradle 构建、设备安装、性能压测、加密基准、真机文件系统切换实验、DNS 查询或官网部署验证。

---

## 3. 当前工程事实审计

### 3.1 导航、返回栈和页面域

当前 `MainAppContent` 在应用顶层创建一个共享 `RoundtableViewModel`，并用单一 `NavHostController` 管理五个目的地：

| 类型 | 当前路由 | 当前页面 |
|---|---|---|
| 顶层 | `roundtable` | 圆桌脑暴 |
| 顶层 | `characters` | 智囊大厅 |
| 顶层 | `audio-library` | 音频库 |
| 二级 | `settings/api-keys` | API Key 管理 |
| 二级 | `settings/telemetry` | 遥测与诊断 |

顶层导航使用 `popUpTo(startDestination)`、`saveState`、`restoreState` 和 `launchSingleTop`；二级页只使用 `launchSingleTop`，返回依赖 `popBackStack()`。遥测从圆桌进入时会依次压入 API Key 页和遥测页，以形成返回路径。

当前不存在以下目标路由或独立页面域：

- 问题优先首页与推荐确认；
- 议题列表、议题工作区和议题详情；
- 阶段时间线、推进议题三步确认；
- Skill 发现、分类、筛选和详情的正式路由；
- 资料库、成果库、个人背景；
- 导入导出、快照和恢复中心；
- 交叉讨论配置和定向回应历史。

**影响**：PR09 需要重新设计目的地模型和导航图，而不是在现有三个底部 Tab 上继续叠加对话框。议题 ID、阶段 ID、成果 ID 等必须成为明确路由参数或可恢复状态；系统返回、深链、进程重建和多层返回栈需要新增测试。

### 3.2 会话、消息、轮次和状态归属

当前 Room 领域对象只有：

- `ChatSession(id, title, createdAt)`；
- `Message(id, chatId, senderId, senderName, avatar, text, timestamp, isPending, roundIndex, audio...)`。

当前“会话”直接承载全部消息，没有议题、阶段、资料、成果、运行记录或来源关系。用户提问写入 `Message` 后，数据库返回的消息主键直接作为 `questionRunId`。后续编排通过“从该用户消息之后，读取到下一条用户消息之前”的消息窗口确定本次运行上下文。

`roundIndex` 的真实含义是：同一个用户问题下，选定参与者完成一批回答后，再次继续时产生的响应批次。它不表达：

- 议题内的阶段；
- 阶段目标；
- 推进方向和措施；
- 阶段继承；
- 阶段总结；
- 多种运行类型之间的关系。

`currentSessionId`、当前重试状态、正在输入的角色、预算追踪器、已答角色、冻结参与者快照和云端 Interaction 游标主要存在进程内。只有消息、会话、角色启用状态和少量设置被持久化。

**影响**：

- 不得将 `ChatSession` 简单改名为 `Topic` 后继续复用全部语义；
- 不得将 `roundIndex` 升级解释为 `stageIndex`；
- 需要显式运行对象，将“自由追问、推进议题、单 Skill 回答、多 Skill 回答、定向回应、交叉讨论、生成成果”区分开；
- 需要决定哪些 UI 状态必须通过 `SavedStateHandle` 恢复，哪些执行状态必须持久化到 Room。

### 3.3 调度、流式写入与失败恢复

当前编排器的真实流程是：

1. 使用全局 `Mutex` 阻止同一编排器并发运行；
2. 读取当前启用角色；
3. 以 `questionRunId` 定位用户问题；
4. 语义路由开启时获取问题向量并按相似度排序，否则沿角色顺序；
5. 冻结最多指定数量的参与者；
6. 按顺序为每位角色插入 pending 消息；
7. 流式更新 pending 文本；
8. 完成后删除 pending 并插入正式消息；
9. 单角色失败、超时或取消时删除 pending，已完成回复保留；
10. 预算和参与者缓存均以用户消息主键为键。

ViewModel 只允许一个活动圆桌 Job。超时或用户停止时，会在 `NonCancellable` 中删除该会话的 pending 消息；应用启动时还会全局删除所有 pending 消息。失败角色重试状态包含 `sessionId + questionRunId + characterIds`，但它主要保存在内存中。进程死亡后，可以保留已完成的正式消息，却不能完整恢复“为何失败、原参与者快照、剩余预算、运行类型和下一恢复动作”。

当前“自动下一轮/继续本轮”依赖内存预算与最后一条用户消息推导。切换进程后，预算追踪器重新建立，不具备严格的跨进程一致性。

**可复用部分**：

- 顺序执行与最小调用间隔；
- Key 尝试计划和请求预算；
- pending 流式占位；
- 单参与者超时；
- 已完成回复保留；
- 失败参与者子集重试；
- transcript 构造和本地/联网资料 Broker。

**不能直接复用的语义**：

- 以用户消息主键作为运行 ID；
- 全局启用角色等于当前议题 Skill 阵容；
- `roundIndex` 等于产品阶段；
- 内存缓存等于可恢复的参与者快照；
- “继续下一轮”等于“推进议题”；
- `targetCharacterIds` 等于显式定向回应或交叉讨论。

### 3.4 角色与 Skill 配置和加载

当前 Skill 是“静态资产 + Room 角色副本”的组合：

1. `SkillLoader` 从 `assets/skills_config.json` 反序列化 `SkillConfig`；
2. 从 `assets/skills/<folder>/SKILL.md` 读取主提示词并剥离 YAML frontmatter；
3. 运行时可选择 `examples/`、`references/` 中的 Markdown；
4. ViewModel 启动时把配置写入或更新 `characters` 表；
5. 现有数据库中的 `isActive` 可继续保留；
6. 角色分组以 `character_groups.characterIds` 的逗号分隔字符串存储；
7. 当前全局启用角色同时影响所有会话。

当前 `SkillConfig` 和 `Character` 缺少目标目录所需的正式字段：

- Skill 类型：人物视角、专业顾问、任务助手、工作流能力；
- 现实支持/思维拓展主价值与多标签；
- 领域、场景、输入、输出、风险、联网和材料要求；
- 来源、许可、版本、更新时间、知识时效；
- 人物模拟声明；
- 单 Skill、多 Skill或两者皆可；
- 推荐理由和能力重复检测所需元数据；
- 官方目录版本与用户收藏/最近使用状态的分离。

**影响**：PR09 应把“官方只读 Skill 定义”和“用户本地状态”分离，避免每次启动把完整 system prompt 复制为可变角色记录。逗号分隔 ID 应替换为关系表或稳定的值对象序列化，并建立引用完整性。

### 3.5 Room Entity、DAO、Schema 和 Migration Test

当前数据库：

```text
数据库类：com.elio.skillroundtable.data.RoundtableDatabase
文件名：roundtable_database
版本：5
实体：characters / chat_sessions / messages / character_groups
Schema：app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json
```

现有 Migration Test 覆盖 `1 → 5`、`2 → 5`、`3 → 5`、`4 → 5`，验证角色、会话、消息、音频列和自定义分组数据保留。CI 通过 `MigrationTestHelper` 在模拟器上执行 Instrumentation Test，并显式校验旧数据库类完全限定名对应的 Schema 路径。

当前数据层存在以下技术限制：

- `messages.chatId` 未声明 Room 外键和索引；
- 删除会话后分别删除 session 与 messages，仓库层未使用单一 `@Transaction`；
- 音频文件删除是数据库删除前的 best-effort 文件操作；
- `character_groups.characterIds` 为逗号分隔文本；
- 没有资料、成果、来源、个人背景、快照、导入任务和运行状态；
- 当前数据库未使用 SQLCipher 或其他数据库文件级加密；
- Android 备份配置允许备份，但具体排除项需要与目标安全策略重新核对。

由于目标 `applicationId` 将创建新的应用沙箱，且冻结为不迁移旧包数据，PR09 **不需要**为旧包数据库设计跨包 Migration。目标是在 `com.elio.jianyu` 下建立新的数据库身份和新的初始 Schema；是否保留 `RoundtableDatabase` 类名、旧版本历史或采用新类名，由 PR09 数据 PR 在精确计划和测试中决定，PR08-F 不提前冻结。

仍需保留的迁移纪律是：一旦目标 App 的新 Schema 发布或进入长期测试，后续每次结构变更都必须同步版本、Migration、导出 Schema 和 Migration Test。

### 3.6 音频与会话关系

当前音频没有独立实体。`Message` 直接保存：

- `audioFilePath`；
- `audioFormat`；
- `audioSizeBytes`。

TTS 先写入缓存 WAV，再立即把 WAV 路径写回消息并播放，然后通过 WorkManager 传入 `message_id + wav_path` 转码 AAC。Worker 完成后再次更新同一条消息的音频字段并删除 WAV。音频库只是查询“音频路径非空的消息”。

当前限制：

- 音频只能附着在消息上；
- 无法表达阶段总结音频、独立音频成果或一个成果的多种格式；
- 文件与数据库更新不是一个真正的跨文件系统事务；
- Worker 的输入使用消息主键，领域重构后需要稳定的音频资产 ID；
- 未显式记录转码状态、失败原因、时长、来源成果和校验值；
- 删除会话会尝试删除关联音频文件，但缺少统一的引用计数和孤儿清理策略。

**影响**：目标“音频作为成果输出”需要独立 `Artifact`/`AudioAsset` 关系。消息生成的音频可作为来源之一，但不应继续把所有音频资产生命周期绑定到消息列。

### 3.7 导出、导入、删除和归档

当前导出只支持：

- 把单个会话格式化为 Markdown；
- 复制到剪贴板；
- 明文保存到公共 `Documents/AI智囊圆桌/`。

当前没有：

- 结构化全库导出；
- 导入；
- 备份密码；
- 加密文件；
- 差异预览；
- 去重和冲突决策；
- 隔离校验；
- 恢复快照；
- 原子替换；
- 回退；
- 快照验证、备注和容量管理。

当前删除会话是硬删除：先清理内存 Interaction 链和关联音频文件，再分别删除 session 和 messages。没有议题归档、软删除、资料/成果保留策略、背景彻底清除、匿名占位和影响范围预览。

**影响**：目标归档、普通删除和彻底清除必须拆成不同命令，并通过领域事务和文件清理计划执行；不能继续让页面直接组合多个 DAO 删除调用。

### 3.8 API Key

当前 Key 池只管理用户自行导入的 BYOK Key，最多 50 个。完整 Key 存在 `noBackupFilesDir/gemini_api_keys.enc`，由 Android Keystore 中别名 `skill_roundtable_api_key_v1` 的 AES-256-GCM Key 加密，并通过 `AtomicFile` 写入。UI 读取掩码摘要；Key 状态、禁用、验证、冷却和会话绑定另存于 SharedPreferences。

该实现可继续作为“设备本地 Key 保险箱”的基础，但必须注意：

- 旧包 Keystore 和密文不迁移；
- 包名变更后用户需在新 App 重新导入 Key；
- 设备绑定 Keystore Key 不适合跨设备加密备份；
- 目标结构化导出默认不应包含 API Key、应用 PIN、生物识别凭据或设备绑定快照密钥；
- `session_key_<sessionId>` 需要迁移为与新运行/议题语义一致的绑定策略，或取消长期绑定；
- Key alias、文件名和错误文案应清理旧品牌技术标识，但修改 alias 会导致已有新包测试数据不可解密，必须在发布前一次性完成。

### 3.9 遥测与云端 Interaction

当前遥测默认级别是 `METADATA_ONLY`。Release 不允许开启正文调试；Debug 可临时启用 `CONTENT_DEBUG`，到期后自动清除预览。写入前会进行脱敏和长度限制，用户可以清空遥测。事件保存在 SharedPreferences，并非 Room。

云端 Interaction 链游标使用 `sessionId + characterId` 作为 Key，仅存在当前进程的 `ConcurrentHashMap`，不写入磁盘。删除会话时会清理该会话游标。长回答续写只有在用户允许云端链并且预算足够时才使用服务商返回的 `previousInteractionId`。

**影响**：

- 新产品不得把议题正文、资料正文、个人背景、成果正文、导入密码或 Key 写入默认遥测；
- 需要把 endpoint、运行类型、风险等级等限制为脱敏枚举/元数据；
- 云端链应与明确的运行和 Skill 参与者关联，且继续保持可关闭、可清理和不用于跨阶段隐式继承；
- 个人背景和高风险资料的日志审计要有独立测试；
- 旧包偏好设置不迁移，新 App 默认策略必须重新初始化。

### 3.10 测试与 CI

当前 Android CI 包含：

- Debug Kotlin 编译；
- JVM 单元测试；
- Lint；
- Debug APK；
- `aapt` 校验包名和 Launcher Activity；
- Release 签名配置校验；
- 开启 R8/资源压缩的 Release APK；
- 验证公开 CI Release APK 未签名；
- Room Schema 无未提交差异；
- API 30 模拟器上的全部 Instrumentation Test；
- 测试、Lint、R8 Mapping、Schema 和 APK 产物上传。

CI 当前硬编码：

- `com.elio.skillroundtable`；
- `com.elio.skillroundtable.MainActivity`；
- `app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json`；
- 通用 `app-debug.apk`/`app-release.apk` 路径和旧品牌文案。

包名迁移必须同步这些断言，否则即使源码编译成功，品牌和安装身份门禁也会失败。

---

## 4. Android 身份与品牌迁移影响

### 4.1 必须同步迁移的标识

| 项目 | 当前 | 目标或处理 |
|---|---|---|
| `applicationId` | `com.elio.skillroundtable` | `com.elio.jianyu` |
| namespace | `com.elio.skillroundtable` | `com.elio.jianyu` |
| 主源码包 | `app/src/main/java/com/elio/skillroundtable/` | 同步移动到 `com/elio/jianyu/` |
| JVM 测试包 | `app/src/test/java/com/elio/skillroundtable/` | 同步移动 |
| Instrumentation 测试包 | `app/src/androidTest/java/com/elio/skillroundtable/` | 同步移动 |
| Launcher Activity | `.MainActivity` 解析为旧 namespace | 解析并由 CI 验证为 `com.elio.jianyu.MainActivity` |
| Room Schema 路径 | 旧数据库类 FQN | 随数据库类 FQN 同步 |
| Root project 名 | `SkillRoundtable` | 候选 `JianyuWorkbench`，由最终规格冻结 |
| App 名 | AI 智囊圆桌 | 见域 |
| Theme/资源前缀 | `SkillRoundtable` 等旧标识 | 分批清理，避免同一 PR 混入视觉重构 |
| 脚本包名 | `run.ps1` 硬编码旧包 | 更新启动、PID、清理和提示 |
| CI 品牌断言 | 旧包、旧 Activity、旧 Schema | 更新为目标值并增加旧标识禁止项 |
| 发布产物 | `app-debug.apk` 等默认名 | 候选带产品名、版本、构建类型和 ABI 的稳定命名 |
| 文档与链接 | 旧仓库、旧路径、旧包 | 建立清单后统一更新 |

namespace 与 `applicationId` 应在同一身份迁移 PR 中完成，避免出现“编译包路径已经变更，但安装身份仍旧”或反向状态。

### 4.2 Manifest、Activity、ADB 和测试设备

Manifest 当前通过相对类名 `.MainActivity` 引用 Activity。namespace 更新后需要验证：

- 合并后的 Manifest；
- `aapt dump badging` 包名与 Launcher Activity；
- `adb shell am start -n com.elio.jianyu/com.elio.jianyu.MainActivity`；
- Instrumentation runner 目标包；
- WorkManager 初始化及 Worker 反射加载；
- 文件提供者或深链（若后续新增）的 authority。

新旧 `applicationId` 可同时安装。冻结边界是不保留旧 App，因此开发与验收流程应显式提供清理，而不是静默删除：

```powershell
adb uninstall com.elio.skillroundtable
adb install -r <new-apk>
adb shell pm path com.elio.jianyu
adb shell am start -n com.elio.jianyu/com.elio.jianyu.MainActivity
```

若只需要清空旧测试数据，可使用 `adb shell pm clear com.elio.skillroundtable`，但这不会卸载旧 App，不符合最终清理目标。脚本可增加明确的 `-RemoveLegacyApp` 参数；默认不应未经确认删除设备数据。

### 4.3 Room 数据库身份与 Schema 路径

包名切换会产生新的应用沙箱，因此旧包中的 `roundtable_database` 不会自动出现在新 App。冻结的“不迁移旧数据”意味着：

- 不读取旧包私有目录；
- 不共享签名桥接；
- 不导出再自动导入旧会话；
- 不复制旧 Keystore；
- 不为旧包数据库写跨包迁移服务。

候选选择：

**方案 A：新数据库类与新文件名，目标 App 从版本 1 开始。**

优点：技术标识干净，符合全新应用身份；不会把旧 `chat_sessions/messages` 误当作目标长期模型。
风险：必须一次性冻结目标初始 Schema，且需要清楚区分“旧工程历史 Schema”和“新产品 Schema”。

**方案 B：保留数据库类名或版本号，在新包内继续演进。**

优点：减少部分代码改名。
风险：容易让维护者误以为存在旧数据升级路径，也会把旧领域模型历史带入新产品。

本评估倾向方案 A，但不在 PR08-E 锁定类名、文件名或版本号。PR08-F 应冻结原则，PR09 数据 PR 再提交精确 Schema。

### 4.4 ProGuard/R8 与发布元数据

当前项目规则只保留通用反射元数据，主要依赖 Room、Retrofit、WorkManager、Serialization 的 consumer rules。迁移后必须实际执行优化 Release 构建和运行测试，重点检查：

- kotlinx.serialization 的新导入导出 Envelope；
- WorkManager Worker 构造与类名；
- Room 生成代码；
- 反射或按类名加载的组件；
- 导入格式版本适配器；
- 深链和 Activity；
- 崩溃堆栈、Mapping 上传和版本对应。

发布元数据至少需要统一：

- App 名、图标、包名、版本号；
- APK/AAB 文件名；
- Release Notes；
- 下载页版本与校验值；
- 隐私政策和服务条款 URL；
- 签名证书管理；
- CI 公开产物继续保持未签名，正式发布由受控环境签名；
- 仓库、官网和应用内“关于”链接。

---

## 5. 目标领域模型候选

本节只给出关系和约束候选，不冻结 Kotlin 类型、Room 表名、主键格式或列级实现。

### 5.1 最小核心关系

```text
Topic（议题）
  ├─ Stage（阶段，线性序列）
  │    ├─ ExecutionRun（一次执行）
  │    │    ├─ RunParticipant（参与 Skill 快照）
  │    │    └─ Message（用户/Skill/系统消息）
  │    ├─ StageMaterialRef
  │    └─ Artifact（成果）
  ├─ TopicSkill / 当前阵容
  ├─ TopicMaterialRef
  └─ TopicContextRef（用户确认带入的个人背景）

SkillDefinition（官方 Skill 定义）
  └─ SkillUserState（收藏、最近使用、本地启用偏好）

Material（资料）
  └─ SourceRecord（来源、发布/检索时间、时效、许可）

Artifact（成果）
  ├─ 来源 Topic / Stage / Run / Message
  └─ AudioAsset（可选音频派生资产）
```

### 5.2 议题与阶段

议题候选字段：

- 稳定 ID、标题、状态、创建/更新时间；
- 当前阶段 ID；
- 归档时间和恢复信息；
- 可编辑议题简报；
- 风险等级或最近风险评估摘要；
- 不直接嵌入全部 Skill ID、资料 ID 或成果正文。

阶段候选字段：

- 所属议题；
- 线性序号；
- 前一阶段 ID；
- 目标；
- 思维拓展/现实支持方向及具体措施；
- 状态：草稿确认流程、活动、完成、取消等；
- 创建来源；
- 继承摘要；
- 阶段总结草稿与确认状态应与正式成果区分。

关键约束：

- 只有“推进议题”第三步最终确认后，才在一个事务中创建新阶段并更新当前阶段；
- 同一议题的阶段序列必须唯一、连续可追溯；
- 取消确认流程不能留下空阶段；
- 不复制整个历史消息树；通过关系和快照表达继承；
- 不自动产生分叉阶段。

### 5.3 执行运行与消息

`ExecutionRun` 候选类型：

- `INITIAL_QUESTION`；
- `FREE_FOLLOW_UP`；
- `SINGLE_SKILL`；
- `MULTI_SKILL`；
- `DIRECTED_RESPONSE`；
- `CROSS_DISCUSSION`；
- `ARTIFACT_GENERATION`；
- `STAGE_SUMMARY_DRAFT`。

运行至少需要记录：

- 所属议题和阶段；
- 用户触发消息/目标；
- 运行类型；
- 参与者选择方式与理由；
- 状态：排队、运行、部分成功、成功、失败、已停止、可重试；
- 开始/结束时间；
- 错误类别和可读恢复动作；
- 网络/搜索策略快照；
- 预算摘要；
- 不保存 API Key 正文。

`RunParticipant` 应保存当次 Skill ID、顺序、状态和必要的定义版本快照，避免官方 Skill 更新后改写历史含义。

`Message` 应显式关联 `stageId` 和 `runId`，并区分角色、Skill、系统与用户。流式 pending 状态可以继续存在，但进程恢复时必须能从运行状态判断“清理、标记中断或允许重试”，不能一律静默删除后丢失原因。

### 5.4 Skill 阵容与单/多 Skill

建议区分：

- **官方 Skill 定义**：随 App 版本发布、只读、可版本化；
- **用户状态**：收藏、最近使用、是否隐藏；
- **议题当前阵容**：当前阶段默认可用 Skill；
- **运行参与者快照**：某次实际执行的 Skill。

单 Skill 和多 Skill 复用同一个执行引擎：

- 单 Skill：参与者列表长度为 1；
- 多 Skill：参与者列表长度大于 1；
- 定向回应：运行目标只有被点名 Skill，但不改变议题阵容；
- 交叉讨论：用户显式选择参与者和焦点，建立独立运行；结果仍属于当前阶段；
- 邀请 Skill：更新当前阵容，历史运行参与者快照不变；
- 移除 Skill：不删除历史消息、资料或成果。

### 5.5 资料、成果、个人背景和来源

资料需要支持议题内引用和全局汇总，建议实体正文只保存一份，再用关系表表达：

- 所属/引用议题；
- 首次引入阶段；
- 确认状态；
- 来源类型；
- 来源 URL/文件/用户输入；
- 发布、检索和最后核验时间；
- 内容摘要和校验值；
- 风险、许可和隐私标签。

成果需要区分：

- 草稿；
- 用户确认保存；
- 被后续成果替代但仍可追溯；
- 类型：判断、行动方案、知识笔记、交付稿、音频等；
- 来源阶段、运行、消息和资料；
- 版本与创建时间。

个人背景必须是独立、可见、可编辑、可停用和可删除的条目。带入某个议题时应建立用户确认的引用关系，而不是把所有背景拼接到每个 Prompt。普通删除不改写历史；彻底清除需要影响范围预览、二次确认和匿名占位策略。

### 5.6 音频作为成果

建议引入独立 `AudioAsset`：

- 稳定资产 ID；
- 来源成果/消息；
- 格式、大小、时长、采样率；
- 文件相对路径，不保存不可移植的绝对路径；
- 状态：待生成、生成中、待转码、可用、失败、删除中；
- 校验值；
- Worker 唯一任务名和重试策略；
- 创建/更新时间和错误摘要。

文件写入应先写临时文件，校验后原子重命名，再在事务中发布可用状态。数据库删除和文件删除需要可恢复的两阶段清理或孤儿扫描，避免数据库引用丢失但文件仍存在，或文件先删而事务失败。

---

## 6. 加密导入导出、快照和回退候选

### 6.1 三类数据载体必须分开

| 类型 | 用途 | 密钥特性 | 默认内容 |
|---|---|---|---|
| 可移植加密导出 | 用户跨设备/长期备份 | 由独立备份密码派生 | 当前库和选择的附件；不含设备绑定快照、API Key、应用锁凭据 |
| 设备绑定恢复快照 | 导入/回退安全点 | Android Keystore 设备密钥 | 当前本地库副本、必要附件和清单 |
| 临时隔离区 | 导入验证与构建候选库 | App 私有临时目录，生命周期短 | 未信任输入的解密结果和临时数据库 |

不能直接复用 `EncryptedApiKeyStore`：它证明了 Keystore + AES-GCM + `AtomicFile` 的本地能力，但备份文件需要独立密码和可移植密钥派生；恢复快照则可以使用设备绑定密钥。

### 6.2 可移植导出文件候选

候选 Envelope：

```text
magic / formatVersion
manifestVersion
createdAt
appVersion / schemaVersion
KDF 参数与 salt
AEAD nonce
加密后的 manifest + database payload + attachments
整体认证标签
可选的非敏感最小头部
```

候选算法方向：

- 内容加密：AES-256-GCM 或平台支持良好的等价 AEAD；
- 密码派生：优先评估 Argon2id；若 Android 依赖、体积或兼容性不满足，再评估 scrypt/PBKDF2；
- 每次导出使用随机 salt 和 nonce；
- 不在文件中保存备份密码、密保答案或可直接解密的设备密钥；
- 具体算法、参数、依赖和兼容版本必须通过移动端性能、安全和许可评估后冻结。

### 6.3 导入状态机

建议按以下不可跳步的状态机实现：

1. 通过 Storage Access Framework 选择文件；
2. 流式复制到 App 私有隔离区；
3. 检查大小上限、可用空间、路径穿越、压缩炸弹和文件数量；
4. 解析最小头部与格式版本；
5. 使用备份密码完成认证解密；
6. 验证 manifest、哈希、Schema 版本、外键、枚举和引用完整性；
7. 在隔离数据库中打开并执行语义校验；
8. 生成分类汇总和逐项差异；
9. 相同数据自动去重；冲突数据等待用户选择当前、备份或两者；
10. 用户选择“合并”或“替换”并最终确认；
11. 创建当前库恢复快照；
12. 立即重新打开并验证快照；
13. 在临时目标库中执行合并/替换；
14. 运行数据库完整性、外键、附件和业务不变量校验；
15. 关闭当前 Room、停止相关 Worker/Flow 写入；
16. 在同一私有文件系统内执行候选库原子切换；
17. 重新打开数据库并进行启动健康检查；
18. 成功后保留恢复快照；失败则恢复原库并展示失败阶段；
19. 清理隔离明文和临时文件。

在第 16 步前不得修改当前正式库。若底层文件系统、WAL/SHM、Room 单例或运行中的 Worker 无法证明安全切换，应改用“新数据库文件构建完成后，受控重启进程再切换”的方案，而不是声称单个 SQL 事务可以覆盖文件、附件和 Keystore。

### 6.4 快照规则映射第 35～49 题

| 冻结行为 | 技术承载候选 |
|---|---|
| 相同数据去重、冲突可选 | 稳定导出 ID、内容哈希、版本/来源比较和用户决策表 |
| 替换前创建并验证快照 | 快照清单、设备绑定 AEAD、创建后 reopen + hash/DB 校验 |
| 分类汇总后逐项展开 | 独立 Diff Engine 输出统计与分页明细 |
| 隔离校验、原子写入 | 私有 quarantine、临时 DB、同文件系统 rename/受控重启 |
| 失败不修改当前库 | 提交点前只写临时载体；失败路径强制保留原库 |
| 快照长期保留 | 独立快照目录和元数据索引；用户主动删除 |
| 操作摘要不含敏感正文 | 本地审计事件只记录阶段、数量、耗时、结果和错误类别 |
| 设备安全密钥保护 | 专用 Keystore alias，与 API Key alias 分离 |
| 容量不足只提醒 | 预估所需空间、阻止操作并给出清理入口；不自动删除 |
| 回退前再建快照 | 回退命令先执行同一快照流水线 |
| 快照元数据与备注 | 名称、来源、时间、数据量、大小、状态、备注 |
| 不含备份密码/锁凭据 | 快照 manifest 白名单 |
| 损坏快照保留并标记 | 验证状态与错误摘要；删除由用户决定 |
| 创建后和恢复前验证 | 两个独立验证时间与结果 |
| 手动快照 | 复用快照服务，来源类型为 MANUAL |
| 导出默认不含快照 | Export manifest 默认排除 snapshots 目录 |

### 6.5 原子性边界

“原子写入”不能被简化为一个 Room `@Transaction`。目标操作同时涉及：

- 数据库主文件、WAL、SHM；
- 成果和音频附件；
- 快照文件；
- manifest；
- App 进程中的 Room 单例、Flow 和 WorkManager。

PR09 应明确定义提交点、幂等恢复标记和崩溃恢复测试。至少覆盖：

- 复制中断；
- 密码错误；
- AEAD 认证失败；
- manifest 损坏；
- Schema 不支持；
- 空间不足；
- 快照创建后崩溃；
- 临时库构建后崩溃；
- 文件切换中断；
- 新库打开失败；
- 附件缺失；
- 回退失败。

---

## 7. 高风险与来源时效的技术承载

高风险和来源时效不能只写入 Skill Prompt。候选承载包括：

- `riskLevel`：普通、需谨慎、高风险；
- `riskDomain`：医疗、法律、金融等；
- `sourceRequired`；
- `sourcePublishedAt`、`retrievedAt`、`lastVerifiedAt`；
- `freshnessPolicy` 或有效期；
- `disclosureType`：人物模拟、非专业结论、信息可能过时；
- `humanReviewRecommended`；
- `decisionOwner = USER`；
- 运行前/结果前的 UI 告知状态；
- 成果保存时保留条件、不确定性和来源关系。

实现要求：

1. 高风险运行在进入模型前生成结构化政策上下文；
2. 结果解析后检查是否缺少来源、时效、条件和不确定性；
3. UI 展示与导出均保留风险说明；
4. 遥测只记录风险枚举，不记录敏感正文；
5. 来源链接、检索时间和引用成果可追溯；
6. 人物型 Skill 明确为 AI 生成视角，不代表本人；
7. 不提供诊断、法律结论或收益保证。

---

## 8. 仓库改名影响与时机

### 8.1 推荐时机

仓库改名应在以下条件满足后执行：

- `com.elio.jianyu` 身份迁移和品牌资源已进入稳定分支；
- CI、脚本、Schema 和发布产物已不依赖旧仓库名；
- README、文档和官网链接清单已准备；
- 第一个“见域”候选 Release 即将形成，但尚未公开分发；
- 所有并行开发分支已完成重基准化或明确更新 remote 的方式。

不建议在 PR09 前半段改名。GitHub 通常提供重定向，但不能把重定向当作永久兼容合同，尤其是 raw 链接、徽章、第三方自动化、克隆地址、文档固定链接和发布下载地址。

### 8.2 链接清单

改名前必须检索并更新：

- README 徽章、克隆命令和仓库链接；
- `AGENTS.md`、规划、架构、测试和环境文档；
- Issue/PR 模板、CODEOWNERS、Security、Contributing；
- GitHub Actions 中仓库名或 Artifact 文案；
- Release Notes、APK/AAB 下载链接和校验文件；
- Android “关于”页、隐私政策和服务条款链接；
- 官网下载页、更新检查和支持入口；
- 第三方索引、分享链接和二维码；
- 本地/远端 AI 交接 Prompt；
- Git remote 更新说明。

仓库设置改名属于管理员操作，不应伪装为普通文件 PR。执行后需单独验证 clone、fetch、Actions、Releases、Issues、PR、raw 文件和旧链接重定向。

### 8.3 回滚

若改名后发现阻塞，可以在未发布前改回旧名；但外部缓存、Release URL 和第三方索引可能已经扩散，因此真正的回滚成本会随公开发布时间增加。应保留完整链接清单和切换记录。

---

## 9. 官网、DNS、HTTPS 与发布入口

当前仓库未在本评估中实施 `jianyu.my-elio.online`。PR09/发布阶段至少需要定义：

### 9.1 DNS 与托管

- 明确托管平台；
- 根据平台配置 CNAME/A/AAAA；
- 验证域名所有权；
- 自动签发和续期 HTTPS 证书；
- 强制 HTTPS；
- 记录 DNS TTL 和回滚值；
- 不在仓库提交 DNS/平台密钥。

### 9.2 最小官网页面

- 产品介绍和当前能力边界；
- 隐私政策；
- 服务条款；
- 高风险与人物模拟声明；
- 下载入口；
- Release 版本、发布日期、最低 Android 版本；
- APK/AAB 来源、SHA-256 校验和签名说明；
- GitHub 仓库、Issue/支持入口；
- 开源许可与第三方声明；
- 联系方式和文档入口。

### 9.3 下载与发布

- 正式包不得使用 CI 临时签名；
- 公共 CI 未签名 Release 只用于构建验证，不应作为普通用户下载入口；
- 正式签名产物应在受控发布环境生成；
- 下载页必须绑定准确版本和校验值；
- 明确侧载风险、系统权限和升级方式；
- 包名、版本、隐私政策 URL 和官网必须一致；
- 发布前完成商标、应用商店重名和域名内容复核。

---

## 10. 风险矩阵

| 风险 | 等级 | 说明 | 缓解 |
|---|---|---|---|
| 把 `roundIndex` 误当阶段 | 高 | 会破坏阶段语义和历史追溯 | 新建显式 Stage/Run 模型 |
| 大范围包路径迁移产生漏改 | 高 | Manifest、测试、Worker、CI 或 Schema 可编译/运行失败 | 独立身份 PR、全仓旧标识扫描、aapt/adb/Release 验证 |
| 导入替换损坏当前库 | 极高 | 可能造成不可恢复的数据损失 | 隔离库、已验证快照、明确提交点、故障注入 |
| 设备绑定密钥被误用于备份 | 高 | 备份无法跨设备恢复 | 可移植备份使用独立密码派生密钥 |
| 音频文件与 DB 不一致 | 中高 | 产生孤儿文件或丢失成果 | 独立资产状态、临时文件、原子重命名、清理任务 |
| Skill 更新改写历史语义 | 中高 | 历史回答无法解释 | 保存参与者定义版本快照 |
| 全局启用角色污染议题阵容 | 高 | 不同议题互相影响 | 议题阵容和用户目录状态分离 |
| 进程死亡丢失重试语义 | 高 | 用户看到残留结果却无法判断恢复 | 持久化 Run 状态和参与者状态 |
| 高风险仅靠提示词 | 高 | UI、导出和日志无法一致执行边界 | 结构化风险/来源元数据和门禁 |
| 仓库过早改名 | 中 | 并行分支、链接和自动化中断 | 放到代码稳定后的独立管理员阶段 |
| 官网下载未绑定签名/校验 | 高 | 供应链与用户信任风险 | 受控签名、版本化 URL、SHA-256、发布核验 |
| PR09 巨型合并 | 高 | 难审查、难回滚、冲突多 | 按身份、数据、导航、执行、安全等原子拆分 |

---

## 11. 验证要求

### 11.1 身份迁移

- 全仓扫描旧包、旧 App 名、旧仓库名和旧主题标识；
- `compileDebugKotlin`、Unit Test、Lint、Debug/Release 构建；
- R8 Mapping 与 Release 安装启动；
- `aapt dump badging`；
- 模拟器/真机同时安装新旧包，验证显式卸载流程；
- `run.ps1` 启动、PID 和 Logcat；
- Instrumentation runner 和 WorkManager Worker。

### 11.2 数据模型

- 新建库 Schema 测试；
- 外键、唯一约束、索引和事务测试；
- 创建阶段最终确认原子性；
- 取消推进不产生阶段；
- Skill 增删不删除历史；
- 归档、普通删除和彻底清除；
- 进程死亡后运行状态恢复；
- 后续版本 Migration Test。

### 11.3 调度

- 单 Skill、多 Skill；
- 定向回应不改变阵容；
- 显式交叉讨论参与者和焦点；
- 顺序、上下文和参与者快照；
- 部分成功、超时、停止、失败重试；
- 重启后可恢复状态；
- 预算和 Key 调度；
- 联网/材料 Broker；
- 高风险来源与时效。

### 11.4 导入导出与快照

- 正确密码、错误密码、被篡改文件；
- 大文件、压缩炸弹、路径穿越和空间不足；
- 同库、旧版本、新版本、不支持版本；
- 去重、冲突三种选择、合并和替换；
- 各阶段故障注入；
- 快照创建后/恢复前验证；
- 回退前快照；
- 损坏快照保留；
- 明文临时文件清理；
- 性能、内存、耗时和电量基准。

### 11.5 隐私与发布

- 默认遥测不含正文、资料、个人背景、Key 和密码；
- Debug 正文预览到期清理；
- 导出不含 Key、应用锁凭据和设备绑定快照；
- 备份规则；
- 正式签名与公开未签名 CI 产物区分；
- 官网 HTTPS、政策链接、下载校验和；
- Release 回滚和旧版本兼容策略。

---

## 12. PR08-F 冻结结果与 PR09 工程决策

PR08-F 已冻结：全新应用沙箱、不迁移旧包数据、Stage / Run / Message 产品语义、资料 / 成果 / 个人背景删除边界、快照与导入失败恢复行为，以及 PR09 的能力域和依赖顺序。

以下内容属于工程实现选择，不要求用户在 PR09 开始前决定，也不得从 PR08 文档臆测：

1. 新数据库类名、文件名、初始版本和精确表结构；
2. ID 类型和稳定导出 ID 编码；
3. 已冻结产品语义对应的字段、外键、事务与级联实现；
4. Skill 官方定义的版本化存储格式；
5. 可移植导出 Envelope 与 KDF / AEAD 算法和参数；
6. 快照目录、容量提醒阈值和保留 UI 的精确实现；
7. 原子替换采用同进程文件切换、受控重启或其他可证明方案；
8. 音频资产格式、清理任务和存储布局；
9. 官网托管平台和发布渠道；
10. 仓库改名与公开发布的精确日期。

每项工程选择必须在对应 PR09 实施 PR 的可执行计划中给出候选比较、失败测试、回滚和验证证据；其中数据库、加密与原子替换方案需要专项评审。

---

## 13. 本 PR 验证状态

### 已完成

- 基于指定 Base SHA 只读审计导航、会话、消息、编排、失败恢复、Skill、Room、音频、导出、删除、Key、遥测、测试和 CI；
- 对照 PR #21、PR #22 和第 1～62 题决策索引；
- 明确当前事实、冻结行为、候选方案和未冻结事项；
- 文档范围不包含生产代码、测试、数据库、Gradle、资源、CI、仓库设置、DNS 或服务器修改。

### 未执行

- Gradle 编译、单元测试、Lint、Debug/Release 构建；
- Instrumentation Test、模拟器和真机；
- Room 候选 Schema 生成或迁移；
- 加密算法性能、安全审计和文件格式验证；
- 导入故障注入；
- 音频迁移和 WorkManager 回归；
- DNS、HTTPS、官网和下载入口验证；
- 仓库改名和链接重定向验证。

本文不能作为“上述实现已经完成或测试通过”的证据。
