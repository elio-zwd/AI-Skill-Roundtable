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
class RoundtableDatabaseMigrationTest {
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
    }

    @After
    fun tearDown() {
        context.deleteDatabase(TEST_DATABASE)
    }

    @Test
    fun migration1To6_matchesCurrentSchemaAndPreservesData() {
        val migrated = migrateLegacyDatabase(
            version = 1,
            createSchema = ::createVersion1Schema
        )

        assertMigratedData(
            database = migrated,
            expectedSkillAssetPath = "",
            expectedSkillDescriptionVector = "",
            expectedRoundIndex = 0
        )
        migrated.close()
    }

    @Test
    fun migration2To6_matchesCurrentSchemaAndPreservesData() {
        val migrated = migrateLegacyDatabase(
            version = 2,
            createSchema = ::createVersion2Schema
        )

        assertMigratedData(
            database = migrated,
            expectedSkillAssetPath = LEGACY_SKILL_PATH,
            expectedSkillDescriptionVector = "",
            expectedRoundIndex = 0
        )
        migrated.close()
    }

    @Test
    fun migration3To6_matchesCurrentSchemaAndPreservesData() {
        val migrated = migrateLegacyDatabase(
            version = 3,
            createSchema = ::createVersion3Schema
        )

        assertMigratedData(
            database = migrated,
            expectedSkillAssetPath = LEGACY_SKILL_PATH,
            expectedSkillDescriptionVector = LEGACY_VECTOR,
            expectedRoundIndex = 0
        )
        migrated.close()
    }

    @Test
    fun migration4To6_matchesCurrentSchemaAndPreservesData() {
        val migrated = migrateLegacyDatabase(
            version = 4,
            createSchema = ::createVersion4Schema
        )

        assertMigratedData(
            database = migrated,
            expectedSkillAssetPath = LEGACY_SKILL_PATH,
            expectedSkillDescriptionVector = LEGACY_VECTOR,
            expectedRoundIndex = LEGACY_ROUND_INDEX
        )
        migrated.close()
    }

    @Test
    fun migration5To6_usesCommittedSchemaAndBackfillsDomainRelations() {
        val legacy = migrationHelper.createDatabase(TEST_DATABASE, 5)
        insertVersion5Data(legacy)
        legacy.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            6,
            true,
            RoundtableDatabase.MIGRATION_5_6
        )

        assertMigratedData(
            database = migrated,
            expectedSkillAssetPath = LEGACY_SKILL_PATH,
            expectedSkillDescriptionVector = LEGACY_VECTOR,
            expectedRoundIndex = LEGACY_ROUND_INDEX
        )
        assertEquals(1, queryCount(migrated, "issues"))
        assertEquals(1, queryCount(migrated, "stages"))
        assertEquals(0, queryCount(migrated, "execution_runs"))
        assertEquals(0, queryCount(migrated, "execution_participant_snapshots"))
        assertVersion6Indices(migrated)
        assertNoForeignKeyViolations(migrated)
        migrated.close()
    }

    @Test
    fun migration5To6_preservesOrphanMessageWithoutInventingDomainParents() {
        val legacy = migrationHelper.createDatabase(TEST_DATABASE, 5)
        insertVersion5Data(legacy)
        legacy.execSQL(
            """
            INSERT INTO messages (
                id, chatId, senderId, senderName, avatar, text,
                timestamp, isPending, roundIndex, audioFilePath,
                audioFormat, audioSizeBytes
            ) VALUES (
                2, 999, 'user', 'User', 'U', 'orphan message',
                2000, 0, 9, NULL, NULL, 0
            )
            """.trimIndent()
        )
        legacy.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            6,
            true,
            RoundtableDatabase.MIGRATION_5_6
        )

        migrated.query(
            """
            SELECT text, roundIndex, issueId, stageId, executionRunId,
                participantSnapshotId
            FROM messages
            WHERE id = 2
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("orphan message", cursor.getString(cursor.getColumnIndexOrThrow("text")))
            assertEquals(9, cursor.getInt(cursor.getColumnIndexOrThrow("roundIndex")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("issueId")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("stageId")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("executionRunId")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("participantSnapshotId")))
        }
        assertEquals(1, queryCount(migrated, "issues"))
        assertEquals(1, queryCount(migrated, "stages"))
        assertNoForeignKeyViolations(migrated)
        migrated.close()
    }

    @Test
    fun migration4To6_preservesAnExistingCustomGroup() {
        val legacyHelper = createLegacyDatabase(version = 4) { database ->
            createVersion4Schema(database)
            createCharacterGroupsTable(database)
        }
        val database = legacyHelper.writableDatabase
        insertLegacyData(database, version = 4)
        database.execSQL(
            """
            INSERT INTO character_groups (
                id, name, description, characterIds, isPreset
            ) VALUES (
                'custom_group', 'Custom Group', 'user data', 'legacy_character', 0
            )
            """.trimIndent()
        )
        legacyHelper.close()

        val migrated = migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            6,
            true,
            *RoundtableDatabase.ALL_MIGRATIONS
        )

        migrated.query(
            "SELECT name, description, characterIds, isPreset " +
                "FROM character_groups WHERE id = 'custom_group'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Custom Group", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals("user data", cursor.getString(cursor.getColumnIndexOrThrow("description")))
            assertEquals(
                "legacy_character",
                cursor.getString(cursor.getColumnIndexOrThrow("characterIds"))
            )
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("isPreset")))
        }
        assertPresetGroups(migrated, expectedGroupCount = 5)
        assertDomainBackfill(migrated, expectedRoundIndex = LEGACY_ROUND_INDEX)
        migrated.close()
    }

    private fun migrateLegacyDatabase(
        version: Int,
        createSchema: (SupportSQLiteDatabase) -> Unit
    ): SupportSQLiteDatabase {
        val legacyHelper = createLegacyDatabase(version, createSchema)
        insertLegacyData(legacyHelper.writableDatabase, version)
        legacyHelper.close()

        return migrationHelper.runMigrationsAndValidate(
            TEST_DATABASE,
            6,
            true,
            *RoundtableDatabase.ALL_MIGRATIONS
        )
    }

    private fun assertMigratedData(
        database: SupportSQLiteDatabase,
        expectedSkillAssetPath: String,
        expectedSkillDescriptionVector: String,
        expectedRoundIndex: Int
    ) {
        database.query(
            """
            SELECT name, systemPrompt, skillAssetPath, skillDescriptionVector, voiceConfig
            FROM characters
            WHERE id = 'legacy_character'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy Character", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals("legacy prompt", cursor.getString(cursor.getColumnIndexOrThrow("systemPrompt")))
            assertEquals(
                expectedSkillAssetPath,
                cursor.getString(cursor.getColumnIndexOrThrow("skillAssetPath"))
            )
            assertEquals(
                expectedSkillDescriptionVector,
                cursor.getString(cursor.getColumnIndexOrThrow("skillDescriptionVector"))
            )
            assertEquals("Aoede", cursor.getString(cursor.getColumnIndexOrThrow("voiceConfig")))
        }

        database.query(
            "SELECT title, createdAt FROM chat_sessions WHERE id = 10"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy Session", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
        }

        assertDomainBackfill(database, expectedRoundIndex)
        assertPresetGroups(database, expectedGroupCount = 4)
        assertNoForeignKeyViolations(database)
    }

    private fun assertDomainBackfill(
        database: SupportSQLiteDatabase,
        expectedRoundIndex: Int
    ) {
        database.query(
            """
            SELECT title, createdAt, updatedAt, legacyChatSessionId
            FROM issues
            WHERE id = 'legacy-chat-10'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy Session", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
            assertEquals(10L, cursor.getLong(cursor.getColumnIndexOrThrow("legacyChatSessionId")))
        }

        database.query(
            """
            SELECT issueId, sequenceIndex, title, objective, createdAt, updatedAt
            FROM stages
            WHERE id = 'legacy-chat-10-stage-0'
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy-chat-10", cursor.getString(cursor.getColumnIndexOrThrow("issueId")))
            assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("sequenceIndex")))
            assertEquals("初始阶段", cursor.getString(cursor.getColumnIndexOrThrow("title")))
            assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("objective")))
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("createdAt")))
            assertEquals(1000L, cursor.getLong(cursor.getColumnIndexOrThrow("updatedAt")))
        }

        database.query(
            """
            SELECT text, roundIndex, audioFilePath, audioFormat, audioSizeBytes,
                issueId, stageId, executionRunId, participantSnapshotId
            FROM messages
            WHERE id = 1
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy message", cursor.getString(cursor.getColumnIndexOrThrow("text")))
            assertEquals(expectedRoundIndex, cursor.getInt(cursor.getColumnIndexOrThrow("roundIndex")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("audioFilePath")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("audioFormat")))
            assertEquals(0L, cursor.getLong(cursor.getColumnIndexOrThrow("audioSizeBytes")))
            assertEquals("legacy-chat-10", cursor.getString(cursor.getColumnIndexOrThrow("issueId")))
            assertEquals(
                "legacy-chat-10-stage-0",
                cursor.getString(cursor.getColumnIndexOrThrow("stageId"))
            )
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("executionRunId")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("participantSnapshotId")))
        }
    }

    private fun assertPresetGroups(
        database: SupportSQLiteDatabase,
        expectedGroupCount: Int
    ) {
        database.query("SELECT COUNT(*) AS groupCount FROM character_groups").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                expectedGroupCount,
                cursor.getInt(cursor.getColumnIndexOrThrow("groupCount"))
            )
        }

        database.query(
            "SELECT name, isPreset FROM character_groups " +
                "WHERE id = 'silicon_valley_venture'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("硅谷创投", cursor.getString(cursor.getColumnIndexOrThrow("name")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("isPreset")))
        }
    }

    private fun assertVersion6Indices(database: SupportSQLiteDatabase) {
        val requiredIndices = setOf(
            "index_issues_legacyChatSessionId",
            "index_stages_issueId",
            "index_stages_issueId_sequenceIndex",
            "index_execution_runs_idempotencyKey",
            "index_execution_participant_snapshots_runId_position",
            "index_messages_stageId_issueId",
            "index_messages_executionRunId_issueId_stageId"
        )
        database.query(
            "SELECT name FROM sqlite_master WHERE type = 'index'"
        ).use { cursor ->
            val actual = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                actual += cursor.getString(cursor.getColumnIndexOrThrow("name"))
            }
            assertTrue("Missing expected indices: ${requiredIndices - actual}", actual.containsAll(requiredIndices))
        }
    }

    private fun assertNoForeignKeyViolations(database: SupportSQLiteDatabase) {
        database.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
    }

    private fun queryCount(database: SupportSQLiteDatabase, tableName: String): Int {
        database.query("SELECT COUNT(*) AS rowCount FROM `$tableName`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(cursor.getColumnIndexOrThrow("rowCount"))
        }
    }

    private fun createLegacyDatabase(
        version: Int,
        createSchema: (SupportSQLiteDatabase) -> Unit
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
                        newVersion: Int
                    ) {
                        error(
                            "Unexpected upgrade while creating legacy test database: " +
                                "$oldVersion -> $newVersion"
                        )
                    }
                }
            )
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(configuration).also {
            it.writableDatabase
        }
    }

    private fun createVersion1Schema(database: SupportSQLiteDatabase) {
        createCommonTables(
            database = database,
            includeSkillAssetPath = false,
            includeSkillDescriptionVector = false,
            includeRoundIndex = false
        )
    }

    private fun createVersion2Schema(database: SupportSQLiteDatabase) {
        createCommonTables(
            database = database,
            includeSkillAssetPath = true,
            includeSkillDescriptionVector = false,
            includeRoundIndex = false
        )
    }

    private fun createVersion3Schema(database: SupportSQLiteDatabase) {
        createCommonTables(
            database = database,
            includeSkillAssetPath = true,
            includeSkillDescriptionVector = true,
            includeRoundIndex = false
        )
    }

    private fun createVersion4Schema(database: SupportSQLiteDatabase) {
        createCommonTables(
            database = database,
            includeSkillAssetPath = true,
            includeSkillDescriptionVector = true,
            includeRoundIndex = true
        )
    }

    private fun createCommonTables(
        database: SupportSQLiteDatabase,
        includeSkillAssetPath: Boolean,
        includeSkillDescriptionVector: Boolean,
        includeRoundIndex: Boolean
    ) {
        val skillAssetPathColumn = if (includeSkillAssetPath) {
            ", skillAssetPath TEXT NOT NULL"
        } else {
            ""
        }
        val skillDescriptionVectorColumn = if (includeSkillDescriptionVector) {
            ", skillDescriptionVector TEXT NOT NULL"
        } else {
            ""
        }
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
            """.trimIndent()
        )
        database.execSQL(
            """
            CREATE TABLE chat_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
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
            """.trimIndent()
        )
    }

    private fun createCharacterGroupsTable(database: SupportSQLiteDatabase) {
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
            """.trimIndent()
        )
    }

    private fun insertLegacyData(database: SupportSQLiteDatabase, version: Int) {
        val characterColumns = mutableListOf(
            "id",
            "name",
            "avatar",
            "tagline",
            "systemPrompt"
        )
        val characterValues = mutableListOf(
            "'legacy_character'",
            "'Legacy Character'",
            "'L'",
            "'legacy tagline'",
            "'legacy prompt'"
        )
        if (version >= 2) {
            characterColumns += "skillAssetPath"
            characterValues += "'$LEGACY_SKILL_PATH'"
        }
        characterColumns += listOf("`order`", "isActive")
        characterValues += listOf("1", "1")
        if (version >= 3) {
            characterColumns += "skillDescriptionVector"
            characterValues += "'$LEGACY_VECTOR'"
        }
        database.execSQL(
            "INSERT INTO characters (${characterColumns.joinToString(", ")}) " +
                "VALUES (${characterValues.joinToString(", ")})"
        )

        database.execSQL(
            """
            INSERT INTO chat_sessions (id, title, createdAt)
            VALUES (10, 'Legacy Session', 1000)
            """.trimIndent()
        )

        val messageColumns = mutableListOf(
            "id",
            "chatId",
            "senderId",
            "senderName",
            "avatar",
            "text",
            "timestamp",
            "isPending"
        )
        val messageValues = mutableListOf(
            "1",
            "10",
            "'legacy_character'",
            "'Legacy Character'",
            "'L'",
            "'legacy message'",
            "1234",
            "0"
        )
        if (version >= 4) {
            messageColumns += "roundIndex"
            messageValues += LEGACY_ROUND_INDEX.toString()
        }
        database.execSQL(
            "INSERT INTO messages (${messageColumns.joinToString(", ")}) " +
                "VALUES (${messageValues.joinToString(", ")})"
        )
    }

    private fun insertVersion5Data(database: SupportSQLiteDatabase) {
        database.execSQL(
            """
            INSERT INTO characters (
                id, name, avatar, tagline, systemPrompt, skillAssetPath,
                `order`, isActive, skillDescriptionVector, voiceConfig
            ) VALUES (
                'legacy_character', 'Legacy Character', 'L', 'legacy tagline',
                'legacy prompt', '$LEGACY_SKILL_PATH', 1, 1, '$LEGACY_VECTOR', 'Aoede'
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO chat_sessions (id, title, createdAt)
            VALUES (10, 'Legacy Session', 1000)
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO messages (
                id, chatId, senderId, senderName, avatar, text,
                timestamp, isPending, roundIndex, audioFilePath,
                audioFormat, audioSizeBytes
            ) VALUES (
                1, 10, 'legacy_character', 'Legacy Character', 'L',
                'legacy message', 1234, 0, $LEGACY_ROUND_INDEX, NULL, NULL, 0
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO character_groups (
                id, name, description, characterIds, isPreset
            ) VALUES (
                'silicon_valley_venture', '硅谷创投', 'preset', 'legacy_character', 1
            )
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO character_groups (
                id, name, description, characterIds, isPreset
            ) VALUES ('group_2', 'Group 2', '', '', 1)
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO character_groups (
                id, name, description, characterIds, isPreset
            ) VALUES ('group_3', 'Group 3', '', '', 1)
            """.trimIndent()
        )
        database.execSQL(
            """
            INSERT INTO character_groups (
                id, name, description, characterIds, isPreset
            ) VALUES ('group_4', 'Group 4', '', '', 1)
            """.trimIndent()
        )
    }

    companion object {
        private const val TEST_DATABASE = "roundtable-migration-test"
        private const val LEGACY_SKILL_PATH = "skills/legacy/SKILL.md"
        private const val LEGACY_VECTOR = "0.1,0.2"
        private const val LEGACY_ROUND_INDEX = 7
    }
}
