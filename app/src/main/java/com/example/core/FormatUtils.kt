package com.example.core

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${kb.roundToInt()} KB"
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.2f MB", mb)
}

fun formatReductionPercent(originalBytes: Long?, newBytes: Long): Int? {
    if (originalBytes == null || originalBytes <= 0L || newBytes >= originalBytes) return null
    return (((originalBytes - newBytes).toDouble() / originalBytes.toDouble()) * 100.0).roundToInt()
}

fun formatSavedSummary(
    originalBytes: Long?,
    newBytes: Long,
    pageCount: Int? = null
): String {
    val savedPercent = formatReductionPercent(originalBytes, newBytes)
    if (originalBytes != null && savedPercent != null && savedPercent > 0) {
        return "Saved! Reduced from ${formatFileSize(originalBytes)} → ${formatFileSize(newBytes)} (-$savedPercent%)"
    }
    return if (pageCount != null && pageCount > 0) {
        "Saved! $pageCount page(s) • ${formatFileSize(newBytes)}"
    } else {
        "Saved! ${formatFileSize(newBytes)}"
    }
}

fun formatTimestamp(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    return when {
        diff < 60_000L -> "Just now"
        diff < 3_600_000L -> "${diff / 60_000L}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000L}h ago"
        else -> SimpleDateFormat("MMM d", Locale.US).format(Date(millis))
    }
}
