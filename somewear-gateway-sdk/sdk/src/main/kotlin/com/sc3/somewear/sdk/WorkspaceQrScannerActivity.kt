package com.sc3.somewear.sdk

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Size
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** CameraX/ML Kit scanner packaged inside the SDK; SC3 does not need ATAK UI. */
public class WorkspaceQrScannerActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var statusView: TextView
    private lateinit var analysisExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner
    private val completed = AtomicBoolean(false)
    private var cameraProvider: ProcessCameraProvider? = null

    private val permissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startCamera()
        } else {
            finishWithError("Camera permission is required to scan a Somewear workspace invite")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analysisExecutor = Executors.newSingleThreadExecutor()
        barcodeScanner = BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
        setContentView(buildContentView())

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            permissionRequest.launch(Manifest.permission.CAMERA)
        }
    }

    private fun buildContentView(): FrameLayout {
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
            textSize = 17f
            gravity = Gravity.CENTER
            setPadding(32, 28, 32, 28)
            text = "Point the camera at a Somewear workspace QR code"
        }
        val cancel = Button(this).apply {
            text = "Cancel"
            setOnClickListener {
                if (completed.compareAndSet(false, true)) {
                    setResult(Activity.RESULT_CANCELED)
                    finish()
                }
            }
        }
        return FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(
                previewView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                statusView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP,
                ),
            )
            addView(
                cancel,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL,
                ).apply { bottomMargin = 48 },
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun startCamera() {
        statusView.text = "Starting camera…"
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener(
            {
                if (isFinishing || isDestroyed) return@addListener
                runCatching {
                    val provider = providerFuture.get()
                    cameraProvider = provider
                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { useCase ->
                            useCase.setAnalyzer(analysisExecutor, ::analyze)
                        }
                    provider.unbindAll()
                    val selector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    }
                    provider.bindToLifecycle(this, selector, preview, analysis)
                    statusView.text = "Point the camera at a Somewear workspace QR code"
                }.onFailure {
                    finishWithError("Could not start the camera: ${it.message ?: "unknown error"}")
                }
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    @ExperimentalGetImage
    private fun analyze(imageProxy: ImageProxy) {
        if (completed.get()) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }
        val image = InputImage.fromMediaImage(
            mediaImage,
            imageProxy.imageInfo.rotationDegrees,
        )
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val invite = barcodes.asSequence()
                    .mapNotNull(Barcode::getRawValue)
                    .firstOrNull { WorkspaceInviteCode.inspect(it) != null }
                if (invite != null && completed.compareAndSet(false, true)) {
                    setResult(
                        Activity.RESULT_OK,
                        Intent().putExtra(WorkspaceQrScanContract.EXTRA_INVITE_CODE, invite),
                    )
                    finish()
                } else if (barcodes.isNotEmpty()) {
                    statusView.text = "That QR code is not a Somewear workspace invite"
                }
            }
            .addOnFailureListener {
                statusView.text = "Could not read that QR code. Hold the camera steady and retry."
            }
            .addOnCompleteListener { imageProxy.close() }
    }

    private fun finishWithError(message: String) {
        if (!completed.compareAndSet(false, true)) return
        setResult(
            Activity.RESULT_CANCELED,
            Intent().putExtra(WorkspaceQrScanContract.EXTRA_ERROR, message),
        )
        finish()
    }

    override fun onDestroy() {
        cameraProvider?.unbindAll()
        if (::barcodeScanner.isInitialized) barcodeScanner.close()
        if (::analysisExecutor.isInitialized) analysisExecutor.shutdownNow()
        super.onDestroy()
    }
}
