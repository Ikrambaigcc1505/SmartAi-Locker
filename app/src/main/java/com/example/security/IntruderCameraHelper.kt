package com.example.security

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

data class IntruderCaptureResult(
    val encryptedFileName: String?,
    val galleryUriString: String?,
    val statusNote: String
)

object IntruderCameraHelper {

    private const val TAG = "IntruderCameraHelper"
    private const val GALLERY_ALBUM_NAME = "SmartAi Locker"
    private val ioExecutor = Executors.newSingleThreadExecutor()

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasFrontCameraFeature(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT) ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    fun captureIntruderSelfie(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        onCaptured: (IntruderCaptureResult) -> Unit
    ) {
        if (!hasCameraPermission(context)) {
            onCaptured(IntruderCaptureResult(null, null, "Camera permission denied — timestamp logged"))
            return
        }
        if (!hasFrontCameraFeature(context)) {
            onCaptured(IntruderCaptureResult(null, null, "No front camera hardware — timestamp logged"))
            return
        }

        val cameraProviderFuture = runCatching {
            ProcessCameraProvider.getInstance(context)
        }.getOrElse { err ->
            Log.w(TAG, "Camera provider unavailable: ${err.message}")
            onCaptured(IntruderCaptureResult(null, null, "Camera unavailable — timestamp logged"))
            return
        }

        val mainExecutor = ContextCompat.getMainExecutor(context)
        cameraProviderFuture.addListener({
            var provider: ProcessCameraProvider? = null
            try {
                provider = cameraProviderFuture.get()
                if (!provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    onCaptured(IntruderCaptureResult(null, null, "Front camera unavailable — timestamp logged"))
                    return@addListener
                }

                // Silent capture: zero flash, zero Preview use case, zero shutter sound
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .build()

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_FRONT_CAMERA,
                    imageCapture
                )

                val boundProvider = provider
                imageCapture.takePicture(
                    ioExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            try {
                                val bytes = imageProxyToRotatedJpeg(image)
                                val encryptedName = "intruder_${UUID.randomUUID()}.enc"
                                val outDir = File(context.filesDir, "intruder_encrypted").apply { mkdirs() }
                                VaultCryptoManager.encryptToFile(bytes, File(outDir, encryptedName))

                                val galleryUri = savePhotoToPublicGallery(context, bytes)
                                mainExecutor.execute { runCatching { boundProvider.unbindAll() } }

                                val note = if (galleryUri != null) {
                                    "Captured & saved to encrypted vault and Pictures/$GALLERY_ALBUM_NAME"
                                } else {
                                    "Captured & saved to encrypted sandbox"
                                }
                                onCaptured(IntruderCaptureResult(encryptedName, galleryUri, note))
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed saving intruder selfie", e)
                                mainExecutor.execute { runCatching { boundProvider.unbindAll() } }
                                onCaptured(IntruderCaptureResult(null, null, "Camera save fallback — timestamp logged"))
                            } finally {
                                image.close()
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.w(TAG, "Intruder capture error: ${exception.message}")
                            mainExecutor.execute { runCatching { boundProvider.unbindAll() } }
                            onCaptured(IntruderCaptureResult(null, null, "Camera busy or blocked — timestamp logged"))
                        }
                    }
                )
            } catch (e: Exception) {
                Log.w(TAG, "CameraX intruder capture fallback: ${e.message}")
                runCatching { provider?.unbindAll() }
                onCaptured(IntruderCaptureResult(null, null, "Camera hardware busy — timestamp logged"))
            }
        }, mainExecutor)
    }

    fun buildTimestampedFilename(timestampMs: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date(timestampMs))
        return "SmartAi_Locker_$stamp.jpg"
    }

    private fun savePhotoToPublicGallery(context: Context, jpegBytes: ByteArray): String? {
        return runCatching {
            val nowMs = System.currentTimeMillis()
            val fileName = buildTimestampedFilename(nowMs)
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.DATE_ADDED, nowMs / 1000L)
                put(MediaStore.Images.Media.DATE_MODIFIED, nowMs / 1000L)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.DATE_TAKEN, nowMs)
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/$GALLERY_ALBUM_NAME"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }

            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return@runCatching null

            resolver.openOutputStream(uri)?.use { out ->
                out.write(jpegBytes)
                out.flush()
            } ?: run {
                resolver.delete(uri, null, null)
                return@runCatching null
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val publish = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, publish, null, null)
            }
            uri.toString()
        }.onFailure { err ->
            Log.w(TAG, "Gallery MediaStore write skipped cleanly: ${err.message}")
        }.getOrNull()
    }

    private fun imageProxyToRotatedJpeg(image: ImageProxy): ByteArray {
        val buffer = image.planes[0].buffer
        val raw = ByteArray(buffer.remaining())
        buffer.get(raw)
        val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return raw
        val rotation = image.imageInfo.rotationDegrees.toFloat()
        val matrix = Matrix().apply {
            if (rotation != 0f) postRotate(rotation)
            postScale(-1f, 1f) // mirror front selfie naturally
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        val out = ByteArrayOutputStream()
        transformed.compress(Bitmap.CompressFormat.JPEG, 86, out)
        if (transformed != bitmap) transformed.recycle()
        bitmap.recycle()
        return out.toByteArray()
    }
}
