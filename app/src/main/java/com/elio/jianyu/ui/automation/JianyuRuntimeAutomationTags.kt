package com.elio.jianyu.ui.automation

/** 数据库维护宿主的稳定静态标签；不包含正文、路径、异常或其他用户数据。 */
object JianyuRuntimeAutomationTags {
    const val MAINTENANCE = "app_runtime_maintenance"
    const val UNAVAILABLE = "app_runtime_unavailable"
    const val RETRY = "app_runtime_retry"

    val frozenStaticTags: List<String> = listOf(
        MAINTENANCE,
        UNAVAILABLE,
        RETRY,
    )
}
