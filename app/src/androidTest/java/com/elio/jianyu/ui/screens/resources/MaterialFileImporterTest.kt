package com.elio.jianyu.ui.screens.resources

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MaterialFileImporterTest {
    @Test
    fun textFileProducesEditableDraftData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "资料摘录.txt").apply {
            writeText("第一段\n第二段")
        }

        val result = MaterialFileImporter.import(context, Uri.fromFile(file))

        assertTrue(result is MaterialFileImportResult.Success)
        val imported = (result as MaterialFileImportResult.Success).file
        assertEquals("资料摘录", imported.title)
        assertEquals("第一段\n第二段", imported.content)
    }

    @Test
    fun emptyAndUnsupportedFilesReturnExplicitFailure() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val empty = File(context.cacheDir, "空资料.txt").apply { writeText("") }
        val unsupported = File(context.cacheDir, "图片.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val emptyResult = MaterialFileImporter.import(context, Uri.fromFile(empty))
        val unsupportedResult = MaterialFileImporter.import(context, Uri.fromFile(unsupported))

        assertTrue(emptyResult is MaterialFileImportResult.Failure)
        assertEquals(
            "文件中没有可用的文本内容。",
            (emptyResult as MaterialFileImportResult.Failure).message,
        )
        assertTrue(unsupportedResult is MaterialFileImportResult.Failure)
        assertEquals(
            "当前支持文本、Markdown、CSV、JSON、HTML 和 DOCX 文件。",
            (unsupportedResult as MaterialFileImportResult.Failure).message,
        )
    }

    @Test
    fun oversizedFileIsRejectedBeforeCreatingDraftData() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "过大资料.txt").apply {
            outputStream().use { output ->
                val block = ByteArray(1024)
                repeat(4097) { output.write(block) }
            }
        }

        val result = MaterialFileImporter.import(context, Uri.fromFile(file))

        assertTrue(result is MaterialFileImportResult.Failure)
        assertEquals(
            "文件超过 4 MB，请先精简后再导入。",
            (result as MaterialFileImportResult.Failure).message,
        )
    }
}
