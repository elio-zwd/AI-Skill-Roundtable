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

data class ApiLog(
    val keyId: String,
    val model: String,
    val requestTime: Long,
    val responseTime: Long,
    val statusCode: Int,
    val errorMessage: String? = null,
    val prompt: String = ""
)

data class KeyStatus(
    val id: String,
    val isBanned: Boolean,
    val banExpireTime: Long,
    val remainingBanTimeMs: Long
)

/**
 * API Key 管理池，提供 10 个备用 Key 的轮询机制，以及 API 429 频控错误的 24 小时熔断保护。
 */
object ApiKeyPool {
    private const val PREFS_NAME = "gemini_api_key_prefs"
    private const val KEY_LAST_USED_ID = "last_used_key_id"
    private const val BAN_DURATION_MS = 24 * 60 * 60 * 1000L // 24小时

    val apiLogs = java.util.concurrent.CopyOnWriteArrayList<ApiLog>()

    fun addLog(log: ApiLog) {
        apiLogs.add(0, log)
        if (apiLogs.size > 50) {
            apiLogs.removeAt(apiLogs.size - 1)
        }
    }

    fun getKeyStatuses(context: Context): List<KeyStatus> {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        return API_KEYS.map { apiKey ->
            val banExpire = prefs.getLong("ban_${apiKey.id}", 0L)
            val isBanned = banExpire > now
            val remaining = if (isBanned) banExpire - now else 0L
            KeyStatus(apiKey.id, isBanned, banExpire, remaining)
        }
    }

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
     * 获取持久化保存的自定义 API Key
     */
    fun getCustomApiKey(context: Context): String? {
        return getPrefs(context).getString("custom_api_key", null)
    }

    /**
     * 持久化保存自定义 API Key
     */
    fun saveCustomApiKey(context: Context, key: String) {
        getPrefs(context).edit()
            .putString("custom_api_key", key)
            .apply()
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
     * 获取指定 Session 绑定的 Key。若无绑定或该 Key 已熔断，则自动在可用 Key 中分配绑定并返回。
     */
    fun getOrBindSessionKey(context: Context, sessionId: Long): ApiKeyInfo? {
        val prefs = getPrefs(context)
        val now = System.currentTimeMillis()
        
        // 1. 尝试读取该 session 当前绑定的 Key ID
        val boundKeyId = prefs.getString("session_key_$sessionId", null)
        if (boundKeyId != null) {
            val banExpire = prefs.getLong("ban_$boundKeyId", 0L)
            if (banExpire < now) {
                val keyInfo = API_KEYS.firstOrNull { it.id == boundKeyId }
                if (keyInfo != null) {
                    return keyInfo
                }
            }
        }
        
        // 2. 无绑定或已熔断，分配第一个当前可用的 Key
        val availableKeys = getAvailableKeys(context)
        if (availableKeys.isEmpty()) return null
        
        val newKey = availableKeys.first()
        bindSessionKey(context, sessionId, newKey.id)
        return newKey
    }

    /**
     * 强行绑定 Session 与 Key ID 的对应关系
     */
    fun bindSessionKey(context: Context, sessionId: Long, keyId: String) {
        getPrefs(context).edit()
            .putString("session_key_$sessionId", keyId)
            .apply()
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
     * 将角色分配给可用的 API 密钥（随机分组策略）
     * 每次会话开始时，将参与角色随机打乱后，每 1~3 个随机分配给一个可用 API Key。
     * 返回 Map<ApiKeyInfo, List<Character>> 代表 keyId 到该组角色的映射。
     */
    fun assignRandomGroups(
        characters: List<com.example.skillroundtable.data.Character>,
        availableKeys: List<ApiKeyInfo>
    ): Map<ApiKeyInfo, List<com.example.skillroundtable.data.Character>> {
        if (characters.isEmpty() || availableKeys.isEmpty()) return emptyMap()
        
        val shuffledChars = characters.shuffled()
        val groups = mutableListOf<List<com.example.skillroundtable.data.Character>>()
        var remaining = shuffledChars
        
        while (remaining.isNotEmpty()) {
            val groupSize = (1..3).random()
            val takeSize = minOf(groupSize, remaining.size)
            groups.add(remaining.take(takeSize))
            remaining = remaining.drop(takeSize)
        }
        
        val result = mutableMapOf<ApiKeyInfo, List<com.example.skillroundtable.data.Character>>()
        groups.forEachIndexed { index, group ->
            val keyInfo = availableKeys[index % availableKeys.size]
            result[keyInfo] = group
        }
        return result
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
