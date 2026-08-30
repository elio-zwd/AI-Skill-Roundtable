package com.elio.jianyu.ui.screens.dialog

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * 见域「对话」页面不可变 UI 状态模型
 */
@Immutable
data class DialogUiState(
    val session: DialogSessionInfo = DialogSessionInfo(),
    val activeRoles: List<SkillRoleUiModel> = emptyList(),
    val messages: List<DialogMessageItem> = emptyList(),
    val searchState: DialogSearchState = DialogSearchState(),
    val thinkingIntensity: String = "标准",
    val composerState: DialogComposerState = DialogComposerState(),
    val activeOverlay: DialogOverlayType = DialogOverlayType.NONE,
    val selectedSkillDetail: SkillRoleDetailUiModel? = null,
    val drawerData: ConversationDrawerUiModel = ConversationDrawerUiModel(),
    val addSkillCatalog: AddSkillCatalogUiModel = AddSkillCatalogUiModel(),
    val isMoreMenuOpen: Boolean = false,
) {
    companion object {
        /**
         * 严格对应 docs/product/重构/UI界面/对话/jianyu-dialog-final-ui-spec.md 截图的预览 Mock 数据
         */
        val PreviewMock: DialogUiState by lazy {
            val planningCoach = SkillRoleUiModel(
                id = "planning_coach",
                name = "规划教练",
                shortDescription = "擅长拆解目标与行动步骤",
                avatarUrl = "",
                avatarText = "规划",
                avatarResId = com.elio.jianyu.R.drawable.avatar_planner,
                tintBg = DialogTokens.RoleLavenderBg,
                tintBorder = DialogTokens.RoleLavenderBorder,
                accentColor = DialogTokens.BrandPurple,
                isInCurrentSession = true,
            )

            val systemsThinker = SkillRoleUiModel(
                id = "systems_thinker",
                name = "系统思考者",
                shortDescription = "擅长识别结构、依赖与风险",
                avatarUrl = "",
                avatarText = "系统",
                avatarResId = com.elio.jianyu.R.drawable.avatar_thinker,
                tintBg = DialogTokens.RoleMintBg,
                tintBorder = DialogTokens.RoleMintBorder,
                accentColor = DialogTokens.RoleMintAccent,
                isInCurrentSession = true,
                hasDecorationSparkle = true,
            )

            val researcher = SkillRoleUiModel(
                id = "researcher",
                name = "研究员",
                shortDescription = "擅长深度检索、事实核查与文献梳理",
                avatarText = "研究",
                tintBg = Color(0xFFF4F7FB),
                tintBorder = Color(0xFFDCE5F2),
                accentColor = DialogTokens.InteractionBlue,
                isInCurrentSession = false,
            )

            val productAdvisor = SkillRoleUiModel(
                id = "product_advisor",
                name = "产品顾问",
                shortDescription = "擅长需求洞察、价值定位与MVP定义",
                avatarText = "产品",
                tintBg = Color(0xFFFFF9F0),
                tintBorder = Color(0xFFFDE8C7),
                accentColor = Color(0xFFE68A00),
                isInCurrentSession = false,
            )

            val socrates = SkillRoleUiModel(
                id = "socrates",
                name = "苏格拉底",
                shortDescription = "擅长苏格拉底式追问，探究底层假设",
                avatarText = "苏格",
                tintBg = Color(0xFFF6F3FF),
                tintBorder = Color(0xFFE4DAFF),
                accentColor = DialogTokens.BrandPurple,
                isInCurrentSession = false,
            )

            val jobs = SkillRoleUiModel(
                id = "steve_jobs",
                name = "史蒂夫·乔布斯",
                shortDescription = "擅长极致体验打磨与直觉式创新",
                avatarText = "乔",
                tintBg = Color(0xFFF5F5F7),
                tintBorder = Color(0xFFE1E1E6),
                accentColor = DialogTokens.TextPrimary,
                isInCurrentSession = false,
            )

            val userMsg = DialogMessageItem.UserMessage(
                id = "msg_1",
                text = "我有一个复杂目标：3 个月内转向嵌入式岗位，但我现在基础不牢，也在上班。你们帮我拆成可执行计划。",
                timestamp = "10:21",
                isDelivered = true,
                avatarText = "我",
                avatarResId = com.elio.jianyu.R.drawable.avatar_user,
            )

            val coachMsg = DialogMessageItem.SkillMessage(
                id = "msg_2",
                role = planningCoach,
                text = "很棒的目标！我们先把“大目标”拆成小步，逐步推进。\n\n" +
                    "**第一步：明确里程碑**\n\n" +
                    "• 1）补基础：聚焦嵌入式必备基础，建立知识框架\n" +
                    "• 2）做项目证明：完成 1~2 个可展示的实战项目\n" +
                    "• 3）准备求职材料：打造作品集，匹配岗位并投递\n\n" +
                    "接下来，你希望优先从哪一项开始？我来帮你细化。",
                timestamp = "10:24",
            )

            val thinkerMsg = DialogMessageItem.SkillMessage(
                id = "msg_3",
                role = systemsThinker,
                text = "**我先补充几个关键约束：**\n\n" +
                    "• **时间**：你白天上班，学习时间有限，需要严格的时间分配与能量管理。\n" +
                    "• **反馈闭环**：每个阶段要有可验证的输出和反馈，及时调整方向，避免走偏。\n" +
                    "• **作品证据**：没有可展示的作品，很难通过简历筛选，这是转岗的硬门槛。\n\n" +
                    "如果这些约束没处理好，计划很容易落地失败。",
                timestamp = "10:27",
            )

            DialogUiState(
                session = DialogSessionInfo(
                    id = "session_embed_plan",
                    title = "如何把一个复杂目标拆成可执行计划？",
                    roleCount = 2,
                ),
                activeRoles = listOf(planningCoach, systemsThinker),
                messages = listOf(userMsg, coachMsg, thinkerMsg),
                searchState = DialogSearchState(
                    enabled = true,
                    statusText = "已开",
                ),
                thinkingIntensity = "标准",
                composerState = DialogComposerState(
                    inputText = "请帮我把‘3个月内转向嵌入式岗位’拆成按周执行的计划",
                    targetRole = null,
                ),
                drawerData = ConversationDrawerUiModel(
                    currentSessionId = "session_embed_plan",
                    groups = listOf(
                        ConversationGroupUiModel(
                            groupName = "今天",
                            sessions = listOf(
                                SessionSummaryUiModel(
                                    id = "session_embed_plan",
                                    title = "如何把一个复杂目标拆成可执行计划？",
                                    previewText = "我有一个复杂目标：3 个月内转向嵌入式岗位...",
                                    time = "10:27",
                                    roleAvatars = listOf("规划", "系统"),
                                    roleCountText = "2 个 Skill 角色",
                                    isSelected = true,
                                ),
                                SessionSummaryUiModel(
                                    id = "session_2",
                                    title = "AI 辅助代码重构工作流",
                                    previewText = "如何利用大模型做好分层解耦与自动化测试？",
                                    time = "08:15",
                                    roleAvatars = listOf("系统", "研究"),
                                    roleCountText = "2 个 Skill 角色",
                                ),
                            ),
                        ),
                        ConversationGroupUiModel(
                            groupName = "昨天",
                            sessions = listOf(
                                SessionSummaryUiModel(
                                    id = "session_3",
                                    title = "个人知识库沉淀架构设计",
                                    previewText = "从卡片盒笔记法到领域模型...",
                                    time = "昨天",
                                    roleAvatars = listOf("规划"),
                                    roleCountText = "1 个 Skill 角色",
                                ),
                            ),
                        ),
                        ConversationGroupUiModel(
                            groupName = "更早",
                            sessions = listOf(
                                SessionSummaryUiModel(
                                    id = "session_4",
                                    title = "产品冷启动增长策略",
                                    previewText = "如何找到最初的100位种子用户...",
                                    time = "08-20",
                                    roleAvatars = listOf("产品", "乔"),
                                    roleCountText = "2 个 Skill 角色",
                                ),
                            ),
                        ),
                    ),
                    archivedCount = 5,
                ),
                addSkillCatalog = AddSkillCatalogUiModel(
                    recentUsed = listOf(planningCoach, systemsThinker),
                    recommended = listOf(researcher, productAdvisor, socrates, jobs),
                    allSkills = listOf(
                        planningCoach,
                        systemsThinker,
                        researcher,
                        productAdvisor,
                        socrates,
                        jobs,
                    ),
                ),
                selectedSkillDetail = SkillRoleDetailUiModel(
                    role = systemsThinker,
                    isInCurrentSession = true,
                    fullDescription = "擅长从系统与结构的角度分析问题，识别关键关系、依赖链与潜在风险，帮助你做出更全面、长远的判断。",
                    capabilities = listOf(
                        SkillCapabilityItem(
                            title = "擅长",
                            detail = "系统结构、因果关系、长期影响",
                            iconType = SkillCapabilityIconType.CUBE,
                        ),
                        SkillCapabilityItem(
                            title = "思维特点",
                            detail = "倾向从整体结构、反馈回路与边界条件分析问题。",
                            iconType = SkillCapabilityIconType.NETWORK,
                        ),
                        SkillCapabilityItem(
                            title = "表达特点",
                            detail = "会先指出关键约束，再给出结构化判断与风险提醒。",
                            iconType = SkillCapabilityIconType.CHAT,
                        ),
                    ),
                ),
            )
        }
    }
}

