package com.kosmos.app.runtime.gemma

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

import com.kosmos.app.domain.tool.ImageProcessor

@Singleton
class ImageInputAdapter @Inject constructor() : ImageProcessor {

    /**
     * [WHY] 크기 검증이 여기 있는 이유: 이미지가 모델로 가는 **단일 관문**이다. 이전에는 공유
     * 시트 경로(`ShareIntentHandler`)만 10MB 를 검사하고, 앱 내 파일 피커 경로는 **아무 검사도
     * 없었다** — `Constants.MAX_IMAGE_SIZE_BYTES` 와 `MAX_IMAGE_DIMENSION_PX` 는 선언만 되고
     * 참조가 0건이었다. 12MP 사진을 고르면 그대로 디코딩돼 3.7GB 모델과 메모리를 다퉜다.
     */
    override suspend fun processImage(rawBytes: ByteArray): AppResult<ByteArray> {
        if (rawBytes.size > Constants.MAX_IMAGE_SIZE_BYTES) {
            return AppResult.Failure(AppError.ImageTooLarge(rawBytes.size.toLong()))
        }
        val decodeResult = decodeImage(rawBytes)
        if (decodeResult is AppResult.Failure) return AppResult.Failure(decodeResult.error)
        val bitmap = (decodeResult as AppResult.Success).data
        return processImage(bitmap)
    }


    companion object {
        private const val JPEG_QUALITY = 85

        /**
         * 디코딩 단계에서 적용할 축소 배수를 구합니다. `BitmapFactory` 는 2의 거듭제곱만
         * 지원하므로 상한을 넘지 않는 가장 작은 배수를 고릅니다.
         *
         * [WHY] 디코딩 **후** 리사이즈가 아니라 디코딩 **시점** 축소다. 후자는 큰 비트맵을 일단
         * 메모리에 올려야 하므로 OOM 을 막지 못한다. 3.7GB 모델과 메모리를 다투는 상황에서
         * 12MP 사진을 그대로 올리면 앱이 죽는다.
         *
         * [WHY] **이것은 OOM 방어이고 해상도 정책이 아니다.** 예전 주석은 이 함수가
         * *"리사이징 금지(Gemma 4 네이티브 패칭 활용)" 원칙과 충돌하지 않는다* 고 적었는데
         * 사실과 달랐다 — 4000px 사진은 1000px 로 **실제로 축소된다.** 코드는 줄이고 주석은
         * 안 줄인다고 말하는 상태였다.
         *
         * [WHY] 해상도를 정하는 정식 레버는 `ExperimentalFlags.visualTokenBudget` 이다(공식 문서
         * capabilities/vision: 70·140·280·560·1120 중 선택, 예산×9 패치). 우리는 그것을 **아직
         * 설정하지 않는다** — 파이썬 하네스가 그 플래그를 노출하지 않아 값을 고를 근거를 로컬에서
         * 만들 수 없다. 근거 없이 고르면 문서 스크린샷 읽기(PRD F3)를 조용히 깎을 위험이 있어,
         * 실기기에서 예산별 비교가 가능해질 때까지 런타임 기본값에 맡긴다.
         */
        internal fun computeSampleSize(width: Int, height: Int): Int {
            var sampleSize = 1
            while (maxOf(width, height) / sampleSize > Constants.MAX_IMAGE_DIMENSION_PX) {
                sampleSize *= 2
            }
            return sampleSize
        }
    }

    /**
     * Bitmap을 JPEG로 압축하여 ByteArray로 변환합니다.
     */
    suspend fun processImage(bitmap: Bitmap): AppResult<ByteArray> = withContext(Dispatchers.Default) {
        try {
            val outputStream = ByteArrayOutputStream()
            // [WHY] 런타임에 넘길 바이트로 만들기 위한 압축이다. 해상도는 이미 디코딩 시점에
            // 정해졌고([computeSampleSize]) 여기서는 바꾸지 않는다.
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
            // 1차: 픽셀을 올리지 않고 크기만 읽어 축소 배수를 정한다.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                return@withContext AppResult.Failure(
                    AppError.UnsupportedImageFormat("이미지 크기를 읽을 수 없습니다.")
                )
            }

            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.RGB_565
                inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight)
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
