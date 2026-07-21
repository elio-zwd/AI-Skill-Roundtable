package com.example.skillroundtable.telemetry

import java.util.concurrent.ConcurrentHashMap

/**
 * 仅在当前进程内保存云端 Interaction 链游标。
 *
 * Key 同时包含会话与角色，防止不同角色共享 previous_interaction_id。
 * 这里只保存服务商返回的不可展示标识，不写入磁盘或系统备份。
 */
object InteractionChainStore {
    private data class ChainKey(
        val sessionId: Long,
        val characterId: String
    )

    private val interactionIds = ConcurrentHashMap<ChainKey, String>()

    fun get(sessionId: Long, characterId: String): String? {
        return interactionIds[ChainKey(sessionId, characterId)]
    }

    fun put(sessionId: Long, characterId: String, interactionId: String) {
        val key = ChainKey(sessionId, characterId)
        if (interactionId.isBlank()) {
            interactionIds.remove(key)
        } else {
            interactionIds[key] = interactionId
        }
    }

    fun clearSession(sessionId: Long) {
        interactionIds.keys
            .filter { it.sessionId == sessionId }
            .forEach { key -> interactionIds.remove(key) }
    }

    fun clearCharacter(characterId: String) {
        interactionIds.keys
            .filter { it.characterId == characterId }
            .forEach { key -> interactionIds.remove(key) }
    }

    fun clearAll() {
        interactionIds.clear()
    }

    internal fun size(): Int = interactionIds.size
}