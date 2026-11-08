package com.yesmin.ai.ai

import com.yesmin.ai.data.SecureKeyStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.util.Base64

data class Turn(val role: String, val text: String) // role: "user" or "model"

/** Inline file sent with a prompt (image/jpeg or application/pdf). */
class Attachment(val bytes: ByteArray, val mime: String)

data class ModelInfo(val id: String, val displayName: String, val methods: List<String>) {
    val supportsText get() = "generateContent" in methods
    val supportsLive get() = "bidiGenerateContent" in methods
}

class GeminiException(message: String) : Exception(message)

/**
 * Thin client for the official Gemini Developer API (REST, v1beta).
 * The API key is read from [SecureKeyStore] per request and is never logged.
 */
class GeminiClient(
    private val keys: SecureKeyStore,
    val http: OkHttpClient = defaultHttp()
) {

    suspend fun listModels(): Result<List<ModelInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            val key = requireKey()
            val req = Request.Builder()
                .url("$BASE/models?pageSize=200")
                .header("x-goog-api-key", key)
                .build()
            val body = execute(req)
            val arr = JSONObject(body).optJSONArray("models") ?: JSONArray()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val methods = o.optJSONArray("supportedGenerationMethods")
                ModelInfo(
                    id = o.getString("name").removePrefix("models/"),
                    displayName = o.optString("displayName"),
                    methods = if (methods == null) emptyList()
                    else (0 until methods.length()).map { methods.getString(it) }
                )
            }
        }
    }

    /** Lightweight connection test: lists models with the saved key. */
    suspend fun testConnection(): Result<Int> =
        listModels().map { it.size }

    suspend fun generate(
        model: String,
        system: String,
        turns: List<Turn>,
        temperature: Float,
        attachment: Attachment? = null,
        useSearch: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val key = requireKey()
            val contents = JSONArray()
            turns.forEachIndexed { index, t ->
                val parts = JSONArray().put(JSONObject().put("text", t.text))
                // Attach the image/PDF to the last user turn.
                if (attachment != null && index == turns.lastIndex && t.role == "user") {
                    parts.put(
                        JSONObject().put(
                            "inlineData",
                            JSONObject()
                                .put("mimeType", attachment.mime)
                                .put("data", Base64.encodeToString(attachment.bytes, Base64.NO_WRAP))
                        )
                    )
                }
                contents.put(JSONObject().put("role", t.role).put("parts", parts))
            }
            val root = JSONObject()
                .put("contents", contents)
                .put(
                    "systemInstruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system)))
                )
                .put("generationConfig", JSONObject().put("temperature", temperature.toDouble()))
            if (useSearch) {
                root.put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            }
            val req = Request.Builder()
                .url("$BASE/models/$model:generateContent")
                .header("x-goog-api-key", key)
                .post(root.toString().toRequestBody(JSON))
                .build()
            parseText(execute(req))
        }
    }

    private fun parseText(body: String): String {
        val json = JSONObject(body)
        val cands = json.optJSONArray("candidates")
        if (cands == null || cands.length() == 0) {
            val block = json.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
            throw GeminiException(
                if (block.isNotEmpty()) "Gemini blocked this request ($block). Try rephrasing."
                else "Gemini returned no answer."
            )
        }
        val parts = cands.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
        val sb = StringBuilder()
        if (parts != null) for (i in 0 until parts.length()) {
            sb.append(parts.getJSONObject(i).optString("text"))
        }
        if (sb.isBlank()) throw GeminiException("Gemini returned an empty answer.")
        return sb.toString().trim()
    }

    private fun requireKey(): String =
        keys.get()?.takeIf { it.isNotBlank() }
            ?: throw GeminiException("No API key saved. Open Settings and add your Gemini API key.")

    private fun execute(req: Request): String {
        try {
            http.newCall(req).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (resp.isSuccessful) return text
                throw GeminiException(describeHttpError(resp.code, text))
            }
        } catch (e: GeminiException) {
            throw e
        } catch (e: IOException) {
            throw GeminiException("Network problem. Check your internet connection and try again.")
        }
    }

    companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta"
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        fun describeHttpError(code: Int, body: String): String {
            val apiMsg = runCatching {
                JSONObject(body).getJSONObject("error").getString("message")
            }.getOrNull()
            val hint = when (code) {
                400 -> if (apiMsg?.contains("API key", true) == true)
                    "The API key looks invalid. Re-check it in Settings."
                else "Bad request. The model name or settings may be wrong."
                401, 403 -> "The key was rejected or lacks permission for this model."
                404 -> "Model not found. Pick another model in Settings (use Load models)."
                429 -> "Quota or rate limit reached. Wait a bit or check your Gemini plan."
                in 500..599 -> "Gemini is having trouble right now. Try again shortly."
                else -> "Request failed."
            }
            return "$hint (HTTP $code)"
        }
    }
}
