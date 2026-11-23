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
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PdfEngine(private val context: Context) {

    val pdfsDir: File by lazy {
        File(context.filesDir, "pdfs").apply { mkdirs() }
    }

    val cacheDir: File
        get() = context.cacheDir

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
            tempFile.delete()
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
            val src = File(info.localPath)
            require(src.exists()) { "File not found: ${info.displayName}" }
            merger.addSource(src)
            val step = 0.15f + (0.45f * (index + 1).toFloat() / files.size)
            onProgress(step, "Queueing ${info.displayName}...")
        }

        onProgress(0.70f, "Merging PDF streams locally...")
        merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())

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
        PDDocument.load(srcFile, MemoryUsageSetting.setupTempFileOnly()).use { sourceDoc ->
            PDDocument().use { targetDoc ->
                selectedPages.forEachIndexed { idx, oneBasedPage ->
                    val page = sourceDoc.getPage(oneBasedPage - 1)
                    targetDoc.importPage(page)
                    val progress = 0.35f + 0.50f * ((idx + 1).toFloat() / selectedPages.size)
                    onProgress(progress, "Extracting page $oneBasedPage...")
                }
                onProgress(0.90f, "Writing split PDF...")
                targetDoc.save(outFile)
            }
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
                        onProgress(progress, "Optimizing page ${i + 1} of $pageCount...")
                    }
                    onProgress(0.92f, "Finalizing compressed PDF...")
                    outDoc.save(outFile)
                }
            }
        }

        // If the original PDF was an ultra-small text-only PDF and rasterizing increased size,
        // fall back to stream-only PDFBox save so we never inflate a document unnecessarily.
        if (outFile.length() > file.sizeBytes) {
            val streamOut = File(context.cacheDir, "stream_${UUID.randomUUID()}.pdf")
            PDDocument.load(srcFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
                doc.save(streamOut)
            }
            if (streamOut.exists() && streamOut.length() < outFile.length()) {
                streamOut.copyTo(outFile, overwrite = true)
            }
            streamOut.delete()
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
                onProgress(progress, "Building page ${idx + 1} of ${imageUris.size}...")
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
        placement: SignaturePlacement,
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
        PDDocument.load(srcFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val pageIdx = (targetPageOneBased - 1).coerceIn(0, doc.numberOfPages - 1)
            val page = doc.getPage(pageIdx)
            val box = page.cropBox ?: page.mediaBox ?: PDRectangle.A4

            onProgress(0.55f, "Embedding vector signature...")
            val pdSignature = LosslessFactory.createFromImage(doc, signatureBitmap)
            val targetW = (box.width * 0.28f).coerceIn(110f, 220f)
            val aspect = signatureBitmap.height.toFloat() / max(1, signatureBitmap.width).toFloat()
            val targetH = targetW * aspect
            val margin = 36f

            val (x, y) = when (placement) {
                SignaturePlacement.BOTTOM_RIGHT ->
                    (box.upperRightX - targetW - margin) to (box.lowerLeftY + margin)
                SignaturePlacement.BOTTOM_CENTER ->
                    (box.lowerLeftX + (box.width - targetW) / 2f) to (box.lowerLeftY + margin)
                SignaturePlacement.BOTTOM_LEFT ->
                    (box.lowerLeftX + margin) to (box.lowerLeftY + margin)
                SignaturePlacement.CENTER ->
                    (box.lowerLeftX + (box.width - targetW) / 2f) to
                        (box.lowerLeftY + (box.height - targetH) / 2f)
            }

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

        onProgress(1f, "Signature applied")
        PdfRecordEntity(
            displayName = outFile.name,
            filePath = outFile.absolutePath,
            pageCount = file.pageCount,
            sizeBytes = outFile.length(),
            originalSizeBytes = file.sizeBytes,
            operationTag = "Signed"
        )
    }

    suspend fun extractAiPayload(file: PickedPdfInfo): PdfAiPayload = withContext(Dispatchers.IO) {
        ensurePdfBoxInit()
        val srcFile = File(file.localPath)
        require(srcFile.exists()) { "PDF file not found." }

        val extracted = PDDocument.load(srcFile, MemoryUsageSetting.setupTempFileOnly()).use { doc ->
            val stripper = PDFTextStripper().apply {
                startPage = 1
                endPage = min(doc.numberOfPages, 25)
            }
            stripper.getText(doc).orEmpty().take(28_000)
        }

        val fallbackBitmaps = if (extracted.trim().length < 80) {
            renderPreviewPages(srcFile.absolutePath, maxPages = 3, maxWidth = 1024)
        } else {
            emptyList()
        }

        PdfAiPayload(
            displayName = file.displayName,
            pageCount = file.pageCount,
            extractedText = extracted,
            fallbackBitmaps = fallbackBitmaps
        )
    }

    suspend fun renderPageThumbnail(
        filePath: String,
        pageIndex: Int = 0,
        maxWidth: Int = 720
    ): Bitmap? = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) return@withContext null
        runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    if (renderer.pageCount == 0) return@runCatching null
                    val clampedIndex = pageIndex.coerceIn(0, renderer.pageCount - 1)
                    renderer.openPage(clampedIndex).use { page ->
                        val ratio = page.height.toFloat() / max(1, page.width).toFloat()
                        val w = maxWidth.coerceAtLeast(160)
                        val h = (w * ratio).roundToInt().coerceAtLeast(160)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
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

    fun createScanTempUri(): Uri {
        val scansDir = File(context.cacheDir, "scans").apply { mkdirs() }
        val file = File(scansDir, "scan_${System.currentTimeMillis()}.jpg")
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    fun getShareUri(filePath: String): Uri {
        val file = File(filePath)
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }

    private fun countPages(file: File): Int {
        return runCatching {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer -> renderer.pageCount }
            }
        }.getOrElse {
            runCatching {
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
