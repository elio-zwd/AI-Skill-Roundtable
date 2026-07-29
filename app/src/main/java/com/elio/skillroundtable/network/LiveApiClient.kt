package com.elio.skillroundtable.network

import android.content.Context
import android.util.Base64
import com.elio.skillroundtable.audio.AudioSynthesisErrorCode
import com.elio.skillroundtable.audio.AudioSynthesisState
import com.elio.skillroundtable.audio.AudioSynthesisStatusStore
import com.elio.skillroundtable.audio.TtsSynthesisException
import com.elio.skillroundtable.audio.pcmBytesToDurationMs
import com.elio.skillroundtable.telemetry.PrivacySafeLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal const val LIVE_TTS_WEBSOCKET_ENDPOINT =
    "wss://generativelanguage.googleapis.com/ws/" +
        "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
internal const val LIVE_TTS_MODEL = "gemini-3.1-flash-live-preview"

private const val MIN_PCM_BYTES = 2
private const val NORMAL_CLOSE_CODE = 1000
private const val CLIENT_ERROR_CLOSE_CODE = 1001

internal data class TtsFailureDescriptor(
    val code: AudioSynthesisErrorCode,
    val displayMessage: String,
    val retryable: Boolean
)

internal fun buildTtsSetupPayload(voiceName: String): String {
    return JSONObject().apply {
        put("setup", JSONObject().apply {
            put("model", "models/$LIVE_TTS_MODEL")
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().apply {
                            put("voiceName", voiceName)
                        })
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", "你是语音朗读助手。只朗读用户提供的文字，不添加开场白、解释或总结。")
                }))
            })
        })
    }.toString()
}

internal fun buildTtsRealtimeInputPayload(text: String): String {
    return JSONObject().apply {
        put("realtimeInput", JSONObject().apply {
            put("text", "请清晰、平稳、自然地朗读以下文字：\n$text")
        })
    }.toString()
}

internal fun extractTtsMessageId(outputFile: File): Long? {
    return Regex("^tts_(\\d+)\\.wav(?:\\.part)?$")
        .matchEntire(outputFile.name)
        ?.groupValues
        ?.getOrNull(1)
        ?.toLongOrNull()
}

internal fun classifyTtsFailure(
    httpStatus: Int?,
    error: Throwable?
): TtsFailureDescriptor {
    return when {
        httpStatus == 401 || httpStatus == 403 -> TtsFailureDescriptor(
            code = AudioSynthesisErrorCode.AUTH_FAILED,
            displayMessage = "API Key 无法使用语音服务",
            retryable = false
        )
        httpStatus == 429 -> TtsFailureDescriptor(
            code = AudioSynthesisErrorCode.RATE_LIMITED,
            displayMessage = "语音请求过于频繁，请稍后重试",
            retryable = true
        )
        httpStatus != null && httpStatus in 500..599 -> TtsFailureDescriptor(
            code = AudioSynthesisErrorCode.MODEL_UNAVAILABLE,
            displayMessage = "语音服务暂时不可用，请稍后重试",
            retryable = true
        )
        httpStatus == 400 || httpStatus == 404 -> TtsFailureDescriptor(
            code = AudioSynthesisErrorCode.PROTOCOL_ERROR,
            displayMessage = "语音服务配置不兼容",
            retryable = false
        )
        error is SocketTimeoutException -> TtsFailureDescriptor(
            code = AudioSynthesisErrorCode.TIMEOUT,
            displayMessage = "语音合成等待超时",
            retryable = true
        )
        error is IOException -> TtsFailureDescriptor(
            code = AudioSynthesisErrorCode.NETWORK_ERROR,
            displayMessage = "网络连接中断，请检查网络后重试",
            retryable = true
        )
        else -> TtsFailureDescriptor(
            code = AudioSynthesisErrorCode.UNKNOWN,
            displayMessage = "语音合成失败，请稍后重试",
            retryable = true
        )
    }
}

