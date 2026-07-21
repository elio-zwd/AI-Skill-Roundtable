package com.example.skillroundtable.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoundtableDatabaseMigrationTest {
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
    fun migration3To4_addsRoundIndexAndPreservesMessages() {
        val legacyHelper = createLegacyDatabase(version = 3, ::createVersion3Schema)
        val database = legacyHelper.writableDatabase
        insertVersion3Data(database)

        RoundtableDatabase.MIGRATION_3_4.migrate(database)

        assertTrue(columnExists(database, "messages", "roundIndex"))
        database.query(
            "SELECT text, roundIndex FROM messages WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy message", cursor.getString(cursor.getColumnIndexOrThrow("text")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("roundIndex")))
        }

        legacyHelper.close()
    }

    @Test
    fun migration4To5_matchesCurrentSchemaAndPreservesData() {
        val legacyHelper = createLegacyDatabase(version = 4, ::createVersion4Schema)
        val database = legacyHelper.writableDatabase
        insertVersion4Data(database)
        legacyHelper.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            5,
            true,
            RoundtableDatabase.MIGRATION_4_5,
        )

        migrated.query(
            "SELECT text, roundIndex, audioFilePath, audioFormat, audioSizeBytes FROM messages WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("round four message", cursor.getString(cursor.getColumnIndexOrThrow("text")))
            assertEquals(7, cursor.getInt(cursor.getColumnIndexOrThrow("roundIndex")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("audioFilePath")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("audioFormat")))
            assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("audioSizeBytes")))
        }

        migrated.query(
            "SELECT name, voiceConfig FROM characters WHERE id = 'legacy_character'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy Character", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals("Aoede", cursor.getString(cursor.getColumnIndexOrThrow("voiceConfig")))
        }

        migrated.close()
    }

    @Test
    fun migration3To5_matchesCurrentSchemaAndPreservesData() {
        val legacyHelper = createLegacyDatabase(version = 3, ::createVersion3Schema)
        val database = legacyHelper.writableDatabase
        insertVersion3Data(database)
        legacyHelper.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            5,
            true,
            RoundtableDatabase.MIGRATION_3_4,
            RoundtableDatabase.MIGRATION_4_5,
        )

        migrated.query(
            "SELECT text, roundIndex, audioFilePath, audioFormat, audioSizeBytes FROM messages WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy message", cursor.getString(cursor.getColumnIndexOrThrow("text")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("roundIndex")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("audioFilePath")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("audioFormat")))
            assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("audioSizeBytes")))
        }

        migrated.query(
            "SELECT name, voiceConfig FROM characters WHERE id = 'legacy_character'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy Character", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals("Aoede", cursor.getString(cursor.getColumnIndexOrThrow("voiceConfig")))
        }

        migrated.close()
    }

    @Test
    fun unsupportedVersionFailsWithoutDeletingDatabase() {
        val legacyHelper = createLegacyDatabase(version = 2) { database ->
            database.execSQL("CREATE TABLE legacy_marker (value TEXT NOT NULL)")
            database.execSQL("INSERT INTO legacy_marker (value) VALUES ('preserve-me')")
        }
        legacyHelper.close()

        val roomDatabase = Room.databaseBuilder(
            context,
            RoundtableDatabase::class.java,
            TEST_DATABASE,
        )
            .addMigrations(
                RoundtableDatabase.MIGRATION_3_4,
                RoundtableDatabase.MIGRATION_4_5,
            )
            .build()

        try {
            roomDatabase.openHelper.writableDatabase
            fail("Opening an unsupported database version must fail without destructive migration.")
        } catch (_: IllegalStateException) {
            // Expected: no migration path from version 2 to version 5.
        } finally {
            roomDatabase.close()
        }

        val databaseFile = context.getDatabasePath(TEST_DATABASE)
        assertTrue(databaseFile.exists())
        val preservedDatabase = SQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY,
        )
        preservedDatabase.rawQuery("SELECT value FROM legacy_marker", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("preserve-me", cursor.getString(0))
        }
        preservedDatabase.close()
    }

    private fun createLegacyDatabase(
        version: Int,
        createSchema: (SupportSQLiteDatabase) -> Unit,
    ): SupportSQLiteOpenHelper {
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(TEST_DATABASE)
            .callback(
                object : SupportSQLiteOpenHelper.Callback(version) {
                    override fun onCreate(database: SupportSQLiteDatabase) {
                        createSchema(database)
                    }

                    override fun onUpgrade(
                        database: SupportSQLiteDatabase,
                        oldVersion: Int,
                        newVersion: Int,
                    ) {
                        error("Unexpected upgrade while creating legacy test database: $oldVersion -> $newVersion")
                    }
                },
            )
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(configuration).also {
            it.writableDatabase
        }
    }

    private fun createVersion3Schema(database: SupportSQLiteDatabase) {
        createCommonTables(database, includeRoundIndex = false)
    }

    private fun createVersion4Schema(database: SupportSQLiteDatabase) {
        createCommonTables(database, includeRoundIndex = true)
    }

    private fun createCommonTables(
        database: SupportSQLiteDatabase,
        includeRoundIndex: Boolean,
    ) {
        database.execSQL(
            """
            CREATE TABLE characters (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                avatar TEXT NOT NULL,
                tagline TEXT NOT NULL,
                systemPrompt TEXT NOT NULL,
                skillAssetPath TEXT NOT NULL,
                `order` INTEGER NOT NULL,
                isActive INTEGER NOT NULL,
                skillDescriptionVector TEXT NOT NULL,
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

        val roundIndexColumn = if (includeRoundIndex) {
            ", roundIndex INTEGER NOT NULL DEFAULT 0"
        } else {
            ""
        }
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
        database.execSQL(
            """
            CREATE TABLE character_groups (
                id TEXT NOT NULL,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                characterIds TEXT NOT NULL,
                isPreset INTEGER NOT NULL,
                PRIMARY KEY(id)
            )
            """.trimIndent(),
        )
    }

    private fun insertVersion3Data(database: SupportSQLiteDatabase) {
        insertLegacyCharacter(database)
        database.execSQL(
            """
            INSERT INTO messages (
                id, chatId, senderId, senderName, avatar, text, timestamp, isPending
            ) VALUES (
                1, 10, 'legacy_character', 'Legacy Character', 'L', 'legacy message', 1234, 0
            )
            """.trimIndent(),
        )
    }

    private fun insertVersion4Data(database: SupportSQLiteDatabase) {
        insertLegacyCharacter(database)
        database.execSQL(
            """
            INSERT INTO messages (
                id, chatId, senderId, senderName, avatar, text, timestamp, isPending, roundIndex
            ) VALUES (
                1, 10, 'legacy_character', 'Legacy Character', 'L', 'round four message', 1234, 0, 7
            )
            """.trimIndent(),
        )
    }

    private fun insertLegacyCharacter(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO characters (
                id, name, avatar, tagline, systemPrompt, skillAssetPath,
                `order`, isActive, skillDescriptionVector
            ) VALUES (
                'legacy_character', 'Legacy Character', 'L', 'legacy tagline',
                'legacy prompt', 'skills/legacy', 1, 1, '0.1,0.2'
            )
            """.trimIndent(),
        )
    }

    private fun columnExists(
        database: SupportSQLiteDatabase,
        tableName: String,
        columnName: String,
    ): Boolean {
        database.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex).equals(columnName, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    companion object {
        private const val TEST_DATABASE = "roundtable-migration-test"
    }
}
