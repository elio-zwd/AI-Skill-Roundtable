package com.elio.jianyu.skill.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkillCatalogRiskSearchTest {
    private val catalog: OfficialSkillCatalog by lazy {
        val source = listOf(
            File("src/main/assets/official_skill_catalog_v1.json"),
            File("app/src/main/assets/official_skill_catalog_v1.json"),
        ).firstOrNull(File::isFile)?.readText()
            ?: error("找不到 official_skill_catalog_v1.json")
        when (val result = OfficialSkillCatalogParser.parse(source)) {
            is OfficialSkillCatalogLoadResult.Success -> result.catalog
            is OfficialSkillCatalogLoadResult.Failure -> error(result.message)
        }
    }

    @Test
    fun politicalAndFinancialRiskPeopleRemainSearchableAndRecommendable() {
        val trump = OfficialSkillCatalogQuery.apply(catalog, query = "特朗普")
        val munger = OfficialSkillCatalogQuery.apply(catalog, query = "charlie_munger")
        val cz = OfficialSkillCatalogQuery.apply(catalog, query = "CZ")

        assertEquals(listOf("donald_trump"), trump.map { it.id })
        assertEquals(listOf("charlie_munger"), munger.map { it.id })
        assertEquals(listOf("changpeng_zhao"), cz.map { it.id })
        (trump + munger + cz).forEach { skill ->
            assertTrue(skill.availability.searchable)
            assertTrue(skill.availability.recommendable)
        }
    }

    @Test
    fun personDefaultOrderIsManifestOrderNotRiskOrder() {
        val people = OfficialSkillCatalogQuery.apply(
            catalog = catalog,
            filters = OfficialSkillCatalogFilters(
                primaryTypes = setOf(OfficialSkillPrimaryType.PERSON_PERSPECTIVE),
            ),
        )

        assertEquals((1..15).toList(), people.map { it.defaultOrder })
        assertEquals("zhang_xuefeng", people.first().id)
        assertEquals("sigmund_freud", people.last().id)
    }
}
