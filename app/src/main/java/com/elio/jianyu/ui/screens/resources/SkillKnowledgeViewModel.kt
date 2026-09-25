package com.elio.jianyu.ui.screens.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elio.jianyu.skill.knowledge.SkillKnowledgeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SkillKnowledgeViewModel internal constructor(
    private val repository: SkillKnowledgeRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<SkillKnowledgeUiState>(SkillKnowledgeUiState.Loading)
    val state: StateFlow<SkillKnowledgeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = SkillKnowledgeUiState.Loading
            _state.value = try {
                val documents = withContext(Dispatchers.IO) {
                    repository.listSkills().flatMap { skill ->
                        skill.documents.map { document ->
                            SkillKnowledgeDocumentUiItem(
                                documentId = document.documentId,
                                skillId = skill.skillId,
                                skillName = skill.skillName,
                                relativePath = document.relativePath,
                                title = document.title,
                                type = document.type,
                                contentHash = document.contentHash,
                                content = repository.loadDocumentContent(
                                    skill.skillId,
                                    document.documentId,
                                ),
                            )
                        }
                    }
                }
                SkillKnowledgeUiState.Content(documents = documents)
            } catch (_: Exception) {
                SkillKnowledgeUiState.Failure("Skill 资料暂时无法读取。")
            }
        }
    }

    fun updateQuery(query: String) = updateContent { copy(query = query) }

    fun openDocument(documentId: String) = updateContent {
        copy(selectedDocumentId = documentId)
    }

    fun dismissDocument() = updateContent { copy(selectedDocumentId = null) }

    fun reportUseResult(success: Boolean) = updateContent {
        copy(message = if (success) "已带入当前会话。" else "当前没有可带入的会话。")
    }

    private fun updateContent(
        transform: SkillKnowledgeUiState.Content.() -> SkillKnowledgeUiState.Content,
    ) {
        val current = _state.value as? SkillKnowledgeUiState.Content ?: return
        _state.value = current.transform()
    }

    companion object {
        fun factory(repository: SkillKnowledgeRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(SkillKnowledgeViewModel::class.java))
                    return SkillKnowledgeViewModel(repository) as T
                }
            }
    }
}
