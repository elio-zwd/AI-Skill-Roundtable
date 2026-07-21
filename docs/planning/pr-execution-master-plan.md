# 五阶段重构总控计划（PR 01～PR 05）

> 文档用途：这是交给执行型 AI 的总入口。任何 AI 开始修改前，必须先读本文件、根目录 `AGENTS.md`，再读对应 PR 任务文档。
>
> 执行顺序：`PR 01 → PR 02 → PR 03 → PR 04 → PR 05`
>
> 核心原则：先恢复可验证性，再修业务正确性，再收紧隐私，最后做发布工程化与开源治理。

---

## 1. 为什么必须按顺序执行

1. **PR 01 可复现构建**：先保证陌生环境能编译，后续修改才有可靠验证基础。
2. **PR 02 圆桌与 Key 编排**：修正最核心的业务语义、Key 调度、重试和并发状态。
3. **PR 03 隐私与遥测**：在稳定的新调用链上收紧日志、持久化和云端会话行为。
4. **PR 04 Release 与 CI**：对已经稳定的代码建立自动化质量门禁、迁移保护和发布配置。
5. **PR 05 开源治理**：最后同步 README、AGENTS、架构文档、第三方来源和免责声明，避免反复返工。

禁止并行执行 PR 02、PR 03、PR 04。它们会同时修改网络层、ViewModel、测试或 Gradle，容易造成冲突和错误结论。

---

## 2. 14 项审查发现覆盖矩阵

| 编号 | 审查发现 | 主负责 PR | 协同 PR | 完成判定 |
|---|---|---|---|---|
| F01 | Gradle Wrapper、JDK、脚本依赖作者本机路径 | PR 01 | PR 04 | 全仓无个人绝对路径；全新环境可执行 Debug 构建 |
| F02 | 表面按 Key 分组，实际请求仍按 `sessionId` 重新选 Key | PR 02 | PR 04 | 调度日志中的 Key ID 与网络层实际使用的 Key ID 一致 |
| F03 | README 声称顺序圆桌，代码实际并发且上下文不稳定 | PR 02 | PR 05 | 后一个角色稳定读取前一个角色已入库发言；相关文档一致 |
| F04 | 遥测明文持久化完整 Prompt、附件与模型回复 | PR 03 | PR 04 | 默认不保存正文；Release 不输出敏感正文；用户可清空 |
| F05 | Release 使用 Debug 签名、示例包名、无压缩、默认图标 | PR 04 | PR 05 | Release 配置独立；不使用 Debug 签名；版本和包名规范化 |
| F06 | Room 使用破坏性迁移，可能静默丢失聊天数据 | PR 04 | 无 | Release 移除 destructive migration；Schema 和迁移测试通过 |
| F07 | README、AGENTS、架构文档与当前实现严重漂移 | PR 01 | PR 05 | 安装流程先纠正；最终所有核心文档与代码一致 |
| F08 | 一次提问可能触发过多 Broker、搜索、主模型和续写调用 | PR 02 | PR 03 | 有硬性请求预算、角色上限、搜索上限和可见统计 |
| F09 | 所有错误都可能盲目换 Key；Key 尝试顺序可能重复 | PR 02 | PR 04 | 每个 Key 每轮最多尝试一次；按 4xx/429/5xx 分类处理 |
| F10 | TTS 文档声称流式秒播，代码实际收完 PCM 后才播放 | PR 05 | 可另建后续功能 PR | 文档准确描述当前行为；真正流式播放作为独立后续任务 |
| F11 | Secret Scan 仅覆盖 Gemini 风格 Key，CI 缺构建、测试、Lint | PR 04 | 无 | CI 至少包含 Secret、Unit Test、Lint、Assemble Debug |
| F12 | `RoundtableViewModel`、`GeminiApi.kt` 职责过重 | PR 02 | PR 03、PR 04 | 调度、重试、遥测可独立测试；不要求一次性全仓重构 |
| F13 | KeyStore、迁移、并发、错误策略测试不足 | PR 04 | PR 02、PR 03 | 核心纯逻辑单测、Room Migration Test、关键安全测试存在 |
| F14 | 名人模拟、图片、Skill 来源和 MIT 授权边界不清 | PR 05 | 无 | UI/README 有 AI 模拟声明；第三方来源与授权单独登记 |

说明：一个发现可以跨两个 PR，但只能有一个“主负责 PR”。主负责 PR 必须完成可验收结果，不能仅把问题转给后续 PR。

---

