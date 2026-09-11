package com.elio.jianyu.skill.role

import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogLoadResult
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogParser
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRolePresentationCatalogTest {
    private val officialCatalog: OfficialSkillCatalog by lazy {
        val result = OfficialSkillCatalogParser.parse(
            assetFile("official_skill_catalog_v1.json").readText(),
            assetFile("official_skill_execution_manifest_v2.json").readText(),
        )
        require(result is OfficialSkillCatalogLoadResult.Success) { "官方目录加载失败" }
        result.catalog
    }

    private val knownIds: Set<String>
        get() = officialCatalog.skills.mapTo(linkedSetOf()) { it.id }

    @Test
    fun productionManifest_mapsEveryOfficialRoleExactlyOnce() {
        val result = SkillRolePresentationCatalogParser.parse(
            assetFile("skill_role_presentation_v1.json").readText(),
            knownIds,
        )
        assertTrue(result is SkillRolePresentationLoadResult.Success)
        val catalog = (result as SkillRolePresentationLoadResult.Success).catalog

        assertEquals(44, catalog.entries.size)
        assertEquals(knownIds, catalog.entries.mapTo(linkedSetOf()) { it.skillId })
    }

    @Test
    fun productionManifest_hasApprovedThoughtAndSpiritFeaturedRoles() {
        val catalog = productionCatalog()

        assertEquals(
            listOf("naval_ravikant", "richard_feynman", "nassim_taleb"),
            catalog.featuredEntries.map { it.skillId },
        )
        assertEquals(listOf(1, 2, 3), catalog.featuredEntries.mapNotNull { it.featuredOrder })
    }

    @Test
    fun productionManifest_lifeToolsContainsExactlyApprovedFiveRoles() {
        val catalog = productionCatalog()

        assertEquals(
            setOf(
                "budget-consumption-coach",
                "habit-wellbeing-coach",
                "relationship-dialogue-practice",
                "chinese-social-etiquette",
                "culture-fortune-entertainment",
            ),
            catalog.entries
                .filter { it.primaryDiscoveryCategory == SkillRoleDiscoveryCategory.LIFE_TOOLS }
                .mapTo(linkedSetOf()) { it.skillId },
        )
    }

    @Test
    fun duplicateUnknownAndMissingOfficialIdsAreRejected() {
        val duplicate = """
            {"schemaVersion":1,"entries":[
              {"skillId":"naval_ravikant","primaryDiscoveryCategory":"THINKING"},
              {"skillId":"naval_ravikant","primaryDiscoveryCategory":"THINKING"}
            ]}
        """.trimIndent()
        assertFailureCode(
            SkillRolePresentationCatalogParser.parse(duplicate, setOf("naval_ravikant")),
            "duplicate_skill_id",
        )

        val unknown = """
            {"schemaVersion":1,"entries":[
              {"skillId":"unknown-role","primaryDiscoveryCategory":"THINKING"}
            ]}
        """.trimIndent()
        assertFailureCode(
            SkillRolePresentationCatalogParser.parse(unknown, setOf("naval_ravikant")),
            "unknown_skill_id",
        )

        val missing = """{"schemaVersion":1,"entries":[]}"""
        assertFailureCode(
            SkillRolePresentationCatalogParser.parse(missing, setOf("naval_ravikant")),
            "missing_skill_id",
        )
    }

    @Test
    fun invalidSchemaCategoryAndFeaturedOrderAreRejected() {
        assertFailureCode(
            SkillRolePresentationCatalogParser.parse(
                """{"schemaVersion":2,"entries":[{"skillId":"naval_ravikant","primaryDiscoveryCategory":"THINKING"}]}""",
                setOf("naval_ravikant"),
            ),
            "unsupported_schema_version",
        )
        assertFailureCode(
            SkillRolePresentationCatalogParser.parse(
                """{"schemaVersion":1,"entries":[{"skillId":"naval_ravikant","primaryDiscoveryCategory":"NOT_A_CATEGORY"}]}""",
                setOf("naval_ravikant"),
            ),
            "invalid_manifest",
        )
        assertFailureCode(
            SkillRolePresentationCatalogParser.parse(
                """{"schemaVersion":1,"entries":[{"skillId":"naval_ravikant","primaryDiscoveryCategory":"THINKING","featuredOrder":2}]}""",
                setOf("naval_ravikant"),
            ),
            "invalid_featured_order",
        )
    }

    private fun productionCatalog(): SkillRolePresentationCatalog {
        val result = SkillRolePresentationCatalogParser.parse(
            assetFile("skill_role_presentation_v1.json").readText(),
            knownIds,
        )
        require(result is SkillRolePresentationLoadResult.Success) { "角色展示目录加载失败" }
        return result.catalog
    }

    private fun assertFailureCode(result: SkillRolePresentationLoadResult, code: String) {
        assertTrue(result is SkillRolePresentationLoadResult.Failure)
        assertTrue((result as SkillRolePresentationLoadResult.Failure).message.contains(code))
    }

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: File("app/src/main/assets/$path")
}
