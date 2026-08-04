# tools/local-verification/AGENTS.md

本目录提供本地严格只读验收的证据收集工具。根目录 `AGENTS.md` 继续优先适用。

## 范围

- 只处理命令执行、日志保存、JUnit 解析、失败摘录和摘要生成。
- 不修改 Android 生产代码、Room、业务测试、设备状态或 GitHub PR 状态。
- 不在本目录实现通用任务编排、模拟器管理或远程执行平台。

## 真实性规则

- 原生命令退出码必须在命令返回后立即保存，不得用后续命令覆盖。
- 缺少、损坏或零测试的 JUnit 结果必须是 `NOT_VERIFIED`，不得推断为 PASS。
- 只有退出码和声明的测试证据均满足要求时才能生成 PASS。
- 工具不能执行的验证必须在摘要中明确标记为未验证。

## Token 与日志规则

- 原始 stdout/stderr 必须写入仓库外目录。
- 禁止使用 `Tee-Object` 把完整日志同时输出到终端。
- 成功步骤最多输出三行摘要，不列出成功测试方法。
- 失败证据必须有行数和字节数上限。
- 展示层脱敏不得改写原始日志。

## 开发与测试

- PowerShell 7 为目标运行时。
- 不引入 Pester 或其他外部依赖；测试入口为：

```powershell
pwsh -NoProfile -File .\tools\local-verification\tests\Run-LocalVerificationToolTests.ps1
```

- 新增行为先增加失败场景，再实现最小代码。
- 测试使用仓库外临时目录，并在 `finally` 中清理。
- 修改完成前检查 Windows 和 Ubuntu GitHub Actions 结果；无法执行时不得声称通过。
