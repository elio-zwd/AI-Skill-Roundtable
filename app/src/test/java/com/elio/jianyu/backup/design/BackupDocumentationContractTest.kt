package com.elio.jianyu.backup.design

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupDocumentationContractTest {
    @Test
    fun frozenDocumentsShareTheSameAlgorithmsAndIdentifiers() {
        val root = repositoryRoot()
        val documents = listOf(
            "docs/security/pr-09-13a-backup-threat-model.md",
            "docs/architecture/pr-09-13a-backup-envelope-spec.md",
            "docs/architecture/pr-09-13a-backup-data-scope.md",
            "docs/planning/pr-09-13a-interface-handoff.md",
        ).associateWith { path -> File(root, path).readText(Charsets.UTF_8) }

        val requiredAcrossSecurityDocuments = listOf(
            "Argon2id",
            "65,536 KiB",
            "AES-256-GCM",
            "AES256_GCM_HKDF_1MB",
        )
        requiredAcrossSecurityDocuments.forEach { value ->
            assertTrue(
                "冻结文档必须共同包含 $value",
                documents.values.all { text -> text.contains(value) },
            )
        }

        val envelope = documents.getValue("docs/architecture/pr-09-13a-backup-envelope-spec.md")
        val handoff = documents.getValue("docs/planning/pr-09-13a-interface-handoff.md")
        listOf(
            "jianyu-portable-backup/1",
            "jianyu-device-snapshot/1",
            "4A 59 42 4B 50 0D 0A 1A",
            "4A 59 53 4E 50 0D 0A 1A",
            "jianyu_backup_snapshot_wrap_v1",
            "org.bouncycastle:bcprov-jdk18on:1.84",
            "com.google.crypto.tink:tink-android:1.23.0",
        ).forEach { value ->
            assertTrue("Envelope 规范缺少 $value", envelope.contains(value))
            assertTrue("交接文档缺少 $value", handoff.contains(value))
        }
    }

    @Test
    fun frozenDocumentsContainNoImplementationPlaceholders() {
        val root = repositoryRoot()
        val frozenPaths = listOf(
            "docs/security/pr-09-13a-backup-threat-model.md",
            "docs/architecture/pr-09-13a-backup-envelope-spec.md",
            "docs/architecture/pr-09-13a-backup-data-scope.md",
            "docs/planning/pr-09-13a-interface-handoff.md",
        )
        val forbidden = listOf(
            "TODO",
            "TBD",
            "待定",
            "之后决定",
            "实现时选择",
            "任选一种",
        )

        frozenPaths.forEach { path ->
            val text = File(root, path).readText(Charsets.UTF_8)
            forbidden.forEach { marker ->
                assertFalse("$path 不得包含占位内容：$marker", text.contains(marker))
            }
        }
    }

    private fun repositoryRoot(): File {
        return generateSequence(File(".").absoluteFile) { current -> current.parentFile }
            .take(6)
            .firstOrNull { candidate ->
                File(candidate, "settings.gradle.kts").isFile && File(candidate, "app").isDirectory
            } ?: error("无法定位仓库根目录，当前目录=${File(".").absolutePath}")
    }
}