## 3. 所有执行 AI 必须遵守的规则

### 3.1 开始前

执行 AI 必须：

1. 阅读 `AGENTS.md`。
2. 阅读本总控文档。
3. 阅读当前 PR 的详细任务文档。
4. 使用 `git status`、`git branch --show-current`、`git log -5 --oneline` 确认仓库状态。
5. 读取任务文档列出的“必须先读文件”，不得凭文件名猜实现。
6. 在修改前输出一份简短实施计划，列出预计修改文件和验证命令。

### 3.2 修改期间

- 不修改任务范围外的 UI、文案或业务逻辑。
- 不顺手升级 Kotlin、AGP、Compose、Room、Retrofit 等依赖，除非任务明确要求。
- 不提交真实 API Key、签名文件、私钥、`.env`、`local.properties`。
- 不以删除功能代替修复。
- 不通过吞掉异常让测试“通过”。
- 不保留两套互相冲突的新旧实现。
- 新增抽象时必须有明确调用方和测试，不创建空壳架构。
- 修改数据库实体时必须同步版本号、Migration、Schema 和测试。

### 3.3 每个逻辑步骤后

至少执行与改动匹配的验证：

```powershell
./gradlew.bat compileDebugKotlin
./gradlew.bat testDebugUnitTest
```

涉及资源、Manifest、Gradle 或完整集成时再执行：

```powershell
./gradlew.bat lintDebug
./gradlew.bat assembleDebug
```

涉及数据库迁移时必须执行对应 Migration Test；涉及 UI 行为时按 `AGENTS.md` 使用设备或模拟器进行人工/自动化验收。

### 3.4 遇到失败时

执行 AI 不得直接跳过失败。必须按顺序：

1. 记录失败命令和第一条根因错误。
2. 判断是环境问题、原仓库问题还是本次修改引入。
3. 只修复与当前 PR 相关的根因。
4. 重跑失败命令。
5. 若受外部环境阻塞，在交付报告中明确写出“未验证项、阻塞原因、人工验证方法”。

---

## 4. 分支和提交建议

每个 PR 单独分支：

```text
refactor/pr-01-reproducible-build
refactor/pr-02-roundtable-orchestration
refactor/pr-03-privacy-telemetry
chore/pr-04-release-ci-quality
docs/pr-05-open-source-governance
```

提交应按可回滚的小步骤组织，例如：

```text
fix: 恢复可移植的 Gradle Wrapper 配置
refactor: 引入确定性的 API Key 尝试顺序
test: 增加 Key 重试策略单元测试
security: 默认关闭遥测正文持久化
docs: 同步圆桌调度与隐私说明
```

禁止把整个 PR 压成一个无法审查的巨大提交。

---

## 5. 统一交付报告模板

每个执行 AI 完成后，必须提交以下报告，不得只说“已完成”：

```markdown
# PR XX 交付报告

## 1. 完成范围
- [x] ...
- [ ] ...（说明未完成原因）

## 2. 修改文件
| 文件 | 修改原因 | 关键变化 |
|---|---|---|
| path | reason | change |

## 3. 设计决策
- 决策：
- 原因：
- 放弃的替代方案：

## 4. 验证结果
| 命令/场景 | 结果 | 证据或说明 |
|---|---|---|
| `./gradlew.bat testDebugUnitTest` | PASS/FAIL | ... |

## 5. 风险与兼容性
- 数据兼容：
- API 兼容：
- UI 行为变化：
- 性能/费用影响：

## 6. 未完成项
- 无 / 列出明确后续项

## 7. 人工验收步骤
1. ...
2. ...
```

---

## 6. PR 合并门禁

任何 PR 出现以下情况都不能合并：

- 编译或本 PR 相关测试失败。
- README/注释宣称的行为与代码不一致。
- 新增真实密钥、签名文件或敏感日志。
- 新增未解释的破坏性数据库迁移。
- 为规避平台限流而设计多 Key 绕过策略。
- 修改范围明显超出当前 PR 且没有拆分说明。
- 交付报告缺失验证证据。

---

## 7. 完成五个 PR 后的最终状态

完成后应达到：

- 陌生开发者可在标准 Android 环境构建和运行。
- 圆桌发言顺序、上下文和 Key 使用确定且可测试。
- 默认遥测不保存完整用户内容。
- Release、Room Migration 和 CI 有基础质量保障。
- README、AGENTS、架构与当前代码一致。
- 名人模拟和第三方内容来源、授权边界清晰。
