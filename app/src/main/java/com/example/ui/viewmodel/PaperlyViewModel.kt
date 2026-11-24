package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.ads.PaperlyAdManager
import com.example.billing.BillingUiState
import com.example.billing.PaperlyBillingManager
import com.example.billing.ProPlanType
import com.example.data.ai.AiImageRepository
import com.example.data.ai.AiSummarizeRepository
import com.example.data.image.ImageEngine
import com.example.data.local.PaperlyDatabase
import com.example.data.local.PdfRecordEntity
import com.example.data.local.PdfRecordRepository
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.AiImageAspectRatio
import com.example.data.model.AiImageStyle
import com.example.data.model.AiSummaryUiState
import com.example.data.model.CompressionLevel
import com.example.data.model.CropAspectPreset
import com.example.data.model.HomeToolCategory
import com.example.data.model.ImageOutputFormat
import com.example.data.model.ImageToolType
import com.example.data.model.OcrUiState
import com.example.data.model.PdfToolType
import com.example.data.model.PickedImageInfo
import com.example.data.model.PickedPdfInfo
import com.example.data.model.ProcessedImageResult
import com.example.data.model.SignaturePlacement
import com.example.data.model.TargetSizeUnit
import com.example.data.model.UpscaleTarget
import com.example.data.pdf.PdfEngine
import com.example.security.SecurityGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

private const val TAG = "PaperlyViewModel"

data class ProcessingState(
    val isProcessing: Boolean = false,
    val progress: Float = 0f,
    val message: String = "",
    val errorMessage: String? = null
)

data class WorkspaceUiState(
    val activeTool: PdfToolType = PdfToolType.MERGE,
    val selectedPdfs: List<PickedPdfInfo> = emptyList(),
    val splitPageRange: String = "1",
    val compressionLevel: CompressionLevel = CompressionLevel.BALANCED,
    val compressUseExactTarget: Boolean = false,
    val compressTargetValue: String = "200",
    val compressTargetUnit: TargetSizeUnit = TargetSizeUnit.KB,
    val scanImageUris: List<Uri> = emptyList(),
    val scanEnhanceContrast: Boolean = true,
    val signTargetPage: Int = 1,
    val signPlacement: SignaturePlacement = SignaturePlacement.BOTTOM_RIGHT,
    val signCenterXNorm: Float = 0.72f,
    val signCenterYNorm: Float = 0.80f,
    val signWidthFraction: Float = 0.28f,
    val lockPassword: String = "",
    val lockConfirmPassword: String = "",
    val outputFileName: String = "",
    val previewThumbnail: Bitmap? = null,
    val processing: ProcessingState = ProcessingState()
)

data class ImageWorkspaceUiState(
    val activeTool: ImageToolType = ImageToolType.COMPRESS_IMAGE,
    val pickedImage: PickedImageInfo? = null,
    val previewBitmap: Bitmap? = null,
    val compressTargetValue: String = "100",
    val compressTargetUnit: TargetSizeUnit = TargetSizeUnit.KB,
    val outputFormat: ImageOutputFormat = ImageOutputFormat.JPG,
    val cropLeftNorm: Float = 0.05f,
    val cropTopNorm: Float = 0.05f,
    val cropRightNorm: Float = 0.95f,
    val cropBottomNorm: Float = 0.95f,
    val cropAspectPreset: CropAspectPreset = CropAspectPreset.FREE,
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val resizeWidthText: String = "",
    val resizeHeightText: String = "",
    val lockAspectRatio: Boolean = true,
    val aiPrompt: String = "",
    val aiStyle: AiImageStyle = AiImageStyle.PHOTOREALISTIC,
    val aiAspectRatio: AiImageAspectRatio = AiImageAspectRatio.SQUARE,
    val upscaleTarget: UpscaleTarget = UpscaleTarget.HD_2X,
    val cutoutPngPath: String? = null,
    val cutoutPreviewBitmap: Bitmap? = null,
    val selectedBgColorArgb: Int? = null,
    val passportRemoveBg: Boolean = true,
    val passportBgColorArgb: Int = 0xFFFFFFFF.toInt(),
    val passportCopyCount: Int = 6,
    val passportExportAsPdf: Boolean = true,
    val passportTilePreview: Bitmap? = null,
    val processing: ProcessingState = ProcessingState()
)

data class ProSheetUiState(
    val visible: Boolean = false,
    val triggeredByTool: PdfToolType? = null,
    val triggeredByImageTool: ImageToolType? = null,
    val navigateOnUnlock: Boolean = true,
    val preloadedRecord: PdfRecordEntity? = null,
    val isLoadingRewardedAd: Boolean = false,
    val rewardedError: String? = null
)

class PaperlyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PaperlyDatabase.getInstance(application)
    private val recordRepository = PdfRecordRepository(database.pdfRecordDao())
    private val prefsRepository = UserPreferencesRepository(application)
    private val aiRepository = AiSummarizeRepository()
    private val aiImageRepository = AiImageRepository()
    val pdfEngine = PdfEngine(application)
    val imageEngine = ImageEngine(application)
    val adManager = PaperlyAdManager(application)

    val billingManager: PaperlyBillingManager = PaperlyBillingManager(
        context = application,
        onEntitlementUpdated = { isActive, productId ->
            prefsRepository.setPlayProActive(isActive, productId)
        },
        onUserMessage = { msg ->
            _snackbarMessage.value = msg
        }
    )

    val billingState: StateFlow<BillingUiState> = billingManager.uiState

    private val isAppIntegrityTrusted: Boolean by lazy {
        SecurityGuard.isAppTrusted(application)
    }

    private val _homeCategory = MutableStateFlow(HomeToolCategory.PDF)
    val homeCategory: StateFlow<HomeToolCategory> = _homeCategory.asStateFlow()

    private val _workspace = MutableStateFlow(WorkspaceUiState())
    val workspace: StateFlow<WorkspaceUiState> = _workspace.asStateFlow()

    private val _imageWorkspace = MutableStateFlow(ImageWorkspaceUiState())
    val imageWorkspace: StateFlow<ImageWorkspaceUiState> = _imageWorkspace.asStateFlow()

    private val _ocrState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val ocrState: StateFlow<OcrUiState> = _ocrState.asStateFlow()

    private val _processedImageResult = MutableStateFlow<ProcessedImageResult?>(null)
    val processedImageResult: StateFlow<ProcessedImageResult?> = _processedImageResult.asStateFlow()

    private val _aiSummaryState = MutableStateFlow<AiSummaryUiState>(AiSummaryUiState.Idle)
    val aiSummaryState: StateFlow<AiSummaryUiState> = _aiSummaryState.asStateFlow()

    private val _proSheetState = MutableStateFlow(ProSheetUiState())
    val proSheetState: StateFlow<ProSheetUiState> = _proSheetState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private val _shouldRequestInAppReview = MutableStateFlow(false)
    val shouldRequestInAppReview: StateFlow<Boolean> = _shouldRequestInAppReview.asStateFlow()

    private val _isRefreshingFiles = MutableStateFlow(false)
    val isRefreshingFiles: StateFlow<Boolean> = _isRefreshingFiles.asStateFlow()

    private var hasScannedFilesOnce = false
    private var aiJob: Job? = null
    private var imageJob: Job? = null

    val recentRecords: StateFlow<List<PdfRecordEntity>> = recordRepository.recentRecords
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isDarkMode: StateFlow<Boolean> = prefsRepository.isDarkMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val debugProOverride: StateFlow<Boolean> = prefsRepository.debugProOverride
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val activePlanProductId: StateFlow<String?> = prefsRepository.activePlanProductId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPro: StateFlow<Boolean> = combine(
        prefsRepository.playProActive,
        prefsRepository.debugProOverride
    ) { playActive, debugOverride ->
        isAppIntegrityTrusted && (playActive || (BuildConfig.DEBUG && debugOverride))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val defaultCompression: StateFlow<CompressionLevel> = prefsRepository.defaultCompression
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompressionLevel.BALANCED)

    val completedCorePdfOps: StateFlow<Int> = prefsRepository.completedCorePdfOps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val aiSummaryCredits: StateFlow<Int> = prefsRepository.aiSummaryCredits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val aiImageCredits: StateFlow<Int> = prefsRepository.aiImageCredits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val upscaleCredits: StateFlow<Int> = prefsRepository.upscaleCredits
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isRewardUnlockedForSession: StateFlow<Boolean> = combine(
        isPro,
        prefsRepository.aiUnlockedUntilMillis
    ) { pro, unlockedUntil ->
        pro || unlockedUntil > System.currentTimeMillis()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        adManager.initialize(preloadRewarded = true)
        if (isAppIntegrityTrusted) {
            billingManager.initialize()
        } else {
            viewModelScope.launch {
                prefsRepository.setPlayProActive(false, null)
            }
        }
        refreshFilesCache(force = false)
    }

    fun refreshFilesCache(force: Boolean = false) {
        if (_isRefreshingFiles.value) return
        if (hasScannedFilesOnce && !force) return
        hasScannedFilesOnce = true
        viewModelScope.launch(Dispatchers.IO) {
            if (force) {
                _isRefreshingFiles.value = true
            }
            try {
                val existing = recordRepository.getAllSnapshot()
                val existingPaths = existing.mapTo(HashSet()) { it.filePath }
                existing.forEach { record ->
                    if (!File(record.filePath).exists()) {
                        recordRepository.deleteRecord(record)
                    }
                }
                val dirFiles = pdfEngine.pdfsDir.listFiles { file ->
                    file.isFile && file.extension.equals("pdf", ignoreCase = true)
                }.orEmpty()
                dirFiles.forEach { file ->
                    if (file.absolutePath !in existingPaths) {
                        val pages = runCatching { pdfEngine.countPages(file) }.getOrDefault(1).coerceAtLeast(1)
                        recordRepository.insert(
                            PdfRecordEntity(
                                displayName = file.name,
                                filePath = file.absolutePath,
                                operationTag = "Saved",
                                pageCount = pages,
                                sizeBytes = file.length(),
                                createdAtMillis = file.lastModified().takeIf { it > 0L }
                                    ?: System.currentTimeMillis()
                            )
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Silent files cache sync failed", e)
            } finally {
                _isRefreshingFiles.value = false
            }
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            prefsRepository.setDarkMode(!isDarkMode.value)
        }
    }

    fun subscribeToPlan(activity: Activity?, planType: ProPlanType) {
        billingManager.launchSubscriptionPurchase(activity, planType)
    }

    fun restorePurchases() {
        billingManager.restorePurchases()
    }

    fun refreshBillingOnResume() {
        viewModelScope.launch {
            billingManager.refreshOnResume()
        }
    }

    fun setDebugProOverride(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        viewModelScope.launch {
            prefsRepository.setDebugProOverride(enabled)
            _snackbarMessage.value = if (enabled) {
                "Debug Pro Override enabled — Ads hidden"
            } else {
                adManager.preloadRewardedAd()
                "Debug Pro Override disabled — Free tier active"
            }
        }
    }

    fun setDefaultCompression(level: CompressionLevel) {
        viewModelScope.launch {
            prefsRepository.setDefaultCompression(level)
            _workspace.update { it.copy(compressionLevel = level) }
        }
    }

    fun openProSheet() {
        billingManager.clearStatusMessage()
        _proSheetState.value = ProSheetUiState(visible = true, triggeredByTool = null)
    }

    fun dismissProSheet() {
        billingManager.clearStatusMessage()
        _proSheetState.value = ProSheetUiState(visible = false)
    }

    fun consumeSnackbar() {
        _snackbarMessage.value = null
    }

    fun postMessage(message: String) {
        _snackbarMessage.value = message
    }

    fun consumeInAppReviewTrigger() {
        _shouldRequestInAppReview.value = false
    }

    fun prepareToolWorkspace(tool: PdfToolType, preloadedRecord: PdfRecordEntity? = null) {
        val currentCompression = defaultCompression.value
        aiJob?.cancel()
        _aiSummaryState.value = AiSummaryUiState.Idle
        _workspace.value = WorkspaceUiState(
            activeTool = tool,
            compressionLevel = currentCompression,
            outputFileName = when (tool) {
                PdfToolType.MERGE -> "Paperly_Merged"
                PdfToolType.SPLIT -> "Paperly_Split"
                PdfToolType.COMPRESS -> "Paperly_Compressed"
                PdfToolType.SCAN -> "Paperly_Scan"
                PdfToolType.SIGN -> "Paperly_Signed"
                PdfToolType.LOCK -> "Paperly_Locked"
                PdfToolType.AI_SUMMARIZE -> ""
            }
        )
        if (preloadedRecord != null) {
            selectExistingRecord(preloadedRecord)
        }
    }

    fun openToolWithRecord(
        tool: PdfToolType,
        record: PdfRecordEntity,
        onNavigateToWorkspace: () -> Unit
    ) {
        if (tool == PdfToolType.AI_SUMMARIZE && !isAppIntegrityTrusted) {
            _snackbarMessage.value = "AI Summarize requires the official Google Play release of Paperly."
            return
        }
        val canProceed = when (tool) {
            PdfToolType.AI_SUMMARIZE -> isPro.value || aiSummaryCredits.value > 0
            PdfToolType.SIGN -> isRewardUnlockedForSession.value
            else -> true
        }
        if (canProceed) {
            prepareToolWorkspace(tool, preloadedRecord = record)
            onNavigateToWorkspace()
        } else {
            adManager.preloadRewardedAd()
            billingManager.clearStatusMessage()
            _proSheetState.value = ProSheetUiState(
                visible = true,
                triggeredByTool = tool,
                navigateOnUnlock = true,
                preloadedRecord = record
            )
        }
    }

    fun handleIncomingExternalPdf(uri: Uri, onOpenedRecord: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val record = pdfEngine.importExternalPdfRecord(uri)
                val id = recordRepository.insert(record)
                onOpenedRecord(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open external PDF", e)
                _snackbarMessage.value = e.localizedMessage ?: "Could not open this PDF file."
            }
        }
    }

    fun openRewardedGateOrProceed(tool: PdfToolType, onNavigateToWorkspace: () -> Unit) {
        if (tool == PdfToolType.AI_SUMMARIZE && !isAppIntegrityTrusted) {
            _snackbarMessage.value = "AI Summarize requires the official Google Play release of Paperly."
            return
        }
        val canProceed = when (tool) {
            PdfToolType.AI_SUMMARIZE -> isPro.value || aiSummaryCredits.value > 0
            PdfToolType.SIGN -> isRewardUnlockedForSession.value
            else -> true
        }
        if (canProceed) {
            prepareToolWorkspace(tool)
            onNavigateToWorkspace()
        } else {
            // Trigger 1 (High-Intent Gate): Show the Pro paywall with 'Start 3-Day Free Trial' as primary
            // and rewarded unlock CTA as a secondary button below.
            adManager.preloadRewardedAd()
            billingManager.clearStatusMessage()
            _proSheetState.value = ProSheetUiState(
                visible = true,
                triggeredByTool = tool,
                navigateOnUnlock = true
            )
        }
    }

    fun openAiSummaryPaywallInWorkspace() {
        adManager.preloadRewardedAd()
        billingManager.clearStatusMessage()
        _proSheetState.value = ProSheetUiState(
            visible = true,
            triggeredByTool = PdfToolType.AI_SUMMARIZE,
            navigateOnUnlock = false
        )
    }

    fun watchRewardedVideoFromProSheet(
        activity: Activity?,
        onUnlocked: (PdfToolType?, ImageToolType?, Boolean, PdfRecordEntity?) -> Unit
    ) {
        val currentSheet = _proSheetState.value
        val pdfTool = currentSheet.triggeredByTool
        val imgTool = currentSheet.triggeredByImageTool
        if (pdfTool == null && imgTool == null) return
        val shouldNavigate = currentSheet.navigateOnUnlock
        val preloaded = currentSheet.preloadedRecord
        if (activity == null) {
            _proSheetState.update {
                it.copy(isLoadingRewardedAd = false, rewardedError = "Unable to present video ad right now.")
            }
            return
        }

        _proSheetState.update { it.copy(isLoadingRewardedAd = true, rewardedError = null) }
        adManager.showRewardedAd(
            activity = activity,
            onUserEarnedReward = {
                viewModelScope.launch {
                    when {
                        pdfTool == PdfToolType.AI_SUMMARIZE -> prefsRepository.grantAiSummaryCredit(1)
                        pdfTool != null -> prefsRepository.grantAiSessionUnlock()
                        imgTool == ImageToolType.TEXT_TO_IMAGE -> prefsRepository.grantAiImageCredit(1)
                        imgTool == ImageToolType.UPSCALER -> prefsRepository.grantUpscaleCredit(1)
                    }
                    _proSheetState.value = ProSheetUiState(visible = false)
                    if (shouldNavigate) {
                        if (pdfTool != null) {
                            prepareToolWorkspace(pdfTool, preloadedRecord = preloaded)
                        } else if (imgTool != null) {
                            prepareImageToolWorkspace(imgTool)
                        }
                    }
                    onUnlocked(pdfTool, imgTool, shouldNavigate, preloaded)
                }
            },
            onDismissedWithoutReward = {
                val errorText = when {
                    pdfTool == PdfToolType.AI_SUMMARIZE ->
                        "Finish watching the short video to unlock 1 free summary."
                    imgTool == ImageToolType.TEXT_TO_IMAGE ->
                        "Finish watching the short video to unlock 1 free AI image."
                    imgTool == ImageToolType.UPSCALER ->
                        "Finish watching the short video to unlock 1 free AI upscale."
                    else ->
                        "Finish watching the short video to unlock this session free."
                }
                _proSheetState.update {
                    it.copy(
                        isLoadingRewardedAd = false,
                        rewardedError = errorText
                    )
                }
            },
            onLoadFailed = { msg ->
                _proSheetState.update {
                    it.copy(isLoadingRewardedAd = false, rewardedError = msg)
                }
            }
        )
    }

    fun addPickedPdfUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _workspace.update {
                it.copy(processing = it.processing.copy(isProcessing = true, message = "Reading PDF...", progress = 0.25f, errorMessage = null))
            }
            try {
                val imported = uris.map { uri -> pdfEngine.importPickedPdf(uri) }
                val tool = _workspace.value.activeTool
                val updatedList = if (tool == PdfToolType.MERGE) {
                    _workspace.value.selectedPdfs + imported
                } else {
                    listOf(imported.first())
                }
                val first = updatedList.firstOrNull()
                val preview = first?.let { pdfEngine.renderPageThumbnail(it.localPath, pageIndex = 0, maxWidth = 640) }
                _workspace.update { state ->
                    state.copy(
                        selectedPdfs = updatedList,
                        splitPageRange = if (first != null && first.pageCount > 1) "1-${minOf(2, first.pageCount)}" else "1",
                        signTargetPage = 1,
                        previewThumbnail = preview,
                        processing = ProcessingState()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import PDF", e)
                _workspace.update {
                    it.copy(processing = ProcessingState(errorMessage = e.localizedMessage ?: "Could not open selected PDF."))
                }
            }
        }
    }

    fun selectExistingRecord(record: PdfRecordEntity) {
        viewModelScope.launch {
            val info = pdfEngine.fromExistingRecord(record)
            val tool = _workspace.value.activeTool
            val updatedList = if (tool == PdfToolType.MERGE) {
                _workspace.value.selectedPdfs + info
            } else {
                listOf(info)
            }
            val first = updatedList.firstOrNull()
            val preview = first?.let { pdfEngine.renderPageThumbnail(it.localPath, pageIndex = 0, maxWidth = 640) }
            _workspace.update { state ->
                state.copy(
                    selectedPdfs = updatedList,
                    splitPageRange = if (first != null && first.pageCount > 1) "1-${minOf(2, first.pageCount)}" else "1",
                    signTargetPage = 1,
                    previewThumbnail = preview,
                    processing = ProcessingState()
                )
            }
        }
    }

    fun removePdfAt(index: Int) {
        _workspace.update { state ->
            val mutable = state.selectedPdfs.toMutableList()
            if (index in mutable.indices) mutable.removeAt(index)
            state.copy(selectedPdfs = mutable)
        }
    }

    fun movePdfUp(index: Int) {
        if (index <= 0) return
        _workspace.update { state ->
            val mutable = state.selectedPdfs.toMutableList()
            if (index in mutable.indices) {
                val item = mutable.removeAt(index)
                mutable.add(index - 1, item)
            }
            state.copy(selectedPdfs = mutable)
        }
    }

    fun movePdfDown(index: Int) {
        _workspace.update { state ->
            val mutable = state.selectedPdfs.toMutableList()
            if (index in 0 until mutable.lastIndex) {
                val item = mutable.removeAt(index)
                mutable.add(index + 1, item)
            }
            state.copy(selectedPdfs = mutable)
        }
    }

    fun updateSplitPageRange(spec: String) {
        _workspace.update { it.copy(splitPageRange = spec, processing = it.processing.copy(errorMessage = null)) }
    }

    fun updateCompressionLevel(level: CompressionLevel) {
        _workspace.update {
            it.copy(
                compressionLevel = level,
                compressUseExactTarget = false,
                processing = it.processing.copy(errorMessage = null)
            )
        }
    }

    fun updatePdfTargetCompression(useExact: Boolean, valueText: String, unit: TargetSizeUnit) {
        val cleaned = valueText.filter { it.isDigit() || it == '.' }.take(7)
        _workspace.update {
            it.copy(
                compressUseExactTarget = useExact,
                compressTargetValue = cleaned,
                compressTargetUnit = unit,
                processing = it.processing.copy(errorMessage = null)
            )
        }
    }

    fun updateOutputFileName(name: String) {
        _workspace.update { it.copy(outputFileName = name) }
    }

    fun addScanImageUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _workspace.update { state ->
            state.copy(scanImageUris = state.scanImageUris + uris, processing = state.processing.copy(errorMessage = null))
        }
    }

    fun importScanImageUris(uris: List<Uri>, fromSmartScan: Boolean = false) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val imported = pdfEngine.importScanUris(uris)
            if (imported.isEmpty()) {
                _snackbarMessage.value = "Could not load selected image(s)."
                return@launch
            }
            _workspace.update { state ->
                val isFirstBatch = state.scanImageUris.isEmpty()
                state.copy(
                    scanImageUris = state.scanImageUris + imported,
                    scanEnhanceContrast = if (isFirstBatch) !fromSmartScan else state.scanEnhanceContrast,
                    processing = state.processing.copy(errorMessage = null)
                )
            }
        }
    }

    fun moveScanImageLeft(index: Int) {
        if (index <= 0) return
        _workspace.update { state ->
            val mutable = state.scanImageUris.toMutableList()
            if (index in mutable.indices) {
                val item = mutable.removeAt(index)
                mutable.add(index - 1, item)
            }
            state.copy(scanImageUris = mutable)
        }
    }

    fun moveScanImageRight(index: Int) {
        _workspace.update { state ->
            val mutable = state.scanImageUris.toMutableList()
            if (index in 0 until mutable.lastIndex) {
                val item = mutable.removeAt(index)
                mutable.add(index + 1, item)
            }
            state.copy(scanImageUris = mutable)
        }
    }

    fun removeScanImageAt(index: Int) {
        _workspace.update { state ->
            val mutable = state.scanImageUris.toMutableList()
            if (index in mutable.indices) mutable.removeAt(index)
            state.copy(scanImageUris = mutable)
        }
    }

    fun clearScanImages() {
        _workspace.update { it.copy(scanImageUris = emptyList(), scanEnhanceContrast = true) }
    }

    fun toggleScanEnhance(enabled: Boolean) {
        _workspace.update { it.copy(scanEnhanceContrast = enabled) }
    }

    fun updateSignTargetPage(page: Int) {
        val maxPage = _workspace.value.selectedPdfs.firstOrNull()?.pageCount ?: 1
        val clamped = page.coerceIn(1, maxPage)
        _workspace.update { it.copy(signTargetPage = clamped) }
        val filePath = _workspace.value.selectedPdfs.firstOrNull()?.localPath ?: return
        viewModelScope.launch {
            val thumb = pdfEngine.renderPageThumbnail(filePath, pageIndex = clamped - 1, maxWidth = 640)
            _workspace.update { it.copy(previewThumbnail = thumb) }
        }
    }

    fun updateSignPlacement(placement: SignaturePlacement) {
        val (cx, cy) = when (placement) {
            SignaturePlacement.BOTTOM_RIGHT -> 0.75f to 0.84f
            SignaturePlacement.BOTTOM_CENTER -> 0.50f to 0.84f
            SignaturePlacement.BOTTOM_LEFT -> 0.25f to 0.84f
            SignaturePlacement.CENTER -> 0.50f to 0.50f
        }
        _workspace.update {
            it.copy(
                signPlacement = placement,
                signCenterXNorm = cx,
                signCenterYNorm = cy
            )
        }
    }

    fun updateSignTransform(centerXNorm: Float, centerYNorm: Float, widthFraction: Float) {
        _workspace.update {
            it.copy(
                signCenterXNorm = centerXNorm.coerceIn(0.08f, 0.92f),
                signCenterYNorm = centerYNorm.coerceIn(0.08f, 0.92f),
                signWidthFraction = widthFraction.coerceIn(0.12f, 0.75f)
            )
        }
    }

    fun updateLockPassword(password: String) {
        _workspace.update {
            it.copy(lockPassword = password, processing = it.processing.copy(errorMessage = null))
        }
    }

    fun updateLockConfirmPassword(confirm: String) {
        _workspace.update {
            it.copy(lockConfirmPassword = confirm, processing = it.processing.copy(errorMessage = null))
        }
    }

    fun processActiveTool(
        signatureBitmap: Bitmap? = null,
        onSuccess: (Long) -> Unit
    ) {
        val state = _workspace.value
        if (state.processing.isProcessing) return

        viewModelScope.launch {
            _workspace.update {
                it.copy(processing = ProcessingState(isProcessing = true, progress = 0.05f, message = "Starting..."))
            }
            try {
                val record = when (state.activeTool) {
                    PdfToolType.MERGE -> {
                        pdfEngine.mergePdfs(state.selectedPdfs, state.outputFileName) { p, msg ->
                            _workspace.update { it.copy(processing = ProcessingState(true, p, msg)) }
                        }
                    }
                    PdfToolType.SPLIT -> {
                        val file = state.selectedPdfs.firstOrNull()
                            ?: throw IllegalStateException("Select a PDF file first.")
                        pdfEngine.splitPdf(file, state.splitPageRange, state.outputFileName) { p, msg ->
                            _workspace.update { it.copy(processing = ProcessingState(true, p, msg)) }
                        }
                    }
                    PdfToolType.COMPRESS -> {
                        val file = state.selectedPdfs.firstOrNull()
                            ?: throw IllegalStateException("Select a PDF file first.")
                        if (state.compressUseExactTarget) {
                            val num = state.compressTargetValue.toDoubleOrNull()
                                ?: throw IllegalStateException("Enter a valid target size in ${state.compressTargetUnit.label}.")
                            require(num > 0.0) { "Target size must be greater than 0." }
                            val targetBytes = (num * state.compressTargetUnit.multiplierBytes).toLong()
                            pdfEngine.compressPdfToTargetSize(file, targetBytes, state.outputFileName) { p, msg ->
                                _workspace.update { it.copy(processing = ProcessingState(true, p, msg)) }
                            }
                        } else {
                            pdfEngine.compressPdf(file, state.compressionLevel, state.outputFileName) { p, msg ->
                                _workspace.update { it.copy(processing = ProcessingState(true, p, msg)) }
                            }
                        }
                    }
                    PdfToolType.SCAN -> {
                        pdfEngine.imagesToPdf(state.scanImageUris, state.scanEnhanceContrast, state.outputFileName) { p, msg ->
                            _workspace.update { it.copy(processing = ProcessingState(true, p, msg)) }
                        }
                    }
                    PdfToolType.SIGN -> {
                        val file = state.selectedPdfs.firstOrNull()
                            ?: throw IllegalStateException("Select a PDF file first.")
                        val sig = signatureBitmap
                            ?: throw IllegalStateException("Draw your signature in the pad before processing.")
                        pdfEngine.signPdf(
                            file = file,
                            signatureBitmap = sig,
                            targetPageOneBased = state.signTargetPage,
                            centerXNorm = state.signCenterXNorm,
                            centerYNorm = state.signCenterYNorm,
                            widthFraction = state.signWidthFraction,
                            outputName = state.outputFileName
                        ) { p, msg ->
                            _workspace.update { it.copy(processing = ProcessingState(true, p, msg)) }
                        }
                    }
                    PdfToolType.LOCK -> {
                        val file = state.selectedPdfs.firstOrNull()
                            ?: throw IllegalStateException("Select a PDF file first.")
                        if (state.lockPassword.length < 4) {
                            throw IllegalStateException("Enter a password with at least 4 characters.")
                        }
                        if (state.lockPassword != state.lockConfirmPassword) {
                            throw IllegalStateException("Passwords do not match.")
                        }
                        pdfEngine.lockPdf(
                            file = file,
                            password = state.lockPassword,
                            outputName = state.outputFileName
                        ) { p, msg ->
                            _workspace.update { it.copy(processing = ProcessingState(true, p, msg)) }
                        }
                    }
                    PdfToolType.AI_SUMMARIZE -> throw IllegalStateException("Use Summarize with Gemini AI.")
                }

                val id = recordRepository.insert(record)
                if (state.activeTool == PdfToolType.MERGE ||
                    state.activeTool == PdfToolType.SPLIT ||
                    state.activeTool == PdfToolType.COMPRESS ||
                    state.activeTool == PdfToolType.LOCK
                ) {
                    prefsRepository.incrementCompletedCorePdfOps()
                }
                _workspace.update { it.copy(processing = ProcessingState()) }
                onSuccess(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "PDF processing failed", e)
                _workspace.update {
                    it.copy(
                        processing = ProcessingState(
                            isProcessing = false,
                            progress = 0f,
                            message = "",
                            errorMessage = e.localizedMessage ?: "Failed to process PDF."
                        )
                    )
                }
            }
        }
    }

    fun summarizeSelectedPdf() {
        if (!isAppIntegrityTrusted) {
            _aiSummaryState.value = AiSummaryUiState.Error(
                "AI Summarize is unavailable in unverified builds. Please install Paperly from Google Play."
            )
            return
        }
        if (!isPro.value && aiSummaryCredits.value <= 0) {
            openAiSummaryPaywallInWorkspace()
            return
        }
        val file = _workspace.value.selectedPdfs.firstOrNull()
        if (file == null) {
            _aiSummaryState.value = AiSummaryUiState.Error("Please select a PDF file first.")
            return
        }

        aiJob?.cancel()
        aiJob = viewModelScope.launch {
            _aiSummaryState.value = AiSummaryUiState.Extracting
            try {
                val payload = pdfEngine.extractAiPayload(file)
                _aiSummaryState.value = AiSummaryUiState.Loading
                val summary = aiRepository.summarizePdf(payload)
                _aiSummaryState.value = AiSummaryUiState.Formatting
                delay(240L)
                if (!isPro.value) {
                    prefsRepository.consumeAiSummaryCredit()
                }
                _aiSummaryState.value = AiSummaryUiState.Success(
                    text = summary,
                    fileName = payload.displayName,
                    pageCount = payload.pageCount,
                    wasTruncated = payload.wasTruncated
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI summary timed out", e)
                _aiSummaryState.value = AiSummaryUiState.Error(
                    "Summarization timed out. Please check your connection and try again."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "AI summary failed", e)
                _aiSummaryState.value = AiSummaryUiState.Error(
                    e.localizedMessage ?: "Failed to generate AI summary."
                )
            }
        }
    }

    fun exportSummaryAsPdf(
        sourceFileName: String,
        summaryText: String,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val record = pdfEngine.exportSummaryToPdf(sourceFileName, summaryText)
                val id = recordRepository.insert(record)
                onSuccess(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export summary PDF", e)
                _snackbarMessage.value = e.localizedMessage ?: "Could not export summary as PDF."
            }
        }
    }

    suspend fun loadRecordById(id: Long): PdfRecordEntity? = recordRepository.getById(id)

    fun saveRecordToUri(filePath: String, destinationUri: Uri, trackForReview: Boolean = false) {
        viewModelScope.launch {
            try {
                pdfEngine.saveToUserUri(filePath, destinationUri)
                _snackbarMessage.value = "Saved to device successfully"
                if (trackForReview) {
                    val promptReview = prefsRepository.incrementSavedPdfCountAndShouldPromptReview()
                    if (promptReview) {
                        _shouldRequestInAppReview.value = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download save failed", e)
                _snackbarMessage.value = e.localizedMessage ?: "Could not save PDF"
            }
        }
    }

    fun setHomeCategory(category: HomeToolCategory) {
        _homeCategory.value = category
    }

    fun prepareImageToolWorkspace(tool: ImageToolType) {
        imageJob?.cancel()
        _ocrState.value = OcrUiState.Idle
        _imageWorkspace.value = ImageWorkspaceUiState(
            activeTool = tool,
            outputFormat = if (tool == ImageToolType.BG_REMOVER) ImageOutputFormat.PNG else ImageOutputFormat.JPG,
            selectedBgColorArgb = null,
            passportRemoveBg = true,
            passportBgColorArgb = 0xFFFFFFFF.toInt()
        )
    }

    fun openImageToolOrGate(tool: ImageToolType, onNavigateToWorkspace: () -> Unit) {
        if (tool == ImageToolType.TEXT_TO_IMAGE && !isAppIntegrityTrusted) {
            _snackbarMessage.value = "AI Image Generator requires the official Google Play release of Paperly."
            return
        }
        val canProceed = when (tool) {
            ImageToolType.TEXT_TO_IMAGE -> isPro.value || aiImageCredits.value > 0
            ImageToolType.UPSCALER -> isPro.value || upscaleCredits.value > 0
            else -> true
        }
        if (canProceed) {
            prepareImageToolWorkspace(tool)
            onNavigateToWorkspace()
        } else {
            adManager.preloadRewardedAd()
            billingManager.clearStatusMessage()
            _proSheetState.value = ProSheetUiState(
                visible = true,
                triggeredByImageTool = tool,
                navigateOnUnlock = true
            )
        }
    }

    fun openImagePaywallInWorkspace(tool: ImageToolType) {
        adManager.preloadRewardedAd()
        billingManager.clearStatusMessage()
        _proSheetState.value = ProSheetUiState(
            visible = true,
            triggeredByImageTool = tool,
            navigateOnUnlock = false
        )
    }

    fun pickImageForWorkspace(uri: Uri) {
        val activeTool = _imageWorkspace.value.activeTool
        imageJob?.cancel()
        imageJob = viewModelScope.launch {
            _imageWorkspace.update {
                it.copy(
                    cutoutPngPath = null,
                    cutoutPreviewBitmap = null,
                    passportTilePreview = null,
                    processing = ProcessingState(true, 0.25f, "Loading image...")
                )
            }
            try {
                val info = imageEngine.importPickedImage(uri)
                val preview = imageEngine.decodePreviewBitmap(info.localPath, maxEdge = 1400)
                _imageWorkspace.update { state ->
                    state.copy(
                        pickedImage = info,
                        previewBitmap = preview,
                        cropLeftNorm = 0.0f,
                        cropTopNorm = 0.0f,
                        cropRightNorm = 1.0f,
                        cropBottomNorm = 1.0f,
                        cropAspectPreset = if (activeTool == ImageToolType.PASSPORT_SHEET) {
                            CropAspectPreset.PASSPORT
                        } else {
                            CropAspectPreset.FREE
                        },
                        rotationDegrees = 0,
                        flipHorizontal = false,
                        resizeWidthText = info.width.toString(),
                        resizeHeightText = info.height.toString(),
                        processing = ProcessingState()
                    )
                }

                when (activeTool) {
                    ImageToolType.IMAGE_TO_TEXT -> runOcrOnUri(uri)
                    ImageToolType.BG_REMOVER -> runBackgroundSegmentation(info)
                    ImageToolType.PASSPORT_SHEET -> {
                        if (_imageWorkspace.value.passportRemoveBg) {
                            runBackgroundSegmentation(info, updatePassportTile = true)
                        } else {
                            refreshPassportTilePreview()
                        }
                    }
                    else -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image import failed", e)
                _imageWorkspace.update {
                    it.copy(processing = ProcessingState(errorMessage = e.localizedMessage ?: "Failed to load image."))
                }
            }
        }
    }

    fun updateImageCompressionConfig(
        targetValue: String? = null,
        unit: TargetSizeUnit? = null,
        format: ImageOutputFormat? = null
    ) {
        _imageWorkspace.update { state ->
            val cleaned = targetValue?.filter { it.isDigit() || it == '.' }?.take(7) ?: state.compressTargetValue
            state.copy(
                compressTargetValue = cleaned,
                compressTargetUnit = unit ?: state.compressTargetUnit,
                outputFormat = format ?: state.outputFormat,
                processing = state.processing.copy(errorMessage = null)
            )
        }
    }

    fun runImageCompression(onResultReady: () -> Unit) {
        val state = _imageWorkspace.value
        val info = state.pickedImage ?: return
        val num = state.compressTargetValue.toDoubleOrNull()
        if (num == null || num <= 0.0) {
            _imageWorkspace.update {
                it.copy(processing = ProcessingState(errorMessage = "Enter a valid target file size."))
            }
            return
        }
        val targetBytes = (num * state.compressTargetUnit.multiplierBytes).toLong()
        viewModelScope.launch {
            _imageWorkspace.update {
                it.copy(processing = ProcessingState(true, 0.08f, "Compressing image..."))
            }
            try {
                val res = imageEngine.compressImageToTarget(info, targetBytes, state.outputFormat) { p, msg ->
                    _imageWorkspace.update { it.copy(processing = ProcessingState(true, p, msg)) }
                }
                _processedImageResult.value = res
                _imageWorkspace.update { it.copy(processing = ProcessingState()) }
                onResultReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _imageWorkspace.update {
                    it.copy(processing = ProcessingState(errorMessage = e.localizedMessage ?: "Compression failed."))
                }
            }
        }
    }

    fun updateCropRect(left: Float, top: Float, right: Float, bottom: Float) {
        _imageWorkspace.update { state ->
            val nextLeft = left.coerceIn(0f, 0.92f)
            val nextTop = top.coerceIn(0f, 0.92f)
            val nextRight = right.coerceIn(nextLeft + 0.08f, 1f)
            val nextBottom = bottom.coerceIn(nextTop + 0.08f, 1f)
            val baseInfo = state.pickedImage
            val newW: String
            val newH: String
            if (baseInfo != null) {
                val swapped = ((state.rotationDegrees % 180) + 180) % 180 != 0
                val srcW = if (swapped) baseInfo.height else baseInfo.width
                val srcH = if (swapped) baseInfo.width else baseInfo.height
                newW = max(16, ((nextRight - nextLeft) * srcW).roundToInt()).toString()
                newH = max(16, ((nextBottom - nextTop) * srcH).roundToInt()).toString()
            } else {
                newW = state.resizeWidthText
                newH = state.resizeHeightText
            }
            state.copy(
                cropLeftNorm = nextLeft,
                cropTopNorm = nextTop,
                cropRightNorm = nextRight,
                cropBottomNorm = nextBottom,
                resizeWidthText = newW,
                resizeHeightText = newH
            )
        }
    }

    fun selectCropAspectPreset(preset: CropAspectPreset) {
        _imageWorkspace.update { state ->
            val info = state.pickedImage
            if (info == null || preset.ratio == null) {
                return@update state.copy(cropAspectPreset = preset)
            }
            val swapped = ((state.rotationDegrees % 180) + 180) % 180 != 0
            val imgW = (if (swapped) info.height else info.width).toFloat()
            val imgH = (if (swapped) info.width else info.height).toFloat()
            val imgRatio = imgW / max(1f, imgH)
            val targetRatio = preset.ratio

            val normW: Float
            val normH: Float
            if (imgRatio > targetRatio) {
                normH = 0.92f
                normW = ((normH * imgH * targetRatio) / imgW).coerceIn(0.15f, 0.96f)
            } else {
                normW = 0.92f
                normH = ((normW * imgW / targetRatio) / imgH).coerceIn(0.15f, 0.96f)
            }
            val l = (1f - normW) / 2f
            val t = (1f - normH) / 2f
            val outW = max(16, (normW * imgW).roundToInt())
            val outH = max(16, (normH * imgH).roundToInt())
            state.copy(
                cropAspectPreset = preset,
                cropLeftNorm = l,
                cropTopNorm = t,
                cropRightNorm = l + normW,
                cropBottomNorm = t + normH,
                resizeWidthText = outW.toString(),
                resizeHeightText = outH.toString()
            )
        }
    }

    fun rotateCropImage90() {
        _imageWorkspace.update { state ->
            val newRot = (state.rotationDegrees + 90) % 360
            state.copy(
                rotationDegrees = newRot,
                cropLeftNorm = 0f,
                cropTopNorm = 0f,
                cropRightNorm = 1f,
                cropBottomNorm = 1f,
                resizeWidthText = state.resizeHeightText,
                resizeHeightText = state.resizeWidthText
            )
        }
    }

    fun toggleCropFlipHorizontal() {
        _imageWorkspace.update { it.copy(flipHorizontal = !it.flipHorizontal) }
    }

    fun toggleCropLockAspectRatio() {
        _imageWorkspace.update { it.copy(lockAspectRatio = !it.lockAspectRatio) }
    }

    fun updateResizeWidthText(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(5)
        _imageWorkspace.update { state ->
            val newH = if (state.lockAspectRatio && digits.isNotBlank()) {
                val wInt = digits.toIntOrNull()
                val curRatio = currentCropPixelRatio(state)
                if (wInt != null && curRatio > 0f) {
                    max(1, (wInt / curRatio).roundToInt()).toString()
                } else state.resizeHeightText
            } else state.resizeHeightText
            state.copy(resizeWidthText = digits, resizeHeightText = newH)
        }
    }

    fun updateResizeHeightText(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(5)
        _imageWorkspace.update { state ->
            val newW = if (state.lockAspectRatio && digits.isNotBlank()) {
                val hInt = digits.toIntOrNull()
                val curRatio = currentCropPixelRatio(state)
                if (hInt != null && curRatio > 0f) {
                    max(1, (hInt * curRatio).roundToInt()).toString()
                } else state.resizeWidthText
            } else state.resizeWidthText
            state.copy(resizeHeightText = digits, resizeWidthText = newW)
        }
    }

    private fun currentCropPixelRatio(state: ImageWorkspaceUiState): Float {
        state.cropAspectPreset.ratio?.let { return it }
        val info = state.pickedImage ?: return 1f
        val swapped = ((state.rotationDegrees % 180) + 180) % 180 != 0
        val imgW = (if (swapped) info.height else info.width).toFloat()
        val imgH = (if (swapped) info.width else info.height).toFloat()
        val cropW = (state.cropRightNorm - state.cropLeftNorm).coerceAtLeast(0.05f) * imgW
        val cropH = (state.cropBottomNorm - state.cropTopNorm).coerceAtLeast(0.05f) * imgH
        return cropW / max(1f, cropH)
    }

    fun runCropRotateResize(onResultReady: () -> Unit) {
        val state = _imageWorkspace.value
        val info = state.pickedImage ?: return
        val wPx = state.resizeWidthText.toIntOrNull()?.coerceIn(16, 8192) ?: info.width
        val hPx = state.resizeHeightText.toIntOrNull()?.coerceIn(16, 8192) ?: info.height
        viewModelScope.launch {
            _imageWorkspace.update {
                it.copy(processing = ProcessingState(true, 0.12f, "Applying crop & resize..."))
            }
            try {
                val res = imageEngine.cropRotateResize(
                    info = info,
                    cropLeftNorm = state.cropLeftNorm,
                    cropTopNorm = state.cropTopNorm,
                    cropRightNorm = state.cropRightNorm,
                    cropBottomNorm = state.cropBottomNorm,
                    rotationDegrees = state.rotationDegrees,
                    flipHorizontal = state.flipHorizontal,
                    targetWidthPx = wPx,
                    targetHeightPx = hPx,
                    format = state.outputFormat
                ) { p, msg ->
                    _imageWorkspace.update { it.copy(processing = ProcessingState(true, p, msg)) }
                }
                _processedImageResult.value = res
                _imageWorkspace.update { it.copy(processing = ProcessingState()) }
                onResultReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _imageWorkspace.update {
                    it.copy(processing = ProcessingState(errorMessage = e.localizedMessage ?: "Crop & resize failed."))
                }
            }
        }
    }

    fun runOcrOnUri(uri: Uri) {
        viewModelScope.launch {
            _ocrState.value = OcrUiState.Scanning
            try {
                val info = _imageWorkspace.value.pickedImage
                    ?.takeIf { it.uriString == uri.toString() }
                    ?: imageEngine.importPickedImage(uri).also { imported ->
                        val prev = imageEngine.decodePreviewBitmap(imported.localPath, 1200)
                        _imageWorkspace.update { it.copy(pickedImage = imported, previewBitmap = prev) }
                    }
                val result = imageEngine.extractTextOcr(Uri.fromFile(File(info.localPath)))
                _ocrState.value = result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ocrState.value = OcrUiState.Error(e.localizedMessage ?: "Could not recognize text.")
            }
        }
    }

    fun saveOcrTextAsPdf(text: String, onSuccess: (Long) -> Unit) {
        val baseTitle = _imageWorkspace.value.pickedImage?.displayName?.substringBeforeLast('.')
            ?: "Paperly_OCR_Text"
        viewModelScope.launch {
            try {
                val record = pdfEngine.exportSummaryToPdf(baseTitle, text)
                val id = recordRepository.insert(record)
                onSuccess(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Could not export OCR text as PDF."
            }
        }
    }

    fun updateAiGenConfig(
        prompt: String? = null,
        style: AiImageStyle? = null,
        aspectRatio: AiImageAspectRatio? = null
    ) {
        _imageWorkspace.update { state ->
            state.copy(
                aiPrompt = prompt ?: state.aiPrompt,
                aiStyle = style ?: state.aiStyle,
                aiAspectRatio = aspectRatio ?: state.aiAspectRatio,
                processing = state.processing.copy(errorMessage = null)
            )
        }
    }

    fun generateAiImage(onResultReady: () -> Unit) {
        if (!isAppIntegrityTrusted) {
            _snackbarMessage.value = "AI Image Generator requires the official Google Play release."
            return
        }
        if (!isPro.value && aiImageCredits.value <= 0) {
            openImagePaywallInWorkspace(ImageToolType.TEXT_TO_IMAGE)
            return
        }
        val state = _imageWorkspace.value
        val cleanPrompt = state.aiPrompt.trim()
        if (cleanPrompt.isEmpty()) {
            _imageWorkspace.update {
                it.copy(processing = ProcessingState(errorMessage = "Describe the image you want to create."))
            }
            return
        }
        imageJob?.cancel()
        imageJob = viewModelScope.launch {
            _imageWorkspace.update {
                it.copy(processing = ProcessingState(true, 0.35f, "Generating image with AI..."))
            }
            try {
                val payload = aiImageRepository.generateImage(
                    prompt = cleanPrompt,
                    style = state.aiStyle,
                    aspectRatio = state.aiAspectRatio
                )
                _imageWorkspace.update {
                    it.copy(processing = ProcessingState(true, 0.85f, "Saving artwork..."))
                }
                val result = imageEngine.saveGeneratedAiBytes(
                    bytes = payload.bytes,
                    mimeType = payload.mimeType,
                    prompt = cleanPrompt
                )
                if (!isPro.value) {
                    prefsRepository.consumeAiImageCredit()
                }
                _processedImageResult.value = result
                _imageWorkspace.update { it.copy(processing = ProcessingState()) }
                onResultReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _imageWorkspace.update {
                    it.copy(
                        processing = ProcessingState(
                            errorMessage = e.localizedMessage ?: "AI image generation failed. Try again."
                        )
                    )
                }
            }
        }
    }

    fun updateUpscaleTarget(target: UpscaleTarget) {
        _imageWorkspace.update { it.copy(upscaleTarget = target) }
    }

    fun runImageUpscale(onResultReady: () -> Unit) {
        if (!isPro.value && upscaleCredits.value <= 0) {
            openImagePaywallInWorkspace(ImageToolType.UPSCALER)
            return
        }
        val state = _imageWorkspace.value
        val info = state.pickedImage ?: return
        imageJob?.cancel()
        imageJob = viewModelScope.launch {
            _imageWorkspace.update {
                it.copy(processing = ProcessingState(true, 0.10f, "Preparing HD upscaler..."))
            }
            try {
                val result = imageEngine.upscaleImage(info, state.upscaleTarget, state.outputFormat) { p, msg ->
                    _imageWorkspace.update { it.copy(processing = ProcessingState(true, p, msg)) }
                }
                if (!isPro.value) {
                    prefsRepository.consumeUpscaleCredit()
                }
                _processedImageResult.value = result
                _imageWorkspace.update { it.copy(processing = ProcessingState()) }
                onResultReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _imageWorkspace.update {
                    it.copy(processing = ProcessingState(errorMessage = e.localizedMessage ?: "Upscaling failed."))
                }
            }
        }
    }

    private suspend fun runBackgroundSegmentation(
        info: PickedImageInfo,
        updatePassportTile: Boolean = false
    ) {
        _imageWorkspace.update {
            it.copy(processing = ProcessingState(true, 0.25f, "Isolating subject with ML Kit..."))
        }
        try {
            val cutoutPath = imageEngine.removeBackground(info) { p, msg ->
                _imageWorkspace.update { it.copy(processing = ProcessingState(true, p, msg)) }
            }
            val cutoutPreview = imageEngine.decodePreviewBitmap(cutoutPath, 1100)
            _imageWorkspace.update {
                it.copy(
                    cutoutPngPath = cutoutPath,
                    cutoutPreviewBitmap = cutoutPreview,
                    processing = ProcessingState()
                )
            }
            if (updatePassportTile) {
                refreshPassportTilePreview()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _imageWorkspace.update {
                it.copy(
                    cutoutPngPath = null,
                    cutoutPreviewBitmap = null,
                    processing = ProcessingState(
                        errorMessage = e.localizedMessage ?: "Could not isolate subject automatically."
                    )
                )
            }
            if (updatePassportTile) {
                refreshPassportTilePreview()
            }
        }
    }

    fun updateBgColor(colorArgb: Int?) {
        _imageWorkspace.update { state ->
            val fmt = if (colorArgb == null) ImageOutputFormat.PNG else ImageOutputFormat.JPG
            state.copy(selectedBgColorArgb = colorArgb, outputFormat = fmt)
        }
    }

    fun exportBgRemovedImage(onResultReady: () -> Unit) {
        val state = _imageWorkspace.value
        val info = state.pickedImage ?: return
        val cutoutPath = state.cutoutPngPath ?: return
        viewModelScope.launch {
            _imageWorkspace.update {
                it.copy(processing = ProcessingState(true, 0.65f, "Exporting image..."))
            }
            try {
                val res = imageEngine.exportCutoutResult(
                    cutoutPngPath = cutoutPath,
                    originalInfo = info,
                    backgroundColorArgb = state.selectedBgColorArgb,
                    format = state.outputFormat
                )
                _processedImageResult.value = res
                _imageWorkspace.update { it.copy(processing = ProcessingState()) }
                onResultReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _imageWorkspace.update {
                    it.copy(processing = ProcessingState(errorMessage = e.localizedMessage ?: "Export failed."))
                }
            }
        }
    }

    fun updatePassportConfig(
        removeBg: Boolean? = null,
        bgColorArgb: Int? = null,
        copyCount: Int? = null,
        exportAsPdf: Boolean? = null
    ) {
        val prevRemoveBg = _imageWorkspace.value.passportRemoveBg
        _imageWorkspace.update { state ->
            state.copy(
                passportRemoveBg = removeBg ?: state.passportRemoveBg,
                passportBgColorArgb = bgColorArgb ?: state.passportBgColorArgb,
                passportCopyCount = copyCount ?: state.passportCopyCount,
                passportExportAsPdf = exportAsPdf ?: state.passportExportAsPdf,
                processing = state.processing.copy(errorMessage = null)
            )
        }
        viewModelScope.launch {
            val state = _imageWorkspace.value
            val info = state.pickedImage ?: return@launch
            if (state.passportRemoveBg && !prevRemoveBg && state.cutoutPngPath == null) {
                runBackgroundSegmentation(info, updatePassportTile = true)
            } else {
                refreshPassportTilePreview()
            }
        }
    }

    private suspend fun refreshPassportTilePreview() {
        val state = _imageWorkspace.value
        val info = state.pickedImage ?: return
        val srcPath = if (state.passportRemoveBg && state.cutoutPngPath != null) {
            state.cutoutPngPath
        } else {
            info.localPath
        }
        val tile = imageEngine.renderPassportTilePreview(
            sourceOrCutoutPath = srcPath,
            removeBgEnabled = state.passportRemoveBg,
            backgroundColorArgb = state.passportBgColorArgb
        )
        _imageWorkspace.update { it.copy(passportTilePreview = tile) }
    }

    fun generatePassportSheet(onResultReady: () -> Unit) {
        val state = _imageWorkspace.value
        val info = state.pickedImage ?: return
        viewModelScope.launch {
            _imageWorkspace.update {
                it.copy(processing = ProcessingState(true, 0.15f, "Building 35×45mm print sheet..."))
            }
            try {
                val srcPath = if (state.passportRemoveBg && state.cutoutPngPath != null) {
                    state.cutoutPngPath
                } else {
                    info.localPath
                }
                val (imgResult, pdfRecord) = imageEngine.generatePassportSheet(
                    sourceOrCutoutPath = srcPath,
                    originalInfo = info,
                    removeBgEnabled = state.passportRemoveBg,
                    backgroundColorArgb = state.passportBgColorArgb,
                    copyCount = state.passportCopyCount,
                    exportAsPdf = state.passportExportAsPdf,
                    pdfsDir = pdfEngine.pdfsDir
                ) { p, msg ->
                    _imageWorkspace.update { it.copy(processing = ProcessingState(true, p, msg)) }
                }
                val pdfId = pdfRecord?.let { recordRepository.insert(it) }
                _processedImageResult.value = imgResult.copy(secondaryPdfRecordId = pdfId)
                _imageWorkspace.update { it.copy(processing = ProcessingState()) }
                onResultReady()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _imageWorkspace.update {
                    it.copy(processing = ProcessingState(errorMessage = e.localizedMessage ?: "Could not generate sheet."))
                }
            }
        }
    }

    fun saveResultImageToGallery() {
        val res = _processedImageResult.value ?: return
        viewModelScope.launch {
            try {
                imageEngine.saveImageToGallery(res.filePath, res.displayName, res.format)
                _snackbarMessage.value = "Saved to Gallery (Pictures/Paperly)"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Could not save image to Gallery."
            }
        }
    }

    fun exportResultImageToPdf(onPdfReady: (Long) -> Unit) {
        val res = _processedImageResult.value ?: return
        val existingPdfId = res.secondaryPdfRecordId
        if (existingPdfId != null) {
            onPdfReady(existingPdfId)
            return
        }
        viewModelScope.launch {
            try {
                val pdfRecord = imageEngine.exportImageFileToPdf(
                    imageFilePath = res.filePath,
                    displayName = res.displayName,
                    pdfsDir = pdfEngine.pdfsDir
                )
                val id = recordRepository.insert(pdfRecord)
                _processedImageResult.value = res.copy(secondaryPdfRecordId = id)
                onPdfReady(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _snackbarMessage.value = e.localizedMessage ?: "Could not convert image to PDF."
            }
        }
    }

    fun deleteRecord(record: PdfRecordEntity) {
        viewModelScope.launch {
            recordRepository.deleteRecord(record)
            _snackbarMessage.value = "Removed ${record.displayName}"
        }
    }

    fun clearAllLocalFiles() {
        viewModelScope.launch {
            recordRepository.clearAll(pdfEngine.pdfsDir, pdfEngine.cacheDir)
            _snackbarMessage.value = "Cleared local Paperly files"
        }
    }

    override fun onCleared() {
        billingManager.destroy()
        super.onCleared()
    }
}
