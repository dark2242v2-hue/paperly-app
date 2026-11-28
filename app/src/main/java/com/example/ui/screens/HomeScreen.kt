package com.example.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.formatFileSize
import com.example.core.formatTimestamp
import com.example.data.local.PdfRecordEntity
import com.example.data.model.HomeToolCategory
import com.example.data.model.ImageToolType
import com.example.data.model.PdfToolType
import com.example.data.model.RecentImageItem
import com.example.ui.components.FittedSingleLineText
import com.example.ui.components.SampledDrawableImage
import com.example.ui.components.ShimmerBox
import com.example.ui.components.ShimmerRecentImageCard
import com.example.ui.components.ShimmerRecentPdfCard
import com.example.ui.components.pressScaleEffect
import com.example.ui.components.rememberPaperlyHaptics
import com.example.ui.theme.PaperlyAmber
import com.example.ui.theme.PaperlyAmberSoft
import com.example.ui.theme.PaperlyEmerald
import com.example.ui.theme.PaperlyEmeraldSoft
import com.example.ui.theme.PaperlyIndigo
import com.example.ui.theme.PaperlyIndigoTint
import com.example.ui.theme.PaperlyRose
import com.example.ui.theme.PaperlyRoseSoft
import com.example.ui.theme.PaperlySky
import com.example.ui.theme.PaperlySkySoft
import com.example.ui.theme.PaperlyViolet
import com.example.ui.theme.PaperlyVioletSoft

