package com.example.application

fun resizeBBoxes(bboxes: List<List<Int>>, originalSize: Int, targetSize: Int): List<List<Int>> {
    val scale = targetSize.toFloat() / originalSize
    return bboxes.map { bbox ->
        bbox.map { coord -> (coord * scale).toInt() }
    }
}

// 실행용 main 함수
fun main() {
    // 원본 BBox 리스트
    val originalBBoxes = listOf(
        listOf(50, 60, 200, 220),
        listOf(30, 40, 100, 120)
    )
    // ex: 원본 이미지 크기 640 → 타겟 크기 384
    val originalSize = 640
    val targetSize   = 384

    val resizedBBoxes = resizeBBoxes(originalBBoxes, originalSize, targetSize)
    println("Resized BBoxes: $resizedBBoxes")
}
