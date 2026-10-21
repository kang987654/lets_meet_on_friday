package com.kosmos.app.ui

/**
 * 모델 id(`gemma-4-e4b-it`) → 화면 이름("Gemma 4 E4B"). 모르는 id 는 그대로.
 *
 * [WHY] 파일·런타임 id 를 그대로 보였다(0.38.3 점검 P2 — 내부 이름 노출). 모델 관리 화면의 이름과 같게 맞춘다.
 */
fun modelDisplayName(modelId: String): String = when {
    modelId.contains("gemma-4-e4b", ignoreCase = true) -> "Gemma 4 E4B"
    modelId.contains("gemma-4-e2b", ignoreCase = true) -> "Gemma 4 E2B"
    else -> modelId
}
