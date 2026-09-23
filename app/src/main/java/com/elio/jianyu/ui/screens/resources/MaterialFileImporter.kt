package com.elio.jianyu.ui.screens.resources

import android.content.Context
import android.net.Uri
import com.elio.jianyu.material.MaterialDocumentReadResult
import com.elio.jianyu.material.MaterialDocumentReader

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

/** UI-04 adapter；真正的读取限制由中立 MaterialDocumentReader 统一执行。 */
internal object MaterialFileImporter {
    suspend fun import(context: Context, uri: Uri): MaterialFileImportResult =
        when (val result = MaterialDocumentReader.read(context, uri)) {
            is MaterialDocumentReadResult.Success -> MaterialFileImportResult.Success(
                ImportedMaterialFile(
                    title = result.document.title,
                    sourceLocator = result.document.sourceLocator,
                    content = result.document.content,
                    mimeType = result.document.mimeType,
                    sizeBytes = result.document.sizeBytes,
                ),
            )
            is MaterialDocumentReadResult.Failure -> MaterialFileImportResult.Failure(
                when (result.code) {
                    "file_too_large" -> "文件超过 4 MB，请先精简后再导入。"
                    "pdf_text_extraction_unavailable" -> "当前无法在本地可靠提取该 PDF 的正文，请先导出为文本或 DOCX。"
                    "unsupported_file_type" -> "当前支持文本、Markdown、CSV、JSON、HTML 和 DOCX 文件。"
                    "empty_text" -> "文件中没有可用的文本内容。"
                    "content_too_large" -> "文件正文超过 20 万字，请先精简后再导入。"
                    "file_unreadable" -> "无法读取该文件。"
                    else -> "文件读取失败，请重新选择。"
                },
            )
        }
}
