package com.elio.jianyu.execution

import com.elio.jianyu.data.RepositoryError
import com.elio.jianyu.network.retry.ApiCallFailure
import com.elio.jianyu.network.retry.ApiExecutionException
import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionErrorMapperTest {
    @Test
    fun rateLimitIsRetryableAndDoesNotExposeProviderBody() {
        val failure = ExecutionErrorMapper.fromThrowable(
            ApiExecutionException(
                failure = ApiCallFailure.Http(429, 1_000),
                operationName = "answer",
                keyId = "key-internal-id",
                cause = IOException("provider-body-with-secret"),
            ),
        )

        assertEquals(ExecutionErrorCode.RATE_LIMITED, failure.code)
        assertTrue(failure.retryable)
        assertFalse(failure.safeMessage.contains("provider-body"))
        assertFalse(failure.safeMessage.contains("key-internal-id"))
    }

    @Test
    fun authenticationFailureIsMappedSeparately() {
        val failure = ExecutionErrorMapper.fromThrowable(
            ApiExecutionException(
                failure = ApiCallFailure.Http(401),
                operationName = "answer",
                keyId = null,
                cause = IOException("unauthorized"),
            ),
        )

        assertEquals(ExecutionErrorCode.AUTHENTICATION_FAILED, failure.code)
    }

    @Test
    fun timeoutIsNotReportedAsEmptyResponse() {
        val failure = ExecutionErrorMapper.fromThrowable(SocketTimeoutException("late"))

        assertEquals(ExecutionErrorCode.TIMEOUT, failure.code)
    }

    @Test
    fun offlineIsStableAndRetryable() {
        val failure = ExecutionErrorMapper.fromThrowable(IOException("network unavailable"))

        assertEquals(ExecutionErrorCode.OFFLINE, failure.code)
        assertTrue(failure.retryable)
    }

    @Test
    fun repositoryClosedDatabaseKeepsStorageFailureSemantics() {
        val failure = ExecutionErrorMapper.fromRepositoryError(
            RepositoryError.StorageFailure("execution", retryable = true),
        )

        assertEquals(ExecutionErrorCode.STORAGE_FAILURE, failure.code)
        assertTrue(failure.retryable)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNeverConvertedToOrdinaryFailure() {
        ExecutionErrorMapper.fromThrowable(CancellationException("cancel"))
    }
}
