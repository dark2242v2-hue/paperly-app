package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.example.BuildConfig
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "PaperlyAdManager"

object AdUnitIds {
    // Official Google Mobile Ads test IDs are used automatically in BuildConfig.DEBUG builds
    // so developer device testing never triggers invalid-traffic policy violations.
    private const val TEST_ADAPTIVE_BANNER = "ca-app-pub-3940256099942544/9214589741"
    private const val TEST_NATIVE_ADVANCED = "ca-app-pub-3940256099942544/2247696110"
    private const val TEST_REWARDED_VIDEO = "ca-app-pub-3940256099942544/5224354917"

    // Live production AdMob Ad Unit IDs for release builds
    private const val LIVE_ADAPTIVE_BANNER = "ca-app-pub-9081683266284660/3778743614"
    private const val LIVE_NATIVE_ADVANCED = "ca-app-pub-9081683266284660/8273230235"
    private const val LIVE_REWARDED_VIDEO = "ca-app-pub-9081683266284660/1775396805"

    val bannerAdUnitId: String
        get() = if (BuildConfig.DEBUG) TEST_ADAPTIVE_BANNER else LIVE_ADAPTIVE_BANNER

    val nativeAdUnitId: String
        get() = if (BuildConfig.DEBUG) TEST_NATIVE_ADVANCED else LIVE_NATIVE_ADVANCED

    val rewardedAdUnitId: String
        get() = if (BuildConfig.DEBUG) TEST_REWARDED_VIDEO else LIVE_REWARDED_VIDEO
}

class PaperlyAdManager(private val appContext: Context) {

    @Volatile
    private var isInitialized = false

    private var preloadedRewardedAd: RewardedAd? = null
    private var isLoadingRewarded = false

    private val _isRewardedReady = MutableStateFlow(false)
    val isRewardedReady: StateFlow<Boolean> = _isRewardedReady.asStateFlow()

    fun initialize(preloadRewarded: Boolean = true) {
        if (!isInitialized) {
            if (BuildConfig.DEBUG) {
                val testConfig = RequestConfiguration.Builder()
                    .setTestDeviceIds(listOf(AdRequest.DEVICE_ID_EMULATOR))
                    .build()
                MobileAds.setRequestConfiguration(testConfig)
            }
            MobileAds.initialize(appContext) {
                Log.d(TAG, "Google Mobile Ads SDK initialized (debug=${BuildConfig.DEBUG})")
                if (preloadRewarded) {
                    preloadRewardedAd()
                }
            }
            isInitialized = true
        } else if (preloadRewarded && preloadedRewardedAd == null) {
            preloadRewardedAd()
        }
    }

    fun preloadRewardedAd(onLoaded: ((Boolean) -> Unit)? = null) {
        if (preloadedRewardedAd != null) {
            _isRewardedReady.value = true
            onLoaded?.invoke(true)
            return
        }
        if (isLoadingRewarded) return
        isLoadingRewarded = true

        val request = AdRequest.Builder().build()
        RewardedAd.load(
            appContext,
            AdUnitIds.rewardedAdUnitId,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    Log.d(TAG, "RewardedAd preloaded")
                    preloadedRewardedAd = ad
                    isLoadingRewarded = false
                    _isRewardedReady.value = true
                    onLoaded?.invoke(true)
                }

                override fun onAdFailedToLoad(loadError: LoadAdError) {
                    Log.w(TAG, "RewardedAd failed to load: ${loadError.message}")
                    preloadedRewardedAd = null
                    isLoadingRewarded = false
                    _isRewardedReady.value = false
                    onLoaded?.invoke(false)
                }
            }
        )
    }

    fun showRewardedAd(
        activity: Activity,
        onUserEarnedReward: () -> Unit,
        onDismissedWithoutReward: () -> Unit,
        onLoadFailed: (String) -> Unit
    ) {
        val currentAd = preloadedRewardedAd
        if (currentAd != null) {
            presentRewardedAd(
                activity = activity,
                ad = currentAd,
                onUserEarnedReward = onUserEarnedReward,
                onDismissedWithoutReward = onDismissedWithoutReward,
                onShowFailed = onLoadFailed
            )
            return
        }

        // Load on demand if not yet preloaded
        val request = AdRequest.Builder().build()
        RewardedAd.load(
            appContext,
            AdUnitIds.rewardedAdUnitId,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    preloadedRewardedAd = ad
                    _isRewardedReady.value = true
                    presentRewardedAd(
                        activity = activity,
                        ad = ad,
                        onUserEarnedReward = onUserEarnedReward,
                        onDismissedWithoutReward = onDismissedWithoutReward,
                        onShowFailed = onLoadFailed
                    )
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "On-demand RewardedAd failed: ${error.message}")
                    _isRewardedReady.value = false
                    onLoadFailed("Video ad unavailable right now. Please try again or skip.")
                }
            }
        )
    }

    private fun presentRewardedAd(
        activity: Activity,
        ad: RewardedAd,
        onUserEarnedReward: () -> Unit,
        onDismissedWithoutReward: () -> Unit,
        onShowFailed: (String) -> Unit
    ) {
        var rewardEarned = false
        preloadedRewardedAd = null
        _isRewardedReady.value = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                preloadRewardedAd()
                if (!rewardEarned) {
                    onDismissedWithoutReward()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(TAG, "RewardedAd failed to show: ${adError.message}")
                preloadRewardedAd()
                onShowFailed("Could not display video ad. Please try again.")
            }
        }

        ad.show(activity) { _ ->
            rewardEarned = true
            onUserEarnedReward()
        }
    }

    fun loadNativeAd(
        onAdLoaded: (NativeAd) -> Unit,
        onAdFailed: (String) -> Unit
    ) {
        val loader = AdLoader.Builder(appContext, AdUnitIds.nativeAdUnitId)
            .forNativeAd { nativeAd ->
                onAdLoaded(nativeAd)
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "NativeAd failed to load: ${error.message}")
                    onAdFailed(error.message)
                }
            })
            .withNativeAdOptions(
                NativeAdOptions.Builder()
                    .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                    .build()
            )
            .build()

        loader.loadAd(AdRequest.Builder().build())
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
