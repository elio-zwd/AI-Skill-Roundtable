package com.example.skillroundtable.network

import android.content.Context
import android.util.Base64
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object LiveApiClient {
    private const val TAG = "LiveApiClient"
    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    /**
     * 将一段文本转换成 WAV 语音并存储到本地。
     * @param voiceName 声音名：Puck, Charon, Kore, Fenrir, Aoede 等
     * @return 导出的 WAV 文件绝对路径，若失败则抛出异常
     */
    suspend fun generateTtsWav(
        context: Context,
        apiKey: String,
        text: String,
        voiceName: String,
        outputFile: File
    ): String = suspendCancellableCoroutine { continuation ->
        val url = "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1alpha.GenerativeService.BidiGenerateContent?key=$apiKey"
        val request = Request.Builder().url(url).build()

        val pcmBuffer = ByteArrayOutputStream()
        var webSocket: WebSocket? = null

        val listener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket 连接成功")
                try {
                    // 1. 发送 Setup 配置
                    val setupJson = JSONObject().apply {
                        put("setup", JSONObject().apply {
                            put("model", "models/gemini-3.1-flash-live")
                            put("generationConfig", JSONObject().apply {
                                put("responseModalities", JSONArray(listOf("AUDIO")))
                                put("speechConfig", JSONObject().apply {
                                    put("voiceConfig", JSONObject().apply {
                                        put("prebuiltVoiceConfig", JSONObject().apply {
                                            put("voiceName", voiceName)
                                        })
                                    })
                                })
                            })
                        })
                    }
                    ws.send(setupJson.toString())

                    // 2. 发送合成文本请求
                    val contentJson = JSONObject().apply {
                        put("clientContent", JSONObject().apply {
                            put("turns", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("role", "user")
                                    put("parts", JSONArray().apply {
                                        put(JSONObject().apply {
                                            put("text", "请用你原本的声音，清晰平稳地读出这段文字。直接开始读，不要有任何开场白或解释：$text")
                                        })
                                    })
                                })
                            })
                            put("turnComplete", true)
                        })
                    }
                    ws.send(contentJson.toString())
                } catch (e: Exception) {
                    Log.e(TAG, "发送初始化 JSON 失败", e)
                    ws.close(1001, "Setup fail")
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }

            override fun onMessage(ws: WebSocket, textMessage: String) {
                try {
                    val root = JSONObject(textMessage)
                    val serverContent = root.optJSONObject("serverContent") ?: return
                    val modelTurn = serverContent.optJSONObject("modelTurn")
                    if (modelTurn != null) {
                        val parts = modelTurn.optJSONArray("parts")
                        if (parts != null) {
                            for (i in 0 until parts.length()) {
                                val part = parts.getJSONObject(i)
                                val inlineData = part.optJSONObject("inlineData") ?: continue
                                val mimeType = inlineData.optString("mimeType") ?: ""
                                if (mimeType.startsWith("audio/pcm")) {
                                    val base64Data = inlineData.optString("data") ?: ""
                                    val pcmBytes = Base64.decode(base64Data, Base64.DEFAULT)
                                    pcmBuffer.write(pcmBytes)
                                }
                            }
                        }
                    }

                    // 检查是否结束
                    val turnComplete = serverContent.optBoolean("turnComplete", false)
                    if (turnComplete) {
                        ws.close(1000, "Normal closure")
                        writeWavFile(pcmBuffer.toByteArray(), outputFile)
                        if (continuation.isActive) {
                            continuation.resume(outputFile.absolutePath)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析 WebSocket 消息出错", e)
                    ws.close(1001, "Error parse")
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket 失败: ${t.message}", t)
                if (continuation.isActive) {
                    continuation.resumeWithException(t)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket 已关闭: $code / $reason")
                if (continuation.isActive) {
                    continuation.resumeWithException(Exception("WebSocket 意外关闭，未收到结束标识。"))
                }
            }
        }

        webSocket = client.newWebSocket(request, listener)
        continuation.invokeOnCancellation {
            webSocket?.close(1000, "Canceled")
        }
    }

    private fun writeWavFile(pcmData: ByteArray, outputFile: File) {
        val sampleRate = 24000
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
        Log.d(TAG, "已写入 WAV 文件: ${outputFile.absolutePath}, 大小: ${outputFile.length()} bytes")
    }

    private fun intToBytes(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun shortToBytes(value: Short): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }
}
