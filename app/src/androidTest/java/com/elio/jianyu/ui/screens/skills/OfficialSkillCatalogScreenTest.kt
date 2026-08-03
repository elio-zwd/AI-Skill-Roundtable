package com.elio.jianyu.ui.screens.skills

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.skill.catalog.OfficialSkillAvailability
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.catalog.OfficialSkillMaterialRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillNetworkRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryValue
import com.elio.jianyu.skill.catalog.OfficialSkillPublicationStatus
import com.elio.jianyu.skill.catalog.OfficialSkillRiskLevel
import com.elio.jianyu.skill.catalog.OfficialSkillSourceStatus
import com.elio.jianyu.skill.catalog.OfficialSkillUseMode
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OfficialSkillCatalogScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val naturalizer = skill(
        id = "original-expression-naturalizer",
        name = "去AI化助手",
        integrity = listOf(
            "只让用户真实内容更符合本人表达。",
            "不规避检测。",
            "不伪造事实。",
            "不伪造经历。",
            "不代写需要本人独立完成的受限内容。",
            "不删除必要诚信声明。",
            "不替用户冒充他人。",
        ),
    )

    @Test
    fun listShowsExplicitAvailabilityAndSpecialBoundary() {
        composeRule.setContent {
            SkillRoundtableTheme {
                OfficialSkillCatalogScreen(
                    uiState = OfficialSkillCatalogUiState(
                        isLoading = false,
                        allSkills = listOf(naturalizer),
                        visibleSkills = listOf(naturalizer),
                        totalSkillCount = 44,
                    ),
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithTag(OfficialSkillCatalogTestTags.ROOT).assertExists()
        composeRule.onNodeWithTag(OfficialSkillCatalogTestTags.skill(naturalizer.id)).assertExists()
        composeRule.onNodeWithText("待门禁").assertExists()
        composeRule.onNodeWithText(
            "只整理真实内容；不规避检测、不伪造事实或经历。",
        ).assertExists()
    }

    @Test
    fun detailShowsIntegrityBoundariesAndDisabledUseState() {
        composeRule.setContent {
            SkillRoundtableTheme {
                OfficialSkillCatalogScreen(
                    uiState = OfficialSkillCatalogUiState(
                        isLoading = false,
                        allSkills = listOf(naturalizer),
                        visibleSkills = listOf(naturalizer),
                        selectedSkill = naturalizer,
                        totalSkillCount = 44,
                    ),
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithTag(OfficialSkillCatalogTestTags.DETAIL).assertExists()
        composeRule.onNodeWithText("诚信边界").assertExists()
        composeRule.onNodeWithText("• 不规避检测。").assertExists()
        composeRule.onNodeWithText("暂不可用").assertExists()
    }

    @Test
    fun filterAndFavoriteActionsEmitEvents() {
        val events = mutableListOf<OfficialSkillCatalogEvent>()
        composeRule.setContent {
            SkillRoundtableTheme {
                OfficialSkillCatalogScreen(
                    uiState = OfficialSkillCatalogUiState(
                        isLoading = false,
                        allSkills = listOf(naturalizer),
                        visibleSkills = listOf(naturalizer),
                        totalSkillCount = 44,
                    ),
                    onEvent = events::add,
                )
            }
        }

        composeRule.onNodeWithTag(OfficialSkillCatalogTestTags.FILTER_BUTTON).performClick()
        composeRule.onNodeWithTag(OfficialSkillCatalogTestTags.favorite(naturalizer.id)).performClick()
        composeRule.runOnIdle {
            assertTrue(events.contains(OfficialSkillCatalogEvent.FilterDialogChanged(true)))
            assertTrue(events.contains(OfficialSkillCatalogEvent.ToggleFavorite(naturalizer.id)))
        }
    }

    @Test
    fun combinationEditorShowsMemberBoundaryAndResponsibilityDisclaimer() {
        composeRule.setContent {
            SkillRoundtableTheme {
                OfficialSkillCatalogScreen(
                    uiState = OfficialSkillCatalogUiState(
                        isLoading = false,
                        allSkills = listOf(naturalizer),
                        visibleSkills = listOf(naturalizer),
                        totalSkillCount = 44,
                        combinationEditor = OfficialSkillCombinationEditorState(
                            combinationId = "combo",
                            name = "写作组合",
                            members = listOf(
                                OfficialSkillCombinationMemberEditorState(naturalizer.id),
                            ),
                            createdAt = 1L,
                            expectedUpdatedAt = null,
                        ),
                    ),
                    onEvent = {},
                )
            }
        }

        composeRule.onNodeWithTag(OfficialSkillCatalogTestTags.COMBINATION_EDITOR).assertExists()
        composeRule.onNodeWithText(
            "默认职责只描述成员在本组合中的关注点，不是 System Prompt。",
        ).assertExists()
        composeRule.onNodeWithText(
            "只整理真实内容；不规避检测、不伪造事实或经历。",
        ).assertExists()
    }

    private fun skill(
        id: String,
        name: String,
        integrity: List<String>,
    ) = OfficialSkillDefinition(
        id = id,
        nameZh = name,
        aliases = listOf("自然表达助手"),
        summary = "让真实内容更符合本人表达。",
        primaryType = OfficialSkillPrimaryType.TASK_ASSISTANT,
        primaryValue = OfficialSkillPrimaryValue.REALITY_SUPPORT,
        domainTags = listOf("writing_research"),
        scenarioTags = listOf("write"),
        inputTags = listOf("document_text"),
        outputTags = listOf("draft"),
        useMode = OfficialSkillUseMode.SINGLE_PREFERRED,
        networkRequirement = OfficialSkillNetworkRequirement.NOT_NEEDED,
        materialRequirements = listOf(OfficialSkillMaterialRequirement.USER_AUTHORIZED),
        riskLevel = OfficialSkillRiskLevel.SENSITIVE,
        publicationStatus = OfficialSkillPublicationStatus.BLOCKED_REWORK,
        sourceStatus = OfficialSkillSourceStatus.ORIGINAL_DESIGN_REQUIRED,
        availability = OfficialSkillAvailability(
            v1Target = true,
            hasAsset = false,
            discoverable = true,
            searchable = true,
            recommendable = false,
            executable = false,
        ),
        typicalScenarios = listOf("写作"),
        inputRequirements = listOf("用户真实内容"),
        outputForms = listOf("可编辑草稿"),
        boundaries = listOf("不改变事实"),
        nonExecutableReason = "尚未通过门禁",
        integrityBoundaries = integrity,
        sourceSummary = "独立原创设计",
        defaultOrder = 44,
    )
}
