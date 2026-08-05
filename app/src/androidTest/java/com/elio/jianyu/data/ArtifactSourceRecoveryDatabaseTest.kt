package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArtifactSourceRecoveryDatabaseTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: RoundtableDatabase
    private lateinit var repository: JianyuRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RoundtableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomJianyuRepository(database)
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun roomV10RecoversAllArtifactSourcesAfterDraftIsAbandoned() = runBlocking {
        repository.saveIssue(
            SaveIssueCommand(
                issueId = ISSUE_ID,
                title = "议题",
                initialStageId = STAGE_ID,
                initialStageTitle = "阶段",
                initialObjective = "形成结论",
                createdAt = 10,
            ),
        ).successValue()
        repository.createExecutionRun(
            CreateExecutionRunCommand(
                run = ExecutionRunEntity(
                    id = RUN_ID,
                    issueId = ISSUE_ID,
                    stageId = STAGE_ID,
                    idempotencyKey = "run-key",
                    status = ExecutionRunStatus.NOT_STARTED,
                    createdAt = 20,
                    updatedAt = 20,
                    runKind = ExecutionRunKind.CROSS_DISCUSSION_SYNTHESIS,
                    discussionId = "discussion-1",
                    historyScope = ExecutionHistoryScope.EXPLICIT_MESSAGES,
                ),
                participants = listOf(
                    ExecutionParticipantSnapshotEntity(
                        id = PARTICIPANT_ID,
                        runId = RUN_ID,
                        sourceType = "official_skill",
                        sourceId = "integrator",
                        displayName = "整合者",
                        avatar = "I",
                        skillAssetPath = "skills/integrator/SKILL.md",
                        systemPrompt = "system",
                        configurationJson = "{}",
                        defaultResponsibility = "",
                        position = 0,
                        createdAt = 20,
                    ),
                ),
            ),
        ).successValue()
        repository.transitionRun(
            TransitionRunCommand(
                runId = RUN_ID,
                expectedStatuses = setOf(ExecutionRunStatus.NOT_STARTED),
                newStatus = ExecutionRunStatus.SUCCEEDED,
                updatedAt = 30,
                startedAt = 25,
                finishedAt = 30,
            ),
        ).successValue()
        repository.appendDomainMessage(
            AppendDomainMessageCommand(
                messageId = MESSAGE_ID,
                issueId = ISSUE_ID,
                stageId = STAGE_ID,
                executionRunId = RUN_ID,
                participantSnapshotId = PARTICIPANT_ID,
                senderId = "integrator",
                senderName = "整合者",
                avatar = "I",
                text = "整合结果",
                timestamp = 40,
                isPending = false,
                roundIndex = 1,
                compatibilitySessionTitle = "议题",
            ),
        ).successValue()
        val draft = StageSummaryDraftEntity(
            id = DRAFT_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            content = "草稿",
            revisionNumber = 1,
            createdAt = 50,
            updatedAt = 50,
        )
        val revision = StageSummaryDraftRevisionEntity(
            id = REVISION_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            draftIdSnapshot = DRAFT_ID,
            revisionNumber = 1,
            contentSnapshot = "草稿",
            createdAt = 50,
        )
        repository.saveStageDraft(SaveStageDraftCommand(draft, revision)).successValue()
        val materialUsage = MaterialUsageSnapshotEntity(
            id = MATERIAL_USAGE_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            runId = RUN_ID,
            titleSnapshot = "资料",
            sourceTypeSnapshot = "note",
            contentSnapshot = "资料快照",
            contentHash = "hash",
            userConfirmedAt = 45,
            createdAt = 45,
        )
        repository.recordMaterialUsage(materialUsage).successValue()
        repository.confirmArtifact(
            ConfirmArtifactCommand(
                artifact = ConfirmedArtifactEntity(
                    id = ARTIFACT_ID,
                    issueId = ISSUE_ID,
                    stageId = STAGE_ID,
                    title = "阶段成果",
                    content = "草稿",
                    artifactType = "general_summary",
                    contentFormat = "markdown",
                    confirmedAt = 60,
                    createdAt = 60,
                    updatedAt = 60,
                ),
                sources = ArtifactSources(
                    messages = listOf(
                        ArtifactMessageSourceEntity(ARTIFACT_ID, ISSUE_ID, MESSAGE_ID, 60),
                    ),
                    runs = listOf(
                        ArtifactRunSourceEntity(ARTIFACT_ID, ISSUE_ID, RUN_ID, 60),
                    ),
                    draftRevisions = listOf(
                        ArtifactDraftSourceEntity(ARTIFACT_ID, ISSUE_ID, REVISION_ID, 60),
                    ),
                    materials = listOf(
                        ArtifactMaterialSourceEntity(
                            ARTIFACT_ID,
                            ISSUE_ID,
                            MATERIAL_USAGE_ID,
                            60,
                        ),
                    ),
                ),
            ),
        ).successValue()

        assertEquals(10, database.openHelper.writableDatabase.version)
        val recovered = repository.listArtifactSourcesForIssue(ISSUE_ID).successValue().single()
        assertEquals(ARTIFACT_ID, recovered.artifactId)
        assertEquals(listOf(MESSAGE_ID), recovered.messages.map { it.messageId })
        assertEquals(listOf(RUN_ID), recovered.runs.map { it.runId })
        assertEquals(listOf(REVISION_ID), recovered.draftRevisions.map { it.draftRevisionId })
        assertEquals(
            listOf(MATERIAL_USAGE_ID),
            recovered.materials.map { it.materialUsageSnapshotId },
        )

        repository.abandonStageDraft(ISSUE_ID, STAGE_ID).successValue()
        val afterAbandon = repository.listArtifactSourcesForIssue(ISSUE_ID).successValue().single()
        assertEquals(recovered, afterAbandon)
        assertTrue(repository.recoverIssue(ISSUE_ID).successValue().resources.drafts.isEmpty())
    }

    private fun <T> RepositoryResult<T>.successValue(): T =
        (this as RepositoryResult.Success<T>).value

    private companion object {
        const val ISSUE_ID = "issue-source-recovery"
        const val STAGE_ID = "stage-source-recovery"
        const val RUN_ID = "run-source-recovery"
        const val PARTICIPANT_ID = "participant-source-recovery"
        const val MESSAGE_ID = 9001L
        const val DRAFT_ID = "draft-source-recovery"
        const val REVISION_ID = "revision-source-recovery"
        const val MATERIAL_USAGE_ID = "material-usage-source-recovery"
        const val ARTIFACT_ID = "artifact-source-recovery"
    }
}
