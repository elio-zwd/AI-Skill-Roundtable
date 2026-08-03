package com.elio.jianyu.ui.screens.skills

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.elio.jianyu.data.DeleteOfficialSkillCombinationCommand
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.OfficialSkillCombinationEntity
import com.elio.jianyu.data.OfficialSkillCombinationMemberEntity
import com.elio.jianyu.data.OfficialSkillCombinationSnapshot
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.SaveOfficialSkillCombinationCommand
import com.elio.jianyu.skill.catalog.OfficialSkillCatalog
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogFilters
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogQuery
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntime
import com.elio.jianyu.skill.catalog.OfficialSkillCatalogRuntimeResult
import com.elio.jianyu.skill.catalog.OfficialSkillCombinationDraft
import com.elio.jianyu.skill.catalog.OfficialSkillCombinationDraftValidator
import com.elio.jianyu.skill.catalog.OfficialSkillCombinationMemberDraft
import com.elio.jianyu.skill.catalog.OfficialSkillMaterialRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillNetworkRequirement
import com.elio.jianyu.skill.catalog.OfficialSkillPreferences
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryType
import com.elio.jianyu.skill.catalog.OfficialSkillPrimaryValue
import com.elio.jianyu.skill.catalog.OfficialSkillPublicationStatus
import com.elio.jianyu.skill.catalog.OfficialSkillRiskLevel
import com.elio.jianyu.skill.catalog.OfficialSkillUseMode
import com.elio.jianyu.skill.catalog.OfficialSkillUseRequest
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * PR09-04 可直接消费的稳定公共入口。
 *
 * App 组合层应先创建 [OfficialSkillCatalogRuntime]，使用其中 validator 构造
 * RoomJianyuRepository，再把同一 runtime 传入本 Route，保证目录与 ID 校验事实源一致。
 */
@Composable
fun OfficialSkillCatalogRoute(
    repository: JianyuRepository,
    runtime: OfficialSkillCatalogRuntime,
    onUseSkill: (OfficialSkillUseRequest) -> Unit,
    modifier: Modifier = Modifier,
    initialSkillId: String? = null,
    onDismissInitialDetail: (() -> Unit)? = null,
    clock: () -> Long = System::currentTimeMillis,
    combinationIdFactory: () -> String = { "official-combination-${UUID.randomUUID()}" },
) {
    OfficialSkillCatalogRoute(
        repository = repository,
        catalog = runtime.catalog,
        preferences = runtime.preferences,
        onUseSkill = onUseSkill,
        modifier = modifier,
        initialSkillId = initialSkillId,
        onDismissInitialDetail = onDismissInitialDetail,
        clock = clock,
        combinationIdFactory = combinationIdFactory,
    )
}

/** Catalog 解析失败时保持本地错误状态，不降级为允许任意官方 ID。 */
@Composable
fun OfficialSkillCatalogRoute(
    repository: JianyuRepository,
    runtimeResult: OfficialSkillCatalogRuntimeResult,
    onUseSkill: (OfficialSkillUseRequest) -> Unit,
    modifier: Modifier = Modifier,
    initialSkillId: String? = null,
    onDismissInitialDetail: (() -> Unit)? = null,
) {
    when (runtimeResult) {
        is OfficialSkillCatalogRuntimeResult.Failure -> OfficialSkillCatalogScreen(
            uiState = OfficialSkillCatalogUiState(
                isLoading = false,
                catalogError = runtimeResult.message,
            ),
            onEvent = {},
            modifier = modifier,
        )
        is OfficialSkillCatalogRuntimeResult.Success -> OfficialSkillCatalogRoute(
            repository = repository,
            runtime = runtimeResult.runtime,
            onUseSkill = onUseSkill,
            modifier = modifier,
            initialSkillId = initialSkillId,
            onDismissInitialDetail = onDismissInitialDetail,
        )
    }
}

