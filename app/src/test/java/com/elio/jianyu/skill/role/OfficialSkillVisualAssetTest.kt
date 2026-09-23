package com.elio.jianyu.skill.role

import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogLoadResult
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogParser
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkillVisualAssetTest {
    private val officialCatalog: OfficialSkillCatalog by lazy {
        val result = OfficialSkillCatalogParser.parse(
            assetFile("official_skill_catalog_v1.json").readText(),
            assetFile("official_skill_execution_manifest_v2.json").readText(),
        )
        require(result is OfficialSkillCatalogLoadResult.Success)
        result.catalog
    }

    @Test
    fun productionAssets_coverEveryOfficialSkillVisual() {
        val issues = officialCatalog.skills.mapNotNull { skill ->
            val path = officialSkillVisualAssetPath(skill)
            val file = assetFile(path)
            when {
                !file.isFile -> "$path: missing"
                else -> {
                    val image = runCatching { ImageIO.read(file) }.getOrNull()
                    when {
                        image == null -> "$path: decode_failed"
                        image.width != image.height -> "$path: undefinedxundefined not_square"
                        image.width < 512 -> "$path: undefinedxundefined too_small"
                        else -> null
                    }
                }
            }
        }

        assertTrue(
            "正式 Skill 视觉资源不完整或不合格：" + issues.joinToString(),
            issues.isEmpty(),
        )
    }

    @Test
    fun productionVisualKinds_are38PortraitsAnd6Tools() {
        val kinds = officialCatalog.skills.groupingBy(::officialSkillVisualKind).eachCount()

        assertEquals(38, kinds[OfficialSkillVisualKind.PORTRAIT])
        assertEquals(6, kinds[OfficialSkillVisualKind.TOOL])
    }

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: File("app/src/main/assets/$path")
}
