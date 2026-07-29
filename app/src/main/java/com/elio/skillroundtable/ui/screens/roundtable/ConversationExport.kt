package com.elio.skillroundtable.ui.screens.roundtable

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.elio.skillroundtable.telemetry.PrivacySafeLogger
import java.nio.charset.StandardCharsets

fun saveMarkdownToLocal(context: Context, title: String, content: String): String? {
    val resolver = context.contentResolver
    val safeTitle = title.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "${safeTitle}_${System.currentTimeMillis()}.md")
        put(MediaStore.MediaColumns.MIME_TYPE, "text/markdown")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/AI智囊圆桌")
        }
    }

    val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
    if (uri != null) {
        try {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray(StandardCharsets.UTF_8))
            }
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "Documents/AI智囊圆桌"
            } else {
                uri.path
            }
        } catch (e: Exception) {
            PrivacySafeLogger.e(
                "MainActivity",
                "保存 Markdown 失败",
                e
            )
        }
    }
    return null
}
