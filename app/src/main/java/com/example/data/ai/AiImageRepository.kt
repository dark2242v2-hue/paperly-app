package com.example.data.ai

import android.util.Base64
import android.util.Log
import com.example.data.model.AiImageAspectRatio
import com.example.data.model.AiImageStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private const val TAG = "AiImageRepository"

data class GeneratedAiImagePayload(
    val bytes: ByteArray,
    val mimeType: String,
    val modelUsed: String
)

class AiImageRepository {

    companion object {
        private const val PRIMARY_ENDPOINT =
            "https://paperly-ai.dark2242v2.workers.dev?model=imagen-3.0-generate-002"
        private const val FALLBACK_ENDPOINT =
            "https://paperly-ai.dark2242v2.workers.dev?model=gemini-2.5-flash-image"
    }

    private class ImageProxyException(
        val statusCode: Int,
        val errorMessage: String
    ) : IllegalStateException(errorMessage)

    suspend fun generateImage(
        prompt: String,
        style: AiImageStyle,
        aspectRatio: AiImageAspectRatio
    ): GeneratedAiImagePayload = withTimeout(60_000L) {
        val cleanPrompt = prompt.trim()
        require(cleanPrompt.isNotEmpty()) { "Enter an image prompt first." }

        val styledPrompt = "$cleanPrompt, ${style.promptModifier}"

        // 1. Primary route: Imagen 3 via Cloudflare Worker proxy
        val imagenBody = JSONObject()
            .put("instances", JSONArray().put(JSONObject().put("prompt", styledPrompt)))
            .put(
                "parameters",
                JSONObject()
                    .put("sampleCount", 1)
                    .put("aspectRatio", aspectRatio.apiValue)
            )
            .toString()

        var primaryError: Exception? = null
        try {
            val result = executeImageRequest(
                endpointUrl = PRIMARY_ENDPOINT,
                requestBodyString = imagenBody,
                modelLabel = "imagen-3.0-generate-002"
            )
            return@withTimeout result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            primaryError = e
            Log.w(TAG, "Primary Imagen endpoint failed, falling back to Gemini Flash Image", e)
        }

        // 2. Fallback route: gemini-2.5-flash-image via Cloudflare Worker proxy
        val geminiPrompt = "Generate a high-resolution image with ${aspectRatio.apiValue} aspect ratio. " +
            "Subject and style: $styledPrompt. Return the generated image directly."
        val geminiBody = JSONObject()
            .put(
                "contents",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", geminiPrompt)))
                )
            )
            .put(
                "generationConfig",
                JSONObject().put(
                    "responseModalities",
                    JSONArray().put("IMAGE").put("TEXT")
                )
            )
            .toString()

        try {
            return@withTimeout executeImageRequest(
                endpointUrl = FALLBACK_ENDPOINT,
                requestBodyString = geminiBody,
                modelLabel = "gemini-2.5-flash-image"
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException(
                e.localizedMessage
                    ?: primaryError?.localizedMessage
                    ?: "Could not generate AI image right now. Please try again."
            )
        }
    }

    private suspend fun executeImageRequest(
        endpointUrl: String,
        requestBodyString: String,
        modelLabel: String
    ): GeneratedAiImagePayload = withContext(Dispatchers.IO) {
        val url = URL(endpointUrl)
        val connection = (url.openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 18_000
            readTimeout = 45_000
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
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val rawResponse = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty().trim()

            if (statusCode !in 200..299) {
                val apiError = if (rawResponse.startsWith("{")) {
                    runCatching {
                        val errJson = JSONObject(rawResponse)
                        errJson.optJSONObject("error")?.optString("message")
                            ?: errJson.optString("error")
                    }.getOrNull()
                } else {
                    null
                }
                throw ImageProxyException(
                    statusCode,
                    apiError?.takeIf { it.isNotBlank() } ?: "AI image service busy (HTTP $statusCode)"
                )
            }

            if (!rawResponse.startsWith("{")) {
                throw ImageProxyException(statusCode, "Unexpected response from AI image service.")
            }

            val json = JSONObject(rawResponse)
            val parsed = extractBase64Image(json)
                ?: throw IllegalStateException("AI response did not include image data.")

            val decodedBytes = Base64.decode(parsed.first, Base64.DEFAULT)
            if (decodedBytes.isEmpty()) {
                throw IllegalStateException("Generated image payload was empty.")
            }

            GeneratedAiImagePayload(
                bytes = decodedBytes,
                mimeType = parsed.second,
                modelUsed = modelLabel
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun extractBase64Image(json: JSONObject): Pair<String, String>? {
        // 1. Imagen / Vertex predict format: predictions[0].bytesBase64Encoded
        val predictions = json.optJSONArray("predictions")
        if (predictions != null && predictions.length() > 0) {
            val first = predictions.optJSONObject(0)
            val b64 = first?.optString("bytesBase64Encoded").orEmpty()
            if (b64.isNotBlank()) {
                val mime = first?.optString("mimeType")?.ifBlank { "image/png" } ?: "image/png"
                return b64 to mime
            }
        }

        // 2. generatedImages[0].image.imageBytes
        val generatedImages = json.optJSONArray("generatedImages")
        if (generatedImages != null && generatedImages.length() > 0) {
            val first = generatedImages.optJSONObject(0)
            val imgObj = first?.optJSONObject("image")
            val b64 = imgObj?.optString("imageBytes").orEmpty()
            if (b64.isNotBlank()) {
                val mime = imgObj?.optString("mimeType")?.ifBlank { "image/png" } ?: "image/png"
                return b64 to mime
            }
        }

        // 3. Gemini candidates[0].content.parts[*].inlineData / inline_data
        val candidates = json.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val parts = candidates.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val inlineObj = part.optJSONObject("inlineData") ?: part.optJSONObject("inline_data")
                    if (inlineObj != null) {
                        val b64 = inlineObj.optString("data")
                        if (b64.isNotBlank()) {
                            val mime = inlineObj.optString("mimeType")
                                .ifBlank { inlineObj.optString("mime_type") }
                                .ifBlank { "image/png" }
                            return b64 to mime
                        }
                    }
                }
            }
        }

        // 4. Direct proxy fields
        val directB64 = json.optString("image")
            .ifBlank { json.optString("data") }
            .ifBlank { json.optString("b64_json") }
        if (directB64.isNotBlank()) {
            return directB64 to "image/png"
        }

        return null
    }
}
