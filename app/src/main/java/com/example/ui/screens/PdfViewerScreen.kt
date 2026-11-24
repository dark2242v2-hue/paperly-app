package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.formatFileSize
import com.example.data.local.PdfRecordEntity
import com.example.data.model.PdfToolType
import com.example.ui.theme.PaperlyEmerald
import com.example.ui.theme.PaperlyViewerCanvas
import com.example.ui.theme.PaperlyViewerChrome
import com.example.ui.viewmodel.PaperlyViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(
    recordId: Long,
    viewModel: PaperlyViewModel,
    onQuickAction: (PdfToolType, PdfRecordEntity) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var record by remember { mutableStateOf<PdfRecordEntity?>(null) }
    var unlockedRecord by remember { mutableStateOf<PdfRecordEntity?>(null) }
    var isPasswordProtected by remember { mutableStateOf(false) }
    var overflowExpanded by remember { mutableStateOf(false) }
    var isBookMode by rememberSaveable(recordId) { mutableStateOf(false) }

    LaunchedEffect(recordId) {
        val loaded = viewModel.loadRecordById(recordId)
        record = loaded
        unlockedRecord = null
        isPasswordProtected = if (loaded != null) {
            loaded.operationTag == "Locked" || viewModel.pdfEngine.requiresPassword(loaded.filePath)
        } else {
            false
        }
    }

    val activeRecord = unlockedRecord ?: record
    val isLockedView = isPasswordProtected && unlockedRecord == null

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { destUri ->
        val current = record
        if (destUri != null && current != null) {
            viewModel.saveRecordToUri(current.filePath, destUri)
        }
    }

    Scaffold(
        containerColor = if (isLockedView) MaterialTheme.colorScheme.background else PaperlyViewerCanvas,
        contentWindowInsets = if (isLockedView) {
            WindowInsets.safeDrawing.exclude(WindowInsets.ime)
        } else {
            WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        },
        topBar = {
            if (isLockedView) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = record?.displayName ?: "PDF Preview",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            activeRecord?.let {
                                Text(
                                    text = "Encrypted PDF • ${formatFileSize(it.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("viewer_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column(modifier = Modifier.padding(end = 4.dp)) {
                            Text(
                                text = record?.displayName ?: "PDF Preview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            activeRecord?.let {
                                Text(
                                    text = "${it.pageCount} page(s) • ${formatFileSize(it.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.68f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("viewer_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        activeRecord?.let { activeRec ->
                            ViewerTopBarTooltipAction(
                                tooltip = if (isBookMode) "Vertical Scroll Mode" else "Book Page-Flip Mode",
                                icon = Icons.AutoMirrored.Rounded.MenuBook,
                                isActive = isBookMode,
                                testTagName = "viewer_book_mode_button",
                                onClick = { isBookMode = !isBookMode }
                            )
                            ViewerTopBarTooltipAction(
                                tooltip = "AI Summarize",
                                icon = Icons.Filled.AutoAwesome,
                                testTagName = "viewer_topbar_ai_button",
                                onClick = { onQuickAction(PdfToolType.AI_SUMMARIZE, activeRec) }
                            )

                            Box {
                                ViewerTopBarTooltipAction(
                                    tooltip = "More options",
                                    icon = Icons.Filled.MoreVert,
                                    testTagName = "viewer_more_menu_button",
                                    onClick = { overflowExpanded = true }
                                )

                                DropdownMenu(
                                    expanded = overflowExpanded,
                                    onDismissRequest = { overflowExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Compress PDF") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Compress, contentDescription = null)
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            onQuickAction(PdfToolType.COMPRESS, activeRec)
                                        },
                                        modifier = Modifier.testTag("viewer_menu_compress")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Merge PDF") },
                                        leadingIcon = {
                                            Icon(Icons.AutoMirrored.Filled.MergeType, contentDescription = null)
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            onQuickAction(PdfToolType.MERGE, activeRec)
                                        },
                                        modifier = Modifier.testTag("viewer_menu_merge")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Lock PDF") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Lock, contentDescription = null)
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            onQuickAction(PdfToolType.LOCK, activeRec)
                                        },
                                        modifier = Modifier.testTag("viewer_menu_lock")
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Save to Device") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Download, contentDescription = null)
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            saveLauncher.launch((record ?: activeRec).displayName)
                                        },
                                        modifier = Modifier.testTag("viewer_menu_download")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Share") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Share, contentDescription = null)
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            val shareTarget = record ?: activeRec
                                            val shareUri = viewModel.pdfEngine.getShareUri(shareTarget.filePath)
                                            val intent = Intent(Intent.ACTION_SEND).apply {
                                                type = "application/pdf"
                                                putExtra(Intent.EXTRA_STREAM, shareUri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(Intent.createChooser(intent, "Share PDF"))
                                        },
                                        modifier = Modifier.testTag("viewer_menu_share")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Print") },
                                        leadingIcon = {
                                            Icon(Icons.Filled.Print, contentDescription = null)
                                        },
                                        onClick = {
                                            overflowExpanded = false
                                            printPdfDocument(
                                                context = context,
                                                filePath = activeRec.filePath,
                                                documentName = activeRec.displayName,
                                                onError = { msg -> viewModel.postMessage(msg) }
                                            )
                                        },
                                        modifier = Modifier.testTag("viewer_menu_print")
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PaperlyViewerChrome,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White
                    )
                )
            }
        }
    ) { innerPadding ->
        val current = activeRecord
        if (current == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (isLockedView) {
            LockedPdfPasswordPromptSection(
                lockedFilePath = current.filePath,
                viewModel = viewModel,
                onUnlocked = { result ->
                    unlockedRecord = current.copy(
                        filePath = result.unlockedFilePath,
                        pageCount = result.pageCount,
                        operationTag = "Unlocked"
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            HdZoomablePdfCanvas(
                filePath = current.filePath,
                pageCount = current.pageCount,
                isBookMode = isBookMode,
                viewModel = viewModel,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewerTopBarTooltipAction(
    tooltip: String,
    icon: ImageVector,
    testTagName: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            PlainTooltip {
                Text(tooltip)
            }
        },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isActive) Color.White.copy(alpha = 0.2f) else Color.Transparent)
                .testTag(testTagName)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = tooltip,
                tint = Color.White
            )
        }
    }
}

@Composable
private fun LockedPdfPasswordPromptSection(
    lockedFilePath: String,
    viewModel: PaperlyViewModel,
    onUnlocked: (com.example.data.pdf.UnlockedPdfResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    var passwordInput by remember(lockedFilePath) { mutableStateOf("") }
    var passwordVisible by remember(lockedFilePath) { mutableStateOf(false) }
    var isUnlocking by remember(lockedFilePath) { mutableStateOf(false) }
    var passwordError by remember(lockedFilePath) { mutableStateOf<String?>(null) }

    fun attemptUnlock() {
        if (isUnlocking) return
        if (passwordInput.isEmpty()) {
            passwordError = "Enter PDF Password"
            return
        }
        isUnlocking = true
        passwordError = null
        coroutineScope.launch {
            try {
                val result = viewModel.pdfEngine.unlockPdfForViewing(lockedFilePath, passwordInput)
                passwordError = null
                onUnlocked(result)
            } catch (e: Exception) {
                passwordError = e.localizedMessage?.takeIf { it.isNotBlank() }
                    ?: "Incorrect password, please try again"
            } finally {
                isUnlocking = false
            }
        }
    }

    Column(
        modifier = modifier
            .imePadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .testTag("viewer_password_card")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PaperlyEmerald.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lock,
                        contentDescription = "Locked PDF",
                        tint = PaperlyEmerald,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    text = "Password-Protected PDF",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Enter the document password to unlock and view pages right inside Paperly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                OutlinedTextField(
                    value = passwordInput,
                    onValueChange = {
                        passwordInput = it
                        if (passwordError != null) passwordError = null
                    },
                    label = { Text("Enter PDF Password") },
                    singleLine = true,
                    isError = passwordError != null,
                    supportingText = passwordError?.let { err ->
                        {
                            Text(
                                text = err,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    },
                    visualTransformation = if (passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { attemptUnlock() }
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("viewer_password_visibility_toggle")
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) {
                                    Icons.Filled.VisibilityOff
                                } else {
                                    Icons.Filled.Visibility
                                },
                                contentDescription = if (passwordVisible) "Hide password" else "Show password"
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("viewer_password_input")
                )

                Button(
                    onClick = { attemptUnlock() },
                    enabled = !isUnlocking,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("viewer_unlock_button")
                ) {
                    if (isUnlocking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlocking…")
                    } else {
                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Unlock & View",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HdZoomablePdfCanvas(
    filePath: String,
    pageCount: Int,
    isBookMode: Boolean,
    viewModel: PaperlyViewModel,
    modifier: Modifier = Modifier
) {
    val navBarBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier.background(PaperlyViewerCanvas)
    ) {
        val availWidthDp = (maxWidth - 32.dp).coerceAtLeast(160.dp)
        val availHeightDp = (maxHeight - 24.dp - navBarBottomPadding).coerceAtLeast(180.dp)
        val maxFitWidthDp = if (maxWidth >= 600.dp) minOf(availWidthDp, 540.dp) else availWidthDp

        val hdRenderWidth = remember(maxFitWidthDp, density) {
            val targetPx = with(density) { maxFitWidthDp.toPx() }
            (targetPx * 2f).roundToInt().coerceIn(1400, 2400)
        }

        val viewportWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportHeightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        var activePageIndex by rememberSaveable(filePath) { mutableIntStateOf(0) }

        val lazyListState = rememberLazyListState(
            initialFirstVisibleItemIndex = activePageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        )
        val pagerState = rememberPagerState(
            initialPage = activePageIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        ) {
            pageCount.coerceAtLeast(0)
        }
        val scope = rememberCoroutineScope()

        var zoomScale by remember(filePath) { mutableFloatStateOf(1f) }
        var panOffset by remember(filePath) { mutableStateOf(Offset.Zero) }
        var zoomAnimJob by remember(filePath) { mutableStateOf<Job?>(null) }

        // Keep page position synced when toggling between Vertical Scroll and Book Page-Flip mode
        LaunchedEffect(isBookMode, pageCount) {
            zoomAnimJob?.cancel()
            zoomScale = 1f
            panOffset = Offset.Zero
            if (pageCount > 0) {
                val target = activePageIndex.coerceIn(0, pageCount - 1)
                if (isBookMode) {
                    if (pagerState.currentPage != target) {
                        pagerState.scrollToPage(target)
                    }
                } else {
                    if (lazyListState.firstVisibleItemIndex != target) {
                        lazyListState.scrollToItem(target)
                    }
                }
            }
        }

        // Reset zoom cleanly when flipping to a new page in Book mode
        LaunchedEffect(pagerState.currentPage, isBookMode) {
            if (isBookMode) {
                activePageIndex = pagerState.currentPage
                zoomAnimJob?.cancel()
                zoomScale = 1f
                panOffset = Offset.Zero
            }
        }

        val currentVerticalPageIndex by remember(pageCount) {
            derivedStateOf {
                if (pageCount <= 0) 0
                else {
                    val info = lazyListState.layoutInfo
                    val visible = info.visibleItemsInfo
                    if (visible.isEmpty()) {
                        lazyListState.firstVisibleItemIndex.coerceIn(0, pageCount - 1)
                    } else {
                        val centerY = (info.viewportStartOffset + info.viewportEndOffset) / 2
                        val centerItem = visible.minByOrNull { item ->
                            abs((item.offset + item.size / 2) - centerY)
                        }
                        (centerItem?.index ?: lazyListState.firstVisibleItemIndex)
                            .coerceIn(0, pageCount - 1)
                    }
                }
            }
        }

        LaunchedEffect(currentVerticalPageIndex, isBookMode) {
            if (!isBookMode) {
                activePageIndex = currentVerticalPageIndex
            }
        }

        val currentPageOneBased = if (pageCount <= 0) {
            0
        } else if (isBookMode) {
            (pagerState.currentPage + 1).coerceIn(1, pageCount)
        } else {
            (currentVerticalPageIndex + 1).coerceIn(1, pageCount)
        }

        fun clampOffset(raw: Offset, scale: Float): Offset {
            if (scale <= 1.005f) return Offset.Zero
            val maxX = (viewportWidthPx * (scale - 1f)) / 2f
            val maxY = (viewportHeightPx * (scale - 1f)) / 2f
            return Offset(
                x = raw.x.coerceIn(-maxX, maxX),
                y = raw.y.coerceIn(-maxY, maxY)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(filePath, viewportWidthPx, viewportHeightPx) {
                    detectTapGestures(
                        onDoubleTap = { tapOffset ->
                            zoomAnimJob?.cancel()
                            val startScale = zoomScale
                            val startOffset = panOffset
                            val targetScale = if (abs(startScale - 1f) > 0.12f) 1f else 2f
                            val center = Offset(viewportWidthPx / 2f, viewportHeightPx / 2f)
                            val rawTargetOffset = if (targetScale <= 1.005f) {
                                Offset.Zero
                            } else {
                                (center - tapOffset) * (targetScale - 1f)
                            }
                            val targetOffset = clampOffset(rawTargetOffset, targetScale)
                            zoomAnimJob = scope.launch {
                                val anim = Animatable(0f)
                                anim.animateTo(1f, tween(durationMillis = 220)) {
                                    val t = value
                                    zoomScale = startScale + (targetScale - startScale) * t
                                    panOffset = Offset(
                                        x = startOffset.x + (targetOffset.x - startOffset.x) * t,
                                        y = startOffset.y + (targetOffset.y - startOffset.y) * t
                                    )
                                }
                            }
                        }
                    )
                }
                .pointerInput(filePath, isBookMode, viewportWidthPx, viewportHeightPx) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop
                        var zoomAcc = 1f
                        var panAcc = Offset.Zero

                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.any { it.isConsumed }
                            if (!canceled) {
                                val pressedCount = event.changes.count { it.pressed }
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()

                                if (!pastTouchSlop) {
                                    zoomAcc *= zoomChange
                                    panAcc += panChange
                                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                                    val zoomMotion = abs(1f - zoomAcc) * centroidSize
                                    val panMotion = panAcc.getDistance()
                                    if (pressedCount > 1 ||
                                        zoomMotion > touchSlop ||
                                        (zoomScale > 1.02f && panMotion > touchSlop)
                                    ) {
                                        pastTouchSlop = true
                                    }
                                }

                                if (pastTouchSlop && (pressedCount > 1 || zoomScale > 1.02f)) {
                                    zoomAnimJob?.cancel()
                                    val oldScale = zoomScale
                                    val newScale = if (pressedCount > 1) {
                                        (oldScale * zoomChange).coerceIn(0.8f, 4f)
                                    } else {
                                        oldScale
                                    }
                                    val ratio = if (oldScale > 0f) newScale / oldScale else 1f
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val viewportCenter = Offset(viewportWidthPx / 2f, viewportHeightPx / 2f)

                                    val desiredOffset = if (centroid != Offset.Unspecified && pressedCount > 1) {
                                        panOffset * ratio + (centroid - viewportCenter) * (1f - ratio) + panChange
                                    } else {
                                        panOffset + panChange
                                    }

                                    val clamped = clampOffset(desiredOffset, newScale)
                                    if (!isBookMode && pressedCount == 1 && zoomScale > 1.02f) {
                                        val leftoverY = desiredOffset.y - clamped.y
                                        if (abs(leftoverY) > 0.5f) {
                                            lazyListState.dispatchRawDelta(-leftoverY / newScale)
                                        }
                                    }

                                    zoomScale = if (abs(newScale - 1f) < 0.015f) 1f else newScale
                                    panOffset = if (newScale <= 1.005f) Offset.Zero else clamped

                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) change.consume()
                                    }
                                }
                            }
                        } while (!canceled && event.changes.any { it.pressed })
                    }
                }
        ) {
            if (isBookMode) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = zoomScale <= 1.02f,
                    key = { index -> "${filePath}_book_$index" },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("viewer_book_pager")
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        }
                ) { pageIndex ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 16.dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = navBarBottomPadding + 12.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        PdfFittedPageSheet(
                            filePath = filePath,
                            pageIndex = pageIndex,
                            hdRenderWidth = hdRenderWidth,
                            maxFitWidthDp = maxFitWidthDp,
                            availHeightDp = availHeightDp,
                            viewModel = viewModel
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("viewer_vertical_list")
                        .graphicsLayer {
                            scaleX = zoomScale
                            scaleY = zoomScale
                            translationX = panOffset.x
                            translationY = panOffset.y
                        },
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = navBarBottomPadding + 16.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(
                        count = pageCount,
                        key = { index -> "${filePath}_$index" }
                    ) { pageIndex ->
                        PdfFittedPageSheet(
                            filePath = filePath,
                            pageIndex = pageIndex,
                            hdRenderWidth = hdRenderWidth,
                            maxFitWidthDp = maxFitWidthDp,
                            availHeightDp = availHeightDp,
                            viewModel = viewModel
                        )
                    }
                }
            }

            if (pageCount > 0) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.Black.copy(alpha = 0.72f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .testTag("viewer_page_counter_badge")
                ) {
                    val zoomLabel = if (abs(zoomScale - 1f) > 0.05f) {
                        " • ${((zoomScale * 10f).roundToInt() / 10f)}x"
                    } else {
                        ""
                    }
                    Text(
                        text = "$currentPageOneBased / $pageCount$zoomLabel",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfFittedPageSheet(
    filePath: String,
    pageIndex: Int,
    hdRenderWidth: Int,
    maxFitWidthDp: androidx.compose.ui.unit.Dp,
    availHeightDp: androidx.compose.ui.unit.Dp,
    viewModel: PaperlyViewModel
) {
    val cachedInitial = remember(filePath, pageIndex, hdRenderWidth) {
        viewModel.pdfEngine.getCachedPageThumbnail(
            filePath = filePath,
            pageIndex = pageIndex,
            maxWidth = hdRenderWidth
        )
    }
    val pageBitmap by produceState<Bitmap?>(
        initialValue = cachedInitial,
        filePath,
        pageIndex,
        hdRenderWidth
    ) {
        if (value == null) {
            value = viewModel.pdfEngine.renderPageThumbnail(
                filePath = filePath,
                pageIndex = pageIndex,
                maxWidth = hdRenderWidth
            )
        }
    }

    val bmp = pageBitmap
    val pageAspect = if (bmp != null) {
        bmp.width.toFloat() / bmp.height.coerceAtLeast(1).toFloat()
    } else {
        1f / 1.414f
    }
    val pageWidthDp = minOf(maxFitWidthDp, availHeightDp * pageAspect)
    val pageHeightDp = pageWidthDp / pageAspect

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        modifier = Modifier
            .size(width = pageWidthDp, height = pageHeightDp)
            .testTag("viewer_page_$pageIndex")
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Page ${pageIndex + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun printPdfDocument(
    context: Context,
    filePath: String,
    documentName: String,
    onError: (String) -> Unit
) {
    val file = File(filePath)
    if (!file.exists()) {
        onError("PDF file is not available for printing.")
        return
    }
    val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
    if (printManager == null) {
        onError("Printing service is unavailable on this device.")
        return
    }
    val jobName = documentName.ifBlank { "Paperly Document" }
    printManager.print(
        jobName,
        PdfFilePrintAdapter(file, jobName),
        PrintAttributes.Builder().build()
    )
}

private class PdfFilePrintAdapter(
    private val file: File,
    private val jobName: String
) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(jobName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            return
        }
        if (destination == null) {
            callback?.onWriteFailed("Invalid print destination.")
            return
        }
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output ->
                    input.copyTo(output)
                }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.localizedMessage ?: "Failed to print PDF.")
        }
    }
}
