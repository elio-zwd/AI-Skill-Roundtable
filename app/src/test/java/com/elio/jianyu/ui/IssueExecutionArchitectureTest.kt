package com.elio.jianyu.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueExecutionArchitectureTest {
    private val repositoryRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }

    @Test
    fun executionUiDoesNotAccessDaoOrNetwork() {
        val source = sourceTree("app/src/main/java/com/elio/jianyu/ui/screens/execution")

        assertFalse(source.contains("Dao"))
        assertFalse(source.contains("Retrofit"))
        assertFalse(source.contains("InteractionStreamingClient"))
        assertFalse(source.contains("GeminiApi"))
        assertFalse(source.contains("ApiKeyScheduler"))
    }

    @Test
    fun stageResultLayerDoesNotOwnNetworkCoordinatorDaoOrBudgetState() {
        val service = sourceTree("app/src/main/java/com/elio/jianyu/result")
        val ui = sourceTree("app/src/main/java/com/elio/jianyu/ui/screens/result")
        val combined = "$service\n$ui"

        assertFalse(combined.contains("JianyuRepositoryDao"))
        assertFalse(combined.contains("Retrofit"))
        assertFalse(combined.contains("InteractionStreamingClient"))
        assertFalse(combined.contains("InteractionExecutionNetworkGateway"))
        assertFalse(combined.contains("ExecutionRunCoordinator"))
        assertFalse(combined.contains("IssueCollaborationCoordinator"))
        assertFalse(combined.contains("RequestBudgetTracker"))
        assertFalse(combined.contains("consumeExecutionBudget"))
    }

    @Test
    fun screenAndComponentsOnlyRenderState() {
        val screen = source("app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionScreen.kt")
        val components = source(
            "app/src/main/java/com/elio/jianyu/ui/screens/execution/IssueExecutionComponents.kt"
        )

        listOf(screen, components).forEach { source ->
            assertFalse(source.contains("JianyuRepository"))
            assertFalse(source.contains("ExecutionRunCoordinator"))
            assertFalse(source.contains("viewModelScope"))
            assertFalse(source.contains("LaunchedEffect"))
        }
    }

    @Test
    fun coordinatorDoesNotDependOnComposeDaoOrLegacyGateway() {
        val coordinator = source(
            "app/src/main/java/com/elio/jianyu/execution/ExecutionRunCoordinator.kt"
        )

        assertFalse(coordinator.contains("androidx.compose"))
        assertFalse(coordinator.contains("JianyuRepositoryDao"))
        assertFalse(coordinator.contains("RoundtableOrchestrator"))
        assertFalse(coordinator.contains("RoundtableDatabaseGateway"))
        assertFalse(coordinator.contains("ChatRepository"))
    }

    @Test
    fun issueDeepLinkUsesSingleCoordinatorAndSharedStageResultService() {
        val app = source("app/src/main/java/com/elio/jianyu/ui/App.kt")
        val runtime = source("app/src/main/java/com/elio/jianyu/JianyuAppRuntime.kt")

        assertTrue(app.contains("IssueExecutionRoute("))
        assertFalse(app.contains("IssueRecoveryRoute("))
        assertTrue(app.contains("stageResultService = appRuntime.stageResultService"))
        assertTrue(runtime.contains("val executionCoordinator: ExecutionRunCoordinator?"))
        assertTrue(runtime.contains("val stageResultService: StageResultService"))
        assertEqualsOnce(runtime, "ExecutionRunCoordinator(")
        assertEqualsOnce(runtime, "StageResultService(repository)")
    }

    @Test
    fun executionPackageHasNoSecondPersistentBudgetMap() {
        val source = sourceTree("app/src/main/java/com/elio/jianyu/execution")

        assertFalse(source.contains("ConcurrentHashMap<Long, RequestBudgetTracker>"))
        assertFalse(source.contains("ConcurrentHashMap<String, RequestBudgetTracker>"))
        assertTrue(source.contains("recordApiCall("))
        assertTrue(source.contains("execution_run_budgets") || source.contains("ExecutionRunBudget"))
    }

    private fun assertEqualsOnce(source: String, token: String) {
        val count = source.windowed(token.length).count { it == token }
        assertTrue("$token 应只在 App 组合层构造一次，实际 $count 次", count == 1)
    }

    private fun source(relativePath: String): String = File(repositoryRoot, relativePath).readText()

    private fun sourceTree(relativePath: String): String = File(repositoryRoot, relativePath)
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .joinToString("\n") { it.readText() }
}
