package com.example.data.model

import android.graphics.Bitmap

enum class PdfToolType(val routeArg: String) {
    MERGE("merge"),
    SPLIT("split"),
    COMPRESS("compress"),
    SCAN("scan"),
    AI_SUMMARIZE("ai_summarize"),
    SIGN("sign"),
    LOCK("lock");

    companion object {
        fun fromRouteArg(arg: String?): PdfToolType =
            entries.firstOrNull { it.routeArg == arg } ?: MERGE
    }
}

enum class CompressionLevel(
    val label: String,
    val subtitle: String,
    val targetDpi: Int,
    val jpegQuality: Int
) {
    LIGHT("Light", "High clarity • ~25% smaller", 170, 82),
    BALANCED("Balanced", "Recommended • ~55% smaller", 130, 68),
    MAXIMUM("Maximum", "Smallest file • ~75% smaller", 96, 50)
}

enum class SignaturePlacement(val label: String) {
    BOTTOM_RIGHT("Bottom Right"),
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_LEFT("Bottom Left"),
    CENTER("Center")
}

data class PickedPdfInfo(
    val id: String,
    val displayName: String,
    val localPath: String,
    val pageCount: Int,
    val sizeBytes: Long
)

data class ProcessedPdfResult(
    val recordId: Long,
    val displayName: String,
    val filePath: String,
    val pageCount: Int,
    val sizeBytes: Long,
    val originalSizeBytes: Long?,
    val operationTag: String
)

data class PdfAiPayload(
    val displayName: String,
    val pageCount: Int,
    val extractedText: String,
    val fallbackBitmaps: List<Bitmap>,
    val wasTruncated: Boolean = false
)

sealed interface AiSummaryUiState {
    data object Idle : AiSummaryUiState
    data object Extracting : AiSummaryUiState
    data object Loading : AiSummaryUiState
    data object Formatting : AiSummaryUiState
    data class Streaming(val text: String, val fileName: String, val pageCount: Int) : AiSummaryUiState
    data class Success(
        val text: String,
        val fileName: String,
        val pageCount: Int,
        val wasTruncated: Boolean = false
    ) : AiSummaryUiState
    data class Error(val message: String) : AiSummaryUiState
}

enum class HomeToolCategory(val label: String) {
    PDF("PDF Tools"),
    IMAGE("Image Tools")
}

enum class ImageToolType(val routeArg: String) {
    COMPRESS_IMAGE("compress_image"),
    CROP_RESIZE("crop_resize"),
    IMAGE_TO_TEXT("image_to_text"),
    TEXT_TO_IMAGE("text_to_image"),
    UPSCALER("upscaler"),
    BG_REMOVER("bg_remover"),
    PASSPORT_SHEET("passport_sheet");

    companion object {
        fun fromRouteArg(arg: String?): ImageToolType =
            entries.firstOrNull { it.routeArg == arg } ?: COMPRESS_IMAGE
    }
}

enum class TargetSizeUnit(val label: String, val multiplierBytes: Long) {
    KB("KB", 1024L),
    MB("MB", 1024L * 1024L)
}

enum class ImageOutputFormat(val label: String, val ext: String, val mimeType: String) {
    JPG("JPG", "jpg", "image/jpeg"),
    PNG("PNG", "png", "image/png"),
    WEBP("WEBP", "webp", "image/webp")
}

enum class CropAspectPreset(val label: String, val ratio: Float?) {
    FREE("Free", null),
    SQUARE("1:1 (Square)", 1f),
    FOUR_FIVE("4:5 (Instagram Portrait)", 4f / 5f),
    NINE_SIXTEEN("9:16 (Story / Reels / Vertical)", 9f / 16f),
    SIXTEEN_NINE("16:9 (Widescreen / YouTube)", 16f / 9f),
    FOUR_THREE("4:3", 4f / 3f),
    THREE_FOUR("3:4", 3f / 4f),
    PASSPORT("Passport (35x45 mm)", 35f / 45f),
    SIGNATURE("Signature (3:1)", 3f)
}

enum class PassportSizePreset(
    val label: String,
    val shortLabel: String,
    val aspectRatio: Float,
    val tileWidthPx: Int,
    val tileHeightPx: Int
) {
    MM_35_45("35×45 mm (India / UK / EU)", "35×45 mm", 35f / 45f, 413, 531),
    INCH_2_2("2×2 inch (US / Visa)", "2×2 inch", 1f, 600, 600),
    MM_35_35("35×35 mm (PAN / Stamp)", "35×35 mm", 1f, 413, 413)
}

enum class AiImageStyle(val label: String, val promptModifier: String) {
    PHOTOREALISTIC("Photorealistic", "photorealistic, ultra detailed natural lighting, sharp 8k photography"),
    DIGITAL_ART("Digital Art", "vibrant digital illustration, rich color harmony, clean concept art"),
    RENDER_3D("3D Render", "clean 3D octane studio render, soft global illumination, tactile materials"),
    ANIME("Anime", "high quality anime key visual art, expressive lighting, crisp linework"),
    MINIMAL_LOGO("Minimal Logo", "clean modern minimalist vector brand mark logo on crisp solid backdrop")
}

enum class AiImageAspectRatio(val label: String, val apiValue: String, val previewRatio: Float) {
    SQUARE("1:1", "1:1", 1f),
    LANDSCAPE("16:9", "16:9", 16f / 9f),
    PORTRAIT("9:16", "9:16", 9f / 16f),
    CLASSIC_PORTRAIT("3:4", "3:4", 3f / 4f)
}

enum class UpscaleTarget(val label: String, val multiplier: Float, val maxEdgePx: Int) {
    HD_2X("2x HD", 2f, 4096),
    ULTRA_4X("4x Ultra", 4f, 7680),
    RES_8K("8K", 1f, 7680),
    MAX_16K("16K Max", 1f, 15360)
}

data class PickedImageInfo(
    val uriString: String,
    val localPath: String,
    val displayName: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long
)

data class ProcessedImageResult(
    val displayName: String,
    val filePath: String,
    val format: ImageOutputFormat,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val originalSizeBytes: Long?,
    val originalWidth: Int?,
    val originalHeight: Int?,
    val operationLabel: String,
    val secondaryPdfRecordId: Long? = null,
    val originalFilePath: String? = null
)

data class RecentImageItem(
    val displayName: String,
    val filePath: String,
    val format: ImageOutputFormat,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val createdAtMillis: Long
)

data class BgBrushPoint(
    val xNorm: Float,
    val yNorm: Float
)

data class BgBrushStroke(
    val isErase: Boolean,
    val radiusNorm: Float,
    val points: List<BgBrushPoint>
)

sealed interface OcrUiState {
    data object Idle : OcrUiState
    data object Scanning : OcrUiState
    data class Success(val text: String, val wordCount: Int, val charCount: Int) : OcrUiState
    data class Error(val message: String) : OcrUiState
}

