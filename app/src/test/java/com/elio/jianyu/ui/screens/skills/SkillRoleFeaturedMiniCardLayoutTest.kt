package com.elio.jianyu.ui.screens.skills

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillRoleFeaturedMiniCardLayoutTest {
    @Test
    fun featuredMiniCard_allowsContentHeightToGrow() {
        val source = sourceFile("SkillRolePageScreen.kt").readText()
        val miniCard = source
            .substringAfter("private fun RoleFeatureMiniCard(")
            .substringBefore("@Composable\nprivate fun RoleRecentCards")

        assertFalse(
            "推荐次卡不能用固定 118dp 高度，否则双行姓名/摘要/AI 模拟标签会被裁切",
            miniCard.contains(".height(118.dp)"),
        )
        assertTrue(
            "推荐次卡应使用可增长的最小高度",
            miniCard.contains(".heightIn(min = 128.dp)"),
        )
    }

    private fun sourceFile(name: String): File = listOf(
        File("src/main/java/com/elio/jianyu/ui/screens/skills/$name"),
        File("app/src/main/java/com/elio/jianyu/ui/screens/skills/$name"),
    ).firstOrNull(File::isFile)
        ?: File("app/src/main/java/com/elio/jianyu/ui/screens/skills/$name")
}
