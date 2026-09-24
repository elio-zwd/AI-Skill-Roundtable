package com.elio.jianyu.ui.screens.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.elio.jianyu.data.ChangePersonalContextLifecycleCommand
import com.elio.jianyu.data.ContextSourceLifecycle
import com.elio.jianyu.data.CreatePersonalContextCommand
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.PersonalContext
import com.elio.jianyu.data.PersonalContextFilter
import com.elio.jianyu.data.PurgePersonalContextCommand
import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.UpdatePersonalContextCommand
import com.elio.jianyu.ui.components.JianyuBadge
import com.elio.jianyu.ui.components.JianyuMetadataRow
import com.elio.jianyu.ui.components.JianyuPageShell
import com.elio.jianyu.ui.components.JianyuStateCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

object PersonalContextTestTags {
    const val SCREEN = "personal_context_screen"
    const val SEARCH = "personal_context_search"
    const val ADD = "personal_context_add"
    const val EMPTY = "personal_context_empty"
    const val EDITOR = "personal_context_editor"
    const val SAVE = "personal_context_save"
    const val DELETE_CONFIRMATION = "personal_context_delete_confirmation"
    const val DELETE_CONFIRM = "personal_context_delete_confirm"
    fun item(id: String): String = "personal_context_item_$id"
}

private data class PersonalContextEditor(
    val source: PersonalContext?,
    val title: String,
    val content: String,
    val sensitive: Boolean,
)

private enum class PersonalContextListFilter {
    ALL,
    SENSITIVE,
    DISABLED,
}

private val visiblePersonalContextLifecycles = setOf(
    ContextSourceLifecycle.ACTIVE,
    ContextSourceLifecycle.DISABLED,
    ContextSourceLifecycle.ARCHIVED,
    ContextSourceLifecycle.DELETED,
    ContextSourceLifecycle.PURGE_REQUESTED,
)

internal enum class PersonalContextDeletionStep {
    MARK_DELETED,
    REQUEST_PURGE,
    PURGE,
    COMPLETE,
}

internal fun personalContextDeletionStep(
    lifecycle: ContextSourceLifecycle,
): PersonalContextDeletionStep = when (lifecycle) {
    ContextSourceLifecycle.ACTIVE,
    ContextSourceLifecycle.DISABLED,
    ContextSourceLifecycle.ARCHIVED,
    -> PersonalContextDeletionStep.MARK_DELETED
    ContextSourceLifecycle.DELETED -> PersonalContextDeletionStep.REQUEST_PURGE
    ContextSourceLifecycle.PURGE_REQUESTED -> PersonalContextDeletionStep.PURGE
    ContextSourceLifecycle.PURGED -> PersonalContextDeletionStep.COMPLETE
}

private fun nextPersonalContextDeleteTimestamp(
    currentUpdatedAt: Long,
    now: Long,
): Long = maxOf(now, currentUpdatedAt + 1L)

