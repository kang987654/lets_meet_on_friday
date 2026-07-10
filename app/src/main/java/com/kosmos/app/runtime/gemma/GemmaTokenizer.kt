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
        val currentState = runtimeManager.loadState.value
        if (currentState is ModelLoadState.Ready) {
            // Note: LiteRT-LM (unlike MediaPipe tasks-genai) does not provide a direct sizeInTokens(String) method.
            // A conversation's getTokenCount() is stateful. We fallback to estimation for v0.
            return estimateTokens(text)
        } else {
            // Model not loaded yet, use estimation
            return estimateTokens(text)
        }
    }

    private fun estimateTokens(text: String): Int {
        // Fallback heuristic: assume 1 token per 3 characters roughly
        return text.length / 3 + 1
    }
}
