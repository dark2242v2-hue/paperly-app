package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.IntentCompat
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ads.findActivity
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

class MainActivity : ComponentActivity() {
    private val pendingExternalPdfUriState = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState == null) {
            extractIncomingPdfUri(intent)?.let { uri ->
                pendingExternalPdfUriState.value = uri
            }
        }

        enableEdgeToEdge()

        setContent {
            val viewModel: PaperlyViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
            val pendingExternalPdfUri by pendingExternalPdfUriState

            LaunchedEffect(isDarkMode) {
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

            PaperlyTheme(darkTheme = isDarkMode) {
                PaperlyApp(
                    viewModel = viewModel,
                    pendingExternalPdfUri = pendingExternalPdfUri,
                    onConsumeExternalPdfUri = { pendingExternalPdfUriState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractIncomingPdfUri(intent)?.let { uri ->
            pendingExternalPdfUriState.value = uri
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
    pendingExternalPdfUri: Uri? = null,
    onConsumeExternalPdfUri: () -> Unit = {}
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

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
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val imageWorkspace by viewModel.imageWorkspace.collectAsStateWithLifecycle()
    val ocrState by viewModel.ocrState.collectAsStateWithLifecycle()
    val processedImageResult by viewModel.processedImageResult.collectAsStateWithLifecycle()
    val aiSummaryState by viewModel.aiSummaryState.collectAsStateWithLifecycle()
    val proSheetState by viewModel.proSheetState.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val activity = LocalContext.current.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshBillingOnResume()
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

    LaunchedEffect(pendingExternalPdfUri) {
        val externalUri = pendingExternalPdfUri
        if (externalUri != null) {
            onConsumeExternalPdfUri()
            viewModel.handleIncomingExternalPdf(externalUri) { openedRecordId ->
                navController.navigate(PaperlyRoutes.pdfViewer(openedRecordId)) {
                    launchSingleTop = true
                }
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

    val isMainTab = currentRoute in listOf(
        PaperlyRoutes.HOME,
        PaperlyRoutes.FILES,
        PaperlyRoutes.SETTINGS
    )

    Scaffold(
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
                    currentRoute = currentRoute,
                    isPro = isPro,
                    showHomeBanner = currentRoute == PaperlyRoutes.HOME,
                    onNavigate = { targetRoute ->
                        navController.navigate(targetRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (isMainTab) innerPadding else androidx.compose.foundation.layout.PaddingValues())
        ) {
            NavHost(
                navController = navController,
                startDestination = PaperlyRoutes.HOME,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None }
            ) {
                composable(
                    route = PaperlyRoutes.HOME,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                ) {
                    HomeScreen(
                        selectedCategory = homeCategory,
                        recentRecords = recentRecords,
                        recentImages = recentImages,
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
                            navController.navigate(PaperlyRoutes.FILES) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(
                    route = PaperlyRoutes.FILES,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                ) {
                    FilesScreen(
                        records = recentRecords,
                        viewModel = viewModel,
                        onOpenViewer = { id ->
                            navController.navigate(PaperlyRoutes.pdfViewer(id))
                        }
                    )
                }

                composable(
                    route = PaperlyRoutes.SETTINGS,
                    enterTransition = { EnterTransition.None },
                    exitTransition = { ExitTransition.None },
                    popEnterTransition = { EnterTransition.None },
                    popExitTransition = { ExitTransition.None }
                ) {
                    SettingsScreen(
                        isDarkMode = isDarkMode,
                        isPro = isPro,
                        isRestoringPurchases = billingState.isRestoring,
                        debugProOverride = debugProOverride,
                        defaultCompression = defaultCompression,
                        savedFileCount = recentRecords.size,
                        onToggleDarkMode = viewModel::toggleDarkMode,
                        onOpenProSheet = viewModel::openProSheet,
                        onRestorePurchases = viewModel::restorePurchases,
                        onToggleDebugPro = viewModel::setDebugProOverride,
                        onSelectCompression = viewModel::setDefaultCompression,
                        onClearAllFiles = viewModel::clearAllLocalFiles
                    )
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
                                popUpTo(PaperlyRoutes.HOME)
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
                            navController.popBackStack(PaperlyRoutes.HOME, inclusive = false)
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
                            navController.popBackStack(PaperlyRoutes.HOME, inclusive = false)
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
