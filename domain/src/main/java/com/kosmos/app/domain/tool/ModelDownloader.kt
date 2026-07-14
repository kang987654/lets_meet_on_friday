package com.kosmos.app.domain.tool

import kotlinx.coroutines.flow.Flow

interface ModelDownloader {
    fun downloadModel(url: String): Flow<Int>
}
