package com.example.application

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.*
import com.example.application.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class GLPDepthHelper(private val assetManager: AssetManager) : ImageProcessor {

    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession

    init {
        val modelFile = copyModelToCache("glpdepth_nyu.onnx")
        session = ortEnv.createSession(modelFile.absolutePath, OrtSession.SessionOptions())
    }

    override suspend fun process(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val input = preprocess(bitmap)
        val inputName = session.inputNames.iterator().next()
        val inputTensor = OnnxTensor.createTensor(ortEnv, input)

        val start = System.nanoTime()
        val result = session.run(mapOf(inputName to inputTensor))
        val end = System.nanoTime()
        android.util.Log.d("GLPDepth", "Inference: ${(end - start) / 1_000_000} ms")

        @Suppress("UNCHECKED_CAST")
        val output2d = (result[0].value as Array<Array<Array<FloatArray>>>)[0][0]  // [h][w] raw depth


        // 모든 픽셀의 거리를 로그에 출력
        val h = output2d.size
        val w = output2d[0].size
        android.util.Log.d("GLPDepth", "depth[${h/2}][${w/2}]=${output2d[h/2][w/2]}")
        val maxValue: Float? = output2d
            .flatMap { row -> row.asIterable() }  // FloatArray → Iterable<Float>
            .maxOrNull()
        android.util.Log.d("GLPDepth", "max depth=${maxValue}")


        postprocess(output2d)
    }

suspend fun detect(bitmap: Bitmap): Array<FloatArray> = withContext(Dispatchers.Default) {
    val input = preprocess(bitmap)
    val inputName = session.inputNames.iterator().next()
    val inputTensor = OnnxTensor.createTensor(ortEnv, input)

    val start = System.nanoTime()
    val result = session.run(mapOf(inputName to inputTensor))
    val end = System.nanoTime()
    android.util.Log.d("GLPDepth", "Inference: ${(end - start) / 1_000_000} ms")

    @Suppress("UNCHECKED_CAST")
    val rawOutput = (result[0].value as Array<Array<Array<FloatArray>>>)[0][0]  // [h][w]

    // 5/8 배 스케일 조정
    val h = rawOutput.size
    val w = rawOutput[0].size
    val scaledOutput = Array(h) { y ->
        FloatArray(w) { x ->
            rawOutput[y][x] * 2f / 8f
        }
    }

    // 로그 출력 (중앙 픽셀 및 max 값)
    android.util.Log.d("GLPDepth", "depth[${h / 2}][${w / 2}] = ${scaledOutput[h / 2][w / 2]}")
    val maxValue = scaledOutput.flatMap { it.asIterable() }.maxOrNull()
    android.util.Log.d("GLPDepth", "max depth = $maxValue")

    scaledOutput
}

    private fun preprocess(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        // 모델 입력 해상도 832×256
        val resized = Bitmap.createScaledBitmap(bitmap, 320, 320, true)
        val floatValues = Array(1) { Array(3) { Array(320) { FloatArray(320) } } }
        for (y in 0 until 320) {
            for (x in 0 until 320) {
                val p = resized.getPixel(x, y)
                floatValues[0][0][y][x] = (Color.red(p)   / 255f - 0.485f) / 0.229f
                floatValues[0][1][y][x] = (Color.green(p) / 255f - 0.456f) / 0.224f
                floatValues[0][2][y][x] = (Color.blue(p)  / 255f - 0.406f) / 0.225f
            }
        }
        return floatValues
    }

    private fun postprocess(output: Array<FloatArray>): Bitmap {
        val h = output.size
        val w = output[0].size
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        val allValues: List<Float> = output.flatMap { row -> row.toList() }
        val max = allValues.maxOrNull() ?: 1f
        val min = allValues.minOrNull() ?: 0f

        for (y in 0 until h) {
            for (x in 0 until w) {
                val v = ((output[y][x] - min) / (max - min) * 255).toInt()
                bmp.setPixel(x, y, Color.rgb(v, v, v))
            }
        }
        return bmp
    }

    private fun copyModelToCache(filename: String): File {
        val isr = assetManager.open(filename)
        val out = File.createTempFile("glpdepth", ".onnx")
        FileOutputStream(out).use { os -> isr.copyTo(os) }
        isr.close()
        return out
    }

    override fun close() {
        session.close()
        ortEnv.close()
    }
}
