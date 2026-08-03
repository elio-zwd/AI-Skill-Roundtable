package com.elio.jianyu.ui.navigation

private val STABLE_ID_PATTERN = Regex("[A-Za-z0-9._-]{1,128}")

enum class ResourceTab(val routeValue: String) {
    MATERIALS("materials"),
    ARTIFACTS("artifacts");

    companion object {
        fun fromRouteValue(value: String?): ResourceTab =
            entries.firstOrNull { it.routeValue == value } ?: MATERIALS
    }
}

object JianyuNavigationRoutes {
    const val ISSUES_GRAPH = "issues_graph"
    const val SKILLS_GRAPH = "skills_graph"

    const val ISSUE_ID_ARGUMENT = "issueId"
    const val STAGE_ID_ARGUMENT = "stageId"
    const val SKILL_ID_ARGUMENT = "skillId"
    const val RESOURCE_TAB_ARGUMENT = "tab"

    const val ISSUE_DETAIL_PATTERN = "issues/{$ISSUE_ID_ARGUMENT}?$STAGE_ID_ARGUMENT={$STAGE_ID_ARGUMENT}"
    const val SKILL_DETAIL_PATTERN = "skills/{$SKILL_ID_ARGUMENT}"
    const val ISSUE_DEEP_LINK_PATTERN = "jianyu://issues/{$ISSUE_ID_ARGUMENT}?$STAGE_ID_ARGUMENT={$STAGE_ID_ARGUMENT}"
    const val SKILL_DEEP_LINK_PATTERN = "jianyu://skills/{$SKILL_ID_ARGUMENT}"
    const val RESOURCES_DEEP_LINK_PATTERN = "jianyu://resources?$RESOURCE_TAB_ARGUMENT={$RESOURCE_TAB_ARGUMENT}"

    fun issue(issueId: String, stageId: String? = null): String {
        val safeIssueId = requireStableId("issueId", issueId)
        return if (stageId == null) {
            "issues/$safeIssueId"
        } else {
            "issues/$safeIssueId?stageId=${requireStableId("stageId", stageId)}"
        }
    }

    fun skillDetail(skillId: String): String =
        "skills/${requireStableId("skillId", skillId)}"

    fun resources(tab: ResourceTab): String =
        "resources?tab=${tab.routeValue}"

    fun isStableId(value: String?): Boolean =
        value != null && STABLE_ID_PATTERN.matches(value)

    private fun requireStableId(name: String, value: String): String {
        require(isStableId(value)) { "$name 必须是 1～128 位稳定 ID" }
        return value
    }
}