object LiveApiClient {
    private const val TAG = "LiveApiClient"

    private val client = OkHttpClient.Builder()
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val activeMessageIds = ConcurrentHashMap.newKeySet<Long>()

    /**
     * 将一段文本转换成 WAV 语音并存储到本地。
     *
     * 协议顺序严格遵循 Live API：setup -> setupComplete -> realtimeInput.text。
     * 音频进度按 24kHz、单声道、16-bit PCM 的已接收字节数换算为真实生成时长。
     */
    suspend fun generateTtsWav(
        context: Context,
        apiKey: String,
        text: String,
        voiceName: String,
        outputFile: File,
        messageId: Long? = extractTtsMessageId(outputFile)
    ): String {
        if (messageId != null && !activeMessageIds.add(messageId)) {
            throw TtsSynthesisException(
                code = AudioSynthesisErrorCode.ALREADY_RUNNING,
                safeDisplayMessage = "这条语音正在合成中",
                retryable = false
            )
        }

        AudioSynthesisStatusStore.update(messageId, AudioSynthesisState.Connecting)
        val tempParent = outputFile.parentFile ?: context.cacheDir
        tempParent.mkdirs()
        val tempFile = File(tempParent, "${outputFile.name}.part")
        runCatching { tempFile.delete() }

        return suspendCancellableCoroutine { continuation ->
            val pcmBuffer = ByteArrayOutputStream()
            val terminal = AtomicBoolean(false)
            val realtimeInputSent = AtomicBoolean(false)
            var webSocket: WebSocket? = null

            fun releaseActiveMessage() {
                if (messageId != null) activeMessageIds.remove(messageId)
            }

            fun finishFailure(
                descriptor: TtsFailureDescriptor,
                cause: Throwable? = null,
                closeSocket: Boolean = true
            ) {
                if (!terminal.compareAndSet(false, true)) return
                runCatching { tempFile.delete() }
                releaseActiveMessage()
                AudioSynthesisStatusStore.update(
                    messageId,
                    AudioSynthesisState.Failed(
                        code = descriptor.code,
                        displayMessage = descriptor.displayMessage,
                        retryable = descriptor.retryable
                    )
                )
                PrivacySafeLogger.e(TAG, "TTS synthesis failed (${descriptor.code})", cause)
                if (continuation.isActive) {
                    continuation.resumeWithException(
                        TtsSynthesisException(
                            code = descriptor.code,
                            safeDisplayMessage = descriptor.displayMessage,
                            retryable = descriptor.retryable,
                            cause = cause
                        )
                    )
                }
                if (closeSocket) {
                    webSocket?.close(CLIENT_ERROR_CLOSE_CODE, "Client failure")
                }
            }

            fun finishSuccess(ws: WebSocket) {
                if (!terminal.compareAndSet(false, true)) return
                AudioSynthesisStatusStore.update(messageId, AudioSynthesisState.Finalizing)
                try {
                    val pcmData = pcmBuffer.toByteArray()
                    if (pcmData.size < MIN_PCM_BYTES) {
                        throw TtsSynthesisException(
                            code = AudioSynthesisErrorCode.EMPTY_AUDIO,
                            safeDisplayMessage = "语音服务未返回有效音频",
                            retryable = true
                        )
                    }

                    writeWavFile(pcmData, tempFile)
                    commitTempFile(tempFile, outputFile)
                    if (!outputFile.exists() || outputFile.length() <= 44L) {
                        throw TtsSynthesisException(
                            code = AudioSynthesisErrorCode.FILE_IO_ERROR,
                            safeDisplayMessage = "音频文件保存失败",
                            retryable = true
                        )
                    }

                    val generatedDurationMs = pcmBytesToDurationMs(pcmData.size.toLong())
                    AudioSynthesisStatusStore.update(
                        messageId,
                        AudioSynthesisState.Ready(
                            audioSizeBytes = outputFile.length(),
                            generatedDurationMs = generatedDurationMs
                        )
                    )
                    releaseActiveMessage()
                    PrivacySafeLogger.d(TAG, "TTS synthesis completed")
                    if (continuation.isActive) continuation.resume(outputFile.absolutePath)
                    ws.close(NORMAL_CLOSE_CODE, "Normal closure")
                } catch (error: Throwable) {
                    runCatching { tempFile.delete() }
                    runCatching { outputFile.delete() }
                    releaseActiveMessage()
                    val exception = error as? TtsSynthesisException
                    val descriptor = if (exception != null) {
                        TtsFailureDescriptor(
                            code = exception.code,
                            displayMessage = exception.safeDisplayMessage,
                            retryable = exception.retryable
                        )
                    } else {
                        TtsFailureDescriptor(
                            code = AudioSynthesisErrorCode.FILE_IO_ERROR,
                            displayMessage = "音频文件保存失败",
                            retryable = true
                        )
                    }
                    AudioSynthesisStatusStore.update(
                        messageId,
                        AudioSynthesisState.Failed(
                            code = descriptor.code,
                            displayMessage = descriptor.displayMessage,
                            retryable = descriptor.retryable
                        )
                    )
                    PrivacySafeLogger.e(TAG, "TTS finalization failed (${descriptor.code})", error)
                    if (continuation.isActive) {
                        continuation.resumeWithException(
                            TtsSynthesisException(
                                code = descriptor.code,
                                safeDisplayMessage = descriptor.displayMessage,
                                retryable = descriptor.retryable,
                                cause = error
                            )
                        )
                    }
                    ws.close(CLIENT_ERROR_CLOSE_CODE, "Finalization failure")
                }
            }

            val listener = object : WebSocketListener() {
                override fun onOpen(ws: WebSocket, response: Response) {
                    PrivacySafeLogger.d(TAG, "TTS WebSocket connected")
                    AudioSynthesisStatusStore.update(messageId, AudioSynthesisState.Configuring)
                    if (!ws.send(buildTtsSetupPayload(voiceName))) {
                        finishFailure(
                            TtsFailureDescriptor(
                                code = AudioSynthesisErrorCode.PROTOCOL_ERROR,
                                displayMessage = "语音服务初始化失败",
                                retryable = true
                            )
                        )
                    }
                }

                override fun onMessage(ws: WebSocket, textMessage: String) {
                    try {
                        val root = JSONObject(textMessage)
                        val apiError = root.optJSONObject("error")
                        if (apiError != null) {
                            val status = apiError.optInt("code", -1).takeIf { it > 0 }
                            finishFailure(classifyTtsFailure(status, null))
                            return
                        }

                        if (root.has("setupComplete")) {
                            if (realtimeInputSent.compareAndSet(false, true)) {
                                if (!ws.send(buildTtsRealtimeInputPayload(text))) {
                                    finishFailure(
                                        TtsFailureDescriptor(
                                            code = AudioSynthesisErrorCode.PROTOCOL_ERROR,
                                            displayMessage = "语音文本发送失败",
                                            retryable = true
                                        )
                                    )
                                    return
                                }
                                AudioSynthesisStatusStore.update(
                                    messageId,
                                    AudioSynthesisState.Generating(receivedBytes = 0L)
                                )
                            }
                            return
                        }

                        val serverContent = root.optJSONObject("serverContent") ?: return
                        val modelTurn = serverContent.optJSONObject("modelTurn")
                        val parts = modelTurn?.optJSONArray("parts")
                        if (parts != null) {
                            for (index in 0 until parts.length()) {
                                val part = parts.optJSONObject(index) ?: continue
                                val inlineData = part.optJSONObject("inlineData") ?: continue
                                val mimeType = inlineData.optString("mimeType")
                                if (!mimeType.startsWith("audio/pcm")) continue
                                val base64Data = inlineData.optString("data")
                                if (base64Data.isBlank()) continue
                                val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                if (pcmBytes.isEmpty()) continue
                                pcmBuffer.write(pcmBytes)
                                AudioSynthesisStatusStore.update(
                                    messageId,
                                    AudioSynthesisState.Generating(
                                        receivedBytes = pcmBuffer.size().toLong()
                                    )
                                )
                            }
                        }

                        if (serverContent.optBoolean("interrupted", false)) {
                            finishFailure(
                                TtsFailureDescriptor(
                                    code = AudioSynthesisErrorCode.PROTOCOL_ERROR,
                                    displayMessage = "语音生成被服务端中断",
                                    retryable = true
                                )
                            )
                            return
                        }

                        val generationComplete =
                            serverContent.optBoolean("generationComplete", false)
                        val turnComplete = serverContent.optBoolean("turnComplete", false)
                        if (generationComplete || turnComplete) {
                            finishSuccess(ws)
                        }
                    } catch (error: Throwable) {
                        finishFailure(
                            TtsFailureDescriptor(
                                code = AudioSynthesisErrorCode.PROTOCOL_ERROR,
                                displayMessage = "语音服务响应解析失败",
                                retryable = true
                            ),
                            cause = error
                        )
                    }
                }

                override fun onFailure(ws: WebSocket, error: Throwable, response: Response?) {
                    finishFailure(
                        descriptor = classifyTtsFailure(response?.code, error),
                        cause = error,
                        closeSocket = false
                    )
                }

                override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                    ws.close(NORMAL_CLOSE_CODE, null)
                }

                override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                    PrivacySafeLogger.d(TAG, "TTS WebSocket closed (code=$code)")
                    finishFailure(
                        TtsFailureDescriptor(
                            code = AudioSynthesisErrorCode.PROTOCOL_ERROR,
                            displayMessage = "语音连接提前关闭，未收到完整音频",
                            retryable = true
                        ),
                        closeSocket = false
                    )
                }
            }

