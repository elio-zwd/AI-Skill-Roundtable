package com.example.skillroundtable.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudInteractionRequestPolicyTest {
    @Test
    fun disabledAlwaysForcesStoreFalseAndDropsPreviousId() {
        val result = CloudInteractionRequestPolicy.apply(false, true, "interaction-123")
        assertFalse(result.store)
        assertNull(result.previousInteractionId)
    }

    @Test
    fun enabledPreservesExplicitStoreAndPreviousId() {
        val result = CloudInteractionRequestPolicy.apply(true, true, "interaction-123")
        assertTrue(result.store)
        assertEquals("interaction-123", result.previousInteractionId)
    }

    @Test
    fun enabledStillDropsPreviousIdWhenRequestIsNotStored() {
        val result = CloudInteractionRequestPolicy.apply(true, false, "interaction-123")
        assertFalse(result.store)
        assertNull(result.previousInteractionId)
    }
}
