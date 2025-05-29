package com.example.application

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.ViewConfiguration
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import org.opencv.android.OpenCVLoader
import java.util.concurrent.Executors

/**
 * MainActivity: 카메라 프레임 분석, STT/LLM/TTS 통합 로직
 * - 볼륨다운 롱프레스: STT 시작
 * - GLPDepthHelper: ONNX 기반 깊이맵 생성 및 절대 거리 변환
 * - YoloHelper: 객체 탐지
 * - MlKitRecognitionHelper: OCR 텍스트 인식
 */
class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_PERMISSIONS = 10
        private const val GLP_SIZE = 320
        private const val YOLO_SIZE = 640
        private const val OCR_SIZE = 640
    }

    private lateinit var previewView: PreviewView
    private lateinit var depthView: ImageView
    private lateinit var overlayView: ImageView
    private lateinit var cameraManager: CameraManager
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var speechManager: STTManager
    private lateinit var ttsManager: TTsManager
    private lateinit var llmManager: LLMManager

    private lateinit var depthHelper: GLPDepthHelper
    private lateinit var yoloHelper: YoloHelper
    private lateinit var ocrHelper: MlKitRecognitionHelper

    private lateinit var audioManager: AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private val longPressTimeout = ViewConfiguration.getLongPressTimeout().toLong()
    private var longPressTriggered = false

    private lateinit var safeMap: Array<FloatArray>
    private var keywords: String = "None"
    private var isTtsBusy = false

    private var ttsReady = false
    private var permissionGranted = false

    private val knownPoints = listOf(
        Triple(128, 0, 7.0),
        Triple(128, 255, 1.75)
    )

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        sttStartTime = System.currentTimeMillis()
        Toast.makeText(this, "음성인식이 시작되었습니다", Toast.LENGTH_SHORT).show()
        speechManager.startSpeechRecognition()
    }

    private var sttStartTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!OpenCVLoader.initDebug())
            Toast.makeText(this, "OpenCV 초기화 실패", Toast.LENGTH_SHORT).show()
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        depthView = findViewById(R.id.depthView)
        overlayView = findViewById(R.id.overlayView)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        depthHelper = GLPDepthHelper(assets)
        yoloHelper = YoloHelper(this)
        ocrHelper = MlKitRecognitionHelper(this)

        llmManager = LLMManager()
        ttsManager = TTsManager(this).apply {
            setOnInitListener {
                ttsReady = true
                if (permissionGranted) initDepthAndCamera()
            }
            init()
        }

        speechManager = STTManager(
            this,
            onTextResult = { text ->
                sttStartTime = System.currentTimeMillis()
                Toast.makeText(this, "인식결과: $text", Toast.LENGTH_SHORT).show()
                llmManager.fetchKeyword(text) { kw -> keywords = kw }
            },
            onError = { code ->
                Toast.makeText(this, "STT 에러: $code", Toast.LENGTH_SHORT).show()
            }
        ).apply { initSpeechRecognizer() }


        requestPermissionsIfNeeded()
    }

    private fun initDepthAndCamera() {
        safeMap = makeMap(GLP_SIZE)
        startCameraAndAnalysis()
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.CAMERA)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO)

        if (needed.isNotEmpty()) requestPermissions(this, needed.toTypedArray(), REQUEST_PERMISSIONS)
        else {
            permissionGranted = true
            if (ttsReady) initDepthAndCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            permissionGranted = true
            if (ttsReady) initDepthAndCamera()
        } else Toast.makeText(this, "카메라 및 마이크 권한 필요", Toast.LENGTH_SHORT).show()
    }

    private fun startCameraAndAnalysis() {
        cameraManager = CameraManager(this, previewView, cameraExecutor) { imageProxy ->
            val bmp = BitmapUtils.imageProxyToBitmap(imageProxy)
            val forYolo = BitmapUtils.resizeBitmap(bmp, YOLO_SIZE, YOLO_SIZE)
            val forOCR = BitmapUtils.resizeBitmap(bmp, OCR_SIZE, OCR_SIZE)
            bgScope.launch {
                // 1) 깊이 맵 추정 및 절대 거리 변환
                val depthMap = depthHelper.detect(bmp)

                // 2) 객체 탐지
                val yoloBboxes = yoloHelper.detect(forYolo)
                val GLPBboxes = resizeBBoxes(yoloBboxes, YOLO_SIZE, GLP_SIZE)

                // 3) 안전 영역 검사
                val unsafeList = isSafe(depthMap, safeMap, GLPBboxes)
                if (unsafeList.isNotEmpty()) {
                    uiScope.launch {
                        overlayView.post {
                            drawModeBoxes(
                                overlayView, GLPBboxes,
                                unsafeList.map { it.first },
                                DrawMode.HAZARD,
                                GLP_SIZE)
                        }
                    }
                }

                if (unsafeList.isNotEmpty()) {
                    val (_, dist, side) = unsafeList.first()
                    if (dist > 0f && !isTtsBusy) {
                        isTtsBusy = true
                        ttsManager.playInstruction(side, dist, "장애물") { isTtsBusy = false }
                        delay(1000L)   // 500ms 딜레이
                    }
                } else if (keywords != "None") {
                    // 4) OCR 및 키워드 매칭
                    val (ocrBoxes, ocrTexts) = ocrHelper.recognize(forOCR)
                    llmManager.fetchMatchOcr(ocrTexts, keywords) { idx, txt ->
                        if (idx >= 0) {
                            val target = resizeBBoxes(listOf(ocrBoxes[idx]), OCR_SIZE, GLP_SIZE)[0]
                            val (dist2, side2) = dist_Cal(depthMap, target)!!
                            if (!isTtsBusy) {
                                isTtsBusy = true
                                ttsManager.playInstruction(side2, dist2, txt ?: keywords) { isTtsBusy = false }

                            }
                            uiScope.launch {
                                delay(1000L)   // 1000ms 딜레이
                                overlayView.post {
                                    drawModeBoxes(overlayView, listOf(target), listOf(0), DrawMode.RELATED, GLP_SIZE, txt)
                                    Toast.makeText(this@MainActivity, "[${"%.2f".format(dist2)} m, $side2] ${txt ?: "N/A"}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                }

                imageProxy.close()
            }
        }
        cameraManager.startCamera(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) handler.postDelayed(longPressRunnable, longPressTimeout)
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    return if (longPressTriggered) {
                        speechManager.stopSpeechRecognition(); true
                    } else {
                        audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                            AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI); true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        speechManager.destroy()
        ttsManager.shutdown()
        uiScope.cancel(); bgScope.cancel()
        depthHelper.close(); yoloHelper.close(); ocrHelper.close()
    }
}






















