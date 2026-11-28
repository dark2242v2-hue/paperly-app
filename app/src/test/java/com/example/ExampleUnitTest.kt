package com.example

import com.example.core.formatFileSize
import com.example.core.formatReductionPercent
import com.example.core.formatSavedSummary
import com.example.data.model.BatchCompressExportMode
import com.example.data.model.ImageOutputFormat
import com.example.data.model.OrganizedPageItem
import com.example.data.model.ProcessedImageResult
import com.example.data.model.ScanFilterMode
import com.example.data.model.SplitWorkspaceMode
import com.example.data.model.UpscaleEnhanceMode
import com.example.data.model.UpscaleTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun formatFileSize_formatsBytesKbAndMb() {
        assertEquals("512 B", formatFileSize(512L))
        assertEquals("420 KB", formatFileSize(420L * 1024L))
        assertEquals("6.60 MB", formatFileSize((6.6 * 1024.0 * 1024.0).toLong()))
    }

    @Test
    fun formatReductionPercent_returnsExpectedSavingsOrNull() {
        assertEquals(90, formatReductionPercent(1000L, 100L))
        assertNull(formatReductionPercent(null, 100L))
        assertNull(formatReductionPercent(100L, 150L))
    }

    @Test
    fun formatSavedSummary_rendersReducedAndStandardSummaries() {
        val original = (6.6 * 1024.0 * 1024.0).toLong()
        val compressed = 420L * 1024L
        val summary = formatSavedSummary(originalBytes = original, newBytes = compressed)
        assertEquals("Saved! Reduced from 6.60 MB → 420 KB (-94%)", summary)

        assertEquals(
            "Saved! 3 page(s) • 420 KB",
            formatSavedSummary(originalBytes = null, newBytes = compressed, pageCount = 3)
        )
    }

    @Test
    fun upscaleTarget_andEnhanceModes_haveBalancedTiersAndPresets() {
        assertEquals("2× Crisp HD", UpscaleTarget.HD_2X.label)
        assertEquals(2f, UpscaleTarget.HD_2X.multiplier)
        assertEquals(2560, UpscaleTarget.HD_2X.maxEdgePx)

        val proTargets = UpscaleTarget.entries.filter { it != UpscaleTarget.HD_2X }
        assertEquals(listOf(UpscaleTarget.ULTRA_4X, UpscaleTarget.PRINT_8X), proTargets)
        assertEquals(4096, UpscaleTarget.ULTRA_4X.maxEdgePx)
        assertEquals(6144, UpscaleTarget.PRINT_8X.maxEdgePx)

        assertEquals(
            listOf(
                UpscaleEnhanceMode.AUTO_PHOTO,
                UpscaleEnhanceMode.TEXT_DOCUMENT,
                UpscaleEnhanceMode.OLD_PHOTO
            ),
            UpscaleEnhanceMode.entries
        )
        assertTrue(UpscaleEnhanceMode.entries.all { it.promptAddon.isNotBlank() })
    }

    @Test
    fun pageOrganizer_andBatchCompressModels_haveExpectedDefaultsAndModes() {
        assertEquals(listOf(SplitWorkspaceMode.RANGE, SplitWorkspaceMode.ORGANIZE), SplitWorkspaceMode.entries)
        val page = OrganizedPageItem(id = "org_0_0", originalPageIndex = 0)
        assertEquals(0, page.rotationDegrees)

        assertEquals(
            listOf(
                BatchCompressExportMode.SINGLE_PDF,
                BatchCompressExportMode.ZIP_ARCHIVE,
                BatchCompressExportMode.GALLERY_IMAGES
            ),
            BatchCompressExportMode.entries
        )

        val result = ProcessedImageResult(
            filePath = "/tmp/sample.jpg",
            displayName = "sample.jpg",
            width = 800,
            height = 600,
            sizeBytes = 1024L,
            originalSizeBytes = 4096L,
            originalWidth = 1600,
            originalHeight = 1200,
            format = ImageOutputFormat.JPG,
            operationLabel = "Compressed"
        )
        assertNull(result.batchExportFilePath)
        assertNull(result.batchExportMimeType)
        assertEquals(1, result.batchFileCount)
    }

    @Test
    fun scanFilterMode_defaultsToOriginalAndIncludesAllModes() {
        assertEquals(ScanFilterMode.ORIGINAL, ScanFilterMode.entries.first())
        assertEquals("Original (100% Clarity)", ScanFilterMode.ORIGINAL.label)
        assertEquals(
            listOf(
                ScanFilterMode.ORIGINAL,
                ScanFilterMode.MAGIC_COLOR,
                ScanFilterMode.CRISP_BW
            ),
            ScanFilterMode.entries
        )
    }
}
