package com.elio.jianyu.network

import com.elio.jianyu.network.keys.ApiKeyLease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderKeyAttemptPlannerTest {
    @Test
    fun preferredThenSessionBoundThenOthersAndLastUsed() {
        val plan = ProviderKeyAttemptPlanner.create(
            available = listOf(lease("c"), lease("a"), lease("b")),
            sessionBoundKeyId = "b",
            lastUsedKeyId = "a",
            preferredKeyId = "c",
        )

        assertEquals(listOf("c", "b", "a"), plan.map(ApiKeyLease::keyId))
    }

    @Test
    fun removesDuplicateLeases() {
        val plan = ProviderKeyAttemptPlanner.create(
            available = listOf(lease("a"), lease("a"), lease("b")),
            sessionBoundKeyId = "a",
            lastUsedKeyId = null,
        )

        assertEquals(listOf("a", "b"), plan.map(ApiKeyLease::keyId))
    }

    @Test
    fun parallelParticipantPositionsCycleAcrossAvailableKeys() {
        val basePlan = listOf(lease("bound"), lease("k2"), lease("k3"))

        assertEquals("bound", ProviderKeyAttemptPlanner.preferredKeyIdForPosition(basePlan, 0))
        assertEquals("k2", ProviderKeyAttemptPlanner.preferredKeyIdForPosition(basePlan, 1))
        assertEquals("k3", ProviderKeyAttemptPlanner.preferredKeyIdForPosition(basePlan, 2))
        assertEquals("bound", ProviderKeyAttemptPlanner.preferredKeyIdForPosition(basePlan, 3))
    }

    @Test
    fun emptyPlanHasNoParticipantPreference() {
        assertNull(ProviderKeyAttemptPlanner.preferredKeyIdForPosition(emptyList(), 5))
    }

    private fun lease(id: String) = ApiKeyLease(
        keyId = id,
        displayName = id,
        provider = AiProvider.DEEPSEEK,
        source = ApiKeySource.LOCAL,
    )
}
