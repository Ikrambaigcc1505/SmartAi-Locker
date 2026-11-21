package com.example.ads

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.LocalVaultColors
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Cleanly renders an adaptive AdMob Banner Ad only when loaded, collapsing smoothly otherwise.
 * Uses a non-zero adaptive container and destroys the AdView on dispose.
 */
@Composable
fun BannerAdSlot(
    adManager: AdManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val networkTick by adManager.networkAvailableTick.collectAsStateWithLifecycle()

    BoxWithConstraints(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val density = context.resources.displayMetrics.density
        val widthDp = maxWidth.value.roundToInt().coerceAtLeast(320)

        val adSize = remember(widthDp) {
            runCatching {
                AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, widthDp)
            }.getOrElse { AdSize.BANNER }
        }
        val bannerHeightDp = remember(adSize, density) {
            val px = runCatching { adSize.getHeightInPixels(context) }.getOrElse { (50 * density).roundToInt() }
            (px / density).dp.coerceAtLeast(50.dp)
        }

        var adLoaded by remember(adSize) { mutableStateOf(false) }
        var retryAttempt by remember(adSize) { mutableIntStateOf(0) }

        val adView = remember(adSize) {
            AdView(context).apply {
                setAdSize(adSize)
                adUnitId = AdConfig.BANNER_AD_UNIT_ID
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    adSize.getHeightInPixels(context).coerceAtLeast((50 * density).roundToInt())
                )
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        adLoaded = true
                        retryAttempt = 0
                        Log.d(AdManager.TAG, "Banner ad loaded (widthDp=$widthDp)")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        adLoaded = false
                        Log.w(
                            AdManager.TAG,
                            "Banner ad failed to load: code=${error.code}, domain=${error.domain}, message=${error.message}"
                        )
                        if (retryAttempt < 3) {
                            retryAttempt += 1
                        }
                    }

                    override fun onAdOpened() {
                        Log.d(AdManager.TAG, "Banner ad opened")
                    }

                    override fun onAdClosed() {
                        Log.d(AdManager.TAG, "Banner ad closed")
                    }
                }
            }
        }

        // Initial load & automatic retry on network restoration or backoff
        LaunchedEffect(adView, networkTick, retryAttempt) {
            if (!adLoaded && adManager.isNetworkAvailable()) {
                if (retryAttempt > 0) {
                    val backoffMs = (5_000L shl (retryAttempt - 1)).coerceAtMost(30_000L)
                    delay(backoffMs)
                }
                if (!adLoaded && adManager.isNetworkAvailable()) {
                    runCatching {
                        adView.loadAd(AdRequest.Builder().build())
                    }.onFailure { err ->
                        Log.w(AdManager.TAG, "Banner loadAd exception: ${err.message}")
                    }
                }
            }
        }

        // Forward lifecycle resume/pause and always destroy AdView on dispose
        DisposableEffect(adView, lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> runCatching { adView.resume() }
                    Lifecycle.Event.ON_PAUSE -> runCatching { adView.pause() }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                runCatching { adView.destroy() }
                Log.d(AdManager.TAG, "Banner AdView destroyed cleanly on dispose")
            }
        }

        AnimatedVisibility(visible = adLoaded, modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bannerHeightDp),
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = {
                        (adView.parent as? ViewGroup)?.removeView(adView)
                        adView
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bannerHeightDp)
                )
            }
        }
    }
}

/**
 * Cleanly renders an AdMob Native Advanced Ad inside a NativeAdView card matching Material 3.
 * Collapses completely when offline or unloaded and cleans up NativeAdView on release.
 */
@Composable
fun NativeAdSlot(
    adManager: AdManager,
    modifier: Modifier = Modifier
) {
    val nativeAd by adManager.nativeAd.collectAsStateWithLifecycle()
    val vaultColors = LocalVaultColors.current
    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(Unit) {
        adManager.ensureNativeAdLoaded()
    }

    val currentAd = nativeAd
    if (currentAd != null) {
        val primaryArgb = colorScheme.primary.toArgb()
        val onPrimaryArgb = colorScheme.onPrimary.toArgb()
        val onSurfaceArgb = colorScheme.onSurface.toArgb()
        val onSurfaceVariantArgb = colorScheme.onSurfaceVariant.toArgb()
        val badgeBgArgb = colorScheme.primaryContainer.toArgb()
        val badgeTextArgb = colorScheme.onPrimaryContainer.toArgb()

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = vaultColors.glassSurface,
            border = BorderStroke(1.dp, vaultColors.glassBorder),
            modifier = modifier.fillMaxWidth()
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                factory = { ctx -> createNativeAdView(ctx) },
                update = { nativeAdView ->
                    bindNativeAdView(
                        nativeAdView = nativeAdView,
                        nativeAd = currentAd,
                        primaryArgb = primaryArgb,
                        onPrimaryArgb = onPrimaryArgb,
                        onSurfaceArgb = onSurfaceArgb,
                        onSurfaceVariantArgb = onSurfaceVariantArgb,
                        badgeBgArgb = badgeBgArgb,
                        badgeTextArgb = badgeTextArgb
                    )
                },
                onRelease = { nativeAdView ->
                    runCatching { nativeAdView.destroy() }
                }
            )
        }
    }
}

