package com.example.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.ads.findActivity
import com.example.core.formatFileSize
import com.example.data.local.PdfRecordEntity
import com.example.data.model.AiSummaryUiState
import com.example.data.model.CompressionLevel
import com.example.data.model.PdfToolType
import com.example.data.model.PickedPdfInfo
import com.example.data.model.SignaturePlacement
import com.example.data.model.TargetSizeUnit
import com.example.ui.components.SignaturePad
import com.example.ui.components.renderSignatureBitmap
import com.example.ui.theme.PaperlyEmerald
import com.example.ui.theme.PaperlyIndigo
import com.example.ui.theme.PaperlyViolet
import com.example.ui.viewmodel.PaperlyViewModel
import com.example.ui.viewmodel.WorkspaceUiState
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolWorkspaceScreen(
    workspace: WorkspaceUiState,
    recentRecords: List<PdfRecordEntity>,
    aiSummaryState: AiSummaryUiState,
    isPro: Boolean,
    aiSummaryCredits: Int,
    onUpgradeProClick: () -> Unit,
    onOpenMultiShotCamera: () -> Unit,
    viewModel: PaperlyViewModel,
    onBack: () -> Unit,
    onProcessingComplete: (Long) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val tool = workspace.activeTool

    val signatureStrokes = remember { mutableStateListOf<List<Offset>>() }
    var signatureCanvasSize by remember { mutableStateOf(IntSize.Zero) }
    var isLaunchingSmartScanner by remember { mutableStateOf(false) }

    val openMultiplePdfsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        viewModel.addPickedPdfUris(uris)
    }

    val openSinglePdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.addPickedPdfUris(listOf(uri))
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importScanImageUris(uris, fromSmartScan = false)
        }
    }

    val smartScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        isLaunchingSmartScanner = false
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val pageUris = scanResult?.pages?.map { it.imageUri }.orEmpty()
            if (pageUris.isNotEmpty()) {
                viewModel.importScanImageUris(pageUris, fromSmartScan = true)
            }
        }
    }

    fun launchSmartDocumentScanner() {
        val hostActivity = activity
        if (hostActivity == null) {
            viewModel.postMessage("Unable to launch Smart Scanner right now.")
            return
        }
        isLaunchingSmartScanner = true
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .build()
        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(hostActivity)
            .addOnSuccessListener { intentSender ->
                smartScannerLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { err ->
                isLaunchingSmartScanner = false
                viewModel.postMessage(
                    err.localizedMessage ?: "Smart Document Scanner is unavailable on this device. Try Normal Photo mode."
                )
            }
    }

    val toolTitle = when (tool) {
        PdfToolType.MERGE -> stringResource(R.string.tool_merge_title)
        PdfToolType.SPLIT -> stringResource(R.string.tool_split_title)
        PdfToolType.COMPRESS -> stringResource(R.string.tool_compress_title)
        PdfToolType.SCAN -> stringResource(R.string.tool_scan_title)
        PdfToolType.AI_SUMMARIZE -> stringResource(R.string.tool_ai_title)
        PdfToolType.SIGN -> stringResource(R.string.tool_sign_title)
        PdfToolType.LOCK -> stringResource(R.string.tool_lock_title)
    }

    val canProcess = when (tool) {
        PdfToolType.MERGE -> workspace.selectedPdfs.size >= 2
        PdfToolType.SPLIT -> workspace.selectedPdfs.isNotEmpty() && workspace.splitPageRange.isNotBlank()
        PdfToolType.COMPRESS -> workspace.selectedPdfs.isNotEmpty()
        PdfToolType.SCAN -> workspace.scanImageUris.isNotEmpty()
        PdfToolType.SIGN -> workspace.selectedPdfs.isNotEmpty() && signatureStrokes.isNotEmpty()
        PdfToolType.LOCK -> workspace.selectedPdfs.isNotEmpty() &&
            workspace.lockPassword.length >= 4 &&
            workspace.lockPassword == workspace.lockConfirmPassword
        PdfToolType.AI_SUMMARIZE -> workspace.selectedPdfs.isNotEmpty()
    } && !workspace.processing.isProcessing

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = toolTitle, style = MaterialTheme.typography.titleLarge)
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = if (tool == PdfToolType.AI_SUMMARIZE) "Gemini AI" else "100% Local",
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
                        modifier = Modifier.testTag("workspace_back_button")
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
            val showBottomBar = tool != PdfToolType.AI_SUMMARIZE &&
                !(tool == PdfToolType.SCAN && workspace.scanImageUris.isEmpty() && !workspace.processing.isProcessing)
            if (showBottomBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp
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
                            AnimatedVisibility(visible = workspace.processing.isProcessing) {
                                val animatedProgress by animateFloatAsState(
                                    targetValue = workspace.processing.progress.coerceIn(0f, 1f),
                                    label = "pdf_progress"
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = workspace.processing.message.ifBlank { "Processing..." },
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
                                            .testTag("process_progress_bar")
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    val sigBmp = if (tool == PdfToolType.SIGN) {
                                        renderSignatureBitmap(signatureStrokes, signatureCanvasSize)
                                    } else {
                                        null
                                    }
                                    viewModel.processActiveTool(
                                        signatureBitmap = sigBmp,
                                        onSuccess = onProcessingComplete
                                    )
                                },
                                enabled = canProcess,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("process_pdf_button"),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text(
                                    text = if (workspace.processing.isProcessing) "Processing..." else stringResource(R.string.process_pdf),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Error banner
                workspace.processing.errorMessage?.let { errorMsg ->
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

                if (tool == PdfToolType.SCAN) {
                    // Scan to PDF workspace with Smart Scan + Normal Photo Multi-Shot mode selector
                    item {
                        ScanWorkspaceContent(
                            imageUris = workspace.scanImageUris,
                            enhanceContrast = workspace.scanEnhanceContrast,
                            outputName = workspace.outputFileName,
                            isLaunchingSmartScanner = isLaunchingSmartScanner,
                            onSmartScan = { launchSmartDocumentScanner() },
                            onOpenMultiShotCamera = onOpenMultiShotCamera,
                            onPickFromGallery = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onMoveLeft = viewModel::moveScanImageLeft,
                            onMoveRight = viewModel::moveScanImageRight,
                            onRemoveImage = viewModel::removeScanImageAt,
                            onClearAll = viewModel::clearScanImages,
                            onToggleEnhance = viewModel::toggleScanEnhance,
                            onOutputNameChange = viewModel::updateOutputFileName
                        )
                    }
                } else {
                    // PDF File Picker Card
                    item {
                        PdfFilePickerSection(
                            tool = tool,
                            selectedPdfs = workspace.selectedPdfs,
                            recentRecords = recentRecords,
                            onPickFromDevice = {
                                if (tool == PdfToolType.MERGE) {
                                    openMultiplePdfsLauncher.launch(arrayOf("application/pdf"))
                                } else {
                                    openSinglePdfLauncher.launch(arrayOf("application/pdf"))
                                }
                            },
                            onPickRecent = viewModel::selectExistingRecord
                        )
                    }

                    // Selected PDF List (with reorder/remove for Merge, or file badge for single-file tools)
                    if (workspace.selectedPdfs.isNotEmpty()) {
                        itemsIndexed(workspace.selectedPdfs, key = { idx, item -> "${item.id}_$idx" }) { index, pdf ->
                            SelectedPdfItemCard(
                                pdf = pdf,
                                index = index,
                                total = workspace.selectedPdfs.size,
                                showReorder = tool == PdfToolType.MERGE,
                                onMoveUp = { viewModel.movePdfUp(index) },
                                onMoveDown = { viewModel.movePdfDown(index) },
                                onRemove = { viewModel.removePdfAt(index) }
                            )
                        }
                    }

                    // Tool-specific controls
                    when (tool) {
                        PdfToolType.MERGE -> {
                            item {
                                OutputNameField(
                                    value = workspace.outputFileName,
                                    onValueChange = viewModel::updateOutputFileName,
                                    helper = if (workspace.selectedPdfs.size < 2) "Select at least 2 PDF files to merge." else "Files will merge in the exact order shown above."
                                )
                            }
                        }

                        PdfToolType.SPLIT -> {
                            workspace.selectedPdfs.firstOrNull()?.let { pdf ->
                                item {
                                    SplitControlsCard(
                                        pdf = pdf,
                                        workspace = workspace,
                                        onRangeChange = viewModel::updateSplitPageRange,
                                        onOutputNameChange = viewModel::updateOutputFileName
                                    )
                                }
                            }
                        }

                        PdfToolType.COMPRESS -> {
                            workspace.selectedPdfs.firstOrNull()?.let { pdf ->
                                item {
                                    CompressControlsCard(
                                        pdf = pdf,
                                        selectedLevel = workspace.compressionLevel,
                                        useExactTarget = workspace.compressUseExactTarget,
                                        targetValue = workspace.compressTargetValue,
                                        targetUnit = workspace.compressTargetUnit,
                                        outputName = workspace.outputFileName,
                                        onSelectLevel = viewModel::updateCompressionLevel,
                                        onTargetConfigChange = viewModel::updatePdfTargetCompression,
                                        onOutputNameChange = viewModel::updateOutputFileName
                                    )
                                }
                            }
                        }

                        PdfToolType.SIGN -> {
                            workspace.selectedPdfs.firstOrNull()?.let { pdf ->
                                item {
                                    SignControlsCard(
                                        pdf = pdf,
                                        workspace = workspace,
                                        strokes = signatureStrokes,
                                        canvasSize = signatureCanvasSize,
                                        onPageChange = viewModel::updateSignTargetPage,
                                        onPlacementChange = viewModel::updateSignPlacement,
                                        onTransformChange = viewModel::updateSignTransform,
                                        onStrokeAdded = { signatureStrokes.add(it) },
                                        onClearStrokes = { signatureStrokes.clear() },
                                        onCanvasSizeChanged = { signatureCanvasSize = it },
                                        onOutputNameChange = viewModel::updateOutputFileName
                                    )
                                }
                            }
                        }

                        PdfToolType.LOCK -> {
                            workspace.selectedPdfs.firstOrNull()?.let { _ ->
                                item {
                                    LockControlsCard(
                                        password = workspace.lockPassword,
                                        confirmPassword = workspace.lockConfirmPassword,
                                        outputName = workspace.outputFileName,
                                        onPasswordChange = viewModel::updateLockPassword,
                                        onConfirmPasswordChange = viewModel::updateLockConfirmPassword,
                                        onOutputNameChange = viewModel::updateOutputFileName
                                    )
                                }
                            }
                        }

                        PdfToolType.AI_SUMMARIZE -> {
                            item {
                                AiSummarizeWorkspaceSection(
                                    selectedPdf = workspace.selectedPdfs.firstOrNull(),
                                    aiState = aiSummaryState,
                                    isPro = isPro,
                                    aiSummaryCredits = aiSummaryCredits,
                                    onSummarizeClick = viewModel::summarizeSelectedPdf,
                                    onUpgradeProClick = onUpgradeProClick,
                                    onCopySummary = { summaryText ->
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(
                                            ClipData.newPlainText(
                                                "Paperly AI Summary",
                                                stripMarkdownForPlainText(summaryText)
                                            )
                                        )
                                        viewModel.postMessage("Summary copied to clipboard")
                                    },
                                    onExportPdf = { fileName, summaryText ->
                                        viewModel.exportSummaryAsPdf(
                                            sourceFileName = fileName,
                                            summaryText = summaryText,
                                            onSuccess = onProcessingComplete
                                        )
                                    }
                                )
                            }
                        }

                        PdfToolType.SCAN -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfFilePickerSection(
    tool: PdfToolType,
    selectedPdfs: List<PickedPdfInfo>,
    recentRecords: List<PdfRecordEntity>,
    onPickFromDevice: () -> Unit,
    onPickRecent: (PdfRecordEntity) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (tool == PdfToolType.MERGE) "Select PDFs to merge" else "Select a PDF file",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (tool == PdfToolType.MERGE) {
                            "${selectedPdfs.size} file(s) queued"
                        } else {
                            "Pick from device storage or your recent Paperly files"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Button(
                onClick = onPickFromDevice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("pick_pdf_file_button"),
                shape = RoundedCornerShape(13.dp)
            ) {
                Icon(
                    imageVector = if (selectedPdfs.isEmpty()) Icons.Filled.UploadFile else Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (tool == PdfToolType.MERGE) {
                        if (selectedPdfs.isEmpty()) "Choose PDF Files" else "Add More PDFs"
                    } else {
                        if (selectedPdfs.isEmpty()) "Choose PDF File" else "Replace PDF File"
                    }
                )
            }

            if (recentRecords.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Quick pick from Recent PDFs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(recentRecords.take(6), key = { it.id }) { rec ->
                            FilterChip(
                                selected = false,
                                onClick = { onPickRecent(rec) },
                                label = {
                                    Text(
                                        text = rec.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 150.dp)
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedPdfItemCard(
    pdf: PickedPdfInfo,
    index: Int,
    total: Int,
    showReorder: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("selected_pdf_item_$index"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pdf.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${pdf.pageCount} page(s) • ${formatFileSize(pdf.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (showReorder) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = index > 0,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("move_up_$index")
                ) {
                    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = index < total - 1,
                    modifier = Modifier.minimumInteractiveComponentSize().testTag("move_down_$index")
                ) {
                    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                }
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("remove_pdf_$index")
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Remove ${pdf.displayName}")
            }
        }
    }
}

@Composable
private fun SplitControlsCard(
    pdf: PickedPdfInfo,
    workspace: WorkspaceUiState,
    onRangeChange: (String) -> Unit,
    onOutputNameChange: (String) -> Unit
) {
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
                text = "Pages to Extract (1–${pdf.pageCount})",
                style = MaterialTheme.typography.titleSmall
            )

            OutlinedTextField(
                value = workspace.splitPageRange,
                onValueChange = onRangeChange,
                label = { Text("Page range (e.g. 1-3, 5)") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("split_range_input")
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val presets = buildList {
                    add("Page 1" to "1")
                    if (pdf.pageCount >= 2) add("First 2" to "1-2")
                    if (pdf.pageCount >= 3) add("Last Page" to "${pdf.pageCount}")
                    add("All" to "all")
                }
                items(presets) { (label, value) ->
                    FilterChip(
                        selected = workspace.splitPageRange.equals(value, ignoreCase = true),
                        onClick = { onRangeChange(value) },
                        label = { Text(label) }
                    )
                }
            }

            workspace.previewThumbnail?.let { bmp ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Page 1 preview",
                        modifier = Modifier
                            .size(width = 56.dp, height = 74.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = "Page 1 preview shown. Extracted pages preserve original vector quality.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutputNameField(
                value = workspace.outputFileName,
                onValueChange = onOutputNameChange,
                helper = null
            )
        }
    }
}

@Composable
private fun CompressControlsCard(
    pdf: PickedPdfInfo,
    selectedLevel: CompressionLevel,
    useExactTarget: Boolean,
    targetValue: String,
    targetUnit: TargetSizeUnit,
    outputName: String,
    onSelectLevel: (CompressionLevel) -> Unit,
    onTargetConfigChange: (Boolean, String, TargetSizeUnit) -> Unit,
    onOutputNameChange: (String) -> Unit
) {
    val presets = listOf(
        Triple("50 KB", "50", TargetSizeUnit.KB),
        Triple("100 KB", "100", TargetSizeUnit.KB),
        Triple("200 KB", "200", TargetSizeUnit.KB),
        Triple("500 KB", "500", TargetSizeUnit.KB),
        Triple("1 MB", "1", TargetSizeUnit.MB)
    )

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
                text = "Compression Level • Original ${formatFileSize(pdf.sizeBytes)}",
                style = MaterialTheme.typography.titleSmall
            )

            CompressionLevel.entries.forEach { level ->
                val selected = !useExactTarget && level == selectedLevel
                Card(
                    onClick = { onSelectLevel(level) },
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        }
                    ),
                    border = BorderStroke(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compress_level_${level.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(level.label, style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = level.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (selected) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))

            Card(
                onClick = { onTargetConfigChange(true, targetValue.ifBlank { "200" }, targetUnit) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (useExactTarget) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    }
                ),
                border = BorderStroke(
                    width = if (useExactTarget) 1.5.dp else 1.dp,
                    color = if (useExactTarget) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pdf_exact_target_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Exact Target File Size",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Ideal for government forms & job portals",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (useExactTarget) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = "Exact Target Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(presets) { (label, valueStr, unit) ->
                            val isSelected = useExactTarget && targetValue == valueStr && targetUnit == unit
                            FilterChip(
                                selected = isSelected,
                                onClick = { onTargetConfigChange(true, valueStr, unit) },
                                label = { Text(label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = targetValue,
                            onValueChange = { onTargetConfigChange(true, it, targetUnit) },
                            label = { Text("Target Size") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("pdf_target_size_input")
                        )
                        TargetSizeUnit.entries.forEach { unit ->
                            val unitSelected = useExactTarget && targetUnit == unit
                            FilterChip(
                                selected = unitSelected,
                                onClick = {
                                    onTargetConfigChange(true, targetValue.ifBlank { "200" }, unit)
                                },
                                label = {
                                    Text(
                                        text = unit.label,
                                        fontWeight = if (unitSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                modifier = Modifier.height(48.dp)
                            )
                        }
                    }
                }
            }

            OutputNameField(
                value = outputName,
                onValueChange = onOutputNameChange,
                helper = null
            )
        }
    }
}

@Composable
private fun ScanWorkspaceContent(
    imageUris: List<Uri>,
    enhanceContrast: Boolean,
    outputName: String,
    isLaunchingSmartScanner: Boolean,
    onSmartScan: () -> Unit,
    onOpenMultiShotCamera: () -> Unit,
    onPickFromGallery: () -> Unit,
    onMoveLeft: (Int) -> Unit,
    onMoveRight: (Int) -> Unit,
    onRemoveImage: (Int) -> Unit,
    onClearAll: () -> Unit,
    onToggleEnhance: (Boolean) -> Unit,
    onOutputNameChange: (String) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 620.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (imageUris.isEmpty()) "Choose Scan Mode" else "Add More Pages",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Card 1: Smart Document Scan (Recommended)
            ScanModeOptionCard(
                title = stringResource(R.string.scan_mode_smart_title),
                subtitle = stringResource(R.string.scan_mode_smart_desc),
                icon = Icons.Filled.DocumentScanner,
                iconTint = MaterialTheme.colorScheme.primary,
                iconContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                badgeText = stringResource(R.string.scan_mode_smart_badge),
                pillTags = if (isLaunchingSmartScanner) {
                    listOf("Opening Smart Scanner…")
                } else {
                    listOf("Auto-crop", "Straighten", "Clean")
                },
                highlighted = true,
                enabled = !isLaunchingSmartScanner,
                isLoading = isLaunchingSmartScanner,
                testTagName = "scan_mode_smart_card",
                onClick = onSmartScan
            )

            // Card 2: Normal Camera (Multi-Shot)
            ScanModeOptionCard(
                title = stringResource(R.string.scan_mode_normal_title),
                subtitle = stringResource(R.string.scan_mode_normal_desc),
                icon = Icons.Filled.CameraAlt,
                iconTint = MaterialTheme.colorScheme.secondary,
                iconContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                badgeText = null,
                pillTags = listOf("Multi-Shot", "Live Strip"),
                highlighted = false,
                enabled = true,
                isLoading = false,
                testTagName = "scan_mode_normal_card",
                onClick = onOpenMultiShotCamera
            )

            // Card 3: Import from Gallery
            ScanModeOptionCard(
                title = stringResource(R.string.scan_mode_gallery_title),
                subtitle = stringResource(R.string.scan_mode_gallery_desc),
                icon = Icons.Filled.PhotoLibrary,
                iconTint = MaterialTheme.colorScheme.tertiary,
                iconContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                badgeText = null,
                pillTags = listOf("Multi-Select", "Photos & Screenshots"),
                highlighted = false,
                enabled = true,
                isLoading = false,
                testTagName = "scan_mode_gallery_card",
                onClick = onPickFromGallery
            )

            if (imageUris.isNotEmpty()) {
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${imageUris.size} page(s) ready",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Reorder, remove, or add more pages before creating your PDF",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onClearAll,
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("scan_clear_all_button")
                        ) {
                            Text("Clear all")
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(
                            items = imageUris,
                            key = { idx, uri -> "${uri}_$idx" }
                        ) { idx, uri ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(width = 114.dp, height = 152.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    AsyncImage(
                                        model = uri,
                                        contentDescription = "Scanned page ${idx + 1}",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                    ) {
                                        Text(
                                            text = "${idx + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveImage(idx) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .testTag("remove_scan_page_$idx")
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Filled.Close,
                                                    contentDescription = "Remove page ${idx + 1}",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        onClick = { onMoveLeft(idx) },
                                        enabled = idx > 0,
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("move_scan_left_$idx")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                                contentDescription = "Move page ${idx + 1} earlier",
                                                modifier = Modifier.size(22.dp),
                                                tint = if (idx > 0) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                }
                                            )
                                        }
                                    }
                                    Surface(
                                        onClick = { onMoveRight(idx) },
                                        enabled = idx < imageUris.lastIndex,
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag("move_scan_right_$idx")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                contentDescription = "Move page ${idx + 1} later",
                                                modifier = Modifier.size(22.dp),
                                                tint = if (idx < imageUris.lastIndex) {
                                                    MaterialTheme.colorScheme.onSurface
                                                } else {
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("scan_color_mode_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.AutoFixHigh,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Scan Color Mode",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (enhanceContrast) {
                                            "High-contrast black & white document filter"
                                        } else {
                                            "Keeps natural or Smart Scanner colors"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val originalSelected = !enhanceContrast
                                    Surface(
                                        onClick = { onToggleEnhance(false) },
                                        shape = CircleShape,
                                        color = if (originalSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("scan_color_mode_original")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "Original",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (originalSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (originalSelected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }

                                    val bwSelected = enhanceContrast
                                    Surface(
                                        onClick = { onToggleEnhance(true) },
                                        shape = CircleShape,
                                        color = if (bwSelected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            Color.Transparent
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                            .testTag("scan_color_mode_bw")
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "Crisp B&W",
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = if (bwSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (bwSelected) {
                                                    MaterialTheme.colorScheme.onPrimary
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    OutputNameField(
                        value = outputName,
                        onValueChange = onOutputNameChange,
                        helper = null
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun ScanModeOptionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconContainerColor: Color,
    badgeText: String?,
    pillTags: List<String>,
    highlighted: Boolean,
    enabled: Boolean,
    isLoading: Boolean,
    testTagName: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            width = if (highlighted) 1.5.dp else 1.dp,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.outline
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTagName)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconContainerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (badgeText != null) {
                        Surface(
                            shape = CircleShape,
                            color = PaperlyEmerald.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                color = PaperlyEmerald,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    pillTags.forEach { tag ->
                        Surface(
                            shape = CircleShape,
                            color = if (highlighted) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.11f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (highlighted) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (highlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun SignControlsCard(
    pdf: PickedPdfInfo,
    workspace: WorkspaceUiState,
    strokes: List<List<Offset>>,
    canvasSize: IntSize,
    onPageChange: (Int) -> Unit,
    onPlacementChange: (SignaturePlacement) -> Unit,
    onTransformChange: (Float, Float, Float) -> Unit,
    onStrokeAdded: (List<Offset>) -> Unit,
    onClearStrokes: () -> Unit,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onOutputNameChange: (String) -> Unit
) {
    val signaturePreviewBmp = remember(strokes.toList(), canvasSize) {
        renderSignatureBitmap(strokes, canvasSize)
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
            SignaturePad(
                strokes = strokes,
                onStrokeAdded = onStrokeAdded,
                onClear = onClearStrokes,
                onSizeChanged = onCanvasSizeChanged
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Position & Resize on Page", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = if (signaturePreviewBmp != null) {
                            "Drag signature box or corner handle on preview"
                        } else {
                            "Draw your signature above, then drag to place"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = { onPageChange(workspace.signTargetPage - 1) },
                        enabled = workspace.signTargetPage > 1,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("-")
                    }
                    Text(
                        text = "${workspace.signTargetPage}/${pdf.pageCount}",
                        style = MaterialTheme.typography.labelLarge
                    )
                    OutlinedButton(
                        onClick = { onPageChange(workspace.signTargetPage + 1) },
                        enabled = workspace.signTargetPage < pdf.pageCount,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("+")
                    }
                }
            }

            workspace.previewThumbnail?.let { bmp ->
                val pageAspect = bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(pageAspect.coerceIn(0.55f, 1.6f))
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(14.dp))
                        .background(Color.White)
                ) {
                    val density = LocalDensity.current
                    val pageW = with(density) { maxWidth.toPx() }
                    val pageH = with(density) { maxHeight.toPx() }

                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Target page preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.FillBounds
                    )

                    if (signaturePreviewBmp != null) {
                        val sigAspect = signaturePreviewBmp.height.toFloat() /
                            signaturePreviewBmp.width.coerceAtLeast(1).toFloat()
                        val sigW = pageW * workspace.signWidthFraction.coerceIn(0.12f, 0.75f)
                        val sigH = (sigW * sigAspect).coerceAtMost(pageH * 0.65f)
                        val finalSigW = sigH / sigAspect.coerceAtLeast(0.01f)

                        val centerX = (workspace.signCenterXNorm * pageW)
                            .coerceIn(finalSigW / 2f, (pageW - finalSigW / 2f).coerceAtLeast(finalSigW / 2f))
                        val centerY = (workspace.signCenterYNorm * pageH)
                            .coerceIn(sigH / 2f, (pageH - sigH / 2f).coerceAtLeast(sigH / 2f))

                        val topLeftX = (centerX - finalSigW / 2f).roundToInt()
                        val topLeftY = (centerY - sigH / 2f).roundToInt()

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(topLeftX, topLeftY) }
                                .size(
                                    width = with(density) { finalSigW.toDp() },
                                    height = with(density) { sigH.toDp() }
                                )
                                .border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .background(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(6.dp)
                                )
                                .pointerInput(pageW, pageH, workspace.signWidthFraction) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val nextX = (workspace.signCenterXNorm + dragAmount.x / pageW)
                                            .coerceIn(0.08f, 0.92f)
                                        val nextY = (workspace.signCenterYNorm + dragAmount.y / pageH)
                                            .coerceIn(0.08f, 0.92f)
                                        onTransformChange(nextX, nextY, workspace.signWidthFraction)
                                    }
                                }
                                .testTag("draggable_signature_box")
                        ) {
                            Image(
                                bitmap = signaturePreviewBmp.asImageBitmap(),
                                contentDescription = "Draggable signature",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                contentScale = ContentScale.Fit
                            )

                            // Bottom-right resize handle
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .size(22.dp)
                                    .pointerInput(pageW) {
                                        detectDragGestures { change, dragAmount ->
                                            change.consume()
                                            val deltaFrac = dragAmount.x / pageW
                                            val nextWidth = (workspace.signWidthFraction + deltaFrac)
                                                .coerceIn(0.12f, 0.72f)
                                            onTransformChange(
                                                workspace.signCenterXNorm,
                                                workspace.signCenterYNorm,
                                                nextWidth
                                            )
                                        }
                                    }
                                    .testTag("signature_resize_handle")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.OpenWith,
                                        contentDescription = "Resize signature",
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Accessible size slider & quick snap presets
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Signature Size",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${(workspace.signWidthFraction * 100).roundToInt()}% page width",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Slider(
                    value = workspace.signWidthFraction,
                    onValueChange = { newWidth ->
                        onTransformChange(workspace.signCenterXNorm, workspace.signCenterYNorm, newWidth)
                    },
                    valueRange = 0.12f..0.72f,
                    modifier = Modifier.testTag("signature_size_slider")
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Quick Snap Position", style = MaterialTheme.typography.labelMedium)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(SignaturePlacement.entries) { placement ->
                        FilterChip(
                            selected = workspace.signPlacement == placement,
                            onClick = { onPlacementChange(placement) },
                            label = { Text(placement.label) }
                        )
                    }
                }
            }

            OutputNameField(
                value = workspace.outputFileName,
                onValueChange = onOutputNameChange,
                helper = null
            )
        }
    }
}

@Composable
private fun LockControlsCard(
    password: String,
    confirmPassword: String,
    outputName: String,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    onOutputNameChange: (String) -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    val hasMismatch = confirmPassword.isNotEmpty() && password != confirmPassword
    val tooShort = password.isNotEmpty() && password.length < 4

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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PaperlyEmerald.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = null,
                        tint = PaperlyEmerald,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text("Set PDF Password", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "128-bit encryption • Processed 100% locally",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text("Password (min 4 characters)") },
                singleLine = true,
                isError = tooShort,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password"
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lock_password_input")
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = onConfirmPasswordChange,
                label = { Text("Confirm password") },
                singleLine = true,
                isError = hasMismatch,
                supportingText = when {
                    tooShort -> { { Text("Use at least 4 characters") } }
                    hasMismatch -> { { Text("Passwords do not match") } }
                    else -> null
                },
                visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                trailingIcon = {
                    IconButton(onClick = { confirmVisible = !confirmVisible }) {
                        Icon(
                            imageVector = if (confirmVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (confirmVisible) "Hide confirm password" else "Show confirm password"
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("lock_confirm_password_input")
            )

            OutputNameField(
                value = outputName,
                onValueChange = onOutputNameChange,
                helper = "Anyone opening this PDF will be prompted for your password."
            )
        }
    }
}

private sealed interface SummaryBlock {
    data class Heading(val title: String) : SummaryBlock
    data class Bullet(val content: String) : SummaryBlock
    data class Numbered(val prefix: String, val content: String) : SummaryBlock
    data class Paragraph(val content: String) : SummaryBlock
}

private val NUMBERED_ITEM_REGEX = Regex("^(\\d+[.)])\\s+(.*)$")
private val BOLD_SEGMENT_REGEX = Regex("\\*\\*(.+?)\\*\\*")

private fun parseSummaryBlocks(raw: String): List<SummaryBlock> {
    return raw.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { line ->
            val numberedMatch = NUMBERED_ITEM_REGEX.matchEntire(line)
            val looksLikePlainHeading = line.endsWith(":") &&
                !line.startsWith("•") &&
                !line.startsWith("- ") &&
                !line.startsWith("* ") &&
                numberedMatch == null &&
                line.length <= 64
            when {
                line.startsWith("#") || looksLikePlainHeading -> {
                    val cleanTitle = line.trimStart('#', ' ')
                        .replace("**", "")
                        .trim()
                    SummaryBlock.Heading(cleanTitle)
                }
                line.startsWith("- ") || line.startsWith("* ") || line.startsWith("•") -> {
                    val body = line.removePrefix("- ")
                        .removePrefix("* ")
                        .removePrefix("•")
                        .trim()
                    SummaryBlock.Bullet(body)
                }
                numberedMatch != null -> {
                    SummaryBlock.Numbered(
                        prefix = numberedMatch.groupValues[1],
                        content = numberedMatch.groupValues[2].trim()
                    )
                }
                else -> SummaryBlock.Paragraph(line)
            }
        }
}

private fun parseBoldMarkdown(text: String, boldColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        for (match in BOLD_SEGMENT_REGEX.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first).replace("**", ""))
            }
            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = boldColor)) {
                append(match.groupValues[1])
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor).replace("**", ""))
        }
    }
}

private fun stripMarkdownForPlainText(raw: String): String {
    return raw.replace("**", "")
        .lines()
        .joinToString("\n") { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("#")) trimmed.trimStart('#', ' ') else line
        }
        .trim()
}

@Composable
private fun AiSummarizeWorkspaceSection(
    selectedPdf: PickedPdfInfo?,
    aiState: AiSummaryUiState,
    isPro: Boolean,
    aiSummaryCredits: Int,
    onSummarizeClick: () -> Unit,
    onUpgradeProClick: () -> Unit,
    onCopySummary: (String) -> Unit,
    onExportPdf: (String, String) -> Unit
) {
    val isBusy = aiState is AiSummaryUiState.Extracting ||
        aiState is AiSummaryUiState.Loading ||
        aiState is AiSummaryUiState.Formatting

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!isPro) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (aiSummaryCredits > 0) {
                    PaperlyEmerald.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                },
                border = BorderStroke(
                    1.dp,
                    if (aiSummaryCredits > 0) PaperlyEmerald.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.outline
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ai_summary_credit_banner")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = when {
                            aiSummaryCredits == 1 -> "1 free summary credit ready"
                            aiSummaryCredits > 1 -> "$aiSummaryCredits free summary credits ready"
                            else -> "Watch a short video or start a 3-day trial to summarize"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (aiSummaryCredits > 0) PaperlyEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Button(
            onClick = onSummarizeClick,
            enabled = selectedPdf != null && !isBusy,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("summarize_with_ai_button"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when (aiState) {
                    is AiSummaryUiState.Extracting -> "Extracting PDF text..."
                    is AiSummaryUiState.Loading -> "Analyzing document..."
                    is AiSummaryUiState.Formatting -> "Formatting summary..."
                    is AiSummaryUiState.Streaming -> "Analyzing document..."
                    else -> "Summarize with Gemini AI"
                },
                style = MaterialTheme.typography.labelLarge
            )
        }

        when (aiState) {
            AiSummaryUiState.Idle -> Unit
            AiSummaryUiState.Extracting, AiSummaryUiState.Loading, AiSummaryUiState.Formatting -> {
                val activeStepIndex = when (aiState) {
                    AiSummaryUiState.Extracting -> 0
                    AiSummaryUiState.Loading -> 1
                    AiSummaryUiState.Formatting -> 2
                    else -> 0
                }
                val stepTitle = when (aiState) {
                    AiSummaryUiState.Extracting -> "Extracting PDF text..."
                    AiSummaryUiState.Loading -> "Analyzing document..."
                    AiSummaryUiState.Formatting -> "Formatting summary..."
                    else -> "Analyzing document..."
                }
                val stepSubtitle = when (aiState) {
                    AiSummaryUiState.Extracting -> "Cleaning and compressing text for fast upload"
                    AiSummaryUiState.Loading -> "Detecting document type & key insights"
                    AiSummaryUiState.Formatting -> "Structuring executive brief & highlights"
                    else -> ""
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_summary_progress_card"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                            Column {
                                Text(
                                    text = stepTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stepSubtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("Extract", "Analyze", "Format").forEachIndexed { idx, label ->
                                val isActive = idx == activeStepIndex
                                val isCompleted = idx < activeStepIndex
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = when {
                                        isActive -> MaterialTheme.colorScheme.primaryContainer
                                        isCompleted -> PaperlyEmerald.copy(alpha = 0.14f)
                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    }
                                ) {
                                    Text(
                                        text = "${idx + 1}. $label",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                                        color = when {
                                            isActive -> MaterialTheme.colorScheme.primary
                                            isCompleted -> PaperlyEmerald
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            is AiSummaryUiState.Streaming, is AiSummaryUiState.Success -> {
                val summaryText = when (aiState) {
                    is AiSummaryUiState.Streaming -> aiState.text
                    is AiSummaryUiState.Success -> aiState.text
                    else -> ""
                }
                val fileName = when (aiState) {
                    is AiSummaryUiState.Streaming -> aiState.fileName
                    is AiSummaryUiState.Success -> aiState.fileName
                    else -> ""
                }
                val wasTruncated = (aiState as? AiSummaryUiState.Success)?.wasTruncated == true
                val isDone = aiState is AiSummaryUiState.Success
                val blocks = remember(summaryText) { parseSummaryBlocks(summaryText) }
                val boldTextColor = MaterialTheme.colorScheme.onSurface

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_summary_result_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Executive Brief • $fileName",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isDone) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = "Complete",
                                    tint = PaperlyEmerald,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        if (wasTruncated) {
                            Text(
                                text = "Summarized the first 18,000 cleaned characters for rapid 2–4s delivery.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            blocks.forEachIndexed { index, block ->
                                when (block) {
                                    is SummaryBlock.Heading -> {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(top = if (index == 0) 2.dp else 8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .width(3.dp)
                                                    .height(16.dp)
                                                    .clip(CircleShape)
                                                    .background(MaterialTheme.colorScheme.primary)
                                            )
                                            Text(
                                                text = block.title,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }

                                    is SummaryBlock.Bullet -> {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = parseBoldMarkdown(block.content, boldTextColor),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    is SummaryBlock.Numbered -> {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            Text(
                                                text = block.prefix,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = parseBoldMarkdown(block.content, boldTextColor),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    is SummaryBlock.Paragraph -> {
                                        Text(
                                            text = parseBoldMarkdown(block.content, boldTextColor),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        if (isDone) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { onCopySummary(summaryText) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("copy_ai_summary_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Copy Summary", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Button(
                                    onClick = { onExportPdf(fileName, summaryText) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(50.dp)
                                        .testTag("export_summary_pdf_button"),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export as PDF", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }

                            if (!isPro) {
                                Surface(
                                    onClick = onUpgradeProClick,
                                    shape = RoundedCornerShape(50),
                                    color = PaperlyIndigo.copy(alpha = 0.08f),
                                    border = BorderStroke(
                                        1.dp,
                                        Brush.horizontalGradient(
                                            listOf(
                                                PaperlyIndigo.copy(alpha = 0.35f),
                                                PaperlyViolet.copy(alpha = 0.35f)
                                            )
                                        )
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .minimumInteractiveComponentSize()
                                        .testTag("summary_upgrade_pro_pill")
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.WorkspacePremium,
                                            contentDescription = null,
                                            tint = PaperlyIndigo,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Upgrade to Pro for unlimited instant summaries without ads →",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = PaperlyIndigo
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            is AiSummaryUiState.Error -> {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_summary_error_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Text(
                                text = "Could not summarize PDF",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            text = aiState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedButton(
                            onClick = onSummarizeClick,
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OutputNameField(
    value: String,
    onValueChange: (String) -> Unit,
    helper: String?
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Output filename") },
            suffix = { Text(".pdf") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("output_filename_input")
        )
        if (!helper.isNullOrBlank()) {
            Text(
                text = helper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
