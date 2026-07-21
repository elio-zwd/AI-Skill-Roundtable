# PR 05：开源治理、文档与长期维护

> 状态：Planned  
> 优先级：P1/P2  
> 前置依赖：PR 01～PR 04 已合并，代码和构建状态稳定  
> 后续依赖：无；完成后进入公开 Beta 准备  
> 覆盖审查项：F07（全面文档同步）、F10、F14，以及五个 PR 的最终收口

---

## 1. 任务目标

让陌生用户和贡献者仅通过仓库文档就能准确理解：

1. 项目是什么、当前能做什么、不能做什么。
2. 如何构建、安装、配置自己的 API Key。
3. 圆桌究竟如何调度、调用量如何控制。
4. 哪些数据会发送到模型服务商、哪些数据会保存在本机。
5. 名人角色是 AI 模拟，不是本人、授权代表或官方背书。
6. 每个 Skill、头像、参考资料的来源和授权边界。
7. 如何贡献代码、报告安全问题、查看版本变化。
8. 当前架构文档与真实代码完全一致。

本 PR 不只是“润色 README”，而是建立长期可维护的文档与第三方内容治理机制。

---

## 2. 本 PR 明确不做什么

- 不继续增加角色、功能或模型。
- 不重写 PR 02 已稳定的圆桌调度。
- 不修改 PR 03 的隐私默认策略。
- 不大规模改 UI；只允许增加必要的“AI 模拟”标识和隐私入口。
- 不声称已获得任何名人或媒体授权，除非仓库有明确书面证据。
- 不把来源不明内容自动纳入 MIT License。
- 不为了文档好看而夸大性能、实时性或“秒播”。

---

## 3. 执行 AI 必须先读的文件

1. `AGENTS.md`
2. `docs/planning/pr-execution-master-plan.md`
3. PR 01～PR 04 的任务文档与交付报告
4. `README.md`
5. `LICENSE`
6. `app/build.gradle.kts`
7. `app/src/main/AndroidManifest.xml`
8. PR 02 的 Roundtable Orchestrator、Budget、Retry、Key Lease 实现
9. PR 03 的 Telemetry、Cloud Interaction 设置实现
10. `LiveApiClient.kt`
11. `AudioTranscodeWorker.kt`
12. `SkillLoader.kt`
13. `assets/skills_config.json`
14. 所有 `assets/skills/**/SKILL.md`
15. `docs/architecture/`
16. `docs/decisions/`
17. `docs/protocols/`
18. `docs/features/`
19. `docs/environment/`
20. `docs/planning/`
21. `tools/README.md`
22. `test/README.md`
23. 仓库内所有图片、头像和生成提示说明

先运行文档漂移扫描：

```powershell
git grep -n -I -E '内置 10 个|7 个 GitHub|20 位|反检测|防屏蔽|组间并发|组内串行|BuildConfig\.GEMINI_API_KEY|file:///|D:\\My_Elio|E:\\MobileApp|gemini-3\.5-flash|text-embedding-004|流式秒播|实时播放|Debug 签名|fallbackToDestructiveMigration'
```

执行 AI 必须逐项判断是当前事实、历史记录还是过时描述，不能机械全局替换。

---

## 4. 固定文档原则

### 4.1 README 只做入口

README 应控制在“新用户能快速判断和开始”的范围，不继续堆积所有 ADR、完工报告和历史 Bug。

### 4.2 事实优先于宣传

所有能力描述必须能在当前代码中找到证据。不能写：

- “真实名人观点”。
- “官方授权人格”。
- “完全安全”。
- “绝不泄露”。
- “真正实时秒播”，如果代码是收完 PCM 后播放。
- “后一个角色看到前序发言”，除非 PR 02 已实际保证。
- “免费无限使用”。

### 4.3 第三方内容与代码许可证分离

