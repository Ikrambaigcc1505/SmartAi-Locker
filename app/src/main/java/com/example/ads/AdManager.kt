package com.example.ads

import android.app.Activity
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Centralized Google AdMob lifecycle manager.
 *
 * - Initializes MobileAds once on a background thread during Application.onCreate().
 * - Checks network connectivity before loading and retries cleanly on connectivity restoration or failure backoff.
 * - Manages Interstitial, Native Advanced, Rewarded, and App Open ad lifecycles safely.
 */
class AdManager private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val initStarted = AtomicBoolean(false)
    @Volatile
    private var isSdkInitialized = false

    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    private var appOpenAd: AppOpenAd? = null
    private val _nativeAd = MutableStateFlow<NativeAd?>(null)
    val nativeAd: StateFlow<NativeAd?> = _nativeAd.asStateFlow()

    private val _networkAvailableTick = MutableStateFlow(0)
    val networkAvailableTick: StateFlow<Int> = _networkAvailableTick.asStateFlow()

    @Volatile
    private var isShowingAd = false
    @Volatile
    private var isLoadingInterstitial = false
    @Volatile
    private var isLoadingNative = false
    @Volatile
    private var isLoadingRewarded = false
    @Volatile
    private var isLoadingAppOpen = false

    private var lastInterstitialShownMs = 0L
    private var lastAppOpenLoadMs = 0L

    private var interstitialRetryAttempt = 0
    private var nativeRetryAttempt = 0
    private var interstitialRetryJob: Job? = null
    private var nativeRetryJob: Job? = null

    private var networkCallbackRegistered = false
    private val connectivityManager: ConnectivityManager? =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available — resuming pending AdMob preloads")
            _networkAvailableTick.value = _networkAvailableTick.value + 1
            scope.launch {
                if (!isSdkInitialized) {
                    initializeInBackground()
                } else {
                    if (interstitialAd == null) loadInterstitial()
                    if (_nativeAd.value == null) loadNativeAd()
                    if (rewardedAd == null) loadRewarded()
                    if (appOpenAd == null) loadAppOpen()
                }
            }
        }
    }

    var consentReadyStatus: String = "AdMob UMP Consent SDK Ready (Offline-Safe)"
        private set

    fun initializeInBackground() {
        registerNetworkMonitor()
        if (!initStarted.compareAndSet(false, true)) return

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching {
                Log.d(TAG, "Initializing Google Mobile Ads SDK on background thread")
                MobileAds.initialize(appContext) { initializationStatus ->
                    isSdkInitialized = true
                    Log.d(
                        TAG,
                        "MobileAds initialized with ${initializationStatus.adapterStatusMap.size} adapters"
                    )
                    scope.launch {
                        preloadInitialAds()
                    }
                }
            }.onFailure { err ->
                initStarted.set(false)
                Log.w(TAG, "MobileAds background initialization failed: ${err.message}")
            }
        }
    }

    private fun registerNetworkMonitor() {
        if (networkCallbackRegistered) return
        runCatching {
            connectivityManager?.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }.onFailure { err ->
            Log.w(TAG, "NetworkCallback registration skipped: ${err.message}")
        }
    }

    fun isNetworkAvailable(): Boolean {
        val cm = connectivityManager ?: return false
        return runCatching {
            val activeNetwork = cm.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    private fun preloadInitialAds() {
        if (!isNetworkAvailable()) {
            Log.d(TAG, "Device offline at init — ad preloads deferred until network available")
            return
        }
        loadInterstitial()
        loadNativeAd()
        loadRewarded()
        loadAppOpen()
    }

    fun requestConsentInfo(activity: Activity?, onStatus: (String) -> Unit = {}) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onStatus(consentReadyStatus)
            return
        }
        if (!isNetworkAvailable()) {
            consentReadyStatus = "Offline mode — UMP consent check deferred"
            onStatus(consentReadyStatus)
            return
        }
        runCatching {
            val params = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build()
            val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
            consentInfo.requestConsentInfoUpdate(
                activity,
                params,
                {
                    if (consentInfo.isConsentFormAvailable) {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                            consentReadyStatus = if (formError == null && consentInfo.canRequestAds()) {
                                "Consent verified (UMP ready for ads)"
                            } else {
                                "UMP standby (${formError?.message ?: "No form required"})"
                            }
                            Log.d(TAG, "UMP status updated: $consentReadyStatus")
                            onStatus(consentReadyStatus)
                        }
                    } else {
                        consentReadyStatus = "Consent check passed (No EEA/regional form needed)"
                        Log.d(TAG, "UMP status updated: $consentReadyStatus")
                        onStatus(consentReadyStatus)
                    }
                },
                { requestError ->
                    consentReadyStatus = "Offline mode — UMP deferred (${requestError.message})"
                    Log.w(TAG, "UMP consent info update failed: ${requestError.message}")
                    onStatus(consentReadyStatus)
                }
            )
        }.onFailure { err ->
            consentReadyStatus = "UMP SDK configured (offline-safe standby)"
            Log.w(TAG, "UMP request skipped: ${err.message}")
            onStatus(consentReadyStatus)
        }
    }

    // -------------------------------------------------------------------------
    // Interstitial Ad Lifecycle
    // -------------------------------------------------------------------------

    fun loadInterstitial() {
        if (interstitialAd != null || isLoadingInterstitial) return
        if (!isSdkInitialized) {
            initializeInBackground()
            return
        }
        if (!isNetworkAvailable()) {
            Log.d(TAG, "Interstitial preload skipped — offline")
            return
        }

        isLoadingInterstitial = true
        val request = AdRequest.Builder().build()
        runCatching {
            InterstitialAd.load(
                appContext,
                AdConfig.INTERSTITIAL_AD_UNIT_ID,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isLoadingInterstitial = false
                        interstitialRetryAttempt = 0
                        interstitialAd = ad
                        Log.d(TAG, "Interstitial ad loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingInterstitial = false
                        interstitialAd = null
                        Log.w(
                            TAG,
                            "Interstitial ad failed to load: code=${error.code}, domain=${error.domain}, message=${error.message}"
                        )
                        scheduleInterstitialRetry()
                    }
                }
            )
        }.onFailure { err ->
            isLoadingInterstitial = false
            Log.w(TAG, "Interstitial load invocation failed: ${err.message}")
        }
    }

    private fun scheduleInterstitialRetry() {
        if (interstitialRetryAttempt >= MAX_RETRY_ATTEMPTS) return
        interstitialRetryJob?.cancel()
        val attempt = interstitialRetryAttempt++
        val delayMs = (5_000L shl attempt).coerceAtMost(45_000L)
        interstitialRetryJob = scope.launch {
            delay(delayMs)
            if (isNetworkAvailable() && interstitialAd == null) {
                loadInterstitial()
            }
        }
    }

    fun maybeShowInterstitial(activity: Activity?, onFinished: () -> Unit = {}) {
        val now = System.currentTimeMillis()
        val cooldownMs = 75_000L
        val ad = interstitialAd
        val cooldownElapsed = lastInterstitialShownMs == 0L || (now - lastInterstitialShownMs >= cooldownMs)
        if (activity == null || activity.isFinishing || activity.isDestroyed || ad == null || isShowingAd || !cooldownElapsed) {
            if (ad == null) loadInterstitial()
            onFinished()
            return
        }

        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Interstitial ad showed full screen content")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial ad dismissed full screen content")
                isShowingAd = false
                interstitialAd = null
                lastInterstitialShownMs = System.currentTimeMillis()
                loadInterstitial()
                onFinished()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(
                    TAG,
                    "Interstitial ad failed to show: code=${adError.code}, domain=${adError.domain}, message=${adError.message}"
                )
                isShowingAd = false
                interstitialAd = null
                loadInterstitial()
                onFinished()
            }
        }

        runCatching {
            ad.show(activity)
        }.onFailure { err ->
            Log.w(TAG, "Interstitial show failed: ${err.message}")
            isShowingAd = false
            interstitialAd = null
            loadInterstitial()
            onFinished()
        }
    }

    // -------------------------------------------------------------------------
    // Native Advanced Ad Lifecycle
    // -------------------------------------------------------------------------

    fun ensureNativeAdLoaded() {
        if (_nativeAd.value == null) {
            loadNativeAd()
        }
    }

    fun loadNativeAd() {
        if (isLoadingNative) return
        if (!isSdkInitialized) {
            initializeInBackground()
            return
        }
        if (!isNetworkAvailable()) {
            Log.d(TAG, "Native ad load skipped — offline")
            return
        }

        isLoadingNative = true
        runCatching {
            val adLoader = AdLoader.Builder(appContext, AdConfig.NATIVE_AD_UNIT_ID)
                .forNativeAd { loadedAd ->
                    isLoadingNative = false
                    nativeRetryAttempt = 0
                    val previous = _nativeAd.getAndUpdate { loadedAd }
                    if (previous != null && previous !== loadedAd) {
                        previous.destroy()
                    }
                    Log.d(TAG, "Native Advanced ad loaded and cached")
                }
                .withAdListener(object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d(TAG, "Native Advanced ad onAdLoaded callback")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingNative = false
                        Log.w(
                            TAG,
                            "Native Advanced ad failed to load: code=${error.code}, domain=${error.domain}, message=${error.message}"
                        )
                        scheduleNativeRetry()
                    }

                    override fun onAdImpression() {
                        Log.d(TAG, "Native Advanced ad impression recorded")
                    }

                    override fun onAdClicked() {
                        Log.d(TAG, "Native Advanced ad clicked")
                    }
                })
                .withNativeAdOptions(NativeAdOptions.Builder().build())
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
        }.onFailure { err ->
            isLoadingNative = false
            Log.w(TAG, "Native Advanced AdLoader error: ${err.message}")
        }
    }

    private fun scheduleNativeRetry() {
        if (nativeRetryAttempt >= MAX_RETRY_ATTEMPTS) return
        nativeRetryJob?.cancel()
        val attempt = nativeRetryAttempt++
        val delayMs = (5_000L shl attempt).coerceAtMost(45_000L)
        nativeRetryJob = scope.launch {
            delay(delayMs)
            if (isNetworkAvailable() && _nativeAd.value == null) {
                loadNativeAd()
            }
        }
    }

    fun releaseNativeAd(ad: NativeAd) {
        val current = _nativeAd.value
        if (current === ad) {
            _nativeAd.compareAndSet(ad, null)
        }
        runCatching { ad.destroy() }
        Log.d(TAG, "Native Advanced ad destroyed cleanly on dispose")
    }

    fun releaseCurrentNativeAd() {
        val current = _nativeAd.getAndUpdate { null } ?: return
        runCatching { current.destroy() }
        Log.d(TAG, "Native Advanced ad released and destroyed on screen dispose")
    }

    // -------------------------------------------------------------------------
    // Rewarded & App Open Lifecycles
    // -------------------------------------------------------------------------

    fun loadRewarded() {
        if (rewardedAd != null || isLoadingRewarded) return
        if (!isSdkInitialized || !isNetworkAvailable()) return

        isLoadingRewarded = true
        val request = AdRequest.Builder().build()
        runCatching {
            RewardedAd.load(
                appContext,
                AdConfig.REWARDED_AD_UNIT_ID,
                request,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        isLoadingRewarded = false
                        rewardedAd = ad
                        Log.d(TAG, "Rewarded ad loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingRewarded = false
                        rewardedAd = null
                        Log.w(
                            TAG,
                            "Rewarded ad failed to load: code=${error.code}, domain=${error.domain}, message=${error.message}"
                        )
                    }
                }
            )
        }.onFailure {
            isLoadingRewarded = false
        }
    }

    fun showRewarded(
        activity: Activity?,
        onRewardEarned: () -> Unit,
        onUnavailable: () -> Unit
    ) {
        val ad = rewardedAd
        if (activity == null || activity.isFinishing || activity.isDestroyed || ad == null || isShowingAd) {
            loadRewarded()
            onUnavailable()
            return
        }
        isShowingAd = true
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "Rewarded ad showed full screen content")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Rewarded ad dismissed full screen content (earned=$earned)")
                isShowingAd = false
                rewardedAd = null
                loadRewarded()
                if (earned) onRewardEarned()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(
                    TAG,
                    "Rewarded ad failed to show: code=${adError.code}, domain=${adError.domain}, message=${adError.message}"
                )
                isShowingAd = false
                rewardedAd = null
                loadRewarded()
                onUnavailable()
            }
        }
        runCatching {
            ad.show(activity) { earned = true }
        }.onFailure {
            isShowingAd = false
            rewardedAd = null
            onUnavailable()
        }
    }

    fun loadAppOpen() {
        val now = System.currentTimeMillis()
        if ((appOpenAd != null && now - lastAppOpenLoadMs < 3_600_000L) || isLoadingAppOpen) return
        if (!isSdkInitialized || !isNetworkAvailable()) return

        isLoadingAppOpen = true
        val request = AdRequest.Builder().build()
        runCatching {
            AppOpenAd.load(
                appContext,
                AdConfig.APP_OPEN_AD_UNIT_ID,
                request,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(ad: AppOpenAd) {
                        isLoadingAppOpen = false
                        appOpenAd = ad
                        lastAppOpenLoadMs = System.currentTimeMillis()
                        Log.d(TAG, "App Open ad loaded")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingAppOpen = false
                        appOpenAd = null
                        Log.w(
                            TAG,
                            "App Open ad failed to load: code=${error.code}, domain=${error.domain}, message=${error.message}"
                        )
                    }
                }
            )
        }.onFailure {
            isLoadingAppOpen = false
        }
    }

    fun showAppOpenIfAvailable(activity: Activity?) {
        val ad = appOpenAd
        if (activity == null || activity.isFinishing || activity.isDestroyed || ad == null || isShowingAd) {
            loadAppOpen()
            return
        }
        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App Open ad showed full screen content")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "App Open ad dismissed full screen content")
                isShowingAd = false
                appOpenAd = null
                loadAppOpen()
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.w(
                    TAG,
                    "App Open ad failed to show: code=${adError.code}, domain=${adError.domain}, message=${adError.message}"
                )
                isShowingAd = false
                appOpenAd = null
                loadAppOpen()
            }
        }
        runCatching {
            ad.show(activity)
        }.onFailure {
            isShowingAd = false
            appOpenAd = null
        }
    }

    fun destroyAll() {
        interstitialRetryJob?.cancel()
        nativeRetryJob?.cancel()
        _nativeAd.getAndUpdate { null }?.let { ad -> runCatching { ad.destroy() } }
        interstitialAd = null
        rewardedAd = null
        appOpenAd = null
        if (networkCallbackRegistered) {
            runCatching { connectivityManager?.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
    }

    companion object {
        const val TAG = "AdMobHelper"
        private const val MAX_RETRY_ATTEMPTS = 3

        @Volatile
        private var instance: AdManager? = null

        fun getInstance(context: Context): AdManager {
            return instance ?: synchronized(this) {
                instance ?: AdManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
