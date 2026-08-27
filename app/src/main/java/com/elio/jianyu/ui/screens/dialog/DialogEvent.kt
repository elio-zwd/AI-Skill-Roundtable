package com.elio.jianyu.ui.screens.dialog

/**
 * 见域「对话」页面用户交互事件定义（全部预留）
 */
sealed interface DialogEvent {
    // 抽屉与浮层控制
    data class SetDrawerOpen(val open: Boolean) : DialogEvent
    data class SetOverlay(val overlay: DialogOverlayType) : DialogEvent
    object DismissOverlay : DialogEvent
    object ToggleMoreMenu : DialogEvent
    object DismissMoreMenu : DialogEvent

    // 顶部与会话操作
    object CreateNewSession : DialogEvent
    data class SelectSession(val sessionId: String) : DialogEvent
    data class SearchSessions(val query: String) : DialogEvent
    object OpenArchivedSessions : DialogEvent
    data class RenameSession(val sessionId: String) : DialogEvent
    data class ExportSession(val sessionId: String) : DialogEvent
    data class ArchiveSession(val sessionId: String) : DialogEvent
    data class DeleteSession(val sessionId: String) : DialogEvent
    object ContinueDeeper : DialogEvent // 继续深入
    object SaveOrOrganizeArtifacts : DialogEvent // 保存 / 整理成果

    // Skill 角色交互
    data class ClickSkillCard(val skillId: String) : DialogEvent
    object ClickAddSkillCard : DialogEvent
    data class SearchSkillsToAdd(val query: String) : DialogEvent
    data class AddSkillToSession(val skillId: String) : DialogEvent
    data class RemoveSkillFromSession(val skillId: String) : DialogEvent
    data class LetSkillAnswerCurrent(val skillId: String) : DialogEvent

    // 消息级操作
    data class CopyMessage(val messageId: String, val content: String) : DialogEvent
    data class SaveMessageAsArtifact(val messageId: String) : DialogEvent
    data class ClickMessageMore(val messageId: String) : DialogEvent

    // 输入区与发送
    data class InputTextChanged(val text: String) : DialogEvent
    object SendMessage : DialogEvent
    object ClickPlusButton : DialogEvent
    object ClickAtButton : DialogEvent
    data class SelectReplyTargetRole(val role: SkillRoleUiModel?) : DialogEvent
    object SelectMultiRoleAnswer : DialogEvent
    object ClearReplyTargetRole : DialogEvent

    // 功能配置
    object ToggleSearchMode : DialogEvent
    data class SelectThinkingIntensity(val intensity: String) : DialogEvent
    object AddFileAttachment : DialogEvent
    object SelectMaterials : DialogEvent
    object ViewReferenceContent : DialogEvent
    object TriggerCrossDiscussion : DialogEvent // 交叉讨论

    // 底部导航
    data class NavigateBottomTab(val tabIndex: Int) : DialogEvent
}
