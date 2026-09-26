# Skill Knowledge + Gemini Embedding 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 `gemini-embedding-2` 把官方 Skill 的 Markdown 资产升级为可检索的结构化 Skill Knowledge，并在【资料】中提供只读 Skill 资料区域，同时保持角色知识默认隔离。

**Architecture:** `SKILL.md` 永远作为 Role Core；开发期把官方 Skill 的 Knowledge Markdown 分块并预生成 768 维向量，运行时只为当前用户问题生成 query embedding，在 Android 本地按当前 Skill 做 cosine retrieval，形成 Context Pack。自动检索与用户资料分域；用户显式选择其他 Skill 文档时通过新的 context source + v15 usage snapshot 带入当前会话。

**Tech Stack:** Android / Kotlin / Coroutines / OkHttp / Jetpack Compose / Material 3 / Room / Python 3 stdlib generator / Gemini Developer API `gemini-embedding-2`

**Spec:** `docs/superpowers/specs/2026-09-26-skill-knowledge-embedding-design.md`

## 执行记录（2026-09-26）

- 已确认的起点：`codex/skill-knowledge-embedding@374a8dc3` 与远端一致，起始工作树干净；PR #77 指向 `main`，仍为 Draft。`origin/main@d56850bf` 是已核实的本地远端引用。
- 用户此前完成真实单条 API smoke：`gemini-embedding-2` 返回 768 维。本轮从生成器复原失败 chunk，元数据与原记录一致；同一输入单条请求 3 次中 2 次成功、1 次空 body HTTP 400。因此没有改动知识正文或回退模型。
- 官方 [Embedding API](https://ai.google.dev/api/embeddings) 规定 `batchEmbedContents` 返回顺序与输入一致；本轮 2 条真实输入批量请求成功返回两个 768 维向量。官方 [价格页](https://ai.google.dev/gemini-api/docs/pricing) 标明免费层不提供异步 Batch API，当前使用有界同步批量生成。
- 生成器已增加按请求数和估算输入量的节流、针对 429 与空 body 400 的有界重试、`build/tmp/skill_knowledge/embeddings` 中按模型/维度/输入哈希键控的向量缓存，以及正式资产的暂存校验。真实 429 曾使首批失败；单条和两条批量复测随后成功。完整生成仍在进行，正式 `manifest.json` 与 `index-v1.bin` 尚未生成。
- 发现 Python code point offset 与 Android UTF-16 `substring` 不一致，修复后聚焦测试通过；检索预算改为计算含来源及 Knowledge Map 的完整格式化文本。修复提交 `203bb942` 和补充测试/记录提交 `e9d275a5` 均已推送。PR #77 保持 Draft。
- 当前本地证据：Python 18/18；JVM 625/625；`compileDebugKotlin`、`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest` PASS；Room/Repository/Context 设备测试 PASS，Resources 13/13 聚焦复测 PASS；Secret scan 与 diff check PASS。Android 真实 BYOK query embedding 设备测试 1/1 PASS（断言 768 维及 HTTP 尝试，Key 未写入仓库）。上述构建发生在正式索引生成前，资产契约、检索质量、升级安装与最终 PR 状态仍待验证。
- Room v15 Schema 已提交，本轮编译后 `git diff --exit-code -- app/schemas/com.elio.jianyu.data.RoundtableDatabase/15.json` PASS。
- 升级安装基线已在共享模拟器准备：使用相同本地调试签名安装旧版 APK，Room 为 v14；建立 1 条议题、1 条用户消息及 Naval/Feynman 两条完成回复。App 重启后议题和 3 条消息仍存在。用户已手动配置 Key；尚未覆盖安装新版 APK，因此升级验收未勾选。
- 独立只读验收在 `59cce9c2` 核对分支、工作区、Python 16/16、JVM 与 CI；正式资产缺失，因此真实召回、资产契约、UI、升级安装均报告未执行。随后完整生成在第 162 个 chunk 遇连续 TLS 握手 EOF，缓存保留 161 个成功向量。新增 RED 测试后将网络异常的有界重试提高至 8 次，退避封顶 60 秒；Python 18/18 PASS，已从 171 个缓存向量续跑。

## Global Constraints

- 实现分支固定为 `codex/skill-knowledge-embedding`，基线为 `codex/gemini-37-38-flash@8a7810f4af086da30401f32d92ea37f4fecb0d66`。
- `SKILL.md` 永远作为 Role Core，每次请求必须加载；不得交给向量召回来决定是否加载。
- Embedding 固定使用 `gemini-embedding-2`，输出维度固定为 `768`；本期不增加模型选择 UI。
- query 固定格式为 `task: search result | query: {currentUserInput}`；文档固定格式为 `title: {title} | text: {headingPath}\n{chunkText}`。
- 官方 Skill Knowledge 使用预生成本地向量索引；运行时只生成 query embedding，不使用 Google File Search。
- 自动检索只能访问当前 Skill 自己的 Knowledge；未显式选择时禁止跨 Skill 召回。
- Skill Knowledge 默认字符预算为 `9_000`，并继续受现有整体 `24_000` 字符门禁约束。
- 最终最多 `8` 个 chunk；候选 Top `12`；同一 document 最多 `2` 个 chunk。
- `CORE`=`SKILL.md`；`KNOWLEDGE`=`references/**/*.md`、`research/**/*.md`、`examples/**/*.md`；`SUPPORTING`=README 类 Markdown。
- 不新增 Embedding/Skill Knowledge 隐私提示、同意弹窗、Banner 或首次使用阻断。
- 不删除或放宽现有用户资料 `sensitive`、网络权限和上下文确认规则。
- 不做用户【我的资料】Embedding、图片/音频/视频 Embedding、云向量库、全局跨 Skill 自动检索。
- 不升级 Kotlin、AGP、Compose、Room、OkHttp 或其他依赖。
- Room 如新增表必须从 v14 迁移到 v15，必须导出 Schema，禁止 destructive migration。
- 任何新网络调用都必须沿用 BYOK Key 池和现有 API 尝试/预算记录，不得新增内置 Key。
- 失败时不回退到旧 `skills_summaries.json` 本地资料 Broker；保留 Role Core 并继续回答。

---

## 文件结构锁定

### 新增生产文件

- `app/src/main/java/com/elio/jianyu/network/GeminiEmbeddingTransport.kt`
  - Gemini Embedding REST 协议、768 维响应解析、BYOK 尝试。
- `app/src/main/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeModels.kt`
  - Manifest / document / chunk / hit / retrieval result 领域模型。
- `app/src/main/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeAssetRepository.kt`
  - 从 assets 读取 manifest、Markdown 正文和二进制向量。
- `app/src/main/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeRetriever.kt`
  - query embedding、cosine、角色隔离、Top-K、每文档上限、字符预算。
- `app/src/main/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeContextFormatter.kt`
  - Knowledge Map + retrieved chunk 的稳定 Prompt 格式。
- `app/src/main/java/com/elio/jianyu/data/SkillKnowledgeContextMigration.kt`
  - Room 14→15。
- `app/src/main/java/com/elio/jianyu/ui/screens/resources/SkillKnowledgeUiState.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/resources/SkillKnowledgeViewModel.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/resources/SkillKnowledgeScreen.kt`
  - 【Skill 资料】只读列表与详情。
- `tools/skill_knowledge/generate_index.py`
- `tools/skill_knowledge/test_generate_index.py`
- `app/src/main/assets/skill_knowledge/manifest.json`
- `app/src/main/assets/skill_knowledge/index-v1.bin`

### 主要修改文件

- `app/src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModel.kt`
- `app/src/main/java/com/elio/jianyu/skill/SkillLoader.kt`
- `app/src/main/java/com/elio/jianyu/execution/ExecutionModels.kt`
- `app/src/main/java/com/elio/jianyu/execution/ExecutionContextBuilder.kt`
- `app/src/main/java/com/elio/jianyu/execution/ExecutionRunCoordinator.kt`
- `app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt`
- `app/src/main/java/com/elio/jianyu/data/MaterialContextModels.kt`
- `app/src/main/java/com/elio/jianyu/data/ResourceLifecycleEntities.kt`
- `app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt`
- `app/src/main/java/com/elio/jianyu/data/MaterialContextRepositoryComponent.kt`
- `app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesUiState.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesOverviewScreen.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesRoute.kt`
- `app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesScreen.kt`
- `app/src/main/java/com/elio/jianyu/network/AiProvider.kt`

### 新增/修改测试

- `app/src/test/java/com/elio/jianyu/network/GeminiEmbeddingTransportTest.kt`
- `app/src/test/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeAssetRepositoryTest.kt`
- `app/src/test/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeRetrieverTest.kt`
- `app/src/test/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeContextFormatterTest.kt`
- `app/src/test/java/com/elio/jianyu/execution/ExecutionContextBuilderTest.kt`
- `app/src/test/java/com/elio/jianyu/execution/ExecutionRunCoordinatorTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeAssetContractTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/data/RoundtableDatabaseMigrationTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/data/MaterialContextRepositoryTest.kt`
- `app/src/androidTest/java/com/elio/jianyu/ui/screens/resources/ResourcesScreenTest.kt`
- `app/src/test/java/com/elio/jianyu/ui/screens/resources/SkillKnowledgeUiStateTest.kt`

---

### Task 1: Gemini Embedding 查询边界

**Files:**
- Create: `app/src/main/java/com/elio/jianyu/network/GeminiEmbeddingTransport.kt`
- Create: `app/src/test/java/com/elio/jianyu/network/GeminiEmbeddingTransportTest.kt`
- Reference: `docs/protocols/models/gemini-embedding-001.md`
- Reference: `app/src/main/java/com/elio/jianyu/network/LiveApiClient.kt`

**Interfaces:**
- Produces:
  - `const val GEMINI_SKILL_KNOWLEDGE_EMBEDDING_MODEL = "gemini-embedding-2"`
  - `const val SKILL_KNOWLEDGE_EMBEDDING_DIMENSION = 768`
  - `fun formatSkillKnowledgeQuery(text: String): String`
  - `suspend fun GeminiEmbeddingTransport.embedQuery(context, sessionId, query, onAttemptStarted): FloatArray`

- [x] **Step 1: 写 RED 测试，固定 query 格式与 768 维解析**

```kotlin
@Test
fun formatSkillKnowledgeQuery_usesAsymmetricSearchPrefix() {
    assertEquals(
        "task: search result | query: AI 教育应该怎么做？",
        formatSkillKnowledgeQuery("  AI 教育应该怎么做？  "),
    )
}

@Test
fun parseEmbeddingResponse_requiresExactly768Values() {
    val json = buildEmbeddingResponse(values = List(768) { 0.25f })
    val vector = parseSkillKnowledgeEmbedding(json)
    assertEquals(768, vector.size)
}
```

同时增加 767 / 769 维必须失败的测试。

- [ ] **Step 2: 运行测试确认失败**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.elio.jianyu.network.GeminiEmbeddingTransportTest"
```

Expected: FAIL，因为 transport / formatter 尚不存在。

- [x] **Step 3: 实现最小 REST Transport**

请求体必须等价于：

```json
{
  "content": {
    "parts": [
      {
        "text": "task: search result | query: AI 教育应该怎么做？"
      }
    ]
  },
  "output_dimensionality": 768
}
```

Endpoint：

```text
https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-2:embedContent
```

Key 获取必须使用：

```kotlin
AiManager.keys(appContext, AiProvider.GEMINI).createAttemptPlan(sessionId)
```

每次真实 HTTP 尝试前调用 `onAttemptStarted()`；不得把 Key 写入日志。

- [x] **Step 4: 跑聚焦测试**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.elio.jianyu.network.GeminiEmbeddingTransportTest"
```

Expected: PASS。

- [x] **Step 5: Commit**

```powershell
git add app/src/main/java/com/elio/jianyu/network/GeminiEmbeddingTransport.kt app/src/test/java/com/elio/jianyu/network/GeminiEmbeddingTransportTest.kt
git commit -m "feat: 增加 Gemini Embedding 查询边界"
```

---

### Task 2: 确定性 Skill Knowledge Manifest 与预生成向量索引

**Files:**
- Create: `tools/skill_knowledge/generate_index.py`
- Create: `tools/skill_knowledge/test_generate_index.py`
- Create generated: `app/src/main/assets/skill_knowledge/manifest.json`
- Create generated: `app/src/main/assets/skill_knowledge/index-v1.bin`
- Test: `app/src/androidTest/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeAssetContractTest.kt`
- Read: `app/src/main/assets/official_skill_catalog_v1.json`
- Read: `app/src/main/assets/skills/**`

**Interfaces:**
- Produces manifest schema version `1`.
- Produces Float32 little-endian vectors, dimension `768`.
- Every document has:
  - `documentId`
  - `skillId`
  - `relativePath`
  - `title`
  - `type` = `CORE|KNOWLEDGE|SUPPORTING`
  - `contentHash`
  - `retrievalEligible`
- Every chunk has:
  - `chunkId`
  - `documentId`
  - `headingPath`
  - `startCharacter`
  - `endCharacter`
  - `vectorOffsetBytes`

- [x] **Step 1: 先写 Python RED 测试**

覆盖：
- `SKILL.md -> CORE / retrievalEligible=false`
- `references/a.md -> KNOWLEDGE / true`
- `references/research/b.md -> KNOWLEDGE / true`
- `examples/demo.md -> KNOWLEDGE / true`
- `README.md -> SUPPORTING / false`
- chunk 不超过 1800 字符；
- overlap 不超过 200；
- 相同输入两次生成 metadata 顺序一致；
- documentId / chunkId 稳定。

Run:

```powershell
python -m unittest tools.skill_knowledge.test_generate_index
```

Expected: FAIL。

- [x] **Step 2: 实现 Markdown 扫描和 heading-aware chunker**

核心纯函数签名：

```python
def classify_markdown(relative_path: str) -> str: ...
def chunk_markdown(document_id: str, text: str, max_chars: int = 1800, overlap_chars: int = 200) -> list[Chunk]: ...
def format_document_for_embedding(title: str, heading_path: str, chunk_text: str) -> str:
    return f"title: {title} | text: {heading_path}\n{chunk_text}"
```

Catalog `assetPath` 是 Skill 根事实源；不得靠硬编码 20 个 legacy folder。

- [x] **Step 3: 让生成器支持 `--validate-only` 和真实生成**

CLI：

```powershell
python tools/skill_knowledge/generate_index.py --repo-root . --validate-only
python tools/skill_knowledge/generate_index.py --repo-root . --model gemini-embedding-2 --dimension 768
```

真实生成只从环境变量读取 `GEMINI_API_KEY`。缺失时必须明确失败，不创建半成品 index。

- [x] **Step 4: 运行 Python 单测**

Run:

```powershell
python -m unittest tools.skill_knowledge.test_generate_index
```

Expected: PASS。

- [ ] **Step 5: 用开发者 Gemini Key 生成正式 assets**

运行前只在本地环境设置 `GEMINI_API_KEY`；不得把值写入命令日志、文件或 Commit。

生成后执行：

```powershell
python tools/skill_knowledge/generate_index.py --repo-root . --validate-only
```

Expected:
- manifest schemaVersion = 1；
- 所有 vector dimension = 768；
- index 二进制长度与 manifest offset 完全吻合；
- 所有 contentHash 与当前 Markdown 一致。

- [x] **Step 6: 增加 Android asset contract test**

测试直接读取 APK assets，至少验证：
- Catalog 中所有 executable Skill 都有 manifest entry；
- `SKILL.md` 为 CORE；
- 有 references/examples 的 Skill 至少存在一个 KNOWLEDGE document；
- vector offset 不越界；
- 每个 vector 768 Float32；
- manifest contentHash 与实际 Markdown 一致。

- [x] **Step 7: 跑资产契约测试可编译**

Run:

```powershell
.\gradlew.bat compileDebugAndroidTestKotlin
```

Expected: PASS。

- [ ] **Step 8: Commit**

```powershell
git add tools/skill_knowledge app/src/main/assets/skill_knowledge app/src/androidTest/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeAssetContractTest.kt
git commit -m "feat: 生成 Skill Knowledge 向量索引"
```

---

### Task 3: 本地 Skill Knowledge Repository 与 Retriever

**Files:**
- Create: `app/src/main/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeModels.kt`
- Create: `app/src/main/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeAssetRepository.kt`
- Create: `app/src/main/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeRetriever.kt`
- Create: `app/src/main/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeContextFormatter.kt`
- Create tests:
  - `app/src/test/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeAssetRepositoryTest.kt`
  - `app/src/test/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeRetrieverTest.kt`
  - `app/src/test/java/com/elio/jianyu/skill/knowledge/SkillKnowledgeContextFormatterTest.kt`

**Interfaces:**

```kotlin
enum class SkillKnowledgeDocumentType { CORE, KNOWLEDGE, SUPPORTING }

data class SkillKnowledgeDocument(
    val documentId: String,
    val skillId: String,
    val relativePath: String,
    val title: String,
    val type: SkillKnowledgeDocumentType,
    val contentHash: String,
    val retrievalEligible: Boolean,
)

data class SkillKnowledgeHit(
    val skillId: String,
    val documentId: String,
    val relativePath: String,
    val title: String,
    val headingPath: String,
    val content: String,
    val score: Float,
    val retrievalOrder: Int,
)

fun interface SkillKnowledgeQueryEmbedder {
    suspend fun embed(
        sessionId: Long,
        currentUserInput: String,
        onAttemptStarted: suspend () -> Unit,
    ): FloatArray
}

sealed interface SkillKnowledgeRetrievalResult {
    data class Available(
        val knowledgeMap: String,
        val hits: List<SkillKnowledgeHit>,
    ) : SkillKnowledgeRetrievalResult

    data class Unavailable(val reasonCode: String) : SkillKnowledgeRetrievalResult
}
```

- [x] **Step 1: RED：Repository 只能按 Skill 暴露自己的文档**

构造 fixture manifest，断言：
- `listDocuments("richard_feynman")` 不出现 `charlie_munger`；
- `loadDocumentContent()` hash 不匹配时失败；
- vector 长度不是 768 时失败。

- [x] **Step 2: RED：Retriever 固定 Top-K 和角色隔离**

用 fake embedder + 小向量 fixture 断言：
- 只搜索 ownerSkillId；
- candidate 只取前 12；
- final 最多 8；
- 同 document 最多 2；
- content 总字符 <= 9000；
- score 顺序稳定；
- embedding exception 返回 `Unavailable`，不抛到回答层。

- [x] **Step 3: RED：Formatter 的 Context Pack 结构**

Expected:

```text
=== Skill Knowledge Map ===
- ...

=== Retrieved Skill Knowledge ===
[source: references/research/01-writings.md#长期主义]
...
```

不得把 SUPPORTING 正文放进 retrieved section。

- [x] **Step 4: 实现 Repository + cosine**

Cosine 必须在已校验维度上运行：

```kotlin
internal fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == 768 && b.size == 768)
    var dot = 0.0
    var aa = 0.0
    var bb = 0.0
    for (index in a.indices) {
        dot += a[index] * b[index]
        aa += a[index] * a[index]
        bb += b[index] * b[index]
    }
    if (aa == 0.0 || bb == 0.0) return 0f
    return (dot / kotlin.math.sqrt(aa * bb)).toFloat()
}
```

禁止为向量检索引入新 native/ANN 依赖。

- [x] **Step 5: 跑三组 JVM 测试**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.elio.jianyu.skill.knowledge.*"
```

Expected: PASS。

- [x] **Step 6: Commit**

```powershell
git add app/src/main/java/com/elio/jianyu/skill/knowledge app/src/test/java/com/elio/jianyu/skill/knowledge
git commit -m "feat: 增加 Skill Knowledge 本地检索器"
```

---

### Task 4: 接入正式 Execution，保持 ContextBuilder 纯函数

**Files:**
- Modify: `app/src/main/java/com/elio/jianyu/execution/ExecutionModels.kt`
- Modify: `app/src/main/java/com/elio/jianyu/execution/ExecutionContextBuilder.kt`
- Modify: `app/src/main/java/com/elio/jianyu/execution/ExecutionRunCoordinator.kt`
- Modify: `app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt`
- Modify tests:
  - `app/src/test/java/com/elio/jianyu/execution/ExecutionContextBuilderTest.kt`
  - `app/src/test/java/com/elio/jianyu/execution/ExecutionRunCoordinatorTest.kt`

**Interfaces:**

新增：

```kotlin
data class ExecutionSkillKnowledgeContext(
    val knowledgeMap: String,
    val hits: List<SkillKnowledgeHit>,
)
```

`ExecutionContextInput` 增加：

```kotlin
val skillKnowledge: ExecutionSkillKnowledgeContext? = null
```

`ExecutionRunCoordinator` 构造器增加：

```kotlin
private val skillKnowledgeRetriever: SkillKnowledgeRetriever
```

- [x] **Step 1: RED：ContextBuilder 顺序与门禁**

测试必须证明：
- `systemInstruction` 仍先包含 participant.systemPrompt；
- Skill Knowledge Context 在 Role Core 后；
- 用户确认资料仍在独立 section；
- Knowledge 加入后整体超 24k 继续触发“执行上下文超过稳定边界”；
- Knowledge 缺失不改变旧输出。

- [x] **Step 2: RED：Coordinator 每个参与者只检索自己的 sourceId**

Fake Retriever 记录 ownerSkillId，双角色运行断言调用顺序：

```text
richard_feynman
charlie_munger
```

不得把所有 participant ids 合成一个全局 query。

- [x] **Step 3: 在 `executeParticipant` build 前执行 retrieval**

sessionId 必须复用正式执行已有稳定 ID：

```kotlin
val sessionId = StableExecutionIds.sessionId(run.issueId)
```

retrieval 的实际网络尝试也必须通过与最终回答相同的 Room API 预算计数路径：

```kotlin
onAttemptStarted = {
    persistence.recordApiCall(
        RecordExecutionApiCallCommand(
            rootRunId = runtime.budget.rootRunId,
            count = 1,
            updatedAt = clock.nowMillis(),
        ),
    )
}
```

Embedding 失败返回 null knowledge context，继续最终回答。

- [x] **Step 4: Runtime wiring**

`JianyuAppRuntimeProvider.create()` 中创建：
- `SkillKnowledgeAssetRepository`
- Gemini-backed `SkillKnowledgeQueryEmbedder`
- `SkillKnowledgeRetriever`

同一个 retriever 注入 `ExecutionRunCoordinator`。

- [x] **Step 5: 跑聚焦测试**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.elio.jianyu.execution.ExecutionContextBuilderTest" --tests "com.elio.jianyu.execution.ExecutionRunCoordinatorTest"
```

Expected: PASS。

- [x] **Step 6: Commit**

```powershell
git add app/src/main/java/com/elio/jianyu/execution app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt app/src/test/java/com/elio/jianyu/execution
git commit -m "feat: 将 Skill Knowledge 接入正式执行链"
```

---

### Task 5: 替换 Top 1 对话中的摘要 Broker 本地资料选择

**Files:**
- Modify: `app/src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModel.kt`
- Modify: `app/src/main/java/com/elio/jianyu/skill/SkillLoader.kt`
- Modify: `app/src/main/java/com/elio/jianyu/network/AiProvider.kt`
- Add/modify focused ViewModel tests under `app/src/test/java/com/elio/jianyu/viewmodel/`

**Interfaces:**
- `RoundtableViewModel` 从 Runtime 获取和 Task 4 相同的 `SkillKnowledgeRetriever`。
- `AiUseCase.MATERIAL_BROKER` 暂保枚举名以避免扩大偏好迁移，本任务只把 displayName/description 更新为“联网决策 / 判断联网检索需求并生成搜索任务”。

- [ ] **Step 1: RED：本地知识选择不再调用文本 Broker**

抽出可测试的决策函数或 gateway，断言 SearchMode.OFF：
- 调用 embedding retriever；
- 不调用 `AiUseCase.MATERIAL_BROKER`；
- 不读取 `skills_summaries.json`；
- final role prompt 含 retrieved chunks。

- [ ] **Step 2: RED：AUTO/ON 的联网能力继续工作**

AUTO：
- local knowledge 由 retriever 提供；
- 文本 decision 只返回 `needSearch/searchQueries`。

ON：
- 强制联网；
- local knowledge 仍来自 retriever。

OFF：
- 不联网；
- local knowledge 仍可检索。

- [x] **Step 3: 修改 `RoundtableViewModel`**

删除：
- `skillsSummaries`
- `loadSkillsSummariesOnce()`
- Broker Prompt 中候选文件列表；
- `selectedFiles`
- `selectedExamples`
- `selectedReferences`
- 整篇 Markdown 读取拼接。

保留：
- SearchMode 的联网决策；
- web grounding；
- thinking directive；
- 最终回答。

最终 role context 由：

```text
mainSkillPrompt
+ SkillKnowledgeContextFormatter
+ thinking directive
+ optional web grounding
```

组成。

- [x] **Step 4: 删除无调用方 `SkillLoader.loadSelectedFiles`**

保留 `loadSkill` 和仍有真实调用方的 asset helper。

- [x] **Step 5: 跑聚焦 JVM 测试**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.elio.jianyu.viewmodel.*" --tests "com.elio.jianyu.skill.knowledge.*"
```

Expected: PASS。

- [x] **Step 6: Commit**

```powershell
git add app/src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModel.kt app/src/main/java/com/elio/jianyu/skill/SkillLoader.kt app/src/main/java/com/elio/jianyu/network/AiProvider.kt app/src/test/java/com/elio/jianyu/viewmodel
git commit -m "refactor: 用向量检索替换 Skill 摘要 Broker"
```

---

### Task 6: 增加显式跨角色 Skill 资料 Context Source 与 Room v15 快照

**Files:**
- Modify: `app/src/main/java/com/elio/jianyu/data/MaterialContextModels.kt`
- Modify: `app/src/main/java/com/elio/jianyu/data/ResourceLifecycleEntities.kt`
- Modify: `app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt`
- Modify: `app/src/main/java/com/elio/jianyu/data/MaterialContextRepositoryComponent.kt`
- Create: `app/src/main/java/com/elio/jianyu/data/SkillKnowledgeContextMigration.kt`
- Modify: `app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt`
- Modify tests:
  - `app/src/androidTest/java/com/elio/jianyu/data/MaterialContextRepositoryTest.kt`
  - `app/src/androidTest/java/com/elio/jianyu/data/RoundtableDatabaseMigrationTest.kt`
- Generated schema: `app/schemas/com.elio.jianyu.data.RoundtableDatabase/15.json`

**Interfaces:**

`ContextSourceType`：

```kotlin
SKILL_KNOWLEDGE("skill_knowledge")
```

新实体：

```kotlin
@Entity(
    tableName = "skill_knowledge_usage_snapshots",
    indices = [
        Index(value = ["runId"]),
        Index(value = ["sourceSkillId", "documentId"]),
        Index(value = ["runId", "sourceSkillId", "documentId"], unique = true),
    ],
)
data class SkillKnowledgeUsageSnapshotEntity(
    @PrimaryKey val id: String,
    val issueId: String,
    val stageId: String,
    val runId: String?,
    val sourceSkillId: String,
    val documentId: String,
    val titleSnapshot: String,
    val relativePathSnapshot: String,
    val contentSnapshot: String,
    val contentHash: String,
    val userConfirmedAt: Long,
    val createdAt: Long,
)
```

`ContextUsageWriteSet` 增加：

```kotlin
val skillKnowledge: List<SkillKnowledgeUsageSnapshotEntity> = emptyList()
```

- [x] **Step 1: RED：Context validator 接受显式 Skill Knowledge，不要求隐私确认**

构造：
- sourceType = SKILL_KNOWLEDGE
- networkAllowed = true
- sensitive = false
- sensitiveConfirmed = false

Expected: 不产生 `SENSITIVE_CONFIRMATION_REQUIRED`。

- [x] **Step 2: RED：Repository 写入、重放和冲突**

验证：
- 同一 run/sourceSkillId/documentId 只允许一个 snapshot；
- 内容快照完整保存；
- `listRunContextUsage` 返回 SKILL_KNOWLEDGE；
- 旧 Material / Personal Context 行为不变。

- [x] **Step 3: 实现 v14→v15 Migration**

`SkillKnowledgeContextMigration.MIGRATION_14_15` 只创建新表和索引，不改写旧数据。

`RoundtableDatabase`：
- version = 15；
- entities 加 `SkillKnowledgeUsageSnapshotEntity`；
- `ALL_MIGRATIONS` 加 14→15。

- [x] **Step 4: 更新 DAO / Repository**

对于 `ConfirmedContextItem.sourceType == SKILL_KNOWLEDGE`：
- 不查 `MaterialReferenceEntity`；
- 要求 content 非空；
- 要求 `contentHash == ContextContentHasher.hash(content)`；
- 强制 `networkAllowed=true`；
- 强制 `sensitive=false`；
- 写入 usage snapshot。

不得把它创建成用户 Material。

- [x] **Step 5: 运行 migration + repository AndroidTest**

Run:

```powershell
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.RoundtableDatabaseMigrationTest
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.data.MaterialContextRepositoryTest
```

Expected: PASS。

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/elio/jianyu/data app/src/androidTest/java/com/elio/jianyu/data app/schemas/com.elio.jianyu.data.RoundtableDatabase/15.json
git commit -m "feat: 保存 Skill 资料显式使用快照"
```

---

### Task 7: 【资料】新增只读 Skill 资料区域

**Files:**
- Create: `app/src/main/java/com/elio/jianyu/ui/screens/resources/SkillKnowledgeUiState.kt`
- Create: `app/src/main/java/com/elio/jianyu/ui/screens/resources/SkillKnowledgeViewModel.kt`
- Create: `app/src/main/java/com/elio/jianyu/ui/screens/resources/SkillKnowledgeScreen.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesUiState.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesOverviewScreen.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesRoute.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesScreen.kt`
- Add/modify tests:
  - `app/src/test/java/com/elio/jianyu/ui/screens/resources/SkillKnowledgeUiStateTest.kt`
  - `app/src/androidTest/java/com/elio/jianyu/ui/screens/resources/ResourcesScreenTest.kt`

**Interfaces:**

```kotlin
data class SkillKnowledgeDocumentUiItem(
    val documentId: String,
    val skillId: String,
    val skillName: String,
    val relativePath: String,
    val title: String,
    val type: SkillKnowledgeDocumentType,
    val content: String,
)

sealed interface SkillKnowledgeUiState {
    data object Loading : SkillKnowledgeUiState
    data class Content(
        val documents: List<SkillKnowledgeDocumentUiItem>,
        val query: String = "",
        val selectedSkillId: String? = null,
        val selectedDocumentId: String? = null,
    ) : SkillKnowledgeUiState
    data class Failure(val message: String) : SkillKnowledgeUiState
}
```

Test tags：

```text
resources_overview_skill_knowledge
skill_knowledge_screen
skill_knowledge_search
skill_knowledge_skill_<skillId>
skill_knowledge_document_<documentId>
skill_knowledge_detail
skill_knowledge_use_in_conversation
```

- [x] **Step 1: RED：UiState 搜索与分组**

搜索命中：
- Skill 名称；
- document title；
- relativePath。

类型标签必须区分：
- 角色核心；
- 参考知识；
- 说明文档。

- [x] **Step 2: RED：Overview 出现独立 Skill 资料入口**

保留现有两个主 summary card；在其下新增独立 Skill 资料入口，不把它伪装成第三种用户 Material 生命周期对象。

- [x] **Step 3: 实现只读列表/详情**

详情不得出现：
- 编辑；
- 删除；
- 归档；
- 清除；
- 敏感开关。

详情必须显示：
- Skill 角色；
- 文档类型；
- relativePath；
- Markdown 正文。

- [x] **Step 4: Route 接入**

沿用 `ResourcesRoute` 的页面内状态，不新增全局一级导航。

BackHandler：
- Skill 文档详情 → Skill 资料列表；
- Skill 资料列表 → 资料总览。

- [x] **Step 5: 跑 JVM + Compose AndroidTest**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.elio.jianyu.ui.screens.resources.SkillKnowledgeUiStateTest"
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.resources.ResourcesScreenTest
```

Expected: PASS。

- [x] **Step 6: Commit**

```powershell
git add app/src/main/java/com/elio/jianyu/ui/screens/resources app/src/test/java/com/elio/jianyu/ui/screens/resources app/src/androidTest/java/com/elio/jianyu/ui/screens/resources
git commit -m "feat: 在资料页增加 Skill 资料区域"
```

---

### Task 8: Skill 资料“带入当前会话”显式跨角色链路

**Files:**
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/resources/SkillKnowledgeViewModel.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/resources/SkillKnowledgeScreen.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/resources/ResourcesRoute.kt`
- Modify: `app/src/main/java/com/elio/jianyu/ui/screens/dialog/DialogRoute.kt`
- Modify as required: `app/src/main/java/com/elio/jianyu/ui/screens/context/ContextConfirmationUiState.kt`
- Modify tests:
  - `app/src/test/java/com/elio/jianyu/ui/screens/context/ContextConfirmationUiStateTest.kt`
  - `app/src/androidTest/java/com/elio/jianyu/ui/screens/context/ContextConfirmationDialogTest.kt`
  - relevant dialog/context tests

**Interfaces:**
- “带入当前会话”产生 `ConfirmedContextItem(sourceType = SKILL_KNOWLEDGE, ...)`。
- 该 item 的：
  - `sourceId = documentId`
  - `sourceKind = sourceSkillId`
  - `sourceLocator = relativePath`
  - `networkAllowed = true`
  - `sensitive = false`
  - `sensitiveConfirmed = false`

- [ ] **Step 1: RED：显式选择才跨角色**

场景：
- 当前角色 = Feynman；
- Munger document 存在；
- 未选择：Feynman auto retrieval hits 中不得出现 Munger；
- 用户显式“带入当前会话”：Context list 出现 Munger document；
- Feynman 自己的 auto retrieval 仍保持 Feynman-only。

- [x] **Step 2: RED：不出现隐私提示**

SKILL_KNOWLEDGE item 不得出现：
- 敏感资料确认；
- 网络权限确认；
- Embedding 隐私提示。

只保留正常“本次将带入哪些资料”的用户可见确认语义。

- [x] **Step 3: 实现 UI action 到 ContextSelectionDraft**

不要创建 `MaterialReferenceEntity` 副本。

- [ ] **Step 4: 验证 usage snapshot**

执行一次正式 Run 后：
- `skill_knowledge_usage_snapshots` 有一条完整快照；
- title/path/content/hash 与用户确认时一致。

- [x] **Step 5: 跑聚焦测试**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.elio.jianyu.ui.screens.context.*"
.\gradlew.bat connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.elio.jianyu.ui.screens.context.ContextConfirmationDialogTest
```

Expected: PASS。

- [x] **Step 6: Commit**

```powershell
git add app/src/main/java/com/elio/jianyu/ui/screens/resources app/src/main/java/com/elio/jianyu/ui/screens/dialog app/src/main/java/com/elio/jianyu/ui/screens/context app/src/test/java/com/elio/jianyu/ui/screens/context app/src/androidTest/java/com/elio/jianyu/ui/screens/context
git commit -m "feat: 支持显式带入其他 Skill 资料"
```

---

### Task 9: 删除旧 Skill Summary 双轨并做静态审计

**Files:**
- Delete: `app/src/main/assets/skills_summaries.json`
- Delete or archive if now unused:
  - `workspace/tools/generate_summaries.py`
  - `workspace/tools/generate_summaries_ai.py`
- Modify: `docs/architecture/broker-precision-decision-with-summaries.md`
- Modify tests/docs that assert old summary mechanism.

- [x] **Step 1: 确认无生产调用方**

Run:

```powershell
git grep -n "skills_summaries\|loadSkillsSummariesOnce\|selectedFiles" -- app/src/main
```

Expected: 无旧本地资料选择调用方。

- [x] **Step 2: 删除旧 asset/helper**

不得保留“Embedding 失败 → summary Broker”的隐藏兼容分支。

- [x] **Step 3: 更新架构文档**

把旧 Broker 文档明确标记为历史，并链接：
- `docs/superpowers/specs/2026-09-26-skill-knowledge-embedding-design.md`

若旧生成脚本无其他调用方，删除；如果仍被历史/开发任务真实使用，则移动到明确 historical 路径而不是继续作为生产生成器。

- [x] **Step 4: 静态检查**

Run:

```powershell
git diff --check
git grep -n "skills_summaries" -- app/src/main
```

Expected: 无生产命中。

- [x] **Step 5: Commit**

```powershell
git add -A
git commit -m "chore: 清理旧 Skill 摘要检索链路"
```

---

### Task 10: 全量验证、真实 API 验收与 PR 交付

**Files:**
- Create: `docs/testing/skill-knowledge-embedding-local-readonly-acceptance-prompt.md`
- Update this plan checkboxes as tasks complete.

- [ ] **Step 1: JVM + 编译**

Run:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Expected: PASS。

- [ ] **Step 2: Lint + Debug APK + AndroidTest APK**

Run:

```powershell
.\gradlew.bat lintDebug assembleDebug assembleDebugAndroidTest
```

Expected: PASS。

- [ ] **Step 3: 数据库设备测试**

至少运行：
- `RoundtableDatabaseMigrationTest`
- `MaterialContextRepositoryTest`
- `SkillKnowledgeAssetContractTest`

Expected: PASS。

- [ ] **Step 4: UI 设备测试**

验证：
- 资料总览存在 Skill 资料入口；
- Skill 角色分组；
- Core / Knowledge / Supporting；
- 详情只读；
- Back 链；
- 带入当前会话；
- 无新增隐私提示。

- [x] **Step 5: 真实 Gemini API query embedding**

只读验收环境提供测试 Key 后，执行一个真实 query：
- model = `gemini-embedding-2`
- output dimension = 768

必须报告：
- HTTP 成功；
- vector size = 768；
- 不回显 Key。

- [ ] **Step 6: 真实 Skill 召回质量 Smoke Test**

固定至少三组：

```text
Richard Feynman：教育 / 解释复杂概念
Charlie Munger：认知偏差 / 决策
Duan Yongping：企业文化 / 长期主义
```

每组输出：
- Top hits 的 document path；
- heading；
- score；
- 是否属于正确 Skill。

PASS 条件：
- 无跨 Skill 自动命中；
- 至少一条明显相关来源进入 Top hits；
- Context Pack 未超 9k。

- [ ] **Step 7: 角色回答回归**

同一问题分别走：
- Top 1 `RoundtableViewModel`；
- 正式 `ExecutionRunCoordinator`。

验证二者都包含同一 Retriever 来源语义，不再出现“一条链有 references、另一条只有 SKILL.md”。

- [ ] **Step 8: 多角色独立性回归**

两个角色同一轮：
- A 的 retrieval query 不包含 B 本轮生成结果；
- B 的 retrieval query 不包含 A 本轮生成结果；
- 各自 hits 仅来自自身 skillId；
- 只有用户显式选择的跨角色 Skill 文档能作为 confirmed context 出现。

- [ ] **Step 9: Secret / diff / branch audit**

Run:

```powershell
pwsh.exe -File .\tools\check-secrets.ps1 -IncludeHistory
git diff --check
git status --short
git log -10 --oneline
```

Expected:
- 无 Key 泄漏；
- 无无关修改；
- 工作区 clean。

- [ ] **Step 10: GitHub CI**

Push 后检查当前 Head CI；失败只读取失败步骤和关键日志，不重复拉取完整正常日志。

- [x] **Step 11: 创建 Draft PR**

Base 应按该功能集成策略指向 `codex/gemini-37-38-flash`，除非执行时该父分支已经完成合并且用户另有指示。

PR 描述必须列出：
- Embedding 设计；
- 预生成 index；
- 两条执行链；
- Room v15；
- Skill 资料 UI；
- 显式跨角色；
- 无新增隐私提示；
- 实际验证与未验证项。

- [ ] **Step 12: 本地 AI 只读验收**

生成的 Prompt 必须要求：
- 不修改代码；
- 不自动修复；
- 不提交；
- 不 push；
- 不 merge；
- 只做 Windows 10 / JDK 17 / Android / 真机或模拟器验证；
- 返回 PASS/FAIL、失败命令、文件/行号、关键日志；
- 真实 API Key 只从本地安全环境读取，不回显。

---

## Plan Self-Review

### Spec coverage

- Role Core 永久在线：Task 4 / 5。
- 768 维 Embedding：Task 1 / 2 / 3。
- 本地静态 index：Task 2 / 3。
- 两条执行链共用 Retriever：Task 4 / 5。
- 角色默认知识隔离：Task 3 / 4 / 10。
- Skill 资料区域：Task 7。
- 显式跨角色资料：Task 6 / 8。
- Room v15 快照：Task 6。
- 无新增隐私提示：Global Constraints / Task 8 / 10。
- 删除旧 summary Broker：Task 5 / 9。
- 真实 API 与本地验收：Task 10。

### Placeholder scan

占位符扫描通过；所有生产范围都有明确 Task、文件、接口和验证命令。

### Type consistency

- `SkillKnowledgeHit` 在 Retriever、Formatter、Execution 三处统一使用。
- `ContextSourceType.SKILL_KNOWLEDGE` 只用于用户显式跨角色 context，不代替自动 retrieval。
- 自动 retrieval 使用 `ExecutionSkillKnowledgeContext`，不写成用户 Material。
- `SkillKnowledgeUsageSnapshotEntity` 只保存用户显式选择的静态 Skill 文档快照。
