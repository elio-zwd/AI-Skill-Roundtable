package com.elio.jianyu.skill.catalog

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 从唯一官方 Catalog 和 v2 Manifest 生成审计矩阵，避免在文档中复制第二套类型、风险和联网元数据。
 *
 * 本测试只对 JVM 内实际执行的 Asset、静态资格和上下文资格输出 PASS；Resolver、运行、协作和 UI
 * 列输出负责验证它们的测试契约名称，必须等对应 Android/设备用例真实执行后才能在验收报告改为 PASS。
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
            assertTrue("${skill.id} 正式资产不存在", assetFile(requireNotNull(skill.assetPath)).isFile)
            AuditRow(
                skillId = skill.id,
                primaryType = skill.primaryType.name,
                riskLevel = skill.riskLevel.name,
                networkRequirement = skill.networkRequirement.name,
                assetExists = "PASS_JVM",
                staticEligibility = "PASS_JVM",
                contextGate = "PASS_JVM",
                resolver = "OfficialSkillExecutionManifestV2AndroidTest",
                singleRun = "ExecutionRunCoordinatorTest",
                multiRun = "ExecutionRunCoordinatorTest",
                directed = "IssueCollaborationCoordinatorTest",
                cross = "IssueCollaborationCoordinatorTest",
                uiDisclosure = "HomeScreenTest",
            )
        }

        assertEquals(44, rows.size)
        assertEquals(44, rows.map(AuditRow::skillId).distinct().size)
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
        val assetExists: String,
        val staticEligibility: String,
        val contextGate: String,
        val resolver: String,
        val singleRun: String,
        val multiRun: String,
        val directed: String,
        val cross: String,
        val uiDisclosure: String,
    ) {
        fun toTsv(): String = listOf(
            skillId,
            primaryType,
            riskLevel,
            networkRequirement,
            assetExists,
            staticEligibility,
            contextGate,
            resolver,
            singleRun,
            multiRun,
            directed,
            cross,
            uiDisclosure,
        ).joinToString("\t")
    }

    private companion object {
        const val MATRIX_HEADER =
            "Skill ID\tPrimary Type\tRisk Level\tNetwork Requirement\tAsset Exists\t" +
                "Static Eligibility\tContext Gate\tResolver\tSingle Run\tMulti Run\t" +
                "Directed\tCross\tUI Disclosure"
    }
}
