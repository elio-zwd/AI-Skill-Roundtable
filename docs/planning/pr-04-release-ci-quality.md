# PR 04：Release 工程化与 CI 质量保障

> 优先级：P1
> 覆盖：正式发布、自动化测试、安全检查

## 目标
从个人项目状态升级为可维护开源项目。

## 任务清单

### 1. Release 配置
- 正式 applicationId。
- 正式签名注入。
- 版本管理规范化。
- 开启 R8 后验证。

### 2. CI Pipeline
增加：
- secret scan
- unit test
- lint
- assembleDebug
- dependency review

### 3. Room 数据安全
- 开启 schema 导出。
- 删除 release destructive migration。
- 增加 Migration 测试。

### 4. 测试补强
覆盖：
- Key 加解密。
- Key 调度。
- 错误重试。
- 并发场景。

## 验收标准
- GitHub Actions 通过。
- 新版本升级不会丢聊天记录。
- Release 构建可安装。

## 禁止事项
- 不使用 Debug 签名发布。
- 不依赖本地环境。
