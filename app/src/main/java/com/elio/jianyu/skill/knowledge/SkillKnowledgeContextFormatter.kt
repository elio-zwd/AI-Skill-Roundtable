package com.elio.jianyu.skill.knowledge

object SkillKnowledgeContextFormatter {
    fun format(result: SkillKnowledgeRetrievalResult.Available): String = buildString {
        if (result.knowledgeMap.isNotBlank()) {
            append("=== Skill Knowledge Map ===\n")
            append(result.knowledgeMap.trim())
        }
        if (result.hits.isNotEmpty()) {
            if (isNotEmpty()) append("\n\n")
            append("=== Retrieved Skill Knowledge ===\n")
            result.hits
                .sortedBy { it.retrievalOrder }
                .forEachIndexed { index, hit ->
                    if (index > 0) append("\n\n")
                    append("[source: ")
                    append(hit.relativePath)
                    if (hit.headingPath.isNotBlank()) {
                        append("#")
                        append(hit.headingPath)
                    }
                    append("]\n")
                    append(hit.content.trim())
                }
        }
    }
}
