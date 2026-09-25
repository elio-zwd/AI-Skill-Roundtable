package com.elio.jianyu.ui.screens.dialog.overlays

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import com.elio.jianyu.ui.screens.dialog.AddSkillCatalogUiModel
import com.elio.jianyu.ui.screens.dialog.DialogEvent
import com.elio.jianyu.ui.screens.dialog.SkillRoleUiModel
import com.elio.jianyu.ui.theme.SkillRoundtableTheme
import org.junit.Rule
import org.junit.Test

class AddSkillRoleBottomSheetTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun recommendedAndAllSkills_renderCanonicalAvatarImages() {
        val recommended = SkillRoleUiModel(
            id = "career-navigator",
            name = "职业发展顾问",
            shortDescription = "梳理职业方向与下一步行动",
            avatarUrl = "avatars/portraits/career-navigator.jpg",
            avatarText = "职业",
        )
        val allSkill = SkillRoleUiModel(
            id = "study-planner",
            name = "学习规划师",
            shortDescription = "根据目标与反馈设计学习计划",
            avatarUrl = "avatars/portraits/study-planner.jpg",
            avatarText = "学习",
        )

        composeRule.setContent {
            SkillRoundtableTheme {
                AddSkillRoleBottomSheet(
                    isOpen = true,
                    catalog = AddSkillCatalogUiModel(
                        recommended = listOf(recommended),
                        allSkills = listOf(allSkill),
                    ),
                    onEvent = { _: DialogEvent -> },
                )
            }
        }

        composeRule.onNodeWithContentDescription("职业发展顾问").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("学习规划师").assertExists()
    }
}
