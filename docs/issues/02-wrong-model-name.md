# Bug 02 — API 模型名 `gemini-3.5-flash` 不存在

## 问题现象
所有向 Gemini API 发起的生成请求均返回失败，角色无法正常作答。

## 影响范围
所有需要 AI 作答的功能（圆桌脑暴、单角色对话）

## 复现条件
1. 配置有效的 API Key
2. 提交任意问题触发 `callGeminiApi()`
3. 请求发出后收到错误响应

## 根因
`GeminiApi.kt` 第 53 行：
```kotlin
@POST("v1beta/models/gemini-3.5-flash:generateContent")
```
模型名 `gemini-3.5-flash` 在 Google Gemini API 中并不存在（OpenAI 有 GPT-3.5，但 Gemini 没有对应命名的模型）。

正确的可用模型名为：
- `gemini-2.0-flash`（推荐，速度快）
- `gemini-1.5-flash`（稳定备用）
- `gemini-1.5-pro`（更强但较慢）

## 解决方案
修改 `GeminiApi.kt`，将模型名改为 `gemini-2.0-flash`，并在 `ApiKeyPool.kt` 中配置主备模型降级逻辑。

## 状态
🚧 待修复

## 关联代码
- [GeminiApi.kt](../../app/src/main/java/com/elio/skillroundtable/network/GeminiApi.kt#L53)
