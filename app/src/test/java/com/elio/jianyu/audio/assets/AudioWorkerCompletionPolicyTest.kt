package com.elio.jianyu.audio.assets

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioWorkerCompletionPolicyTest {
    @Test
    fun availableAndSuppressedCompleteWithoutAutomaticRetry() {
        assertEquals(
            AudioWorkerCompletion.SUCCESS,
            AudioWorkerCompletionPolicy.resolve(
                AudioGenerationExecutionResult.Available("asset-1", "asset.wav"),
            ),
        )
        assertEquals(
            AudioWorkerCompletion.SUCCESS,
            AudioWorkerCompletionPolicy.resolve(
                AudioGenerationExecutionResult.Suppressed(AudioGenerationErrorCode.CANCELED),
            ),
        )
    }

    @Test
    fun businessFailureIsTerminalUntilExplicitUserRetry() {
        assertEquals(
            AudioWorkerCompletion.FAILURE,
            AudioWorkerCompletionPolicy.resolve(
                AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.OFFLINE),
            ),
        )
        assertEquals(
            AudioWorkerCompletion.FAILURE,
            AudioWorkerCompletionPolicy.resolve(
                AudioGenerationExecutionResult.Failure(AudioGenerationErrorCode.RATE_LIMITED),
            ),
        )
    }
}
