# PR 01：可复现构建与新贡献者上手

> 状态：Planned  
> 优先级：P0  
> 前置依赖：无  
> 覆盖审查项：F01 构建路径不可移植、F07 README/AGENTS 与实现漂移（安装部分）

## 1. 背景

当前仓库的 Gradle Wrapper 指向作者电脑上的本地文件：

```properties
distributionUrl=file:///E:/MobileApp/sdk/gradle-8.14-all.zip
```

`run.ps1` 和部分文档还写死了 `D:\My_Elio\dev-tools\jdk-17.0.19+10`。此外，Android 构建脚本已经取消 `.env -> BuildConfig.GEMINI_API_KEY` 注入，但 README 仍要求复制 `.env` 后编译，导致新用户按照文档操作也无法得到预期结果。

本 PR 的目标是让陌