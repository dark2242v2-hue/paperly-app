package com.example.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.example.security.SecurityGuard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private const val TAG = "PaperlyBillingManager"

enum class ProPlanType(val productId: String) {
    MONTHLY(PaperlyBillingManager.PRODUCT_MONTHLY),
    YEARLY(PaperlyBillingManager.PRODUCT_YEARLY)
}

data class SubscriptionPlanUi(
    val planType: ProPlanType,
    val productId: String,
    val formattedPrice: String,
    val periodLabel: String,
    val priceAmountMicros: Long,
    val priceCurrencyCode: String = "INR",
    val hasFreeTrial: Boolean = false,
    val isLoadedFromPlay: Boolean = false,
    val productDetails: ProductDetails? = null,
    val offerToken: String? = null
)

data class BillingUiState(
    val isConnected: Boolean = false,
    val isLoadingProducts: Boolean = false,
    val isPurchasing: Boolean = false,
    val isRestoring: Boolean = false,
    val monthlyPlan: SubscriptionPlanUi = SubscriptionPlanUi(
        planType = ProPlanType.MONTHLY,
        productId = PaperlyBillingManager.PRODUCT_MONTHLY,
        formattedPrice = "₹79",
        periodLabel = "month",
        priceAmountMicros = 79_000_000L,
        priceCurrencyCode = "INR",
        hasFreeTrial = false
    ),
    val yearlyPlan: SubscriptionPlanUi = SubscriptionPlanUi(
        planType = ProPlanType.YEARLY,
        productId = PaperlyBillingManager.PRODUCT_YEARLY,
        formattedPrice = "₹399",
        periodLabel = "year",
        priceAmountMicros = 399_000_000L,
        priceCurrencyCode = "INR",
        hasFreeTrial = true
    ),
    val yearlyAnchorFormatted: String = "₹948",
    val yearlyMonthlyEquivalentFormatted: String = "₹33",
    val yearlySavingsPercent: Int = 58,
    val statusMessage: String? = null
)

