package com.example.skillroundtable.network.retry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiExecutionExceptionPrivacyTest {
    @Test
    fun messageUsesStableCategoryWithoutRawCauseText() {
        val secret = "https://example.test/request?key=super-secret"
        val failure = ApiCallFailure.Network(IllegalStateException(secret))
        val exception = ApiExecutionException(
            failure = failure,
            operationName = "GoogleSearch",
            keyId = "key-1",
            cause = IllegalStateException("raw-provider-message $secret")
        )

        assertTrue(exception.message.orEmpty().contains("NETWORK"))
        assertFalse(exception.message.orEmpty().contains("super-secret"))
        assertFalse(exception.message.orEmpty().contains("raw-provider-message"))
    }
}
