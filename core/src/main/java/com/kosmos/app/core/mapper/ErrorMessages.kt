package com.kosmos.app.core.mapper

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppLanguage

/**
 * [ErrorMessages]
 * [AppError]를 사용자에게 보여줄 문구(한국어/영어)로 변환합니다.
 *
 * ### Architecture Context
 * - **Layer**: Core (Mapper)
 * - **Dependencies**: [AppError], [ErrorCode], [ErrorCodeMapper], [AppLanguage]
 *
 * ### Key Flow
 * 1. [ErrorCodeMapper]로 정규화 코드를 구한 뒤 코드별 사용자 문구를 반환합니다.
 * 2. ViewModel/화면은 `AppError.toString()`을 직접 노출하지 않고 이 함수만 사용합니다.
 *
 * [WHY] 기존에는 `DbWriteError(task_item)`처럼 내부 클래스명이 화면에 그대로 노출됐다.
 * 원인 진단은 로그(AppLogger)와 감사 로그가 담당하고, 화면에는 조치 가능한 문장만 보여준다.
 */
object ErrorMessages {

    fun userMessage(error: AppError, lang: AppLanguage = AppLanguage.KO): String {
        val code = ErrorCodeMapper.toErrorCode(error)
        return if (lang == AppLanguage.EN) userMessageEn(code) else userMessageKo(code)
    }

    private fun userMessageKo(code: ErrorCode): String = when (code) {
        ErrorCode.EMPTY_INPUT -> "내용을 입력해 주세요."
        ErrorCode.INPUT_TOO_LONG -> "입력이 너무 길어요. 조금 줄여서 다시 보내 주세요."
        ErrorCode.INVALID_INPUT -> "입력을 확인해 주세요."

        ErrorCode.IMAGE_TOO_LARGE -> "이미지가 너무 커요. 10MB 이하 파일을 첨부해 주세요."
        ErrorCode.UNSUPPORTED_IMAGE_FORMAT -> "지원하지 않는 파일 형식이에요."

        ErrorCode.PERMISSION_DENIED_CALENDAR -> "캘린더 권한이 없어 기기 캘린더에는 저장하지 못했어요. 설정에서 권한을 허용해 주세요."
        ErrorCode.PERMISSION_DENIED_MICROPHONE -> "마이크 권한이 필요해요. 설정에서 권한을 허용해 주세요."
        ErrorCode.PERMISSION_DENIED_STORAGE -> "파일 접근 권한이 필요해요."

        ErrorCode.MODEL_NOT_FOUND -> "AI 모델 파일을 찾을 수 없어요. 설정 > 모델 관리에서 내려받아 주세요."
        ErrorCode.MODEL_NOT_READY -> "AI 모델을 준비하고 있어요. 잠시 후 다시 시도해 주세요."
        ErrorCode.MODEL_LOAD_FAILED -> "AI 모델을 불러오지 못했어요. 파일이 손상됐을 수 있으니 설정 > 모델 관리에서 다시 내려받아 주세요."
        ErrorCode.MODEL_INFERENCE_TIMEOUT -> "답을 만드는 데 너무 오래 걸려 중단했어요. 다시 시도해 주세요."
        ErrorCode.MODEL_INFERENCE_ERROR -> "답을 만들지 못했어요. 다시 시도해 주세요."

        ErrorCode.CALENDAR_PROVIDER_ERROR -> "기기 캘린더에 접근하지 못했어요. 일정은 앱에 저장되어 있어요."
        ErrorCode.STT_ERROR -> "음성을 인식하지 못했어요. 다시 말씀해 주세요."

        ErrorCode.NETWORK_UNAVAILABLE -> "네트워크에 연결할 수 없어요. 연결 상태를 확인해 주세요."
        ErrorCode.SEARCH_TIMEOUT -> "검색이 지연되고 있어요. 잠시 후 다시 시도해 주세요."

        ErrorCode.DB_WRITE_ERROR -> "저장에 실패했어요. 다시 시도해 주세요."
        ErrorCode.DB_READ_ERROR -> "데이터를 불러오지 못했어요. 다시 시도해 주세요."

        ErrorCode.EXPORT_FAILED -> "백업 파일을 만들지 못했어요."
        ErrorCode.IMPORT_FAILED -> "복원에 실패했어요. 백업 파일을 확인해 주세요."
        ErrorCode.IMPORT_MANIFEST_MISMATCH -> "백업 파일 버전이 맞지 않아요."
        ErrorCode.IMPORT_SCHEMA_MISMATCH -> "백업 파일 형식이 올바르지 않아요."
        ErrorCode.INSUFFICIENT_STORAGE -> "저장 공간이 부족해요. 공간을 확보한 뒤 다시 시도해 주세요."

        ErrorCode.TEMPERATURE_WARNING -> "기기가 따뜻해져 답이 조금 느려질 수 있어요."
        ErrorCode.TEMPERATURE_CRITICAL -> "발열이 심해 기기 보호를 위해 잠시 중단했어요."
    }

