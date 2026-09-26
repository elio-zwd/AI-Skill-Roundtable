package com.elio.jianyu.network

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 仅本地传入 geminiKey 时调用真实 Gemini；CI 和普通设备测试不持有密钥。 */
@RunWith(AndroidJUnit4::class)
class GeminiEmbeddingRuntimeSmokeTest {
    @Test
    fun runtimeQueryUsesByokPoolAndReturns768Dimensions() = runBlocking {
        val key = InstrumentationRegistry.getArguments().getString("geminiKey")
        assumeTrue("本地未传入 Gemini Key", !key.isNullOrBlank())
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repository = AiManager.keys(context, AiProvider.GEMINI)
        val imported = repository.importBatch(key!!)
        try {
            var attemptCount = 0
            val vector = GeminiEmbeddingTransport.embedQuery(
                context = context,
                sessionId = 20260926L,
                query = "怎样用简单语言解释复杂概念？",
                onAttemptStarted = { attemptCount++ },
            )
            assertEquals(768, vector.size)
            assertTrue(attemptCount > 0)
        } finally {
            imported.importedIds.forEach(repository::delete)
        }
    }
}
