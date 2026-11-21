package com.example.security

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.data.DisguiseMode

object AppDisguiseManager {

    fun applyLauncherDisguise(context: Context, targetMode: DisguiseMode): Boolean {
        return runCatching {
            val pm = context.packageManager
            DisguiseMode.entries.forEach { mode ->
                val component = ComponentName(context.packageName, mode.aliasClassName)
                val newState = if (mode == targetMode) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                pm.setComponentEnabledSetting(
                    component,
                    newState,
                    PackageManager.DONT_KILL_APP
                )
            }
            true
        }.getOrDefault(false)
    }

    fun getActiveLauncherDisguise(context: Context): DisguiseMode {
        val pm = context.packageManager
        for (mode in DisguiseMode.entries) {
            val state = runCatching {
                pm.getComponentEnabledSetting(ComponentName(context.packageName, mode.aliasClassName))
            }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)
            if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                return mode
            }
        }
        return DisguiseMode.DEFAULT
    }
}
