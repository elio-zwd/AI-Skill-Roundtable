package com.example.skillroundtable.telemetry

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
    fun enabledPreservesOnlyExplicitStoreAndRoleLocalPreviousId() {
        val result = CloudInteractionRequestPolicy.apply(true, true, "interaction-123")
        assertTrue(result.store)
        assertTrue(result.previousInteractionId == "interaction-123")
    }
}
