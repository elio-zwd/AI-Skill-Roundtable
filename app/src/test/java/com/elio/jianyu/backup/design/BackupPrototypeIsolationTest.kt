package com.elio.jianyu.backup.design

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPrototypeIsolationTest {
    @Test
    fun designPrototypeIsNotReferencedByProductionRuntime() {
        val root = repositoryRoot()
        val productionRoot = File(root, "app/src/main")
        val forbiddenNames = listOf(
            "com.elio.jianyu.backup.design",
            "BackupCryptoPrototype",
            "BackupEnvelopePrototype",
            "BackupRecordStreamPrototype",
            "BackupDataScopePrototype",
        )

        val matches = productionRoot.walkTopDown()
            .filter(File::isFile)
            .filter { it.extension in setOf("kt", "java", "xml") }
            .flatMap { file ->
                val text = file.readText(Charsets.UTF_8)
                forbiddenNames.asSequence()
                    .filter(text::contains)
                    .map { forbidden -> "${file.relativeTo(root).path}:$forbidden" }
            }
            .toList()

        assertTrue("设计原型不得进入生产调用链：$matches", matches.isEmpty())
    }

    @Test
    fun cryptographicPrototypeDependenciesAreTestOnly() {
        val buildFile = File(repositoryRoot(), "app/build.gradle.kts")
        val relevantLines = buildFile.readLines(Charsets.UTF_8)
            .map(String::trim)
            .filter { line ->
                line.contains("bcprov-jdk18on") || line.contains("tink-android")
            }

        assertTrue(relevantLines.any { it == "testImplementation(\"org.bouncycastle:bcprov-jdk18on:1.84\")" })
        assertTrue(relevantLines.any { it == "testImplementation(\"com.google.crypto.tink:tink-android:1.23.0\")" })
        assertFalse(relevantLines.any { it.startsWith("implementation(") })
        assertFalse(relevantLines.any { it.startsWith("api(") })
        assertFalse(relevantLines.any { it.startsWith("androidTestImplementation(") })
    }

    @Test
    fun productionAliasAndSnapshotAliasRemainSeparated() {
        assertFalse(
            BackupDesignConstants.SNAPSHOT_KEY_ALIAS ==
                BackupDesignConstants.API_KEY_ALIAS_MUST_NOT_BE_REUSED,
        )
    }

    private fun repositoryRoot(): File {
        val candidates = generateSequence(File(".").absoluteFile) { current -> current.parentFile }
            .take(6)
            .toList()
        return candidates.firstOrNull { candidate ->
            File(candidate, "settings.gradle.kts").isFile && File(candidate, "app").isDirectory
        } ?: error("无法定位仓库根目录，当前目录=${File(".").absolutePath}")
    }
}
