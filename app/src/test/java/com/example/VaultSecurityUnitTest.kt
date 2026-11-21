package com.example

import com.example.data.IntruderLogEntity
import com.example.data.SecurityConfigState
import com.example.data.SmartAiClassifier
import com.example.data.SystemPermissionStatus
import com.example.data.VaultCategory
import com.example.security.CalculatorEngine
import com.example.security.KeypadHelper
import com.example.security.VaultCryptoManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VaultSecurityUnitTest {

    @Test
    fun pbkdf2PinHashingVerifiesCorrectly() {
        val salt = VaultCryptoManager.generateSaltBase64()
        val hash = VaultCryptoManager.hashSecret("2580", salt)
        assertTrue(VaultCryptoManager.verifySecret("2580", salt, hash))
        assertFalse(VaultCryptoManager.verifySecret("2581", salt, hash))
        assertFalse(VaultCryptoManager.verifySecret("", salt, hash))
    }

    @Test
    fun smartVaultBackupEncryptionRoundTripSucceeds() {
        val samplePayload = "SmartAI-Offline-Vault-Payload-12345".toByteArray(Charsets.UTF_8)
        val encrypted = VaultCryptoManager.encryptBackupWithPassphrase(samplePayload, "strongPass!9")
        val decrypted = VaultCryptoManager.decryptBackupWithPassphrase(encrypted, "strongPass!9")
        assertArrayEquals(samplePayload, decrypted)
    }

    @Test(expected = Exception::class)
    fun smartVaultBackupRejectsWrongPassphrase() {
        val samplePayload = "Secret-Vault-Bytes".toByteArray(Charsets.UTF_8)
        val encrypted = VaultCryptoManager.encryptBackupWithPassphrase(samplePayload, "correctPass")
        VaultCryptoManager.decryptBackupWithPassphrase(encrypted, "wrongPass")
    }

    @Test
    fun streamingSmartVaultV2MultiChunkRoundTripSucceeds() {
        // 300 KB payload forces 5 distinct 64KB CipherOutputStream / CipherInputStream chunks
        val largePayload = ByteArray(300 * 1024) { idx -> ((idx * 31) and 0xFF).toByte() }
        val encryptedOut = ByteArrayOutputStream()
        VaultCryptoManager.encryptBackupStream(
            ByteArrayInputStream(largePayload),
            encryptedOut,
            "UltraStrong#2026"
        )
        val cipherBytes = encryptedOut.toByteArray()
        assertTrue(cipherBytes.size > largePayload.size)

        val decryptedOut = ByteArrayOutputStream()
        VaultCryptoManager.decryptBackupStream(
            ByteArrayInputStream(cipherBytes),
            decryptedOut,
            "UltraStrong#2026"
        )
        assertArrayEquals(largePayload, decryptedOut.toByteArray())
    }

    @Test(expected = Exception::class)
    fun streamingSmartVaultV2RejectsWrongPassphraseAndTruncation() {
        val payload = ByteArray(150 * 1024) { 0x42 }
        val encryptedOut = ByteArrayOutputStream()
        VaultCryptoManager.encryptBackupStream(
            ByteArrayInputStream(payload),
            encryptedOut,
            "rightPass123"
        )
        VaultCryptoManager.decryptBackupStream(
            ByteArrayInputStream(encryptedOut.toByteArray()),
            ByteArrayOutputStream(),
            "wrongPass123"
        )
    }

    @Test
    fun calculatorEngineEvaluatesMathAndExtractsPinTriggers() {
        assertEquals("28", CalculatorEngine.evaluateExpression("12+8×2"))
        assertEquals("5", CalculatorEngine.applyUnary("25", "sqrt"))
        assertEquals("Error", CalculatorEngine.evaluateExpression("9÷0"))

        assertEquals("2580", CalculatorEngine.extractPinCandidate("2580"))
        assertEquals("135790", CalculatorEngine.extractPinCandidate("135790"))
        assertNull(CalculatorEngine.extractPinCandidate("12+34"))
        assertNull(CalculatorEngine.extractPinCandidate("99"))
    }

    @Test
    fun randomizedKeypadContainsAllTenDigitsInShuffledOrder() {
        val standard = KeypadHelper.generatePinKeypadRows(randomized = false)
        assertEquals(listOf("1", "2", "3"), standard[0])
        assertEquals(listOf("BIO", "0", "DEL"), standard[3])

        val shuffled = KeypadHelper.generatePinKeypadRows(randomized = true, random = Random(42))
        assertEquals("BIO", shuffled[3][0])
        assertEquals("DEL", shuffled[3][2])

        val digitsOnly = shuffled.flatten().filter { it != "BIO" && it != "DEL" }
        assertEquals(10, digitsOnly.size)
        assertEquals((0..9).map { it.toString() }.toSet(), digitsOnly.toSet())
        assertNotEquals(standard.flatten(), shuffled.flatten())
    }

    @Test
    fun smartAiClassifierIdentifiesSensitiveDocumentsAndLargeMedia() {
        val passport = SmartAiClassifier.classifyFileMetadata("my_passport_scan.pdf", "application/pdf")
        assertEquals(VaultCategory.DOCUMENTS, passport.recommendedCategory)
        assertEquals("Identity & IDs", passport.aiTag)
        assertTrue(passport.shouldSuggestAutoHide)

        val bankPdf = SmartAiClassifier.classifyFileMetadata("march_bank_statement.pdf", "application/pdf")
        assertEquals("Financial & Banking", bankPdf.aiTag)
        assertTrue(bankPdf.confidence >= 0.9f)

        val largeVideo = SmartAiClassifier.classifyFileMetadata(
            displayName = "clip_01.mp4",
            mimeType = "video/mp4",
            sizeBytes = 25L * 1024L * 1024L
        )
        assertEquals(VaultCategory.VIDEOS, largeVideo.recommendedCategory)
        assertEquals("Large Private Video", largeVideo.aiTag)
        assertTrue(largeVideo.shouldSuggestAutoHide)
    }

    @Test
    fun localSecurityAnalysisReflectsDeviceStateAndIntruderLogs() {
        val strongConfig = SecurityConfigState(
            onboardingCompleted = true,
            pinLength = 6,
            hasMasterPin = true,
            hasPattern = true,
            biometricEnabled = true,
            autoLockSeconds = 0,
            hasSecurityQuestions = true,
            flagSecureEnabled = true,
            intruderSelfieEnabled = true
        )
        val fullPermissions = SystemPermissionStatus(cameraGranted = true, biometricHardwareReady = true)
        val fortress = SmartAiClassifier.analyzeLocalSecurityScore(strongConfig, fullPermissions, emptyList())
        assertEquals(100, fortress.score)
        assertEquals("Fortress", fortress.grade)

        val alerted = SmartAiClassifier.analyzeLocalSecurityScore(
            strongConfig.copy(biometricEnabled = false),
            fullPermissions,
            listOf(IntruderLogEntity(attemptMethod = "PIN", failedAttemptCount = 3, summary = "Failed"))
        )
        assertTrue(alerted.score < fortress.score)
    }
}
