package com.elio.jianyu.ui.screens.execution

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.elio.jianyu.audio.runtime.JianyuAudioRuntime
import com.elio.jianyu.collaboration.IssueCollaborationCoordinator
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.execution.ExecutionRunCoordinator
import com.elio.jianyu.result.StageResultLoadResult
import com.elio.jianyu.result.StageResultService
import com.elio.jianyu.result.StageResultWorkspace
import com.elio.jianyu.ui.automation.JianyuAudioAutomationTags
import kotlinx.coroutines.launch

/**
 * 在现有单一 IssueExecution 工作区上叠加独立音频资产入口。
 *
 * 候选来源与原成果面板使用同一 StageResultService；恢复只执行读取，不创建资产、
 * 不安排 WorkManager、不访问网络。打开弹窗时重新读取，便于看到刚确认的成果。
 */
@Composable
fun AudioEnabledIssueExecutionRoute(
    repository: JianyuRepository,
    coordinator: ExecutionRunCoordinator?,
    collaborationCoordinator: IssueCollaborationCoordinator?,
    stageResultService: StageResultService,
    audioRuntime: JianyuAudioRuntime,
    issueId: String,
    stageId: String?,
    onBack: () -> Unit,
    onOpenStage: (String, String?) -> Unit = { _, _ -> },
) {
    val scope = rememberCoroutineScope()
    val controller = remember(audioRuntime) {
        AudioAssetWorkspaceController(RuntimeAudioAssetWorkspaceOperations(audioRuntime))
    }
    var audioState by remember { mutableStateOf(controller.state) }
    var workspace by remember { mutableStateOf<StageResultWorkspace?>(null) }
    var workspaceError by remember { mutableStateOf<String?>(null) }
    var dialogVisible by remember { mutableStateOf(false) }

    suspend fun reloadAudioWorkspace() {
        val currentStageId = stageId?.takeIf { it.isNotBlank() }
        if (issueId.isBlank() || currentStageId == null) {
            workspace = null
            workspaceError = "STAGE_NOT_FOUND"
            return
        }
        when (val loaded = stageResultService.load(issueId, currentStageId)) {
            is StageResultLoadResult.Ready -> {
                workspace = loaded.workspace
                workspaceError = null
            }
            is StageResultLoadResult.Failure -> {
                workspace = null
                workspaceError = loaded.errorCode
            }
        }
        controller.load(issueId, currentStageId)
        audioState = controller.state
    }

    LaunchedEffect(issueId, stageId) {
        reloadAudioWorkspace()
    }
    DisposableEffect(controller) {
        onDispose { controller.release() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        IssueExecutionRoute(
            repository = repository,
            coordinator = coordinator,
            collaborationCoordinator = collaborationCoordinator,
            stageResultService = stageResultService,
            issueId = issueId,
            stageId = stageId,
            onBack = onBack,
            onOpenStage = onOpenStage,
        )
        if (issueId.isNotBlank() && !stageId.isNullOrBlank()) {
            FloatingActionButton(
                onClick = {
                    dialogVisible = true
                    scope.launch { reloadAudioWorkspace() }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag(JianyuAudioAutomationTags.ENTRY),
            ) {
                Icon(Icons.Default.VolumeUp, contentDescription = "音频资产")
            }
        }
    }

    if (dialogVisible) {
        Dialog(
            onDismissRequest = { dialogVisible = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.9f)
                    .testTag(JianyuAudioAutomationTags.DIALOG),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                ) {
                    TextButton(
                        onClick = { dialogVisible = false },
                        modifier = Modifier.align(Alignment.End),
                    ) { Text("关闭") }
                    val loadedWorkspace = workspace
                    if (loadedWorkspace == null) {
                        Text(
                            text = "无法读取当前阶段音频来源：${workspaceError ?: "LOADING"}",
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        AudioAssetWorkspacePanel(
                            issueId = loadedWorkspace.issueId,
                            stageId = loadedWorkspace.stageId,
                            messages = loadedWorkspace.selectableMessages,
                            artifacts = loadedWorkspace.artifacts,
                            state = audioState,
                            onRefresh = {
                                scope.launch {
                                    reloadAudioWorkspace()
                                }
                            },
                            onRequestGeneration = { reference ->
                                controller.requestGeneration(reference)
                                audioState = controller.state
                            },
                            onRequestRetry = { audioAssetId ->
                                controller.requestRetry(audioAssetId)
                                audioState = controller.state
                            },
                            onCancelGeneration = { audioAssetId ->
                                scope.launch {
                                    controller.cancelGeneration(audioAssetId)
                                    audioState = controller.state
                                }
                            },
                            onRequestDelete = { audioAssetId ->
                                controller.requestDelete(audioAssetId)
                                audioState = controller.state
                            },
                            onConfirmPendingAction = {
                                scope.launch {
                                    controller.confirmPendingAction()
                                    audioState = controller.state
                                }
                            },
                            onDismissPendingAction = {
                                controller.dismissPendingAction()
                                audioState = controller.state
                            },
                            onPlay = { asset ->
                                controller.play(asset)
                                audioState = controller.state
                            },
                            onPause = {
                                controller.pause()
                                audioState = controller.state
                            },
                            onResume = {
                                controller.resume()
                                audioState = controller.state
                            },
                            onStop = {
                                controller.stop()
                                audioState = controller.state
                            },
                        )
                    }
                }
            }
        }
    }
}