    private fun userMessageEn(code: ErrorCode): String = when (code) {
        ErrorCode.EMPTY_INPUT -> "Please enter a message."
        ErrorCode.INPUT_TOO_LONG -> "Your input is too long. Please shorten it and try again."
        ErrorCode.INVALID_INPUT -> "Please check your input."

        ErrorCode.IMAGE_TOO_LARGE -> "The image is too large. Please attach a file under 10 MB."
        ErrorCode.UNSUPPORTED_IMAGE_FORMAT -> "Unsupported file format."

        ErrorCode.PERMISSION_DENIED_CALENDAR -> "Calendar permission is off, so the event couldn't be saved to your device calendar. Please allow access in Settings."
        ErrorCode.PERMISSION_DENIED_MICROPHONE -> "Microphone permission is required. Please allow access in Settings."
        ErrorCode.PERMISSION_DENIED_STORAGE -> "File access permission is required."

        ErrorCode.MODEL_NOT_FOUND -> "AI model file not found. Please download it in Settings > Manage Model."
        ErrorCode.MODEL_NOT_READY -> "Preparing the AI model. Please try again in a moment."
        ErrorCode.MODEL_LOAD_FAILED -> "Couldn't load the AI model. The file may be corrupted — please download it again in Settings > Manage Model."
        ErrorCode.MODEL_INFERENCE_TIMEOUT -> "Response took too long and was stopped. Please try again."
        ErrorCode.MODEL_INFERENCE_ERROR -> "Couldn't generate a response. Please try again."

        ErrorCode.CALENDAR_PROVIDER_ERROR -> "Couldn't access the device calendar. Your event is saved in KOSMOS."
        ErrorCode.STT_ERROR -> "Couldn't recognize your voice. Please try speaking again."

        ErrorCode.NETWORK_UNAVAILABLE -> "No network connection. Please check your connection."
        ErrorCode.SEARCH_TIMEOUT -> "Search timed out. Please try again in a moment."

        ErrorCode.DB_WRITE_ERROR -> "Failed to save. Please try again."
        ErrorCode.DB_READ_ERROR -> "Failed to load data. Please try again."

        ErrorCode.EXPORT_FAILED -> "Couldn't create the backup file."
        ErrorCode.IMPORT_FAILED -> "Restore failed. Please check the backup file."
        ErrorCode.IMPORT_MANIFEST_MISMATCH -> "Backup file version is incompatible."
        ErrorCode.IMPORT_SCHEMA_MISMATCH -> "Backup file format is invalid."
        ErrorCode.INSUFFICIENT_STORAGE -> "Not enough storage space. Please free up space and try again."

        ErrorCode.TEMPERATURE_WARNING -> "Your device is warm, so responses may be slightly slower."
        ErrorCode.TEMPERATURE_CRITICAL -> "Paused temporarily to protect your device from overheating."
    }
}
