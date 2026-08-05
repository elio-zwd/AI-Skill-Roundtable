package com.elio.jianyu.ui.screens.resources

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.result.ArtifactType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class ArtifactLibraryViewModel(
    private val loader: ArtifactLibraryLoader,
) : ViewModel() {
    private val _state = MutableStateFlow<ArtifactLibraryUiState>(ArtifactLibraryUiState.Loading)
    val state: StateFlow<ArtifactLibraryUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ArtifactLibraryUiState.Loading
            _state.value = loader.load()
        }
    }

    fun updateQuery(query: String) {
        updateContent { copy(query = query) }
    }

    fun selectTypes(types: Set<ArtifactType>) {
        updateContent { copy(selectedTypes = types) }
    }

    fun setIncludeHistory(includeHistory: Boolean) {
        updateContent { copy(includeHistory = includeHistory) }
    }

    fun openArtifact(artifactId: String) {
        updateContent { copy(selectedArtifactId = artifactId) }
    }

    fun dismissArtifact() {
        updateContent { copy(selectedArtifactId = null) }
    }

    private fun updateContent(
        transform: ArtifactLibraryUiState.Content.() -> ArtifactLibraryUiState.Content,
    ) {
        _state.value = when (val current = _state.value) {
            is ArtifactLibraryUiState.Content -> current.transform()
            is ArtifactLibraryUiState.PartialFailure -> current.copy(
                content = current.content.transform(),
            )
            ArtifactLibraryUiState.Empty,
            ArtifactLibraryUiState.Loading,
            is ArtifactLibraryUiState.Failure,
            -> current
        }
    }

    companion object {
        fun factory(repository: JianyuRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ArtifactLibraryViewModel::class.java))
                    return ArtifactLibraryViewModel(ArtifactLibraryLoader(repository)) as T
                }
            }
    }
}
