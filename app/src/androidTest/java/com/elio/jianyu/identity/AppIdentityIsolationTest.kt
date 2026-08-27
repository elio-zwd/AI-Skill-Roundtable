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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppIdentityIsolationTest {

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val expectedPackage = "com.elio.jianyu"
    private val legacyPackage = listOf(
        "com",
        "elio",
        "skillroundtable",
    ).joinToString(".")

    @Test
    fun targetPackageAndLauncher_useJianyuIdentity() {
        assertEquals(expectedPackage, context.packageName)
        assertEquals(expectedPackage, context.applicationContext.packageName)

        val launchIntent = context.packageManager.getLaunchIntentForPackage(expectedPackage)
        assertNotNull("见域必须具有可解析的 Launcher Activity", launchIntent)
        assertEquals(
            "$expectedPackage.MainActivity",
            launchIntent?.component?.className,
        )
    }

    @Test
    fun privateDirectoriesAndDatabase_areScopedToJianyuSandbox() {
        val privatePaths = listOf(
            context.dataDir,
            context.filesDir,
            context.cacheDir,
            context.noBackupFilesDir,
            context.getDatabasePath(DATABASE_NAME),
        )

        privatePaths.forEach { path ->
            val canonicalPath = path.canonicalPath
            assertTrue(
                "私有路径应属于见域沙箱：$canonicalPath",
                canonicalPath.contains(expectedPackage),
            )
            assertFalse(
                "私有路径不得属于旧包沙箱：$canonicalPath",
                canonicalPath.contains(legacyPackage),
            )
        }
    }

    @Test
    fun freshInstall_hasNoLegacyStateSessionsOrApiKeys() = runBlocking {
        val databaseFile = context.getDatabasePath(DATABASE_NAME)
        val encryptedKeyFile = File(context.noBackupFilesDir, ENCRYPTED_KEY_FILE)
        val legacyFileSentinel = File(context.filesDir, LEGACY_FILE_SENTINEL)
        val legacyPreferences = context.getSharedPreferences(
            LEGACY_PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        )

        assertFalse("身份测试必须从全新见域数据库开始", databaseFile.exists())
        assertFalse("全新见域沙箱不得已有密钥文件", encryptedKeyFile.exists())
        assertFalse("见域不得看见旧包私有文件哨兵", legacyFileSentinel.exists())
        assertFalse(
            "见域不得看见旧包 SharedPreferences 哨兵",
            legacyPreferences.contains(LEGACY_PREFERENCES_KEY),
        )

        val keyStore = EncryptedApiKeyStore(context, ENCRYPTED_KEY_FILE)
        assertTrue(keyStore.read().isEmpty())
        assertNull(keyStore.lastError)
        assertFalse("只读空保险箱不得创建密钥文件", encryptedKeyFile.exists())

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val database = RoundtableDatabase.getDatabase(context, scope)
            assertTrue(database.chatDao().getAllSessions().first().isEmpty())
            database.openHelper.readableDatabase
                .query("SELECT COUNT(*) AS messageCount FROM messages")
                .use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals(0, cursor.getInt(cursor.getColumnIndexOrThrow("messageCount")))
                }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun legacyNamedKeystoreAlias_isNotVisibleInFreshJianyuSandbox() {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

        assertFalse(
            "新包首次启动不得看见旧包 UID 下的同名 Key",
            keyStore.containsAlias(KEY_ALIAS),
        )
    }

    private companion object {
        const val DATABASE_NAME = "roundtable_database"
        const val KEY_ALIAS = "skill_roundtable_api_key_v1"
        const val ENCRYPTED_KEY_FILE = "gemini_api_keys.enc"
        const val LEGACY_FILE_SENTINEL = "pr0901_legacy_private_sentinel.txt"
        const val LEGACY_PREFERENCES_NAME = "pr0901_legacy_identity_sentinel"
        const val LEGACY_PREFERENCES_KEY = "legacy_package_present"
    }
}
