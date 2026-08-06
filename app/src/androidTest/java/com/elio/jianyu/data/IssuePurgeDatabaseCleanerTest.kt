package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IssuePurgeDatabaseCleanerTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: RoundtableDatabase
    private lateinit var cleaner: IssuePurgeDatabaseCleaner

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RoundtableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        cleaner = IssuePurgeDatabaseCleaner(database)
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun purgeKeepsRelatedTargetAndGlobalPersonalContextWhileDowngradingSource() = runBlocking {
        insertIssue(SOURCE_ISSUE_ID, SOURCE_STAGE_ID, trashed = true)
        insertIssue(TARGET_ISSUE_ID, TARGET_STAGE_ID, trashed = false)
        insertArchiveEvent()
        insertRelation()
        insertGlobalPersonalContext()
        insertDatabasePurgingOperation()

        val result = cleaner.purge(OPERATION_ROW_ID, PURGED_AT)

        assertTrue(result is RepositoryResult.Success)
        assertEquals(0, count("SELECT COUNT(*) FROM issues WHERE id = ?", SOURCE_ISSUE_ID))
        assertEquals(1, count("SELECT COUNT(*) FROM issues WHERE id = ?", TARGET_ISSUE_ID))
        assertEquals(1, count("SELECT COUNT(*) FROM personal_context_entries WHERE id = ?", CONTEXT_ID))
        assertEquals(0, count("SELECT COUNT(*) FROM issue_purge_operations WHERE id = ?", OPERATION_ROW_ID))
        database.openHelper.readableDatabase.query(
            "SELECT sourceIssueId, sourceArchiveEventId, sourcePurgedAt, targetIssueId " +
                "FROM issue_relations WHERE id = ?",
            arrayOf(RELATION_ID),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertNull(cursor.getString(0))
            assertNull(cursor.getString(1))
            assertEquals(PURGED_AT, cursor.getLong(2))
            assertEquals(TARGET_ISSUE_ID, cursor.getString(3))
        }
        assertEquals(0, database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { it.count })
    }

    @Test
    fun foreignKeyFailureRollsBackIssueLifecycleOperationAndAllEarlierDeletes() = runBlocking {
        insertIssue(SOURCE_ISSUE_ID, SOURCE_STAGE_ID, trashed = true)
        insertIssue(TARGET_ISSUE_ID, TARGET_STAGE_ID, trashed = false)
        insertArchiveEvent()
        insertRelation()
        insertDatabasePurgingOperation()
        insertRun(
            id = SOURCE_RUN_ID,
            issueId = SOURCE_ISSUE_ID,
            stageId = SOURCE_STAGE_ID,
            retryOfRunId = null,
        )
        insertRun(
            id = TARGET_RUN_ID,
            issueId = TARGET_ISSUE_ID,
            stageId = TARGET_STAGE_ID,
            retryOfRunId = SOURCE_RUN_ID,
        )

        val result = cleaner.purge(OPERATION_ROW_ID, PURGED_AT)

        assertTrue(result is RepositoryResult.Failure)
        assertEquals(1, count("SELECT COUNT(*) FROM issues WHERE id = ?", SOURCE_ISSUE_ID))
        assertEquals(1, count("SELECT COUNT(*) FROM stages WHERE id = ?", SOURCE_STAGE_ID))
        assertEquals(1, count("SELECT COUNT(*) FROM issue_lifecycle WHERE issueId = ?", SOURCE_ISSUE_ID))
        assertEquals(1, count("SELECT COUNT(*) FROM issue_purge_operations WHERE id = ?", OPERATION_ROW_ID))
        assertEquals(1, count("SELECT COUNT(*) FROM issue_archive_events WHERE id = ?", ARCHIVE_EVENT_ID))
        assertEquals(1, count("SELECT COUNT(*) FROM issue_relations WHERE id = ?", RELATION_ID))
        assertEquals(1, count("SELECT COUNT(*) FROM execution_runs WHERE id = ?", SOURCE_RUN_ID))
        assertEquals(1, count("SELECT COUNT(*) FROM execution_runs WHERE id = ?", TARGET_RUN_ID))
        assertEquals(0, database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { it.count })
    }

    private fun insertIssue(
        issueId: String,
        stageId: String,
        trashed: Boolean,
    ) {
        val db = database.openHelper.writableDatabase
        db.execSQL(
            "INSERT INTO issues (id, title, createdAt, updatedAt, legacyChatSessionId) " +
                "VALUES (?, ?, 100, 100, NULL)",
            arrayOf(issueId, "测试议题"),
        )
        db.execSQL(
            "INSERT INTO stages (id, issueId, sequenceIndex, title, objective, createdAt, updatedAt) " +
                "VALUES (?, ?, 0, '初始阶段', '验证目标', 100, 100)",
            arrayOf(stageId, issueId),
        )
        db.execSQL(
            "INSERT INTO issue_lifecycle " +
                "(issueId, state, previousState, stateChangedAt, updatedAt, archivedAt, trashedAt, purgeRequestedAt) " +
                "VALUES (?, ?, ?, 100, 500, ?, ?, ?)",
            if (trashed) {
                arrayOf(issueId, "trashed", "archived", 200L, 300L, 400L)
            } else {
                arrayOf(issueId, "active", null, null, null, null)
            },
        )
    }

    private fun insertArchiveEvent() {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO issue_archive_events " +
                "(id, issueId, archiveOperationId, payloadHash, summaryMarkdown, currentStageIdSnapshot, " +
                "stageCountSnapshot, runCountSnapshot, draftCountSnapshot, artifactCountSnapshot, " +
                "audioAssetCountSnapshot, archivedAt, createdAt) " +
                "VALUES (?, ?, 'archive-operation-1', 'archive-payload-1', '归档简报', ?, 1, 0, 0, 0, 0, 200, 200)",
            arrayOf(ARCHIVE_EVENT_ID, SOURCE_ISSUE_ID, SOURCE_STAGE_ID),
        )
    }

    private fun insertRelation() {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO issue_relations " +
                "(id, sourceIssueId, targetIssueId, sourceArchiveEventId, operationId, payloadHash, " +
                "relationType, createdAt, sourcePurgedAt) " +
                "VALUES (?, ?, ?, ?, 'relation-operation-1', 'relation-payload-1', 'continuation', 250, NULL)",
            arrayOf(RELATION_ID, SOURCE_ISSUE_ID, TARGET_ISSUE_ID, ARCHIVE_EVENT_ID),
        )
    }

    private fun insertGlobalPersonalContext() {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO personal_context_entries " +
                "(id, title, content, contentHash, isEnabled, createdAt, updatedAt, lifecycleState, " +
                "sensitive, disabledAt, archivedAt, deletedAt, purgeRequestedAt, purgedAt) " +
                "VALUES (?, '全局背景', '不得删除', 'context-hash', 1, 100, 100, 'active', 0, NULL, NULL, NULL, NULL, NULL)",
            arrayOf(CONTEXT_ID),
        )
    }

    private fun insertDatabasePurgingOperation() {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO issue_purge_operations " +
                "(id, issueId, operationId, payloadHash, impactHash, state, requestedAt, startedAt, " +
                "updatedAt, failedAt, failureCode, failurePhase, retryCount) " +
                "VALUES (?, ?, 'purge-idempotency-1', 'purge-payload-1', 'purge-impact-1', " +
                "'database_purging', 400, 410, 420, NULL, NULL, NULL, 0)",
            arrayOf(OPERATION_ROW_ID, SOURCE_ISSUE_ID),
        )
    }

    private fun insertRun(
        id: String,
        issueId: String,
        stageId: String,
        retryOfRunId: String?,
    ) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO execution_runs " +
                "(id, issueId, stageId, triggerMessageId, idempotencyKey, status, retryOfRunId, createdAt, " +
                "updatedAt, startedAt, finishedAt, stoppedAt, failureCode, failureMessage, runKind, " +
                "parentRunId, discussionId, historyScope) " +
                "VALUES (?, ?, ?, NULL, ?, 'stopped', ?, 100, 100, 100, 100, 100, NULL, NULL, " +
                "'standard', NULL, NULL, 'full_stage')",
            arrayOf(id, issueId, stageId, "idempotency-$id", retryOfRunId),
        )
    }

    private fun count(sql: String, value: String): Int =
        database.openHelper.readableDatabase.query(sql, arrayOf(value)).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }

    private companion object {
        const val SOURCE_ISSUE_ID = "source-issue"
        const val SOURCE_STAGE_ID = "source-stage"
        const val SOURCE_RUN_ID = "source-run"
        const val TARGET_ISSUE_ID = "target-issue"
        const val TARGET_STAGE_ID = "target-stage"
        const val TARGET_RUN_ID = "target-run"
        const val ARCHIVE_EVENT_ID = "archive-event-1"
        const val RELATION_ID = "relation-1"
        const val OPERATION_ROW_ID = "purge-operation-row-1"
        const val CONTEXT_ID = "personal-context-1"
        const val PURGED_AT = 600L
    }
}
