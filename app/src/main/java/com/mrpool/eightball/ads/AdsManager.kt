package com.mrpool.eightball.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.mrpool.eightball.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The ads, and nothing about when to show them — that is [AdPolicy]'s job and is tested.
 *
 * Two rules run through all of it:
 *
 * **An ad that is not ready is not waited for.** Every show takes a callback and calls it
 * whether the ad ran, failed or was never loaded. A game that froze on a lobby button
 * because an advert had not arrived would be worse than a game with no adverts, and the
 * network this rides on is a phone's, which is to say sometimes not there at all.
 *
 * **The next ad is loaded as soon as the last one closes.** Loading takes seconds; asking
 * at the moment the player finishes a match would mean either a wait or a miss.
 */
class AdsManager(context: Context) {

    private val appContext = context.applicationContext

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null

    private var loadingInterstitial = false
    private var loadingRewarded = false
    private var released = false

    private val _rewardReady = MutableStateFlow(false)

    /** True when a rewarded ad is loaded and could be shown this moment. */
    val rewardReady: StateFlow<Boolean> = _rewardReady.asStateFlow()

    /** True when the build is using Google's test units rather than real ones. */
    val usingTestAds: Boolean get() = BuildConfig.ADMOB_TEST_IDS

    init {
        // Initialisation reaches the network, so it is handed off rather than waited on.
        runCatching { MobileAds.initialize(appContext) { } }
            .onFailure { Log.w(TAG, "could not start the ads SDK", it) }
        loadInterstitial()
        loadRewarded()
    }

    // ------------------------------------------------------------------ interstitial

    private fun loadInterstitial() {
        if (released || loadingInterstitial || interstitial != null) return
        loadingInterstitial = true
        runCatching {
            InterstitialAd.load(
                appContext,
                BuildConfig.ADMOB_INTERSTITIAL_UNIT,
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        loadingInterstitial = false
                        interstitial = if (released) null else ad
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loadingInterstitial = false
                        interstitial = null
                        Log.w(TAG, "no full screen ad: ${error.message}")
                    }
                }
            )
        }.onFailure {
            loadingInterstitial = false
            Log.w(TAG, "could not ask for a full screen ad", it)
        }
    }

    /**
     * Shows a full screen ad if one is ready, then calls [onDone].
     *
     * [onDone] always runs — after the ad closes, or at once when there is nothing to
     * show. Whatever the caller was going to do next must not depend on an advert.
     */
    fun showInterstitial(activity: Activity, onDone: () -> Unit) {
        val ad = interstitial
        if (released || ad == null) {
            loadInterstitial()
            onDone()
            return
        }
        interstitial = null
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadInterstitial()
                onDone()
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                Log.w(TAG, "full screen ad would not show: ${error.message}")
                loadInterstitial()
                onDone()
            }
        }
        runCatching { ad.show(activity) }.onFailure {
            Log.w(TAG, "could not show the full screen ad", it)
            loadInterstitial()
            onDone()
        }
    }

    // ---------------------------------------------------------------------- rewarded

    private fun loadRewarded() {
        if (released || loadingRewarded || rewarded != null) return
        loadingRewarded = true
        runCatching {
            RewardedAd.load(
                appContext,
                BuildConfig.ADMOB_REWARDED_UNIT,
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        loadingRewarded = false
                        rewarded = if (released) null else ad
                        _rewardReady.value = !released
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        loadingRewarded = false
                        rewarded = null
                        _rewardReady.value = false
                        Log.w(TAG, "no rewarded ad: ${error.message}")
                    }
                }
            )
        }.onFailure {
            loadingRewarded = false
            _rewardReady.value = false
            Log.w(TAG, "could not ask for a rewarded ad", it)
        }
    }

    /**
     * Shows a rewarded ad.
     *
     * [onEarned] runs only if the player actually watched enough of it to earn the reward,
     * which is the SDK's decision and not ours.
     *
     * [onFinished] always runs, earned or not, and is told whether an ad was actually put
     * on screen. That matters: "the player skipped it" and "there was nothing to show" look
     * identical from the outside and are owed different things, and a caller left to work
     * it out from whether an ad happened to be loaded a moment earlier would sometimes get
     * it wrong.
     */
    fun showRewarded(
        activity: Activity,
        onEarned: () -> Unit,
        onFinished: (shown: Boolean) -> Unit
    ) {
        val ad = rewarded
        if (released || ad == null) {
            loadRewarded()
            onFinished(false)
            return
        }
        rewarded = null
        _rewardReady.value = false

        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                loadRewarded()
                if (earned) onEarned()
                onFinished(true)
            }

            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                // Loaded but refused to play, so nothing was asked of the player.
                Log.w(TAG, "rewarded ad would not show: ${error.message}")
                loadRewarded()
                onFinished(false)
            }
        }
        runCatching {
            // The reward is granted here and paid when the ad closes, so a player who
            // watches it through is paid once and a player who backs out is not paid at all.
            ad.show(activity) { earned = true }
        }.onFailure {
            Log.w(TAG, "could not show the rewarded ad", it)
            loadRewarded()
            onFinished(false)
        }
    }

    fun release() {
        released = true
        interstitial = null
        rewarded = null
        _rewardReady.value = false
    }

    private companion object {
        const val TAG = "AdsManager"
    }
}
