package com.example

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.HomeToolCategory
import com.example.ui.components.ShimmerFileRowCard
import com.example.ui.components.ShimmerPreviewCard
import com.example.ui.components.ShimmerRecentImageCard
import com.example.ui.components.ShimmerRecentPdfCard
import com.example.ui.screens.HomeScreen
import com.example.ui.theme.PaperlyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Paperly", appName)
    }

    @Test
    fun `shimmer skeleton cards render in light and dark themes`() {
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.setContent {
            Column {
                PaperlyTheme(darkTheme = false) {
                    ShimmerRecentPdfCard()
                    ShimmerRecentImageCard()
                }
                PaperlyTheme(darkTheme = true) {
                    ShimmerFileRowCard()
                    ShimmerPreviewCard()
                }
            }
        }
        composeTestRule.mainClock.advanceTimeBy(160)

        composeTestRule.onNodeWithTag("shimmer_recent_pdf_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shimmer_recent_image_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("shimmer_file_row_card").assertIsDisplayed()
    }

    @Test
    fun `home screen shows shimmer row while loading and empty card after load`() {
        composeTestRule.mainClock.autoAdvance = false
        var isLoading by mutableStateOf(true)
        composeTestRule.setContent {
            PaperlyTheme(darkTheme = false) {
                HomeScreen(
                    selectedCategory = HomeToolCategory.PDF,
                    recentRecords = emptyList(),
                    recentImages = emptyList(),
                    isInitialFilesLoading = isLoading,
                    isInitialImagesLoading = isLoading,
                    onCategorySelected = {},
                    onToolSelected = {},
                    onImageToolSelected = {},
                    onRecentSelected = {},
                    onRecentImageSelected = {},
                    onSeeAllFiles = {}
                )
            }
        }
        composeTestRule.mainClock.advanceTimeBy(120)

        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("recent_pdfs_shimmer_row"))
        composeTestRule.mainClock.advanceTimeBy(120)

        composeTestRule.onNodeWithTag("recent_pdfs_shimmer_row").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("recent_empty_card").fetchSemanticsNodes().size)

        isLoading = false
        composeTestRule.mainClock.advanceTimeBy(120)

        composeTestRule.onNode(hasScrollAction())
            .performScrollToNode(hasTestTag("recent_empty_card"))
        composeTestRule.onNodeWithTag("recent_empty_card").assertIsDisplayed()
    }
}
