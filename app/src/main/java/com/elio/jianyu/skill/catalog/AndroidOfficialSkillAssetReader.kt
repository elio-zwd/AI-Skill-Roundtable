package com.elio.jianyu.skill.catalog

import android.content.Context
import com.elio.jianyu.skill.SkillLoader
import java.io.BufferedReader
import java.io.InputStreamReader

/** 只读取 APK 内版本化 Skill 资产，不访问网络、数据库或开发机路径。 */
class AndroidOfficialSkillAssetReader(
    context: Context,
) : OfficialSkillAssetReader {
    private val assets = context.applicationContext.assets

    override fun read(assetPath: String): OfficialSkillAssetReadResult {
        val parent = assetPath.substringBeforeLast('/', missingDelimiterValue = "")
        val fileName = assetPath.substringAfterLast('/')
        val exists = runCatching {
            parent.isNotBlank() && assets.list(parent)?.contains(fileName) == true
        }.getOrDefault(false)
        if (!exists) return OfficialSkillAssetReadResult.Missing

        return try {
            val content = assets.open(assetPath).use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    reader.readText()
                }
            }
            OfficialSkillAssetReadResult.Success(
                SkillLoader.stripYamlFrontmatter(content),
            )
        } catch (_: Exception) {
            OfficialSkillAssetReadResult.Unreadable
        }
    }
}
