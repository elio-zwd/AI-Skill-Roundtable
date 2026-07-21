package com.example.skillroundtable.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ApiKeyProviderTest {
    @Test
    fun `本地来源在重复指纹时优先`() {
        val local = record("local", "same-key", ApiKeySource.LOCAL)
        val remoteDuplicate = record("remote-duplicate", "same-key", ApiKeySource.REMOTE)
        val remoteUnique = record("remote-unique", "another-key", ApiKeySource.REMOTE)
        val provider = CompositeApiKeyProvider(
            localProvider = ApiKeyProvider { listOf(local) },
            remoteProvider = ApiKeyProvider { listOf(remoteDuplicate, remoteUnique) }
        )

        val records = provider.getRecords()

        assertEquals(2, records.size)
        assertSame(local, records.first())
        assertEquals("remote-unique", records.last().id)
    }

    private fun record(id: String, key: String, source: ApiKeySource): ApiKeyRecord {
        return ApiKeyRecord(
            id = id,
            displayName = id,
            key = key,
            fingerprint = fingerprintApiKey(key),
            source = source
        )
    }
}
