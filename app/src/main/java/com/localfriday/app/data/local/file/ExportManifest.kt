package com.localfriday.app.data.local.file

import kotlinx.serialization.Serializable

@Serializable
data class ExportManifest(
    val version: String = "1.0",
    val encrypted: Boolean = false,
    val exportDate: Long = System.currentTimeMillis(),
    val appVersion: String,
    val modelId: String? = null,
    val sessionCount: Int = 0
)
