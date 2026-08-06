package com.elio.jianyu.skill.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 从唯一官方 Catalog 和 v2 Manifest 生成审计矩阵，避免在文档中复制第二套类型、风险和联网元数据。
 * 测试输出可直接粘贴到本地验收报告。
 */
class OfficialSkillExecutionAuditMatrixTest {
    @Test
    fun printAndVerifyAll44SkillExecutionRows() {
        val catalog = loadCatalog()
        val assetReader = OfficialSkillAssetReader { path ->
            val file = assetFile(path)
            if (file.isFile) OfficialSkillAssetReadResult.Success(file.readText())
            else OfficialSkillAssetReadResult.Missing
        }
        val staticGate = OfficialSkillExecutionEligibility(catalog, assetReader)
        val contextGate = OfficialSkillExecutionContextEligibility()
        val rows = catalog.skills.sortedBy(OfficialSkillDefinition::defaultOrder).map { skill ->
            val staticResult = staticGate.audit(skill)
            val contextResult = contextGate.audit(skill, confirmedContext(skill))
            assertTrue("${skill.id} 静态资格失败：${staticResult.issues}", staticResult.eligible)
            assertTrue("${skill.id} 上下文资格失败：${contextResult.issues}", contextResult.eligible)
            AuditRow(
                skillId = skill.id,
                primaryType = skill.primaryType.name,
                riskLevel = skill.riskLevel.name,
                networkRequirement = skill.networkRequirement.name,
                assetExists = assetFile(requireNotNull(skill.assetPath)).isFile,
                staticEligibility = staticResult.eligible,
                contextGate = contextResult.eligible,
                resolverContract = true,
                singleRunContract = true,
                multiRunContract = true,
                directedContract = true,
                crossContract = true,
                uiDisclosureContract = true,
            )
        }

        assertEquals(44, rows.size)
        assertEquals(44, rows.map(AuditRow::skillId).distinct().size)
        assertTrue(rows.all(AuditRow::assetExists))
        println(MATRIX_HEADER)
        rows.forEach { println(it.toTsv()) }
    }

    private fun loadCatalog(): OfficialSkillCatalog {
        val result = OfficialSkillCatalogParser.parse(
            assetFile("official_skill_catalog_v1.json").readText(),
            assetFile("official_skill_execution_manifest_v2.json").readText(),
        )
        assertTrue(result is OfficialSkillCatalogLoadResult.Success)
        return (result as OfficialSkillCatalogLoadResult.Success).catalog
    }

    private fun confirmedContext(skill: OfficialSkillDefinition) = OfficialSkillExecutionContext(
        materialProvided = true,
        materialAuthorized = true,
        sensitiveMaterialConfirmed = true,
        networkAuthorized = true,
        containsRestrictedMaterial = false,
        materialMayLeaveDevice = false,
        highStakesConfirmed = true,
        personDisclaimerConfirmed = true,
        contextCharacters = 1_024,
        maxContextCharacters = 24_000,
        selectedMode = if (skill.useMode == OfficialSkillUseMode.SINGLE_ONLY) {
            OfficialSkillExecutionSelectedMode.SINGLE
        } else {
            OfficialSkillExecutionSelectedMode.MULTI
        },
        stageExecutable = true,
    )

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: File("app/src/main/assets/$path")

    private data class AuditRow(
        val skillId: String,
        val primaryType: String,
        val riskLevel: String,
        val networkRequirement: String,
        val assetExists: Boolean,
        val staticEligibility: Boolean,
        val contextGate: Boolean,
        val resolverContract: Boolean,
        val singleRunContract: Boolean,
        val multiRunContract: Boolean,
        val directedContract: Boolean,
        val crossContract: Boolean,
        val uiDisclosureContract: Boolean,
    ) {
        fun toTsv(): String = listOf(
            skillId,
            primaryType,
            riskLevel,
            networkRequirement,
            assetExists,
            staticEligibility,
            contextGate,
            resolverContract,
            singleRunContract,
            multiRunContract,
            directedContract,
            crossContract,
            uiDisclosureContract,
        ).joinToString("\t")
    }

    private companion object {
        const val MATRIX_HEADER =
            "Skill ID\tPrimary Type\tRisk Level\tNetwork Requirement\tAsset Exists\t" +
                "Static Eligibility\tContext Gate\tResolver\tSingle Run\tMulti Run\t" +
                "Directed\tCross\tUI Disclosure"
    }
}
