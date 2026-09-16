package com.elio.jianyu.viewmodel

import android.app.Application
import androidx.lifecycle.viewModelScope
import com.elio.jianyu.JianyuAppRuntimeProvider
import com.elio.jianyu.data.CharacterRepository
import com.elio.jianyu.data.ChatRepository
import com.elio.jianyu.data.ConversationSessionPreferences
import com.elio.jianyu.data.RoundtableDatabase
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.catalog.OfficialSkillDefinition
import com.elio.jianyu.skill.role.OfficialSkillConversationRoleAdapter
import com.elio.jianyu.telemetry.PrivacySafeLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val ROLE_ACTION_SETTLE_TIMEOUT_MS = 5_000L

/**
 * 为【角色】页提供可等待的真实会话动作。
 *
 * 这里先把官方 Skill 按需桥接为 legacy Character，再把 participant 持久化到目标会话，
 * 最后只走 selectSession 的单一 rehydrate 路径发布 current session / roster。Boolean 仅在
 * 该次 rehydrate 完成且目标 participant 已进入当前会话状态后返回 true。
 */
suspend fun RoundtableViewModel.createNewSessionWithSkillRole(
    skillId: String,
    title: String = "新建对话",
    settleTimeoutMs: Long = ROLE_ACTION_SETTLE_TIMEOUT_MS,
): Boolean {
    val definition = resolveExecutableOfficialSkill(skillId)
        ?: return roleActionFailure("start_new", "official_role_unavailable")
    val application = getApplication<Application>()
    val database = RoundtableDatabase.getDatabase(application, viewModelScope)
    val characterRepository = CharacterRepository(database.characterDao())
    val adapter = OfficialSkillConversationRoleAdapter(application, characterRepository)
    if (adapter.ensureCompatibleCharacter(definition) == null) {
        return roleActionFailure("start_new", "compatibility_adapter_rejected")
    }

    val previousSessionId = currentSessionId.value
    val chatRepository = ChatRepository(database.chatDao())
    val conversationPreferences = ConversationSessionPreferences(application)
    val sessionId = try {
        chatRepository.createSession(title)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        return roleActionFailure("start_new", "session_create_failed")
    }
    conversationPreferences.setParticipantIds(sessionId, listOf(skillId))

    try {
        if (refreshSessionRosterAndAwait(sessionId, skillId, settleTimeoutMs)) {
            return true
        }
        compensateCreatedRoleSession(
            chatRepository = chatRepository,
            conversationPreferences = conversationPreferences,
            createdSessionId = sessionId,
            previousSessionId = previousSessionId,
        )
        return roleActionFailure("start_new", "session_roster_not_settled")
    } catch (cancelled: CancellationException) {
        compensateCreatedRoleSession(
            chatRepository = chatRepository,
            conversationPreferences = conversationPreferences,
            createdSessionId = sessionId,
            previousSessionId = previousSessionId,
        )
        throw cancelled
    }
}

/**
 * 始终把 participant 变更绑定到动作开始时捕获的 session ID。
 *
 * participant 先写入捕获会话的偏好，再通过 selectSession 统一刷新 ViewModel。这样不会
 * 同时启动“直接 add”与“session rehydrate”两条互相竞争的异步发布路径；成功返回前等待
 * currentSession 对应本次 refresh 重新发布，再核对 roster。
 */
