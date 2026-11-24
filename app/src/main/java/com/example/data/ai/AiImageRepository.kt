package com.example.data.ai

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.data.model.AiImageAspectRatio
import com.example.data.model.AiImageStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection
import kotlin.random.Random

private const val TAG = "AiImageRepository"
private const val WATERMARK_CROP_BOTTOM_PX = 48

data class GeneratedAiImagePayload(
    val bytes: ByteArray,
    val mimeType: String,
    val modelUsed: String
)

private class PollinationsHttpException(
    val statusCode: Int,
    message: String
) : IllegalStateException(message)

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
        val requestHeight = height + WATERMARK_CROP_BOTTOM_PX
        val seed = Random.nextInt(100_000, 9_999_999)

        val primaryUrl =
            "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$requestHeight&model=flux&nologo=true&seed=$seed"

        try {
            return@withTimeout downloadImagePayload(primaryUrl, modelName = "flux")
        } catch (e: CancellationException) {
            throw e
        } catch (e: PollinationsHttpException) {
            Log.w(TAG, "Pollinations flux failed with HTTP ${e.statusCode}, retrying with turbo", e)
            if (e.statusCode in setOf(402, 429, 503)) {
                delay(450L)
                val fallbackUrl =
                    "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$requestHeight&model=turbo&nologo=true&seed=$seed"
                try {
                    return@withTimeout downloadImagePayload(fallbackUrl, modelName = "turbo")
                } catch (ce: CancellationException) {
                    throw ce
                } catch (fallbackErr: PollinationsHttpException) {
                    Log.w(TAG, "Pollinations turbo fallback failed with HTTP ${fallbackErr.statusCode}", fallbackErr)
                    if (fallbackErr.statusCode in setOf(402, 429, 503)) {
                        throw IllegalStateException("AI image server is busy right now — please tap Generate again in a moment")
                    }
                    throw fallbackErr
                } catch (fallbackErr: Exception) {
                    Log.w(TAG, "Pollinations turbo fallback failed", fallbackErr)
                    throw IllegalStateException("AI image server is busy right now — please tap Generate again in a moment")
                }
            }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Pollinations flux attempt failed, retrying with turbo", e)
            delay(500L)
            val fallbackUrl =
                "https://image.pollinations.ai/prompt/$encodedPrompt?width=$width&height=$requestHeight&model=turbo&nologo=true&seed=$seed"
            try {
                return@withTimeout downloadImagePayload(fallbackUrl, modelName = "turbo")
            } catch (ce: CancellationException) {
                throw ce
            } catch (fallbackErr: PollinationsHttpException) {
                if (fallbackErr.statusCode in setOf(402, 429, 503)) {
                    throw IllegalStateException("AI image server is busy right now — please tap Generate again in a moment")
                }
                throw fallbackErr
            } catch (fallbackErr: Exception) {
                throw IllegalStateException(
                    fallbackErr.localizedMessage
                        ?: "AI image server is busy right now — please tap Generate again in a moment"
                )
            }
        }
    }

    private suspend fun downloadImagePayload(
        endpointUrl: String,
        modelName: String
    ): GeneratedAiImagePayload =
        withContext(Dispatchers.IO) {
            val url = URL(endpointUrl)
            val connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 45_000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"
                )
                setRequestProperty("Referer", "https://paperly.app")
                setRequestProperty("Accept", "image/jpeg,image/png,image/webp,image/*;q=0.8")
            }

            try {
                val statusCode = connection.responseCode
                if (statusCode !in 200..299) {
                    throw PollinationsHttpException(
                        statusCode = statusCode,
                        message = "AI image generator returned HTTP $statusCode. Please try again."
                    )
                }

                val contentType = connection.contentType.orEmpty()
                val rawBytes = connection.inputStream.use { it.readBytes() }
                if (rawBytes.isEmpty()) {
                    throw IllegalStateException("Generated image download was empty.")
                }

                val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                    ?: throw IllegalStateException("Received invalid image data from generator.")

                val isPng = contentType.contains("png", ignoreCase = true)
                val detectedMime = if (isPng) "image/png" else "image/jpeg"

                val finalBytes = try {
                    if (decoded.height > WATERMARK_CROP_BOTTOM_PX + 64) {
                        val croppedHeight = decoded.height - WATERMARK_CROP_BOTTOM_PX
                        val cropped = Bitmap.createBitmap(decoded, 0, 0, decoded.width, croppedHeight)
                        try {
                            val out = ByteArrayOutputStream()
                            val fmt = if (isPng) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
                            cropped.compress(fmt, if (isPng) 100 else 95, out)
                            out.toByteArray()
                        } finally {
                            if (cropped !== decoded && !cropped.isRecycled) {
                                cropped.recycle()
                            }
                        }
                    } else {
                        rawBytes
                    }
                } finally {
                    if (!decoded.isRecycled) {
                        decoded.recycle()
                    }
                }

                GeneratedAiImagePayload(
                    bytes = finalBytes,
                    mimeType = detectedMime,
                    modelUsed = "pollinations-$modelName"
                )
            } finally {
                connection.disconnect()
            }
        }
}
