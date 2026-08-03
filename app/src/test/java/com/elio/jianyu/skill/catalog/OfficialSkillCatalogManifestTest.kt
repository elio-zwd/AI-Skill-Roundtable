package com.elio.jianyu.skill.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkillCatalogManifestTest {
    private val sourceText: String by lazy {
        listOf(
            File("src/main/assets/official_skill_catalog_v1.json"),
            File("app/src/main/assets/official_skill_catalog_v1.json"),
        ).firstOrNull(File::isFile)?.readText()
            ?: error("找不到 official_skill_catalog_v1.json")
    }

    private val catalog: OfficialSkillCatalog by lazy {
        when (val result = OfficialSkillCatalogParser.parse(sourceText)) {
            is OfficialSkillCatalogLoadResult.Success -> result.catalog
            is OfficialSkillCatalogLoadResult.Failure -> error(result.message)
        }
    }

    @Test
    fun manifest_containsExactly44UniqueOfficialSkills() {
        assertEquals(44, catalog.skills.size)
        assertEquals(44, catalog.skills.map { it.id }.toSet().size)
        assertTrue(catalog.skills.all { it.nameZh.isNotBlank() })
        assertEquals((1..44).toList(), catalog.skills.map { it.defaultOrder })
    }

    @Test
    fun manifest_preservesResearchMappingAndSpecialIds() {
        assertNotNull(catalog.findById("office-document-productivity"))
        assertNotNull(catalog.findById("original-expression-naturalizer"))
        assertNotNull(catalog.findById("zhang_xuefeng"))
        assertFalse(catalog.containsOfficialId("zhangxuefeng-perspective"))
        assertFalse(catalog.containsOfficialId("academic-ai-evasion"))
        assertEquals(1, catalog.skills.count { it.id == "zhang_xuefeng" })
    }

    @Test
    fun manifest_mapsAll20HistoricalAssetsWithoutCopyingPrompts() {
        val expectedAssetPaths = mapOf(
            "zhang_xuefeng" to "skills/zhangxuefeng-skill-main/SKILL.md",
            "elon_musk" to "skills/elon-musk-skill-main/SKILL.md",
            "richard_feynman" to "skills/feynman-skill-main/SKILL.md",
            "charlie_munger" to "skills/munger-skill-main/SKILL.md",
            "naval_ravikant" to "skills/naval-skill-main/SKILL.md",
            "steve_jobs" to "skills/steve-jobs-skill-main/SKILL.md",
            "nassim_taleb" to "skills/taleb-skill-main/SKILL.md",
            "andrej_karpathy" to "skills/karpathy-skill/SKILL.md",
            "zhang_yiming" to "skills/zhang-yiming-skill/SKILL.md",
            "paul_graham" to "skills/paul-graham-skill/SKILL.md",
            "ilya_sutskever" to "skills/ilya-sutskever-skill/SKILL.md",
            "donald_trump" to "skills/trump-skill/SKILL.md",
            "mr_beast" to "skills/mrbeast-skill/SKILL.md",
            "justin_sun" to "skills/sun-yuchen-perspective/SKILL.md",
            "sigmund_freud" to "skills/freud-skill/SKILL.md",
            "x_mentor" to "skills/x-mentor-skill/SKILL.md",
            "feng_ge" to "skills/fengge-skill/SKILL.md",
            "changpeng_zhao" to "skills/cz-skill/SKILL.md",
            "duan_yongping" to "skills/duan-yongping-skill/SKILL.md",
            "tim_cook" to "skills/tim-cook-skill/SKILL.md",
        )
        val historical = catalog.skills.filter { it.id in expectedAssetPaths.keys }

        assertEquals(20, historical.size)
        assertTrue(historical.all { it.availability.hasAsset })
        assertTrue(historical.all { it.availability.discoverable })
        assertTrue(historical.all { it.availability.searchable })
        assertTrue(historical.all { it.availability.recommendable })
        assertEquals(expectedAssetPaths, historical.associate { it.id to it.assetPath })
        assertFalse(sourceText.contains("systemPrompt", ignoreCase = true))
    }

    @Test
    fun manifest_separatesOfficialIdentityPublicationAndExecution() {
        val riskyPerson = requireNotNull(catalog.findById("donald_trump"))
        val office = requireNotNull(catalog.findById("office-document-productivity"))
        val naturalizer = requireNotNull(catalog.findById("original-expression-naturalizer"))

        assertTrue(riskyPerson.availability.discoverable)
        assertTrue(riskyPerson.availability.recommendable)
        assertTrue(riskyPerson.isOfficialCandidate)
        assertFalse(office.availability.executable)
        assertFalse(naturalizer.availability.executable)
        assertEquals(OfficialSkillPublicationStatus.BLOCKED_REWORK, naturalizer.publicationStatus)
        assertTrue(naturalizer.integrityBoundaries.isNotEmpty())
    }

    @Test
    fun manifest_coversAllFourPrimaryTypesAndThreePrimaryValues() {
        assertEquals(OfficialSkillPrimaryType.entries.toSet(), catalog.skills.map { it.primaryType }.toSet())
        assertEquals(OfficialSkillPrimaryValue.entries.toSet(), catalog.skills.map { it.primaryValue }.toSet())
    }
}
