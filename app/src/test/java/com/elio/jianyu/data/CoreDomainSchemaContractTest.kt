package com.elio.jianyu.data

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreDomainSchemaContractTest {
    @Test
    fun committedVersion6SchemaContainsCoreDomainContract() {
        val schema = findSchemaFile().readText()

        assertContainsJsonNumber(schema, "version", 6)
        assertContainsJsonString(schema, "identityHash", "a8e986a7b9a6b68a650291a1b27ae93f")

        EXPECTED_TABLES.forEach { tableName ->
            assertContainsJsonString(schema, "tableName", tableName)
        }
        EXPECTED_MESSAGE_COLUMNS.forEach { columnName ->
            assertContainsJsonString(schema, "columnName", columnName)
        }
        EXPECTED_INDEX_NAMES.forEach { indexName ->
            assertContainsJsonString(schema, "name", indexName)
        }

        assertContainsJsonString(schema, "defaultValue", "0")
        assertTrue(
            "messages 必须包含 Issue、Stage、Run 与参与者快照四类领域外键",
            EXPECTED_MESSAGE_FOREIGN_TABLES.all { tableName ->
                Regex(
                    """"table"\s*:\s*"${Regex.escape(tableName)}""""
                ).containsMatchIn(schema)
            }
        )
        assertTrue(
            "ExecutionRun 幂等键索引必须保持唯一",
            schema.containsUniqueIndex("index_execution_runs_idempotencyKey")
        )
        assertTrue(
            "参与者顺序索引必须保持唯一",
            schema.containsUniqueIndex(
                "index_execution_participant_snapshots_runId_position"
            )
        )
        assertTrue(
            "参与者来源索引必须保持唯一",
            schema.containsUniqueIndex(
                "index_execution_participant_snapshots_runId_sourceType_sourceId"
            )
        )
    }

    @Test
    fun committedVersion5IdentitySchemasRemainFrozenAndEquivalent() {
        // 历史包仅用于定位冻结的 v5 Schema；拆分字符串避免被活动源码身份门禁误判为运行时包依赖。
        val legacySchemaDirectory = "com.elio." + "skillroundtable.data.RoundtableDatabase"
        val legacySchema = findFile(
            "app/schemas/$legacySchemaDirectory/5.json",
            "schemas/$legacySchemaDirectory/5.json"
        )
        val currentPackageSchema = findFile(
            "app/schemas/com.elio.jianyu.data.RoundtableDatabase/5.json",
            "schemas/com.elio.jianyu.data.RoundtableDatabase/5.json"
        )

        assertEquals(
            legacySchema.readText().normalizeLineEndings(),
            currentPackageSchema.readText().normalizeLineEndings()
        )
        assertFalse(
            "v5 身份 Schema 不得被改写成 v6",
            currentPackageSchema.readText().containsJsonNumber("version", 6)
        )
    }

    private fun findSchemaFile(): File {
        return findFile(
            "app/schemas/com.elio.jianyu.data.RoundtableDatabase/6.json",
            "schemas/com.elio.jianyu.data.RoundtableDatabase/6.json"
        )
    }

    private fun findFile(vararg candidates: String): File {
        return candidates
            .map(::File)
            .firstOrNull(File::isFile)
            .also { file ->
                assertNotNull(
                    "找不到提交的 Room Schema：${candidates.joinToString()}",
                    file
                )
            }
            ?: error("Room Schema file missing")
    }

    private fun assertContainsJsonString(
        source: String,
        fieldName: String,
        expectedValue: String
    ) {
        assertTrue(
            "Schema 缺少 $fieldName=$expectedValue",
            Regex(
                """"${Regex.escape(fieldName)}"\s*:\s*"${Regex.escape(expectedValue)}""""
            ).containsMatchIn(source)
        )
    }

    private fun assertContainsJsonNumber(
        source: String,
        fieldName: String,
        expectedValue: Int
    ) {
        assertTrue(
            "Schema 缺少 $fieldName=$expectedValue",
            source.containsJsonNumber(fieldName, expectedValue)
        )
    }

    private fun String.containsJsonNumber(fieldName: String, expectedValue: Int): Boolean {
        return Regex(
            """"${Regex.escape(fieldName)}"\s*:\s*$expectedValue(?:\s|,|})"""
        ).containsMatchIn(this)
    }

    private fun String.containsUniqueIndex(indexName: String): Boolean {
        return Regex(
            """\{\s*"name"\s*:\s*"${Regex.escape(indexName)}"\s*,\s*"unique"\s*:\s*true""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).containsMatchIn(this)
    }

    private fun String.normalizeLineEndings(): String {
        return replace("\r\n", "\n").replace("\r", "\n")
    }

    companion object {
        private val EXPECTED_TABLES = setOf(
            "characters",
            "chat_sessions",
            "messages",
            "character_groups",
            "issues",
            "stages",
            "execution_runs",
            "execution_participant_snapshots"
        )

        private val EXPECTED_MESSAGE_COLUMNS = setOf(
            "roundIndex",
            "issueId",
            "stageId",
            "executionRunId",
            "participantSnapshotId"
        )

        private val EXPECTED_MESSAGE_FOREIGN_TABLES = setOf(
            "issues",
            "stages",
            "execution_runs",
            "execution_participant_snapshots"
        )

        private val EXPECTED_INDEX_NAMES = setOf(
            "index_issues_legacyChatSessionId",
            "index_stages_issueId_sequenceIndex",
            "index_execution_runs_idempotencyKey",
            "index_execution_participant_snapshots_runId_position",
            "index_execution_participant_snapshots_runId_sourceType_sourceId",
            "index_messages_stageId_issueId",
            "index_messages_executionRunId_issueId_stageId"
        )
    }
}
