package com.kosmos.app.runtime.gemma

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

import com.kosmos.app.domain.tool.ImageProcessor

@Singleton
class ImageInputAdapter @Inject constructor() : ImageProcessor {

    override suspend fun processImage(rawBytes: ByteArray): AppResult<ByteArray> {
        val decodeResult = decodeImage(rawBytes)
        if (decodeResult is AppResult.Failure) return AppResult.Failure(decodeResult.error)
        val bitmap = (decodeResult as AppResult.Success).data
        return processImage(bitmap)
    }


    companion object {
        private const val JPEG_QUALITY = 85
    }

    /**
     * Bitmap을 JPEG로 압축하여 ByteArray로 변환합니다.
     */
    suspend fun processImage(bitmap: Bitmap): AppResult<ByteArray> = withContext(Dispatchers.Default) {
        try {
            val outputStream = ByteArrayOutputStream()
            // JPEG 포맷으로 압축하여 품질 유지 (리사이징 금지: Gemma 4 네이티브 패칭 활용)
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
            val byteArray = outputStream.toByteArray()
            AppResult.Success(byteArray)
        } catch (e: Exception) {
            AppResult.Failure(AppError.UnsupportedImageFormat("이미지 전처리 실패: ${e.message}"))
        }
    }

    /**
     * ByteArray를 Bitmap 객체로 디코딩합니다.
     * [WHY] 이전의 싱글턴 공유 reusableBitmap(inBitmap 풀)은 반환 인스턴스가 풀 슬롯 그 자체라서
     * 이전 호출자가 든 비트맵의 픽셀을 다음 디코딩이 덮어쓰고, 동시 호출 시 경합하며,
     * 대형 비트맵이 앱 수명 동안 상주하는 문제가 있어 제거했다.
     */
    suspend fun decodeImage(byteArray: ByteArray): AppResult<Bitmap> = withContext(Dispatchers.Default) {
        try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
            }

            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
            if (bitmap != null) {
                AppResult.Success(bitmap)
            } else {
                AppResult.Failure(AppError.UnsupportedImageFormat("디코딩 결과가 null입니다."))
            }
        } catch (e: Exception) {
            AppResult.Failure(AppError.UnsupportedImageFormat("디코딩 실패: ${e.message}"))
        }
    }
}
