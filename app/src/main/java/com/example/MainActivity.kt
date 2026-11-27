package com.example

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ads.findActivity
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.ImageToolType
import com.example.data.model.PdfToolType
import com.example.ui.components.PaperlyBottomBarWithBanner
import com.example.ui.components.PaperlyProSheet
import com.example.ui.components.PaperlyTopBar
import com.example.ui.navigation.PaperlyRoutes
import com.example.ui.screens.DownloadReadyScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.ImageResultScreen
import com.example.ui.screens.ImageWorkspaceScreen
import com.example.ui.screens.MultiShotCameraScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolWorkspaceScreen
import com.example.ui.theme.PaperlyTheme
import com.example.ui.viewmodel.PaperlyViewModel

data class ExternalPdfLaunchRequest(
    val uri: Uri,
    val token: Long = System.nanoTime()
)

class MainActivity : ComponentActivity() {
    private val initialExternalPdfRequestState = mutableStateOf<ExternalPdfLaunchRequest?>(null)
    private val warmExternalPdfRequestState = mutableStateOf<ExternalPdfLaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val initialDarkMode = UserPreferencesRepository.readBootDarkModeSync(this)
        window.setBackgroundDrawable(
            ColorDrawable(if (initialDarkMode) 0xFF0B1120.toInt() else 0xFFF8FAFC.toInt())
        )
        enableEdgeToEdge(
            statusBarStyle = if (initialDarkMode) {
                SystemBarStyle.dark(Color.Transparent.toArgb())
            } else {
                SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
            },
            navigationBarStyle = if (initialDarkMode) {
                SystemBarStyle.dark(Color.Transparent.toArgb())
            } else {
                SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
            }
        )

        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            extractIncomingPdfUri(intent)?.let { uri ->
                initialExternalPdfRequestState.value = ExternalPdfLaunchRequest(uri)
            }
        }

        setContent {
            val viewModel: PaperlyViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val initialExternalPdfRequest by initialExternalPdfRequestState
            val warmExternalPdfRequest by warmExternalPdfRequestState
            var appliedDarkMode by remember { mutableStateOf(initialDarkMode) }

            LaunchedEffect(isDarkMode) {
                if (appliedDarkMode != isDarkMode) {
                    appliedDarkMode = isDarkMode
                    window.setBackgroundDrawable(
                        ColorDrawable(if (isDarkMode) 0xFF0B1120.toInt() else 0xFFF8FAFC.toInt())
                    )
                    enableEdgeToEdge(
                        statusBarStyle = if (isDarkMode) {
                            SystemBarStyle.dark(Color.Transparent.toArgb())
                        } else {
                            SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                        },
                        navigationBarStyle = if (isDarkMode) {
                            SystemBarStyle.dark(Color.Transparent.toArgb())
                        } else {
                            SystemBarStyle.light(Color.Transparent.toArgb(), Color.Transparent.toArgb())
                        }
                    )
                }
            }

            PaperlyTheme(darkTheme = isDarkMode) {
                PaperlyApp(
                    viewModel = viewModel,
                    initialExternalPdfRequest = initialExternalPdfRequest,
                    onConsumeInitialExternalPdfRequest = { initialExternalPdfRequestState.value = null },
                    warmExternalPdfRequest = warmExternalPdfRequest,
                    onConsumeWarmExternalPdfRequest = { warmExternalPdfRequestState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIncomingPdfUri(intent)?.let { uri ->
            warmExternalPdfRequestState.value = ExternalPdfLaunchRequest(uri)
        }
    }

    private fun extractIncomingPdfUri(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> {
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            }
            else -> null
        }
    }
}

@Composable
fun PaperlyApp(
    viewModel: PaperlyViewModel,
    initialExternalPdfRequest: ExternalPdfLaunchRequest? = null,
    onConsumeInitialExternalPdfRequest: () -> Unit = {},
    warmExternalPdfRequest: ExternalPdfLaunchRequest? = null,
    onConsumeWarmExternalPdfRequest: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val startDestination = rememberSaveable {
        if (initialExternalPdfRequest != null) {
            PaperlyRoutes.EXTERNAL_PDF_VIEWER
        } else {
            PaperlyRoutes.MAIN_TABS
        }
    }

    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val debugProOverride by viewModel.debugProOverride.collectAsStateWithLifecycle()
    val activePlanProductId by viewModel.activePlanProductId.collectAsStateWithLifecycle()
    val billingState by viewModel.billingState.collectAsStateWithLifecycle()
    val defaultCompression by viewModel.defaultCompression.collectAsStateWithLifecycle()
    val completedCorePdfOps by viewModel.completedCorePdfOps.collectAsStateWithLifecycle()
    val aiSummaryCredits by viewModel.aiSummaryCredits.collectAsStateWithLifecycle()
    val aiImageCredits by viewModel.aiImageCredits.collectAsStateWithLifecycle()
    val upscaleCredits by viewModel.upscaleCredits.collectAsStateWithLifecycle()
    val homeCategory by viewModel.homeCategory.collectAsStateWithLifecycle()
    val recentRecords by viewModel.recentRecords.collectAsStateWithLifecycle()
    val recentImages by viewModel.recentImages.collectAsStateWithLifecycle()
    val isInitialFilesLoading by viewModel.isInitialFilesLoading.collectAsStateWithLifecycle()
    val isInitialImagesLoading by viewModel.isInitialImagesLoading.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val imageWorkspace by viewModel.imageWorkspace.collectAsStateWithLifecycle()
    val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()
    val processedImageResult by viewModel.processedImageResult.collectAsStateWithLifecycle()
    val aiSummaryState by viewModel.aiSummaryState.collectAsStateWithLifecycle()
    val proSheetState by viewModel.proSheetState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val isAdsSdkInitialized by viewModel.isAdsSdkInitialized.collectAsStateWithLifecycle()
    val externalPdfOpenState by viewModel.externalPdfOpenState.collectAsStateWithLifecycle()

    val activity = LocalContext.current.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(lifecycleOwner) {
        var hasPausedOnce = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> hasPausedOnce = true
                Lifecycle.Event.ON_RESUME -> if (hasPausedOnce) {
                    viewModel.refreshBillingOnResume()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
        }
    }

    var hasConsumedInitialExternal by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialExternalPdfRequest) {
        val req = initialExternalPdfRequest
        if (req != null && !hasConsumedInitialExternal) {
            hasConsumedInitialExternal = true
            viewModel.openExternalPdfRequest(req.uri, req.token)
            onConsumeInitialExternalPdfRequest()
        }
    }

    LaunchedEffect(warmExternalPdfRequest) {
        val req = warmExternalPdfRequest
        if (req != null) {
            viewModel.openExternalPdfRequest(req.uri, req.token)
            onConsumeWarmExternalPdfRequest()
            navController.navigate(PaperlyRoutes.EXTERNAL_PDF_VIEWER) {
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(isPro) {
        val pendingTool = proSheetState.triggeredByTool
        val pendingImgTool = proSheetState.triggeredByImageTool
        if (isPro && proSheetState.visible && (pendingTool != null || pendingImgTool != null)) {
            val shouldNavigate = proSheetState.navigateOnUnlock
            val preloaded = proSheetState.preloadedRecord
            viewModel.dismissProSheet()
            if (pendingTool != null) {
                if (shouldNavigate) {
                    viewModel.prepareToolWorkspace(pendingTool, preloadedRecord = preloaded)
                    navController.navigate(PaperlyRoutes.toolWorkspace(pendingTool))
                } else if (pendingTool == PdfToolType.AI_SUMMARIZE && workspace.selectedPdfs.isNotEmpty()) {
                    viewModel.summarizeSelectedPdf(skipCreditCheck = true)
                }
            } else if (pendingImgTool != null) {
                if (shouldNavigate) {
                    viewModel.prepareImageToolWorkspace(pendingImgTool)
                    navController.navigate(PaperlyRoutes.imageWorkspace(pendingImgTool))
                } else if (pendingImgTool == ImageToolType.TEXT_TO_IMAGE && imageWorkspace.aiPrompt.isNotBlank()) {
                    viewModel.generateAiImage(
                        onResultReady = { navController.navigate(PaperlyRoutes.IMAGE_RESULT) },
                        skipCreditCheck = true
                    )
                } else if (pendingImgTool == ImageToolType.UPSCALER && imageWorkspace.pickedImage != null) {
                    viewModel.runImageUpscale(
                        onResultReady = { navController.navigate(PaperlyRoutes.IMAGE_RESULT) },
                        skipCreditCheck = true
                    )
                }
            }
        }
    }

    var activeMainTab by rememberSaveable { mutableStateOf(PaperlyRoutes.HOME) }
    val isMainTab = currentRoute == PaperlyRoutes.MAIN_TABS ||
        (currentRoute == null && startDestination == PaperlyRoutes.MAIN_TABS)

    BackHandler(enabled = isMainTab && activeMainTab != PaperlyRoutes.HOME) {
        activeMainTab = PaperlyRoutes.HOME
    }

    LaunchedEffect(isMainTab, activeMainTab) {
        if (isMainTab && activeMainTab == PaperlyRoutes.SETTINGS) {
            viewModel.refreshTempCacheSize()
        }
    }

    var rootEntered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { rootEntered = true }
    val rootAlpha by animateFloatAsState(
        targetValue = if (rootEntered) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = EaseOutCubic),
        label = "rootEntranceAlpha"
    )
    val rootScale by animateFloatAsState(
        targetValue = if (rootEntered) 1f else 0.96f,
        animationSpec = tween(durationMillis = 220, easing = EaseOutCubic),
        label = "rootEntranceScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
    Scaffold(
        modifier = Modifier.graphicsLayer {
            alpha = rootAlpha
            scaleX = rootScale
            scaleY = rootScale
        },
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (isMainTab) {
                PaperlyTopBar(
                    isDarkMode = isDarkMode,
                    isPro = isPro,
                    onToggleDarkMode = viewModel::toggleDarkMode,
                    onProClick = viewModel::openProSheet,
                    modifier = Modifier.statusBarsPadding()
                )
            }
        },
        bottomBar = {
            if (isMainTab) {
                PaperlyBottomBarWithBanner(
                    currentRoute = activeMainTab,
                    isPro = isPro,
                    showHomeBanner = activeMainTab == PaperlyRoutes.HOME,
                    isAdsSdkInitialized = isAdsSdkInitialized,
                    onNavigate = { targetRoute ->
                        activeMainTab = targetRoute
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isMainTab) innerPadding else PaddingValues())
        ) {
            NavHost(
                navController = navController,
                startDestination = startDestination,
                enterTransition = {
                    slideInHorizontally(tween(durationMillis = 220, easing = EaseOutCubic)) { (it * 0.20f).toInt() } +
                        fadeIn(tween(durationMillis = 220, easing = EaseOutCubic))
                },
                exitTransition = {
                    slideOutHorizontally(tween(durationMillis = 220, easing = EaseOutCubic)) { -(it * 0.12f).toInt() } +
                        fadeOut(tween(durationMillis = 180, easing = EaseOutCubic))
                },
                popEnterTransition = {
                    slideInHorizontally(tween(durationMillis = 220, easing = EaseOutCubic)) { -(it * 0.12f).toInt() } +
                        fadeIn(tween(durationMillis = 220, easing = EaseOutCubic))
                },
                popExitTransition = {
                    slideOutHorizontally(tween(durationMillis = 220, easing = EaseOutCubic)) { (it * 0.20f).toInt() } +
                        fadeOut(tween(durationMillis = 200, easing = EaseOutCubic))
                }
            ) {
                composable(route = PaperlyRoutes.MAIN_TABS) {
                    val tempCacheSizeBytes by viewModel.tempCacheSizeBytes.collectAsStateWithLifecycle()
                    val homeSelected = activeMainTab == PaperlyRoutes.HOME
                    val filesSelected = activeMainTab == PaperlyRoutes.FILES
                    val settingsSelected = activeMainTab == PaperlyRoutes.SETTINGS

                    var hasVisitedFiles by rememberSaveable { mutableStateOf(false) }
                    var hasVisitedSettings by rememberSaveable { mutableStateOf(false) }

                    LaunchedEffect(activeMainTab) {
                        if (activeMainTab == PaperlyRoutes.FILES) hasVisitedFiles = true
                        if (activeMainTab == PaperlyRoutes.SETTINGS) hasVisitedSettings = true
                    }

                    val homeAlpha by animateFloatAsState(
                        targetValue = if (homeSelected) 1f else 0f,
                        animationSpec = tween(durationMillis = 150, easing = EaseOutCubic),
                        label = "homeTabAlpha"
                    )
                    val filesAlpha by animateFloatAsState(
                        targetValue = if (filesSelected) 1f else 0f,
                        animationSpec = tween(durationMillis = 150, easing = EaseOutCubic),
                        label = "filesTabAlpha"
                    )
                    val settingsAlpha by animateFloatAsState(
                        targetValue = if (settingsSelected) 1f else 0f,
                        animationSpec = tween(durationMillis = 150, easing = EaseOutCubic),
                        label = "settingsTabAlpha"
                    )

                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(modifier = tabLayerModifier(selected = homeSelected, alpha = homeAlpha)) {
                            HomeScreen(
                                selectedCategory = homeCategory,
                                recentRecords = recentRecords,
                                recentImages = recentImages,
                                isInitialFilesLoading = isInitialFilesLoading,
                                isInitialImagesLoading = isInitialImagesLoading,
                                onCategorySelected = viewModel::setHomeCategory,
                                onToolSelected = { tool ->
                                    if (tool == PdfToolType.AI_SUMMARIZE ||
                                        tool == PdfToolType.SIGN
                                    ) {
                                        viewModel.openRewardedGateOrProceed(tool) {
                                            navController.navigate(PaperlyRoutes.toolWorkspace(tool))
                                        }
                                    } else {
                                        viewModel.prepareToolWorkspace(tool)
                                        navController.navigate(PaperlyRoutes.toolWorkspace(tool))
                                    }
                                },
                                onImageToolSelected = { imgTool ->
                                    viewModel.openImageToolOrGate(imgTool) {
                                        navController.navigate(PaperlyRoutes.imageWorkspace(imgTool))
                                    }
                                },
                                onRecentSelected = { rec ->
                                    navController.navigate(PaperlyRoutes.pdfViewer(rec.id))
                                },
                                onRecentImageSelected = { item ->
                                    viewModel.openRecentImage(item)
                                    navController.navigate(PaperlyRoutes.IMAGE_RESULT)
                                },
                                onSeeAllFiles = {
                                    hasVisitedFiles = true
                                    activeMainTab = PaperlyRoutes.FILES
                                },
                                getCachedImageThumbnail = viewModel.imageEngine::getCachedPreview,
                                loadImageThumbnail = { path, maxEdge ->
                                    viewModel.imageEngine.decodePreviewBitmap(path, maxEdge)
                                },
                                getCachedPdfThumbnail = { path, maxW ->
                                    viewModel.pdfEngine.getCachedPageThumbnail(path, pageIndex = 0, maxWidth = maxW)
                                },
                                loadPdfThumbnail = { path, maxW ->
                                    viewModel.pdfEngine.renderPageThumbnail(path, pageIndex = 0, maxWidth = maxW)
                                }
                            )
                        }

                        if (filesSelected || hasVisitedFiles) {
                            Box(modifier = tabLayerModifier(selected = filesSelected, alpha = filesAlpha)) {
                                FilesScreen(
                                    records = recentRecords,
                                    viewModel = viewModel,
                                    onOpenViewer = { id ->
                                        navController.navigate(PaperlyRoutes.pdfViewer(id))
                                    }
                                )
                            }
                        }

                        if (settingsSelected || hasVisitedSettings) {
                            Box(modifier = tabLayerModifier(selected = settingsSelected, alpha = settingsAlpha)) {
                                SettingsScreen(
                                    isDarkMode = isDarkMode,
                                    isPro = isPro,
                                    activePlanProductId = activePlanProductId,
                                    isRestoringPurchases = billingState.isRestoring,
                                    debugProOverride = debugProOverride,
                                    defaultCompression = defaultCompression,
                                    savedFileCount = recentRecords.size,
                                    tempCacheSizeBytes = tempCacheSizeBytes,
                                    onToggleDarkMode = viewModel::toggleDarkMode,
                                    onOpenProSheet = viewModel::openProSheet,
                                    onRestorePurchases = viewModel::restorePurchases,
                                    onToggleDebugPro = viewModel::setDebugProOverride,
                                    onSelectCompression = viewModel::setDefaultCompression,
                                    onRefreshCacheSize = viewModel::refreshTempCacheSize,
                                    onClearTempCache = viewModel::clearTempCache,
                                    onClearAllFiles = viewModel::clearAllLocalFiles
                                )
                            }
                        }
                    }
                }

                composable(
                    route = PaperlyRoutes.TOOL_WORKSPACE,
                    arguments = listOf(navArgument("toolType") { type = NavType.StringType })
                ) {
                    ToolWorkspaceScreen(
                        workspace = workspace,
                        recentRecords = recentRecords,
                        aiSummaryState = aiSummaryState,
                        isPro = isPro,
                        aiSummaryCredits = aiSummaryCredits,
                        onUpgradeProClick = viewModel::openProSheet,
                        onOpenMultiShotCamera = {
                            navController.navigate(PaperlyRoutes.MULTI_SHOT_CAMERA)
                        },
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onProcessingComplete = { recordId ->
                            navController.navigate(PaperlyRoutes.downloadReady(recordId)) {
                                popUpTo(startDestination)
                            }
                        }
                    )
                }

                composable(PaperlyRoutes.MULTI_SHOT_CAMERA) {
                    MultiShotCameraScreen(
                        capturedUris = workspace.scanImageUris,
                        createOutputFile = viewModel.pdfEngine::createScanTempFile,
                        fileToUri = viewModel.pdfEngine::fileToProviderUri,
                        onImageCaptured = { uri -> viewModel.addScanImageUris(listOf(uri)) },
                        onGalleryImagesPicked = { uris -> viewModel.importScanImageUris(uris, fromSmartScan = false) },
                        onRemoveCapturedAt = viewModel::removeScanImageAt,
                        onDone = { navController.popBackStack() },
                        onClose = { navController.popBackStack() }
                    )
                }

                composable(
                    route = PaperlyRoutes.DOWNLOAD_READY,
                    arguments = listOf(navArgument("recordId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
                    DownloadReadyScreen(
                        recordId = recordId,
                        isPro = isPro,
                        isDarkMode = isDarkMode,
                        showAhaTrialBanner = !isPro && completedCorePdfOps >= 2,
                        viewModel = viewModel,
                        onUpgradeProClick = viewModel::openProSheet,
                        onPreviewPdf = { id ->
                            navController.navigate(PaperlyRoutes.pdfViewer(id))
                        },
                        onDone = {
                            if (!navController.popBackStack(PaperlyRoutes.MAIN_TABS, inclusive = false)) {
                                navController.navigate(PaperlyRoutes.MAIN_TABS) {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }

                composable(
                    route = PaperlyRoutes.IMAGE_WORKSPACE,
                    arguments = listOf(navArgument("imageToolType") { type = NavType.StringType })
                ) {
                    ImageWorkspaceScreen(
                        state = imageWorkspace,
                        ocrState = ocrState,
                        isPro = isPro,
                        isDarkMode = isDarkMode,
                        aiImageCredits = aiImageCredits,
                        upscaleCredits = upscaleCredits,
                        viewModel = viewModel,
                        onUpgradeProClick = viewModel::openProSheet,
                        onBack = { navController.popBackStack() },
                        onImageResultReady = {
                            navController.navigate(PaperlyRoutes.IMAGE_RESULT)
                        },
                        onPdfExported = { pdfId ->
                            navController.navigate(PaperlyRoutes.pdfViewer(pdfId))
                        }
                    )
                }

                composable(PaperlyRoutes.IMAGE_RESULT) {
                    ImageResultScreen(
                        result = processedImageResult,
                        isPro = isPro,
                        isDarkMode = isDarkMode,
                        viewModel = viewModel,
                        onOpenPdfViewer = { pdfId ->
                            navController.navigate(PaperlyRoutes.pdfViewer(pdfId))
                        },
                        onDone = {
                            if (!navController.popBackStack(PaperlyRoutes.MAIN_TABS, inclusive = false)) {
                                navController.navigate(PaperlyRoutes.MAIN_TABS) {
                                    popUpTo(0) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }

                composable(
                    route = PaperlyRoutes.PDF_VIEWER,
                    arguments = listOf(navArgument("recordId") { type = NavType.LongType })
                ) { backStackEntry ->
                    val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
                    PdfViewerScreen(
                        recordId = recordId,
                        viewModel = viewModel,
                        onQuickAction = { tool, rec ->
                            viewModel.openToolWithRecord(tool, rec) {
                                navController.navigate(PaperlyRoutes.toolWorkspace(tool))
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(route = PaperlyRoutes.EXTERNAL_PDF_VIEWER) {
                    PdfViewerScreen(
                        recordId = -1L,
                        viewModel = viewModel,
                        externalOpenState = externalPdfOpenState,
                        onQuickAction = { tool, rec ->
                            viewModel.openToolWithRecord(tool, rec) {
                                navController.navigate(PaperlyRoutes.toolWorkspace(tool))
                            }
                        },
                        onBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(PaperlyRoutes.MAIN_TABS) {
                                    popUpTo(PaperlyRoutes.EXTERNAL_PDF_VIEWER) { inclusive = true }
                                    launchSingleTop = true
                                }
                            }
                        }
                    )
                }
            }
        }
    }
    }

    PaperlyProSheet(
        state = proSheetState,
        isPro = isPro,
        activePlanProductId = activePlanProductId,
        billingState = billingState,
        onSubscribe = { planType ->
            viewModel.subscribeToPlan(activity, planType)
        },
        onWatchRewardedVideo = {
            viewModel.watchRewardedVideoFromProSheet(activity) { unlockedPdfTool, unlockedImgTool, shouldNavigate, _ ->
                if (unlockedPdfTool != null) {
                    if (shouldNavigate) {
                        navController.navigate(PaperlyRoutes.toolWorkspace(unlockedPdfTool))
                    } else if (unlockedPdfTool == PdfToolType.AI_SUMMARIZE && workspace.selectedPdfs.isNotEmpty()) {
                        viewModel.summarizeSelectedPdf(skipCreditCheck = true)
                    }
                } else if (unlockedImgTool != null) {
                    if (shouldNavigate) {
                        navController.navigate(PaperlyRoutes.imageWorkspace(unlockedImgTool))
                    } else if (unlockedImgTool == ImageToolType.TEXT_TO_IMAGE && imageWorkspace.aiPrompt.isNotBlank()) {
                        viewModel.generateAiImage(
                            onResultReady = { navController.navigate(PaperlyRoutes.IMAGE_RESULT) },
                            skipCreditCheck = true
                        )
                    } else if (unlockedImgTool == ImageToolType.UPSCALER && imageWorkspace.pickedImage != null) {
                        viewModel.runImageUpscale(
                            onResultReady = { navController.navigate(PaperlyRoutes.IMAGE_RESULT) },
                            skipCreditCheck = true
                        )
                    }
                }
            }
        },
        onRestorePurchases = viewModel::restorePurchases,
        onDismiss = viewModel::dismissProSheet
    )
}

private fun tabLayerModifier(selected: Boolean, alpha: Float): Modifier {
    return Modifier
        .fillMaxSize()
        .zIndex(if (selected) 1f else 0f)
        .graphicsLayer { this.alpha = alpha }
        .then(
            if (selected) {
                Modifier
            } else {
                Modifier
                    .clearAndSetSemantics {}
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
            }
        )
}
