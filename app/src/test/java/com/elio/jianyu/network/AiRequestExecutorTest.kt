package com.elio.jianyu.network

import com.elio.jianyu.network.keys.ApiKeyLease
import com.elio.jianyu.network.retry.ApiCallFailure
import com.elio.jianyu.network.retry.ApiRetryDecision
import com.elio.jianyu.network.retry.ApiRetryPolicy
import com.elio.jianyu.roundtable.DelayProvider
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiRequestExecutorTest {
    @Test
    fun retryPolicyKeepsFailureClassificationIndependentOfTransport() {
        assertEquals(
            ApiRetryDecision.COOLDOWN_AND_TRY_NEXT_KEY,
            ApiRetryPolicy.getDecision(ApiCallFailure.Http(429), sameKeyAttemptCount = 0),
        )
        assertEquals(
            ApiRetryDecision.RETRY_SAME_KEY,
            ApiRetryPolicy.getDecision(ApiCallFailure.Network(IOException()), sameKeyAttemptCount = 0),
        )
        assertEquals(3_000L, ApiRetryPolicy.parseRetryAfterMs("3"))
    }

    @Test
    fun retriesSameKeyAndRecordsEachPhysicalAttempt() = runBlocking {
        val reporter = FakeReporter()
        val delays = mutableListOf<Long>()
        val executor = AiRequestExecutor(AiProvider.DEEPSEEK, reporter)
        var attempts = 0

        val result = executor.execute(
            sessionId = 7L,
            attemptPlan = listOf(lease("key-a")),
            operationName = "test",
            delayProvider = object : DelayProvider {
                override suspend fun delay(ms: Long) {
                    delays += ms
                }
            },
            onAttemptStarted = { attempts++ },
        ) {
            if (attempts == 1) throw IOException("temporary")
            "ok"
        }

        assertEquals("ok", result)
        assertEquals(2, attempts)
        assertEquals(listOf(1_000L), delays)
        assertEquals(listOf("key-a"), reporter.successful)
        assertTrue(reporter.failures.single().second is ApiCallFailure.Network)
    }

    @Test
    fun doesNotWrapCancellation() = runBlocking {
        val executor = AiRequestExecutor(AiProvider.GEMINI, FakeReporter())

        try {
            executor.execute(
                sessionId = 1L,
                attemptPlan = listOf(lease("key-a", AiProvider.GEMINI)),
                operationName = "cancel",
            ) {
                throw CancellationException("stop")
            }
        } catch (error: CancellationException) {
            assertEquals("stop", error.message)
            return@runBlocking
        }
        throw AssertionError("CancellationException was not propagated")
    }

    private fun lease(id: String, provider: AiProvider = AiProvider.DEEPSEEK) = ApiKeyLease(
        keyId = id,
        displayName = id,
        provider = provider,
        source = ApiKeySource.LOCAL,
    )

    private class FakeReporter : ProviderKeyAttemptReporter {
        val successful = mutableListOf<String>()
        val failures = mutableListOf<Pair<String, ApiCallFailure>>()

        override fun secretFor(keyId: String): String = "secret-$keyId"

        override fun recordSuccess(sessionId: Long, keyId: String) {
            successful += keyId
        }

        override fun recordFailure(keyId: String, failure: ApiCallFailure) {
            failures += keyId to failure
        }
    }
}
