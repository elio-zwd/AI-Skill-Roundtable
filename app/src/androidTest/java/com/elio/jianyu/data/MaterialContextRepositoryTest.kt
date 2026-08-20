package com.elio.jianyu.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialContextRepositoryTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private lateinit var database: RoundtableDatabase
    private lateinit var repository: JianyuRepository

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, RoundtableDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomJianyuRepository(
            database = database,
            officialSkillIdValidator = OfficialSkillIdValidator { true },
        )
    }

    @After
    fun tearDown() {
        if (database.isOpen) database.close()
    }

    @Test
    fun materialAndPersonalContextCrudAreIdempotentAndLifecycleFiltered() = runBlocking {
        saveIssue()
        val create = materialCommand()

        val first = repository.createMaterial(create)
        val repeated = repository.createMaterial(create)
        val conflict = repository.createMaterial(create.copy(content = "不同正文"))
        val current = first.successValue()
        val updated = repository.updateMaterial(
            UpdateMaterialCommand(
                id = current.id,
                title = "更新后的资料",
                sourceType = current.sourceType,
                sourceLocator = current.sourceLocator,
                content = "更新正文",
                sourcePublishedAt = 50L,
                sourceCapturedAt = 120L,
                sensitive = true,
                expectedUpdatedAt = current.updatedAt,
                updatedAt = 200L,
            ),
        ).successValue()

        assertFalse((first as RepositoryResult.Success).idempotent)
        assertTrue((repeated as RepositoryResult.Success).idempotent)
        assertTrue(conflict.failureError() is RepositoryError.IdempotencyConflict)
        assertNotEquals(current.contentHash, updated.contentHash)
        assertEquals(50L, updated.sourcePublishedAt)
        assertEquals(120L, updated.sourceCapturedAt)
        assertEquals(100L, updated.createdAt)

        val archived = repository.changeMaterialLifecycle(
            ChangeMaterialLifecycleCommand(
                materialId = updated.id,
                expectedUpdatedAt = updated.updatedAt,
                target = ContextSourceLifecycle.ARCHIVED,
                changedAt = 300L,
            ),
        ).successValue()
        assertTrue(repository.listMaterials().successValue().isEmpty())
        assertEquals(
            listOf(updated.id),
            repository.listMaterials(
                MaterialFilter(lifecycles = setOf(ContextSourceLifecycle.ARCHIVED)),
            ).successValue().map { it.id },
        )
        repository.changeMaterialLifecycle(
            ChangeMaterialLifecycleCommand(
                materialId = archived.id,
                expectedUpdatedAt = archived.updatedAt,
                target = ContextSourceLifecycle.ACTIVE,
                changedAt = 400L,
            ),
        ).successValue()

        val personal = repository.createPersonalContext(
            CreatePersonalContextCommand(
                id = PERSONAL_ID,
                title = "个人背景",
                content = "长期背景",
                sensitive = true,
                createdAt = 500L,
            ),
        ).successValue()
        repository.changePersonalContextLifecycle(
            ChangePersonalContextLifecycleCommand(
                personalContextId = personal.id,
                expectedUpdatedAt = personal.updatedAt,
                target = ContextSourceLifecycle.DISABLED,
                changedAt = 600L,
            ),
        ).successValue()

        assertTrue(repository.listPersonalContexts().successValue().isEmpty())
        assertEquals(
            listOf(PERSONAL_ID),
            repository.listPersonalContexts(
                PersonalContextFilter(lifecycles = setOf(ContextSourceLifecycle.DISABLED)),
            ).successValue().map { it.id },
        )
        assertEquals(0, foreignKeyViolations())
    }

    @Test
    fun staleSelectionIsRejectedBeforeRuntimeCreation() = runBlocking {
        saveIssue()
        val material = repository.createMaterial(materialCommand()).successValue()
        val draft = confirmedDraft(material = material, runId = RUN_ID)
        val prepared = repository.prepareExecutionContext(
            PrepareExecutionContextCommand(draft = draft, preparedAt = 200L),
        ).successValue()
        repository.updateMaterial(
            UpdateMaterialCommand(
                id = material.id,
                title = material.title,
                sourceType = material.sourceType,
                sourceLocator = material.sourceLocator,
                content = "确认后被编辑",
                sourcePublishedAt = material.sourcePublishedAt,
                sourceCapturedAt = material.sourceCapturedAt,
                sensitive = material.sensitive,
                expectedUpdatedAt = material.updatedAt,
                updatedAt = 250L,
            ),
        ).successValue()

        val result = repository.createExecutionRuntime(runtimeCommand(prepared.usage))

        val error = result.failureError() as RepositoryError.InvalidState
        assertEquals(ContextValidationError.SOURCE_STALE.code, error.stateCode)
        assertNull(database.jianyuRepositoryDao().getExecutionRun(RUN_ID))
        assertTrue(database.jianyuRepositoryDao().getMaterialUsagesForRun(RUN_ID).isEmpty())
        assertTrue(database.jianyuRepositoryDao().getPersonalContextUsagesForRun(RUN_ID).isEmpty())
        assertEquals(0, foreignKeyViolations())
    }

    @Test
    fun runtimeAndActualUsageSnapshotsAreAtomicIdempotentAndHistoricallyStable() = runBlocking {
        saveIssue()
        val material = repository.createMaterial(materialCommand()).successValue()
        val personal = repository.createPersonalContext(
            CreatePersonalContextCommand(
                id = PERSONAL_ID,
                title = "个人背景",
                content = "跨议题背景",
                sensitive = true,
                createdAt = 110L,
            ),
        ).successValue()
        val prepared = repository.prepareExecutionContext(
            PrepareExecutionContextCommand(
                draft = confirmedDraft(material, personal, RUN_ID),
                preparedAt = 200L,
            ),
        ).successValue()
        val command = runtimeCommand(prepared.usage)

        val first = repository.createExecutionRuntime(command)
        val repeated = repository.createExecutionRuntime(command)
        val beforeEdit = repository.listRunContextUsage(RUN_ID).successValue()
        repository.updateMaterial(
            UpdateMaterialCommand(
                id = material.id,
                title = "资料新标题",
                sourceType = material.sourceType,
                sourceLocator = material.sourceLocator,
                content = "资料当前版本已变化",
                sourcePublishedAt = material.sourcePublishedAt,
                sourceCapturedAt = material.sourceCapturedAt,
                sensitive = false,
                expectedUpdatedAt = material.updatedAt,
                updatedAt = 300L,
            ),
        ).successValue()
        val afterEdit = repository.listRunContextUsage(RUN_ID).successValue()
        val conflictingUsage = prepared.usage.copy(
            materials = prepared.usage.materials.map { it.copy(contentSnapshot = "冲突正文") },
        )
        val conflict = repository.createExecutionRuntime(command.copy(contextUsage = conflictingUsage))

        assertFalse((first as RepositoryResult.Success).idempotent)
        assertTrue((repeated as RepositoryResult.Success).idempotent)
        assertEquals(2, beforeEdit.size)
        assertEquals(beforeEdit, afterEdit)
        assertEquals(
            "资料确认摘录",
            beforeEdit.first { it.sourceType == ContextSourceType.MATERIAL }.content,
        )
        assertEquals(
            "跨议题背景",
            beforeEdit.first { it.sourceType == ContextSourceType.PERSONAL_CONTEXT }.content,
        )
        assertTrue(beforeEdit.all { it.networkAllowed })
        assertTrue(beforeEdit.first { it.sourceType == ContextSourceType.PERSONAL_CONTEXT }.sensitive)
        assertTrue(conflict.failureError() is RepositoryError.IdempotencyConflict)
        assertEquals(0, foreignKeyViolations())
    }

    @Test
    fun purgeAnonymizesCurrentAndHistoricalContentWithoutBreakingRelations() = runBlocking {
        saveIssue()
        val material = repository.createMaterial(materialCommand(sensitive = true)).successValue()
        val prepared = repository.prepareExecutionContext(
            PrepareExecutionContextCommand(
                draft = confirmedDraft(material = material, runId = RUN_ID),
                preparedAt = 200L,
            ),
        ).successValue()
        repository.createExecutionRuntime(runtimeCommand(prepared.usage)).successValue()

        val deleted = repository.changeMaterialLifecycle(
            ChangeMaterialLifecycleCommand(
                materialId = material.id,
                expectedUpdatedAt = material.updatedAt,
                target = ContextSourceLifecycle.DELETED,
                changedAt = 300L,
            ),
        ).successValue()
        val impact = repository.getMaterialPurgeImpact(material.id).successValue()
        val requested = repository.changeMaterialLifecycle(
            ChangeMaterialLifecycleCommand(
                materialId = material.id,
                expectedUpdatedAt = deleted.updatedAt,
                target = ContextSourceLifecycle.PURGE_REQUESTED,
                changedAt = 400L,
            ),
        ).successValue()
        val purged = repository.purgeMaterial(
            PurgeMaterialCommand(
                materialId = material.id,
                expectedUpdatedAt = requested.updatedAt,
                confirmedAt = 500L,
            ),
        ).successValue()
        val history = repository.listRunContextUsage(RUN_ID).successValue().single()

        assertEquals(1, impact.issueCount)
        assertEquals(1, impact.stageCount)
        assertEquals(1, impact.usageSnapshotCount)
        assertEquals(1, impact.runCount)
        assertEquals(ContextSourceLifecycle.PURGED, purged.lifecycle)
        assertEquals("", purged.title)
        assertEquals("", purged.content)
        assertEquals("", purged.contentHash)
        assertFalse(purged.sensitive)
        assertEquals(SnapshotContentState.PURGED, history.contentState)
        assertNull(history.title)
        assertNull(history.content)
        assertNull(history.contentHash)
        assertFalse(history.networkAllowed)
        assertFalse(history.sensitive)
        assertEquals(0, foreignKeyViolations())
    }

    private suspend fun saveIssue() {
        repository.saveIssue(
            SaveIssueCommand(
                issueId = ISSUE_ID,
                title = "议题",
                initialStageId = STAGE_ID,
                initialStageTitle = "阶段",
                initialObjective = "目标",
                createdAt = 10L,
            ),
        ).successValue()
    }

    private fun materialCommand(sensitive: Boolean = false): CreateMaterialCommand =
        CreateMaterialCommand(
            id = MATERIAL_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            title = "资料标题",
            sourceType = "note",
            sourceLocator = "local://note",
            content = "资料全文包含资料确认摘录",
            sourcePublishedAt = 20L,
            sourceCapturedAt = 90L,
            sensitive = sensitive,
            createdAt = 100L,
        )

    private fun confirmedDraft(
        material: Material,
        personal: PersonalContext? = null,
        runId: String,
    ): ContextSelectionDraft {
        val items = buildList {
            add(
                ConfirmedContextItem(
                    sourceType = ContextSourceType.MATERIAL,
                    sourceId = material.id,
                    title = material.title,
                    sourceKind = material.sourceType,
                    sourceLocator = material.sourceLocator,
                    sourcePublishedAt = material.sourcePublishedAt,
                    sourceCapturedAt = material.sourceCapturedAt,
                    content = "资料确认摘录",
                    contentHash = ContextContentHasher.hash("资料确认摘录"),
                    expectedSourceHash = material.contentHash,
                    expectedSourceUpdatedAt = material.updatedAt,
                    confirmationOrder = 0,
                    userConfirmedAt = 180L,
                    networkAllowed = true,
                    sensitive = material.sensitive,
                    sensitiveConfirmed = material.sensitive,
                ),
            )
            if (personal != null) {
                add(
                    ConfirmedContextItem(
                        sourceType = ContextSourceType.PERSONAL_CONTEXT,
                        sourceId = personal.id,
                        title = personal.title,
                        content = personal.content,
                        contentHash = personal.contentHash,
                        expectedSourceHash = personal.contentHash,
                        expectedSourceUpdatedAt = personal.updatedAt,
                        confirmationOrder = 1,
                        userConfirmedAt = 181L,
                        networkAllowed = true,
                        sensitive = personal.sensitive,
                        sensitiveConfirmed = personal.sensitive,
                    ),
                )
            }
        }
        return ContextSelectionDraft(
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            runId = runId,
            baseContextCharacters = 100,
            items = items,
            confirmed = true,
        )
    }

    private fun runtimeCommand(usage: ContextUsageWriteSet): CreateExecutionRuntimeCommand {
        val run = ExecutionRunEntity(
            id = RUN_ID,
            issueId = ISSUE_ID,
            stageId = STAGE_ID,
            triggerMessageId = null,
            idempotencyKey = "context-runtime-key",
            retryOfRunId = null,
            createdAt = 220L,
            updatedAt = 220L,
            actualModelId = "gemini-3.6-flash",
            actualThinkingLevel = ExecutionThinkingLevel.MEDIUM,
            thinkingLevelSource = ExecutionThinkingSource.AUTO_ROUTED,
        )
        val participant = ExecutionParticipantSnapshotEntity(
            id = "$RUN_ID-participant-0",
            runId = RUN_ID,
            sourceType = "official_skill",
            sourceId = "skill-a",
            displayName = "Skill A",
            avatar = "A",
            skillAssetPath = "skills/skill-a/SKILL.md",
            systemPrompt = "System",
            configurationJson = "{}",
            defaultResponsibility = "",
            position = 0,
            createdAt = 220L,
        )
        return CreateExecutionRuntimeCommand(
            run = run,
            participants = listOf(participant),
            budgetRootRunId = RUN_ID,
            budget = ExecutionRuntimeBudgetConfig(),
            contextUsage = usage,
        )
    }

    private fun foreignKeyViolations(): Int = database.openHelper.writableDatabase
        .query("PRAGMA foreign_key_check")
        .use { it.count }

    private fun <T> RepositoryResult<T>.successValue(): T =
        (this as RepositoryResult.Success<T>).value

    private fun RepositoryResult<*>.failureError(): RepositoryError =
        (this as RepositoryResult.Failure).error

    private companion object {
        const val ISSUE_ID = "material-context-issue"
        const val STAGE_ID = "material-context-stage"
        const val MATERIAL_ID = "material-1"
        const val PERSONAL_ID = "personal-1"
        const val RUN_ID = "material-context-run"
    }
}
