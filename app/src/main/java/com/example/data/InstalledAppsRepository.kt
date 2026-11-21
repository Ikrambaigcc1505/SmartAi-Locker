package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(private val context: Context) {

    suspend fun loadInstalledApps(lockedPackageNames: Set<String>): List<InstalledAppInfo> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolved = pm.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
            val seen = HashSet<String>()
            val results = mutableListOf<InstalledAppInfo>()

            for (info in resolved) {
                val appInfo = info.activityInfo?.applicationInfo ?: continue
                val pkg = appInfo.packageName ?: continue
                if (pkg == context.packageName || !seen.add(pkg)) continue

                val appName = info.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: pkg
                val icon = runCatching { info.loadIcon(pm) }.getOrNull()
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

                results += InstalledAppInfo(
                    packageName = pkg,
                    appName = appName,
                    icon = icon,
                    isSystemApp = isSystem,
                    isLocked = lockedPackageNames.contains(pkg)
                )
            }

            results.sortedWith(
                compareByDescending<InstalledAppInfo> { it.isLocked }
                    .thenBy { it.appName.lowercase() }
            )
        }
}
