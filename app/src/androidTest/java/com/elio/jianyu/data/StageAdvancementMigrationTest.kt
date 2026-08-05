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
class StageAdvancementMigrationTest {
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
    fun migration10To11PreservesExistingStageAndDoesNotInventAdvancement() {
        val version10 = migrationHelper.createDatabase(TEST_DATABASE, 10)
        version10.execSQL("INSERT INTO issues VALUES ('issue-1','Issue',100,100,NULL)")
        version10.execSQL(
            "INSERT INTO stages VALUES ('stage-1','issue-1',0,'Stage','Objective',100,100)",
        )
        version10.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            11,
            true,
            RoundtableDatabase.MIGRATION_10_11,
        )

        assertEquals(1, scalarInt(migrated, "SELECT COUNT(*) FROM stages"))
        assertEquals(0, scalarInt(migrated, "SELECT COUNT(*) FROM stage_advancements"))
        assertTable(migrated, "stage_advancement_measures")
        assertTable(migrated, "stage_advancement_skill_members")
        assertTable(migrated, "stage_advancement_materials")
        assertTable(migrated, "stage_advancement_artifacts")
        assertIndex(migrated, "index_stage_advancements_operationId")
        assertIndex(migrated, "index_stage_advancement_skill_members_stageId_position")
        assertEquals(0, migrated.query("PRAGMA foreign_key_check").use { it.count })
        migrated.close()
    }

    @Test
    fun migration10To11AllowsOneStageToReferenceItsSourceWithoutCopyingContent() {
        val version10 = migrationHelper.createDatabase(TEST_DATABASE, 10)
        version10.execSQL("INSERT INTO issues VALUES ('issue-1','Issue',100,100,NULL)")
        version10.execSQL(
            "INSERT INTO stages VALUES ('stage-1','issue-1',0,'Stage 1','Objective 1',100,100)",
        )
        version10.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            11,
            true,
            RoundtableDatabase.MIGRATION_10_11,
        )
        migrated.execSQL(
            "INSERT INTO stages VALUES ('stage-2','issue-1',1,'Stage 2','Objective 2',200,200)",
        )
        migrated.execSQL(
            """
            INSERT INTO stage_advancements (
                stageId,issueId,sourceStageId,operationId,payloadHash,
                realitySupport,thinkingExpansion,objective,expectedOutput,confirmedAt,createdAt
            ) VALUES (
                'stage-2','issue-1','stage-1','operation-1','hash-1',
                1,1,'Objective 2','Action plan',200,200
            )
            """.trimIndent(),
        )
        migrated.execSQL(
            "INSERT INTO stage_advancement_measures VALUES " +
                "('stage-2','issue-1','clarify_next_step',0)",
        )
        migrated.execSQL(
            """
            INSERT INTO stage_advancement_skill_members (
                stageId,issueId,officialSkillId,position,responsibility,
                sourceRunId,sourceParticipantSnapshotId,catalogVersionBasis,confirmedAt
            ) VALUES (
                'stage-2','issue-1','study-planner',0,'Plan',NULL,NULL,'catalog-v1',200
            )
            """.trimIndent(),
        )

        assertEquals(
            "stage-1",
            scalarString(migrated, "SELECT sourceStageId FROM stage_advancements"),
        )
        assertEquals(1, scalarInt(migrated, "SELECT COUNT(*) FROM stage_advancement_measures"))
        assertEquals(1, scalarInt(migrated, "SELECT COUNT(*) FROM stage_advancement_skill_members"))
        assertEquals(0, migrated.query("PRAGMA foreign_key_check").use { it.count })
        migrated.close()
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
        private const val TEST_DATABASE = "stage-advancement-migration-test"
    }
}
