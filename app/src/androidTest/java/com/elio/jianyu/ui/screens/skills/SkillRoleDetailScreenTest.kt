package com.elio.jianyu.ui.screens.skills

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogLoadResult
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogParser
import com.elio.jianyu.skill.role.SkillRolePresentationCatalogLoader
import com.elio.jianyu.skill.role.SkillRolePresentationLoadResult
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SkillRoleDetailScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

    private val projection by lazy {
        val catalogResult = OfficialSkillCatalogParser.loadFromAssets(targetContext)
        require(catalogResult is OfficialSkillCatalogLoadResult.Success)
        val presentationResult = SkillRolePresentationCatalogLoader.load(
            targetContext,
            catalogResult.catalog,
        )
        require(presentationResult is SkillRolePresentationLoadResult.Success)
        projectSkillRoleCatalog(catalogResult.catalog, presentationResult.catalog)
    }

    @Test
    fun personRole_showsDisclosureAndTwoExplicitConversationActions() {
        val role = projection.allRoles.first { it.skillId == "naval_ravikant" }
        var started = 0

        composeRule.setContent {
            SkillRoundtableTheme {
                SkillRoleDetailScreen(
                    role = role,
                    isFavorite = false,
                    canAddToCurrentConversation = false,
                    onBack = {},
                    onToggleFavorite = {},
                    onStartNewConversation = { started++ },
                    onAddToCurrentConversation = {},
                )
            }
        }

        composeRule.onNodeWithText("AI 模拟角色").assertExists()
        role.officialSkill.personDisclaimer?.let { disclosure ->
            composeRule.onNodeWithText(disclosure).assertExists()
        } ?: error("人物型官方 Skill 必须有 personDisclaimer")
        composeRule.onNodeWithText("工作方式").assertExists()
        composeRule.onNodeWithText("来源与能力依据").assertExists()
        composeRule.onNodeWithText(role.officialSkill.sourceSummary).assertExists()
        composeRule.onNodeWithText("开始新对话").performClick()
        composeRule.onNodeWithText("增加到当前会话").assertIsNotEnabled()
        composeRule.onNodeWithText("当前没有可加入的会话").assertExists()
        composeRule.runOnIdle { assertEquals(1, started) }
    }

    @Test
    fun executableRole_exposesCoreOfficialFactsWithoutInventedScore() {
        val role = projection.allRoles.first { it.isExecutable && !it.isPersonSimulation }

        composeRule.setContent {
            SkillRoundtableTheme {
                SkillRoleDetailScreen(
                    role = role,
                    isFavorite = false,
                    canAddToCurrentConversation = true,
                    onBack = {},
                    onToggleFavorite = {},
                    onStartNewConversation = {},
                    onAddToCurrentConversation = {},
                )
            }
        }

        composeRule.onNodeWithText(role.name).assertExists()
        composeRule.onNodeWithText("适合的问题").assertExists()
        composeRule.onNodeWithText("工作方式").assertExists()
        composeRule.onNodeWithText("输入要求").assertExists()
        composeRule.onNodeWithText("输出形式").assertExists()
        composeRule.onNodeWithText("边界").assertExists()
        composeRule.onNodeWithText("来源与能力依据").assertExists()
        composeRule.onNodeWithText("开始新对话").assertExists()
        composeRule.onNodeWithText("增加到当前会话").assertExists()
        composeRule.runOnIdle {
            assertTrue(role.officialSkill.typicalScenarios.isNotEmpty())
            assertTrue(role.officialSkill.sourceSummary.isNotBlank())
        }
    }
}
