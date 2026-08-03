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
        val historicalIds = setOf(
            "zhang_xuefeng", "elon_musk", "richard_feynman", "charlie_munger",
            "naval_ravikant", "steve_jobs", "nassim_taleb", "andrej_karpathy",
            "zhang_yiming", "paul_graham", "ilya_sutskever", "donald_trump",
            "mr_beast", "justin_sun", "sigmund_freud", "x_mentor", "feng_ge",
            "changpeng_zhao", "duan_yongping", "tim_cook",
        )
        val historical = catalog.skills.filter { it.id in historicalIds }

        assertEquals(20, historical.size)
        assertTrue(historical.all { it.availability.hasAsset })
        assertTrue(historical.all { it.availability.discoverable })
        assertTrue(historical.all { it.availability.searchable })
        assertTrue(historical.all { it.availability.recommendable })
        assertTrue(historical.all { !it.assetPath.isNullOrBlank() })
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
