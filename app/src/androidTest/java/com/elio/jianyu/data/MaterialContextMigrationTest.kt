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
class MaterialContextMigrationTest {
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
    fun migration8To9PreservesSourcesAndUsesPrivacyProtectiveSnapshotDefaults() {
        val version8 = migrationHelper.createDatabase(TEST_DATABASE, 8)
        insertVersion8Fixtures(version8)
        version8.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            9,
            true,
            RoundtableDatabase.MIGRATION_8_9,
        )

        assertEquals(
            "deleted",
            scalarString(
                migrated,
                "SELECT lifecycleState FROM material_references WHERE id = 'material-deleted'",
            ),
        )
        assertEquals(
            "purge_requested",
            scalarString(
                migrated,
                "SELECT lifecycleState FROM material_references WHERE id = 'material-purge'",
            ),
        )
        assertEquals(
            "disabled",
            scalarString(
                migrated,
                "SELECT lifecycleState FROM personal_context_entries WHERE id = 'context-disabled'",
            ),
        )
        assertEquals(
            "active",
            scalarString(
                migrated,
                "SELECT lifecycleState FROM personal_context_entries WHERE id = 'context-active'",
            ),
        )
        assertEquals(
            0,
            scalarInt(migrated, "SELECT networkAllowed FROM material_usage_snapshots LIMIT 1"),
        )
        assertEquals(
            1,
            scalarInt(migrated, "SELECT sensitive FROM material_usage_snapshots LIMIT 1"),
        )
        assertEquals(
            0,
            scalarInt(
                migrated,
                "SELECT networkAllowed FROM personal_context_usage_snapshots LIMIT 1",
            ),
        )
        assertEquals(
            1,
            scalarInt(
                migrated,
                "SELECT sensitive FROM personal_context_usage_snapshots LIMIT 1",
            ),
        )
        assertIndex(migrated, "index_material_references_lifecycleState")
        assertIndex(migrated, "index_personal_context_entries_lifecycleState")
        assertEquals(0, migrated.query("PRAGMA foreign_key_check").use { it.count })
        migrated.close()
    }

    private fun insertVersion8Fixtures(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        database.execSQL("INSERT INTO issues VALUES ('issue-1','Issue',100,100,NULL)")
        database.execSQL(
            "INSERT INTO stages VALUES ('stage-1','issue-1',0,'Stage','Objective',100,100)",
        )
        database.execSQL(
            """
            INSERT INTO material_references (
                id,issueId,stageId,title,sourceType,sourceLocator,content,contentHash,
                sourcePublishedAt,sourceCapturedAt,createdAt,updatedAt,deletedAt,purgeRequestedAt
            ) VALUES
                ('material-deleted','issue-1','stage-1','Deleted','note',NULL,'old','hash',NULL,NULL,100,100,200,NULL),
                ('material-purge','issue-1','stage-1','Purge','note',NULL,'old','hash',NULL,NULL,100,100,200,300)
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO personal_context_entries (
                id,title,content,contentHash,isEnabled,createdAt,updatedAt,deletedAt,purgeRequestedAt
            ) VALUES
                ('context-disabled','Disabled','old','hash',0,100,100,NULL,NULL),
                ('context-active','Active','old','hash',1,100,100,NULL,NULL)
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO material_usage_snapshots (
                id,issueId,stageId,runId,materialReferenceId,titleSnapshot,sourceTypeSnapshot,
                sourceLocatorSnapshot,contentSnapshot,contentHash,contentState,
                sourcePublishedAtSnapshot,sourceCapturedAtSnapshot,userConfirmedAt,createdAt
            ) VALUES (
                'material-usage','issue-1','stage-1',NULL,'material-deleted','Deleted','note',
                NULL,'old','hash','available',NULL,NULL,100,100
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO personal_context_usage_snapshots (
                id,issueId,stageId,runId,personalContextEntryId,titleSnapshot,contentSnapshot,
                contentHash,contentState,userConfirmedAt,createdAt
            ) VALUES (
                'context-usage','issue-1','stage-1',NULL,'context-disabled','Disabled','old',
                'hash','available',100,100
            )
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
        private const val TEST_DATABASE = "material-context-migration-test"
    }
}
