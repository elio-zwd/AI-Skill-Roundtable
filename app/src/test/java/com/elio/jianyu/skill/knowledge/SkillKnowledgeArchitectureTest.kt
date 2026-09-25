package com.elio.jianyu.skill.knowledge

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillKnowledgeArchitectureTest {
    private val projectRoot = findProjectRoot()

    @Test
    fun productionNoLongerUsesSummaryBrokerForLocalKnowledgeSelection() {
        val viewModel = projectRoot
            .resolve("app/src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModel.kt")
            .readText()
        val loader = projectRoot
            .resolve("app/src/main/java/com/elio/jianyu/skill/SkillLoader.kt")
            .readText()
        val provider = projectRoot
            .resolve("app/src/main/java/com/elio/jianyu/network/AiProvider.kt")
            .readText()

        assertFalse(viewModel.contains("skills_summaries"))
        assertFalse(viewModel.contains("loadSkillsSummariesOnce"))
        assertFalse(viewModel.contains("selectedFiles"))
        assertFalse(loader.contains("loadSelectedFiles"))
        assertTrue(viewModel.contains("skillKnowledgeRetriever?.retrieve"))
        assertTrue(viewModel.contains("SkillKnowledgeContextFormatter.format"))
        assertTrue(provider.contains("MATERIAL_BROKER(\"联网决策\""))
    }

    @Test
    fun roundtableLocalKnowledgeIsIndependentFromWebDecisionModes() {
        val viewModel = projectRoot
            .resolve("app/src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModel.kt")
            .readText()

        val retrievalIndex = viewModel.indexOf("skillKnowledgeRetriever?.retrieve")
        val modeIndex = viewModel.indexOf("val mode = _searchMode.value")
        val brokerGuardIndex = viewModel.indexOf("if (mode != SearchMode.OFF)")
        assertTrue(retrievalIndex >= 0)
        assertTrue(modeIndex > retrievalIndex)
        assertTrue(brokerGuardIndex > modeIndex)

        assertTrue(viewModel.contains("SearchMode.AUTO ->"))
        assertTrue(viewModel.contains("SearchMode.ON ->"))
        assertTrue(viewModel.contains("SearchMode.OFF -> error("))
        assertTrue(viewModel.contains("if (mode == SearchMode.ON)"))
        assertTrue(viewModel.contains("finalQueries.add(retrievalQuery.ifBlank"))
        assertTrue(viewModel.contains("operationName = \"WebDecision\""))
        assertFalse(viewModel.contains("selectedFiles"))
        assertFalse(viewModel.contains("skills_summaries"))
    }

    @Test
    fun explicitCrossSkillSelectionUsesDedicatedConfirmedContextWithoutPrivacyFlags() {
        val viewModel = projectRoot
            .resolve("app/src/main/java/com/elio/jianyu/viewmodel/RoundtableViewModel.kt")
            .readText()

        val actionStart = viewModel.indexOf("fun addSkillKnowledgeToCurrentConversation(")
        assertTrue(actionStart >= 0)
        val actionEnd = viewModel.indexOf(
            "fun currentActiveConversationContextSelections()",
            startIndex = actionStart,
        )
        assertTrue(actionEnd > actionStart)
        val action = viewModel.substring(actionStart, actionEnd)

        assertTrue(action.contains("sourceType = ContextSourceType.SKILL_KNOWLEDGE"))
        assertTrue(action.contains("sourceKind = selection.skillId"))
        assertTrue(action.contains("sourceLocator = selection.relativePath"))
        assertTrue(action.contains("networkAllowed = true"))
        assertTrue(action.contains("sensitive = false"))
        assertTrue(action.contains("sensitiveConfirmed = false"))

        val retrievalCall = viewModel.substring(
            viewModel.indexOf("skillKnowledgeRetriever?.retrieve"),
            viewModel.indexOf("val configuration =", viewModel.indexOf("skillKnowledgeRetriever?.retrieve")),
        )
        assertTrue(retrievalCall.contains("ownerSkillId = character.id"))
        assertFalse(retrievalCall.contains("selection.skillId"))
    }

    @Test
    fun collaborationRuntimePersistsValidatesAndComparesSkillKnowledgeUsage() {
        val collaboration = projectRoot
            .resolve("app/src/main/java/com/elio/jianyu/data/CollaborationRepositoryComponent.kt")
            .readText()

        assertTrue(collaboration.contains("insertSkillKnowledgeUsages(sortedUsage.skillKnowledge)"))
        assertTrue(collaboration.contains("getSkillKnowledgeUsagesForRun(runId) == sorted.skillKnowledge"))
        assertTrue(collaboration.contains("val skillKnowledgeValid = usage.skillKnowledge.all"))
    }

    @Test
    fun collaborationRetryClonesExplicitSkillKnowledgeUsage() {
        val retry = projectRoot
            .resolve("app/src/main/java/com/elio/jianyu/data/CollaborationRetryRepositoryComponent.kt")
            .readText()

        assertTrue(retry.contains("getSkillKnowledgeUsagesForRun(sourceRunId)"))
        assertTrue(retry.contains("insertSkillKnowledgeUsages(skillKnowledge)"))
        assertTrue(retry.contains("runId = targetRunId"))
    }

    @Test
    fun collaborationRebindKeepsExplicitSkillKnowledgeUsage() {
        val coordinator = projectRoot
            .resolve("app/src/main/java/com/elio/jianyu/collaboration/IssueCollaborationCoordinator.kt")
            .readText()

        assertTrue(coordinator.contains("skillKnowledge = source.skillKnowledge.mapIndexed"))
        assertTrue(coordinator.contains("runId = runId"))
    }

    @Test
    fun legacySummaryAssetsAndGeneratorsAreRemoved() {
        assertFalse(projectRoot.resolve("app/src/main/assets/skills_summaries.json").exists())
        assertFalse(projectRoot.resolve("workspace/tools/generate_summaries.py").exists())
        assertFalse(projectRoot.resolve("workspace/tools/generate_summaries_ai.py").exists())
    }

    private fun findProjectRoot(): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        while (current != null) {
            if (current.resolve("app/src/main").isDirectory) return current
            current = current.parentFile
        }
        error("无法定位项目根目录")
    }
}
