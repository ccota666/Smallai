package com.mimochat.app.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.mimochat.app.data.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object FilePicker {

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
        }
    }.getOrNull()

    /** 显示用短文件名：保留开头 + 扩展名，中间省略 */
    fun shortName(name: String, maxLen: Int = 18): String {
        if (name.length <= maxLen) return name
        val dot = name.lastIndexOf('.')
        val ext = if (dot > 0) name.substring(dot) else ""
        val base = if (dot > 0) name.substring(0, dot) else name
        val keep = (maxLen - ext.length - 1).coerceAtLeast(4)
        return base.take(keep) + "…" + ext
    }

    /** 文件类型大类 */
    fun typeLabel(type: String): String = when (type) {
        "image" -> "图片"
        "text" -> "文本"
        else -> "文件"
    }

    suspend fun toAttachment(context: Context, uri: Uri): Attachment? =
        withContext(Dispatchers.IO) {
            runCatching {
                val name = displayName(context, uri) ?: "file"
                val mime = context.contentResolver.getType(uri) ?: guessMime(name)
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@withContext null
                when {
                    mime.startsWith("image/") -> {
                        val ext = extFrom(mime, name)
                        val dir = File(context.filesDir, "attachments")
                        dir.mkdirs()
                        val file = File(dir, "${System.currentTimeMillis()}_${name.sanitize()}.$ext")
                        file.writeBytes(bytes)
                        Attachment("image", path = file.absolutePath, name = name)
                    }
                    mime.startsWith("text/") || mime == "application/json" ||
                        mime == "application/xml" || isTextExt(name) ->
                        Attachment("text", name = name, content = String(bytes, Charsets.UTF_8))
                    else -> null
                }
            }.getOrNull()
        }

    fun loadImage(path: String, maxDim: Int = 1200): ImageBitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var sample = 1
        while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap()
    }.getOrNull()

    private fun guessMime(name: String): String =
        when (name.substringAfterLast('.', "").lowercase()) {
            "txt", "md", "markdown", "log" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "csv" -> "text/csv"
            else -> "application/octet-stream"
        }

    private fun isTextExt(name: String): Boolean {
        val e = name.substringAfterLast('.', "").lowercase()
        return e in setOf(
            "txt", "md", "markdown", "log", "csv", "json", "xml", "yaml", "yml",
            "java", "kt", "py", "js", "ts", "html", "css", "sh", "c", "cpp", "h",
            "go", "rs", "rb", "php", "sql", "swift", "toml", "ini", "conf"
        )
    }

    private fun extFrom(mime: String, name: String): String = when (mime) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/bmp" -> "bmp"
        else -> name.substringAfterLast('.', "png").lowercase()
    }

    private fun String.sanitize(): String = replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
