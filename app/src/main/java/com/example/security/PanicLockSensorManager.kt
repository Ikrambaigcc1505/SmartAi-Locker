package com.example.security

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs
import kotlin.math.sqrt

enum class PanicTriggerReason(val label: String) {
    FACE_DOWN("Face-Down"),
    SHAKE("Firm Shake")
}

/**
 * Pure threshold state machine for detecting Shake and Face-Down panic gestures
 * from 3-axis accelerometer readings (m/s^2).
 */
class PanicLockDetector(
    private val shakeThresholdG: Float = DEFAULT_SHAKE_THRESHOLD_G,
    private val shakeWindowMs: Long = DEFAULT_SHAKE_WINDOW_MS,
    private val shakeSpikeMinGapMs: Long = DEFAULT_SPIKE_MIN_GAP_MS,
    private val faceDownHoldMs: Long = DEFAULT_FACE_DOWN_HOLD_MS,
    private val cooldownMs: Long = DEFAULT_COOLDOWN_MS
) {
    private var seenUprightSinceStart = false
    private var faceDownStartMs = 0L
    private var firstShakeMs = 0L
    private var lastShakeSpikeMs = 0L
    private var shakeCount = 0
    private var lastTriggerMs = Long.MIN_VALUE

    fun reset() {
        seenUprightSinceStart = false
        faceDownStartMs = 0L
        firstShakeMs = 0L
        lastShakeSpikeMs = 0L
        shakeCount = 0
    }

    fun onAccelerometerSample(
        x: Float,
        y: Float,
        z: Float,
        timestampMs: Long
    ): PanicTriggerReason? {
        if (lastTriggerMs != Long.MIN_VALUE && timestampMs - lastTriggerMs < cooldownMs) {
            return null
        }

        // 1. Face-down detection: require device to have been non-face-down first so
        // unlocking while lying down does not immediately re-lock in a loop.
        if (z > UPRIGHT_Z_THRESHOLD) {
            seenUprightSinceStart = true
            faceDownStartMs = 0L
        } else if (seenUprightSinceStart &&
            z <= FACE_DOWN_Z_THRESHOLD &&
            abs(x) < MAX_LATERAL_REST_ACCEL &&
            abs(y) < MAX_LATERAL_REST_ACCEL
        ) {
            if (faceDownStartMs == 0L) {
                faceDownStartMs = timestampMs
            } else if (timestampMs - faceDownStartMs >= faceDownHoldMs) {
                markTriggered(timestampMs)
                return PanicTriggerReason.FACE_DOWN
            }
        } else if (z > FACE_DOWN_Z_THRESHOLD) {
            faceDownStartMs = 0L
        }

        // 2. Firm shake detection: >= shakeThresholdG twice within shakeWindowMs
        // with a minimum debounce gap so a single sensor burst doesn't double-count.
        val gForce = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        if (gForce >= shakeThresholdG) {
            if (shakeCount == 0 || timestampMs - firstShakeMs > shakeWindowMs) {
                firstShakeMs = timestampMs
                lastShakeSpikeMs = timestampMs
                shakeCount = 1
            } else if (timestampMs - lastShakeSpikeMs >= shakeSpikeMinGapMs) {
                lastShakeSpikeMs = timestampMs
                shakeCount++
                if (shakeCount >= 2) {
                    markTriggered(timestampMs)
                    return PanicTriggerReason.SHAKE
                }
            }
        }

        return null
    }

    private fun markTriggered(nowMs: Long) {
        lastTriggerMs = nowMs
        reset()
    }

    companion object {
        const val DEFAULT_SHAKE_THRESHOLD_G = 2.45f
        const val DEFAULT_SHAKE_WINDOW_MS = 800L
        const val DEFAULT_SPIKE_MIN_GAP_MS = 120L
        const val DEFAULT_FACE_DOWN_HOLD_MS = 180L
        const val DEFAULT_COOLDOWN_MS = 1_500L

        private const val UPRIGHT_Z_THRESHOLD = -3.5f
        private const val FACE_DOWN_Z_THRESHOLD = -8.3f
        private const val MAX_LATERAL_REST_ACCEL = 5.0f
    }
}

/**
 * SensorManager accelerometer listener wrapper that detects Face-Down or Firm Shake panic gestures.
 */
class PanicLockSensorManager(
    context: Context,
    private val onPanicTriggered: (PanicTriggerReason) -> Unit
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val motionSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val detector = PanicLockDetector()
    private var isRegistered = false

    val hasAccelerometer: Boolean
        get() = motionSensor != null

    val isMonitoring: Boolean
        get() = isRegistered

    fun start(): Boolean {
        val sensor = motionSensor ?: return false
        if (isRegistered) return true
        detector.reset()
        isRegistered = sensorManager?.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_UI
        ) == true
        return isRegistered
    }

    fun stop() {
        if (!isRegistered) return
        sensorManager?.unregisterListener(this)
        isRegistered = false
        detector.reset()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val activeType = motionSensor?.type ?: return
        if (event?.sensor?.type != activeType) return
        val values = event.values
        if (values.size < 3) return
        val trigger = detector.onAccelerometerSample(
            x = values[0],
            y = values[1],
            z = values[2],
            timestampMs = System.currentTimeMillis()
        )
        if (trigger != null) {
            onPanicTriggered(trigger)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        fun hasAccelerometer(context: Context): Boolean {
            val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            return sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null ||
                sm?.getDefaultSensor(Sensor.TYPE_GRAVITY) != null
        }
    }
}