根目录 MIT License 默认只覆盖项目原创代码和明确可授权内容。第三方文章摘要、名人公开言论、肖像、媒体资料和外部 Skill 可能受各自版权约束，必须单独登记。

### 4.4 文档分层

```text
README.md                         新用户入口
AGENTS.md                         AI/贡献者工作规则
CONTRIBUTING.md                   人类贡献流程
SECURITY.md                       安全报告与支持范围
CHANGELOG.md                      版本变化
THIRD_PARTY_NOTICES.md            第三方来源和授权边界
docs/architecture/               当前架构事实
docs/decisions/                  为什么做出关键决策
docs/environment/                构建和环境
docs/features/                   功能使用与限制
docs/planning/                   计划和交付记录
```

---

## 5. 详细实施步骤

### 5.1 重写 README

建议固定结构：

#### 1. 标题和一句话定位

示例：

```markdown
# AI 智囊圆桌

一款原生 Android 多角色 AI 讨论应用。多个基于公开资料构建的 AI 视角按顺序阅读前序发言，对同一问题补充、质疑和综合。
```

不得把模拟角色描述为本人。

#### 2. 重要声明

README 首屏可见位置必须包含：

```markdown
> 本项目中的人物角色均为基于公开资料构建的 AI 模拟，不代表本人观点，也不表示获得本人、所属机构或品牌的授权与背书。
```

并链接到完整说明。

#### 3. 截图或演示

- 使用仓库内自有截图。
- 截图不得包含真实 API Key、私人对话、手机号或邮箱。
- 如果没有合规截图，先留清晰占位，不从网络抓取图片。

#### 4. 核心能力

只列当前真实能力，例如：

- 严格顺序圆桌。
- App 内 BYOK Key 管理。
- Android Keystore 加密。
- 可选联网搜索。
- 可选 TTS 生成与本地音频管理。
- Metadata 遥测和临时内容调试。

每项避免营销夸大。

#### 5. 工作方式

用一张简化流程图说明：

```text
用户问题 → 角色 A → A 入库 → 角色 B 读取 A → B 入库 → ...
```

注明默认角色上限、搜索上限和请求预算，以 PR 02 代码为准。

#### 6. 快速开始

引用 PR 01 的真实流程：

1. JDK 17 / Android SDK。
2. Clone。
3. `./gradlew.bat assembleDebug`。
4. 安装。
5. App 内导入自己的 Key。

不要复制过多环境细节；链接到 `docs/environment/`。

#### 7. 隐私和费用

简明说明：

- 用户问题、选中的 Skill 内容和上下文会发送给 Gemini API。
- 默认遥测只保存元数据。
- 云端 Interaction 默认关闭/按 PR 03 实际值说明。
- API 额度和费用由用户自己的 Key/Google 账号承担。
- 多角色和联网搜索会增加调用量。

#### 8. 当前限制

至少列：

- 名人视角并不保证准确复现本人。
- AI 输出可能出错。
- 联网来源和模型可用性受服务商影响。
- TTS 当前行为的真实限制。
- Android 版本要求。
- 项目仍处 Beta。

#### 9. 文档入口

链接到架构、环境、隐私、贡献、第三方声明。

#### 10. License

清楚区分代码 MIT 和第三方内容各自条款。

---

### 5.2 精简和同步 AGENTS.md

当前 AGENTS 既是规则又包含大量历史状态，容易陈旧。

新结构建议：

1. 项目一句话定位。
2. 当前技术栈，版本从实际 Gradle 文件读取。
3. 当前目录结构。
4. 必须遵守的工作规则。
5. 构建和测试命令。
6. 安全规则。
7. 数据库迁移规则。
8. UI/设备验收规则。
9. 核心文档入口。
10. 明确禁止事项。

删除或迁移：

- 长篇已交付功能流水账。
- 已修复的历史 Bug。
- 已下线模型。
- 旧 7 人/10 Key 描述。
- 个人绝对路径。
- `file:///` 链接。
- 与当前 PR 02/03/04 不一致的调度、遥测和 Release 描述。