internal suspend fun completePersonalContextDeletion(
    repository: JianyuRepository,
    personalContextId: String,
    clock: () -> Long = System::currentTimeMillis,
): RepositoryResult<PersonalContext> {
    var current = when (val loaded = repository.getPersonalContext(personalContextId)) {
        is RepositoryResult.Success -> loaded.value
        is RepositoryResult.Failure -> return loaded
    }

    while (true) {
        when (personalContextDeletionStep(current.lifecycle)) {
            PersonalContextDeletionStep.MARK_DELETED -> {
                when (
                    val result = repository.changePersonalContextLifecycle(
                        ChangePersonalContextLifecycleCommand(
                            personalContextId = current.id,
                            expectedUpdatedAt = current.updatedAt,
                            target = ContextSourceLifecycle.DELETED,
                            changedAt = nextPersonalContextDeleteTimestamp(current.updatedAt, clock()),
                        ),
                    )
                ) {
                    is RepositoryResult.Success -> current = result.value
                    is RepositoryResult.Failure -> return result
                }
            }
            PersonalContextDeletionStep.REQUEST_PURGE -> {
                when (
                    val result = repository.changePersonalContextLifecycle(
                        ChangePersonalContextLifecycleCommand(
                            personalContextId = current.id,
                            expectedUpdatedAt = current.updatedAt,
                            target = ContextSourceLifecycle.PURGE_REQUESTED,
                            changedAt = nextPersonalContextDeleteTimestamp(current.updatedAt, clock()),
                        ),
                    )
                ) {
                    is RepositoryResult.Success -> current = result.value
                    is RepositoryResult.Failure -> return result
                }
            }
            PersonalContextDeletionStep.PURGE -> {
                return repository.purgePersonalContext(
                    PurgePersonalContextCommand(
                        personalContextId = current.id,
                        expectedUpdatedAt = current.updatedAt,
                        confirmedAt = nextPersonalContextDeleteTimestamp(current.updatedAt, clock()),
                    ),
                )
            }
            PersonalContextDeletionStep.COMPLETE -> {
                return RepositoryResult.Success(current, idempotent = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonalContextRoute(
    repository: JianyuRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var allItems by remember { mutableStateOf<List<PersonalContext>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var operation by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var listFilter by rememberSaveable { mutableStateOf(PersonalContextListFilter.ALL) }
    var editor by remember { mutableStateOf<PersonalContextEditor?>(null) }
    var deleteTarget by remember { mutableStateOf<PersonalContext?>(null) }
    var deleteInput by remember { mutableStateOf("") }
    var deleteError by remember { mutableStateOf<String?>(null) }

    fun reload() {
        scope.launch {
            loading = true
            val result = withContext(Dispatchers.IO) {
                repository.listPersonalContexts(
                    PersonalContextFilter(lifecycles = visiblePersonalContextLifecycles),
                )
            }
            when (result) {
                is RepositoryResult.Success -> {
                    allItems = result.value
                    errorMessage = null
                }
                is RepositoryResult.Failure -> errorMessage = result.error.toUserMessage()
            }
            loading = false
        }
    }

    LaunchedEffect(repository) { reload() }

    val visibleItems = allItems.filter { item ->
        val matchesFilter = when (listFilter) {
            PersonalContextListFilter.ALL -> true
            PersonalContextListFilter.SENSITIVE -> item.sensitive
            PersonalContextListFilter.DISABLED -> item.lifecycle == ContextSourceLifecycle.DISABLED
        }
        matchesFilter && (
            query.isBlank() ||
                item.title.contains(query, ignoreCase = true) ||
                (!item.sensitive && item.content.contains(query, ignoreCase = true))
            )
    }

    PersonalContextScreen(
        items = visibleItems,
        query = query,
        listFilter = listFilter,
        loading = loading,
        operation = operation,
        errorMessage = errorMessage,
        onBack = onBack,
        onQueryChange = { query = it },
        onListFilterChange = { listFilter = it },
        onRetry = ::reload,
        onAdd = {
            errorMessage = null
            editor = PersonalContextEditor(null, "", "", false)
        },
        onEdit = { item ->
            errorMessage = null
            editor = PersonalContextEditor(item, item.title, item.content, item.sensitive)
        },
        onToggleLifecycle = { item ->
            scope.launch {
                operation = true
                val target = if (item.lifecycle == ContextSourceLifecycle.ACTIVE) {
                    ContextSourceLifecycle.DISABLED
                } else {
                    ContextSourceLifecycle.ACTIVE
                }
                val result = withContext(Dispatchers.IO) {
                    repository.changePersonalContextLifecycle(
                        ChangePersonalContextLifecycleCommand(
                            personalContextId = item.id,
                            expectedUpdatedAt = item.updatedAt,
                            target = target,
                            changedAt = System.currentTimeMillis(),
                        ),
                    )
                }
                operation = false
                when (result) {
                    is RepositoryResult.Success -> reload()
                    is RepositoryResult.Failure -> errorMessage = result.error.toUserMessage()
                }
            }
        },
        onDelete = { item ->
            deleteInput = ""
            deleteError = null
            deleteTarget = item
        },
    )

    editor?.let { draft ->
        PersonalContextEditorSheet(
            editor = draft,
            operation = operation,
            onDismiss = { if (!operation) editor = null },
            onSave = { title, content, sensitive ->
                scope.launch {
                    operation = true
                    val now = System.currentTimeMillis()
                    val result = withContext(Dispatchers.IO) {
                        if (draft.source == null) {
                            repository.createPersonalContext(
                                CreatePersonalContextCommand(
                                    id = "personal-${UUID.randomUUID()}",
                                    title = title,
                                    content = content,
                                    sensitive = sensitive,
                                    createdAt = now,
                                ),
                            )
                        } else {
                            repository.updatePersonalContext(
                                UpdatePersonalContextCommand(
                                    id = draft.source.id,
                                    title = title,
                                    content = content,
                                    sensitive = sensitive,
                                    expectedUpdatedAt = draft.source.updatedAt,
                                    updatedAt = now,
                                ),
                            )
                        }
                    }
                    operation = false
                    when (result) {
                        is RepositoryResult.Success -> {
                            editor = null
                            reload()
                        }
                        is RepositoryResult.Failure -> errorMessage = result.error.toUserMessage()
                    }
                }
            },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            modifier = Modifier.testTag(PersonalContextTestTags.DELETE_CONFIRMATION),
            onDismissRequest = { if (!operation) deleteTarget = null },
            title = { Text("删除这项个人背景？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("删除后将清除这项背景的正文；已产生的使用快照仍保留为历史记录。")
                    Text("请输入“删除”以继续。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    deleteError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                    OutlinedTextField(
                        value = deleteInput,
                        onValueChange = { deleteInput = it },
                        singleLine = true,
                        label = { Text("确认文字") },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = deleteInput.trim() == "删除" && !operation,
                    onClick = {
                        scope.launch {
                            operation = true
                            deleteError = null
                            val result = withContext(Dispatchers.IO) {
                                completePersonalContextDeletion(repository, target.id)
                            }
                            operation = false
                            when (result) {
                                is RepositoryResult.Success -> {
                                    deleteTarget = null
                                    deleteInput = ""
                                    reload()
                                }
                                is RepositoryResult.Failure -> {
                                    deleteError = result.error.toUserMessage()
                                    errorMessage = deleteError
                                    reload()
                                }
                            }
                        }
                    },
                    modifier = Modifier.testTag(PersonalContextTestTags.DELETE_CONFIRM),
                ) { Text("确认删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }, enabled = !operation) { Text("取消") }
            },
        )
    }
}

@Composable
private fun PersonalContextScreen(
    items: List<PersonalContext>,
    query: String,
    listFilter: PersonalContextListFilter,
    loading: Boolean,
    operation: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onListFilterChange: (PersonalContextListFilter) -> Unit,
    onRetry: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (PersonalContext) -> Unit,
    onToggleLifecycle: (PersonalContext) -> Unit,
    onDelete: (PersonalContext) -> Unit,
) {
    JianyuPageShell(
        title = "个人背景",
        subtitle = "由你决定何时使用",
        onBack = onBack,
        contentScrollable = true,
        modifier = Modifier.testTag(PersonalContextTestTags.SCREEN),
    ) {
        JianyuStateCard(
            title = "只在你确认后使用",
            message = "个人背景不会自动发送给 Skill 角色。打开对话的资料与背景选择后，明确勾选的内容才会进入本次执行。",
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("搜索个人背景") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag(PersonalContextTestTags.SEARCH),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = listFilter == PersonalContextListFilter.ALL,
                onClick = { onListFilterChange(PersonalContextListFilter.ALL) },
                label = { Text("全部") },
            )
            FilterChip(
                selected = listFilter == PersonalContextListFilter.SENSITIVE,
                onClick = { onListFilterChange(PersonalContextListFilter.SENSITIVE) },
                label = { Text("敏感") },
            )
            FilterChip(
                selected = listFilter == PersonalContextListFilter.DISABLED,
                onClick = { onListFilterChange(PersonalContextListFilter.DISABLED) },
                label = { Text("已停用") },
            )
        }
        Button(
            onClick = onAdd,
            enabled = !operation,
            modifier = Modifier.testTag(PersonalContextTestTags.ADD),
        ) { Text("+ 添加个人背景") }
        errorMessage?.let { message ->
            JianyuStateCard(
                title = "个人背景操作失败",
                message = message,
                actionLabel = "重试读取",
                onAction = onRetry,
            )
        }
        when {
            loading -> Text("正在读取个人背景…")
            items.isEmpty() -> JianyuStateCard(
                title = "还没有个人背景",
                message = "添加目标、时间、经验或表达偏好；保存后仍由你决定是否用于某次对话。",
                modifier = Modifier.testTag(PersonalContextTestTags.EMPTY),
            )
            else -> items.forEach { item ->
                PersonalContextCard(
                    item = item,
                    operation = operation,
                    onEdit = { onEdit(item) },
                    onToggleLifecycle = { onToggleLifecycle(item) },
                    onDelete = { onDelete(item) },
                )
            }
        }
    }
}

@Composable
private fun PersonalContextCard(
    item: PersonalContext,
    operation: Boolean,
    onEdit: () -> Unit,
    onToggleLifecycle: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(PersonalContextTestTags.item(item.id)),
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (item.sensitive) JianyuBadge("敏感", containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            }
            Text(
                if (item.sensitive) {
                    "敏感内容已隐藏；进入编辑后查看。"
                } else {
                    item.content.replace("\n", " ")
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            JianyuMetadataRow("状态", personalContextLifecycleLabel(item.lifecycle))
            JianyuMetadataRow("最近更新", formatPersonalContextTime(item.updatedAt))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item.lifecycle in setOf(
                        ContextSourceLifecycle.DELETED,
                        ContextSourceLifecycle.PURGE_REQUESTED,
                    )
                ) {
                    TextButton(onClick = onDelete, enabled = !operation) {
                        Text("继续删除")
                    }
                } else {
                    TextButton(onClick = onEdit, enabled = !operation) { Text("编辑") }
                    TextButton(onClick = onToggleLifecycle, enabled = !operation) {
                        Text(if (item.lifecycle == ContextSourceLifecycle.ACTIVE) "停用" else "重新启用")
                    }
                    TextButton(onClick = onDelete, enabled = !operation) { Text("删除") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalContextEditorSheet(
    editor: PersonalContextEditor,
    operation: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
) {
    var title by remember(editor) { mutableStateOf(editor.title) }
    var content by remember(editor) { mutableStateOf(editor.content) }
    var sensitive by remember(editor) { mutableStateOf(editor.sensitive) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(PersonalContextTestTags.EDITOR),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (editor.source == null) "添加个人背景" else "编辑个人背景", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text("内容") },
                minLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = sensitive, onCheckedChange = { sensitive = it })
                Text("标记为敏感内容；下次使用时仍需再次确认")
            }
            Text(
                "保存不会自动把内容发送给任何 Skill 角色。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onDismiss, enabled = !operation, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(
                    onClick = { onSave(title.trim(), content.trim(), sensitive) },
                    enabled = title.isNotBlank() && content.isNotBlank() && !operation,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(PersonalContextTestTags.SAVE),
                ) { Text(if (operation) "保存中…" else "保存") }
            }
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.heightIn(min = 24.dp))
        }
    }
}

private fun ContextSourceLifecycle.label(): String = when (this) {
    ContextSourceLifecycle.ACTIVE -> "可选择使用"
    ContextSourceLifecycle.DISABLED -> "已停用"
    ContextSourceLifecycle.ARCHIVED -> "已归档"
    ContextSourceLifecycle.DELETED -> "已删除"
    ContextSourceLifecycle.PURGE_REQUESTED -> "等待清除"
    ContextSourceLifecycle.PURGED -> "内容已清除"
}

private fun personalContextLifecycleLabel(lifecycle: ContextSourceLifecycle): String = lifecycle.label()

private fun formatPersonalContextTime(timestamp: Long): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(timestamp))

private fun RepositoryError.toUserMessage(): String = when (this) {
    is RepositoryError.NotFound -> "这项个人背景已经不存在，请刷新列表。"
    is RepositoryError.AlreadyExists -> "这项个人背景已经存在。"
    is RepositoryError.IdempotencyConflict -> "保存请求与已有记录冲突，请重新编辑后再试。"
    is RepositoryError.InvalidState -> "当前状态不允许执行该操作，请刷新后重试。"
    is RepositoryError.ConstraintViolation -> "内容不符合保存要求，请检查标题和正文。"
    is RepositoryError.StorageFailure -> "本地数据暂时不可用；可以稍后重试。"
    is RepositoryError.CompatibilityFailure -> "当前数据版本不支持该操作，请先完成维护。"
}
