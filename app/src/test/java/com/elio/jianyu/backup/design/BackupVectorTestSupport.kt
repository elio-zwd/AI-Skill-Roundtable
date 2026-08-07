package com.elio.jianyu.backup.design

import java.io.File
import org.json.JSONObject

/** Test-only loader for public PR09-13A vectors. */
internal object BackupVectorTestSupport {
    fun load(name: String): JSONObject {
        val relative = "docs/testing/vectors/pr-09-13a/$name"
        val candidates = listOf(
            File(relative),
            File("../$relative"),
            File("../../$relative"),
        )
        val file = candidates.firstOrNull(File::isFile)
            ?: error("找不到公开测试向量：$relative，当前目录=${File(".").absolutePath}")
        return JSONObject(file.readText(Charsets.UTF_8))
    }
}

internal fun Throwable.requireBackupCode(expected: BackupDesignErrorCode) {
    val actual = this as? BackupDesignException
        ?: throw AssertionError("期望 BackupDesignException，实际为 ${this::class.java.name}", this)
    if (actual.errorCode != expected) {
        throw AssertionError("期望 ${expected.storageValue}，实际 ${actual.errorCode.storageValue}", actual)
    }
}