历史信息可保留在 `CHANGELOG.md`、ADR 或 planning walkthrough，不应继续作为 AI 当前工作事实。

---

### 5.3 新增 CONTRIBUTING.md

至少包含：

- 开发环境入口。
- Fork/Branch/Commit/PR 流程。
- 提交格式 `type: 中文说明`。
- 修改前先读 AGENTS。
- 必须运行的测试。
- UI 修改需设备截图/验证。
- 数据库修改必须 Migration + Schema + Test。
- 不提交 Key 和签名文件。
- 新增 Skill 的来源和许可证要求。
- PR 描述模板。

建议 PR Checklist：

```markdown
- [ ] 仅修改本 PR 范围
- [ ] 编译通过
- [ ] 单元测试通过
- [ ] Lint 通过
- [ ] 未提交敏感信息
- [ ] 文档与实现同步
- [ ] 数据库变更包含 Migration/Schema/Test
- [ ] 新第三方内容已登记来源和许可证
```

---

### 5.4 新增 SECURITY.md

至少包含：

- 支持的版本范围。
- 如何私下报告漏洞。
- 不要在公开 Issue 粘贴 API Key、私钥或私人对话。
- 漏洞报告应包含版本、复现步骤、影响。
- Key 泄露后的建议：立即在服务商控制台撤销/轮换。
- 本项目不代管用户 Key。
- 安全范围：Key 存储、日志泄露、数据库、网络请求、依赖漏洞。
- 不承诺固定响应时间；不要编造邮箱。若仓库没有安全联系邮箱，可使用 GitHub Private Vulnerability Reporting（若已启用）或让维护者后续补充明确渠道。

禁止在文档中放用户未确认的私人邮箱。

---

### 5.5 建立 CHANGELOG.md

采用 Keep a Changelog 风格或简化版本：

```markdown
# Changelog

## [Unreleased]
### Added
### Changed
### Fixed
### Security

## [0.1.0] - YYYY-MM-DD
```

将现有近期重大变化整理为：

- BYOK + Keystore。
- 严格顺序圆桌。
- 请求预算和错误分类。
- 隐私遥测收口。
- Release/CI。

不要把每个细小提交原样复制成流水账。

日期必须使用真实发布日期；未发布时保持 Unreleased。

---

### 5.6 新增 THIRD_PARTY_NOTICES.md

建立清晰表格：

| 类型 | 名称/路径 | 来源 | 作者/权利人 | 许可证/使用依据 | 本仓库处理方式 |
|---|---|---|---|---|---|
| 代码依赖 | compose-markdown | 官方仓库 | ... | 对应 License | 仅依赖 |
| Skill | elon-musk-skill-main | 原始来源 URL | ... | 待核实/指定许可证 | 不默认纳入 MIT |
| 头像 | avatars/elon_musk.jpg | 用户生成/具体工具 | 用户/待确认 | 项目自有或使用说明 | App 资产 |
| 引用 | 某公开访谈 | 原始页面 | 发布方 | 引用/摘要 | 仅用于研究型提示 |

要求：

- 没有来源时写“待核实”，不能编造。
- 没有许可证时写“未发现明确许可证”，不能默认 MIT。
- 外部 Skill 如果包含许可证文件，应保留并链接。
- 第三方依赖 License 可通过依赖清单核对。
- 肖像和生成图应注明生成方式、是否基于真人照片、维护者的使用声明。

如果清点工作量很大，至少完成所有随 APK 分发的 20 个 Skill 和头像；不能只做 2 个示例就宣称完成。

---

### 5.7 统一 Skill 元数据规范

为每个 Skill 定义统一 frontmatter 字段。不要立即破坏 SkillLoader 已解析字段；先确认兼容性。

建议：

