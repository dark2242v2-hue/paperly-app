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
import com.example.data.ai.AiUpscalerRepository
import com.example.data.local.PdfRecordEntity
import com.example.data.model.BatchCompressExportMode
import com.example.data.model.BgBrushStroke
import com.example.data.model.ImageOutputFormat
import com.example.data.model.OcrUiState
import com.example.data.model.PassportSizePreset
import com.example.data.model.PickedImageInfo
import com.example.data.model.ProcessedImageResult
import com.example.data.model.RecentImageItem
import com.example.data.model.UpscaleEnhanceMode
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class ImageEngine(private val context: Context) {

    private val aiUpscalerRepository = AiUpscalerRepository()

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

    private val latestPreviewKeyByTarget = ConcurrentHashMap<String, String>()

    fun getCachedPreview(filePath: String, maxEdge: Int): Bitmap? {
        val key = latestPreviewKeyByTarget["$filePath|$maxEdge"] ?: return null
        return previewCache.get(key)?.takeIf { !it.isRecycled }
    }

    suspend fun decodePreviewBitmap(filePath: String, maxEdge: Int = 1400): Bitmap? =
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) return@withContext null
            val targetKey = "$filePath|$maxEdge"
            val key = "$filePath|${file.lastModified()}|$maxEdge"
            latestPreviewKeyByTarget[targetKey] = key
            previewCache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }
            val bmp = decodeSampledFileBitmap(file, maxEdge) ?: return@withContext null
            previewCache.put(key, bmp)
            latestPreviewKeyByTarget[targetKey] = key
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

        val copied = runCatching {
            openUriInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { out ->
                    input.copyTo(out)
                }
            }
        }.getOrNull()
        if (copied == null || !tempFile.exists() || tempFile.length() <= 0L) {
            runCatching { tempFile.delete() }
            throw IllegalStateException("Unable to read selected image.")
        }

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
                currentCoroutineContext().ensureActive()
                val p = 0.2f + 0.7f * ((stepIndex + 1).toFloat() / scaleSteps.size)
                onProgress(p, "Encoding pass ${stepIndex + 1} of ${scaleSteps.size} (${(scale * 100).roundToInt()}%)...")

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

    suspend fun batchCompressImages(
        items: List<PickedImageInfo>,
        targetBytesPerImage: Long,
        format: ImageOutputFormat,
        exportMode: BatchCompressExportMode,
        pdfsDir: File,
        onProgress: (Float, String) -> Unit
    ): Pair<ProcessedImageResult, PdfRecordEntity?> = withContext(Dispatchers.Default) {
        require(items.isNotEmpty()) { "Select at least 1 image for batch compression." }
        val clampedTarget = targetBytesPerImage.coerceAtLeast(2_048L)
        val total = items.size
        val completedCount = AtomicInteger(0)
        val semaphore = Semaphore(permits = 3)

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
        val outputFormatActual = if (format == ImageOutputFormat.PNG) ImageOutputFormat.JPG else format

        data class BatchCompressedItem(
            val index: Int,
            val fileName: String,
            val bytes: ByteArray,
            val width: Int,
            val height: Int
        )

        onProgress(0.06f, "Compressing $total images in parallel...")
        val compressedList = coroutineScope {
            items.mapIndexed { idx, info ->
                async(Dispatchers.Default) {
                    semaphore.withPermit {
                        currentCoroutineContext().ensureActive()
                        val srcFile = File(info.localPath)
                        require(srcFile.exists()) { "Source image missing: ${info.displayName}" }
                        val baseBitmap = decodeSampledFileBitmap(srcFile, 3072)
                            ?: throw IllegalStateException("Could not decode ${info.displayName}.")
                        val scaleSteps = floatArrayOf(1.0f, 0.85f, 0.70f, 0.55f, 0.40f, 0.28f, 0.18f, 0.12f)
                        var bestBytes: ByteArray? = null
                        var bestW = baseBitmap.width
                        var bestH = baseBitmap.height
                        try {
                            for (scale in scaleSteps) {
                                currentCoroutineContext().ensureActive()
                                val working = if (scale >= 0.999f) {
                                    baseBitmap
                                } else {
                                    val tw = (baseBitmap.width * scale).roundToInt().coerceAtLeast(32)
                                    val th = (baseBitmap.height * scale).roundToInt().coerceAtLeast(32)
                                    Bitmap.createScaledBitmap(baseBitmap, tw, th, true)
                                }
                                try {
                                    val hi = compressToByteArray(working, compressFormat, 92)
                                    if (hi.size <= clampedTarget) {
                                        bestBytes = hi
                                        bestW = working.width
                                        bestH = working.height
                                        break
                                    }
                                    val lo = compressToByteArray(working, compressFormat, 14)
                                    if (lo.size <= clampedTarget) {
                                        var qLo = 14
                                        var qHi = 92
                                        var chosen = lo
                                        while (qLo <= qHi) {
                                            val mid = (qLo + qHi) ushr 1
                                            val attempt = compressToByteArray(working, compressFormat, mid)
                                            if (attempt.size <= clampedTarget) {
                                                chosen = attempt
                                                qLo = mid + 1
                                            } else {
                                                qHi = mid - 1
                                            }
                                        }
                                        bestBytes = chosen
                                        bestW = working.width
                                        bestH = working.height
                                        break
                                    }
                                    if (bestBytes == null || lo.size < bestBytes!!.size) {
                                        bestBytes = lo
                                        bestW = working.width
                                        bestH = working.height
                                    }
                                } finally {
                                    if (working !== baseBitmap) working.recycle()
                                }
                            }
                        } finally {
                            baseBitmap.recycle()
                        }
                        val done = completedCount.incrementAndGet()
                        onProgress(
                            0.10f + 0.65f * (done.toFloat() / total.toFloat()),
                            "Compressed $done of $total images..."
                        )
                        val stem = sanitizeFileName(info.displayName.substringBeforeLast('.'))
                        BatchCompressedItem(
                            index = idx,
                            fileName = "${stem}_${idx + 1}_compressed.${outputFormatActual.ext}",
                            bytes = bestBytes ?: throw IllegalStateException("Compression failed for ${info.displayName}"),
                            width = bestW,
                            height = bestH
                        )
                    }
                }
            }.awaitAll().sortedBy { it.index }
        }

        val totalOriginalBytes = items.sumOf { it.sizeBytes }
        val firstItem = compressedList.first()
        val previewFile = withContext(Dispatchers.IO) {
            val f = uniqueImageOutputFile("Batch_Preview_${System.currentTimeMillis()}.${outputFormatActual.ext}")
            FileOutputStream(f).use { it.write(firstItem.bytes) }
            f
        }

        when (exportMode) {
            BatchCompressExportMode.SINGLE_PDF -> {
                onProgress(0.82f, "Building combined PDF (${compressedList.size} pages)...")
                val pdfRecord = withContext(Dispatchers.IO) {
                    pdfsDir.mkdirs()
                    PDFBoxResourceLoader.init(context.applicationContext)
                    var pdfOut = File(pdfsDir, "Paperly_Batch_Compressed_${System.currentTimeMillis()}.pdf")
                    var suffix = 2
                    while (pdfOut.exists()) {
                        pdfOut = File(pdfsDir, "Paperly_Batch_Compressed_${System.currentTimeMillis()}_$suffix.pdf")
                        suffix++
                    }
                    PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                        compressedList.forEach { entry ->
                            val bmp = BitmapFactory.decodeByteArray(entry.bytes, 0, entry.bytes.size)
                                ?: throw IllegalStateException("Failed to decode compressed image.")
                            try {
                                val isLandscape = bmp.width > bmp.height
                                val pageRect = if (isLandscape) {
                                    PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
                                } else {
                                    PDRectangle.A4
                                }
                                val pdPage = PDPage(pageRect)
                                doc.addPage(pdPage)
                                val margin = 20f
                                val availW = pageRect.width - margin * 2f
                                val availH = pageRect.height - margin * 2f
                                val scale = min(availW / bmp.width.toFloat(), availH / bmp.height.toFloat())
                                val drawW = bmp.width * scale
                                val drawH = bmp.height * scale
                                val drawX = (pageRect.width - drawW) / 2f
                                val drawY = (pageRect.height - drawH) / 2f
                                val pdImg = JPEGFactory.createFromStream(doc, ByteArrayInputStream(entry.bytes))
                                PDPageContentStream(doc, pdPage).use { cs ->
                                    cs.drawImage(pdImg, drawX, drawY, drawW, drawH)
                                }
                            } finally {
                                bmp.recycle()
                            }
                        }
                        doc.save(pdfOut)
                    }
                    PdfRecordEntity(
                        displayName = pdfOut.name,
                        filePath = pdfOut.absolutePath,
                        pageCount = compressedList.size,
                        sizeBytes = pdfOut.length(),
                        originalSizeBytes = totalOriginalBytes,
                        operationTag = "Batch PDF"
                    )
                }
                onProgress(1f, "Batch PDF created")
                val result = ProcessedImageResult(
                    displayName = pdfRecord.displayName,
                    filePath = previewFile.absolutePath,
                    format = outputFormatActual,
                    width = firstItem.width,
                    height = firstItem.height,
                    sizeBytes = pdfRecord.sizeBytes,
                    originalSizeBytes = totalOriginalBytes,
                    originalWidth = items.first().width,
                    originalHeight = items.first().height,
                    operationLabel = "Batch Compressed PDF (${compressedList.size} imgs)",
                    batchExportFilePath = pdfRecord.filePath,
                    batchExportMimeType = "application/pdf",
                    batchFileCount = compressedList.size
                )
                result to pdfRecord
            }

            BatchCompressExportMode.ZIP_ARCHIVE -> {
                onProgress(0.84f, "Creating ZIP archive...")
                val zipFile = withContext(Dispatchers.IO) {
                    val outZip = uniqueImageOutputFile("Paperly_Batch_Compressed_${System.currentTimeMillis()}.zip")
                    val usedNames = HashSet<String>()
                    ZipOutputStream(FileOutputStream(outZip)).use { zos ->
                        compressedList.forEach { entry ->
                            var entryName = entry.fileName
                            var n = 2
                            while (!usedNames.add(entryName)) {
                                val stem = entry.fileName.substringBeforeLast('.')
                                val ext = entry.fileName.substringAfterLast('.')
                                entryName = "${stem}_$n.$ext"
                                n++
                            }
                            zos.putNextEntry(ZipEntry(entryName))
                            zos.write(entry.bytes)
                            zos.closeEntry()
                        }
                    }
                    outZip
                }
                onProgress(1f, "ZIP archive created")
                val result = ProcessedImageResult(
                    displayName = zipFile.name,
                    filePath = previewFile.absolutePath,
                    format = outputFormatActual,
                    width = firstItem.width,
                    height = firstItem.height,
                    sizeBytes = zipFile.length(),
                    originalSizeBytes = totalOriginalBytes,
                    originalWidth = items.first().width,
                    originalHeight = items.first().height,
                    operationLabel = "Batch Compressed ZIP (${compressedList.size} imgs)",
                    batchExportFilePath = zipFile.absolutePath,
                    batchExportMimeType = "application/zip",
                    batchFileCount = compressedList.size
                )
                result to null
            }

            BatchCompressExportMode.GALLERY_IMAGES -> {
                onProgress(0.82f, "Saving ${compressedList.size} compressed images to Gallery...")
                var totalCompressedBytes = 0L
                withContext(Dispatchers.IO) {
                    compressedList.forEachIndexed { idx, entry ->
                        val tempFile = uniqueImageOutputFile(entry.fileName)
                        FileOutputStream(tempFile).use { it.write(entry.bytes) }
                        totalCompressedBytes += tempFile.length()
                        saveImageToGallery(tempFile.absolutePath, tempFile.name, outputFormatActual)
                        onProgress(
                            0.82f + 0.18f * ((idx + 1).toFloat() / compressedList.size),
                            "Saved ${idx + 1} of ${compressedList.size}..."
                        )
                    }
                }
                onProgress(1f, "All images saved to Gallery")
                val result = ProcessedImageResult(
                    displayName = "${compressedList.size}_compressed_images",
                    filePath = previewFile.absolutePath,
                    format = outputFormatActual,
                    width = firstItem.width,
                    height = firstItem.height,
                    sizeBytes = totalCompressedBytes,
                    originalSizeBytes = totalOriginalBytes,
                    originalWidth = items.first().width,
                    originalHeight = items.first().height,
                    operationLabel = "Batch Compressed (${compressedList.size} Images)",
                    batchFileCount = compressedList.size
                )
                result to null
            }
        }
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
        var tempCopiedFile: File? = null
        val sourceFile = when (imageUri.scheme) {
            "file" -> imageUri.path?.let { File(it) }
            else -> null
        } ?: run {
            val tempDir = File(context.cacheDir, "ocr_temp").apply { mkdirs() }
            val temp = File(tempDir, "ocr_${UUID.randomUUID()}.jpg")
            val copied = runCatching {
                openUriInputStream(imageUri)?.use { input ->
                    FileOutputStream(temp).use { out -> input.copyTo(out) }
                }
            }.getOrNull()
            if (copied == null || !temp.exists() || temp.length() <= 0L) {
                runCatching { temp.delete() }
                throw IllegalStateException("Unable to read image for text recognition.")
            }
            tempCopiedFile = temp
            temp
        }

        val scaledBitmap = try {
            decodeSampledFileBitmap(sourceFile, maxDimension = 2048)
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            null
        } ?: run {
            tempCopiedFile?.delete()
            throw IllegalStateException("Could not decode selected image for OCR.")
        }

        var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
        try {
            val inputImage = InputImage.fromBitmap(scaledBitmap, 0)
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = recognizer.process(inputImage).awaitResult()
            val cleaned = result?.text?.trim().orEmpty()
            if (cleaned.isEmpty()) {
                throw IllegalStateException("No readable text detected in this image.")
            }
            val words = cleaned.split(Regex("\\s+")).count { it.isNotBlank() }
            OcrUiState.Success(
                text = cleaned,
                wordCount = words,
                charCount = cleaned.length
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            throw e
        } catch (t: Throwable) {
            throw IllegalStateException(
                t.localizedMessage ?: "Text recognition could not process this image. Please try another photo.",
                t
            )
        } finally {
            if (!scaledBitmap.isRecycled) scaledBitmap.recycle()
            runCatching { recognizer?.close() }
            tempCopiedFile?.let { runCatching { it.delete() } }
        }
    }

    suspend fun upscaleImage(
        info: PickedImageInfo,
        target: UpscaleTarget,
        enhanceMode: UpscaleEnhanceMode = UpscaleEnhanceMode.AUTO_PHOTO,
        format: ImageOutputFormat,
        onProgress: (Float, String) -> Unit
    ): ProcessedImageResult = withContext(Dispatchers.IO) {
        val srcFile = File(info.localPath)
        require(srcFile.exists()) { "Source image not found." }

        onProgress(0.10f, "Analyzing source resolution...")
        val source = decodeSampledFileBitmap(srcFile, 2048)
            ?: throw IllegalStateException("Could not decode image.")

        val srcW = max(source.width, info.width)
        val srcH = max(source.height, info.height)
        val aspect = srcW.toFloat() / max(1, srcH).toFloat()

        val desiredLongEdge = (max(srcW, srcH) * target.multiplier)
            .roundToInt()
            .coerceAtMost(target.maxEdgePx)
            .coerceAtLeast(max(srcW, srcH))

        // Clamp safely against available JVM/heap memory (avoid OOM on lower-RAM devices)
        val maxHeapBytes = Runtime.getRuntime().maxMemory()
        val safePixelBudget = ((maxHeapBytes * 0.24) / 4.0).toLong().coerceIn(8_000_000L, 64_000_000L)
        var targetW = if (srcW >= srcH) desiredLongEdge else (desiredLongEdge * aspect).roundToInt()
        var targetH = if (srcH >= srcW) desiredLongEdge else (desiredLongEdge / aspect).roundToInt()
        val desiredPixels = targetW.toLong() * targetH.toLong()
        if (desiredPixels > safePixelBudget) {
            val scaleDown = kotlin.math.sqrt(safePixelBudget.toDouble() / desiredPixels.toDouble())
            targetW = (targetW * scaleDown).roundToInt().coerceAtLeast(source.width)
            targetH = (targetH * scaleDown).roundToInt().coerceAtLeast(source.height)
        }
        targetW = targetW.coerceIn(source.width, target.maxEdgePx)
        targetH = targetH.coerceIn(source.height, target.maxEdgePx)

        var workingBitmap: Bitmap? = null
        var usedOfflineFallback = false
        try {
            // Primary Engine: Neural AI Super-Resolution (gemini-2.5-flash-image)
            onProgress(0.22f, "AI neural restoration (${enhanceMode.label})...")
            val aiRestored = try {
                val rawAi = aiUpscalerRepository.restoreImage(source, enhanceMode)
                alignBitmapAspectRatio(rawAi, aspect)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {
                usedOfflineFallback = true
                null
            }

            if (aiRestored != null) {
                if (!source.isRecycled) source.recycle()
                workingBitmap = aiRestored
                onProgress(0.55f, "Scaling neural output to ${targetW}×${targetH}...")
            } else {
                // Automatic Offline / Free-Quota Fallback: Bilateral Denoise -> Progressive Bicubic -> Unsharp Mask (1.8)
                onProgress(0.28f, "Offline Sharpen Mode: denoising...")
                val denoised = withContext(Dispatchers.Default) {
                    applyBilateralDenoiseBanded(source)
                }
                if (denoised !== source && !source.isRecycled) {
                    source.recycle()
                }
                workingBitmap = denoised
            }

            var current = checkNotNull(workingBitmap)
            var pass = 1
            while (current.width < targetW || current.height < targetH) {
                currentCoroutineContext().ensureActive()
                val nextW = min(targetW, current.width * 2)
                val nextH = min(targetH, current.height * 2)
                onProgress(
                    (0.56f + pass * 0.08f).coerceAtMost(0.80f),
                    "Upscaling pass $pass (${nextW}×${nextH})..."
                )
                val scaled = Bitmap.createScaledBitmap(current, nextW, nextH, true)
                if (scaled !== current && !current.isRecycled) {
                    current.recycle()
                }
                current = scaled
                workingBitmap = current
                pass++
                if (pass > 5) break
            }

            onProgress(
                0.84f,
                if (usedOfflineFallback) "Applying offline unsharp detail boost..."
                else "Refining micro-details & edge crispness..."
            )
            val sharpened = withContext(Dispatchers.Default) {
                applyLuminanceUnsharpMaskBanded(
                    src = current,
                    amount = if (usedOfflineFallback) 1.8f else 0.55f
                )
            }
            if (sharpened !== current && !current.isRecycled) {
                current.recycle()
            }
            current = sharpened
            workingBitmap = current

            onProgress(0.93f, "Encoding ${format.label}...")
            val baseTitle = info.displayName.substringBeforeLast('.')
            val suffix = if (usedOfflineFallback) "${target.name.lowercase()}_offline" else "${target.name.lowercase()}_ai"
            val outFile = uniqueImageOutputFile("${sanitizeFileName(baseTitle)}_$suffix.${format.ext}")
            writeBitmapToFile(current, outFile, format, quality = 96)

            onProgress(1f, if (usedOfflineFallback) "Offline sharpen complete" else "AI upscale complete")
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
                originalFilePath = info.localPath,
                isOfflineFallback = usedOfflineFallback,
                enhanceModeLabel = enhanceMode.label
            )
        } finally {
            if (!source.isRecycled) source.recycle()
            val lingering = workingBitmap
            if (lingering != null && !lingering.isRecycled) {
                lingering.recycle()
            }
        }
    }

    private fun alignBitmapAspectRatio(aiBitmap: Bitmap, targetAspect: Float): Bitmap {
        val aiW = aiBitmap.width
        val aiH = aiBitmap.height
        if (aiW <= 1 || aiH <= 1 || targetAspect <= 0f) return aiBitmap
        val aiAspect = aiW.toFloat() / aiH.toFloat()
        if (abs(aiAspect - targetAspect) / targetAspect <= 0.02f) return aiBitmap

        val cropW: Int
        val cropH: Int
        if (aiAspect > targetAspect) {
            cropH = aiH
            cropW = (aiH * targetAspect).roundToInt().coerceIn(1, aiW)
        } else {
            cropW = aiW
            cropH = (aiW / targetAspect).roundToInt().coerceIn(1, aiH)
        }
        val offsetX = ((aiW - cropW) / 2).coerceAtLeast(0)
        val offsetY = ((aiH - cropH) / 2).coerceAtLeast(0)
        return runCatching {
            val cropped = Bitmap.createBitmap(aiBitmap, offsetX, offsetY, cropW, cropH)
            if (cropped !== aiBitmap && !aiBitmap.isRecycled) aiBitmap.recycle()
            cropped
        }.getOrElse { aiBitmap }
    }

    suspend fun removeBackground(
        info: PickedImageInfo,
        onProgress: (Float, String) -> Unit
    ): Pair<String, String> = withContext(Dispatchers.IO) {
        val srcFile = File(info.localPath)
        require(srcFile.exists()) { "Source image not found." }

        onProgress(0.25f, "Running ML Kit Subject Segmentation...")
        val source = decodeSampledFileBitmap(srcFile, maxDimension = 2048)
            ?: throw IllegalStateException("Could not decode image.")

        var segmenter: com.google.mlkit.vision.segmentation.subject.SubjectSegmenter? = null
        var fg: Bitmap? = null
        try {
            val options = SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()
                .build()
            segmenter = SubjectSegmentation.getClient(options)
            val inputImage = InputImage.fromBitmap(source, 0)
            val result = segmenter.process(inputImage).awaitResult()
            val foreground = result?.foregroundBitmap?.takeIf { !it.isRecycled }
                ?: throw IllegalStateException("No clear foreground subject detected.")
            fg = foreground

            onProgress(0.85f, "Saving transparent cutout...")
            val outDir = File(context.cacheDir, "cutouts").apply { mkdirs() }
            val uid = UUID.randomUUID()
            val cutoutFile = File(outDir, "cutout_$uid.png")
            FileOutputStream(cutoutFile).use { out ->
                foreground.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            val alignedOrigFile = File(outDir, "orig_$uid.jpg")
            FileOutputStream(alignedOrigFile).use { out ->
                source.compress(Bitmap.CompressFormat.JPEG, 96, out)
            }
            onProgress(1f, "Subject isolated")
            cutoutFile.absolutePath to alignedOrigFile.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalStateException) {
            throw e
        } catch (t: Throwable) {
            throw IllegalStateException(
                t.localizedMessage ?: "Subject segmentation could not process this photo.",
                t
            )
        } finally {
            if (fg != null && fg !== source && !fg.isRecycled) {
                fg.recycle()
            }
            if (!source.isRecycled) {
                source.recycle()
            }
            runCatching { segmenter?.close() }
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
        val decodedFg = decodeSampledFileBitmap(srcFile, maxDimension = 4096)
            ?: throw IllegalStateException("Could not decode cutout image.")

        val mutableFg = if (decodedFg.isMutable) {
            decodedFg
        } else {
            decodedFg.copy(Bitmap.Config.ARGB_8888, true).also { decodedFg.recycle() }
        }

        val alignedOrig = if (strokes.any { !it.isErase } && !alignedOriginalPath.isNullOrBlank()) {
            decodeSampledFileBitmap(File(alignedOriginalPath), maxDimension = 4096)?.let { raw ->
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
        sizePreset: PassportSizePreset,
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
        onProgress(0.2f, "Preparing ${sizePreset.shortLabel} passport layout...")

        val rawPortrait = decodeSampledFileBitmap(srcFile, 2048)
            ?: throw IllegalStateException("Could not decode portrait image.")

        val tileW = sizePreset.tileWidthPx
        val tileH = sizePreset.tileHeightPx

        if (copyCount <= 1) {
            val singleW = (tileW * 2).coerceAtLeast(826)
            val singleH = (tileH * 2).coerceAtLeast(826)
            val singlePhoto = createPassportTileBitmap(
                source = rawPortrait,
                tileW = singleW,
                tileH = singleH,
                fillBg = removeBgEnabled,
                bgColorArgb = backgroundColorArgb,
                zoom = zoom,
                offsetXNorm = offsetXNorm,
                offsetYNorm = offsetYNorm
            )
            rawPortrait.recycle()

            try {
                onProgress(0.75f, "Saving high-res ${sizePreset.shortLabel} passport photo...")
                val jpgFile = uniqueImageOutputFile("Paperly_Passport_Single.jpg")
                writeBitmapToFile(singlePhoto, jpgFile, ImageOutputFormat.JPG, quality = 96)

                var pdfRecord: PdfRecordEntity? = null
                if (exportAsPdf) {
                    pdfsDir.mkdirs()
                    val pdfFile = File(pdfsDir, "Paperly_Passport_Single_${System.currentTimeMillis()}.pdf")
                    saveBitmapAsSinglePagePdf(singlePhoto, pdfFile)
                    pdfRecord = PdfRecordEntity(
                        displayName = pdfFile.name,
                        filePath = pdfFile.absolutePath,
                        pageCount = 1,
                        sizeBytes = pdfFile.length(),
                        originalSizeBytes = originalInfo.sizeBytes,
                        operationTag = "Scanned"
                    )
                }

                onProgress(1f, "Single passport photo ready")
                val imageResult = ProcessedImageResult(
                    displayName = jpgFile.name,
                    filePath = jpgFile.absolutePath,
                    format = ImageOutputFormat.JPG,
                    width = singlePhoto.width,
                    height = singlePhoto.height,
                    sizeBytes = jpgFile.length(),
                    originalSizeBytes = originalInfo.sizeBytes,
                    originalWidth = originalInfo.width,
                    originalHeight = originalInfo.height,
                    operationLabel = "Single Passport Photo (${sizePreset.shortLabel})",
                    secondaryPdfRecordId = null,
                    originalFilePath = originalInfo.localPath
                )
                return@withContext imageResult to pdfRecord
            } finally {
                if (!singlePhoto.isRecycled) singlePhoto.recycle()
            }
        }

        val cols = when (copyCount) {
            4 -> 2
            6 -> 3
            8 -> 4
            else -> 4
        }
        val rows = ((copyCount + cols - 1) / cols).coerceAtLeast(1)
        val gap = 28
        val minSheetW = max(1800, cols * tileW + (cols - 1) * gap + 80)
        val baseSheetH = if (copyCount <= 8) 1200 else 2400
        val minSheetH = max(baseSheetH, rows * tileH + (rows - 1) * gap + 80)
        val sheetW = minSheetW
        val sheetH = minSheetH

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
                operationLabel = "Passport Sheet ($copyCount copies • ${sizePreset.shortLabel})",
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

    private suspend fun applyMultiRadiusClarityEnhance(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val totalPixels = w.toLong() * h.toLong()
        if (w < 6 || h < 6) return src
        val useMultiRadius = totalPixels <= 38_000_000L
        if (totalPixels > 120_000_000L) return src

        return try {
            val out = Bitmap.createBitmap(w, h, src.config ?: Bitmap.Config.ARGB_8888)
            val edgeRow = IntArray(w)
            for (yEdge in listOf(0, 1, h - 2, h - 1)) {
                src.getPixels(edgeRow, 0, w, 0, yEdge, w, 1)
                out.setPixels(edgeRow, 0, w, 0, yEdge, w, 1)
            }

            val workerCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            val bandHeight = 192
            var yStart = 2
            val interiorEnd = h - 2
            while (yStart < interiorEnd) {
                currentCoroutineContext().ensureActive()
                val yEnd = min(interiorEnd, yStart + bandHeight)
                val outRows = yEnd - yStart
                val inStart = yStart - 2
                val inRows = outRows + 4
                val inBand = IntArray(inRows * w)
                val outBand = IntArray(outRows * w)
                src.getPixels(inBand, 0, w, 0, inStart, w, inRows)

                coroutineScope {
                    val effectiveWorkers = if (outRows >= 16) min(workerCount, outRows) else 1
                    val rowsPerWorker = (outRows + effectiveWorkers - 1) / effectiveWorkers
                    (0 until effectiveWorkers).map { workerIdx ->
                        async(Dispatchers.Default) {
                            val localStart = workerIdx * rowsPerWorker
                            val localEnd = min(outRows, localStart + rowsPerWorker)
                            for (localOutY in localStart until localEnd) {
                                ensureActive()
                                val inY = localOutY + 2
                                val rowPrev2Off = (inY - 2) * w
                                val rowPrev1Off = (inY - 1) * w
                                val rowCurrOff = inY * w
                                val rowNext1Off = (inY + 1) * w
                                val rowNext2Off = (inY + 2) * w
                                val outOff = localOutY * w

                                outBand[outOff] = inBand[rowCurrOff]
                                outBand[outOff + 1] = inBand[rowCurrOff + 1]
                                outBand[outOff + w - 2] = inBand[rowCurrOff + w - 2]
                                outBand[outOff + w - 1] = inBand[rowCurrOff + w - 1]

                                for (x in 2 until w - 2) {
                                    val c = inBand[rowCurrOff + x]
                                    val a = c ushr 24
                                    val rC = (c ushr 16) and 0xFF
                                    val gC = (c ushr 8) and 0xFF
                                    val bC = c and 0xFF

                                    val top1 = inBand[rowPrev1Off + x]
                                    val bot1 = inBand[rowNext1Off + x]
                                    val lft1 = inBand[rowCurrOff + x - 1]
                                    val rgt1 = inBand[rowCurrOff + x + 1]

                                    val rFineAvg = (((top1 ushr 16) and 0xFF) + ((bot1 ushr 16) and 0xFF) +
                                        ((lft1 ushr 16) and 0xFF) + ((rgt1 ushr 16) and 0xFF)) shr 2
                                    val gFineAvg = (((top1 ushr 8) and 0xFF) + ((bot1 ushr 8) and 0xFF) +
                                        ((lft1 ushr 8) and 0xFF) + ((rgt1 ushr 8) and 0xFF)) shr 2
                                    val bFineAvg = ((top1 and 0xFF) + (bot1 and 0xFF) + (lft1 and 0xFF) + (rgt1 and 0xFF)) shr 2

                                    val rDiffFine = rC - rFineAvg
                                    val gDiffFine = gC - gFineAvg
                                    val bDiffFine = bC - bFineAvg

                                    val rDelta: Int
                                    val gDelta: Int
                                    val bDelta: Int

                                    if (useMultiRadius) {
                                        val top2 = inBand[rowPrev2Off + x]
                                        val bot2 = inBand[rowNext2Off + x]
                                        val lft2 = inBand[rowCurrOff + x - 2]
                                        val rgt2 = inBand[rowCurrOff + x + 2]
                                        val rCoarseAvg = (((top2 ushr 16) and 0xFF) + ((bot2 ushr 16) and 0xFF) +
                                            ((lft2 ushr 16) and 0xFF) + ((rgt2 ushr 16) and 0xFF)) shr 2
                                        val gCoarseAvg = (((top2 ushr 8) and 0xFF) + ((bot2 ushr 8) and 0xFF) +
                                            ((lft2 ushr 8) and 0xFF) + ((rgt2 ushr 8) and 0xFF)) shr 2
                                        val bCoarseAvg = ((top2 and 0xFF) + (bot2 and 0xFF) + (lft2 and 0xFF) + (rgt2 and 0xFF)) shr 2

                                        val rDiffCoarse = rC - rCoarseAvg
                                        val gDiffCoarse = gC - gCoarseAvg
                                        val bDiffCoarse = bC - bCoarseAvg

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
                                    outBand[outOff + x] = (a shl 24) or (rNew shl 16) or (gNew shl 8) or bNew
                                }
                            }
                        }
                    }.awaitAll()
                }
                out.setPixels(outBand, 0, w, 0, yStart, w, outRows)
                yStart = yEnd
            }
            out
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            src
        }
    }

    private suspend fun applyBilateralDenoiseBanded(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val totalPixels = w.toLong() * h.toLong()
        if (w < 6 || h < 6 || totalPixels > 24_000_000L) return src

        // Precompute spatial (radius 2) & range intensity LUTs for fast edge-preserving denoising
        val spatialLut = FloatArray(25)
        val sigmaSpatialSq2 = 2f * 2.0f * 2.0f
        for (dy in -2..2) {
            for (dx in -2..2) {
                spatialLut[(dy + 2) * 5 + (dx + 2)] = exp(-(dx * dx + dy * dy) / sigmaSpatialSq2)
            }
        }
        val rangeLut = FloatArray(768)
        val sigmaRangeSq2 = 2f * 30f * 30f
        for (d in 0 until 768) {
            rangeLut[d] = exp(-(d * d) / sigmaRangeSq2)
        }

        return try {
            val out = Bitmap.createBitmap(w, h, src.config ?: Bitmap.Config.ARGB_8888)
            val edgeRow = IntArray(w)
            for (yEdge in listOf(0, 1, h - 2, h - 1)) {
                src.getPixels(edgeRow, 0, w, 0, yEdge, w, 1)
                out.setPixels(edgeRow, 0, w, 0, yEdge, w, 1)
            }

            val workerCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            val bandHeight = 192
            var yStart = 2
            val interiorEnd = h - 2
            while (yStart < interiorEnd) {
                currentCoroutineContext().ensureActive()
                val yEnd = min(interiorEnd, yStart + bandHeight)
                val outRows = yEnd - yStart
                val inStart = yStart - 2
                val inRows = outRows + 4
                val inBand = IntArray(inRows * w)
                val outBand = IntArray(outRows * w)
                src.getPixels(inBand, 0, w, 0, inStart, w, inRows)

                coroutineScope {
                    val effectiveWorkers = if (outRows >= 16) min(workerCount, outRows) else 1
                    val rowsPerWorker = (outRows + effectiveWorkers - 1) / effectiveWorkers
                    (0 until effectiveWorkers).map { workerIdx ->
                        async(Dispatchers.Default) {
                            val localStart = workerIdx * rowsPerWorker
                            val localEnd = min(outRows, localStart + rowsPerWorker)
                            for (localOutY in localStart until localEnd) {
                                ensureActive()
                                val inY = localOutY + 2
                                val rowOff = inY * w
                                val outOff = localOutY * w

                                outBand[outOff] = inBand[rowOff]
                                outBand[outOff + 1] = inBand[rowOff + 1]
                                outBand[outOff + w - 2] = inBand[rowOff + w - 2]
                                outBand[outOff + w - 1] = inBand[rowOff + w - 1]

                                for (x in 2 until w - 2) {
                                    val c = inBand[rowOff + x]
                                    val a = c ushr 24
                                    val rC = (c ushr 16) and 0xFF
                                    val gC = (c ushr 8) and 0xFF
                                    val bC = c and 0xFF

                                    var sumW = 0f
                                    var sumR = 0f
                                    var sumG = 0f
                                    var sumB = 0f
                                    var kIdx = 0
                                    for (dy in -2..2) {
                                        val nRow = (inY + dy) * w
                                        for (dx in -2..2) {
                                            val n = inBand[nRow + x + dx]
                                            val rN = (n ushr 16) and 0xFF
                                            val gN = (n ushr 8) and 0xFF
                                            val bN = n and 0xFF
                                            val colorDist = (abs(rN - rC) + abs(gN - gC) + abs(bN - bC)).coerceIn(0, 767)
                                            val weight = spatialLut[kIdx++] * rangeLut[colorDist]
                                            sumW += weight
                                            sumR += rN * weight
                                            sumG += gN * weight
                                            sumB += bN * weight
                                        }
                                    }
                                    val invW = if (sumW > 0f) 1f / sumW else 1f
                                    val rOut = (sumR * invW).roundToInt().coerceIn(0, 255)
                                    val gOut = (sumG * invW).roundToInt().coerceIn(0, 255)
                                    val bOut = (sumB * invW).roundToInt().coerceIn(0, 255)
                                    outBand[outOff + x] = (a shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
                                }
                            }
                        }
                    }.awaitAll()
                }
                out.setPixels(outBand, 0, w, 0, yStart, w, outRows)
                yStart = yEnd
            }
            out
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            src
        }
    }

    private suspend fun applyLuminanceUnsharpMaskBanded(src: Bitmap, amount: Float): Bitmap {
        val w = src.width
        val h = src.height
        val totalPixels = w.toLong() * h.toLong()
        if (w < 6 || h < 6 || totalPixels > 72_000_000L) return src

        return try {
            val out = Bitmap.createBitmap(w, h, src.config ?: Bitmap.Config.ARGB_8888)
            val edgeRow = IntArray(w)
            for (yEdge in listOf(0, 1, h - 2, h - 1)) {
                src.getPixels(edgeRow, 0, w, 0, yEdge, w, 1)
                out.setPixels(edgeRow, 0, w, 0, yEdge, w, 1)
            }

            val workerCount = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            val bandHeight = 256
            var yStart = 2
            val interiorEnd = h - 2
            while (yStart < interiorEnd) {
                currentCoroutineContext().ensureActive()
                val yEnd = min(interiorEnd, yStart + bandHeight)
                val outRows = yEnd - yStart
                val inStart = yStart - 2
                val inRows = outRows + 4
                val inBand = IntArray(inRows * w)
                val outBand = IntArray(outRows * w)
                src.getPixels(inBand, 0, w, 0, inStart, w, inRows)

                coroutineScope {
                    val effectiveWorkers = if (outRows >= 16) min(workerCount, outRows) else 1
                    val rowsPerWorker = (outRows + effectiveWorkers - 1) / effectiveWorkers
                    (0 until effectiveWorkers).map { workerIdx ->
                        async(Dispatchers.Default) {
                            val localStart = workerIdx * rowsPerWorker
                            val localEnd = min(outRows, localStart + rowsPerWorker)
                            for (localOutY in localStart until localEnd) {
                                ensureActive()
                                val inY = localOutY + 2
                                val rowP1 = (inY - 1) * w
                                val rowC = inY * w
                                val rowN1 = (inY + 1) * w
                                val outOff = localOutY * w

                                outBand[outOff] = inBand[rowC]
                                outBand[outOff + 1] = inBand[rowC + 1]
                                outBand[outOff + w - 2] = inBand[rowC + w - 2]
                                outBand[outOff + w - 1] = inBand[rowC + w - 1]

                                for (x in 2 until w - 2) {
                                    val c = inBand[rowC + x]
                                    val a = c ushr 24
                                    val rC = (c ushr 16) and 0xFF
                                    val gC = (c ushr 8) and 0xFF
                                    val bC = c and 0xFF

                                    val top = inBand[rowP1 + x]
                                    val bot = inBand[rowN1 + x]
                                    val lft = inBand[rowC + x - 1]
                                    val rgt = inBand[rowC + x + 1]
                                    val tl = inBand[rowP1 + x - 1]
                                    val tr = inBand[rowP1 + x + 1]
                                    val bl = inBand[rowN1 + x - 1]
                                    val br = inBand[rowN1 + x + 1]

                                    val rBlur = (((top ushr 16) and 0xFF) * 2 + ((bot ushr 16) and 0xFF) * 2 +
                                        ((lft ushr 16) and 0xFF) * 2 + ((rgt ushr 16) and 0xFF) * 2 +
                                        ((tl ushr 16) and 0xFF) + ((tr ushr 16) and 0xFF) +
                                        ((bl ushr 16) and 0xFF) + ((br ushr 16) and 0xFF) + rC * 4) shr 4
                                    val gBlur = (((top ushr 8) and 0xFF) * 2 + ((bot ushr 8) and 0xFF) * 2 +
                                        ((lft ushr 8) and 0xFF) * 2 + ((rgt ushr 8) and 0xFF) * 2 +
                                        ((tl ushr 8) and 0xFF) + ((tr ushr 8) and 0xFF) +
                                        ((bl ushr 8) and 0xFF) + ((br ushr 8) and 0xFF) + gC * 4) shr 4
                                    val bBlur = ((top and 0xFF) * 2 + (bot and 0xFF) * 2 +
                                        (lft and 0xFF) * 2 + (rgt and 0xFF) * 2 +
                                        (tl and 0xFF) + (tr and 0xFF) +
                                        (bl and 0xFF) + (br and 0xFF) + bC * 4) shr 4

                                    // Compute luminance delta so chroma noise isn't over-boosted
                                    val lumC = (rC * 77 + gC * 150 + bC * 29) shr 8
                                    val lumBlur = (rBlur * 77 + gBlur * 150 + bBlur * 29) shr 8
                                    val lumDiff = lumC - lumBlur
                                    val boost = if (abs(lumDiff) < 3) {
                                        0
                                    } else {
                                        (lumDiff * amount).roundToInt().coerceIn(-56, 56)
                                    }

                                    val rNew = (rC + boost).coerceIn(0, 255)
                                    val gNew = (gC + boost).coerceIn(0, 255)
                                    val bNew = (bC + boost).coerceIn(0, 255)
                                    outBand[outOff + x] = (a shl 24) or (rNew shl 16) or (gNew shl 8) or bNew
                                }
                            }
                        }
                    }.awaitAll()
                }
                out.setPixels(outBand, 0, w, 0, yStart, w, outRows)
                yStart = yEnd
            }
            out
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            src
        }
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
        if (!file.exists()) return null
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        var sampleSize = 1
        while ((boundsOpts.outWidth / sampleSize) > maxDimension ||
            (boundsOpts.outHeight / sampleSize) > maxDimension
        ) {
            sampleSize *= 2
        }

        var decoded: Bitmap? = null
        for (attempt in 0..2) {
            try {
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                if (decoded != null) break
            } catch (_: OutOfMemoryError) {
                sampleSize *= 2
            }
        }
        val baseBitmap = decoded ?: return null

        // Clamp exact max dimension if still slightly above maxDimension after power-of-2 sampling
        val scaledBitmap = if (max(baseBitmap.width, baseBitmap.height) > maxDimension) {
            val scale = maxDimension.toFloat() / max(baseBitmap.width, baseBitmap.height).toFloat()
            val targetW = (baseBitmap.width * scale).roundToInt().coerceAtLeast(1)
            val targetH = (baseBitmap.height * scale).roundToInt().coerceAtLeast(1)
            runCatching {
                Bitmap.createScaledBitmap(baseBitmap, targetW, targetH, true).also {
                    if (it !== baseBitmap) baseBitmap.recycle()
                }
            }.getOrElse { baseBitmap }
        } else {
            baseBitmap
        }

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
        if (matrix.isIdentity) return scaledBitmap
        return runCatching {
            Bitmap.createBitmap(scaledBitmap, 0, 0, scaledBitmap.width, scaledBitmap.height, matrix, true).also {
                if (it !== scaledBitmap) scaledBitmap.recycle()
            }
        }.getOrElse { scaledBitmap }
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

    suspend fun renameOutputFile(
        currentPath: String,
        requestedName: String,
        desiredExtension: String
    ): File = withContext(Dispatchers.IO) {
        val srcFile = File(currentPath)
        require(srcFile.exists()) { "Output file no longer exists." }
        val ext = desiredExtension.trimStart('.').ifBlank { srcFile.extension.ifBlank { "jpg" } }
        val cleanedStem = sanitizeFileName(
            requestedName.trim()
                .removeSuffix(".$ext")
                .removeSuffix(".${ext.uppercase()}")
        )
        val parent = srcFile.parentFile ?: imagesDir
        var destFile = File(parent, "$cleanedStem.$ext")
        if (destFile.absolutePath == srcFile.absolutePath) return@withContext srcFile
        var counter = 2
        while (destFile.exists()) {
            destFile = File(parent, "${cleanedStem}_$counter.$ext")
            counter++
        }
        val moved = srcFile.renameTo(destFile) || run {
            srcFile.copyTo(destFile, overwrite = false)
            srcFile.delete()
            destFile.exists()
        }
        check(moved) { "Could not rename file on storage." }
        destFile
    }

    private fun openUriInputStream(uri: Uri): InputStream? {
        val resolverStream = runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
        if (resolverStream != null) return resolverStream
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val file = uri.path?.let { File(it) }
            if (file != null && file.exists() && file.canRead()) {
                return runCatching { file.inputStream() }.getOrNull()
            }
        }
        return null
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
