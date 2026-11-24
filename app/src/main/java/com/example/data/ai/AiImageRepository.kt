package com.example.data.ai

import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.model.AiImageAspectRatio
import com.example.data.model.AiImageStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlin.random.Random

private const val TAG = "AiImageRepository"

data class GeneratedAiImagePayload(
    val bytes: ByteArray,
    val mimeType: String,
    val modelUsed: String
)

class AiImageRepository {

    suspend fun generateImage(
        prompt: String,
        style: AiImageStyle,
        aspectRatio: AiImageAspectRatio
    ): GeneratedAiImagePayload = withTimeout(60_000L) {
        val cleanPrompt = prompt.trim()
        require(cleanPrompt.isNotEmpty()) { "Enter an image prompt first." }

        val styledPrompt = "$cleanPrompt, ${style.promptModifier}"
        val encodedPrompt = withContext(Dispatchers.IO) {
            URLEncoder.encode(styledPrompt, "UTF-8").replace("+", "%20")
        }

        val (width, height) = when (aspectRatio) {
            AiImageAspectRatio.SQUARE -> 1024 to 1024
            AiImageAspectRatio.LANDSCAPE -> 1280 to 720
            AiImageAspectRatio.PORTRAIT -> 720 to 1280
            AiImageAspectRatio.CLASSIC_PORTRAIT -> 896 to 1152
        }

        var lastError: Exception? = null
        repeat(2) { attempt ->
            val seed = Random.nextInt(100_000, 9_999_999)
            val endpointUrl =
                "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$height&nologo=true&seed=$seed"
            try {
                return@withTimeout downloadImagePayload(endpointUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Pollinations generation attempt ${attempt + 1} failed", e)
                if (attempt == 0) delay(700L)
            }
        }

        throw IllegalStateException(
            lastError?.localizedMessage
                ?: "Could not generate AI image right now. Please check your internet connection and try again."
        )
    }

    private suspend fun downloadImagePayload(endpointUrl: String): GeneratedAiImagePayload =
        withContext(Dispatchers.IO) {
            val url = URL(endpointUrl)
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 45_000
                setRequestProperty("User-Agent", "PaperlyAndroidApp/1.0")
                setRequestProperty("Accept", "image/jpeg,image/png,image/*")
            }

            try {
                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    throw IllegalStateException("AI image generator returned HTTP $statusCode. Please try again.")
                }

                val contentType = connection.contentType.orEmpty()
                val bytes = connection.inputStream.use { it.readBytes() }
                if (bytes.isEmpty()) {
                    throw IllegalStateException("Generated image download was empty.")
                }

                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                    throw IllegalStateException("Received invalid image data from generator.")
                }

                val detectedMime = when {
                    bounds.outMimeType?.contains("png", ignoreCase = true) == true ||
                        contentType.contains("png", ignoreCase = true) -> "image/png"
                    else -> "image/jpeg"
                }

                GeneratedAiImagePayload(
                    bytes = bytes,
                    mimeType = detectedMime,
                    modelUsed = "pollinations"
                )
            } finally {
                connection.disconnect()
            }
        }
}
