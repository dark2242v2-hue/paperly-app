package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import com.example.core.formatFileSize
import com.example.core.formatReductionPercent
import com.example.core.formatSavedSummary
import com.example.data.model.ImageOutputFormat
import com.example.data.model.ProcessedImageResult
import com.example.ui.components.ExportCompletionActionSheet
import com.example.ui.components.FittedSingleLineText
import com.example.ui.components.ShimmerBox
import com.example.ui.components.SplitBeforeAfterComparisonPreview
import com.example.ui.components.SponsoredNativeAdCard
import com.example.ui.components.TransparencyCheckerboardCanvas
import com.example.ui.components.rememberPaperlyHaptics
import com.example.ui.components.shareFileToWhatsApp
import com.example.ui.components.shareFileViaSystemChooser
import com.example.ui.theme.PaperlyEmerald
import com.example.ui.viewmodel.PaperlyViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageResultScreen(
    result: ProcessedImageResult?,
    isPro: Boolean,
    isDarkMode: Boolean,
    viewModel: PaperlyViewModel,
    onOpenPdfViewer: (Long) -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberPaperlyHaptics()
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showExportActionSheet by rememberSaveable(result?.filePath, result?.batchExportFilePath) {
        mutableStateOf(false)
    }

    val saveZipOrPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            result?.batchExportMimeType ?: "application/zip"
        )
    ) { destUri ->
        val srcPath = result?.batchExportFilePath
        if (destUri != null && !srcPath.isNullOrBlank()) {
            viewModel.saveRecordToUri(srcPath, destUri)
        }
    }

    LaunchedEffect(result?.filePath, result?.originalFilePath, result?.operationLabel) {
        if (result != null) {
            haptics.confirmSuccess()
        }
        delay(60L)
        previewBitmap = result?.filePath?.let { path ->
            viewModel.imageEngine.decodePreviewBitmap(path, maxEdge = 1600)
        }
        originalBitmap = if (result != null &&
            result.operationLabel.startsWith("Upscaled") &&
            !result.originalFilePath.isNullOrBlank()
        ) {
            viewModel.imageEngine.decodePreviewBitmap(result.originalFilePath, maxEdge = 1200)
        } else {
            null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = result?.operationLabel ?: "Image Ready",
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDone,
                        modifier = Modifier.testTag("image_result_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (result == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No processed image available", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onDone) { Text("Done") }
                }
            }
            return@Scaffold
        }

        val savedPercent = formatReductionPercent(result.originalSizeBytes, result.sizeBytes)
        val effectiveFilePath = result.batchExportFilePath ?: result.filePath
        val effectiveMimeType = result.batchExportMimeType ?: result.format.mimeType
        val isBatchZip = result.batchExportMimeType == "application/zip"
        val batchPdfId = if (result.batchExportMimeType == "application/pdf") result.secondaryPdfRecordId else null
        val isBatchPdf = batchPdfId != null

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 540.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Ready",
                                tint = PaperlyEmerald,
                                modifier = Modifier.size(22.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = result.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val batchCount = result.batchFileCount
                                val subtitleStats = if (batchCount > 1) {
                                    "$batchCount files • ${formatSavedSummary(result.originalSizeBytes, result.sizeBytes)}"
                                } else {
                                    "${formatSavedSummary(result.originalSizeBytes, result.sizeBytes)} • ${result.width}×${result.height} ${result.format.label}"
                                }
                                Text(
                                    text = subtitleStats,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (savedPercent != null && savedPercent > 0) {
                                        PaperlyEmerald
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        val showSplitComparison = result.operationLabel.startsWith("Upscaled") &&
                            originalBitmap != null && previewBitmap != null
                        if (showSplitComparison) {
                            SplitBeforeAfterComparisonPreview(
                                originalBitmap = originalBitmap,
                                enhancedBitmap = previewBitmap
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            ) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    if (result.format == ImageOutputFormat.PNG) {
                                        TransparencyCheckerboardCanvas(modifier = Modifier.fillMaxSize())
                                    }
                                    val bmp = previewBitmap
                                    if (bmp != null) {
                                        Image(
                                            bitmap = bmp.asImageBitmap(),
                                            contentDescription = "Processed image output",
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    } else {
                                        ShimmerBox(
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (result.originalSizeBytes != null && result.originalWidth != null && result.originalHeight != null) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "Before • ${result.originalWidth}×${result.originalHeight}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = formatFileSize(result.originalSizeBytes),
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                "After • ${result.width}×${result.height}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (savedPercent != null && savedPercent > 0) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = PaperlyEmerald.copy(alpha = 0.16f)
                                                ) {
                                                    Text(
                                                        text = "-$savedPercent%",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = PaperlyEmerald,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = formatFileSize(result.sizeBytes),
                                            style = MaterialTheme.typography.titleSmall,
                                            color = PaperlyEmerald
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            haptics.lightTap()
                            when {
                                batchPdfId != null -> onOpenPdfViewer(batchPdfId)
                                isBatchZip -> saveZipOrPdfLauncher.launch(result.displayName)
                                else -> viewModel.saveResultImageToGallery()
                            }
                        },
                        shape = RoundedCornerShape(13.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("save_image_gallery_button")
                    ) {
                        Icon(
                            imageVector = if (isBatchPdf) Icons.Filled.PictureAsPdf else Icons.Filled.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FittedSingleLineText(
                            text = when {
                                isBatchPdf -> "Open Batch PDF in Viewer"
                                isBatchZip -> "Save ZIP Archive"
                                else -> "Save to Gallery"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.lightTap()
                                runCatching {
                                    val shareUri = viewModel.imageEngine.getShareUri(effectiveFilePath)
                                    shareFileViaSystemChooser(
                                        context = context,
                                        shareUri = shareUri,
                                        mimeType = effectiveMimeType,
                                        chooserTitle = if (isBatchZip) "Share ZIP Archive" else "Share file"
                                    )
                                }.onFailure {
                                    Toast.makeText(context, "Unable to share file right now.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(13.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("share_image_button")
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            FittedSingleLineText("Share")
                        }

                        OutlinedButton(
                            onClick = {
                                haptics.lightTap()
                                if (batchPdfId != null) {
                                    onOpenPdfViewer(batchPdfId)
                                } else {
                                    val viewUri = viewModel.imageEngine.getShareUri(effectiveFilePath)
                                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(viewUri, effectiveMimeType)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    runCatching {
                                        context.startActivity(Intent.createChooser(viewIntent, "Open file with"))
                                    }.onFailure {
                                        Toast.makeText(context, "No compatible viewer app found.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            shape = RoundedCornerShape(13.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("open_image_file_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            FittedSingleLineText("Open File")
                        }

                        if (!isBatchZip && !isBatchPdf) {
                            OutlinedButton(
                                onClick = {
                                    haptics.lightTap()
                                    viewModel.exportResultImageToPdf { pdfId ->
                                        onOpenPdfViewer(pdfId)
                                    }
                                },
                                shape = RoundedCornerShape(13.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("export_image_pdf_button")
                            ) {
                                Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(5.dp))
                                FittedSingleLineText("Save as PDF")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                haptics.lightTap()
                                runCatching {
                                    val shareUri = viewModel.imageEngine.getShareUri(effectiveFilePath)
                                    shareFileToWhatsApp(
                                        context = context,
                                        shareUri = shareUri,
                                        mimeType = effectiveMimeType,
                                        chooserTitle = "Share via WhatsApp"
                                    )
                                }.onFailure {
                                    Toast.makeText(context, "Unable to share right now.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = androidx.compose.ui.graphics.Color(0xFF16A34A),
                                contentColor = androidx.compose.ui.graphics.Color.White
                            ),
                            shape = RoundedCornerShape(13.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("image_whatsapp_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            FittedSingleLineText("WhatsApp", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                haptics.lightTap()
                                showExportActionSheet = true
                            },
                            shape = RoundedCornerShape(13.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("image_rename_actions_button")
                        ) {
                            Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            FittedSingleLineText("Rename & More")
                        }
                    }

                    TextButton(
                        onClick = {
                            haptics.lightTap()
                            onDone()
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        FittedSingleLineText("Done")
                    }
                }

                if (!isPro) {
                    SponsoredNativeAdCard(
                        adManager = viewModel.adManager,
                        isDarkMode = isDarkMode
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        val savingsHeadline = when {
            savedPercent != null && savedPercent > 0 && result.originalSizeBytes != null ->
                "Saved! Reduced from ${formatFileSize(result.originalSizeBytes)} → ${formatFileSize(result.sizeBytes)} (-$savedPercent%)"
            else ->
                "${result.operationLabel} • ${formatFileSize(result.sizeBytes)}"
        }
        val beforeAfter = if (result.originalSizeBytes != null && result.originalSizeBytes > 0L) {
            "Original ${formatFileSize(result.originalSizeBytes)} → Output ${formatFileSize(result.sizeBytes)}"
        } else null

        ExportCompletionActionSheet(
            visible = showExportActionSheet,
            displayName = result.displayName,
            savingsHeadline = savingsHeadline,
            beforeAfterLine = beforeAfter,
            onOpen = {
                showExportActionSheet = false
                if (batchPdfId != null) {
                    onOpenPdfViewer(batchPdfId)
                } else {
                    val viewUri = viewModel.imageEngine.getShareUri(effectiveFilePath)
                    val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(viewUri, effectiveMimeType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(viewIntent, "Open file with"))
                    }.onFailure {
                        Toast.makeText(context, "No compatible viewer app found.", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onShareWhatsApp = {
                runCatching {
                    val shareUri = viewModel.imageEngine.getShareUri(effectiveFilePath)
                    shareFileToWhatsApp(
                        context = context,
                        shareUri = shareUri,
                        mimeType = effectiveMimeType,
                        chooserTitle = "Share via WhatsApp"
                    )
                }.onFailure {
                    Toast.makeText(context, "Unable to share right now.", Toast.LENGTH_SHORT).show()
                }
            },
            onShareSystem = {
                runCatching {
                    val shareUri = viewModel.imageEngine.getShareUri(effectiveFilePath)
                    shareFileViaSystemChooser(
                        context = context,
                        shareUri = shareUri,
                        mimeType = effectiveMimeType,
                        chooserTitle = "Share file"
                    )
                }.onFailure {
                    Toast.makeText(context, "Unable to share right now.", Toast.LENGTH_SHORT).show()
                }
            },
            onRename = { newName ->
                viewModel.renameProcessedImageResult(newName).isSuccess
            },
            onDismiss = { showExportActionSheet = false }
        )
    }
}
