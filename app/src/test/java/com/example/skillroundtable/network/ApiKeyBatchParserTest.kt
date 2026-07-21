package com.example.skillroundtable.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ApiKeyBatchParserTest {
    @Test
    fun `兼容方括号 中英文逗号和换行`() {
        val result = ApiKeyBatchParser.parse("[\"key-a\", key-b，'key-c'\nkey-d]")

        assertEquals(listOf("key-a", "key-b", "key-c", "key-d"), result.keys)
        assertEquals(0, result.duplicates)
        assertEquals(0, result.invalid)
    }

    @Test
    fun `批次内重复项只保留一次`() {
        val result = ApiKeyBatchParser.parse("key-a,key-a，key-b\nkey-b")

        assertEquals(listOf("key-a", "key-b"), result.keys)
        assertEquals(2, result.duplicates)
    }

    @Test
    fun `包含内部空白的密钥会被拒绝`() {
        val result = ApiKeyBatchParser.parse("key-a, bad key, key-b")

        assertEquals(listOf("key-a", "key-b"), result.keys)
        assertEquals(1, result.invalid)
    }

    @Test
    fun `指纹稳定且不会暴露原文`() {
        val first = fingerprintApiKey("secret-value")
        val second = fingerprintApiKey("secret-value")

        assertEquals(first, second)
        assertNotEquals("secret-value", first)
        assertEquals(64, first.length)
    }
}
