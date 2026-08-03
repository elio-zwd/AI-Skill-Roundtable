package com.elio.jianyu.ui.screens.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elio.jianyu.data.JianyuRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private fun createIssuesNavigationLoader(repository: JianyuRepository): IssuesNavigationLoader {
    val reader = JianyuIssueNavigationReader(repository)
    return IssuesNavigationLoader(reader)
}

class IssuesViewModel internal constructor(
    private val loader: IssuesNavigationLoader,
) : ViewModel() {
    private val _issuesState = MutableStateFlow<IssuesUiState>(IssuesUiState.Loading)
    val issuesState: StateFlow<IssuesUiState> = _issuesState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _issuesState.value = IssuesUiState.Loading
            _issuesState.value = loader.load()
        }
    }

    companion object {
        fun factory(repository: JianyuRepository): ViewModelProvider.Factory =
            issuesViewModelFactory {
                IssuesViewModel(createIssuesNavigationLoader(repository))
            }
    }
}

class IssueRecoveryViewModel internal constructor(
    private val loader: IssuesNavigationLoader,
) : ViewModel() {
    private val _recoveryState =
        MutableStateFlow<IssueRecoveryUiState>(IssueRecoveryUiState.Loading)
    val recoveryState: StateFlow<IssueRecoveryUiState> = _recoveryState.asStateFlow()

    fun recover(
        issueId: String?,
        stageId: String?,
    ) {
        viewModelScope.launch {
            _recoveryState.value = IssueRecoveryUiState.Loading
            _recoveryState.value = loader.recover(issueId, stageId)
        }
    }

    companion object {
        fun factory(repository: JianyuRepository): ViewModelProvider.Factory =
            issuesViewModelFactory {
                IssueRecoveryViewModel(createIssuesNavigationLoader(repository))
            }
    }
}

private fun <T : ViewModel> issuesViewModelFactory(
    createViewModel: () -> T,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
        val viewModel = createViewModel()
        require(modelClass.isAssignableFrom(viewModel::class.java)) {
            "不支持的 ViewModel 类型：${modelClass.name}"
        }
        return viewModel as VM
    }
}
