package com.elio.jianyu.ui.screens.resources

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.ui.navigation.ResourceTab
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ResourcesScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun personalContextLibraryExplainsDefaultUnselectedRule() {
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    state = ResourcesUiState.Content(
                        section = ResourceLibrarySection.PERSONAL_CONTEXTS,
                    ),
                )
            }
        }

        composeRule.onNodeWithText("个人背景可跨议题复用，但每次执行默认不勾选。")
            .assertIsDisplayed()
        composeRule.onNodeWithText("背景条目不会在应用启动或创建议题时自动加入模型上下文。")
            .assertIsDisplayed()
    }

    @Test
    fun deletedMaterialOffersRestoreAndPurgeActions() {
        composeRule.setContent {
            SkillRoundtableTheme {
                ResourcesScreen(
                    selectedTab = ResourceTab.MATERIALS,
                    onSelectTab = {},
                    onOpenSettings = {},
                    state = ResourcesUiState.Content(
                        lifecycles = setOf(ContextSourceLifecycle.DELETED),
                        materials = listOf(
                            MaterialUiItem(
                                id = "material-1",
                                issueId = "issue-1",
                                stageId = null,
                                title = "已删除资料",
                                sourceType = "note",
                                sourceLocator = null,
                                contentPreview = "正文",
                                content = "正文",
                                sourcePublishedAt = null,
                                sourceCapturedAt = 1L,
                                sensitive = false,
                                lifecycle = ContextSourceLifecycle.DELETED,
                                updatedAt = 1L,
                            ),
                        ),
                    ),
                )
            }
        }

        composeRule.onNodeWithTag(ResourcesTestTags.material("material-1")).assertIsDisplayed()
        composeRule.onNodeWithText("恢复").assertIsDisplayed()
        composeRule.onNodeWithText("彻底清除").performClick()
    }
}
