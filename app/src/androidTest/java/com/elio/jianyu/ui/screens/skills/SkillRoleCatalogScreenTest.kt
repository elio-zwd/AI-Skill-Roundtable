package com.elio.jianyu.ui.screens.skills

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogLoadResult
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogParser
import com.elio.jianyu.skill.role.SkillRoleDiscoveryCategory
import com.elio.jianyu.skill.role.SkillRolePresentationCatalog
import com.elio.jianyu.skill.role.SkillRolePresentationCatalogLoader
import com.elio.jianyu.skill.role.SkillRolePresentationLoadResult
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkillRoleCatalogScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    private val catalog: OfficialSkillCatalog by lazy {
        val result = OfficialSkillCatalogParser.loadFromAssets(targetContext)
        require(result is OfficialSkillCatalogLoadResult.Success)
        result.catalog
    }

    private val presentationCatalog: SkillRolePresentationCatalog by lazy {
        val result = SkillRolePresentationCatalogLoader.load(targetContext, catalog)
        require(result is SkillRolePresentationLoadResult.Success)
        result.catalog
    }

    @Test
    fun allCategory_usesApprovedRecommendationCopyAndHidesEmptyRecentSection() {
        val projection = projectSkillRoleCatalog(catalog, presentationCatalog)

        composeRule.setContent {
            SkillRoundtableTheme {
                SkillRolePageScreen(
                    uiState = OfficialSkillCatalogUiState(
                        isLoading = false,
                        totalSkillCount = 44,
                        roleCatalog = projection,
                    ),
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("推荐角色").assertExists()
        composeRule.onNodeWithText("为你推荐").assertDoesNotExist()
        composeRule.onNodeWithText("最近使用").assertDoesNotExist()
        composeRule.onNodeWithText("全部角色").assertExists()
        projection.featuredRoles.forEach { role ->
            composeRule.onNodeWithText(role.name).assertExists()
        }
    }

    @Test
    fun featuredCards_doNotExposeRawDomainTagTokens() {
        val projection = projectSkillRoleCatalog(catalog, presentationCatalog)
        val rawTokens = projection.featuredRoles
            .flatMap { it.officialSkill.domainTags }
            .filter { '_' in it }
            .distinct()

        composeRule.setContent {
            SkillRoundtableTheme {
                SkillRolePageScreen(
                    uiState = OfficialSkillCatalogUiState(
                        isLoading = false,
                        totalSkillCount = 44,
                        roleCatalog = projection,
                    ),
                    onEvent = {},
                )
            }
        }

        rawTokens.forEach { token ->
            composeRule.onNodeWithText(token).assertDoesNotExist()
        }
    }

    @Test
    fun specificCategory_showsDescriptionAndOnlyProjectedCategoryRoles() {
        val projection = projectSkillRoleCatalog(
            catalog = catalog,
            presentationCatalog = presentationCatalog,
            selectedCategory = SkillRoleDiscoveryCategory.LIFE_TOOLS,
        )
        assertTrue(projection.visibleRoles.isNotEmpty())

        composeRule.setContent {
            SkillRoundtableTheme {
                SkillRolePageScreen(
                    uiState = OfficialSkillCatalogUiState(
                        isLoading = false,
                        totalSkillCount = 44,
                        roleCatalog = projection,
                    ),
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithText("生活工具").assertExists()
        composeRule
            .onNodeWithText("消费、自我管理、关系、礼仪与文化类日常辅助")
            .assertExists()
        projection.visibleRoles.first().let { role ->
            composeRule.onNodeWithText(role.name).assertExists()
        }
    }

    @Test
    fun favoriteAction_isIndependentFromOpeningRoleDetail() {
        val projection = projectSkillRoleCatalog(catalog, presentationCatalog)
        val target = projection.allRoles.first()
        val events = mutableListOf<OfficialSkillCatalogEvent>()

        composeRule.setContent {
            SkillRoundtableTheme {
                SkillRolePageScreen(
                    uiState = OfficialSkillCatalogUiState(
                        isLoading = false,
                        totalSkillCount = 44,
                        roleCatalog = projection,
                    ),
                    onEvent = events::add,
                )
            }
        }

        composeRule
            .onNodeWithTag(OfficialSkillCatalogTestTags.favorite(target.skillId))
            .performClick()
        composeRule.runOnIdle {
            assertTrue(events.contains(OfficialSkillCatalogEvent.ToggleFavorite(target.skillId)))
            assertTrue(events.none { it is OfficialSkillCatalogEvent.OpenDetail })
        }
    }
}
