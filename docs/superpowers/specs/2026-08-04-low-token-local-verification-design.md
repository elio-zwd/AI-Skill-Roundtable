# 低 Token 本地验收证据工具设计

## 1. 背景

现有本地严格只读验收会完整执行 Gradle、adb、JUnit 和 Instrumentation，但本地 AI 往往同时读取大量成功日志、Task 列表、Daemon 信息和重复测试输出。这些文本对验收结论帮助有限，却持续占用上下文 Token。

本设计不减少测试覆盖，而是把“执行测试”和“AI 阅读证据”分离：

```text
完整执行命令
→ 原始 stdout/stderr 写入仓库外临时日志
→ 解析机器可读测试结果
→ 生成小型 JSON/Markdown 摘要
→ 仅失败时输出受限关键日志
```

## 2. 目标

- 成功步骤在终端最多输出三行摘要。
- 原始日志默认位于 `$env:TEMP` 下的显式验收目录，不进入仓库。
- 原生命令退出码、开始结束时间、持续时间和日志 SHA-256 必须保留。
- JVM 与 Instrumentation 结果优先从 JUnit XML 统计。
- XML 缺失、损坏或零测试时返回 `NOT_VERIFIED`，不得误报 `PASS`。
- 失败时只输出有限行数和字节数的关键上下文，并对常见 Token、API Key 和密码形式做展示层脱敏。
- 全量测试通过且报告已证明目标测试运行后，不再重复执行相同定向测试。

## 3. 非目标

- 不修改 Android 生产代码、Room Schema、业务测试或用户数据。
- 不建立通用 CI/CD 平台或任务 DSL。
- 不自动启动模拟器、操作 PR 状态或合并分支。
- 不批量改写历史验收文档。
- 不把展示层脱敏当成秘密扫描器；原始日志仍按敏感证据处理。

## 4. 架构

首版使用一个 PowerShell 模块和一个 CLI 包装脚本：

```text
tools/local-verification/
├── AGENTS.md
├── LocalVerification.psm1
├── Invoke-LocalVerification.ps1
└── tests/
    └── Run-LocalVerificationToolTests.ps1
```

模块提供四类职责：

1. `ConvertFrom-JUnitEvidence`：解析 XML、合并重复测试身份并生成统计；
2. `Get-BoundedFailureExcerpt`：流式读取日志、提取关键上下文、限制行数/字节数并脱敏；
3. `Invoke-VerificationStep`：静默执行原生命令并写入单步骤证据 JSON；
4. `Write-VerificationSummary`：聚合多个步骤并生成总体 JSON/Markdown。

CLI 只负责参数绑定和简洁终端输出，不复制模块逻辑。

## 5. 状态模型

单步骤状态：

- `PASS`：退出码为 0；若声明 JUnit 路径，则结果存在、可解析、测试数大于 0 且失败数为 0；
- `FAIL`：退出码非 0，或 JUnit 结果包含 failure/error；
- `NOT_VERIFIED`：命令退出码为 0，但声明的测试报告缺失、损坏或没有测试。

总体状态优先级：

```text
FAIL > NOT_VERIFIED > PASS
```

任何 `FAIL` 使总结果为 `FAIL`；没有失败但存在 `NOT_VERIFIED` 时，总结果为 `NOT_VERIFIED`。

## 6. JUnit 合并规则

- 支持根节点为 `testsuite` 或 `testsuites` 的常见 JUnit XML。
- 以 `classname + name` 作为测试身份。
- 多文件出现同一测试时只统计一次，结果采用最严重状态：`error > failure > skipped > passed`。
- 同时保留来源文件和失败消息，供失败摘要使用。
- 不仅依赖 testsuite 顶层数字；实际以 `testcase` 节点为主要事实。

## 7. 日志和隐私

- 日志目录必须由调用方显式指定，推荐位于 `$env:TEMP\jianyu-local-verification\<PR>-<Head>`。
- 不使用 `Tee-Object` 将完整日志复制到终端。
- 成功时不读取和展示完整日志。
- 失败摘录默认最多 120 行、32 KiB，优先匹配 `FAILED`、`FAILURE:`、`AssertionError`、`Exception`、`Caused by:` 等模式。
- 展示副本屏蔽 Bearer Token、`AIza` 特征及常见 `key/token/secret/password` 赋值；原始日志不修改。
- 单步骤 JSON 记录日志绝对路径和 SHA-256，但不内嵌日志正文。

## 8. CLI 契约

示例：

```powershell
pwsh -NoProfile -File .\tools\local-verification\Invoke-LocalVerification.ps1 `
  -Name jvm-full `
  -OutputDirectory "$env:TEMP\jianyu-pr40" `
  -Command .\gradlew.bat `
  -CommandArguments @('--no-daemon','--console=plain','--warning-mode=summary',':app:testDebugUnitTest') `
  -JUnitPath @('.\app\build\test-results\testDebugUnitTest\TEST-*.xml')
```

成功输出：

```text
[PASS] jvm-full | exit=0 | total=292 | passed=292 | failed=0 | skipped=0 | duration=218s
stepJson=C:\...\steps\jvm-full.json
logSha256=<sha256>
```

失败输出只增加失败测试名称和有界摘录，不打印完整日志。

## 9. 测试策略

采用不依赖 Pester 的纯 PowerShell 测试脚本，动态创建临时 JUnit XML 和假命令。测试覆盖：

- 全通过、多文件、重复测试、failure、error、skipped；
- XML 缺失、损坏和零测试；
- stdout/stderr 静默重定向；
- 退出码 0、1、2 的保真；
- 日志 SHA-256 可复算；
- 摘录行数和字节限制；
- 常见秘密特征只在展示副本中被屏蔽；
- 总体状态聚合规则；
- JSON 和 Markdown 输出一致。

GitHub Actions 在 Windows 与 Ubuntu 的 `pwsh` 上运行轻量工具测试，不运行 Android 设备测试。

## 10. 使用纪律

从后续验收 Prompt 开始：

- 首次 Gradle 执行默认不使用 `--stacktrace`；
- 仅当 XML 和有界摘录不足以定位时，对失败步骤定向使用 `--stacktrace` 重跑；
- 全量结果完整通过后，不重复运行同一批定向测试；
- AI 报告引用步骤 JSON、统计和失败摘录，不粘贴完整成功日志。

## 11. 风险与防护

- **退出码被 PowerShell 覆盖**：原生命令返回后立即复制 `$LASTEXITCODE`，并以多种非零码测试。
- **JUnit 路径差异**：由调用方显式传 Glob；找不到时返回 `NOT_VERIFIED`。
- **重复统计**：按测试身份合并并采用最严重状态。
- **日志进入仓库**：工具要求显式仓库外目录，且拒绝位于当前仓库根目录下的输出路径。
- **过度抽象**：首版不增加 Profile DSL、并行执行、远程设备管理或历史趋势数据库。

## 12. 完成条件

- 工具测试在 Windows/Ubuntu CI 实际通过；
- 成功步骤终端输出不含假命令原始 stdout/stderr；
- 非零退出码不被吞掉；
- 损坏或缺失 XML 不会产生 PASS；
- 失败摘录满足行数和字节上限；
- 输出目录位于仓库外；
- PR 差异不包含 Android 生产代码、Room Schema 或业务测试修改。
