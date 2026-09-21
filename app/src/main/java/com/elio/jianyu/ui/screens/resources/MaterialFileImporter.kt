package com.elio.jianyu.ui.screens.resources

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

internal data class ImportedMaterialFile(
    val title: String,
    val sourceLocator: String,
    val content: String,
    val mimeType: String,
    val sizeBytes: Long,
)

internal sealed interface MaterialFileImportResult {
    data class Success(val file: ImportedMaterialFile) : MaterialFileImportResult
    data class Failure(val message: String) : MaterialFileImportResult
}

internal object MaterialFileImporter {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_CHARACTERS = 200_000

    suspend fun import(context: Context, uri: Uri): MaterialFileImportResult = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val metadata = runCatching {
                resolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (!cursor.moveToFirst()) return@use null
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                    name to size
                }
            }.getOrNull()
            val displayName = metadata?.first?.takeIf(String::isNotBlank)
                ?: uri.lastPathSegment?.substringAfterLast('/')?.takeIf(String::isNotBlank)
                ?: "本地文件"
            val declaredSize = metadata?.second ?: -1L
            if (declaredSize > MAX_BYTES) {
                return@withContext MaterialFileImportResult.Failure("文件超过 4 MB，请先精简后再导入。")
            }
            val bytes = resolver.openInputStream(uri)?.use { input ->
                val buffer = ByteArray(MAX_BYTES + 1)
                var offset = 0
                while (offset < buffer.size) {
                    val count = input.read(buffer, offset, buffer.size - offset)
                    if (count < 0) break
                    offset += count
                }
                if (offset > MAX_BYTES) {
                    return@withContext MaterialFileImportResult.Failure("文件超过 4 MB，请先精简后再导入。")
                }
                buffer.copyOf(offset)
            } ?: return@withContext MaterialFileImportResult.Failure("无法读取该文件。")

            val mimeType = resolver.getType(uri).orEmpty()
            val extension = displayName.substringAfterLast('.', "").lowercase()
            val extracted = when {
                extension == "docx" || mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    extractDocx(bytes)
                extension in setOf("txt", "md", "csv", "json", "xml", "html", "htm") ||
                    mimeType.startsWith("text/") || mimeType == "application/json" ->
                    bytes.toString(Charsets.UTF_8)
                extension == "pdf" || mimeType == "application/pdf" ->
                    return@withContext MaterialFileImportResult.Failure(
                        "当前无法在本地可靠提取该 PDF 的正文，请先导出为文本或 DOCX。",
                    )
                else -> return@withContext MaterialFileImportResult.Failure(
                    "当前支持文本、Markdown、CSV、JSON、HTML 和 DOCX 文件。",
                )
            }.trim()

            if (extracted.isBlank()) {
                return@withContext MaterialFileImportResult.Failure("文件中没有可用的文本内容。")
            }
            if (extracted.length > MAX_CHARACTERS) {
                return@withContext MaterialFileImportResult.Failure("文件正文超过 20 万字，请先精简后再导入。")
            }
            MaterialFileImportResult.Success(
                ImportedMaterialFile(
                    title = displayName.substringBeforeLast('.').ifBlank { displayName },
                    sourceLocator = uri.toString(),
                    content = extracted,
                    mimeType = mimeType.ifBlank { extension.ifBlank { "file" } },
                    sizeBytes = if (declaredSize >= 0) declaredSize else bytes.size.toLong(),
                ),
            )
        }.getOrElse {
            MaterialFileImportResult.Failure("文件读取失败，请重新选择。")
        }
    }

    private fun extractDocx(bytes: ByteArray): String {
        val documentXml = ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") return@use zip.readBytes()
                entry = zip.nextEntry
            }
            null
        } ?: return ""

        val parser = Xml.newPullParser().apply {
            setInput(ByteArrayInputStream(documentXml), Charsets.UTF_8.name())
        }
        val result = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.TEXT -> result.append(parser.text)
                XmlPullParser.END_TAG -> if (parser.name in setOf("p", "br", "tab")) result.append('\n')
            }
            event = parser.next()
        }
        return result.toString()
    }
}
