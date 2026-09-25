# Gemini 2.5 Web Grounding Models Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在不新增 Gemini 协议链路的前提下，加入 2.5 Flash、2.5 Flash-Lite、3.5 Flash-Lite，并让联网检索默认使用 2.5 Flash。

**Architecture:** 保持现有 Interactions API 和 `supportsWebGrounding` 机制；在模型目录中新增模型，并新增按 `AiUseCase` 解析默认模型的入口。联网执行路径本身不改，只通过 `WEB_GROUNDING` 的默认配置选择 2.5 Flash。

**Tech Stack:** Android / Kotlin / Jetpack Compose / JUnit4 / Gemini Interactions API

## Global Constraints

- 保留现有 3.x Google Search 联网支持，不删除或降级 `supportsWebGrounding`。
- 不新增 Gemini 2.5 GenerateContent 专用链路。
- Gemini 通用默认仍为 `gemini-3.8-flash`；仅 `WEB_GROUNDING` 默认改为 `gemini-2.5-flash`。
- 不修改 Room Schema，不升级依赖。
- 已保存的用户模型选择优先于新默认值。

---

### Task 1: 用测试冻结新增模型与默认规则

**Files:**
- Modify: `app/src/test/java/com/elio/jianyu/network/AiProviderTest.kt`
- Modify: `app/src/test/java/com/elio/jianyu/network/GeminiInteractionRequestContractTest.kt`
- Modify: `app/src/androidTest/java/com/elio/jianyu/ui/screens/settings/SettingsScreenRegressionTest.kt`

**Interfaces:**
- Consumes: `AiModel`, `AiUseCase`, `defaultModel(AiProvider)`
- Produces: 新的 `defaultModel(AiUseCase)` 期望契约；新增模型目录和 Interactions 请求契约。

- [ ] **Step 1: 增加模型目录测试**
  - 期望 Gemini 顺序包含 3.8、3.7、3.6、3.5 Flash、3.5 Flash-Lite、3.1 Flash-Lite、2.5 Flash、2.5 Flash-Lite。
  - 断言三个新增模型的准确 model ID 和 `supportsWebGrounding=true`。

- [ ] **Step 2: 增加用途默认测试**
  - 断言 `defaultModel(AiProvider.GEMINI) == GEMINI_38_FLASH`。
  - 断言 `defaultModel(AiUseCase.WEB_GROUNDING) == GEMINI_25_FLASH`。
  - 断言其他 Gemini 文本用途默认仍为 3.8 Flash。

- [ ] **Step 3: 增加思考档位测试**
  - 2.5 Flash / Flash-Lite 的应用 `minimal` 映射为 `low`。
  - 3.5 Flash-Lite 的 `minimal` 保持 `minimal`。
  - `low / medium / high` 对全部新增模型原样通过。

- [ ] **Step 4: 增加 Interactions + Google Search 请求编码测试**
  - 使用 `GEMINI_25_FLASH` 构造 `CreateInteractionRequest`。
  - 断言模型 ID 为 `gemini-2.5-flash`、工具为 `{"type":"google_search"}`、`thinking_level` 为归一化后的 `low`。

- [ ] **Step 5: 扩展设置页 AndroidTest**
  - 普通文本模型选择显示 `gemini-3.5-flash-lite`。
  - 联网检索选择页显示 2.5 Flash、2.5 Flash-Lite，并继续显示既有 3.x 联网模型。

### Task 2: 实现模型目录与按用途默认模型

**Files:**
- Modify: `app/src/main/java/com/elio/jianyu/network/AiProvider.kt`

**Interfaces:**
- Produces: `AiModel.GEMINI_35_FLASH_LITE`、`AiModel.GEMINI_25_FLASH`、`AiModel.GEMINI_25_FLASH_LITE`、`defaultModel(AiUseCase)`。

- [ ] **Step 1: 新增三个 AiModel 枚举项**，准确设置 model ID、显示名和 `supportsWebGrounding=true`。
- [ ] **Step 2: 新增按用途默认模型函数**，让 `WEB_GROUNDING` 返回 2.5 Flash，其余用途按提供商现有默认。
- [ ] **Step 3: 更新配置加载、重置和 provider 切换**，统一使用按用途默认规则，同时不覆盖 SharedPreferences 中已有合法明确选择。
- [ ] **Step 4: 更新思考档位模型集合**，把 2.5 Flash / Flash-Lite 加入 `minimal -> low` 归一化集合。

### Task 3: 同步 Gemini 协议文档

**Files:**
- Modify: `docs/protocols/models/gemini-model-request-contract.md`
- Modify: `docs/protocols/guides/gemini-api.md`
- Modify if needed: `docs/architecture/system-architecture.md`

**Interfaces:**
- Consumes: Task 2 最终模型目录与默认规则。

- [ ] **Step 1: 更新当前模型矩阵**，加入 3.5 Flash-Lite 和 2.5 Flash / Flash-Lite。
- [ ] **Step 2: 修正旧的“2.5 不能直接使用 Interactions thinking_level”描述**，记录 2026-09-26 官方 Interactions / Thinking 现状。
- [ ] **Step 3: 记录联网默认模型为 2.5 Flash，同时明确 3.x 联网能力继续保留。**
- [ ] **Step 4: 记录 2.5 账号访问限制，不把访问失败描述成协议不兼容。**

### Task 4: 验证与 PR 收口

**Files:**
- Update: `docs/superpowers/plans/2026-09-26-gemini-25-web-grounding-models.md`
- Update: PR #76 描述

- [ ] **Step 1: 复查 branch diff**，确保没有新增 2.5 GenerateContent 分支或无关重构。
- [ ] **Step 2: 运行当前环境可执行的验证；无法执行 Android/Gradle 时明确标记 NOT_RUN。**
- [ ] **Step 3: 更新本 Plan checkbox 为实际状态。**
- [ ] **Step 4: 更新 PR #76 标题/描述，纳入 2.5、3.5 Flash-Lite 与联网默认规则。**
- [ ] **Step 5: 生成本地 AI 只读验收 Prompt，覆盖 JVM、AndroidTest、真实 Key 的 Interactions + Google Search 最小验证。**
