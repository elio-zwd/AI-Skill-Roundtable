package com.elio.jianyu.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceLifecycleSchemaContractTest {
    @Test
    fun committedVersion7SchemaContainsResourceLifecycleContract() {
        val schema = findSchemaFile().readText()

        assertContainsJsonNumber(schema, "version", 7)
        assertContainsJsonString(schema, "identityHash", VERSION_7_IDENTITY_HASH)

        EXPECTED_NEW_TABLES.forEach { tableName ->
            assertContainsJsonString(schema, "tableName", tableName)
        }
        EXPECTED_MESSAGE_COLUMNS.forEach { columnName ->
            assertContainsJsonString(schema, "columnName", columnName)
        }
        EXPECTED_INDEX_NAMES.forEach { indexName ->
            assertContainsJsonString(schema, "name", indexName)
        }
        EXPECTED_FOREIGN_TABLES.forEach { tableName ->
            assertTrue(
                "Schema 缺少指向 $tableName 的外键",
                Regex(
                    """"table"\s*:\s*"${Regex.escape(tableName)}""""
                ).containsMatchIn(schema)
            )
        }

        assertTrue(
            "音频路径必须保持唯一",
            schema.containsUniqueIndex("index_audio_assets_storagePath")
        )
        assertTrue(
            "音频生成键必须保持唯一",
            schema.containsUniqueIndex("index_audio_assets_generationKey")
        )
        assertTrue(
            "同一组合成员顺序必须唯一",
            schema.containsUniqueIndex(
                "index_official_skill_combination_members_combinationId_position"
            )
        )
        assertTrue(
            "同一 Stage 只能有一个当前草稿",
            schema.containsUniqueIndex("index_stage_summary_drafts_issueId_stageId")
        )
        assertTrue(
            "同一 Stage 的草稿修订号必须唯一",
            schema.containsUniqueIndex(
                "index_stage_summary_draft_revisions_issueId_stageId_revisionNumber"
            )
        )
    }

    @Test
    fun schemaKeepsDraftArtifactAudioAndLifecycleBoundariesSeparate() {
        val schema = findSchemaFile().readText()
        val officialMembers = schema.entitySection("official_skill_combination_members")
        val lifecycle = schema.entitySection("issue_lifecycle")
        val draft = schema.entitySection("stage_summary_drafts")
        val audio = schema.entitySection("audio_assets")

        assertFalse(
            "官方组合成员不得保存 System Prompt",
            officialMembers.contains("systemPrompt", ignoreCase = true)
        )
        assertFalse(
            "官方组合成员不得保存自定义 Prompt 正文",
            officialMembers.contains("customPrompt", ignoreCase = true)
        )
        assertFalse(
            "草稿不得带自动过期字段",
            draft.contains("expiresAt", ignoreCase = true)
        )
        assertFalse(
            "生命周期不得带自动过期字段",
            lifecycle.contains("expiresAt", ignoreCase = true)
        )
        assertFalse(
            "生命周期不得带自动清空字段",
            lifecycle.contains("autoPurge", ignoreCase = true)
        )
        assertTrue(
            "生命周期必须表达 active 默认状态",
            lifecycle.contains("DEFAULT 'active'")
        )
        assertTrue(
            "音频资产必须表达文件状态",
            audio.contains("fileState")
        )
        assertTrue(
            "音频资产必须同时支持消息或成果来源",
            audio.contains("sourceMessageId") && audio.contains("sourceArtifactId")
        )
        assertFalse(
            "Room Schema 不得定义自动清空 Trigger",
            schema.contains("CREATE TRIGGER", ignoreCase = true)
        )
    }

    @Test
    fun committedVersion6SchemaRemainsFrozenAndVersion7IsAdditive() {
        val version6 = findFile(
            "app/schemas/com.elio.jianyu.data.RoundtableDatabase/6.json",
            "schemas/com.elio.jianyu.data.RoundtableDatabase/6.json"
        ).readText()
        val version7 = findSchemaFile().readText()

        assertContainsJsonNumber(version6, "version", 6)
        assertContainsJsonString(version6, "identityHash", VERSION_6_IDENTITY_HASH)
        assertContainsJsonNumber(version7, "version", 7)

        EXPECTED_CORE_TABLES.forEach { tableName ->
            assertContainsJsonString(version7, "tableName", tableName)
        }
        EXPECTED_MESSAGE_COLUMNS.forEach { columnName ->
            assertContainsJsonString(version7, "columnName", columnName)
        }
    }

    private fun findSchemaFile(): File {
        return findFile(
            "app/schemas/com.elio.jianyu.data.RoundtableDatabase/7.json",
            "schemas/com.elio.jianyu.data.RoundtableDatabase/7.json"
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
            Regex(
                """"${Regex.escape(fieldName)}"\s*:\s*$expectedValue(?:\s|,|})"""
            ).containsMatchIn(source)
        )
    }

    private fun String.containsUniqueIndex(indexName: String): Boolean {
        return Regex(
            """\{\s*"name"\s*:\s*"${Regex.escape(indexName)}"\s*,\s*"unique"\s*:\s*true""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        ).containsMatchIn(this)
    }

    private fun String.entitySection(tableName: String): String {
        val startToken = "\"tableName\": \"$tableName\""
        val start = indexOf(startToken)
        require(start >= 0) { "Schema 缺少 Entity：$tableName" }
        val next = indexOf("\"tableName\": \"", start + startToken.length)
        return if (next >= 0) substring(start, next) else substring(start)
    }

    companion object {
        private const val VERSION_6_IDENTITY_HASH = "a8e986a7b9a6b68a650291a1b27ae93f"
        private const val VERSION_7_IDENTITY_HASH = "75a521643a63a5fbd7196edd6ed68e1e"

        private val EXPECTED_CORE_TABLES = setOf(
            "characters",
            "chat_sessions",
            "messages",
            "character_groups",
            "issues",
            "stages",
            "execution_runs",
            "execution_participant_snapshots"
        )

        private val EXPECTED_NEW_TABLES = setOf(
            "material_references",
            "material_usage_snapshots",
            "personal_context_entries",
            "personal_context_usage_snapshots",
            "stage_summary_drafts",
            "stage_summary_draft_revisions",
            "confirmed_artifacts",
            "artifact_message_sources",
            "artifact_run_sources",
            "artifact_draft_sources",
            "artifact_material_sources",
            "audio_assets",
            "official_skill_combinations",
            "official_skill_combination_members",
            "issue_lifecycle"
        )

        private val EXPECTED_MESSAGE_COLUMNS = setOf(
            "roundIndex",
            "audioFilePath",
            "audioFormat",
            "audioSizeBytes",
            "issueId",
            "stageId",
            "executionRunId",
            "participantSnapshotId"
        )

        private val EXPECTED_INDEX_NAMES = setOf(
            "index_execution_runs_id_issueId",
            "index_messages_id_issueId",
            "index_messages_id_issueId_stageId",
            "index_material_usage_snapshots_runId_materialReferenceId",
            "index_personal_context_usage_snapshots_runId_personalContextEntryId",
            "index_stage_summary_drafts_issueId_stageId",
            "index_stage_summary_draft_revisions_issueId_stageId_revisionNumber",
            "index_audio_assets_storagePath",
            "index_audio_assets_generationKey",
            "index_official_skill_combination_members_combinationId_position",
            "index_issue_lifecycle_state"
        )

        private val EXPECTED_FOREIGN_TABLES = setOf(
            "issues",
            "stages",
            "execution_runs",
            "messages",
            "confirmed_artifacts",
            "material_usage_snapshots",
            "stage_summary_draft_revisions",
            "official_skill_combinations"
        )
    }
}
