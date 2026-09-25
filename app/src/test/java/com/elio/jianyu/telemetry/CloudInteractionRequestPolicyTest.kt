package com.elio.jianyu.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudInteractionRequestPolicyTest {
    @Test
    fun storedRequestPreservesPreviousInteractionId() {
        val result = CloudInteractionRequestPolicy.apply(true, "interaction-123")
        assertTrue(result.store)
        assertEquals("interaction-123", result.previousInteractionId)
    }

    @Test
    fun nonStoredRequestDropsPreviousInteractionId() {
        val result = CloudInteractionRequestPolicy.apply(false, "interaction-123")
        assertFalse(result.store)
        assertNull(result.previousInteractionId)
    }
}
