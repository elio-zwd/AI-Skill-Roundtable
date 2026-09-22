package com.elio.jianyu.backup

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.elio.jianyu.JianyuAppRuntimeProvider
import com.elio.jianyu.data.RepositoryResult
import com.elio.jianyu.data.SaveIssueCommand
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceSnapshotServiceAndroidTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() = runBlocking {
        JianyuAppRuntimeProvider.resetForTests(context)
        context.deleteDatabase("roundtable_database")
        SnapshotCatalog.list(context).forEach { SnapshotCatalog.delete(context, it.snapshotId) }
    }

    @After
    fun tearDown() = runBlocking {
        JianyuAppRuntimeProvider.resetForTests(context)
        SnapshotCatalog.list(context).forEach { SnapshotCatalog.delete(context, it.snapshotId) }
    }

    @Test
    fun snapshotIsVerifiedAndRuntimeReopensWithPersistedData() = runBlocking {
        val runtime = JianyuAppRuntimeProvider.get(context)
        val save = runtime.repository.saveIssue(
            SaveIssueCommand(
                issueId = "snapshot-issue",
                title = "快照验收",
                initialStageId = "snapshot-stage",
                initialStageTitle = "快照节点",
                initialObjective = "验证闭库重开",
                createdAt = 1L,
            ),
        )
        assertTrue(save is RepositoryResult.Success)

        val result = DeviceSnapshotService(context).createSnapshot("snapshot-test")
        assertTrue(result.file.isFile)
        assertTrue(result.file.name.endsWith(BackupProtocol.snapshotExtension))
        assertEquals("snapshot-test", SnapshotCatalog.list(context).single().snapshotId)

        val reopened = JianyuAppRuntimeProvider.get(context).repository.recoverIssue("snapshot-issue")
        assertTrue(reopened is RepositoryResult.Success)
    }
}
