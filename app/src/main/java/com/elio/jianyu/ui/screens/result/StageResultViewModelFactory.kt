package com.elio.jianyu.ui.screens.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.elio.jianyu.result.StageResultService

/** 使用应用运行时共享的阶段成果服务创建 ViewModel。 */
fun stageResultViewModelFactory(
    service: StageResultService,
    issueId: String,
    stageId: String,
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(StageResultViewModel::class.java))
        return StageResultViewModel(
            service = service,
            issueId = issueId,
            stageId = stageId,
        ) as T
    }
}
