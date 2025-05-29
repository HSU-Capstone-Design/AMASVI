package com.example.application

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Color
import android.util.Log
import android.widget.ImageView
import androidx.core.graphics.createBitmap

/**
 * 그리기 모드: 위험 또는 연관
 */
enum class DrawMode { HAZARD, RELATED }

/**
 * 지정된 박스 인덱스만 해당 모드 색상으로 오버레이에 그립니다.
 *
 * @param overlayView  ImageView에 그릴 비트맵을 설정합니다
 * @param allBoxes     전체 바운딩박스 리스트 (각 [x1,y1,x2,y2])
 * @param indices      그릴 박스 인덱스 목록
 * @param mode         DrawMode.HAZARD -> 빨간색, DrawMode.RELATED -> 파란색
 * @param size         캔버스 너비/높이 (픽셀)
 * @param text         위험경고 메세지 or 연관된 OCR결과
 */
fun drawModeBoxes(
    overlayView: ImageView,
    allBoxes: List<List<Int>>,
    indices: List<Int>,
    mode: DrawMode,
    size: Int,
    text: String? = null  // 단일 텍스트
) {
    // 1) 빈 비트맵 & 캔버스
    val bmp = createBitmap(size, size)
    val canvas = Canvas(bmp)

    // 2) 박스 그리기용 페인트
    val boxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = when (mode) {
            DrawMode.HAZARD -> Color.RED
            DrawMode.RELATED -> Color.BLUE
        }
    }

    // 3) 텍스트용 페인트
    val textPaint = Paint().apply {
        style = Paint.Style.FILL
        textSize = 36f
        color = boxPaint.color
        isAntiAlias = true
    }

    Log.d("MLKitOCR", "BBox=$allBoxes, Text='${indices}'")
    // 4) 박스와 텍스트 그리기
    indices.forEach { idx ->
        val (x1, y1, x2, y2) = allBoxes[idx]

        // 사각형 그리기
        canvas.drawRect(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat(), boxPaint)

        // 텍스트 결정
        val label = when (mode) {
            DrawMode.HAZARD -> "warning"
            DrawMode.RELATED -> text
        }

        // 텍스트 그리기
        label?.let {
            val textX = x1.toFloat()
            val textY = (y1.toFloat() - 10f).coerceAtLeast(30f)
            canvas.drawText(it, textX, textY, textPaint)
        }
    }

    // 5) ImageView에 비트맵 설정
    overlayView.post {
        overlayView.setImageBitmap(bmp)
    }
}
