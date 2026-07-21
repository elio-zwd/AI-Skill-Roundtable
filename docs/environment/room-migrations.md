# Room Schema 与数据迁移

## 当前数据库版本

```text
RoundtableDatabase version = 5
```

当前 Schema 由 Room/KSP 自动生成并提交到：

```text
app/schemas/com.example.skillroundtable.data.RoundtableDatabase/5.json
```

禁止手工编造或修改 Schema JSON。实体或数据库版本发生变化后，应通过 Gradle 编译重新生成 Schema，并将真实差异与迁移实现一同提交。

## 支持的迁移路径

当前显式支持：

```text
3 → 4
4 → 5
3 → 4 → 5
```

### 3 → 4

为 `messages` 表增加：

```text
roundIndex INTEGER NOT NULL DEFAULT 0
```

现有消息正文和其他字段必须保留。

### 4 → 5

为 `messages` 表增加：

```text
audioFilePath TEXT
audioFormat TEXT
audioSizeBytes INTEGER NOT NULL DEFAULT 0
```

并为 `characters` 表增加：

```text
voiceConfig TEXT NOT NULL DEFAULT 'Aoede'
```

已有角色、会话和消息数据必须保留。

## 不支持的旧版本

当前没有可靠的 v1 或 v2 Schema，也没有经过验证的 1→5、2→5 迁移实现。

应用不再使用 `fallbackToDestructiveMigration()`。遇到缺失迁移路径时，Room 会拒绝打开数据库并明确失败，而不是静默删除用户聊天数据。

在补充更早版本迁移前，必须先取得对应版本的真实历史 Schema 或数据库样本，并增加数据保留测试。

## 自动化测试

Instrumentation 测试文件：

```text
app/src/androidTest/java/com/example/skillroundtable/data/RoundtableDatabaseMigrationTest.kt
```

覆盖：

- 3→4 增加 `roundIndex` 并保留消息；
- 4→5 与当前 v5 Schema 完全匹配并保留角色和消息；
- 3→5 完整迁移链；
- 不支持版本安全失败且不删除原数据库文件和标记数据。

本地执行：

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

GitHub Actions 使用 API 30 x86_64 Emulator 执行相同的 Instrumentation Test。

## Schema 变更流程

修改 Room 实体或数据库版本时必须同时完成：

1. 递增 `@Database(version = ...)`；
2. 编写明确的 `Migration`；
3. 运行 Gradle/KSP 生成新版本 Schema；
4. 提交新 Schema JSON；
5. 增加旧版本到新版本的 Migration Test；
6. 验证 CI 中 Schema 工作树无未提交变化；
7. 禁止通过 destructive migration、删除测试或降低 Schema 校验绕过失败。
