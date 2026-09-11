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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val ROLE_ACTION_SETTLE_TIMEOUT_MS = 5_000L

/**
 * 为【角色】页提供可等待的真实会话动作。
 *
 * 这里先把官方 Skill 按需桥接为 legacy Character，再复用 RoundtableViewModel 已有的
 * session / participant 状态发布逻辑。Boolean 只在目标 participant 已进入当前会话状态后
 * 返回 true，调用方因此可以安全地把 recent-use 写在成功之后。
 */
suspend fun RoundtableViewModel.createNewSessionWithSkillRole(
    skillId: String,
    title: String = "新建对话",
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

    val chatRepository = ChatRepository(database.chatDao())
    val conversationPreferences = ConversationSessionPreferences(application)
    val sessionId = runCatching { chatRepository.createSession(title) }.getOrNull()
        ?: return roleActionFailure("start_new", "session_create_failed")
    conversationPreferences.setParticipantIds(sessionId, listOf(skillId))

    // participant 先持久化，再发布 current session。selectSession 会先清空 ViewModel roster，
    // 因此同时走现有 addSkillRoleToCurrentSession 的直接发布路径，避免把成功依赖于一次
    // 二次异步 rehydrate；两条路径最终都读取同一个已持久化 participant 集合。
    selectSession(sessionId)
    addSkillRoleToCurrentSession(skillId)

    return if (awaitSkillRoleInSession(sessionId, skillId)) {
        true
    } else {
        roleActionFailure("start_new", "session_roster_not_settled")
    }
}

/**
 * 始终把 participant 变更绑定到动作开始时捕获的 session ID。
 *
 * 兼容角色准备完成后再次核对 session；随后调用现有 addSkillRoleToCurrentSession。
 * 该方法会在调用瞬间捕获 currentSessionId，并在真正写入前再次核对，因此既不会把角色
 * 写入后来切换的新会话，也不需要通过 selectSession 清空并重载整个会话状态。
 */
suspend fun RoundtableViewModel.addSkillRoleToCurrentSessionAwait(
    skillId: String,
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

    val currentParticipants = currentParticipantIds.value.distinct()
    if (skillId !in currentParticipants && currentParticipants.size >= 15) {
        return roleActionFailure("add_current", "participant_limit_reached")
    }

    // 这里到方法调用之间没有挂起点；旧方法同步捕获同一个 sessionId，随后在异步写入前
    // 还会再次检查 currentSessionId，并直接更新 _currentParticipantIds。
    addSkillRoleToCurrentSession(skillId)

    return if (awaitSkillRoleInSession(sessionId, skillId)) {
        true
    } else {
        roleActionFailure("add_current", "session_roster_not_settled")
    }
}

private suspend fun RoundtableViewModel.awaitSkillRoleInSession(
    sessionId: Long,
    skillId: String,
): Boolean {
    val settled = withTimeoutOrNull(ROLE_ACTION_SETTLE_TIMEOUT_MS) {
        combine(currentSessionId, currentParticipantIds) { currentId, participantIds ->
            currentId to participantIds
        }.first { (currentId, participantIds) ->
            currentId != sessionId || skillId in participantIds
        }
    } ?: return false

    return settled.first == sessionId && skillId in settled.second
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
