package com.elio.jianyu.skill.knowledge

import android.content.Context
import com.elio.jianyu.network.GEMINI_SKILL_KNOWLEDGE_EMBEDDING_MODEL
import com.elio.jianyu.network.SKILL_KNOWLEDGE_EMBEDDING_DIMENSION
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlinx.serialization.json.Json

interface SkillKnowledgeAssetReader {
    fun readBytes(path: String): ByteArray
}

private class AndroidSkillKnowledgeAssetReader(
    context: Context,
) : SkillKnowledgeAssetReader {
    private val assets = context.applicationContext.assets

    override fun readBytes(path: String): ByteArray =
        assets.open(path).use { it.readBytes() }
}

interface SkillKnowledgeRepository {
    fun listSkills(): List<SkillKnowledgeSkill>
    fun listDocuments(skillId: String): List<SkillKnowledgeDocument>
    fun loadDocumentContent(skillId: String, documentId: String): String
    fun loadChunkContent(
        skillId: String,
        document: SkillKnowledgeDocument,
        chunk: SkillKnowledgeChunk,
    ): String
    fun loadVector(chunk: SkillKnowledgeChunk): FloatArray
}

class SkillKnowledgeAssetRepository(
    private val assetReader: SkillKnowledgeAssetReader,
) : SkillKnowledgeRepository {
    constructor(context: Context) : this(AndroidSkillKnowledgeAssetReader(context))

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val manifest: SkillKnowledgeManifest by lazy {
        val decoded = json.decodeFromString<SkillKnowledgeManifest>(
            assetReader.readBytes(MANIFEST_PATH).toString(Charsets.UTF_8),
        )
        check(decoded.schemaVersion == 1) { "不支持的 Skill Knowledge manifest 版本" }
        check(decoded.model == GEMINI_SKILL_KNOWLEDGE_EMBEDDING_MODEL) {
            "Skill Knowledge embedding 模型不匹配"
        }
        check(decoded.vectorDimension == SKILL_KNOWLEDGE_EMBEDDING_DIMENSION) {
            "Skill Knowledge embedding 维度不匹配"
        }
        check(decoded.vectorEncoding == "float32-le") {
            "Skill Knowledge vector encoding 不匹配"
        }
        decoded
    }

    private val indexBytes: ByteArray by lazy {
        assetReader.readBytes(INDEX_PATH)
    }

    override fun listSkills(): List<SkillKnowledgeSkill> = manifest.skills

    override fun listDocuments(skillId: String): List<SkillKnowledgeDocument> =
        manifest.skills
            .firstOrNull { it.skillId == skillId }
            ?.documents
            .orEmpty()

    override fun loadDocumentContent(skillId: String, documentId: String): String {
        val skill = manifest.skills.firstOrNull { it.skillId == skillId }
            ?: error("未知 Skill Knowledge owner: $skillId")
        val document = skill.documents.firstOrNull { it.documentId == documentId }
            ?: error("未知 Skill Knowledge document: $documentId")
        val content = assetReader.readBytes(document.assetPath)
            .toString(Charsets.UTF_8)
            .normalizeNewlines()
        check(sha256(content) == document.contentHash) {
            "Skill Knowledge 文档与预生成索引不一致"
        }
        return content
    }

    override fun loadChunkContent(
        skillId: String,
        document: SkillKnowledgeDocument,
        chunk: SkillKnowledgeChunk,
    ): String {
        require(document.skillId == skillId) { "不得跨 Skill 读取 Knowledge chunk" }
        require(chunk.documentId == document.documentId) { "Knowledge chunk 不属于该文档" }
        val content = loadDocumentContent(skillId, document.documentId)
        // 生成器的字符位置按 Unicode code point 计数；Kotlin substring 使用 UTF-16 下标。
        val codePointCount = content.codePointCount(0, content.length)
        check(chunk.startCharacter in 0 until codePointCount) {
            "Knowledge chunk 起点越界"
        }
        check(chunk.endCharacter in (chunk.startCharacter + 1)..codePointCount) {
            "Knowledge chunk 终点越界"
        }
        val startOffset = content.offsetByCodePoints(0, chunk.startCharacter)
        val endOffset = content.offsetByCodePoints(0, chunk.endCharacter)
        return content.substring(startOffset, endOffset)
    }

    override fun loadVector(chunk: SkillKnowledgeChunk): FloatArray {
        check(chunk.vectorLength == manifest.vectorDimension) {
            "Knowledge chunk vector 维度不匹配"
        }
        val byteCount = manifest.vectorDimension * Float.SIZE_BYTES
        val offset = chunk.vectorOffsetBytes
        check(offset >= 0 && offset + byteCount <= indexBytes.size) {
            "Knowledge chunk vector offset 越界"
        }
        val buffer = ByteBuffer.wrap(indexBytes, offset, byteCount)
            .order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(manifest.vectorDimension) { buffer.getFloat() }
    }

    private fun String.normalizeNewlines(): String =
        replace("\r\n", "\n").replace('\r', '\n')

    private fun sha256(content: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte ->
                "%02x".format(byte.toInt() and 0xff)
            }

    private companion object {
        const val MANIFEST_PATH = "skill_knowledge/manifest.json"
        const val INDEX_PATH = "skill_knowledge/index-v1.bin"
    }
}
