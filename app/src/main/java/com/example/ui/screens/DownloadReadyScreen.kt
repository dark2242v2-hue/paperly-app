package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.font.FontWeight
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ads.findActivity
import com.example.core.formatFileSize
import com.example.core.formatReductionPercent
import com.example.core.formatSavedSummary
import com.example.data.local.PdfRecordEntity
import com.example.ui.components.FittedSingleLineText
import com.example.ui.components.ShimmerBox
import com.example.ui.components.ShimmerFileRowCard
import com.example.ui.components.SponsoredNativeAdCard
import com.example.ui.components.rememberPaperlyHaptics
import com.example.ui.theme.PaperlyEmerald
import com.example.ui.viewmodel.PaperlyViewModel
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadReadyScreen(
    recordId: Long,
    isPro: Boolean,
    isDarkMode: Boolean,
    showAhaTrialBanner: Boolean,
    viewModel: PaperlyViewModel,
    onUpgradeProClick: () -> Unit,
    onPreviewPdf: (Long) -> Unit,
    onDone: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberPaperlyHaptics()
    var record by remember { mutableStateOf<PdfRecordEntity?>(null) }
    var thumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var ahaBannerDismissed by rememberSaveable(recordId) { mutableStateOf(false) }

    val shouldRequestInAppReview by viewModel.shouldRequestInAppReview.collectAsStateWithLifecycle()

    LaunchedEffect(shouldRequestInAppReview) {
        if (shouldRequestInAppReview) {
            viewModel.consumeInAppReviewTrigger()
            val hostActivity = context.findActivity() ?: return@LaunchedEffect
            runCatching {
                val manager = ReviewManagerFactory.create(hostActivity)
                manager.requestReviewFlow().addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        manager.launchReviewFlow(hostActivity, task.result)
                    }
                }
            }
        }
    }

    LaunchedEffect(recordId) {
        isLoading = true
        val loaded = viewModel.loadRecordById(recordId)
        record = loaded
        isLoading = false
        if (loaded != null) {
            haptics.confirmSuccess()
            delay(60L)
            thumbnail = viewModel.pdfEngine.renderPageThumbnail(loaded.filePath, pageIndex = 0, maxWidth = 560)
        }
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val current = record
        if (destUri != null && current != null) {
            viewModel.saveRecordToUri(current.filePath, destUri, trackForReview = true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.download_ready_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onDone,
                        modifier = Modifier.testTag("download_ready_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 600.dp)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ShimmerBox(modifier = Modifier.size(56.dp), shape = CircleShape)
                    ShimmerBox(modifier = Modifier.size(width = 180.dp, height = 22.dp), shape = CircleShape)
                    ShimmerFileRowCard()
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
                }
            }
        } else {
            val current = record
            if (current == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Document not found", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = onDone) { Text(stringResource(R.string.back_to_home)) }
                    }
                }
            } else {
                val savedPercent = formatReductionPercent(current.originalSizeBytes, current.sizeBytes)

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 600.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Success header
                        item {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(PaperlyEmerald.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Ready",
                                        tint = PaperlyEmerald,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(R.string.download_ready_title),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = stringResource(R.string.download_ready_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Output File Card with real PDF page 1 thumbnail
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("download_ready_file_card"),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 72.dp, height = 96.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val bmp = thumbnail
                                        if (bmp != null) {
                                            Image(
                                                bitmap = bmp.asImageBitmap(),
                                                contentDescription = "PDF first page preview",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else if (current.operationTag == "Locked") {
                                            Icon(
                                                imageVector = Icons.Filled.Lock,
                                                contentDescription = "Locked PDF",
                                                tint = PaperlyEmerald,
                                                modifier = Modifier.size(30.dp)
                                            )
                                        } else {
                                            ShimmerBox(
                                                modifier = Modifier.fillMaxSize(),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer
                                            ) {
                                                Text(
                                                    text = current.operationTag,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                            if (current.operationTag == "Compressed" && savedPercent != null) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = PaperlyEmerald.copy(alpha = 0.14f)
                                                ) {
                                                    Text(
                                                        text = "-$savedPercent%",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = PaperlyEmerald,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = current.displayName,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Text(
                                            text = formatSavedSummary(
                                                originalBytes = current.originalSizeBytes,
                                                newBytes = current.sizeBytes,
                                                pageCount = current.pageCount
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (savedPercent != null && savedPercent > 0) {
                                                PaperlyEmerald
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Primary Save to Device & Share buttons
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            haptics.lightTap()
                                            createDocumentLauncher.launch(current.displayName)
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                        modifier = Modifier
                                            .weight(1.3f)
                                            .heightIn(min = 52.dp)
                                            .testTag("download_pdf_button"),
                                        shape = RoundedCornerShape(14.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Download,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        FittedSingleLineText(
                                            text = "Save to Device",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            haptics.lightTap()
                                            runCatching {
                                                val shareUri = viewModel.pdfEngine.getShareUri(current.filePath)
                                                val intent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/pdf"
                                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Share PDF"))
                                            }.onFailure {
                                                Toast.makeText(context, "Unable to share PDF right now.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 52.dp)
                                            .testTag("share_pdf_button"),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        FittedSingleLineText(
                                            text = stringResource(R.string.share_pdf),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            haptics.lightTap()
                                            onPreviewPdf(current.id)
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("preview_pdf_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Visibility,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        FittedSingleLineText(stringResource(R.string.open_viewer))
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            haptics.lightTap()
                                            val viewUri = viewModel.pdfEngine.getShareUri(current.filePath)
                                            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(viewUri, "application/pdf")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            runCatching {
                                                context.startActivity(Intent.createChooser(viewIntent, "Open PDF with"))
                                            }.onFailure {
                                                Toast.makeText(context, "No external PDF app found.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("open_with_pdf_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                            contentDescription = null,
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        FittedSingleLineText("Open With")
                                    }
                                }

                                TextButton(
                                    onClick = {
                                        haptics.lightTap()
                                        onDone()
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("done_to_home_button")
                                ) {
                                    FittedSingleLineText(stringResource(R.string.back_to_home))
                                }
                            }
                        }

                        // Trigger 2 (Aha! Moment): Subtle dismissible banner after 2 merges/splits/compressions
                        if (showAhaTrialBanner && !isPro && !ahaBannerDismissed) {
                            item {
                                AnimatedVisibility(
                                    visible = !ahaBannerDismissed,
                                    enter = expandVertically(),
                                    exit = shrinkVertically()
                                ) {
                                    Card(
                                        onClick = onUpgradeProClick,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("download_ready_aha_banner"),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                        ),
                                        border = BorderStroke(
                                            1.dp,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                        )
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.WorkspacePremium,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Text(
                                                text = "Enjoying Paperly? Remove ads & unlock AI free for 3 days →",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { ahaBannerDismissed = true },
                                                modifier = Modifier
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("aha_banner_dismiss_button")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = "Dismiss",
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Safe spacing before Sponsored NativeAd card (hidden when Pro is active)
                        if (!isPro) {
                            item {
                                Spacer(modifier = Modifier.height(8.dp))
                                SponsoredNativeAdCard(
                                    adManager = viewModel.adManager,
                                    isDarkMode = isDarkMode
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
