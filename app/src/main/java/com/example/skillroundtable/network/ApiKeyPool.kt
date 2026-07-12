package com.example.skillroundtable.network

import android.content.Context
import android.content.SharedPreferences

/**
 * 封装的 API 密钥信息实体。
 */
data class ApiKeyInfo(
    val id: String,
    val key: String,
    val account: String
)

/**
 * API Key 管理池，提供 10 个备用 Key 的轮询机制，以及 API 429 频控错误的 24 小时熔断保护。
 */
object ApiKeyPool {
    private const val PREFS_NAME = "gemini_api_key_prefs"
    private const val KEY_LAST_USED_ID = "last_used_key_id"
    private const val BAN_DURATION_MS = 24 * 60 * 60 * 1000L // 24小时

    // 内置 10 个来自 life-archive-app 的备用 Key
    val API_KEYS = listOf(
        ApiKeyInfo("w1", "REDACTED_GEMINI_API_KEY", "a1"),
        ApiKeyInfo("w2", "REDACTED_GEMINI_API_KEY", "a1"),
        ApiKeyInfo("w3", "REDACTED_GEMINI_API_KEY", "a1"),
        ApiKeyInfo("w4", "REDACTED_GEMINI_API_KEY", "a1"),
        ApiKeyInfo("w5", "REDACTED_GEMINI_API_KEY", "a1"),
        ApiKeyInfo("w6", "REDACTED_GEMINI_API_KEY", "a1"),
        ApiKeyInfo("w7", "REDACTED_GEMINI_API_KEY", "a1"),
        ApiKeyInfo("w8", "REDACTED_GEMINI_API_KEY", "a1"),
        ApiKeyInfo("w9", "REDACTED_GEMINI_API_KEY", "a1"),
        ApiKeyInfo("w10", "REDACTED_GEMINI_API_KEY", "a1")
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 将特定 Key 禁用 24 小时（熔断）
     */
    fun banKey(context: Context, keyId: String) {
        val expireTime = System.currentTimeMillis() + BAN_DURATION_MS
        getPrefs(context).edit()
            .putLong("ban_$keyId", expireTime)
            .apply()
    }

    /**
     * 记录上一次使用的 Key ID
     */
    fun setLastUsedKeyId(context: Context, keyId: String) {
        getPrefs(context).edit()
            .putString(KEY_LAST_USED_ID, keyId)
            .apply()
    }

    /**
     * 获取上一次使用的 Key ID
     */
    fun getLastUsedKeyId(context: Context): String? {
        return getPrefs(context).getString(KEY_LAST_USED_ID, null)
    }

    /**
     * 获取当前所有未处于熔断期的可用 Key
     */
    fun getAvailableKeys(context: Context): List<ApiKeyInfo> {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        
        return API_KEYS.filter { apiKey ->
            val banExpire = prefs.getLong("ban_${apiKey.id}", 0L)
            banExpire < now
        }
    }

    /**
     * 获取本次请求的可尝试 Key 顺序。
     * 如果可用 Key 的数量大于 1 且存在上一次成功使用的 Key ID，
     * 则优先尝试其他可用 Key，避免连续对同一个 Key 发起并发请求导致频控。
     */
    fun getKeyAttemptOrder(context: Context): List<ApiKeyInfo> {
        val availableKeys = getAvailableKeys(context)
        val lastUsedId = getLastUsedKeyId(context)

        if (availableKeys.size <= 1 || lastUsedId == null) {
            return availableKeys
        }

        val preferredKeys = availableKeys.filter { it.id != lastUsedId }
        val deferredKeys = availableKeys.filter { it.id == lastUsedId }

        return preferredKeys + deferredKeys
    }

    /**
     * 清空所有被熔断 Key 的状态（用于调试或后台状态重置）
     */
    fun clearBans(context: Context) {
        val editor = getPrefs(context).edit()
        API_KEYS.forEach { apiKey ->
            editor.remove("ban_${apiKey.id}")
        }
        editor.apply()
    }
}
