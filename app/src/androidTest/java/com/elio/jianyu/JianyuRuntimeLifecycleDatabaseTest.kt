package com.elio.jianyu

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.RoundtableDatabase
import com.elio.jianyu.data.SaveIssueCommand
import com.elio.jianyu.runtime.DatabaseMaintenanceOutcome
import com.elio.jianyu.runtime.DatabaseMaintenanceStage
import com.elio.jianyu.runtime.JianyuRuntimeState
import com.elio.jianyu.runtime.JianyuRuntimeUnavailableException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JianyuRuntimeLifecycleDatabaseTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp(): Unit = runBlocking {
        JianyuAppRuntimeProvider.resetForTests(context)
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown(): Unit = runBlocking {
        JianyuAppRuntimeProvider.resetForTests(context)
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun maintenanceWaitsForLeaseThenReopensNewRuntimeAndKeepsDataWritable(): Unit = runBlocking {
        val initialState = JianyuAppRuntimeProvider.observe(context).value as JianyuRuntimeState.Ready
        val oldRuntime = initialState.runtime
        val oldRepository = oldRuntime.repository
        oldRepository.saveIssue(issueCommand("issue-before", "stage-before", 10L)).successValue()
        val lease = requireNotNull(
            JianyuAppRuntimeProvider.tryAcquireReady(initialState.generation),
        )
        val beforeCloseEntered = CompletableDeferred<Unit>()
        val whileClosedEntered = CompletableDeferred<Unit>()

        val maintenance = async(Dispatchers.IO) {
            JianyuAppRuntimeProvider.withDatabaseClosed(
                context = context,
                beforeClose = {
                    beforeCloseEntered.complete(Unit)
                    assertTrue(it.isOpen)
                },
                whileClosed = {
                    whileClosedEntered.complete(Unit)
                    assertFalse(oldRuntime.database.isOpen)
                    "snapshot-source-ready"
                },
                afterReopen = { reopened ->
                    assertTrue(
                        "重开后的数据库句柄已关闭: explicitlyClosed=${reopened.database.isExplicitlyClosed}",
                        reopened.database.isOpen,
                    )
                    assertDatabaseHealthy(reopened)
                },
            )
        }

        JianyuAppRuntimeProvider.observe(context).first {
            it is JianyuRuntimeState.Maintenance
        }
        assertFalse(beforeCloseEntered.isCompleted)
        assertFalse(whileClosedEntered.isCompleted)

        lease.close()
        assertEquals("snapshot-source-ready", maintenance.await().successValue())

        val reopenedState = JianyuAppRuntimeProvider.observe(context).value as JianyuRuntimeState.Ready
        assertEquals(initialState.generation + 1L, reopenedState.generation)
        assertNotSame(oldRuntime, reopenedState.runtime)
        assertNotSame(oldRuntime.database, reopenedState.runtime.database)
        assertTrue(reopenedState.runtime.database.isOpen)
        assertTrue(
            oldRepository.recoverIssue("issue-before").failureError() is RepositoryError.StorageFailure,
        )
        assertEquals(
            "issue-before",
            reopenedState.runtime.repository.recoverIssue("issue-before").successValue().core.issue.id,
        )
        reopenedState.runtime.repository
            .saveIssue(issueCommand("issue-after", "stage-after", 20L))
            .successValue()
        assertEquals(
            "issue-after",
            reopenedState.runtime.repository.recoverIssue("issue-after").successValue().core.issue.id,
        )
    }

    @Test
    fun beforeCloseFailureRestoresOriginalReadyGenerationWithoutClosingDatabase(): Unit = runBlocking {
        val initial = JianyuAppRuntimeProvider.observe(context).value as JianyuRuntimeState.Ready

        val outcome = withContext(Dispatchers.IO) {
            JianyuAppRuntimeProvider.withDatabaseClosed(
                context = context,
                beforeClose = { throw IllegalStateException("checkpoint-failed") },
                whileClosed = { error("whileClosed must not run") },
                afterReopen = { error("afterReopen must not run") },
            )
        }

        val failure = outcome as DatabaseMaintenanceOutcome.Failure
        assertEquals(DatabaseMaintenanceStage.BEFORE_CLOSE, failure.stage)
        assertFalse(failure.reopened)
        val restored = JianyuAppRuntimeProvider.observe(context).value as JianyuRuntimeState.Ready
        assertEquals(initial.generation, restored.generation)
        assertSame(initial.runtime, restored.runtime)
        assertTrue(restored.runtime.database.isOpen)
    }

    @Test
    fun whileClosedFailureStillReopensAndPublishesUsableNewGeneration(): Unit = runBlocking {
        val initial = JianyuAppRuntimeProvider.observe(context).value as JianyuRuntimeState.Ready
        initial.runtime.repository.saveIssue(issueCommand("issue-1", "stage-1", 10L)).successValue()

        val outcome = withContext(Dispatchers.IO) {
            JianyuAppRuntimeProvider.withDatabaseClosed(
                context = context,
                beforeClose = {},
                whileClosed = { throw IllegalStateException("expected-test-failure") },
                afterReopen = { assertDatabaseHealthy(it) },
            )
        }

        val failure = outcome as DatabaseMaintenanceOutcome.Failure
        assertEquals(DatabaseMaintenanceStage.WHILE_CLOSED, failure.stage)
        assertTrue(failure.reopened)
        val reopened = JianyuAppRuntimeProvider.observe(context).value as JianyuRuntimeState.Ready
        assertEquals(initial.generation + 1L, reopened.generation)
        assertEquals(
            "issue-1",
            reopened.runtime.repository.recoverIssue("issue-1").successValue().core.issue.id,
        )
    }

    @Test
    fun cancellationInsideClosedStageStillReopensBeforeCancellationEscapes(): Unit = runBlocking {
        val initial = JianyuAppRuntimeProvider.observe(context).value as JianyuRuntimeState.Ready
        val entered = CompletableDeferred<Unit>()

        val maintenance = async(Dispatchers.IO) {
            JianyuAppRuntimeProvider.withDatabaseClosed(
                context = context,
                beforeClose = {},
                whileClosed = {
                    entered.complete(Unit)
                    CompletableDeferred<Unit>().await()
                },
                afterReopen = { assertDatabaseHealthy(it) },
            )
        }
        entered.await()
        maintenance.cancelAndJoin()

        val reopened = JianyuAppRuntimeProvider.observe(context).value as JianyuRuntimeState.Ready
        assertEquals(initial.generation + 1L, reopened.generation)
        assertTrue(reopened.runtime.database.isOpen)
        reopened.runtime.repository
            .saveIssue(issueCommand("issue-after-cancel", "stage-after-cancel", 30L))
            .successValue()
    }

    @Test
    fun afterReopenVerificationFailureClosesCandidateUntilValidatedRetry(): Unit = runBlocking {
        val initial = JianyuAppRuntimeProvider.observe(context).value as JianyuRuntimeState.Ready
        var rejectedDatabase: RoundtableDatabase? = null

        val outcome = withContext(Dispatchers.IO) {
            JianyuAppRuntimeProvider.withDatabaseClosed(
                context = context,
                beforeClose = {},
                whileClosed = { Unit },
                afterReopen = {
                    rejectedDatabase = it.database
                    throw IllegalStateException("verification-failed")
                },
            )
        }

        val failure = outcome as DatabaseMaintenanceOutcome.Failure
        assertEquals(DatabaseMaintenanceStage.AFTER_REOPEN, failure.stage)
        assertFalse(failure.reopened)
        val rejected = requireNotNull(rejectedDatabase)
        assertFalse(rejected.isOpen)
        val unavailable = JianyuAppRuntimeProvider.observe(context).value as JianyuRuntimeState.Unavailable
        assertEquals(initial.generation + 1L, unavailable.generation)
        assertEquals(DatabaseMaintenanceStage.AFTER_REOPEN, unavailable.stage)

        assertTrue(
            withContext(Dispatchers.IO) {
                JianyuAppRuntimeProvider.retryOpen(context)
            },
        )
        val retried = JianyuAppRuntimeProvider.observe(context).value as JianyuRuntimeState.Ready
        assertEquals(unavailable.generation, retried.generation)
        assertNotSame(rejected, retried.runtime.database)
        assertTrue(retried.runtime.database.isOpen)
        assertDatabaseHealthy(retried.runtime)
        retried.runtime.repository
            .saveIssue(issueCommand("issue-after-retry", "stage-after-retry", 40L))
            .successValue()
    }

    @Test
    fun maintenanceRejectsDirectGetInsteadOfReturningClosingRuntime(): Unit = runBlocking {
        val initial = JianyuAppRuntimeProvider.observe(context).value as JianyuRuntimeState.Ready
        val entered = CompletableDeferred<Unit>()
        val continueClosed = CompletableDeferred<Unit>()

        val maintenance = async(Dispatchers.IO) {
            JianyuAppRuntimeProvider.withDatabaseClosed(
                context = context,
                beforeClose = {},
                whileClosed = {
                    entered.complete(Unit)
                    continueClosed.await()
                },
                afterReopen = { assertDatabaseHealthy(it) },
            )
        }
        entered.await()

        var rejected = false
        try {
            JianyuAppRuntimeProvider.get(context)
        } catch (_: JianyuRuntimeUnavailableException) {
            rejected = true
        }
        assertTrue(rejected)
        assertFalse(initial.runtime.database.isOpen)

        continueClosed.complete(Unit)
        maintenance.await()
    }

    private fun assertDatabaseHealthy(runtime: JianyuAppRuntime) {
        runtime.database.openHelper.writableDatabase
            .query("SELECT 1")
            .use { cursor -> assertTrue("最小查询没有返回结果", cursor.moveToFirst()) }
        runtime.database.openHelper.writableDatabase
            .query("PRAGMA foreign_key_check")
            .use { cursor -> assertEquals("外键检查发现损坏关系", 0, cursor.count) }
    }

    private fun issueCommand(issueId: String, stageId: String, createdAt: Long) = SaveIssueCommand(
        issueId = issueId,
        title = issueId,
        initialStageId = stageId,
        initialStageTitle = stageId,
        initialObjective = "验证运行时重开",
        createdAt = createdAt,
    )

    private fun <T> RepositoryResult<T>.successValue(): T =
        (this as RepositoryResult.Success<T>).value

    private fun RepositoryResult<*>.failureError(): RepositoryError =
        (this as RepositoryResult.Failure).error

    private fun <T> DatabaseMaintenanceOutcome<T>.successValue(): T =
        when (this) {
            is DatabaseMaintenanceOutcome.Success -> value
            is DatabaseMaintenanceOutcome.Failure -> error(
                "维护未成功: stage=${stage}, reopened=${reopened}, " +
                    "cause=${cause::class.java.simpleName}:${cause.message}",
            )
        }

    private companion object {
        const val DATABASE_NAME = "roundtable_database"
    }
}
