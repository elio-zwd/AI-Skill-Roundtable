package com.elio.jianyu.backup.design

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupDocumentationContractTest {
    @Test
    fun envelopeAndHandoffShareFrozenAlgorithmsAndIdentifiers() {
        val root = repositoryRoot()
        val threatModel = File(
            root,
            "docs/security/pr-09-13a-backup-threat-model.md",
        ).readText(Charsets.UTF_8)
        val envelope = File(
            root,
            "docs/architecture/pr-09-13a-backup-envelope-spec.md",
        ).readText(Charsets.UTF_8)
        val handoff = File(
            root,
            "docs/planning/pr-09-13a-interface-handoff.md",
        ).readText(Charsets.UTF_8)

        listOf(
            "Argon2id",
            "Purge",
            "Android Auto Backup",
            "authentication_failed",
        ).forEach { value ->
            assertTrue("威胁模型缺少安全边界：$value", threatModel.contains(value))
        }

        listOf(
            "Argon2id",
            "65,536 KiB",
            "AES-256-GCM",
            "AES256_GCM_HKDF_1MB",
            "jianyu-portable-backup/1",
            "jianyu-device-snapshot/1",
            "4A 59 42 4B 50 0D 0A 1A",
            "4A 59 53 4E 50 0D 0A 1A",
            "jianyu_backup_snapshot_wrap_v1",
            "org.bouncycastle:bcprov-jdk15to18:1.84",
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
