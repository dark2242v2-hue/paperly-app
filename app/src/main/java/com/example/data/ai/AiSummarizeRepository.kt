package com.example.data.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.example.data.model.PdfAiPayload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val TAG = "AiSummarizeRepository"

class AiSummarizeRepository {

    companion object {
        private const val PRIMARY_ENDPOINT =
            "https://" + "paperly-ai.dark2242v2.workers.dev" + "?model=gemini-3.8-flash"
        private const val FALLBACK_ENDPOINT =
            "https://" + "paperly-ai.dark2242v2.workers.dev" + "?model=gemini-3.5-flash-lite"
        private const val MAX_RETRIES_PER_ENDPOINT = 2
        private const val RETRY_DELAY_MS = 2_000L
    }

    private class ProxyHttpException(
        val statusCode: Int,
        val errorMessage: String
    ) : IllegalStateException(errorMessage) {
        val shouldFallbackImmediately: Boolean
            get() = statusCode == 404

        val isRateLimitedOrBusy: Boolean
            get() = statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504 ||
                errorMessage.contains("high demand", ignoreCase = true) ||
                errorMessage.contains("overloaded", ignoreCase = true) ||
                errorMessage.contains("unavailable", ignoreCase = true) ||
                errorMessage.contains("server busy", ignoreCase = true)

        val shouldRetryWithFallback: Boolean
            get() = shouldFallbackImmediately || isRateLimitedOrBusy
    }

    suspend fun summarizePdf(payload: PdfAiPayload): String = withTimeout(90_000L) {
        val requestBodyString = buildRequestPayload(payload).toString()

        // 1. Send POST to paperly-ai.dark2242v2.workers.dev?model=gemini-3.8-flash with retries on 429/503
        var lastError: Exception? = null
        for (attempt in 0..MAX_RETRIES_PER_ENDPOINT) {
            try {
                return@withTimeout executeGenerateContent(
                    endpointUrl = PRIMARY_ENDPOINT,
                    requestBodyString = requestBodyString
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProxyHttpException) {
                lastError = e
                if (e.shouldFallbackImmediately) {
                    Log.w(TAG, "$PRIMARY_ENDPOINT returned HTTP ${e.statusCode}, switching to fallback", e)
                    break
                } else if (e.isRateLimitedOrBusy) {
                    Log.w(TAG, "$PRIMARY_ENDPOINT busy (HTTP ${e.statusCode}) on attempt ${attempt + 1}", e)
                    if (attempt < MAX_RETRIES_PER_ENDPOINT) {
                        delay(RETRY_DELAY_MS)
                        continue
                    }
                    break
                } else {
                    throw e
                }
            }
        }

        // 2. Automatic fallback to ?model=gemini-3.5-flash-lite on HTTP 429, 404, or 503
        Log.i(TAG, "Falling back to $FALLBACK_ENDPOINT due to primary failure (${(lastError as? ProxyHttpException)?.statusCode})")
        for (attempt in 0..MAX_RETRIES_PER_ENDPOINT) {
            try {
                return@withTimeout executeGenerateContent(
                    endpointUrl = FALLBACK_ENDPOINT,
                    requestBodyString = requestBodyString
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProxyHttpException) {
                lastError = e
                if (e.isRateLimitedOrBusy && attempt < MAX_RETRIES_PER_ENDPOINT) {
                    Log.w(TAG, "$FALLBACK_ENDPOINT busy (HTTP ${e.statusCode}) on attempt ${attempt + 1}", e)
                    delay(RETRY_DELAY_MS)
                } else {
                    throw e
                }
            }
        }

        throw (lastError ?: IllegalStateException("AI service is temporarily busy. Please try again."))
    }

    private fun buildRequestPayload(payload: PdfAiPayload): JSONObject {
        val trimmedText = payload.extractedText.trim()
        val partsArray = JSONArray()

        if (trimmedText.length >= 60) {
            val prompt = """
                You are Paperly AI, a concise document assistant.
                Summarize the PDF document "${payload.displayName}" (${payload.pageCount} pages).
                Output a clean, well-structured bullet-point summary using plain bullet points (•).
                Organize it into:
                Overview:
                • 1–2 concise bullets explaining what this document is about

                Key Takeaways:
                • 4–7 crisp bullet points covering the most important facts, figures, and conclusions

                Action Items / Next Steps:
                • Relevant actions, deadlines, or key topics (omit only if none apply)

                Avoid markdown bolding clutter (no **asterisks**) so the bullet points read cleanly.

                Document Text:
                $trimmedText
            """.trimIndent()
            partsArray.put(JSONObject().put("text", prompt))
        } else {
            val prompt = """
                You are Paperly AI, a concise document assistant.
                This PDF ("${payload.displayName}", ${payload.pageCount} pages) appears scanned or image-based.
                Read the attached page images and produce a clean bullet-point summary using plain bullet points (•) with Overview and Key Takeaways.
                Avoid **markdown asterisks** so the summary reads cleanly.
            """.trimIndent()
            partsArray.put(JSONObject().put("text", prompt))

            payload.fallbackBitmaps.forEach { bitmap ->
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 82, baos)
                val base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                partsArray.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", "image/jpeg")
                            .put("data", base64Image)
                    )
                )
            }

            if (trimmedText.isNotEmpty()) {
                partsArray.put(JSONObject().put("text", "Partial extracted text:\n$trimmedText"))
            }
        }

