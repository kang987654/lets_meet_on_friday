package com.localfriday.app.ui.feature.settings

import com.localfriday.app.domain.modelrunner.ModelLoadState

data class SettingsUiState(
    val responseStyle: String = "DEFAULT",
    val modelLoadState: ModelLoadState = ModelLoadState.Loading
)
