package com.example.security

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.data.SecurityPreferences
import com.example.data.VaultDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppLockMonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastObservedForeground: String? = null
    private var lastPromptMs: Long = 0L

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val notification = buildNotification()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.onFailure { err ->
            Log.w(TAG, "Unable to promote monitor service to foreground: ${err.message}")
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startMonitoringLoop()
        return START_STICKY
    }

    private fun startMonitoringLoop() {
        scope.launch {
            while (isActive) {
                runCatching { checkForegroundApp() }
                delay(1_200L)
            }
        }
    }

    private suspend fun checkForegroundApp() {
        if (!SystemPermissionsHelper.hasUsageStatsPermission(applicationContext)) return
        // Avoid duplicate prompts when AccessibilityService is already active
        if (SystemPermissionsHelper.isAccessibilityServiceEnabled(applicationContext)) return

        val pkg = queryRecentForegroundPackage() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return

        val prefs = SecurityPreferences(applicationContext).configFlow.first()
        val dao = VaultDatabase.getInstance(applicationContext).vaultDao()
        val lockedApps = dao.getLockedAppsOnce().filter { it.isLocked }.map { it.packageName }.toSet()

        val shouldLock = (prefs.settingsAppLocked && pkg == "com.android.settings") ||
            lockedApps.contains(pkg)

        if (shouldLock && !AppLockAccessibilityService.isPackageTemporarilyUnlocked(pkg)) {
            val now = System.currentTimeMillis()
            if (pkg != lastObservedForeground || now - lastPromptMs > 4_000L) {
                lastObservedForeground = pkg
                lastPromptMs = now
                val lockIntent = Intent(applicationContext, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    putExtra(EXTRA_LOCKED_TARGET_PACKAGE, pkg)
                }
                startActivity(lockIntent)
            }
        }
    }

    private fun queryRecentForegroundPackage(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return null
        val end = System.currentTimeMillis()
        val start = end - 5_000L
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        var latestPkg: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            @Suppress("DEPRECATION")
            val isResumed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND
            }
            if (isResumed && !event.packageName.isNullOrBlank()) {
                latestPkg = event.packageName
            }
        }
        return latestPkg
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "SmartAI App Guard",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.monitor_service_description)
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)
            }
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("App & Vault guard running locally")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pi)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "AppLockMonitorService"
        private const val CHANNEL_ID = "smartai_app_guard_channel"
        private const val NOTIFICATION_ID = 7021
        const val EXTRA_LOCKED_TARGET_PACKAGE = "EXTRA_LOCKED_TARGET_PACKAGE"

        fun startIfPermitted(context: Context) {
            if (!SystemPermissionsHelper.hasUsageStatsPermission(context)) return
            val intent = Intent(context, AppLockMonitorService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { err ->
                Log.w(TAG, "Background FGS start deferred: ${err.message}")
            }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, AppLockMonitorService::class.java))
            }
        }
    }
}
