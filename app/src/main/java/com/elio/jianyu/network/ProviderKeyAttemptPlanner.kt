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
}
