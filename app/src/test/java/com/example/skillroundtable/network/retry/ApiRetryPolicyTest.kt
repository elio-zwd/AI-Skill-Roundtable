package com.example.skillroundtable.network.retry

import android.content.Context
import android.content.SharedPreferences
import com.example.skillroundtable.network.ApiKeyPool
import com.example.skillroundtable.network.ApiKeyRecord
import com.example.skillroundtable.network.ApiKeySource
import com.example.skillroundtable.network.ApiKeyValidationState
import com.example.skillroundtable.network.EncryptedApiKeyStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any

class ApiRetryPolicyTest {

    // 简易 SharedPreferences 实现，避开 Matcher 异常
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

    @After
    fun tearDown() {
        val storeField = ApiKeyPool::class.java.getDeclaredField("store")
        storeField.isAccessible = true
        storeField.set(ApiKeyPool, null)

        val contextField = ApiKeyPool::class.java.getDeclaredField("appContext")
        contextField.isAccessible = true
        contextField.set(ApiKeyPool, null)
    }

    @Test
    fun testGetDecisionHttp400() {
        val failure = ApiCallFailure.Http(400)
        val decision = ApiRetryPolicy.getDecision(failure, sameKeyAttemptCount = 0)
        assertEquals("400 应该直接停止请求", ApiRetryDecision.STOP_REQUEST, decision)
    }

    @Test
    fun testGetDecisionHttp401And403() {
        val failure401 = ApiCallFailure.Http(401)
        val decision401 = ApiRetryPolicy.getDecision(failure401, sameKeyAttemptCount = 0)
        assertEquals("401 应该尝试下一个 Key", ApiRetryDecision.TRY_NEXT_KEY, decision401)

        val failure403 = ApiCallFailure.Http(403)
        val decision403 = ApiRetryPolicy.getDecision(failure403, sameKeyAttemptCount = 0)
        assertEquals("403 应该尝试下一个 Key", ApiRetryDecision.TRY_NEXT_KEY, decision403)
    }

    @Test
    fun testGetDecisionHttp429() {
        val failure = ApiCallFailure.Http(429)
        val decision = ApiRetryPolicy.getDecision(failure, sameKeyAttemptCount = 0)
        assertEquals("429 应该触发冷却并尝试下一个 Key", ApiRetryDecision.COOLDOWN_AND_TRY_NEXT_KEY, decision)
    }

    @Test
    fun testGetDecisionHttp500() {
        val failure = ApiCallFailure.Http(500)
        val decision1 = ApiRetryPolicy.getDecision(failure, sameKeyAttemptCount = 0)
        assertEquals("500 在初次尝试时应重试同一个 Key", ApiRetryDecision.RETRY_SAME_KEY, decision1)

        val decision2 = ApiRetryPolicy.getDecision(failure, sameKeyAttemptCount = 2)
        assertEquals("500 在达到最大重试次数后应换 Key", ApiRetryDecision.TRY_NEXT_KEY, decision2)
    }

    @Test
    fun testGetDecisionNetworkError() {
        val failure = ApiCallFailure.Network(Exception("Timeout"))
        val decision1 = ApiRetryPolicy.getDecision(failure, sameKeyAttemptCount = 0)
        assertEquals("网络异常在初次尝试时应重试同一个 Key", ApiRetryDecision.RETRY_SAME_KEY, decision1)

        val decision2 = ApiRetryPolicy.getDecision(failure, sameKeyAttemptCount = 1)
        assertEquals("网络异常达到次数后应换 Key", ApiRetryDecision.TRY_NEXT_KEY, decision2)
    }

    @Test
    fun testHandleKeyStatusUpdateFor401() {
        val context = mock(Context::class.java)
        val fakePrefs = FakeSharedPreferences()
        `when`(context.getSharedPreferences("gemini_api_key_prefs", Context.MODE_PRIVATE)).thenReturn(fakePrefs)

        val mockStore = mock(EncryptedApiKeyStore::class.java)
        val existingRecord = ApiKeyRecord(
            id = "my_key",
            displayName = "K1",
            key = "secret_123",
            fingerprint = "fp_123",
            source = ApiKeySource.LOCAL,
            validationState = ApiKeyValidationState.AVAILABLE
        )
        `when`(mockStore.read()).thenReturn(listOf(existingRecord))
        `when`(mockStore.write(any())).thenReturn(true)

        // 反射装配
        val storeField = ApiKeyPool::class.java.getDeclaredField("store")
        storeField.isAccessible = true
        storeField.set(ApiKeyPool, mockStore)

        val contextField = ApiKeyPool::class.java.getDeclaredField("appContext")
        contextField.isAccessible = true
        contextField.set(ApiKeyPool, context)

        val failure = ApiCallFailure.Http(401)
        ApiRetryPolicy.handleKeyStatusUpdate(context, "my_key", failure)

        // 验证 mockStore 的 write 方法确实被调用，将 my_key 标为 INVALID
        verify(mockStore).write(any())
    }
}
