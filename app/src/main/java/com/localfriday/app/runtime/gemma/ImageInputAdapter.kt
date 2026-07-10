package com.localfriday.app.runtime.gemma

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.common.AppResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageInputAdapter @Inject constructor() {

    companion object {
        private const val JPEG_QUALITY = 85
    }

    // GC 스파이크 방지를 위한 재사용 가능한 비트맵 풀 (inBitmap)
    private var reusableBitmap: Bitmap? = null

    /**
     * Bitmap을 적절한 크기로 리사이즈 후 압축하여 ByteArray로 변환합니다.
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
     * ByteArray를 Bitmap 객체로 디코딩합니다. RGB_565 및 inBitmap을 활용하여 메모리 효율을 높입니다.
     */
    suspend fun decodeImage(byteArray: ByteArray): AppResult<Bitmap> = withContext(Dispatchers.Default) {
        try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inMutable = true
                reusableBitmap?.let {
                    // inBitmap을 위한 메모리 크기 검증
                    val optionsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, optionsBounds)
                    val requiredBytes = optionsBounds.outWidth * optionsBounds.outHeight * 2 // RGB_565 = 2 bytes/pixel
                    if (it.allocationByteCount >= requiredBytes) {
                        inBitmap = it
                    }
                }
            }

            val bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, options)
            if (bitmap != null) {
                reusableBitmap = bitmap
                AppResult.Success(bitmap)
            } else {
                AppResult.Failure(AppError.UnsupportedImageFormat("디코딩 결과가 null입니다."))
            }
        } catch (e: Exception) {
            AppResult.Failure(AppError.UnsupportedImageFormat("디코딩 실패: ${e.message}"))
        }
    }

}
