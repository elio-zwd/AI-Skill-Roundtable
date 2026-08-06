package com.elio.jianyu.network.audio

import com.elio.jianyu.audio.assets.AudioGenerationConfig
import com.elio.jianyu.audio.assets.AudioGenerationGatewayErrorCode
import com.elio.jianyu.audio.assets.AudioGenerationGatewayResult
import com.elio.jianyu.audio.assets.AudioGenerationOutput
import com.elio.jianyu.audio.assets.AudioGenerationRequest
import com.elio.jianyu.audio.assets.AudioTargetFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ByokAudioGenerationGatewayTest {
    @Test
    fun noAvailableKeyFailsBeforeTransport() = kotlinx.coroutines.runBlocking {
        val keys = FakeKeyProvider(emptyList())
        var calls = 0
        val gateway = ByokAudioGenerationGateway(
            keyProvider = keys,
            transport = GeminiAudioTransport { _, _, _ ->
                calls += 1
                GeminiAudioTransportResult.Success(byteArrayOf(1, 2))
            },
        )

        val result = gateway.generate(request(), RecordingOutput())

        assertEquals(
            AudioGenerationGatewayResult.Failure(AudioGenerationGatewayErrorCode.AUTH_UNAVAILABLE),
            result,
        )
        assertEquals(0, calls)
    }

    @Test
    fun authenticationFailureRotatesToNextKeyAndWritesValidWav() = kotlinx.coroutines.runBlocking {
        val keys = FakeKeyProvider(
            listOf(
                AudioByokKeyLease("key-a", "secret-a"),
                AudioByokKeyLease("key-b", "secret-b"),
            ),
        )
        val attemptedSecrets = mutableListOf<String>()
        val gateway = ByokAudioGenerationGateway(
            keyProvider = keys,
            transport = GeminiAudioTransport { secret, _, voice ->
                attemptedSecrets += secret
                assertEquals("Aoede", voice)
                if (secret == "secret-a") {
                    GeminiAudioTransportResult.Failure(AudioGenerationGatewayErrorCode.AUTH_UNAVAILABLE)
                } else {
                    GeminiAudioTransportResult.Success(byteArrayOf(1, 0, 2, 0))
                }
            },
        )
        val output = RecordingOutput()

        val result = gateway.generate(request(), output)

        assertEquals(AudioGenerationGatewayResult.Success, result)
        assertEquals(listOf("secret-a", "secret-b"), attemptedSecrets)
        assertEquals(listOf("key-a"), keys.invalid)
        assertEquals(listOf("key-b"), keys.successful)
        assertEquals("RIFF", output.bytes.copyOfRange(0, 4).decodeToString())
        assertEquals("WAVE", output.bytes.copyOfRange(8, 12).decodeToString())
        assertEquals(48, output.bytes.size)
    }

    @Test
    fun unsupportedAacDoesNotConsumeKey() = kotlinx.coroutines.runBlocking {
        val keys = FakeKeyProvider(listOf(AudioByokKeyLease("key-a", "secret-a")))
        var calls = 0
        val gateway = ByokAudioGenerationGateway(
            keyProvider = keys,
            transport = GeminiAudioTransport { _, _, _ ->
                calls += 1
                GeminiAudioTransportResult.Success(byteArrayOf(1, 2))
            },
        )
        val unsupported = request().copy(
            config = request().config.copy(targetFormat = AudioTargetFormat.AAC_ADTS),
        )

        val result = gateway.generate(unsupported, RecordingOutput())

        assertEquals(
            AudioGenerationGatewayResult.Failure(AudioGenerationGatewayErrorCode.INVALID_RESPONSE),
            result,
        )
        assertEquals(0, calls)
        assertTrue(keys.successful.isEmpty())
    }

    private fun request() = AudioGenerationRequest(
        content = "需要朗读的正式内容",
        config = AudioGenerationConfig(
            voiceProfileId = "jianyu-default",
            targetFormat = AudioTargetFormat.WAV,
            parameterVersion = 1,
        ),
    )

    private class RecordingOutput : AudioGenerationOutput {
        private val buffer = java.io.ByteArrayOutputStream()
        val bytes: ByteArray get() = buffer.toByteArray()

        override fun write(bytes: ByteArray) {
            buffer.write(bytes)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            buffer.write(bytes, offset, length)
        }
    }

    private class FakeKeyProvider(
        private val leases: List<AudioByokKeyLease>,
    ) : AudioByokKeyProvider {
        val successful = mutableListOf<String>()
        val invalid = mutableListOf<String>()
        val limited = mutableListOf<String>()

        override fun attemptPlan(): List<AudioByokKeyLease> = leases
        override fun reportSuccess(keyId: String) { successful += keyId }
        override fun reportAuthenticationFailure(keyId: String) { invalid += keyId }
        override fun reportRateLimited(keyId: String) { limited += keyId }
    }
}
