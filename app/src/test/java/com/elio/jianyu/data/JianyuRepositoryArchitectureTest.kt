package com.elio.jianyu.data

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JianyuRepositoryArchitectureTest {
    private val repositoryRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }

    @Test
    fun roomUsesVersionThirteenThinkingSchema() {
        val databaseSource = source("app/src/main/java/com/elio/jianyu/data/RoundtableDatabase.kt")
        val coreDomainSource = source("app/src/main/java/com/elio/jianyu/data/CoreDomain.kt")
        val executionMigrationSource = source(
            "app/src/main/java/com/elio/jianyu/data/ExecutionRuntimeMigration.kt",
        )
        val contextMigrationSource = source(
            "app/src/main/java/com/elio/jianyu/data/MaterialContextMigration.kt",
        )
        val collaborationMigrationSource = source(
            "app/src/main/java/com/elio/jianyu/data/CollaborationMigration.kt",
        )
        val advancementMigrationSource = source(
            "app/src/main/java/com/elio/jianyu/data/StageAdvancementMigration.kt",
        )
        val lifecycleMigrationSource = source(
            "app/src/main/java/com/elio/jianyu/data/IssueLifecycleV12Migration.kt",
        )

        assertTrue(databaseSource.contains("version = 13"))
        assertFalse(databaseSource.contains("MIGRATION_12_13"))
        assertTrue(databaseSource.contains("MIGRATION_7_8"))
        assertTrue(databaseSource.contains("MIGRATION_8_9"))
        assertTrue(databaseSource.contains("MIGRATION_9_10"))
        assertTrue(databaseSource.contains("MIGRATION_10_11"))
        assertTrue(databaseSource.contains("MIGRATION_11_12"))
        assertTrue(databaseSource.contains("ExecutionParticipantStateEntity::class"))
        assertTrue(databaseSource.contains("ExecutionRunBudgetEntity::class"))
        assertTrue(databaseSource.contains("CrossDiscussionSessionEntity::class"))
        assertTrue(databaseSource.contains("ExecutionMessageUsageSnapshotEntity::class"))
        assertTrue(databaseSource.contains("StageAdvancementEntity::class"))
        assertTrue(databaseSource.contains("StageAdvancementSkillMemberEntity::class"))
        assertTrue(databaseSource.contains("IssueArchiveEventEntity::class"))
        assertTrue(databaseSource.contains("IssueResumeEventEntity::class"))
        assertTrue(databaseSource.contains("IssueRelationEntity::class"))
        assertTrue(databaseSource.contains("IssuePurgeOperationEntity::class"))
        assertTrue(coreDomainSource.contains("defaultThinkingPolicy"))
        assertTrue(coreDomainSource.contains("actualThinkingLevel"))
        assertTrue(executionMigrationSource.contains("Migration(7, 8)"))
        assertTrue(executionMigrationSource.contains("execution_participant_states"))
        assertTrue(executionMigrationSource.contains("execution_run_budgets"))
        assertTrue(contextMigrationSource.contains("Migration(8, 9)"))
        assertTrue(contextMigrationSource.contains("networkAllowed"))
        assertTrue(contextMigrationSource.contains("sensitive"))
        assertTrue(collaborationMigrationSource.contains("Migration(9, 10)"))
        assertTrue(collaborationMigrationSource.contains("cross_discussion_sessions"))
        assertTrue(collaborationMigrationSource.contains("execution_message_usage_snapshots"))
        assertTrue(advancementMigrationSource.contains("Migration(10, 11)"))
        assertTrue(advancementMigrationSource.contains("stage_advancements"))
        assertTrue(advancementMigrationSource.contains("stage_advancement_skill_members"))
        assertTrue(lifecycleMigrationSource.contains("Migration(11, 12)"))
        assertTrue(lifecycleMigrationSource.contains("issue_archive_events"))
        assertTrue(lifecycleMigrationSource.contains("issue_resume_events"))
        assertTrue(lifecycleMigrationSource.contains("issue_relations"))
        assertTrue(lifecycleMigrationSource.contains("issue_purge_operations"))
    }

    @Test
    fun viewModelsAndUiCannotAccessDomainDaosDirectly() {
        val guardedRoots = listOf(
            File(repositoryRoot, "app/src/main/java/com/elio/jianyu/viewmodel"),
            File(repositoryRoot, "app/src/main/java/com/elio/jianyu/ui"),
        )

        val offenders = guardedRoots
            .filter(File::exists)
            .flatMap { root -> root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .filter { file ->
                val text = file.readText()
                text.contains("coreDomainDao()") ||
                    text.contains("resourceLifecycleDao()") ||
                    text.contains("jianyuRepositoryDao()") ||
                    text.contains("collaborationDao()") ||
                    text.contains("stageAdvancementDao()") ||
                    text.contains("issueLifecycleV12Dao()")
            }

        assertTrue(
            "发现生产调用方绕过 Repository：${offenders.map { it.relativeTo(repositoryRoot) }}",
            offenders.isEmpty(),
        )
    }

    @Test
    fun repositoryUsesSingleTransactionCoordinatorWithoutNetworkOrFileDeletion() {
        val transactionSource = source(
            "app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt",
        )
        val repositoryFiles = listOf(
            "app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt",
            "app/src/main/java/com/elio/jianyu/data/IssueExecutionRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/StageAdvancementRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/ExecutionRuntimeRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/CollaborationRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/CollaborationRetryRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/CollaborationRuntimeRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/CrossDiscussionSynthesisRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/PendingMessageRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/ResourceRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/UsageRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/LifecycleRecoveryRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/MaterialContextRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/LifecycleGatedRepositoryComponent.kt",
            "app/src/main/java/com/elio/jianyu/data/RoomIssueLifecycleV12Repository.kt",
            "app/src/main/java/com/elio/jianyu/data/IssuePurgeDatabaseCleaner.kt",
            "app/src/main/java/com/elio/jianyu/data/JianyuRepositoryTransactions.kt",
        ).map(::source)

        assertTrue(transactionSource.contains("withTransaction"))
        assertTrue(transactionSource.contains("catch (error: CancellationException)"))
        assertTrue(
            transactionSource.indexOf("catch (error: CancellationException)") <
                transactionSource.indexOf("catch (error: IllegalStateException)"),
        )
        repositoryFiles.forEach { repositorySource ->
            assertFalse(repositorySource.contains("import com.elio.jianyu.network"))
            assertFalse(repositorySource.contains("import retrofit2."))
            assertFalse(repositorySource.contains("WorkManager"))
            assertFalse(repositorySource.contains("java.io.File"))
            assertFalse(repositorySource.contains(".delete()"))
            assertFalse(repositorySource.contains("deleteRecursively"))
        }
    }

    @Test
    fun publicRepositoryIsFacadeInsteadOfGodObject() {
        val facade = source("app/src/main/java/com/elio/jianyu/data/RoomJianyuRepository.kt")
        val lineCount = facade.lineSequence().count()

        assertTrue("公共 Repository 门面不应重新膨胀，当前行数：$lineCount", lineCount < 360)
        assertTrue(facade.contains("IssueExecutionRepositoryComponent"))
        assertTrue(facade.contains("StageAdvancementRepositoryComponent"))
        assertTrue(facade.contains("ExecutionRuntimeRepositoryComponent"))
        assertTrue(facade.contains("CollaborationRepositoryComponent"))
        assertTrue(facade.contains("CrossDiscussionSynthesisRepositoryComponent"))
        assertTrue(facade.contains("CollaborationRetryRepositoryComponent"))
        assertTrue(facade.contains("PendingMessageRepositoryComponent"))
        assertTrue(facade.contains("ResourceRepositoryComponent"))
        assertTrue(facade.contains("UsageRepositoryComponent"))
        assertTrue(facade.contains("LifecycleRecoveryRepositoryComponent"))
        assertTrue(facade.contains("MaterialContextRepositoryComponent"))
        assertTrue(facade.contains("LifecycleGatedRepositoryComponent"))
        assertFalse(facade.contains("withTransaction"))
    }

    @Test
    fun usageSnapshotWritesHaveOneInternalComponent() {
        val resourceComponent = source(
            "app/src/main/java/com/elio/jianyu/data/ResourceRepositoryComponent.kt",
        )
        val usageComponent = source(
            "app/src/main/java/com/elio/jianyu/data/UsageRepositoryComponent.kt",
        )

        assertFalse(resourceComponent.contains("fun recordMaterialUsage"))
        assertFalse(resourceComponent.contains("fun recordPersonalContextUsage"))
        assertTrue(usageComponent.contains("fun recordMaterialUsage"))
        assertTrue(usageComponent.contains("fun recordPersonalContextUsage"))
    }

    @Test
    fun newDomainMessageInsertUsesAbortAndPendingUsesCompareAndSet() {
        val daoSource = source("app/src/main/java/com/elio/jianyu/data/JianyuRepositoryDao.kt")
        val insertStart = daoSource.indexOf("suspend fun insertDomainMessage")
        val annotationStart = daoSource.lastIndexOf("@Insert", insertStart)
        val annotation = daoSource.substring(annotationStart, insertStart)

        assertTrue(annotation.contains("OnConflictStrategy.ABORT"))
        assertFalse(annotation.contains("OnConflictStrategy.REPLACE"))
        assertTrue(daoSource.contains("suspend fun compareAndSetPendingDomainMessage"))
        assertTrue(daoSource.contains("AND isPending = 1"))
    }

    @Test
    fun legacyChatRepositoryCannotCleanOrExposeDomainCompatibilitySessions() {
        val chatSource = source("app/src/main/java/com/elio/jianyu/data/ChatSession.kt")

        assertTrue(chatSource.contains("isDomainCompatibilitySession"))
        assertTrue(chatSource.contains("Skipped deletion of domain compatibility session"))
        assertTrue(chatSource.contains("id NOT LIKE 'legacy-chat-%'"))
        assertTrue(chatSource.contains("legacyChatSessionId = messages.chatId"))
        assertFalse(chatSource.contains("@Query(\"DELETE FROM messages WHERE isPending = 1\")"))
    }

    private fun source(relativePath: String): String = File(repositoryRoot, relativePath).readText()
}
