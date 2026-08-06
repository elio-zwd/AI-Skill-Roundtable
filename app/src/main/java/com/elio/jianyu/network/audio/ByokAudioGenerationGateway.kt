package com.elio.jianyu.network.audio

import android.content.Context
import android.util.Base64
import com.elio.jianyu.audio.assets.AudioGenerationGateway
import com.elio.jianyu.audio.assets.AudioGenerationGatewayErrorCode
import com.elio.jianyu.audio.assets.AudioGenerationGatewayResult
import com.elio.jianyu.audio.assets.AudioGenerationOutput
import com.elio.jianyu.audio.assets.AudioGenerationRequest
import com.elio.jianyu.audio.assets.AudioTargetFormat
import com.elio.jianyu.network.ApiKeyPool
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/** 只在网络边界短暂持有的 BYOK Key 租约。 */
data class AudioByokKeyLease(
    val keyId: String,
    internal val secret: String,
)

interface AudioByokKeyProvider {
    fun attemptPlan(): List<AudioByokKeyLease>
    fun reportSuccess(keyId: String)
    fun reportAuthenticationFailure(keyId: String)
    fun reportRateLimited(keyId: String)
}

sealed interface GeminiAudioTransportResult {
    data class Success(val pcmBytes: ByteArray) : GeminiAudioTransportResult
    data class Failure(val errorCode: AudioGenerationGatewayErrorCode) : GeminiAudioTransportResult
}

fun interface GeminiAudioTransport {
    suspend fun synthesize(
        apiKey: String,
        content: String,
        voiceName: String,
    ): GeminiAudioTransportResult
}

/**
 * 通过既有 BYOK Key Pool 调用 Gemini Live 音频协议。
 *
 * Gateway 仅输出最终 WAV 字节，不持久化 Key、正文或网络临时路径。
 */
class ByokAudioGenerationGateway(
    private val keyProvider: AudioByokKeyProvider,
    private val transport: GeminiAudioTransport,
) : AudioGenerationGateway {
    constructor(context: Context) : this(
        keyProvider = ApiKeyPoolAudioByokKeyProvider(context.applicationContext),
        transport = OkHttpGeminiAudioTransport(),
    )

    override suspend fun generate(
        request: AudioGenerationRequest,
        output: AudioGenerationOutput,
    ): AudioGenerationGatewayResult {
        if (request.content.isBlank() || request.config.parameterVersion != PARAMETER_VERSION) {
            return AudioGenerationGatewayResult.Failure(
                AudioGenerationGatewayErrorCode.INVALID_RESPONSE,
            )
        }
        if (request.config.targetFormat != AudioTargetFormat.WAV) {
            return AudioGenerationGatewayResult.Failure(
                AudioGenerationGatewayErrorCode.INVALID_RESPONSE,
            )
        }
        val voiceName = when (request.config.voiceProfileId) {
            VOICE_PROFILE_ID -> DEFAULT_GEMINI_VOICE
            else -> return AudioGenerationGatewayResult.Failure(
                AudioGenerationGatewayErrorCode.INVALID_RESPONSE,
            )
        }
        val attempts = keyProvider.attemptPlan()
        if (attempts.isEmpty()) {
            return AudioGenerationGatewayResult.Failure(
                AudioGenerationGatewayErrorCode.AUTH_UNAVAILABLE,
            )
        }

        var finalKeyFailure = AudioGenerationGatewayErrorCode.AUTH_UNAVAILABLE
        attempts.forEach { lease ->
            when (val result = transport.synthesize(lease.secret, request.content, voiceName)) {
                is GeminiAudioTransportResult.Success -> {
                    if (result.pcmBytes.size < 2 || result.pcmBytes.size % 2 != 0) {
                        return AudioGenerationGatewayResult.Failure(
                            AudioGenerationGatewayErrorCode.EMPTY_RESPONSE,
                        )
                    }
                    GeminiAudioWavEncoder.write(result.pcmBytes, output)
                    keyProvider.reportSuccess(lease.keyId)
                    return AudioGenerationGatewayResult.Success
                }
                is GeminiAudioTransportResult.Failure -> when (result.errorCode) {
                    AudioGenerationGatewayErrorCode.AUTH_UNAVAILABLE -> {
                        finalKeyFailure = result.errorCode
                        keyProvider.reportAuthenticationFailure(lease.keyId)
                    }
                    AudioGenerationGatewayErrorCode.RATE_LIMITED -> {
                        finalKeyFailure = result.errorCode
                        keyProvider.reportRateLimited(lease.keyId)
                    }
                    else -> return AudioGenerationGatewayResult.Failure(result.errorCode)
                }
            }
        }
        return AudioGenerationGatewayResult.Failure(finalKeyFailure)
    }

    private companion object {
        const val VOICE_PROFILE_ID = "jianyu-default"
        const val PARAMETER_VERSION = 1
        const val DEFAULT_GEMINI_VOICE = "Aoede"
    }
}