private data class HomeToolItem(
    val key: String,
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val accent: Color,
    val softBg: Color,
    val badge: String? = null,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    selectedCategory: HomeToolCategory,
    recentRecords: List<PdfRecordEntity>,
    recentImages: List<RecentImageItem>,
    isInitialFilesLoading: Boolean = false,
    isInitialImagesLoading: Boolean = false,
    onCategorySelected: (HomeToolCategory) -> Unit,
    onToolSelected: (PdfToolType) -> Unit,
    onImageToolSelected: (ImageToolType) -> Unit,
    onRecentSelected: (PdfRecordEntity) -> Unit,
    onRecentImageSelected: (RecentImageItem) -> Unit,
    onSeeAllFiles: () -> Unit,
    getCachedImageThumbnail: (String, Int) -> Bitmap? = { _, _ -> null },
    loadImageThumbnail: suspend (String, Int) -> Bitmap? = { _, _ -> null },
    getCachedPdfThumbnail: (String, Int) -> Bitmap? = { _, _ -> null },
    loadPdfThumbnail: suspend (String, Int) -> Bitmap? = { _, _ -> null },
    listState: LazyListState = rememberLazyListState(),
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val haptics = rememberPaperlyHaptics()
    val pdfToolItems = remember(onToolSelected) {
        listOf(
            HomeToolItem(
                key = PdfToolType.MERGE.routeArg,
                titleRes = R.string.tool_merge_title,
                descRes = R.string.tool_merge_desc,
                icon = Icons.AutoMirrored.Filled.MergeType,
                accent = PaperlyIndigo,
                softBg = PaperlyIndigoTint,
                onClick = { onToolSelected(PdfToolType.MERGE) }
            ),
            HomeToolItem(
                key = PdfToolType.SPLIT.routeArg,
                titleRes = R.string.tool_split_title,
                descRes = R.string.tool_split_desc,
                icon = Icons.AutoMirrored.Filled.CallSplit,
                accent = PaperlySky,
                softBg = PaperlySkySoft,
                onClick = { onToolSelected(PdfToolType.SPLIT) }
            ),
            HomeToolItem(
                key = PdfToolType.COMPRESS.routeArg,
                titleRes = R.string.tool_compress_title,
                descRes = R.string.tool_compress_desc,
                icon = Icons.Filled.Compress,
                accent = PaperlyEmerald,
                softBg = PaperlyEmeraldSoft,
                onClick = { onToolSelected(PdfToolType.COMPRESS) }
            ),
            HomeToolItem(
                key = PdfToolType.SCAN.routeArg,
                titleRes = R.string.tool_scan_title,
                descRes = R.string.tool_scan_desc,
                icon = Icons.Filled.DocumentScanner,
                accent = PaperlyAmber,
                softBg = PaperlyAmberSoft,
                onClick = { onToolSelected(PdfToolType.SCAN) }
            ),
            HomeToolItem(
                key = PdfToolType.AI_SUMMARIZE.routeArg,
                titleRes = R.string.tool_ai_title,
                descRes = R.string.tool_ai_desc,
                icon = Icons.Filled.AutoAwesome,
                accent = PaperlyViolet,
                softBg = PaperlyVioletSoft,
                badge = "AI",
                onClick = { onToolSelected(PdfToolType.AI_SUMMARIZE) }
            ),
            HomeToolItem(
                key = PdfToolType.SIGN.routeArg,
                titleRes = R.string.tool_sign_title,
                descRes = R.string.tool_sign_desc,
                icon = Icons.Filled.Draw,
                accent = PaperlyRose,
                softBg = PaperlyRoseSoft,
                onClick = { onToolSelected(PdfToolType.SIGN) }
            ),
            HomeToolItem(
                key = PdfToolType.LOCK.routeArg,
                titleRes = R.string.tool_lock_title,
                descRes = R.string.tool_lock_desc,
                icon = Icons.Filled.Lock,
                accent = PaperlyEmerald,
                softBg = PaperlyEmeraldSoft,
                onClick = { onToolSelected(PdfToolType.LOCK) }
            )
        )
    }

    val imageToolItems = remember(onImageToolSelected) {
        listOf(
            HomeToolItem(
                key = ImageToolType.COMPRESS_IMAGE.routeArg,
                titleRes = R.string.img_tool_compress_title,
                descRes = R.string.img_tool_compress_desc,
                icon = Icons.Filled.PhotoSizeSelectLarge,
                accent = PaperlyEmerald,
                softBg = PaperlyEmeraldSoft,
                badge = "KB",
                onClick = { onImageToolSelected(ImageToolType.COMPRESS_IMAGE) }
            ),
            HomeToolItem(
                key = ImageToolType.CROP_RESIZE.routeArg,
                titleRes = R.string.img_tool_crop_title,
                descRes = R.string.img_tool_crop_desc,
                icon = Icons.Filled.Crop,
                accent = PaperlySky,
                softBg = PaperlySkySoft,
                onClick = { onImageToolSelected(ImageToolType.CROP_RESIZE) }
            ),
            HomeToolItem(
                key = ImageToolType.IMAGE_TO_TEXT.routeArg,
                titleRes = R.string.img_tool_ocr_title,
                descRes = R.string.img_tool_ocr_desc,
                icon = Icons.Filled.TextFields,
                accent = PaperlyIndigo,
                softBg = PaperlyIndigoTint,
                badge = "OCR",
                onClick = { onImageToolSelected(ImageToolType.IMAGE_TO_TEXT) }
            ),
            HomeToolItem(
                key = ImageToolType.TEXT_TO_IMAGE.routeArg,
                titleRes = R.string.img_tool_gen_title,
                descRes = R.string.img_tool_gen_desc,
                icon = Icons.Filled.AutoAwesome,
                accent = PaperlyViolet,
                softBg = PaperlyVioletSoft,
                badge = "AI",
                onClick = { onImageToolSelected(ImageToolType.TEXT_TO_IMAGE) }
            ),
            HomeToolItem(
                key = ImageToolType.UPSCALER.routeArg,
                titleRes = R.string.img_tool_upscale_title,
                descRes = R.string.img_tool_upscale_desc,
                icon = Icons.Filled.HighQuality,
                accent = PaperlyAmber,
                softBg = PaperlyAmberSoft,
                badge = "8×",
                onClick = { onImageToolSelected(ImageToolType.UPSCALER) }
            ),
            HomeToolItem(
                key = ImageToolType.BG_REMOVER.routeArg,
                titleRes = R.string.img_tool_bg_title,
                descRes = R.string.img_tool_bg_desc,
                icon = Icons.Filled.AutoFixHigh,
                accent = PaperlyRose,
                softBg = PaperlyRoseSoft,
                onClick = { onImageToolSelected(ImageToolType.BG_REMOVER) }
            ),
            HomeToolItem(
                key = ImageToolType.PASSPORT_SHEET.routeArg,
                titleRes = R.string.img_tool_passport_title,
                descRes = R.string.img_tool_passport_desc,
                icon = Icons.Filled.Badge,
                accent = PaperlyIndigo,
                softBg = PaperlyIndigoTint,
                onClick = { onImageToolSelected(ImageToolType.PASSPORT_SHEET) }
            )
        )
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val gridColumns = when {
            maxWidth >= 820.dp -> 4
            maxWidth >= 560.dp -> 3
            else -> 2
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 880.dp)
                .graphicsLayer {},
            contentPadding = PaddingValues(top = 6.dp, bottom = bottomContentPadding + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                val heroGradient = if (isDark) {
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF1E1B4B).copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            PaperlyIndigoTint,
                            Color(0xFFF1F3F9),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                }
                val quickScanInteraction = remember { MutableInteractionSource() }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp)
                        .testTag("home_hero_banner_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isDark) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                        } else {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(heroGradient)
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = PaperlyEmerald.copy(alpha = if (isDark) 0.18f else 0.12f),
                                border = BorderStroke(1.dp, PaperlyEmerald.copy(alpha = 0.28f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.VerifiedUser,
                                        contentDescription = null,
                                        tint = PaperlyEmerald,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    FittedSingleLineText(
                                        text = "100% Offline & Private",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PaperlyEmerald
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    haptics.selectionClick()
                                    onToolSelected(PdfToolType.SCAN)
                                },
                                interactionSource = quickScanInteraction,
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .defaultMinSize(minHeight = 40.dp)
                                    .pressScaleEffect(quickScanInteraction)
                                    .testTag("home_quick_scan_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DocumentScanner,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                FittedSingleLineText(
                                    text = "Quick Scan",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                text = stringResource(R.string.home_headline),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.home_subheadline),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Sleek Segmented Pill Switcher: 'PDF Tools' | 'Image Tools'
            item {
                ToolCategorySwitcher(
                    selectedCategory = selectedCategory,
                    onCategorySelected = { category ->
                        haptics.selectionClick()
                        onCategorySelected(category)
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            // Adaptive Tool Grid (2 cols on phone, 3/4 cols on tablet)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (selectedCategory == HomeToolCategory.ALL || selectedCategory == HomeToolCategory.PDF) {
                        ToolSectionGrid(
                            sectionTitle = stringResource(R.string.tools_section_title),
                            tools = pdfToolItems,
                            gridColumns = gridColumns
                        )
                    }
                    if (selectedCategory == HomeToolCategory.ALL || selectedCategory == HomeToolCategory.IMAGE) {
                        ToolSectionGrid(
                            sectionTitle = stringResource(R.string.image_tools_section_title),
                            tools = imageToolItems,
                            gridColumns = gridColumns
                        )
                    }
                }
            }

            // Context-aware Recent Files Carousel
            item {
                if (selectedCategory == HomeToolCategory.PDF || selectedCategory == HomeToolCategory.ALL) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.recent_pdfs_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            if (recentRecords.isNotEmpty()) {
                                TextButton(
                                    onClick = {
                                        haptics.lightTap()
                                        onSeeAllFiles()
                                    },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("recent_see_all_button")
                                ) {
                                    Text(stringResource(R.string.see_all))
                                }
                            }
                        }

                        if (recentRecords.isEmpty() && isInitialFilesLoading) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.testTag("recent_pdfs_shimmer_row")
                            ) {
                                items(3, key = { it }) {
                                    ShimmerRecentPdfCard()
                                }
                            }
                        } else if (recentRecords.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .testTag("recent_empty_card"),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    SampledDrawableImage(
                                        resId = R.drawable.img_empty_documents,
                                        contentDescription = "Empty documents illustration",
                                        maxEdgePx = 192,
                                        modifier = Modifier
                                            .size(64.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.recent_empty_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.recent_empty_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.testTag("recent_pdfs_row")
                            ) {
                                items(recentRecords.take(10), key = { it.id }) { record ->
                                    RecentPdfCard(
                                        record = record,
                                        cachedThumbnail = getCachedPdfThumbnail(record.filePath, 180),
                                        loadThumbnail = { loadPdfThumbnail(record.filePath, 180) },
                                        onClick = {
                                            haptics.selectionClick()
                                            onRecentSelected(record)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (selectedCategory == HomeToolCategory.IMAGE ||
                    (selectedCategory == HomeToolCategory.ALL && (recentImages.isNotEmpty() || isInitialImagesLoading))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(
                            top = if (selectedCategory == HomeToolCategory.ALL) 16.dp else 0.dp
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.recent_images_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        if (recentImages.isEmpty() && isInitialImagesLoading) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.testTag("recent_images_shimmer_row")
                            ) {
                                items(3, key = { it }) {
                                    ShimmerRecentImageCard()
                                }
                            }
                        } else if (recentImages.isEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .testTag("recent_images_empty_card"),
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(PaperlyIndigoTint),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Image,
                                            contentDescription = null,
                                            tint = PaperlyIndigo,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = stringResource(R.string.recent_images_empty_title),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = stringResource(R.string.recent_images_empty_subtitle),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        } else {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.testTag("recent_images_row")
                            ) {
                                items(recentImages.take(10), key = { it.filePath }) { img ->
                                    RecentImageCard(
                                        item = img,
                                        cachedThumbnail = getCachedImageThumbnail(img.filePath, 200),
                                        loadThumbnail = { loadImageThumbnail(img.filePath, 200) },
                                        onClick = {
                                            haptics.selectionClick()
                                            onRecentImageSelected(img)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolSectionGrid(
    sectionTitle: String,
    tools: List<HomeToolItem>,
    gridColumns: Int
) {
    val useCompactSeventhRow = gridColumns == 2 && tools.size == 7
    val gridItems = if (useCompactSeventhRow) tools.dropLast(1) else tools
    val seventhItem = if (useCompactSeventhRow) tools.last() else null

    Column(
        modifier = Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = sectionTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "${tools.size} tools",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        gridItems.chunked(gridColumns).forEach { rowItems ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                rowItems.forEach { spec ->
                    ToolActionCard(
                        spec = spec,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
                repeat(gridColumns - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        if (seventhItem != null) {
            FeaturedFullWidthToolCard(spec = seventhItem)
        }
    }
}

@Composable
private fun ToolCategorySwitcher(
    selectedCategory: HomeToolCategory,
    onCategorySelected: (HomeToolCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = HomeToolCategory.entries
    val selectedIndex = categories.indexOf(selectedCategory).coerceAtLeast(0)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_category_switcher"),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f))
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            val tabWidth = maxWidth / categories.size
            val indicatorOffsetX by animateDpAsState(
                targetValue = tabWidth * selectedIndex,
                animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
                label = "category_pill_offset"
            )

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 2.dp,
                modifier = Modifier
                    .offset(x = indicatorOffsetX)
                    .width(tabWidth)
                    .height(42.dp)
            ) {}

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                categories.forEach { category ->
                    val selected = category == selectedCategory
                    val icon = when (category) {
                        HomeToolCategory.ALL -> Icons.Filled.Apps
                        HomeToolCategory.PDF -> Icons.Filled.PictureAsPdf
                        HomeToolCategory.IMAGE -> Icons.Filled.Image
                    }
                    val tabContentColor by animateColorAsState(
                        targetValue = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        animationSpec = tween(durationMillis = 160, easing = EaseOutCubic),
                        label = "cat_fg_${category.name}"
                    )
                    val interactionSource = remember { MutableInteractionSource() }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .minimumInteractiveComponentSize()
                            .pressScaleEffect(interactionSource)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = androidx.compose.material3.ripple()
                            ) { onCategorySelected(category) }
                            .testTag("category_tab_${category.name.lowercase()}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = tabContentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            FittedSingleLineText(
                                text = category.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = tabContentColor
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeaturedFullWidthToolCard(
    spec: HomeToolItem
) {
    val haptics = rememberPaperlyHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = {
            haptics.selectionClick()
            spec.onClick()
        },
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .pressScaleEffect(interactionSource)
            .testTag("tool_card_${spec.key}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(spec.accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = stringResource(spec.titleRes),
                    tint = spec.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(spec.titleRes),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(spec.descRes),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun RecentPdfCard(
    record: PdfRecordEntity,
    cachedThumbnail: Bitmap?,
    loadThumbnail: suspend () -> Bitmap?,
    onClick: () -> Unit
) {
    val thumbBitmap by produceState(
        initialValue = cachedThumbnail,
        key1 = record.filePath,
        key2 = record.sizeBytes
    ) {
        if (value == null || value?.isRecycled == true) {
            value = loadThumbnail()
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .width(184.dp)
            .pressScaleEffect(interactionSource)
            .testTag("recent_pdf_card_${record.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = thumbBitmap?.takeIf { !it.isRecycled }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "${record.displayName} preview",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (record.operationTag == "Locked") {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        ShimmerBox(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = record.operationTag,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = record.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${record.pageCount}p • ${formatFileSize(record.sizeBytes)} • ${formatTimestamp(record.createdAtMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentImageCard(
    item: RecentImageItem,
    cachedThumbnail: Bitmap?,
    loadThumbnail: suspend () -> Bitmap?,
    onClick: () -> Unit
) {
    val thumbBitmap by produceState(
        initialValue = cachedThumbnail,
        key1 = item.filePath,
        key2 = item.createdAtMillis
    ) {
        if (value == null || value?.isRecycled == true) {
            value = loadThumbnail()
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = Modifier
            .width(184.dp)
            .pressScaleEffect(interactionSource)
            .testTag("recent_image_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(82.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                val bmp = thumbBitmap?.takeIf { !it.isRecycled }
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    ShimmerBox(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = item.format.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = item.displayName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "${item.width}×${item.height} • ${formatFileSize(item.sizeBytes)} • ${formatTimestamp(item.createdAtMillis)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ToolActionCard(
    spec: HomeToolItem,
    modifier: Modifier = Modifier
) {
    val haptics = rememberPaperlyHaptics()
    val interactionSource = remember { MutableInteractionSource() }
    Card(
        onClick = {
            haptics.selectionClick()
            spec.onClick()
        },
        interactionSource = interactionSource,
        modifier = modifier
            .defaultMinSize(minHeight = 132.dp)
            .pressScaleEffect(interactionSource)
            .testTag("tool_card_${spec.key}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(spec.accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = stringResource(spec.titleRes),
                        tint = spec.accent,
                        modifier = Modifier.size(23.dp)
                    )
                }

                if (spec.badge != null) {
                    Surface(
                        shape = CircleShape,
                        color = spec.accent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = spec.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = spec.accent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stringResource(spec.titleRes),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(spec.descRes),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
