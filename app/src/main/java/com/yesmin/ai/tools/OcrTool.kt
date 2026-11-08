package com.yesmin.ai.tools

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Image helpers: on-device OCR (Latin script) and JPEG downscaling for Gemini image input. */
object ImageTools {

    fun loadBitmap(context: Context, uri: Uri, maxSide: Int = 1600): Bitmap? =
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()

    fun toJpeg(bitmap: Bitmap, maxSide: Int = 1280, quality: Int = 85): ByteArray {
        val scale = maxSide.toFloat() / maxOf(bitmap.width, bitmap.height)
        val bmp = if (scale < 1f)
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        else bitmap
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }.toByteArray()
    }

    /** Extracts text with ML Kit. Handles Latin script (English, Hinglish in Roman letters). */
    suspend fun ocr(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
        runCatching {
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                suspendCancellableCoroutine<String> { cont ->
                    recognizer.process(InputImage.fromBitmap(bitmap, 0))
                        .addOnSuccessListener { cont.resume(it.text) }
                        .addOnFailureListener { cont.resumeWithException(it) }
                }
            } finally {
                recognizer.close()
            }
        }
    }
}
