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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val ROLE_ACTION_SETTLE_TIMEOUT_MS = 5_000L

/**
 * 为【角色】页提供可等待的真实会话动作。
 *
 * 这里先把官方 Skill 按需桥接为 legacy Character，再复用 RoundtableViewModel 现有
 * session 状态发布逻辑。Boolean 只在目标状态真正落地后返回 true，调用方因此可以
 * 安全地把 recent-use 写在成功之后。
 */
suspend fun RoundtableViewModel.createNewSessionWithSkillRole(
    skillId: String,
    title: String = "新建对话",
): Boolean {
    val definition = resolveExecutableOfficialSkill(skillId) ?: return false
    val application = getApplication<Application>()
    val database = RoundtableDatabase.getDatabase(application, viewModelScope)
    val characterRepository = CharacterRepository(database.characterDao())
    val adapter = OfficialSkillConversationRoleAdapter(application, characterRepository)
    if (adapter.ensureCompatibleCharacter(definition) == null) return false

    val chatRepository = ChatRepository(database.chatDao())
    val conversationPreferences = ConversationSessionPreferences(application)
    val sessionId = runCatching { chatRepository.createSession(title) }.getOrNull() ?: return false
    conversationPreferences.setParticipantIds(sessionId, listOf(skillId))

    // participant 已先持久化，再发布 current session，避免 create-then-add 空会话竞态。
    selectSession(sessionId)
    val settled = withTimeoutOrNull(ROLE_ACTION_SETTLE_TIMEOUT_MS) {
        combine(currentSessionId, currentParticipantIds) { currentId, participantIds ->
            currentId to participantIds
        }.first { (currentId, participantIds) ->
            currentId != sessionId || skillId in participantIds
        }
    } ?: return false

    return settled.first == sessionId && skillId in settled.second
}

/**
 * 始终把 participant 变更绑定到动作开始时捕获的 session ID。
 *
 * 不调用旧 [RoundtableViewModel.addSkillRoleToCurrentSession]，因为旧 wrapper 会在真正
 * 执行时重新读取 currentSessionId，可能把角色写入刚切换的新会话。这里在兼容角色
 * 准备完成后再次核对 session，并直接更新捕获会话的偏好；随后仅在它仍为当前会话时
 * 通过 selectSession 刷新 ViewModel 状态。成功返回前继续等待 participant 状态落地。
 */
suspend fun RoundtableViewModel.addSkillRoleToCurrentSessionAwait(
    skillId: String,
): Boolean {
    val sessionId = currentSessionId.value ?: return false
    val definition = resolveExecutableOfficialSkill(skillId) ?: return false
    val application = getApplication<Application>()
    val database = RoundtableDatabase.getDatabase(application, viewModelScope)
    val characterRepository = CharacterRepository(database.characterDao())
    val adapter = OfficialSkillConversationRoleAdapter(application, characterRepository)
    if (adapter.ensureCompatibleCharacter(definition) == null) return false
    if (currentSessionId.value != sessionId) return false

    val conversationPreferences = ConversationSessionPreferences(application)
    val originalParticipantIds = conversationPreferences.getParticipantIds(
        sessionId = sessionId,
        defaultIds = currentParticipantIds.value,
    )
    val updatedParticipantIds = (originalParticipantIds + skillId).distinct().take(15)
    if (skillId !in updatedParticipantIds) return false

    conversationPreferences.setParticipantIds(sessionId, updatedParticipantIds)
    if (currentSessionId.value != sessionId) {
        // 极窄并发窗口下只恢复原捕获会话，绝不把 participant 写入新会话。
        conversationPreferences.setParticipantIds(sessionId, originalParticipantIds)
        return false
    }

    // setParticipantIds 到 selectSession 之间没有挂起点；UI 主线程不会在中间切换会话。
    selectSession(sessionId)
    val settled = withTimeoutOrNull(ROLE_ACTION_SETTLE_TIMEOUT_MS) {
        combine(currentSessionId, currentParticipantIds) { currentId, participantIds ->
            currentId to participantIds
        }.first { (currentId, participantIds) ->
            currentId != sessionId || skillId in participantIds
        }
    } ?: return false

    return settled.first == sessionId && skillId in settled.second
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
