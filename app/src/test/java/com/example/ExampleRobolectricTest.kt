package com.example

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.example.ads.AdConfig
import com.example.security.IntruderCameraHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("SmartAi Locker", appName)
  }

  @Test
  fun `intruder gallery filename contains timestamp and jpg extension`() {
    val filename = IntruderCameraHelper.buildTimestampedFilename(1_760_000_000_000L)
    assertTrue(filename.startsWith("SmartAi_Locker_"))
    assertTrue(filename.endsWith(".jpg"))
  }

  @Test
  fun `admob configuration and manifest metadata match live ids`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    assertTrue(context is SmartAiApplication)
    assertEquals("ca-app-pub-5015993859793728~8464255789", AdConfig.ADMOB_APP_ID)
    assertEquals("ca-app-pub-5015993859793728/7151174115", AdConfig.BANNER_AD_UNIT_ID)
    assertEquals("ca-app-pub-5015993859793728/2297021425", AdConfig.INTERSTITIAL_AD_UNIT_ID)
    assertEquals("ca-app-pub-5015993859793728/8843529737", AdConfig.NATIVE_AD_UNIT_ID)
    assertEquals("ca-app-pub-5015993859793728~8464255789", context.getString(R.string.admob_app_id))

    val appInfo = context.packageManager.getApplicationInfo(
      context.packageName,
      PackageManager.GET_META_DATA
    )
    val metaAppId = appInfo.metaData?.getString("com.google.android.gms.ads.APPLICATION_ID")
    assertEquals("ca-app-pub-5015993859793728~8464255789", metaAppId)
    assertTrue(appInfo.metaData?.getBoolean("com.google.android.gms.ads.flag.OPTIMIZE_INITIALIZATION") == true)
    assertTrue(appInfo.metaData?.getBoolean("com.google.android.gms.ads.flag.OPTIMIZE_AD_LOADING") == true)

    val pkgInfo = context.packageManager.getPackageInfo(
      context.packageName,
      PackageManager.GET_PERMISSIONS
    )
    val perms = pkgInfo.requestedPermissions?.toSet().orEmpty()
    assertTrue("android.permission.ACCESS_ADSERVICES_ATTRIBUTION" !in perms)
    assertTrue("android.permission.ACCESS_ADSERVICES_TOPICS" !in perms)
    assertTrue("android.permission.ACCESS_ADSERVICES_AD_ID" !in perms)
  }
}
