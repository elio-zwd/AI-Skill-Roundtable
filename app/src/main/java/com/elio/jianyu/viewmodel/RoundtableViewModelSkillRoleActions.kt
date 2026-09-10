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
 * 不改动旧同步 API；这里先把官方 Skill 按需桥接为 legacy Character，再复用
 * RoundtableViewModel 现有 session/participant 状态发布逻辑。Boolean 只在目标状态
 * 真正落地后返回 true，调用方因此可以安全地把 recent-use 写在成功之后。
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
 * 捕获当前 session 后再解析并桥接角色；若期间用户切换会话，本次动作失败且不会把
 * participant 写到新会话。成功返回前会等待现有 ViewModel participant 状态包含该角色。
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

    addSkillRoleToCurrentSession(skillId)
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