/**
 * 会话元信息
 */
@Immutable
data class DialogSessionInfo(
    val id: String = "",
    val title: String = "新建对话",
    val roleCount: Int = 0,
)

/**
 * Skill 角色 UI 展示模型
 */
@Immutable
data class SkillRoleUiModel(
    val id: String,
    val name: String,
    val shortDescription: String,
    val avatarUrl: String = "",
    val avatarText: String = "",
    val avatarResId: Int? = null,
    val tintBg: Color = DialogTokens.RoleLavenderBg,
    val tintBorder: Color = DialogTokens.RoleLavenderBorder,
    val accentColor: Color = DialogTokens.BrandPurple,
    val isInCurrentSession: Boolean = false,
    val hasDecorationSparkle: Boolean = false,
)

/**
 * 对话消息项（统一密封接口）
 */
sealed interface DialogMessageItem {
    val id: String
    val timestamp: String

    /** 用户消息 */
    @Immutable
    data class UserMessage(
        override val id: String,
        val text: String,
        override val timestamp: String,
        val isDelivered: Boolean = true,
        val avatarUrl: String = "",
        val avatarText: String = "我",
        val avatarResId: Int? = null,
    ) : DialogMessageItem

    /** Skill 角色回复消息 */
    @Immutable
    data class SkillMessage(
        override val id: String,
        val role: SkillRoleUiModel,
        val text: String,
        override val timestamp: String,
        val isStreaming: Boolean = false,
    ) : DialogMessageItem
}

