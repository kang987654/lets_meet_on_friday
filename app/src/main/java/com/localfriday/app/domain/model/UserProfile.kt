package com.localfriday.app.domain.model

data class UserProfile(
    val name: String,
    val role: String,
    val preferences: Map<String, String> = emptyMap()
)
