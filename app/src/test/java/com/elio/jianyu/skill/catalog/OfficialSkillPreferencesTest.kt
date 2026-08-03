package com.elio.jianyu.skill.catalog

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkillPreferencesTest {
    private val catalog = InMemoryOfficialSkillCatalog(
        listOf(minimalSkill("first", 1), minimalSkill("second", 2)),
    )

    @Test
    fun favoriteStoresOnlyValidStableOfficialIds() = runBlocking {
        val preferences = InMemoryOfficialSkillPreferences(catalog)

        assertTrue(preferences.setFavorite("first", true))
        assertFalse(preferences.setFavorite("unknown", true))
        assertEquals(setOf("first"), preferences.favoriteIds.value)

        assertTrue(preferences.setFavorite("first", false))
        assertTrue(preferences.favoriteIds.value.isEmpty())
    }

    @Test
    fun viewingDetailDoesNotCreateRecentUse() = runBlocking {
        val preferences = InMemoryOfficialSkillPreferences(catalog)

        preferences.onSkillDetailViewed("first")

        assertTrue(preferences.recentUses.value.isEmpty())
    }

    @Test
    fun explicitRecordSkillUsedCreatesStableDescendingHistory() = runBlocking {
        val preferences = InMemoryOfficialSkillPreferences(catalog, maxRecent = 2)

        assertTrue(preferences.recordSkillUsed("first", usedAt = 10L))
        assertTrue(preferences.recordSkillUsed("second", usedAt = 20L))
        assertTrue(preferences.recordSkillUsed("first", usedAt = 30L))
        assertFalse(preferences.recordSkillUsed("unknown", usedAt = 40L))

        assertEquals(
            listOf(RecentOfficialSkillUse("first", 30L), RecentOfficialSkillUse("second", 20L)),
            preferences.recentUses.value,
        )
    }

    @Test
    fun initialUnknownIdsAreQuarantined() {
        val preferences = InMemoryOfficialSkillPreferences(
            catalog = catalog,
            initialFavoriteIds = setOf("first", "unknown"),
            initialRecentUses = listOf(
                RecentOfficialSkillUse("unknown", 40L),
                RecentOfficialSkillUse("second", 30L),
            ),
        )

        assertEquals(setOf("first"), preferences.favoriteIds.value)
        assertEquals(listOf(RecentOfficialSkillUse("second", 30L)), preferences.recentUses.value)
    }

    private fun minimalSkill(id: String, order: Int) = OfficialSkillDefinition(
        id = id,
        nameZh = id,
        aliases = emptyList(),
        summary = id,
        primaryType = OfficialSkillPrimaryType.TASK_ASSISTANT,
        primaryValue = OfficialSkillPrimaryValue.REALITY_SUPPORT,
        domainTags = listOf("office_operations"),
        scenarioTags = listOf("write"),
        inputTags = listOf("question"),
        outputTags = listOf("draft"),
        useMode = OfficialSkillUseMode.BOTH,
        networkRequirement = OfficialSkillNetworkRequirement.NOT_NEEDED,
        materialRequirements = listOf(OfficialSkillMaterialRequirement.OPTIONAL),
        riskLevel = OfficialSkillRiskLevel.GENERAL,
        publicationStatus = OfficialSkillPublicationStatus.PUBLISHABLE,
        sourceStatus = OfficialSkillSourceStatus.VERIFIED_IMPLEMENTATION_SOURCE,
        availability = OfficialSkillAvailability(
            v1Target = true,
            hasAsset = true,
            discoverable = true,
            searchable = true,
            recommendable = true,
            executable = true,
        ),
        typicalScenarios = listOf("测试"),
        inputRequirements = listOf("问题"),
        outputForms = listOf("草稿"),
        boundaries = listOf("不保存敏感正文"),
        nonExecutableReason = null,
        personDisclaimer = null,
        integrityBoundaries = emptyList(),
        sourceSummary = "测试",
        assetPath = "skills/$id/SKILL.md",
        defaultOrder = order,
    )
}
