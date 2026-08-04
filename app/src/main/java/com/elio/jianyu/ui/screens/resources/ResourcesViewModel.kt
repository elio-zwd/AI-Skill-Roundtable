package com.elio.jianyu.ui.screens.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elio.jianyu.data.ChangeMaterialLifecycleCommand
import com.elio.jianyu.data.ChangePersonalContextLifecycleCommand
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.data.ContextSourceType
import com.elio.jianyu.data.CreateMaterialCommand
import com.elio.jianyu.data.CreatePersonalContextCommand
import com.elio.jianyu.data.IssueLifecycleState
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.Material
import com.elio.jianyu.data.MaterialFilter
import com.elio.jianyu.data.PersonalContext
import com.elio.jianyu.data.PersonalContextFilter
import com.elio.jianyu.data.PurgeMaterialCommand
import com.elio.jianyu.data.PurgePersonalContextCommand
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.UpdateMaterialCommand
import com.elio.jianyu.data.UpdatePersonalContextCommand
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ResourcesViewModel internal constructor(
    private val repository: JianyuRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ResourcesUiState>(ResourcesUiState.Loading)
    val state: StateFlow<ResourcesUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ResourcesUiState.Loading
            val issueResult = repository.listIssueNavigation(setOf(IssueLifecycleState.ACTIVE))
            val materialResult = repository.listMaterials(
                MaterialFilter(lifecycles = ContextSourceLifecycle.entries.toSet()),
            )
            val personalResult = repository.listPersonalContexts(
                PersonalContextFilter(lifecycles = ContextSourceLifecycle.entries.toSet()),
            )
            val hardFailures = listOf(issueResult, materialResult, personalResult)
                .filterIsInstance<RepositoryResult.Failure>()
            if (hardFailures.size == 3) {
                _state.value = ResourcesUiState.Failure(
                    repositoryErrorMessage(hardFailures.first().error),
                )
                return@launch
            }

            val issueOptions = when (issueResult) {
                is RepositoryResult.Success -> issueResult.value.map { navigation ->
                    val stages = when (val recovered = repository.recoverIssue(navigation.issue.id)) {
                        is RepositoryResult.Success -> recovered.value.core.stages.map { stage ->
                            ResourceStageOption(stage.id, stage.title)
                        }
                        is RepositoryResult.Failure -> emptyList()
                    }
                    ResourceIssueOption(
                        issueId = navigation.issue.id,
                        title = navigation.issue.title,
                        stages = stages,
                    )
                }
                is RepositoryResult.Failure -> emptyList()
            }
            _state.value = ResourcesUiState.Content(
                issues = issueOptions,
                materials = (materialResult as? RepositoryResult.Success)
                    ?.value.orEmpty().map(Material::toUi),
                personalContexts = (personalResult as? RepositoryResult.Success)
                    ?.value.orEmpty().map(PersonalContext::toUi),
                partialFailure = hardFailures.firstOrNull()?.let {
                    "部分资料未能读取：${repositoryErrorMessage(it.error)}"
                },
            )
        }
    }

    fun selectSection(section: ResourceLibrarySection) = updateContent { copy(section = section) }

    fun updateQuery(query: String) = updateContent { copy(query = query) }

    fun selectLifecycles(lifecycles: Set<ContextSourceLifecycle>) = updateContent {
        copy(lifecycles = lifecycles.ifEmpty { setOf(ContextSourceLifecycle.ACTIVE) })
    }

    fun openNewMaterial() = updateContent {
        copy(
            editor = ResourceEditorDraft(
                sourceType = ContextSourceType.MATERIAL,
                issueId = issues.firstOrNull()?.issueId.orEmpty(),
                stageId = issues.firstOrNull()?.stages?.firstOrNull()?.stageId,
            ),
        )
    }

    fun openNewPersonalContext() = updateContent {
        copy(editor = ResourceEditorDraft(sourceType = ContextSourceType.PERSONAL_CONTEXT))
    }

    fun editMaterial(item: MaterialUiItem) = updateContent {
        copy(
            editor = ResourceEditorDraft(
                sourceType = ContextSourceType.MATERIAL,
                sourceId = item.id,
                issueId = item.issueId,
                stageId = item.stageId,
                title = item.title,
                sourceKind = item.sourceType,
                sourceLocator = item.sourceLocator.orEmpty(),
                content = item.content,
                sensitive = item.sensitive,
                expectedUpdatedAt = item.updatedAt,
            ),
        )
    }

    fun editPersonalContext(item: PersonalContextUiItem) = updateContent {
        copy(
            editor = ResourceEditorDraft(
                sourceType = ContextSourceType.PERSONAL_CONTEXT,
                sourceId = item.id,
                title = item.title,
                content = item.content,
                sensitive = item.sensitive,
                expectedUpdatedAt = item.updatedAt,
            ),
        )
    }

    fun updateEditor(editor: ResourceEditorDraft) = updateContent { copy(editor = editor) }

    fun dismissEditor() = updateContent { copy(editor = null) }

    fun saveEditor() {
        val content = _state.value as? ResourcesUiState.Content ?: return
        val editor = content.editor ?: return
        if (editor.title.isBlank() || editor.content.isBlank()) {
            updateContent { copy(partialFailure = "标题和正文不能为空。") }
            return
        }
        if (editor.sourceType == ContextSourceType.MATERIAL && editor.issueId.isBlank()) {
            updateContent { copy(partialFailure = "资料必须关联一个议题。") }
            return
        }
        runOperation {
            val now = System.currentTimeMillis()
            when (editor.sourceType) {
                ContextSourceType.MATERIAL -> if (editor.sourceId == null) {
                    repository.createMaterial(
                        CreateMaterialCommand(
                            id = UUID.randomUUID().toString(),
                            issueId = editor.issueId,
                            stageId = editor.stageId,
                            title = editor.title,
                            sourceType = editor.sourceKind.ifBlank { "note" },
                            sourceLocator = editor.sourceLocator,
                            content = editor.content,
                            sourceCapturedAt = now,
                            sensitive = editor.sensitive,
                            createdAt = now,
                        ),
                    )
                } else {
                    repository.updateMaterial(
                        UpdateMaterialCommand(
                            id = editor.sourceId,
                            title = editor.title,
                            sourceType = editor.sourceKind.ifBlank { "note" },
                            sourceLocator = editor.sourceLocator,
                            content = editor.content,
                            sourceCapturedAt = now,
                            sensitive = editor.sensitive,
                            expectedUpdatedAt = requireNotNull(editor.expectedUpdatedAt),
                            updatedAt = nextTimestamp(editor.expectedUpdatedAt, now),
                        ),
                    )
                }
                ContextSourceType.PERSONAL_CONTEXT -> if (editor.sourceId == null) {
                    repository.createPersonalContext(
                        CreatePersonalContextCommand(
                            id = UUID.randomUUID().toString(),
                            title = editor.title,
                            content = editor.content,
                            sensitive = editor.sensitive,
                            createdAt = now,
                        ),
                    )
                } else {
                    repository.updatePersonalContext(
                        UpdatePersonalContextCommand(
                            id = editor.sourceId,
                            title = editor.title,
                            content = editor.content,
                            sensitive = editor.sensitive,
                            expectedUpdatedAt = requireNotNull(editor.expectedUpdatedAt),
                            updatedAt = nextTimestamp(editor.expectedUpdatedAt, now),
                        ),
                    )
                }
            }
        }
    }

    fun changeMaterialLifecycle(item: MaterialUiItem, target: ContextSourceLifecycle) {
        runOperation {
            val now = nextTimestamp(item.updatedAt, System.currentTimeMillis())
            repository.changeMaterialLifecycle(
                ChangeMaterialLifecycleCommand(item.id, item.updatedAt, target, now),
            )
        }
    }

    fun changePersonalContextLifecycle(
        item: PersonalContextUiItem,
        target: ContextSourceLifecycle,
    ) {
        runOperation {
            val now = nextTimestamp(item.updatedAt, System.currentTimeMillis())
            repository.changePersonalContextLifecycle(
                ChangePersonalContextLifecycleCommand(item.id, item.updatedAt, target, now),
            )
        }
    }

    fun requestMaterialPurge(item: MaterialUiItem) {
        runOperation(refreshAfter = false) {
            val requestedAt = nextTimestamp(item.updatedAt, System.currentTimeMillis())
            val changed = repository.changeMaterialLifecycle(
                ChangeMaterialLifecycleCommand(
                    item.id,
                    item.updatedAt,
                    ContextSourceLifecycle.PURGE_REQUESTED,
                    requestedAt,
                ),
            )
            val source = (changed as? RepositoryResult.Success)?.value
                ?: return@runOperation changed
            when (val impact = repository.getMaterialPurgeImpact(item.id)) {
                is RepositoryResult.Success -> {
                    updateContent {
                        copy(
                            purgeConfirmation = ResourcePurgeConfirmation(
                                ContextSourceType.MATERIAL,
                                item.id,
                                item.title,
                                source.updatedAt,
                                impact.value,
                            ),
                            operationInProgress = false,
                        )
                    }
                    impact
                }
                is RepositoryResult.Failure -> impact
            }
        }
    }

    fun requestPersonalContextPurge(item: PersonalContextUiItem) {
        runOperation(refreshAfter = false) {
            val requestedAt = nextTimestamp(item.updatedAt, System.currentTimeMillis())
            val changed = repository.changePersonalContextLifecycle(
                ChangePersonalContextLifecycleCommand(
                    item.id,
                    item.updatedAt,
                    ContextSourceLifecycle.PURGE_REQUESTED,
                    requestedAt,
                ),
            )
            val source = (changed as? RepositoryResult.Success)?.value
                ?: return@runOperation changed
            when (val impact = repository.getPersonalContextPurgeImpact(item.id)) {
                is RepositoryResult.Success -> {
                    updateContent {
                        copy(
                            purgeConfirmation = ResourcePurgeConfirmation(
                                ContextSourceType.PERSONAL_CONTEXT,
                                item.id,
                                item.title,
                                source.updatedAt,
                                impact.value,
                            ),
                            operationInProgress = false,
                        )
                    }
                    impact
                }
                is RepositoryResult.Failure -> impact
            }
        }
    }

    fun cancelPurge() {
        val confirmation = (state.value as? ResourcesUiState.Content)?.purgeConfirmation ?: return
        runOperation {
            val now = nextTimestamp(confirmation.expectedUpdatedAt, System.currentTimeMillis())
            when (confirmation.sourceType) {
                ContextSourceType.MATERIAL -> repository.changeMaterialLifecycle(
                    ChangeMaterialLifecycleCommand(
                        confirmation.sourceId,
                        confirmation.expectedUpdatedAt,
                        ContextSourceLifecycle.DELETED,
                        now,
                    ),
                )
                ContextSourceType.PERSONAL_CONTEXT -> repository.changePersonalContextLifecycle(
                    ChangePersonalContextLifecycleCommand(
                        confirmation.sourceId,
                        confirmation.expectedUpdatedAt,
                        ContextSourceLifecycle.DELETED,
                        now,
                    ),
                )
            }
        }
    }

    fun confirmPurge() {
        val confirmation = (state.value as? ResourcesUiState.Content)?.purgeConfirmation ?: return
        runOperation {
            val now = nextTimestamp(confirmation.expectedUpdatedAt, System.currentTimeMillis())
            when (confirmation.sourceType) {
                ContextSourceType.MATERIAL -> repository.purgeMaterial(
                    PurgeMaterialCommand(confirmation.sourceId, confirmation.expectedUpdatedAt, now),
                )
                ContextSourceType.PERSONAL_CONTEXT -> repository.purgePersonalContext(
                    PurgePersonalContextCommand(
                        confirmation.sourceId,
                        confirmation.expectedUpdatedAt,
                        now,
                    ),
                )
            }
        }
    }

    private fun runOperation(
        refreshAfter: Boolean = true,
        operation: suspend () -> RepositoryResult<*>,
    ) {
        val current = _state.value as? ResourcesUiState.Content ?: return
        if (current.operationInProgress) return
        viewModelScope.launch {
            _state.value = current.copy(operationInProgress = true, partialFailure = null)
            when (val result = operation()) {
                is RepositoryResult.Success -> if (refreshAfter) refresh() else Unit
                is RepositoryResult.Failure -> updateContent {
                    copy(
                        operationInProgress = false,
                        partialFailure = repositoryErrorMessage(result.error),
                    )
                }
            }
        }
    }

    private fun updateContent(transform: ResourcesUiState.Content.() -> ResourcesUiState.Content) {
        val current = _state.value as? ResourcesUiState.Content ?: return
        _state.value = current.transform()
    }

    companion object {
        fun factory(repository: JianyuRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ResourcesViewModel::class.java))
                    return ResourcesViewModel(repository) as T
                }
            }
    }
}