class PaperlyBillingManager(
    context: Context,
    private val onEntitlementUpdated: suspend (isActive: Boolean, productId: String?) -> Unit,
    private val onUserMessage: (String) -> Unit
) {

    companion object {
        const val PRODUCT_MONTHLY = "paperly_pro_monthly"
        const val PRODUCT_YEARLY = "paperly_pro_yearly"
        private val SUBSCRIPTION_IDS = listOf(PRODUCT_MONTHLY, PRODUCT_YEARLY)
    }

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val connectionMutex = Mutex()

    private val _uiState = MutableStateFlow(BillingUiState())
    val uiState: StateFlow<BillingUiState> = _uiState.asStateFlow()

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                scope.launch {
                    val outcome = handlePurchasedList(purchases.orEmpty())
                    _uiState.update {
                        it.copy(
                            isPurchasing = false,
                            statusMessage = outcome.failureMessage
                        )
                    }
                    if (outcome.activeProductId != null) {
                        onEntitlementUpdated(true, outcome.activeProductId)
                        onUserMessage("Paperly Pro activated — Ad-Free workspace unlocked!")
                    } else if (outcome.hasPendingPurchase) {
                        onUserMessage("Purchase pending payment confirmation. Pro will unlock once completed.")
                    }
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                _uiState.update { it.copy(isPurchasing = false, statusMessage = null) }
            }
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                scope.launch {
                    _uiState.update { it.copy(isPurchasing = false, statusMessage = null) }
                    refreshActiveSubscriptions(userInitiatedRestore = true)
                }
            }
            else -> {
                Log.w(TAG, "Purchase update error: ${billingResult.responseCode} ${billingResult.debugMessage}")
                _uiState.update {
                    it.copy(
                        isPurchasing = false,
                        statusMessage = "Purchase could not be completed (${billingResult.debugMessage.ifBlank { "error ${billingResult.responseCode}" }})."
                    )
                }
            }
        }
    }

    private val billingClientDelegate = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        BillingClient.newBuilder(appContext)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .enablePrepaidPlans()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()
    }
    private val billingClient: BillingClient
        get() = billingClientDelegate.value

    fun initialize() {
        scope.launch {
            runCatching {
                if (ensureConnected()) {
                    queryProductDetails()
                    refreshActiveSubscriptions(userInitiatedRestore = false)
                }
            }.onFailure { error ->
                Log.w(TAG, "Billing initialize failed cleanly", error)
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    suspend fun refreshOnResume() {
        runCatching {
            if (ensureConnected()) {
                if (!_uiState.value.monthlyPlan.isLoadedFromPlay || !_uiState.value.yearlyPlan.isLoadedFromPlay) {
                    queryProductDetails()
                }
                refreshActiveSubscriptions(userInitiatedRestore = false)
            }
        }.onFailure { error ->
            Log.w(TAG, "Billing refreshOnResume failed cleanly", error)
        }
    }

    fun restorePurchases() {
        scope.launch {
            _uiState.update { it.copy(isRestoring = true, statusMessage = null) }
            if (!ensureConnected()) {
                _uiState.update {
                    it.copy(
                        isRestoring = false,
                        statusMessage = "Google Play Store is unavailable right now. Please check your connection."
                    )
                }
                onUserMessage("Google Play Billing is unavailable on this device.")
                return@launch
            }
            queryProductDetails()
            val restored = refreshActiveSubscriptions(userInitiatedRestore = true)
            _uiState.update { it.copy(isRestoring = false) }
            if (restored) {
                onUserMessage("Paperly Pro subscription restored!")
            } else {
                onUserMessage("No active Paperly Pro subscription found.")
            }
        }
    }

    fun launchSubscriptionPurchase(activity: Activity?, planType: ProPlanType) {
        if (activity == null) {
            _uiState.update { it.copy(statusMessage = "Unable to open Google Play checkout from this screen.") }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isPurchasing = true, statusMessage = null) }
            if (!ensureConnected()) {
                _uiState.update {
                    it.copy(
                        isPurchasing = false,
                        statusMessage = "Could not connect to Google Play Store. Please try again."
                    )
                }
                return@launch
            }

            if (!_uiState.value.monthlyPlan.isLoadedFromPlay && !_uiState.value.yearlyPlan.isLoadedFromPlay) {
                queryProductDetails()
            }

            val selectedPlan = when (planType) {
                ProPlanType.MONTHLY -> _uiState.value.monthlyPlan
                ProPlanType.YEARLY -> _uiState.value.yearlyPlan
            }

            val productDetails = selectedPlan.productDetails
            val offerToken = selectedPlan.offerToken
            if (productDetails == null || offerToken.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isPurchasing = false,
                        statusMessage = "Subscription offer not yet loaded from Google Play (${selectedPlan.productId})."
                    )
                }
                return@launch
            }

            val productDetailsParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(productDetails)
                .setOfferToken(offerToken)
                .build()

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParams))
                .build()

            val launchResult = runCatching {
                billingClient.launchBillingFlow(activity, billingFlowParams)
            }.getOrNull()
            if (launchResult == null || launchResult.responseCode != BillingClient.BillingResponseCode.OK) {
                val msg = launchResult?.debugMessage ?: "Play Billing unavailable"
                _uiState.update {
                    it.copy(
                        isPurchasing = false,
                        statusMessage = "Unable to start Google Play checkout ($msg)."
                    )
                }
            }
        }
    }

    fun destroy() {
        if (billingClientDelegate.isInitialized()) {
            runCatching { billingClient.endConnection() }
        }
        scope.cancel()
    }

    private suspend fun ensureConnected(): Boolean = connectionMutex.withLock {
        val client = runCatching { billingClient }.getOrElse { error ->
            Log.w(TAG, "BillingClient creation failed", error)
            return@withLock false
        }
        if (client.isReady) {
            _uiState.update { it.copy(isConnected = true) }
            return@withLock true
        }
        val connected = withTimeoutOrNull(8_000L) {
            suspendCancellableCoroutine { cont ->
                runCatching {
                    client.startConnection(object : BillingClientStateListener {
                        override fun onBillingSetupFinished(billingResult: BillingResult) {
                            val ready = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                            _uiState.update { it.copy(isConnected = ready) }
                            if (cont.isActive) cont.resume(ready)
                        }

                        override fun onBillingServiceDisconnected() {
                            _uiState.update { it.copy(isConnected = false) }
                            if (cont.isActive) cont.resume(false)
                        }
                    })
                }.onFailure { error ->
                    Log.w(TAG, "Billing startConnection failed cleanly", error)
                    _uiState.update { it.copy(isConnected = false) }
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false
        if (!connected) {
            _uiState.update { it.copy(isConnected = false) }
        }
        connected
    }

    private suspend fun queryProductDetails() {
        _uiState.update { it.copy(isLoadingProducts = true) }
        val productList = SUBSCRIPTION_IDS.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.SUBS)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(productList)
            .build()

        val result = withContext(Dispatchers.IO) {
            runCatching { billingClient.queryProductDetails(params) }.getOrNull()
        }

        if (result != null && result.billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            val detailsList = result.productDetailsList.orEmpty()
            val monthlyDetails = detailsList.firstOrNull { it.productId == PRODUCT_MONTHLY }
            val yearlyDetails = detailsList.firstOrNull { it.productId == PRODUCT_YEARLY }

            _uiState.update { current ->
                val updatedMonthly = parseMonthlyPlan(monthlyDetails, current.monthlyPlan)
                val updatedYearly = parseYearlyPlan(yearlyDetails, current.yearlyPlan)
                val anchors = computePricingAnchors(updatedMonthly, updatedYearly)
                current.copy(
                    isLoadingProducts = false,
                    monthlyPlan = updatedMonthly,
                    yearlyPlan = updatedYearly,
                    yearlyAnchorFormatted = anchors.anchorFormatted,
                    yearlyMonthlyEquivalentFormatted = anchors.monthlyEquivalentFormatted,
                    yearlySavingsPercent = anchors.savingsPercent
                )
            }
        } else {
            Log.w(TAG, "queryProductDetails failed: ${result?.billingResult?.debugMessage ?: "error"}")
            _uiState.update { it.copy(isLoadingProducts = false) }
        }
    }

    private fun parseMonthlyPlan(
        details: ProductDetails?,
        fallback: SubscriptionPlanUi
    ): SubscriptionPlanUi {
        if (details == null) return fallback
        val offer = details.subscriptionOfferDetails?.firstOrNull() ?: return fallback
        val paidPhase = offer.pricingPhases.pricingPhaseList.lastOrNull { it.priceAmountMicros > 0L }
            ?: offer.pricingPhases.pricingPhaseList.firstOrNull()
        return fallback.copy(
            formattedPrice = paidPhase?.formattedPrice ?: fallback.formattedPrice,
            priceAmountMicros = paidPhase?.priceAmountMicros ?: fallback.priceAmountMicros,
            priceCurrencyCode = paidPhase?.priceCurrencyCode ?: fallback.priceCurrencyCode,
            isLoadedFromPlay = true,
            productDetails = details,
            offerToken = offer.offerToken
        )
    }

    private fun parseYearlyPlan(
        details: ProductDetails?,
        fallback: SubscriptionPlanUi
    ): SubscriptionPlanUi {
        if (details == null) return fallback
        val offers = details.subscriptionOfferDetails.orEmpty()
        if (offers.isEmpty()) return fallback

        // Detect an eligible free trial offer phase (priceAmountMicros == 0L)
        val freeTrialOffer = offers.firstOrNull { offer ->
            offer.pricingPhases.pricingPhaseList.any { phase -> phase.priceAmountMicros == 0L }
        }
        val chosenOffer = freeTrialOffer ?: offers.first()
        val hasTrial = freeTrialOffer != null
        val recurringPaidPhase = chosenOffer.pricingPhases.pricingPhaseList.lastOrNull {
            it.priceAmountMicros > 0L
        } ?: chosenOffer.pricingPhases.pricingPhaseList.lastOrNull()

        return fallback.copy(
            formattedPrice = recurringPaidPhase?.formattedPrice ?: fallback.formattedPrice,
            priceAmountMicros = recurringPaidPhase?.priceAmountMicros ?: fallback.priceAmountMicros,
            priceCurrencyCode = recurringPaidPhase?.priceCurrencyCode ?: fallback.priceCurrencyCode,
            hasFreeTrial = hasTrial,
            isLoadedFromPlay = true,
            productDetails = details,
            offerToken = chosenOffer.offerToken
        )
    }

    private data class PricingAnchors(
        val anchorFormatted: String,
        val monthlyEquivalentFormatted: String,
        val savingsPercent: Int
    )

    private fun computePricingAnchors(
        monthly: SubscriptionPlanUi,
        yearly: SubscriptionPlanUi
    ): PricingAnchors {
        val anchorMicros = monthly.priceAmountMicros * 12L
        val yearlyMicros = yearly.priceAmountMicros
        if (anchorMicros <= 0L || yearlyMicros <= 0L) {
            return PricingAnchors("₹948", "₹33", 58)
        }

        val anchorFormatted = formatMicrosAsCurrency(anchorMicros, monthly.priceCurrencyCode)
        val rawMonthlyEqMicros = yearlyMicros / 12.0
        val wholeYearly = (yearlyMicros % 1_000_000L == 0L) && yearlyMicros >= 10_000_000L
        val monthlyEqMicros = if (wholeYearly) {
            (rawMonthlyEqMicros / 1_000_000.0).roundToInt().coerceAtLeast(1) * 1_000_000L
        } else {
            rawMonthlyEqMicros.roundToInt().toLong()
        }
        val monthlyEqFormatted = formatMicrosAsCurrency(monthlyEqMicros, yearly.priceCurrencyCode)
        val savings = if (anchorMicros > yearlyMicros) {
            (((anchorMicros - yearlyMicros).toDouble() / anchorMicros.toDouble()) * 100.0)
                .roundToInt()
                .coerceIn(1, 95)
        } else {
            58
        }
        return PricingAnchors(anchorFormatted, monthlyEqFormatted, savings)
    }

    private fun formatMicrosAsCurrency(micros: Long, currencyCode: String): String {
        return runCatching {
            val amount = micros / 1_000_000.0
            val formatter = NumberFormat.getCurrencyInstance(Locale.getDefault())
            formatter.currency = Currency.getInstance(currencyCode)
            if (micros % 1_000_000L == 0L) {
                formatter.maximumFractionDigits = 0
                formatter.minimumFractionDigits = 0
            } else {
                formatter.maximumFractionDigits = 2
                formatter.minimumFractionDigits = 2
            }
            formatter.format(amount)
        }.getOrElse {
            val whole = (micros / 1_000_000L)
            "₹$whole"
        }
    }

    private data class PurchaseVerificationOutcome(
        val activeProductId: String? = null,
        val hasPendingPurchase: Boolean = false,
        val failureMessage: String? = null
    )

    private suspend fun refreshActiveSubscriptions(userInitiatedRestore: Boolean): Boolean {
        val trusted = withContext(Dispatchers.IO) { SecurityGuard.isAppTrusted(appContext) }
        if (!trusted) {
            onEntitlementUpdated(false, null)
            return false
        }

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()

        val result = withContext(Dispatchers.IO) {
            runCatching { billingClient.queryPurchasesAsync(params) }.getOrNull()
        }

        if (result == null || result.billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
            Log.w(TAG, "queryPurchasesAsync failed: ${result?.billingResult?.debugMessage ?: "error"}")
            return false
        }

        val outcome = handlePurchasedList(result.purchasesList)
        val hasActiveSub = outcome.activeProductId != null
        onEntitlementUpdated(hasActiveSub, outcome.activeProductId)

        if (userInitiatedRestore && outcome.failureMessage != null) {
            _uiState.update { it.copy(statusMessage = outcome.failureMessage) }
        } else if (userInitiatedRestore && !hasActiveSub) {
            Log.i(TAG, "No active verified & acknowledged subscription found during restore.")
        }
        return hasActiveSub
    }

    private suspend fun handlePurchasedList(purchases: List<Purchase>): PurchaseVerificationOutcome {
        val trusted = withContext(Dispatchers.IO) { SecurityGuard.isAppTrusted(appContext) }
        if (!trusted) {
            return PurchaseVerificationOutcome(
                activeProductId = null,
                failureMessage = "Pro verification requires the official Google Play release."
            )
        }

        var activeProductId: String? = null
        var hasPending = false
        var lastError: String? = null

        for (purchase in purchases) {
            val matchingId = purchase.products.firstOrNull { it in SUBSCRIPTION_IDS } ?: continue

            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    val acknowledged = if (purchase.isAcknowledged) {
                        true
                    } else {
                        val ackParams = AcknowledgePurchaseParams.newBuilder()
                            .setPurchaseToken(purchase.purchaseToken)
                            .build()
                        val ackResult = withContext(Dispatchers.IO) {
                            runCatching { billingClient.acknowledgePurchase(ackParams) }.getOrNull()
                        }
                        if (ackResult != null && ackResult.responseCode == BillingClient.BillingResponseCode.OK) {
                            true
                        } else {
                            Log.w(TAG, "Failed to acknowledge purchase: ${ackResult?.debugMessage ?: "error"}")
                            lastError = "Subscription could not be acknowledged yet. Please tap Restore Purchases."
                            false
                        }
                    }

                    // Strict Play Billing check: unlock ONLY when PURCHASED AND acknowledged
                    if (acknowledged) {
                        activeProductId = matchingId
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    hasPending = true
                }
                else -> {
                    // UNSPECIFIED_STATE or invalid purchase state — do not unlock
                }
            }
        }

        return PurchaseVerificationOutcome(
            activeProductId = activeProductId,
            hasPendingPurchase = hasPending,
            failureMessage = if (activeProductId == null) lastError else null
        )
    }
}
