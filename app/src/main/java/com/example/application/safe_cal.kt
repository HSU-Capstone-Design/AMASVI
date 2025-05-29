package com.example.application

fun isSafe(
    depthMap: Array<FloatArray>,
    safeMap: Array<FloatArray>,
    bbox: List<List<Int>>,
    gridSize: Int = 3
): List<Triple<Int, Float, String>> {
    val imgCenter = depthMap[0].size / 2
    var closest: Triple<Int, Float, String>? = null

    for ((idx, box) in bbox.withIndex()) {
        val (x1, y1, x2, y2) = box
        var minDist: Float? = null
        for (i in y1 until y2 step gridSize) {
            for (j in x1 until x2 step gridSize) {
                val d = depthMap[i][j]
                if (d < safeMap[i][j] && (minDist == null || d < minDist)) {
                    minDist = d
                }
            }
        }
        if (minDist != null) {
            if (minDist > 3){
                return emptyList()
            }
            val centerX = (x1 + x2) / 2
            val third = depthMap[0].size / 3
            val side = when {
                centerX < third           -> "L"
                centerX < third * 2       -> "F"  // Center (전방)
                else                      -> "R"
            }
            val candidate = Triple(idx, minDist, side)
            if (closest == null || candidate.second < closest.second) {
                closest = candidate
            }
        }
    }

    return closest?.let { listOf(it) } ?: emptyList()
}
