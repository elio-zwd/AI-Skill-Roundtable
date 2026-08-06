package com.elio.jianyu.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResourceLifecycleMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RoundtableDatabase::class.java
    )

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
        context.deleteDatabase(V5_DATABASE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
        context.deleteDatabase(V5_DATABASE)
    }

    @Test
    fun allMigrationsRemainContinuousFromVersion1ToVersion12() {
        val migrationPairs = RoundtableDatabase.ALL_MIGRATIONS.map {
            it.startVersion to it.endVersion
        }

        assertEquals(
            listOf(
                1 to 2,
                2 to 3,
                3 to 4,
                4 to 5,
                5 to 6,
                6 to 7,
                7 to 8,
                8 to 9,
                9 to 10,
                10 to 11,
                11 to 12,
            ),
            migrationPairs
        )
    }

    @Test
    fun migration6To7PreservesV6RowsAndDoesNotInventResourceHistory() {
        val version6 = migrationHelper.createDatabase(TEST_DATABASE, 6)
        insertVersion6Fixture(version6)
        version6.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            7,
            true,
            RoundtableDatabase.MIGRATION_6_7
        )

        listOf(
            "characters",
            "character_groups",
            "chat_sessions",
            "messages",
            "issues",
            "stages",
            "execution_runs",
            "execution_participant_snapshots",
            "issue_lifecycle"
        ).forEach { table -> assertEquals(table, 1, count(migrated, table)) }

        listOf(
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
            "official_skill_combination_members"
        ).forEach { table -> assertEquals(table, 0, count(migrated, table)) }

        migrated.query(
            "SELECT roundIndex, audioFilePath, audioFormat, audioSizeBytes, " +
                "issueId, stageId, executionRunId, participantSnapshotId " +
                "FROM messages WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(7, cursor.getInt(cursor.getColumnIndexOrThrow("roundIndex")))
            assertEquals(
                "/legacy/audio.wav",
                cursor.getString(cursor.getColumnIndexOrThrow("audioFilePath"))
            )
            assertEquals("wav", cursor.getString(cursor.getColumnIndexOrThrow("audioFormat")))
            assertEquals(4096L, cursor.getLong(cursor.getColumnIndexOrThrow("audioSizeBytes")))
            assertEquals(ISSUE_ID, cursor.getString(cursor.getColumnIndexOrThrow("issueId")))
            assertEquals(STAGE_ID, cursor.getString(cursor.getColumnIndexOrThrow("stageId")))
            assertEquals(RUN_ID, cursor.getString(cursor.getColumnIndexOrThrow("executionRunId")))
            assertEquals(
                SNAPSHOT_ID,
                cursor.getString(cursor.getColumnIndexOrThrow("participantSnapshotId"))
            )
        }

        migrated.query(
            "SELECT state, previousState, stateChangedAt, updatedAt, purgeRequestedAt " +
                "FROM issue_lifecycle WHERE issueId = ?",
            arrayOf(ISSUE_ID)
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("active", cursor.getString(cursor.getColumnIndexOrThrow("state")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("previousState")))
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("stateChangedAt")))
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
            assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow("purgeRequestedAt")))
        }

        assertIndex(migrated, "index_execution_runs_id_issueId")
        assertIndex(migrated, "index_messages_id_issueId")
        assertIndex(migrated, "index_messages_id_issueId_stageId")
        assertEquals(0, foreignKeyViolations(migrated))

        RoundtableDatabase.MIGRATION_6_7.migrate(migrated)
        assertEquals(1, count(migrated, "issue_lifecycle"))
        assertEquals(0, foreignKeyViolations(migrated))
        migrated.close()
    }

    @Test
    fun migration5To7ComposesExistingMigrationChainAndPreservesLegacyAudio() {
        val version5 = migrationHelper.createDatabase(V5_DATABASE, 5)
        version5.execSQL(
            "INSERT INTO chat_sessions (id, title, createdAt) VALUES (10, 'legacy', 900)"
        )
        version5.execSQL(
            """
            INSERT INTO messages (
                id, chatId, senderId, senderName, avatar, text,
                timestamp, isPending, roundIndex, audioFilePath,
                audioFormat, audioSizeBytes
            ) VALUES (
                1, 10, 'user', 'User', 'U', 'v5 message',
                901, 0, 3, '/legacy/v5.mp3', 'mp3', 512
            )
            """.trimIndent()
        )
        version5.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            V5_DATABASE,
            7,
            true,
            RoundtableDatabase.MIGRATION_5_6,
            RoundtableDatabase.MIGRATION_6_7
        )

        assertEquals(1, count(migrated, "issues"))
        assertEquals(1, count(migrated, "stages"))
        assertEquals(1, count(migrated, "issue_lifecycle"))
        assertEquals(0, count(migrated, "audio_assets"))
        migrated.query(
            "SELECT roundIndex, audioFilePath, audioFormat, audioSizeBytes, issueId, stageId " +
                "FROM messages WHERE id = 1"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow("roundIndex")))
            assertEquals(
                "/legacy/v5.mp3",
                cursor.getString(cursor.getColumnIndexOrThrow("audioFilePath"))
            )
            assertEquals("mp3", cursor.getString(cursor.getColumnIndexOrThrow("audioFormat")))
            assertEquals(512L, cursor.getLong(cursor.getColumnIndexOrThrow("audioSizeBytes")))
            assertEquals(
                "legacy-chat-10",
                cursor.getString(cursor.getColumnIndexOrThrow("issueId"))
            )
            assertEquals(
                "legacy-chat-10-stage-0",
                cursor.getString(cursor.getColumnIndexOrThrow("stageId"))
            )
        }
        assertEquals(0, foreignKeyViolations(migrated))
        migrated.close()
    }

    private fun insertVersion6Fixture(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO characters VALUES " +
                "('character-1','Character','C','tag','prompt','skills/one/SKILL.md'," +
                "0,1,'vector','Aoede')"
        )
        database.execSQL(
            "INSERT INTO character_groups VALUES " +
                "('group-1','Group','description','character-1',0)"
        )
        database.execSQL(
            "INSERT INTO chat_sessions (id,title,createdAt) VALUES (1,'session',800)"
        )
        database.execSQL(
            "INSERT INTO issues VALUES ('$ISSUE_ID','Issue',900,1000,1)"
        )
        database.execSQL(
            "INSERT INTO stages VALUES ('$STAGE_ID','$ISSUE_ID',0,'Stage','Objective',910,1000)"
        )
        database.execSQL(
            """
            INSERT INTO execution_runs VALUES (
                '$RUN_ID','$ISSUE_ID','$STAGE_ID',NULL,'idempotency-1','completed',
                NULL,920,1000,930,990,NULL,NULL,NULL
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO execution_participant_snapshots VALUES (
                '$SNAPSHOT_ID','$RUN_ID','official_skill','official-a','Snapshot','S',
                'skills/official-a/SKILL.md','historical prompt','{}',
                'historical responsibility',0,940
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO messages VALUES (
                1,1,'official-a','Snapshot','S','legacy audio message',950,0,7,
                '/legacy/audio.wav','wav',4096,'$ISSUE_ID','$STAGE_ID','$RUN_ID','$SNAPSHOT_ID'
            )
            """.trimIndent()
        )
    }

    private fun count(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String
    ): Int {
        database.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(0)
        }
    }

    private fun assertIndex(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        index: String
    ) {
        database.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name=?",
            arrayOf(index)
        ).use { cursor -> assertTrue("缺失索引 $index", cursor.moveToFirst()) }
    }

    private fun foreignKeyViolations(
        database: androidx.sqlite.db.SupportSQLiteDatabase
    ): Int = database.query("PRAGMA foreign_key_check").use { it.count }

    companion object {
        private const val TEST_DATABASE = "resource-lifecycle-migration-test"
        private const val V5_DATABASE = "resource-lifecycle-v5-migration-test"
        private const val ISSUE_ID = "issue-1"
        private const val STAGE_ID = "stage-1"
        private const val RUN_ID = "run-1"
        private const val SNAPSHOT_ID = "snapshot-1"
    }
}