```yaml
---
name: elon-musk-perspective
description: ...
version: 1.0.0
updated_at: 2026-07-21
persona_type: public-figure-simulation
simulation_disclaimer: true
source_summary: Based on public biographies, interviews and writings.
source_index: SOURCES.md
license_note: See THIRD_PARTY_NOTICES.md
---
```

每个 Skill 目录建议新增：

```text
SOURCES.md
```

结构：

```markdown
# Sources

## Primary/Original
- 标题 — 作者/发布方 — 日期 — URL — 用途

## Secondary
- ...

## Notes
- 哪些内容是总结或推断
- 哪些直接引语已核对
- 最后核对日期
```

执行规则：

- 原始来源优先。
- 直接引语必须能追溯。
- 不确定的语录不得标为原话。
- 长篇第三方原文不应复制入仓库；使用摘要和链接。
- 来源 URL 可放文档中。
- 不要求本 PR 对所有人物观点做事实审计，但必须建立可追踪框架并完成现有资产基础登记。

---

### 5.8 增加永久“AI 模拟”标识

只做最小 UI 修改：

- 角色详情页显示“AI 模拟视角”。
- 每个角色回复头部或头像附近显示低干扰标签：`AI 模拟`。
- 首次进入圆桌可展示一次整体说明。
- 设置/关于页面提供完整免责声明入口。

建议短文案：

```text
AI 模拟
```

完整说明：

```text
该角色基于公开资料构建，用于提供一种分析视角，不代表本人真实观点，也不表示获得本人或相关机构授权、认可或背书。
```

禁止让模型只在首次回答中自己输出免责声明，因为 Prompt 可能失效；UI 标识才是稳定边界。

---

### 5.9 修复 TTS 文档与实现不一致（F10）

本 PR 固定选择低风险方案：**先修正文档，不在本 PR 重写音频引擎。**

当前实现如仍为：

```text
WebSocket 接收 PCM → 内存累积 → 收到 turnComplete → 写 WAV → 播放
```

文档必须准确写成：

```text
通过 WebSocket 获取音频数据，接收完成后写入 WAV 并播放；随后可在后台转码为 AAC。
```

删除或修改：

- “边收边播”。
- “首次秒播”。
- “真正实时流式播放”。
- “无延迟”。

可以写：

- “无需等待 AAC 转码即可先播放 WAV”。
- “网络接收完成后立即播放”。

新增独立后续任务文档或 Issue 建议：

```text
实现 AudioTrack 边收边播、增量文件写入和长文本内存控制
```

但不得在本 PR顺手实现。

---

### 5.10 全面同步架构文档

重点更新：

```text
docs/architecture/system-architecture.md
docs/architecture/audio-engine-architecture.md
docs/features/debug-panel-and-telemetry-diagnostics.md
docs/protocols/gemini-api.md
```

必须与 PR 02～04 一致：

- 严格顺序圆桌，不再写 Key 组并发。
- Key Lease 和确定性重试。
- 请求预算。
- 角色级或关闭的 Interaction 链。
- Metadata-only 默认遥测。
- Content Debug 的 24 小时限制。
- Release 构建和 Room Migration 策略。
- TTS 接收完成后播放。

架构图中的类名必须真实存在。禁止画尚未实现的未来架构却不标“Planned”。

---

### 5.11 处理旧 ADR 和 Planning 文档

历史文档可以保留当时决策，但要防止读者把它当当前事实。

规则：

- 已被取代的 ADR 顶部增加：

```markdown
> 状态：Superseded by ADR-XXX
```

- 已过时的 walkthrough 顶部增加：

```markdown
> 历史交付记录：本文描述当时版本，不代表当前实现。当前架构见 ...
```

- 不应为了“文档一致”删除所有历史记录。
- 当前事实只由 README、AGENTS、Architecture 和最新 ADR 表达。

如果 PR 02/03 引入新的重大决策，应补 ADR，例如：

```text
adr-009-serial-roundtable-and-deterministic-key-leases.md
adr-010-privacy-first-telemetry-and-cloud-interaction-default.md
```

