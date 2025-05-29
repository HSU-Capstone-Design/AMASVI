// YoloHelper.kt
package com.example.application

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.example.application.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/**
 * GPU Delegate를 사용한 Anchor-free YOLOv11-Large TFLite 추론 헬퍼입니다.
 * MainActivity에서 `processor = YoloHelper(this)`로 초기화하세요.
 */
class YoloHelper(private val context: Context) : ImageProcessor, Closeable {
    companion object {
        private const val TAG = "YoloHelper"
        private const val INPUT_SIZE  = 640
        private const val NUM_CLASSES = 13
        private const val CONF_THRESH = 0.01f
        private const val NMS_THRESH  = 0.2f
        private const val TOTAL_CELLS = 8400

        /** 시그모이드 활성화 함수 */
        private fun sigmoid(x: Float) = 1f / (1f + exp(-x))

        /** 두 박스 간 IoU 계산 */
        private fun iou(a: RectF, b: RectF): Float {
            val areaA = max(0f, a.width())  * max(0f, a.height())
            val areaB = max(0f, b.width())  * max(0f, b.height())
            val left   = max(a.left,   b.left)
            val top    = max(a.top,    b.top)
            val right  = min(a.right,  b.right)
            val bottom = min(a.bottom, b.bottom)
            val w = max(0f, right  - left)
            val h = max(0f, bottom - top)
            val inter = w * h
            return if (areaA + areaB - inter > 0f)
                inter / (areaA + areaB - inter)
            else 0f
        }
    }

    // GPU Delegate를 통한 가속화
    private val gpuDelegate by lazy { GpuDelegate() }

    // TFLite 인터프리터 초기화
    private val interpreter: Interpreter by lazy {
        val afd = context.assets.openFd("YOLObest_sim_float32.tflite")
        val modelBuffer = FileInputStream(afd.fileDescriptor).channel.use { channel ->
            channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
        }
        val options = Interpreter.Options().apply { addDelegate(gpuDelegate) }
        Interpreter(modelBuffer, options)
    }

    /**
     * 검출 결과 데이터 클래스
     */
    data class Detection(val label: String, val classIndex: Int, val score: Float, val bbox: RectF)

    // 클래스 이름 배열 (실제 이름으로 교체 가능)
    private val labels = Array(NUM_CLASSES) { i -> "Class$i" }

    override suspend fun process(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val dets = detect(bitmap)
        val uck = listOf(Detection("ck", 1, 0.8f, RectF(1f,2f,3f,4f)))
        drawDetections(bitmap, uck)
    }

    /**
     * 추론 및 후처리 (상세 로그 포함)
     */
    suspend fun detect(orig: Bitmap): List<List<Int>> = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "==== detect() 시작 ====")
//            Log.d(TAG, "원본 이미지 크기: ${orig.width}x${orig.height}")

            // 1) Letterbox 방식 resize
            val ow = orig.width.toFloat()
            val oh = orig.height.toFloat()
            val scale = min(INPUT_SIZE / ow, INPUT_SIZE / oh)
            val nw = (ow * scale).toInt()
            val nh = (oh * scale).toInt()
            val padX = (INPUT_SIZE - nw) / 2f
            val padY = (INPUT_SIZE - nh) / 2f
