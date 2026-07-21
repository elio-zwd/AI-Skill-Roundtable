package com.elio.skillroundtable.telemetry

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InteractionChainStoreTest {
    @After
    fun tearDown() {
        InteractionChainStore.clearAll()
    }

    @Test
    fun isolatesChainsBySessionAndCharacter() {
        InteractionChainStore.put(1L, "role_a", "interaction-a")
        InteractionChainStore.put(1L, "role_b", "interaction-b")
        InteractionChainStore.put(2L, "role_a", "interaction-c")

        assertEquals("interaction-a", InteractionChainStore.get(1L, "role_a"))
        assertEquals("interaction-b", InteractionChainStore.get(1L, "role_b"))
        assertEquals("interaction-c", InteractionChainStore.get(2L, "role_a"))
        assertEquals(3, InteractionChainStore.size())
    }

    @Test
    fun clearingSessionDoesNotRemoveOtherSessions() {
        InteractionChainStore.put(1L, "role_a", "interaction-a")
        InteractionChainStore.put(2L, "role_a", "interaction-b")

        InteractionChainStore.clearSession(1L)

        assertNull(InteractionChainStore.get(1L, "role_a"))
        assertEquals("interaction-b", InteractionChainStore.get(2L, "role_a"))
    }

    @Test
    fun blankInteractionIdRemovesExistingCursor() {
        InteractionChainStore.put(1L, "role_a", "interaction-a")
        InteractionChainStore.put(1L, "role_a", "")

        assertNull(InteractionChainStore.get(1L, "role_a"))
    }
}