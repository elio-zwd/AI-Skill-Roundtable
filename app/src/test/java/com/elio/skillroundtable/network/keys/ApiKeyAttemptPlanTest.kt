package com.elio.skillroundtable.network.keys

import android.content.Context
import android.content.SharedPreferences
import com.elio.skillroundtable.network.ApiKeyPool
import com.elio.skillroundtable.network.ApiKeyRecord
import com.elio.skillroundtable.network.ApiKeySource
import com.elio.skillroundtable.network.ApiKeyValidationState
import com.elio.skillroundtable.network.CompositeApiKeyProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class ApiKeyAttemptPlanTest {

    // 手写简易 SharedPreference，100% 避开 Mockito Matcher 滥用风险
    class FakeSharedPreferences(private val map: Map<String, Any> = emptyMap()) : SharedPreferences {
        override fun getAll(): Map<String, *> = map
        override fun getString(key: String, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = defValues
        override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor = this
            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String, value: Int): SharedPreferences.Editor = this
            override fun putLong(key: String, value: Long): SharedPreferences.Editor = this
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String): SharedPreferences.Editor = this
            override fun clear(): SharedPreferences.Editor = this
            override fun commit(): Boolean = true
            override fun apply() {}
        }
    }

    private fun setupMockKeys(records: List<ApiKeyRecord>, context: Context) {
        val mockProvider = mock(CompositeApiKeyProvider::class.java)
        `when`(mockProvider.getRecords()).thenReturn(records)

        val providerField = ApiKeyPool::class.java.getDeclaredField("provider")
        providerField.isAccessible = true
        providerField.set(ApiKeyPool, mockProvider)

        val contextField = ApiKeyPool::class.java.getDeclaredField("appContext")
        contextField.isAccessible = true
        contextField.set(ApiKeyPool, context)
    }

    @After
    fun tearDown() {
        val providerField = ApiKeyPool::class.java.getDeclaredField("provider")
        providerField.isAccessible = true
        providerField.set(ApiKeyPool, null)

        val contextField = ApiKeyPool::class.java.getDeclaredField("appContext")
        contextField.isAccessible = true
        contextField.set(ApiKeyPool, null)
    }

    @Test
    fun testCreateAttemptPlanDeterminedOrdering() {
        val context = mock(Context::class.java)
        val fakePrefs = FakeSharedPreferences()
        `when`(context.getSharedPreferences("gemini_api_key_prefs", Context.MODE_PRIVATE)).thenReturn(fakePrefs)

        val recordsList = listOf(
            ApiKeyRecord(id = "key_c", displayName = "Acc C", key = "secret_c", fingerprint = "fp_c", source = ApiKeySource.LOCAL, validationState = ApiKeyValidationState.AVAILABLE),
            ApiKeyRecord(id = "key_a", displayName = "Acc A", key = "secret_a", fingerprint = "fp_a", source = ApiKeySource.LOCAL, validationState = ApiKeyValidationState.AVAILABLE),
            ApiKeyRecord(id = "key_b", displayName = "Acc B", key = "secret_b", fingerprint = "fp_b", source = ApiKeySource.LOCAL, validationState = ApiKeyValidationState.AVAILABLE)
        )

        setupMockKeys(recordsList, context)

        val plan = ApiKeyScheduler.createAttemptPlan(context, sessionId = 100)

        assertEquals("计划里应包含全部 3 个 Key 租约", 3, plan.size)
        assertEquals("第一个尝试的应该是 key_a", "key_a", plan[0].keyId)
        assertEquals("第二个尝试的应该是 key_b", "key_b", plan[1].keyId)
        assertEquals("第三个尝试的应该是 key_c", "key_c", plan[2].keyId)
    }

    @Test
    fun testCreateAttemptPlanWithSessionBinding() {
        val context = mock(Context::class.java)
        // 绑定 session_key_100 到 key_b
        val fakePrefs = FakeSharedPreferences(mapOf("session_key_100" to "key_b"))
        `when`(context.getSharedPreferences("gemini_api_key_prefs", Context.MODE_PRIVATE)).thenReturn(fakePrefs)

        val recordsList = listOf(
            ApiKeyRecord(id = "key_c", displayName = "Acc C", key = "secret_c", fingerprint = "fp_c", source = ApiKeySource.LOCAL, validationState = ApiKeyValidationState.AVAILABLE),
            ApiKeyRecord(id = "key_a", displayName = "Acc A", key = "secret_a", fingerprint = "fp_a", source = ApiKeySource.LOCAL, validationState = ApiKeyValidationState.AVAILABLE),
            ApiKeyRecord(id = "key_b", displayName = "Acc B", key = "secret_b", fingerprint = "fp_b", source = ApiKeySource.LOCAL, validationState = ApiKeyValidationState.AVAILABLE)
        )

        setupMockKeys(recordsList, context)

        val plan = ApiKeyScheduler.createAttemptPlan(context, sessionId = 100)

        assertEquals("计划包含 3 个 Key", 3, plan.size)
        assertEquals("第一个首选尝试的应当是绑定的 key_b", "key_b", plan[0].keyId)
        assertEquals("第二个应当是 key_a", "key_a", plan[1].keyId)
        assertEquals("第三个应当是 key_c", "key_c", plan[2].keyId)
    }

    @Test
    fun testKeyPlanPreferredBoundLastUsedPriority() {
        val context = mock(Context::class.java)
        // 绑定 session_key_100 到 key_b，Last Used Key 设为 key_a
        val fakePrefs = FakeSharedPreferences(mapOf(
            "session_key_100" to "key_b",
            "last_used_key_id" to "key_a"
        ))
        `when`(context.getSharedPreferences("gemini_api_key_prefs", Context.MODE_PRIVATE)).thenReturn(fakePrefs)

        val recordsList = listOf(
            ApiKeyRecord(id = "key_c", displayName = "Acc C", key = "secret_c", fingerprint = "fp_c", source = ApiKeySource.LOCAL, validationState = ApiKeyValidationState.AVAILABLE),
            ApiKeyRecord(id = "key_a", displayName = "Acc A", key = "secret_a", fingerprint = "fp_a", source = ApiKeySource.LOCAL, validationState = ApiKeyValidationState.AVAILABLE),
            ApiKeyRecord(id = "key_b", displayName = "Acc B", key = "secret_b", fingerprint = "fp_b", source = ApiKeySource.LOCAL, validationState = ApiKeyValidationState.AVAILABLE)
        )

        setupMockKeys(recordsList, context)

        // 显式将 preferredKeyId 传为 key_c
        val plan = ApiKeyScheduler.createAttemptPlan(context, sessionId = 100, preferredKeyId = "key_c")

        // 预期排序：Preferred (key_c) -> Bound (key_b) -> 其他，但 Last Used (key_a) 必须被移到末尾！
        assertEquals("Preferred key_c 应该排第一", "key_c", plan[0].keyId)
        assertEquals("Bound key_b 应该排第二", "key_b", plan[1].keyId)
        assertEquals("Last Used key_a 应该被轮转到末尾", "key_a", plan[2].keyId)
    }

    @Test
    fun testKeyPlanFiltersDisabledInvalidAndCoolingKeys() {
        val context = mock(Context::class.java)
        // 模拟 key_cooling 处于冷却时间（截止时间在未来）
        val futureTime = System.currentTimeMillis() + 100000L
        val fakePrefs = FakeSharedPreferences(mapOf(
            "ban_key_cooling" to futureTime
        ))
        `when`(context.getSharedPreferences("gemini_api_key_prefs", Context.MODE_PRIVATE)).thenReturn(fakePrefs)

        val recordsList = listOf(
            // 正常
            ApiKeyRecord(id = "key_normal", displayName = "Normal", key = "secret_normal", fingerprint = "fp_n", source = ApiKeySource.LOCAL, validationState = ApiKeyValidationState.AVAILABLE),
            // 无效（INVALID）
            ApiKeyRecord(id = "key_invalid", displayName = "Invalid", key = "secret_invalid", fingerprint = "fp_i", source = ApiKeySource.LOCAL, validationState = ApiKeyValidationState.INVALID),
            // 被禁用（DISABLED）—— 将 record 设为 enabled = false
            ApiKeyRecord(id = "key_disabled", displayName = "Disabled", key = "secret_disabled", fingerprint = "fp_d", source = ApiKeySource.LOCAL, validationState = ApiKeyValidationState.AVAILABLE, enabled = false),
            // 冷却中（COOLING）
            ApiKeyRecord(id = "key_cooling", displayName = "Cooling", key = "secret_cooling", fingerprint = "fp_c", source = ApiKeySource.LOCAL, validationState = ApiKeyValidationState.AVAILABLE)
        )

        setupMockKeys(recordsList, context)

        val plan = ApiKeyScheduler.createAttemptPlan(context, sessionId = 100)

        assertEquals("计划里应该只包含唯一正常的 key_normal", 1, plan.size)
        assertEquals("过滤后的 Key 应该是 key_normal", "key_normal", plan[0].keyId)
    }
}
