package com.kosmos.app.assistant.context

import com.kosmos.app.domain.model.ModelOutput
import javax.inject.Inject

class ResponseParser @Inject constructor() {
    fun parse(rawString: String): ModelOutput {
        return ModelOutput.TextOutput(content = rawString.trim())
    }
}
