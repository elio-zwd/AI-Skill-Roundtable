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
class ExecutionRuntimeMigrationTest {
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
    fun allMigrationsRemainContinuousFromVersion1ToVersion8() {
        assertEquals(
            listOf(1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 8),
            RoundtableDatabase.ALL_MIGRATIONS.map { it.startVersion to it.endVersion },
        )
    }

    @Test
    fun migration7To8SafelyBackfillsLegacyRuntimeWithoutInventingBudgetUse() {
        val version7 = migrationHelper.createDatabase(TEST_DATABASE, 7)
        insertVersion7Fixture(version7)
        version7.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            8,
            true,
            RoundtableDatabase.MIGRATION_7_8,
        )

        assertEquals(1, count(migrated, "execution_runs"))
        assertEquals(1, count(migrated, "execution_participant_snapshots"))
        assertEquals(1, count(migrated, "execution_participant_states"))
        assertEquals(1, count(migrated, "execution_run_budgets"))
        assertIndex(
            migrated,
            "index_execution_participant_states_participantSnapshotId_runId",
        )
        assertIndex(migrated, "index_execution_participant_states_runId")
        assertIndex(migrated, "index_execution_participant_states_runId_status")
        assertIndex(migrated, "index_execution_participant_states_outputMessageId")
        assertEquals("retryable", scalarString(migrated, "SELECT status FROM execution_runs"))
        assertEquals(
            "retryable",
            scalarString(migrated, "SELECT status FROM execution_participant_states"),
        )
        assertEquals(
            "process_interrupted",
            scalarString(migrated, "SELECT lastErrorCode FROM execution_participant_states"),
        )
        assertEquals(0, scalarInt(migrated, "SELECT usedApiCalls FROM execution_run_budgets"))
        assertEquals(1, scalarInt(migrated, "SELECT closed FROM execution_run_budgets"))
        assertEquals(0, foreignKeyViolations(migrated))
        migrated.close()
    }

    private fun insertVersion7Fixture(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL(
            "INSERT INTO issues VALUES ('$ISSUE_ID','Issue',100,100,NULL)",
        )
        database.execSQL(
            "INSERT INTO stages VALUES ('$STAGE_ID','$ISSUE_ID',0,'Stage','Objective',100,100)",
        )
        database.execSQL(
            """
            INSERT INTO execution_runs VALUES (
                '$RUN_ID','$ISSUE_ID','$STAGE_ID',NULL,'command-1','not_started',
                NULL,100,100,NULL,NULL,NULL,NULL,NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO execution_participant_snapshots VALUES (
                '$PARTICIPANT_ID','$RUN_ID','official_skill','skill-a','Skill A','A',
                'skills/a/SKILL.md','prompt','{}','',0,100
            )
            """.trimIndent(),
        )
    }

    private fun count(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        table: String,
    ): Int = scalarInt(database, "SELECT COUNT(*) FROM `$table`")

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

    private fun assertIndex(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        index: String,
    ) {
        database.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name=?",
            arrayOf(index),
        ).use { cursor -> assertTrue("缺失索引 $index", cursor.moveToFirst()) }
    }

    private fun foreignKeyViolations(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
    ): Int = database.query("PRAGMA foreign_key_check").use { it.count }

    companion object {
        private const val TEST_DATABASE = "execution-runtime-migration-test"
        private const val ISSUE_ID = "issue-1"
        private const val STAGE_ID = "stage-1"
        private const val RUN_ID = "run-1"
        private const val PARTICIPANT_ID = "participant-1"
    }
}
