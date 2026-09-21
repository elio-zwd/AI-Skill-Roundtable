package com.elio.jianyu.ui.screens.skills

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogLoadResult
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogParser
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.role.SkillRolePresentationCatalog
import com.elio.jianyu.skill.role.SkillRolePresentationCatalogLoader
import com.elio.jianyu.skill.role.SkillRolePresentationLoadResult
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkillRoleDiscoveryScreenTest {
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

    private val roles: List<SkillRoleCardUi> by lazy {
        projectSkillRoleCatalog(catalog, presentationCatalog).allRoles
    }

    @Test
    fun rootFilter_usesDraftBottomSheetAndAppliesApprovedFilters() {
        val events = mutableListOf<OfficialSkillCatalogEvent>()

        composeRule.setContent {
            SkillRoundtableTheme {
                SkillRolePageScreen(
                    uiState = OfficialSkillCatalogUiState(
                        isLoading = false,
                        totalSkillCount = roles.size,
                        roleCatalog = projectSkillRoleCatalog(catalog, presentationCatalog),
                        discoveryFilterSheetVisible = true,
                    ),
                    onEvent = events::add,
                )
            }
        }

        composeRule.onNodeWithTag("skill_role_filter_sheet").assertExists()
        composeRule.onNodeWithText("AI 模拟人物").performClick()
        composeRule.onNodeWithTag("filter_sheet_apply").performClick()

        composeRule.runOnIdle {
            val applied = events.filterIsInstance<OfficialSkillCatalogEvent.DiscoveryFiltersApplied>().single()
            assertEquals(setOf(OfficialSkillPrimaryType.PERSON_PERSPECTIVE), applied.filters.primaryTypes)
        }
    }

    @Test
    fun rootFilterButton_requestsNewDiscoverySheetInsteadOfLegacyDialog() {
        val events = mutableListOf<OfficialSkillCatalogEvent>()

        composeRule.setContent {
            SkillRoundtableTheme {
                SkillRolePageScreen(
                    uiState = OfficialSkillCatalogUiState(
                        isLoading = false,
                        totalSkillCount = roles.size,
                        roleCatalog = projectSkillRoleCatalog(catalog, presentationCatalog),
                    ),
                    onEvent = events::add,
                )
            }
        }

        composeRule.onNodeWithTag(OfficialSkillCatalogTestTags.FILTER_BUTTON).performClick()

        composeRule.runOnIdle {
            assertTrue(events.contains(OfficialSkillCatalogEvent.DiscoveryFilterSheetChanged(true)))
            assertTrue(events.none { it is OfficialSkillCatalogEvent.FilterDialogChanged })
        }
    }

    @Test
    fun search_supportsInputClearRelevantOrderingAndPersonDisclosure() {
        composeRule.setContent {
            var query by remember { mutableStateOf("") }
            val results = searchSkillRoles(roles, query)
            SkillRoundtableTheme {
                SkillRoleSearchScreen(
                    query = query,
                    onQueryChange = { query = it },
                    filters = RoleDiscoveryFilters(),
                    roles = results,
                    onBack = {},
                    onOpenFilters = {},
                    onRemoveFilter = {},
                    onClearAllFilters = {},
                    onOpenDetail = {},
                    onToggleFavorite = {},
                )
            }
        }

        composeRule.onNodeWithTag("search_screen_input").performTextInput("费曼")
        composeRule.onNodeWithText("理查德·费曼").assertExists()
        composeRule.onNodeWithText("按相关性排序").assertExists()
        composeRule.onNodeWithText("AI 模拟角色").assertExists()

        composeRule.onNodeWithTag("search_screen_clear_query").performClick()
        composeRule.onNodeWithText("按相关性排序").assertDoesNotExist()
        composeRule.onNodeWithText("共找到 ${roles.size} 个角色").assertExists()
    }

    @Test
    fun favorites_distinguishesTrueEmptyStateFromFilteredNoResult() {
        composeRule.setContent {
            SkillRoundtableTheme {
                SkillRoleFavoritesScreen(
                    query = "",
                    onQueryChange = {},
                    filters = RoleDiscoveryFilters(),
                    roles = emptyList(),
                    hasAnyFavorites = false,
                    onBack = {},
                    onOpenFilters = {},
                    onRemoveFilter = {},
                    onClearAllFilters = {},
                    onOpenDetail = {},
                    onRemoveFavorite = {},
                    onBrowseAllRoles = {},
                )
            }
        }

        composeRule.onNodeWithText("还没有收藏的 Skill 角色").assertExists()
        composeRule.onNodeWithText("没有符合条件的收藏角色").assertDoesNotExist()
    }

    @Test
    fun recent_startAndClearActionsAreIndependentAndRequireConfirmation() {
        val role = roles.first()
        val started = mutableListOf<String>()
        var cleared = false

        composeRule.setContent {
            SkillRoundtableTheme {
                SkillRoleRecentScreen(
                    sections = listOf(
                        SkillRoleRecentSection(
                            group = SkillRoleRecentGroup.TODAY,
                            roles = listOf(role),
                        ),
                    ),
                    onBack = {},
                    onOpenDetail = {},
                    onStartNewConversation = started::add,
                    onClearRecent = { cleared = true },
                )
            }
        }

        composeRule.onNodeWithTag("recent_start_chat_${role.skillId}").performClick()
        composeRule.runOnIdle { assertEquals(listOf(role.skillId), started) }

        composeRule.onNodeWithTag("recent_screen_clear_button").performClick()
        composeRule.onNodeWithTag("clear_recent_dialog").assertExists()
        composeRule.runOnIdle { assertTrue(!cleared) }

        composeRule.onNodeWithTag("confirm_clear_recent_button").performClick()
        composeRule.runOnIdle { assertTrue(cleared) }
    }
}
