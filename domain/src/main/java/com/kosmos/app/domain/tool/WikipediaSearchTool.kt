package com.kosmos.app.domain.tool

import com.kosmos.app.core.common.AppResult

interface WikipediaSearchTool {
    suspend fun search(topic: String, lang: String): AppResult<String>
}