//            Log.d(TAG, "리사이즈→ $nw x $nh, 패딩→($padX, $padY)")

            val resized = Bitmap.createScaledBitmap(orig, nw, nh, true)
            val letter = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
            Canvas(letter).apply {
                drawColor(Color.BLACK)
                drawBitmap(resized, padX, padY, null)
            }

            // 2) ByteBuffer에 입력 준비
            val inputBuf = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
                .order(ByteOrder.nativeOrder()).apply {
                    val px = IntArray(INPUT_SIZE * INPUT_SIZE)
                    letter.getPixels(px, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
                    px.forEach { p ->
                        putFloat(((p shr 16) and 0xFF) / 255f)
                        putFloat(((p shr 8) and 0xFF) / 255f)
                        putFloat((p and 0xFF) / 255f)
                    }
                    rewind()
                }
//            Log.d(TAG, "입력 버퍼 준비 완료 (limit=${inputBuf.limit()})")

            // 3) 추론 수행
            val output = Array(1) { Array(4 + NUM_CLASSES) { FloatArray(TOTAL_CELLS) } }
            val t0 = SystemClock.elapsedRealtime()
            interpreter.run(inputBuf, output)
            val infTime = SystemClock.elapsedRealtime() - t0
            Log.d(TAG, "추론 시간: ${infTime}ms")

            // 4) 디코딩
            val raw = output[0]
            val dets = mutableListOf<Detection>()
            for (i in 0 until TOTAL_CELLS) {
                val classProbs = FloatArray(NUM_CLASSES) { c -> raw[4 + c][i] }
                val maxResult = classProbs.withIndex().maxByOrNull { it.value } ?: continue
                val bestC = maxResult.index
                val bestP = maxResult.value
                if (bestP < CONF_THRESH) continue

                val cx = raw[0][i]
                val cy = raw[1][i]
                val w = raw[2][i]
                val h = raw[3][i]
                val x1 = cx - w / 2f
                val y1 = cy - h / 2f
                val x2 = cx + w / 2f
                val y2 = cy + h / 2f

                val ox1 = ((x1 - padX) / scale).coerceIn(0f, ow)
                val oy1 = ((y1 - padY) / scale).coerceIn(0f, oh)
                val ox2 = ((x2 - padX) / scale).coerceIn(0f, ow)
                val oy2 = ((y2 - padY) / scale).coerceIn(0f, oh)

                dets += Detection(labels[bestC], bestC, bestP, RectF(ox1, oy1, ox2, oy2))
            }

//            Log.d(TAG, "디코딩 후 후보 개수: ${dets.size}")

            // 5) NMS
            val sorted = dets.sortedByDescending { it.score }.toMutableList()
            val finalDets = mutableListOf<Detection>()
            while (sorted.isNotEmpty()) {
                val d = sorted.removeAt(0)
                finalDets += d
                sorted.removeAll { iou(d.bbox, it.bbox) > NMS_THRESH }
            }

            Log.d(TAG, "NMS 후 최종 검출 개수: ${finalDets.size}")
//            finalDets.forEach { d ->
//                Log.d(TAG, "  → ${d.label} (${String.format("%.2f", d.score)}) at ${d.bbox}")
//            }
            Log.d(TAG, "==== detect() 종료 ====")

            finalDets.map { det ->
                listOf(det.bbox.left.toInt(), det.bbox.top.toInt(), det.bbox.right.toInt(), det.bbox.bottom.toInt())
            }
        } catch (e: Exception) {
            Log.e(TAG, "detect() 중 예외 발생", e)
            emptyList()
        }
    }

    /**
     * 검출 결과를 비트맵에 그려 반환
     */
    private fun drawDetections(src: Bitmap, dets: List<Detection>): Bitmap {
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paintBox = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val paintText = Paint().apply {
            style = Paint.Style.FILL
            textSize = 36f
        }
        dets.forEach { d ->
            val col = Color.rgb(
                (d.classIndex * 40) % 256,
                (d.classIndex * 80) % 256,
                (d.classIndex *120) % 256
            )
            paintBox.color = col
            paintText.color = col
            canvas.drawRect(d.bbox, paintBox)
            canvas.drawText("${d.label} ${(d.score * 100).toInt()}%", d.bbox.left, d.bbox.top - 8f, paintText)
        }
        return out
    }

    /**
     * 리소스 해제
     */
    override fun close() {
        interpreter.close()
        gpuDelegate.close()
        Log.d(TAG, "Interpreter 및 GPUDelegate 해제 완료")
    }

    // 로그 출력을 위한 포맷 확장 함수
    private fun Float.format(digits: Int) = "%.\${digits}f".format(this)
}


