// File: rel2abs.kt
package com.example.application

import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Convert MiDaS relative depth map (2D Float array) to absolute depth using known real points.
 *
 * @param midasDepthArray  2D array of relative depth values from MiDaS (unnormalized).
 * @param knownPoints      List of (x, y, realDepth) where realDepth is in meters.
 * @return                 2D array of absolute depth values (in meters), or null on failure.
 */
fun convertRelativeToAbsoluteDepth(
    midasDepthArray: Array<FloatArray>,
    knownPoints: List<Triple<Int, Int, Double>>
): Array<FloatArray> {
    val height = midasDepthArray.size
    val width = midasDepthArray[0].size

    // 1. Normalize to 0...1
    var maxVal = Float.NEGATIVE_INFINITY
    for (row in midasDepthArray) {
        for (value in row) {
            maxVal = max(maxVal, value)
        }
    }
    if (maxVal <= 0f) {
        Log.d("PositionCheck", "Invalid depth map: all values are zero or negative. Returning default depth map.")
        return Array(height) { FloatArray(width) { 0.0f } }
    }

    val normalized = Array(height) { y ->
        FloatArray(width) { x -> midasDepthArray[y][x] / maxVal }
    }

    // 2. 최소 두 개의 known point 필요
    if (knownPoints.size < 2) {
        Log.d("PositionCheck","Not enough known points to compute absolute depth.")
        return Array(height) { FloatArray(width) { 0.0f } }
    }

    // 3. knownPoints로부터 A, y 행렬 구성
    val A = Array(knownPoints.size) { DoubleArray(2) }
    val yVec = DoubleArray(knownPoints.size)
    for ((i, point) in knownPoints.withIndex()) {
        val (x, y, realDepth) = point
        val rel = normalized[y][x].toDouble()
        A[i][0] = rel
        A[i][1] = 1.0 - rel
        yVec[i] = 1.0 / realDepth
    }

    // 4. Least squares 해법으로 Ax = y 푸는 과정
    val AtA = Array(2) { DoubleArray(2) }
    val Aty = DoubleArray(2)
    for (i in knownPoints.indices) {
        AtA[0][0] += A[i][0] * A[i][0]
        AtA[0][1] += A[i][0] * A[i][1]
        AtA[1][0] += A[i][1] * A[i][0]
        AtA[1][1] += A[i][1] * A[i][1]
        Aty[0] += A[i][0] * yVec[i]
        Aty[1] += A[i][1] * yVec[i]
    }

    val det = AtA[0][0] * AtA[1][1] - AtA[0][1] * AtA[1][0]
    if (abs(det) < 1e-6) {
        Log.d("PositionCheck", "Matrix is near-singular; cannot solve.")
        return Array(height) { FloatArray(width) { 0.0f } }
    }

    val s = (Aty[0] * AtA[1][1] - Aty[1] * AtA[0][1]) / det
    val t = (AtA[0][0] * Aty[1] - AtA[1][0] * Aty[0]) / det

    val minDepth = 1.0 / s
    val maxDepth = 1.0 / t

    val A_param = (1.0 / minDepth) - (1.0 / maxDepth)
    val B_param = 1.0 / maxDepth

    // 5. 절대 깊이 계산
    val absDepth = Array(height) { y ->
        FloatArray(width) { x ->
            val rel = normalized[y][x].toDouble()
            val real = 1.0 / (A_param * rel + B_param)
            real.toFloat()
        }
    }

    return absDepth
}
