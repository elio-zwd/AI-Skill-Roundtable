package com.elio.skillroundtable.network.keys

import android.content.Context
import com.elio.skillroundtable.network.ApiKeyPool

object ApiKeyScheduler {
    /**
     * 为当前提问请求分配一个确定性、不重复的 Key 尝试计划。
     *
     * 规则：
     * 1. 过滤禁用、无效和在冷却中的 Key；
     * 2. 首先使用 sessionId 绑定的 Key 或传入的首选 Key（如果它可用）；
     * 3. 跨会话轮转：上次成功使用的 lastUsedKeyId 放到计划最末尾；
     * 4. 保证计划中每个 KeyId 最多只出现一次，避免无意义的重复换 Key 尝试。
     */
    fun createAttemptPlan(
        context: Context,
        sessionId: Long,
        preferredKeyId: String? = null
    ): List<ApiKeyLease> {
        val available = ApiKeyPool.getAvailableKeys(context).map { info ->
            ApiKeyLease(
                keyId = info.id,
                displayName = info.account,
                secret = info.key,
                source = info.source
            )
        }
        if (available.isEmpty()) return emptyList()

        val boundKeyId = context.getSharedPreferences("gemini_api_key_prefs", Context.MODE_PRIVATE)
            .getString("session_key_$sessionId", null)

        val lastUsedKeyId = ApiKeyPool.getLastUsedKeyId(context)

        val preferred = available.firstOrNull { it.keyId == preferredKeyId }
        val bound = available.firstOrNull { it.keyId == boundKeyId && it.keyId != preferredKeyId }
        
        val others = available.filter { it.keyId != preferredKeyId && it.keyId != boundKeyId }
        
        val lastUsed = others.firstOrNull { it.keyId == lastUsedKeyId }
        val restOthers = others.filter { it.keyId != lastUsedKeyId }.sortedBy { it.keyId }

        val result = mutableListOf<ApiKeyLease>()
        if (preferred != null) {
            result.add(preferred)
        }
        if (bound != null) {
            result.add(bound)
        }
        result.addAll(restOthers)
        if (lastUsed != null) {
            result.add(lastUsed)
        }

        return result.distinctBy { it.keyId }
    }
}
