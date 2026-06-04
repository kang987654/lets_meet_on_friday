package com.localfriday.app.contract

import com.localfriday.app.core.common.AppError
import com.localfriday.app.core.mapper.ErrorCodeMapper
import com.localfriday.app.domain.model.AuditEventType
import org.junit.Assert.assertEquals
import org.junit.Test

class ContractConsistencyTest {

    @Test
    fun `ErrorCodeMapper correctly maps ImageTooLarge to IMAGE_TOO_LARGE`() {
        val error = AppError.ImageTooLarge(1000L)
        val errorCode = ErrorCodeMapper.toErrorCode(error)
        assertEquals("IMAGE_TOO_LARGE", errorCode.name)
    }

    @Test
    fun `ErrorCodeMapper correctly maps SearchError to SEARCH_TIMEOUT`() {
        val error = AppError.SearchError("timeout")
        val errorCode = ErrorCodeMapper.toErrorCode(error)
        assertEquals("SEARCH_TIMEOUT", errorCode.name)
    }

    @Test
    fun `AuditEventType enum matches api spec`() {
        val types = AuditEventType.values().map { it.name }
        assert(types.contains("MODEL_RUN"))
        assert(types.contains("SEARCH_USED"))
        assert(types.contains("EXPORT"))
        assert(types.contains("IMPORT"))
        assert(types.contains("THERMAL_WARNING"))
        assert(types.contains("THERMAL_SHUTDOWN"))
    }
}
