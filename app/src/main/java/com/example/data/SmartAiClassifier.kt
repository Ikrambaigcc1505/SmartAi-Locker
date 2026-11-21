package com.example.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.Locale
import java.util.UUID

object SmartAiClassifier {

    data class ClassificationResult(
        val recommendedCategory: VaultCategory,
        val aiTag: String,
        val confidence: Float,
        val reason: String,
        val shouldSuggestAutoHide: Boolean
    )

    data class SecurityCheckItem(
        val title: String,
        val detail: String,
        val passed: Boolean,
        val pointsEarned: Int,
        val maxPoints: Int
    )

    data class SecurityAnalysisReport(
        val score: Int,
        val grade: String,
        val checks: List<SecurityCheckItem>
    )

    private val identityRegex = Regex(
        "(?<![a-z])(passport|id[_-]?card|national[_-]?id|aadhaar|cnic|driver|license|ssn|visa|permit|birth|kyc|identity)(?![a-z])",
        RegexOption.IGNORE_CASE
    )
    private val financeRegex = Regex(
        "(?<![a-z])(bank|stmt|statement|inv|invoice|tax|salary|payslip|receipt|credit|debit|acct|account|iban|swift|wallet|crypto|seed|recovery|billing|loan|mortgage|irs|w2)(?![a-z])",
        RegexOption.IGNORE_CASE
    )
    private val confidentialRegex = Regex(
        "(?<![a-z])(nda|contract|agreement|confidential|secret|private|pwd|password|credentials|backup|legal|medical|prescription|report|lab|hr|offer|otp)(?![a-z])",
        RegexOption.IGNORE_CASE
    )
    private val mediaCaptureRegex = Regex(
        "(?<![a-z])(img|vid|pxl|dcim|screenshot|screen[_-]?rec|whatsapp|telegram|snap)[_-]?\\d*",
        RegexOption.IGNORE_CASE
    )
    private val voiceKeywords = listOf(
        "call", "rec", "recording", "voice", "memo", "note", "meeting", "interview", "audio"
    )

    private const val LARGE_VIDEO_THRESHOLD_BYTES = 10L * 1024L * 1024L
    private const val LARGE_IMAGE_THRESHOLD_BYTES = 4L * 1024L * 1024L

