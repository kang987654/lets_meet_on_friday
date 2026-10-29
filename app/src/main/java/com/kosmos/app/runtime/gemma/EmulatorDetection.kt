package com.kosmos.app.runtime.gemma

import android.os.Build

/**
 * 안드로이드 에뮬레이터인가 — 기기 식별 문자열만 보는 순수 판정.
 *
 * [WHY] 에뮬레이터에서는 엔진을 처음부터 CPU 로 잡아야 답 생성까지 된다(GemmaModelRunner 엔진 초기화) — 판정이 틀려 실기기를 에뮬레이터로 보면
 * 실기기가 CPU 로 느려지므로, 에뮬레이터 고유 값(가상 하드웨어 이름·SDK 이미지 제품명)만 본다.
 */
internal fun isEmulator(hardware: String, product: String, fingerprint: String): Boolean =
    hardware == "ranchu" || hardware == "goldfish" ||
        product.startsWith("sdk_gphone") || product.startsWith("sdk_google") || product.contains("emulator") ||
        fingerprint.startsWith("generic")

/** 지금 기기가 에뮬레이터인가 — 프로세스 동안 바뀌지 않는다. */
internal val runningOnEmulator: Boolean by lazy { isEmulator(Build.HARDWARE, Build.PRODUCT, Build.FINGERPRINT) }