suspend fun RoundtableViewModel.addSkillRoleToCurrentSessionAwait(
    skillId: String,
    settleTimeoutMs: Long = ROLE_ACTION_SETTLE_TIMEOUT_MS,
): Boolean {
    val sessionId = currentSessionId.value
        ?: return roleActionFailure("add_current", "no_current_session")
    val definition = resolveExecutableOfficialSkill(skillId)
        ?: return roleActionFailure("add_current", "official_role_unavailable")
    val application = getApplication<Application>()
    val database = RoundtableDatabase.getDatabase(application, viewModelScope)
    val characterRepository = CharacterRepository(database.characterDao())
    val adapter = OfficialSkillConversationRoleAdapter(application, characterRepository)
    if (adapter.ensureCompatibleCharacter(definition) == null) {
        return roleActionFailure("add_current", "compatibility_adapter_rejected")
    }
    if (currentSessionId.value != sessionId) {
        return roleActionFailure("add_current", "session_changed_before_mutation")
    }

    val conversationPreferences = ConversationSessionPreferences(application)
    val originalParticipantIds = conversationPreferences.getParticipantIds(
        sessionId = sessionId,
        defaultIds = currentParticipantIds.value,
    ).distinct()
    val updatedParticipantIds = (originalParticipantIds + skillId).distinct().take(15)
    if (skillId !in updatedParticipantIds) {
        return roleActionFailure("add_current", "participant_limit_reached")
    }

    conversationPreferences.setParticipantIds(sessionId, updatedParticipantIds)
    if (currentSessionId.value != sessionId) {
        conversationPreferences.setParticipantIds(sessionId, originalParticipantIds)
        return roleActionFailure("add_current", "session_changed_before_refresh")
    }

    try {
        if (refreshSessionRosterAndAwait(sessionId, skillId, settleTimeoutMs)) {
            return true
        }
        compensateAddedRole(
            conversationPreferences = conversationPreferences,
            sessionId = sessionId,
            originalParticipantIds = originalParticipantIds,
        )
        return roleActionFailure("add_current", "session_roster_not_settled")
    } catch (cancelled: CancellationException) {
        compensateAddedRole(
            conversationPreferences = conversationPreferences,
            sessionId = sessionId,
            originalParticipantIds = originalParticipantIds,
        )
        throw cancelled
    }
}

private suspend fun RoundtableViewModel.compensateCreatedRoleSession(
    chatRepository: ChatRepository,
    conversationPreferences: ConversationSessionPreferences,
    createdSessionId: Long,
    previousSessionId: Long?,
) {
    withContext(NonCancellable) {
        conversationPreferences.clearSession(createdSessionId)
        try {
            chatRepository.deleteSession(createdSessionId)
        } catch (error: Exception) {
            PrivacySafeLogger.e(
                "RoundtableViewModel",
                "Failed to compensate incomplete skill-role session",
                error,
            )
        }
        restoreSessionSelectionAfterRoleAction(
            expectedCurrentSessionId = createdSessionId,
            restoreSessionId = previousSessionId,
        )
    }
}

private suspend fun RoundtableViewModel.compensateAddedRole(
    conversationPreferences: ConversationSessionPreferences,
    sessionId: Long,
    originalParticipantIds: List<String>,
) {
    withContext(NonCancellable) {
        conversationPreferences.setParticipantIds(sessionId, originalParticipantIds)
        restoreSessionSelectionAfterRoleAction(
            expectedCurrentSessionId = sessionId,
            restoreSessionId = sessionId,
        )
    }
}

/**
 * selectSession 会同步清空 currentSession，并在 participant 解析完成后依次重新发布
 * currentSession 与 currentParticipantIds。等待 currentSession 的这次重新发布，相当于等待
 * 单一 rehydrate 流程完成；随后直接检查最终 roster，避免把完成信号建立在两个并发 flow
 * 的 combine 时序上。
 */
private suspend fun RoundtableViewModel.refreshSessionRosterAndAwait(
    sessionId: Long,
    skillId: String,
    settleTimeoutMs: Long,
): Boolean {
    selectSession(sessionId)
    val sessionPublished = withTimeoutOrNull(settleTimeoutMs) {
        currentSession.first { session -> session?.id == sessionId }
        true
    } == true
    if (!sessionPublished) return false

    return currentSessionId.value == sessionId &&
        skillId in currentParticipantIds.value
}

private fun roleActionFailure(action: String, reason: String): Boolean {
    PrivacySafeLogger.w(
        "RoundtableViewModel",
        "Skill role action failed (action=$action, reason=$reason)",
    )
    return false
}

private fun RoundtableViewModel.resolveExecutableOfficialSkill(
    skillId: String,
): OfficialSkillDefinition? {
    if (skillId.isBlank()) return null
    val application = getApplication<Application>()
    val runtime = runCatching { JianyuAppRuntimeProvider.get(application) }.getOrNull()
        ?: return null
    val catalogRuntime = runtime.officialSkillCatalogRuntimeResult as? OfficialSkillCatalogRuntimeResult.Success
        ?: return null
    val definition = catalogRuntime.runtime.catalog.findById(skillId) ?: return null
    return definition.takeIf { it.availability.executable }
}
