package com.elio.jianyu.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResourceLifecycleDatabaseTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: RoundtableDatabase
    private lateinit var coreDao: CoreDomainDao
    private lateinit var resourceDao: ResourceLifecycleDao

    @Before
    fun setUp() {
        context.deleteDatabase(REOPEN_DATABASE)
        database = Room.inMemoryDatabaseBuilder(context, RoundtableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        coreDao = database.coreDomainDao()
        resourceDao = database.resourceLifecycleDao()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(REOPEN_DATABASE)
    }

    @Test
    fun editingAndDeletingCurrentMaterialDoesNotRewriteUsageSnapshot() = runBlocking {
        insertIssueStageAndRun()
        val current = materialReference(content = "current-v1", hash = "hash-v1")
        resourceDao.insertMaterialReference(current)
        resourceDao.recordMaterialUsage(
            materialUsage(
                content = "current-v1",
                hash = "hash-v1"
            )
        )

        resourceDao.updateMaterialReference(
            current.copy(content = "current-v2", contentHash = "hash-v2", updatedAt = 200L)
        )
        resourceDao.deleteMaterialReference(current.id)

        val snapshot = requireNotNull(resourceDao.getMaterialUsageSnapshot(MATERIAL_USAGE_ID))
        assertNull(snapshot.materialReferenceId)
        assertEquals("current-v1", snapshot.contentSnapshot)
        assertEquals("hash-v1", snapshot.contentHash)
        assertEquals(SnapshotContentState.AVAILABLE, snapshot.contentState)
    }

    @Test
    fun editingAndDeletingCurrentPersonalContextDoesNotRewriteUsageSnapshot() = runBlocking {
        insertIssueStageAndRun()
        val current = personalContext(content = "background-v1", hash = "background-hash-v1")
        resourceDao.insertPersonalContextEntry(current)
        resourceDao.recordPersonalContextUsage(
            personalContextUsage(
                content = "background-v1",
                hash = "background-hash-v1"
            )
        )

        resourceDao.updatePersonalContextEntry(
            current.copy(
                content = "background-v2",
                contentHash = "background-hash-v2",
                updatedAt = 210L
            )
        )
        resourceDao.deletePersonalContextEntry(current.id)

        val snapshot = requireNotNull(
            resourceDao.getPersonalContextUsageSnapshot(PERSONAL_CONTEXT_USAGE_ID)
        )
        assertNull(snapshot.personalContextEntryId)
        assertEquals("background-v1", snapshot.contentSnapshot)
        assertEquals("background-hash-v1", snapshot.contentHash)
    }

    @Test
    fun unconfirmedPersonalContextUsageIsRejectedWithoutLeakingContent() = runBlocking {
        insertIssueStageAndRun()
        val sensitive = "private-background-value"
        val error = runCatching {
            resourceDao.recordPersonalContextUsage(
                personalContextUsage(content = sensitive, confirmedAt = 0L)
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains(sensitive).not())
        assertNull(resourceDao.getPersonalContextUsageSnapshot(PERSONAL_CONTEXT_USAGE_ID))
    }

    @Test
    fun usageSnapshotsRejectMismatchedIssueStageAndRun() = runBlocking {
        insertIssueStageAndRun()
        coreDao.createIssueWithInitialStage(
            issue(id = ISSUE_2),
            stage(id = STAGE_2, issueId = ISSUE_2)
        )

        assertConstraint {
            resourceDao.recordMaterialUsage(
                materialUsage(issueId = ISSUE_ID, stageId = STAGE_2, runId = RUN_ID)
            )
        }
        assertConstraint {
            resourceDao.recordPersonalContextUsage(
                personalContextUsage(issueId = ISSUE_2, stageId = STAGE_2, runId = RUN_ID)
            )
        }
    }

    @Test
    fun draftAndLifecycleSurviveDatabaseReopenWithoutExpiry() = runBlocking {
        database.close()
        val firstOpen = openPersistentDatabase()
        val firstCoreDao = firstOpen.coreDomainDao()
        val firstResourceDao = firstOpen.resourceLifecycleDao()
        firstCoreDao.createIssueWithInitialStage(issue(), stage())
        firstResourceDao.saveDraftWithRevision(draft(), draftRevision())
        firstResourceDao.insertIssueLifecycle(
            lifecycle(
                state = IssueLifecycleState.TRASHED,
                previousState = IssueLifecycleState.ARCHIVED,
                changedAt = 300L,
                trashedAt = 300L
            )
        )
        firstOpen.close()

        val reopened = openPersistentDatabase()
        val reopenedDao = reopened.resourceLifecycleDao()
        val restoredDraft = reopenedDao.getStageSummaryDraft(ISSUE_ID, STAGE_ID)
        val restoredRevisions = reopenedDao.getStageSummaryDraftRevisions(ISSUE_ID, STAGE_ID)
        val restoredLifecycle = reopenedDao.getIssueLifecycle(ISSUE_ID)

        assertEquals("draft-content", restoredDraft?.content)
        assertEquals(1, restoredRevisions.size)
        assertEquals("draft-content", restoredRevisions.single().contentSnapshot)
        assertEquals(IssueLifecycleState.TRASHED, restoredLifecycle?.state)
        assertEquals(IssueLifecycleState.ARCHIVED, restoredLifecycle?.previousState)
        assertNull(restoredLifecycle?.purgeRequestedAt)
        reopened.close()

        reopenInMemoryDatabase()
    }

    @Test
    fun abandoningDraftDoesNotDeleteConfirmedArtifactOrRevisionHistory() = runBlocking {
        insertIssueAndStage()
        resourceDao.saveDraftWithRevision(draft(), draftRevision())
        resourceDao.createArtifactWithSources(
            artifact(),
            ArtifactSources(
                draftRevisions = listOf(
                    ArtifactDraftSourceEntity(
                        artifactId = ARTIFACT_ID,
                        issueId = ISSUE_ID,
                        draftRevisionId = DRAFT_REVISION_ID,
                        createdAt = 220L
                    )
                )
            )
        )

        resourceDao.abandonStageSummaryDraft(ISSUE_ID, STAGE_ID)

        assertNull(resourceDao.getStageSummaryDraft(ISSUE_ID, STAGE_ID))
        assertEquals(1, resourceDao.getStageSummaryDraftRevisions(ISSUE_ID, STAGE_ID).size)
        assertEquals(ARTIFACT_ID, resourceDao.getConfirmedArtifact(ARTIFACT_ID)?.id)
    }

    @Test
    fun crossIssueArtifactSourceRollsBackWholeTransaction() = runBlocking {
        insertIssueStageAndRun()
        coreDao.createIssueWithInitialStage(
            issue(id = ISSUE_2),
            stage(id = STAGE_2, issueId = ISSUE_2)
        )
        val otherRun = run(
            id = RUN_2,
            issueId = ISSUE_2,
            stageId = STAGE_2,
            idempotencyKey = "issue-2-stage-2-run-2"
        )
        coreDao.insertExecutionRun(otherRun)

        assertConstraint {
            resourceDao.createArtifactWithSources(
                artifact(),
                ArtifactSources(
                    runs = listOf(
                        ArtifactRunSourceEntity(
                            artifactId = ARTIFACT_ID,
                            issueId = ISSUE_ID,
                            runId = RUN_2,
                            createdAt = 230L
                        )
                    )
                )
            )
        }

        assertNull(resourceDao.getConfirmedArtifact(ARTIFACT_ID))
    }

    @Test
    fun audioAssetRequiresOneLegalSourceAndCanRepresentMissingFile() = runBlocking {
        insertIssueAndStage()
        val messageId = insertDomainMessage()
        resourceDao.createAudioAsset(
            audioAsset(
                sourceMessageId = messageId,
                fileState = AudioFileState.MISSING
            )
        )

        val stored = requireNotNull(resourceDao.getAudioAsset(AUDIO_ID))
        assertEquals(AudioFileState.MISSING, stored.fileState)
        assertEquals("/controlled/missing.opus", stored.storagePath)

        val noSourceError = runCatching {
            resourceDao.createAudioAsset(
                audioAsset(id = "audio-no-source", sourceMessageId = null)
            )
        }.exceptionOrNull()
        assertTrue(noSourceError is IllegalArgumentException)

        assertConstraint {
            resourceDao.createAudioAsset(
                audioAsset(
                    id = "audio-wrong-issue",
                    issueId = ISSUE_2,
                    stageId = STAGE_2,
                    sourceMessageId = messageId,
                    storagePath = "/controlled/wrong.opus"
                )
            )
        }
    }

    @Test
    fun officialCombinationPreservesOrderAndRejectsDuplicates() = runBlocking {
        val combination = combination()
        val members = listOf(
            combinationMember(skillId = "official-a", position = 1),
            combinationMember(skillId = "official-b", position = 0)
        )
        resourceDao.createOfficialCombination(combination, members)

        val stored = resourceDao.getOfficialSkillCombinationMembers(COMBINATION_ID)
        assertEquals(listOf(0, 1), stored.map { it.position })
        assertEquals(listOf("official-b", "official-a"), stored.map { it.officialSkillId })

        val duplicateSkillError = runCatching {
            resourceDao.createOfficialCombination(
                combination(id = "combination-duplicate-skill"),
                listOf(
                    combinationMember(
                        combinationId = "combination-duplicate-skill",
                        skillId = "official-a",
                        position = 0
                    ),
                    combinationMember(
                        combinationId = "combination-duplicate-skill",
                        skillId = "official-a",
                        position = 1
                    )
                )
            )
        }.exceptionOrNull()
        assertTrue(duplicateSkillError is IllegalArgumentException)

        val duplicatePositionError = runCatching {
            resourceDao.createOfficialCombination(
                combination(id = "combination-duplicate-position"),
                listOf(
                    combinationMember(
                        combinationId = "combination-duplicate-position",
                        skillId = "official-a",
                        position = 0
                    ),
                    combinationMember(
                        combinationId = "combination-duplicate-position",
                        skillId = "official-b",
                        position = 0
                    )
                )
            )
        }.exceptionOrNull()
        assertTrue(duplicatePositionError is IllegalArgumentException)
    }

    @Test
    fun combinationChangesDoNotRewriteParticipantHistory() = runBlocking {
        insertIssueStageAndRun()
        coreDao.insertParticipantSnapshots(listOf(participantSnapshot()))
        resourceDao.createOfficialCombination(
            combination(),
            listOf(combinationMember(defaultResponsibility = "当前默认职责"))
        )

        val updated = combination().copy(name = "更新组合", updatedAt = 400L)
        resourceDao.updateOfficialSkillCombination(updated)

        val snapshot = coreDao.getParticipantSnapshots(RUN_ID).single()
        assertEquals("历史职责", snapshot.defaultResponsibility)
        assertEquals("历史 Prompt", snapshot.systemPrompt)
    }

    @Test
    fun lifecycleStatesAreDistinctAndRecoveryMetadataIsPreserved() = runBlocking {
        insertIssueAndStage()
        resourceDao.insertIssueLifecycle(lifecycle())
        resourceDao.updateIssueLifecycle(
            lifecycle(
                state = IssueLifecycleState.ARCHIVED,
                previousState = IssueLifecycleState.ACTIVE,
                changedAt = 500L,
                archivedAt = 500L
            )
        )
        resourceDao.updateIssueLifecycle(
            lifecycle(
                state = IssueLifecycleState.TRASHED,
                previousState = IssueLifecycleState.ARCHIVED,
                changedAt = 600L,
                archivedAt = 500L,
                trashedAt = 600L
            )
        )

        val trashed = requireNotNull(resourceDao.getIssueLifecycle(ISSUE_ID))
        assertEquals(IssueLifecycleState.TRASHED, trashed.state)
        assertEquals(IssueLifecycleState.ARCHIVED, trashed.previousState)
        assertNull(trashed.purgeRequestedAt)

        resourceDao.updateIssueLifecycle(
            trashed.copy(
                state = IssueLifecycleState.ARCHIVED,
                previousState = IssueLifecycleState.TRASHED,
                stateChangedAt = 700L,
                updatedAt = 700L,
                trashedAt = null
            )
        )
        val restored = requireNotNull(resourceDao.getIssueLifecycle(ISSUE_ID))
        assertEquals(IssueLifecycleState.ARCHIVED, restored.state)
        assertEquals(IssueLifecycleState.TRASHED, restored.previousState)
    }

    @Test
    fun orphanRecordsAndUniqueConflictsAreRejected() = runBlocking {
        assertConstraint {
            resourceDao.insertMaterialReference(materialReference(issueId = "missing-issue"))
        }
        assertConstraint {
            resourceDao.saveDraftWithRevision(draft(), draftRevision())
        }
        assertConstraint {
            resourceDao.insertIssueLifecycle(lifecycle())
        }

        insertIssueAndStage()
        resourceDao.createOfficialCombination(
            combination(),
            listOf(combinationMember())
        )
        assertConstraint {
            database.openHelper.writableDatabase.execSQL(
                "INSERT INTO official_skill_combination_members " +
                    "(combinationId, officialSkillId, position, defaultResponsibility, createdAt) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf(COMBINATION_ID, "official-b", 0, null, 101L)
            )
        }
    }

    @Test
    fun foreignKeyCheckReportsNoViolations() = runBlocking {
        insertIssueStageAndRun()
        resourceDao.insertIssueLifecycle(lifecycle())
        resourceDao.insertMaterialReference(materialReference())
        resourceDao.recordMaterialUsage(materialUsage())
        resourceDao.insertPersonalContextEntry(personalContext())
        resourceDao.recordPersonalContextUsage(personalContextUsage())
        resourceDao.saveDraftWithRevision(draft(), draftRevision())
        resourceDao.createArtifactWithSources(artifact(), ArtifactSources())

        database.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { cursor ->
            assertEquals(0, cursor.count)
        }
    }

    private suspend fun insertIssueAndStage() {
        coreDao.createIssueWithInitialStage(issue(), stage())
    }

    private suspend fun insertIssueStageAndRun() {
        insertIssueAndStage()
        coreDao.insertExecutionRun(run())
    }

    private suspend fun insertDomainMessage(): Long {
        val chatId = database.chatDao().insertSession(
            ChatSession(title = "资源生命周期测试", createdAt = 100L)
        )
        return database.chatDao().insertMessage(
            Message(
                chatId = chatId,
                senderId = "user",
                senderName = "用户",
                avatar = "U",
                text = "来源消息",
                timestamp = 120L,
                roundIndex = 2,
                audioFilePath = "/legacy/audio.wav",
                audioFormat = "wav",
                audioSizeBytes = 128L,
                issueId = ISSUE_ID,
                stageId = STAGE_ID
            )
        )
    }

    private fun openPersistentDatabase(): RoundtableDatabase {
        return Room.databaseBuilder(context, RoundtableDatabase::class.java, REOPEN_DATABASE)
            .addMigrations(*RoundtableDatabase.ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
    }

    private fun reopenInMemoryDatabase() {
        database = Room.inMemoryDatabaseBuilder(context, RoundtableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        coreDao = database.coreDomainDao()
        resourceDao = database.resourceLifecycleDao()
    }

    private fun issue(id: String = ISSUE_ID) = IssueEntity(
        id = id,
        title = "测试议题 $id",
        createdAt = 100L,
        updatedAt = 100L
    )

    private fun stage(
        id: String = STAGE_ID,
        issueId: String = ISSUE_ID
    ) = StageEntity(
        id = id,
        issueId = issueId,
        sequenceIndex = 0,
        title = "测试阶段 $id",
        objective = "推进目标",
        createdAt = 110L,
        updatedAt = 110L
    )

    private fun run(
        id: String = RUN_ID,
        issueId: String = ISSUE_ID,
        stageId: String = STAGE_ID,
        idempotencyKey: String = "issue-1-stage-1-run-1"
    ) = ExecutionRunEntity(
        id = id,
        issueId = issueId,
        stageId = stageId,
        idempotencyKey = idempotencyKey,
        createdAt = 120L,
        updatedAt = 120L,
        actualModelId = "gemini-3.6-flash",
        actualThinkingLevel = ExecutionThinkingLevel.MEDIUM,
        thinkingLevelSource = ExecutionThinkingSource.AUTO_ROUTED,
    )

    private fun participantSnapshot() = ExecutionParticipantSnapshotEntity(
        id = "participant-snapshot-1",
        runId = RUN_ID,
        sourceType = "official_skill",
        sourceId = "official-a",
        displayName = "历史成员",
        avatar = "H",
        skillAssetPath = "skills/official-a/SKILL.md",
        systemPrompt = "历史 Prompt",
        configurationJson = "{}",
        defaultResponsibility = "历史职责",
        position = 0,
        createdAt = 130L
    )

    private fun materialReference(
        issueId: String = ISSUE_ID,
        content: String = "material-content",
        hash: String = "material-hash"
    ) = MaterialReferenceEntity(
        id = MATERIAL_ID,
        issueId = issueId,
        stageId = if (issueId == ISSUE_ID) STAGE_ID else null,
        title = "资料",
        sourceType = "user_note",
        sourceLocator = "note://material-1",
        content = content,
        contentHash = hash,
        sourceCapturedAt = 120L,
        createdAt = 120L,
        updatedAt = 120L
    )

    private fun materialUsage(
        issueId: String = ISSUE_ID,
        stageId: String = STAGE_ID,
        runId: String? = RUN_ID,
        content: String = "material-content",
        hash: String = "material-hash"
    ) = MaterialUsageSnapshotEntity(
        id = MATERIAL_USAGE_ID,
        issueId = issueId,
        stageId = stageId,
        runId = runId,
        materialReferenceId = MATERIAL_ID,
        titleSnapshot = "资料",
        sourceTypeSnapshot = "user_note",
        sourceLocatorSnapshot = "note://material-1",
        contentSnapshot = content,
        contentHash = hash,
        userConfirmedAt = 130L,
        createdAt = 130L
    )

    private fun personalContext(
        content: String = "background-content",
        hash: String = "background-hash"
    ) = PersonalContextEntryEntity(
        id = PERSONAL_CONTEXT_ID,
        title = "个人背景",
        content = content,
        contentHash = hash,
        createdAt = 120L,
        updatedAt = 120L
    )

    private fun personalContextUsage(
        issueId: String = ISSUE_ID,
        stageId: String = STAGE_ID,
        runId: String? = RUN_ID,
        content: String = "background-content",
        hash: String = "background-hash",
        confirmedAt: Long = 130L
    ) = PersonalContextUsageSnapshotEntity(
        id = PERSONAL_CONTEXT_USAGE_ID,
        issueId = issueId,
        stageId = stageId,
        runId = runId,
        personalContextEntryId = PERSONAL_CONTEXT_ID,
        titleSnapshot = "个人背景",
        contentSnapshot = content,
        contentHash = hash,
        userConfirmedAt = confirmedAt,
        createdAt = 130L
    )

    private fun draft() = StageSummaryDraftEntity(
        id = DRAFT_ID,
        issueId = ISSUE_ID,
        stageId = STAGE_ID,
        content = "draft-content",
        revisionNumber = 1,
        createdAt = 200L,
        updatedAt = 200L
    )

    private fun draftRevision() = StageSummaryDraftRevisionEntity(
        id = DRAFT_REVISION_ID,
        issueId = ISSUE_ID,
        stageId = STAGE_ID,
        draftIdSnapshot = DRAFT_ID,
        revisionNumber = 1,
        contentSnapshot = "draft-content",
        createdAt = 200L
    )

    private fun artifact() = ConfirmedArtifactEntity(
        id = ARTIFACT_ID,
        issueId = ISSUE_ID,
        stageId = STAGE_ID,
        title = "正式成果",
        content = "artifact-content",
        artifactType = "stage_summary",
        contentFormat = "markdown",
        confirmedAt = 220L,
        createdAt = 220L,
        updatedAt = 220L
    )

    private fun audioAsset(
        id: String = AUDIO_ID,
        issueId: String = ISSUE_ID,
        stageId: String = STAGE_ID,
        sourceMessageId: Long? = null,
        storagePath: String = "/controlled/missing.opus",
        fileState: AudioFileState = AudioFileState.PENDING
    ) = AudioAssetEntity(
        id = id,
        issueId = issueId,
        stageId = stageId,
        sourceMessageId = sourceMessageId,
        storagePath = storagePath,
        mimeType = "audio/ogg",
        format = "opus",
        sizeBytes = 0L,
        fileState = fileState,
        createdAt = 240L,
        updatedAt = 240L
    )

    private fun combination(id: String = COMBINATION_ID) = OfficialSkillCombinationEntity(
        id = id,
        name = "官方组合",
        createdAt = 100L,
        updatedAt = 100L
    )

    private fun combinationMember(
        combinationId: String = COMBINATION_ID,
        skillId: String = "official-a",
        position: Int = 0,
        defaultResponsibility: String? = null
    ) = OfficialSkillCombinationMemberEntity(
        combinationId = combinationId,
        officialSkillId = skillId,
        position = position,
        defaultResponsibility = defaultResponsibility,
        createdAt = 100L
    )

    private fun lifecycle(
        state: IssueLifecycleState = IssueLifecycleState.ACTIVE,
        previousState: IssueLifecycleState? = null,
        changedAt: Long = 100L,
        archivedAt: Long? = null,
        trashedAt: Long? = null
    ) = IssueLifecycleEntity(
        issueId = ISSUE_ID,
        state = state,
        previousState = previousState,
        stateChangedAt = changedAt,
        updatedAt = changedAt,
        archivedAt = archivedAt,
        trashedAt = trashedAt
    )

    private suspend fun assertConstraint(block: suspend () -> Unit) {
        try {
            block()
            fail("Expected a SQLite constraint failure")
        } catch (error: Throwable) {
            assertTrue(
                "Expected SQLiteConstraintException but was ${error::class.java.name}",
                error.hasConstraintCause()
            )
        }
    }

    private fun Throwable.hasConstraintCause(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SQLiteConstraintException) return true
            current = current.cause
        }
        return false
    }

    companion object {
        private const val ISSUE_ID = "issue-1"
        private const val ISSUE_2 = "issue-2"
        private const val STAGE_ID = "stage-1"
        private const val STAGE_2 = "stage-2"
        private const val RUN_ID = "run-1"
        private const val RUN_2 = "run-2"
        private const val MATERIAL_ID = "material-1"
        private const val MATERIAL_USAGE_ID = "material-usage-1"
        private const val PERSONAL_CONTEXT_ID = "personal-context-1"
        private const val PERSONAL_CONTEXT_USAGE_ID = "personal-context-usage-1"
        private const val DRAFT_ID = "draft-1"
        private const val DRAFT_REVISION_ID = "draft-revision-1"
        private const val ARTIFACT_ID = "artifact-1"
        private const val AUDIO_ID = "audio-1"
        private const val COMBINATION_ID = "combination-1"
        private const val REOPEN_DATABASE = "resource-lifecycle-reopen-test"
    }
}
