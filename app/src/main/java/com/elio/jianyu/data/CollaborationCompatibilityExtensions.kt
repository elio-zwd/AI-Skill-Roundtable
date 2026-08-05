package com.elio.jianyu.data

/**
 * 为协作编排层保留显式导入符号；实际调用仍由 [JianyuRepository] 成员接口处理。
 */
@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
suspend fun JianyuRepository.listRunContextUsage(
    runId: String,
): RepositoryResult<List<ContextUsageSnapshot>> = this.listRunContextUsage(runId)
