package com.elio.jianyu.ui.navigation

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JianyuDeepLinkManifestTest {
    private val manifest: String by lazy {
        findManifest().readText()
    }

    @Test
    fun manifest_exposesOnlyStableJianyuNavigationHosts() {
        assertTrue(manifest.contains("android.intent.action.VIEW"))
        assertTrue(manifest.contains("android.intent.category.DEFAULT"))
        assertTrue(manifest.contains("android.intent.category.BROWSABLE"))

        val hosts = Regex("android:host=\"([^\"]+)\"")
            .findAll(manifest)
            .map { match -> match.groupValues[1] }
            .toList()
        assertEquals(listOf("issues", "skills", "resources"), hosts)

        val schemes = Regex("android:scheme=\"([^\"]+)\"")
            .findAll(manifest)
            .map { match -> match.groupValues[1] }
            .toSet()
        assertEquals(setOf("jianyu"), schemes)
    }

    @Test
    fun manifest_doesNotExposeSensitiveDeepLinkFields() {
        listOf("apiKey", "prompt", "materialBody", "artifactBody", "personalContext")
            .forEach { sensitiveField ->
                assertFalse(
                    "Manifest 不得包含敏感深链字段：$sensitiveField",
                    manifest.contains(sensitiveField, ignoreCase = true),
                )
            }
    }

    private fun findManifest(): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        while (current != null) {
            val moduleManifest = current.resolve("src/main/AndroidManifest.xml")
            if (moduleManifest.isFile) return moduleManifest

            val repositoryManifest = current.resolve("app/src/main/AndroidManifest.xml")
            if (repositoryManifest.isFile) return repositoryManifest
            current = current.parentFile
        }
        error("无法定位 app/src/main/AndroidManifest.xml")
    }
}
