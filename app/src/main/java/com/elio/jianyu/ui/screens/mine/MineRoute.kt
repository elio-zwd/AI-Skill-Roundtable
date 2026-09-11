package com.elio.jianyu.ui.screens.mine

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.elio.jianyu.data.JianyuRepository
import com.elio.jianyu.data.PersonalContextFilter
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.network.AiManager
import com.elio.jianyu.network.AiUseCase
import com.elio.jianyu.network.ApiKeyValidationState
import com.elio.jianyu.telemetry.TelemetryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MineRoute(
    repository: JianyuRepository,
    onOpenSettings: () -> Unit,
    onOpenAiManagement: () -> Unit,
    onOpenTelemetry: () -> Unit,
) {
    val context = LocalContext.current
    val configurationRepository = remember(context) { AiManager.configuration(context) }
    val configuration by configurationRepository.configuration.collectAsState()
    val selectedModel = configuration.modelFor(AiUseCase.ROUNDTABLE_ANSWER)
    val keyRepository = remember(context, selectedModel.provider) {
        AiManager.keys(context, selectedModel.provider)
    }
    val keySummaries by keyRepository.summaries.collectAsState()
    val telemetryLevel by TelemetryRepository.level.collectAsState()

    var personalContextCount by remember { mutableStateOf<Int?>(null) }
    var personalContextLoadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(repository) {
        val result = withContext(Dispatchers.IO) {
            repository.listPersonalContexts(PersonalContextFilter())
        }
        when (result) {
            is RepositoryResult.Success -> {
                personalContextCount = result.value.size
                personalContextLoadFailed = false
            }
            is RepositoryResult.Failure -> {
                personalContextCount = null
                personalContextLoadFailed = true
            }
        }
    }

    MineScreen(
        uiState = MineUiState(
            personalContextCount = personalContextCount,
            personalContextLoadFailed = personalContextLoadFailed,
            modelDisplayName = selectedModel.displayName,
            availableKeyCount = keySummaries.count { summary ->
                summary.enabled &&
                    summary.validationState != ApiKeyValidationState.INVALID &&
                    summary.remainingBanTimeMs <= 0L
            },
            telemetryLevel = telemetryLevel,
        ),
        onOpenSettings = onOpenSettings,
        onOpenAiManagement = onOpenAiManagement,
        onOpenTelemetry = onOpenTelemetry,
    )
}
