package com.example.application

import android.graphics.Bitmap

/**
 * MainActivity 와 헬퍼들 간의 공통 인터페이스.
 */
interface ImageProcessor {
    /**
     * 주어진 Bitmap 을 처리한 결과 Bitmap 을 돌려줍니다.
     */
    suspend fun process(bitmap: Bitmap): Bitmap

    /**
     * 사용이 끝나면 호출하여 리소스를 해제합니다.
     */
    fun close()
}