object GeminiAudioWavEncoder {
    private const val SAMPLE_RATE = 24_000
    private const val CHANNELS: Short = 1
    private const val BITS_PER_SAMPLE: Short = 16

    fun write(pcmBytes: ByteArray, output: AudioGenerationOutput) {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(36 + pcmBytes.size)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1)
            putShort(CHANNELS)
            putInt(SAMPLE_RATE)
            putInt(SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8)
            putShort((CHANNELS * BITS_PER_SAMPLE / 8).toShort())
            putShort(BITS_PER_SAMPLE)
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(pcmBytes.size)
        }.array()
        output.write(header)
        output.write(pcmBytes)
    }
}

private class ApiKeyPoolAudioByokKeyProvider(
    private val context: Context,
) : AudioByokKeyProvider {
    override fun attemptPlan(): List<AudioByokKeyLease> {
        return ApiKeyPool.getKeyAttemptOrder(context).map { info ->
            AudioByokKeyLease(info.id, info.key)
        }
    }

    override fun reportSuccess(keyId: String) {
        ApiKeyPool.setLastUsedKeyId(context, keyId)
    }

    override fun reportAuthenticationFailure(keyId: String) {
        ApiKeyPool.markKeyInvalid(context, keyId, "语音服务鉴权失败")
    }

    override fun reportRateLimited(keyId: String) {
        ApiKeyPool.banKey(context, keyId)
    }
}

private class OkHttpGeminiAudioTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
) : GeminiAudioTransport {
    override suspend fun synthesize(
        apiKey: String,
        content: String,
        voiceName: String,
    ): GeminiAudioTransportResult = suspendCancellableCoroutine { continuation ->
        val pcm = ByteArrayOutputStream()
        val terminal = AtomicBoolean(false)
        val inputSent = AtomicBoolean(false)
        var socket: WebSocket? = null

        fun finish(result: GeminiAudioTransportResult, closeSocket: Boolean = true) {
            if (!terminal.compareAndSet(false, true)) return
            if (continuation.isActive) continuation.resume(result)
            if (closeSocket) socket?.close(NORMAL_CLOSE_CODE, "complete")
        }

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (!webSocket.send(setupPayload(voiceName))) {
                    finish(GeminiAudioTransportResult.Failure(
                        AudioGenerationGatewayErrorCode.INVALID_RESPONSE,
                    ))
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val root = JSONObject(text)
                    root.optJSONObject("error")?.let { apiError ->
                        finish(GeminiAudioTransportResult.Failure(
                            mapHttpStatus(apiError.optInt("code", -1)),
                        ))
                        return
                    }
                    if (root.has("setupComplete")) {
                        if (inputSent.compareAndSet(false, true) &&
                            !webSocket.send(inputPayload(content))
                        ) {
                            finish(GeminiAudioTransportResult.Failure(
                                AudioGenerationGatewayErrorCode.INVALID_RESPONSE,
                            ))
                        }
                        return
                    }
                    val serverContent = root.optJSONObject("serverContent") ?: return
                    val parts = serverContent.optJSONObject("modelTurn")
                        ?.optJSONArray("parts")
                    if (parts != null) {
                        for (index in 0 until parts.length()) {
                            val inline = parts.optJSONObject(index)
                                ?.optJSONObject("inlineData") ?: continue
                            if (!inline.optString("mimeType").startsWith("audio/pcm")) continue
                            val encoded = inline.optString("data")
                            if (encoded.isBlank()) continue
                            val bytes = Base64.decode(encoded, Base64.DEFAULT)
                            if (pcm.size() + bytes.size > MAX_PCM_BYTES) {
                                finish(GeminiAudioTransportResult.Failure(
                                    AudioGenerationGatewayErrorCode.INVALID_RESPONSE,
                                ))
                                return
                            }
                            pcm.write(bytes)
                        }
                    }
                    if (serverContent.optBoolean("interrupted", false)) {
                        finish(GeminiAudioTransportResult.Failure(
                            AudioGenerationGatewayErrorCode.INVALID_RESPONSE,
                        ))
                        return
                    }
                    if (serverContent.optBoolean("generationComplete", false) ||
                        serverContent.optBoolean("turnComplete", false)
                    ) {
                        val bytes = pcm.toByteArray()
                        finish(
                            if (bytes.isEmpty()) {
                                GeminiAudioTransportResult.Failure(
                                    AudioGenerationGatewayErrorCode.EMPTY_RESPONSE,
                                )
                            } else {
                                GeminiAudioTransportResult.Success(bytes)
                            },
                        )
                    }
                } catch (_: Throwable) {
                    finish(GeminiAudioTransportResult.Failure(
                        AudioGenerationGatewayErrorCode.INVALID_RESPONSE,
                    ))
                }
            }

            override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                finish(
                    GeminiAudioTransportResult.Failure(
                        when {
                            response != null -> mapHttpStatus(response.code)
                            error is SocketTimeoutException -> AudioGenerationGatewayErrorCode.TIMEOUT
                            error is IOException -> AudioGenerationGatewayErrorCode.OFFLINE
                            else -> AudioGenerationGatewayErrorCode.UNKNOWN
                        },
                    ),
                    closeSocket = false,
                )
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                finish(
                    GeminiAudioTransportResult.Failure(
                        AudioGenerationGatewayErrorCode.INVALID_RESPONSE,
                    ),
                    closeSocket = false,
                )
            }
        }

        socket = client.newWebSocket(
            Request.Builder().url("$ENDPOINT?key=$apiKey").build(),
            listener,
        )
        continuation.invokeOnCancellation {
            if (terminal.compareAndSet(false, true)) socket?.cancel()
        }
    }

    private fun setupPayload(voiceName: String): String = JSONObject().apply {
        put("setup", JSONObject().apply {
            put("model", "models/$MODEL")
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().put("AUDIO"))
                put("speechConfig", JSONObject().apply {
                    put("voiceConfig", JSONObject().apply {
                        put("prebuiltVoiceConfig", JSONObject().put("voiceName", voiceName))
                    })
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().put(
                    "text",
                    "你是语音朗读助手。只朗读用户提供的文字，不添加开场白、解释或总结。",
                )))
            })
        })
    }.toString()

    private fun inputPayload(content: String): String = JSONObject().apply {
        put("realtimeInput", JSONObject().put(
            "text",
            "请清晰、平稳、自然地朗读以下文字：\n$content",
        ))
    }.toString()

    private fun mapHttpStatus(status: Int): AudioGenerationGatewayErrorCode = when (status) {
        401, 403 -> AudioGenerationGatewayErrorCode.AUTH_UNAVAILABLE
        429 -> AudioGenerationGatewayErrorCode.RATE_LIMITED
        in 500..599 -> AudioGenerationGatewayErrorCode.OFFLINE
        else -> AudioGenerationGatewayErrorCode.INVALID_RESPONSE
    }

    private companion object {
        const val ENDPOINT = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
        const val MODEL = "gemini-3.1-flash-live-preview"
        const val NORMAL_CLOSE_CODE = 1000
        const val MAX_PCM_BYTES = 32 * 1024 * 1024
    }
}
