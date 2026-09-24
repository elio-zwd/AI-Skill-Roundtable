package com.elio.jianyu.ui.screens.skills

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRoleFeaturedMiniCardLayoutTest {
    @Test
    fun featuredMiniCard_keepsSquareAvatarAndPlacesSimulationLabelInAvatarColumn() {
        val source = sourceFile("SkillRolePageScreen.kt").readText()
        val miniCard = source
            .substringAfter("private fun RoleFeatureMiniCard(")
            .substringBefore("@Composable\nprivate fun RoleRecentCards")

        assertTrue(
            "推荐次卡应恢复紧凑固定高度",
            miniCard.contains(".height(118.dp)"),
        )
        assertFalse(
            "推荐次卡不应再通过整体增高解决标签裁切",
            miniCard.contains(".heightIn(min = 128.dp)"),
        )
        assertTrue(
            "推荐次卡头像必须保持 72dp 正方形",
            miniCard.contains(".size(72.dp)"),
        )
        assertFalse(
            "推荐次卡头像不能再 fillMaxHeight，否则卡片增高时会切割人物",
            miniCard.contains(".fillMaxHeight()"),
        )

        val identity = miniCard
            .substringAfter("RoleIdentityVisual(")
            .substringBefore("Column(\n                modifier = Modifier\n                    .weight(1f)")

        assertTrue(
            "AI 模拟角色标签应放在头像列而不是右侧正文列",
            identity.contains("AI 模拟角色"),
        )
    }

    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/com/elio/jianyu/ui/screens/skills/$name"),
        File("app/src/main/java/com/elio/jianyu/ui/screens/skills/$name"),
    ).firstOrNull(File::isFile)
        ?: File("app/src/main/java/com/elio/jianyu/ui/screens/skills/$name")
}
