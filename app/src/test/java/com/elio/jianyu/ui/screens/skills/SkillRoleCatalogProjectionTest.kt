package com.elio.jianyu.ui.screens.skills

import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogLoadResult
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogParser
import com.elio.jianyu.skill.catalog.RecentOfficialSkillUse
import com.elio.jianyu.skill.role.SkillRoleDiscoveryCategory
import com.elio.jianyu.skill.role.SkillRolePresentationCatalog
import com.elio.jianyu.skill.role.SkillRolePresentationCatalogParser
import com.elio.jianyu.skill.role.SkillRolePresentationLoadResult
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRoleCatalogProjectionTest {
    private val officialCatalog: OfficialSkillCatalog by lazy {
        val result = OfficialSkillCatalogParser.parse(
            assetFile("official_skill_catalog_v1.json").readText(),
            assetFile("official_skill_execution_manifest_v2.json").readText(),
        )
        require(result is OfficialSkillCatalogLoadResult.Success)
        result.catalog
    }

    private val presentationCatalog: SkillRolePresentationCatalog by lazy {
        val result = SkillRolePresentationCatalogParser.parse(
            assetFile("skill_role_presentation_v1.json").readText(),
            officialCatalog.skills.mapTo(linkedSetOf()) { it.id },
        )
        require(result is SkillRolePresentationLoadResult.Success)
        result.catalog
    }

    @Test
    fun projection_keepsAll44RolesEvenWhenLegacyVisualsCoverOnly20() {
        val legacyVisuals = officialCatalog.skills.take(20).associate { it.id to "avatars/${it.id}.jpg" }

        val projection = projectSkillRoleCatalog(
            catalog = officialCatalog,
            presentationCatalog = presentationCatalog,
            query = "",
            selectedCategory = null,
            favoriteIds = emptySet(),
            recentUses = emptyList(),
            legacyAvatarPaths = legacyVisuals,
        )

        assertEquals(44, projection.allRoles.size)
        assertEquals(44, projection.visibleRoles.size)
        assertEquals(20, projection.allRoles.count { it.avatarAssetPath != null })
    }

    @Test
    fun featuredRoles_followApprovedEditorialOrder_notDefaultOrder() {
        val projection = projectSkillRoleCatalog(
            catalog = officialCatalog,
            presentationCatalog = presentationCatalog,
        )

        assertEquals(
            listOf("naval_ravikant", "richard_feynman", "nassim_taleb"),
            projection.featuredRoles.map { it.skillId },
        )
        assertFalse(projection.featuredRoles.map { it.skillId } == officialCatalog.skills.take(3).map { it.id })
    }

    @Test
    fun searchAndDiscoveryCategory_areIntersected() {
        val projection = projectSkillRoleCatalog(
            catalog = officialCatalog,
            presentationCatalog = presentationCatalog,
            query = "预算",
            selectedCategory = SkillRoleDiscoveryCategory.LIFE_TOOLS,
        )

        assertEquals(listOf("budget-consumption-coach"), projection.visibleRoles.map { it.skillId })
        assertTrue(projection.visibleRoles.all { it.primaryDiscoveryCategory == SkillRoleDiscoveryCategory.LIFE_TOOLS })
    }

    @Test
    fun favoritesAndRecentUses_arePresentationState_only() {
        val projection = projectSkillRoleCatalog(
            catalog = officialCatalog,
            presentationCatalog = presentationCatalog,
            favoriteIds = setOf("steve_jobs"),
            recentUses = listOf(
                RecentOfficialSkillUse("career-navigator", usedAt = 200L),
                RecentOfficialSkillUse("naval_ravikant", usedAt = 100L),
            ),
        )

        assertTrue(projection.allRoles.single { it.skillId == "steve_jobs" }.isFavorite)
        assertEquals(
            listOf("career-navigator", "naval_ravikant"),
            projection.recentRoles.map { it.skillId },
        )
        assertEquals(200L, projection.recentRoles.first().lastUsedAt)
        assertEquals(44, projection.allRoles.size)
    }

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: File("app/src/main/assets/$path")
}
