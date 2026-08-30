package com.elio.jianyu.data

import android.content.Context

/**
 * 保存现有 ChatSession 表尚未承载的轻量会话偏好。
 *
 * 参与角色属于会话，不再复用 Character.isActive 这一全局开关；归档也与删除分开保存。
 */
class ConversationSessionPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun getParticipantIds(sessionId: Long, defaultIds: List<String>): List<String> {
        val stored = preferences.getString(participantKey(sessionId), null)
        return stored
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.distinct()
            ?: defaultIds.distinct()
    }

    fun setParticipantIds(sessionId: Long, participantIds: List<String>) {
        preferences.edit()
            .putString(participantKey(sessionId), participantIds.distinct().joinToString(","))
            .apply()
    }

    fun getArchivedSessionIds(): Set<Long> = preferences
        .getStringSet(ARCHIVED_SESSION_IDS, emptySet())
        .orEmpty()
        .mapNotNull(String::toLongOrNull)
        .toSet()

    fun setArchived(sessionId: Long, archived: Boolean): Set<Long> {
        val updated = getArchivedSessionIds().toMutableSet().apply {
            if (archived) add(sessionId) else remove(sessionId)
        }
        preferences.edit()
            .putStringSet(ARCHIVED_SESSION_IDS, updated.map(Long::toString).toSet())
            .apply()
        return updated
    }

    fun clearSession(sessionId: Long): Set<Long> {
        val archived = getArchivedSessionIds() - sessionId
        preferences.edit()
            .remove(participantKey(sessionId))
            .putStringSet(ARCHIVED_SESSION_IDS, archived.map(Long::toString).toSet())
            .apply()
        return archived
    }

    private fun participantKey(sessionId: Long): String = "session_${sessionId}_participant_ids"

    private companion object {
        const val PREFERENCES_NAME = "conversation_session_preferences"
        const val ARCHIVED_SESSION_IDS = "archived_session_ids"
    }
}