private data class NativeAdViewHolder(
    val iconView: ImageView,
    val badgeView: TextView,
    val headlineView: TextView,
    val advertiserView: TextView,
    val mediaView: MediaView,
    val bodyView: TextView,
    val callToActionView: Button
)

private fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).roundToInt()
}

private fun createNativeAdView(context: Context): NativeAdView {
    val root = NativeAdView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Header Row: Icon + Title/Advertiser + Ad Badge
    val headerRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val iconView = ImageView(context).apply {
        val size = dpToPx(context, 44)
        layoutParams = LinearLayout.LayoutParams(size, size).apply {
            marginEnd = dpToPx(context, 12)
        }
        scaleType = ImageView.ScaleType.CENTER_CROP
    }

    val titleColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
    }

    val headlineView = TextView(context).apply {
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
    }

    val advertiserView = TextView(context).apply {
        textSize = 12f
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    titleColumn.addView(headlineView)
    titleColumn.addView(advertiserView)

    val badgeView = TextView(context).apply {
        text = "Ad"
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dpToPx(context, 8), dpToPx(context, 3), dpToPx(context, 8), dpToPx(context, 3))
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = dpToPx(context, 8)
        }
    }

    headerRow.addView(iconView)
    headerRow.addView(titleColumn)
    headerRow.addView(badgeView)
    container.addView(headerRow)

    val mediaView = MediaView(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(context, 150)
        ).apply {
            topMargin = dpToPx(context, 10)
        }
    }
    container.addView(mediaView)

    val bodyView = TextView(context).apply {
        textSize = 13f
        maxLines = 3
        ellipsize = TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dpToPx(context, 8)
        }
    }
    container.addView(bodyView)

    val ctaView = Button(context).apply {
        isAllCaps = false
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        minHeight = dpToPx(context, 48)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = dpToPx(context, 10)
        }
    }
    container.addView(ctaView)

    root.addView(container)

    root.iconView = iconView
    root.headlineView = headlineView
    root.advertiserView = advertiserView
    root.mediaView = mediaView
    root.bodyView = bodyView
    root.callToActionView = ctaView

    root.tag = NativeAdViewHolder(
        iconView = iconView,
        badgeView = badgeView,
        headlineView = headlineView,
        advertiserView = advertiserView,
        mediaView = mediaView,
        bodyView = bodyView,
        callToActionView = ctaView
    )
    return root
}

private fun bindNativeAdView(
    nativeAdView: NativeAdView,
    nativeAd: NativeAd,
    primaryArgb: Int,
    onPrimaryArgb: Int,
    onSurfaceArgb: Int,
    onSurfaceVariantArgb: Int,
    badgeBgArgb: Int,
    badgeTextArgb: Int
) {
    val holder = nativeAdView.tag as? NativeAdViewHolder ?: return
    val density = nativeAdView.context.resources.displayMetrics.density

    holder.headlineView.setTextColor(onSurfaceArgb)
    holder.headlineView.text = nativeAd.headline.orEmpty()

    val advertiserText = nativeAd.advertiser
    if (advertiserText.isNullOrBlank()) {
        holder.advertiserView.visibility = View.GONE
    } else {
        holder.advertiserView.visibility = View.VISIBLE
        holder.advertiserView.setTextColor(onSurfaceVariantArgb)
        holder.advertiserView.text = advertiserText
    }

    holder.badgeView.setTextColor(badgeTextArgb)
    holder.badgeView.background = GradientDrawable().apply {
        setColor(badgeBgArgb)
        cornerRadius = 8f * density
    }

    val iconDrawable = nativeAd.icon?.drawable
    if (iconDrawable != null) {
        holder.iconView.visibility = View.VISIBLE
        holder.iconView.setImageDrawable(iconDrawable)
    } else {
        holder.iconView.visibility = View.GONE
    }

    val mediaContent = nativeAd.mediaContent
    if (mediaContent != null) {
        holder.mediaView.visibility = View.VISIBLE
        holder.mediaView.mediaContent = mediaContent
    } else {
        holder.mediaView.visibility = View.GONE
    }

    val bodyText = nativeAd.body
    if (bodyText.isNullOrBlank()) {
        holder.bodyView.visibility = View.GONE
    } else {
        holder.bodyView.visibility = View.VISIBLE
        holder.bodyView.setTextColor(onSurfaceVariantArgb)
        holder.bodyView.text = bodyText
    }

    val ctaText = nativeAd.callToAction
    if (ctaText.isNullOrBlank()) {
        holder.callToActionView.visibility = View.GONE
    } else {
        holder.callToActionView.visibility = View.VISIBLE
        holder.callToActionView.text = ctaText
        holder.callToActionView.setTextColor(onPrimaryArgb)
        holder.callToActionView.background = GradientDrawable().apply {
            setColor(primaryArgb)
            cornerRadius = 14f * density
        }
    }

    nativeAdView.setNativeAd(nativeAd)
}
