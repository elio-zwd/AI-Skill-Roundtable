package com.elio.jianyu.backup

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class RepositoryBackupMapperCoverageTest {
    private val mapperSource: String by lazy {
        findRepositoryRoot()
            .resolve("app/src/main/java/com/elio/jianyu/backup/RepositoryBackupMapper.kt")
            .readText()
    }

    @Test
    fun frozenPortableRegistryEntriesHaveConcreteProductionMappings() {
        val requiredTypes = listOf(
            "participant_state",
            "run_budget",
            "message_usage",
            "cross_discussion",
            "archive_event",
            "resume_event",
            "issue_relation",
            "safe_user_setting",
        )

        requiredTypes.forEach { type ->
            assertTrue(
                "RepositoryBackupMapper 缺少冻结实体类型：$type",
                mapperSource.contains("\"$type\""),
            )
        }
    }

    @Test
    fun mapperReadsFormalRuntimeAndLifecycleSourcesInsteadOfSilentlyOmittingThem() {
        listOf(
            "getExecutionRuntime(",
            "getStageCollaboration(",
            "listArchiveEvents(",
            "listResumeEvents(",
            "listIssueRelations(",
            "UNSUPPORTED_LEGACY_DATA",
        ).forEach { marker ->
            assertTrue(
                "RepositoryBackupMapper 缺少正式数据读取/预检：$marker",
                mapperSource.contains(marker),
            )
        }
    }

    private fun findRepositoryRoot(): File {
        var current: File? = File(System.getProperty("user.dir")).absoluteFile
        while (current != null) {
            if (current.resolve("settings.gradle.kts").isFile) return current
            current = current.parentFile
        }
        error("无法定位仓库根目录")
    }
}
