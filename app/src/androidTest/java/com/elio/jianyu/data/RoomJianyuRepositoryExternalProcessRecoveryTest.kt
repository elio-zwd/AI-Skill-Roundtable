package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * 两阶段外部进程恢复测试。
 *
 * 该测试需要在一次 APK 安装后通过两次 `adb shell am instrument` 分别运行 step1、step2，
 * 并在两者之间执行：
 * 1. adb shell am force-stop com.elio.jianyu
 * 2. adb shell monkey -p com.elio.jianyu -c android.intent.category.LAUNCHER 1
 * 3. adb shell am force-stop com.elio.jianyu
 *
 * 普通全量 Instrumentation 不具备外部 ADB 协调条件，因此未显式传入
 * `jianyuExternalProcessRecovery=true` 时跳过本类，避免与同进程已打开的生产数据库单例争用。
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class RoomJianyuRepositoryExternalProcessRecoveryTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun requireExplicitExternalProcessRecoveryRun() {
        val enabled = InstrumentationRegistry.getArguments()
            .getString(EXTERNAL_PROCESS_RECOVERY_ARGUMENT)
            ?.equals("true", ignoreCase = true) == true
        assumeTrue(
            "外部进程恢复测试必须通过一次安装后的 adb shell am instrument 显式启用",
            enabled,
        )
    }

    @Test
    fun step1SeedRecoveryStateBeforeExternalForceStop() = runBlocking {
        context.deleteDatabase(DATABASE_NAME)
        val database = openDatabase()
        try {
            val repository = RoomJianyuRepository(database)
            repository.saveIssue(issueCommand()).successValue()
            repository.createExecutionRun(runCommand()).successValue()
            repository.transitionRun(
                TransitionRunCommand(
                    runId = RUN_ID,
                    expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                    newStatus = ExecutionRunStatus.RUNNING,
                    updatedAt = 20L,
                    startedAt = 20L
                )
            ).successValue()
            repository.appendDomainMessage(pendingMessageCommand()).successValue()
            repository.saveStageDraft(draftCommand()).successValue()

            val seeded = repository.recoverIssue(ISSUE_ID).successValue()
            assertEquals(ExecutionRunStatus.RUNNING, seeded.core.runs.single().status)
            assertEquals(listOf(MESSAGE_ID), seeded.core.pendingMessages.map { it.id })
            assertEquals(listOf(DRAFT_ID), seeded.resources.drafts.map { it.id })
            assertEquals(IssueLifecycleState.ACTIVE, seeded.core.lifecycle.state)
        } finally {
            database.close()
        }
    }

    @Test
    fun step2VerifyRecoveryStateAfterExternalForceStopAndAppRestart() = runBlocking {
        val database = openDatabase()
        try {
            assertNotNull(
                "step2 未找到 step1 写入的 Issue；请确认两阶段之间没有重新安装 APK、清除数据或再次运行 connectedDebugAndroidTest",
                database.coreDomainDao().getIssue(ISSUE_ID),
            )
            assertNotNull(
                "step2 未找到 step1 写入的 Lifecycle；请确认两阶段使用同一应用数据目录",
                database.resourceLifecycleDao().getIssueLifecycle(ISSUE_ID),
            )

            val repository = RoomJianyuRepository(database)
            val recovery1 = repository.recoverIssue(ISSUE_ID).successValue()
            val recovery2 = repository.recoverIssue(ISSUE_ID).successValue()

            assertEquals(recovery1, recovery2)
            assertEquals(1, recovery1.core.stages.size)
            assertEquals(1, recovery1.core.runs.size)
            assertEquals(ExecutionRunStatus.RUNNING, recovery1.core.runs.single().status)
            assertEquals(listOf(MESSAGE_ID), recovery1.core.pendingMessages.map { it.id })
            assertEquals(listOf(DRAFT_ID), recovery1.resources.drafts.map { it.id })
            assertEquals(IssueLifecycleState.ACTIVE, recovery1.core.lifecycle.state)
            assertTrue(recovery1.core.successfulParticipantSnapshotIds().isEmpty())
            assertEquals(setOf(PARTICIPANT_ID), recovery1.core.retryableParticipantSnapshotIds())
            database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
                assertEquals(0, cursor.count)
            }
        } finally {
            database.close()
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    private fun openDatabase(): RoundtableDatabase {
        return Room.databaseBuilder(context, RoundtableDatabase::class.java, DATABASE_NAME)
            .addMigrations(*RoundtableDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
    }

    private fun issueCommand(): SaveIssueCommand {
        return SaveIssueCommand(
            issueId = ISSUE_ID,
            title = "外部恢复议题",
            initialStageId = STAGE_ID,
            initialStageTitle = "初始阶段",
            initialObjective = "验证真实进程停止",
            createdAt = 10L
        )
    }

    private fun runCommand(): CreateExecutionRunCommand {
        return CreateExecutionRunCommand(
            run = ExecutionRunEntity(
                id = RUN_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                idempotencyKey = "external-process-run-key",
                createdAt = 15L,
                updatedAt = 15L
            ),
            participants = listOf(
                ExecutionParticipantSnapshotEntity(
                    id = PARTICIPANT_ID,
                    runId = RUN_ID,
                    sourceType = "official_skill",
                    sourceId = "skill-a",
                    displayName = "Skill A",
                    avatar = "A",
                    skillAssetPath = "skills/skill-a/SKILL.md",
                    systemPrompt = "system",
                    configurationJson = "{}",
                    defaultResponsibility = "",
                    position = 0,
                    createdAt = 15L
                )
            )
        )
    }

    private fun pendingMessageCommand(): AppendDomainMessageCommand {
        return AppendDomainMessageCommand(
            messageId = MESSAGE_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            executionRunId = RUN_ID,
            participantSnapshotId = PARTICIPANT_ID,
            senderId = "skill-a",
            senderName = "Skill A",
            avatar = "A",
            text = "",
            timestamp = 25L,
            isPending = true,
            roundIndex = 1,
            compatibilitySessionTitle = "外部恢复议题"
        )
    }

    private fun draftCommand(): SaveStageDraftCommand {
        val draft = StageSummaryDraftEntity(
            id = DRAFT_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            content = "外部恢复草稿",
            revisionNumber = 1,
            createdAt = 26L,
            updatedAt = 26L
        )
        return SaveStageDraftCommand(
            draft = draft,
            revision = StageSummaryDraftRevisionEntity(
                id = REVISION_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                draftIdSnapshot = DRAFT_ID,
                revisionNumber = 1,
                contentSnapshot = draft.content,
                createdAt = 26L
            )
        )
    }

    private fun <T> RepositoryResult<T>.successValue(): T {
        return when (this) {
            is RepositoryResult.Success -> value
            is RepositoryResult.Failure -> throw AssertionError(
                "期望 RepositoryResult.Success，实际为 Failure(error=$error)",
            )
        }
    }

    companion object {
        private const val EXTERNAL_PROCESS_RECOVERY_ARGUMENT =
            "jianyuExternalProcessRecovery"
        private const val DATABASE_NAME = "roundtable_database"
        private const val ISSUE_ID = "issue-external-process-recovery"
        private const val STAGE_ID = "stage-external-process-recovery"
        private const val RUN_ID = "run-external-process-recovery"
        private const val PARTICIPANT_ID = "participant-external-process-recovery"
        private const val MESSAGE_ID = 5001L
        private const val DRAFT_ID = "draft-external-process-recovery"
        private const val REVISION_ID = "revision-external-process-recovery"
    }
}
