package com.elio.jianyu.result

enum class ArtifactType(
    val storageValue: String,
    val displayName: String,
) {
    GENERAL_SUMMARY("general_summary", "通用阶段总结"),
    ACTION_PLAN("action_plan", "行动方案"),
    DECISION_RECORD("decision_record", "决策记录"),
    KNOWLEDGE_NOTE("knowledge_note", "知识笔记"),
    DELIVERABLE("deliverable", "交付稿"),
    ;

    companion object {
        val DEFAULT: ArtifactType = GENERAL_SUMMARY

        fun fromStorageValue(value: String): ArtifactType? =
            entries.firstOrNull { it.storageValue == value }
    }
}

const val ARTIFACT_CONTENT_FORMAT_MARKDOWN = "markdown"
