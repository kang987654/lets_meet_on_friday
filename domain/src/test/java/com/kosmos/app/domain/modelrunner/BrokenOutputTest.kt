package com.kosmos.app.domain.modelrunner

import com.kosmos.app.core.common.AppLanguage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [BrokenOutput] — 한국어 결과에 한글이 거의 없으면 깨진 것(0.42.4, 휴대폰에서 본 실제 모양). */
class BrokenOutputTest {
    @Test
    fun `휴대폰에서 본 깨진 회고는 깨진 것으로 본다`() {
        assertTrue(BrokenOutput.looksBroken("cist(파 21.dinizadi 62.c,gr6000001020stx'a00w60100g*66012w+진은iPuselessusagi60,gन्तु&asami64420000 by-all? DamenP6060l lmerapi3201 is new", AppLanguage.KO))
    }

    @Test
    fun `영문 낱말이 섞인 정상 한국어와 짧은 글, 영어 결과는 깨진 것이 아니다`() {
        assertFalse(BrokenOutput.looksBroken("이번 주에는 Kotlin 공부와 Android 앱 개발에 시간을 쓰셨어요. 치과 예약도 잡으셨어요.", AppLanguage.KO))
        assertFalse(BrokenOutput.looksBroken("판정: 같음", AppLanguage.KO))
        assertFalse(BrokenOutput.looksBroken("This week you spent time on dentist appointments and running.", AppLanguage.EN))
    }
}
