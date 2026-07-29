package com.elio.skillroundtable.network

import com.elio.skillroundtable.audio.AudioSynthesisErrorCode
import com.elio.skillroundtable.audio.AudioSynthesisState
import com.elio.skillroundtable.audio.AudioSynthesisStatusStore
import com.elio.skillroundtable.audio.pcmBytesToDurationMs
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveApiClientTest {
    @After
    fun tearDown() {
        AudioSynthesisStatusStore.resetForTests()
    }

    @Test
    fun websocketEndpointUsesCurrentV1BetaService() {
        assertTrue(LIVE_TTS_WEBSOCKET_ENDPOINT.startsWith("wss://"))
        assertTrue(LIVE_TTS_WEBSOCKET_ENDPOINT.contains(".v1beta."))
        assertFalse(LIVE_TTS_WEBSOCKET_ENDPOINT.contains(".v1alpha."))
    }

    @Test
    fun setupPayloadContainsOnlySetupEnvelopeAndSelectedVoice() {
        val root = JSONObject(buildTtsSetupPayload("Aoede"))

        assertEquals(setOf("setup"), root.keySet())
        val setup = root.getJSONObject("setup")
        assertEquals("models/$LIVE_TTS_MODEL", setup.getString("model"))

        val generationConfig = setup.getJSONObject("generationConfig")
        assertEquals("AUDIO", generationConfig.getJSONArray("responseModalities").getString(0))
        val voiceName = generationConfig
            .getJSONObject("speechConfig")
            .getJSONObject("voiceConfig")
            .getJSONObject("prebuiltVoiceConfig")
            .getString("voiceName")
        assertEquals("Aoede", voiceName)
        assertTrue(setup.has("systemInstruction"))
        assertFalse(root.has("clientContent"))
        assertFalse(root.has("realtimeInput"))
    }

    @Test
    fun textPayloadUsesRealtimeInputInsteadOfClientContent() {
        val root = JSONObject(buildTtsRealtimeInputPayload("需要朗读的内容"))

        assertEquals(setOf("realtimeInput"), root.keySet())
        assertTrue(
            root.getJSONObject("realtimeInput")
                .getString("text")
                .contains("需要朗读的内容")
        )
        assertFalse(root.has("clientContent"))
    }

    @Test
    fun extractsMessageIdOnlyFromExpectedTtsFileName() {
        assertEquals(42L, extractTtsMessageId(File("/tmp/tts_42.wav")))
        assertEquals(42L, extractTtsMessageId(File("/tmp/tts_42.wav.part")))
        assertNull(extractTtsMessageId(File("/tmp/audio_42.wav")))
        assertNull(extractTtsMessageId(File("/tmp/tts_invalid.wav")))
    }

    @Test
    fun convertsPcmBytesToGeneratedDuration() {
        assertEquals(0L, pcmBytesToDurationMs(0L))
        assertEquals(500L, pcmBytesToDurationMs(24_000L))
        assertEquals(1_000L, pcmBytesToDurationMs(48_000L))
    }

    @Test
    fun classifiesAuthenticationAndRateLimitFailures() {
        val auth = classifyTtsFailure(httpStatus = 403, error = null)
        val rateLimited = classifyTtsFailure(httpStatus = 429, error = null)

        assertEquals(AudioSynthesisErrorCode.AUTH_FAILED, auth.code)
        assertFalse(auth.retryable)
        assertEquals(AudioSynthesisErrorCode.RATE_LIMITED, rateLimited.code)
        assertTrue(rateLimited.retryable)
    }

    @Test
    fun classifiesTimeoutAndNetworkFailuresWithoutExposingRawMessage() {
        val timeout = classifyTtsFailure(
            httpStatus = null,
            error = SocketTimeoutException("request URL contains sensitive query")
        )
        val network = classifyTtsFailure(
            httpStatus = null,
            error = IOException("request URL contains sensitive query")
        )

        assertEquals(AudioSynthesisErrorCode.TIMEOUT, timeout.code)
        assertEquals("语音合成等待超时", timeout.displayMessage)
        assertEquals(AudioSynthesisErrorCode.NETWORK_ERROR, network.code)
        assertFalse(network.displayMessage.contains("sensitive"))
    }

    @Test
    fun statusStoreKeepsIndependentStatePerMessage() {
        AudioSynthesisStatusStore.update(1L, AudioSynthesisState.Connecting)
        AudioSynthesisStatusStore.update(
            2L,
            AudioSynthesisState.Generating(receivedBytes = 48_000L)
        )

        assertTrue(AudioSynthesisStatusStore.stateFor(1L) is AudioSynthesisState.Connecting)
        val second = AudioSynthesisStatusStore.stateFor(2L)
        assertTrue(second is AudioSynthesisState.Generating)
        assertEquals(1_000L, (second as AudioSynthesisState.Generating).generatedDurationMs)
    }
}
