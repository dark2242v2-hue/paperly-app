package com.example.data.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import com.example.data.local.PdfRecordEntity
import com.example.data.model.CompressionLevel
import com.example.data.model.PdfAiPayload
import com.example.data.model.PickedPdfInfo
import com.example.data.model.SignaturePlacement
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class UnlockedPdfResult(
    val unlockedFilePath: String,
    val pageCount: Int
)

class PdfEngine(private val context: Context) {

    val pdfsDir: File by lazy {
        File(context.filesDir, "pdfs").apply { mkdirs() }
    }

    val cacheDir: File
        get() = context.cacheDir

    private val pageThumbnailCache: LruCache<String, Bitmap> by lazy {
        val maxMemoryKb = (Runtime.getRuntime().maxMemory() / 1024L).toInt()
        val cacheSizeKb = (maxMemoryKb / 8).coerceIn(16_384, 65_536)
        object : LruCache<String, Bitmap>(cacheSizeKb) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return (value.allocationByteCount / 1024).coerceAtLeast(1)
            }
        }
    }

    private val latestPageKeyByTarget = ConcurrentHashMap<String, String>()

    private fun buildTargetKey(filePath: String, pageIndex: Int, maxWidth: Int): String =
        "$filePath|$pageIndex|$maxWidth"

    fun getCachedPageThumbnail(
        filePath: String,
        pageIndex: Int = 0,
        maxWidth: Int = 720
    ): Bitmap? {
        val key = latestPageKeyByTarget[buildTargetKey(filePath, pageIndex, maxWidth)] ?: return null
        return pageThumbnailCache.get(key)?.takeIf { !it.isRecycled }
    }

    @Synchronized
    private fun ensurePdfBoxInit() {
        if (!isPdfBoxInitialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            isPdfBoxInitialized = true
        }
    }

    suspend fun importPickedPdf(uri: Uri): PickedPdfInfo = withContext(Dispatchers.IO) {
        ensurePdfBoxInit()
        val displayName = resolveDisplayName(uri) ?: "Document.pdf"
        val safeName = sanitizeFileName(displayName)
        val tempFile = File(context.cacheDir, "picked_${UUID.randomUUID()}_$safeName")

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to read selected PDF file.")

        val pageCount = countPages(tempFile)
        if (pageCount <= 0) {
            val isLocked = requiresPasswordSync(tempFile)
            tempFile.delete()
            if (isLocked) {
                throw IllegalStateException("This PDF is password-protected. Open it directly in the viewer to unlock first.")
            }
            throw IllegalStateException("Selected file does not contain valid PDF pages.")
        }

        PickedPdfInfo(
            id = UUID.randomUUID().toString(),
            displayName = if (displayName.endsWith(".pdf", ignoreCase = true)) displayName else "$displayName.pdf",
            localPath = tempFile.absolutePath,
            pageCount = pageCount,
            sizeBytes = tempFile.length()
        )
    }

    suspend fun importExternalPdfRecord(uri: Uri): PdfRecordEntity = withContext(Dispatchers.IO) {
        ensurePdfBoxInit()
        val displayName = resolveDisplayName(uri) ?: "Shared_Document.pdf"
        val cleanName = ensurePdfExtension(sanitizeFileName(displayName))
        val outFile = uniqueOutputFile(cleanName)

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(outFile).use { output ->
                input.copyTo(output)
            }
        } ?: throw IllegalStateException("Unable to read opened PDF file.")

        val pageCount = countPages(outFile)
        if (pageCount > 0) {
            return@withContext PdfRecordEntity(
                displayName = outFile.name,
                filePath = outFile.absolutePath,
                pageCount = pageCount,
                sizeBytes = outFile.length(),
                originalSizeBytes = null,
                operationTag = "Opened"
            )
        }

        if (requiresPasswordSync(outFile)) {
            return@withContext PdfRecordEntity(
                displayName = outFile.name,
                filePath = outFile.absolutePath,
                pageCount = 0,
                sizeBytes = outFile.length(),
                originalSizeBytes = null,
                operationTag = "Locked"
            )
        }

        outFile.delete()
        throw IllegalStateException("Opened file is not a valid PDF.")
    }

    suspend fun requiresPassword(filePath: String): Boolean = withContext(Dispatchers.IO) {
        ensurePdfBoxInit()
        requiresPasswordSync(File(filePath))
    }

    suspend fun unlockPdfForViewing(filePath: String, password: String): UnlockedPdfResult = withContext(Dispatchers.IO) {
        ensurePdfBoxInit()
        val srcFile = File(filePath)
        require(srcFile.exists()) { "PDF file not found." }
        if (password.isEmpty()) {
            throw IllegalArgumentException("Incorrect password, please try again")
        }

        val unlockedDir = File(context.cacheDir, "unlocked_view").apply { mkdirs() }
        val outFile = File(unlockedDir, "view_${UUID.randomUUID()}.pdf")
        try {
            PDDocument.load(srcFile, password, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val pages = doc.numberOfPages
                doc.isAllSecurityToBeRemoved = true
                doc.save(outFile)
                UnlockedPdfResult(
                    unlockedFilePath = outFile.absolutePath,
                    pageCount = pages
                )
            }
        } catch (e: InvalidPasswordException) {
            outFile.delete()
            throw IllegalArgumentException("Incorrect password, please try again", e)
        } catch (e: Exception) {
            outFile.delete()
            throw e
        }
    }

    private fun requiresPasswordSync(file: File): Boolean {
        if (!file.exists()) return false
        return try {
            PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { false }
        } catch (_: InvalidPasswordException) {
            true
        } catch (_: Exception) {
            false
        }
    }

    fun fromExistingRecord(record: PdfRecordEntity): PickedPdfInfo {
        return PickedPdfInfo(
            id = "record_${record.id}",
            displayName = record.displayName,
            localPath = record.filePath,
            pageCount = record.pageCount,
            sizeBytes = record.sizeBytes
        )
    }

    suspend fun mergePdfs(
        files: List<PickedPdfInfo>,
        outputName: String,
        onProgress: (Float, String) -> Unit
    ): PdfRecordEntity = withContext(Dispatchers.IO) {
        require(files.size >= 2) { "Select at least 2 PDF files to merge." }
        ensurePdfBoxInit()

        val finalName = ensurePdfExtension(sanitizeFileName(outputName.ifBlank { "Paperly_Merged" }))
        val outFile = uniqueOutputFile(finalName)

        onProgress(0.15f, "Preparing ${files.size} documents...")
        val merger = PDFMergerUtility()
        merger.destinationFileName = outFile.absolutePath

        files.forEachIndexed { index, info ->
            val step = 0.15f + (0.45f * (index + 1).toFloat() / files.size)
            onProgress(step, "Preparing PDF ${index + 1} of ${files.size}...")
            val src = File(info.localPath)
            require(src.exists()) { "File not found: ${info.displayName}" }
            merger.addSource(src)
        }

        onProgress(0.70f, "Merging PDF streams locally...")
        try {
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        } catch (e: InvalidPasswordException) {
            outFile.delete()
            throw IllegalStateException("One or more selected PDFs are password-protected. Unlock them in the viewer first.", e)
        } catch (e: Exception) {
            outFile.delete()
            throw e
        }

        onProgress(0.95f, "Verifying merged document...")
        val pages = countPages(outFile)
        onProgress(1f, "Merge complete")

        PdfRecordEntity(
            displayName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = pages,
            sizeBytes = outFile.length(),
            originalSizeBytes = files.sumOf { it.sizeBytes },
            operationTag = "Merged"
        )
    }

    suspend fun splitPdf(
        file: PickedPdfInfo,
        pageRangeSpec: String,
        outputName: String,
        onProgress: (Float, String) -> Unit
    ): PdfRecordEntity = withContext(Dispatchers.IO) {
        ensurePdfBoxInit()
        val srcFile = File(file.localPath)
        require(srcFile.exists()) { "Source PDF not found." }

        onProgress(0.15f, "Parsing page selection...")
        val selectedPages = parsePageRange(pageRangeSpec, file.pageCount)
        require(selectedPages.isNotEmpty()) {
            "Enter valid page numbers between 1 and ${file.pageCount} (e.g. 1-3, 5)."
        }

        val baseName = outputName.ifBlank {
            file.displayName.removeSuffix(".pdf") + "_split"
        }
        val outFile = uniqueOutputFile(ensurePdfExtension(sanitizeFileName(baseName)))

        onProgress(0.35f, "Loading PDF structure...")
        try {
            PDDocument.load(srcFile, MemoryUsageSetting.setupTempFileOnly()).use { sourceDoc ->
                PDDocument().use { targetDoc ->
                    selectedPages.forEachIndexed { idx, oneBasedPage ->
                        val page = sourceDoc.getPage(oneBasedPage - 1)
                        targetDoc.importPage(page)
                        val progress = 0.35f + 0.50f * ((idx + 1).toFloat() / selectedPages.size)
                        onProgress(progress, "Processing page ${idx + 1} of ${selectedPages.size}...")
                    }
                    onProgress(0.90f, "Writing split PDF...")
                    targetDoc.save(outFile)
                }
            }
        } catch (e: InvalidPasswordException) {
            outFile.delete()
            throw IllegalStateException("This PDF is password-protected. Unlock it in the viewer first.", e)
        } catch (e: Exception) {
            outFile.delete()
            throw e
        }

        onProgress(1f, "Split complete")
        PdfRecordEntity(
            displayName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = selectedPages.size,
            sizeBytes = outFile.length(),
            originalSizeBytes = file.sizeBytes,
            operationTag = "Split"
        )
    }

    suspend fun compressPdf(
        file: PickedPdfInfo,
        level: CompressionLevel,
        outputName: String,
        onProgress: (Float, String) -> Unit
    ): PdfRecordEntity = withContext(Dispatchers.IO) {
        ensurePdfBoxInit()
        val srcFile = File(file.localPath)
        require(srcFile.exists()) { "Source PDF not found." }

        val baseName = outputName.ifBlank {
            file.displayName.removeSuffix(".pdf") + "_compressed"
        }
        val outFile = uniqueOutputFile(ensurePdfExtension(sanitizeFileName(baseName)))

        onProgress(0.10f, "Inspecting ${file.pageCount} pages...")
        val pfd = ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    val pageCount = renderer.pageCount
                    PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
                        val scaleFactor = level.targetDpi / 72f
                        for (i in 0 until pageCount) {
                            renderer.openPage(i).use { page ->
                                val origW = max(1, page.width)
                                val origH = max(1, page.height)
                                val renderW = (origW * scaleFactor).roundToInt().coerceIn(320, 2200)
                                val renderH = (origH * scaleFactor).roundToInt().coerceIn(320, 3000)

                                val bitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val baos = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, baos)
                                bitmap.recycle()

                                val pdPage = PDPage(PDRectangle(origW.toFloat(), origH.toFloat()))
                                outDoc.addPage(pdPage)
                                val pdImage = JPEGFactory.createFromStream(
                                    outDoc,
                                    ByteArrayInputStream(baos.toByteArray())
                                )
                                PDPageContentStream(outDoc, pdPage).use { cs ->
                                    cs.drawImage(pdImage, 0f, 0f, origW.toFloat(), origH.toFloat())
                                }
                            }
                            val progress = 0.15f + 0.72f * ((i + 1).toFloat() / pageCount)
                            onProgress(progress, "Processing page ${i + 1} of $pageCount...")
                        }
                        onProgress(0.92f, "Finalizing compressed PDF...")
                        outDoc.save(outFile)
                    }
                }
            }
        } catch (e: SecurityException) {
            outFile.delete()
            throw IllegalStateException("This PDF is password-protected. Unlock it in the viewer first.", e)
        } catch (e: Exception) {
            outFile.delete()
            throw e
        }

        // If the original PDF was an ultra-small text-only PDF and rasterizing increased size,
        // fall back to stream-only PDFBox save so we never inflate a document unnecessarily.
        if (outFile.length() > file.sizeBytes) {
            val streamOut = File(context.cacheDir, "stream_${UUID.randomUUID()}.pdf")
            try {
                runCatching {
                    PDDocument.load(srcFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                        doc.save(streamOut)
                    }
                    if (streamOut.exists() && streamOut.length() > 0L && streamOut.length() < outFile.length()) {
                        streamOut.copyTo(outFile, overwrite = true)
                    }
                }
            } finally {
                streamOut.delete()
            }
        }

        onProgress(1f, "Compression complete")
        PdfRecordEntity(
            displayName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = countPages(outFile),
            sizeBytes = outFile.length(),
            originalSizeBytes = file.sizeBytes,
            operationTag = "Compressed"
        )
    }

    suspend fun compressPdfToTargetSize(
        file: PickedPdfInfo,
        targetBytes: Long,
        outputName: String,
        onProgress: (Float, String) -> Unit
    ): PdfRecordEntity = withContext(Dispatchers.IO) {
        ensurePdfBoxInit()
        val srcFile = File(file.localPath)
        require(srcFile.exists()) { "Source PDF not found." }

        val clampedTarget = targetBytes.coerceAtLeast(10_240L)
        val baseName = outputName.ifBlank {
            file.displayName.removeSuffix(".pdf") + "_compressed"
        }
        val outFile = uniqueOutputFile(ensurePdfExtension(sanitizeFileName(baseName)))

        onProgress(0.08f, "Analyzing ${file.pageCount} pages for target size...")
        val pfd = ParcelFileDescriptor.open(srcFile, ParcelFileDescriptor.MODE_READ_ONLY)
        try {
            pfd.use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                val pageCount = max(1, renderer.pageCount)
                val pdfOverhead = 2_400L + pageCount * 520L
                val pageBudget = ((clampedTarget - pdfOverhead) / pageCount).coerceAtLeast(2_048L)

                fun buildTargetPdf(destination: File, perPageBudget: Long, initialDpi: Float, progressStart: Float, progressSpan: Float) {
                    PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { outDoc ->
                        val scaleFactor = initialDpi / 72f
                        val scaleSteps = floatArrayOf(1.0f, 0.78f, 0.60f, 0.45f, 0.34f, 0.25f)

                        for (i in 0 until pageCount) {
                            renderer.openPage(i).use { page ->
                                val origW = max(1, page.width)
                                val origH = max(1, page.height)
                                val renderW = (origW * scaleFactor).roundToInt().coerceIn(220, 1800)
                                val renderH = (origH * scaleFactor).roundToInt().coerceIn(220, 2400)

                                val baseBitmap = Bitmap.createBitmap(renderW, renderH, Bitmap.Config.ARGB_8888)
                                baseBitmap.eraseColor(Color.WHITE)
                                page.render(baseBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                var chosenBytes: ByteArray? = null
                                try {
                                    for (step in scaleSteps) {
                                        val working = if (step >= 0.999f) {
                                            baseBitmap
                                        } else {
                                            val w = (baseBitmap.width * step).roundToInt().coerceAtLeast(120)
                                            val h = (baseBitmap.height * step).roundToInt().coerceAtLeast(120)
                                            Bitmap.createScaledBitmap(baseBitmap, w, h, true)
                                        }
                                        try {
                                            val highTry = encodeJpegBytes(working, 90)
                                            if (highTry.size <= perPageBudget) {
                                                chosenBytes = highTry
                                                break
                                            }
                                            val lowTry = encodeJpegBytes(working, 14)
                                            if (lowTry.size <= perPageBudget) {
                                                var lo = 14
                                                var hi = 90
                                                var candidate = lowTry
                                                while (lo <= hi) {
                                                    val mid = (lo + hi) ushr 1
                                                    val attempt = encodeJpegBytes(working, mid)
                                                    if (attempt.size <= perPageBudget) {
                                                        candidate = attempt
                                                        lo = mid + 1
                                                    } else {
                                                        hi = mid - 1
                                                    }
                                                }
                                                chosenBytes = candidate
                                                break
                                            }
                                            if (chosenBytes == null || lowTry.size < chosenBytes!!.size) {
                                                chosenBytes = lowTry
                                            }
                                        } finally {
                                            if (working !== baseBitmap) working.recycle()
                                        }
                                    }
                                } finally {
                                    baseBitmap.recycle()
                                }

                                val pdPage = PDPage(PDRectangle(origW.toFloat(), origH.toFloat()))
                                outDoc.addPage(pdPage)
                                val pdImage = JPEGFactory.createFromStream(
                                    outDoc,
                                    ByteArrayInputStream(chosenBytes)
                                )
                                PDPageContentStream(outDoc, pdPage).use { cs ->
                                    cs.drawImage(pdImage, 0f, 0f, origW.toFloat(), origH.toFloat())
                                }
                            }
                            onProgress(
                                progressStart + progressSpan * ((i + 1).toFloat() / pageCount),
                                "Processing page ${i + 1} of $pageCount..."
                            )
                        }
                        outDoc.save(destination)
                    }
                }

                buildTargetPdf(outFile, pageBudget, initialDpi = 144f, progressStart = 0.12f, progressSpan = 0.65f)

                if (outFile.length() > clampedTarget * 1.05 && outFile.length() > 12_288L) {
                    onProgress(0.82f, "Fine-tuning to exact target size...")
                    val ratio = (clampedTarget.toDouble() / outFile.length().toDouble()).coerceIn(0.25, 0.90)
                    val adjustedBudget = (pageBudget * ratio * 0.88).roundToInt().toLong().coerceAtLeast(1_500L)
                    val retryFile = File(context.cacheDir, "target_retry_${UUID.randomUUID()}.pdf")
                    buildTargetPdf(retryFile, adjustedBudget, initialDpi = 96f, progressStart = 0.84f, progressSpan = 0.12f)
                    if (retryFile.exists() && retryFile.length() < outFile.length()) {
                        retryFile.copyTo(outFile, overwrite = true)
                    }
                    retryFile.delete()
                }
            }
        }
        } catch (e: SecurityException) {
            outFile.delete()
            throw IllegalStateException("This PDF is password-protected. Unlock it in the viewer first.", e)
        } catch (e: Exception) {
            outFile.delete()
            throw e
        }

        onProgress(1f, "Target compression complete")
        PdfRecordEntity(
            displayName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = countPages(outFile),
            sizeBytes = outFile.length(),
            originalSizeBytes = file.sizeBytes,
            operationTag = "Compressed"
        )
    }

    private fun encodeJpegBytes(bitmap: Bitmap, quality: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(1, 100), baos)
        return baos.toByteArray()
    }

    suspend fun imagesToPdf(
        imageUris: List<Uri>,
        grayscaleEnhance: Boolean,
        outputName: String,
        onProgress: (Float, String) -> Unit
    ): PdfRecordEntity = withContext(Dispatchers.IO) {
        require(imageUris.isNotEmpty()) { "Add at least 1 photo or scan to create a PDF." }
        ensurePdfBoxInit()

        val baseName = outputName.ifBlank { "Paperly_Scan" }
        val outFile = uniqueOutputFile(ensurePdfExtension(sanitizeFileName(baseName)))

        onProgress(0.10f, "Preparing ${imageUris.size} scanned page(s)...")
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            imageUris.forEachIndexed { idx, uri ->
                val rawBitmap = decodeSampledBitmap(uri, maxDimension = 1800)
                    ?: throw IllegalStateException("Could not decode image #${idx + 1}")
                val processedBitmap = if (grayscaleEnhance) {
                    applyDocumentScanFilter(rawBitmap).also { rawBitmap.recycle() }
                } else {
                    rawBitmap
                }

                val isLandscape = processedBitmap.width > processedBitmap.height
                val pageRect = if (isLandscape) {
                    PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width)
                } else {
                    PDRectangle.A4
                }
                val page = PDPage(pageRect)
                doc.addPage(page)

                val margin = 24f
                val availW = pageRect.width - margin * 2f
                val availH = pageRect.height - margin * 2f
                val scale = min(
                    availW / processedBitmap.width.toFloat(),
                    availH / processedBitmap.height.toFloat()
                )
                val drawW = processedBitmap.width * scale
                val drawH = processedBitmap.height * scale
                val drawX = (pageRect.width - drawW) / 2f
                val drawY = (pageRect.height - drawH) / 2f

                val baos = ByteArrayOutputStream()
                processedBitmap.compress(Bitmap.CompressFormat.JPEG, 86, baos)
                processedBitmap.recycle()

                val pdImage = JPEGFactory.createFromStream(doc, ByteArrayInputStream(baos.toByteArray()))
                PDPageContentStream(doc, page).use { cs ->
                    cs.drawImage(pdImage, drawX, drawY, drawW, drawH)
                }

                val progress = 0.15f + 0.75f * ((idx + 1).toFloat() / imageUris.size)
                onProgress(progress, "Processing page ${idx + 1} of ${imageUris.size}...")
            }

            onProgress(0.95f, "Saving scanned PDF...")
            doc.save(outFile)
        }

        onProgress(1f, "Scan PDF ready")
        PdfRecordEntity(
            displayName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = imageUris.size,
            sizeBytes = outFile.length(),
            originalSizeBytes = null,
            operationTag = "Scanned"
        )
    }

    suspend fun signPdf(
        file: PickedPdfInfo,
        signatureBitmap: Bitmap,
        targetPageOneBased: Int,
        centerXNorm: Float = 0.72f,
        centerYNorm: Float = 0.82f,
        widthFraction: Float = 0.28f,
        outputName: String,
        onProgress: (Float, String) -> Unit
    ): PdfRecordEntity = withContext(Dispatchers.IO) {
        ensurePdfBoxInit()
        val srcFile = File(file.localPath)
        require(srcFile.exists()) { "Source PDF not found." }

        val baseName = outputName.ifBlank {
            file.displayName.removeSuffix(".pdf") + "_signed"
        }
        val outFile = uniqueOutputFile(ensurePdfExtension(sanitizeFileName(baseName)))

        onProgress(0.25f, "Opening document...")
        var totalPages = file.pageCount
        try {
            PDDocument.load(srcFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                totalPages = doc.numberOfPages
                val pageIdx = (targetPageOneBased - 1).coerceIn(0, doc.numberOfPages - 1)
                val page = doc.getPage(pageIdx)
                val box = page.cropBox ?: page.mediaBox ?: PDRectangle.A4

                onProgress(0.55f, "Embedding signature...")
                val pdSignature = LosslessFactory.createFromImage(doc, signatureBitmap)
                val aspect = signatureBitmap.height.toFloat() / max(1, signatureBitmap.width).toFloat()
                var targetW = box.width * widthFraction.coerceIn(0.10f, 0.78f)
                var targetH = targetW * aspect
                val maxH = box.height * 0.65f
                if (targetH > maxH) {
                    targetH = maxH
                    targetW = targetH / max(0.01f, aspect)
                }

                val minX = box.lowerLeftX
                val maxX = (box.upperRightX - targetW).coerceAtLeast(minX)
                val minY = box.lowerLeftY
                val maxY = (box.upperRightY - targetH).coerceAtLeast(minY)

                val x = (box.lowerLeftX + centerXNorm * box.width - targetW / 2f).coerceIn(minX, maxX)
                val y = (box.lowerLeftY + (1f - centerYNorm) * box.height - targetH / 2f).coerceIn(minY, maxY)

                PDPageContentStream(
                    doc,
                    page,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                ).use { cs ->
                    cs.drawImage(pdSignature, x, y, targetW, targetH)
                }

                onProgress(0.90f, "Saving signed PDF...")
                doc.save(outFile)
            }
        } catch (e: InvalidPasswordException) {
            outFile.delete()
            throw IllegalStateException("This PDF is password-protected. Unlock it in the viewer first.", e)
        } catch (e: Exception) {
            outFile.delete()
            throw e
        }

        onProgress(1f, "Signature applied")
        PdfRecordEntity(
            displayName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = totalPages,
            sizeBytes = outFile.length(),
            originalSizeBytes = file.sizeBytes,
            operationTag = "Signed"
        )
    }

    suspend fun lockPdf(
        file: PickedPdfInfo,
        password: String,
        outputName: String,
        onProgress: (Float, String) -> Unit
    ): PdfRecordEntity = withContext(Dispatchers.IO) {
        ensurePdfBoxInit()
        require(password.length >= 4) { "Enter a password with at least 4 characters." }
        val srcFile = File(file.localPath)
        require(srcFile.exists()) { "Source PDF not found." }

        val baseName = outputName.ifBlank {
            file.displayName.removeSuffix(".pdf") + "_locked"
        }
        val outFile = uniqueOutputFile(ensurePdfExtension(sanitizeFileName(baseName)))

        onProgress(0.20f, "Loading PDF structure...")
        var totalPages = file.pageCount
        try {
            PDDocument.load(srcFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                totalPages = doc.numberOfPages
                onProgress(0.55f, "Encrypting with 128-bit password protection...")
                val accessPermission = AccessPermission()
                val policy = StandardProtectionPolicy(password, password, accessPermission).apply {
                    encryptionKeyLength = 128
                }
                doc.protect(policy)
                onProgress(0.88f, "Saving locked PDF...")
                doc.save(outFile)
            }
        } catch (e: InvalidPasswordException) {
            outFile.delete()
            throw IllegalStateException("This PDF is already password-protected.", e)
        }

        onProgress(1f, "PDF locked")
        PdfRecordEntity(
            displayName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = totalPages,
            sizeBytes = outFile.length(),
            originalSizeBytes = file.sizeBytes,
            operationTag = "Locked"
        )
    }

    suspend fun extractAiPayload(file: PickedPdfInfo): PdfAiPayload = withContext(Dispatchers.IO) {
        ensurePdfBoxInit()
        val srcFile = File(file.localPath)
        require(srcFile.exists()) { "PDF file not found." }

        val rawExtracted = try {
            PDDocument.load(srcFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                val stripper = PDFTextStripper().apply {
                    startPage = 1
                    endPage = min(doc.numberOfPages, 40)
                }
                stripper.getText(doc).orEmpty()
            }
        } catch (e: InvalidPasswordException) {
            throw IllegalStateException("This PDF is password-protected. Unlock it in the viewer first.", e)
        }

        val normalizedLines = rawExtracted
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lineSequence()
            .map { line -> line.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")

        val maxChars = 18_000
        val wasTruncated = normalizedLines.length > maxChars
        val compressedText = if (wasTruncated) {
            val slice = normalizedLines.take(maxChars)
            val lastSpace = slice.lastIndexOf(' ')
            if (lastSpace > maxChars - 400) slice.substring(0, lastSpace).trimEnd() else slice.trimEnd()
        } else {
            normalizedLines
        }

        val fallbackBitmaps = if (compressedText.length < 80) {
            renderPreviewPages(srcFile.absolutePath, maxPages = 3, maxWidth = 1024)
        } else {
            emptyList()
        }

        PdfAiPayload(
            displayName = file.displayName,
            pageCount = file.pageCount,
            extractedText = compressedText,
            fallbackBitmaps = fallbackBitmaps,
            wasTruncated = wasTruncated
        )
    }

    suspend fun exportSummaryToPdf(
        sourceFileName: String,
        summaryText: String
    ): PdfRecordEntity = withContext(Dispatchers.IO) {
        val baseStem = sourceFileName.removeSuffix(".pdf").removeSuffix(".PDF")
        val outFile = uniqueOutputFile(ensurePdfExtension(sanitizeFileName("${baseStem}_summary")))

        val pageWidth = 595
        val pageHeight = 842
        val marginH = 44f
        val marginTop = 52f
        val marginBottom = 48f
        val contentWidth = (pageWidth - marginH * 2).toInt()

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4F46E5")
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val headingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 16f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1E293B")
            textSize = 11.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 9.5f
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 1f
        }

        val normalizedBody = summaryText
            .replace("**", "")
            .lines()
            .joinToString("\n") { rawLine ->
                val trimmed = rawLine.trim()
                when {
                    trimmed.startsWith("#") -> "\n${trimmed.trimStart('#', ' ').uppercase()}"
                    trimmed.startsWith("- ") -> "• ${trimmed.removePrefix("- ").trimStart()}"
                    trimmed.startsWith("* ") -> "• ${trimmed.removePrefix("* ").trimStart()}"
                    else -> rawLine
                }
            }
            .trim()
        val bodyLayout = StaticLayout.Builder
            .obtain(normalizedBody, 0, normalizedBody.length, bodyPaint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(4f, 1.25f)
            .setIncludePad(true)
            .build()

        val pdfDocument = PdfDocument()
        try {
            var lineIndex = 0
            var pageNum = 1

            while (lineIndex < bodyLayout.lineCount || pageNum == 1) {
                val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                var cursorY = marginTop
                if (pageNum == 1) {
                    canvas.drawText("PAPERLY AI SUMMARY", marginH, cursorY, titlePaint)
                    cursorY += 22f
                    val titleLayout = StaticLayout.Builder
                        .obtain(sourceFileName, 0, sourceFileName.length, headingPaint, contentWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .build()
                    canvas.save()
                    canvas.translate(marginH, cursorY)
                    titleLayout.draw(canvas)
                    canvas.restore()
                    cursorY += titleLayout.height + 12f
                    canvas.drawLine(marginH, cursorY, pageWidth - marginH, cursorY, dividerPaint)
                    cursorY += 18f
                }

                val maxBodyY = pageHeight - marginBottom - 16f
                while (lineIndex < bodyLayout.lineCount) {
                    val lineTop = bodyLayout.getLineTop(lineIndex)
                    val lineBottom = bodyLayout.getLineBottom(lineIndex)
                    val lineHeight = (lineBottom - lineTop).toFloat()
                    if (cursorY + lineHeight > maxBodyY) break

                    val baseline = cursorY + (bodyLayout.getLineBaseline(lineIndex) - lineTop)
                    val lineStart = bodyLayout.getLineStart(lineIndex)
                    val lineEnd = bodyLayout.getLineEnd(lineIndex)
                    val lineStr = normalizedBody.substring(lineStart, lineEnd).trimEnd('\n')
                    canvas.drawText(lineStr, marginH, baseline, bodyPaint)
                    cursorY += lineHeight
                    lineIndex++
                }

                canvas.drawText("Page $pageNum • Generated by Paperly", marginH, pageHeight - 24f, footerPaint)
                pdfDocument.finishPage(page)
                pageNum++
                if (lineIndex >= bodyLayout.lineCount) break
            }

            FileOutputStream(outFile).use { fos ->
                pdfDocument.writeTo(fos)
            }
        } finally {
            pdfDocument.close()
        }

        PdfRecordEntity(
            displayName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = countPages(outFile),
            sizeBytes = outFile.length(),
            originalSizeBytes = null,
            operationTag = "Summary"
        )
    }

    suspend fun renderPageThumbnail(
        filePath: String,
        pageIndex: Int = 0,
        maxWidth: Int = 720
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null
        val targetKey = buildTargetKey(filePath, pageIndex, maxWidth)
        val cacheKey = "${filePath}|${file.lastModified()}|$pageIndex|$maxWidth"
        latestPageKeyByTarget[targetKey] = cacheKey
        pageThumbnailCache.get(cacheKey)?.takeIf { !it.isRecycled }?.let { cached ->
            return@withContext cached
        }
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return@runCatching null
                    val clampedIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(clampedIndex).use { page ->
                        val ratio = page.height.toFloat() / max(1, page.width).toFloat()
                        var w = maxWidth.coerceIn(160, 2880)
                        var h = (w * ratio).roundToInt().coerceAtLeast(160)
                        val maxPixels = 7_500_000f
                        val totalPixels = w.toFloat() * h.toFloat()
                        if (totalPixels > maxPixels) {
                            val scaleDown = kotlin.math.sqrt(maxPixels / totalPixels)
                            w = (w * scaleDown).roundToInt().coerceAtLeast(160)
                            h = (h * scaleDown).roundToInt().coerceAtLeast(160)
                        }
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        pageThumbnailCache.put(cacheKey, bmp)
                        latestPageKeyByTarget[targetKey] = cacheKey
                        bmp
                    }
                }
            }
        }.getOrNull()
    }

    private fun renderPreviewPages(filePath: String, maxPages: Int, maxWidth: Int): List<Bitmap> {
        val file = File(filePath)
        if (!file.exists()) return emptyList()
        val result = mutableListOf<Bitmap>()
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val count = min(renderer.pageCount, maxPages)
                    for (i in 0 until count) {
                        renderer.openPage(i).use { page ->
                            val ratio = page.height.toFloat() / max(1, page.width).toFloat()
                            val w = maxWidth
                            val h = (w * ratio).roundToInt().coerceAtLeast(200)
                            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            result.add(bmp)
                        }
                    }
                }
            }
        }
        return result
    }

    suspend fun saveToUserUri(sourcePath: String, destinationUri: Uri) = withContext(Dispatchers.IO) {
        val srcFile = File(sourcePath)
        require(srcFile.exists()) { "PDF file no longer exists." }
        context.contentResolver.openOutputStream(destinationUri)?.use { out ->
            srcFile.inputStream().use { input ->
                input.copyTo(out)
            }
        } ?: throw IllegalStateException("Could not open destination for saving.")
    }

    fun createScanTempFile(): File {
        val scansDir = File(context.cacheDir, "scans").apply { mkdirs() }
        return File(scansDir, "scan_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
    }

    fun fileToProviderUri(file: File): Uri {
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun createScanTempUri(): Uri {
        return fileToProviderUri(createScanTempFile())
    }

    suspend fun importScanUris(uris: List<Uri>): List<Uri> = withContext(Dispatchers.IO) {
        uris.mapNotNull { sourceUri ->
            runCatching {
                val destFile = createScanTempFile()
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: return@runCatching null
                if (destFile.length() <= 0L) {
                    destFile.delete()
                    null
                } else {
                    fileToProviderUri(destFile)
                }
            }.getOrNull()
        }
    }

    fun getShareUri(filePath: String): Uri {
        val file = File(filePath)
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun countPages(file: File): Int {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        }.getOrElse {
            runCatching {
                ensurePdfBoxInit()
                PDDocument.load(file, MemoryUsageSetting.setupTempFileOnly()).use { it.numberOfPages }
            }.getOrDefault(0)
        }
    }

    private fun parsePageRange(spec: String, totalPages: Int): List<Int> {
        val trimmed = spec.trim()
        if (trimmed.equals("all", ignoreCase = true)) {
            return (1..totalPages).toList()
        }
        if (trimmed.isEmpty()) return emptyList()

        val pages = mutableListOf<Int>()
        val parts = trimmed.split(",")
        for (part in parts) {
            val token = part.trim()
            if (token.isEmpty()) continue
            if ("-" in token) {
                val bounds = token.split("-")
                if (bounds.size != 2) throw IllegalArgumentException("Invalid range '$token'")
                val start = bounds[0].trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid start page in '$token'")
                val end = bounds[1].trim().toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid end page in '$token'")
                require(start in 1..totalPages && end in 1..totalPages && start <= end) {
                    "Page range $token is out of bounds (1-$totalPages)."
                }
                for (p in start..end) pages.add(p)
            } else {
                val single = token.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid page number '$token'")
                require(single in 1..totalPages) {
                    "Page $single is out of bounds (1-$totalPages)."
                }
                pages.add(single)
            }
        }
        return pages.distinct()
    }

    private fun decodeSampledBitmap(uri: Uri, maxDimension: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        var sampleSize = 1
        while (boundsOpts.outWidth / sampleSize > maxDimension ||
            boundsOpts.outHeight / sampleSize > maxDimension
        ) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null

        val rotation = readExifRotation(uri)
        if (rotation == 0f) return decoded
        val matrix = Matrix().apply { postRotate(rotation) }
        return Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
            if (it !== decoded) decoded.recycle()
        }
    }

    private fun readExifRotation(uri: Uri): Float {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        }.getOrDefault(0f)
    }

    private fun applyDocumentScanFilter(source: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.35f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, translate,
                0f, contrast, 0f, 0f, translate,
                0f, 0f, contrast, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            )
        )
        grayscale.postConcat(contrastMatrix)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(grayscale) }
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "Paperly_Doc" }
    }

    private fun ensurePdfExtension(name: String): String {
        return if (name.endsWith(".pdf", ignoreCase = true)) name else "$name.pdf"
    }

    private fun uniqueOutputFile(fileName: String): File {
        var candidate = File(pdfsDir, fileName)
        if (!candidate.exists()) return candidate
        val stem = fileName.removeSuffix(".pdf").removeSuffix(".PDF")
        var counter = 2
        while (candidate.exists()) {
            candidate = File(pdfsDir, "${stem}_$counter.pdf")
            counter++
        }
        return candidate
    }

    companion object {
        @Volatile
        private var isPdfBoxInitialized = false
    }
}
