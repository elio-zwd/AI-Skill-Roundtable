# PR09-09 远端验证证据

> 仓库：`elio-zwd/AI-Skill-Roundtable`  
> Draft PR：#39  
> Base：`main@228ec6f972684512fb6287d89c253da6c4aebd91`  
> 分支：`feat/pr-09-09-material-context-source`  
> 本文件记录范围：截至设备测试源码编译验证提交 `9c855ab47a219e79fd20891fc975bffdfc5ff64d` 的可核验证据。

## 1. TDD Red 证据

Android CI Run `30872404168` 在仅提交失败测试、尚未加入生产 API 时执行：

```text
生产 compileDebugKotlin：通过
compileDebugUnitTestKotlin：失败，退出码 1
失败原因：MaterialContext、ContextSelection、ContextUsage 与 ExecutionContextGate 等新 API 尚不存在
```

该失败符合预期 Red，随后才提交最小生产实现。

## 2. 核心 Green 证据

Android CI Run `30873255674` 在核心领域、Room v9、Repository、ExecutionContextGate 与原子 Runtime 实现后执行，已实际完成：

```text
应用身份与连续 Migration 守卫：通过
compileDebugKotlin：通过
全量 JVM：通过
lintDebug：通过
assembleDebug：通过
Debug APK、包名与 Migration 守卫：通过
```

该 Run 的测试报告 artifact 统计：

```text
测试套件：77
测试总数：284
通过：284
失败：0
错误：0
跳过：0
```

Run 后续因分支产生新提交而被 GitHub concurrency 取消，因此不把未执行的 Release/R8 后续步骤记为通过。

## 3. UI 编译证据

包含资料管理页面、个人背景页面和执行上下文确认 UI 的源码，已在 Room Schema 生成 Workflow 与普通 Android CI 中真实通过 `:app:compileDebugKotlin`。这验证了 Route → ViewModel → Screen → Components 装配、Compose API、领域类型和现有执行页面集成可以编译。

## 4. AndroidTest 编译证据

PR09 AndroidTest Compile Run `30874675148` 实际执行：

```text
./gradlew :app:compileDebugAndroidTestKotlin --no-daemon --stacktrace
```

结果：成功。随后 Workflow 自动删除自身并提交 `test: 验证资料背景设备测试可编译`，未在最终树保留临时 Workflow。

该结果只证明 AndroidTest 源码可编译，不代表测试已在模拟器或真机执行。

## 5. Room v9 Schema 证据

Room 从 v8 前向升级至 v9，保留 `8.json`，新增由 Room 编译生成的：

```text
app/schemas/com.elio.jianyu.data.RoundtableDatabase/9.json
```

生成 artifact 中 `9.json`：

```text
字节数：100626
SHA-256：7df1b136fb2152a4c5258b5698c436aa123fa313f09ee528800f6e90bea3c5a9
```

Schema 由当前源码执行 `:app:compileDebugKotlin` 生成并提交，未手写 Identity Hash。迁移链守卫已更新为：

```text
1→2→3→4→5→6→7→8→9
```

v8 旧 Usage Snapshot 的保守默认值为：

```text
networkAllowed = false
sensitive = true
```

不会伪造历史联网授权。

## 6. Secret scan 证据

最新普通 Secret scan 已通过。扫描范围继续覆盖：

```text
当前工作树
暂存区
所有生产源码
HEAD 可达历史
禁止跟踪的敏感文件
旧 API Key 架构
```

PR 开发过程中用于传输原子补丁的已删除随机 Base64 分片偶然匹配 `AIza` 正则。由于禁止改写历史，扫描器只精确排除以下两类已删除固定路径：

```text
.github/pr09-core.patch.gz.b64.part-*
.github/pr09-ui.patch.gz.b64.part-*
```

该豁免不覆盖当前文件、生产源码、普通历史路径或通用 API Key 模式。

## 7. 静态架构复核

已核对：

```text
UI 与 ViewModel 不访问 DAO
Context Confirmation Components 不访问 Repository 或网络
MaterialContext Repository 不调用 Gemini
项目仍只有一个 ExecutionContextBuilder
项目仍使用现有 ExecutionRunCoordinator
ExecutionRun 与 Participant 状态枚举语义未改
预算、Stop、成功成员过滤和迟到回调语义未改
个人背景默认不选
资料候选不等于自动发送
未授权联网不会静默移除来源后继续
24,000 字符超限不会截断或摘要
```

PR #39 当前无 Inline Review Thread。

## 8. 尚未远端执行的验证

GitHub 普通 CI 不运行模拟器或真机 Instrumentation。以下内容尚未实际执行：

```text
MaterialContextRepositoryTest 的设备运行
MaterialContextMigrationTest 的设备运行
ResourcesScreenTest 的设备运行
ContextConfirmationDialogTest 的设备运行
全量 connectedDebugAndroidTest
360dp、200% 字号、明暗主题、TalkBack、Activity 重建
Logcat 隐私验证
进程强停与恢复后的人工场景
```

远端未实际执行设备 Instrumentation；等待本地 AI 严格只读验收。

完整本地验收 Prompt：

```text
docs/testing/pr-09-09-local-read-only-acceptance.md
```

## 9. 最终普通 CI

本文件提交将触发最新 Head 的普通 Android CI 与 Secret scan。最终交付结论必须读取该最新 Head 的真实结果，至少覆盖：

```text
Secret scan
应用身份与 Room v9 连续迁移守卫
compileDebugKotlin
全量 JVM
lintDebug
assembleDebug
Debug APK 与包名验证
assembleRelease / R8
Release 包验证
已提交 Room Schema 当前性
```

若最新 Head 任一步骤失败，PR #39 继续保持 Draft，并按失败日志回到远端开发对话修复。

## 10. Superpowers 声明

Superpowers 插件接口未调用；本任务读取仓库内保存的 Superpowers 6.2.0 Skill 文件，并按照项目适配规则执行等价人工流程。