        return JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", partsArray)
                )
            )
            .put(
                "generationConfig",
                JSONObject()
                    .put("temperature", 0.3)
                    .put("maxOutputTokens", 1200)
            )
    }

    private suspend fun executeGenerateContent(
        endpointUrl: String,
        requestBodyString: String
    ): String = withContext(Dispatchers.IO) {
        val url = URL(endpointUrl)
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("User-Agent", "PaperlyAndroidApp/1.0")
            setRequestProperty("Accept", "application/json")
        }

        try {
            connection.outputStream.use { os ->
                os.write(requestBodyString.toByteArray(Charsets.UTF_8))
            }

            val statusCode = connection.responseCode
            val responseStream = if (statusCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val rawResponse = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val trimmedResponse = rawResponse.trimStart()

            if (statusCode !in 200..299) {
                val apiError = if (trimmedResponse.startsWith("{")) {
                    runCatching {
                        val errJson = JSONObject(trimmedResponse)
                        errJson.optJSONObject("error")?.optString("message")
                            ?: errJson.optString("error")
                    }.getOrNull()
                } else {
                    null
                }
                val message = apiError?.takeIf { it.isNotBlank() }
                    ?: "Server busy, please try again (HTTP $statusCode)"
                throw ProxyHttpException(statusCode, message)
            }

            if (!trimmedResponse.startsWith("{")) {
                throw ProxyHttpException(
                    statusCode,
                    "Unexpected non-JSON response from server (HTTP $statusCode)"
                )
            }

            val json = JSONObject(trimmedResponse)

            // Parse standard Gemini response or simplified proxy response
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val contentParts = candidates
                    .getJSONObject(0)
                    .optJSONObject("content")
                    ?.optJSONArray("parts")
                if (contentParts != null) {
                    val summaryBuilder = StringBuilder()
                    for (i in 0 until contentParts.length()) {
                        val partText = contentParts.getJSONObject(i).optString("text")
                        if (partText.isNotBlank()) {
                            if (summaryBuilder.isNotEmpty()) summaryBuilder.append("\n")
                            summaryBuilder.append(partText.trim())
                        }
                    }
                    val parsed = summaryBuilder.toString().trim()
                    if (parsed.isNotEmpty()) return@withContext parsed
                }
            }

            val directText = json.optString("summary").ifBlank { json.optString("text") }.trim()
            if (directText.isNotEmpty()) return@withContext directText

            throw IllegalStateException("AI service returned an empty summary.")
        } finally {
            connection.disconnect()
        }
    }
}
