package com.elio.jianyu.home

import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogLoadResult
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeExecutableSkillIntegrationTest {
    private val catalog: OfficialSkillCatalog by lazy {
        val base = assetFile("official_skill_catalog_v1.json").readText()
        val publication = assetFile("official_skill_execution_batch_v1.json").readText()
        when (val result = OfficialSkillCatalogParser.parse(base, publication)) {
            is OfficialSkillCatalogLoadResult.Success -> result.catalog
            is OfficialSkillCatalogLoadResult.Failure -> error(result.message)
        }
    }

    @Test
    fun productionCatalogProvidesRealSingleSkillRecommendation() {
        val outcome = HomeRecommendationPolicy.recommend(
            catalog = catalog,
            request = HomeRecommendationRequest(
                question = "请让学习规划师根据我的目标和时间制定学习计划",
                directions = setOf(ValueDirection.REALITY_SUPPORT),
            ),
        )

        assertTrue(outcome is HomeRecommendationOutcome.Ready)
        val recommendation = (outcome as HomeRecommendationOutcome.Ready).recommendation
        assertEquals(RecommendationMode.SINGLE, recommendation.mode)
        assertEquals(1, recommendation.selectedSkills.size)
        assertTrue(recommendation.selectedSkills.single().executable)
        assertEquals("study-planner", recommendation.selectedSkills.single().skillId)
        assertTrue(HomeRecommendationPolicy.validateForStart(catalog, recommendation).isEmpty())
    }

    @Test
    fun productionCatalogProvidesAtLeastTwoRealMembersForMultiSkillRecommendation() {
        val outcome = HomeRecommendationPolicy.recommend(
            catalog = catalog,
            request = HomeRecommendationRequest(
                question = "请让调研与事实核查助手核查证据，再由会议行动助手整理行动并形成汇报方案",
                directions = setOf(
                    ValueDirection.REALITY_SUPPORT,
                    ValueDirection.THINKING_EXPANSION,
                ),
            ),
        )

        assertTrue(outcome is HomeRecommendationOutcome.Ready)
        val recommendation = (outcome as HomeRecommendationOutcome.Ready).recommendation
        assertEquals(RecommendationMode.MULTI, recommendation.mode)
        assertEquals(2, recommendation.selectedSkills.size)
        assertEquals(2, recommendation.selectedSkills.map { it.skillId }.distinct().size)
        assertTrue(recommendation.selectedSkills.all { it.executable })
        assertTrue(HomeRecommendationPolicy.validateForStart(catalog, recommendation).isEmpty())
    }

    @Test
    fun nonExecutableCatalogCandidateStillCannotEnterFinalStart() {
        val outcome = HomeRecommendationPolicy.recommend(
            catalog = catalog,
            request = HomeRecommendationRequest(
                question = "请用张雪峰视角比较教育选择，再由学习规划师形成学习计划",
                directions = setOf(ValueDirection.REALITY_SUPPORT),
            ),
        )
        val recommendation = (outcome as HomeRecommendationOutcome.Ready).recommendation
        val nonExecutable = recommendation.skills.firstOrNull { it.skillId == "zhang_xuefeng" }
            ?: error("张雪峰只可查看候选必须出现在推荐结果")
        val tampered = recommendation.copy(
            skills = recommendation.skills.map { item ->
                item.copy(selected = item.skillId == nonExecutable.skillId)
            },
        )

        val errors = HomeRecommendationPolicy.validateForStart(catalog, tampered)

        assertTrue(HomeRecommendationValidationError.NON_EXECUTABLE_SKILL in errors)
    }

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: error("找不到 assets/$path")
}
