package com.example.data.ai

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.data.model.PdfAiPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

class AiSummarizeRepository {

    companion object {
        private const val GEMINI_REST_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"
    }

    suspend fun summarizePdf(payload: PdfAiPayload): String = withTimeout(60_000L) {
        withContext(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY.trim()
            if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
                throw IllegalStateException(
                    "GEMINI_API_KEY is not configured. Add your key in the AI Studio Secrets panel (or .env) and rebuild."
                )
            }

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

            val requestBody = JSONObject()
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

            val url = URL(GEMINI_REST_ENDPOINT)
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 45_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }

            try {
                connection.outputStream.use { os ->
                    os.write(requestBody.toString().toByteArray(Charsets.UTF_8))
                }

                val statusCode = connection.responseCode
                val responseStream = if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val rawResponse = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                if (statusCode !in 200..299) {
                    val apiError = runCatching {
                        JSONObject(rawResponse).optJSONObject("error")?.optString("message")
                    }.getOrNull()
                    throw IllegalStateException(
                        apiError?.takeIf { it.isNotBlank() }
                            ?: "Gemini REST API request failed (HTTP $statusCode)."
                    )
                }

                val json = JSONObject(rawResponse)
                val candidates = json.optJSONArray("candidates")
                if (candidates == null || candidates.length() == 0) {
                    throw IllegalStateException("Gemini returned no summary candidates.")
                }

                val contentParts = candidates
                    .getJSONObject(0)
                    .optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?: throw IllegalStateException("Gemini response had no content parts.")

                val summaryBuilder = StringBuilder()
                for (i in 0 until contentParts.length()) {
                    val partText = contentParts.getJSONObject(i).optString("text")
                    if (partText.isNotBlank()) {
                        if (summaryBuilder.isNotEmpty()) summaryBuilder.append("\n")
                        summaryBuilder.append(partText.trim())
                    }
                }

                val result = summaryBuilder.toString().trim()
                if (result.isEmpty()) {
                    throw IllegalStateException("Gemini returned an empty summary.")
                }
                result
            } finally {
                connection.disconnect()
            }
        }
    }
}
