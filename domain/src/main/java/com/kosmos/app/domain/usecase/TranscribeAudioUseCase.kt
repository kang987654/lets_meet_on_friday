package com.kosmos.app.domain.usecase

import com.kosmos.app.core.common.AppError
import com.kosmos.app.core.common.AppResult
import com.kosmos.app.core.common.Constants
import com.kosmos.app.domain.modelrunner.ChatPrompt
import com.kosmos.app.domain.modelrunner.ModelRunner
import javax.inject.Inject

/**
 * [TranscribeAudioUseCase]
 * 녹음된 오디오를 온디바이스 모델로 받아써 텍스트로 돌려줍니다.
 *
 * ### Architecture Context
 * - **Layer**: Domain (UseCase)
 * - **Dependencies**: [ModelRunner]
 *
 * [WHY] 별도 STT 엔진을 두지 않는다. 이미 올라와 있는 멀티모달 모델이 오디오를 이해하므로
 * 전사는 그 모델에게 맡기는 것이 자산도 메모리도 아끼는 길이다.
 *
 * [WHY] `oneShot = true` 다. 전사는 사용자 대화가 아니라 부수 계산이므로 채팅 Conversation
 * 에 섞이면 안 된다 — 섞이면 시스템 지시가 달라져 캐시된 대화가 파괴되고 다음 채팅 턴이
 * 전체 프리필을 다시 낸다(ADR-010). 임시 대화는 툴 선언도 없어 프리필이 짧다.
 */
class TranscribeAudioUseCase @Inject constructor(
    private val modelRunner: ModelRunner
) {
    suspend operator fun invoke(audioFilePath: String): AppResult<String> {
        // [WHY] `currentInput` 을 **비운다.** 지시 문장을 사용자 턴에 실어 보내면 무음 오디오에서
        // 모델이 그 문장을 그대로 되읊는다 — PC 실험(exp16)에서 무음 2초에
        // `"이 오디오를 들리는 그대로 받아써 주세요."` 가 전사문으로 돌아왔다. 공백이 아니므로
        // 아래 빈 검사를 통과해, 앱이 그 문장을 사용자 메시지로 저장하고 답까지 한다(PRD EC3 위반).
        //
        // 오디오만 보내도 전사는 정상이고 무음에서는 빈 결과가 온다(exp16 실측). 무엇을 할지는
        // 시스템 지시가 이미 말하므로 사용자 턴에 문장이 필요하지 않다.
        val prompt = ChatPrompt(
            sessionId = SESSION_ID,
            systemInstruction = SYSTEM_INSTRUCTION,
            history = emptyList(),
            currentInput = "",
            oneShot = true
        )

        return when (val result = modelRunner.generateWithAudio(prompt, audioFilePath)) {
            is AppResult.Failure -> AppResult.Failure(result.error)
            is AppResult.Success -> {
                val transcript = clean(result.data.text)
                // [WHY] 무음이나 잡음만 녹음된 경우다. 빈 사용자 메시지를 저장하면 대화 기록에
                // 내용 없는 말풍선이 남고 모델도 답할 것이 없다. 오류로 올려 보내면 화면이
                // `ErrorMessages` 의 "음성을 인식하지 못했어요. 다시 말씀해주세요." 를 띄운다
                // (PRD EC3 무음 입력 재시도 안내).
                if (transcript.isBlank()) {
                    AppResult.Failure(AppError.SttError("전사 결과가 비었습니다"))
                } else {
                    AppResult.Success(transcript)
                }
            }
        }
    }

    /**
     * 모델이 덧붙이기 쉬운 껍데기를 벗깁니다.
     *
     * [WHY] 지시를 아무리 못 박아도 4B 모델은 `"안녕하세요"` 처럼 따옴표로 감싸거나
     * `사용자: 안녕하세요` 처럼 화자 라벨을 붙이는 경향이 있다. 그대로 두면 그 껍데기가
     * 사용자 메시지로 저장되고 이후 모든 검색·히스토리에 남는다.
     */
    private fun clean(raw: String): String {
        var text = raw.trim()

        for (prefix in PREFIXES) {
            if (text.startsWith(prefix, ignoreCase = true)) {
                text = text.removeRange(0, prefix.length).trim()
            }
        }

        // 앞뒤를 감싼 따옴표 한 겹만 벗긴다(본문 안의 따옴표는 건드리지 않는다).
        for ((open, close) in QUOTE_PAIRS) {
            if (text.length >= 2 && text.first() == open && text.last() == close) {
                text = text.substring(1, text.length - 1).trim()
                break
            }
        }

        // [WHY] 모델이 전사 대신 답변을 늘어놓는 실패 모드에 대비한 상한이다. 이 값은 채팅
        // 입력 상한과 같아야 한다 — 여기를 통과한 텍스트가 곧바로 사용자 메시지가 된다.
        return text.take(Constants.MAX_INPUT_CHARS)
    }

    private companion object {
        const val SESSION_ID = "audio-transcription"

        const val SYSTEM_INSTRUCTION =
            "[System]\n" +
                "You are a speech-to-text transcriber. Output ONLY the exact words spoken in the " +
                "audio, in the language they were spoken. Do NOT answer, translate, summarize, or " +
                "add any commentary, labels, or quotation marks. If there is no intelligible " +
                "speech, output nothing at all."

        // [WHY] 사용자 턴 지시 문장을 없앴다 — 무음일 때 모델이 그것을 되읊어 전사문으로
        // 내보내는 것이 실측됐다(exp16). 상수도 함께 지운다. 되살리려면 그 되읊음을 다시
        // 걸러낼 방법이 필요하다.

        val PREFIXES = listOf("사용자:", "사용자 :", "User:", "Transcript:", "전사:", "받아쓰기:")

        val QUOTE_PAIRS = listOf('"' to '"', '\'' to '\'', '“' to '”', '‘' to '’')
    }
}
