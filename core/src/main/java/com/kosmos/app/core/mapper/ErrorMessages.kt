package com.kosmos.app.core.mapper

import com.kosmos.app.core.common.AppError

/**
 * [ErrorMessages]
 * [AppError]를 사용자에게 보여줄 한국어 문구로 변환합니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Mapper)
 * - **Dependencies**: [AppError], [ErrorCode], [ErrorCodeMapper]
 *
 * ### Key Flow
 * 1. [ErrorCodeMapper]로 정규화 코드를 구한 뒤 코드별 사용자 문구를 반환합니다.
 * 2. ViewModel/화면은 `AppError.toString()`을 직접 노출하지 않고 이 함수만 사용합니다.
 *
 * [WHY] 기존에는 `DbWriteError(task_item)`처럼 내부 클래스명이 화면에 그대로 노출됐다.
 * 원인 진단은 로그(AppLogger)와 감사 로그가 담당하고, 화면에는 조치 가능한 문장만 보여준다.
 */
object ErrorMessages {

    fun userMessage(error: AppError): String = when (ErrorCodeMapper.toErrorCode(error)) {
        ErrorCode.EMPTY_INPUT -> "내용을 입력해주세요."
        ErrorCode.INPUT_TOO_LONG -> "입력이 너무 길어요. 조금 줄여서 다시 보내주세요."
        ErrorCode.MISSING_TIME_INFO -> "시간 정보가 없어요. 몇 시인지 알려주세요."
        ErrorCode.INVALID_INPUT -> "입력을 확인해주세요."

        ErrorCode.IMAGE_TOO_LARGE -> "이미지가 너무 커요. 10MB 이하 파일을 첨부해주세요."
        ErrorCode.UNSUPPORTED_IMAGE_FORMAT -> "지원하지 않는 파일 형식이에요."
        ErrorCode.IN_FLIGHT_CONFLICT -> "이전 요청을 처리하는 중이에요. 잠시 후 다시 시도해주세요."
        ErrorCode.DUPLICATE_EVENT -> "같은 일정이 이미 등록되어 있어요."

        ErrorCode.PERMISSION_DENIED_CALENDAR -> "캘린더 권한이 없어 기기 캘린더에는 저장하지 못했어요. 설정에서 권한을 허용해주세요."
        ErrorCode.PERMISSION_DENIED_MICROPHONE -> "마이크 권한이 필요해요. 설정에서 권한을 허용해주세요."
        ErrorCode.PERMISSION_DENIED_STORAGE -> "파일 접근 권한이 필요해요."

        ErrorCode.MODEL_NOT_FOUND -> "AI 모델 파일을 찾을 수 없어요. 설정 > 모델 관리에서 내려받아 주세요."
        ErrorCode.MODEL_NOT_READY -> "AI 모델을 준비하고 있어요. 잠시 후 다시 시도해주세요."
        ErrorCode.MODEL_INFERENCE_TIMEOUT -> "응답 생성이 너무 오래 걸려 중단했어요. 다시 시도해주세요."
        ErrorCode.MODEL_INFERENCE_ERROR -> "응답을 만들지 못했어요. 다시 시도해주세요."

        ErrorCode.CALENDAR_PROVIDER_ERROR -> "기기 캘린더에 접근하지 못했어요. 일정은 앱에 저장되어 있어요."
        ErrorCode.STT_ERROR -> "음성을 인식하지 못했어요. 다시 말씀해주세요."

        ErrorCode.NETWORK_UNAVAILABLE -> "네트워크에 연결할 수 없어요. 연결 상태를 확인해주세요."
        ErrorCode.SEARCH_TIMEOUT -> "검색이 지연되고 있어요. 잠시 후 다시 시도해주세요."

        ErrorCode.DB_WRITE_ERROR -> "저장에 실패했어요. 다시 시도해주세요."
        ErrorCode.DB_READ_ERROR -> "데이터를 불러오지 못했어요. 다시 시도해주세요."
        ErrorCode.DB_MIGRATION_ERROR -> "데이터 업데이트 중 문제가 발생했어요. 앱을 다시 시작해주세요."

        ErrorCode.EXPORT_FAILED -> "백업 파일을 만들지 못했어요."
        ErrorCode.IMPORT_FAILED -> "복원에 실패했어요. 백업 파일을 확인해주세요."
        ErrorCode.IMPORT_MANIFEST_MISMATCH -> "백업 파일 버전이 맞지 않아요."
        ErrorCode.IMPORT_SCHEMA_MISMATCH -> "백업 파일 형식이 올바르지 않아요."
        ErrorCode.INSUFFICIENT_STORAGE -> "저장 공간이 부족해요. 공간을 확보한 뒤 다시 시도해주세요."

        ErrorCode.TEMPERATURE_WARNING -> "기기가 따뜻해져 응답이 조금 느려질 수 있어요."
        ErrorCode.TEMPERATURE_CRITICAL -> "발열이 심해 기기 보호를 위해 잠시 중단했어요."
    }
}
