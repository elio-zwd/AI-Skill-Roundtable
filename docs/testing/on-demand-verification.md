# 按需验证指南

## 1. 读取与执行条件

只有用户明确要求构建、测试、扫描、验收或查询/等待 GitHub CI 时，才读取本文件，并按用户指定范围执行。实现、文档整理、提交、推送和 Git 合并本身不构成验证授权，实测默认由用户负责。

- 用户只要求编译，就只编译；只要求单测，就只运行相关单测。下表是命令索引，不是自动执行清单。
- 用户只要求查询 CI 状态，就报告当前状态，不自动持续等待、重跑或下载制品。
- 用户要求合并时，不主动将 CI 完成设为合并前置条件；若 GitHub 分支保护实际阻止合并，如实说明，不绕过保护。
- 本指南不修改 GitHub Actions 工作流；推送或 PR 事件仍可能自动触发云端 CI，不能据此扩大本地操作或等待范围。

## 2. 本地环境与命令

需要执行 Android 构建或测试时，确认 JDK 17、Android SDK 和仓库环境；不要把示例占位路径写入实际配置。使用 Windows 10 x64、`pwsh.exe`（PowerShell 7）和仓库 Gradle Wrapper，环境配置见 [Android 编译指南](../environment/android-compilation-guide.md)。

如需核实实际 Gradle 与 JVM 环境：

```powershell
.\gradlew.bat --version
```

| 用户要求的范围 | 对应动作或命令 | 范围说明 |
|---|---|---|
| 文档检查 | 文件回读、相对链接、术语与覆盖关系、范围核对、`git diff --check` | 不自动查询 PR CI |
| Kotlin 编译 | `.\gradlew.bat compileDebugKotlin` | 不自动追加单测或打包 |
| 本地单元测试 | `.\gradlew.bat testDebugUnitTest` | 可按用户指定模块或用例聚焦 |
| Lint | `.\gradlew.bat lintDebug` | 不自动扩展为全套验证 |
| Debug APK 构建 | `.\gradlew.bat assembleDebug` | 按需说明生成的制品路径 |
| Android/Compose 测试编译 | `.\gradlew.bat compileDebugAndroidTestKotlin` | 编译通过不表示设备执行通过 |
| 设备测试 | `.\gradlew.bat connectedDebugAndroidTest` | 需有设备或模拟器；不自行卸载应用或清空数据 |
| 数据库验证 | 版本与 Schema 一致性、相关测试、新建与重新打开检查 | 按授权范围执行，说明既有数据处理结果 |
| Release 或签名验证 | `.\gradlew.bat assembleRelease`，核对签名和制品 | 缺少必要配置时说明阻塞 |
| 密钥扫描 | `pwsh.exe -File .\tools\check-secrets.ps1` | 用户要求历史扫描时加 `-IncludeHistory` |
| 多角色独立性验收 | 相关上下文测试或用户指定验收 | 关注 `previous_interaction_id`、历史拼接、摘要、调度器、Prompt、缓存与召回是否泄漏其他角色的默认判断 |

用户明确要求完整本地 Debug 验证时，可执行：

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest lintDebug assembleDebug compileDebugAndroidTestKotlin
```

`clean` 仅在授权范围内排查缓存或确需干净构建时使用。检查通过后，除非有新改动、失败或未解决疑点，不重复执行同一轮验证。

## 3. GitHub Actions 查询与等待

仅在用户明确要求对应操作时执行：

1. 锁定 PR 的 Base、Head SHA 和 Draft 状态，按当前 Head 查询关联 Workflow Run。
2. 查询状态与持续等待分别按用户要求处理。等待时适当拉长间隔，不反复报告未变化的状态；全部成功后停止。
3. 只有失败或取消时读取 Job，先看失败步骤摘要，必要时再取完整日志。
4. 仅用户要求验收制品时，获取所需 APK、测试报告或 Schema Artifact。
5. 遇到权限、速率限制或服务错误，记录原因并停止连续试探。
6. 用户授权重跑时，仅 Runner、网络或服务端瞬时故障可直接重跑；代码或文档问题须先修复并产生新 Head，不创建空提交触发 CI。

## 4. 结果与交付

- 失败时记录失败命令和首个根因，区分环境、既有问题与本次引入问题；需要修复或重跑时遵守用户授权范围。
- 准确区分本地实际通过、GitHub CI 通过、仅静态检查、环境阻塞未执行和待用户实测，不把测试编译写成设备验收通过。
- 没有执行的检查写明未执行；没有 PR 时写“未创建 PR”，不声称 CI 通过。
- 用户明确要求真机或本地 AI 验收方案时，提供相应验收 Prompt，列清操作范围、预期结果和数据边界。
