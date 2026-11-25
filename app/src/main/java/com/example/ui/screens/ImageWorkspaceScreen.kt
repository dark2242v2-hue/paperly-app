package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixOff
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ads.PaperlyAdManager
import com.example.core.formatFileSize
import com.example.data.model.AiImageAspectRatio
import com.example.data.model.AiImageStyle
import com.example.data.model.BgBrushStroke
import com.example.data.model.CropAspectPreset
import com.example.data.model.ImageOutputFormat
import com.example.data.model.ImageToolType
import com.example.data.model.OcrUiState
import com.example.data.model.PassportSizePreset
import com.example.data.model.PickedImageInfo
import com.example.data.model.ProcessedImageResult
import com.example.data.model.TargetSizeUnit
import com.example.data.model.UpscaleTarget
import com.example.ui.components.AnimatedOptionChip
import com.example.ui.components.AnimatedSelectableCard
import com.example.ui.components.BgTouchUpPreview
import com.example.ui.components.ColorSwatchCircle
import com.example.ui.components.CustomColorPickerDialog
import com.example.ui.components.FittedSingleLineText
import com.example.ui.components.InteractiveCropPreview
import com.example.ui.components.PassportFramingPreview
import com.example.ui.components.SponsoredNativeAdCard
import com.example.ui.components.bringIntoViewAboveKeyboard
import com.example.ui.components.rememberPaperlyHaptics
import com.example.ui.theme.PaperlyEmerald
import com.example.ui.theme.PaperlyIndigo
import com.example.ui.viewmodel.ImageWorkspaceUiState
import com.example.ui.viewmodel.PaperlyViewModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageWorkspaceScreen(
    state: ImageWorkspaceUiState,
    ocrState: OcrUiState,
    isPro: Boolean,
    isDarkMode: Boolean,
    aiImageCredits: Int,
    upscaleCredits: Int,
    viewModel: PaperlyViewModel,
    onUpgradeProClick: () -> Unit,
    onBack: () -> Unit,
    onImageResultReady: () -> Unit,
    onPdfExported: (Long) -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberPaperlyHaptics()
    val tool = state.activeTool

    LaunchedEffect(ocrState) {
        if (ocrState is OcrUiState.Success) {
            haptics.confirmSuccess()
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.pickImageForWorkspace(uri)
        }
    }

    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        if (saved && uri != null) {
            viewModel.pickImageForWorkspace(uri)
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val (_, uri) = viewModel.imageEngine.createCameraCaptureUri()
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            viewModel.postMessage("Camera permission denied. You can still pick an image from Gallery.")
        }
    }

    fun launchCameraForOcr() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            val (_, uri) = viewModel.imageEngine.createCameraCaptureUri()
            pendingCameraUri = uri
            takePictureLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val toolTitle = when (tool) {
        ImageToolType.COMPRESS_IMAGE -> stringResource(R.string.img_tool_compress_title)
        ImageToolType.CROP_RESIZE -> stringResource(R.string.img_tool_crop_title)
        ImageToolType.IMAGE_TO_TEXT -> stringResource(R.string.img_tool_ocr_title)
        ImageToolType.TEXT_TO_IMAGE -> stringResource(R.string.img_tool_gen_title)
        ImageToolType.UPSCALER -> stringResource(R.string.img_tool_upscale_title)
        ImageToolType.BG_REMOVER -> stringResource(R.string.img_tool_bg_title)
        ImageToolType.PASSPORT_SHEET -> stringResource(R.string.img_tool_passport_title)
    }

    val badgeLabel = when (tool) {
        ImageToolType.TEXT_TO_IMAGE -> "Cloud AI"
        ImageToolType.IMAGE_TO_TEXT, ImageToolType.BG_REMOVER -> "ML Kit Offline"
        else -> "100% Local"
    }

    val processedResult by viewModel.processedImageResult.collectAsStateWithLifecycle()
    var showPassportConfirmDialog by remember { mutableStateOf(false) }

    val primaryCtaLabel = when (tool) {
        ImageToolType.COMPRESS_IMAGE -> "Compress Image"
        ImageToolType.CROP_RESIZE -> "Save Cropped & Resized"
        ImageToolType.IMAGE_TO_TEXT -> ""
        ImageToolType.TEXT_TO_IMAGE -> "Generate AI Image"
        ImageToolType.UPSCALER -> "Upscale to ${state.upscaleTarget.label}"
        ImageToolType.BG_REMOVER -> "Export Cutout"
        ImageToolType.PASSPORT_SHEET -> if (state.passportCopyCount <= 1) {
            "Create Single Passport Photo"
        } else {
            "Create ${state.passportCopyCount}-Photo Sheet"
        }
    }

    val hasTextInputFields = tool == ImageToolType.COMPRESS_IMAGE ||
        tool == ImageToolType.CROP_RESIZE ||
        tool == ImageToolType.TEXT_TO_IMAGE

    val canProcess = when (tool) {
        ImageToolType.COMPRESS_IMAGE -> state.pickedImage != null &&
            (state.compressTargetValue.toDoubleOrNull() ?: 0.0) > 0.0
        ImageToolType.CROP_RESIZE -> state.pickedImage != null &&
            (state.resizeWidthText.toIntOrNull() ?: 0) >= 16 &&
            (state.resizeHeightText.toIntOrNull() ?: 0) >= 16
        ImageToolType.IMAGE_TO_TEXT -> false
        ImageToolType.TEXT_TO_IMAGE -> state.aiPrompt.trim().isNotEmpty()
        ImageToolType.UPSCALER -> state.pickedImage != null
        ImageToolType.BG_REMOVER -> state.pickedImage != null && state.cutoutPngPath != null
        ImageToolType.PASSPORT_SHEET -> state.pickedImage != null
    } && !state.processing.isProcessing

    fun triggerPrimaryAction() {
        haptics.lightTap()
        when (tool) {
            ImageToolType.COMPRESS_IMAGE -> viewModel.runImageCompression(onImageResultReady)
            ImageToolType.CROP_RESIZE -> viewModel.runCropRotateResize(onImageResultReady)
            ImageToolType.TEXT_TO_IMAGE -> viewModel.generateAiImage(onImageResultReady)
            ImageToolType.UPSCALER -> viewModel.runImageUpscale(onImageResultReady)
            ImageToolType.BG_REMOVER -> viewModel.exportBgRemovedImage(onImageResultReady)
            ImageToolType.PASSPORT_SHEET -> viewModel.generatePassportSheet {
                haptics.confirmSuccess()
                showPassportConfirmDialog = true
            }
            ImageToolType.IMAGE_TO_TEXT -> Unit
        }
    }

    if (showPassportConfirmDialog && processedResult != null) {
        PassportPreviewConfirmDialog(
            result = processedResult!!,
            viewModel = viewModel,
            onShare = {
                haptics.lightTap()
                val res = processedResult!!
                val shareUri = viewModel.imageEngine.getShareUri(res.filePath)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = res.format.mimeType
                    putExtra(Intent.EXTRA_STREAM, shareUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share passport photo"))
            },
            onSaveToGallery = {
                haptics.confirmSuccess()
                viewModel.saveResultImageToGallery()
            },
            onOpenFullResult = {
                showPassportConfirmDialog = false
                onImageResultReady()
            },
            onDismiss = { showPassportConfirmDialog = false }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = toolTitle,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = badgeLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("image_workspace_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (tool != ImageToolType.IMAGE_TO_TEXT && !hasTextInputFields) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    modifier = Modifier.graphicsLayer {}
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    ) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ImageProcessingProgressBanner(state = state)

                            Button(
                                onClick = { triggerPrimaryAction() },
                                enabled = canProcess,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("image_process_cta_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                FittedSingleLineText(
                                    text = if (state.processing.isProcessing) "Processing..." else primaryCtaLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .graphicsLayer {},
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 12.dp,
                    bottom = if (imeVisible) 140.dp else 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                state.processing.errorMessage?.let { errorMsg ->
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = errorMsg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }

                if (tool != ImageToolType.TEXT_TO_IMAGE) {
                    item {
                        ImagePickerCard(
                            pickedImage = state.pickedImage,
                            showCameraForOcr = tool == ImageToolType.IMAGE_TO_TEXT,
                            onPickFromGallery = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onSnapCamera = { launchCameraForOcr() }
                        )
                    }
                }

                when (tool) {
                    ImageToolType.COMPRESS_IMAGE -> {
                        state.pickedImage?.let { info ->
                            item {
                                ImageCompressionControlsCard(
                                    info = info,
                                    state = state,
                                    onConfigChange = viewModel::updateImageCompressionConfig,
                                    onClearPreset = viewModel::clearCompressionPresetSelection
                                )
                            }
                        }
                    }

                    ImageToolType.CROP_RESIZE -> {
                        if (state.pickedImage != null && state.previewBitmap != null) {
                            item {
                                CropAndResizeWorkspaceCard(
                                    state = state,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }

                    ImageToolType.IMAGE_TO_TEXT -> {
                        item {
                            OcrWorkspaceCard(
                                ocrState = ocrState,
                                isPro = isPro,
                                isDarkMode = isDarkMode,
                                adManager = viewModel.adManager,
                                onCopyText = { text ->
                                    haptics.lightTap()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Paperly OCR Text", text))
                                    viewModel.postMessage("Extracted text copied to clipboard")
                                },
                                onShareText = { text ->
                                    haptics.lightTap()
                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    context.startActivity(Intent.createChooser(sendIntent, "Share extracted text"))
                                },
                                onSaveAsPdf = { text ->
                                    haptics.lightTap()
                                    viewModel.saveOcrTextAsPdf(text, onPdfExported)
                                }
                            )
                        }
                    }

                    ImageToolType.TEXT_TO_IMAGE -> {
                        item {
                            AiTextToImageWorkspaceCard(
                                state = state,
                                isPro = isPro,
                                credits = aiImageCredits,
                                onUpdateConfig = viewModel::updateAiGenConfig,
                                onUpgradeClick = onUpgradeProClick
                            )
                        }
                    }

                    ImageToolType.UPSCALER -> {
                        state.pickedImage?.let { info ->
                            item {
                                UpscalerWorkspaceCard(
                                    info = info,
                                    state = state,
                                    isPro = isPro,
                                    credits = upscaleCredits,
                                    onTargetSelected = viewModel::updateUpscaleTarget,
                                    onFormatSelected = { viewModel.updateImageCompressionConfig(format = it) },
                                    onUpgradeClick = onUpgradeProClick
                                )
                            }
                        }
                    }

                    ImageToolType.BG_REMOVER -> {
                        if (state.pickedImage != null) {
                            item {
                                BgRemoverWorkspaceCard(
                                    state = state,
                                    onColorSelected = viewModel::updateBgColor,
                                    onBrushModeChanged = viewModel::setBgBrushMode,
                                    onBrushRadiusChanged = viewModel::setBgBrushRadius,
                                    onStrokeCompleted = viewModel::addBgBrushStroke,
                                    onUndoStroke = viewModel::undoBgBrushStroke
                                )
                            }
                        }
                    }

                    ImageToolType.PASSPORT_SHEET -> {
                        if (state.pickedImage != null) {
                            item {
                                PassportSheetWorkspaceCard(
                                    state = state,
                                    onUpdatePassport = viewModel::updatePassportConfig,
                                    onFramingChanged = viewModel::updatePassportFraming,
                                    onResetFraming = viewModel::resetPassportFraming
                                )
                            }
                        }
                    }
                }

                if (hasTextInputFields) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ImageProcessingProgressBanner(state = state)

                            Button(
                                onClick = { triggerPrimaryAction() },
                                enabled = canProcess,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .testTag("image_process_cta_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                FittedSingleLineText(
                                    text = if (state.processing.isProcessing) "Processing..." else primaryCtaLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageProcessingProgressBanner(state: ImageWorkspaceUiState) {
    AnimatedVisibility(visible = state.processing.isProcessing) {
        val animatedProgress by animateFloatAsState(
            targetValue = state.processing.progress.coerceIn(0f, 1f),
            label = "image_progress"
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = state.processing.message.ifBlank { "Processing..." },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(animatedProgress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
private fun PassportPreviewConfirmDialog(
    result: ProcessedImageResult,
    viewModel: PaperlyViewModel,
    onShare: () -> Unit,
    onSaveToGallery: () -> Unit,
    onOpenFullResult: () -> Unit,
    onDismiss: () -> Unit
) {
    val previewBitmap by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = result.filePath
    ) {
        value = viewModel.imageEngine.decodePreviewBitmap(result.filePath, maxEdge = 1200)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = PaperlyEmerald,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = result.operationLabel,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        val bmp = previewBitmap
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Passport output preview",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                        } else {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        }
                    }
                }

                Text(
                    text = "${result.width}×${result.height} px • ${formatFileSize(result.sizeBytes)} • ${result.format.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSaveToGallery,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("passport_dialog_save_button")
                    ) {
                        Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        FittedSingleLineText("Save to Gallery")
                    }

                    OutlinedButton(
                        onClick = onShare,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("passport_dialog_share_button")
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        FittedSingleLineText("Share")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenFullResult) {
                Text("Full View & PDF")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Edit More")
            }
        }
    )
}

@Composable
private fun ImagePickerCard(
    pickedImage: PickedImageInfo?,
    showCameraForOcr: Boolean,
    onPickFromGallery: () -> Unit,
    onSnapCamera: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pickedImage?.displayName ?: "Select an Image",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (pickedImage != null) {
                            "${pickedImage.width}×${pickedImage.height} px • ${formatFileSize(pickedImage.sizeBytes)}"
                        } else {
                            "Choose a photo cleanly using Android Photo Picker"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onPickFromGallery,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("pick_image_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.AddPhotoAlternate,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FittedSingleLineText(if (pickedImage == null) "Choose Photo" else "Change Photo")
                }

                if (showCameraForOcr) {
                    OutlinedButton(
                        onClick = onSnapCamera,
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("ocr_camera_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FittedSingleLineText("Snap Photo")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageCompressionControlsCard(
    info: PickedImageInfo,
    state: ImageWorkspaceUiState,
    onConfigChange: (String?, TargetSizeUnit?, ImageOutputFormat?, String?, Boolean) -> Unit,
    onClearPreset: () -> Unit
) {
    val haptics = rememberPaperlyHaptics()
    val presets = listOf(
        Triple("10 KB", "10", TargetSizeUnit.KB),
        Triple("20 KB", "20", TargetSizeUnit.KB),
        Triple("50 KB", "50", TargetSizeUnit.KB),
        Triple("100 KB", "100", TargetSizeUnit.KB),
        Triple("500 KB", "500", TargetSizeUnit.KB)
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        state.previewBitmap?.let { bmp ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Selected image preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .padding(10.dp)
                )
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Target File Size • Original ${formatFileSize(info.sizeBytes)}",
                    style = MaterialTheme.typography.titleSmall
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presets) { (label, valStr, unit) ->
                        val selected = state.compressSelectedPresetLabel == label
                        AnimatedOptionChip(
                            label = label,
                            selected = selected,
                            onClick = { onConfigChange(valStr, unit, null, label, false) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.compressTargetValue,
                        onValueChange = { onConfigChange(it, null, null, null, true) },
                        label = { Text("Exact Target") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .bringIntoViewAboveKeyboard()
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    onClearPreset()
                                }
                            }
                            .testTag("image_compress_target_input")
                    )

                    TargetSizeUnit.entries.forEach { unit ->
                        AnimatedOptionChip(
                            label = unit.label,
                            selected = state.compressTargetUnit == unit,
                            onClick = { onConfigChange(null, unit, null, null, false) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Text(
                    text = "Output Format",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ImageOutputFormat.JPG, ImageOutputFormat.WEBP).forEach { fmt ->
                        AnimatedOptionChip(
                            label = fmt.label,
                            selected = state.outputFormat == fmt,
                            onClick = { onConfigChange(null, null, fmt, null, false) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CropAndResizeWorkspaceCard(
    state: ImageWorkspaceUiState,
    viewModel: PaperlyViewModel
) {
    val bmp = state.previewBitmap ?: return
    val haptics = rememberPaperlyHaptics()
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        InteractiveCropPreview(
            bitmap = bmp,
            leftNorm = state.cropLeftNorm,
            topNorm = state.cropTopNorm,
            rightNorm = state.cropRightNorm,
            bottomNorm = state.cropBottomNorm,
            rotationDegrees = state.rotationDegrees,
            flipHorizontal = state.flipHorizontal,
            lockedRatio = state.cropAspectPreset.ratio,
            onCropChanged = viewModel::updateCropRect
        )

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Aspect Ratio & Orientation",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(CropAspectPreset.entries) { preset ->
                        AnimatedOptionChip(
                            label = preset.label,
                            selected = state.cropAspectPreset == preset,
                            onClick = { viewModel.selectCropAspectPreset(preset) },
                            modifier = Modifier.testTag("crop_aspect_${preset.name.lowercase()}")
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            haptics.lightTap()
                            viewModel.rotateCropImage90()
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.RotateRight, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        FittedSingleLineText("Rotate 90°")
                    }
                    OutlinedButton(
                        onClick = {
                            haptics.lightTap()
                            viewModel.toggleCropFlipHorizontal()
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                    ) {
                        Icon(Icons.Filled.Flip, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        FittedSingleLineText(if (state.flipHorizontal) "Flipped" else "Flip Horiz")
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Text(
                    text = "Exact Pixel Dimensions (px)",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.resizeWidthText,
                        onValueChange = viewModel::updateResizeWidthText,
                        label = { Text("Width px") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .bringIntoViewAboveKeyboard()
                            .testTag("crop_resize_width_input")
                    )

                    IconButton(
                        onClick = viewModel::toggleCropLockAspectRatio,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("lock_aspect_button")
                    ) {
                        Icon(
                            imageVector = if (state.lockAspectRatio) Icons.Filled.Link else Icons.Filled.LinkOff,
                            contentDescription = "Toggle aspect lock",
                            tint = if (state.lockAspectRatio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedTextField(
                        value = state.resizeHeightText,
                        onValueChange = viewModel::updateResizeHeightText,
                        label = { Text("Height px") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .bringIntoViewAboveKeyboard()
                            .testTag("crop_resize_height_input")
                    )
                }

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ImageOutputFormat.entries) { fmt ->
                        AnimatedOptionChip(
                            label = fmt.label,
                            selected = state.outputFormat == fmt,
                            onClick = { viewModel.updateImageCompressionConfig(format = fmt) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OcrWorkspaceCard(
    ocrState: OcrUiState,
    isPro: Boolean,
    isDarkMode: Boolean,
    adManager: PaperlyAdManager,
    onCopyText: (String) -> Unit,
    onShareText: (String) -> Unit,
    onSaveAsPdf: (String) -> Unit
) {
    when (ocrState) {
        OcrUiState.Idle -> {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Instant On-Device OCR", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Pick a photo or snap a document page above to extract selectable, copyable text 100% offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        OcrUiState.Scanning -> {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.5.dp)
                    Column {
                        Text("Recognizing Text...", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Running Google ML Kit Text Recognition locally",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        is OcrUiState.Error -> {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Text(
                    text = ocrState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        is OcrUiState.Success -> {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Extracted Text", style = MaterialTheme.typography.titleMedium)
                        Surface(
                            shape = CircleShape,
                            color = PaperlyEmerald.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = "${ocrState.wordCount} words • ${ocrState.charCount} chars",
                                style = MaterialTheme.typography.labelSmall,
                                color = PaperlyEmerald,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        SelectionContainer {
                            Text(
                                text = ocrState.text,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onCopyText(ocrState.text) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            FittedSingleLineText("Copy")
                        }
                        OutlinedButton(
                            onClick = { onShareText(ocrState.text) },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            FittedSingleLineText("Share")
                        }
                    }

                    Button(
                        onClick = { onSaveAsPdf(ocrState.text) },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp)
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        FittedSingleLineText("Save as PDF", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (!isPro) {
                Spacer(modifier = Modifier.height(14.dp))
                SponsoredNativeAdCard(
                    adManager = adManager,
                    isDarkMode = isDarkMode
                )
            }
        }
    }
}

@Composable
private fun AiTextToImageWorkspaceCard(
    state: ImageWorkspaceUiState,
    isPro: Boolean,
    credits: Int,
    onUpdateConfig: (String?, AiImageStyle?, AiImageAspectRatio?) -> Unit,
    onUpgradeClick: () -> Unit
) {
    val promptIdeas = listOf(
        "A serene Japanese zen garden at golden sunrise with koi pond",
        "Futuristic minimalist desk setup with warm ambient lighting",
        "Cute origami paper fox on vibrant indigo background",
        "Clean geometric mountain emblem logo in cobalt and gold"
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!isPro) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PaperlyIndigo.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, PaperlyIndigo.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Free credits left: $credits",
                        style = MaterialTheme.typography.labelLarge,
                        color = PaperlyIndigo,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = CircleShape,
                        color = PaperlyIndigo,
                        modifier = Modifier.clickable(onClick = onUpgradeClick)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text("Go Pro", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = PaperlyIndigo)
                    Text("Describe Your Image", style = MaterialTheme.typography.titleMedium)
                }

                OutlinedTextField(
                    value = state.aiPrompt,
                    onValueChange = { onUpdateConfig(it, null, null) },
                    label = { Text("Enter creative prompt") },
                    placeholder = { Text("e.g., Cozy alpine cabin under northern lights, 8k...") },
                    minLines = 3,
                    maxLines = 5,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewAboveKeyboard()
                        .testTag("ai_image_prompt_input")
                )

                Text("Quick Prompt Ideas", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(promptIdeas) { idea ->
                        AnimatedOptionChip(
                            label = idea.take(30) + "…",
                            selected = state.aiPrompt == idea,
                            onClick = { onUpdateConfig(idea, null, null) }
                        )
                    }
                }

                Text("Art Style", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AiImageStyle.entries) { style ->
                        AnimatedOptionChip(
                            label = style.label,
                            selected = state.aiStyle == style,
                            onClick = { onUpdateConfig(null, style, null) }
                        )
                    }
                }

                Text("Aspect Ratio", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AiImageAspectRatio.entries) { ratio ->
                        AnimatedOptionChip(
                            label = ratio.label,
                            selected = state.aiAspectRatio == ratio,
                            onClick = { onUpdateConfig(null, null, ratio) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpscalerWorkspaceCard(
    info: PickedImageInfo,
    state: ImageWorkspaceUiState,
    isPro: Boolean,
    credits: Int,
    onTargetSelected: (UpscaleTarget) -> Unit,
    onFormatSelected: (ImageOutputFormat) -> Unit,
    onUpgradeClick: () -> Unit
) {
    val srcMax = max(info.width, info.height)
    val aspect = info.width.toFloat() / max(1, info.height).toFloat()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!isPro) {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PaperlyIndigo.copy(alpha = 0.08f)),
                border = BorderStroke(1.dp, PaperlyIndigo.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Free upscales left: $credits",
                        style = MaterialTheme.typography.labelLarge,
                        color = PaperlyIndigo,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        shape = CircleShape,
                        color = PaperlyIndigo,
                        modifier = Modifier.clickable(onClick = onUpgradeClick)
                    ) {
                        Text(
                            text = "Unlimited Pro",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        state.previewBitmap?.let { bmp ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Source image preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(10.dp)
                )
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Select Super-Resolution Target",
                    style = MaterialTheme.typography.titleSmall
                )

                UpscaleTarget.entries.forEach { target ->
                    val selected = state.upscaleTarget == target
                    val desiredEdge = when (target) {
                        UpscaleTarget.HD_2X -> min(target.maxEdgePx, (srcMax * 2))
                        UpscaleTarget.ULTRA_4X -> min(target.maxEdgePx, (srcMax * 4))
                        UpscaleTarget.RES_8K -> 7680
                        UpscaleTarget.MAX_16K -> 15360
                    }.coerceAtLeast(srcMax)
                    val outW = if (info.width >= info.height) desiredEdge else (desiredEdge * aspect).roundToInt()
                    val outH = if (info.height >= info.width) desiredEdge else (desiredEdge / aspect).roundToInt()

                    AnimatedSelectableCard(
                        selected = selected,
                        onClick = { onTargetSelected(target) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(target.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = "Up to ${outW}×${outH} px (memory-safe multi-pass HD)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Text("Output Format", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageOutputFormat.entries.forEach { fmt ->
                        AnimatedOptionChip(
                            label = fmt.label,
                            selected = state.outputFormat == fmt,
                            onClick = { onFormatSelected(fmt) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BgRemoverWorkspaceCard(
    state: ImageWorkspaceUiState,
    onColorSelected: (Int?) -> Unit,
    onBrushModeChanged: (Boolean) -> Unit,
    onBrushRadiusChanged: (Float) -> Unit,
    onStrokeCompleted: (BgBrushStroke) -> Unit,
    onUndoStroke: () -> Unit
) {
    var showColorDialog by remember { mutableStateOf(false) }

    val presets = listOf(
        null to "Clear",
        0xFFFFFFFF.toInt() to "White",
        0xFF1E64C8.toInt() to "Blue",
        0xFFC81E1E.toInt() to "Red",
        0xFF0F172A.toInt() to "Dark",
        0xFFE5E7EB.toInt() to "Light"
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BgTouchUpPreview(
            cutoutBitmap = state.cutoutPreviewBitmap,
            originalBitmap = state.previewBitmap,
            selectedBgColorArgb = state.selectedBgColorArgb,
            strokes = state.bgBrushStrokes,
            isEraseMode = state.bgBrushModeIsErase,
            brushRadiusNorm = state.bgBrushRadiusNorm,
            onStrokeCompleted = onStrokeCompleted
        )

        // Compact Manual Touch-Up Bar
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Manual Touch-Up", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Paint on preview to refine edges",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = onUndoStroke,
                        enabled = state.bgBrushStrokes.isNotEmpty(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .heightIn(min = 40.dp)
                            .testTag("bg_touchup_undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo touch-up stroke",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Undo")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AnimatedOptionChip(
                        label = "Erase",
                        selected = state.bgBrushModeIsErase,
                        onClick = { onBrushModeChanged(true) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AutoFixOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("bg_erase_pill")
                    )
                    AnimatedOptionChip(
                        label = "Restore",
                        selected = !state.bgBrushModeIsErase,
                        onClick = { onBrushModeChanged(false) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Brush,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        modifier = Modifier.testTag("bg_restore_pill")
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "${(state.bgBrushRadiusNorm * 1000).roundToInt()}px",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Brush Size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = state.bgBrushRadiusNorm,
                        onValueChange = onBrushRadiusChanged,
                        valueRange = 0.015f..0.095f,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("bg_brush_size_slider")
                    )
                }
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Choose Backdrop", style = MaterialTheme.typography.titleSmall)

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(presets) { (argb, label) ->
                        ColorSwatchCircle(
                            color = argb?.let { Color(it) },
                            label = label,
                            selected = state.selectedBgColorArgb == argb,
                            onClick = { onColorSelected(argb) }
                        )
                    }
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable { showColorDialog = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Palette,
                                    contentDescription = "Custom Color",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Text("Custom", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }

    if (showColorDialog) {
        CustomColorPickerDialog(
            initialColorArgb = state.selectedBgColorArgb ?: 0xFF1E64C8.toInt(),
            onColorConfirmed = { argb ->
                onColorSelected(argb)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false }
        )
    }
}

@Composable
private fun PassportSheetWorkspaceCard(
    state: ImageWorkspaceUiState,
    onUpdatePassport: (Boolean?, Int?, Int?, Boolean?, PassportSizePreset?) -> Unit,
    onFramingChanged: (Float, Float, Float) -> Unit,
    onResetFraming: () -> Unit
) {
    val haptics = rememberPaperlyHaptics()
    var showColorDialog by remember { mutableStateOf(false) }

    val studioSwatches = listOf(
        0xFFFFFFFF.toInt() to "White",
        0xFF1E64C8.toInt() to "Official",
        0xFF87CEEB.toInt() to "Sky Blue",
        0xFFC81E1E.toInt() to "Crimson",
        0xFFE5E7EB.toInt() to "Gray"
    )

    val framingBitmap = if (state.passportRemoveBg && state.cutoutPreviewBitmap != null) {
        state.cutoutPreviewBitmap
    } else {
        state.previewBitmap
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Interactive Drag & Pinch-to-Zoom Framing Preview
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PassportFramingPreview(
                    bitmap = framingBitmap,
                    removeBgEnabled = state.passportRemoveBg,
                    bgColorArgb = state.passportBgColorArgb,
                    zoom = state.passportZoom,
                    offsetXNorm = state.passportOffsetXNorm,
                    offsetYNorm = state.passportOffsetYNorm,
                    onFramingChanged = onFramingChanged,
                    aspectRatio = state.passportSizePreset.aspectRatio
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("${state.passportSizePreset.shortLabel} Framing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (state.passportCopyCount <= 1) {
                            "Drag to position • Pinch to zoom (Single photo)"
                        } else {
                            "Drag to position • Pinch to zoom (${state.passportCopyCount} copies)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                haptics.selectionClick()
                                onFramingChanged(
                                    state.passportOffsetXNorm,
                                    state.passportOffsetYNorm,
                                    (state.passportZoom - 0.15f).coerceIn(0.65f, 3.2f)
                                )
                            },
                            contentPadding = PaddingValues(0.dp),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("passport_zoom_out")
                        ) {
                            Icon(Icons.Filled.Remove, contentDescription = "Zoom out", modifier = Modifier.size(18.dp))
                        }

                        OutlinedButton(
                            onClick = {
                                haptics.selectionClick()
                                onFramingChanged(
                                    state.passportOffsetXNorm,
                                    state.passportOffsetYNorm,
                                    (state.passportZoom + 0.15f).coerceIn(0.65f, 3.2f)
                                )
                            },
                            contentPadding = PaddingValues(0.dp),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("passport_zoom_in")
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Zoom in", modifier = Modifier.size(18.dp))
                        }

                        OutlinedButton(
                            onClick = {
                                haptics.selectionClick()
                                onResetFraming()
                            },
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("passport_framing_reset")
                        ) {
                            Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            FittedSingleLineText("Reset", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remove & Replace Background", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (state.passportRemoveBg) {
                                "Auto-cut portrait & apply clean studio color"
                            } else {
                                "Off — original photo background preserved"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.passportRemoveBg,
                        onCheckedChange = { enabled ->
                            haptics.selectionClick()
                            onUpdatePassport(enabled, null, null, null, null)
                        },
                        modifier = Modifier.testTag("passport_remove_bg_switch")
                    )
                }

                AnimatedVisibility(visible = state.passportRemoveBg) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(studioSwatches) { (argb, label) ->
                            ColorSwatchCircle(
                                color = Color(argb),
                                label = label,
                                selected = state.passportBgColorArgb == argb,
                                onClick = { onUpdatePassport(null, argb, null, null, null) }
                            )
                        }
                        item {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer)
                                        .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                        .clickable { showColorDialog = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Palette,
                                        contentDescription = "Custom Color",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text("Custom", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

                Text("Passport Size Preset", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PassportSizePreset.entries) { preset ->
                        AnimatedOptionChip(
                            label = preset.label,
                            selected = state.passportSizePreset == preset,
                            onClick = { onUpdatePassport(null, null, null, null, preset) },
                            modifier = Modifier.testTag("passport_preset_${preset.name.lowercase()}")
                        )
                    }
                }

                Text("Copies on Print Sheet", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(1, 4, 6, 8, 12)) { count ->
                        val chipLabel = if (count == 1) "1 Photo" else "$count Photos"
                        AnimatedOptionChip(
                            label = chipLabel,
                            selected = state.passportCopyCount == count,
                            onClick = { onUpdatePassport(null, null, count, null, null) },
                            modifier = Modifier.testTag("passport_copies_$count")
                        )
                    }
                }

                Text("Preferred Print Format", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AnimatedOptionChip(
                        label = "Print PDF + JPG",
                        selected = state.passportExportAsPdf,
                        onClick = { onUpdatePassport(null, null, null, true, null) }
                    )
                    AnimatedOptionChip(
                        label = "JPG Only",
                        selected = !state.passportExportAsPdf,
                        onClick = { onUpdatePassport(null, null, null, false, null) }
                    )
                }
            }
        }
    }

    if (showColorDialog) {
        CustomColorPickerDialog(
            initialColorArgb = state.passportBgColorArgb,
            onColorConfirmed = { argb ->
                onUpdatePassport(true, argb, null, null, null)
                showColorDialog = false
            },
            onDismiss = { showColorDialog = false }
        )
    }
}
