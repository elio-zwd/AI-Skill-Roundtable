package com.elio.jianyu.skill.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkillExecutableBatchTest {
    private val baseSource: String by lazy { assetFile("official_skill_catalog_v1.json").readText() }
    private val publicationSource: String by lazy {
        assetFile("official_skill_execution_batch_v1.json").readText()
    }
    private val catalog: OfficialSkillCatalog by lazy {
        when (val result = OfficialSkillCatalogParser.parse(baseSource, publicationSource)) {
            is OfficialSkillCatalogLoadResult.Success -> result.catalog
            is OfficialSkillCatalogLoadResult.Failure -> error(result.message)
        }
    }

    @Test
    fun productionCatalogKeeps44IdsAndPublishesExactlyFourOriginalTaskSkills() {
        val executable = catalog.skills.filter { it.availability.executable }

        assertEquals(44, catalog.skills.size)
        assertEquals((1..44).toList(), catalog.skills.map { it.defaultOrder })
        assertEquals(
            setOf(
                "study-planner",
                "meeting-to-action",
                "report-proposal-writer",
                "research-fact-checker",
            ),
            executable.map { it.id }.toSet(),
        )
        assertEquals(4, executable.size)
        assertTrue(executable.none { it.primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE })
        assertTrue(executable.none { it.riskLevel == OfficialSkillRiskLevel.HIGH_STAKES })
        assertTrue(executable.none { it.riskLevel == OfficialSkillRiskLevel.URGENT })
        assertTrue(executable.all { it.publicationStatus == OfficialSkillPublicationStatus.PUBLISHABLE })
        assertTrue(
            executable.all {
                it.sourceStatus == OfficialSkillSourceStatus.VERIFIED_IMPLEMENTATION_SOURCE
            },
        )
        assertTrue(executable.all { it.nonExecutableReason == null })
        assertTrue(
            executable.all {
                it.availability.v1Target &&
                    it.availability.hasAsset &&
                    it.availability.discoverable &&
                    it.availability.searchable &&
                    it.availability.recommendable
            },
        )
    }

    @Test
    fun everyPublishedSkillHasReadableNonEmptyAssetAndPassesEligibilityAudit() {
        val reader = OfficialSkillAssetReader { assetPath ->
            val file = assetFile(assetPath)
            if (!file.isFile) {
                OfficialSkillAssetReadResult.Missing
            } else {
                runCatching { OfficialSkillAssetReadResult.Success(file.readText()) }
                    .getOrElse { OfficialSkillAssetReadResult.Unreadable }
            }
        }
        val eligibility = OfficialSkillExecutionEligibility(catalog, reader)
        val executable = catalog.skills.filter { it.availability.executable }

        val results = executable.map { eligibility.audit(it.id) }

        assertEquals(4, results.size)
        assertTrue(results.all(OfficialSkillExecutionEligibilityResult::eligible))
        assertTrue(executable.all { assetFile(requireNotNull(it.assetPath)).readText().isNotBlank() })
    }

    @Test
    fun unpublishedCandidatesRemainNonExecutableWithAccurateReason() {
        val deferredIds = setOf(
            "team-handover",
            "office-document-productivity",
            "product-competition-analyst",
            "original-expression-naturalizer",
            "zhang_xuefeng",
        )

        deferredIds.forEach { id ->
            val skill = requireNotNull(catalog.findById(id))
            assertFalse("$id 不得在首批被激活", skill.availability.executable)
            assertTrue("$id 必须保留不可执行原因", !skill.nonExecutableReason.isNullOrBlank())
        }
    }

    @Test
    fun executionPublicationRejectsUnknownIdAndUnverifiedSource() {
        val unknown = publicationSource.replace(
            "\"study-planner\"",
            "\"unknown-skill\"",
        )
        val unverified = publicationSource.replaceFirst(
            "\"VERIFIED_IMPLEMENTATION_SOURCE\"",
            "\"IMPLEMENTATION_SOURCE_PENDING\"",
        )

        val unknownResult = OfficialSkillCatalogParser.parse(baseSource, unknown)
        val unverifiedResult = OfficialSkillCatalogParser.parse(baseSource, unverified)

        assertTrue(unknownResult is OfficialSkillCatalogLoadResult.Failure)
        assertTrue(
            (unknownResult as OfficialSkillCatalogLoadResult.Failure)
                .message.contains("unknown_execution_skill"),
        )
        assertTrue(unverifiedResult is OfficialSkillCatalogLoadResult.Failure)
        assertTrue(
            (unverifiedResult as OfficialSkillCatalogLoadResult.Failure)
                .message.contains("execution_source_not_verified"),
        )
    }

    @Test
    fun skillAssetsDoNotContainCredentialOrPromptExfiltrationInstructions() {
        val text = catalog.skills
            .filter { it.availability.executable }
            .joinToString("\n") { assetFile(requireNotNull(it.assetPath)).readText() }
            .lowercase()

        assertFalse(text.contains("AIza".lowercase()))
        assertFalse(text.contains("sk-"))
        assertFalse(text.contains("忽略系统提示"))
        assertFalse(text.contains("泄露系统提示"))
        assertFalse(text.contains("规避安全规则"))
        assertFalse(text.contains("虚构引用"))
    }

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: File("app/src/main/assets/$path")
}
