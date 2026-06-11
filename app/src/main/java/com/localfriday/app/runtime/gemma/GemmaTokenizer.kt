package com.localfriday.app.runtime.gemma

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.localfriday.app.domain.modelrunner.ModelLoadState
import com.localfriday.app.domain.tool.Tokenizer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GemmaTokenizer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val runtimeManager: GemmaRuntimeManager
) : Tokenizer {

    private var llmInference: LlmInference? = null

    override fun sizeInTokens(text: String): Int {
        val currentState = runtimeManager.loadState.value
        if (currentState is ModelLoadState.Ready) {
            if (llmInference == null) {
                try {
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(currentState.modelInfo.modelPath)
                        .build()
                    llmInference = LlmInference.createFromOptions(context, options)
                } catch (e: Exception) {
                    // Fallback if initialization fails
                    return estimateTokens(text)
                }
            }
            return try {
                llmInference?.sizeInTokens(text) ?: estimateTokens(text)
            } catch (e: Exception) {
                estimateTokens(text)
            }
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
