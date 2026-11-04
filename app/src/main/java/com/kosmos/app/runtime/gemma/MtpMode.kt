package com.kosmos.app.runtime.gemma

import com.google.ai.edge.litertlm.SpeculativeDecodingConfig
import com.google.ai.edge.litertlm.SpeculativeDecodingMode
import java.io.File

/**
 * [MtpMode]
 * 추측 디코딩(MTP — 모델 파일에 든 드래프터가 다음 토큰 몇 개를 미리 내고 본 모델이 한 번에 검증)의 켜짐 방식입니다 (0.41.1).
 *
 * ### Architecture Context
 * - **Layer**: Runtime (Gemma) — LiteRT-LM 0.19.0 [SpeculativeDecodingConfig] 를 채팅·oneShot 대화 설정에 싣는다([chatConversationConfig]).
 *
 * ### Key Flow
 * 1. 엔진을 올릴 때 [resolve] 로 한 번 정한다 — GPU 엔진만. CPU(에뮬레이터·폴백)는 늘 미지정(이득 없음·미검증, exp77).
 * 2. 디버그 빌드는 앱 외부 파일 폴더의 [DEBUG_FILE_NAME] 으로 덮어쓴다 — 휴대폰에서 모드별 속도를 재기 위해(`adb push` 후 앱 재시작).
 *
 * [WHY] 0.18.0 까지는 켜지 않았다 — 툴 선언 + 제약 디코딩과 드래프터 제안이 충돌한다고 봤다(ADR-011). 0.19.0 에서 PC 회귀 세트·깊은 문맥 숫자(exp77c)가
 * MTP 켜짐에서도 무회귀였다. 기본값 [ADAPTIVE] 는 휴대폰 실측(0.41.1 M2, ADR-036)으로 정했다.
 */
enum class MtpMode {
    OFF,
    ON,
    ADAPTIVE;

    /** 대화 설정에 실을 값 — [OFF] 도 명시한다(런타임 기본값이 바뀌어도 우리가 고른 동작을 유지). */
    fun toConfig(): SpeculativeDecodingConfig = SpeculativeDecodingConfig(
        when (this) {
            OFF -> SpeculativeDecodingMode.OFF
            ON -> SpeculativeDecodingMode.ON
            ADAPTIVE -> SpeculativeDecodingMode.ADAPTIVE
        }
    )

    companion object {
        /**
         * 릴리스 기본값.
         *
         * [WHY] 휴대폰(SM8750) 디코드가 OFF 약 17 → ON 약 31 → ADAPTIVE 약 33 토큰/초였고, 긴 답에서는 ADAPTIVE 가 꾸준히 가장 빨랐다.
         * 추측이 손해인 구간에서 스스로 끄는 쪽이라 안전하다(ADR-036, CHANGELOG 0.41.1).
         */
        val DEFAULT: MtpMode = ADAPTIVE

        const val DEBUG_FILE_NAME = "debug_mtp.txt"

        /** "on"·" ADAPTIVE\n" 처럼 대소문자·공백을 무시하고 읽는다. 비었거나 모르는 값이면 null. */
        fun parse(text: String?): MtpMode? {
            val key = text?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.name == key }
        }

        /**
         * 이번 엔진에 쓸 모드 — GPU 가 아니면 null(설정을 싣지 않는다). 디버그 빌드에서만 [debugDir] 의 파일을 본다.
         */
        fun resolve(gpu: Boolean, debug: Boolean, debugDir: File?): MtpMode? {
            if (!gpu) return null
            if (!debug || debugDir == null) return DEFAULT
            val text = runCatching { File(debugDir, DEBUG_FILE_NAME).takeIf { it.isFile }?.readText() }.getOrNull()
            return parse(text) ?: DEFAULT
        }
    }
}
