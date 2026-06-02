package com.localfriday.app.domain.model

// v1 SearchRequest contract
data class SearchRequest(
    val query: String,
    val maxResults: Int = 3
)
