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
