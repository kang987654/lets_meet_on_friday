package com.kosmos.app.runtime.gemma

import android.content.Context
import com.kosmos.app.domain.modelrunner.ModelLoadState
import com.kosmos.app.domain.tool.Tokenizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaTokenizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeManager: GemmaRuntimeManager
) : Tokenizer {

    override fun sizeInTokens(text: String): Int {
        // [WHY] LiteRT-LM은 stateless sizeInTokens(String) API를 제공하지 않아 v0에서는
        // 로드 상태와 무관하게 추정치를 사용한다 (기존의 동일 분기 두 개를 통합).
        return estimateTokens(text)
    }

    private fun estimateTokens(text: String): Int {
        // Fallback heuristic: assume 1 token per 3 characters roughly
        return text.length / 3 + 1
    }
}
