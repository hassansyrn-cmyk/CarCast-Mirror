package com.carcast.mirror.monetization

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

@Composable
fun SafeIdleBanner(surface: AdSurface, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!MonetizationManager.canShowBanner(surface)) return
    val widthDp = context.resources.configuration.screenWidthDp.coerceAtLeast(320)
    val adView = remember(surface, widthDp) {
        AdView(context).apply {
            adUnitId = MonetizationManager.bannerAdUnitId
            setAdSize(AdSize.getLargeAnchoredAdaptiveBannerAdSize(context, widthDp))
            loadAd(AdRequest.Builder().build())
        }
    }
    DisposableEffect(adView) {
        onDispose { adView.destroy() }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Advertisement", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
            AndroidView(factory = { adView }, modifier = Modifier.fillMaxWidth())
        }
    }
}