private fun Material.toUi(): MaterialUiItem = MaterialUiItem(
    id = id,
    issueId = issueId,
    stageId = stageId,
    title = title.ifBlank { "内容已清除" },
    sourceType = sourceType,
    sourceLocator = sourceLocator,
    contentPreview = preview(content, sensitive, lifecycle),
    content = content,
    sourcePublishedAt = sourcePublishedAt,
    sourceCapturedAt = sourceCapturedAt,
    sensitive = sensitive,
    lifecycle = lifecycle,
    updatedAt = updatedAt,
)

private fun PersonalContext.toUi(): PersonalContextUiItem = PersonalContextUiItem(
    id = id,
    title = title.ifBlank { "内容已清除" },
    contentPreview = preview(content, sensitive, lifecycle),
    content = content,
    sensitive = sensitive,
    lifecycle = lifecycle,
    updatedAt = updatedAt,
)

private fun preview(
    content: String,
    sensitive: Boolean,
    lifecycle: ContextSourceLifecycle,
): String = when {
    lifecycle == ContextSourceLifecycle.PURGED -> "内容已清除"
    sensitive -> "敏感内容已隐藏，进入编辑后查看"
    else -> content.lineSequence().firstOrNull().orEmpty().take(120)
}

private fun repositoryErrorMessage(error: RepositoryError): String = when (error) {
    is RepositoryError.NotFound -> "关联内容不存在或已被清除。"
    is RepositoryError.InvalidState -> "内容状态已变化，请刷新后重试。"
    is RepositoryError.IdempotencyConflict -> "稳定 ID 已用于不同内容。"
    is RepositoryError.ConstraintViolation -> "内容不符合保存或确认约束。"
    is RepositoryError.StorageFailure -> "本地存储暂时不可用。"
    is RepositoryError.AlreadyExists -> "内容已经存在。"
    is RepositoryError.CompatibilityFailure -> "当前数据版本暂时无法处理。"
}

private fun nextTimestamp(previous: Long?, now: Long): Long =
    if (previous == null) now.coerceAtLeast(1L) else maxOf(now, previous + 1L)
