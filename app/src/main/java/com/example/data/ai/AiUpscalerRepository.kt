package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.example.data.model.UpscaleEnhanceMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "AiUpscalerRepository"

class AiUpscalerRepository {

    companion object {
        private const val UPSCALER_ENDPOINT =
            "https://" + "paperly-ai.dark2242v2.workers.dev" + "?model=gemini-2.5-flash-image"
        private const val MAX_CLOUD_INPUT_EDGE_PX = 1024
        private const val BASE_RESTORATION_PROMPT =
            "Perform professional AI super-resolution and photo restoration on this exact image. " +
                "Eliminate all blur, pixelation, low-resolution softness, and JPEG compression artifacts. " +
                "Reconstruct hyper-sharp, natural micro-details in faces, eyes, eyelashes, hair strands, " +
                "skin texture, fabric, and crisp readable edges on any text or documents. " +
                "Keep the person's exact facial identity, pose, lighting, colors, background, and " +
                "composition 100% faithful to the original without altering or hallucinating new elements."
    }

    suspend fun restoreImage(
        sourceBitmap: Bitmap,
        mode: UpscaleEnhanceMode
    ): Bitmap = withTimeout(45_000L) {
        val requestJson = withContext(Dispatchers.Default) {
            buildRequestPayload(sourceBitmap, mode)
        }
        executeGenerateImage(requestJson.toString())
    }

    private fun buildRequestPayload(sourceBitmap: Bitmap, mode: UpscaleEnhanceMode): JSONObject {
        val srcMax = max(sourceBitmap.width, sourceBitmap.height)
        val encodedBitmap = if (srcMax > MAX_CLOUD_INPUT_EDGE_PX) {
            val scale = MAX_CLOUD_INPUT_EDGE_PX.toFloat() / srcMax.toFloat()
            val targetW = (sourceBitmap.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (sourceBitmap.height * scale).roundToInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(sourceBitmap, targetW, targetH, true)
        } else {
            sourceBitmap
        }

        val base64Image = try {
            val baos = ByteArrayOutputStream()
            encodedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        } finally {
            if (encodedBitmap !== sourceBitmap && !encodedBitmap.isRecycled) {
                encodedBitmap.recycle()
            }
        }

        val fullPrompt = "$BASE_RESTORATION_PROMPT\n\nTarget profile (${mode.label}): ${mode.promptAddon}"
        val partsArray = JSONArray()
            .put(
                JSONObject().put(
                    "inline_data",
                    JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", base64Image)
                )
            )
            .put(JSONObject().put("text", fullPrompt))

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
                    .put("responseModalities", JSONArray().put("IMAGE"))
            )
    }

    private suspend fun executeGenerateImage(requestBodyString: String): Bitmap =
        withContext(Dispatchers.IO) {
            val url = URL(UPSCALER_ENDPOINT)
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
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
                val responseStream = if (statusCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                val rawResponse = responseStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val trimmed = rawResponse.trimStart()

                if (statusCode !in 200..299) {
                    val errorMsg = if (trimmed.startsWith("{")) {
                        runCatching {
                            val errJson = JSONObject(trimmed)
                            errJson.optJSONObject("error")?.optString("message")
                                ?: errJson.optString("error")
                        }.getOrNull()
                    } else {
                        null
                    }
                    throw IllegalStateException(
                        errorMsg?.takeIf { it.isNotBlank() }
                            ?: "AI Upscaler returned HTTP $statusCode"
                    )
                }

                if (!trimmed.startsWith("{")) {
                    throw IllegalStateException("Unexpected non-JSON AI Upscaler response (HTTP $statusCode)")
                }

                val json = JSONObject(trimmed)
                val base64Bytes = extractImageBytes(json)
                    ?: throw IllegalStateException("AI Upscaler response did not include an image.")

                BitmapFactory.decodeByteArray(base64Bytes, 0, base64Bytes.size)
                    ?: throw IllegalStateException("Could not decode AI-restored image bytes.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: UnknownHostException) {
                Log.w(TAG, "Offline host unreachable during neural upscale", e)
                throw IllegalStateException("Offline: host unreachable", e)
            } catch (e: ConnectException) {
                Log.w(TAG, "Connection failed during neural upscale", e)
                throw IllegalStateException("Offline: connection failed", e)
            } catch (e: SocketTimeoutException) {
                Log.w(TAG, "Timeout during neural upscale", e)
                throw IllegalStateException("Neural upscale timed out", e)
            } catch (e: SSLException) {
                Log.w(TAG, "SSL error during neural upscale", e)
                throw IllegalStateException("SSL handshake failed", e)
            } catch (e: IOException) {
                Log.w(TAG, "Network IO error during neural upscale", e)
                throw IllegalStateException("Network error during neural upscale", e)
            } finally {
                connection.disconnect()
            }
        }

    private fun extractImageBytes(json: JSONObject): ByteArray? {
        val candidates = json.optJSONArray("candidates") ?: return null
        for (cIdx in 0 until candidates.length()) {
            val parts = candidates.optJSONObject(cIdx)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?: continue
            for (pIdx in 0 until parts.length()) {
                val part = parts.optJSONObject(pIdx) ?: continue
                val inlineObj = part.optJSONObject("inlineData")
                    ?: part.optJSONObject("inline_data")
                    ?: continue
                val b64 = inlineObj.optString("data").trim()
                if (b64.isNotEmpty()) {
                    val decoded = runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
                    if (decoded != null && decoded.isNotEmpty()) {
                        return decoded
                    }
                }
            }
        }
        return null
    }
}
