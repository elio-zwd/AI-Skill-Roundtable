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
class CoreDomainMigrationIdempotencyTest {
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
    fun migration5To6_reexecutionDoesNotDuplicateBackfillOrChangeMessageSemantics() {
        val legacy = migrationHelper.createDatabase(TEST_DATABASE, 5)
        legacy.execSQL(
            """
            INSERT INTO chat_sessions (id, title, createdAt)
            VALUES (10, 'Legacy Session', 1000)
            """.trimIndent()
        )
        legacy.execSQL(
            """
            INSERT INTO messages (
                id, chatId, senderId, senderName, avatar, text,
                timestamp, isPending, roundIndex, audioFilePath,
                audioFormat, audioSizeBytes
            ) VALUES (
                1, 10, 'user', 'User', 'U', 'legacy message',
                1234, 0, 7, NULL, NULL, 0
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

        RoundtableDatabase.MIGRATION_5_6.migrate(migrated)

        assertEquals(1, queryCount(migrated, "issues"))
        assertEquals(1, queryCount(migrated, "stages"))
        assertEquals(1, queryCount(migrated, "messages"))
        assertEquals(0, queryCount(migrated, "execution_runs"))
        assertEquals(0, queryCount(migrated, "execution_participant_snapshots"))

        migrated.query(
            """
            SELECT text, roundIndex, issueId, stageId,
                executionRunId, participantSnapshotId
            FROM messages
            WHERE id = 1
            """.trimIndent()
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("legacy message", cursor.getString(cursor.getColumnIndexOrThrow("text")))
            assertEquals(7, cursor.getInt(cursor.getColumnIndexOrThrow("roundIndex")))
            assertEquals(
                "legacy-chat-10",
                cursor.getString(cursor.getColumnIndexOrThrow("issueId"))
            )
            assertEquals(
                "legacy-chat-10-stage-0",
                cursor.getString(cursor.getColumnIndexOrThrow("stageId"))
            )
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("executionRunId")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("participantSnapshotId")))
        }

        migrated.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
        migrated.close()
    }

    private fun queryCount(
        database: androidx.sqlite.db.SupportSQLiteDatabase,
        tableName: String
    ): Int {
        database.query("SELECT COUNT(*) AS rowCount FROM `$tableName`").use { cursor ->
            assertTrue(cursor.moveToFirst())
            return cursor.getInt(cursor.getColumnIndexOrThrow("rowCount"))
        }
    }

    companion object {
        private const val TEST_DATABASE = "core-domain-migration-idempotency-test"
    }
}
