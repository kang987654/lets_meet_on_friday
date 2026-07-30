package com.kosmos.app.core.config

import com.kosmos.app.core.common.Constants

/**
 * [ModelConfig]
 * 온디바이스 AI LLM 엔진(Gemma 4)의 메타데이터 및 토큰/맥락 설정을 담는 데이터 클래스입니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Config)
 * - **Dependencies**: [Constants]
 *
 * ### Key Flow
 * 1. 모델 로더 및 러너 초기화 시 설정 값을 전달하는 데이터 전달 객체로 활용됩니다.
 */
data class ModelConfig(
    val modelId: String = "gemma4-e4b-it",
    val modelFileName: String = Constants.DEFAULT_MODEL_FILENAME,
    val quantization: String = "INT4",
    val maxContextTokens: Int = Constants.MAX_CONTEXT_TOKENS,
    val maxConversationTurns: Int = Constants.MAX_CONVERSATION_TURNS
)
