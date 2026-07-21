# PR 01：可复现构建与新贡献者上手

> 优先级：P0
> 覆盖：构建移植、README 安装流程、开发环境说明

## 目标
让陌生开发者 clone 后可以完成编译、安装、运行，不依赖作者电脑环境。

## 任务清单

### 1. 修复 Gradle Wrapper
- 移除 `file:///E:/...` 本地路径。
- 恢复官方 HTTPS distributionUrl。
- 验证 `./gradlew assembleDebug`。

### 2. 移除个人机器依赖
- run.ps1 不允许写死 JDK 路径。
- 支持 JAVA_HOME。
- 缺失环境时输出安装提示。

### 3. 更新配置说明
- 明确 API Key 当前流程：应用内导入，而非 `.env` 注入。
- 删除失效的旧教程。
- 清理 `file:///` 文档链接。

### 4. 补充开发文档
新增：
- 开发环境要求
- Android Studio 配置
- Debug 构建流程
- 常见失败排查

## 验收标准
- 新 Windows 用户无需修改脚本即可编译。
- README 指令全部可执行。
- CI 环境可以完成 debug build。
- 不包含作者本机路径。

## 禁止事项
- 不恢复硬编码 API Key。
- 不提交本地 Gradle 缓存。
- 不增加新的环境耦合。
