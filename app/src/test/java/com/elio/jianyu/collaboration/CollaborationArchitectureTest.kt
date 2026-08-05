package com.elio.jianyu.collaboration

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollaborationArchitectureTest {
    private val repositoryRoot: File by lazy {
        generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/java").isDirectory }
    }

    @Test
    fun collaborationLayerDoesNotAccessDaoRetrofitOrApiKey() {
        val source = sourceTree("app/src/main/java/com/elio/jianyu/collaboration")

        assertFalse(source.contains("JianyuRepositoryDao"))
        assertFalse(source.contains("CollaborationDao"))
        assertFalse(source.contains("Retrofit"))
        assertFalse(source.contains("GeminiApi"))
        assertFalse(source.contains("EncryptedApiKeyStore"))
        assertFalse(source.contains("RoundtableOrchestrator"))
    }

    @Test
    fun collaborationUsesTheSingleExecutionCoordinator() {
        val source = source("app/src/main/java/com/elio/jianyu/collaboration/IssueCollaborationCoordinator.kt")

        assertTrue(source.contains("ExecutionRunCoordinator"))
        assertTrue(source.contains("startPrepared("))
        assertFalse(source.contains("networkGateway"))
        assertFalse(source.contains("appendMessage("))
        assertFalse(source.contains("consumeBudget("))
        assertFalse(source.contains("ConcurrentHashMap"))
    }

    @Test
    fun synthesisRelationshipNeverUsesRetryField() {
        val coordinator = source(
            "app/src/main/java/com/elio/jianyu/collaboration/IssueCollaborationCoordinator.kt",
        )

        assertTrue(coordinator.contains("parentRunId = session.responseRunId"))
        assertFalse(coordinator.contains("retryOfRunId = session.responseRunId"))
        assertTrue(coordinator.contains("CROSS_DISCUSSION_SYNTHESIS"))
    }

    @Test
    fun collaborationUiDoesNotAccessDaoOrNetwork() {
        val source = sourceTree("app/src/main/java/com/elio/jianyu/ui/screens/execution")

        assertFalse(source.contains("JianyuRepositoryDao"))
        assertFalse(source.contains("CollaborationDao"))
        assertFalse(source.contains("Retrofit"))
        assertFalse(source.contains("InteractionStreamingClient"))
        assertFalse(source.contains("EncryptedApiKeyStore"))
    }

    @Test
    fun crossDiscussionCoordinatorHasNoAutomaticMultiRoundLoopOrVoting() {
        val source = source(
            "app/src/main/java/com/elio/jianyu/collaboration/IssueCollaborationCoordinator.kt",
        )

        assertFalse(source.contains("while (true)"))
        assertFalse(source.contains("MAX_DISCUSSION_ROUNDS"))
        assertFalse(source.contains("voteCount"))
        assertTrue(source.contains("autoStartSynthesisOnFullSuccess"))
    }

    private fun source(relativePath: String): String = File(repositoryRoot, relativePath).readText()

    private fun sourceTree(relativePath: String): String = File(repositoryRoot, relativePath)
        .walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .joinToString("\n") { it.readText() }
}
