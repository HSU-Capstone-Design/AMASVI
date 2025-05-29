package com.example.application

import android.graphics.*
import androidx.collection.LruCache
import androidx.camera.core.ImageProxy
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.io.ByteArrayOutputStream

object BitmapUtils {
    private val bitmapPool = object : LruCache<String, Bitmap>(10) {
        override fun entryRemoved(
            evicted: Boolean, key: String, oldValue: Bitmap, newValue: Bitmap?
        ) {
            if (!oldValue.isRecycled) oldValue.recycle()
        }
    }

    fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
        val key = "${width}x${height}"

        var reusableBitmap = bitmapPool.get(key)
        if (reusableBitmap == null || reusableBitmap.isRecycled) {
            reusableBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmapPool.put(key, reusableBitmap)
        }

        val canvas = Canvas(reusableBitmap)
        val scaleX = width / bitmap.width.toFloat()
        val scaleY = height / bitmap.height.toFloat()
        val matrix = Matrix().apply { setScale(scaleX, scaleY) }

        canvas.drawBitmap(bitmap, matrix, null)
        return reusableBitmap
    }

    fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        // 1) Y, U, V plane 버퍼 가져오기
        val yPlane = imageProxy.planes[0].buffer
        val uPlane = imageProxy.planes[1].buffer
        val vPlane = imageProxy.planes[2].buffer

        val ySize = yPlane.remaining()
        val uSize = uPlane.remaining()
        val vSize = vPlane.remaining()

        // 2) NV21 포맷(VU 순서) 바이트 배열 재구성
        val nv21 = ByteArray(ySize + uSize + vSize).also { array ->
            yPlane.get(array, 0, ySize)
            vPlane.get(array, ySize, vSize)         // NV21은 V 먼저
            uPlane.get(array, ySize + vSize, uSize)
        }

        // 3) YuvImage → JPEG → Bitmap 변환 (컬러 제대로)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
        val out = ByteArrayOutputStream().apply {
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, this)
        }
        val tmpBitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size())

        // 4) 센서 회전 정보 적용 (가로/세로 방향 보정)
        val rotation = imageProxy.imageInfo.rotationDegrees
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(tmpBitmap, 0, 0, tmpBitmap.width, tmpBitmap.height, matrix, true)
    }

    /** OpenCV Mat → Android Bitmap 변환 */
    fun matToBitmap(mat: Mat): Bitmap {
        val bmp = Bitmap.createBitmap(
            mat.cols(),
            mat.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(mat, bmp)
        return bmp
    }
}
