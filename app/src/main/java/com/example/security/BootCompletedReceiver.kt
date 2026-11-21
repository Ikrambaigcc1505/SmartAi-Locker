package com.example.security

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.VaultDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppLockAccessibilityService.clearUnlockedSessions()
                val dao = VaultDatabase.getInstance(context.applicationContext).vaultDao()
                val hasLockedApps = dao.getLockedAppsOnce().any { it.isLocked }
                if (hasLockedApps && SystemPermissionsHelper.hasUsageStatsPermission(context)) {
                    AppLockMonitorService.startIfPermitted(context.applicationContext)
                }
            } catch (e: Exception) {
                Log.w("BootCompletedReceiver", "Re-arm monitor error: ${e.message}")
            } finally {
                pendingResult.finish()
            }
        }
    }
}
