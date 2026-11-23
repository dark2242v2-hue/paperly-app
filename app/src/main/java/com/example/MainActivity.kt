package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.core.configureAppCheck
import com.example.data.model.PdfToolType
import com.example.ui.components.AiUnlockGateModal
import com.example.ui.components.PaperlyBottomBarWithBanner
import com.example.ui.components.PaperlyProSheet
import com.example.ui.components.PaperlyTopBar
import com.example.ui.navigation.PaperlyRoutes
import com.example.ui.screens.DownloadReadyScreen
import com.example.ui.screens.FilesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PdfViewerScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ToolWorkspaceScreen
import com.example.ui.theme.PaperlyTheme
import com.example.ui.viewmodel.PaperlyViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configure Firebase App Check with launch intent before any UI or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val viewModel: PaperlyViewModel = viewModel()
            val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()

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
                PaperlyApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun PaperlyApp(viewModel: PaperlyViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isDarkMode by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val isPro by viewModel.isPro.collectAsStateWithLifecycle()
    val defaultCompression by viewModel.defaultCompression.collectAsStateWithLifecycle()
    val recentRecords by viewModel.recentRecords.collectAsStateWithLifecycle()
    val workspace by viewModel.workspace.collectAsStateWithLifecycle()
    val aiSummaryState by viewModel.aiSummaryState.collectAsStateWithLifecycle()
    val aiGateModal by viewModel.aiGateModal.collectAsStateWithLifecycle()
    val showProSheet by viewModel.showProSheet.collectAsStateWithLifecycle()
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackbarMessage) {
        val msg = snackbarMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbar()
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
                    onNavigate = { targetRoute ->
                        navController.navigate(targetRoute) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onProClick = viewModel::openProSheet
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
                startDestination = PaperlyRoutes.HOME
            ) {
                composable(PaperlyRoutes.HOME) {
                    HomeScreen(
                        recentRecords = recentRecords,
                        onToolSelected = { tool ->
                            if (tool == PdfToolType.AI_SUMMARIZE) {
                                viewModel.openAiGateOrProceed {
                                    navController.navigate(PaperlyRoutes.toolWorkspace(PdfToolType.AI_SUMMARIZE))
                                }
                            } else {
                                viewModel.prepareToolWorkspace(tool)
                                navController.navigate(PaperlyRoutes.toolWorkspace(tool))
                            }
                        },
                        onRecentSelected = { rec ->
                            navController.navigate(PaperlyRoutes.pdfViewer(rec.id))
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

                composable(PaperlyRoutes.FILES) {
                    FilesScreen(
                        records = recentRecords,
                        viewModel = viewModel,
                        onOpenViewer = { id ->
                            navController.navigate(PaperlyRoutes.pdfViewer(id))
                        }
                    )
                }

                composable(PaperlyRoutes.SETTINGS) {
                    SettingsScreen(
                        isDarkMode = isDarkMode,
                        isPro = isPro,
                        defaultCompression = defaultCompression,
                        savedFileCount = recentRecords.size,
                        onToggleDarkMode = viewModel::toggleDarkMode,
                        onOpenProSheet = viewModel::openProSheet,
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
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onProcessingComplete = { recordId ->
                            navController.navigate(PaperlyRoutes.downloadReady(recordId)) {
                                popUpTo(PaperlyRoutes.HOME)
                            }
                        }
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
                        viewModel = viewModel,
                        onPreviewPdf = { id ->
                            navController.navigate(PaperlyRoutes.pdfViewer(id))
                        },
                        onDone = {
                            navController.popBackStack(PaperlyRoutes.HOME, inclusive = false)
                        },
                        onOpenProSheet = viewModel::openProSheet
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
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }

    AiUnlockGateModal(
        state = aiGateModal,
        onWatchVideo = {
            viewModel.startRewardedVideoUnlock {
                navController.navigate(PaperlyRoutes.toolWorkspace(PdfToolType.AI_SUMMARIZE))
            }
        },
        onUpgradePro = {
            viewModel.upgradeToProFromAiModal {
                navController.navigate(PaperlyRoutes.toolWorkspace(PdfToolType.AI_SUMMARIZE))
            }
        },
        onDismiss = viewModel::dismissAiGateModal
    )

    PaperlyProSheet(
        visible = showProSheet,
        isPro = isPro,
        onTogglePro = viewModel::setProEnabled,
        onDismiss = viewModel::dismissProSheet
    )
}
