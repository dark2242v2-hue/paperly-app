package com.example

import com.example.core.formatFileSize
import com.example.core.formatReductionPercent
import com.example.core.formatSavedSummary
import com.example.data.model.UpscaleTarget
import org.junit.Assert.assertEquals
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
    fun upscaleTarget_hd2xIsBaseTierAndHigherTiersHaveDistinctCaps() {
        assertEquals("2x HD", UpscaleTarget.HD_2X.label)
        assertEquals(2f, UpscaleTarget.HD_2X.multiplier)
        assertEquals(4096, UpscaleTarget.HD_2X.maxEdgePx)

        val proTargets = UpscaleTarget.entries.filter { it != UpscaleTarget.HD_2X }
        assertEquals(listOf(UpscaleTarget.ULTRA_4X, UpscaleTarget.RES_8K, UpscaleTarget.MAX_16K), proTargets)
        assertTrue(proTargets.all { it.maxEdgePx >= 7680 })
    }
}
