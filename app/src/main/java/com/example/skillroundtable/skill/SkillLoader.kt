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
 * 技能模板与配置文件加载工具，支持解析 JSON 角色配置以及提取 Markdown 技能包并自动拼合 Examples 和 References。
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
     * 递归遍历 assets 下的指定目录，寻找所有以 .md 结尾的文件路径
     */
    private fun findMdFiles(context: Context, path: String): List<String> {
        val files = mutableListOf<String>()
        try {
            val list = context.assets.list(path)
            if (list.isNullOrEmpty()) {
                // 如果是文件或者空目录
                if (path.endsWith(".md", ignoreCase = true)) {
                    files.add(path)
                }
            } else {
                // 如果是目录，递归遍历子项
                for (item in list) {
                    val subPath = if (path.isEmpty()) item else "$path/$item"
                    files.addAll(findMdFiles(context, subPath))
                }
            }
        } catch (e: Exception) {
            // 发生异常时，如果是以 .md 结尾的尝试判断是否是文件
            if (path.endsWith(".md", ignoreCase = true)) {
                files.add(path)
            }
        }
        return files
    }

    /**
     * 从 assets 目录读取指定路径的技能主文件并递归拼合 examples/ 与 references/ 子目录下所有 .md 文件。
     *
     * @param context Android 上下文
     * @param assetPath asset 文件相对路径，例如 "skills/elon-musk-skill-main/SKILL.md"
     * @return 拼合并剥离头部 YAML frontmatter 后的完整 System Prompt 文本
     */
    fun loadSkill(context: Context, assetPath: String): String {
        // 1. 读取基础的 SKILL.md
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
        val basePrompt = stripYamlFrontmatter(content)

        // 获取技能的根文件夹路径，例如 "skills/elon-musk-skill-main"
        val parentDir = assetPath.substringBeforeLast("/", "")
        if (parentDir.isEmpty()) {
            return basePrompt
        }

        val finalPrompt = StringBuilder(basePrompt)

        // 2. 递归加载 examples 目录下的 Few-Shot 示例
        val examplesDir = "$parentDir/examples"
        val exampleFiles = findMdFiles(context, examplesDir).sorted()
        if (exampleFiles.isNotEmpty()) {
            finalPrompt.append("\n\n## Few-Shot Conversation Examples\n")
            for (file in exampleFiles) {
                try {
                    context.assets.open(file).use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                            val fileContent = reader.readText()
                            val fileName = file.substringAfterLast("/")
                            finalPrompt.append("\n### Example: $fileName\n")
                            finalPrompt.append(fileContent)
                            finalPrompt.append("\n")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 3. 递归加载 references 目录下的核心参考文献与研究资料
        val referencesDir = "$parentDir/references"
        val referenceFiles = findMdFiles(context, referencesDir).sorted()
        if (referenceFiles.isNotEmpty()) {
            finalPrompt.append("\n\n## Core Reference Knowledge & Research\n")
            for (file in referenceFiles) {
                try {
                    context.assets.open(file).use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                            val fileContent = reader.readText()
                            val fileName = file.substringAfterLast("/")
                            finalPrompt.append("\n### Reference: $fileName\n")
                            finalPrompt.append(fileContent)
                            finalPrompt.append("\n")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return finalPrompt.toString().trim()
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
