package com.example.skillroundtable.network

import com.example.skillroundtable.data.Character
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

    @Test
    fun `多组复用同一密钥时不会丢失角色`() {
        val characters = (1..20).map { index ->
            Character(
                id = "character-$index",
                name = "角色$index",
                avatar = "",
                tagline = "",
                systemPrompt = "",
                order = index
            )
        }
        val keys = listOf(
            ApiKeyInfo("key-1", "value-1", "K1"),
            ApiKeyInfo("key-2", "value-2", "K2")
        )

        val grouped = ApiKeyPool.assignRandomGroups(characters, keys)
        val groupedIds = grouped.values.flatten().map { it.id }

        assertEquals(characters.map { it.id }.sorted(), groupedIds.sorted())
        assertEquals(characters.size, groupedIds.distinct().size)
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
