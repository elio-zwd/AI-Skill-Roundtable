# Room Schema 与数据迁移

## 当前数据库版本

```text
RoundtableDatabase version = 5
```

本次应用身份迁移只改变 Kotlin FQCN、`namespace` 和 `applicationId`，不改变 Room Entity、DAO、Database 结构、数据库名、Migration 或数据库版本。

## Schema 路径边界

### 历史旧包 Schema

```text
app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json
```

该文件是旧 FQCN 的历史基线，必须保留且不得修改。它用于证明包名迁移前后的 Room 结构语义没有变化，不代表当前应用仍使用旧包名。

### 当前新包 Schema

```text
app/schemas/com.elio.jianyu.data.RoundtableDatabase/5.json
```

该路径对应当前 `com.elio.jianyu.data.RoundtableDatabase`。Task 4 将通过 Room/KSP 真实生成并提交该文件；Task 3 不创建、不手工编写也不提交新 Schema。PR09-01 最终应同时保留旧历史 Schema 与新当前 Schema。

## 换行符与一致性验证

Task 2 编译期间已经临时生成新 FQCN `5.json`，并得到以下事实：

```text
旧 5.json：CRLF
KSP 新生成 5.json：LF
```

两份文件的 253 行 JSON 数据语义一致，但原始 SHA-256 因换行符不同而不同。因此：

- 不得声称新旧文件字节哈希一致；
- 不得为了制造相同哈希而修改旧历史 Schema；
- Task 4 必须使用可复现方式验证 JSON 语义、`formatVersion`、数据库 `version`、`identityHash`、Entity、字段、索引和 setup queries 一致；
- Task 4 必须记录原始文件哈希、规范化文本或结构化比较结果及换行符差异。

禁止手工编造或修改 Room Schema JSON。实体或数据库版本发生变化后，应通过 Gradle/KSP 重新生成 Schema，并将真实差异与迁移实现一同提交。

## 历史 Schema 来源

v1～v4 的结构依据仓库历史提交中的真实 Room 实体和 `@Database(version = ...)` 还原，而不是凭字段名称猜测：

- v1：基础角色、会话和消息表；
- v2：`characters` 增加 `skillAssetPath`；
- v3：`characters` 增加 `skillDescriptionVector`；
- v4：`messages` 增加 `roundIndex`；
- v5：增加音频字段、`voiceConfig` 和 `character_groups` 表。

## 支持的迁移路径

当前注册完整的顺序迁移：

```text
1 → 2 → 3 → 4 → 5
```

因此支持从 v1、v2、v3 或 v4 直接升级到当前 v5。

### 1 → 2

为 `characters` 增加：

```text
skillAssetPath TEXT NOT NULL
```

旧数据使用空字符串作为安全默认值。

### 2 → 3

为 `characters` 增加：

```text
skillDescriptionVector TEXT NOT NULL
```

旧数据使用空字符串作为安全默认值。

### 3 → 4

为 `messages` 增加：

```text
roundIndex INTEGER NOT NULL DEFAULT 0
```

### 4 → 5

为 `messages` 增加：

```text
audioFilePath TEXT
audioFormat TEXT
audioSizeBytes INTEGER NOT NULL DEFAULT 0
```

`characters` 会以数据保留方式重建为当前精确 Schema，并补充：

```text
voiceConfig TEXT NOT NULL DEFAULT 'Aoede'
```

该步骤还会创建 `character_groups` 表，并使用 `INSERT OR IGNORE` 初始化四个预设分组；已存在的自定义分组不会被覆盖。

## 数据安全策略

应用不使用 `fallbackToDestructiveMigration()`。缺失迁移路径或 Schema 不匹配时，Room 会明确失败，而不是静默删除聊天记录。

迁移测试必须验证：

- 角色名称、Prompt、Skill 路径和向量保留；
- 会话标题与创建时间保留；
- 消息正文、轮次和默认音频字段正确；
- 自定义角色分组保留；
- 四个预设分组存在；
- 最终数据库与 Room 自动生成的 v5 Schema 完全匹配。

## 自动化测试

Instrumentation 测试文件：

```text
app/src/androidTest/java/com/elio/jianyu/data/RoundtableDatabaseMigrationTest.kt
```

覆盖：

- 1→5；
- 2→5；
- 3→5；
- 4→5；
- 已存在自定义分组的 4→5；
- 每条路径的数据保留和最终 Schema 校验。

本地执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

GitHub Actions 使用 API 30 x86_64 Emulator 执行相同的 Instrumentation Test。

## Schema 变更流程

修改 Room 实体或数据库版本时必须同时完成：

1. 递增 `@Database(version = ...)`；
2. 编写明确的数据保留式 `Migration`；
3. 运行 Gradle/KSP 生成新版本 Schema；
4. 提交新 Schema JSON；
5. 增加所有受支持旧版本到新版本的 Migration Test；
6. 验证 CI 中 Schema 工作树无未提交变化；
7. 禁止通过 destructive migration、删除测试或降低 Schema 校验绕过失败。
