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
    fun packagedManifestMatchesPublishedCoreAndRepositoryKnowledge() {
        val publication = JSONObject(readAsset("official_skill_execution_manifest_v2.json"))
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

        val publishedSkills = publication.getJSONArray("skills")
        assertEquals(44, publishedSkills.length())
        assertEquals(publishedSkills.length(), manifestSkills.length())
        assertEquals(manifestSkills.length(), bySkillId.size)

        val documentIds = mutableSetOf<String>()
        val chunkIds = mutableSetOf<String>()
        var nextOffset = 0
        repeat(manifestSkills.length()) { skillIndex ->
            val skill = manifestSkills.getJSONObject(skillIndex)
            val documents = skill.getJSONArray("documents")
            repeat(documents.length()) { documentIndex ->
                val document = documents.getJSONObject(documentIndex)
                assertEquals(skill.getString("skillId"), document.getString("skillId"))
                assertTrue(documentIds.add(document.getString("documentId")))
                val chunks = document.getJSONArray("chunks")
                if (document.getString("type") == "KNOWLEDGE") {
                    assertTrue(chunks.length() > 0)
                }
                repeat(chunks.length()) { chunkIndex ->
                    val chunk = chunks.getJSONObject(chunkIndex)
                    assertTrue(chunkIds.add(chunk.getString("chunkId")))
                    assertEquals(document.getString("documentId"), chunk.getString("documentId"))
                    assertEquals(nextOffset, chunk.getInt("vectorOffsetBytes"))
                    assertEquals(768, chunk.getInt("vectorLength"))
                    nextOffset += 768 * 4
                }
            }
        }
        assertEquals(nextOffset, indexBytes.size)

        repeat(publishedSkills.length()) { index ->
            val published = publishedSkills.getJSONObject(index)
            val skillId = published.getString("id")
            val packaged = bySkillId[skillId]
                ?: error("Missing Skill Knowledge manifest entry for $skillId")
            val documents = packaged.getJSONArray("documents")
            assertTrue(documents.length() > 0)

            var coreCount = 0
            repeat(documents.length()) { documentIndex ->
                val document = documents.getJSONObject(documentIndex)
                val relativePath = document.getString("relativePath")
                val assetPath = document.getString("assetPath")
                val content = readAsset(assetPath).normalizeNewlines()
                assertEquals(sha256(content), document.getString("contentHash"))

                if (relativePath.equals("SKILL.md", ignoreCase = true)) {
                    coreCount++
                    assertEquals("CORE", document.getString("type"))
                    assertFalse(document.getBoolean("retrievalEligible"))
                    assertEquals(published.getString("assetPath"), assetPath)
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
            assertEquals("Skill $skillId must contain one current CORE SKILL.md", 1, coreCount)
        }

        assertTrue(hasKnowledge(bySkillId.getValue("richard_feynman")))
        assertTrue(hasKnowledge(bySkillId.getValue("charlie_munger")))
        assertTrue(hasKnowledge(bySkillId.getValue("zhang_xuefeng")))

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

    private fun hasKnowledge(skill: JSONObject): Boolean {
        val documents = skill.getJSONArray("documents")
        repeat(documents.length()) { index ->
            if (documents.getJSONObject(index).getString("type") == "KNOWLEDGE") {
                return true
            }
        }
        return false
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
