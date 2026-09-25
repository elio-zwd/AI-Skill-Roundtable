package com.elio.jianyu.material

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Xml
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser

internal data class MaterialDocument(
    val title: String,
    val sourceLocator: String,
    val content: String,
    val mimeType: String,
    val sizeBytes: Long,
)

internal sealed interface MaterialDocumentReadResult {
    data class Success(val document: MaterialDocument) : MaterialDocumentReadResult
    data class Failure(val code: String) : MaterialDocumentReadResult
}

/**
 * 资料正文唯一文件读取边界。页面和对话附件都走同一套字节、字符和格式限制，
 * 不把二进制/PDF 内容伪装成 UTF-8 文本。
 */
internal object MaterialDocumentReader {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_CHARACTERS = 200_000

    suspend fun read(context: Context, uri: Uri): MaterialDocumentReadResult = withContext(Dispatchers.IO) {
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
                return@withContext MaterialDocumentReadResult.Failure("file_too_large")
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
                    return@withContext MaterialDocumentReadResult.Failure("file_too_large")
                }
                buffer.copyOf(offset)
            } ?: return@withContext MaterialDocumentReadResult.Failure("file_unreadable")

            val mimeType = resolver.getType(uri).orEmpty()
            val extension = displayName.substringAfterLast('.', "").lowercase()
            val extracted = when {
                extension == "docx" ||
                    mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    extractDocx(bytes)
                extension in setOf("txt", "md", "csv", "json", "xml", "html", "htm") ||
                    mimeType.startsWith("text/") || mimeType == "application/json" ->
                    bytes.toString(Charsets.UTF_8)
                extension == "pdf" || mimeType == "application/pdf" ->
                    return@withContext MaterialDocumentReadResult.Failure("pdf_text_extraction_unavailable")
                else -> return@withContext MaterialDocumentReadResult.Failure("unsupported_file_type")
            }.trim()

            if (extracted.isBlank()) {
                return@withContext MaterialDocumentReadResult.Failure("empty_text")
            }
            if (extracted.length > MAX_CHARACTERS) {
                return@withContext MaterialDocumentReadResult.Failure("content_too_large")
            }
            MaterialDocumentReadResult.Success(
                MaterialDocument(
                    title = displayName.substringBeforeLast('.').ifBlank { displayName },
                    sourceLocator = uri.toString(),
                    content = extracted,
                    mimeType = mimeType.ifBlank { extension.ifBlank { "file" } },
                    sizeBytes = if (declaredSize >= 0) declaredSize else bytes.size.toLong(),
                ),
            )
        }.getOrElse {
            MaterialDocumentReadResult.Failure("file_read_failed")
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
