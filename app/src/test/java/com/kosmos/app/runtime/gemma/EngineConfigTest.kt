package com.kosmos.app.runtime.gemma

import com.google.ai.edge.litertlm.Backend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * [EngineConfigTest]
 * 엔진에 **오디오 백엔드가 설정되는지**를 못박습니다.
 *
 * [WHY] 이 테스트가 없어서 음성 입력이 처음부터 동작하지 않았다. `EngineConfig` 가
 * `backend`·`visionBackend` 만 설정하고 `audioBackend` 를 빼먹었는데, 오디오 경로 테스트
 * (`TranscribeAudioUseCaseTest` 7건)는 `ModelRunner` 를 목으로 대체해 **잘못 설정된 그
 * 컴포넌트 자체를 검증 대상에서 지웠다.** 2026-08-12 실기기에서 "응답을 만들지 못했어요" 로
 * 드러났고, 그때까지 통과하던 테스트는 동작 불가능한 경로를 검증하고 있었다.
 *
 * 그래서 여기서는 목을 쓰지 않고 설정 조립 함수만 직접 호출한다.
 */
class EngineConfigTest {

    @Test
    fun `GPU 설정에도 오디오 백엔드가 들어간다`() {
        val config = buildEngineConfig(MODEL, Backend.GPU(), CACHE)

        assertNotNull(
            "audioBackend 가 null 이면 엔진이 Content.AudioFile 을 받지 못해 음성이 통째로 실패한다",
            config.audioBackend
        )
        assertNotNull("visionBackend 가 null 이면 이미지 첨부가 실패한다", config.visionBackend)
    }

    @Test
    fun `CPU 폴백에도 오디오 백엔드와 캐시 경로가 들어간다`() {
        // [WHY] 폴백 경로는 GPU 초기화 실패 기기에서만 쓰이므로 눈에 잘 띄지 않는다. 예전에는
        // `cacheDir` 조차 넘기지 않아 그 기기들은 매 실행마다 커널 캐시를 다시 만들었다.
        val config = buildEngineConfig(MODEL, Backend.CPU(), CACHE)

        assertNotNull(config.audioBackend)
        assertEquals(CACHE, config.cacheDir)
    }

    @Test
    fun `모델 경로가 그대로 전달된다`() {
        assertEquals(MODEL, buildEngineConfig(MODEL, Backend.GPU(), CACHE).modelPath)
    }

    @Test
    fun `EngineConfig 의 오디오 백엔드 기본값은 null 이다`() {
        // [WHY] 이 한 줄이 위 테스트들을 실제 가드로 만든다. 기본값이 null 이어야 "안 넣으면
        // 음성이 죽는다"가 성립하고, 우리 조립 함수의 단언이 의미를 갖는다.
        //
        // [WHY] 동시에 SDK 감시 장치다. 언젠가 litertlm 이 기본값을 CPU 로 바꾸면 이 테스트가
        // 깨지면서 알려 준다 — 그때는 `buildEngineConfig` 의 명시 지정이 불필요해진다.
        val withoutAudio = com.google.ai.edge.litertlm.EngineConfig(
            modelPath = MODEL,
            backend = Backend.GPU(),
            visionBackend = Backend.GPU(),
            cacheDir = CACHE
        )

        org.junit.Assert.assertNull(
            "기본값이 null 이 아니면 audioBackend 누락이 음성 실패의 원인이라는 진단이 틀린 것이다",
            withoutAudio.audioBackend
        )
    }

    private companion object {
        const val MODEL = "/data/user/0/com.kosmos.app/files/gemma-4-E4B-it.litertlm"
        const val CACHE = "/data/user/0/com.kosmos.app/cache"
    }
}
