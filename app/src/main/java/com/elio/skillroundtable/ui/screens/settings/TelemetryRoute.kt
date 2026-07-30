package com.elio.skillroundtable.ui.screens.settings

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.elio.skillroundtable.network.ApiKeyPool
import com.elio.skillroundtable.telemetry.CloudInteractionSettings
import com.elio.skillroundtable.telemetry.TelemetryLevel
import com.elio.skillroundtable.telemetry.TelemetryRepository

/**
 * 保留 PR07-B 冻结的页面入口签名，由 Route 负责状态采集和业务事件连接。
 */
@Composable
fun ApiTelemetryScreen(
    currentSessionId: Long?,
    onBack: () -> Unit,
) {
    TelemetryRoute(
        currentSessionId = currentSessionId,
        onBack = onBack,
    )
}

@Composable
fun TelemetryRoute(
    currentSessionId: Long?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ApiKeyPool.init(context)
        TelemetryRepository.init(context)
        CloudInteractionSettings.init(context)
    }

    val events by TelemetryRepository.events.collectAsState()
    val level by TelemetryRepository.level.collectAsState()
    val storageError by TelemetryRepository.storageError.collectAsState()
    val cloudInteractionEnabled by CloudInteractionSettings.enabled.collectAsState()
    var expandedEventId by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<TelemetryConfirmation?>(null) }

    val keyStatuses = remember(currentSessionId) {
        ApiKeyPool.getKeyStatuses(context)
    }
    val currentKeyInfo = remember(currentSessionId) {
        currentSessionId?.let { ApiKeyPool.getOrBindSessionKey(context, it) }
    }
    val expiresAt = TelemetryRepository.contentDebugExpiresAt(context)
    val remainingMinutes = expiresAt?.let {
        ((it - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L)
    }

    TelemetryScreen(
        uiState = TelemetryUiState(
            events = events,
            level = level,
            storageError = storageError,
            cloudInteractionEnabled = cloudInteractionEnabled,
            expandedEventId = expandedEventId,
            confirmation = confirmation,
            remainingContentDebugMinutes = remainingMinutes,
            estimatedBytes = TelemetryRepository.estimatedBytes(),
            currentKeyId = currentKeyInfo?.id,
            currentKeyAccount = currentKeyInfo?.account,
            availableKeyCount = telemetryAvailableKeyCount(keyStatuses),
            totalKeyCount = keyStatuses.size,
        ),
        onBack = onBack,
        onSelectLevel = { selectedLevel ->
            TelemetryRepository.setLevel(context, selectedLevel)
        },
        onToggleContentDebug = {
            if (level == TelemetryLevel.CONTENT_DEBUG) {
                TelemetryRepository.disableContentDebugAndPurgePreviews(context)
            } else {
                confirmation = TelemetryConfirmation.ContentDebug
            }
        },
        onClearTelemetry = {
            val success = TelemetryRepository.clearAllTelemetry(context)
            Toast.makeText(
                context,
                if (success) "遥测已清空" else "遥测清空失败",
                Toast.LENGTH_SHORT,
            ).show()
        },
        onDisableContentDebugAndPurge = {
            TelemetryRepository.disableContentDebugAndPurgePreviews(context)
        },
        onCloudInteractionChange = { enabled ->
            if (enabled) {
                confirmation = TelemetryConfirmation.CloudInteraction
            } else {
                CloudInteractionSettings.setEnabled(context, false)
            }
        },
        onToggleEvent = { event ->
            if (event.containsContentPreview) {
                expandedEventId = if (expandedEventId == event.id) null else event.id
            }
        },
        onDismissConfirmation = {
            confirmation = null
        },
        onConfirmContentDebug = {
            val success = TelemetryRepository.enableContentDebug(context)
            Toast.makeText(
                context,
                if (success) {
                    "正文调试已开启，24 小时后自动过期"
                } else {
                    "当前构建不允许开启"
                },
                Toast.LENGTH_SHORT,
            ).show()
            confirmation = null
        },
        onConfirmCloudInteraction = {
            CloudInteractionSettings.setEnabled(context, true)
            confirmation = null
        },
    )
}
