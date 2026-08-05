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

        assertFalse(source.contains("import com.elio.jianyu.data.JianyuRepositoryDao"))
        assertFalse(source.contains("import com.elio.jianyu.data.CollaborationDao"))
        assertFalse(source.contains("import retrofit2."))
        assertFalse(source.contains("import com.elio.jianyu.network.GeminiApi"))
        assertFalse(source.contains("import com.elio.jianyu.network.EncryptedApiKeyStore"))
        assertFalse(source.contains("import com.elio.jianyu.roundtable.RoundtableOrchestrator"))
    }

    @Test
    fun collaborationUsesTheSingleExecutionCoordinator() {
        val source = source("app/src/main/java/com/elio/jianyu/collaboration/IssueCollaborationCoordinator.kt")

        assertTrue(source.contains("ExecutionRunCoordinator"))
        assertTrue(source.contains("startPrepared("))
        assertFalse(source.contains("networkGateway ="))
        assertFalse(source.contains("persistence.appendMessage("))
        assertFalse(source.contains("persistence.consumeBudget("))
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

        assertFalse(source.contains("import com.elio.jianyu.data.JianyuRepositoryDao"))
        assertFalse(source.contains("import com.elio.jianyu.data.CollaborationDao"))
        assertFalse(source.contains("import retrofit2."))
        assertFalse(source.contains("import com.elio.jianyu.network.InteractionStreamingClient"))
        assertFalse(source.contains("import com.elio.jianyu.network.EncryptedApiKeyStore"))
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
