package com.elio.jianyu.lifecycle

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueLifecycleWriteGateArchitectureTest {
    private val repositoryRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }

    @Test
    fun publicRepositoryRoutesEveryIssueBusinessWriteThroughLifecycleGate() {
        val facade = source("app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt")
        val gated = source(
            "app/src/main/java/com/elio/jianyu/data/LifecycleGatedRepositoryComponent.kt",
        )

        listOf(
            "createStage(command)" to "ADVANCE_STAGE",
            "undoLatestUnrunStage(issueId, stageId)" to "ADVANCE_STAGE",
            "advanceIssue(command)" to "ADVANCE_STAGE",
            "createExecutionRun(command)" to "CREATE_RUN",
            "createExecutionRuntime(command)" to "CREATE_RUN",
            "recoverInterruptedExecution(command)" to "CREATE_RUN",
            "createDirectedInteraction(command)" to "DIRECTED_RESPONSE",
            "createCrossDiscussionResponse(command)" to "CROSS_DISCUSSION",
            "createCrossDiscussionSynthesis(command)" to "CROSS_DISCUSSION",
            "createCollaborationRetry(command)" to "CROSS_DISCUSSION",
            "appendDomainMessage(command)" to "CREATE_RUN",
            "updatePendingDomainMessage(command)" to "CREATE_RUN",
            "saveStageDraft(command)" to "SAVE_DRAFT",
            "abandonStageDraft(issueId, stageId)" to "SAVE_DRAFT",
            "confirmArtifact(command)" to "CONFIRM_ARTIFACT",
            "recordMaterialUsage(entity)" to "RECORD_CONTEXT_USAGE",
            "recordPersonalContextUsage(entity)" to "RECORD_CONTEXT_USAGE",
            "prepareExecutionContext(command)" to "RECORD_CONTEXT_USAGE",
            "moveIssueToTrash(issueId, changedAt)" to "MOVE_TO_TRASH",
            "restoreIssueFromTrash(issueId, changedAt)" to "RESTORE_FROM_TRASH",
        ).forEach { (facadeCall, action) ->
            assertTrue("门面缺少生命周期委托：$facadeCall", facade.contains("lifecycleWrites.$facadeCall"))
            assertTrue("门禁组件缺少动作：$action", gated.contains("IssueWriteAction.$action"))
        }
        assertTrue(gated.contains("terminal"))
        assertTrue(gated.contains("ExecutionRunStatus.STOPPED"))
        assertTrue(gated.contains("ExecutionRunStatus.FAILED"))
    }

    @Test
    fun stageResultAndExecutionCoordinatorsCannotBypassRepositoryGate() {
        val stageResult = source("app/src/main/java/com/elio/jianyu/result/StageResultService.kt")
        val execution = source("app/src/main/java/com/elio/jianyu/execution/ExecutionRunCoordinator.kt")
        val collaboration = source(
            "app/src/main/java/com/elio/jianyu/collaboration/IssueCollaborationCoordinator.kt",
        )

        assertTrue(stageResult.contains("repository.saveStageDraft"))
        assertTrue(stageResult.contains("repository.confirmArtifact"))
        assertFalse(stageResult.contains("jianyuRepositoryDao"))
        assertTrue(execution.contains("persistence.createRuntime"))
        assertFalse(execution.contains("jianyuRepositoryDao"))
        assertTrue(collaboration.contains("repository.createDirectedInteraction"))
        assertTrue(collaboration.contains("repository.createCrossDiscussionResponse"))
        assertTrue(collaboration.contains("repository.createCrossDiscussionSynthesis"))
        assertFalse(collaboration.contains("jianyuRepositoryDao"))
    }

    @Test
    fun productionAudioGenerationUsesLifecycleAwareRepositoryAndBlocksLateSuccess() {
        val runtime = source("app/src/main/java/com/elio/jianyu/audio/runtime/JianyuAudioRuntime.kt")
        val decorator = source(
            "app/src/main/java/com/elio/jianyu/data/LifecycleAwareAudioAssetRepository.kt",
        )

        assertTrue(runtime.contains("LifecycleAwareAudioAssetRepository"))
        assertTrue(runtime.contains("repository = generationRepository"))
        assertTrue(decorator.contains("IssueWriteAction.GENERATE_AUDIO"))
        assertTrue(decorator.contains("markAvailable"))
        assertTrue(decorator.contains("if (!allows(asset.source.issueId"))
        assertTrue(decorator.contains("return false"))
        assertTrue(decorator.contains("markCanceled"))
        assertTrue(decorator.contains("markFailed"))
    }

    @Test
    fun legacyLifecycleShortcutsCannotCreateIncompleteV12Facts() {
        val facade = source("app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt")

        assertTrue(facade.contains("archive_event_required"))
        assertTrue(facade.contains("resume_event_required"))
        assertTrue(facade.contains("purge_operation_required"))
        assertFalse(facade.contains("lifecycleRecovery.archiveIssue"))
        assertFalse(facade.contains("lifecycleRecovery.restoreIssue"))
        assertFalse(facade.contains("lifecycleRecovery.requestIssuePurge"))
    }

    @Test
    fun purgeWorkerAndFileCleanerPreserveStrictBoundaries() {
        val worker = source("app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeWorker.kt")
        val scheduler = source("app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeScheduler.kt")
        val fileCleaner = source("app/src/main/java/com/elio/jianyu/lifecycle/IssuePurgeFileCleaner.kt")
        val databaseCleaner = source(
            "app/src/main/java/com/elio/jianyu/data/IssuePurgeDatabaseCleaner.kt",
        )

        assertTrue(worker.contains("purge_operation_id"))
        assertFalse(worker.contains("issue_title"))
        assertFalse(worker.contains("impact_hash"))
        assertTrue(scheduler.contains("ExistingWorkPolicy.KEEP"))
        assertTrue(scheduler.contains("keyValueMap.keys != setOf"))
        assertTrue(fileCleaner.contains("reconcileFilesForIssue"))
        assertTrue(fileCleaner.contains("requestDelete"))
        assertTrue(fileCleaner.contains("removeCommitted"))
        assertTrue(fileCleaner.contains("removeTemporaryFilesForAsset"))
        assertFalse(fileCleaner.contains("deleteRecursively"))
        assertFalse(fileCleaner.contains("sourceLocator"))
        assertTrue(databaseCleaner.contains("databaseTransaction"))
        assertTrue(databaseCleaner.contains("PRAGMA foreign_key_check"))
        assertFalse(databaseCleaner.contains("foreign_keys = OFF"))
    }

    private fun source(relativePath: String): String = File(repositoryRoot, relativePath).readText()
}
