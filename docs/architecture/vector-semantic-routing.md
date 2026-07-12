# 描述向量语义路由方案说明 (vector-semantic-routing.md)

本文件详述了 **AI 智囊圆桌** 的向量语义路由（Vector Semantic Routing）设计方案与技术规格。该方案旨在通过 LLM 向量匹配，为用户的提问智能分配发言优先级，实现“最懂该领域的智囊首发，其余智囊连环论证”的动态脑暴。

---

## 1. 整体架构与数据流

向量语义路由由**编译期向量提取**与**运行期实时计算**两部分组成：

```
【 编译期（Python 脚本） 】
 docs/skills/*/SKILL.md (YAML description)
       ↓
 调用 text-embedding-004 API (使用 .env key)
       ↓
 预计算每个角色的 768 维描述向量 (descriptionVector)
       ↓
 保存至 assets/skills_config.json
       
--------------------------------------------------------------

【 运行期（Android Kotlin） 】
 用户输入提问 (Query)
       ↓
 调用 text-embedding-004 API 转化为 queryVector (使用多 Key 旋转池)
       ↓
 本地读取角色 descriptionVector (从 Room 动态载入)
       ↓
 本地计算余弦相似度 (Cosine Similarity)
       ↓
 对激活的智囊角色列表进行降序排列 (Expert-First Sorting)
       ↓
 按排序顺序触发 runRoundtableSequence (依次作答)
```

---

## 2. 数据结构变化

### 2.1 skills_config.json 与 Character 实体扩展
在 Character 实体类和 Room 数据库中，扩展以下字段：
- `skillDescriptionVector: String` — 用 JSON 字符串存储 768 维 Float 向量，便于本地存储。

### 2.2 API 请求模型
新增 `EmbedContentRequest` 和 `EmbedContentResponse` 结构以调用 `text-embedding-004`：

```kotlin
@Serializable
data class EmbedContentRequest(
    val model: String = "models/text-embedding-004",
    val content: Content
)

@Serializable
data class EmbedContentResponse(
    val embedding: Embedding
)

@Serializable
data class Embedding(
    val values: List<Float>
)
```

---

## 3. 算法实现：余弦相似度

在 Kotlin 本地代数计算中，计算提问向量 $A$ 与智囊描述向量 $B$ 的夹角余弦值：

$$\text{Similarity} = \frac{\sum (A_i \times B_i)}{\sqrt{\sum A_i^2} \times \sqrt{\sum B_i^2}}$$

由于向量通常已归一化，其余弦相似度等价于点积（Dot Product），计算开销小于 0.1 毫秒。

---

## 4. 动态发言排序策略 (Expert-First Sorting)

当用户在 UI 中开启“**专家先发**（智能排序）”模式：
1. 捕获提问 -> 获取 `queryVector`。
2. 过滤当前激活的 `activeCharacters`。
3. 对每个智囊计算其与 `queryVector` 的相似度，存储为临时权值。
4. 按相似度从高到低对 `activeCharacters` 进行 `sortedByDescending` 重新排队。
5. 按照排好的队列触发 `runRoundtableSequence` 作答。
6. 如果智能排序关闭，则回归原始的默认物理顺序（按 `order` 排序）。

---

## 5. UI 交互设计

- 在圆桌席位图上方标题栏中，紧贴“自动顺延”开关左侧，新增一个开关 “**专家先发**”。
- 绑定 ViewModel 中的 `isSemanticRoutingEnabled` 双向状态流。
