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
