package com.example.application

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

fun makeMap(n: Int): Array<FloatArray> {
    val centerX = n / 2
    val vanishingY = (n * 0.3).toInt()
    val bottomY = n + vanishingY

    // 물리 파라미터 (단위: m)
    val H = 1.3
    val startZ = 1.75
    val leftWorld = -0.6
    val rightWorld = 0.6
    val wallHeight = 1.7

    val worldWidth = rightWorld - leftWorld
    val scaleX = (bottomY - vanishingY).toDouble() / worldWidth

    fun unprojectFloor(u: Int, v: Int): Triple<Double, Double, Double>? {
        val factor = (v - vanishingY).toDouble() / (bottomY - vanishingY)
        if (factor <= 0) return null
        val z = startZ + (H / factor) - H
        val x = (u - centerX).toDouble() / (scaleX * factor)
        return Triple(x, 0.0, z)
    }

    fun unprojectWall(u: Int, v: Int, wallX: Double): Triple<Double, Double, Double>? {
        val denom = wallX * scaleX
        if (abs(denom) < 1e-9) return null
        val factor = (u - centerX).toDouble() / denom
        if (factor <= 0) return null
        val z = startZ + (H / factor) - H
        val num = (v - vanishingY).toDouble()
        val denom2 = (bottomY - vanishingY) * factor
        if (abs(denom2) < 1e-9) return null
        val y = H - H * (num / denom2)
        return Triple(wallX, y, z)
    }

    val distanceMap = Array(n) { FloatArray(n) }
    for (v in 0 until n) {
        for (u in 0 until n) {
            val candidates = mutableListOf<Double>()
            unprojectFloor(u, v)?.let { (xF, _, zF) ->
                if (xF in leftWorld..rightWorld && zF >= startZ)
                    candidates.add(sqrt(xF * xF + zF * zF))
            }
            unprojectWall(u, v, leftWorld)?.let { (xL, yL, zL) ->
                if (yL in 0.0..wallHeight && zL >= startZ)
                    candidates.add(sqrt(xL * xL + (yL - H) * (yL - H) + zL * zL))
            }
            unprojectWall(u, v, rightWorld)?.let { (xR, yR, zR) ->
                if (yR in 0.0..wallHeight && zR >= startZ)
                    candidates.add(sqrt(xR * xR + (yR - H) * (yR - H) + zR * zR))
            }
            distanceMap[v][u] = candidates.minOrNull()?.toFloat()
                ?: Float.POSITIVE_INFINITY
        }
    }
    return distanceMap
}

fun visualizeMqp(distanceMap: Array<FloatArray>): Mat {
    val n = distanceMap.size

    // 출력용 Mat
    val output = Mat(n, n, CvType.CV_8UC3, Scalar(0.0, 0.0, 0.0))
    val nearNorm = Mat(n, n, CvType.CV_8UC1, Scalar(0.0))
    val midNorm  = Mat(n, n, CvType.CV_8UC1, Scalar(0.0))

    // 거리 맵을 0~255 범위로 정규화
    for (v in 0 until n) {
        for (u in 0 until n) {
            val d = distanceMap[v][u].toDouble()
            if (d > 0 && d <= 10) {
                val value = ((d / 10.0) * 255.0).coerceIn(0.0, 255.0)
                nearNorm.put(v, u, value)     // vararg Double 호출
            }
            if (d > 10 && d <= 150) {
                val value = (((d - 10) / 140.0) * 255.0).coerceIn(0.0, 255.0)
                midNorm.put(v, u, value)      // vararg Double 호출
            }
        }
    }

    // 컬러맵 적용
    val nearColor = Mat()
    val midColor  = Mat()
    Imgproc.applyColorMap(nearNorm, nearColor, Imgproc.COLORMAP_JET)
    Imgproc.applyColorMap(midNorm,  midColor,  Imgproc.COLORMAP_HOT)

    // 최종 출력 이미지에 픽셀 단위로 복사
    for (v in 0 until n) {
        for (u in 0 until n) {
            val d     = distanceMap[v][u].toDouble()
            val pixel = when {
                d > 0  && d <= 10  -> nearColor.get(v, u)
                d > 10 && d <= 150 -> midColor.get(v, u)
                else               -> doubleArrayOf(0.0, 0.0, 0.0)
            }
            output.put(v, u, pixel[0], pixel[1], pixel[2])
        }
    }

    return output
}

