package com.elio.jianyu.skill.role

import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogLoadResult
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogParser
import java.io.File
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
        val missing = officialCatalog.skills.mapNotNull { skill ->
            val path = officialSkillVisualAssetPath(skill)
            path.takeUnless { assetFile(path).isFile }
        }

        assertTrue(
            "缺少正式 Skill 视觉资源：" + missing.joinToString(),
            missing.isEmpty(),
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
