package com.elio.jianyu.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExecutionThinkingPolicyMigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RoundtableDatabase::class.java,
    )

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migration12To13PreservesRunsAndBackfillsThinkingSnapshots() {
        val version12 = migrationHelper.createDatabase(TEST_DATABASE, 12)
        insertVersion12Fixture(version12)
        version12.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            13,
            true,
            RoundtableDatabase.MIGRATION_12_13,
        )

        assertEquals(
            "auto",
            scalarString(migrated, "SELECT defaultThinkingPolicy FROM issues WHERE id='$ISSUE_ID'"),
        )
        assertRunSnapshot(
            migrated,
            runId = STANDARD_RUN_ID,
            expectedThinkingLevel = "medium",
        )
        assertRunSnapshot(
            migrated,
            runId = CROSS_DISCUSSION_RUN_ID,
            expectedThinkingLevel = "high",
        )
        assertEquals(
            STANDARD_RUN_ID,
            scalarString(
                migrated,
                "SELECT runId FROM execution_participant_snapshots WHERE id='$PARTICIPANT_ID'",
            ),
        )
        assertEquals(0, migrated.query("PRAGMA foreign_key_check").use { it.count })
        migrated.close()
    }

    private fun insertVersion12Fixture(database: SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO issues (id, title, createdAt, updatedAt, legacyChatSessionId) " +
                "VALUES ('$ISSUE_ID', '旧议题', 100, 100, NULL)",
        )
        database.execSQL(
            "INSERT INTO stages (id, issueId, sequenceIndex, title, objective, createdAt, updatedAt) " +
                "VALUES ('$STAGE_ID', '$ISSUE_ID', 0, '旧阶段', '保留数据', 100, 100)",
        )
        insertRun(
            database = database,
            runId = STANDARD_RUN_ID,
            idempotencyKey = "standard-key",
            runKind = "standard",
        )
        insertRun(
            database = database,
            runId = CROSS_DISCUSSION_RUN_ID,
            idempotencyKey = "cross-key",
            runKind = "cross_discussion_response",
            retryOfRunId = STANDARD_RUN_ID,
        )
        database.execSQL(
            """
            INSERT INTO execution_participant_snapshots (
                id, runId, sourceType, sourceId, displayName, avatar, skillAssetPath,
                systemPrompt, configurationJson, defaultResponsibility, position, createdAt
            ) VALUES (
                '$PARTICIPANT_ID', '$STANDARD_RUN_ID', 'official_skill', 'skill-a', 'Skill A', 'A',
                'skills/a/SKILL.md', 'prompt', '{}', '形成执行步骤', 0, 100
            )
            """.trimIndent(),
        )
    }

    private fun insertRun(
        database: SupportSQLiteDatabase,
        runId: String,
        idempotencyKey: String,
        runKind: String,
        retryOfRunId: String? = null,
    ) {
        database.execSQL(
            """
            INSERT INTO execution_runs (
                id, issueId, stageId, triggerMessageId, idempotencyKey, status, retryOfRunId,
                createdAt, updatedAt, startedAt, finishedAt, stoppedAt, failureCode,
                failureMessage, runKind, parentRunId, discussionId, historyScope
            ) VALUES (
                '$runId', '$ISSUE_ID', '$STAGE_ID', NULL, '$idempotencyKey', 'succeeded',
                ${retryOfRunId?.let { "'$it'" } ?: "NULL"},
                100, 110, 101, 110, NULL, NULL, NULL, '$runKind', NULL, NULL, 'full_stage'
            )
            """.trimIndent(),
        )
    }

    private fun assertRunSnapshot(
        database: SupportSQLiteDatabase,
        runId: String,
        expectedThinkingLevel: String,
    ) {
        database.query(
            """
            SELECT status, actualModelId, actualThinkingLevel, thinkingLevelSource
            FROM execution_runs
            WHERE id = '$runId'
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("succeeded", cursor.getString(cursor.getColumnIndexOrThrow("status")))
            assertEquals(
                "gemini-3.6-flash",
                cursor.getString(cursor.getColumnIndexOrThrow("actualModelId")),
            )
            assertEquals(
                expectedThinkingLevel,
                cursor.getString(cursor.getColumnIndexOrThrow("actualThinkingLevel")),
            )
            assertEquals(
                "auto_routed",
                cursor.getString(cursor.getColumnIndexOrThrow("thinkingLevelSource")),
            )
        }
    }

    private fun scalarString(
        database: SupportSQLiteDatabase,
        sql: String,
    ): String = database.query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getString(0)
    }

    private companion object {
        const val TEST_DATABASE = "execution-thinking-policy-migration-test"
        const val ISSUE_ID = "issue-1"
        const val STAGE_ID = "stage-1"
        const val STANDARD_RUN_ID = "run-standard"
        const val CROSS_DISCUSSION_RUN_ID = "run-cross"
        const val PARTICIPANT_ID = "participant-1"
    }
}