ADR 应包含背景、决策、替代方案、后果和状态。

---

### 5.12 统一术语

全仓统一：

| 避免术语 | 推荐术语 |
|---|---|
| 反检测、防屏蔽 | 速率限制保护、配额保护、退避策略 |
| 内置 10 Key | 用户导入 Key / 可用 Key |
| 名人本人 | AI 模拟视角 |
| 真实观点 | 基于公开资料生成的推断 |
| 流式秒播 | WebSocket 音频接收完成后播放 |
| 无限上下文/无限调用 | 具体模型限制和请求预算 |
| 绝对安全 | 使用 Keystore 加密并遵循数据最小化 |

全局替换后必须人工检查语义，不得破坏历史引语或代码标识。

---

### 5.13 校正 LICENSE 署名和范围说明

当前 License 版权名必须与维护者公开使用的署名保持一致。

执行 AI 不得自行决定法律身份。处理方式：

1. 查看 README、GitHub 用户名和仓库已有署名。
2. 如果存在明显冲突，在交付报告列出并使用仓库所有者明确确认的署名。
3. 若没有确认，不擅自改成真实姓名；可暂时保持并在 README 说明维护者账号。
4. `THIRD_PARTY_NOTICES.md` 明确第三方内容不当然由 MIT 覆盖。

可在 README License 部分写：

```text
项目原创代码采用 MIT License。人物名称、第三方资料、引用、图片和外部 Skill 仍受各自权利与许可证约束，详见 THIRD_PARTY_NOTICES.md。
```

这不是法律意见，不要写过度保证。

---

### 5.14 可选：增加文档一致性检查脚本

建议新增：

```text
tools/check-doc-consistency.ps1
```

扫描明显已禁止模式：

- 作者绝对路径。
- `file:///`。
- `BuildConfig.GEMINI_API_KEY`。
- `内置 10 个 Key`。
- `反检测`。
- `组间并发`。
- `text-embedding-004`。
- 旧包名（如 PR 04 已迁移）。

注意：脚本只用于发现，不替代人工审查。历史文档允许例外时，应通过明确 allowlist 路径处理，而不是完全关闭规则。

可将脚本加入 PR 04 建立的 CI。

---

## 6. 预计新增/修改文件

新增：

```text
CONTRIBUTING.md
SECURITY.md
CHANGELOG.md
THIRD_PARTY_NOTICES.md
可能新增 docs/decisions/adr-009-*.md
可能新增 docs/decisions/adr-010-*.md
可能新增 tools/check-doc-consistency.ps1
各 Skill 目录的 SOURCES.md
```

主要修改：

```text
README.md
AGENTS.md
LICENSE（仅在署名确认后）
app/src/main/java/** 角色展示相关小范围 UI
docs/architecture/*
docs/features/*
docs/protocols/*
docs/environment/*
docs/decisions/*
docs/planning/*
app/src/main/assets/skills/**/SKILL.md
```

说明：实际 Skill 资产路径以仓库为准，执行 AI 必须先列清单。

---

## 7. 必须执行的验证

### 7.1 文档链接

检查 Markdown 相对链接。可以使用现有链接检查工具；若没有，至少运行搜索：

```powershell
git grep -n -I 'file:///'
```

结果必须为空或仅在明确历史示例中被代码块包裹并说明禁止使用；推荐完全为空。

### 7.2 旧事实扫描

```powershell
git grep -n -I -E '内置 10 个|7 个 GitHub|反检测|防屏蔽|组间并发|组内串行|BuildConfig\.GEMINI_API_KEY|text-embedding-004|流式秒播|无延迟播放'
```

命中必须逐项解释。

### 7.3 Skill 清单

执行 AI 必须输出：

- Skill 总数。
- 每个 Skill 是否有来源索引。
- 每个 Skill 是否有免责声明字段。
- 每个头像是否有来源/生成说明。
- 未核实项列表。

