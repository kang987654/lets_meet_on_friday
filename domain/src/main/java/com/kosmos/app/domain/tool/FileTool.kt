package com.kosmos.app.domain.tool

import com.kosmos.app.core.common.AppResult

/**
 * [v1] 확장: 파일 I/O 도구
 */
interface FileTool {
    suspend fun readFile(path: String): AppResult<ByteArray>
    suspend fun saveFile(path: String, bytes: ByteArray): AppResult<Unit>
}
