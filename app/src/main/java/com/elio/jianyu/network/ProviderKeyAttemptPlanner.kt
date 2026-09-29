package com.elio.jianyu.network

import com.elio.jianyu.network.keys.ApiKeyLease

/** 纯排序规则：不读取 Android 状态，也不持有任一提供商的密钥池。 */
object ProviderKeyAttemptPlanner {
    fun create(
        available: List<ApiKeyLease>,
        sessionBoundKeyId: String?,
        lastUsedKeyId: String?,
        preferredKeyId: String? = null,
    ): List<ApiKeyLease> {
        val preferred = available.firstOrNull { it.keyId == preferredKeyId }
        val bound = available.firstOrNull { it.keyId == sessionBoundKeyId && it.keyId != preferredKeyId }
        val others = available.filter { it.keyId != preferredKeyId && it.keyId != sessionBoundKeyId }
        val lastUsed = others.firstOrNull { it.keyId == lastUsedKeyId }
        return buildList {
            preferred?.let(::add)
            bound?.let(::add)
            addAll(others.filter { it.keyId != lastUsedKeyId }.sortedBy(ApiKeyLease::keyId))
            lastUsed?.let(::add)
        }.distinctBy(ApiKeyLease::keyId)
    }

    /**
     * 并行参与者按冻结位置稳定分摊首选 Key。
     * 只选择 preferred，不改变 create() 中原有的 session-bound / retry 排序语义。
     */
    fun preferredKeyIdForPosition(
        basePlan: List<ApiKeyLease>,
        participantPosition: Int,
    ): String? {
        if (basePlan.isEmpty()) return null
        val index = Math.floorMod(participantPosition, basePlan.size)
        return basePlan[index].keyId
    }
}
