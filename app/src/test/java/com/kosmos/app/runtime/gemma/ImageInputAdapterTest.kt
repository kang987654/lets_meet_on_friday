package com.kosmos.app.runtime.gemma

import android.graphics.Bitmap
import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream

/**
 * [ImageInputAdapterTest]
 * 이미지가 모델로 가는 단일 관문의 크기·치수 방어를 검증합니다.
 *
 * [WHY] 이전에는 공유 시트 경로만 10MB 를 검사하고 **앱 내 파일 피커 경로는 아무 검사도
 * 없었다.** `Constants.MAX_IMAGE_SIZE_BYTES` 와 `MAX_IMAGE_DIMENSION_PX` 는 선언만 되고 참조가
 * 0건이어서, 12MP 사진을 고르면 그대로 디코딩돼 3.7GB 모델과 메모리를 다퉜다. 또한 이미지·음성
 * 경로에는 테스트가 아예 없었다(유일한 멀티모달 테스트는 문서/텍스트 경로를 검증했다).
 */
@RunWith(RobolectricTestRunner::class)
class ImageInputAdapterTest {

    private val adapter = ImageInputAdapter()

    private fun jpegBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    // --- 축소 배수 (순수 계산) ---

    @Test
    fun `상한 이하 이미지는 손대지 않는다`() {
        // [WHY] "리사이징 금지(Gemma 4 네이티브 패칭 활용)" 원칙과 충돌하지 않아야 한다.
        assertEquals(1, ImageInputAdapter.computeSampleSize(800, 600))
        assertEquals(1, ImageInputAdapter.computeSampleSize(Constants.MAX_IMAGE_DIMENSION_PX, 500))
    }

    @Test
    fun `상한을 넘으면 2의 거듭제곱으로 축소한다`() {
        // 2048 → 배수 2(1024), 4000(12MP 사진) → 배수 4(1000)
        assertEquals(2, ImageInputAdapter.computeSampleSize(2048, 1536))
        assertEquals(4, ImageInputAdapter.computeSampleSize(4000, 3000))
        // 9000 → 배수 16 (8 은 1125px 로 아직 상한을 넘는다)
        assertEquals(16, ImageInputAdapter.computeSampleSize(9000, 100))
    }

    @Test
    fun `축소 후 긴 변이 항상 상한 이하다`() {
        // [WHY] 배수를 하나씩 확인하는 것보다 이 불변식이 계약의 본질이다.
        for (size in listOf(1025, 1600, 2048, 3000, 4000, 6000, 12000)) {
            val sample = ImageInputAdapter.computeSampleSize(size, size / 2)
            assertTrue(
                "$size / $sample = ${size / sample} 가 상한을 넘는다",
                size / sample <= Constants.MAX_IMAGE_DIMENSION_PX
            )
        }
    }

    @Test
    fun `세로가 긴 이미지도 긴 변을 기준으로 판정한다`() {
        assertEquals(4, ImageInputAdapter.computeSampleSize(100, 4000))
    }

    // --- 크기 방어 ---

    @Test
    fun `상한을 넘는 바이트는 디코딩 전에 거부된다`() {
        // [WHY] 디코딩 전에 잘라내는 것이 핵심이다 — 디코딩까지 가면 이미 메모리를 쓴다.
        val tooBig = ByteArray(Constants.MAX_IMAGE_SIZE_BYTES + 1)

        val result = runBlocking { adapter.processImage(tooBig) }

        assertTrue(result is AppResult.Failure)
        assertTrue(
            "ImageTooLarge 여야 한다: ${(result as AppResult.Failure).error}",
            result.error is AppError.ImageTooLarge
        )
    }

    @Test
    fun `상한 이하 정상 이미지는 JPEG 로 통과한다`() {
        val result = runBlocking { adapter.processImage(jpegBytes(640, 480)) }

        assertTrue("실패: ${(result as? AppResult.Failure)?.error}", result is AppResult.Success)
        assertTrue((result as AppResult.Success).data.isNotEmpty())
    }

    @Test
    fun `큰 이미지는 거부되지 않고 축소되어 통과한다`() {
        // [WHY] 상한을 넘는다고 실패시키면 사용자가 찍은 사진 대부분을 못 쓴다 — 거부가 아니라
        // 디코딩 시점 축소로 처리하는 것이 이 관문의 계약이다.
        val result = runBlocking { adapter.processImage(jpegBytes(3000, 2000)) }

        assertTrue("실패: ${(result as? AppResult.Failure)?.error}", result is AppResult.Success)
    }

    // [WHY] "이미지가 아닌 바이트는 형식 오류" 는 여기서 검증할 수 없다 — Robolectric 의
    // `ShadowBitmapFactory` 는 JPEG 를 실제로 파싱하지 않고 임의 바이트에도 가짜 비트맵을
    // 만들어 주므로 디코딩 실패를 재현할 방법이 없다(실측 확인). 그 경로는 실기기 전용이다.
}
