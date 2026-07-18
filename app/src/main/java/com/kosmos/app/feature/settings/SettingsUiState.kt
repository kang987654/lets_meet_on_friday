package com.kosmos.app.feature.settings

import com.kosmos.app.domain.modelrunner.ModelLoadState

data class SettingsUiState(
    val responseStyle: String = "DEFAULT",
    val maxTokens: Int = com.kosmos.app.core.common.Constants.MAX_CONTEXT_TOKENS,
    val modelLoadState: ModelLoadState = ModelLoadState.Loading
)
