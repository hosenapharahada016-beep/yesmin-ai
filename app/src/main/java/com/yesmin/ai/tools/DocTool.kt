package com.yesmin.ai.tools

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a user-picked document through the system file picker (no storage permission needed).
 * Plain-text formats are read directly. PDFs are sent to Gemini as-is via GeminiClient.generate(attachment = ...),
 * so no PDF parsing library is bundled.
 */
object DocTool {

    const val MAX_TEXT_CHARS = 60_000

    fun mimeOf(context: Context, uri: Uri): String =
        context.contentResolver.getType(uri) ?: "application/octet-stream"

    fun nameOf(context: Context, uri: Uri): String =
        runCatching {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull() ?: "document"

    suspend fun readText(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: error("Could not open the file.")
            if (text.length > MAX_TEXT_CHARS) text.take(MAX_TEXT_CHARS) + "\n[...truncated]" else text
        }
    }

    suspend fun readBytes(context: Context, uri: Uri, maxBytes: Int = 15 * 1024 * 1024): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                val b = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("Could not open the file.")
                if (b.size > maxBytes) error("File is too large (max 15 MB).")
                b
            }
        }
}
