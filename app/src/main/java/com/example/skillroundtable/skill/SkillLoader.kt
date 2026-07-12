package com.example.skillroundtable.skill

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 技能配置文件对应的实体结构，用于反序列化 JSON 配置。
 */
@Serializable
data class SkillConfig(
    val id: String,
    val name: String,
    val avatar: String,
    val tagline: String,
    val systemPrompt: String = "",
    val skillAssetPath: String,
    val order: Int,
    val isActive: Boolean = true,
    val skillName: String = "",
    val skillDescription: String = "",
    val descriptionVector: List<Float> = emptyList() // 768维浮点特征向量
)

/**
 * 技能模板与配置文件加载工具，支持解析 JSON 角色配置以及提取 Markdown 技能包。
 */
object SkillLoader {

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    /**
     * 读取并解析 assets 下的技能 JSON 配置文件。
     *
     * @param context Android 上下文
     * @param assetPath JSON 文件的相对路径，默认为 "skills_config.json"
     * @return 解析后的技能配置列表
     */
    fun loadSkillsConfig(context: Context, assetPath: String = "skills_config.json"): List<SkillConfig> {
        return try {
            context.assets.open(assetPath).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    val jsonStr = reader.readText()
                    jsonParser.decodeFromString<List<SkillConfig>>(jsonStr)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * 罗列 assets 指定目录下的所有文件名。
     */
    fun listFilesInAssetDir(context: Context, path: String): List<String> {
        return try {
            context.assets.list(path)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 根据 Broker 选择的文件列表，动态读取并拼装对应的 examples/ 或 references/ 内容。
     */
    fun loadSelectedFiles(
        context: Context,
        skillFolder: String,
        selectedFiles: List<String>,
        isExample: Boolean
    ): String {
        if (selectedFiles.isEmpty()) return ""
        val sb = StringBuilder()
        val subDir = if (isExample) "examples" else "references"
        val header = if (isExample) "\n\n## Few-Shot Selected Examples\n" else "\n\n## Selected Reference Knowledge\n"
        
        sb.append(header)
        for (fileName in selectedFiles) {
            val assetPath = "skills/$skillFolder/$subDir/$fileName"
            try {
                context.assets.open(assetPath).use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                        val fileContent = reader.readText()
                        sb.append("\n### Selected File: $fileName\n")
                        sb.append(fileContent)
                        sb.append("\n")
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return sb.toString()
    }

    /**
     * 从 assets 目录读取指定路径的技能主文件并剥离头部 YAML frontmatter。
     *
     * @param context Android 上下文
     * @param assetPath asset 文件相对路径，例如 "skills/elon-musk-skill-main/SKILL.md"
     * @return 剥离头部 YAML frontmatter 后的 System Prompt 文本
     */
    fun loadSkill(context: Context, assetPath: String): String {
        val content = try {
            context.assets.open(assetPath).use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
        return stripYamlFrontmatter(content)
    }

    /**
     * 剥离文本顶部的 YAML frontmatter。
     * YAML frontmatter 被包裹在文件开头的第一个和第二个 "---" 之间。
     *
     * @param content 原始 Markdown 文本
     * @return 剥离后的文本
     */
    fun stripYamlFrontmatter(content: String): String {
        val lines = content.lines()
        if (lines.isEmpty()) return ""

        val trimmedFirst = lines.first().trim()
        if (trimmedFirst != "---") {
            return content
        }

        var secondMarkerIndex = -1
        for (i in 1 until lines.size) {
            if (lines[i].trim() == "---") {
                secondMarkerIndex = i
                break
            }
        }

        if (secondMarkerIndex != -1) {
            val remainingLines = lines.subList(secondMarkerIndex + 1, lines.size)
            return remainingLines.joinToString("\n").trim()
        }

        return content
    }
}
