package com.elio.jianyu.skill.knowledge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillKnowledgeAssetContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun packagedManifestMatchesCatalogMarkdownAndBinaryIndex() {
        val catalog = JSONObject(readAsset("official_skill_catalog_v1.json"))
        val manifest = JSONObject(readAsset("skill_knowledge/manifest.json"))
        val indexBytes = context.assets.open("skill_knowledge/index-v1.bin").use { it.readBytes() }

        assertEquals(1, manifest.getInt("schemaVersion"))
        assertEquals("gemini-embedding-2", manifest.getString("model"))
        assertEquals(768, manifest.getInt("vectorDimension"))
        assertEquals("float32-le", manifest.getString("vectorEncoding"))

        val manifestSkills = manifest.getJSONArray("skills")
        val bySkillId = buildMap<String, JSONObject> {
            repeat(manifestSkills.length()) { index ->
                val item = manifestSkills.getJSONObject(index)
                put(item.getString("skillId"), item)
            }
        }

        val catalogSkills = catalog.getJSONArray("skills")
        repeat(catalogSkills.length()) { index ->
            val skill = catalogSkills.getJSONObject(index)
            val availability = skill.getJSONObject("availability")
            if (!availability.getBoolean("hasAsset")) return@repeat

            val skillId = skill.getString("id")
            val packaged = bySkillId[skillId]
                ?: error("Missing Skill Knowledge manifest entry for $skillId")
            val documents = packaged.getJSONArray("documents")
            assertTrue(documents.length() > 0)

            var foundCore = false
            repeat(documents.length()) { documentIndex ->
                val document = documents.getJSONObject(documentIndex)
                val relativePath = document.getString("relativePath")
                val assetPath = packaged.getString("assetRoot") + "/" + relativePath
                val content = readAsset(assetPath).normalizeNewlines()
                assertEquals(sha256(content), document.getString("contentHash"))

                if (relativePath.equals("SKILL.md", ignoreCase = true)) {
                    foundCore = true
                    assertEquals("CORE", document.getString("type"))
                    assertFalse(document.getBoolean("retrievalEligible"))
                }

                val chunks = document.getJSONArray("chunks")
                repeat(chunks.length()) { chunkIndex ->
                    val chunk = chunks.getJSONObject(chunkIndex)
                    assertEquals(768, chunk.getInt("vectorLength"))
                    val offset = chunk.getInt("vectorOffsetBytes")
                    assertTrue(offset >= 0)
                    assertTrue(offset + 768 * 4 <= indexBytes.size)
                }
            }
            assertTrue("Skill $skillId must contain CORE SKILL.md", foundCore)
        }

        val maxEnd = bySkillId.values
            .flatMap { skill ->
                val documents = skill.getJSONArray("documents")
                buildList {
                    repeat(documents.length()) { documentIndex ->
                        val chunks = documents.getJSONObject(documentIndex).getJSONArray("chunks")
                        repeat(chunks.length()) { chunkIndex ->
                            val chunk = chunks.getJSONObject(chunkIndex)
                            add(chunk.getInt("vectorOffsetBytes") + 768 * 4)
                        }
                    }
                }
            }
            .maxOrNull() ?: 0
        assertEquals(maxEnd, indexBytes.size)
    }

    private fun readAsset(path: String): String =
        context.assets.open(path).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private fun String.normalizeNewlines(): String =
        replace("\r\n", "\n").replace('\r', '\n')

    private fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
