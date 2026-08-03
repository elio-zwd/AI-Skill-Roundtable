package com.elio.jianyu.ui.screens.issues

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.RoomJianyuRepository
import com.elio.jianyu.data.RoundtableDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private object JianyuRepositoryProvider {
    private val databaseScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var repository: JianyuRepository? = null

    fun get(context: Context): JianyuRepository =
        repository ?: synchronized(this) {
            repository ?: RoomJianyuRepository(
                RoundtableDatabase.getDatabase(
                    context = context.applicationContext,
                    scope = databaseScope,
                ),
            ).also { created -> repository = created }
        }
}

private fun createIssuesNavigationLoader(context: Context): IssuesNavigationLoader {
    val reader = JianyuIssueNavigationReader(
        JianyuRepositoryProvider.get(context.applicationContext),
    )
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
        fun factory(context: Context): ViewModelProvider.Factory =
            issuesViewModelFactory {
                IssuesViewModel(createIssuesNavigationLoader(context))
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
        fun factory(context: Context): ViewModelProvider.Factory =
            issuesViewModelFactory {
                IssueRecoveryViewModel(createIssuesNavigationLoader(context))
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
