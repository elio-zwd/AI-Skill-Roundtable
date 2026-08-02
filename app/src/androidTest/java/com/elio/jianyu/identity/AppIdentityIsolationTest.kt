package com.elio.jianyu.identity

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elio.jianyu.data.RoundtableDatabase
import com.elio.jianyu.network.EncryptedApiKeyStore
import java.io.File
import java.security.KeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIdentityIsolationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun targetPackage_usesJianyuApplicationId() {
        assertEquals("com.elio.jianyu", context.packageName)
    }

    @Test
    fun privateDirectories_areScopedToJianyuSandbox() {
        val expectedPackage = "com.elio.jianyu"
        val legacyPackage = listOf(
            "com",
            "elio",
            "skillroundtable",
        ).joinToString(".")

        listOf(
            context.dataDir,
            context.filesDir,
            context.cacheDir,
            context.noBackupFilesDir,
        ).forEach { directory ->
            val canonicalPath = directory.canonicalPath
            assertTrue(
                "私有目录应属于见域沙箱：$canonicalPath",
                canonicalPath.contains(expectedPackage),
            )
            assertFalse(
                "私有目录不得属于旧包沙箱：$canonicalPath",
                canonicalPath.contains(legacyPackage),
            )
        }
    }

    @Test
    fun freshInstall_hasNoUserSessionsOrApiKeys() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val database = RoundtableDatabase.getDatabase(context, scope)
            assertTrue(database.chatDao().getAllSessions().first().isEmpty())
            assertTrue(EncryptedApiKeyStore(context).read().isEmpty())
            assertFalse(
                File(context.noBackupFilesDir, "gemini_api_keys.enc").exists(),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun legacyNamedKeystoreAlias_isNotVisibleInFreshJianyuSandbox() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        assertFalse(
            "新包首次启动不得看见旧包 UID 下的同名 Key",
            keyStore.containsAlias("skill_roundtable_api_key_v1"),
        )
    }
}