    fun analyzeLocalSecurityScore(
        config: SecurityConfigState,
        permissionStatus: SystemPermissionStatus,
        intruderLogs: List<IntruderLogEntity>
    ): SecurityAnalysisReport {
        val checks = mutableListOf<SecurityCheckItem>()

        val bioPts = if (config.biometricEnabled) 20 else 0
        checks += SecurityCheckItem(
            title = "Biometric Unlock",
            detail = if (config.biometricEnabled) "Fingerprint / FaceID active" else "Enable Biometrics in Settings",
            passed = config.biometricEnabled,
            pointsEarned = bioPts,
            maxPoints = 20
        )

        val pinStrengthPts = when {
            config.pinLength >= 6 && config.hasPattern -> 20
            config.hasMasterPin && config.hasPattern -> 15
            config.hasMasterPin -> 10
            else -> 0
        }
        checks += SecurityCheckItem(
            title = "PIN & Pattern Strength",
            detail = if (config.pinLength >= 6 && config.hasPattern) "6-Digit PIN + 3x3 Pattern active"
            else "${config.pinLength}-Digit PIN ${if (config.hasPattern) "+ Pattern" else "only"} (6-digit recommended)",
            passed = pinStrengthPts >= 15,
            pointsEarned = pinStrengthPts,
            maxPoints = 20
        )

        val autoLockPts = when {
            config.autoLockSeconds == 0 -> 15
            config.autoLockSeconds <= 60 -> 10
            else -> 5
        }
        checks += SecurityCheckItem(
            title = "Auto-Lock Timer",
            detail = if (config.autoLockSeconds == 0) "Instant Lock active on minimize"
            else "Locks after ${config.autoLockSeconds}s in background",
            passed = config.autoLockSeconds <= 60,
            pointsEarned = autoLockPts,
            maxPoints = 15
        )

        val recoveryPts = if (config.hasSecurityQuestions) 15 else 0
        checks += SecurityCheckItem(
            title = "Offline Recovery Questions",
            detail = if (config.hasSecurityQuestions) "2 local recovery answers configured" else "Configure recovery answers",
            passed = config.hasSecurityQuestions,
            pointsEarned = recoveryPts,
            maxPoints = 15
        )

        val intruderArmed = config.intruderSelfieEnabled && permissionStatus.cameraGranted
        val intruderPts = if (intruderArmed) 15 else if (config.intruderSelfieEnabled) 8 else 0
        checks += SecurityCheckItem(
            title = "Intruder Selfie Guard",
            detail = if (intruderArmed) "Front camera armed for 3 wrong attempts"
            else "Grant Camera permission for intruder capture",
            passed = intruderArmed,
            pointsEarned = intruderPts,
            maxPoints = 15
        )

        val flagSecurePts = if (config.flagSecureEnabled) 15 else 0
        checks += SecurityCheckItem(
            title = "Screenshot & Recents Shield",
            detail = if (config.flagSecureEnabled) "FLAG_SECURE blocking screen capture" else "Enable FLAG_SECURE in Settings",
            passed = config.flagSecureEnabled,
            pointsEarned = flagSecurePts,
            maxPoints = 15
        )

        val rawScore = checks.sumOf { it.pointsEarned }
        val logPenalty = (intruderLogs.size * 3).coerceAtMost(10)
        val finalScore = (rawScore - logPenalty).coerceIn(0, 100)

        if (intruderLogs.isNotEmpty()) {
            checks += SecurityCheckItem(
                title = "Failed Unlock Alerts",
                detail = "${intruderLogs.size} failed-attempt log(s) detected locally (-$logPenalty pts)",
                passed = false,
                pointsEarned = 0,
                maxPoints = 0
            )
        }

        val grade = when {
            finalScore >= 90 -> "Fortress"
            finalScore >= 75 -> "Strong"
            finalScore >= 55 -> "Moderate"
            else -> "At Risk"
        }
        return SecurityAnalysisReport(score = finalScore, grade = grade, checks = checks)
    }

    fun classifyFileMetadata(
        displayName: String,
        mimeType: String,
        snippetText: String? = null,
        sizeBytes: Long = 0L
    ): ClassificationResult {
        val lowerName = displayName.lowercase(Locale.ROOT)
        val lowerMime = mimeType.lowercase(Locale.ROOT)
        val combinedText = "$lowerName ${snippetText.orEmpty().lowercase(Locale.ROOT)}"

        val category = detectVaultCategory(lowerName, lowerMime)

        return when {
            identityRegex.containsMatchIn(combinedText) -> ClassificationResult(
                recommendedCategory = category,
                aiTag = "Identity & IDs",
                confidence = 0.96f,
                reason = "Regex matched identity or government credential markers",
                shouldSuggestAutoHide = true
            )
            financeRegex.containsMatchIn(combinedText) -> ClassificationResult(
                recommendedCategory = category,
                aiTag = "Financial & Banking",
                confidence = 0.94f,
                reason = "Regex matched banking, invoice, or statement markers",
                shouldSuggestAutoHide = true
            )
            confidentialRegex.containsMatchIn(combinedText) -> ClassificationResult(
                recommendedCategory = category,
                aiTag = "Confidential Docs",
                confidence = 0.91f,
                reason = "Matched legal, medical, or sensitive access keywords",
                shouldSuggestAutoHide = true
            )
            category == VaultCategory.AUDIO && voiceKeywords.any { combinedText.contains(it) } -> ClassificationResult(
                recommendedCategory = VaultCategory.AUDIO,
                aiTag = "Voice & Call Memo",
                confidence = 0.88f,
                reason = "Identified private voice note or call recording pattern",
                shouldSuggestAutoHide = true
            )
            category == VaultCategory.VIDEOS && sizeBytes >= LARGE_VIDEO_THRESHOLD_BYTES -> ClassificationResult(
                recommendedCategory = VaultCategory.VIDEOS,
                aiTag = "Large Private Video",
                confidence = 0.89f,
                reason = "Large video file (>= 10 MB) flagged for sandboxed vaulting",
                shouldSuggestAutoHide = true
            )
            category == VaultCategory.PHOTOS && sizeBytes >= LARGE_IMAGE_THRESHOLD_BYTES -> ClassificationResult(
                recommendedCategory = VaultCategory.PHOTOS,
                aiTag = "High-Res / RAW Photo",
                confidence = 0.86f,
                reason = "Large image or RAW photo (>= 4 MB) flagged for locking",
                shouldSuggestAutoHide = true
            )
            category == VaultCategory.PHOTOS && mediaCaptureRegex.containsMatchIn(combinedText) -> ClassificationResult(
                recommendedCategory = VaultCategory.PHOTOS,
                aiTag = "Private Photo",
                confidence = 0.84f,
                reason = "Personal camera, screenshot, or messaging media pattern",
                shouldSuggestAutoHide = true
            )
            category == VaultCategory.VIDEOS -> ClassificationResult(
                recommendedCategory = VaultCategory.VIDEOS,
                aiTag = "Private Video",
                confidence = 0.82f,
                reason = "Personal video clip recommended for sandboxed playback",
                shouldSuggestAutoHide = true
            )
            category == VaultCategory.DOCUMENTS -> ClassificationResult(
                recommendedCategory = VaultCategory.DOCUMENTS,
                aiTag = "Protected Document",
                confidence = 0.79f,
                reason = "Document or archive suitable for AES-256 vault storage",
                shouldSuggestAutoHide = true
            )
            else -> ClassificationResult(
                recommendedCategory = category,
                aiTag = when (category) {
                    VaultCategory.PHOTOS -> "Gallery Media"
                    VaultCategory.VIDEOS -> "Video Clip"
                    VaultCategory.AUDIO -> "Audio Track"
                    VaultCategory.DOCUMENTS -> "General File"
                },
                confidence = 0.74f,
                reason = "Standard media file indexed by on-device classifier",
                shouldSuggestAutoHide = false
            )
        }
    }

