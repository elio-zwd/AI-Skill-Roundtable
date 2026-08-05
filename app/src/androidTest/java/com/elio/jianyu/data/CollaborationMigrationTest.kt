package com.elio.jianyu.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
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
class CollaborationMigrationTest {
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
    fun migration9To10PreservesRuntimeAndDefaultsOldRunsToStandardFullStage() {
        val version9 = migrationHelper.createDatabase(TEST_DATABASE, 9)
        insertVersion9Runtime(version9)
        version9.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            10,
            true,
            RoundtableDatabase.MIGRATION_9_10,
        )

        assertEquals("standard", scalarString(migrated, "SELECT runKind FROM execution_runs"))
        assertEquals("full_stage", scalarString(migrated, "SELECT historyScope FROM execution_runs"))
        assertTrue(scalarNullableString(migrated, "SELECT parentRunId FROM execution_runs") == null)
        assertTrue(scalarNullableString(migrated, "SELECT discussionId FROM execution_runs") == null)
        assertEquals(1, scalarInt(migrated, "SELECT COUNT(*) FROM execution_participant_snapshots"))
        assertEquals(1, scalarInt(migrated, "SELECT COUNT(*) FROM execution_participant_states"))
        assertEquals(1, scalarInt(migrated, "SELECT COUNT(*) FROM execution_run_budgets"))
        assertTable(migrated, "cross_discussion_sessions")
        assertTable(migrated, "execution_message_usage_snapshots")
        assertIndex(migrated, "index_execution_runs_stageId_runKind")
        assertIndex(migrated, "index_execution_message_usage_snapshots_runId_usageOrder")
        assertEquals(0, migrated.query("PRAGMA foreign_key_check").use { it.count })
        migrated.close()
    }

    private fun insertVersion9Runtime(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL("INSERT INTO issues VALUES ('issue-1','Issue',100,100,NULL)")
        database.execSQL(
            "INSERT INTO stages VALUES ('stage-1','issue-1',0,'Stage','Objective',100,100)",
        )
        database.execSQL(
            """
            INSERT INTO execution_runs (
                id,issueId,stageId,triggerMessageId,idempotencyKey,status,retryOfRunId,
                createdAt,updatedAt,startedAt,finishedAt,stoppedAt,failureCode,failureMessage
            ) VALUES (
                'run-1','issue-1','stage-1',NULL,'run-key','succeeded',NULL,
                100,200,110,200,NULL,NULL,NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO execution_participant_snapshots (
                id,runId,sourceType,sourceId,displayName,avatar,skillAssetPath,
                systemPrompt,configurationJson,defaultResponsibility,position,createdAt
            ) VALUES (
                'participant-1','run-1','official_skill','study-planner','学习规划助手','学',
                'skills/study-planner/SKILL.md','冻结提示词','{}','规划',0,100
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO execution_participant_states (
                participantSnapshotId,runId,status,attemptCount,outputMessageId,startedAt,
                finishedAt,lastErrorCode,lastErrorMessage,hasIncompleteOutput,updatedAt
            ) VALUES (
                'participant-1','run-1','succeeded',1,NULL,110,200,NULL,NULL,0,200
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO execution_run_budgets (
                rootRunId,maxApiCalls,usedApiCalls,reservedRequiredCalls,maxCharacters,
                maxSearchQueriesPerCharacter,maxOutputTokensPerAnswer,closed,updatedAt
            ) VALUES ('run-1',3,1,0,6,3,4096,1,200)
            """.trimIndent(),
        )
    }

    private fun scalarInt(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String,
    ): Int = database.query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private fun scalarString(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String,
    ): String = database.query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getString(0)
    }

    private fun scalarNullableString(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        sql: String,
    ): String? = database.query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        if (cursor.isNull(0)) null else cursor.getString(0)
    }

    private fun assertTable(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
    ) {
        database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table),
        ).use { cursor -> assertTrue("缺失表 $table", cursor.moveToFirst()) }
    }

    private fun assertIndex(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        index: String,
    ) {
        database.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name=?",
            arrayOf(index),
        ).use { cursor -> assertTrue("缺失索引 $index", cursor.moveToFirst()) }
    }

    companion object {
        private const val TEST_DATABASE = "collaboration-migration-test"
    }
}
