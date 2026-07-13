package com.kosmos.app.feature.settings

import com.kosmos.app.domain.modelrunner.ModelLoadState

data class SettingsUiState(
    val responseStyle: String = "DEFAULT",
    val modelLoadState: ModelLoadState = ModelLoadState.Loading
)
