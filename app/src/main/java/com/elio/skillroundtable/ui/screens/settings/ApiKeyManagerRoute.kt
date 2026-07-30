package com.elio.skillroundtable.ui.screens.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.elio.skillroundtable.network.ApiKeyPool
import kotlinx.coroutines.launch

@Composable
fun ApiKeyManagerRoute(
    currentSessionId: Long?,
    onBack: () -> Unit,
    onOpenTelemetry: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val summaries by ApiKeyPool.summaries.collectAsState()
    val storageError by ApiKeyPool.storageError.collectAsState()
    var input by remember { mutableStateOf("") }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<ApiKeyConfirmation?>(null) }

    LaunchedEffect(Unit) {
        ApiKeyPool.init(context)
    }

    val currentKeyAccount = remember(currentSessionId, summaries) {
        currentSessionId?.let { ApiKeyPool.getOrBindSessionKey(context, it) }?.account
    }

    ApiKeyManagerScreen(
        uiState = ApiKeyManagerUiState(
            summaries = summaries,
            storageError = storageError,
            currentKeyAccount = currentKeyAccount,
            input = input,
            resultMessage = resultMessage,
            confirmation = confirmation,
        ),
        onBack = onBack,
        onOpenTelemetry = onOpenTelemetry,
        onInputChange = { input = it },
        onImport = {
            val result = ApiKeyPool.importBatch(context, input)
            resultMessage = batchImportSummary(result)
            if (result.added > 0) {
                input = ""
                scope.launch {
                    ApiKeyPool.validateKeys(context, result.importedIds)
                }
            }
        },
        onToggle = { summary, disabled ->
            ApiKeyPool.setKeyDisabled(context, summary.id, disabled)
        },
        onValidate = { summary ->
            scope.launch {
                ApiKeyPool.validateKey(context, summary.id)
            }
        },
        onRequestDelete = { summary ->
            confirmation = ApiKeyConfirmation.Delete(summary)
        },
        onRequestClearAll = {
            confirmation = ApiKeyConfirmation.ClearAll
        },
        onDismissConfirmation = {
            confirmation = null
        },
        onConfirmDelete = { summary ->
            ApiKeyPool.deleteKey(context, summary.id)
            confirmation = null
        },
        onConfirmClearAll = {
            val success = ApiKeyPool.clearAllKeys(context)
            confirmation = null
            Toast.makeText(
                context,
                if (success) "已清空 API Key" else "清空失败",
                Toast.LENGTH_SHORT,
            ).show()
        },
    )
}
