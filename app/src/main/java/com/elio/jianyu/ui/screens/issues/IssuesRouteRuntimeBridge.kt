package com.elio.jianyu.ui.screens.issues

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.elio.jianyu.JianyuAppRuntimeProvider
import com.elio.jianyu.data.JianyuRepository

/** 保持既有 App 组装调用兼容，并复用全局唯一运行时，不创建第二套数据库或 Repository。 */
@Composable
fun IssuesRoute(
    repository: JianyuRepository,
    onOpenIssue: (issueId: String, stageId: String?) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: IssuesViewModel = viewModel(
        factory = IssuesViewModel.factory(repository),
    ),
) {
    val applicationContext = LocalContext.current.applicationContext
    val runtime = remember(applicationContext) {
        JianyuAppRuntimeProvider.get(applicationContext).lifecycleRuntime
    }
    IssuesRoute(
        repository = repository,
        lifecycleRuntime = runtime,
        onOpenIssue = onOpenIssue,
        onOpenSettings = onOpenSettings,
        viewModel = viewModel,
    )
}
