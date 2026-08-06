package com.elio.jianyu.skill.catalog

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialSkillExecutionManifestV2Test {
    private val baseSource by lazy { assetFile("official_skill_catalog_v1.json").readText() }
    private val v1Source by lazy { assetFile("official_skill_execution_batch_v1.json").readText() }
    private val v2Source by lazy { assetFile("official_skill_execution_manifest_v2.json").readText() }

    @Test
    fun v1HistoricalPublicationRemainsExactlyFourAndKeepsFirstBatchRestrictions() {
        val result = OfficialSkillCatalogParser.parse(baseSource, v1Source)
        assertTrue(result is OfficialSkillCatalogLoadResult.Success)
        val executable = (result as OfficialSkillCatalogLoadResult.Success)
            .catalog.skills.filter { it.availability.executable }

        assertEquals(
            listOf(
                "study-planner",
                "meeting-to-action",
                "report-proposal-writer",
                "research-fact-checker",
            ),
            executable.sortedBy { it.defaultOrder }.map { it.id },
        )
        assertTrue(executable.none { it.primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE })
        assertTrue(executable.none { it.riskLevel == OfficialSkillRiskLevel.HIGH_STAKES })
    }

    @Test
    fun v2PublishesExactlyTheStable44InCatalogOrder() {
        val result = OfficialSkillCatalogParser.parse(baseSource, v2Source)
        assertTrue(result is OfficialSkillCatalogLoadResult.Success)
        val catalog = (result as OfficialSkillCatalogLoadResult.Success).catalog

        assertEquals(44, catalog.skills.size)
        assertEquals((1..44).toList(), catalog.skills.map { it.defaultOrder })
        assertEquals(44, catalog.skills.count { it.availability.executable })
        assertTrue(catalog.skills.all { it.availability.hasAsset })
        assertTrue(catalog.skills.all { it.availability.discoverable })
        assertTrue(catalog.skills.all { it.availability.searchable })
        assertTrue(catalog.skills.all { it.availability.recommendable })
        assertTrue(catalog.skills.all { it.publicationStatus == OfficialSkillPublicationStatus.PUBLISHABLE })
        assertTrue(catalog.skills.all {
            it.sourceStatus == OfficialSkillSourceStatus.VERIFIED_IMPLEMENTATION_SOURCE
        })
    }

    @Test
    fun v2RejectsMissingDuplicateUnknownAndOrderMismatch() {
        val missing = v2Source.replaceFirst(
            Regex("\\s*\\{\\s*\\\"id\\\": \\\"original-expression-naturalizer\\\"[\\s\\S]*?\\n\\s*}\\n\\s*]"),
            "\n  ]",
        )
        val duplicate = v2Source.replaceFirst(
            "\"id\": \"elon_musk\"",
            "\"id\": \"zhang_xuefeng\"",
        )
        val unknown = v2Source.replaceFirst(
            "\"id\": \"elon_musk\"",
            "\"id\": \"unknown-person\"",
        )
        val orderMismatch = v2Source.replaceFirst(
            "\"expectedDefaultOrder\": 2",
            "\"expectedDefaultOrder\": 44",
        )

        assertFailureCode(OfficialSkillCatalogParser.parse(baseSource, missing), "invalid_execution_manifest_size")
        assertFailureCode(OfficialSkillCatalogParser.parse(baseSource, duplicate), "duplicate_execution_skill_id")
        assertFailureCode(OfficialSkillCatalogParser.parse(baseSource, unknown), "unknown_execution_skill")
        assertFailureCode(OfficialSkillCatalogParser.parse(baseSource, orderMismatch), "execution_order_mismatch")
    }

    @Test
    fun everyV2AssetExistsIsUniqueAndPassesStaticEligibility() {
        val result = OfficialSkillCatalogParser.parse(baseSource, v2Source)
        val catalog = (result as OfficialSkillCatalogLoadResult.Success).catalog
        val reader = OfficialSkillAssetReader { path ->
            val file = assetFile(path)
            if (!file.isFile) OfficialSkillAssetReadResult.Missing
            else OfficialSkillAssetReadResult.Success(file.readText())
        }
        val eligibility = OfficialSkillExecutionEligibility(catalog, reader)
        val hashes = linkedSetOf<String>()

        catalog.skills.forEach { skill ->
            val path = requireNotNull(skill.assetPath)
            val file = assetFile(path)
            assertTrue("${skill.id} 缺少正式资产 $path", file.isFile)
            val text = file.readText(Charsets.UTF_8)
            assertTrue("${skill.id} 资产为空", text.isNotBlank())
            assertFalse("${skill.id} 含 TODO", text.contains("TODO", ignoreCase = true))
            assertFalse("${skill.id} 含 TBD", text.contains("TBD", ignoreCase = true))
            assertFalse("${skill.id} 含 .env", text.contains(".env", ignoreCase = true))
            assertFalse("${skill.id} 含 API Key", text.contains("AIza", ignoreCase = true))
            assertFalse("${skill.id} 含 OpenAI 风格 Key", text.contains("sk-"))
            assertTrue("${skill.id} 静态资格失败", eligibility.audit(skill).eligible)
            assertTrue("${skill.id} 与其他资产正文完全重复", hashes.add(sha256(normalize(text))))
        }
        assertEquals(44, hashes.size)
    }

    @Test
    fun personHighStakesAndSpecialAssetsExposeRequiredSafetySections() {
        val catalog = (OfficialSkillCatalogParser.parse(baseSource, v2Source)
            as OfficialSkillCatalogLoadResult.Success).catalog

        val people = catalog.skills.filter { it.primaryType == OfficialSkillPrimaryType.PERSON_PERSPECTIVE }
        assertEquals(19, people.size)
        people.forEach { skill ->
            val text = assetFile(requireNotNull(skill.assetPath)).readText()
            assertTrue(text.contains("## AI 模拟身份声明"))
            assertTrue(text.contains("## 公开来源与时效边界"))
            assertTrue(text.contains("## 观点不确定性"))
            assertTrue(text.contains("## 不得冒充本人"))
            assertFalse(text.contains("我是${skill.nameZh}"))
            assertFalse(text.contains("本人授权"))
        }

        val highStakes = catalog.skills.filter {
            it.riskLevel == OfficialSkillRiskLevel.HIGH_STAKES ||
                it.riskLevel == OfficialSkillRiskLevel.URGENT
        }
        assertTrue(highStakes.size >= 17)
        highStakes.forEach { skill ->
            val text = assetFile(requireNotNull(skill.assetPath)).readText()
            assertTrue(text.contains("## 高后果边界"))
            assertTrue(text.contains("## 当前地区与时效"))
            assertTrue(text.contains("## 现实专业复核条件"))
            assertTrue(text.contains("## 紧急情况处理"))
        }

        val office = assetText(catalog, "office-document-productivity")
        assertTrue(office.contains("Markdown"))
        assertTrue(office.contains("纯文本"))
        assertFalse(office.contains("控制 Word"))
        assertFalse(office.contains("自动提交"))

        val naturalizer = assetText(catalog, "original-expression-naturalizer")
        assertTrue(naturalizer.contains("不规避 AI 检测"))
        assertTrue(naturalizer.contains("不协助学术作弊"))
        assertTrue(naturalizer.contains("不伪造事实"))
        assertTrue(naturalizer.contains("不冒充他人"))

        val patent = assetText(catalog, "patent-disclosure-organizer")
        assertTrue(patent.contains("禁止外传"))
        assertTrue(patent.contains("脱敏摘要"))

        val fortune = assetText(catalog, "culture-fortune-entertainment")
        assertTrue(fortune.contains("不得用于重大决策"))
    }

    @Test
    fun v1AndV2AreDifferentAuditablePublicationFacts() {
        assertNotEquals(v1Source, v2Source)
        assertTrue(v1Source.contains("jianyu-official-skill-execution-batch-v1"))
        assertTrue(v2Source.contains("jianyu-official-skill-execution-manifest-v2"))
        assertEquals(4, Regex("\\\"id\\\"").findAll(v1Source).count())
        assertEquals(44, Regex("\\\"id\\\"").findAll(v2Source).count())
    }

    private fun assetText(catalog: OfficialSkillCatalog, id: String): String =
        assetFile(requireNotNull(requireNotNull(catalog.findById(id)).assetPath)).readText()

    private fun assertFailureCode(result: OfficialSkillCatalogLoadResult, code: String) {
        assertTrue(result is OfficialSkillCatalogLoadResult.Failure)
        assertTrue((result as OfficialSkillCatalogLoadResult.Failure).message.contains(code))
    }

    private fun normalize(text: String): String = text
        .lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .joinToString("\n")

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun assetFile(path: String): File = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
    ).firstOrNull(File::isFile) ?: File("app/src/main/assets/$path")
}
