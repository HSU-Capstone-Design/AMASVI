package com.example.application

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService

class CameraManager(
    private val context: Context,
    private val previewView: PreviewView,
    private val executor: ExecutorService,
    private val onImageAvailable: (ImageProxy) -> Unit
) {
    fun startCamera(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Preview에 1:1 해상도 지정
            val preview = Preview.Builder()
                .setTargetResolution(Size(640, 640))       // 1:1 해상도로 고정
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            // ImageAnalysis에도 1:1 해상도 지정
            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 640))       // 1:1 해상도로 고정
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor) { imageProxy ->
                    onImageAvailable(imageProxy)
                }}

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )
        }, ContextCompat.getMainExecutor(context))
    }
}
