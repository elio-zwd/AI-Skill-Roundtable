package com.example.skillroundtable.network.keys

import android.content.Context
import com.example.skillroundtable.network.ApiKeyPool

object ApiKeyScheduler {
    /**
     * 为当前提问请求分配一个确定性、不重复的 Key 尝试计划。
     *
     * 规则：
     * 1. 过滤禁用、无效和在冷却中的 Key；
     * 2. 首先使用 sessionId 绑定的 Key 或传入的首选 Key（如果它可用）；
     * 3. 其余可用 Key 按照其 ID（或者列表中的确定性位置）进行排序；
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

        val primaryKeyId = preferredKeyId ?: boundKeyId

        val primary = available.firstOrNull { it.keyId == primaryKeyId }
        val rest = available.filter { it.keyId != primaryKeyId }

        val result = mutableListOf<ApiKeyLease>()
        if (primary != null) {
            result.add(primary)
        }
        // 将其它可用 key 按 ID 排序以保证确定性的轮转顺序
        result.addAll(rest.sortedBy { it.keyId })

        return result.distinctBy { it.keyId }
    }
}
