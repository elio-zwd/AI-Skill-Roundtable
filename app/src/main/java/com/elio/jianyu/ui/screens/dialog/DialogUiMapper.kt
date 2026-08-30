package com.elio.jianyu.ui.screens.dialog

import com.elio.jianyu.data.Character
import com.elio.jianyu.data.ChatSession
import com.elio.jianyu.data.Message
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.Color

internal fun mapDialogUiState(
    localState: DialogUiState,
    sessions: List<ChatSession>,
    currentSession: ChatSession?,
    messages: List<Message>,
    characters: List<Character>,
    participantIds: List<String>,
    archivedSessionIds: Set<Long>,
    showArchivedSessions: Boolean,
    isGenerating: Boolean,
    searchEnabled: Boolean,
    thinkingIntensity: String,
): DialogUiState {
    val roleById = characters.associate { character ->
        character.id to character.toSkillRoleUiModel(character.id in participantIds)
    }
    val activeRoles = participantIds.mapNotNull(roleById::get)
    val allRoles = characters.map { character -> roleById.getValue(character.id) }
    val selectedRole = localState.selectedSkillDetail?.role?.id?.let(roleById::get)

    return localState.copy(
        session = DialogSessionInfo(
            id = currentSession?.id?.toString().orEmpty(),
            title = currentSession?.title ?: "新建对话",
            roleCount = activeRoles.size,
        ),
        activeRoles = activeRoles,
        messages = messages.map { message -> message.toDialogMessage(roleById) },
        searchState = DialogSearchState(
            enabled = searchEnabled,
            statusText = if (searchEnabled) "已开" else "已关",
        ),
        thinkingIntensity = thinkingIntensity,
        composerState = localState.composerState.copy(isGenerating = isGenerating),
        selectedSkillDetail = selectedRole?.let(::buildSkillRoleDetail),
        drawerData = ConversationDrawerUiModel(
            currentSessionId = currentSession?.id?.toString().orEmpty(),
            searchQuery = localState.drawerData.searchQuery,
            groups = buildConversationGroups(
                sessions = sessions,
                currentSessionId = currentSession?.id,
                query = localState.drawerData.searchQuery,
                archivedSessionIds = archivedSessionIds,
                showArchivedSessions = showArchivedSessions,
                currentRoleCount = activeRoles.size,
            ),
            archivedCount = archivedSessionIds.size,
            isShowingArchived = showArchivedSessions,
        ),
        addSkillCatalog = AddSkillCatalogUiModel(
            searchQuery = localState.addSkillCatalog.searchQuery,
            recentUsed = activeRoles.take(5),
            recommended = allRoles.filterNot { role -> role.id in participantIds }.take(8),
            allSkills = allRoles,
        ),
    )
}

internal fun Character.toSkillRoleUiModel(inCurrentSession: Boolean): SkillRoleUiModel =
    SkillRoleUiModel(
        id = id,
        name = name,
        shortDescription = tagline,
        avatarUrl = avatar,
        avatarText = name.take(2),
        tintBg = roleColors(id).background,
        tintBorder = roleColors(id).border,
        accentColor = roleColors(id).accent,
        isInCurrentSession = inCurrentSession,
    )

private fun Message.toDialogMessage(
    roleById: Map<String, SkillRoleUiModel>,
): DialogMessageItem = if (senderId == "user") {
    DialogMessageItem.UserMessage(
        id = id.toString(),
        text = text,
        timestamp = formatMessageTime(timestamp),
    )
} else {
    val role = roleById[senderId] ?: SkillRoleUiModel(
        id = senderId,
        name = senderName,
        shortDescription = "历史 Skill 角色",
        avatarUrl = avatar,
        avatarText = senderName.take(2),
        tintBg = roleColors(senderId).background,
        tintBorder = roleColors(senderId).border,
        accentColor = roleColors(senderId).accent,
    )
    DialogMessageItem.SkillMessage(
        id = id.toString(),
        role = role,
        text = if (isPending && text == "正在思考中...") "正在思考…" else text,
        timestamp = formatMessageTime(timestamp),
        isStreaming = isPending,
    )
}

