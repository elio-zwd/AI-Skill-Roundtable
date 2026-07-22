package com.elio.skillroundtable.network.retry

import android.content.Context
import android.content.SharedPreferences
import com.elio.skillroundtable.network.ApiKeyPool
import com.elio.skillroundtable.network.ApiKeyRecord
import com.elio.skillroundtable.network.ApiKeySource
import com.elio.skillroundtable.network.ApiKeyValidationState
import com.elio.skillroundtable.network.EncryptedApiKeyStore
import com.elio.skillroundtable.network.keys.ApiKeyLease
import com.elio.skillroundtable.network.RetrofitClient
import okhttp3.MediaType.Companion.toMediaType
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.kotlin.any

class ApiRetryPolicyTest {

    // 真正能够读写数据的手写 FakeSharedPreferences，保障测试稳定性
    class FakeSharedPreferences(private val map: MutableMap<String, Any> = mutableMapOf()) : SharedPreferences {
        override fun getAll(): Map<String, *> = map
        override fun getString(key: String, defValue: String?): String? = (map[key] as? String) ?: defValue
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = defValues
        override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

        class FakeEditor(private val map: MutableMap<String, Any>) : SharedPreferences.Editor {
            override fun putString(key: String, value: String?): SharedPreferences.Editor {
                if (value != null) map[key] = value else map.remove(key)
                return this
            }
            override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = this
            override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                map[key] = value
                return this
            }
            override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                map[key] = value
                return this
            }
            override fun putFloat(key: String, value: Float): SharedPreferences.Editor = this
            override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = this
            override fun remove(key: String): SharedPreferences.Editor {
                map.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                map.clear()
                return this
            }
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
        `when`(context.applicationContext).thenReturn(context)

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

        verify(mockStore).write(any())
    }

    @Test
    fun testRetryExecutorUsesOneAndTwoSecondBackoff() = kotlinx.coroutines.runBlocking {
        val context = mock(Context::class.java)
        val fakePrefs = FakeSharedPreferences()
        `when`(context.getSharedPreferences("gemini_api_key_prefs", Context.MODE_PRIVATE)).thenReturn(fakePrefs)
        `when`(context.applicationContext).thenReturn(context)

        val mockStore = mock(EncryptedApiKeyStore::class.java)
        `when`(mockStore.read()).thenReturn(emptyList())

        // 反射装配
        val storeField = ApiKeyPool::class.java.getDeclaredField("store")
        storeField.isAccessible = true
        storeField.set(ApiKeyPool, mockStore)

        val contextField = ApiKeyPool::class.java.getDeclaredField("appContext")
        contextField.isAccessible = true
        contextField.set(ApiKeyPool, context)

        val tracker = com.elio.skillroundtable.roundtable.RequestBudgetTracker(10)
        val attemptPlan = listOf(
            ApiKeyLease("key_a", "K1", "secret_1", ApiKeySource.LOCAL)
        )

        val delays = mutableListOf<Long>()
        val delayProvider = object : com.elio.skillroundtable.roundtable.DelayProvider {
            override suspend fun delay(ms: Long) {
                delays.add(ms)
            }
        }

        var callCount = 0
        var caughtException: Throwable? = null
        try {
            RetrofitClient.executeWithBudgetAndRetry(
                context = context,
                sessionId = 1L,
                attemptPlan = attemptPlan,
                tracker = tracker,
                operationName = "Test5xx",
                delayProvider = delayProvider
            ) {
                callCount++
                if (callCount <= 2) {
                    val response = retrofit2.Response.error<String>(
                        500,
                        okhttp3.ResponseBody.create("application/json".toMediaType(), "Internal Server Error")
                    )
                    throw retrofit2.HttpException(response)
                }
                "success"
            }
        } catch (e: Throwable) {
            caughtException = e
        }

        if (caughtException != null) {
            caughtException.printStackTrace()
            fail("不应当抛出异常，应该重试成功并返回 success。抛出的异常为: $caughtException")
        }

        assertEquals("总调用次数（1次首发+2次重试）", 3, callCount)
        assertEquals("记录了两次重试延迟", 2, delays.size)
        assertEquals("第一次重试退避应为 1000ms", 1000L, delays[0])
        assertEquals("第二次重试退避应为 2000ms", 2000L, delays[1])
    }

    @Test
    fun testRetryAfterIsRespected() {
        val context = mock(Context::class.java)
        // 使用真正能够保存读写数据的手写 FakeSharedPreferences
        val fakePrefs = FakeSharedPreferences()

        `when`(context.getSharedPreferences("gemini_api_key_prefs", Context.MODE_PRIVATE)).thenReturn(fakePrefs)
        `when`(context.applicationContext).thenReturn(context)

        val mockStore = mock(EncryptedApiKeyStore::class.java)
        val existingRecord = ApiKeyRecord(
            id = "key_cooldown",
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

        val failure = ApiCallFailure.Http(429, 15000L)

        ApiRetryPolicy.handleKeyStatusUpdate(context, "key_cooldown", failure)

        // 从真实的 fakePrefs 中进行断言
        val banTime = fakePrefs.getLong("ban_key_cooldown", 0L)
        assertTrue("应当写入了远在未来的冷却截止时间", banTime > System.currentTimeMillis() + 14000L)
    }

    @Test
    fun testParseRetryAfterMs() {
        val parsed15 = ApiRetryPolicy.parseRetryAfterMs("15")
        assertEquals("字符串 15 应被成功解析为 15000L", 15000L, parsed15)

        val parsedNull = ApiRetryPolicy.parseRetryAfterMs(null)
        assertEquals("null 应解析为 null", null, parsedNull)

        val parsedInvalid = ApiRetryPolicy.parseRetryAfterMs("invalid")
        assertEquals("非数字格式应解析为 null", null, parsedInvalid)
    }
}
