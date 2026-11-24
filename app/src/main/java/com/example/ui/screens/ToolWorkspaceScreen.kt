package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.core.formatFileSize
import com.example.data.local.PdfRecordEntity
import com.example.data.model.AiSummaryUiState
import com.example.data.model.CompressionLevel
import com.example.data.model.PdfToolType
import com.example.data.model.PickedPdfInfo
import com.example.data.model.SignaturePlacement
import com.example.ui.components.SignaturePad
import com.example.ui.components.renderSignatureBitmap
import com.example.ui.theme.PaperlyEmerald
import com.example.ui.theme.PaperlyIndigo
import com.example.ui.theme.PaperlyViolet
import com.example.ui.viewmodel.PaperlyViewModel
import com.example.ui.viewmodel.WorkspaceUiState
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
    viewModel: PaperlyViewModel,
    onBack: () -> Unit,
    onProcessingComplete: (Long) -> Unit
) {
    val context = LocalContext.current
    val tool = workspace.activeTool

    val signatureStrokes = remember { mutableStateListOf<List<Offset>>() }
    var signatureCanvasSize by remember { mutableStateOf(IntSize.Zero) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

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
        viewModel.addScanImageUris(uris)
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { saved ->
        val captured = pendingCameraUri
        if (saved && captured != null) {
            viewModel.addScanImageUris(listOf(captured))
        }
    }

    val toolTitle = when (tool) {
        PdfToolType.MERGE -> stringResource(R.string.tool_merge_title)
        PdfToolType.SPLIT -> stringResource(R.string.tool_split_title)
        PdfToolType.COMPRESS -> stringResource(R.string.tool_compress_title)
        PdfToolType.SCAN -> stringResource(R.string.tool_scan_title)
        PdfToolType.AI_SUMMARIZE -> stringResource(R.string.tool_ai_title)
        PdfToolType.SIGN -> stringResource(R.string.tool_sign_title)
    }

    val canProcess = when (tool) {
        PdfToolType.MERGE -> workspace.selectedPdfs.size >= 2
        PdfToolType.SPLIT -> workspace.selectedPdfs.isNotEmpty() && workspace.splitPageRange.isNotBlank()
        PdfToolType.COMPRESS -> workspace.selectedPdfs.isNotEmpty()
        PdfToolType.SCAN -> workspace.scanImageUris.isNotEmpty()
        PdfToolType.SIGN -> workspace.selectedPdfs.isNotEmpty() && signatureStrokes.isNotEmpty()
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
            if (tool != PdfToolType.AI_SUMMARIZE) {
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
                    // Scan to PDF workspace
                    item {
                        ScanWorkspaceContent(
                            imageUris = workspace.scanImageUris,
                            enhanceContrast = workspace.scanEnhanceContrast,
                            outputName = workspace.outputFileName,
                            onPickPhotos = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            onTakePhoto = {
                                val uri = viewModel.pdfEngine.createScanTempUri()
                                pendingCameraUri = uri
                                takePictureLauncher.launch(uri)
                            },
                            onRemoveImage = viewModel::removeScanImageAt,
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
                                        outputName = workspace.outputFileName,
                                        onSelectLevel = viewModel::updateCompressionLevel,
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
                                        onPageChange = viewModel::updateSignTargetPage,
                                        onPlacementChange = viewModel::updateSignPlacement,
                                        onStrokeAdded = { signatureStrokes.add(it) },
                                        onClearStrokes = { signatureStrokes.clear() },
                                        onCanvasSizeChanged = { signatureCanvasSize = it },
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
    outputName: String,
    onSelectLevel: (CompressionLevel) -> Unit,
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
                text = "Compression Level • Original ${formatFileSize(pdf.sizeBytes)}",
                style = MaterialTheme.typography.titleSmall
            )

            CompressionLevel.entries.forEach { level ->
                val selected = level == selectedLevel
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
    onPickPhotos: () -> Unit,
    onTakePhoto: () -> Unit,
    onRemoveImage: (Int) -> Unit,
    onToggleEnhance: (Boolean) -> Unit,
    onOutputNameChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Capture or Pick Pages", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Add photos from your library using Android Photo Picker or snap a document with your camera.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onPickPhotos,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("scan_pick_photos_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photos")
                    }

                    OutlinedButton(
                        onClick = onTakePhoto,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("scan_take_photo_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Camera")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enhance Document Contrast", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Crisp B&W high-contrast scan filter",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = enhanceContrast,
                        onCheckedChange = onToggleEnhance,
                        modifier = Modifier.testTag("scan_enhance_switch")
                    )
                }
            }
        }

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
                    Text(
                        text = "${imageUris.size} page(s) ready",
                        style = MaterialTheme.typography.titleSmall
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        itemsIndexed(imageUris) { idx, uri ->
                            Box(
                                modifier = Modifier
                                    .size(width = 104.dp, height = 136.dp)
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
                                    onClick = { onRemoveImage(idx) },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(28.dp)
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

@Composable
private fun SignControlsCard(
    pdf: PickedPdfInfo,
    workspace: WorkspaceUiState,
    strokes: List<List<Offset>>,
    onPageChange: (Int) -> Unit,
    onPlacementChange: (SignaturePlacement) -> Unit,
    onStrokeAdded: (List<Offset>) -> Unit,
    onClearStrokes: () -> Unit,
    onCanvasSizeChanged: (IntSize) -> Unit,
    onOutputNameChange: (String) -> Unit
) {
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
                Text("Target Page", style = MaterialTheme.typography.titleSmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { onPageChange(workspace.signTargetPage - 1) },
                        enabled = workspace.signTargetPage > 1,
                        modifier = Modifier.height(48.dp)
                    ) {
                        Text("-")
                    }
                    Text(
                        text = "${workspace.signTargetPage} / ${pdf.pageCount}",
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
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Target page preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Fit
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Signature Placement", style = MaterialTheme.typography.titleSmall)
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

            SignaturePad(
                strokes = strokes,
                onStrokeAdded = onStrokeAdded,
                onClear = onClearStrokes,
                onSizeChanged = onCanvasSizeChanged
            )

            OutputNameField(
                value = workspace.outputFileName,
                onValueChange = onOutputNameChange,
                helper = null
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
