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
            "https://" + "paperly-ai.dark2242v2.workers.dev" + "?model=gemini-3.5-flash-lite"
        private const val FALLBACK_ENDPOINT =
            "https://" + "paperly-ai.dark2242v2.workers.dev" + "?model=gemini-2.5-flash-lite"
        private const val FALLBACK_MAX_RETRIES = 1
        private const val FALLBACK_RETRY_DELAY_MS = 1_000L
    }

    private class ProxyHttpException(
        val statusCode: Int,
        val errorMessage: String
    ) : IllegalStateException(errorMessage) {
        val isFallbackTrigger: Boolean
            get() = statusCode == 404 || statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504 ||
                errorMessage.contains("high demand", ignoreCase = true) ||
                errorMessage.contains("overloaded", ignoreCase = true) ||
                errorMessage.contains("unavailable", ignoreCase = true) ||
                errorMessage.contains("server busy", ignoreCase = true)
    }

    suspend fun summarizePdf(payload: PdfAiPayload): String = withTimeout(45_000L) {
        val requestBodyString = buildRequestPayload(payload).toString()

        // 1. Primary fast route: ?model=gemini-3.5-flash-lite (2-4s typical response)
        var lastError: Exception? = null
        try {
            return@withTimeout executeGenerateContent(
                endpointUrl = PRIMARY_ENDPOINT,
                requestBodyString = requestBodyString
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProxyHttpException) {
            lastError = e
            if (!e.isFallbackTrigger) {
                throw e
            }
            Log.w(TAG, "$PRIMARY_ENDPOINT failed (HTTP ${e.statusCode}), falling back immediately to $FALLBACK_ENDPOINT", e)
        }

        // 2. Immediate fallback to ?model=gemini-2.5-flash-lite on HTTP 429, 404, or 503
        for (attempt in 0..FALLBACK_MAX_RETRIES) {
            try {
                return@withTimeout executeGenerateContent(
                    endpointUrl = FALLBACK_ENDPOINT,
                    requestBodyString = requestBodyString
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ProxyHttpException) {
                lastError = e
                if (e.isFallbackTrigger && attempt < FALLBACK_MAX_RETRIES) {
                    Log.w(TAG, "$FALLBACK_ENDPOINT busy (HTTP ${e.statusCode}) on attempt ${attempt + 1}", e)
                    delay(FALLBACK_RETRY_DELAY_MS)
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
        val truncationHint = if (payload.wasTruncated) {
            "\nNote: This document is large; the text below was compressed and capped at the first 18,000 characters."
        } else {
            ""
        }

        val formattingGuidelines = """
            Automatically identify the document type (e.g., study notes, contract/agreement, invoice/financial report, resume/CV, technical manual, research paper, or article) and adapt your terminology and focus accordingly.

            Format your response strictly as a polished executive brief using EXACTLY these four Markdown section headings and concise bullet points with **bold key terms** (such as **important terms**, figures, dates, obligations, or formulas):

            ## Executive Overview
            Write a crisp 2-sentence core takeaway stating the identified document type and its primary purpose.

            ## Key Facts, Clauses & Concepts
            - Bullet points highlighting **bold key terms**, critical figures, dates, definitions, or core concepts tailored to this document type.

            ## Critical Details & Comparisons
            - Bullet points detailing important rules, obligations, clauses, formulas, pricing breakdowns, comparisons, or metrics specific to the document.

            ## Action Items & Key Takeaways
            - Top 3–5 concrete next steps, deadlines, warnings, or must-remember takeaways.

            Keep the tone authoritative, clear, and concise. Use `- ` for bullets and `**bold**` only for high-signal terms. Do not include any preamble or closing chatter outside these four sections.
        """.trimIndent()

        if (trimmedText.length >= 60) {
            val prompt = """
                You are Paperly AI, an expert executive document analyst.
                Analyze the PDF "${payload.displayName}" (${payload.pageCount} pages).$truncationHint

                $formattingGuidelines

                Document Content:
                $trimmedText
            """.trimIndent()
            partsArray.put(JSONObject().put("text", prompt))
        } else {
            val prompt = """
                You are Paperly AI, an expert executive document analyst.
                This PDF ("${payload.displayName}", ${payload.pageCount} pages) appears scanned or image-based.
                Read the attached page images carefully and produce an executive-grade breakdown.

                $formattingGuidelines
            """.trimIndent()
            partsArray.put(JSONObject().put("text", prompt))

            payload.fallbackBitmaps.forEach { bitmap ->
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
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
                    .put("temperature", 0.25)
                    .put("maxOutputTokens", 1400)
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
