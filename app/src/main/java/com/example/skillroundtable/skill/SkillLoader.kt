package com.example.skillroundtable.skill

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 技能模板加载工具，用于从 Assets 读取技能 Markdown 并去除 YAML frontmatter 头部信息。
 */
object SkillLoader {

    /**
     * 从 assets 目录读取指定路径的技能文件，并自动剥离 YAML frontmatter 头部。
     *
     * @param context Android 上下文
     * @param assetPath asset 文件相对路径，例如 "skills/elon_musk.md"
     * @return 剥离头部后的纯 Markdown 文本
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
