
// File: MidasHelper.kt
package com.example.application

import android.content.res.AssetManager
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class MidasHelper(assetManager: AssetManager, modelPath: String = "midas-midas-v2-float.tflite") : Closeable {
    private val tflite: Interpreter

    init {
        val fd = assetManager.openFd(modelPath)
        FileInputStream(fd.fileDescriptor).channel.use { channel ->
            val buf = channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            tflite = Interpreter(buf)
        }
    }

    // File: MidasHelper.kt 에 추가
    suspend fun process(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        // 1) 상대 깊이 추정
        val depthMap = estimateDepth(bitmap, 256)
        // 2) 시각화
        val mat = visualizeMqp(depthMap)
        // 3) Bitmap으로 반환
        BitmapUtils.matToBitmap(mat)
    }

    /**
     * 원본 bitmap -> 모델 입력(256×256) -> 추론 -> outputSize×outputSize 2D 배열 반환
     */
    suspend fun estimateDepth(bitmap: Bitmap, outputSize: Int): Array<FloatArray> = withContext(Dispatchers.Default) {
        val inputBmp = Bitmap.createScaledBitmap(bitmap, 256, 256, true)
        val input = ByteBuffer.allocateDirect(1 * 3 * 256 * 256 * 4).order(ByteOrder.nativeOrder()).apply {
            val pixels = IntArray(256 * 256)
            inputBmp.getPixels(pixels, 0, 256, 0, 0, 256, 256)
            pixels.forEach { p ->
                putFloat((p shr 16 and 0xFF) / 255f)
                putFloat((p shr  8 and 0xFF) / 255f)
                putFloat((p        and 0xFF) / 255f)
            }
            rewind()
        }
        val output = TensorBuffer.createFixedSize(intArrayOf(1,1,256,256), DataType.FLOAT32)
        tflite.run(input, output.buffer.rewind())
        val raw = output.floatArray
        Array(outputSize) { y ->
            FloatArray(outputSize) { x ->
                val yy = y * 256 / outputSize
                val xx = x * 256 / outputSize
                raw[yy * 256 + xx]
            }
        }
    }

    override fun close() { tflite.close() }
}