@Composable
fun OfficialSkillCatalogRoute(
    repository: JianyuRepository,
    catalog: OfficialSkillCatalog,
    preferences: OfficialSkillPreferences,
    onUseSkill: (OfficialSkillUseRequest) -> Unit,
    modifier: Modifier = Modifier,
    initialSkillId: String? = null,
    onDismissInitialDetail: (() -> Unit)? = null,
    clock: () -> Long = System::currentTimeMillis,
    combinationIdFactory: () -> String = { "official-combination-${UUID.randomUUID()}" },
) {
    if (initialSkillId != null && !catalog.containsOfficialId(initialSkillId)) {
        OfficialSkillCatalogScreen(
            uiState = OfficialSkillCatalogUiState(
                isLoading = false,
                catalogError = "无法定位官方 Skill：未知 ID $initialSkillId",
            ),
            onEvent = {},
            modifier = modifier,
        )
        return
    }

    val scope = rememberCoroutineScope()
    val favoriteIds by preferences.favoriteIds.collectAsState()
    val recentUses by preferences.recentUses.collectAsState()

    var query by rememberSaveable { mutableStateOf("") }
    var sectionName by rememberSaveable {
        mutableStateOf(OfficialSkillCatalogSection.DISCOVER.name)
    }
    val section = runCatching { OfficialSkillCatalogSection.valueOf(sectionName) }
        .getOrDefault(OfficialSkillCatalogSection.DISCOVER)
    var filters by rememberSaveable(stateSaver = officialSkillCatalogFiltersSaver) {
        mutableStateOf(OfficialSkillCatalogFilters())
    }
    var filterDialogVisible by rememberSaveable { mutableStateOf(false) }
    var selectedSkillId by rememberSaveable(initialSkillId) {
        mutableStateOf(initialSkillId)
    }
    var combinations by remember { mutableStateOf<List<OfficialSkillCombinationSnapshot>>(emptyList()) }
    var combinationsLoading by remember { mutableStateOf(true) }
    var combinationError by remember { mutableStateOf<String?>(null) }
    var editor by remember { mutableStateOf<OfficialSkillCombinationEditorState?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun replaceCombination(snapshot: OfficialSkillCombinationSnapshot) {
        combinations = (combinations.filterNot { it.combination.id == snapshot.combination.id } + snapshot)
            .sortedWith(
                compareBy<OfficialSkillCombinationSnapshot> { it.combination.name.lowercase() }
                    .thenBy { it.combination.id },
            )
    }

    fun reloadCombinations() {
        scope.launch {
            combinationsLoading = true
            combinationError = null
            when (val result = repository.listOfficialSkillCombinations()) {
                is RepositoryResult.Success -> {
                    combinations = result.value.sortedWith(
                        compareBy<OfficialSkillCombinationSnapshot> { it.combination.name.lowercase() }
                            .thenBy { it.combination.id },
                    )
                }
                is RepositoryResult.Failure -> {
                    combinationError = result.error.toUserMessage()
                }
            }
            combinationsLoading = false
        }
    }

    LaunchedEffect(repository) {
        combinationsLoading = true
        combinationError = null
        when (val result = repository.listOfficialSkillCombinations()) {
            is RepositoryResult.Success -> {
                combinations = result.value.sortedWith(
                    compareBy<OfficialSkillCombinationSnapshot> { it.combination.name.lowercase() }
                        .thenBy { it.combination.id },
                )
            }
            is RepositoryResult.Failure -> combinationError = result.error.toUserMessage()
        }
        combinationsLoading = false
    }

    LaunchedEffect(initialSkillId, preferences) {
        initialSkillId?.let { preferences.onSkillDetailViewed(it) }
    }

    val recentIds = recentUses.mapTo(linkedSetOf()) { it.skillId }
    val effectiveFilters = when (section) {
        OfficialSkillCatalogSection.DISCOVER -> filters
        OfficialSkillCatalogSection.FAVORITES -> filters.copy(favoritesOnly = true)
        OfficialSkillCatalogSection.RECENT -> filters.copy(recentOnly = true)
        OfficialSkillCatalogSection.COMBINATIONS -> filters
    }
    val queriedSkills = OfficialSkillCatalogQuery.apply(
        catalog = catalog,
        query = query,
        filters = effectiveFilters,
        favoriteIds = favoriteIds,
        recentSkillIds = recentIds,
    )
    val visibleSkills = if (section == OfficialSkillCatalogSection.RECENT) {
        val recentOrder = recentUses.mapIndexed { index, use -> use.skillId to index }.toMap()
        queriedSkills.sortedWith(
            compareBy<com.elio.jianyu.skill.catalog.OfficialSkillDefinition> {
                recentOrder[it.id] ?: Int.MAX_VALUE
            }.thenBy { it.defaultOrder },
        )
    } else {
        queriedSkills
    }

    val uiState = OfficialSkillCatalogUiState(
        isLoading = false,
        query = query,
        filters = filters,
        filterDialogVisible = filterDialogVisible,
        section = section,
        visibleSkills = visibleSkills,
        totalSkillCount = catalog.skills.size,
        favoriteIds = favoriteIds,
        recentUses = recentUses,
        selectedSkill = selectedSkillId?.let(catalog::findById),
        combinations = combinations,
        combinationsLoading = combinationsLoading,
        combinationError = combinationError,
        combinationEditor = editor,
        message = message,
    )

    OfficialSkillCatalogScreen(
        uiState = uiState,
        modifier = modifier,
        onEvent = { event ->
            when (event) {
                is OfficialSkillCatalogEvent.SearchChanged -> query = event.value
                is OfficialSkillCatalogEvent.SectionChanged -> sectionName = event.value.name
                is OfficialSkillCatalogEvent.FilterDialogChanged -> {
                    filterDialogVisible = event.visible
                }
                is OfficialSkillCatalogEvent.TogglePrimaryType -> {
                    filters = filters.copy(primaryTypes = filters.primaryTypes.toggle(event.value))
                }
                is OfficialSkillCatalogEvent.TogglePrimaryValue -> {
                    filters = filters.copy(primaryValues = filters.primaryValues.toggle(event.value))
                }
                is OfficialSkillCatalogEvent.ToggleUseMode -> {
                    filters = filters.copy(useModes = filters.useModes.toggle(event.value))
                }
                is OfficialSkillCatalogEvent.ToggleNetwork -> {
                    filters = filters.copy(
                        networkRequirements = filters.networkRequirements.toggle(event.value),
                    )
                }
                is OfficialSkillCatalogEvent.ToggleMaterial -> {
                    filters = filters.copy(
                        materialRequirements = filters.materialRequirements.toggle(event.value),
                    )
                }
                is OfficialSkillCatalogEvent.ToggleRisk -> {
                    filters = filters.copy(risks = filters.risks.toggle(event.value))
                }
                is OfficialSkillCatalogEvent.TogglePublication -> {
                    filters = filters.copy(
                        publicationStatuses = filters.publicationStatuses.toggle(event.value),
                    )
                }
                OfficialSkillCatalogEvent.ToggleExecutableOnly -> {
                    filters = filters.copy(executableOnly = !filters.executableOnly)
                }
                OfficialSkillCatalogEvent.ClearFilters -> filters = OfficialSkillCatalogFilters()
                is OfficialSkillCatalogEvent.OpenDetail -> {
                    if (catalog.containsOfficialId(event.skillId)) {
                        selectedSkillId = event.skillId
                        scope.launch { preferences.onSkillDetailViewed(event.skillId) }
                    } else {
                        message = "未知官方 Skill ID"
                    }
                }
                OfficialSkillCatalogEvent.DismissDetail -> {
                    val leavesInitialDetail = initialSkillId != null && selectedSkillId == initialSkillId
                    selectedSkillId = null
                    if (leavesInitialDetail) onDismissInitialDetail?.invoke()
                }
                is OfficialSkillCatalogEvent.ToggleFavorite -> scope.launch {
                    val isFavorite = event.skillId in favoriteIds
                    if (!preferences.setFavorite(event.skillId, !isFavorite)) {
                        message = "收藏保存失败或 Skill ID 已失效"
                    }
                }
                is OfficialSkillCatalogEvent.UseSkill -> {
                    val skill = catalog.findById(event.skillId)
                    when {
                        skill == null -> message = "未知官方 Skill ID"
                        !skill.availability.executable -> {
                            message = skill.nonExecutableReason ?: "该 Skill 当前不可执行"
                        }
                        else -> onUseSkill(OfficialSkillUseRequest(skillId = skill.id))
                    }
                }
                is OfficialSkillCatalogEvent.CreateCombination -> {
                    val now = clock()
                    val seedMembers = event.seedSkillId
                        ?.takeIf(catalog::containsOfficialId)
                        ?.let { listOf(OfficialSkillCombinationMemberEditorState(it)) }
                        .orEmpty()
                    editor = OfficialSkillCombinationEditorState(
                        combinationId = combinationIdFactory(),
                        name = "",
                        members = seedMembers,
                        createdAt = now,
                        expectedUpdatedAt = null,
                    )
                }
                is OfficialSkillCatalogEvent.EditCombination -> {
                    val snapshot = combinations.firstOrNull {
                        it.combination.id == event.combinationId
                    }
                    if (snapshot == null) {
                        message = "组合不存在或已删除"
                    } else {
                        editor = OfficialSkillCombinationEditorState(
                            combinationId = snapshot.combination.id,
                            name = snapshot.combination.name,
                            members = snapshot.members
                                .sortedBy { it.position }
                                .map {
                                    OfficialSkillCombinationMemberEditorState(
                                        skillId = it.officialSkillId,
                                        defaultResponsibility = it.defaultResponsibility.orEmpty(),
                                    )
                                },
                            createdAt = snapshot.combination.createdAt,
                            expectedUpdatedAt = snapshot.combination.updatedAt,
                        )
                    }
                }
                is OfficialSkillCatalogEvent.DeleteCombination -> {
                    val snapshot = combinations.firstOrNull {
                        it.combination.id == event.combinationId
                    }
                    if (snapshot == null) {
                        message = "组合不存在或已删除"
                    } else {
                        scope.launch {
                            when (
                                val result = repository.deleteOfficialSkillCombination(
                                    DeleteOfficialSkillCombinationCommand(
                                        combinationId = snapshot.combination.id,
                                        expectedUpdatedAt = snapshot.combination.updatedAt,
                                        deletedAt = clock(),
                                    ),
                                )
                            ) {
                                is RepositoryResult.Success -> {
                                    combinations = combinations.filterNot {
                                        it.combination.id == snapshot.combination.id
                                    }
                                    message = "组合已移入软删除状态"
                                }
                                is RepositoryResult.Failure -> {
                                    message = result.error.toUserMessage()
                                    reloadCombinations()
                                }
                            }
                        }
                    }
                }
                OfficialSkillCatalogEvent.DismissCombinationEditor -> editor = null
                is OfficialSkillCatalogEvent.CombinationNameChanged -> {
                    editor = editor?.copy(name = event.value, validationMessage = null)
                }
                is OfficialSkillCatalogEvent.ToggleCombinationMember -> {
                    editor = editor?.let { current ->
                        val updated = if (current.members.any { it.skillId == event.skillId }) {
                            current.members.filterNot { it.skillId == event.skillId }
                        } else if (catalog.containsOfficialId(event.skillId)) {
                            current.members + OfficialSkillCombinationMemberEditorState(event.skillId)
                        } else {
                            current.members
                        }
                        current.copy(members = updated, validationMessage = null)
                    }
                }
                is OfficialSkillCatalogEvent.MoveCombinationMember -> {
                    editor = editor?.let { current ->
                        val from = current.members.indexOfFirst { it.skillId == event.skillId }
                        val to = from + event.offset
                        if (from < 0 || to !in current.members.indices) {
                            current
                        } else {
                            val mutable = current.members.toMutableList()
                            val moved = mutable.removeAt(from)
                            mutable.add(to, moved)
                            current.copy(members = mutable, validationMessage = null)
                        }
                    }
                }
                is OfficialSkillCatalogEvent.CombinationResponsibilityChanged -> {
                    editor = editor?.let { current ->
                        current.copy(
                            members = current.members.map { member ->
                                if (member.skillId == event.skillId) {
                                    member.copy(defaultResponsibility = event.value)
                                } else {
                                    member
                                }
                            },
                            validationMessage = null,
                        )
                    }
                }
                OfficialSkillCatalogEvent.SaveCombination -> {
                    val current = editor
                    if (current != null && !current.isSaving) {
                        val draft = OfficialSkillCombinationDraft(
                            name = current.name,
                            members = current.members.map { member ->
                                OfficialSkillCombinationMemberDraft(
                                    skillId = member.skillId,
                                    defaultResponsibility = member.defaultResponsibility
                                        .trim()
                                        .takeIf(String::isNotEmpty),
                                )
                            },
                        )
                        val issues = OfficialSkillCombinationDraftValidator.validate(draft, catalog)
                        if (issues.isNotEmpty()) {
                            editor = current.copy(
                                validationMessage = issues.first().toUserMessage(),
                            )
                        } else {
                            editor = current.copy(isSaving = true, validationMessage = null)
                            scope.launch {
                                val now = clock()
                                val existingMembers = combinations
                                    .firstOrNull { it.combination.id == current.combinationId }
                                    ?.members
                                    .orEmpty()
                                    .associateBy { it.officialSkillId }
                                val command = SaveOfficialSkillCombinationCommand(
                                    combination = OfficialSkillCombinationEntity(
                                        id = current.combinationId,
                                        name = current.name.trim(),
                                        isEnabled = true,
                                        createdAt = current.createdAt,
                                        updatedAt = now,
                                        deletedAt = null,
                                    ),
                                    members = current.members.mapIndexed { position, member ->
                                        OfficialSkillCombinationMemberEntity(
                                            combinationId = current.combinationId,
                                            officialSkillId = member.skillId,
                                            position = position,
                                            defaultResponsibility = member.defaultResponsibility
                                                .trim()
                                                .takeIf(String::isNotEmpty),
                                            createdAt = existingMembers[member.skillId]?.createdAt ?: now,
                                        )
                                    },
                                    expectedUpdatedAt = current.expectedUpdatedAt,
                                )
                                when (val result = repository.saveOfficialSkillCombination(command)) {
                                    is RepositoryResult.Success -> {
                                        replaceCombination(result.value)
                                        editor = null
                                        message = "官方 Skill 组合已保存"
                                    }
                                    is RepositoryResult.Failure -> {
                                        editor = current.copy(
                                            isSaving = false,
                                            validationMessage = result.error.toUserMessage(),
                                        )
                                        if (result.error.isConcurrencyConflict()) reloadCombinations()
                                    }
                                }
                            }
                        }
                    }
                }
                OfficialSkillCatalogEvent.DismissMessage -> message = null
            }
        },
    )
}

private fun RepositoryError.toUserMessage(): String = when (this) {
    is RepositoryError.NotFound -> "目标不存在或已删除"
    is RepositoryError.AlreadyExists -> "目标已存在"
    is RepositoryError.IdempotencyConflict -> "数据已被其他操作更新，请刷新后重试"
    is RepositoryError.InvalidState -> "当前状态不允许该操作"
    is RepositoryError.ConstraintViolation -> when (constraintCode) {
        "unknown_official_skill_id" -> "组合中包含未知官方 Skill ID"
        "duplicate_official_skill_id" -> "同一 Skill 不能重复加入组合"
        "duplicate_member_position" -> "组合成员位置不能重复"
        else -> "组合数据不符合约束：$constraintCode"
    }
    is RepositoryError.StorageFailure -> if (retryable) {
        "存储暂时失败，请稍后重试"
    } else {
        "存储失败"
    }
    is RepositoryError.CompatibilityFailure -> "当前数据与应用版本不兼容"
}

private fun RepositoryError.isConcurrencyConflict(): Boolean =
    this is RepositoryError.IdempotencyConflict ||
        (this is RepositoryError.InvalidState && stateCode.contains("updated", ignoreCase = true))

private fun com.elio.jianyu.skill.catalog.OfficialSkillCombinationDraftIssue.toUserMessage(): String =
    when (code) {
        "blank_name" -> "请输入组合名称"
        "name_too_long" -> "组合名称过长"
        "empty_members" -> "请至少选择一个官方 Skill"
        "duplicate_skill_id" -> "同一 Skill 不能重复加入组合"
        "unknown_official_skill_id" -> "组合中包含未知官方 Skill ID"
        "default_responsibility_too_long" -> "默认职责过长"
        "unsafe_default_responsibility" -> "默认职责不得覆盖官方 Prompt、系统边界或安全规则"
        else -> "组合数据不完整"
    }

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private val officialSkillCatalogFiltersSaver = mapSaver(
    save = { filters ->
        mapOf(
            "primaryTypes" to filters.primaryTypes.joinToString(",") { it.name },
            "primaryValues" to filters.primaryValues.joinToString(",") { it.name },
            "useModes" to filters.useModes.joinToString(",") { it.name },
            "network" to filters.networkRequirements.joinToString(",") { it.name },
            "materials" to filters.materialRequirements.joinToString(",") { it.name },
            "risks" to filters.risks.joinToString(",") { it.name },
            "publication" to filters.publicationStatuses.joinToString(",") { it.name },
            "executable" to filters.executableOnly,
        )
    },
    restore = { values ->
        OfficialSkillCatalogFilters(
            primaryTypes = enumSet(values["primaryTypes"] as? String, OfficialSkillPrimaryType.entries),
            primaryValues = enumSet(values["primaryValues"] as? String, OfficialSkillPrimaryValue.entries),
            useModes = enumSet(values["useModes"] as? String, OfficialSkillUseMode.entries),
            networkRequirements = enumSet(
                values["network"] as? String,
                OfficialSkillNetworkRequirement.entries,
            ),
            materialRequirements = enumSet(
                values["materials"] as? String,
                OfficialSkillMaterialRequirement.entries,
            ),
            risks = enumSet(values["risks"] as? String, OfficialSkillRiskLevel.entries),
            publicationStatuses = enumSet(
                values["publication"] as? String,
                OfficialSkillPublicationStatus.entries,
            ),
            executableOnly = values["executable"] as? Boolean ?: false,
        )
    },
)

private fun <T : Enum<T>> enumSet(
    encoded: String?,
    values: List<T>,
): Set<T> {
    if (encoded.isNullOrBlank()) return emptySet()
    val byName = values.associateBy { it.name }
    return encoded.split(',').mapNotNullTo(linkedSetOf(), byName::get)
}
