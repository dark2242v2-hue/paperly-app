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
import com.example.data.ai.AiSummarizeRepository
import com.example.data.local.PaperlyDatabase
import com.example.data.local.PdfRecordEntity
import com.example.data.local.PdfRecordRepository
import com.example.data.local.UserPreferencesRepository
import com.example.data.model.AiSummaryUiState
import com.example.data.model.CompressionLevel
import com.example.data.model.PdfToolType
import com.example.data.model.PickedPdfInfo
import com.example.data.model.SignaturePlacement
import com.example.data.pdf.PdfEngine
import com.example.security.SecurityGuard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    val scanImageUris: List<Uri> = emptyList(),
    val scanEnhanceContrast: Boolean = true,
    val signTargetPage: Int = 1,
    val signPlacement: SignaturePlacement = SignaturePlacement.BOTTOM_RIGHT,
    val outputFileName: String = "",
    val previewThumbnail: Bitmap? = null,
    val processing: ProcessingState = ProcessingState()
)

data class ProSheetUiState(
    val visible: Boolean = false,
    val triggeredByTool: PdfToolType? = null,
    val isLoadingRewardedAd: Boolean = false,
    val rewardedError: String? = null
)

class PaperlyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PaperlyDatabase.getInstance(application)
    private val recordRepository = PdfRecordRepository(database.pdfRecordDao())
    private val prefsRepository = UserPreferencesRepository(application)
    private val aiRepository = AiSummarizeRepository()
    val pdfEngine = PdfEngine(application)
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

    init {
        adManager.initialize(preloadRewarded = true)
        if (isAppIntegrityTrusted) {
            billingManager.initialize()
        } else {
            viewModelScope.launch {
                prefsRepository.setPlayProActive(false, null)
            }
        }
    }

    val recentRecords: StateFlow<List<PdfRecordEntity>> = recordRepository.recentRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    val isRewardUnlockedForSession: StateFlow<Boolean> = combine(
        isPro,
        prefsRepository.aiUnlockedUntilMillis
    ) { pro, unlockedUntil ->
        pro || unlockedUntil > System.currentTimeMillis()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _workspace = MutableStateFlow(WorkspaceUiState())
    val workspace: StateFlow<WorkspaceUiState> = _workspace.asStateFlow()

    private val _aiSummaryState = MutableStateFlow<AiSummaryUiState>(AiSummaryUiState.Idle)
    val aiSummaryState: StateFlow<AiSummaryUiState> = _aiSummaryState.asStateFlow()

    private val _proSheetState = MutableStateFlow(ProSheetUiState())
    val proSheetState: StateFlow<ProSheetUiState> = _proSheetState.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    private var aiJob: Job? = null

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

    fun prepareToolWorkspace(tool: PdfToolType) {
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
                PdfToolType.AI_SUMMARIZE -> ""
            }
        )
    }

    fun openRewardedGateOrProceed(tool: PdfToolType, onNavigateToWorkspace: () -> Unit) {
        if (tool == PdfToolType.AI_SUMMARIZE && !isAppIntegrityTrusted) {
            _snackbarMessage.value = "AI Summarize requires the official Google Play release of Paperly."
            return
        }
        if (isRewardUnlockedForSession.value) {
            prepareToolWorkspace(tool)
            onNavigateToWorkspace()
        } else {
            // Trigger 1 (High-Intent Gate): Show the Pro paywall with 'Start 3-Day Free Trial' as primary
            // and 'Watch a short video to unlock this session free' as a secondary text button below.
            adManager.preloadRewardedAd()
            billingManager.clearStatusMessage()
            _proSheetState.value = ProSheetUiState(
                visible = true,
                triggeredByTool = tool
            )
        }
    }

    fun watchRewardedVideoFromProSheet(
        activity: Activity?,
        onUnlocked: (PdfToolType) -> Unit
    ) {
        val tool = _proSheetState.value.triggeredByTool ?: return
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
                    prefsRepository.grantAiSessionUnlock()
                    _proSheetState.value = ProSheetUiState(visible = false)
                    prepareToolWorkspace(tool)
                    onUnlocked(tool)
                }
            },
            onDismissedWithoutReward = {
                _proSheetState.update {
                    it.copy(
                        isLoadingRewardedAd = false,
                        rewardedError = "Finish watching the short video to unlock this session free."
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
        _workspace.update { it.copy(compressionLevel = level) }
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

    fun removeScanImageAt(index: Int) {
        _workspace.update { state ->
            val mutable = state.scanImageUris.toMutableList()
            if (index in mutable.indices) mutable.removeAt(index)
            state.copy(scanImageUris = mutable)
        }
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
        _workspace.update { it.copy(signPlacement = placement) }
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
                        pdfEngine.compressPdf(file, state.compressionLevel, state.outputFileName) { p, msg ->
                            _workspace.update { it.copy(processing = ProcessingState(true, p, msg)) }
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
                            placement = state.signPlacement,
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
                    state.activeTool == PdfToolType.COMPRESS
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
                _aiSummaryState.value = AiSummaryUiState.Success(
                    text = summary,
                    fileName = payload.displayName,
                    pageCount = payload.pageCount
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "AI summary timed out", e)
                _aiSummaryState.value = AiSummaryUiState.Error(
                    "Summarization timed out after 90 seconds. Please check your connection and try again."
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

    fun saveRecordToUri(filePath: String, destinationUri: Uri) {
        viewModelScope.launch {
            try {
                pdfEngine.saveToUserUri(filePath, destinationUri)
                _snackbarMessage.value = "Saved to device successfully"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Download save failed", e)
                _snackbarMessage.value = e.localizedMessage ?: "Could not save PDF"
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