/**
 * 联网搜索状态模型
 */
@Immutable
data class DialogSearchState(
    val enabled: Boolean = true,
    val statusText: String = "已开",
)

/**
 * 对话编辑器状态
 */
@Immutable
data class DialogComposerState(
    val inputText: String = "",
    val targetRole: SkillRoleUiModel? = null, // @ 点名的角色
    val isMultiRoleAnswer: Boolean = false,   // 是否为“多个角色分别回答”
    val isGenerating: Boolean = false,
)

/**
 * 页面激活的 Overlay 浮层类型
 */
enum class DialogOverlayType {
    NONE,
    DRAWER_SESSIONS,            // 左侧会话记录抽屉
    SHEET_ADD_SKILL,            // 增加 Skill 角色 Bottom Sheet
    SHEET_SKILL_DETAIL,         // Skill 角色详情 Bottom Sheet
    SHEET_COMPOSER_PLUS_MENU,   // 输入区「+」二级功能 Bottom Sheet
    SHEET_TARGET_ROLE_SELECT,   // 选择本次回复角色 / @ Bottom Sheet
}

/**
 * 角色详情展示模型
 */
@Immutable
data class SkillRoleDetailUiModel(
    val role: SkillRoleUiModel,
    val isInCurrentSession: Boolean,
    val fullDescription: String,
    val capabilities: List<SkillCapabilityItem>,
    val identityDisclosure: String? = null,
)

enum class SkillCapabilityIconType {
    CUBE,       // 立方体：擅长
    NETWORK,    // 网络节点：思维特点
    CHAT,       // 对话气泡：表达特点
}

@Immutable
data class SkillCapabilityItem(
    val title: String,
    val detail: String,
    val iconType: SkillCapabilityIconType,
)

/**
 * 会话记录抽屉数据模型
 */
@Immutable
data class ConversationDrawerUiModel(
    val currentSessionId: String = "",
    val searchQuery: String = "",
    val groups: List<ConversationGroupUiModel> = emptyList(),
    val archivedCount: Int = 0,
    val isShowingArchived: Boolean = false,
)

@Immutable
data class ConversationGroupUiModel(
    val groupName: String, // 今天、昨天、更早
    val sessions: List<SessionSummaryUiModel>,
)

@Immutable
data class SessionSummaryUiModel(
    val id: String,
    val title: String,
    val previewText: String,
    val time: String,
    val roleAvatars: List<String>,
    val roleCountText: String,
    val isSelected: Boolean = false,
)

/**
 * 增加角色 Catalog 模型
 */
@Immutable
data class AddSkillCatalogUiModel(
    val searchQuery: String = "",
    val recentUsed: List<SkillRoleUiModel> = emptyList(),
    val recommended: List<SkillRoleUiModel> = emptyList(),
    val allSkills: List<SkillRoleUiModel> = emptyList(),
)
