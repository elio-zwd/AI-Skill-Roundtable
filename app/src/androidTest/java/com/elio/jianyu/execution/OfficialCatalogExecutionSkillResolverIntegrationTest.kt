package com.elio.jianyu.execution

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogLoadResult
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogParser
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.catalog.createOfficialSkillCatalogRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialCatalogExecutionSkillResolverIntegrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun realCatalogAndAssetsResolveSingleAndMultipleOfficialSkills() = runBlocking {
        val runtimeResult = createOfficialSkillCatalogRuntime(context)
        assertTrue(runtimeResult is OfficialSkillCatalogRuntimeResult.Success)
        val runtime = (runtimeResult as OfficialSkillCatalogRuntimeResult.Success).runtime
        val resolver = OfficialCatalogExecutionSkillResolver(
            context = context,
            catalog = runtime.catalog,
            executionEligibility = runtime.executionEligibility,
        )

        val single = resolver.resolve(
            runId = "run-single",
            selections = listOf(
                ExecutionSkillSelection(
                    officialSkillId = "study-planner",
                    defaultResponsibility = "拆解学习目标和检查节点",
                ),
            ),
            createdAt = 1_000L,
        )
        val multiple = resolver.resolve(
            runId = "run-multiple",
            selections = listOf(
                ExecutionSkillSelection(
                    officialSkillId = "research-fact-checker",
                    defaultResponsibility = "核查事实、来源和时效",
                ),
                ExecutionSkillSelection(
                    officialSkillId = "report-proposal-writer",
                    defaultResponsibility = "形成可编辑汇报草稿",
                ),
            ),
            createdAt = 2_000L,
        )

        assertEquals(1, single.size)
        assertEquals("study-planner", single.single().sourceId)
        assertEquals(0, single.single().position)
        assertEquals("拆解学习目标和检查节点", single.single().defaultResponsibility)
        assertTrue(single.single().systemPrompt.isNotBlank())
        assertEquals(
            listOf("research-fact-checker", "report-proposal-writer"),
            multiple.map { it.sourceId },
        )
        assertEquals(listOf(0, 1), multiple.map { it.position })
        assertTrue(multiple.all { it.systemPrompt.isNotBlank() })
        assertTrue(multiple.all { it.skillAssetPath.endsWith("/SKILL.md") })
        assertTrue(multiple.all { it.configurationJson.contains(it.sourceId) })
    }

    @Test
    fun realResolverRejectsDuplicateUnknownAndHistoricalV1NonExecutableSkills() = runBlocking {
        val runtime = requireNotNull(
            (createOfficialSkillCatalogRuntime(context) as? OfficialSkillCatalogRuntimeResult.Success)
                ?.runtime,
        )
        val resolver = OfficialCatalogExecutionSkillResolver(
            context = context,
            catalog = runtime.catalog,
            executionEligibility = runtime.executionEligibility,
        )
        val historicalV1Catalog = requireNotNull(
            (
                OfficialSkillCatalogParser.loadFromAssets(
                    context = context,
                    executionPublicationAssetPath =
                        OfficialSkillCatalogParser.V1_EXECUTION_PUBLICATION_ASSET_PATH,
                ) as? OfficialSkillCatalogLoadResult.Success
                )?.catalog,
        )
        val historicalV1Resolver = OfficialCatalogExecutionSkillResolver(
            context = context,
            catalog = historicalV1Catalog,
        )

        val duplicate = runCatching {
            resolver.resolve(
                runId = "run-duplicate",
                selections = listOf(
                    ExecutionSkillSelection("study-planner"),
                    ExecutionSkillSelection("study-planner"),
                ),
                createdAt = 1_000L,
            )
        }.exceptionOrNull()
        val unknown = runCatching {
            resolver.resolve(
                runId = "run-unknown",
                selections = listOf(ExecutionSkillSelection("unknown-skill")),
                createdAt = 1_000L,
            )
        }.exceptionOrNull()
        val nonExecutable = runCatching {
            historicalV1Resolver.resolve(
                runId = "run-non-executable",
                selections = listOf(ExecutionSkillSelection("zhang_xuefeng")),
                createdAt = 1_000L,
            )
        }.exceptionOrNull()

        assertTrue(duplicate is IllegalArgumentException)
        assertTrue(unknown is InvalidExecutionSkillException)
        assertEquals("unknown_official_skill", (unknown as InvalidExecutionSkillException).reasonCode)
        assertTrue(nonExecutable is InvalidExecutionSkillException)
        assertEquals(
            "skill_not_executable",
            (nonExecutable as InvalidExecutionSkillException).reasonCode,
        )
        assertFalse(nonExecutable.message.orEmpty().contains("SKILL.md"))
    }
}
