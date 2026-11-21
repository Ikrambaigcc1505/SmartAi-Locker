package com.example.ads

/**
 * Single-source Google AdMob Configuration.
 *
 * NOTE: Never display raw App ID or Ad Unit IDs in any user-facing UI.
 */
object AdConfig {
    // Google AdMob Application ID (also declared in AndroidManifest.xml & strings.xml)
    const val ADMOB_APP_ID = "ca-app-pub-5015993859793728~8464255789"

    // Banner Ad Unit ID (anchored adaptive banner in dashboard shell)
    const val BANNER_AD_UNIT_ID = "ca-app-pub-5015993859793728/7151174115"

    // Interstitial Ad Unit ID (shown naturally on vault section transitions with cooldown)
    const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-5015993859793728/2297021425"

    // Native Advanced Ad Unit ID (rendered inside HomeDashboardScreen via NativeAdView)
    const val NATIVE_AD_UNIT_ID = "ca-app-pub-5015993859793728/8843529737"

    // Rewarded Ad Unit ID (optional Pro theme & AI Deep Scan unlock)
    const val REWARDED_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"

    // App Open Ad Unit ID (shown when returning to an already-unlocked session)
    const val APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
}
