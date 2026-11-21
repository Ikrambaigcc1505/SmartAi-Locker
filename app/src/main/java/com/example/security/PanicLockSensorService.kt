package com.example.security

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.example.MainActivity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object PanicLockEventBus {
    private val _events = MutableSharedFlow<PanicTriggerReason>(extraBufferCapacity = 4)
    val events: SharedFlow<PanicTriggerReason> = _events.asSharedFlow()

    private val _isMonitoring = MutableStateFlow(false)
    val isMonitoring: StateFlow<Boolean> = _isMonitoring.asStateFlow()

    fun notifyTriggered(reason: PanicTriggerReason) {
        _events.tryEmit(reason)
    }

    fun setMonitoring(active: Boolean) {
        _isMonitoring.value = active
    }
}

/**
 * Android Service backed by SensorManager that monitors device acceleration for
 * firm shaking or face-down positioning, clearing unlocked sessions and launching
 * the lock screen immediately when the panic threshold is reached.
 */
class PanicLockSensorService : Service() {

    private var sensorManager: PanicLockSensorManager? = null

    override fun onCreate() {
        super.onCreate()
        sensorManager = PanicLockSensorManager(applicationContext) { reason ->
            handlePanicThresholdReached(reason)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            sensorManager?.stop()
            PanicLockEventBus.setMonitoring(false)
            stopSelf()
            return START_NOT_STICKY
        }
        val active = sensorManager?.start() == true
        PanicLockEventBus.setMonitoring(active)
        return START_NOT_STICKY
    }

    private fun handlePanicThresholdReached(reason: PanicTriggerReason) {
        // Disarm sensor monitoring immediately
        sensorManager?.stop()
        PanicLockEventBus.setMonitoring(false)

        // 1. Immediately clear active app-lock sessions and securely wipe decrypted stream cache
        AppLockAccessibilityService.clearUnlockedSessions()
        runCatching {
            val streamDir = File(applicationContext.cacheDir, "vault_stream")
            streamDir.listFiles()?.forEach { VaultCryptoManager.secureDeleteFile(it) }
            streamDir.deleteRecursively()
        }

        // 2. Notify active ViewModel / UI observers in-process
        PanicLockEventBus.notifyTriggered(reason)

        // 3. Bring up the lock screen cleanly even if another protected app was in foreground
        runCatching {
            val lockIntent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                putExtra(EXTRA_PANIC_LOCK_TRIGGERED, true)
                putExtra(EXTRA_PANIC_REASON, reason.name)
            }
            startActivity(lockIntent)
        }.onFailure { err ->
            Log.w(TAG, "Unable to launch lock screen activity from service: ${err.message}")
        }

        // 4. Disarm service until next authenticated unlock
        stopSelf()
    }

    override fun onDestroy() {
        sensorManager?.stop()
        PanicLockEventBus.setMonitoring(false)
        sensorManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "PanicLockSensorService"
        private const val ACTION_STOP = "com.example.security.ACTION_STOP_PANIC_SENSOR"
        const val EXTRA_PANIC_LOCK_TRIGGERED = "EXTRA_PANIC_LOCK_TRIGGERED"
        const val EXTRA_PANIC_REASON = "EXTRA_PANIC_REASON"

        fun start(context: Context) {
            runCatching {
                context.startService(Intent(context, PanicLockSensorService::class.java))
            }.onFailure { err ->
                Log.w(TAG, "Panic sensor service start skipped: ${err.message}")
            }
        }

        fun stop(context: Context) {
            PanicLockEventBus.setMonitoring(false)
            runCatching {
                context.stopService(Intent(context, PanicLockSensorService::class.java))
            }.onFailure { err ->
                Log.w(TAG, "Panic sensor service stop skipped: ${err.message}")
            }
        }
    }
}
