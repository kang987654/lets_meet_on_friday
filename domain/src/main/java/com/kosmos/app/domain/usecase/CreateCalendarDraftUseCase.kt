package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.domain.model.CalendarDraft
import com.kosmos.app.domain.model.ModelOutput
import java.time.format.DateTimeParseException
import java.time.Instant
import javax.inject.Inject

class CreateCalendarDraftUseCase @Inject constructor() {
    operator fun invoke(output: ModelOutput.CalendarDraftOutput): AppResult<CalendarDraft> {
        // 1. 시간 누락 확인
        if (output.startIso.isBlank() || output.endIso.isBlank()) {
            return AppResult.Failure(
                AppError.ValidationError("time", "missing_time")
            )
        }

        // 2. 시간 형식 유효성 확인 (ISO-8601 파싱 시도)
        try {
            Instant.parse(output.startIso)
            Instant.parse(output.endIso)
        } catch (e: DateTimeParseException) {
            return AppResult.Failure(
                AppError.ValidationError("time", "missing_time")
            )
        }

        // 3. 도메인 모델 변환
        val draft = CalendarDraft(
            title = output.title,
            startIso = output.startIso,
            endIso = output.endIso,
            note = output.note,
            confidence = output.confidence
        )

        return AppResult.Success(draft)
    }
}
