package com.kosmos.app.domain.tool

import com.kosmos.app.core.common.AppResult

interface ImageProcessor {
    suspend fun processImage(rawBytes: ByteArray): AppResult<ByteArray>
}
