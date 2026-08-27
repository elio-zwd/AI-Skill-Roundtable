package com.elio.jianyu.ui.screens.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.elio.jianyu.network.AiManager
import com.elio.jianyu.network.AiProvider
import com.elio.jianyu.network.ApiKeySummary
import kotlinx.coroutines.launch

@Composable
fun AiManagementRoute(
    currentSessionId: Long?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val configurationRepository = remember(context) { AiManager.configuration(context) }
    val configuration by configurationRepository.configuration.collectAsState()
    var keyProviderName by rememberSaveable { mutableStateOf(AiProvider.GEMINI.name) }
    val keyProvider = AiProvider.valueOf(keyProviderName)
    val keyRepository = remember(keyProvider) {
        AiManager.keys(context, keyProvider)
    }
    val summaries by keyRepository.summaries.collectAsState()
    val storageError by keyRepository.storageError.collectAsState()
    var input by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<AiManagementConfirmation?>(null) }

    LaunchedEffect(keyProvider) {
        input = ""
        resultMessage = null
        confirmation = null
    }

    val currentKeyAccount = remember(keyProvider, currentSessionId, summaries) {
        currentSessionId?.let(keyRepository::getOrBindSessionKey)?.account
    }

    AiManagementScreen(
        uiState = AiManagementUiState(
            configuration = configuration,
            keyProvider = keyProvider,
            summaries = summaries,
            storageError = storageError,
            currentKeyAccount = currentKeyAccount,
            input = input,
            resultMessage = resultMessage,
            confirmation = confirmation,
        ),
        onBack = onBack,
        onSelectProvider = configurationRepository::selectProvider,
        onSelectModel = configurationRepository::selectModel,
        onSelectKeyProvider = { provider -> keyProviderName = provider.name },
        onInputChange = { input = it },
        onImport = {
            val result = keyRepository.importBatch(input)
            resultMessage = aiBatchImportSummary(result)
            input = ""
            if (result.importedIds.isNotEmpty()) {
                scope.launch { AiManager.validateKeys(context, keyProvider, result.importedIds) }
            }
        },
        onToggleKey = { summary, enabled -> keyRepository.setDisabled(summary.id, !enabled) },
        onValidateKey = { summary ->
            scope.launch { AiManager.validateKey(context, keyProvider, summary.id) }
        },
        onRequestDeleteKey = { summary -> confirmation = AiManagementConfirmation.Delete(summary) },
        onRequestClearProviderKeys = { confirmation = AiManagementConfirmation.ClearProviderKeys },
        onDismissConfirmation = { confirmation = null },
        onConfirmDeleteKey = { summary: ApiKeySummary ->
            val success = keyRepository.delete(summary.id)
            Toast.makeText(context, if (success) "已删除 API Key" else "删除失败", Toast.LENGTH_SHORT).show()
            confirmation = null
        },
        onConfirmClearProviderKeys = {
            val success = keyRepository.clear()
            Toast.makeText(context, if (success) "已清空 ${keyProvider.displayName} Key" else "清空失败", Toast.LENGTH_SHORT).show()
            confirmation = null
        },
    )
}
