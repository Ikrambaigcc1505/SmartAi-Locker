package com.example

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.data.SecurityConfigState
import com.example.security.AppLockAccessibilityService
import com.example.security.PanicLockDetector
import com.example.security.PanicLockEventBus
import com.example.security.PanicLockSensorManager
import com.example.security.PanicLockSensorService
import com.example.security.PanicTriggerReason
import com.example.ui.components.SensorMonitoringBadge
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.SmartAiLockerTheme
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import org.robolectric.shadows.ShadowSensorManager
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PanicLockSensorTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `detector triggers SHAKE after two high-g spikes within window`() {
        val detector = PanicLockDetector()
        // Baseline upright
        assertNull(detector.onAccelerometerSample(0f, 0f, 9.81f, 1_000L))
        // First ~2.8g spike
        assertNull(detector.onAccelerometerSample(27.5f, 0f, 0f, 1_100L))
        // Too-soon sample within 120ms debounce does not trigger
        assertNull(detector.onAccelerometerSample(27.5f, 0f, 0f, 1_150L))
        // Second >= 2.45g spike >= 120ms later and within 800ms
        val triggered = detector.onAccelerometerSample(0f, 28.0f, 0f, 1_300L)
        assertEquals(PanicTriggerReason.SHAKE, triggered)
    }

    @Test
    fun `detector triggers FACE_DOWN after upright then sustained face down`() {
        val detector = PanicLockDetector()
        // Initial face-down before upright should NOT trigger (prevents relock loop)
        assertNull(detector.onAccelerometerSample(0f, 0f, -9.8f, 1_000L))
        assertNull(detector.onAccelerometerSample(0f, 0f, -9.8f, 1_400L))

        // Upright seen
        assertNull(detector.onAccelerometerSample(0f, 2.0f, 9.5f, 1_600L))
        // Flipped face down — below 180ms hold
        assertNull(detector.onAccelerometerSample(0f, 0f, -9.4f, 1_700L))
        assertNull(detector.onAccelerometerSample(0f, 0f, -9.4f, 1_800L))
        // Held face down >= 180ms
        val triggered = detector.onAccelerometerSample(0.2f, -0.3f, -9.5f, 1_900L)
        assertEquals(PanicTriggerReason.FACE_DOWN, triggered)
    }

    @Test
    fun `service monitors accelerometer clears unlocked sessions and launches lock screen on SHAKE`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val shadowSm: ShadowSensorManager = Shadows.shadowOf(sensorManager)
        val accelSensor = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowSm.addSensor(accelSensor)

        AppLockAccessibilityService.clearUnlockedSessions()
        AppLockAccessibilityService.markPackageTemporarilyUnlocked("com.example.protectedapp")
        assertTrue(AppLockAccessibilityService.isPackageTemporarilyUnlocked("com.example.protectedapp"))

        PanicLockEventBus.setMonitoring(false)
        val controller = Robolectric.buildService(PanicLockSensorService::class.java)
            .create()
            .startCommand(0, 0)
        val service = controller.get()

        val sensorWrapper = findPanicSensorWrapper(service)
        assertTrue(sensorWrapper.hasAccelerometer)
        assertTrue(sensorWrapper.isMonitoring)
        assertTrue(PanicLockEventBus.isMonitoring.value)
        assertTrue(shadowSm.hasListener(sensorWrapper))

        // Feed two high-g shake events directly via SensorEventListener
        runBlocking {
            val received = mutableListOf<PanicTriggerReason>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2_000L) {
                    received.add(PanicLockEventBus.events.first())
                }
            }

            sensorWrapper.onSensorChanged(createAccelEvent(accelSensor, 28.0f, 0f, 0f))
            Thread.sleep(150L)
            sensorWrapper.onSensorChanged(createAccelEvent(accelSensor, -28.0f, 0f, 0f))
            collector.join()

            assertEquals(listOf(PanicTriggerReason.SHAKE), received)
        }

        // Session cleared & monitoring stopped
        assertFalse(AppLockAccessibilityService.isPackageTemporarilyUnlocked("com.example.protectedapp"))
        assertFalse(PanicLockEventBus.isMonitoring.value)

        // Lock screen Activity launch intent verified
        val appShadow = Shadows.shadowOf(ApplicationProvider.getApplicationContext<SmartAiApplication>())
        val nextIntent: Intent = appShadow.nextStartedActivity
        assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
        assertTrue(nextIntent.getBooleanExtra(PanicLockSensorService.EXTRA_PANIC_LOCK_TRIGGERED, false))
        assertEquals(
            PanicTriggerReason.SHAKE.name,
            nextIntent.getStringExtra(PanicLockSensorService.EXTRA_PANIC_REASON)
        )

        controller.destroy()
        assertFalse(sensorWrapper.isMonitoring)
        assertFalse(PanicLockEventBus.isMonitoring.value)
    }

    @Test
    fun `service terminates session wipes temp stream cache and locks immediately on FACE_DOWN`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val shadowSm: ShadowSensorManager = Shadows.shadowOf(sensorManager)
        val accelSensor = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        shadowSm.addSensor(accelSensor)

        // Create active unlocked app session & decrypted temp stream file
        AppLockAccessibilityService.clearUnlockedSessions()
        AppLockAccessibilityService.markPackageTemporarilyUnlocked("com.example.messenger")
        val tempDir = File(context.cacheDir, "vault_stream").apply { mkdirs() }
        val tempFile = File(tempDir, "secret_preview.mp4").apply { writeText("decrypted-bytes") }
        assertTrue(tempFile.exists())

        PanicLockEventBus.setMonitoring(false)
        val controller = Robolectric.buildService(PanicLockSensorService::class.java)
            .create()
            .startCommand(0, 0)
        val sensorWrapper = findPanicSensorWrapper(controller.get())
        assertTrue(PanicLockEventBus.isMonitoring.value)

        runBlocking {
            val received = mutableListOf<PanicTriggerReason>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(2_000L) {
                    received.add(PanicLockEventBus.events.first())
                }
            }

            // Device held upright, then placed flat face-down for >= 180ms
            sensorWrapper.onSensorChanged(createAccelEvent(accelSensor, 0f, 1.5f, 9.6f))
            sensorWrapper.onSensorChanged(createAccelEvent(accelSensor, 0.1f, -0.1f, -9.5f))
            Thread.sleep(200L)
            sensorWrapper.onSensorChanged(createAccelEvent(accelSensor, 0.0f, 0.1f, -9.6f))
            collector.join()

            assertEquals(listOf(PanicTriggerReason.FACE_DOWN), received)
        }

        // Session & decrypted cache terminated immediately
        assertFalse(AppLockAccessibilityService.isPackageTemporarilyUnlocked("com.example.messenger"))
        assertFalse(tempFile.exists())
        assertFalse(PanicLockEventBus.isMonitoring.value)

        val appShadow = Shadows.shadowOf(ApplicationProvider.getApplicationContext<SmartAiApplication>())
        val nextIntent: Intent = appShadow.nextStartedActivity
        assertEquals(MainActivity::class.java.name, nextIntent.component?.className)
        assertTrue(nextIntent.getBooleanExtra(PanicLockSensorService.EXTRA_PANIC_LOCK_TRIGGERED, false))
        assertEquals(
            PanicTriggerReason.FACE_DOWN.name,
            nextIntent.getStringExtra(PanicLockSensorService.EXTRA_PANIC_REASON)
        )

        controller.destroy()
        assertFalse(PanicLockEventBus.isMonitoring.value)
    }

    @Test
    fun `settings panic gesture toggle updates sensor monitoring visual indicator`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val shadowSm: ShadowSensorManager = Shadows.shadowOf(sensorManager)
        shadowSm.addSensor(ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER))

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            var config by remember { mutableStateOf(SecurityConfigState(panicLockEnabled = true)) }
            var monitoring by remember { mutableStateOf(true) }

            SmartAiLockerTheme {
                SettingsScreen(
                    config = config,
                    isPanicSensorMonitoring = monitoring,
                    onBack = {},
                    onSetThemeMode = {},
                    onSetAccentTheme = {},
                    onSetPreferredAuth = {},
                    onSetBiometricEnabled = {},
                    onSetIntruderSelfieEnabled = {},
                    onSetAutoLockTimer = { _, _ -> },
                    onSetKeepUnlockedDuringPlayback = {},
                    onSetBackgroundAudioPlay = {},
                    onSetFlagSecure = {},
                    onSetRandomizeKeypad = {},
                    onSetFakeCrashEnabled = {},
                    onSetPanicLockEnabled = { enabled ->
                        config = config.copy(panicLockEnabled = enabled)
                        monitoring = enabled
                    },
                    onOpenAppDisguise = {},
                    onOpenPrivacyGuarantee = {},
                    onVerifyCurrentPin = { true },
                    onUpdatePinAndPattern = { _, _, _ -> },
                    onUpdateSecurityQuestions = { _, _, _, _ -> },
                    onWatchRewardedUnlock = {}
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.onNodeWithTag("settings_lazy_column")
            .performScrollToNode(hasTestTag("panic_sensor_status_indicator"))
        composeRule.mainClock.advanceTimeBy(100L)

        composeRule.onNodeWithText("Panic Gesture").assertIsDisplayed()
        composeRule.onNodeWithTag("switch_panic_lock").assertIsOn()
        composeRule.onNodeWithText("Monitoring Motion Sensors Now").assertIsDisplayed()

        // Toggle Panic Gesture OFF -> monitoring indicator turns paused
        composeRule.onNodeWithTag("switch_panic_lock").performClick()
        composeRule.mainClock.advanceTimeBy(100L)

        composeRule.onNodeWithTag("switch_panic_lock").assertIsOff()
        composeRule.onNodeWithText("Sensor Monitoring Paused").assertIsDisplayed()
    }

    @Test
    fun `sensor monitoring badge displays live status and off state`() {
        composeRule.mainClock.autoAdvance = false
        var active by mutableStateOf(true)
        var enabled by mutableStateOf(true)

        composeRule.setContent {
            SmartAiLockerTheme {
                SensorMonitoringBadge(
                    isMonitoring = active,
                    isEnabled = enabled,
                    hasSensor = true,
                    onClick = {}
                )
            }
        }

        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.onNodeWithText("Sensors Live • Face-Down/Shake").assertIsDisplayed()

        active = false
        enabled = false
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.onNodeWithText("Panic Gesture Off").assertIsDisplayed()
    }

    private fun findPanicSensorWrapper(service: PanicLockSensorService): PanicLockSensorManager {
        val field = PanicLockSensorService::class.java.getDeclaredField("sensorManager")
        field.isAccessible = true
        return field.get(service) as PanicLockSensorManager
    }

    private fun createAccelEvent(sensor: Sensor, x: Float, y: Float, z: Float): SensorEvent {
        val ctor = SensorEvent::class.java.getDeclaredConstructor(Int::class.javaPrimitiveType)
        ctor.isAccessible = true
        val event = ctor.newInstance(3)
        event.sensor = sensor
        event.values[0] = x
        event.values[1] = y
        event.values[2] = z
        return event
    }
}
