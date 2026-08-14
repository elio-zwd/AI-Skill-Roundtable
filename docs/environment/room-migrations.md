# Room Schema 与数据迁移

## 当前数据库版本

```text
RoundtableDatabase version = 13
```

当前数据库名保持为 `roundtable_database`，并通过连续、保留数据的迁移链支持历史安装升级。

## Schema 路径边界

### 历史旧包 Schema

```text
app/schemas/com.elio.skillroundtable.data.RoundtableDatabase/5.json
```

该文件是旧 FQCN 的历史基线，必须保留且不得修改。它用于证明包名迁移前后的 Room 结构语义没有变化，不代表当前应用仍使用旧包名。

### 当前新包 Schema

```text
app/schemas/com.elio.jianyu.data.RoundtableDatabase/13.json
```

该路径对应当前 `com.elio.jianyu.data.RoundtableDatabase`。Schema 由 Room/KSP 生成，禁止人工编造或修改。

## 换行符与一致性验证

CI Artifact 中 Room/KSP 输出了旧、新两个 FQCN 的 `5.json`，二者均为 LF，原始 SHA-256 均为：

```text
1537e500199e09fb4b7591f9ce5e3861c585b7325e9ede6a3e0d7403da39d695
```

Windows 工作区可能按照 Git 配置把已跟踪的旧文件检出为 CRLF，而 KSP 新生成文件为 LF。在该情形下，工作区原始 SHA-256 会不同，但规范化换行后的内容仍应完全一致。因此验证规则为：

- 旧 FQCN Schema 相对固定 Base Commit 不得修改；
- 两份文件必须均可解析为 JSON；
- 规范化 CRLF / LF 后文本必须完全一致；
- 结构化 JSON 必须完全一致；
- `formatVersion`、数据库 `version`、`identityHash`、Entity、字段、索引、外键和 setup queries 必须一致；
- 不得为了制造某个工作区原始哈希而修改旧历史 Schema。

当前两份 Schema 的固定语义值包括：

```text
formatVersion：1
database version：5
identityHash：63f0fb76786f10fbeee22a6655997b5d
```

`tools/check-app-identity.ps1` 和 GitHub Android CI 同时执行旧 Schema 冻结检查、JSON 解析、换行规范化比较和结构化比较。构建完成后还必须确认 `app/schemas` 没有未提交差异或新文件。

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
1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11 → 12 → 13
```

因此支持从 v1 至 v12 直接升级到当前 v13。

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

`characters` 会以数据保留方式重建为目标 Schema，并补充：

```text
voiceConfig TEXT NOT NULL DEFAULT 'Aoede'
```

该步骤还会创建 `character_groups` 表，并使用 `INSERT OR IGNORE` 初始化四个预设分组；已存在的自定义分组不会被覆盖。

### 5 → 12

v6 至 v12 依次引入议题、阶段、执行运行时、资料与个人背景、协作、阶段推进，以及议题归档与清理状态机。每一步均有对应的显式 Migration 和已提交 Schema。

### 12 → 13

- `issues` 增加 `defaultThinkingPolicy TEXT NOT NULL DEFAULT 'auto'`；
- `execution_runs` 增加 `actualModelId`、`actualThinkingLevel` 与 `thinkingLevelSource` 三个非空快照字段；
- 因后三个字段在 v13 Schema 中没有默认值，迁移重建 `execution_runs` 为精确 Schema，保留历史运行记录、索引和外键；
- 历史运行缺少可还原的原始配置，迁移按当时默认值写入 `gemini-3.6-flash`、自动路由来源，并对标准运行写入 `medium`、交叉讨论运行写入 `high`。

## 数据安全策略

应用不使用 `fallbackToDestructiveMigration()`。缺失迁移路径或 Schema 不匹配时，Room 会明确失败，而不是静默删除聊天记录。

迁移测试必须验证：

- 角色名称、Prompt、Skill 路径和向量保留；
- 会话标题与创建时间保留；
- 消息正文、轮次和默认音频字段正确；
- 自定义角色分组保留；
- 四个预设分组存在；
- 最终数据库与 Room 自动生成的目标版本 Schema 完全匹配。

## 自动化测试

Instrumentation 测试文件：

```text
app/src/androidTest/java/com/elio/jianyu/data/
```

覆盖：

- 历史版本到当前版本的连续升级；
- v12→v13 的思考策略与执行快照回填；
- 每条路径的数据保留、外键检查和最终 Schema 校验。

本地执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

GitHub Actions 使用 API 30 x86_64 Emulator 执行相同的 Instrumentation Test。身份隔离测试会先在全新安装上独立运行，随后清理临时见域安装并排除身份类运行剩余完整套件，避免测试顺序污染空沙箱断言。

## Schema 变更流程

修改 Room 实体或数据库版本时必须同时完成：

1. 递增 `@Database(version = ...)`；
2. 编写明确的数据保留式 `Migration`；
3. 运行 Gradle/KSP 生成新版本 Schema；
4. 提交新 Schema JSON；
5. 增加所有受支持旧版本到新版本的 Migration Test；
6. 验证 CI 中 Schema 工作树无未提交变化；
7. 禁止通过 destructive migration、删除测试或降低 Schema 校验绕过失败。
