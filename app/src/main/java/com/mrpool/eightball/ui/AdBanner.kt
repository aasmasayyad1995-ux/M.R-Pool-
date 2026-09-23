package com.mrpool.eightball.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.mrpool.eightball.BuildConfig

/**
 * The strip along the bottom of the menus.
 *
 * Fifty density pixels of it, reserved whether an ad turns up or not. A banner that
 * appears late and shoves the buttons upward under a thumb already on its way down is how
 * a player taps something they did not mean to, and the first one they mistap is the ad.
 *
 * Never drawn over the table: [com.mrpool.eightball.ads.AdPolicy.showBanner] decides that,
 * and it is tested.
 */
@Composable
fun AdBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = remember {
        runCatching {
            AdView(context).apply {
                adUnitId = BuildConfig.ADMOB_BANNER_UNIT
                setAdSize(AdSize.BANNER)
                loadAd(AdRequest.Builder().build())
            }
        }.getOrNull()
    }

    DisposableEffect(view) {
        onDispose { runCatching { view?.destroy() } }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            // The height is the banner's, held open from the start so nothing moves later.
            .height(BANNER_HEIGHT)
            .background(Color(0x22000000)),
        contentAlignment = Alignment.Center
    ) {
        if (view != null) {
            AndroidView(factory = { view }, modifier = Modifier.padding(0.dp))
        }
    }
}

/** The height of a standard AdMob banner, reserved up front. */
private val BANNER_HEIGHT = 50.dp
