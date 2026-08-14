package com.kosmos.app.domain.model

data class SearchRequest(
    val query: String,
    val maxResults: Int = 3
)
