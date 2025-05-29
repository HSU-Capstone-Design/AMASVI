package com.example.application

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.example.application.ImageProcessor
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class MlKitRecognitionHelper(private val context: Context) : ImageProcessor {
    private val recognizer = TextRecognition.getClient(
        KoreanTextRecognizerOptions.Builder().build()
    )

    override suspend fun process(bitmap: Bitmap): Bitmap {
        val input = InputImage.fromBitmap(bitmap, 0)
        val start = System.currentTimeMillis()
        val result: Text = recognizer.process(input).await()
        Log.d("MLKitOCR", "Inference time: ${System.currentTimeMillis()-start}ms")

        val out = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val paintBox = Paint().apply { color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 3f }
        val paintText = Paint().apply { color = Color.YELLOW; textSize = 40f; style = Paint.Style.FILL }

        result.textBlocks.flatMap { it.lines }.forEach { line ->
            line.boundingBox?.let { r ->
                canvas.drawRect(r, paintBox)
                canvas.drawText(line.text, r.left.toFloat(), r.top - 10f, paintText)
            }
        }
        return out
    }

    /** OCR 박스와 텍스트 리스트를 반환하는 새 메서드 */
    suspend fun recognize(bitmap: Bitmap): Pair<List<List<Int>>, List<String>> = withContext(Dispatchers.Default) {
        val input = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer.process(input).await()

        val boxes = mutableListOf<List<Int>>()
        val texts = mutableListOf<String>()

        result.textBlocks.flatMap { it.lines }.forEach { line ->
            line.boundingBox?.let { r ->
                boxes += listOf(r.left, r.top, r.right, r.bottom)
                texts += line.text
            }
        }
        Pair(boxes, texts)
    }


    override fun close() {
        recognizer.close()
    }
}
