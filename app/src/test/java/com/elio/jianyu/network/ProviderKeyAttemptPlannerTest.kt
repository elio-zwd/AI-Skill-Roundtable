package com.elio.jianyu.network

import com.elio.jianyu.network.keys.ApiKeyLease
import org.junit.Assert.assertEquals
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

    private fun lease(id: String) = ApiKeyLease(
        keyId = id,
        displayName = id,
        provider = AiProvider.DEEPSEEK,
        source = ApiKeySource.LOCAL,
    )
}