    fun inspectUriForSuggestion(context: Context, uri: Uri): AiScanCandidate {
        var displayName = "document_${System.currentTimeMillis()}"
        var sizeBytes = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) sizeBytes = cursor.getLong(sizeIdx)
            }
        }
        val mimeType = context.contentResolver.getType(uri) ?: guessMimeFromName(displayName)
        val snippet = runCatching {
            if (mimeType.startsWith("text/") || mimeType == "application/pdf") {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val buf = ByteArray(4096)
                    val read = stream.read(buf)
                    if (read > 0) String(buf, 0, read, Charsets.UTF_8) else null
                }
            } else {
                null
            }
        }.getOrNull()

        val result = classifyFileMetadata(displayName, mimeType, snippet, sizeBytes)
        return AiScanCandidate(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            sourceUriString = uri.toString(),
            recommendedCategory = result.recommendedCategory,
            aiTag = result.aiTag,
            confidence = result.confidence,
            reason = result.reason,
            isAlreadyVaulted = false
        )
    }

    fun detectVaultCategory(fileName: String, mimeType: String): VaultCategory {
        val lower = fileName.lowercase(Locale.ROOT)
        val mime = mimeType.lowercase(Locale.ROOT)
        return when {
            mime.startsWith("image/") ||
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
                lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".dng") ||
                lower.endsWith(".raw") -> VaultCategory.PHOTOS
            mime.startsWith("video/") ||
                lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.endsWith(".avi") ||
                lower.endsWith(".mov") || lower.endsWith(".webm") -> VaultCategory.VIDEOS
            mime.startsWith("audio/") ||
                lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac") ||
                lower.endsWith(".m4a") || lower.endsWith(".ogg") || lower.endsWith(".flac") -> VaultCategory.AUDIO
            else -> VaultCategory.DOCUMENTS
        }
    }

    fun guessMimeFromName(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "raw", "dng" -> "image/x-adobe-dng"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "m4a" -> "audio/mp4"
            "pdf" -> "application/pdf"
            "txt", "log", "csv", "json", "xml" -> "text/plain"
            "zip" -> "application/zip"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }
    }
}
