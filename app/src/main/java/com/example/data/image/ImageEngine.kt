package com.example.data.image

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.LruCache
import androidx.core.content.FileProvider
import com.example.data.local.PdfRecordEntity
import com.example.data.model.BgBrushStroke
import com.example.data.model.ImageOutputFormat
import com.example.data.model.OcrUiState
import com.example.data.model.PickedImageInfo
import com.example.data.model.ProcessedImageResult
import com.example.data.model.RecentImageItem
import com.example.data.model.UpscaleTarget
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ImageEngine(private val context: Context) {

    val imagesDir: File by lazy {
        File(context.filesDir, "images").apply { mkdirs() }
    }

    private val previewCache: LruCache<String, Bitmap> by lazy {
        val maxKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        val cacheKb = (maxKb / 10).coerceIn(8_192, 32_768)
        object : LruCache<String, Bitmap>(cacheKb) {
            override fun sizeOf(key: String, value: Bitmap): Int =
                (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
    }

    fun getCachedPreview(filePath: String, maxEdge: Int): Bitmap? {
        val mod = runCatching { File(filePath).lastModified() }.getOrDefault(0L)
        return previewCache.get("$filePath|$mod|$maxEdge")?.takeIf { !it.isRecycled }
    }

    suspend fun decodePreviewBitmap(filePath: String, maxEdge: Int = 1400): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) return@withContext null
            val key = "$filePath|${file.lastModified()}|$maxEdge"
            previewCache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }
            val bmp = decodeSampledFileBitmap(file, maxEdge) ?: return@withContext null
            previewCache.put(key, bmp)
            bmp
        }

    fun createCameraCaptureUri(): Pair<File, Uri> {
        val dir = File(context.cacheDir, "ocr_camera").apply { mkdirs() }
        val file = File(dir, "ocr_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return file to uri
    }

    suspend fun listRecentImages(limit: Int = 15): List<RecentImageItem> = withContext(Dispatchers.IO) {
        val files = imagesDir.listFiles { file ->
            file.isFile && file.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp")
        }.orEmpty()

        files.sortedByDescending { it.lastModified() }
            .take(limit)
            .mapNotNull { file ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, bounds)
                val w = bounds.outWidth
                val h = bounds.outHeight
                if (w <= 0 || h <= 0) return@mapNotNull null
                val fmt = when (file.extension.lowercase()) {
                    "png" -> ImageOutputFormat.PNG
                    "webp" -> ImageOutputFormat.WEBP
                    else -> ImageOutputFormat.JPG
                }
                RecentImageItem(
                    displayName = file.name,
                    filePath = file.absolutePath,
                    format = fmt,
                    width = w,
                    height = h,
                    sizeBytes = file.length(),
                    createdAtMillis = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
                )
            }
    }

    suspend fun importPickedImage(uri: Uri): PickedImageInfo = withContext(Dispatchers.IO) {
        val displayName = resolveDisplayName(uri) ?: "Image_${System.currentTimeMillis()}.jpg"
        val safeName = sanitizeFileName(displayName)
        val pickedDir = File(context.cacheDir, "picked_images").apply { mkdirs() }
        val tempFile = File(pickedDir, "img_${UUID.randomUUID()}_$safeName")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { out ->
                input.copyTo(out)
            }
        } ?: throw IllegalStateException("Unable to read selected image.")

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(tempFile.absolutePath, bounds)
        val orientation = readExifOrientation(tempFile)
        val swapDims = orientation == ExifInterface.ORIENTATION_ROTATE_90 ||
            orientation == ExifInterface.ORIENTATION_ROTATE_270 ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        val w = if (swapDims) bounds.outHeight else bounds.outWidth
        val h = if (swapDims) bounds.outWidth else bounds.outHeight
        require(w > 0 && h > 0) { "Selected file is not a valid image." }

        PickedImageInfo(
            uriString = uri.toString(),
            localPath = tempFile.absolutePath,
            displayName = displayName,
            width = w,
            height = h,
            sizeBytes = tempFile.length()
        )
    }

    suspend fun compressImageToTarget(
        info: PickedImageInfo,
        targetBytes: Long,
        format: ImageOutputFormat,
        onProgress: (Float, String) -> Unit
    ): ProcessedImageResult = withContext(Dispatchers.IO) {
        val srcFile = File(info.localPath)
        require(srcFile.exists()) { "Source image is unavailable." }
        val clampedTarget = targetBytes.coerceAtLeast(2_048L)

        onProgress(0.15f, "Decoding image...")
        val baseBitmap = decodeSampledFileBitmap(srcFile, 4096)
            ?: throw IllegalStateException("Could not decode image.")

        val compressFormat = when (format) {
            ImageOutputFormat.JPG -> Bitmap.CompressFormat.JPEG
            ImageOutputFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
            ImageOutputFormat.PNG -> Bitmap.CompressFormat.JPEG
        }

        val scaleSteps = floatArrayOf(1.0f, 0.85f, 0.70f, 0.55f, 0.42f, 0.30f, 0.22f, 0.15f, 0.10f, 0.07f)
        var bestBytes: ByteArray? = null
        var bestWidth = baseBitmap.width
        var bestHeight = baseBitmap.height

        try {
            for ((stepIndex, scale) in scaleSteps.withIndex()) {
                val p = 0.2f + 0.7f * ((stepIndex + 1).toFloat() / scaleSteps.size)
                onProgress(p, "Optimizing size (${(scale * 100).roundToInt()}% scale)...")

                val working = if (scale >= 0.999f) {
                    baseBitmap
                } else {
                    val targetW = (baseBitmap.width * scale).roundToInt().coerceAtLeast(32)
                    val targetH = (baseBitmap.height * scale).roundToInt().coerceAtLeast(32)
                    Bitmap.createScaledBitmap(baseBitmap, targetW, targetH, true)
                }

                try {
                    val maxQualityBytes = compressToByteArray(working, compressFormat, 95)
                    if (maxQualityBytes.size <= clampedTarget) {
                        bestBytes = maxQualityBytes
                        bestWidth = working.width
                        bestHeight = working.height
                        break
                    }

                    val minQualityBytes = compressToByteArray(working, compressFormat, 12)
                    if (minQualityBytes.size <= clampedTarget) {
                        // Binary search quality in [12..95]
                        var low = 12
                        var high = 95
                        var candidateBytes = minQualityBytes
                        while (low <= high) {
                            val mid = (low + high) ushr 1
                            val attempt = compressToByteArray(working, compressFormat, mid)
                            if (attempt.size <= clampedTarget) {
                                candidateBytes = attempt
                                low = mid + 1
                            } else {
                                high = mid - 1
                            }
                        }
                        bestBytes = candidateBytes
                        bestWidth = working.width
                        bestHeight = working.height
                        break
                    }

                    // Keep smallest seen so far as fallback
                    if (bestBytes == null || minQualityBytes.size < bestBytes!!.size) {
                        bestBytes = minQualityBytes
                        bestWidth = working.width
                        bestHeight = working.height
                    }
                } finally {
                    if (working !== baseBitmap) {
                        working.recycle()
                    }
                }
            }
        } finally {
            baseBitmap.recycle()
        }

        val finalBytes = bestBytes ?: throw IllegalStateException("Compression produced no output.")
        val baseTitle = info.displayName.substringBeforeLast('.')
        val outFile = uniqueImageOutputFile("${sanitizeFileName(baseTitle)}_compressed.${format.ext}")
        FileOutputStream(outFile).use { it.write(finalBytes) }

        onProgress(1f, "Compression complete")
        ProcessedImageResult(
            displayName = outFile.name,
            filePath = outFile.absolutePath,
            format = format,
            width = bestWidth,
            height = bestHeight,
            sizeBytes = outFile.length(),
            originalSizeBytes = info.sizeBytes,
            originalWidth = info.width,
            originalHeight = info.height,
            operationLabel = "Compressed Image",
            originalFilePath = info.localPath
        )
    }

    suspend fun cropRotateResize(
        info: PickedImageInfo,
        cropLeftNorm: Float,
        cropTopNorm: Float,
        cropRightNorm: Float,
        cropBottomNorm: Float,
        rotationDegrees: Int,
        flipHorizontal: Boolean,
        targetWidthPx: Int,
        targetHeightPx: Int,
        format: ImageOutputFormat,
        onProgress: (Float, String) -> Unit
    ): ProcessedImageResult = withContext(Dispatchers.IO) {
        val srcFile = File(info.localPath)
        require(srcFile.exists()) { "Source image is unavailable." }

        onProgress(0.2f, "Loading source image...")
        val decoded = decodeSampledFileBitmap(srcFile, 4096)
            ?: throw IllegalStateException("Unable to decode image.")

        val transformed = try {
            val normRot = ((rotationDegrees % 360) + 360) % 360
            if (normRot == 0 && !flipHorizontal) {
                decoded
            } else {
                val matrix = Matrix().apply {
                    if (normRot != 0) postRotate(normRot.toFloat())
                    if (flipHorizontal) postScale(-1f, 1f)
                }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                    if (it !== decoded) decoded.recycle()
                }
            }
        } catch (t: Throwable) {
            decoded.recycle()
            throw t
        }

        onProgress(0.5f, "Cropping & resizing...")
        val cropped = try {
            val l = (cropLeftNorm.coerceIn(0f, 0.95f) * transformed.width).roundToInt()
                .coerceIn(0, transformed.width - 1)
            val t = (cropTopNorm.coerceIn(0f, 0.95f) * transformed.height).roundToInt()
                .coerceIn(0, transformed.height - 1)
            val r = (cropRightNorm.coerceIn(cropLeftNorm + 0.05f, 1f) * transformed.width).roundToInt()
                .coerceIn(l + 1, transformed.width)
            val b = (cropBottomNorm.coerceIn(cropTopNorm + 0.05f, 1f) * transformed.height).roundToInt()
                .coerceIn(t + 1, transformed.height)
            val cw = (r - l).coerceAtLeast(1)
            val ch = (b - t).coerceAtLeast(1)
            if (l == 0 && t == 0 && cw == transformed.width && ch == transformed.height) {
                transformed
            } else {
                Bitmap.createBitmap(transformed, l, t, cw, ch).also {
                    if (it !== transformed) transformed.recycle()
                }
            }
        } catch (t: Throwable) {
            transformed.recycle()
            throw t
        }

        val safeOutW = targetWidthPx.coerceIn(16, 8192)
        val safeOutH = targetHeightPx.coerceIn(16, 8192)
        val finalBitmap = try {
            if (cropped.width == safeOutW && cropped.height == safeOutH) {
                cropped
            } else {
                Bitmap.createScaledBitmap(cropped, safeOutW, safeOutH, true).also {
                    if (it !== cropped) cropped.recycle()
                }
            }
        } catch (t: Throwable) {
            cropped.recycle()
            throw t
        }

        onProgress(0.85f, "Saving ${format.label}...")
        val baseTitle = info.displayName.substringBeforeLast('.')
        val outFile = uniqueImageOutputFile("${sanitizeFileName(baseTitle)}_edited.${format.ext}")
        try {
            writeBitmapToFile(finalBitmap, outFile, format, quality = 94)
        } finally {
            finalBitmap.recycle()
        }

        onProgress(1f, "Done")
        ProcessedImageResult(
            displayName = outFile.name,
            filePath = outFile.absolutePath,
            format = format,
            width = safeOutW,
            height = safeOutH,
            sizeBytes = outFile.length(),
            originalSizeBytes = info.sizeBytes,
            originalWidth = info.width,
            originalHeight = info.height,
            operationLabel = "Cropped & Resized",
            originalFilePath = info.localPath
        )
    }

    suspend fun extractTextOcr(imageUri: Uri): OcrUiState.Success = withContext(Dispatchers.IO) {
        val inputImage = InputImage.fromFilePath(context, imageUri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val result = recognizer.process(inputImage).awaitResult()
            val cleaned = result.text.trim()
            if (cleaned.isEmpty()) {
                throw IllegalStateException("No readable text detected in this image.")
            }
            val words = cleaned.split(Regex("\\s+")).count { it.isNotBlank() }
            OcrUiState.Success(
                text = cleaned,
                wordCount = words,
                charCount = cleaned.length
            )
        } finally {
            runCatching { recognizer.close() }
        }
    }

    suspend fun upscaleImage(
        info: PickedImageInfo,
        target: UpscaleTarget,
        format: ImageOutputFormat,
        onProgress: (Float, String) -> Unit
    ): ProcessedImageResult = withContext(Dispatchers.IO) {
        val srcFile = File(info.localPath)
        require(srcFile.exists()) { "Source image not found." }

        onProgress(0.12f, "Analyzing source resolution...")
        val source = decodeSampledFileBitmap(srcFile, 4096)
            ?: throw IllegalStateException("Could not decode image.")

        val srcW = source.width
        val srcH = source.height
        val aspect = srcW.toFloat() / max(1, srcH).toFloat()

        var desiredLongEdge = when (target) {
            UpscaleTarget.HD_2X -> (max(srcW, srcH) * 2f).roundToInt().coerceAtMost(target.maxEdgePx)
            UpscaleTarget.ULTRA_4X -> (max(srcW, srcH) * 4f).roundToInt().coerceAtMost(target.maxEdgePx)
            UpscaleTarget.RES_8K -> 7680
            UpscaleTarget.MAX_16K -> 15360
        }.coerceAtLeast(max(srcW, srcH))

        // Clamp safely against available JVM/heap memory (avoid OOM on lower-RAM devices)
        val maxHeapBytes = Runtime.getRuntime().maxMemory()
        val safePixelBudget = ((maxHeapBytes * 0.24) / 4.0).toLong().coerceIn(8_000_000L, 140_000_000L)
        var targetW = if (srcW >= srcH) desiredLongEdge else (desiredLongEdge * aspect).roundToInt()
        var targetH = if (srcH >= srcW) desiredLongEdge else (desiredLongEdge / aspect).roundToInt()
        val desiredPixels = targetW.toLong() * targetH.toLong()
        if (desiredPixels > safePixelBudget) {
            val scaleDown = kotlin.math.sqrt(safePixelBudget.toDouble() / desiredPixels.toDouble())
            targetW = (targetW * scaleDown).roundToInt().coerceAtLeast(srcW)
            targetH = (targetH * scaleDown).roundToInt().coerceAtLeast(srcH)
        }
        targetW = targetW.coerceIn(srcW, 15360)
        targetH = targetH.coerceIn(srcH, 15360)

        // Progressive multi-pass 2x scaling for crisp gradients & minimal ringing
        var current = source
        var pass = 1
        try {
            while (current.width < targetW || current.height < targetH) {
                val nextW = min(targetW, current.width * 2)
                val nextH = min(targetH, current.height * 2)
                onProgress(
                    (0.20f + pass * 0.18f).coerceAtMost(0.75f),
                    "Multi-pass HD scaling (${nextW}×${nextH})..."
                )
                val scaled = Bitmap.createScaledBitmap(current, nextW, nextH, true)
                if (current !== scaled) current.recycle()
                current = scaled
                pass++
                if (pass > 5) break
            }

            onProgress(0.80f, "Boosting clarity & fine edge detail...")
            val enhanced = applyMultiRadiusClarityEnhance(current)
            if (enhanced !== current) {
                current.recycle()
                current = enhanced
            }

            onProgress(0.92f, "Encoding ${format.label}...")
            val baseTitle = info.displayName.substringBeforeLast('.')
            val outFile = uniqueImageOutputFile("${sanitizeFileName(baseTitle)}_${target.name.lowercase()}.${format.ext}")
            writeBitmapToFile(current, outFile, format, quality = 96)

            onProgress(1f, "Upscale complete")
            ProcessedImageResult(
                displayName = outFile.name,
                filePath = outFile.absolutePath,
                format = format,
                width = current.width,
                height = current.height,
                sizeBytes = outFile.length(),
                originalSizeBytes = info.sizeBytes,
                originalWidth = info.width,
                originalHeight = info.height,
                operationLabel = "Upscaled ${target.label}",
                originalFilePath = info.localPath
            )
        } finally {
            if (!current.isRecycled) current.recycle()
        }
    }

    suspend fun removeBackground(
        info: PickedImageInfo,
        onProgress: (Float, String) -> Unit
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val srcFile = File(info.localPath)
        require(srcFile.exists()) { "Source image not found." }

        onProgress(0.25f, "Running ML Kit Subject Segmentation...")
        val source = decodeSampledFileBitmap(srcFile, 2048)
            ?: throw IllegalStateException("Could not decode image.")

        val options = SubjectSegmenterOptions.Builder()
            .enableForegroundBitmap()
            .build()
        val segmenter = SubjectSegmentation.getClient(options)
        try {
            val inputImage = InputImage.fromBitmap(source, 0)
            val result = segmenter.process(inputImage).awaitResult()
            val fg = result.foregroundBitmap
                ?: throw IllegalStateException("No clear foreground subject detected.")

            onProgress(0.85f, "Saving transparent cutout...")
            val outDir = File(context.cacheDir, "cutouts").apply { mkdirs() }
            val uid = UUID.randomUUID()
            val cutoutFile = File(outDir, "cutout_$uid.png")
            FileOutputStream(cutoutFile).use { out ->
                fg.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val alignedOrigFile = File(outDir, "orig_$uid.jpg")
            FileOutputStream(alignedOrigFile).use { out ->
                source.compress(Bitmap.CompressFormat.JPEG, 96, out)
            }
            if (fg !== source) fg.recycle()
            onProgress(1f, "Subject isolated")
            cutoutFile.absolutePath to alignedOrigFile.absolutePath
        } finally {
            source.recycle()
            runCatching { segmenter.close() }
        }
    }

    suspend fun exportCutoutResult(
        cutoutPngPath: String,
        alignedOriginalPath: String?,
        strokes: List<BgBrushStroke>,
        originalInfo: PickedImageInfo,
        backgroundColorArgb: Int?,
        format: ImageOutputFormat
    ): ProcessedImageResult = withContext(Dispatchers.IO) {
        val srcFile = File(cutoutPngPath)
        require(srcFile.exists()) { "Cutout bitmap not found." }
        val decodedFg = BitmapFactory.decodeFile(srcFile.absolutePath)
            ?: throw IllegalStateException("Could not decode cutout image.")

        val mutableFg = if (decodedFg.isMutable) {
            decodedFg
        } else {
            decodedFg.copy(Bitmap.Config.ARGB_8888, true).also { decodedFg.recycle() }
        }

        val alignedOrig = if (strokes.any { !it.isErase } && !alignedOriginalPath.isNullOrBlank()) {
            BitmapFactory.decodeFile(alignedOriginalPath)?.let { raw ->
                if (raw.width == mutableFg.width && raw.height == mutableFg.height) {
                    raw
                } else {
                    Bitmap.createScaledBitmap(raw, mutableFg.width, mutableFg.height, true).also {
                        if (it !== raw) raw.recycle()
                    }
                }
            }
        } else null

        try {
            if (strokes.isNotEmpty()) {
                val canvas = Canvas(mutableFg)
                val w = mutableFg.width.toFloat()
                val h = mutableFg.height.toFloat()
                val minSide = min(w, h)
                val restoreShader = alignedOrig?.let { orig ->
                    BitmapShader(orig, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
                }

                strokes.forEach { stroke ->
                    if (stroke.points.isEmpty()) return@forEach
                    val radiusPx = (stroke.radiusNorm * minSide).coerceAtLeast(3f)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = radiusPx * 2f
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                        if (stroke.isErase) {
                            xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                        } else if (restoreShader != null) {
                            shader = restoreShader
                        }
                    }
                    if (!stroke.isErase && restoreShader == null) return@forEach

                    if (stroke.points.size == 1) {
                        val pt = stroke.points[0]
                        paint.style = Paint.Style.FILL
                        canvas.drawCircle(pt.xNorm * w, pt.yNorm * h, radiusPx, paint)
                    } else {
                        val path = Path().apply {
                            val first = stroke.points.first()
                            moveTo(first.xNorm * w, first.yNorm * h)
                            for (i in 1 until stroke.points.size) {
                                val p = stroke.points[i]
                                lineTo(p.xNorm * w, p.yNorm * h)
                            }
                        }
                        canvas.drawPath(path, paint)
                    }
                }
            }
        } finally {
            alignedOrig?.recycle()
        }

        val effectiveFormat = if (backgroundColorArgb == null) ImageOutputFormat.PNG else format
        val outputBitmap = if (backgroundColorArgb == null) {
            mutableFg
        } else {
            val composed = Bitmap.createBitmap(mutableFg.width, mutableFg.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(composed)
            canvas.drawColor(backgroundColorArgb)
            canvas.drawBitmap(mutableFg, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            mutableFg.recycle()
            composed
        }

        val baseTitle = originalInfo.displayName.substringBeforeLast('.')
        val outFile = uniqueImageOutputFile("${sanitizeFileName(baseTitle)}_nobg.${effectiveFormat.ext}")
        try {
            writeBitmapToFile(outputBitmap, outFile, effectiveFormat, quality = 95)
            ProcessedImageResult(
                displayName = outFile.name,
                filePath = outFile.absolutePath,
                format = effectiveFormat,
                width = outputBitmap.width,
                height = outputBitmap.height,
                sizeBytes = outFile.length(),
                originalSizeBytes = originalInfo.sizeBytes,
                originalWidth = originalInfo.width,
                originalHeight = originalInfo.height,
                operationLabel = if (backgroundColorArgb == null) "Transparent PNG" else "Background Replaced",
                originalFilePath = originalInfo.localPath
            )
        } finally {
            outputBitmap.recycle()
        }
    }

    suspend fun generatePassportSheet(
        sourceOrCutoutPath: String,
        originalInfo: PickedImageInfo,
        removeBgEnabled: Boolean,
        backgroundColorArgb: Int,
        copyCount: Int,
        exportAsPdf: Boolean,
        zoom: Float,
        offsetXNorm: Float,
        offsetYNorm: Float,
        pdfsDir: File,
        onProgress: (Float, String) -> Unit
    ): Pair<ProcessedImageResult, PdfRecordEntity?> = withContext(Dispatchers.IO) {
        val srcFile = File(sourceOrCutoutPath)
        require(srcFile.exists()) { "Portrait image is unavailable." }
        onProgress(0.2f, "Preparing 35×45mm passport layout...")

        val rawPortrait = decodeSampledFileBitmap(srcFile, 1600)
            ?: throw IllegalStateException("Could not decode portrait image.")

        val tileW = 413 // 35mm @ 300 DPI
        val tileH = 531 // 45mm @ 300 DPI
        val (sheetW, sheetH, cols) = when (copyCount) {
            4 -> Triple(1800, 1200, 2)
            6 -> Triple(1800, 1200, 3)
            8 -> Triple(1800, 1200, 4)
            else -> Triple(1800, 2400, 4)
        }
        val rows = ((copyCount + cols - 1) / cols).coerceAtLeast(1)

        val tile = createPassportTileBitmap(
            source = rawPortrait,
            tileW = tileW,
            tileH = tileH,
            fillBg = removeBgEnabled,
            bgColorArgb = backgroundColorArgb,
            zoom = zoom,
            offsetXNorm = offsetXNorm,
            offsetYNorm = offsetYNorm
        )
        rawPortrait.recycle()

        val sheet = Bitmap.createBitmap(sheetW, sheetH, Bitmap.Config.ARGB_8888)
        try {
            onProgress(0.55f, "Placing $copyCount passport photos with cut guides...")
            val canvas = Canvas(sheet)
            canvas.drawColor(Color.WHITE)

            val gap = 28
            val gridW = cols * tileW + (cols - 1) * gap
            val gridH = rows * tileH + (rows - 1) * gap
            val startX = ((sheetW - gridW) / 2f).coerceAtLeast(16f)
            val startY = ((sheetH - gridH) / 2f).coerceAtLeast(16f)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#CBD5E1")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            for (idx in 0 until copyCount) {
                val r = idx / cols
                val c = idx % cols
                val left = startX + c * (tileW + gap)
                val top = startY + r * (tileH + gap)
                canvas.drawBitmap(tile, left, top, bitmapPaint)
                canvas.drawRect(left, top, left + tileW, top + tileH, borderPaint)
            }
            tile.recycle()

            onProgress(0.82f, "Generating print-ready output...")
            val jpgFile = uniqueImageOutputFile("Paperly_Passport_${copyCount}x.jpg")
            writeBitmapToFile(sheet, jpgFile, ImageOutputFormat.JPG, quality = 95)

            var pdfRecord: PdfRecordEntity? = null
            if (exportAsPdf) {
                pdfsDir.mkdirs()
                val pdfFile = File(pdfsDir, "Paperly_Passport_${copyCount}x_${System.currentTimeMillis()}.pdf")
                saveBitmapAsSinglePagePdf(sheet, pdfFile)
                pdfRecord = PdfRecordEntity(
                    displayName = pdfFile.name,
                    filePath = pdfFile.absolutePath,
                    pageCount = 1,
                    sizeBytes = pdfFile.length(),
                    originalSizeBytes = originalInfo.sizeBytes,
                    operationTag = "Scanned"
                )
            }

            onProgress(1f, "Passport sheet ready")
            val imageResult = ProcessedImageResult(
                displayName = jpgFile.name,
                filePath = jpgFile.absolutePath,
                format = ImageOutputFormat.JPG,
                width = sheet.width,
                height = sheet.height,
                sizeBytes = jpgFile.length(),
                originalSizeBytes = originalInfo.sizeBytes,
                originalWidth = originalInfo.width,
                originalHeight = originalInfo.height,
                operationLabel = "Passport Sheet ($copyCount copies)",
                secondaryPdfRecordId = null,
                originalFilePath = originalInfo.localPath
            )
            imageResult to pdfRecord
        } finally {
            if (!tile.isRecycled) tile.recycle()
            if (!sheet.isRecycled) sheet.recycle()
        }
    }

    suspend fun saveImageToGallery(filePath: String, displayName: String, format: ImageOutputFormat): Uri =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            require(file.exists()) { "Image file no longer exists." }

            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, format.mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/Paperly"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Could not create Gallery entry.")

            try {
                resolver.openOutputStream(uri)?.use { out ->
                    file.inputStream().use { input -> input.copyTo(out) }
                } ?: throw IllegalStateException("Could not write image to Gallery.")

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                }
                uri
            } catch (t: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw t
            }
        }

    suspend fun exportImageFileToPdf(
        imageFilePath: String,
        displayName: String,
        pdfsDir: File
    ): PdfRecordEntity = withContext(Dispatchers.IO) {
        val imgFile = File(imageFilePath)
        require(imgFile.exists()) { "Image file not found." }
        val bmp = decodeSampledFileBitmap(imgFile, 3200)
            ?: throw IllegalStateException("Could not decode image for PDF export.")

        pdfsDir.mkdirs()
        val base = sanitizeFileName(displayName.substringBeforeLast('.')).ifBlank { "Paperly_Image" }
        val pdfFile = File(pdfsDir, "${base}_${System.currentTimeMillis()}.pdf")
        try {
            saveBitmapAsSinglePagePdf(bmp, pdfFile)
            PdfRecordEntity(
                displayName = pdfFile.name,
                filePath = pdfFile.absolutePath,
                pageCount = 1,
                sizeBytes = pdfFile.length(),
                originalSizeBytes = imgFile.length(),
                operationTag = "Scanned"
            )
        } finally {
            bmp.recycle()
        }
    }

    suspend fun saveGeneratedAiBytes(bytes: ByteArray, mimeType: String, prompt: String): ProcessedImageResult =
        withContext(Dispatchers.IO) {
            val isPng = mimeType.contains("png", ignoreCase = true)
            val format = if (isPng) ImageOutputFormat.PNG else ImageOutputFormat.JPG
            val slug = sanitizeFileName(prompt.take(28)).ifBlank { "AI_Art" }
            val outFile = uniqueImageOutputFile("${slug}_ai.${format.ext}")
            FileOutputStream(outFile).use { it.write(bytes) }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(outFile.absolutePath, bounds)
            val w = bounds.outWidth.coerceAtLeast(1)
            val h = bounds.outHeight.coerceAtLeast(1)

            ProcessedImageResult(
                displayName = outFile.name,
                filePath = outFile.absolutePath,
                format = format,
                width = w,
                height = h,
                sizeBytes = outFile.length(),
                originalSizeBytes = null,
                originalWidth = null,
                originalHeight = null,
                operationLabel = "AI Generated"
            )
        }

    fun getShareUri(filePath: String): Uri {
        val file = File(filePath)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun saveBitmapAsSinglePagePdf(bitmap: Bitmap, outFile: File) {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val pageRect = PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat())
            val page = PDPage(pageRect)
            doc.addPage(page)
            val pdImage = if (bitmap.hasAlpha()) {
                LosslessFactory.createFromImage(doc, bitmap)
            } else {
                JPEGFactory.createFromImage(doc, bitmap, 0.92f)
            }
            PDPageContentStream(doc, page).use { stream ->
                stream.drawImage(pdImage, 0f, 0f, pageRect.width, pageRect.height)
            }
            doc.save(outFile)
        }
    }

    private fun createPassportTileBitmap(
        source: Bitmap,
        tileW: Int,
        tileH: Int,
        fillBg: Boolean,
        bgColorArgb: Int,
        zoom: Float = 1.0f,
        offsetXNorm: Float = 0f,
        offsetYNorm: Float = 0f
    ): Bitmap {
        val tile = Bitmap.createBitmap(tileW, tileH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(tile)
        if (fillBg) {
            canvas.drawColor(bgColorArgb)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        val baseScale = max(tileW.toFloat() / max(1, source.width), tileH.toFloat() / max(1, source.height))
        val effectiveScale = baseScale * zoom.coerceIn(0.6f, 3.5f)
        val drawW = source.width * effectiveScale
        val drawH = source.height * effectiveScale
        val left = (tileW - drawW) / 2f + offsetXNorm * tileW
        val top = (tileH - drawH) * 0.25f + offsetYNorm * tileH
        val destRect = RectF(left, top, left + drawW, top + drawH)
        canvas.drawBitmap(source, null, destRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return tile
    }

    private fun applyMultiRadiusClarityEnhance(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val totalPixels = w.toLong() * h.toLong()
        if (w < 4 || h < 4) return src
        // If ultra-large 16K exceeds fast pixel pass budget, downscale-sharpen-blend or run lightweight 3x3
        val useMultiRadius = totalPixels <= 38_000_000L
        if (totalPixels > 120_000_000L) return src

        return runCatching {
            val rowPrev2 = IntArray(w)
            val rowPrev1 = IntArray(w)
            val rowCurr = IntArray(w)
            val rowNext1 = IntArray(w)
            val rowNext2 = IntArray(w)
            val rowOut = IntArray(w)
            val out = Bitmap.createBitmap(w, h, src.config ?: Bitmap.Config.ARGB_8888)

            src.getPixels(rowPrev2, 0, w, 0, 0, w, 1)
            src.getPixels(rowPrev1, 0, w, 0, 1, w, 1)
            out.setPixels(rowPrev2, 0, w, 0, 0, w, 1)
            out.setPixels(rowPrev1, 0, w, 0, 1, w, 1)

            src.getPixels(rowCurr, 0, w, 0, 2, w, 1)
            src.getPixels(rowNext1, 0, w, 0, 3, w, 1)

            for (y in 2 until h - 2) {
                src.getPixels(rowNext2, 0, w, 0, y + 2, w, 1)
                rowOut[0] = rowCurr[0]
                rowOut[1] = rowCurr[1]
                rowOut[w - 2] = rowCurr[w - 2]
                rowOut[w - 1] = rowCurr[w - 1]

                for (x in 2 until w - 2) {
                    val c = rowCurr[x]
                    val a = c ushr 24
                    val rC = (c ushr 16) and 0xFF
                    val gC = (c ushr 8) and 0xFF
                    val bC = c and 0xFF

                    val top1 = rowPrev1[x]
                    val bot1 = rowNext1[x]
                    val lft1 = rowCurr[x - 1]
                    val rgt1 = rowCurr[x + 1]

                    val rFineAvg = (((top1 ushr 16) and 0xFF) + ((bot1 ushr 16) and 0xFF) +
                        ((lft1 ushr 16) and 0xFF) + ((rgt1 ushr 16) and 0xFF)) shr 2
                    val gFineAvg = (((top1 ushr 8) and 0xFF) + ((bot1 ushr 8) and 0xFF) +
                        ((lft1 ushr 8) and 0xFF) + ((rgt1 ushr 8) and 0xFF)) shr 2
                    val bFineAvg = ((top1 and 0xFF) + (bot1 and 0xFF) + (lft1 and 0xFF) + (rgt1 and 0xFF)) shr 2

                    val rDiffFine = rC - rFineAvg
                    val gDiffFine = gC - gFineAvg
                    val bDiffFine = bC - bFineAvg

                    var rDelta: Int
                    var gDelta: Int
                    var bDelta: Int

                    if (useMultiRadius) {
                        val top2 = rowPrev2[x]
                        val bot2 = rowNext2[x]
                        val lft2 = rowCurr[x - 2]
                        val rgt2 = rowCurr[x + 2]
                        val rCoarseAvg = (((top2 ushr 16) and 0xFF) + ((bot2 ushr 16) and 0xFF) +
                            ((lft2 ushr 16) and 0xFF) + ((rgt2 ushr 16) and 0xFF)) shr 2
                        val gCoarseAvg = (((top2 ushr 8) and 0xFF) + ((bot2 ushr 8) and 0xFF) +
                            ((lft2 ushr 8) and 0xFF) + ((rgt2 ushr 8) and 0xFF)) shr 2
                        val bCoarseAvg = ((top2 and 0xFF) + (bot2 and 0xFF) + (lft2 and 0xFF) + (rgt2 and 0xFF)) shr 2

                        val rDiffCoarse = rC - rCoarseAvg
                        val gDiffCoarse = gC - gCoarseAvg
                        val bDiffCoarse = bC - bCoarseAvg

                        // Coring: smooth tiny flat compression noise; strongly sharpen real detail & structure
                        rDelta = if (kotlin.math.abs(rDiffFine) < 3) -(rDiffFine / 3) else ((rDiffFine * 85 + rDiffCoarse * 40) / 100)
                        gDelta = if (kotlin.math.abs(gDiffFine) < 3) -(gDiffFine / 3) else ((gDiffFine * 85 + gDiffCoarse * 40) / 100)
                        bDelta = if (kotlin.math.abs(bDiffFine) < 3) -(bDiffFine / 3) else ((bDiffFine * 85 + bDiffCoarse * 40) / 100)
                    } else {
                        rDelta = if (kotlin.math.abs(rDiffFine) < 3) 0 else ((rDiffFine * 85) / 100)
                        gDelta = if (kotlin.math.abs(gDiffFine) < 3) 0 else ((gDiffFine * 85) / 100)
                        bDelta = if (kotlin.math.abs(bDiffFine) < 3) 0 else ((bDiffFine * 85) / 100)
                    }

                    val rNew = (rC + rDelta.coerceIn(-48, 48)).coerceIn(0, 255)
                    val gNew = (gC + gDelta.coerceIn(-48, 48)).coerceIn(0, 255)
                    val bNew = (bC + bDelta.coerceIn(-48, 48)).coerceIn(0, 255)
                    rowOut[x] = (a shl 24) or (rNew shl 16) or (gNew shl 8) or bNew
                }

                out.setPixels(rowOut, 0, w, 0, y, w, 1)
                System.arraycopy(rowPrev1, 0, rowPrev2, 0, w)
                System.arraycopy(rowCurr, 0, rowPrev1, 0, w)
                System.arraycopy(rowNext1, 0, rowCurr, 0, w)
                System.arraycopy(rowNext2, 0, rowNext1, 0, w)
            }
            out.setPixels(rowCurr, 0, w, 0, h - 2, w, 1)
            out.setPixels(rowNext1, 0, w, 0, h - 1, w, 1)
            out
        }.getOrElse { src }
    }

    private fun compressToByteArray(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(format, quality.coerceIn(0, 100), baos)
        return baos.toByteArray()
    }

    private fun writeBitmapToFile(bitmap: Bitmap, file: File, format: ImageOutputFormat, quality: Int) {
        val androidFormat = when (format) {
            ImageOutputFormat.JPG -> Bitmap.CompressFormat.JPEG
            ImageOutputFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageOutputFormat.WEBP -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSY
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
            }
        }
        FileOutputStream(file).use { out ->
            bitmap.compress(androidFormat, if (format == ImageOutputFormat.PNG) 100 else quality, out)
        }
    }

    private fun decodeSampledFileBitmap(file: File, maxDimension: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        var sampleSize = 1
        while ((boundsOpts.outWidth / sampleSize) > maxDimension ||
            (boundsOpts.outHeight / sampleSize) > maxDimension
        ) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return null

        val orientation = readExifOrientation(file)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
        }
        if (matrix.isIdentity) return decoded
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private fun readExifOrientation(file: File): Int = runCatching {
        ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun uniqueImageOutputFile(desiredName: String): File {
        var candidate = File(imagesDir, desiredName)
        if (!candidate.exists()) return candidate
        val stem = desiredName.substringBeforeLast('.')
        val ext = desiredName.substringAfterLast('.', "jpg")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(imagesDir, "${stem}_$counter.$ext")
            counter++
        }
        return candidate
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .ifBlank { "Paperly_Image" }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { error ->
            if (cont.isActive) cont.resumeWithException(error)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
}