            val request = Request.Builder()
                .url("$LIVE_TTS_WEBSOCKET_ENDPOINT?key=$apiKey")
                .build()
            webSocket = client.newWebSocket(request, listener)

            continuation.invokeOnCancellation {
                if (terminal.compareAndSet(false, true)) {
                    runCatching { tempFile.delete() }
                    releaseActiveMessage()
                    AudioSynthesisStatusStore.update(
                        messageId,
                        AudioSynthesisState.Failed(
                            code = AudioSynthesisErrorCode.CANCELLED,
                            displayMessage = "语音合成已取消",
                            retryable = true
                        )
                    )
                    webSocket?.cancel()
                }
            }
        }
    }

    private fun commitTempFile(tempFile: File, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        if (outputFile.exists() && !outputFile.delete()) {
            throw IOException("Unable to replace existing audio output")
        }
        if (!tempFile.renameTo(outputFile)) {
            tempFile.copyTo(outputFile, overwrite = true)
            if (!tempFile.delete()) {
                PrivacySafeLogger.w(TAG, "Temporary TTS file cleanup deferred")
            }
        }
    }

    private fun writeWavFile(pcmData: ByteArray, outputFile: File) {
        val sampleRate = 24_000
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val totalSize = 36 + dataSize

        FileOutputStream(outputFile).use { out ->
            out.write("RIFF".toByteArray())
            out.write(intToBytes(totalSize))
            out.write("WAVE".toByteArray())

            out.write("fmt ".toByteArray())
            out.write(intToBytes(16))
            out.write(shortToBytes(1))
            out.write(shortToBytes(channels.toShort()))
            out.write(intToBytes(sampleRate))
            out.write(intToBytes(byteRate))
            out.write(shortToBytes(blockAlign.toShort()))
            out.write(shortToBytes(bitsPerSample.toShort()))

            out.write("data".toByteArray())
            out.write(intToBytes(dataSize))
            out.write(pcmData)
        }
    }

    private fun intToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun shortToBytes(value: Short): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }
}
