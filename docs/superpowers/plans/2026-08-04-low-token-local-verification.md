# 低 Token 本地验收证据工具实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. 本项目不使用并行子智能体或 Git Worktree 假设。

**Goal:** 提供 PowerShell 本地验收工具，使完整构建与测试输出保存在仓库外，AI 只读取可信摘要和有界失败证据。

**Architecture:** 使用 `LocalVerification.psm1` 集中实现 JUnit 解析、失败摘录、步骤执行和摘要聚合；使用薄 CLI 包装器暴露单步骤执行；使用无外部依赖的 PowerShell 测试脚本完成 Red-Green 验证。

**Tech Stack:** PowerShell 7、JUnit XML、GitHub Actions、JSON、Markdown。

## Global Constraints

- 基线固定为 `main@50a0fb401ebbdbc9ce26aba9a7dd40916b6ba610`。
- 分支固定为 `chore/low-token-local-verification`。
- 不修改 Android 生产代码、Room Schema、业务测试或现有 PR。
- 输出目录必须位于仓库外。
- 测试报告缺失、损坏或零测试时不得返回 PASS。
- 成功步骤不得把原始 stdout/stderr 打印到终端。
- 未实际执行的命令和 CI 必须标记为未验证。

---

### Task 1: 建立工具目录规则与测试失败场景

**Files:**
- Create: `tools/local-verification/AGENTS.md`
- Create: `tools/local-verification/tests/Run-LocalVerificationToolTests.ps1`

**Interfaces:**
- Produces: 无外部依赖测试入口 `pwsh -NoProfile -File tools/local-verification/tests/Run-LocalVerificationToolTests.ps1`。
- Consumes: 计划中的模块函数名称。

- [ ] **Step 1: 写入失败测试**

测试脚本首先断言模块及函数存在，并覆盖 JUnit、静默执行、退出码、日志 Hash、失败摘录和摘要聚合场景。模块尚不存在时应明确失败。

- [ ] **Step 2: 记录 Red 预期**

预期首轮失败原因：`LocalVerification.psm1` 不存在或函数未定义。远端 GitHub 插件不能执行本地命令时，只能提交测试并在 PR 中标记“Red 尚未实际运行”。

- [ ] **Step 3: 提交**

```text
test: 增加低 Token 验收工具失败场景
```

### Task 2: 实现 JUnit 解析和有界失败摘录

**Files:**
- Create: `tools/local-verification/LocalVerification.psm1`

**Interfaces:**
- Produces: `ConvertFrom-JUnitEvidence -Path <glob[]>`。
- Produces: `Get-BoundedFailureExcerpt -LogPath <path> -MaxLines <int> -MaxBytes <int>`。

- [ ] **Step 1: 实现最小 JUnit 解析**

读取匹配的 XML 文件，以 `classname::name` 合并测试身份，状态严重度为 `error > failure > skipped > passed`。

- [ ] **Step 2: 处理无证据状态**

路径缺失、XML 损坏或没有 `testcase` 时返回 `Status = NOT_VERIFIED` 并携带原因。

- [ ] **Step 3: 实现流式失败摘录**

使用固定前后文、行数和字节数上限，避免完整日志进入内存或终端。

- [ ] **Step 4: 实现展示层脱敏**

屏蔽 Bearer Token、`AIza` 特征和常见 key/token/secret/password 赋值；不修改原始日志。

- [ ] **Step 5: 静态核对测试契约**

逐项对照 Task 1 的 JUnit 与摘录断言。

- [ ] **Step 6: 提交**

```text
feat: 解析 JUnit 证据并限制失败日志输出
```

### Task 3: 实现静默步骤执行和总体摘要

**Files:**
- Modify: `tools/local-verification/LocalVerification.psm1`
- Create: `tools/local-verification/Invoke-LocalVerification.ps1`

**Interfaces:**
- Produces: `Invoke-VerificationStep`，返回步骤证据对象并写入 `steps/<name>.json`。
- Produces: `Write-VerificationSummary`，聚合步骤 JSON 为 `verification-summary.json` 和 `.md`。
- CLI consumes: `-Name`、`-OutputDirectory`、`-Command`、`-CommandArguments`、可选 `-JUnitPath`。

- [ ] **Step 1: 实现仓库外目录守卫**

解析仓库根目录与输出目录；输出目录位于仓库内时直接拒绝。

- [ ] **Step 2: 实现静默原生命令执行**

将所有输出重定向到日志；命令返回后立即保存 `$LASTEXITCODE`；命令启动异常记录为退出码 127。

- [ ] **Step 3: 写入日志 Hash 与时间证据**

保存 UTC 开始/结束时间、持续毫秒、日志绝对路径和 SHA-256。

- [ ] **Step 4: 接入 JUnit 状态**

退出码非零或测试失败为 FAIL；退出码为零但声明的 JUnit 不可验证时为 NOT_VERIFIED。

- [ ] **Step 5: 实现摘要聚合**

总体优先级固定为 `FAIL > NOT_VERIFIED > PASS`，JSON 与 Markdown 使用同一对象生成。

- [ ] **Step 6: 实现紧凑 CLI 输出**

成功最多三行；失败增加失败测试名称和有界摘录，不输出完整日志。

- [ ] **Step 7: 提交**

```text
feat: 实现静默验收步骤与结构化摘要
```

### Task 4: 接入协议文档和 CI

**Files:**
- Create: `docs/testing/local-verification-evidence-protocol.md`
- Create: `docs/testing/local-read-only-acceptance-template.md`
- Create: `.github/workflows/local-verification-tools.yml`

**Interfaces:**
- CI runs: `pwsh -NoProfile -File ./tools/local-verification/tests/Run-LocalVerificationToolTests.ps1`。

- [ ] **Step 1: 编写证据协议**

冻结成功输出、失败展开、XML 优先、全量通过后不重复定向测试和仓库外日志规则。

- [ ] **Step 2: 编写后续验收模板**

提供可替换的 PR、分支、Head、命令和 JUnit 路径占位符；禁止复制完整成功日志。

- [ ] **Step 3: 增加 Windows 与 Ubuntu CI**

仅运行轻量 PowerShell 工具测试，不启动 Android 模拟器。

- [ ] **Step 4: 提交**

```text
ci: 验证低 Token 本地验收工具
```

### Task 5: 完成前核验与 Draft PR

**Files:**
- Review all files changed from base.

- [ ] **Step 1: 静态检查范围**

确认没有 Android 生产代码、Room Schema、业务测试、依赖版本或现有工作流行为变化。

- [ ] **Step 2: 运行或读取真实验证证据**

本地可执行时运行 PowerShell 测试；否则明确等待 GitHub CI，不得声称通过。

- [ ] **Step 3: 请求代码审查**

重点检查误报 PASS、退出码覆盖、路径逃逸、重复测试统计、秘密展示和输出上限。

- [ ] **Step 4: 创建 Draft PR**

标题：`chore: 添加低 Token 本地验收证据工具`。

- [ ] **Step 5: 保持 Draft**

未经用户明确授权不得标记 Ready、合并或删除分支。