private fun buildSkillRoleDetail(role: SkillRoleUiModel): SkillRoleDetailUiModel =
    SkillRoleDetailUiModel(
        role = role,
        isInCurrentSession = role.isInCurrentSession,
        fullDescription = role.shortDescription,
        capabilities = listOf(
            SkillCapabilityItem("擅长", role.shortDescription, SkillCapabilityIconType.CUBE),
            SkillCapabilityItem("思维特点", "按自身角色设定独立形成判断。", SkillCapabilityIconType.NETWORK),
            SkillCapabilityItem("表达特点", "保持稳定身份与表达风格，并说明关键条件。", SkillCapabilityIconType.CHAT),
        ),
        identityDisclosure = if (role.id in PERSON_PERSPECTIVE_IDS) {
            "AI 模拟角色，基于可获得资料构建，不代表本人，也不保证复现本人当前或完整观点。"
        } else {
            null
        },
    )

private fun buildConversationGroups(
    sessions: List<ChatSession>,
    currentSessionId: Long?,
    query: String,
    archivedSessionIds: Set<Long>,
    showArchivedSessions: Boolean,
    currentRoleCount: Int,
): List<ConversationGroupUiModel> {
    val normalizedQuery = query.trim()
    val visible = sessions.filter { session ->
        val archived = session.id in archivedSessionIds
        archived == showArchivedSessions &&
            (normalizedQuery.isEmpty() || session.title.contains(normalizedQuery, ignoreCase = true))
    }
    val calendar = Calendar.getInstance()
    val todayStart = calendar.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val yesterdayStart = todayStart - 24L * 60L * 60L * 1000L
    val grouped = linkedMapOf(
        "今天" to mutableListOf<SessionSummaryUiModel>(),
        "昨天" to mutableListOf(),
        "更早" to mutableListOf(),
    )
    visible.forEach { session ->
        val group = when {
            session.createdAt >= todayStart -> "今天"
            session.createdAt >= yesterdayStart -> "昨天"
            else -> "更早"
        }
        grouped.getValue(group) += SessionSummaryUiModel(
            id = session.id.toString(),
            title = session.title,
            previewText = if (showArchivedSessions) "已归档会话" else "点击继续这段对话",
            time = formatSessionTime(session.createdAt),
            roleAvatars = emptyList(),
            roleCountText = if (session.id == currentSessionId) {
                "$currentRoleCount 个 Skill 角色"
            } else {
                "已保存会话"
            },
            isSelected = session.id == currentSessionId,
        )
    }
    return grouped.mapNotNull { (name, items) ->
        items.takeIf(List<SessionSummaryUiModel>::isNotEmpty)
            ?.let { ConversationGroupUiModel(name, it) }
    }
}

private fun formatMessageTime(timestamp: Long): String =
    SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))

private fun formatSessionTime(timestamp: Long): String =
    SimpleDateFormat("MM-dd", Locale.CHINA).format(Date(timestamp))

private data class RoleColors(
    val background: Color,
    val border: Color,
    val accent: Color,
)

private fun roleColors(id: String): RoleColors = when ((id.hashCode() and Int.MAX_VALUE) % 4) {
    0 -> RoleColors(Color(0xFFF7F5FE), Color(0xFFE7E1FB), Color(0xFF6340F8))
    1 -> RoleColors(Color(0xFFF0FAF6), Color(0xFFCDEDE2), Color(0xFF28B383))
    2 -> RoleColors(Color(0xFFF4F7FB), Color(0xFFDCE5F2), Color(0xFF176DFF))
    else -> RoleColors(Color(0xFFFFF9F0), Color(0xFFFDE8C7), Color(0xFFE68A00))
}

private val PERSON_PERSPECTIVE_IDS = setOf(
    "zhang_xuefeng",
    "elon_musk",
    "richard_feynman",
    "charlie_munger",
    "naval_ravikant",
    "steve_jobs",
    "nassim_taleb",
    "andrej_karpathy",
    "zhang_yiming",
    "paul_graham",
    "ilya_sutskever",
    "donald_trump",
    "mr_beast",
    "justin_sun",
    "sigmund_freud",
    "feng_ge",
    "changpeng_zhao",
    "duan_yongping",
    "tim_cook",
)
