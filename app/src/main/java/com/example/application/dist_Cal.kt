package com.example.application

fun dist_Cal(
    depthMap: Array<FloatArray>,
    bbox: List<Int>,
    gridSize: Int = 3
): Pair<Float, String>? {
    val imgCenter = depthMap[0].size / 2
    val (x1, y1, x2, y2) = bbox
    var minDistance: Float? = null

    for (i in y1 until y2 step gridSize) {
        for (j in x1 until x2 step gridSize) {
            val d = depthMap[i][j]
            if (minDistance == null || d < minDistance) {
                minDistance = d
            }
        }
    }
    return minDistance?.let {
        val centerX = (x1 + x2) / 2
        val third = depthMap[0].size / 3
        val side = when {
            centerX < third           -> "L"
            centerX < third * 2       -> "F"  // Center (전방)
            else                      -> "R"
        }
        Pair(it, side)
    }
}
