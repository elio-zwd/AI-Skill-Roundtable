# 见域本地验收证据协议

## 1. 目的

本协议用于减少本地 AI 验收时无价值的终端文本，同时保持构建、测试和设备验证的真实性。它约束的是证据传递方式，不降低测试范围。

## 2. 核心原则

```text
完整执行
≠ 完整输出给 AI
```

所有命令仍按验收要求完整运行；原始 stdout/stderr 保存到仓库外临时日志；AI 默认只读取结构化摘要和必要的失败摘录。

## 3. 强制规则

1. 原始日志必须位于 `$env:TEMP` 或其他明确的仓库外目录。
2. 禁止使用 `Tee-Object` 把完整日志同时输出到终端。
3. 成功步骤最多输出三行：结果行、步骤 JSON 路径、日志 SHA-256。
4. 成功时不得列出全部 Task、全部通过测试或完整 adb/Gradle 输出。
5. 声明测试步骤时，统计以 JUnit XML 为主要事实；退出码和测试统计必须同时记录。
6. JUnit XML 缺失、损坏或零测试时必须为 `NOT_VERIFIED`。
7. 失败时先读取失败测试身份和有界摘录；默认最多 120 行、32 KiB。
8. 完整 Stacktrace 只在已有证据不足时对失败步骤定向重跑，不作为首次默认参数。
9. 全量测试通过且 XML 已证明目标测试运行后，不得无理由重复执行同一批定向测试。
10. 展示摘录必须屏蔽常见 Token、API Key 和密码形式；原始日志不得被工具改写。
11. 最终报告不得粘贴完整成功日志，只引用步骤 JSON、测试统计、退出码、日志 Hash 和失败证据。

## 4. 推荐目录

```powershell
$evidenceRoot = Join-Path $env:TEMP "jianyu-pr-<PR>-<HEAD>"
```

目录结构：

```text
<evidenceRoot>/
├── logs/
│   └── <step>.log
├── steps/
│   └── <step>.json
├── verification-summary.json
└── verification-summary.md
```

这些文件不得提交到仓库。

## 5. 单步骤执行

```powershell
$arguments = @(
    '--no-daemon',
    '--console=plain',
    '--warning-mode=summary',
    ':app:testDebugUnitTest'
) | ConvertTo-Json -Compress

pwsh -NoProfile -File .\tools\local-verification\Invoke-LocalVerification.ps1 `
    -Name 'jvm-full' `
    -OutputDirectory $evidenceRoot `
    -Command '.\gradlew.bat' `
    -CommandArgumentsJson $arguments `
    -JUnitPath '.\app\build\test-results\testDebugUnitTest\TEST-*.xml'
```

成功输出示例：

```text
[PASS] jvm-full | exit=0 | total=292 | passed=292 | failed=0 | errors=0 | skipped=0 | duration=218s
stepJson=C:\...\steps\jvm-full.json
logSha256=<sha256>
```

## 6. 失败处理顺序

1. 读取步骤 JSON；
2. 确认退出码与 JUnit 失败测试；
3. 阅读工具已生成的有界摘录；
4. 仍不足时用 `Select-String` 或 `Get-Content -Tail` 查看有限范围；
5. 只有必要时带 `--stacktrace` 定向重跑失败步骤；
6. 禁止直接把完整日志加载到 AI 上下文。

建议人工查询：

```powershell
Select-String -Path $logPath `
    -Pattern 'FAILED|FAILURE:|AssertionError|Exception|Caused by:' `
    -Context 3,8 |
    Select-Object -First 80
```

## 7. 全量与定向测试

正常路径：

```text
全量执行一次
→ 解析 XML
→ 确认目标测试类存在并通过
→ 不再重复定向执行
```

只在以下情况运行定向测试：

- 全量测试失败，需要稳定复现；
- 全量报告缺少目标测试；
- 测试进程中断或报告不完整；
- 设备异常导致结果无法判定；
- 修复后复验具体失败场景。

## 8. 状态解释

- `PASS`：命令退出码为 0，且声明的测试报告完整、可解析、测试数大于 0、无 failure/error。
- `FAIL`：命令非零，或测试报告包含 failure/error。
- `NOT_VERIFIED`：命令可能成功，但声明的证据缺失、损坏或为空。

报告必须保持上述分类，不得把 `NOT_VERIFIED` 改写成“基本通过”。

## 9. 隐私

- 日志可能包含测试正文、设备路径或敏感错误上下文，应视为临时敏感证据。
- PASS 后可删除原始日志；FAIL 时保留至问题交接完成。
- 不上传日志到公开 Issue、PR 评论或聊天，除非先人工审阅并最小化摘录。
- 工具脱敏仅用于降低意外展示风险，不能替代 `tools/check-secrets.ps1`。

## 10. 适用范围

从本协议合并后的新验收 Prompt 开始采用。历史验收文档不批量重写；需要复跑历史 PR 时，可以在不修改原文的情况下附加本协议。