### 7.4 构建和测试

即使主要是文档，也必须确保 UI 标签和 Asset frontmatter 未破坏构建/解析：

```powershell
./gradlew.bat testDebugUnitTest
./gradlew.bat lintDebug
./gradlew.bat assembleDebug
./gradlew.bat assembleRelease
```

设备验收：

1. 角色列表显示 AI 模拟标识。
2. 角色详情显示完整声明入口。
3. 圆桌回复旁持续可见 AI 模拟标签。
4. SkillLoader 能解析全部 Skill。
5. 应用内文案与 README 一致。
6. 关于/设置页能打开隐私和第三方说明入口（如果实现为 App 内页面或外链）。

---

## 8. 验收清单

- [ ] README 结构精简，首屏定位清晰。
- [ ] README 首屏有 AI 模拟非本人声明。
- [ ] README 安装流程与 PR 01 一致。
- [ ] README 圆桌行为与 PR 02 一致。
- [ ] README 隐私说明与 PR 03 一致。
- [ ] README Release/构建信息与 PR 04 一致。
- [ ] AGENTS 只保留当前工作事实和规则。
- [ ] 新增 CONTRIBUTING.md。
- [ ] 新增 SECURITY.md，未编造联系方式。
- [ ] 新增 CHANGELOG.md。
- [ ] 新增 THIRD_PARTY_NOTICES.md。
- [ ] 20 个现有 Skill 均有来源/许可状态记录。
- [ ] 20 个头像均有来源或生成说明。
- [ ] UI 稳定显示 AI 模拟标签。
- [ ] 不暗示本人授权、认可或背书。
- [ ] TTS 文档准确说明接收完成后播放。
- [ ] 真正流式播放被拆为独立后续任务。
- [ ] 架构文档类名、流程和代码一致。
- [ ] 旧 ADR/Planning 有历史状态标识。
- [ ] “反检测/防屏蔽”等术语已清理。
- [ ] `file:///` 和作者个人路径已清理。
- [ ] MIT 与第三方内容授权边界写清。
- [ ] Build、Test、Lint 通过。

---

## 9. 禁止事项

- 禁止把 AI 角色写成真人本人。
- 禁止声称获得名人、公司或媒体授权，除非有证据。
- 禁止把来源不明 Skill、文章、头像默认标 MIT。
- 禁止编造原始来源、作者、许可证和发布日期。
- 禁止复制长篇第三方原文到仓库。
- 禁止只给两个 Skill 补来源就宣称 20 个已完成。
- 禁止从网络随意下载 Logo/头像替换资源。
- 禁止保留已失效构建和 Key 教程。
- 禁止继续写“真正实时秒播”而代码未实现。
- 禁止为了统一文档删除有价值的历史 ADR。
- 禁止修改 LICENSE 署名为用户未确认的真实身份。
- 禁止把 SECURITY.md 写成虚假的安全承诺。

---

## 10. 交付报告必须额外回答

1. README 最终有哪些一级章节？
2. AI 模拟声明出现在哪些 UI 和文档位置？
3. CONTRIBUTING 要求贡献者运行哪些命令？
4. SECURITY 的漏洞报告渠道是什么，是否经过维护者确认？
5. 20 个 Skill 中多少已找到明确许可证，多少待核实？
6. 20 个头像分别属于用户原创、AI 生成、第三方还是待核实？
7. THIRD_PARTY_NOTICES 如何区分代码依赖、Skill、引用和头像？
8. 哪些旧 ADR 被标记 Superseded？
9. TTS 文档最终如何描述当前行为？
10. 是否创建了真正流式播放的独立后续任务？
11. 全仓还剩哪些旧术语或个人路径，为什么？
12. LICENSE 署名是否修改，依据是什么？
13. 如何证明所有 Skill 仍能被 SkillLoader 正常解析？
14. 文档一致性检查是否进入 CI？
