package com.example.security

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.MainActivity
import com.example.data.SecurityPreferences
import com.example.data.VaultDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AppLockAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastPromptPackage: String? = null
    private var lastPromptTimeMs: Long = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName || pkg == "com.android.systemui") return
        if (isPackageTemporarilyUnlocked(pkg)) return

        serviceScope.launch {
            val prefs = SecurityPreferences(applicationContext).configFlow.first()
            val dao = VaultDatabase.getInstance(applicationContext).vaultDao()
            val lockedApps = dao.getLockedAppsOnce().filter { it.isLocked }.map { it.packageName }.toSet()

            val isSettingsLocked = prefs.settingsAppLocked && pkg == "com.android.settings"
            val shouldLock = isSettingsLocked || lockedApps.contains(pkg)

            if (shouldLock) {
                val now = System.currentTimeMillis()
                if (pkg != lastPromptPackage || now - lastPromptTimeMs > 3_500L) {
                    lastPromptPackage = pkg
                    lastPromptTimeMs = now
                    val lockIntent = Intent(applicationContext, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra(AppLockMonitorService.EXTRA_LOCKED_TARGET_PACKAGE, pkg)
                    }
                    startActivity(lockIntent)
                }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        private val unlockedSessionPackages = ConcurrentHashMap<String, Long>()

        fun markPackageTemporarilyUnlocked(packageName: String) {
            unlockedSessionPackages[packageName] = System.currentTimeMillis()
        }

        fun isPackageTemporarilyUnlocked(packageName: String): Boolean {
            return unlockedSessionPackages.containsKey(packageName)
        }

        fun clearUnlockedSessions() {
            unlockedSessionPackages.clear()
        }
    }
}
