# ADR 008 — Interactions API 迁移、向量平替与 API 密钥细粒度物理控制设计决策

## 1. 背景 (Context)

在 AI 智囊圆桌的 v2.2 版本中，应用遭遇了数个 T0 级别的底层崩溃与连接中断 Bug，同时用户提出了对 API Key 池更加微观的掌控诉求：
1. **模型退休崩溃**：Google 官方于 2026 年 1 月 14 日下线并退休了原 `text-embedding-004` 语义嵌入模型，导致路由时接口报错 404 崩溃。
2. **大模型生成中断**：在使用 `gemini-3.5-flash` 模型脑暴时，由于开启了 `high` 深度 Thinking 推理，模型思考吞吐时间明显增加，导致原 Retrofit/OkHttp 默认的 60 秒读取超时被频繁触发，抛出 `SocketTimeoutException`（端侧日志呈现为状态码 `-1`）。
3. **架构过时**：原本使用的 `generateContent` 接口不具备流式会话 ID 缓存追踪。为了进一步减少长文本 Payload 开销，需要将其全面向新版 `Interactions API` 架构迁移，引入 `previous_interaction_id` 会话链。
4. **Key池黑盒**：用户无法手动干预 10 个内置 API Key 的使用情况，需要设计一个一键开关，以及能对每一个内置 Key 进行单独禁用/启用的细粒度物理 Toggle 机制。

---

## 2. 决策 (Decisions)

为了应对上述挑战，我们在网络层、逻辑控制层与 UI 层联合实施了以下架构改造决策：

### 2.1 迁移到新版 Interactions API 并在端侧做双兜底
*   **端点与 Revision 挂接**：将生成、续写、摘要等接口全量迁移至 `POST v1beta/interactions` 端点，且强制请求头中附带 `Api-Revision: 2026-05-20`，接入 Google 最新的会话托管服务。
*   **隐式 ID 链传导**：在 ViewModel 中基于 `sessionId` 维护一个 `lastInteractionIds` 内存 Map。在多角色交锋时，将前一个角色的 `interactionId` 作为 `previousInteractionId` 传给云端，实现免传输历史上下文的“隐式上下文链式传导”。
*   **免费层 Fallback 降级**：云端 Interactions 会话的默认留存时间为 1 天。一旦由于超时、非法 ID 或者是免费层配额导致 Interactions 接口报错，端侧将自动捕获异常，并使用“包含完整历史会话 prompt 的全量历史拼接包”作为纯文本回退重新发起普通生成，确保高可用率。

### 2.2 `gemini-embedding-001` 的套娃截断与嵌套修正
*   **模型物理平替**：全量升级使用 `models/gemini-embedding-001` 嵌入模型替代退休的 `text-embedding-004`。
*   **嵌套截断对齐**：根据 Generative Language API 官方标准，截断维度参数 `output_dimensionality` 不能作为顶层参数，必须被包裹于 `config` (新定义 `EmbedContentConfig` 数据类) 对象下。通过此嵌套对齐，我们成功向下游获取到了 768 维的截断向量，规避了由于与 Room 数据库 768 维向量发生维度 mismatch 导致的致命崩溃。

### 2.3 极致放宽 HTTP 超时配置
*   在 `OkHttpClient` 初始化中，将 `readTimeout` 延长到 **300 秒（5分钟）**，同时将连接与写入超时放宽至 90 秒，给主力模型思维过程留出极充沛的网络带宽和缓冲时间。

### 2.4 内置 API Key 池的“一键整体”与“点击卡片单独”微控制
*   **整体控制**：在“配置 Gemini API 密钥”弹窗（右上角齿轮）里增加一键开关 “启用内置备用 API 密钥池”。关闭后强制退回仅使用自定义 Key。
*   **独立控制**：在“API 熔断诊断与遥测日志”全屏面板中，10 个内置 Key（w1 到 w10）的 Card 卡片被赋予点击监听。用户只要直接点击任意“可用”的内置 Key 即可将其手动“禁用”（卡片变为灰色，过滤池在 `getAvailableKeys` 中将立即自动排除它），再次点击可恢复其“可用”状态，实现了极简的 Toggle 式微交互控制。

---

## 3. 后果与评估 (Consequences)

*   **正向影响**：
    *   彻底消除了已下线 `text-embedding-004` 模型引起的 404 网络崩溃。
    *   由于 Interactions 云端会话链的高效利用，大幅度节省了多角色问答大文本历史的传输流量，TTFT（首字延迟）显著缩短。
    *   300 秒超时配置消除了 Thinking 推理时间过长导致的 Socket 断开异常。
    *   用户获得了对 API 密钥池百分之百的单独控制权，能够实时调整具体哪个 Key 参与脑暴计算。
*   **负向影响**：
    *   `gemini-embedding-001` 截断为 768 维后相比 3072 维默认表达力有微弱折损，但对于圆桌路由精度已完全足够，并且与本地 Room 数据库 768 维做到了完美对齐。
