package com.elio.jianyu.data

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
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
class IssueLifecycleV12MigrationTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        RoundtableDatabase::class.java,
    )

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        databaseNames().forEach(context::deleteDatabase)
    }

    @After
    fun tearDown() {
        databaseNames().forEach(context::deleteDatabase)
    }

    @Test
    fun legacyVersionsOneToFourMigrateContinuouslyTo13() {
        for (startVersion in 1..4) {
            val name = databaseName(startVersion)
            createLegacyDatabase(name, startVersion).close()

            val migrated = migrationHelper.runMigrationsAndValidate(
                name,
                13,
                true,
                *RoundtableDatabase.ALL_MIGRATIONS,
            )
            assertEquals(
                "v$startVersion→v13 foreign_key_check",
                0,
                migrated.query("PRAGMA foreign_key_check").use { it.count },
            )
            migrated.close()
        }
    }

    @Test
    fun committedSchemaVersionsFiveToTwelveMigrateContinuouslyTo13() {
        for (startVersion in 5..12) {
            val name = databaseName(startVersion)
            migrationHelper.createDatabase(name, startVersion).close()
            val migrated = migrationHelper.runMigrationsAndValidate(
                name,
                13,
                true,
                *RoundtableDatabase.ALL_MIGRATIONS,
            )
            assertEquals(
                "v$startVersion→v13 foreign_key_check",
                0,
                migrated.query("PRAGMA foreign_key_check").use { it.count },
            )
            migrated.close()
        }
    }

    @Test
    fun migration11To12PreservesArchivedAndTrashedStatesWithoutInventingEvents() {
        val version11 = migrationHelper.createDatabase(DIRECT_DATABASE, 11)
        version11.execSQL("INSERT INTO issues VALUES ('archive-1','Archive',100,100,NULL)")
        version11.execSQL("INSERT INTO issues VALUES ('trash-1','Trash',100,100,NULL)")
        version11.execSQL(
            "INSERT INTO issue_lifecycle VALUES " +
                "('archive-1','archived',NULL,110,110,110,NULL,NULL)",
        )
        version11.execSQL(
            "INSERT INTO issue_lifecycle VALUES " +
                "('trash-1','trashed','active',120,120,NULL,120,NULL)",
        )
        version11.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            DIRECT_DATABASE,
            12,
            true,
            RoundtableDatabase.MIGRATION_11_12,
        )

        assertEquals(
            "archived",
            scalarString(migrated, "SELECT state FROM issue_lifecycle WHERE issueId='archive-1'"),
        )
        assertEquals(
            "trashed",
            scalarString(migrated, "SELECT state FROM issue_lifecycle WHERE issueId='trash-1'"),
        )
        assertEquals(0, scalarInt(migrated, "SELECT COUNT(*) FROM issue_archive_events"))
        assertEquals(0, scalarInt(migrated, "SELECT COUNT(*) FROM issue_resume_events"))
        assertEquals(0, scalarInt(migrated, "SELECT COUNT(*) FROM issue_relations"))
        assertEquals(0, scalarInt(migrated, "SELECT COUNT(*) FROM issue_purge_operations"))
        assertEquals(0, migrated.query("PRAGMA foreign_key_check").use { it.count })
        migrated.close()
    }

    @Test
    fun migration11To12ConvertsLegacyPurgeRequestIntoExplainableRetryableOperation() {
        val version11 = migrationHelper.createDatabase(DIRECT_DATABASE, 11)
        version11.execSQL("INSERT INTO issues VALUES ('trash-1','Trash',100,100,NULL)")
        version11.execSQL(
            "INSERT INTO issue_lifecycle VALUES " +
                "('trash-1','trashed','active',120,130,NULL,120,125)",
        )
        version11.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            DIRECT_DATABASE,
            12,
            true,
            RoundtableDatabase.MIGRATION_11_12,
        )

        assertEquals(1, scalarInt(migrated, "SELECT COUNT(*) FROM issue_purge_operations"))
        assertEquals(
            "failed_retryable",
            scalarString(migrated, "SELECT state FROM issue_purge_operations"),
        )
        assertEquals(
            "legacy_purge_request_requires_review",
            scalarString(migrated, "SELECT failureCode FROM issue_purge_operations"),
        )
        assertEquals(
            "impact",
            scalarString(migrated, "SELECT failurePhase FROM issue_purge_operations"),
        )
        assertNull(scalarNullableString(migrated, "SELECT impactHash FROM issue_purge_operations"))
        assertEquals(0, migrated.query("PRAGMA foreign_key_check").use { it.count })
        migrated.close()
    }

    @Test
    fun migration11To12CreatesAllLifecycleTablesAndRequiredUniqueIndexes() {
        migrationHelper.createDatabase(DIRECT_DATABASE, 11).close()
        val migrated = migrationHelper.runMigrationsAndValidate(
            DIRECT_DATABASE,
            12,
            true,
            RoundtableDatabase.MIGRATION_11_12,
        )

        listOf(
            "issue_archive_events",
            "issue_resume_events",
            "issue_relations",
            "issue_purge_operations",
        ).forEach { assertTable(migrated, it) }
        listOf(
            "index_issue_archive_events_archiveOperationId",
            "index_issue_resume_events_resumeOperationId",
            "index_issue_relations_operationId",
            "index_issue_purge_operations_issueId",
            "index_issue_purge_operations_operationId",
        ).forEach { assertIndex(migrated, it) }
        assertEquals(0, migrated.query("PRAGMA foreign_key_check").use { it.count })
        migrated.close()
    }

    private fun createLegacyDatabase(
        name: String,
        version: Int,
    ): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(database: SupportSQLiteDatabase) {
                        createLegacySchema(database, version)
                    }

                    override fun onUpgrade(
                        database: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {
                        error("Unexpected legacy fixture upgrade: $oldVersion -> $newVersion")
                    }
                },
            )
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(configuration).also {
            it.writableDatabase
        }
    }

    private fun createLegacySchema(
        database: SupportSQLiteDatabase,
        version: Int,
    ) {
        val skillAssetPathColumn = if (version >= 2) ", skillAssetPath TEXT NOT NULL" else ""
        val skillDescriptionVectorColumn =
            if (version >= 3) ", skillDescriptionVector TEXT NOT NULL" else ""
        val roundIndexColumn = if (version >= 4) ", roundIndex INTEGER NOT NULL DEFAULT 0" else ""

        database.execSQL(
            """
            CREATE TABLE characters (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                avatar TEXT NOT NULL,
                tagline TEXT NOT NULL,
                systemPrompt TEXT NOT NULL$skillAssetPathColumn,
                `order` INTEGER NOT NULL,
                isActive INTEGER NOT NULL$skillDescriptionVectorColumn,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE chat_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                chatId INTEGER NOT NULL,
                senderId TEXT NOT NULL,
                senderName TEXT NOT NULL,
                avatar TEXT NOT NULL,
                text TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                isPending INTEGER NOT NULL$roundIndexColumn
            )
            """.trimIndent(),
        )
    }

    private fun scalarInt(
        database: SupportSQLiteDatabase,
        sql: String,
    ): Int = database.query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getInt(0)
    }

    private fun scalarString(
        database: SupportSQLiteDatabase,
        sql: String,
    ): String = database.query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getString(0)
    }

    private fun scalarNullableString(
        database: SupportSQLiteDatabase,
        sql: String,
    ): String? = database.query(sql).use { cursor ->
        assertTrue(cursor.moveToFirst())
        if (cursor.isNull(0)) null else cursor.getString(0)
    }

    private fun assertTable(
        database: SupportSQLiteDatabase,
        table: String,
    ) {
        database.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table),
        ).use { cursor -> assertTrue("缺失表 $table", cursor.moveToFirst()) }
    }

    private fun assertIndex(
        database: SupportSQLiteDatabase,
        index: String,
    ) {
        database.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name=?",
            arrayOf(index),
        ).use { cursor -> assertTrue("缺失索引 $index", cursor.moveToFirst()) }
    }

    private fun databaseNames(): List<String> = (1..12).map(::databaseName) + DIRECT_DATABASE

    private fun databaseName(version: Int): String = "issue-lifecycle-v$version-to-v12"

    private companion object {
        const val DIRECT_DATABASE = "issue-lifecycle-v11-to-v12"
    }
}
